"""
The control loop: owns the controllers, the active mode, the e-stop latch, and
the fixed-rate tick that pushes joint commands through safety to the arm.
"""

from __future__ import annotations

import asyncio
import logging
import time

import numpy as np

from . import MOTORS
from . import protocol
from .arm import Arm
from .config import Config
from .controllers import (
    Ctx,
    GotoController,
    JogController,
    SequenceController,
    VoiceController,
)
from .safety import Safety

log = logging.getLogger("soarm.loop")

_GLOBAL_TYPES = {"hello", "ping", "stop", "resume", "mode"}


class ControlLoop:
    def __init__(self, arm: Arm, safety: Safety, config: Config):
        self.arm = arm
        self.safety = safety
        self.config = config
        self.controllers = {
            c.name: c
            for c in (
                JogController(safety.timeout),
                GotoController(),
                SequenceController(),
                VoiceController(),
            )
        }
        self.active = "jog"
        self.stopped = False
        self.server = None            # set by run_bridge

        self._last_q = np.asarray(config.home_joints_deg, float)
        self._reply_client = None
        self._controlling_client = None   # last client that sent a control frame
        self._running = False

    # -- outbound plumbing (sync; server does the actual async send) ----------

    def broadcast(self, frame: dict) -> None:
        if self.server is not None:
            self.server.broadcast(protocol.encode(frame))

    def reply(self, frame: dict) -> None:
        if self._reply_client is not None:
            self._reply_client.enqueue(protocol.encode(frame))
        else:
            self.broadcast(frame)

    def _ctx(self) -> Ctx:
        return Ctx(
            arm=self.arm,
            safety=self.safety,
            config=self.config,
            reply=self.reply,
            broadcast=self.broadcast,
            estop=self._trigger_estop,
            resume=self._clear_estop,
        )

    def _trigger_estop(self) -> None:
        if not self.stopped:
            log.warning("E-STOP")
        self.stopped = True

    def _clear_estop(self) -> None:
        if self.stopped:
            log.info("stop cleared (by controller)")
        self.stopped = False

    # -- inbound ----------------------------------------------------------

    def _send_welcome(self, client) -> None:
        client.enqueue(
            protocol.encode(
                protocol.welcome(
                    self.arm.robot_type, list(MOTORS), self.arm.has_kinematics
                )
            )
        )

    def on_connect(self, client) -> None:
        # Wait for the client's `hello` before greeting (see protocol.py).
        pass

    def on_disconnect(self, client) -> None:
        if client is self._reply_client:
            self._reply_client = None
        # Only the client that was actually driving the arm forces a stop on
        # drop. A passive monitor (the 3D viewer) refreshing must not halt it.
        if client is self._controlling_client:
            self._controlling_client = None
            self._trigger_estop()

    def dispatch(self, msg: dict, client) -> None:
        self._reply_client = client
        t = msg.get("type")

        if t not in ("hello", "ping"):
            self._controlling_client = client   # this client is driving now

        if t == "hello":
            self._send_welcome(client)
        elif t == "ping":
            self.reply(protocol.pong(msg.get("t")))
        elif t == "stop":
            self._trigger_estop()
        elif t == "resume":
            self.stopped = False
            log.info("stop cleared")
        elif t == "mode":
            self.set_mode(str(msg.get("mode", "")))
        else:
            self.controllers[self.active].handle(msg, self._ctx())

    def set_mode(self, name: str) -> None:
        if name not in self.controllers or name == self.active:
            return
        ctx = self._ctx()
        self.controllers[self.active].on_deactivate(ctx)
        self.active = name
        self.stopped = False
        self.controllers[name].on_activate(ctx)
        log.info("mode -> %s", name)

    # -- the loop -------------------------------------------------------

    async def run(self) -> None:
        fps = max(1, self.config.control.fps)
        period = 1.0 / fps
        tele_every = max(1, round(fps / max(1, self.config.control.telemetry_hz)))

        try:
            self._last_q = self.arm.read_joints()
        except Exception as e:  # noqa: BLE001
            log.warning("initial read failed: %s", e)

        self._running = True
        n = 0
        log.info("control loop @ %d Hz (active=%s)", fps, self.active)

        while self._running:
            t0 = time.monotonic()
            try:
                if not self.arm.torque_on:
                    # hand-guided (sequence release): track pose, send nothing
                    self._last_q = self.arm.read_joints()
                else:
                    if self.stopped:
                        q_des = self._last_q
                    else:
                        out = self.controllers[self.active].tick(period, self._ctx())
                        q_des = self._last_q if out is None else np.asarray(out, float)

                    q_des = self.safety.clamp_joints(q_des)
                    q_des = self.safety.limit_step(q_des, self._last_q)
                    self.arm.send_joints(q_des)
                    self._last_q = q_des
            except Exception as e:  # noqa: BLE001 - a bad tick must not kill the loop
                log.exception("tick failed")
                self.broadcast(protocol.error(f"tick: {e}"))

            n += 1
            if n % tele_every == 0:
                self._emit_state()

            await asyncio.sleep(max(0.0, period - (time.monotonic() - t0)))

    def stop_loop(self) -> None:
        self._running = False

    def _emit_state(self) -> None:
        try:
            q = self.arm.read_joints()
        except Exception:  # noqa: BLE001
            return
        joints = {m: float(q[i]) for i, m in enumerate(MOTORS)}
        ee = None
        if self.arm.has_kinematics:
            try:
                xyz = self.arm.ee_xyz(q)
                ee = {"x": float(xyz[0]), "y": float(xyz[1]), "z": float(xyz[2])}
            except Exception:  # noqa: BLE001
                ee = None
        self.broadcast(protocol.state(self.active, self.stopped, joints, ee))

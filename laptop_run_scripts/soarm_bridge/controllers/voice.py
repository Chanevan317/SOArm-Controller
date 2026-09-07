"""
Voice: discrete keywords -> motion primitives. Deliberately small.

  stop                      -> latched e-stop
  home                      -> joint-space move to config.home_joints_deg
  open / close              -> drive the gripper to 0 / 100
  up/down/left/right/
    forward/back             -> a timed EE velocity pulse (voice.pulse_ms)
  faster / slower            -> shift the pulse speed tier

Directional pulses need kinematics. Axis signs are assumptions to verify.
"""

from __future__ import annotations

import logging

import numpy as np

from .. import J
from .base import Controller, Ctx

log = logging.getLogger("soarm.voice")

_DIRS = {
    "forward": (0, +1.0), "back": (0, -1.0),
    "left": (1, +1.0), "right": (1, -1.0),
    "up": (2, +1.0), "down": (2, -1.0),
}


class VoiceController(Controller):
    name = "voice"

    def __init__(self) -> None:
        self.reset()

    def reset(self) -> None:
        self._job: str | None = None          # None | "home" | "pulse" | "grip"
        self._t = 0.0
        self._dur = 0.0
        self._q_start: np.ndarray | None = None
        self._q_goal: np.ndarray | None = None
        self._ee: np.ndarray | None = None
        self._R0: np.ndarray | None = None
        self._q_seed: np.ndarray | None = None
        self._dir = np.zeros(3)
        self._grip_goal = 0.0
        self._speed = 0

    def on_activate(self, ctx: Ctx) -> None:
        self.reset()
        self._speed = int(np.clip(ctx.config.voice.pulse_speed_index, 0, 2))

    def handle(self, msg: dict, ctx: Ctx) -> None:
        if msg.get("type") != "voice":
            return
        token = str(msg.get("token", "")).lower().strip()

        if token == "stop":
            self.reset()
            ctx.estop()
            ctx.reply({"type": "ack", "of": "voice", "ok": True, "reason": "stop"})
            return

        if token in ("faster", "slower"):
            self._speed = int(np.clip(self._speed + (1 if token == "faster" else -1), 0, 2))
            ctx.reply({"type": "ack", "of": "voice", "ok": True, "reason": f"speed {self._speed}"})
            return

        if token == "home":
            self._q_start = ctx.arm.read_joints().copy()
            self._q_goal = ctx.safety.clamp_joints(np.asarray(ctx.config.home_joints_deg, float))
            self._t, self._dur, self._job = 0.0, max(0.5, ctx.config.goto.move_time_s), "home"
            ctx.reply({"type": "ack", "of": "voice", "ok": True, "reason": "home"})
            return

        if token in ("open", "close"):
            self._grip_goal = 0.0 if token == "open" else 100.0
            self._q_seed = ctx.arm.read_joints().copy()
            self._t, self._dur, self._job = 0.0, 1.5, "grip"
            ctx.reply({"type": "ack", "of": "voice", "ok": True, "reason": token})
            return

        if token in _DIRS:
            if not ctx.arm.has_kinematics:
                ctx.reply({"type": "ack", "of": "voice", "ok": False, "reason": "no kinematics"})
                return
            axis, sign = _DIRS[token]
            self._dir = np.zeros(3)
            self._dir[axis] = sign
            q = ctx.arm.read_joints()
            t = ctx.arm.fk(q)
            self._ee = t[:3, 3].copy()
            self._R0 = t[:3, :3].copy()
            self._q_seed = q.copy()
            self._t, self._dur, self._job = 0.0, ctx.config.voice.pulse_ms / 1000.0, "pulse"
            ctx.reply({"type": "ack", "of": "voice", "ok": True, "reason": token})
            return

        ctx.reply({"type": "ack", "of": "voice", "ok": False, "reason": f"unknown '{token}'"})

    def tick(self, dt: float, ctx: Ctx) -> np.ndarray | None:
        if self._job is None:
            return None
        self._t += dt
        done = self._t >= self._dur

        if self._job == "home":
            a = min(1.0, self._t / self._dur)
            a = a * a * (3.0 - 2.0 * a)
            q = self._q_start + (self._q_goal - self._q_start) * a
            if done:
                self._job = None
            return q

        if self._job == "grip":
            a = min(1.0, self._t / self._dur)
            q = self._q_seed.copy()
            g0 = self._q_seed[J["gripper"]]
            q[J["gripper"]] = g0 + (self._grip_goal - g0) * a
            if done:
                self._job = None
            return q

        if self._job == "pulse":
            lin = ctx.config.jog.speed_scale_mps[self._speed]
            self._ee = ctx.safety.clamp_workspace(self._ee + self._dir * lin * dt)
            t_des = np.eye(4)
            t_des[:3, :3] = self._R0
            t_des[:3, 3] = self._ee
            q = ctx.arm.ik(self._q_seed, t_des).astype(float)
            self._q_seed = q.copy()
            if done:
                self._job = None
            return q

        return None

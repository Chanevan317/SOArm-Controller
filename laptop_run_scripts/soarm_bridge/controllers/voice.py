"""
Voice: discrete keywords -> motion primitives. Deliberately small.

  stop                       -> latched e-stop, cancels everything
  home                       -> joint-space move to config.home_joints_deg (one-shot)
  open / close               -> drive the gripper to 0 / 100 (one-shot)
  up/down/left/right/
    forward/back              -> LATCH continuous motion in that direction.
                                Say it once and the arm keeps going; say another
                                direction to change; say "stop" to halt. Auto-stops
                                after voice.latch_timeout_s with no new keyword.
  rotate left / rotate right  -> LATCH continuous wrist_roll the same way.
  faster / slower             -> shift the speed tier (applies to the live latch).

Directional motion needs kinematics. Axis signs are assumptions to verify.
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
        self._job: str | None = None          # None | home | grip | drive | roll
        self._t = 0.0
        self._dur = 0.0
        self._idle = 0.0                       # seconds since the last voice keyword
        self._q_start: np.ndarray | None = None
        self._q_goal: np.ndarray | None = None
        self._ee: np.ndarray | None = None
        self._R0: np.ndarray | None = None
        self._q_seed: np.ndarray | None = None
        self._dir = np.zeros(3)               # latched translation direction (unit)
        self._roll_sign = 0.0                 # latched wrist_roll direction
        self._grip_goal = 0.0
        self._speed = 1

    def on_activate(self, ctx: Ctx) -> None:
        self.reset()
        self._speed = int(np.clip(ctx.config.voice.pulse_speed_index, 0, 2))

    def _latch_timeout(self, ctx: Ctx) -> float:
        return float(getattr(ctx.config.voice, "latch_timeout_s", 10.0))

    def handle(self, msg: dict, ctx: Ctx) -> None:
        if msg.get("type") != "voice":
            return
        token = str(msg.get("token", "")).lower().strip()
        self._idle = 0.0

        if token == "stop":
            self.reset()
            ctx.estop()
            ctx.reply({"type": "ack", "of": "voice", "ok": True, "reason": "stop"})
            return

        if token in ("faster", "slower"):
            self._speed = int(np.clip(self._speed + (1 if token == "faster" else -1), 0, 2))
            ctx.reply({"type": "ack", "of": "voice", "ok": True, "reason": f"speed {self._speed}"})
            return

        # Any motion keyword also lifts a prior "stop" (natural voice flow).
        ctx.resume()

        poses = getattr(ctx.config, "named_poses", {"home": ctx.config.home_joints_deg})
        if token in poses:
            self._q_start = ctx.arm.read_joints().copy()
            self._q_goal = ctx.safety.clamp_joints(np.asarray(poses[token], float))
            self._t, self._dur, self._job = 0.0, max(0.5, ctx.config.goto.move_time_s), "home"
            ctx.reply({"type": "ack", "of": "voice", "ok": True, "reason": token})
            return

        if token in ("open", "close"):
            self._grip_goal = 100.0 if token == "open" else 0.0   # 0 = closed, 100 = open
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
            if self._job != "drive":                       # (re)seed from the live pose
                q = ctx.arm.read_joints()
                t = ctx.arm.fk(q)
                self._ee = t[:3, 3].copy()
                self._R0 = t[:3, :3].copy()
                self._q_seed = q.copy()
            self._job = "drive"
            ctx.reply({"type": "ack", "of": "voice", "ok": True, "reason": token})
            return

        if token in ("rotate left", "rotate right"):
            self._roll_sign = 1.0 if token == "rotate left" else -1.0
            if self._job != "roll":
                self._q_seed = ctx.arm.read_joints().copy()
            self._job = "roll"
            ctx.reply({"type": "ack", "of": "voice", "ok": True, "reason": token})
            return

        ctx.reply({"type": "ack", "of": "voice", "ok": False, "reason": f"unknown '{token}'"})

    def tick(self, dt: float, ctx: Ctx) -> np.ndarray | None:
        if self._job is None:
            return None

        # Dead-man: stop a latched drive if no keyword arrived recently.
        if self._job in ("drive", "roll"):
            self._idle += dt
            if self._idle > self._latch_timeout(ctx):
                self._job = None
                ctx.reply({"type": "ack", "of": "voice", "ok": True, "reason": "latch timeout"})
                return None

        if self._job == "home":
            self._t += dt
            a = min(1.0, self._t / self._dur)
            a = a * a * (3.0 - 2.0 * a)
            q = self._q_start + (self._q_goal - self._q_start) * a
            if self._t >= self._dur:
                self._job = None
            return q

        if self._job == "grip":
            self._t += dt
            a = min(1.0, self._t / self._dur)
            q = self._q_seed.copy()
            g0 = self._q_seed[J["gripper"]]
            q[J["gripper"]] = g0 + (self._grip_goal - g0) * a
            if self._t >= self._dur:
                self._job = None
            return q

        if self._job == "drive":
            lin = ctx.config.jog.speed_scale_mps[self._speed]
            prev_ee = self._ee.copy()
            self._ee = ctx.safety.clamp_workspace(self._ee + self._dir * lin * dt)
            t_des = np.eye(4)
            t_des[:3, :3] = self._R0
            t_des[:3, 3] = self._ee
            q = ctx.arm.ik(self._q_seed, t_des, orientation_weight=0.0).astype(float)
            reached = ctx.arm.fk(q)[:3, 3]
            if np.linalg.norm(reached - self._ee) > 0.010:   # hit the edge — hold
                self._ee = prev_ee
                return self._q_seed
            self._q_seed = q.copy()
            return q

        if self._job == "roll":
            rate = ctx.config.jog.pitch_speed_dps[self._speed]
            q = self._q_seed.copy()
            q[J["wrist_roll"]] = self._q_seed[J["wrist_roll"]] + self._roll_sign * rate * dt
            self._q_seed = q.copy()
            return q

        return None

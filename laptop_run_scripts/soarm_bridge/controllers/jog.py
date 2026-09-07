"""
Jog: joystick velocities -> an integrated end-effector target -> IK.

The phone streams normalised velocities. Each tick we move a virtual EE target
by velocity * speed_scale * dt, clamp it to the workspace box, and solve IK.
Wrist pitch rotates the target frame about its local Y; gripper is integrated
directly as a 0..100 position.

FIRST PASS: axis mapping (phone vx/vy/vz -> base x/y/z) and the local pitch axis
are assumptions to verify on hardware. No filtering yet — raw stick goes straight
in.
"""

from __future__ import annotations

import logging

import numpy as np

from .. import J
from ..safety import Watchdog
from .base import Controller, Ctx

log = logging.getLogger("soarm.jog")


class JogController(Controller):
    name = "jog"

    def __init__(self, watchdog_timeout_s: float):
        self._wd = Watchdog(watchdog_timeout_s)
        self._warned_no_kin = False
        self.reset()

    def reset(self) -> None:
        self.vx = self.vy = self.vz = 0.0
        self.pitch = self.roll = self.grip = 0.0
        self.speed = 1
        self.enabled = False
        self._ee: np.ndarray | None = None       # target EE xyz (m)
        self._R0: np.ndarray | None = None       # seed EE rotation (3x3)
        self._pitch_off = 0.0                    # held wrist_flex offset (deg)
        self._roll_off = 0.0                     # held wrist_roll offset (deg)
        self._grip_pos = 0.0                     # target gripper (0..100)
        self._q_seed: np.ndarray | None = None

    def on_activate(self, ctx: Ctx) -> None:
        self.reset()

    def handle(self, msg: dict, ctx: Ctx) -> None:
        if msg.get("type") != "jog":
            return
        self.vx = float(msg.get("vx", 0.0))
        self.vy = float(msg.get("vy", 0.0))
        self.vz = float(msg.get("vz", 0.0))
        self.pitch = float(msg.get("pitch", 0.0))
        self.roll = float(msg.get("roll", 0.0))
        self.grip = float(msg.get("grip", 0.0))
        self.speed = int(np.clip(msg.get("speed", 1), 0, 2))
        self.enabled = bool(msg.get("enabled", True))
        self._wd.feed()

    def _seed(self, ctx: Ctx) -> None:
        q = ctx.arm.read_joints()
        t = ctx.arm.fk(q)
        self._ee = t[:3, 3].copy()
        self._R0 = t[:3, :3].copy()
        self._pitch_off = 0.0
        self._roll_off = 0.0
        self._grip_pos = float(q[J["gripper"]])
        self._q_seed = q.copy()

    def tick(self, dt: float, ctx: Ctx) -> np.ndarray | None:
        if not ctx.arm.has_kinematics:
            if not self._warned_no_kin:
                ctx.reply({"type": "error", "msg": "jog needs kinematics (no URDF loaded)"})
                self._warned_no_kin = True
            return None

        if not self.enabled or self._wd.stale():
            self._ee = None          # re-seed from the live pose on next enable
            return None

        if self._ee is None or self._q_seed is None or self._R0 is None:
            self._seed(ctx)

        jc = ctx.config.jog
        lin = jc.speed_scale_mps[self.speed]
        # phone axes -> base axes (x forward, y left, z up). Verify signs visually.
        prev_ee = self._ee.copy()
        self._ee = ctx.safety.clamp_workspace(
            self._ee + np.array([self.vx, self.vy, self.vz], float) * lin * dt
        )

        # Position-only IK — a 5-DOF arm can't hold orientation AND track XY,
        # so we let orientation float and drive wrist pitch as a joint offset.
        t_des = np.eye(4)
        t_des[:3, :3] = self._R0
        t_des[:3, 3] = self._ee
        q_ik = ctx.arm.ik(self._q_seed, t_des, orientation_weight=0.0).astype(float)

        # If IK can't actually reach the target, we've hit the edge of the
        # arm's reach. Hold there: keep the last good joints (don't send a
        # folded, self-colliding solution) and stop the target creeping.
        reached = ctx.arm.fk(q_ik)[:3, 3]
        if np.linalg.norm(reached - self._ee) > 0.010:
            self._ee = prev_ee
            q_ik = self._q_seed
        else:
            self._q_seed = q_ik.copy()

        # wrist pitch / roll: held joint offsets, not fed back to IK. Bounded so
        # the wrist can't curl back into the forearm.
        pl = getattr(jc, "pitch_limit_deg", 55.0)
        rl = getattr(jc, "roll_limit_deg", 90.0)
        self._pitch_off = float(
            np.clip(self._pitch_off + jc.pitch_speed_dps[self.speed] * self.pitch * dt, -pl, pl)
        )
        self._roll_off = float(
            np.clip(self._roll_off + jc.pitch_speed_dps[self.speed] * self.roll * dt, -rl, rl)
        )
        # gripper: integrate directly
        self._grip_pos = float(
            np.clip(self._grip_pos + jc.grip_speed_pps[self.speed] * self.grip * dt, 0.0, 100.0)
        )

        q_out = q_ik.copy()
        q_out[J["wrist_flex"]] += self._pitch_off
        q_out[J["wrist_roll"]] += self._roll_off
        q_out[J["gripper"]] = self._grip_pos
        return q_out

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

try:
    from lerobot.utils.rotation import Rotation
except Exception:  # noqa: BLE001
    from scipy.spatial.transform import Rotation  # type: ignore


class JogController(Controller):
    name = "jog"

    def __init__(self, watchdog_timeout_s: float):
        self._wd = Watchdog(watchdog_timeout_s)
        self._warned_no_kin = False
        self.reset()

    def reset(self) -> None:
        self.vx = self.vy = self.vz = 0.0
        self.pitch = self.grip = 0.0
        self.speed = 1
        self.enabled = False
        self._ee: np.ndarray | None = None       # target EE xyz (m)
        self._R0: np.ndarray | None = None       # seed EE rotation (3x3)
        self._pitch_off = 0.0                    # accumulated pitch (rad)
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
        # phone axes -> base axes (VERIFY: sign/order on real hardware)
        self._ee = self._ee + np.array([self.vx, self.vy, self.vz], float) * lin * dt
        self._ee = ctx.safety.clamp_workspace(self._ee)

        # pitch: accumulate a rotation about the seed frame's local Y
        self._pitch_off += np.deg2rad(jc.pitch_speed_dps[self.speed]) * self.pitch * dt
        r_des = self._R0 @ Rotation.from_rotvec([0.0, self._pitch_off, 0.0]).as_matrix()

        t_des = np.eye(4)
        t_des[:3, :3] = r_des
        t_des[:3, 3] = self._ee

        q = ctx.arm.ik(self._q_seed, t_des).astype(float)

        # gripper: integrate directly
        self._grip_pos = float(
            np.clip(self._grip_pos + jc.grip_speed_pps[self.speed] * self.grip * dt, 0.0, 100.0)
        )
        q[J["gripper"]] = self._grip_pos

        self._q_seed = q.copy()
        return q

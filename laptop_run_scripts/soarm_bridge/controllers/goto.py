"""
Go To: a typed Cartesian target -> solve IK once -> interpolate in joint space
over goto.move_time_s with a smoothstep profile. Once there, hold the goal.

Joint-space interpolation (rather than streaming IK) keeps the motion predictable
and avoids mid-path IK surprises for a first pass.
"""

from __future__ import annotations

import logging

import numpy as np

from .. import J
from .base import Controller, Ctx

log = logging.getLogger("soarm.goto")

try:
    from lerobot.utils.rotation import Rotation
except Exception:  # noqa: BLE001
    from scipy.spatial.transform import Rotation  # type: ignore


def _smoothstep(a: float) -> float:
    a = min(1.0, max(0.0, a))
    return a * a * (3.0 - 2.0 * a)


class GotoController(Controller):
    name = "goto"

    def __init__(self) -> None:
        self.reset()

    def reset(self) -> None:
        self._q_start: np.ndarray | None = None
        self._q_goal: np.ndarray | None = None
        self._t = 0.0
        self._dur = 1.0

    def on_activate(self, ctx: Ctx) -> None:
        self.reset()

    def handle(self, msg: dict, ctx: Ctx) -> None:
        if msg.get("type") != "goto":
            return
        if not ctx.arm.has_kinematics:
            ctx.reply({"type": "ack", "of": "goto", "ok": False, "reason": "no kinematics"})
            return

        # mm -> m, clamp to the workspace box
        target = np.array(
            [msg.get("x", 0.0), msg.get("y", 0.0), msg.get("z", 0.0)], float
        ) / 1000.0
        target = ctx.safety.clamp_workspace(target)

        q_now = ctx.arm.read_joints()
        t_cur = ctx.arm.fk(q_now)

        r_des = t_cur[:3, :3]
        pitch = msg.get("pitch")
        if pitch is not None:
            # interpret pitch as absolute rotation about the current frame's local Y
            r_des = t_cur[:3, :3] @ Rotation.from_rotvec(
                [0.0, np.deg2rad(float(pitch)), 0.0]
            ).as_matrix()

        t_des = np.eye(4)
        t_des[:3, :3] = r_des
        t_des[:3, 3] = target

        try:
            q_goal = ctx.arm.ik(q_now, t_des).astype(float)
        except Exception as e:  # noqa: BLE001
            ctx.reply({"type": "ack", "of": "goto", "ok": False, "reason": f"IK failed: {e}"})
            return

        q_goal = ctx.safety.clamp_joints(q_goal)
        # keep the gripper where it is unless we later add a gripper field
        q_goal[J["gripper"]] = q_now[J["gripper"]]

        # sanity: does FK of the solution land near the request?
        reached = ctx.arm.fk(q_goal)[:3, 3]
        err = float(np.linalg.norm(reached - target))
        if err > 0.05:
            ctx.reply(
                {"type": "ack", "of": "goto", "ok": False,
                 "reason": f"target unreachable (off by {err*1000:.0f} mm)"}
            )
            return

        self._q_start = q_now
        self._q_goal = q_goal
        self._t = 0.0
        self._dur = max(0.3, float(ctx.config.goto.move_time_s))
        ctx.reply({"type": "ack", "of": "goto", "ok": True, "reason": ""})

    def tick(self, dt: float, ctx: Ctx) -> np.ndarray | None:
        if self._q_goal is None or self._q_start is None:
            return None
        self._t += dt
        a = _smoothstep(self._t / self._dur)
        return self._q_start + (self._q_goal - self._q_start) * a

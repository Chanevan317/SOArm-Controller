"""
Sequence: release torque to hand-guide, capture joint poses, replay them.

No IK involved — everything is joint space. Replay builds a list of move/hold
segments and walks it. Move duration is set by the largest joint delta divided by
sequence.replay_joint_speed_dps.
"""

from __future__ import annotations

import logging

import numpy as np

from .base import Controller, Ctx

log = logging.getLogger("soarm.seq")


class SequenceController(Controller):
    name = "sequence"

    def __init__(self) -> None:
        self.captured: list[np.ndarray] = []
        self.playing = False
        self._plan: list[dict] = []
        self._i = 0
        self._t = 0.0
        self._released = False

    def reset(self) -> None:
        self.playing = False
        self._plan = []
        self._i = 0
        self._t = 0.0

    def on_deactivate(self, ctx: Ctx) -> None:
        # leaving the mode with motors off would be a surprise — re-enable
        if self._released:
            ctx.arm.set_torque(True)
            self._released = False
        self.reset()

    def _emit(self, ctx: Ctx) -> None:
        ctx.reply({"type": "seq_state", "captured": len(self.captured), "playing": self.playing})

    def handle(self, msg: dict, ctx: Ctx) -> None:
        if msg.get("type") != "seq":
            return
        cmd = msg.get("cmd")

        if cmd == "release":
            ctx.arm.set_torque(False)
            self._released = True
            self.playing = False
            self._emit(ctx)

        elif cmd == "hold":
            ctx.arm.set_torque(True)
            self._released = False
            self._emit(ctx)

        elif cmd == "capture":
            if self._released or ctx.arm.sim:
                self.captured.append(ctx.arm.read_joints().copy())
                self._emit(ctx)
            else:
                ctx.reply({"type": "error", "msg": "release motors before capturing"})

        elif cmd == "clear":
            self.captured.clear()
            self._emit(ctx)

        elif cmd == "play":
            self._build_plan(msg.get("steps", []), ctx)

        elif cmd == "stop":
            self.reset()
            self._emit(ctx)

    def _build_plan(self, steps: list[dict], ctx: Ctx) -> None:
        if not self.captured:
            ctx.reply({"type": "error", "msg": "nothing captured"})
            return
        ctx.arm.set_torque(True)
        self._released = False

        speed = max(1.0, float(ctx.config.sequence.replay_joint_speed_dps))
        q_cur = ctx.arm.read_joints().copy()
        plan: list[dict] = []
        for step in steps:
            if "pose" in step:
                idx = int(step["pose"])
                if not (0 <= idx < len(self.captured)):
                    continue
                q_to = self.captured[idx].copy()
                dur = max(0.3, float(np.max(np.abs(q_to - q_cur))) / speed)
                plan.append({"kind": "move", "a": q_cur.copy(), "b": q_to, "dur": dur})
                q_cur = q_to
            elif "delay_ms" in step:
                plan.append({"kind": "hold", "q": q_cur.copy(),
                             "dur": max(0.0, float(step["delay_ms"]) / 1000.0)})

        if not plan:
            ctx.reply({"type": "error", "msg": "empty sequence"})
            return
        self._plan = plan
        self._i = 0
        self._t = 0.0
        self.playing = True
        self._emit(ctx)

    def tick(self, dt: float, ctx: Ctx) -> np.ndarray | None:
        if not self.playing or self._i >= len(self._plan):
            if self.playing:                 # just finished
                self.playing = False
                self._emit(ctx)
            return None

        self._t += dt
        seg = self._plan[self._i]

        if seg["kind"] == "move":
            a = min(1.0, self._t / seg["dur"])
            a = a * a * (3.0 - 2.0 * a)       # smoothstep
            q = seg["a"] + (seg["b"] - seg["a"]) * a
            if self._t >= seg["dur"]:
                self._i += 1
                self._t = 0.0
            return q

        # hold
        if self._t >= seg["dur"]:
            self._i += 1
            self._t = 0.0
        return seg["q"]

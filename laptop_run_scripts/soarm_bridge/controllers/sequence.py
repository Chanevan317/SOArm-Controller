"""
Sequence / pose mode — the same thing the app's Sequence screen drives, and the
viewer's "author poses" panel.

Two ways to place the arm before capturing a pose:
  * release torque and hand-guide it       (real hardware)
  * send absolute joint targets            {"type":"seq","q":{"elbow_flex":40,...}}
                                            (the viewer's sliders; works in sim)
Then capture / delete / clear / play. Replay is joint-space: a list of move/hold
segments, move duration = largest joint delta / sequence.replay_joint_speed_dps.
No IK.
"""

from __future__ import annotations

import logging

import numpy as np

from .. import J
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
        self._target: np.ndarray | None = None      # slider target (absolute joints)

    def reset(self) -> None:
        self.playing = False
        self._plan = []
        self._i = 0
        self._t = 0.0

    def on_activate(self, ctx: Ctx) -> None:
        # sliders start wherever the arm currently is
        self._target = ctx.arm.read_joints().astype(float)

    def on_deactivate(self, ctx: Ctx) -> None:
        if self._released:                           # don't leave motors off
            ctx.arm.set_torque(True)
            self._released = False
        self._target = None
        self.reset()

    def _emit(self, ctx: Ctx) -> None:
        ctx.reply({
            "type": "seq_state",
            "captured": len(self.captured),
            "playing": self.playing,
            "released": self._released,
            "poses": [[round(float(v), 2) for v in p] for p in self.captured],
        })

    def handle(self, msg: dict, ctx: Ctx) -> None:
        if msg.get("type") != "seq":
            return

        # absolute joint target from the sliders (partial dict merges)
        if "q" in msg and isinstance(msg["q"], dict):
            if self._target is None:
                self._target = ctx.arm.read_joints().astype(float)
            for name, val in msg["q"].items():
                if name in J:
                    self._target[J[name]] = float(val)
            self._target = ctx.safety.clamp_joints(self._target)
            self.playing = False
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
            self._target = ctx.arm.read_joints().astype(float)
            self._emit(ctx)

        elif cmd == "capture":
            q = self._target if (self._target is not None and not self._released) \
                else ctx.arm.read_joints()
            self.captured.append(np.asarray(q, float).copy())
            self._emit(ctx)

        elif cmd == "delete":
            i = int(msg.get("index", -1))
            if 0 <= i < len(self.captured):
                self.captured.pop(i)
            self._emit(ctx)

        elif cmd == "clear":
            self.captured.clear()
            self._emit(ctx)

        elif cmd == "play":
            self._build_plan(msg.get("steps") or [{"pose": i} for i in range(len(self.captured))], ctx)

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
        if self.playing and self._i < len(self._plan):
            self._t += dt
            seg = self._plan[self._i]
            if seg["kind"] == "move":
                a = min(1.0, self._t / seg["dur"])
                a = a * a * (3.0 - 2.0 * a)          # smoothstep
                q = seg["a"] + (seg["b"] - seg["a"]) * a
            else:
                q = seg["q"]
            if self._t >= seg["dur"]:
                self._i += 1
                self._t = 0.0
            return q

        if self.playing:                             # just finished
            self.playing = False
            self._emit(ctx)

        if self._released:                           # hand-guided: send nothing
            return None
        return self._target                          # hold at the slider pose (or None)

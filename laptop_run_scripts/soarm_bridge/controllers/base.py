"""Controller interface and the context object passed to them."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable

import numpy as np

from ..arm import Arm
from ..config import Config
from ..safety import Safety


@dataclass
class Ctx:
    """Handles a controller uses to talk back to the world."""

    arm: Arm
    safety: Safety
    config: Config
    reply: Callable[[dict], None]      # send one frame to the controlling client
    broadcast: Callable[[dict], None]  # send one frame to all clients
    estop: Callable[[], None]          # latch an emergency stop
    resume: Callable[[], None]         # clear a latched stop


class Controller:
    name = "base"

    def on_activate(self, ctx: Ctx) -> None:
        """Called when this becomes the active mode."""

    def on_deactivate(self, ctx: Ctx) -> None:
        """Called when another mode takes over."""

    def handle(self, msg: dict, ctx: Ctx) -> None:
        """Consume one inbound frame addressed to this mode."""

    def tick(self, dt: float, ctx: Ctx) -> np.ndarray | None:
        """
        Produce the desired joint 6-vector (deg) for this control tick, or None
        to hold the last commanded position. Safety clamping/rate-limiting is
        applied by the loop afterwards.
        """
        return None

    def reset(self) -> None:
        """Drop transient state."""

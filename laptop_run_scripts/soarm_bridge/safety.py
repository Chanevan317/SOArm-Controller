"""Clamps and a watchdog. Every joint vector the loop sends passes through here."""

from __future__ import annotations

import time

import numpy as np

from .config import SafetyCfg


class Safety:
    def __init__(self, cfg: SafetyCfg):
        self.jmin = np.asarray(cfg.joint_min_deg, float)
        self.jmax = np.asarray(cfg.joint_max_deg, float)
        self.wmin = np.asarray(cfg.workspace_min_m, float)
        self.wmax = np.asarray(cfg.workspace_max_m, float)
        self.max_step = float(cfg.max_joint_step_deg)
        self.timeout = float(cfg.watchdog_timeout_s)

    # -- clamps --------------------------------------------------------------

    def clamp_joints(self, q: np.ndarray) -> np.ndarray:
        return np.clip(np.asarray(q, float), self.jmin, self.jmax)

    def clamp_workspace(self, xyz: np.ndarray) -> np.ndarray:
        return np.clip(np.asarray(xyz, float), self.wmin, self.wmax)

    def limit_step(self, q_target: np.ndarray, q_prev: np.ndarray) -> np.ndarray:
        """Cap the per-tick change of every joint to max_step degrees."""
        q_target = np.asarray(q_target, float)
        q_prev = np.asarray(q_prev, float)
        delta = np.clip(q_target - q_prev, -self.max_step, self.max_step)
        return q_prev + delta


class Watchdog:
    """Fed by streamed commands (jog). `stale()` once nothing arrives in time."""

    def __init__(self, timeout_s: float):
        self.timeout = timeout_s
        self._last = 0.0

    def feed(self) -> None:
        self._last = time.monotonic()

    def stale(self) -> bool:
        return (time.monotonic() - self._last) > self.timeout

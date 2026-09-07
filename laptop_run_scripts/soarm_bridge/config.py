"""Load config.yaml into typed dataclasses."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
import yaml


@dataclass
class RobotCfg:
    type: str = "so101"          # so100 | so101
    port: str = "/dev/ttyACM0"
    id: str = "soarm"
    use_degrees: bool = True


@dataclass
class KinematicsCfg:
    enabled: bool = True
    urdf_path: str = "./assets/so101_new_calib_nomesh.urdf"
    target_frame: str = "gripper_frame_link"


@dataclass
class ServerCfg:
    host: str = "0.0.0.0"
    port: int = 8765


@dataclass
class ControlCfg:
    fps: int = 30
    telemetry_hz: int = 10


@dataclass
class SafetyCfg:
    joint_min_deg: np.ndarray = field(
        default_factory=lambda: np.array([-110, -100, -100, -100, -180, 0], float)
    )
    joint_max_deg: np.ndarray = field(
        default_factory=lambda: np.array([110, 100, 100, 100, 180, 100], float)
    )
    workspace_min_m: np.ndarray = field(
        default_factory=lambda: np.array([-0.30, -0.30, 0.02], float)
    )
    workspace_max_m: np.ndarray = field(
        default_factory=lambda: np.array([0.32, 0.30, 0.35], float)
    )
    max_joint_step_deg: float = 6.0
    watchdog_timeout_s: float = 0.4


@dataclass
class JogCfg:
    speed_scale_mps: list[float] = field(default_factory=lambda: [0.03, 0.07, 0.13])
    pitch_speed_dps: list[float] = field(default_factory=lambda: [15, 30, 55])
    grip_speed_pps: list[float] = field(default_factory=lambda: [60, 120, 200])


@dataclass
class GotoCfg:
    move_time_s: float = 2.5


@dataclass
class SequenceCfg:
    replay_joint_speed_dps: float = 40.0
    default_delay_ms: int = 500


@dataclass
class VoiceCfg:
    pulse_ms: int = 700
    pulse_speed_index: int = 0


@dataclass
class Config:
    robot: RobotCfg = field(default_factory=RobotCfg)
    kinematics: KinematicsCfg = field(default_factory=KinematicsCfg)
    server: ServerCfg = field(default_factory=ServerCfg)
    control: ControlCfg = field(default_factory=ControlCfg)
    safety: SafetyCfg = field(default_factory=SafetyCfg)
    jog: JogCfg = field(default_factory=JogCfg)
    goto: GotoCfg = field(default_factory=GotoCfg)
    sequence: SequenceCfg = field(default_factory=SequenceCfg)
    voice: VoiceCfg = field(default_factory=VoiceCfg)
    home_joints_deg: np.ndarray = field(
        default_factory=lambda: np.array([0, -90, 90, 0, 0, 50], float)
    )

    @property
    def urdf_path(self) -> Path:
        return Path(self.kinematics.urdf_path).expanduser()


def _merge(dc, data: dict):
    """Shallow-merge a dict onto a dataclass instance, coercing arrays."""
    for key, val in (data or {}).items():
        if not hasattr(dc, key):
            continue
        cur = getattr(dc, key)
        if isinstance(cur, np.ndarray):
            setattr(dc, key, np.asarray(val, float))
        elif hasattr(cur, "__dataclass_fields__"):
            _merge(cur, val)
        else:
            setattr(dc, key, val)
    return dc


def load(path: str | Path | None) -> Config:
    cfg = Config()
    if path is None:
        return cfg
    raw = yaml.safe_load(Path(path).expanduser().read_text()) or {}
    return _merge(cfg, raw)

"""
Hardware wrapper: an SO-100/SO-101 follower plus a placo kinematics model,
behind a small interface the control loop and controllers use.

`sim=True` skips all hardware: joint state is held in memory and `send_joints`
applies instantly. Kinematics still work if the URDF is present, so the whole
pipeline (protocol -> controllers -> IK -> safety) can be exercised with no arm.
"""

from __future__ import annotations

import logging
from pathlib import Path

import numpy as np

from . import MOTORS

log = logging.getLogger("soarm.arm")


class Arm:
    def __init__(
        self,
        robot_type: str = "so101",
        port: str = "/dev/ttyACM0",
        robot_id: str = "soarm",
        use_degrees: bool = True,
        sim: bool = False,
        urdf_path: str | Path | None = None,
        target_frame: str = "gripper",
        home_joints_deg: np.ndarray | None = None,
    ):
        self.robot_type = robot_type
        self.port = port
        self.robot_id = robot_id
        self.use_degrees = use_degrees
        self.sim = sim
        self.target_frame = target_frame

        self._robot = None
        self._kin = None
        self._torque_on = True
        self._sim_q = (
            np.asarray(home_joints_deg, float).copy()
            if home_joints_deg is not None
            else np.array([0, -90, 90, 0, 0, 50], float)
        )

        self._urdf_path = Path(urdf_path).expanduser() if urdf_path else None

    # -- lifecycle --------------------------------------------------------------

    def connect(self) -> None:
        if self._urdf_path and self._urdf_path.is_file():
            try:
                from lerobot.model import RobotKinematics

                self._kin = RobotKinematics(str(self._urdf_path), self.target_frame)
                log.info("kinematics loaded from %s", self._urdf_path)
            except Exception as e:  # noqa: BLE001 - degrade to no-IK, don't crash
                log.warning("kinematics unavailable (%s); jog/goto will refuse", e)
        else:
            log.warning("no URDF at %s; jog/goto will refuse", self._urdf_path)

        if self.sim:
            log.info("SIM mode — no hardware")
            return

        from lerobot.robots.so_follower import SO101FollowerConfig, SO101Follower

        cfg = SO101FollowerConfig(
            port=self.port, id=self.robot_id, use_degrees=self.use_degrees
        )
        self._robot = SO101Follower(cfg)
        self._robot.connect()          # runs calibration prompt if needed
        self._torque_on = True
        log.info("connected to %s on %s", self.robot_type, self.port)

    def disconnect(self) -> None:
        if self._robot is not None:
            try:
                self._robot.disconnect()   # disables torque
            except Exception as e:  # noqa: BLE001
                log.warning("disconnect: %s", e)
        self._robot = None

    # -- capabilities ---------------------------------------------------------

    @property
    def has_kinematics(self) -> bool:
        return self._kin is not None

    # -- state --------------------------------------------------------------

    def read_joints(self) -> np.ndarray:
        """6-vector in the MOTORS order; joints in degrees, gripper 0..100."""
        if self.sim or self._robot is None:
            return self._sim_q.copy()
        obs = self._robot.get_observation()
        return np.array([obs[f"{m}.pos"] for m in MOTORS], float)

    def send_joints(self, q: np.ndarray) -> None:
        q = np.asarray(q, float)
        if self.sim or self._robot is None:
            if self._torque_on:
                self._sim_q = q.copy()
            return
        if not self._torque_on:
            return
        self._robot.send_action({f"{m}.pos": float(q[i]) for i, m in enumerate(MOTORS)})

    def set_torque(self, on: bool) -> None:
        self._torque_on = on
        if self._robot is None:
            return
        if on:
            self._robot.bus.enable_torque()
        else:
            self._robot.bus.disable_torque()

    @property
    def torque_on(self) -> bool:
        return self._torque_on

    # -- kinematics -------------------------------------------------------

    def fk(self, q: np.ndarray) -> np.ndarray:
        """4x4 end-effector pose in metres. Requires kinematics."""
        return self._kin.forward_kinematics(np.asarray(q, float))

    def ik(
        self,
        q_current: np.ndarray,
        t_des: np.ndarray,
        iters: int = 6,
        orientation_weight: float = 0.05,
    ) -> np.ndarray:
        """
        Joint 6-vector (deg) reaching `t_des` (4x4, metres).

        placo's solver takes one Gauss-Newton step per call, so we iterate a few
        times (seeding each from the last result) to converge before returning.
        The SO arm is 5-DOF: position dominates, orientation tracks only softly.
        Gripper value is carried through from `q_current`.
        """
        q = np.asarray(q_current, float)
        for _ in range(max(1, iters)):
            q = self._kin.inverse_kinematics(
                q, t_des, position_weight=1.0, orientation_weight=orientation_weight
            )
        return q

    def ee_xyz(self, q: np.ndarray | None = None) -> np.ndarray:
        q = self.read_joints() if q is None else q
        return self.fk(q)[:3, 3]

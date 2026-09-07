#!/usr/bin/env python
"""
FK/IK sanity check — no hardware.

    python scripts/test_kinematics.py --config config.yaml

Loads the URDF, runs forward kinematics on the home pose, then asks IK to return
to a slightly offset target and reports the residual. If this errors or the
residual is large, the URDF / target_frame in config.yaml is wrong.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np

sys_path = Path(__file__).resolve().parent.parent
import sys

sys.path.insert(0, str(sys_path))

from soarm_bridge import config as cfgmod  # noqa: E402
from soarm_bridge.arm import Arm  # noqa: E402


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--config", default=None)
    args = ap.parse_args()
    cfg = cfgmod.load(args.config)

    arm = Arm(
        sim=True,
        urdf_path=str(cfg.urdf_path),
        target_frame=cfg.kinematics.target_frame,
        home_joints_deg=cfg.home_joints_deg,
    )
    arm.connect()
    if not arm.has_kinematics:
        print("NO kinematics — check kinematics.urdf_path points at a file in assets/")
        return 1

    q0 = np.asarray(cfg.home_joints_deg, float)
    t0 = arm.fk(q0)
    print("home joints (deg):", np.round(q0, 2).tolist())
    print("home EE xyz (m)  :", np.round(t0[:3, 3], 4).tolist())

    # nudge the target 3 cm in +x / -z and solve
    t_des = t0.copy()
    t_des[:3, 3] = t_des[:3, 3] + np.array([0.03, 0.0, -0.03])
    q1 = arm.ik(q0, t_des)
    reached = arm.fk(q1)[:3, 3]
    resid = float(np.linalg.norm(reached - t_des[:3, 3]))
    print("IK target xyz    :", np.round(t_des[:3, 3], 4).tolist())
    print("IK reached xyz   :", np.round(reached, 4).tolist())
    print("IK joints (deg)  :", np.round(q1, 2).tolist())
    print(f"residual         : {resid*1000:.1f} mm  ->  {'OK' if resid < 0.01 else 'CHECK URDF/frame'}")
    return 0 if resid < 0.02 else 2


if __name__ == "__main__":
    raise SystemExit(main())

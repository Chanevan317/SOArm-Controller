#!/usr/bin/env python
"""
Find the arm's serial port. Thin wrapper around LeRobot's own tool.

    python scripts/find_port.py

Unplug the arm when prompted, plug it back in, and note the port that appears.
Put it in config.yaml as robot.port. Equivalent to running `lerobot-find-port`.
"""

from __future__ import annotations

import sys


def main() -> int:
    try:
        from lerobot.scripts.lerobot_find_port import find_port
    except Exception as e:  # noqa: BLE001
        print(f"could not import lerobot's find_port ({e}).")
        print("Run the CLI directly:  lerobot-find-port")
        return 1
    find_port()
    return 0


if __name__ == "__main__":
    sys.exit(main())

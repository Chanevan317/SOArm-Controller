#!/usr/bin/env python
"""
Download the SO-ARM .stl meshes so the viewer shows the real arm.

`run_bridge.py` already does this automatically on first launch — run this only
to fetch them ahead of time, or to retry after a failed download.

    python scripts/fetch_meshes.py            # so101
    python scripts/fetch_meshes.py --model so100

Resumable. Source: github.com/TheRobotStudio/SO-ARM100 (Apache-2.0).
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from soarm_bridge.meshes import ensure_meshes  # noqa: E402

ASSETS = Path(__file__).resolve().parent.parent / "assets"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", choices=["so101", "so100"], default="so101")
    args = ap.parse_args()

    have, total = ensure_meshes(ASSETS, args.model, progress=print)
    if not total:
        return 1
    print(f"\n{have}/{total} meshes present in {ASSETS}/")
    if have == total:
        print("hard-refresh the viewer — you'll see the real arm.")
        print("optional: `git add laptop_run_scripts/assets/*.stl` to vendor them.")
        return 0
    print("some meshes failed — rerun to retry the rest.")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())

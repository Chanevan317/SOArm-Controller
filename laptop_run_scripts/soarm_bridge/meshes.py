"""
Fetch the SO-ARM .stl meshes the viewer needs, into assets/ (flat, by basename).

Source: github.com/TheRobotStudio/SO-ARM100  (Apache-2.0), branch main,
Simulation/<MODEL>/assets/. Verified layout as of 2026-09.

Called automatically by run_bridge.py on first launch; also exposed via
scripts/fetch_meshes.py.
"""

from __future__ import annotations

import logging
import re
import urllib.request
from pathlib import Path

log = logging.getLogger("soarm.meshes")

RAW = "https://raw.githubusercontent.com/TheRobotStudio/SO-ARM100/main/Simulation"
_MODEL_DIR = {"so101": "SO101", "so100": "SO100"}


def _get(url: str, timeout: int = 60) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "soarm-bridge"})
    with urllib.request.urlopen(req, timeout=timeout) as r:  # noqa: S310
        return r.read()


def mesh_refs(urdf_path: Path) -> list[str]:
    """Basenames of every mesh the URDF references."""
    txt = urdf_path.read_text()
    refs = re.findall(r'filename\s*=\s*["\']([^"\']+)["\']', txt)
    return sorted({r.split("://", 1)[-1].split("/")[-1] for r in refs})


def ensure_meshes(
    assets_dir: Path,
    model: str = "so101",
    *,
    progress=None,
) -> tuple[int, int]:
    """
    Make sure every mesh referenced by <model>'s URDF exists in assets_dir.
    Returns (present, total). Never raises — a download failure just leaves that
    file missing (the viewer falls back to a stick figure).

    `progress` is an optional callback(str) for line-by-line status.
    """
    def say(msg: str) -> None:
        (progress or log.info)(msg)

    folder = _MODEL_DIR.get(model, "SO101")
    urdf = assets_dir / (f"{model}_new_calib.urdf" if model == "so101" else "so100.urdf")
    if not urdf.is_file():
        say(f"no URDF at {urdf}; cannot fetch meshes")
        return (0, 0)

    names = mesh_refs(urdf)
    total = len(names)
    missing = [n for n in names if not (assets_dir / n).exists() or (assets_dir / n).stat().st_size == 0]
    if not missing:
        return (total, total)

    base = f"{RAW}/{folder}/assets"
    say(f"fetching {len(missing)} mesh(es) into {assets_dir}/ (one-time, ~1-2 min)")
    have = total - len(missing)
    for i, name in enumerate(missing, 1):
        for attempt in (1, 2, 3):
            try:
                data = _get(f"{base}/{name}")
                (assets_dir / name).write_bytes(data)
                have += 1
                say(f"  [{i}/{len(missing)}] {name}  ({len(data) // 1024} KB)")
                break
            except Exception as e:  # noqa: BLE001
                if attempt == 3:
                    say(f"  [{i}/{len(missing)}] FAILED {name}: {e}  (rerun to retry)")
                else:
                    say(f"  [{i}/{len(missing)}] {name}: {e} — retry {attempt + 1}/3")
    return (have, total)

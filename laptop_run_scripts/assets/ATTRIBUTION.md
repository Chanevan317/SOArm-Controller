# assets/ — vendored robot model

## `so101_new_calib.urdf`

The SO-101 arm description, copied verbatim from **TheRobotStudio / SO-ARM100**:
`Simulation/SO101/so101_new_calib.urdf`
<https://github.com/TheRobotStudio/SO-ARM100>

Licensed **Apache License 2.0** (see the upstream repo). Vendored here so the
bridge has no runtime download. Not modified.

## `so101_new_calib_nomesh.urdf`

Derived from the file above by stripping every `<visual>` and `<collision>`
block, leaving only the kinematic tree. That is all `placo` needs for IK, and it
loads with no mesh files present. **This is the file the bridge and the 3D viewer
use** (`kinematics.urdf_path` in `config.yaml`).

Regenerate it after updating the source URDF:

```python
import re, pathlib
s = pathlib.Path("so101_new_calib.urdf").read_text()
s = re.sub(r"<visual>.*?</visual>", "", s, flags=re.S)
s = re.sub(r"<collision>.*?</collision>", "", s, flags=re.S)
pathlib.Path("so101_new_calib_nomesh.urdf").write_text(s)
```

## Meshes (`*.stl`)

Also from **TheRobotStudio / SO-ARM100** (`Simulation/SO101/assets/`, Apache-2.0),
stored flat here by `scripts/fetch_meshes.py`. Used only by the 3D viewer — IK
never touches them. If they're absent the viewer falls back to a stick figure.

```bash
python scripts/fetch_meshes.py          # download them, then git add assets/*.stl
```

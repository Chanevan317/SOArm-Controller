# Running the laptop bridge

The bridge (`laptop_run_scripts/`) is the Python program that receives commands
from the phone, works out joint angles with [LeRobot](https://huggingface.co/docs/lerobot)
kinematics, and drives the SO‑100 / SO‑101 over USB. It also serves a 3‑D viewer
web page so you can watch the arm move.

For how the phone reaches it, see [connecting.md](connecting.md).

---

## One‑time setup

Needs Python 3.10+ and a virtual env with `lerobot`, `placo`, `numpy`,
`websockets`, `pyyaml`:

```bash
uv venv --python 3.12 ~/.venvs/lerobot
source ~/.venvs/lerobot/bin/activate
uv pip install --torch-backend cpu "lerobot[feetech,kinematics]"
uv pip install websockets pyyaml
```

(Plain `pip` works too; `uv` is just faster.)

The arm model (URDF) is committed in `laptop_run_scripts/assets/`, so there's
nothing to download for kinematics. The viewer's 3‑D meshes download on first run
unless you pass `--no-fetch`.

---

## The three ways to run it

Always `source ~/.venvs/lerobot/bin/activate` first, then from
`laptop_run_scripts/`:

```bash
# 1. Self‑driving demo — no arm, no phone. A fake phone cycles it through
#    jog / go‑to / voice so you can see it work in the viewer.
python run_bridge.py --demo

# 2. Simulator + the real phone app. The arm is virtual; the app drives it;
#    the viewer mirrors it. This is the everyday one.
python run_bridge.py

# 3. Real hardware + the real phone app.
python run_bridge.py --target hardware --config config.yaml
```

Each one starts:

- the WebSocket bridge on **:8765** (what the phone connects to),
- the discovery responder on **udp/8766** (so the phone finds it),
- the viewer on **:8080**, which opens in your browser.

| flag | effect |
| --- | --- |
| `--target sim` (default) / `hardware` | virtual arm vs. the real one |
| `--demo` | spawn a looping fake phone that drives the arm |
| `--config config.yaml` | load settings (ports, limits, poses) |
| `--no-viewer` / `--no-open` | don't serve the web page / don't open the browser |
| `--no-fetch` | skip the one‑time mesh download (viewer shows a stick figure) |
| `--robot so100\|so101`, `--port /dev/ttyACM0` | override the config |

---

## Configuration

Copy the example and edit:

```bash
cp config.example.yaml config.yaml
```

Key sections:

- `robot` — `type` (`so100` / `so101`), `port` (the USB device), calibration `id`.
- `server` — WebSocket host/port (default `0.0.0.0:8765`).
- `safety` — joint limits and the end‑effector box the arm is allowed into. The
  defaults match the SO‑101 URDF and are deliberately conservative so jog can't
  fold the arm into itself.
- `named_poses` — the poses Voice can move to (`home`, `rest`, `ready`,
  `extend`, …). Add your own.
- `voice`, `jog`, `goto` — speeds and feel.

---

## The 3‑D viewer

Opens at `http://localhost:8080/viewer/`. It mirrors the arm live from the
bridge's telemetry — whatever the phone (or the demo) does, you see here.

**Pose mode** (button in the viewer): puts the bridge into Sequence mode and
gives you six joint sliders. Drag to a pose → **Capture** → repeat → **Play**
interpolates through them. This is how you build pose sequences on the simulator
without the arm. Don't use it while `--demo` is running — the demo bot fights the
mode.

---

## First run against the real arm

```bash
python scripts/find_port.py            # find the arm's /dev/tty… port
lerobot-calibrate --robot.type=so101_follower --robot.port=/dev/ttyACM0 --robot.id=soarm
cp config.example.yaml config.yaml     # set robot.port, robot.type
python run_bridge.py --target hardware --config config.yaml
```

Before trusting it:

- Run `lerobot-find-joint-limits` and put real numbers in `safety:`.
- First live moves: arm on a box, clear of obstacles, low speed, hand on Ctrl‑C
  (it disables torque on exit).
- Expect an axis sign to be wrong somewhere on the first try — test small.

---

## Testing without hardware

`scripts/fake_phone.py` sends the exact frames the app sends. With `--loop` it
cycles forever (that's what `--demo` runs); without it, a one‑shot pass that also
covers edge cases (dropped packets, rapid mode switches, e‑stop + resume):

```bash
python run_bridge.py                 # terminal 1
python scripts/fake_phone.py         # terminal 2
```

---

## Folder layout

```
laptop_run_scripts/
├── run_bridge.py            entrypoint
├── config.example.yaml      copy to config.yaml
├── assets/                  SO‑101 URDF (vendored) + meshes (fetched)
├── soarm_bridge/
│   ├── protocol.py          the phone↔bridge message catalogue (in the docstring)
│   ├── loop.py              30 Hz control loop, mode switching, e‑stop
│   ├── server.py            WebSocket server
│   ├── discovery.py         UDP responder for auto‑discovery
│   ├── arm.py               SO‑10x follower + kinematics (+ sim mode)
│   ├── safety.py            joint / workspace clamps, watchdog
│   └── controllers/         jog · goto · sequence(=pose) · voice
├── viewer/index.html        the 3‑D web viewer
└── scripts/                 fetch_meshes · fake_phone · find_port · test_kinematics
```

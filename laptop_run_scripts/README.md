# laptop_run_scripts — SOArm bridge

The laptop side of **SOArm Controller**. Receives intent from the phone app over a
WebSocket, turns it into joint commands for an SO‑100 / SO‑101 arm using
[LeRobot](https://huggingface.co/docs/lerobot) for inverse kinematics and the
Feetech servo bus, and streams telemetry back.

This is a **first pass** — it runs all four modes end to end, but the limits,
speeds and gains in `config.example.yaml` are placeholders to be tuned against the
real arm. Nothing here chases perfection yet.

```
phone app  ──WebSocket(JSON)──▶  run_bridge.py
                                   ├─ server.py      accept clients, decode frames
                                   ├─ loop.py        30 Hz control loop + safety
                                   ├─ controllers/   jog · goto · sequence · voice
                                   └─ arm.py         SO10xFollower + RobotKinematics
                                                        │ USB serial
                                                        ▼
                                                     SO‑100 arm
```

## Layout

```
laptop_run_scripts/
├── run_bridge.py            entrypoint
├── config.example.yaml      copy to config.yaml and edit
├── requirements.txt         extra deps (most come from the lerobot env)
├── assets/
│   ├── so101_new_calib.urdf         vendored from TheRobotStudio (Apache-2.0)
│   ├── so101_new_calib_nomesh.urdf  meshes stripped — used by IK + the viewer
│   └── ATTRIBUTION.md
├── soarm_bridge/
│   ├── config.py            YAML -> dataclasses
│   ├── protocol.py          the wire contract (message catalogue in the docstring)
│   ├── arm.py               hardware wrapper, incl. sim mode (no arm needed)
│   ├── meshes.py            auto-downloads the viewer's .stl meshes on first run
│   ├── safety.py            joint/workspace clamps, per-tick rate limit, watchdog
│   ├── server.py            websockets server
│   ├── loop.py              control loop, mode switching, e-stop
│   └── controllers/
│       ├── base.py          Controller interface
│       ├── jog.py           velocity -> integrated EE target -> IK
│       ├── goto.py          target pose -> joint-space interpolation
│       ├── sequence.py      pose mode: sliders / hand-teach -> capture -> replay
│       └── voice.py         keyword -> timed motion primitives / e-stop / home
├── viewer/
│   └── index.html          browser 3D view — mirrors the bridge's telemetry onto the URDF
└── scripts/
    ├── fetch_meshes.py      pre-fetch / retry those meshes manually
    ├── test_kinematics.py   FK/IK sanity check, no hardware
    ├── fake_phone.py        emit the frames the app will send — scripted test scenarios
    ├── serve_viewer.py      standalone HTTP server for the viewer (bridge serves it too)
    └── find_port.py         wrapper around `lerobot-find-port`
```

## Run

Uses the global `~/.venvs/lerobot` env (`lerobot`, `placo`, `numpy`, `websockets`,
`pyyaml` — all already there). `source ~/.venvs/lerobot/bin/activate` first.

There are **three** you'll actually use:

```bash
# 1. self-driving demo — sim, full 3D model, driven by a looping fake phone
python run_bridge.py --demo

# 2. sim + the real Android app  (app connects to ws://<laptop-ip>:8765)
python run_bridge.py

# 3. real hardware + the real Android app
python run_bridge.py --target hardware --config config.yaml
```

All three start the WebSocket bridge (`:8765`) and the 3D viewer web page
(`:8080`, opens in the browser). `--demo` additionally spawns
`scripts/fake_phone.py --loop` — a real WebSocket client that cycles the arm
through jog / go-to / voice, so you see it move without the app.

On the **first** run the viewer's arm meshes (~13 `.stl`, ~1–2 min on a slow
link) download into `assets/` automatically; later runs skip them. Then
`git add assets/*.stl` to vendor them so it never downloads again. `--no-fetch`
skips the download (viewer shows a stick figure instead).

| other flags | |
| --- | --- |
| `--no-viewer` / `--no-open` | skip the web page / skip opening the browser |
| `--no-fetch` | don't auto-download meshes |
| `--robot so100\|so101`, `--port` | override `config.yaml` |

### Building pose sequences in the browser

The viewer's **pose mode** button puts the bridge into `sequence` mode — the same
mode the app's Sequence screen drives. Six joint sliders move the arm directly in
the sim; drag to a pose → **Capture** → repeat → **Play** interpolates through
them; **Clear** / per-step delete to edit.

Use it with launch #2 (`python run_bridge.py`, sim + app): navigate the app to
its Sequence screen for the UX, and — since the app has no transport yet — click
**pose mode** in the viewer to actually put the bridge there and capture with the
sliders. Not with `--demo` running (its bot fights the mode switch).

### Real-arm first time

```bash
python scripts/find_port.py
lerobot-calibrate --robot.type=so101_follower --robot.port=/dev/ttyACM0 --robot.id=soarm
cp config.example.yaml config.yaml     # edit robot.port, robot.type, safety limits
python run_bridge.py --target hardware --config config.yaml
```

## More testing (optional)

`scripts/fake_phone.py` (without `--loop`) runs a one-shot pass that also covers
the edges — enable/disable re-seed, a watchdog gap, partial frames, rapid mode
flips, e-stop + resume:

```bash
python run_bridge.py                       # terminal 1
python scripts/fake_phone.py               # terminal 2
```

`scripts/serve_viewer.py` serves the viewer standalone if you don't want the
bridge to. Verified: all four modes drive the sim end to end with no errors.

## Before pointing it at anything

- Run `lerobot-find-joint-limits` and paste real numbers into `safety:`.
- First live run: arm on a box, clear of obstacles, `control.fps` low, be ready to
  kill it (Ctrl‑C disables torque on the way out).
- Expect an axis sign or a unit to be wrong on the first Go To — verify with small
  targets before trusting it.

## Wire protocol

See the module docstring in `soarm_bridge/protocol.py` for the full message
catalogue. It is the contract the Android app must implement; the app side is not
wired yet.

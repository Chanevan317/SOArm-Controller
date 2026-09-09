# SOArm Controller

Control an **SO‑100 / SO‑101** robot arm from an Android phone.

The phone is the interface. A laptop runs a small Python **bridge** that turns the
phone's commands into motor moves for the arm over USB. The phone reaches the
laptop over the **same Wi‑Fi** or over a **USB cable** — and finds it on its own,
no IP address to type.

```
┌──────────────┐  Wi‑Fi or USB (WebSocket)  ┌────────────────────────┐  USB   ┌─────────┐
│  Android app │ ─────────────────────────▶ │  Laptop bridge         │ ─────▶ │ SO‑100  │
│  (this repo) │   jog / target / pose      │  Python + LeRobot      │Feetech │  / 101  │
│              │   / voice                  │  (IK, safety limits)   │  bus   │  arm    │
│              │ ◀───────────────────────── │                        │        │         │
└──────────────┘   live arm position        └────────────────────────┘        └─────────┘
```

The arm is the standard low‑cost one from
[TheRobotStudio](https://github.com/TheRobotStudio/SO-ARM100): 5 joints + a
gripper, Feetech STS3215 servos. The laptop side is a thin layer over
[LeRobot](https://huggingface.co/docs/lerobot)'s kinematics — the novel part is
the app.

---

## Control modes

Four tabs. Live arm position comes back to the app on all of them.

| Mode | What it does |
| --- | --- |
| **Jog** | Analog joystick for the gripper's X / Y, a slider for height, hold‑bars for wrist **pitch** and **roll** and the gripper, a pinned **STOP**. Rate control — push further to go faster. Holds at the edge of the arm's reach instead of straining. |
| **Go To** | Type an X / Y / Z target (mm). Shows the reachable range and the live position; **Use current** fills it in. Closes the gripper on arrival. Targets save as presets. |
| **Sequence / pose** | Place the arm — joint sliders in the browser viewer, or hand‑guide it on real hardware — **Capture**, repeat, **Play**. Sequences save and reload. |
| **Voice** | Offline keyword recognition (Vosk). Directions **latch** ("right" keeps going until "stop" or another direction). Named poses: `home`, `rest`, `ready`, `extend`. No cloud. |

First launch shows a welcome page, a permissions step, and a splash screen.
System / Light / Dark theme.

---

## Status

| Part | State |
| --- | --- |
| App UI — onboarding, theming, all four screens | ✅ |
| App ↔ bridge transport (WebSocket) + auto‑discovery | ✅ |
| Bridge — control loop, IK, safety limits, e‑stop, sim mode | ✅ |
| 3‑D viewer that mirrors the arm live | ✅ |
| Voice recognition (Vosk, on‑device) | ✅ |
| Tuned against real hardware | ⚠️ first pass — limits and feel in `config.yaml` are conservative placeholders |

---

## Quick start

**1. Set up the laptop's Python env** (once):

```bash
# with uv (fast):
uv venv --python 3.12 ~/.venvs/soarm
source ~/.venvs/soarm/bin/activate
uv pip install --torch-backend cpu "lerobot[feetech,kinematics]" websockets pyyaml

# or with plain venv + pip:
python3 -m venv ~/.venvs/soarm
source ~/.venvs/soarm/bin/activate
pip install torch --index-url https://download.pytorch.org/whl/cpu
pip install "lerobot[feetech,kinematics]" websockets pyyaml
```

**2. Run the bridge** (laptop):

```bash
source ~/.venvs/soarm/bin/activate
cd laptop_run_scripts
python run_bridge.py --demo        # see it move with no arm and no phone
```

**3. Get the app.** Install a ready‑made APK from
[Releases](../../releases), or build it yourself —
[docs/building-the-app.md](docs/building-the-app.md).

**4. Connect.** Put phone and laptop on the same Wi‑Fi (or plug in a USB cable),
open the app — it finds the bridge by itself. If it doesn't, it's almost always
the laptop firewall — see [docs/connecting.md](docs/connecting.md).

Then run `python run_bridge.py` (no `--demo`) and drive the arm from the phone.

---

## Docs

- **[Running the bridge](docs/running-the-bridge.md)** — env setup, the run
  modes, config, the viewer, first run against the real arm.
- **[Connecting the phone](docs/connecting.md)** — auto‑discovery, USB cable,
  **firewall**, Wi‑Fi options, Android 16, troubleshooting checklist.
- **[Building the app](docs/building-the-app.md)** — Gradle / Android Studio,
  install, permissions.
- The phone↔bridge message format is documented in the docstring at the top of
  `laptop_run_scripts/soarm_bridge/protocol.py`.

---

## Tech

- **App:** Kotlin + Jetpack Compose, Material 3, single activity. `minSdk 24`,
  target/compile SDK 37. OkHttp for the WebSocket, `org.json` for framing, Vosk
  for offline speech (model downloaded on first use). Local data is plain JSON
  files. arm64‑v8a APK, ~30 MB.
- **Bridge:** Python, `lerobot[feetech,kinematics]` + `websockets`. 30 Hz control
  loop with joint / workspace clamps and a packet watchdog. `placo` for IK. Runs
  fully in **sim** with no hardware.
- SO‑101 URDF is vendored (`laptop_run_scripts/assets/`, Apache‑2.0).

---

## Credits

- **SO‑ARM100** hardware & URDF — [TheRobotStudio](https://github.com/TheRobotStudio/SO-ARM100) (Apache‑2.0)
- **LeRobot** — [Hugging Face](https://github.com/huggingface/lerobot)
- **Vosk** offline speech — [Alpha Cephei](https://alphacephei.com/vosk/)

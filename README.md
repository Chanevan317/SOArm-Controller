# SOArm Controller

An Android app for controlling the **SO‑100 / SO‑101** robot arm from a phone.

The phone is the interface; a laptop on the same Wi‑Fi does the actual driving. You
pick a control mode on the phone, the phone streams intent to the laptop, and a small
Python bridge turns that into motor commands for the arm over USB.

```
┌──────────────┐   Wi‑Fi (UDP / WebSocket)   ┌───────────────────────┐   USB serial   ┌─────────┐
│  Android app │ ─────────────────────────▶ │  Laptop bridge         │ ─────────────▶ │ SO‑100  │
│  (this repo) │   end‑effector targets,    │  Python + LeRobot      │   Feetech bus  │  arm    │
│              │   poses, voice commands    │  (inverse kinematics,  │                │         │
│              │ ◀───────────────────────── │   safety limits)       │                │         │
└──────────────┘   status / telemetry       └───────────────────────┘                └─────────┘
```

The arm has **5 joints plus a gripper** (base, shoulder, elbow, wrist pitch, wrist
roll), driven by Feetech STS3215 serial‑bus servos. It is the standard low‑cost arm
from [TheRobotStudio](https://github.com/TheRobotStudio/SO-ARM100), designed to work
with [Hugging Face LeRobot](https://huggingface.co/docs/lerobot). The laptop side of
this project is a thin layer on top of LeRobot's existing kinematics and teleop
pipeline — the novel part is the phone app.

---

## Control modes

Four tabs in a floating bottom navigation bar. Every mode is fully usable without a
connection — the controls just don't send anything yet. Actions that would read live
data from the arm (saving a target, capturing a pose, adding a delay) pop a short
"not connected" warning first.

| Mode | What it does |
| --- | --- |
| **Jog** | One analog joystick for the gripper's X / Y, a vertical slider for height (Z), split bars for wrist pitch and the gripper, and a pinned **STOP**. Rate control: push further to move faster; release and it re‑centres. |
| **Go To** | Type an X / Y / Z target (mm, robot base frame) and optional wrist angles; the arm plans a path and moves there. Targets can be **saved as named presets**. |
| **Sequence** | Release the motors, hand‑guide the arm to a pose, capture it, repeat. Insert delays between steps. Play back with joint‑space interpolation. Sequences can be **saved** and reloaded. |
| **Voice** | Offline keyword recognition (Vosk). A fixed ~17‑word grammar (`stop`, `home`, `open`, `go to one`, …) triggers the primitives of the other modes. No cloud, no natural language. |

Around the modes: a one‑time welcome screen on first launch, a splash screen on cold
start, a System / Light / Dark theme picker, an About screen, and a connection sheet
for the laptop's `host:port`.

---

## Project status

This repo currently contains a **working UI plus the on‑device voice engine**. The
network transport and all arm‑control logic are intentionally stubbed until the
hardware bring‑up and an end‑to‑end validation run against a real SO‑100 fix the
on‑wire protocol.

| Area | State |
| --- | --- |
| App shell — onboarding, splash, theming, navigation, all four screens | ✅ built |
| Voice recognition (Vosk, on‑device) | ✅ functional — recognises commands; does **not** send them yet |
| Local persistence (Go To presets, sequences) | ✅ functional — JSON files in app storage |
| Connection sheet (host / port entry, status) | ⚠️ UI only — simulates the handshake |
| Streaming joystick / pose / voice intent over the network | ⛔ not wired |
| Inverse kinematics, motion planning, safety watchdog | ⛔ laptop side, not started |

---

## How it will connect

- **Network:** phone and laptop on the same Wi‑Fi (a real 5 GHz router preferred; a
  laptop‑hosted hotspot as fallback). The app takes a `host:port` and will stream
  small, timestamped packets — an end‑effector twist (linear + angular velocity) for
  Jog, a target pose for Go To, joint vectors for Sequence, discrete tokens for Voice.
- **Laptop bridge:** a Python process using `lerobot[phone,kinematics]`. It receives
  the packets, low‑pass filters them, transforms into the robot base frame, clamps to
  a safe workspace, solves inverse kinematics (Placo), and streams joint targets to
  the servos at ~30–60 Hz. A watchdog stops the arm on packet loss.
- Two integration routes are open: reuse LeRobot's existing phone‑teleop example
  (`examples/phone_to_so100/`) by matching its WebSocket message shape, or write a
  ~50‑line custom LeRobot `Teleoperator` that consumes this app's own packet format
  and feeds the same processor pipeline.

---

## Tech stack

- **Kotlin + Jetpack Compose**, Material 3. Single activity, no fragments, no XML UI.
- **minSdk 24**, target/compile SDK 37. AGP 9.3.2 with its built‑in Kotlin support
  (no separate `kotlin-android` plugin), Gradle 9.5, Kotlin 2.2.10, Compose BOM
  2026.02.
- **[Vosk](https://alphacephei.com/vosk/)** (`com.alphacephei:vosk-android`) for
  offline speech. The ~40 MB `vosk-model-small-en-us` model is **not bundled** — it
  downloads once on first use into app storage.
- **`androidx.core:core-splashscreen`** for the cold‑start splash.
- **Persistence:** plain JSON files in `filesDir` behind repository classes that
  expose Kotlin `StateFlow`s, plus a small `SharedPreferences` store for the theme
  choice and the onboarding flag. (Room was avoided because AGP 9's built‑in Kotlin
  makes the KSP annotation processor it needs awkward to add; swapping the repos for
  Room later touches no UI code.)
- No dependency‑injection framework, no navigation library — state is hoisted and
  passed explicitly; the four modes switch on a `when`.
- The build ships **arm64‑v8a only** (`ndk { abiFilters += "arm64-v8a" }`) to keep
  the APK small; drop that block to package every ABI.

### Design language

Minimalist, drawing from ColorOS 16 system apps. Orange accent (`#FF6A2C`, echoing
the SO‑100's printed plastic). Light mode on `#F5F5F5`; dark mode is **true black**
(`#000000`) with `#151515` surfaces. Theme is **System / Light / Dark**, chosen from
the top‑bar overflow menu or the About screen and remembered across launches.

---

## Repository layout

```
app/src/main/
├── AndroidManifest.xml               RECORD_AUDIO, INTERNET, ACCESS_NETWORK_STATE
├── res/
│   ├── drawable-nodpi/so100.webp     welcome / about hero image
│   ├── drawable-nodpi/so100_logo.webp  app‑icon artwork
│   ├── mipmap-*/                     launcher icon (raster + adaptive)
│   └── values/themes.xml             app theme + splash theme
└── java/com/example/soarmcontroller/
    ├── MainActivity.kt               entry point, splash, theme, onboarding gate
    ├── data/
    │   ├── model/Models.kt           GoToPreset, SeqStep, SequenceRecord
    │   ├── JsonFileStore.kt          atomic JSON read/write
    │   ├── PresetRepository.kt       Go To presets   -> goto_presets.json
    │   ├── SequenceRepository.kt     taught sequences -> sequences.json
    │   ├── Settings.kt               SharedPreferences: theme + onboarded flag
    │   └── AppStore.kt               repository container
    ├── voice/
    │   ├── VoiceModel.kt             one‑time model download + unpack
    │   └── VoiceRecognizer.kt        Vosk wrapper, grammar‑constrained, StateFlow
    └── ui/
        ├── SoArmApp.kt               scaffold: top bar + nav + animated About overlay
        ├── AppInfo.kt                REPO_URL and other static metadata
        ├── theme/                    Color, Type, Shape, Theme
        ├── navigation/Destination.kt the four tabs
        ├── connection/              ConnectionController + bottom sheet
        ├── components/              Joystick, VerticalJog, SplitAction, AxisReadout,
        │                            FloatingNavBar, SoArmTopBar, ConnectionPill,
        │                            ExpandableCard, LevelBars, GuidelinesDialog,
        │                            NameDialog, NotConnectedDialog
        └── screens/                 Welcome, Jog, GoTo, Sequence, Voice, About,
                                     ModeScaffold
```

---

## Building & running

Requires the Android SDK and a JDK 17+ (the app builds with JDK 25 from Android
Studio's bundled runtime).

```bash
# build the debug APK
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleDebug

# install to a connected device and launch
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.soarmcontroller/.MainActivity

# or, build + install in one step
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:installDebug
```

Or open the project in Android Studio and press Run. The debug APK is ~28 MB
(arm64‑only); the ~40 MB speech model is fetched at runtime, not packaged.

### Laptop side (for later)

```bash
uv venv --python 3.12 ~/.venvs/lerobot
source ~/.venvs/lerobot/bin/activate
uv pip install --torch-backend cpu "lerobot[phone,kinematics]"
```

Then bring up the arm with `lerobot-find-port` → `lerobot-setup-motors` →
`lerobot-calibrate`, and validate the concept with LeRobot's own phone‑teleop
example before wiring this app's transport.

---

## Roadmap

1. Hardware bring‑up: calibrate the SO‑100, confirm keyboard/gamepad end‑effector
   control works through LeRobot.
2. End‑to‑end validation with LeRobot's phone teleop over the target Wi‑Fi — measure
   latency, tune the control feel.
3. Wire this app's network transport (start with the Jog twist packet).
4. Fill in Go To motion planning, Sequence record/replay, and map Voice tokens to
   primitives — all through the LeRobot safety pipeline.
5. Optional later: an AR / spatial‑tracking mode (ARCore) as a second input source
   producing the same packets.

---

## Credits

- **SO‑ARM100** hardware — [TheRobotStudio](https://github.com/TheRobotStudio/SO-ARM100)
- **LeRobot** robot‑learning stack — [Hugging Face](https://github.com/huggingface/lerobot)
- **Vosk** offline speech recognition — [Alpha Cephei](https://alphacephei.com/vosk/)

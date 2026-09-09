# Building & installing the Android app

Don't want to build? Grab a prebuilt APK from
[Releases](https://github.com/Chanevan317/SOArm-Controller/releases) — the
`*-universal.apk` there runs on any device (Android 7.0+). Enable "install
unknown apps" on the phone, open it, done. You still need the laptop bridge
running ([running-the-bridge.md](running-the-bridge.md)).

## Requirements

- Android SDK (via Android Studio or `sdkmanager`), API 37 platform.
- JDK 17+. The project builds fine with the JDK bundled in Android Studio
  (`/opt/android-studio/jbr` on Linux).
- A phone on **Android 8+** (`minSdk 24`), USB debugging on for `adb` install.

## Build & install

From the repo root:

```bash
# build the debug APK
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleDebug
#   -> app/build/outputs/apk/debug/app-debug.apk   (~30 MB)

# build + install onto a connected phone
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:installDebug

# or install an existing APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or just open the project in Android Studio and press **Run**.

By default the APK ships **arm64‑v8a only** (~30 MB) — every modern phone. For a
build that runs on *anything* (arm32, x86, x86_64 too), add `-PuniversalApk`:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleDebug -PuniversalApk
#   -> ~60 MB, all ABIs — this is what the GitHub Releases APK is built with
```

The ~40 MB offline speech model is **not** in the APK either way — it downloads
once, on first use of Voice mode.

## Permissions the app asks for

A one‑time screen after the welcome page requests:

- **Microphone** — only for Voice mode; recognition is on‑device, nothing is
  uploaded.
- **Local network / Nearby Wi‑Fi** — to find the laptop bridge and connect.
  Android 16 also shows its own local‑network prompt the first time you connect;
  allow it. See [connecting.md](connecting.md#android-16--local-network-permission).

Anything skipped there is asked again when it's actually needed.

## After installing

Point it at the bridge — but you don't type an IP. Start
`python run_bridge.py` on the laptop, put both on the same Wi‑Fi, open the app.
Details and troubleshooting in [connecting.md](connecting.md).

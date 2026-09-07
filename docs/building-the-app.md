# Building & installing the Android app

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

The APK ships **arm64‑v8a only** to stay small (drop the `ndk { abiFilters }`
block in `app/build.gradle.kts` to package every ABI). The ~40 MB offline speech
model is **not** in the APK — it downloads once, on first use of Voice mode.

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

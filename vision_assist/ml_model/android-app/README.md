# VisionAssist Android Module

This module contains the Android app prototype for VisionAssist. The debug APK
has been built, installed, and launched on the Pixel 7 Android 15 emulator.
That was a launch smoke test only; the full capture and enhancement flow has
not yet been validated end-to-end.

## Included

- Kotlin onboarding screen for condition context, spectacle power context, brightness, contrast, and adaptive contrast.
- Local profile persistence through `SharedPreferences`.
- Validated JSON-compatible `VisualProfile`.
- `TYPE_APPLICATION_OVERLAY` window with `FLAG_NOT_TOUCHABLE` and `FLAG_NOT_FOCUSABLE`.
- OpenCV frame processor for frames supplied by a user-consented `MediaProjection` pipeline.
- Sensitive-app package policy and overlay suspend/resume path when a foreground package is supplied.
- User-consented `MediaProjection` capture using `VirtualDisplay` and `ImageReader`.
- Foreground notification service lifecycle for active capture.

## Prototype status and limitations

- A Gradle debug build succeeds, and the app launches in the Pixel 7 Android 15 (API 35) emulator.
- The emulator check did not exercise permission prompts, live capture, rendered enhancement, rotation, latency, or stop/error handling. No physical-device testing has been performed.
- Sensitive-app detection is not automatic. The policy only acts when a foreground package is provided, so do not treat it as a complete or guaranteed safeguard.
- There is no dedicated synthetic-image presentation mode yet. Do not describe the current emulator launch check as a successful live screen-enhancement demonstration.
- The project does not diagnose eye conditions or calculate/recommend prescriptions.

## Important platform boundary

An overlay window alone cannot rewrite pixels rendered by another application.
The app has a `MediaProjection` capture and OpenCV processing path, but its
end-to-end behavior still needs to be exercised and verified. Android requires
user consent for screen capture; this project does not bypass that requirement.
Device testing is still needed for frame latency, rotation, memory use, and app
compatibility.

## Build prerequisites

Install Android Studio with:

- Android Studio bundled JBR (JetBrains Runtime) or a compatible JDK
- Android SDK Platform 35
- Android build tools
- Gradle support

This workspace is configured to keep Android SDK files on `D:`:

- SDK: `D:\Android\Sdk`
- Gradle cache: `D:\Android\Gradle`
- Emulator data: `D:\Android\Avd`
- Java runtime: `C:\Program Files\Android\Android Studio\jbr`

Install the SDK components through Android Studio's SDK Manager using
`D:\Android\Sdk` as the SDK location. The project sets
`org.gradle.java.home` to Android Studio's bundled JBR. If the inherited
`JAVA_HOME` points to a missing directory, the Gradle wrapper also falls back
to the bundled JBR. The machine-level `JAVA_HOME` may still need correction if
it is stale; a user-level setting cannot override a stale machine-level value
in every shell.

Build the debug APK from this directory:

```powershell
.\gradlew.bat assembleDebug
```

The current build succeeds, with a compatibility warning because Android Gradle
Plugin 8.5.2 was tested through compileSdk 34 while this app uses compileSdk 35.
For the latest verified build and prototype status, see
[`../PROJECT_STATUS.md`](../PROJECT_STATUS.md).

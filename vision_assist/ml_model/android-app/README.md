# VisionAssist Android Module

This module is the Android-side scaffold for the VisionAssist prototype.

## Included

- Kotlin onboarding screen for condition context, spectacle power context, brightness, contrast, and adaptive contrast.
- Local profile persistence through `SharedPreferences`.
- Validated JSON-compatible `VisualProfile`.
- `TYPE_APPLICATION_OVERLAY` window with `FLAG_NOT_TOUCHABLE` and `FLAG_NOT_FOCUSABLE`.
- OpenCV frame processor for frames supplied by a future user-consented `MediaProjection` pipeline.
- Sensitive-app policy and overlay suspend/resume path.
- User-consented `MediaProjection` capture using `VirtualDisplay` and `ImageReader`.
- Foreground notification service lifecycle for active capture.

## Important platform boundary

An overlay window alone cannot rewrite pixels rendered by another application. Full-screen pixel processing now requests Android `MediaProjection` consent, captures frames through `VirtualDisplay` and `ImageReader`, processes them with OpenCV, and displays the processed frame in the non-touchable overlay. Device testing is still required to tune frame latency, rotation, and memory use.

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

Install the SDK components through Android Studio's SDK Manager using `D:\Android\Sdk` as the SDK location. If a stale machine-level `JAVA_HOME` is present, the project wrapper now falls back to Android Studio's bundled JBR automatically. Then open this `android-app` directory in Android Studio and sync the Gradle project.

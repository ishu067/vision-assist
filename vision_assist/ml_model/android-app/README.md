# VisionAssist Android Module

This module contains the Android app prototype for VisionAssist. The debug APK
has been built, installed, and exercised on the Pixel 7 Android 15 emulator.
The user-consented full-screen capture, first-frame status, and in-app stop
flow were verified there. This is still prototype validation, not broad device
or app compatibility testing.

## Included

- Kotlin onboarding screen for condition context, spectacle power context, brightness, contrast, and adaptive contrast.
- Local profile persistence through `SharedPreferences`.
- Validated JSON-compatible `VisualProfile`.
- `TYPE_APPLICATION_OVERLAY` window with `FLAG_NOT_TOUCHABLE` and `FLAG_NOT_FOCUSABLE`.
- OpenCV frame processor for frames supplied by a user-consented `MediaProjection` pipeline.
- Opt-in Accessibility Service that observes foreground package names and suspends capture for selected sensitive-app name patterns.
- User-consented `MediaProjection` capture using `VirtualDisplay` and `ImageReader`.
- Foreground notification service lifecycle for active capture.

## Prototype status and limitations

- A Gradle debug build succeeds, and the app launches in the Pixel 7 Android 15 (API 35) emulator.
- Emulator flow verified: grant notifications, start enhancement, choose **Entire screen** in Android's MediaProjection prompt, approve capture, wait for the first-frame running status, and tap **Stop enhancement**. Cancelling capture consent also displays a denial message without crashing.
- On Android 13 and later, VisionAssist asks for notification permission before starting capture so the foreground-service notification and its Stop action are accessible. Android still controls the consent prompt for every screen-capture session.
- The direct notification Stop action was not separately tapped during verification. Startup timeout, projection revocation, rotation, latency, and memory-use cases remain untested. No physical-device testing has been performed.
- The Accessibility Service was enabled with explicit user consent on the Pixel 7 Android 15 emulator. VisionAssist reported it enabled and responding.
- The service observes window-state events and stores foreground package identifiers only; it does not retrieve window content. A conservative package-name heuristic suspends capture for selected bank, wallet, password, authenticator, keyboard, and input-method patterns. Names may not reliably identify sensitive apps, so this is best-effort and is not guaranteed protection.
- Emulator app-switch test passed: with Entire screen capture approved, switching to Android Settings left the projection active; opening Settings search displayed `com.google.android.inputmethod.latin`, which the service detected and used to stop capture. The persisted status showed the sensitive-app suspension message and Android reported no remaining MediaProjection.
- Emulator detection-loss test passed: while capture was active, turning the Accessibility Service off in Android Settings stopped capture, cleared the projection, and persisted a visible detection-disconnected message. The service was then re-enabled with user consent and confirmed enabled/connected.
- To reproduce, tap **Enable foreground-app detection**, select **VisionAssist foreground-app detection** under Android's Accessibility settings, turn it on, and accept Android's warning. Return to VisionAssist and confirm the status says it is enabled and responding. Start capture with Android's consent, switch to a harmless app, and open a text field to show the emulator keyboard. Do not use actual banking apps or private account data. To test detection loss, switch Accessibility detection off during capture; it should stop and display the reason.
- There is no dedicated synthetic-image presentation mode yet. Do not describe the current emulator launch check as a successful live screen-enhancement demonstration.
- The project does not diagnose eye conditions or calculate/recommend prescriptions.

## Focused verification

Run the Android unit test and debug build from this directory:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

`SensitiveAppPolicyTest` checks representative sensitive-name matches,
including the emulator's `com.google.android.inputmethod.latin`, and an
ordinary package name. The emulator test also exercised user consent, active
capture across a safe Settings app switch, automatic keyboard-triggered
suspension, and fail-closed shutdown when the Accessibility Service was
disabled. Android requires users to enable the service themselves in Settings;
VisionAssist never enables it programmatically.

## Important platform boundary

An overlay window alone cannot rewrite pixels rendered by another application.
The app has a `MediaProjection` capture and OpenCV processing path, but its
end-to-end start, first-frame, and in-app stop behavior has been exercised in
the Pixel 7 emulator. Android requires user consent for screen capture; this
project does not bypass that requirement. Physical-device testing is still
needed for frame latency, rotation, memory use, and app compatibility.

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

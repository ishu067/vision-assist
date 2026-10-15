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
- Offline demo that creates synthetic artwork locally and previews the original beside the OpenCV-enhanced result.
- Opt-in Accessibility Service that observes foreground package names and suspends capture for selected sensitive-app name patterns.
- User-consented `MediaProjection` capture using `VirtualDisplay` and `ImageReader`.
- Foreground notification service lifecycle for active capture.

## Prototype status and limitations

- A Gradle debug build succeeds, and the app launches in the Pixel 7 Android 15 (API 35) emulator.
- Emulator flow verified: grant notifications, start enhancement, choose **Entire screen** in Android's MediaProjection prompt, approve capture, wait for the first-frame running status, and tap **Stop enhancement**. Cancelling capture consent also displays a denial message without crashing.
- Offline presentation flow verified on the Pixel 7 Android 15 emulator: set brightness, contrast, or adaptive contrast in the profile section, then tap **Run offline synthetic demo**. VisionAssist creates non-sensitive artwork locally and shows side-by-side synthetic input and enhanced preview. The section and status explicitly say this is not a live screen, and Android reported no active MediaProjection after the demo ran.
- On Android 13 and later, VisionAssist asks for notification permission before starting capture so the foreground-service notification and its Stop action are accessible. Android still controls the consent prompt for every screen-capture session.
- The direct notification Stop action was not separately tapped during verification. Startup timeout, projection revocation, rotation, latency, and memory-use cases remain untested. No physical-device testing has been performed.
- The Accessibility Service was enabled with explicit user consent on the Pixel 7 Android 15 emulator. VisionAssist reported it enabled and responding.
- The service observes window-state events and stores foreground package identifiers only; it does not retrieve window content. A conservative package-name heuristic suspends capture for selected bank, wallet, password, authenticator, keyboard, and input-method patterns. Names may not reliably identify sensitive apps, so this is best-effort and is not guaranteed protection.
- Emulator app-switch test passed: with Entire screen capture approved, switching to Android Settings left the projection active; opening Settings search displayed `com.google.android.inputmethod.latin`, which the service detected and used to stop capture. The persisted status showed the sensitive-app suspension message and Android reported no remaining MediaProjection.
- Emulator detection-loss test passed: while capture was active, turning the Accessibility Service off in Android Settings stopped capture, cleared the projection, and persisted a visible detection-disconnected message. The service was then re-enabled with user consent and confirmed enabled/connected.
- To reproduce, tap **Enable foreground-app detection**, select **VisionAssist foreground-app detection** under Android's Accessibility settings, turn it on, and accept Android's warning. Return to VisionAssist and confirm the status says it is enabled and responding. Start capture with Android's consent, switch to a harmless app, and open a text field to show the emulator keyboard. Do not use actual banking apps or private account data. To test detection loss, switch Accessibility detection off during capture; it should stop and display the reason.
- The offline demo is a sample-image presentation path, not proof of real-app or physical-device behavior. Live capture remains a separate action and still requires the platform permissions and explicit MediaProjection consent.
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

## Short presentation demo

Use the offline path as the default; it requires no screen-capture, overlay,
notification, or Accessibility permission. Allow about 2–3 minutes.

### Setup

Build and install the debug APK (or open the already-installed app):

```powershell
.\gradlew.bat assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.visionassist.android/.MainActivity
```

### Rehearsal script

1. Point out that VisionAssist is an accessibility prototype, not a diagnosis
   or prescription tool. Explain that the demo below uses generated sample
   artwork, not a live screen.
2. In **Your visual profile**, adjust brightness or contrast and optionally
   toggle adaptive contrast. The profile inputs are controls for this preview;
   condition and spectacle-power entries are context only.
3. Tap **Run offline synthetic demo**.
4. Show **Synthetic input** beside **Enhanced synthetic preview**. The status
   should say **Offline demo active** and explicitly state that it is not live
   capture. The screen-capture permission prompt should not appear.
5. Change a setting and run the demo again to compare the result.
6. Point out that **Start screen enhancement** remains a separate live-capture
   option; do not tap it unless you intend to demonstrate the optional live
   flow below.

### Optional live-capture demonstration

Live capture is not needed for the short presentation. If demonstrating it,
prepare these permissions beforehand, through Android's system UI:

1. Allow notifications on Android 13+.
2. Allow VisionAssist to display over other apps.
3. Enable **VisionAssist foreground-app detection** in Accessibility settings
   and accept Android's warning. The service observes foreground package names
   only; its sensitive-app matching is best-effort.
4. Return to VisionAssist, tap **Start screen enhancement**, choose
   **Entire screen** in Android's MediaProjection prompt, and explicitly
   approve capture. Android may show a further confirmation according to the
   OS version.
5. Expected result: the capture status reports the first frame/running state,
   the ongoing notification offers **Stop**, and tapping **Stop enhancement**
   ends capture. Android requires consent again when starting another session.

### Fallbacks and limitations

- If permissions are unavailable, declined, or not prepared, use the offline
  demo; it is designed to work without them. It only demonstrates the current
  Android brightness, contrast, and CLAHE processing on synthetic content.
- If the offline preview reports an OpenCV error, restart VisionAssist and
  retry once. If it still fails, use the Python sample demo documented in the
  project README and describe it as a separate Python image demo—not as
  Android live capture.
- If live capture is declined or fails, state that it did not start and return
  to the offline demo; never imply synthetic imagery is captured content.
- The only end-to-end device validation documented here is on the Pixel 7
  Android 15 emulator. No physical-device validation has been performed.
  Rotation, performance, memory use, direct notification Stop, and broad app
  compatibility remain unverified.
- Foreground-package matching can miss sensitive apps and is not guaranteed
  protection. Do not test against actual banking apps or private data.
- VisionAssist does not diagnose conditions or recommend prescriptions.

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

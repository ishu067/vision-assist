# VisionAssist Project Status

Last verified: 2026-10-14

## Current prototype status

VisionAssist has a runnable Python image-enhancement prototype and an Android
debug APK that builds, installs, and launches in the configured emulator. This
is enough for continued prototype development and a basic app-launch
demonstration; it is **not yet verified as a dependable live screen-enhancement
demo or production-ready accessibility tool**.

Verified in this workspace:

- Python unit tests: 4 tests pass with `python -m unittest discover -s tests -v`.
- Python demo: `python -m vision_assist.demo` writes
  `artifacts/demo_enhanced.png` and reports one cache hit and one miss.
- Android build: `.\gradlew.bat assembleDebug` succeeds from
  `android-app` using the wrapper's Android Studio JBR fallback.
- Emulator smoke test: the debug APK installed and `MainActivity` launched on
  the Pixel 7 AVD running Android 15 (API 35).

Not verified yet:

- Interacting through the Android UI to grant permissions and complete the
  MediaProjection capture flow.
- Visible enhancement quality, overlay behavior during real capture, or
  reliable stop/error handling.
- Rotation, latency, memory use, compatibility across apps, or physical-device
  behavior.
- Automatic foreground-package detection and sensitive-app suspension.
- A dedicated offline/demo-mode presentation flow.

The Android app should currently be presented as an **early prototype**. Do not
claim that live enhancement, sensitive-app exclusion, or physical-device
behavior has been fully validated.

## 1. Source document reviewed

The project synopsis describes VisionAssist as an Android-first accessibility system. Its intended flow is:

1. Collect a user's declared eye condition, spectacle power, and visual calibration results.
2. Create a local visual enhancement profile.
3. Apply pixel-level enhancement without moving or resizing the original app interface.
4. Use OpenCV, Android `ColorMatrix`, and CLAHE/sharpening for the main visual effect.
5. Use a small MobileNetV2/TensorFlow Lite component for runtime performance decisions.
6. Provide a transparent overlay, one-touch activation, local storage, and sensitive-app exclusions.
7. Keep screen data and personal information on the device.

The synopsis explicitly excludes medical diagnosis and treatment. VisionAssist must remain an accessibility aid, not an eye examination or prescription system.

## 2. What has been implemented

A laptop-testable Python prototype now exists under `src/vision_assist`.

### Visual profile

`profile.py` defines `VisualProfile` and `build_profile()`.

It stores:

- Declared condition text
- Sphere and cylinder power as user context
- Brightness
- Contrast
- Sharpening strength
- Color assistance mode
- CLAHE enablement

Values are bounded before use. Spectacle power is never converted into a diagnosis or prescription.

### OpenCV enhancement engine

`engine.py` applies the current profile to BGR `uint8` frames using:

- Brightness and contrast adjustment
- CLAHE adaptive contrast
- Grayscale conversion
- Approximate color-assistance matrices
- Unsharp-mask style sharpening

The engine changes pixels only. It does not alter UI layout, buttons, or interaction behavior.

### Auto-cache

`cache.py` provides a bounded in-memory LRU cache.

Each cache key includes:

- Frame bytes
- Frame shape
- Frame data type
- A digest of the complete visual profile

This means a repeated frame is reused, while changing the user profile forces a fresh enhancement. Cached arrays are copied on read/write to avoid accidental mutation between callers.

### Demo and tests

`demo.py` creates a sample frame, applies the filter, repeats the same request, and writes `artifacts/demo_enhanced.png`.

`tests/test_engine.py` verifies:

- Repeated frames produce identical output and a cache hit.
- Profile changes invalidate the cache key.
- Input bounds and unsupported color modes are handled safely.

## 3. Python validation

The following checks passed:

```text
python -m unittest discover -s tests -v
Ran 4 tests ... OK

python -m vision_assist.demo
cache hits=1, misses=1
```

OpenCV and NumPy are available in the current Python environment. The test
suite covers caching, profile validation, JSON round-tripping, and runtime
policy behavior; it does not validate Android rendering or capture.

## 4. Current architecture

```text
User inputs and calibration
          |
          v
   VisualProfile
          |
          v
 EnhancementEngine <---- FrameCache
          |
          v
   Enhanced local frame
```

The Accessibility Service, Quick Settings tile, TFLite performance controller,
and a dedicated presentation demo mode are not implemented yet.

## 5. Completed bridge step

The profile contract is now portable:

1. `VisualProfile.to_json()` serializes validated settings for local storage.
2. `VisualProfile.from_json()` restores settings through the same validation path.
3. `RuntimePolicy` exposes renderer settings for a future Kotlin/Android client.
4. `policy_from_profile()` selects a 16 ms or 33 ms target based on processing intensity.
5. Sensitive-app suspension is enabled by default in the runtime policy.

The bridge has a round-trip test and is ready for an Android client to consume.

## 6. Android milestone completed

An Android module now exists under `android-app`.

### Onboarding and storage

- `MainActivity.kt` connects the onboarding form to a validated Kotlin `VisualProfile`.
- The form collects declared condition context, spectacle power context, brightness, contrast, and adaptive contrast preference.
- `ProfileStorage.kt` stores the serialized profile in app-private `SharedPreferences`.
- The UI clearly states that the app does not diagnose or calculate a prescription.

### Overlay service

- `OverlayService.kt` creates a `TYPE_APPLICATION_OVERLAY` window.
- The window uses `FLAG_NOT_TOUCHABLE` and `FLAG_NOT_FOCUSABLE` so touch input remains with the underlying app.
- Overlay permission is requested through Android system settings.

### OpenCV integration

- `OpenCvFrameProcessor.kt` is wired to the OpenCV Android dependency.
- It supports CLAHE, brightness/contrast adjustment, and sharpening for captured `Bitmap` frames.
- `MainActivity.kt` requests user consent through `MediaProjectionManager`.
- `MediaProjectionCapture.kt` creates the `VirtualDisplay` and `ImageReader` frame source.
- `OverlayService.kt` runs foreground capture, sends frames through OpenCV, and displays processed frames in the non-touchable overlay.
- Device testing is still required for rotation, frame latency, memory use, and actual app compatibility.

### Sensitive-app exclusion

- `SensitiveAppPolicy.kt` identifies package-name fragments associated with banking, wallets, passwords, authenticators, keyboards, and IMEs.
- The service accepts `EXTRA_FOREGROUND_PACKAGE` and removes the overlay for a sensitive package when it is given the foreground package.
- Automatic foreground-package detection is not implemented; therefore sensitive-app suspension is not currently an end-to-end safeguard and must not be presented as guaranteed protection.

### Validation

- Android Studio is installed at `C:\Program Files\Android\Android Studio` with a bundled JBR. Android SDK Platform 35, Android build tools, Platform-Tools/ADB, and a Pixel 7 AVD are installed under `D:\Android`.
- The machine-level `JAVA_HOME` still contains a stale path (`C:\Users\ishuy\AppData\Local\Programs\Eclipse Adoptium`). A non-elevated attempt to clear it was denied. The Android wrapper falls back to Android Studio's JBR when the inherited Java path is invalid; the project also sets `org.gradle.java.home`.
- `.\gradlew.bat assembleDebug` completed successfully. The build emitted a warning that Android Gradle Plugin 8.5.2 was tested through compileSdk 34 while this app uses compileSdk 35.
- The debug APK installed and `MainActivity` launched on the Pixel 7 AVD (Android 15/API 35). This was a launch smoke test only; permissions and live screen capture were not exercised.
- No physical-device validation has been performed.

## 7. Next steps for a presentation-level prototype

Prioritize a reliable, honest demonstration over production features:

1. Walk through the Android app and document the exact permissions and actions
   needed to start/stop capture. Make permission-denied, startup, and capture
   failures visible to the user.
2. Add a clear demo-mode path using synthetic or bundled non-sensitive imagery
   so the enhancement can be shown even if live capture is unavailable. Label
   it clearly as demo content, not live screen capture.
3. Implement foreground-app detection for the existing sensitive-app policy
   only if it can be tested end-to-end. Explain its user-granted Accessibility
   permission and limitations; do not claim guaranteed protection.
4. Rehearse the permission and capture flow in the emulator. Verify overlay
   appearance, enhancement output, and stopping behavior. Record failures and
   limitations in the README.
5. Refresh this status and the Android README with only results actually
   observed. TFLite and broad device compatibility can remain out of scope for
   a presentation-only prototype.

## 8. Safety and product boundaries

- Do not claim that the system diagnoses cataracts, color blindness, refractive error, or any other condition.
- Do not calculate or recommend spectacle prescriptions.
- Keep medical fields local and minimize their retention.
- Implement and verify sensitive-surface suspension before describing it as a feature; the current package policy alone does not detect the foreground app automatically.
- Validate accessibility benefit with qualified users and clinical/accessibility guidance before real-world deployment.

# VisionAssist Project Status

Date: 2026-09-19

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

## 3. Validation completed

The following checks passed:

```text
python -m unittest discover -s tests -v
Ran 3 tests ... OK

python -m compileall -q src tests
Completed successfully

python -m vision_assist.demo
cache hits=1, misses=1
```

OpenCV and NumPy are available in the current Python environment. TensorFlow, ONNX Runtime, and the Android toolchain are not installed in this workspace yet.

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

The Android overlay, Accessibility Service, Quick Settings tile, and TFLite performance controller are not implemented yet.

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
- The service accepts `EXTRA_FOREGROUND_PACKAGE` and removes the overlay for a sensitive package.
- A future Accessibility Service can send foreground-package changes to this service.

### Validation

- Required Android files and XML resources were checked structurally.
- The Python regression suite still passes all 4 tests.
- Android Studio is now installed at `C:\Program Files\Android\Android Studio` with bundled Java 25.
- The Android SDK Platform 35, Build-Tools, Platform-Tools/ADB, and a Gradle wrapper are still pending.
- Android SDK, Gradle cache, emulator data, and Android user data are configured for `D:\Android` to protect the nearly-full `C:` drive.
- APK compilation and device testing have not run yet.

## 7. Next implementation step

Open Android Studio's SDK Manager, select `D:\Android\Sdk` as the SDK location, and install SDK Platform 35, Android SDK Build-Tools, and Android SDK Platform-Tools. Then sync/build this module, implement Accessibility Service foreground-package updates, and test rotation, frame latency, memory use, and sensitive-app suspension on a real device before adding the TFLite performance controller.

## 8. Safety and product boundaries

- Do not claim that the system diagnoses cataracts, color blindness, refractive error, or any other condition.
- Do not calculate or recommend spectacle prescriptions.
- Keep medical fields local and minimize their retention.
- Suspend enhancement for banking, password, keyboard, and other sensitive surfaces.
- Validate accessibility benefit with qualified users and clinical/accessibility guidance before real-world deployment.

# VisionAssist

VisionAssist is an early accessibility prototype for visual enhancement. It
includes a laptop-testable Python image-processing prototype and an Android app
prototype with a synthetic-image demo and a separate, user-consented live
screen-capture path.

> **Prototype only:** VisionAssist is not a medical device. It does not
> diagnose eye conditions or recommend prescriptions. Android live capture
> and foreground-app detection have been exercised on a Pixel 7 Android 15
> emulator only; physical-device behavior and broad app compatibility have not
> been validated. The sensitive-app package-name policy is best-effort and
> does not guarantee protection.

## Project documentation

- [Python prototype, setup, demo, and tests](vision_assist/ml_model/README.md)
- [Android setup, permissions, tests, and presentation script](vision_assist/ml_model/android-app/README.md)
- [Detailed verified status and remaining work](vision_assist/ml_model/PROJECT_STATUS.md)

## Quick start

### Python demo

From the repository root in PowerShell:

```powershell
Set-Location .\vision_assist\ml_model
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -e .
.\.venv\Scripts\python.exe -m vision_assist.demo
```

Run the Python unit tests with:

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

### Android app

The Android project is in `vision_assist\ml_model\android-app`. From that
directory, build and run its focused unit tests with:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The APK is written to
`app\build\outputs\apk\debug\app-debug.apk`. For a presentation, the
permission-free **Run offline synthetic demo** is the recommended path. It
generates sample artwork locally and is explicitly not a live screen capture.
See the [Android presentation script](vision_assist/ml_model/android-app/README.md#short-presentation-demo)
for steps, optional live-capture consent prompts, fallbacks, and limitations.

# VisionAssist ML Prototype

Project progress is documented in [PROJECT_STATUS.md](PROJECT_STATUS.md).

This repository contains the laptop-testable core of the VisionAssist concept from `visionAssist.pdf`.

## What this prototype does

- Builds a local visual enhancement profile from user-declared information and calibration values.
- Applies deterministic OpenCV enhancements: brightness, contrast, CLAHE, color correction, and sharpening.
- Uses a bounded in-memory LRU auto-cache keyed by the frame content and the full profile.
- Keeps medical information local and treats it as user context, not as a diagnostic signal.

The Android prototype is available in [android-app](android-app). It includes
an offline synthetic-image demonstration and a separate, user-consented live
screen-capture path. The Android UI currently exposes brightness, contrast,
and adaptive contrast; it does not yet implement all Python profile modes or
the planned TFLite performance controller.

Android setup and manual test notes are in
[android-app/README.md](android-app/README.md).

## Setup

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -e .
```

## Run the demo

```powershell
\.venv\Scripts\python.exe -m vision_assist.demo
```

The demo creates a sample image in `artifacts/demo_enhanced.png` and prints cache statistics.

For an on-device presentation without screen-capture consent, launch
VisionAssist and use **Run offline synthetic demo**. It generates a local
sample image and labels the input and enhanced preview as synthetic; it does
not display a live app screen.

## Test

```powershell
\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

## Android prototype and remaining work

- User profile settings are stored in Android private storage.
- Android uses OpenCV for brightness, contrast, and CLAHE on captured frames and
  on the offline synthetic demo image.
- Live screen capture requires Android's explicit MediaProjection consent.
- The overlay is non-touchable and non-focusable. The opt-in Accessibility
  Service uses a best-effort package-name policy; it is not guaranteed
  sensitive-app protection.
- Physical-device compatibility and additional profile modes remain to be
  validated. TFLite work is not required for the presentation prototype.

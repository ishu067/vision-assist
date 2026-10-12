# VisionAssist ML Prototype

Project progress is documented in [PROJECT_STATUS.md](PROJECT_STATUS.md).

This repository contains the laptop-testable core of the VisionAssist concept from `visionAssist.pdf`.

## What this prototype does

- Builds a local visual enhancement profile from user-declared information and calibration values.
- Applies deterministic OpenCV enhancements: brightness, contrast, CLAHE, color correction, and sharpening.
- Uses a bounded in-memory LRU auto-cache keyed by the frame content and the full profile.
- Keeps medical information local and treats it as user context, not as a diagnostic signal.

The target product described in the synopsis is Android-first. This Python package is the testable filter/cache core; the Android overlay and TFLite performance controller should consume the same profile contract later.

The Android scaffold is in [android-app](android-app) with its own setup notes in [android-app/README.md](android-app/README.md).

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

## Test

```powershell
\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

## Planned Android mapping

1. Store the serialized `VisualProfile` in Android private storage.
2. Reimplement `EnhancementEngine` with OpenCV Android and `ColorMatrix`.
3. Capture screen frames through the permitted Android accessibility/overlay architecture.
4. Keep overlay windows non-touchable and non-focusable, and suspend them for sensitive apps.
5. Add a quantized MobileNetV2/TFLite controller only for workload or scene-policy selection; do not use it for diagnosis or prescription.

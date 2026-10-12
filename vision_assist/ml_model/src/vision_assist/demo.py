"""Generate a local demo frame."""

from pathlib import Path

import cv2
import numpy as np

from .engine import EnhancementEngine
from .profile import build_profile


def main() -> None:
    height, width = 360, 640
    frame = np.zeros((height, width, 3), dtype=np.uint8)
    cv2.rectangle(frame, (40, 40), (width - 40, height - 40), (80, 120, 180), -1)
    cv2.putText(frame, "VisionAssist", (90, 190), cv2.FONT_HERSHEY_SIMPLEX, 1.7, (240, 240, 240), 3)

    profile = build_profile(contrast=1.35, brightness=12, sharpness=0.6, clahe=True)
    engine = EnhancementEngine()
    enhanced = engine.apply(frame, profile)
    engine.apply(frame, profile)

    output = Path("artifacts/demo_enhanced.png")
    output.parent.mkdir(exist_ok=True)
    cv2.imwrite(str(output), enhanced)
    print(f"wrote {output}")
    print(f"cache hits={engine.cache.hits}, misses={engine.cache.misses}")


if __name__ == "__main__":
    main()

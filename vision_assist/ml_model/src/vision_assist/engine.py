"""OpenCV enhancement pipeline."""

import cv2
import numpy as np

from .cache import FrameCache
from .profile import VisualProfile


class EnhancementEngine:
    """Apply a profile to BGR frames while reusing repeated results."""

    def __init__(self, cache: FrameCache | None = None) -> None:
        self.cache = cache or FrameCache()

    def apply(self, frame: np.ndarray, profile: VisualProfile) -> np.ndarray:
        self._validate_frame(frame)
        cached = self.cache.get(frame, profile)
        if cached is not None:
            return cached

        result = frame.copy()
        if profile.clahe:
            lab = cv2.cvtColor(result, cv2.COLOR_BGR2LAB)
            lightness, green_red, blue_yellow = cv2.split(lab)
            lightness = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(lightness)
            result = cv2.cvtColor(cv2.merge((lightness, green_red, blue_yellow)), cv2.COLOR_LAB2BGR)

        result = cv2.convertScaleAbs(result, alpha=profile.contrast, beta=profile.brightness)
        result = self._apply_color_mode(result, profile.color_mode)
        if profile.sharpness > 0:
            blurred = cv2.GaussianBlur(result, (0, 0), 1.0)
            result = cv2.addWeighted(result, 1.0 + profile.sharpness, blurred, -profile.sharpness, 0)

        self.cache.put(frame, profile, result)
        return result

    @staticmethod
    def _apply_color_mode(frame: np.ndarray, mode: str) -> np.ndarray:
        if mode == "normal":
            return frame
        if mode == "grayscale":
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            return cv2.cvtColor(gray, cv2.COLOR_GRAY2BGR)

        # Approximate accessibility matrices. They are user-selectable display aids,
        # not clinical color-vision correction or diagnosis.
        matrices = {
            "protanopia": np.array([[0.567, 0.433, 0.0], [0.558, 0.442, 0.0], [0.0, 0.242, 0.758]], dtype=np.float32),
            "deuteranopia": np.array([[0.625, 0.375, 0.0], [0.7, 0.3, 0.0], [0.0, 0.3, 0.7]], dtype=np.float32),
            "tritanopia": np.array([[0.95, 0.05, 0.0], [0.0, 0.433, 0.567], [0.0, 0.475, 0.525]], dtype=np.float32),
        }
        matrix = matrices[mode]
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
        corrected = np.clip(rgb @ matrix.T, 0.0, 1.0)
        return cv2.cvtColor((corrected * 255).astype(np.uint8), cv2.COLOR_RGB2BGR)

    @staticmethod
    def _validate_frame(frame: np.ndarray) -> None:
        if not isinstance(frame, np.ndarray) or frame.dtype != np.uint8:
            raise TypeError("frame must be a uint8 NumPy array")
        if frame.ndim != 3 or frame.shape[2] != 3:
            raise ValueError("frame must have shape (height, width, 3)")

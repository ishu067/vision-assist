"""User-controlled visual enhancement settings."""

from dataclasses import asdict, dataclass
import json
from typing import Any


@dataclass(frozen=True)
class VisualProfile:
    """Serializable settings for visual assistance, not a medical diagnosis."""

    condition: str = "none"
    sphere_power: float = 0.0
    cylinder_power: float = 0.0
    brightness: float = 0.0
    contrast: float = 1.0
    sharpness: float = 0.0
    color_mode: str = "normal"
    clahe: bool = False

    def as_dict(self) -> dict[str, Any]:
        return asdict(self)

    def to_json(self) -> str:
        """Serialize the profile for local storage or an Android client."""
        return json.dumps(self.as_dict(), sort_keys=True)

    @classmethod
    def from_json(cls, payload: str) -> "VisualProfile":
        """Load a profile through the same validation rules as user input."""
        values = json.loads(payload)
        if not isinstance(values, dict):
            raise ValueError("profile JSON must contain an object")
        return build_profile(**values)


def _clamp(value: float, minimum: float, maximum: float) -> float:
    return max(minimum, min(maximum, float(value)))


def build_profile(
    *,
    condition: str = "none",
    sphere_power: float = 0.0,
    cylinder_power: float = 0.0,
    brightness: float = 0.0,
    contrast: float = 1.0,
    sharpness: float = 0.0,
    color_mode: str = "normal",
    clahe: bool = False,
) -> VisualProfile:
    """Create a bounded profile from user input and calibration results.

    Spectacle power is retained as user context. It is intentionally not used
    to diagnose a condition or calculate a prescription.
    """
    supported_modes = {"normal", "protanopia", "deuteranopia", "tritanopia", "grayscale"}
    normalized_mode = color_mode.lower()
    if normalized_mode not in supported_modes:
        raise ValueError(f"unsupported color mode: {color_mode}")

    return VisualProfile(
        condition=condition.strip() or "none",
        sphere_power=_clamp(sphere_power, -20.0, 20.0),
        cylinder_power=_clamp(cylinder_power, -10.0, 10.0),
        brightness=_clamp(brightness, -100.0, 100.0),
        contrast=_clamp(contrast, 0.5, 2.5),
        sharpness=_clamp(sharpness, 0.0, 2.0),
        color_mode=normalized_mode,
        clahe=bool(clahe),
    )

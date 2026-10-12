"""Runtime policy derived from user-controlled visual settings."""

from dataclasses import asdict, dataclass
import json

from .profile import VisualProfile


@dataclass(frozen=True)
class RuntimePolicy:
    """Non-medical rendering guidance for a future mobile overlay."""

    brightness: float
    contrast: float
    sharpness: float
    color_mode: str
    clahe: bool
    target_frame_interval_ms: int
    suspend_for_sensitive_apps: bool = True

    def as_dict(self) -> dict[str, object]:
        return asdict(self)

    def to_json(self) -> str:
        return json.dumps(self.as_dict(), sort_keys=True)


def policy_from_profile(profile: VisualProfile) -> RuntimePolicy:
    """Convert a profile into settings that an Android renderer can consume."""
    intensity = abs(profile.contrast - 1.0) + profile.sharpness
    target_frame_interval_ms = 33 if intensity > 2.0 or profile.clahe else 16
    return RuntimePolicy(
        brightness=profile.brightness,
        contrast=profile.contrast,
        sharpness=profile.sharpness,
        color_mode=profile.color_mode,
        clahe=profile.clahe,
        target_frame_interval_ms=target_frame_interval_ms,
    )

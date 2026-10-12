import unittest

import numpy as np

from vision_assist import EnhancementEngine, FrameCache, build_profile, policy_from_profile


class EnhancementEngineTests(unittest.TestCase):
    def test_repeated_frame_uses_cache_and_returns_same_pixels(self) -> None:
        frame = np.full((32, 32, 3), 100, dtype=np.uint8)
        profile = build_profile(contrast=1.2, brightness=10)
        engine = EnhancementEngine(FrameCache(max_items=2))

        first = engine.apply(frame, profile)
        second = engine.apply(frame, profile)

        self.assertTrue(np.array_equal(first, second))
        self.assertEqual(engine.cache.misses, 1)
        self.assertEqual(engine.cache.hits, 1)

    def test_profile_changes_invalidate_cache_key(self) -> None:
        frame = np.zeros((8, 8, 3), dtype=np.uint8)
        engine = EnhancementEngine()
        engine.apply(frame, build_profile(brightness=0))
        engine.apply(frame, build_profile(brightness=20))
        self.assertEqual(engine.cache.misses, 2)

    def test_profile_bounds_and_invalid_mode(self) -> None:
        profile = build_profile(sphere_power=50, brightness=-200, contrast=9)
        self.assertEqual(profile.sphere_power, 20.0)
        self.assertEqual(profile.brightness, -100.0)
        self.assertEqual(profile.contrast, 2.5)
        with self.assertRaises(ValueError):
            build_profile(color_mode="diagnose")

    def test_profile_json_round_trip_and_runtime_policy(self) -> None:
        profile = build_profile(contrast=1.4, sharpness=0.5, clahe=True)
        restored = type(profile).from_json(profile.to_json())
        policy = policy_from_profile(restored)
        self.assertEqual(restored, profile)
        self.assertEqual(policy.color_mode, "normal")
        self.assertEqual(policy.target_frame_interval_ms, 33)
        self.assertTrue(policy.suspend_for_sensitive_apps)


if __name__ == "__main__":
    unittest.main()

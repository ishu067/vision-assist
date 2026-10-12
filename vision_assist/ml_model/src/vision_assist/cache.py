"""Bounded cache for repeated screen frames."""

from collections import OrderedDict
import hashlib
from typing import Hashable

import numpy as np


class FrameCache:
    """Small LRU cache keyed by frame bytes, shape, dtype, and profile."""

    def __init__(self, max_items: int = 8) -> None:
        if max_items < 1:
            raise ValueError("max_items must be at least 1")
        self.max_items = max_items
        self._items: OrderedDict[Hashable, np.ndarray] = OrderedDict()
        self.hits = 0
        self.misses = 0

    @staticmethod
    def key(frame: np.ndarray, profile: object) -> tuple[str, tuple[int, ...], str, str]:
        digest = hashlib.blake2b(frame.tobytes(), digest_size=16).hexdigest()
        profile_data = getattr(profile, "as_dict", lambda: profile)()
        profile_digest = hashlib.blake2b(repr(sorted(profile_data.items())).encode(), digest_size=16).hexdigest()
        return digest, tuple(frame.shape), str(frame.dtype), profile_digest

    def get(self, frame: np.ndarray, profile: object) -> np.ndarray | None:
        cache_key = self.key(frame, profile)
        result = self._items.get(cache_key)
        if result is None:
            self.misses += 1
            return None
        self._items.move_to_end(cache_key)
        self.hits += 1
        return result.copy()

    def put(self, frame: np.ndarray, profile: object, enhanced: np.ndarray) -> None:
        cache_key = self.key(frame, profile)
        self._items[cache_key] = enhanced.copy()
        self._items.move_to_end(cache_key)
        while len(self._items) > self.max_items:
            self._items.popitem(last=False)

    def clear(self) -> None:
        self._items.clear()

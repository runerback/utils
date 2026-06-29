from abc import ABC, abstractmethod
import numpy as np


class NoiseSource(ABC):
    """Base class for realtime noise sources."""

    def __init__(self, seed: int | None = None):
        self.rng = np.random.default_rng(seed)

    @abstractmethod
    def generate(self, frames: int, channels: int) -> np.ndarray:
        """Return a (frames, channels) float32 array in [-1, 1]."""
        ...


class WhiteNoiseSource(NoiseSource):
    def generate(self, frames: int, channels: int) -> np.ndarray:
        return self.rng.standard_normal((frames, channels)).astype(np.float32)


class BrownNoiseSource(NoiseSource):
    def __init__(self, seed: int | None = None, leak: float = 0.99):
        super().__init__(seed)
        self.leak = leak
        self._state: np.ndarray | None = None

    def generate(self, frames: int, channels: int) -> np.ndarray:
        white = self.rng.standard_normal((frames, channels)).astype(np.float32)
        if self._state is None or self._state.shape[0] != channels:
            self._state = np.zeros(channels, dtype=np.float32)
        out = np.empty_like(white)
        for i in range(frames):
            self._state = self._state * self.leak + white[i]
            out[i] = self._state
        return out


class PinkNoiseSource(NoiseSource):
    """Approximate pink noise using the Voss-McCartney algorithm."""

    def __init__(self, seed: int | None = None):
        super().__init__(seed)
        self._rows = 16
        self._vals = None
        self._index = 0

    def generate(self, frames: int, channels: int) -> np.ndarray:
        out = np.empty((frames, channels), dtype=np.float32)
        if self._vals is None or self._vals.shape[1] != channels:
            self._vals = self.rng.standard_normal((self._rows, channels)).astype(np.float32)
            self._index = 0
        for i in range(frames):
            self._index += 1
            row = (self._index & -self._index).bit_length() - 1
            if row >= self._rows:
                self._index = 1
                row = 0
            self._vals[row] = self.rng.standard_normal(channels).astype(np.float32)
            out[i] = self._vals.sum(axis=0)
        return out


class TuneSource(NoiseSource):
    """A simple test tone (440 Hz sine) to verify settings are applied."""

    def __init__(self, seed: int | None = None, sample_rate: float = 44100.0, frequency: float = 440.0):
        super().__init__(seed)
        self.sample_rate = sample_rate
        self.frequency = frequency
        self._phase = 0.0

    def generate(self, frames: int, channels: int) -> np.ndarray:
        t = np.arange(frames, dtype=np.float32) / self.sample_rate
        phase = self._phase + 2 * np.pi * self.frequency * t
        samples = np.sin(phase).astype(np.float32)
        self._phase = float(phase[-1]) + 2 * np.pi * self.frequency / self.sample_rate
        return np.tile(samples[:, np.newaxis], (1, channels))

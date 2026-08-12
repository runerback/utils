from abc import ABC, abstractmethod
import numpy as np


class NoiseSource(ABC):
    """Base class for noise sources."""

    def __init__(self, seed: int | None = None, distribution: str = "normal"):
        self.rng = np.random.default_rng(seed)
        self.distribution = distribution

    def _random(self, size) -> np.ndarray:
        if self.distribution == "laplace":
            return self.rng.laplace(0.0, 1.0 / np.sqrt(2.0), size)
        if self.distribution == "uniform":
            return self.rng.uniform(-np.sqrt(3.0), np.sqrt(3.0), size)
        return self.rng.standard_normal(size)

    @abstractmethod
    def generate(self, frames: int, channels: int) -> np.ndarray:
        """Return a (frames, channels) float32 array in [-1, 1]."""
        ...

    def reset(self) -> None:
        """Reset any internal state for deterministic generation."""


class WhiteNoiseSource(NoiseSource):
    def generate(self, frames: int, channels: int) -> np.ndarray:
        return self._random((frames, channels)).astype(np.float32)


class BrownNoiseSource(NoiseSource):
    def __init__(self, seed: int | None = None, leak: float = 0.99, distribution: str = "normal"):
        super().__init__(seed, distribution)
        self.leak = leak
        self._state: np.ndarray | None = None

    def reset(self) -> None:
        self._state = None

    def generate(self, frames: int, channels: int) -> np.ndarray:
        white = self._random((frames, channels)).astype(np.float32)
        if self._state is None or self._state.shape[0] != channels:
            self._state = np.zeros(channels, dtype=np.float32)
        out = np.empty_like(white)
        for i in range(frames):
            self._state = self._state * self.leak + white[i]
            out[i] = self._state
        scale = np.sqrt(1.0 - self.leak * self.leak)
        return out * scale


class PinkNoiseSource(NoiseSource):
    """Approximate pink noise using the Voss-McCartney algorithm."""

    def __init__(self, seed: int | None = None, distribution: str = "normal"):
        super().__init__(seed, distribution)
        self._rows = 16
        self._vals: np.ndarray | None = None
        self._index = 0

    def reset(self) -> None:
        self._vals = None
        self._index = 0

    def generate(self, frames: int, channels: int) -> np.ndarray:
        out = np.empty((frames, channels), dtype=np.float32)
        if self._vals is None or self._vals.shape[1] != channels:
            self._vals = self._random((self._rows, channels)).astype(np.float32)
            self._index = 0
        for i in range(frames):
            self._index += 1
            row = (self._index & -self._index).bit_length() - 1
            if row >= self._rows:
                self._index = 1
                row = 0
            self._vals[row] = self._random(channels).astype(np.float32)
            out[i] = self._vals.sum(axis=0) / np.sqrt(self._rows)
        return out


class TuneSource(NoiseSource):
    """A simple test tone (440 Hz sine)."""

    def __init__(self, seed: int | None = None, sample_rate: float = 44100.0, frequency: float = 440.0):
        super().__init__(seed)
        self.sample_rate = sample_rate
        self.frequency = frequency
        self._phase = 0.0

    def reset(self) -> None:
        self._phase = 0.0

    def generate(self, frames: int, channels: int) -> np.ndarray:
        t = np.arange(frames, dtype=np.float32) / self.sample_rate
        phase = self._phase + 2 * np.pi * self.frequency * t
        samples = np.sin(phase).astype(np.float32)
        self._phase = float(phase[-1]) + 2 * np.pi * self.frequency / self.sample_rate
        return np.tile(samples[:, np.newaxis], (1, channels))


def generate_noise(
    noise_type: str,
    frames: int,
    channels: int,
    sample_rate: int = 44100,
    seed: int | None = None,
    distribution: str = "normal",
    leak: float = 0.99,
    frequency: float = 440.0,
) -> np.ndarray:
    """Generate a complete noise clip, resetting source state for determinism."""
    source_map = {
        "brown": lambda: BrownNoiseSource(seed=seed, leak=leak, distribution=distribution),
        "white": lambda: WhiteNoiseSource(seed=seed, distribution=distribution),
        "pink": lambda: PinkNoiseSource(seed=seed, distribution=distribution),
        "tune": lambda: TuneSource(seed=seed, sample_rate=sample_rate, frequency=frequency),
    }
    source_cls = source_map.get(noise_type, source_map["brown"])
    source = source_cls()
    source.reset()
    samples = source.generate(frames, channels)
    peak = np.max(np.abs(samples))
    if peak > 1.0:
        samples = samples / peak
    return samples

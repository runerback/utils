import numpy as np


def apply_gain(buffer: np.ndarray, gain: float) -> np.ndarray:
    return np.clip(buffer * gain, -1.0, 1.0)


def apply_widening(buffer: np.ndarray, width: float, sample_rate: int = 44100) -> np.ndarray:
    """Widen stereo image by mixing delayed opposite channel."""
    if buffer.shape[1] != 2 or width <= 0:
        return buffer
    delay_samples = max(1, int(0.005 * sample_rate))
    left, right = buffer[:, 0], buffer[:, 1]
    delayed_right = np.concatenate((np.zeros(delay_samples, dtype=buffer.dtype), right[:-delay_samples]))
    delayed_left = np.concatenate((np.zeros(delay_samples, dtype=buffer.dtype), left[:-delay_samples]))
    out = np.empty_like(buffer)
    out[:, 0] = left * (1 - width) + delayed_right * width
    out[:, 1] = right * (1 - width) + delayed_left * width
    return np.clip(out, -1.0, 1.0)


def apply_saturation(buffer: np.ndarray, amount: float) -> np.ndarray:
    """Gentle arctan saturation that rounds sharp peaks instead of hard clipping."""
    if amount <= 0:
        return buffer
    drive = 1.0 + amount * 9.0
    return (2.0 / np.pi) * np.arctan(drive * buffer)


class DCBlocker:
    """First-order high-pass filter to remove DC drift from integrated noise."""

    def __init__(self, sample_rate: int, channels: int, cutoff: float = 5.0):
        self.sample_rate = sample_rate
        self.channels = channels
        self.cutoff = cutoff
        self._state = np.zeros(channels, dtype=np.float32)
        self._prev_in = np.zeros(channels, dtype=np.float32)
        rc = 1.0 / (2.0 * np.pi * cutoff)
        dt = 1.0 / sample_rate
        self._alpha = rc / (rc + dt)

    def process(self, buffer: np.ndarray) -> np.ndarray:
        out = np.empty_like(buffer)
        for i in range(buffer.shape[0]):
            self._state = self._alpha * (self._state + buffer[i] - self._prev_in)
            self._prev_in = buffer[i]
            out[i] = self._state
        return out


class SimpleReverb:
    """Lightweight Schroeder-style reverb implemented with numpy ring buffers."""

    def __init__(self, sample_rate: int, channels: int, amount: float = 0.0):
        self.sample_rate = sample_rate
        self.channels = channels
        self.amount = amount
        if amount <= 0:
            return
        comb_delays = [0.03, 0.037, 0.043, 0.05]
        self.comb_delays = [int(d * sample_rate) for d in comb_delays]
        self.comb_buffers = [np.zeros((d, channels), dtype=np.float32) for d in self.comb_delays]
        self.comb_indices = [0] * len(self.comb_delays)
        self.comb_feedback = [0.805, 0.827, 0.783, 0.764]
        allpass_delays = [0.005, 0.0017]
        self.allpass_delays = [int(d * sample_rate) for d in allpass_delays]
        self.allpass_buffers = [np.zeros((d, channels), dtype=np.float32) for d in self.allpass_delays]
        self.allpass_indices = [0] * len(self.allpass_delays)
        self.allpass_feedback = 0.7

    def _comb_step(self, buffer: np.ndarray) -> np.ndarray:
        out = np.zeros_like(buffer)
        for delay, mem, idx, fb in zip(self.comb_delays, self.comb_buffers, self.comb_indices, self.comb_feedback):
            for i in range(buffer.shape[0]):
                delayed = mem[idx]
                mem[idx] = buffer[i] + delayed * fb
                idx = (idx + 1) % delay
                out[i] += delayed
            self.comb_indices[self.comb_delays.index(delay)] = idx
        return out

    def _allpass_step(self, buffer: np.ndarray) -> np.ndarray:
        out = buffer.copy()
        for delay, mem, idx in zip(self.allpass_delays, self.allpass_buffers, self.allpass_indices):
            for i in range(out.shape[0]):
                delayed = mem[idx]
                mem[idx] = out[i] + delayed * self.allpass_feedback
                out[i] = delayed - out[i] * self.allpass_feedback
                idx = (idx + 1) % delay
            self.allpass_indices[self.allpass_delays.index(delay)] = idx
        return out

    def process(self, buffer: np.ndarray) -> np.ndarray:
        if self.amount <= 0:
            return buffer
        dry = buffer
        wet = self._comb_step(buffer)
        wet = self._allpass_step(wet)
        max_val = np.max(np.abs(wet))
        if max_val > 1e-6:
            wet = wet / max_val
        out = dry * (1 - self.amount) + wet * self.amount
        return np.clip(out, -1.0, 1.0)


def apply_reverb(buffer: np.ndarray, sample_rate: int, amount: float) -> np.ndarray:
    if amount <= 0:
        return buffer
    reverb = SimpleReverb(sample_rate, buffer.shape[1], amount)
    return reverb.process(buffer)


class LowPassFilter:
    """Simple first-order IIR low-pass filter with per-channel state."""

    def __init__(self, sample_rate: int, channels: int, cutoff: float = 20000.0):
        self.sample_rate = sample_rate
        self.channels = channels
        self.cutoff = cutoff
        self._state: np.ndarray | None = None
        self._alpha = self._compute_alpha(cutoff)

    def _compute_alpha(self, cutoff: float) -> float:
        rc = 1.0 / (2.0 * np.pi * max(cutoff, 1.0))
        dt = 1.0 / self.sample_rate
        return dt / (rc + dt)

    def set_cutoff(self, cutoff: float) -> None:
        self.cutoff = cutoff
        self._alpha = self._compute_alpha(cutoff)

    def process(self, buffer: np.ndarray) -> np.ndarray:
        if self.cutoff >= self.sample_rate / 2:
            return buffer
        if self._state is None or self._state.shape[0] != self.channels:
            self._state = np.zeros(self.channels, dtype=np.float32)
        out = np.empty_like(buffer)
        for i in range(buffer.shape[0]):
            self._state = self._alpha * buffer[i] + (1.0 - self._alpha) * self._state
            out[i] = self._state
        return out


class ButterworthLowPass:
    """Second-order Butterworth low-pass filter for a smooth, rounded rolloff."""

    def __init__(self, sample_rate: int, channels: int, cutoff: float = 20000.0):
        self.sample_rate = sample_rate
        self.channels = channels
        self.cutoff = cutoff
        self._state1 = np.zeros(channels, dtype=np.float32)
        self._state2 = np.zeros(channels, dtype=np.float32)
        self._a1 = 0.0
        self._a2 = 0.0
        self._b0 = 1.0
        self._b1 = 0.0
        self._b2 = 0.0
        self._compute_coeffs(cutoff)

    def _compute_coeffs(self, cutoff: float) -> None:
        cutoff = max(1.0, min(cutoff, self.sample_rate / 2.0 - 1.0))
        c = 1.0 / np.tan(np.pi * cutoff / self.sample_rate)
        c2 = c * c
        a0 = 1.0 + np.sqrt(2.0) * c + c2
        self._b0 = 1.0 / a0
        self._b1 = 2.0 * self._b0
        self._b2 = self._b0
        self._a1 = (2.0 * (1.0 - c2)) / a0
        self._a2 = (1.0 - np.sqrt(2.0) * c + c2) / a0

    def set_cutoff(self, cutoff: float) -> None:
        self.cutoff = cutoff
        self._compute_coeffs(cutoff)

    def process(self, buffer: np.ndarray) -> np.ndarray:
        if self.cutoff >= self.sample_rate / 2:
            return buffer
        out = np.empty_like(buffer)
        for i in range(buffer.shape[0]):
            x = buffer[i]
            y = self._b0 * x + self._b1 * self._state1 + self._b2 * self._state2
            y = y - self._a1 * self._state1 - self._a2 * self._state2
            self._state2 = self._state1.copy()
            self._state1 = y
            out[i] = y
        return out


def apply_lowpass(buffer: np.ndarray, sample_rate: int, cutoff: float) -> np.ndarray:
    if cutoff >= sample_rate / 2:
        return buffer
    filt = ButterworthLowPass(sample_rate, buffer.shape[1], cutoff)
    return filt.process(buffer)


def cutoff_for_softness(softness: float, sample_rate: int) -> float:
    """Map softness 0..1 to a cutoff frequency."""
    if softness <= 0:
        return sample_rate / 2.0
    return max(200.0, 20000.0 * (0.01 ** softness))


def apply_wave_lfo(
    buffer: np.ndarray,
    sample_rate: int,
    rate: float,
    seed: int | None = None,
) -> np.ndarray:
    """Apply sine-wave amplitude modulation. Phase is deterministic for a given seed."""
    frames = buffer.shape[0]
    t = np.arange(frames, dtype=np.float32) / sample_rate
    rng = np.random.default_rng(seed)
    phase_offset = rng.random() * 2 * np.pi if seed is not None else 0.0
    lfo = 0.5 + 0.5 * np.sin(2 * np.pi * rate * t + phase_offset)
    return buffer * lfo[:, np.newaxis]

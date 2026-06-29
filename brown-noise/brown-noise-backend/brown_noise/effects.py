import numpy as np


def apply_gain(buffer: np.ndarray, gain: float) -> np.ndarray:
    return np.clip(buffer * gain, -1.0, 1.0)


def apply_widening(buffer: np.ndarray, width: float, sample_rate: int = 44100) -> np.ndarray:
    """Widen stereo image by mixing delayed opposite channel."""
    if buffer.shape[1] != 2 or width <= 0:
        return buffer
    delay_samples = max(1, int(0.005 * sample_rate))  # 5 ms delay
    left, right = buffer[:, 0], buffer[:, 1]
    delayed_right = np.concatenate((np.zeros(delay_samples, dtype=buffer.dtype), right[:-delay_samples]))
    delayed_left = np.concatenate((np.zeros(delay_samples, dtype=buffer.dtype), left[:-delay_samples]))
    out = np.empty_like(buffer)
    out[:, 0] = left * (1 - width) + delayed_right * width
    out[:, 1] = right * (1 - width) + delayed_left * width
    return np.clip(out, -1.0, 1.0)


class SimpleReverb:
    """Lightweight Schroeder-style reverb implemented with numpy ring buffers."""

    def __init__(self, sample_rate: int, channels: int, amount: float = 0.0):
        self.sample_rate = sample_rate
        self.channels = channels
        self.amount = amount
        if amount <= 0:
            return
        # Comb filter delays in seconds
        comb_delays = [0.03, 0.037, 0.043, 0.05]
        self.comb_delays = [int(d * sample_rate) for d in comb_delays]
        self.comb_buffers = [np.zeros((d, channels), dtype=np.float32) for d in self.comb_delays]
        self.comb_indices = [0] * len(self.comb_delays)
        self.comb_feedback = [0.805, 0.827, 0.783, 0.764]
        # All-pass delays
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
        # Normalize wet signal roughly
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
        # First-order low-pass: y[n] = alpha * x[n] + (1 - alpha) * y[n-1]
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


def apply_lowpass(buffer: np.ndarray, sample_rate: int, cutoff: float) -> np.ndarray:
    if cutoff >= sample_rate / 2:
        return buffer
    filt = LowPassFilter(sample_rate, buffer.shape[1], cutoff)
    return filt.process(buffer)

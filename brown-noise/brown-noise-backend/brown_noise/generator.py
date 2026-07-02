import queue
import struct
import threading
import time
import numpy as np

from .config import AudioConfig
from .effects import ButterworthLowPass, DCBlocker, SimpleReverb, apply_gain, apply_saturation, apply_widening
from .sources import BrownNoiseSource, NoiseSource, PinkNoiseSource, TuneSource, WhiteNoiseSource


def _downsample_waveform(buffer: np.ndarray, points: int = 1024) -> list[float]:
    if buffer.size == 0:
        return [0.0] * points
    mono = buffer if buffer.ndim == 1 else np.mean(buffer, axis=1)
    if len(mono) <= points:
        return mono.tolist()
    bins = np.array_split(mono, points)
    return [float(np.mean(b)) for b in bins]


class AudioGenerator:
    def __init__(self, config: AudioConfig):
        self.config = config
        self.audio_queue: queue.Queue[bytes] = queue.Queue(maxsize=5)
        self._clients: dict[int, queue.Queue[bytes]] = {}
        self._client_lock = threading.Lock()
        self._client_event = threading.Event()
        self._stats_event = threading.Event()
        self._lock = threading.Lock()
        self._next_client_id = 0
        self._running = False
        self._thread: threading.Thread | None = None
        self._reverb: SimpleReverb | None = None
        self._lowpass: ButterworthLowPass | None = None
        self._dcblocker: DCBlocker | None = None
        self._source: NoiseSource = BrownNoiseSource()
        self._wave_time = 0.0
        self._saturation = 0.3
        self._node_waveforms: dict[str, list[float]] = {}
        self._gain_history: np.ndarray = np.zeros(0, dtype=np.float32)
        self._waveform_history_chunks = 30
        self._apply_config()

    def _cutoff_for_softness(self, softness: float) -> float:
        # softness 0 = no filtering, 1 = very soft (200 Hz cutoff)
        if softness <= 0:
            return self.config.sample_rate / 2.0
        # Exponential curve: 0.5 already rolls off to ~2 kHz for sleep-friendly sound
        return max(200.0, 20000.0 * (0.01 ** softness))

    def _apply_config(self) -> None:
        with self._lock:
            source_map: dict[str, type[NoiseSource]] = {
                "brown": BrownNoiseSource,
                "white": WhiteNoiseSource,
                "pink": PinkNoiseSource,
                "tune": TuneSource,
            }
            source_cls = source_map.get(self.config.noise_type, BrownNoiseSource)
            kwargs: dict = {"seed": self.config.seed}
            if self.config.noise_type == "brown":
                kwargs["leak"] = self.config.leak
            elif self.config.noise_type == "tune":
                kwargs["sample_rate"] = self.config.sample_rate
            self._source = source_cls(**kwargs)
            self._reverb = SimpleReverb(
                self.config.sample_rate, self.config.channels, self.config.reverb
            ) if self.config.reverb > 0 else None
            cutoff = self._cutoff_for_softness(self.config.softness)
            self._lowpass = ButterworthLowPass(self.config.sample_rate, self.config.channels, cutoff)
            self._dcblocker = DCBlocker(self.config.sample_rate, self.config.channels)

    def update_config(
        self,
        *,
        noise_type: str | None = None,
        leak: float | None = None,
        gain: float | None = None,
        surround: float | None = None,
        reverb: float | None = None,
        softness: float | None = None,
        wave: bool | None = None,
        wave_rate: float | None = None,
        bypass_source: bool | None = None,
        bypass_widening: bool | None = None,
        bypass_reverb: bool | None = None,
        bypass_lowpass: bool | None = None,
        bypass_dcblocker: bool | None = None,
        bypass_saturation: bool | None = None,
        bypass_wave: bool | None = None,
        bypass_gain: bool | None = None,
    ) -> None:
        with self._lock:
            if noise_type is not None:
                self.config.noise_type = noise_type
            if leak is not None:
                self.config.leak = leak
            if gain is not None:
                self.config.gain = gain
            if surround is not None:
                self.config.surround = surround
            if reverb is not None:
                self.config.reverb = reverb
            if softness is not None:
                self.config.softness = softness
            if wave is not None:
                self.config.wave = wave
            if wave_rate is not None:
                self.config.wave_rate = wave_rate
            if bypass_source is not None:
                self.config.bypass_source = bypass_source
            if bypass_widening is not None:
                self.config.bypass_widening = bypass_widening
            if bypass_reverb is not None:
                self.config.bypass_reverb = bypass_reverb
            if bypass_lowpass is not None:
                self.config.bypass_lowpass = bypass_lowpass
            if bypass_dcblocker is not None:
                self.config.bypass_dcblocker = bypass_dcblocker
            if bypass_saturation is not None:
                self.config.bypass_saturation = bypass_saturation
            if bypass_wave is not None:
                self.config.bypass_wave = bypass_wave
            if bypass_gain is not None:
                self.config.bypass_gain = bypass_gain
        self._apply_config()

    def register_client(self) -> queue.Queue[bytes]:
        with self._client_lock:
            client_id = self._next_client_id
            self._next_client_id += 1
            client_queue: queue.Queue[bytes] = queue.Queue(maxsize=10)
            self._clients[client_id] = client_queue
        self._client_event.set()
        self._stats_event.set()
        return client_queue

    def unregister_client(self, client_queue: queue.Queue[bytes]) -> None:
        with self._client_lock:
            for cid, q in list(self._clients.items()):
                if q is client_queue:
                    del self._clients[cid]
                    break
        self._stats_event.set()

    def start(self) -> None:
        self._running = True
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._running = False
        self._client_event.set()
        self._stats_event.set()
        if self._thread:
            self._thread.join(timeout=2.0)

    def _run(self) -> None:
        frames_per_chunk = self.config.chunk_size
        seconds_per_chunk = frames_per_chunk / self.config.sample_rate
        next_time = time.perf_counter()
        int16_max = np.float32(32767.0)

        while self._running:
            with self._client_lock:
                has_clients = bool(self._clients)
                if not has_clients:
                    self._client_event.clear()
            if not has_clients:
                self._client_event.wait()
                next_time = time.perf_counter()
                continue

            with self._lock:
                source = self._source
                reverb = self._reverb
                lowpass = self._lowpass
                dcblocker = self._dcblocker
                gain = self.config.gain
                surround = self.config.surround
                sample_rate = self.config.sample_rate
                channels = self.config.channels
                wave = self.config.wave
                wave_rate = self.config.wave_rate
                bypass_source = self.config.bypass_source
                bypass_widening = self.config.bypass_widening
                bypass_reverb = self.config.bypass_reverb
                bypass_lowpass = self.config.bypass_lowpass
                bypass_dcblocker = self.config.bypass_dcblocker
                bypass_saturation = self.config.bypass_saturation
                bypass_wave = self.config.bypass_wave
                bypass_gain = self.config.bypass_gain

            raw = source.generate(frames_per_chunk, channels) if not bypass_source else np.zeros((frames_per_chunk, channels), dtype=np.float32)
            waveforms: dict[str, list[float]] = {}
            waveforms["source"] = _downsample_waveform(raw)
            if surround > 0 and not bypass_widening:
                raw = apply_widening(raw, surround, sample_rate)
            waveforms["widening"] = _downsample_waveform(raw)
            if reverb is not None and not bypass_reverb:
                raw = reverb.process(raw)
            waveforms["reverb"] = _downsample_waveform(raw)
            if lowpass is not None and not bypass_lowpass:
                raw = lowpass.process(raw)
            waveforms["lowpass"] = _downsample_waveform(raw)
            if dcblocker is not None and not bypass_dcblocker:
                raw = dcblocker.process(raw)
            waveforms["dcblocker"] = _downsample_waveform(raw)
            if not bypass_saturation:
                raw = apply_saturation(raw, self._saturation)
            waveforms["saturation"] = _downsample_waveform(raw)
            if wave and not bypass_wave:
                t = np.arange(frames_per_chunk, dtype=np.float32) / sample_rate + self._wave_time
                lfo = 0.5 + 0.5 * np.sin(2 * np.pi * wave_rate * t)
                raw = raw * lfo[:, np.newaxis]
                self._wave_time = float(t[-1]) + 1.0 / sample_rate
            waveforms["wave"] = _downsample_waveform(raw)
            if not bypass_gain:
                raw = apply_gain(raw, gain)
            waveforms["gain"] = _downsample_waveform(raw)
            gain_mono = np.mean(raw, axis=1)

            with self._lock:
                self._node_waveforms = waveforms
                history = np.concatenate((self._gain_history, gain_mono))
                max_history = self._waveform_history_chunks * frames_per_chunk
                self._gain_history = history[-max_history:]

            pcm = (raw * int16_max).astype(np.int16)
            interleaved = pcm.reshape(-1)
            data = interleaved.tobytes()
            packet = struct.pack("<I", len(data)) + data

            try:
                self.audio_queue.put(packet, timeout=0.1)
            except queue.Full:
                try:
                    self.audio_queue.get_nowait()
                except queue.Empty:
                    pass
                try:
                    self.audio_queue.put(packet, timeout=0.1)
                except queue.Full:
                    pass

            with self._client_lock:
                clients = list(self._clients.values())
            for q in clients:
                try:
                    q.put_nowait(packet)
                except queue.Full:
                    try:
                        q.get_nowait()
                    except queue.Empty:
                        pass
                    try:
                        q.put_nowait(packet)
                    except queue.Full:
                        pass

            next_time += seconds_per_chunk
            sleep_time = next_time - time.perf_counter()
            if sleep_time > 0:
                time.sleep(sleep_time)

    def get_stats(self) -> dict:
        with self._client_lock:
            return {
                "clients": len(self._clients),
                "audio_queue": self.audio_queue.qsize(),
            }

    def get_node_waveforms(self) -> dict[str, list[float]]:
        with self._lock:
            return dict(self._node_waveforms)

    def get_visible_waveforms(self) -> dict[str, list[float]]:
        with self._lock:
            gain = self._gain_history
            return {"gain": _downsample_waveform(gain)} if gain.size else {}

    def wait_for_stats_change(self, timeout: float | None = None) -> bool:
        if self._stats_event.wait(timeout):
            self._stats_event.clear()
            return True
        return False

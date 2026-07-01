import queue
import struct
import threading
import time
import numpy as np

from .config import AudioConfig
from .effects import ButterworthLowPass, DCBlocker, SimpleReverb, apply_gain, apply_saturation, apply_widening
from .sources import BrownNoiseSource, NoiseSource, PinkNoiseSource, TuneSource, WhiteNoiseSource


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

            raw = source.generate(frames_per_chunk, channels)
            if surround > 0:
                raw = apply_widening(raw, surround, sample_rate)
            if reverb is not None:
                raw = reverb.process(raw)
            if lowpass is not None:
                raw = lowpass.process(raw)
            if dcblocker is not None:
                raw = dcblocker.process(raw)
            raw = apply_saturation(raw, self._saturation)
            if wave:
                t = np.arange(frames_per_chunk, dtype=np.float32) / sample_rate + self._wave_time
                lfo = 0.5 + 0.5 * np.sin(2 * np.pi * wave_rate * t)
                raw = raw * lfo[:, np.newaxis]
                self._wave_time = float(t[-1]) + 1.0 / sample_rate
            raw = apply_gain(raw, gain)

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

    def wait_for_stats_change(self, timeout: float | None = None) -> bool:
        if self._stats_event.wait(timeout):
            self._stats_event.clear()
            return True
        return False

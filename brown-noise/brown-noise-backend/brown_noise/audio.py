import queue
import struct

import sounddevice as sd
import numpy as np

from .config import AudioConfig
from .generator import AudioGenerator


class LocalAudioPlayer:
    def __init__(self, config: AudioConfig, generator: AudioGenerator):
        self.config = config
        self.generator = generator
        self._stream: sd.RawOutputStream | None = None

    def start(self) -> None:
        self._stream = sd.RawOutputStream(
            samplerate=self.config.sample_rate,
            channels=self.config.channels,
            dtype=np.int16,
            blocksize=self.config.chunk_size,
            callback=self._callback,
        )
        self._stream.start()

    def stop(self) -> None:
        if self._stream:
            self._stream.stop()
            self._stream.close()
            self._stream = None

    def _callback(self, outdata, frames: int, time_info, status) -> None:
        out = np.frombuffer(outdata, dtype=np.int16).reshape(frames, self.config.channels)
        try:
            packet = self.generator.audio_queue.get_nowait()
            length = struct.unpack("<I", packet[:4])[0]
            data = packet[4:4 + length]
            expected = frames * self.config.channels * 2
            if len(data) >= expected:
                out[:] = np.frombuffer(data[:expected], dtype=np.int16).reshape(
                    frames, self.config.channels
                )
            else:
                out.fill(0)
        except queue.Empty:
            out.fill(0)
        except Exception:
            out.fill(0)

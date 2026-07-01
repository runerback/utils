from dataclasses import dataclass


@dataclass
class AudioConfig:
    host: str = "0.0.0.0"
    port: int = 54545
    control_port: int = 54546
    sample_rate: int = 44100
    channels: int = 2
    chunk_size: int = 1024
    gain: float = 0.5
    noise_type: str = "brown"
    leak: float = 0.99
    seed: int | None = None
    surround: float = 0.0
    reverb: float = 0.0
    softness: float = 0.6
    wave: bool = False
    wave_rate: float = 0.5
    bypass_source: bool = False
    bypass_widening: bool = False
    bypass_reverb: bool = False
    bypass_lowpass: bool = False
    bypass_dcblocker: bool = False
    bypass_saturation: bool = False
    bypass_wave: bool = False
    bypass_gain: bool = False
    web_port: int = 8080

import torch
import numpy as np
from typing_extensions import override
from comfy_api.latest import ComfyExtension, io

from .noise.sources import generate_noise
from .noise.effects import (
    apply_gain,
    apply_widening,
    apply_saturation,
    apply_wave_lfo,
    cutoff_for_softness,
    DCBlocker,
    SimpleReverb,
    ButterworthLowPass,
)


def _audio_to_numpy(audio: dict) -> tuple[np.ndarray, int]:
    """Convert ComfyUI AUDIO dict to numpy [samples, channels]."""
    waveform = audio["waveform"]
    if isinstance(waveform, torch.Tensor):
        waveform = waveform.detach().cpu()
    arr = waveform.numpy()
    if arr.ndim == 3:
        arr = arr.squeeze(0)
    # ComfyUI waveform is [channels, samples]
    if arr.shape[0] <= 8:
        arr = arr.transpose(1, 0)
    return arr.astype(np.float32), audio["sample_rate"]


def _numpy_to_audio(arr: np.ndarray, sample_rate: int) -> dict:
    """Convert numpy [samples, channels] to ComfyUI AUDIO dict."""
    if arr.dtype != np.float32:
        arr = arr.astype(np.float32)
    # Ensure shape [samples, channels]
    if arr.ndim == 1:
        arr = arr[:, np.newaxis]
    waveform = torch.from_numpy(arr).permute(1, 0).unsqueeze(0)
    return {"waveform": waveform, "sample_rate": sample_rate}


class AudioLength(io.ComfyNode):
    @classmethod
    def define_schema(cls) -> io.Schema:
        return io.Schema(
            node_id="Noises-AudioLength",
            display_name="Audio Length",
            category="audio/noises",
            inputs=[
                io.Float.Input(
                    "seconds",
                    default=5.0,
                    min=0.1,
                    max=3600.0,
                    step=0.1,
                ),
                io.Int.Input(
                    "sample_rate",
                    default=44100,
                    min=8000,
                    max=192000,
                    step=100,
                ),
            ],
            outputs=[
                io.Int.Output("samples"),
                io.Float.Output("seconds"),
            ],
        )

    @classmethod
    def execute(cls, seconds, sample_rate) -> io.NodeOutput:
        samples = round(seconds * sample_rate)
        return io.NodeOutput(samples, seconds)


class NoiseSource(io.ComfyNode):
    @classmethod
    def define_schema(cls) -> io.Schema:
        return io.Schema(
            node_id="Noises-NoiseSource",
            display_name="Noise Source",
            category="audio/noises",
            inputs=[
                io.Combo.Input(
                    "noise_type",
                    options=["brown", "white", "pink", "tune"],
                    default="brown",
                ),
                io.Float.Input(
                    "length_seconds",
                    default=5.0,
                    min=0.1,
                    max=3600.0,
                    step=0.1,
                ),
                io.Int.Input(
                    "sample_rate",
                    default=44100,
                    min=8000,
                    max=192000,
                    step=100,
                ),
                io.Int.Input(
                    "channels",
                    default=2,
                    min=1,
                    max=8,
                    step=1,
                ),
                io.Int.Input(
                    "seed",
                    default=0,
                    min=0,
                    max=0xFFFFFFFFFFFFFFFF,
                    step=1,
                ),
                io.Combo.Input(
                    "distribution",
                    options=["normal", "uniform", "laplace"],
                    default="normal",
                ),
                io.Float.Input(
                    "leak",
                    default=0.99,
                    min=0.0,
                    max=0.9999,
                    step=0.001,
                ),
                io.Float.Input(
                    "frequency",
                    default=440.0,
                    min=20.0,
                    max=20000.0,
                    step=1.0,
                ),
            ],
            outputs=[
                io.Audio.Output("audio"),
            ],
        )

    @classmethod
    def execute(
        cls,
        noise_type,
        length_seconds,
        sample_rate,
        channels,
        seed,
        distribution,
        leak,
        frequency,
    ) -> io.NodeOutput:
        frames = round(length_seconds * sample_rate)
        samples = generate_noise(
            noise_type=noise_type,
            frames=frames,
            channels=channels,
            sample_rate=sample_rate,
            seed=seed,
            distribution=distribution,
            leak=leak,
            frequency=frequency,
        )
        audio = _numpy_to_audio(samples, sample_rate)
        return io.NodeOutput(audio)


class Widening(io.ComfyNode):
    @classmethod
    def define_schema(cls) -> io.Schema:
        return io.Schema(
            node_id="Noises-StereoWidening",
            display_name="Stereo Widening",
            category="audio/noises",
            inputs=[
                io.Audio.Input("audio"),
                io.Float.Input("width", default=0.5, min=0.0, max=1.0, step=0.01),
            ],
            outputs=[io.Audio.Output("audio")],
        )

    @classmethod
    def execute(cls, audio, width) -> io.NodeOutput:
        arr, sample_rate = _audio_to_numpy(audio)
        out = apply_widening(arr, width, sample_rate)
        return io.NodeOutput(_numpy_to_audio(out, sample_rate))


class Reverb(io.ComfyNode):
    @classmethod
    def define_schema(cls) -> io.Schema:
        return io.Schema(
            node_id="Noises-Reverb",
            display_name="Reverb",
            category="audio/noises",
            inputs=[
                io.Audio.Input("audio"),
                io.Float.Input("amount", default=0.3, min=0.0, max=1.0, step=0.01),
            ],
            outputs=[io.Audio.Output("audio")],
        )

    @classmethod
    def execute(cls, audio, amount) -> io.NodeOutput:
        arr, sample_rate = _audio_to_numpy(audio)
        reverb = SimpleReverb(sample_rate, arr.shape[1], amount)
        out = reverb.process(arr)
        return io.NodeOutput(_numpy_to_audio(out, sample_rate))


class LowPass(io.ComfyNode):
    @classmethod
    def define_schema(cls) -> io.Schema:
        return io.Schema(
            node_id="Noises-LowPass",
            display_name="Low Pass Filter",
            category="audio/noises",
            inputs=[
                io.Audio.Input("audio"),
                io.Float.Input(
                    "softness",
                    default=0.6,
                    min=0.0,
                    max=1.0,
                    step=0.01,
                    tooltip="0 = no filtering, 1 = very soft (200 Hz cutoff)",
                ),
            ],
            outputs=[io.Audio.Output("audio")],
        )

    @classmethod
    def execute(cls, audio, softness) -> io.NodeOutput:
        arr, sample_rate = _audio_to_numpy(audio)
        cutoff = cutoff_for_softness(softness, sample_rate)
        lowpass = ButterworthLowPass(sample_rate, arr.shape[1], cutoff)
        out = lowpass.process(arr)
        return io.NodeOutput(_numpy_to_audio(out, sample_rate))


class DCBlockerNode(io.ComfyNode):
    @classmethod
    def define_schema(cls) -> io.Schema:
        return io.Schema(
            node_id="Noises-DCBlocker",
            display_name="DC Blocker",
            category="audio/noises",
            inputs=[
                io.Audio.Input("audio"),
            ],
            outputs=[io.Audio.Output("audio")],
        )

    @classmethod
    def execute(cls, audio) -> io.NodeOutput:
        arr, sample_rate = _audio_to_numpy(audio)
        blocker = DCBlocker(sample_rate, arr.shape[1])
        out = blocker.process(arr)
        return io.NodeOutput(_numpy_to_audio(out, sample_rate))


class Saturation(io.ComfyNode):
    @classmethod
    def define_schema(cls) -> io.Schema:
        return io.Schema(
            node_id="Noises-Saturation",
            display_name="Saturation",
            category="audio/noises",
            inputs=[
                io.Audio.Input("audio"),
                io.Float.Input("amount", default=0.3, min=0.0, max=1.0, step=0.01),
            ],
            outputs=[io.Audio.Output("audio")],
        )

    @classmethod
    def execute(cls, audio, amount) -> io.NodeOutput:
        arr, sample_rate = _audio_to_numpy(audio)
        out = apply_saturation(arr, amount)
        return io.NodeOutput(_numpy_to_audio(out, sample_rate))


class WaveLFO(io.ComfyNode):
    @classmethod
    def define_schema(cls) -> io.Schema:
        return io.Schema(
            node_id="Noises-WaveLFO",
            display_name="Wave LFO",
            category="audio/noises",
            inputs=[
                io.Audio.Input("audio"),
                io.Float.Input("rate", default=0.5, min=0.01, max=20.0, step=0.01),
                io.Int.Input(
                    "seed",
                    default=0,
                    min=0,
                    max=0xFFFFFFFFFFFFFFFF,
                    step=1,
                    optional=True,
                ),
            ],
            outputs=[io.Audio.Output("audio")],
        )

    @classmethod
    def execute(cls, audio, rate, seed=0) -> io.NodeOutput:
        arr, sample_rate = _audio_to_numpy(audio)
        seed_value = seed if seed is not None else None
        out = apply_wave_lfo(arr, sample_rate, rate, seed=seed_value)
        return io.NodeOutput(_numpy_to_audio(out, sample_rate))


class Gain(io.ComfyNode):
    @classmethod
    def define_schema(cls) -> io.Schema:
        return io.Schema(
            node_id="Noises-Gain",
            display_name="Gain",
            category="audio/noises",
            inputs=[
                io.Audio.Input("audio"),
                io.Float.Input("gain", default=0.5, min=0.0, max=2.0, step=0.01),
            ],
            outputs=[io.Audio.Output("audio")],
        )

    @classmethod
    def execute(cls, audio, gain) -> io.NodeOutput:
        arr, sample_rate = _audio_to_numpy(audio)
        out = apply_gain(arr, gain)
        return io.NodeOutput(_numpy_to_audio(out, sample_rate))

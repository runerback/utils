"""Basic tests for ComfyUI-Noises DSP and nodes."""

import sys
import types
import importlib.util
from pathlib import Path

pkg_dir = Path(__file__).parent.parent
comfy_root = pkg_dir.parent.parent
sys.path.insert(0, str(comfy_root))   # ComfyUI root (for comfy_api)
sys.path.insert(0, str(pkg_dir.parent))  # custom_nodes
sys.path.insert(0, str(pkg_dir))         # ComfyUI-Noises package

# Import the DSP subpackage directly (it has no hyphen).
import noise.sources as sources
import noise.effects as effects

# Build a fake parent package so nodes.py can use relative imports.
pkg = types.ModuleType("ComfyUI_Noises")
pkg.__path__ = [str(pkg_dir)]
sys.modules["ComfyUI_Noises"] = pkg

noise_pkg = types.ModuleType("ComfyUI_Noises.noise")
noise_pkg.sources = sources
noise_pkg.effects = effects
sys.modules["ComfyUI_Noises.noise"] = noise_pkg
sys.modules["ComfyUI_Noises.noise.sources"] = sources
sys.modules["ComfyUI_Noises.noise.effects"] = effects

spec = importlib.util.spec_from_file_location("ComfyUI_Noises.nodes", pkg_dir / "nodes.py")
nodes_mod = importlib.util.module_from_spec(spec)
sys.modules["ComfyUI_Noises.nodes"] = nodes_mod
spec.loader.exec_module(nodes_mod)

import numpy as np


def test_source_repeatability():
    a = sources.generate_noise("brown", 4410, 2, 44100, seed=42)
    b = sources.generate_noise("brown", 4410, 2, 44100, seed=42)
    assert np.allclose(a, b), "same seed should produce identical output"
    assert a.shape == (4410, 2)
    assert -1.0 <= a.min() <= a.max() <= 1.0


def test_noise_source_node():
    out = nodes_mod.NoiseSource.execute(
        noise_type="brown",
        length_seconds=0.1,
        sample_rate=44100,
        channels=2,
        seed=42,
        distribution="normal",
        leak=0.99,
        frequency=440.0,
    )
    audio = out.args[0]
    assert "waveform" in audio and "sample_rate" in audio
    assert audio["waveform"].shape == (1, 2, 4410)
    assert audio["sample_rate"] == 44100


def test_audio_length_node():
    out = nodes_mod.AudioLength.execute(seconds=0.5, sample_rate=44100)
    samples, seconds = out.args
    assert samples == 22050
    assert abs(seconds - 0.5) < 1e-6


def test_gain_node():
    source = nodes_mod.NoiseSource.execute(
        noise_type="white",
        length_seconds=0.1,
        sample_rate=44100,
        channels=2,
        seed=1,
        distribution="normal",
        leak=0.99,
        frequency=440.0,
    )
    original = source.args[0]["waveform"].numpy()
    out = nodes_mod.Gain.execute(source.args[0], gain=0.5)
    processed = out.args[0]["waveform"].numpy()
    assert processed.shape == original.shape
    assert np.allclose(processed, original * 0.5)


def test_full_chain():
    audio = nodes_mod.NoiseSource.execute(
        noise_type="pink",
        length_seconds=0.1,
        sample_rate=44100,
        channels=2,
        seed=123,
        distribution="normal",
        leak=0.99,
        frequency=440.0,
    ).args[0]
    audio = nodes_mod.Widening.execute(audio, width=0.5).args[0]
    audio = nodes_mod.Reverb.execute(audio, amount=0.3).args[0]
    audio = nodes_mod.LowPass.execute(audio, softness=0.6).args[0]
    audio = nodes_mod.DCBlockerNode.execute(audio).args[0]
    audio = nodes_mod.Saturation.execute(audio, amount=0.3).args[0]
    audio = nodes_mod.WaveLFO.execute(audio, rate=0.5, seed=0).args[0]
    audio = nodes_mod.Gain.execute(audio, gain=0.5).args[0]
    arr, sr = nodes_mod._audio_to_numpy(audio)
    assert arr.shape == (4410, 2)
    assert sr == 44100
    assert -1.0 <= arr.min() <= arr.max() <= 1.0


import asyncio


def test_schemas():
    async def _load():
        extension = nodes_mod.ComfyUINoisesExtension()
        return await extension.get_node_list()

    node_list = asyncio.run(_load())
    assert len(node_list) == 9
    for node_cls in node_list:
        schema = node_cls.define_schema()
        assert schema.node_id
        assert schema.display_name
        assert schema.category


if __name__ == "__main__":
    test_source_repeatability()
    print("test_source_repeatability passed")
    test_noise_source_node()
    print("test_noise_source_node passed")
    test_audio_length_node()
    print("test_audio_length_node passed")
    test_gain_node()
    print("test_gain_node passed")
    test_full_chain()
    print("test_full_chain passed")
    test_schemas()
    print("test_schemas passed")
    print("All tests passed.")

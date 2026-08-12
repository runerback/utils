from typing_extensions import override
from comfy_api.latest import ComfyExtension, io

from .nodes import (
    AudioLength,
    NoiseSource,
    Widening,
    Reverb,
    LowPass,
    DCBlockerNode,
    Saturation,
    WaveLFO,
    Gain,
)


class ComfyUINoisesExtension(ComfyExtension):
    @override
    async def get_node_list(self) -> list[type[io.ComfyNode]]:
        return [
            AudioLength,
            NoiseSource,
            Widening,
            Reverb,
            LowPass,
            DCBlockerNode,
            Saturation,
            WaveLFO,
            Gain,
        ]


async def comfy_entrypoint() -> ComfyUINoisesExtension:
    return ComfyUINoisesExtension()


__all__ = ["comfy_entrypoint"]

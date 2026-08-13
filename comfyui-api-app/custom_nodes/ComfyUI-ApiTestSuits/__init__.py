import hashlib
import time
import numpy as np
import torch
import torchvision.transforms.functional as TVF
import comfy.utils
import nodes
from PIL import Image
from typing_extensions import override
from comfy_api.latest import ComfyExtension, io


def _preview(image):
    """Send a legacy binary preview of the first image in the batch."""
    first = image[0]
    arr = np.clip(255.0 * first.cpu().numpy(), 0, 255).astype(np.uint8)
    pil_img = Image.fromarray(arr)
    pbar = comfy.utils.ProgressBar(1)
    pbar.update_absolute(1, 1, ("JPEG", pil_img, None))


def _ensure_min_duration(start_time: float, interval_ms: int):
    """Sleep only if image processing finished faster than the requested minimum duration."""
    if interval_ms <= 0:
        return
    elapsed_ms = (time.perf_counter() - start_time) * 1000.0
    remaining = interval_ms - elapsed_ms
    if remaining > 0:
        time.sleep(remaining / 1000.0)


def _generate_key_stream(seed_int: int, length: int) -> bytes:
    """Generate a deterministic byte stream of exactly `length` bytes.

    The stream is derived from `seed_int` via a hash chain using SHA-512.
    Each round hashes the seed concatenated with a 4-byte big-endian counter.
    """
    seed_bytes = str(seed_int).encode("utf-8")
    stream = bytearray()
    counter = 0

    while len(stream) < length:
        counter_bytes = counter.to_bytes(4, byteorder="big", signed=False)
        block = hashlib.sha512(seed_bytes + counter_bytes).digest()
        stream.extend(block)
        counter += 1

    return bytes(stream[:length])


def _transform_xor_array(arr: np.ndarray, number: int) -> np.ndarray:
    """Deterministic pixel-wise XOR transformation for uint8 numpy arrays.

    Args:
        arr: uint8 array of shape (H, W, C) or (B, H, W, C) with C == 3 or 4.
        number: Non-negative integer acting as the transformation key.

    Returns:
        A uint8 array of the same shape. Reversible: applying the same
        number twice returns the original array.
    """
    if arr.ndim == 3:
        h, w, c = arr.shape
        flat = arr.reshape(1, h, w, c)
    else:
        flat = arr

    b, h, w, c = flat.shape
    key_stream = _generate_key_stream(number, h * w * 3)
    key = np.frombuffer(key_stream, dtype=np.uint8).reshape((1, h, w, 3))

    out = flat.copy()
    out[..., :3] = np.bitwise_xor(out[..., :3], key)

    if arr.ndim == 3:
        return out.reshape(h, w, c)
    return out


class ATSResize(io.ComfyNode):
    @classmethod
    def define_schema(cls) -> io.Schema:
        return io.Schema(
            node_id="ATS-Resize",
            display_name="ATS-Resize",
            category="api_test_suits/image",
            description="Resize an image by percent or absolute size.",
            inputs=[
                io.Image.Input("image"),
                io.Int.Input(
                    "interval",
                    default=0,
                    min=0,
                    max=60000,
                    step=1,
                    optional=True,
                    force_input=True,
                ),
                io.DynamicCombo.Input(
                    "mode",
                    options=[
                        io.DynamicCombo.Option(
                            "percent",
                            [io.Int.Input("percent", default=50, min=1, max=100, step=1)],
                        ),
                        io.DynamicCombo.Option(
                            "size",
                            [
                                io.Int.Input("width", default=512, min=1, max=nodes.MAX_RESOLUTION, step=1),
                                io.Int.Input("height", default=512, min=1, max=nodes.MAX_RESOLUTION, step=1),
                            ],
                        ),
                    ],
                ),
            ],
            outputs=[
                io.Image.Output(),
                io.Int.Output(display_name="interval"),
            ],
        )

    @classmethod
    async def execute(cls, image, interval, mode) -> io.NodeOutput:
        start_time = time.perf_counter()

        _, h, w, _ = image.shape
        mode_key = mode.get("mode", "percent")

        if mode_key == "percent":
            percent = mode.get("percent", 50)
            pct = percent / 100.0
            new_w = max(1, int(w * pct))
            new_h = max(1, int(h * pct))
        else:
            new_w = mode.get("width", 512)
            new_h = mode.get("height", 512)

        samples = image.movedim(-1, 1)
        resized = comfy.utils.common_upscale(samples, new_w, new_h, "bilinear", "disabled")
        resized = resized.movedim(1, -1)

        _preview(resized)
        _ensure_min_duration(start_time, interval)
        return io.NodeOutput(resized, interval)


class ATSGrayScale(io.ComfyNode):
    @classmethod
    def define_schema(cls) -> io.Schema:
        return io.Schema(
            node_id="ATS-GrayScale",
            display_name="ATS-GrayScale",
            category="api_test_suits/image",
            description="Convert an image to grayscale.",
            inputs=[
                io.Image.Input("image"),
                io.Int.Input(
                    "interval",
                    default=0,
                    min=0,
                    max=60000,
                    step=1,
                    optional=True,
                    force_input=True,
                ),
            ],
            outputs=[
                io.Image.Output(),
                io.Int.Output(display_name="interval"),
            ],
        )

    @classmethod
    async def execute(cls, image, interval) -> io.NodeOutput:
        start_time = time.perf_counter()

        weights = torch.tensor([0.299, 0.587, 0.114], dtype=image.dtype, device=image.device)
        gray = image[..., :3] @ weights
        gray = gray.unsqueeze(-1).expand(-1, -1, -1, 3)

        if image.shape[-1] == 4:
            gray = torch.cat([gray, image[..., 3:4]], dim=-1)

        _preview(gray)
        _ensure_min_duration(start_time, interval)
        return io.NodeOutput(gray, interval)


class ATSRotate(io.ComfyNode):
    @classmethod
    def define_schema(cls) -> io.Schema:
        return io.Schema(
            node_id="ATS-Rotate",
            display_name="ATS-Rotate",
            category="api_test_suits/image",
            description="Rotate an image by an arbitrary degree.",
            inputs=[
                io.Image.Input("image"),
                io.Int.Input(
                    "interval",
                    default=0,
                    min=0,
                    max=60000,
                    step=1,
                    optional=True,
                    force_input=True,
                ),
                io.Combo.Input("direction", options=["clockwise", "anti-clockwise"], default="clockwise"),
                io.Int.Input("degree", default=90, min=0, max=360, step=1),
            ],
            outputs=[
                io.Image.Output(),
                io.Int.Output(display_name="interval"),
            ],
        )

    @classmethod
    async def execute(cls, image, interval, direction, degree) -> io.NodeOutput:
        start_time = time.perf_counter()

        angle = degree if direction == "clockwise" else -degree
        samples = image.movedim(-1, 1)
        rotated = torch.stack([
            TVF.rotate(img, angle=angle, interpolation=TVF.InterpolationMode.BILINEAR, expand=False)
            for img in samples
        ])
        rotated = rotated.movedim(1, -1)

        _preview(rotated)
        _ensure_min_duration(start_time, interval)
        return io.NodeOutput(rotated, interval)


class ATSCrop(io.ComfyNode):
    @classmethod
    def define_schema(cls) -> io.Schema:
        return io.Schema(
            node_id="ATS-Crop",
            display_name="ATS-Crop",
            category="api_test_suits/image",
            description="Crop an image to a rectangular region.",
            inputs=[
                io.Image.Input("image"),
                io.Int.Input(
                    "interval",
                    default=0,
                    min=0,
                    max=60000,
                    step=1,
                    optional=True,
                    force_input=True,
                ),
                io.Int.Input("x", default=0, min=0, max=nodes.MAX_RESOLUTION, step=1),
                io.Int.Input("y", default=0, min=0, max=nodes.MAX_RESOLUTION, step=1),
                io.Int.Input("w", default=256, min=1, max=nodes.MAX_RESOLUTION, step=1),
                io.Int.Input("h", default=256, min=1, max=nodes.MAX_RESOLUTION, step=1),
            ],
            outputs=[
                io.Image.Output(),
                io.Int.Output(display_name="interval"),
            ],
        )

    @classmethod
    async def execute(cls, image, interval, x, y, w, h) -> io.NodeOutput:
        start_time = time.perf_counter()

        _, img_h, img_w, _ = image.shape
        x = max(0, min(x, img_w - 1))
        y = max(0, min(y, img_h - 1))
        w = max(1, min(w, img_w - x))
        h = max(1, min(h, img_h - y))

        cropped = image[:, y:y+h, x:x+w, :]

        _preview(cropped)
        _ensure_min_duration(start_time, interval)
        return io.NodeOutput(cropped, interval)


class ATSInvert(io.ComfyNode):
    @classmethod
    def define_schema(cls) -> io.Schema:
        return io.Schema(
            node_id="ATS-Invert",
            display_name="ATS-Invert",
            category="api_test_suits/image",
            description="Invert the colors of an image.",
            inputs=[
                io.Image.Input("image"),
                io.Int.Input(
                    "interval",
                    default=0,
                    min=0,
                    max=60000,
                    step=1,
                    optional=True,
                    force_input=True,
                ),
            ],
            outputs=[
                io.Image.Output(),
                io.Int.Output(display_name="interval"),
            ],
        )

    @classmethod
    async def execute(cls, image, interval) -> io.NodeOutput:
        start_time = time.perf_counter()

        inverted = 1.0 - image

        _preview(inverted)
        _ensure_min_duration(start_time, interval)
        return io.NodeOutput(inverted, interval)


class ATSXor(io.ComfyNode):
    @classmethod
    def define_schema(cls) -> io.Schema:
        return io.Schema(
            node_id="ATS-Xor",
            display_name="ATS-Xor",
            category="api_test_suits/image",
            description="Apply a deterministic seed-driven XOR transform to an image.",
            inputs=[
                io.Image.Input("image"),
                io.Int.Input(
                    "seed",
                    default=12345678,
                    min=0,
                    max=999999999999,
                    step=1,
                ),
                io.Int.Input(
                    "interval",
                    default=0,
                    min=0,
                    max=60000,
                    step=1,
                    optional=True,
                    force_input=True,
                ),
            ],
            outputs=[
                io.Image.Output(),
                io.Int.Output(display_name="interval"),
            ],
        )

    @classmethod
    async def execute(cls, image, seed, interval) -> io.NodeOutput:
        start_time = time.perf_counter()

        arr = np.clip(255.0 * image.cpu().numpy(), 0, 255).astype(np.uint8)
        xored = _transform_xor_array(arr, int(seed))
        out = torch.from_numpy(xored / 255.0).to(dtype=image.dtype, device=image.device)

        _preview(out)
        _ensure_min_duration(start_time, interval)
        return io.NodeOutput(out, interval)


class ApiTestSuitsExtension(ComfyExtension):
    @override
    async def get_node_list(self) -> list[type[io.ComfyNode]]:
        return [ATSResize, ATSGrayScale, ATSRotate, ATSCrop, ATSInvert, ATSXor]


async def comfy_entrypoint() -> ApiTestSuitsExtension:
    return ApiTestSuitsExtension()

from __future__ import annotations

from src.types import CropWindow


def solve_crop_window(
    centers: list[tuple[float, float]],
    source_width: int,
    source_height: int,
    crop_margin_percent: float = 0.0,
) -> CropWindow:
    if not centers:
        raise ValueError("Cannot solve a crop window without tracked centers.")

    aspect_ratio = source_width / source_height
    half_width_limit = min(min(center_x, source_width - center_x) for center_x, _ in centers)
    half_height_limit = min(min(center_y, source_height - center_y) for _, center_y in centers)
    margin_scale = max(0.0, 1.0 - (crop_margin_percent / 100.0))
    half_width_limit *= margin_scale
    half_height_limit *= margin_scale

    max_height_from_width = (half_width_limit * 2.0) / aspect_ratio
    crop_height = int(min(half_height_limit * 2.0, max_height_from_width))
    crop_height = max(2, crop_height - (crop_height % 2))
    crop_width = int(round(crop_height * aspect_ratio))
    crop_width = max(2, crop_width - (crop_width % 2))

    if crop_width > source_width:
        crop_width = source_width - (source_width % 2)
    if crop_height > source_height:
        crop_height = source_height - (source_height % 2)

    if crop_width < 2 or crop_height < 2:
        raise ValueError("The tracked centers do not allow a valid crop window.")

    return CropWindow(width=crop_width, height=crop_height)


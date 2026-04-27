from __future__ import annotations

import math

import cv2
import numpy as np

from src.config import RuntimeConfig
from src.types import Detection


def measure_blur(frame: np.ndarray, bbox: tuple[float, float, float, float]) -> float:
    frame_height, frame_width = frame.shape[:2]
    x1, y1, x2, y2 = bbox
    margin_x = max(4, int((x2 - x1) * 0.1))
    margin_y = max(4, int((y2 - y1) * 0.1))
    left = max(0, int(x1) - margin_x)
    top = max(0, int(y1) - margin_y)
    right = min(frame_width, int(x2) + margin_x)
    bottom = min(frame_height, int(y2) + margin_y)
    region = frame[top:bottom, left:right]
    if region.size == 0:
        region = frame

    gray = cv2.cvtColor(region, cv2.COLOR_BGR2GRAY)
    return float(cv2.Laplacian(gray, cv2.CV_64F).var())


def evaluate_detection(
    frame: np.ndarray,
    detection: Detection | None,
    frame_width: int,
    frame_height: int,
    config: RuntimeConfig,
) -> tuple[bool, str, Detection | None]:
    if detection is None:
        return False, "no_bird_detected", None

    frame_center_x = frame_width / 2.0
    frame_center_y = frame_height / 2.0
    half_width = frame_width / 2.0
    half_height = frame_height / 2.0

    detection.blur_score = measure_blur(frame, detection.bbox)
    detection.center_offset_x = (detection.center_x - frame_center_x) / frame_width
    detection.center_offset_y = (detection.center_y - frame_center_y) / frame_height
    detection.normalized_center_distance = math.sqrt(
        ((detection.center_x - frame_center_x) / half_width) ** 2
        + ((detection.center_y - frame_center_y) / half_height) ** 2
    ) / math.sqrt(2.0)

    max_offset_x = config.center_zone_fraction / 2.0
    max_offset_y = config.center_zone_fraction / 2.0

    if detection.confidence < config.confidence_threshold:
        return False, "low_confidence", detection
    if detection.blur_score < config.blur_threshold:
        return False, "bird_too_blurry", detection
    if abs(detection.center_offset_x) > max_offset_x or abs(detection.center_offset_y) > max_offset_y:
        return False, "bird_too_far_from_center", detection
    return True, "tracking", detection


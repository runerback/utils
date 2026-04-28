from __future__ import annotations

import math
from pathlib import Path

import numpy as np
from ultralytics import YOLO

from src.config import YOLOV8S_DOWNLOAD_URL
from src.types import Detection


class BirdDetector:
    def __init__(
        self,
        model_path: Path,
        device: str,
        inference_confidence: float = 0.05,
        inference_image_size: int = 640,
    ) -> None:
        if not model_path.exists():
            raise FileNotFoundError(
                "Model weights were not found at "
                f"{model_path}. Download yolov8s.pt from {YOLOV8S_DOWNLOAD_URL} "
                "and place it at the configured model path."
            )
        self._model = YOLO(str(model_path))
        self._bird_class_id = self._resolve_bird_class_id()
        self._device = None if device == "auto" else device
        self._inference_confidence = inference_confidence
        self._inference_image_size = inference_image_size

    def _resolve_bird_class_id(self) -> int:
        names = self._model.names
        if isinstance(names, dict):
            for class_id, class_name in names.items():
                if str(class_name).lower() == "bird":
                    return int(class_id)
        else:
            for class_id, class_name in enumerate(names):
                if str(class_name).lower() == "bird":
                    return class_id
        raise ValueError("The configured YOLO model does not expose a COCO 'bird' class.")

    def detect(self, frame: np.ndarray) -> list[Detection]:
        result = self._model.predict(
            source=frame,
            classes=[self._bird_class_id],
            conf=self._inference_confidence,
            device=self._device,
            imgsz=self._inference_image_size,
            verbose=False,
        )[0]

        if result.boxes is None or len(result.boxes) == 0:
            return []

        boxes = result.boxes.xyxy.cpu().tolist()
        confidences = result.boxes.conf.cpu().tolist()
        detections: list[Detection] = []
        for box, confidence in zip(boxes, confidences):
            x1, y1, x2, y2 = [float(value) for value in box]
            center_x = (x1 + x2) / 2.0
            center_y = (y1 + y2) / 2.0
            detections.append(
                Detection(
                    bbox=(x1, y1, x2, y2),
                    confidence=float(confidence),
                    center_x=center_x,
                    center_y=center_y,
                )
            )
        return detections


def apply_tracking_anchor(
    detection: Detection,
    anchor_x_percent: float,
    anchor_y_percent: float,
) -> Detection:
    x1, y1, x2, y2 = detection.bbox
    width = x2 - x1
    height = y2 - y1
    if width <= 0.0 or height <= 0.0 or not all(math.isfinite(value) for value in (x1, y1, x2, y2)):
        return _fallback_to_bbox_center(detection)

    anchor_x = x1 + (width * (anchor_x_percent / 100.0))
    anchor_y = y1 + (height * (anchor_y_percent / 100.0))
    if not math.isfinite(anchor_x) or not math.isfinite(anchor_y):
        return _fallback_to_bbox_center(detection)

    detection.tracking_anchor_x = anchor_x
    detection.tracking_anchor_y = anchor_y
    detection.tracking_anchor_source = (
        "bbox_center" if anchor_x_percent == 50.0 and anchor_y_percent == 50.0 else "bbox_relative"
    )
    return detection


def _fallback_to_bbox_center(detection: Detection) -> Detection:
    detection.tracking_anchor_x = detection.center_x
    detection.tracking_anchor_y = detection.center_y
    detection.tracking_anchor_source = "bbox_center_fallback"
    return detection


def select_primary_detection(
    detections: list[Detection],
    frame_width: int,
    frame_height: int,
) -> Detection | None:
    if not detections:
        return None

    frame_center_x = frame_width / 2.0
    frame_center_y = frame_height / 2.0
    half_width = frame_width / 2.0
    half_height = frame_height / 2.0

    def score(detection: Detection) -> float:
        dx = abs(detection.tracking_x - frame_center_x) / half_width
        dy = abs(detection.tracking_y - frame_center_y) / half_height
        distance_penalty = (dx + dy) / 2.0
        return detection.confidence - (distance_penalty * 0.35)

    return max(detections, key=score)


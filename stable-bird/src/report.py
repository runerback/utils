from __future__ import annotations

import json
from pathlib import Path

import cv2
import numpy as np

from src.config import RuntimeConfig
from src.types import FrameEvaluation, ProcessingSummary, Segment, SplitEvent, VideoInfo


def draw_debug_frame(
    frame: np.ndarray,
    evaluation: FrameEvaluation,
    frame_width: int,
    frame_height: int,
    center_zone_percent: float,
    tracked_center: tuple[float, float] | None,
) -> np.ndarray:
    overlay = frame.copy()
    zone_half_width = int((frame_width * (center_zone_percent / 100.0)) / 2.0)
    zone_half_height = int((frame_height * (center_zone_percent / 100.0)) / 2.0)
    frame_center = (frame_width // 2, frame_height // 2)

    cv2.rectangle(
        overlay,
        (frame_center[0] - zone_half_width, frame_center[1] - zone_half_height),
        (frame_center[0] + zone_half_width, frame_center[1] + zone_half_height),
        (0, 255, 255),
        2,
    )
    cv2.drawMarker(overlay, frame_center, (0, 255, 255), markerType=cv2.MARKER_CROSS, markerSize=24, thickness=2)

    if evaluation.detection is not None:
        x1, y1, x2, y2 = [int(value) for value in evaluation.detection.bbox]
        color = (0, 200, 0) if evaluation.is_good else (0, 0, 255)
        cv2.rectangle(overlay, (x1, y1), (x2, y2), color, 2)
        cv2.drawMarker(
            overlay,
            (int(round(evaluation.detection.tracking_x)), int(round(evaluation.detection.tracking_y))),
            (255, 255, 0),
            markerType=cv2.MARKER_DIAMOND,
            markerSize=16,
            thickness=2,
        )
        label = (
            f"bird conf={evaluation.detection.confidence:.2f} "
            f"blur={evaluation.detection.blur_score:.1f}"
        )
        cv2.putText(overlay, label, (x1, max(24, y1 - 10)), cv2.FONT_HERSHEY_SIMPLEX, 0.55, color, 2)

    if tracked_center is not None:
        cv2.drawMarker(
            overlay,
            (int(round(tracked_center[0])), int(round(tracked_center[1]))),
            (255, 0, 0),
            markerType=cv2.MARKER_TILTED_CROSS,
            markerSize=22,
            thickness=2,
        )

    cv2.putText(
        overlay,
        f"frame={evaluation.frame_index} status={evaluation.reason}",
        (20, 32),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.75,
        (255, 255, 255),
        2,
    )
    return overlay


def write_manifest(
    path: Path,
    config: RuntimeConfig,
    video_info: VideoInfo,
    summary: ProcessingSummary,
) -> None:
    manifest = {
        "source_video": summary.source_video,
        "output_root": summary.output_root,
        "video_info": {
            "width": video_info.width,
            "height": video_info.height,
            "fps": video_info.fps,
            "frame_count": video_info.frame_count,
            "duration_seconds": video_info.duration_seconds,
        },
        "config": {
            "model_path": str(config.model_path),
            "device": config.device,
            "confidence_threshold": config.confidence_threshold,
            "blur_threshold": config.blur_threshold,
            "center_zone_percent": config.center_zone_percent,
            "grace_frames": config.grace_frames,
            "smoothing_alpha": config.smoothing_alpha,
            "min_segment_frames": config.min_segment_frames,
            "inference_confidence": config.inference_confidence,
            "inference_image_size": config.inference_image_size,
            "trace_every_n_frames": config.trace_every_n_frames,
            "crop_margin_percent": config.crop_margin_percent,
            "tracking_anchor_x_percent": config.tracking_anchor_x_percent,
            "tracking_anchor_y_percent": config.tracking_anchor_y_percent,
            "debug_preview": config.debug_preview,
        },
        "accepted_segments": [serialize_segment(segment) for segment in summary.accepted_segments],
        "rejected_segments": [serialize_segment(segment) for segment in summary.rejected_segments],
        "split_events": [serialize_split_event(event) for event in summary.split_events],
        "debug_preview_path": summary.debug_preview_path,
    }
    path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")


def serialize_segment(segment: Segment) -> dict[str, object]:
    crop_window = None
    if segment.crop_window is not None:
        crop_window = {
            "width": segment.crop_window.width,
            "height": segment.crop_window.height,
        }

    return {
        "segment_id": segment.segment_id,
        "accepted": segment.accepted,
        "start_frame": segment.start_frame,
        "end_frame": segment.end_frame,
        "start_time": round(segment.start_time, 3),
        "end_time": round(segment.end_time, 3),
        "frame_count": segment.frame_count,
        "split_reason": segment.split_reason,
        "crop_window": crop_window,
        "output_path": segment.output_path,
    }


def serialize_split_event(event: SplitEvent) -> dict[str, object]:
    return {
        "frame_index": event.frame_index,
        "timestamp_seconds": round(event.timestamp_seconds, 3),
        "reason": event.reason,
        "previous_segment_id": event.previous_segment_id,
    }


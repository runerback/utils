from __future__ import annotations

import shutil
import subprocess
from pathlib import Path
from typing import Callable

import cv2
import numpy as np

from src.types import CropWindow, Segment, SegmentFrame, VideoInfo


def ensure_video_output_dirs(output_dir: Path, video_stem: str) -> dict[str, Path]:
    video_root = output_dir / video_stem
    clips_dir = video_root / "clips"
    temp_dir = video_root / "temp"
    debug_dir = video_root / "debug"
    for directory in (video_root, clips_dir, temp_dir, debug_dir):
        directory.mkdir(parents=True, exist_ok=True)
    return {
        "video_root": video_root,
        "clips_dir": clips_dir,
        "temp_dir": temp_dir,
        "debug_dir": debug_dir,
    }


def create_video_writer(path: Path, fps: float, frame_size: tuple[int, int]) -> cv2.VideoWriter:
    writer = cv2.VideoWriter(str(path), cv2.VideoWriter_fourcc(*"mp4v"), fps, frame_size)
    if not writer.isOpened():
        raise RuntimeError(f"Unable to open video writer for {path}")
    return writer


def crop_frame(frame: np.ndarray, crop_window: CropWindow, center: tuple[float, float]) -> np.ndarray:
    frame_height, frame_width = frame.shape[:2]
    half_width = crop_window.width / 2.0
    half_height = crop_window.height / 2.0

    left = int(round(center[0] - half_width))
    top = int(round(center[1] - half_height))
    left = max(0, min(left, frame_width - crop_window.width))
    top = max(0, min(top, frame_height - crop_window.height))
    right = left + crop_window.width
    bottom = top + crop_window.height
    return frame[top:bottom, left:right]


def render_segment(
    source_video: Path,
    segment: Segment,
    output_path: Path,
    temp_dir: Path,
    video_info: VideoInfo,
    progress_callback: Callable[[int, int], None] | None = None,
) -> None:
    if segment.crop_window is None:
        raise ValueError("Segment crop window must be solved before rendering.")

    silent_path = temp_dir / f"{output_path.stem}_silent.mp4"
    writer = create_video_writer(silent_path, video_info.fps, (segment.crop_window.width, segment.crop_window.height))
    capture = cv2.VideoCapture(str(source_video))

    try:
        current_index = 0
        frame_cursor = 0
        while frame_cursor < segment.frame_count:
            ok, frame = capture.read()
            if not ok:
                break

            planned_frame: SegmentFrame = segment.frames[frame_cursor]
            if current_index < planned_frame.frame_index:
                current_index += 1
                continue
            if current_index > planned_frame.frame_index:
                raise RuntimeError("Segment frame planning and source frames are out of sync.")

            cropped = crop_frame(
                frame,
                segment.crop_window,
                (planned_frame.render_center_x, planned_frame.render_center_y),
            )
            writer.write(cropped)
            frame_cursor += 1
            if progress_callback is not None:
                progress_callback(frame_cursor, segment.frame_count)
            current_index += 1
    finally:
        capture.release()
        writer.release()

    mux_audio(
        source_video=source_video,
        silent_video=silent_path,
        output_video=output_path,
        start_seconds=segment.start_time,
        end_seconds=segment.end_time,
    )
    if silent_path.exists():
        silent_path.unlink()


def finalize_debug_preview(source_video: Path, silent_debug_path: Path, output_path: Path) -> None:
    mux_audio(
        source_video=source_video,
        silent_video=silent_debug_path,
        output_video=output_path,
        start_seconds=None,
        end_seconds=None,
    )
    if silent_debug_path.exists():
        silent_debug_path.unlink()


def mux_audio(
    source_video: Path,
    silent_video: Path,
    output_video: Path,
    start_seconds: float | None,
    end_seconds: float | None,
) -> None:
    ffmpeg_path = shutil.which("ffmpeg")
    if ffmpeg_path is None:
        raise RuntimeError("FFmpeg is required on PATH to mux audio into the rendered outputs.")

    command = [ffmpeg_path, "-hide_banner", "-loglevel", "error", "-y", "-i", str(silent_video)]
    if start_seconds is not None:
        command.extend(["-ss", f"{start_seconds:.3f}"])
    if end_seconds is not None:
        command.extend(["-to", f"{end_seconds:.3f}"])
    command.extend(
        [
            "-i",
            str(source_video),
            "-map",
            "0:v:0",
            "-map",
            "1:a?",
            "-c:v",
            "copy",
            "-c:a",
            "aac",
            "-shortest",
            str(output_video),
        ]
    )
    completed = subprocess.run(command, capture_output=True, text=True, check=False)
    if completed.returncode != 0:
        raise RuntimeError(f"FFmpeg mux failed for {output_video}: {completed.stderr.strip()}")


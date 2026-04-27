from __future__ import annotations

from dataclasses import dataclass, field
from typing import Callable, Protocol


@dataclass(frozen=True)
class ProcessingEvent:
    kind: str
    source_video: str | None = None
    message: str | None = None
    progress_percent: float | None = None
    details: dict[str, object] = field(default_factory=dict)


class ProgressReporter(Protocol):
    def emit(self, event: ProcessingEvent) -> None:
        ...


class CallbackProgressReporter:
    def __init__(self, callback: Callable[[ProcessingEvent], None]) -> None:
        self._callback = callback

    def emit(self, event: ProcessingEvent) -> None:
        self._callback(event)


class CompositeProgressReporter:
    def __init__(self, *reporters: ProgressReporter) -> None:
        self._reporters = reporters

    def emit(self, event: ProcessingEvent) -> None:
        for reporter in self._reporters:
            reporter.emit(event)


class StdoutProgressReporter:
    def emit(self, event: ProcessingEvent) -> None:
        line = format_event_for_stdout(event)
        if line:
            print(line, flush=True)


def format_event_for_stdout(event: ProcessingEvent) -> str | None:
    details = event.details
    video_name = details.get("video_name") or event.source_video or "video"

    if event.kind == "run_started":
        return (
            f"[start] input={details.get('input_path')} output={details.get('output_dir')} "
            f"device={details.get('device')} imgsz={details.get('inference_image_size')}"
        )
    if event.kind == "video_started":
        return (
            f"[video] {video_name}: scanning {details.get('frame_count')} frames "
            f"({details.get('duration_seconds', 0.0):.1f}s) -> {details.get('trace_path')}"
        )
    if event.kind == "scan_progress":
        return (
            f"[scan] {video_name}: frame {details.get('frame_index')}/{details.get('frame_count')} "
            f"({details.get('scan_progress_percent', 0.0):.1f}%) reason={details.get('reason')}"
        )
    if event.kind == "scan_complete":
        return (
            f"[scan] {video_name}: frame scan complete, segments={details.get('segment_count')} "
            f"split_events={details.get('split_event_count')}"
        )
    if event.kind == "render_started":
        return (
            f"[render] {video_name}: segment {details.get('segment_id'):03d} "
            f"frames {details.get('start_frame')}-{details.get('end_frame')} "
            f"crop={details.get('crop_width')}x{details.get('crop_height')}"
        )
    if event.kind == "video_completed":
        return (
            f"[done] {video_name}: accepted={details.get('accepted_segment_count')} "
            f"rejected={details.get('rejected_segment_count')} manifest={details.get('manifest_path')}"
        )
    if event.kind == "video_failed":
        return f"[error] {video_name}: {details.get('error') or event.message}"
    return None

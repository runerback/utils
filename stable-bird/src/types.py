from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class VideoInfo:
    source_path: str
    width: int
    height: int
    fps: float
    frame_count: int
    duration_seconds: float


@dataclass
class Detection:
    bbox: tuple[float, float, float, float]
    confidence: float
    center_x: float
    center_y: float
    blur_score: float = 0.0
    center_offset_x: float = 0.0
    center_offset_y: float = 0.0
    normalized_center_distance: float = 0.0


@dataclass(frozen=True)
class FrameEvaluation:
    frame_index: int
    timestamp_seconds: float
    detection: Detection | None
    is_good: bool
    reason: str


@dataclass(frozen=True)
class SegmentFrame:
    frame_index: int
    timestamp_seconds: float
    render_center_x: float
    render_center_y: float
    detection: Detection | None
    is_good: bool
    reason: str


@dataclass(frozen=True)
class CropWindow:
    width: int
    height: int


@dataclass
class Segment:
    segment_id: int
    source_video: str
    frames: list[SegmentFrame]
    split_reason: str
    accepted: bool
    crop_window: CropWindow | None = None
    output_path: str | None = None

    @property
    def start_frame(self) -> int:
        return self.frames[0].frame_index

    @property
    def end_frame(self) -> int:
        return self.frames[-1].frame_index

    @property
    def start_time(self) -> float:
        return self.frames[0].timestamp_seconds

    @property
    def end_time(self) -> float:
        return self.frames[-1].timestamp_seconds

    @property
    def frame_count(self) -> int:
        return len(self.frames)


@dataclass(frozen=True)
class SplitEvent:
    frame_index: int
    timestamp_seconds: float
    reason: str
    previous_segment_id: int | None = None


@dataclass
class ProcessingSummary:
    source_video: str
    output_root: str
    accepted_segments: list[Segment] = field(default_factory=list)
    rejected_segments: list[Segment] = field(default_factory=list)
    split_events: list[SplitEvent] = field(default_factory=list)
    manifest_path: str | None = None
    debug_preview_path: str | None = None


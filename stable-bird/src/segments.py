from __future__ import annotations

from src.types import Detection, Segment, SegmentFrame, SplitEvent


class SegmentPlanner:
    def __init__(self, source_video: str, grace_frames: int, min_segment_frames: int) -> None:
        self._source_video = source_video
        self._grace_frames = grace_frames
        self._min_segment_frames = min_segment_frames
        self._active_frames: list[SegmentFrame] = []
        self._pending_bad_frames: list[SegmentFrame] = []
        self._segments: list[Segment] = []
        self._split_events: list[SplitEvent] = []
        self._next_segment_id = 1

    @property
    def segments(self) -> list[Segment]:
        return self._segments

    @property
    def split_events(self) -> list[SplitEvent]:
        return self._split_events

    def push_good(
        self,
        frame_index: int,
        timestamp_seconds: float,
        detection: Detection,
        render_center: tuple[float, float],
    ) -> None:
        if self._pending_bad_frames:
            self._active_frames.extend(self._pending_bad_frames)
            self._pending_bad_frames.clear()

        self._active_frames.append(
            SegmentFrame(
                frame_index=frame_index,
                timestamp_seconds=timestamp_seconds,
                render_center_x=render_center[0],
                render_center_y=render_center[1],
                detection=detection,
                is_good=True,
                reason="tracking",
            )
        )

    def push_bad(
        self,
        frame_index: int,
        timestamp_seconds: float,
        reason: str,
        render_center: tuple[float, float] | None,
        detection: Detection | None = None,
    ) -> bool:
        if not self._active_frames or render_center is None:
            self._split_events.append(
                SplitEvent(
                    frame_index=frame_index,
                    timestamp_seconds=timestamp_seconds,
                    reason=reason,
                    previous_segment_id=None,
                )
            )
            return False

        self._pending_bad_frames.append(
            SegmentFrame(
                frame_index=frame_index,
                timestamp_seconds=timestamp_seconds,
                render_center_x=render_center[0],
                render_center_y=render_center[1],
                detection=detection,
                is_good=False,
                reason=reason,
            )
        )
        if len(self._pending_bad_frames) <= self._grace_frames:
            return False

        first_bad = self._pending_bad_frames[0]
        previous_segment_id = self._next_segment_id
        self._finalize_active_segment(split_reason=first_bad.reason)
        self._split_events.append(
            SplitEvent(
                frame_index=first_bad.frame_index,
                timestamp_seconds=first_bad.timestamp_seconds,
                reason=first_bad.reason,
                previous_segment_id=previous_segment_id,
            )
        )
        self._pending_bad_frames.clear()
        return True

    def finish(self) -> None:
        if self._pending_bad_frames:
            first_bad = self._pending_bad_frames[0]
            previous_segment_id = self._next_segment_id
            self._finalize_active_segment(split_reason=first_bad.reason)
            self._split_events.append(
                SplitEvent(
                    frame_index=first_bad.frame_index,
                    timestamp_seconds=first_bad.timestamp_seconds,
                    reason=first_bad.reason,
                    previous_segment_id=previous_segment_id,
                )
            )
            self._pending_bad_frames.clear()
        elif self._active_frames:
            self._finalize_active_segment(split_reason="end_of_video")

    def _finalize_active_segment(self, split_reason: str) -> None:
        if not self._active_frames:
            return

        segment_frames = list(self._active_frames)
        segment = Segment(
            segment_id=self._next_segment_id,
            source_video=self._source_video,
            frames=segment_frames,
            split_reason=split_reason,
            accepted=len(segment_frames) >= self._min_segment_frames,
        )
        self._segments.append(segment)
        self._active_frames.clear()
        self._next_segment_id += 1


from __future__ import annotations

import logging
from pathlib import Path

import cv2

from src.config import RuntimeConfig, resolve_input_videos
from src.crop import solve_crop_window
from src.detect import BirdDetector, select_primary_detection
from src.logging_utils import attach_video_trace, detach_handler
from src.progress import ProcessingEvent, ProgressReporter
from src.quality import evaluate_detection
from src.render import create_video_writer, ensure_video_output_dirs, finalize_debug_preview, render_segment
from src.report import draw_debug_frame, write_manifest
from src.segments import SegmentPlanner
from src.track import ExponentialCenterTracker
from src.types import FrameEvaluation, ProcessingSummary, VideoInfo

SCAN_PROGRESS_SHARE = 80.0
RENDER_PROGRESS_SHARE = 18.0
FINALIZING_PROGRESS_PERCENT = 99.0


def process_inputs(config: RuntimeConfig, reporter: ProgressReporter | None = None) -> list[ProcessingSummary]:
    logger = logging.getLogger("stable_bird.pipeline")
    logger.info(
        "Starting processing input=%s output=%s model=%s device=%s imgsz=%s",
        config.input_path,
        config.output_dir,
        config.model_path,
        config.device,
        config.inference_image_size,
    )
    detector = BirdDetector(
        config.model_path,
        config.device,
        inference_confidence=config.inference_confidence,
        inference_image_size=config.inference_image_size,
    )
    _emit(
        reporter,
        "run_started",
        message="Starting bird video processing",
        progress_percent=0.0,
        input_path=str(config.input_path),
        output_dir=str(config.output_dir),
        model_path=str(config.model_path),
        device=config.device,
        inference_image_size=config.inference_image_size,
    )
    results: list[ProcessingSummary] = []
    for video_path in resolve_input_videos(config.input_path):
        results.append(process_video(video_path, config, detector, reporter=reporter))
    logger.info("Completed processing %d video(s)", len(results))
    return results


def process_video(
    video_path: Path,
    config: RuntimeConfig,
    detector: BirdDetector,
    reporter: ProgressReporter | None = None,
) -> ProcessingSummary:
    video_info = inspect_video(video_path)
    output_dirs = ensure_video_output_dirs(config.output_dir, video_path.stem)
    trace_path, trace_handler = attach_video_trace(output_dirs["video_root"])
    logger = logging.getLogger(f"stable_bird.pipeline.{video_path.stem}")
    summary = ProcessingSummary(source_video=str(video_path), output_root=str(output_dirs["video_root"]))

    capture = cv2.VideoCapture(str(video_path))
    if not capture.isOpened():
        raise RuntimeError(f"Unable to open source video: {video_path}")

    tracker = ExponentialCenterTracker(config.smoothing_alpha)
    planner = SegmentPlanner(video_path.name, config.grace_frames, config.min_segment_frames)
    debug_writer = None
    silent_debug_path = output_dirs["debug_dir"] / f"{video_path.stem}_debug_silent.mp4"
    if config.debug_preview:
        debug_writer = create_video_writer(silent_debug_path, video_info.fps, (video_info.width, video_info.height))

    try:
        logger.info(
            "Video start path=%s trace=%s resolution=%sx%s fps=%.2f frames=%s duration=%.2fs",
            video_path,
            trace_path,
            video_info.width,
            video_info.height,
            video_info.fps,
            video_info.frame_count,
            video_info.duration_seconds,
        )
        _emit(
            reporter,
            "video_started",
            source_video=str(video_path),
            message=f"Scanning {video_path.name}",
            progress_percent=0.0,
            video_name=video_path.name,
            trace_path=str(trace_path),
            output_root=str(output_dirs["video_root"]),
            frame_count=video_info.frame_count,
            duration_seconds=video_info.duration_seconds,
            width=video_info.width,
            height=video_info.height,
            fps=video_info.fps,
        )
        frame_index = 0
        while True:
            ok, frame = capture.read()
            if not ok:
                break

            timestamp = frame_index / video_info.fps
            detections = detector.detect(frame)
            primary_detection = select_primary_detection(detections, video_info.width, video_info.height)
            is_good, reason, evaluated_detection = evaluate_detection(
                frame=frame,
                detection=primary_detection,
                frame_width=video_info.width,
                frame_height=video_info.height,
                config=config,
            )

            tracked_center = tracker.current_center
            if is_good and evaluated_detection is not None:
                tracked_center = tracker.update((evaluated_detection.center_x, evaluated_detection.center_y))
                planner.push_good(
                    frame_index=frame_index,
                    timestamp_seconds=timestamp,
                    detection=evaluated_detection,
                    render_center=tracked_center,
                )
            else:
                split_triggered = planner.push_bad(
                    frame_index=frame_index,
                    timestamp_seconds=timestamp,
                    reason=reason,
                    render_center=tracked_center,
                    detection=evaluated_detection,
                )
                if split_triggered:
                    logger.info("Split triggered frame=%s time=%.3fs reason=%s", frame_index, timestamp, reason)
                    tracker.reset()

            evaluation = FrameEvaluation(
                frame_index=frame_index,
                timestamp_seconds=timestamp,
                detection=evaluated_detection,
                is_good=is_good,
                reason=reason,
            )
            if debug_writer is not None:
                debug_frame = draw_debug_frame(
                    frame=frame,
                    evaluation=evaluation,
                    frame_width=video_info.width,
                    frame_height=video_info.height,
                    center_zone_percent=config.center_zone_percent,
                    tracked_center=tracked_center,
                )
                debug_writer.write(debug_frame)

            if frame_index % config.trace_every_n_frames == 0:
                progress_percent = 0.0
                if video_info.frame_count > 0:
                    progress_percent = (frame_index / video_info.frame_count) * 100.0
                logger.info(
                    "Progress frame=%s/%s time=%.3fs reason=%s detections=%s tracked_center=%s",
                    frame_index,
                    video_info.frame_count,
                    timestamp,
                    reason,
                    len(detections),
                    (
                        None
                        if tracked_center is None
                        else (round(tracked_center[0], 1), round(tracked_center[1], 1))
                    ),
                )
                _emit(
                    reporter,
                    "scan_progress",
                    source_video=str(video_path),
                    message=f"Scanning frame {frame_index}",
                    progress_percent=(progress_percent / 100.0) * SCAN_PROGRESS_SHARE,
                    video_name=video_path.name,
                    frame_index=frame_index,
                    frame_count=video_info.frame_count,
                    timestamp_seconds=timestamp,
                    scan_progress_percent=progress_percent,
                    reason=reason,
                )
            frame_index += 1

        if debug_writer is not None:
            debug_writer.release()
            debug_writer = None

        planner.finish()
        logger.info("Frame scan complete; segments=%s split_events=%s", len(planner.segments), len(planner.split_events))
        accepted_segment_total = sum(1 for segment in planner.segments if segment.accepted)
        _emit(
            reporter,
            "scan_complete",
            source_video=str(video_path),
            message=f"Scan complete for {video_path.name}",
            progress_percent=SCAN_PROGRESS_SHARE,
            video_name=video_path.name,
            segment_count=len(planner.segments),
            accepted_segment_count=accepted_segment_total,
            rejected_segment_count=len(planner.segments) - accepted_segment_total,
            split_event_count=len(planner.split_events),
        )

        completed_segments = 0
        accepted_segment_divisor = max(accepted_segment_total, 1)
        for segment in planner.segments:
            if segment.accepted:
                centers = [(frame.render_center_x, frame.render_center_y) for frame in segment.frames]
                segment.crop_window = solve_crop_window(
                    centers,
                    video_info.width,
                    video_info.height,
                    crop_margin_percent=config.crop_margin_percent,
                )
                clip_path = output_dirs["clips_dir"] / f"segment_{segment.segment_id:03d}.mp4"
                logger.info(
                    "Rendering accepted segment id=%s frames=%s-%s count=%s crop=%sx%s",
                    segment.segment_id,
                    segment.start_frame,
                    segment.end_frame,
                    segment.frame_count,
                    segment.crop_window.width,
                    segment.crop_window.height,
                )
                _emit(
                    reporter,
                    "render_started",
                    source_video=str(video_path),
                    message=f"Rendering segment {segment.segment_id:03d}",
                    progress_percent=SCAN_PROGRESS_SHARE
                    + (completed_segments / accepted_segment_divisor) * RENDER_PROGRESS_SHARE,
                    video_name=video_path.name,
                    segment_id=segment.segment_id,
                    segment_index=completed_segments + 1,
                    segment_count=accepted_segment_total,
                    start_frame=segment.start_frame,
                    end_frame=segment.end_frame,
                    crop_width=segment.crop_window.width,
                    crop_height=segment.crop_window.height,
                )
                render_segment(
                    source_video=video_path,
                    segment=segment,
                    output_path=clip_path,
                    temp_dir=output_dirs["temp_dir"],
                    video_info=video_info,
                    progress_callback=lambda rendered_frames, total_frames, segment_id=segment.segment_id: _emit(
                        reporter,
                        "render_progress",
                        source_video=str(video_path),
                        message=f"Rendering segment {segment_id:03d}",
                        progress_percent=SCAN_PROGRESS_SHARE
                        + (
                            (
                                completed_segments
                                + ((rendered_frames / total_frames) if total_frames > 0 else 1.0)
                            )
                            / accepted_segment_divisor
                        )
                        * RENDER_PROGRESS_SHARE,
                        video_name=video_path.name,
                        segment_id=segment_id,
                        segment_index=completed_segments + 1,
                        segment_count=accepted_segment_total,
                        rendered_frames=rendered_frames,
                        frame_count=total_frames,
                    ),
                )
                segment.output_path = str(clip_path)
                summary.accepted_segments.append(segment)
                completed_segments += 1
                _emit(
                    reporter,
                    "render_complete",
                    source_video=str(video_path),
                    message=f"Rendered segment {segment.segment_id:03d}",
                    progress_percent=SCAN_PROGRESS_SHARE
                    + (completed_segments / accepted_segment_divisor) * RENDER_PROGRESS_SHARE,
                    video_name=video_path.name,
                    segment_id=segment.segment_id,
                    segment_index=completed_segments,
                    segment_count=accepted_segment_total,
                    clip_path=str(clip_path),
                )
            else:
                logger.info(
                    "Rejecting short segment id=%s frames=%s-%s count=%s reason=%s",
                    segment.segment_id,
                    segment.start_frame,
                    segment.end_frame,
                    segment.frame_count,
                    segment.split_reason,
                )
                summary.rejected_segments.append(segment)

        _emit(
            reporter,
            "finalizing_output",
            source_video=str(video_path),
            message=f"Finalizing outputs for {video_path.name}",
            progress_percent=FINALIZING_PROGRESS_PERCENT,
            video_name=video_path.name,
            output_root=str(output_dirs["video_root"]),
        )
        if config.debug_preview:
            debug_output_path = output_dirs["video_root"] / f"{video_path.stem}_debug.mp4"
            logger.info("Muxing debug preview to %s", debug_output_path)
            finalize_debug_preview(video_path, silent_debug_path, debug_output_path)
            summary.debug_preview_path = str(debug_output_path)

        summary.split_events.extend(planner.split_events)
        manifest_path = output_dirs["video_root"] / "manifest.json"
        write_manifest(manifest_path, config, video_info, summary)
        summary.manifest_path = str(manifest_path)
        cleanup_temp_dir(output_dirs["temp_dir"])
        logger.info(
            "Video complete accepted_segments=%s rejected_segments=%s manifest=%s",
            len(summary.accepted_segments),
            len(summary.rejected_segments),
            summary.manifest_path,
        )
        _emit(
            reporter,
            "video_completed",
            source_video=str(video_path),
            message=f"Completed {video_path.name}",
            progress_percent=100.0,
            video_name=video_path.name,
            output_root=str(output_dirs["video_root"]),
            accepted_segment_count=len(summary.accepted_segments),
            rejected_segment_count=len(summary.rejected_segments),
            manifest_path=summary.manifest_path,
            debug_preview_path=summary.debug_preview_path,
            trace_path=str(trace_path),
            clip_paths=[segment.output_path for segment in summary.accepted_segments if segment.output_path],
        )
        return summary
    except Exception as exc:
        logger.exception("Video processing failed for %s", video_path)
        _emit(
            reporter,
            "video_failed",
            source_video=str(video_path),
            message=str(exc),
            video_name=video_path.name,
            output_root=str(output_dirs["video_root"]),
            trace_path=str(trace_path),
            error=str(exc),
        )
        raise
    finally:
        capture.release()
        if debug_writer is not None:
            debug_writer.release()
        detach_handler(trace_handler)


def cleanup_temp_dir(temp_dir: Path) -> None:
    if not temp_dir.exists():
        return
    for candidate in temp_dir.iterdir():
        if candidate.is_file():
            candidate.unlink()
    temp_dir.rmdir()


def inspect_video(video_path: Path) -> VideoInfo:
    capture = cv2.VideoCapture(str(video_path))
    if not capture.isOpened():
        raise RuntimeError(f"Unable to inspect source video: {video_path}")
    try:
        width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT))
        fps = float(capture.get(cv2.CAP_PROP_FPS) or 0.0)
        if fps <= 0:
            raise ValueError(f"Unable to determine FPS for {video_path}")
        frame_count = int(capture.get(cv2.CAP_PROP_FRAME_COUNT))
        duration_seconds = frame_count / fps if frame_count > 0 else 0.0
        return VideoInfo(
            source_path=str(video_path),
            width=width,
            height=height,
            fps=fps,
            frame_count=frame_count,
            duration_seconds=duration_seconds,
        )
    finally:
        capture.release()


def _emit(
    reporter: ProgressReporter | None,
    kind: str,
    *,
    source_video: str | None = None,
    message: str | None = None,
    progress_percent: float | None = None,
    **details: object,
) -> None:
    if reporter is None:
        return
    reporter.emit(
        ProcessingEvent(
            kind=kind,
            source_video=source_video,
            message=message,
            progress_percent=progress_percent,
            details=details,
        )
    )


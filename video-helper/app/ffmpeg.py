from __future__ import annotations

import json
import locale
import logging
import math
import re
import subprocess
from pathlib import Path

from .schemas import EditState, VideoMetadata

logger = logging.getLogger(__name__)


class FFmpegService:
    _SCENE_PTS_TIME_PATTERN = re.compile(r"pts_time:(?P<pts_time>-?\d+(?:\.\d+)?)")
    _SEGMENT_EPSILON = 1e-6
    _FRAME_TOLERANCE_CAP = 0.1
    _GIF_ESTIMATE_BYTES_PER_PIXEL = 0.16
    _GIF_ESTIMATE_MIN_FRAME_BYTES = 2048

    def __init__(self, ffmpeg_bin: str = "ffmpeg", ffprobe_bin: str = "ffprobe") -> None:
        self.ffmpeg_bin = ffmpeg_bin
        self.ffprobe_bin = ffprobe_bin

    def _decode_process_output(self, output: str | bytes | None) -> str:
        if output is None:
            return ""
        if isinstance(output, str):
            return output
        for encoding in ("utf-8-sig", locale.getpreferredencoding(False), "utf-8"):
            if not encoding:
                continue
            try:
                return output.decode(encoding)
            except UnicodeDecodeError:
                continue
        return output.decode("utf-8", errors="replace")

    def _command_text(self, command: list[str]) -> str:
        return subprocess.list2cmdline([str(part) for part in command])

    def probe(self, source: Path) -> VideoMetadata:
        command = [
            self.ffprobe_bin,
            "-v",
            "error",
            "-print_format",
            "json",
            "-show_streams",
            "-show_format",
            str(source),
        ]
        try:
            result = subprocess.run(command, check=True, capture_output=True)
        except subprocess.CalledProcessError as exc:
            logger.exception(
                "ffprobe command failed for %s [command=%s, stdout=%r, stderr=%r]",
                source,
                self._command_text(command),
                self._decode_process_output(exc.stdout).strip(),
                self._decode_process_output(exc.stderr).strip(),
            )
            raise
        stdout_text = self._decode_process_output(result.stdout).strip()
        stderr_text = self._decode_process_output(result.stderr).strip()
        if not stdout_text:
            detail = f"ffprobe returned no JSON output for '{source}'"
            if stderr_text:
                detail = f"{detail}: {stderr_text}"
            raise ValueError(detail)
        try:
            payload = json.loads(stdout_text)
        except json.JSONDecodeError as exc:
            detail = f"ffprobe returned invalid JSON for '{source}'"
            if stderr_text:
                detail = f"{detail}: {stderr_text}"
            raise ValueError(detail) from exc

        video_stream = next((s for s in payload.get("streams", []) if s.get("codec_type") == "video"), None)
        if not video_stream:
            raise ValueError("No video stream found")
        audio_stream = next((s for s in payload.get("streams", []) if s.get("codec_type") == "audio"), None)
        format_payload = payload.get("format", {})

        width = int(video_stream["width"])
        height = int(video_stream["height"])
        duration = float(video_stream.get("duration") or format_payload.get("duration") or 0)
        rate = video_stream.get("avg_frame_rate", "0/1")
        num, den = rate.split("/")
        fps = float(num) / float(den) if float(den) else 0.0
        frame_count = int(round(duration * fps)) if duration > 0 and fps > 0 else 1
        return VideoMetadata(
            width=width,
            height=height,
            duration=duration,
            fps=fps,
            frame_count=max(frame_count, 1),
            video_codec=str(video_stream.get("codec_name") or "unknown"),
            audio_codec=str(audio_stream.get("codec_name")) if audio_stream and audio_stream.get("codec_name") else None,
            container_format=str(format_payload.get("format_name") or "unknown"),
        )

    def validate_state(self, metadata: VideoMetadata, state: EditState) -> None:
        trim_end = state.trim.end or metadata.duration
        trim_duration = trim_end - state.trim.start
        if state.trim.start >= trim_end:
            raise ValueError("Trim start must be less than trim end")
        if trim_end > metadata.duration:
            raise ValueError("Trim end exceeds video duration")

        crop = state.crop
        if state.crop_enabled and crop.width and crop.height:
            if crop.x + crop.width > metadata.width or crop.y + crop.height > metadata.height:
                raise ValueError("Crop rectangle exceeds source dimensions")
        if state.resize_max is not None and state.resize_max < 2:
            raise ValueError("resize_max must be at least 2")
        if state.scene_split.enabled and not (state.scene_split.detector == "ffmpeg" and state.scene_split.fixed_length_enabled):
            min_len, max_len = self.adjusted_scene_split_lengths(
                metadata,
                state.scene_split.min_clip_length,
                state.scene_split.max_clip_length,
            )
            if not self._is_segmentable_duration(trim_duration, min_len, max_len):
                raise ValueError("Scene split min/max clip lengths cannot segment the current trimmed duration")

    def frame_interval(self, metadata: VideoMetadata) -> float:
        if metadata.fps <= self._SEGMENT_EPSILON:
            return 0.0
        return 1.0 / metadata.fps

    def clamp_frame_timestamp(self, metadata: VideoMetadata, timestamp: float) -> float:
        safe_ts = max(0.0, min(timestamp, metadata.duration))
        frame_interval = self.frame_interval(metadata)
        if frame_interval <= self._SEGMENT_EPSILON:
            return round(safe_ts, 6)
        max_seek = max(0.0, metadata.duration - frame_interval)
        return round(min(safe_ts, max_seek), 6)

    def adjusted_scene_split_lengths(self, metadata: VideoMetadata, min_len: float, max_len: float) -> tuple[float, float]:
        tolerance = min(self._FRAME_TOLERANCE_CAP, self.frame_interval(metadata))
        adjusted_min = max(self._SEGMENT_EPSILON, min_len - tolerance)
        adjusted_max = max_len + tolerance
        return adjusted_min, adjusted_max

    def _filters(
        self,
        metadata: VideoMetadata,
        state: EditState,
        trim_start: float | None = None,
        trim_end: float | None = None,
    ) -> str:
        filters: list[str] = []
        effective_trim_start = state.trim.start if trim_start is None else trim_start
        effective_trim_end = (state.trim.end or metadata.duration) if trim_end is None else trim_end
        filters.append(self._video_timing_filter(state, effective_trim_start, effective_trim_end))
        if state.crop_enabled and state.crop.width and state.crop.height:
            filters.append(
                f"crop={state.crop.width}:{state.crop.height}:{state.crop.x}:{state.crop.y}"
            )
        filters.extend(self._rotation_filters(state))
        if state.resize_max:
            max_size = state.resize_max
            filters.append(
                "scale="
                f"if(gte(iw\\,ih)\\,min(iw\\,{max_size})\\,-2):"
                f"if(gte(iw\\,ih)\\,-2\\,min(ih\\,{max_size}))"
            )
        return ",".join(filters)

    def _video_timing_filter(self, state: EditState, trim_start: float, trim_end: float) -> str:
        speed = state.speed
        if math.isclose(speed, 1.0, rel_tol=1e-9, abs_tol=1e-9):
            pts_expression = "PTS-STARTPTS"
        else:
            pts_multiplier = self._format_filter_number(1.0 / speed)
            pts_expression = f"{pts_multiplier}*(PTS-STARTPTS)"
        return f"trim=start={trim_start}:end={trim_end},setpts={pts_expression}"

    def _rotation_filters(self, state: EditState) -> list[str]:
        quarter_turns = state.rotation.quarter_turns
        if quarter_turns == 1:
            return ["transpose=1"]
        if quarter_turns == 2:
            return ["transpose=1", "transpose=1"]
        if quarter_turns == 3:
            return ["transpose=2"]
        return []

    def _audio_segment_options(
        self,
        metadata: VideoMetadata,
        state: EditState,
        segment_start: float,
        segment_end: float,
    ) -> list[str]:
        if not metadata.audio_codec:
            return ["-an"]
        audio_filters = [
            f"atrim=start={segment_start}:end={segment_end}",
            "asetpts=PTS-STARTPTS",
            *self._tempo_filters(state.speed),
        ]
        return [
            "-map",
            "0:v:0",
            "-map",
            "0:a?",
            "-af",
            ",".join(audio_filters),
            "-c:a",
            "aac",
            "-b:a",
            "128k",
        ]

    def _tempo_filters(self, speed: float) -> list[str]:
        remaining_speed = speed
        filters: list[str] = []
        while remaining_speed < 0.5 - self._SEGMENT_EPSILON:
            filters.append("atempo=0.5")
            remaining_speed /= 0.5
        while remaining_speed > 2.0 + self._SEGMENT_EPSILON:
            filters.append("atempo=2")
            remaining_speed /= 2.0
        if not math.isclose(remaining_speed, 1.0, rel_tol=1e-9, abs_tol=1e-9):
            filters.append(f"atempo={self._format_filter_number(remaining_speed)}")
        return filters

    def _format_filter_number(self, value: float) -> str:
        text = f"{value:.6f}".rstrip("0").rstrip(".")
        return text or "0"

    def _resolved_dimensions(self, metadata: VideoMetadata, state: EditState) -> tuple[int, int]:
        width = metadata.width
        height = metadata.height
        if state.crop_enabled and state.crop.width and state.crop.height:
            width = state.crop.width
            height = state.crop.height
        if state.rotation.quarter_turns % 2 == 1:
            width, height = height, width
        resize_max = state.resize_max
        if resize_max and resize_max >= 2:
            source_width = width
            source_height = height
            if width >= height and width > resize_max:
                width = resize_max
                height = max(2, round((source_height * resize_max) / max(1, source_width)))
            elif height > width and height > resize_max:
                height = resize_max
                width = max(2, round((source_width * resize_max) / max(1, source_height)))
            if width % 2 != 0:
                width = max(2, width - 1)
            if height % 2 != 0:
                height = max(2, height - 1)
        return max(2, width), max(2, height)

    def _effective_fps(self, metadata: VideoMetadata, state: EditState) -> float:
        return max(1.0, metadata.fps)

    def _gif_filter_complex(self, filters: str) -> str:
        filter_prefix = f"[0:v]{filters}" if filters else "[0:v]"
        return (
            f"{filter_prefix},split[palette_input][gif_input];"
            "[palette_input]palettegen=stats_mode=diff[palette];"
            "[gif_input][palette]paletteuse=dither=sierra2_4a"
        )

    def build_preview_command(self, source: Path, output: Path, metadata: VideoMetadata, state: EditState) -> list[str]:
        vf = self._filters(metadata, state)
        trim_end = state.trim.end or metadata.duration
        return [
            self.ffmpeg_bin,
            "-y",
            "-i",
            str(source),
            "-vf",
            vf,
            *self._audio_segment_options(metadata, state, state.trim.start, trim_end),
            "-preset",
            "veryfast",
            "-crf",
            "30",
            "-movflags",
            "+faststart",
            str(output),
        ]

    def build_preview_segment_command(
        self,
        source: Path,
        output: Path,
        metadata: VideoMetadata,
        state: EditState,
        segment_start: float,
        segment_end: float,
    ) -> list[str]:
        vf = self._filters(metadata, state, trim_start=segment_start, trim_end=segment_end)
        return [
            self.ffmpeg_bin,
            "-y",
            "-i",
            str(source),
            "-vf",
            vf,
            *self._audio_segment_options(metadata, state, segment_start, segment_end),
            "-c:v",
            "libx264",
            "-preset",
            "veryfast",
            "-crf",
            "30",
            "-pix_fmt",
            "yuv420p",
            "-movflags",
            "+faststart",
            str(output),
        ]

    def build_export_command(self, source: Path, output: Path, metadata: VideoMetadata, state: EditState) -> list[str]:
        vf = self._filters(metadata, state)
        trim_end = state.trim.end or metadata.duration
        return [
            self.ffmpeg_bin,
            "-y",
            "-i",
            str(source),
            "-vf",
            vf,
            *self._audio_segment_options(metadata, state, state.trim.start, trim_end),
            "-preset",
            "medium",
            "-crf",
            "20",
            "-movflags",
            "+faststart",
            str(output),
        ]

    def build_export_gif_command(self, source: Path, output: Path, metadata: VideoMetadata, state: EditState) -> list[str]:
        filters = self._filters(metadata, state)
        return [
            self.ffmpeg_bin,
            "-y",
            "-i",
            str(source),
            "-filter_complex",
            self._gif_filter_complex(filters),
            "-an",
            "-loop",
            "0",
            str(output),
        ]

    def build_export_segment_command(
        self,
        source: Path,
        output: Path,
        metadata: VideoMetadata,
        state: EditState,
        segment_start: float,
        segment_end: float,
    ) -> list[str]:
        vf = self._filters(metadata, state, trim_start=segment_start, trim_end=segment_end)
        return [
            self.ffmpeg_bin,
            "-y",
            "-i",
            str(source),
            "-vf",
            vf,
            *self._audio_segment_options(metadata, state, segment_start, segment_end),
            "-c:v",
            "libx264",
            "-preset",
            "medium",
            "-crf",
            "20",
            "-pix_fmt",
            "yuv420p",
            "-movflags",
            "+faststart",
            str(output),
        ]

    def build_export_gif_segment_command(
        self,
        source: Path,
        output: Path,
        metadata: VideoMetadata,
        state: EditState,
        segment_start: float,
        segment_end: float,
    ) -> list[str]:
        filters = self._filters(metadata, state, trim_start=segment_start, trim_end=segment_end)
        return [
            self.ffmpeg_bin,
            "-y",
            "-i",
            str(source),
            "-filter_complex",
            self._gif_filter_complex(filters),
            "-an",
            "-loop",
            "0",
            str(output),
        ]

    def build_player_proxy_command(self, source: Path, output: Path) -> list[str]:
        return [
            self.ffmpeg_bin,
            "-y",
            "-i",
            str(source),
            "-map",
            "0:v:0",
            "-map",
            "0:a?",
            "-c:v",
            "libx264",
            "-preset",
            "veryfast",
            "-crf",
            "23",
            "-pix_fmt",
            "yuv420p",
            "-c:a",
            "aac",
            "-b:a",
            "128k",
            "-movflags",
            "+faststart",
            str(output),
        ]

    def requires_player_proxy(self, metadata: VideoMetadata) -> bool:
        return metadata.video_codec.lower() in {"hevc", "h265"}

    def build_frame_image_command(self, source: Path, output: Path, timestamp: float) -> list[str]:
        safe_ts = max(0.0, timestamp)
        return [
            self.ffmpeg_bin,
            "-y",
            "-ss",
            f"{safe_ts:.6f}",
            "-i",
            str(source),
            "-frames:v",
            "1",
            "-q:v",
            "2",
            str(output),
        ]

    def build_scene_detection_command(self, source: Path, threshold: float) -> list[str]:
        if threshold <= 0 or threshold > 1:
            raise ValueError("scene threshold must be greater than 0 and less than or equal to 1")
        scene_filter = f"select='gt(scene\\,{threshold:.6f})',showinfo"
        return [
            self.ffmpeg_bin,
            "-hide_banner",
            "-nostats",
            "-i",
            str(source),
            "-vf",
            scene_filter,
            "-an",
            "-f",
            "null",
            "-",
        ]

    def parse_scene_changes(self, ffmpeg_output: str, dedupe_tolerance: float = 1e-3) -> list[float]:
        timestamps: list[float] = []
        for line in ffmpeg_output.splitlines():
            match = self._SCENE_PTS_TIME_PATTERN.search(line)
            if not match:
                continue
            try:
                pts_time = float(match.group("pts_time"))
            except ValueError:
                continue
            if pts_time >= 0:
                timestamps.append(pts_time)
        if not timestamps:
            return []

        normalized: list[float] = []
        for timestamp in sorted(timestamps):
            if not normalized or abs(timestamp - normalized[-1]) > dedupe_tolerance:
                normalized.append(timestamp)
        return normalized

    def detect_scene_changes(self, source: Path, threshold: float) -> list[float]:
        command = self.build_scene_detection_command(source, threshold)
        try:
            result = subprocess.run(command, check=True, capture_output=True, text=True)
        except subprocess.CalledProcessError as exc:
            logger.exception(
                "Scene detection command failed for %s [threshold=%s, command=%s, stdout=%r, stderr=%r]",
                source,
                threshold,
                self._command_text(command),
                (exc.stdout or "").strip(),
                (exc.stderr or "").strip(),
            )
            raise
        output = "\n".join(part for part in [result.stdout, result.stderr] if part)
        timestamps = self.parse_scene_changes(output)
        logger.info("Detected %s FFmpeg scene cuts for %s", len(timestamps), source)
        return timestamps

    def _is_segmentable_duration(self, duration: float, min_len: float, max_len: float) -> bool:
        if abs(duration) <= self._SEGMENT_EPSILON:
            return True
        min_segments = math.ceil((duration - self._SEGMENT_EPSILON) / max_len)
        max_segments = math.floor((duration + self._SEGMENT_EPSILON) / min_len)
        return min_segments <= max_segments and max_segments >= 1

    def _pick_synthetic_cut(
        self,
        segment_start: float,
        duration: float,
        min_len: float,
        max_len: float,
    ) -> float:
        lower = segment_start + min_len
        upper = min(segment_start + max_len, duration)
        remaining = duration - segment_start
        max_remaining_segments = max(1, math.floor((remaining - min_len + self._SEGMENT_EPSILON) / min_len))

        best_end: float | None = None
        for remaining_segments in range(1, max_remaining_segments + 1):
            interval_start = max(lower, duration - (remaining_segments * max_len))
            interval_end = min(upper, duration - (remaining_segments * min_len))
            if interval_start - interval_end > self._SEGMENT_EPSILON:
                continue
            if best_end is None or interval_end > best_end:
                best_end = interval_end

        if best_end is None:
            raise ValueError("Cannot construct valid segment boundaries for the requested duration range")
        return min(duration, max(lower, best_end))

    def build_scene_split_segments(
        self,
        duration: float,
        candidate_cuts: list[float],
        min_len: float,
        max_len: float,
    ) -> list[tuple[float, float]]:
        if duration <= 0:
            raise ValueError("duration must be greater than 0")
        if min_len <= 0 or max_len <= 0:
            raise ValueError("min_len and max_len must be greater than 0")
        if min_len > max_len:
            raise ValueError("min_len must be less than or equal to max_len")
        if not self._is_segmentable_duration(duration, min_len, max_len):
            raise ValueError("Cannot satisfy strict min/max segment lengths for the provided duration")

        valid_candidates = sorted(
            {
                round(cut, 6)
                for cut in candidate_cuts
                if self._SEGMENT_EPSILON < cut < duration - self._SEGMENT_EPSILON
            }
        )

        segments: list[tuple[float, float]] = []
        start = 0.0
        while duration - start > self._SEGMENT_EPSILON:
            remaining = duration - start
            if min_len - remaining <= self._SEGMENT_EPSILON and remaining - max_len <= self._SEGMENT_EPSILON:
                segments.append((round(start, 6), round(duration, 6)))
                break
            if remaining < min_len - self._SEGMENT_EPSILON:
                raise ValueError("Cannot construct valid segment boundaries for the requested duration range")

            lower = start + min_len
            upper = min(start + max_len, duration)

            candidate_end: float | None = None
            for cut in valid_candidates:
                if cut < lower - self._SEGMENT_EPSILON:
                    continue
                if cut > upper + self._SEGMENT_EPSILON:
                    break
                if self._is_segmentable_duration(duration - cut, min_len, max_len):
                    candidate_end = cut

            end = candidate_end if candidate_end is not None else self._pick_synthetic_cut(start, duration, min_len, max_len)
            segments.append((round(start, 6), round(end, 6)))
            start = end

        return segments

    def build_fixed_length_segments(self, duration: float, clip_length: float) -> list[tuple[float, float]]:
        if duration <= 0:
            raise ValueError("duration must be greater than 0")
        if clip_length <= 0:
            raise ValueError("clip_length must be greater than 0")

        if duration - clip_length <= self._SEGMENT_EPSILON:
            return [(0.0, round(duration, 6))]

        segments: list[tuple[float, float]] = []
        start = 0.0
        while duration - start > self._SEGMENT_EPSILON:
            end = min(duration, start + clip_length)
            remaining = duration - end
            if end >= duration - self._SEGMENT_EPSILON or remaining < clip_length - self._SEGMENT_EPSILON:
                segments.append((round(start, 6), round(duration, 6)))
                break
            segments.append((round(start, 6), round(end, 6)))
            start = end

        return segments

    def estimate_gif_size(
        self,
        metadata: VideoMetadata,
        state: EditState,
        segment_start: float | None = None,
        segment_end: float | None = None,
    ) -> int:
        effective_start = state.trim.start if segment_start is None else segment_start
        effective_end = (state.trim.end or metadata.duration) if segment_end is None else segment_end
        duration = max(0.0, effective_end - effective_start)
        width, height = self._resolved_dimensions(metadata, state)
        fps = self._effective_fps(metadata, state)
        frame_count = max(1.0, duration * fps)
        bytes_per_frame = max(
            float(self._GIF_ESTIMATE_MIN_FRAME_BYTES),
            width * height * self._GIF_ESTIMATE_BYTES_PER_PIXEL,
        )
        palette_overhead = 32 * 1024
        total_bytes = int(round((frame_count * bytes_per_frame) + palette_overhead))
        return max(1024, total_bytes)

    def run(self, command: list[str]) -> None:
        logger.debug("Running ffmpeg command: %s", self._command_text(command))
        try:
            subprocess.run(command, check=True, capture_output=True, text=True)
        except subprocess.CalledProcessError as exc:
            logger.exception(
                "ffmpeg command failed [command=%s, stdout=%r, stderr=%r]",
                self._command_text(command),
                (exc.stdout or "").strip(),
                (exc.stderr or "").strip(),
            )
            raise


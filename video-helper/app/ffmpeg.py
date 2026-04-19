from __future__ import annotations

import json
import subprocess
from pathlib import Path

from .schemas import EditState, VideoMetadata


class FFmpegService:
    def __init__(self, ffmpeg_bin: str = "ffmpeg", ffprobe_bin: str = "ffprobe") -> None:
        self.ffmpeg_bin = ffmpeg_bin
        self.ffprobe_bin = ffprobe_bin

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
        result = subprocess.run(command, check=True, capture_output=True, text=True)
        payload = json.loads(result.stdout)

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

    def _filters(self, metadata: VideoMetadata, state: EditState) -> str:
        filters: list[str] = []
        trim_end = state.trim.end or metadata.duration
        filters.append(f"trim=start={state.trim.start}:end={trim_end},setpts=PTS-STARTPTS")
        if state.crop_enabled and state.crop.width and state.crop.height:
            filters.append(
                f"crop={state.crop.width}:{state.crop.height}:{state.crop.x}:{state.crop.y}"
            )
        if state.resize_max:
            max_size = state.resize_max
            filters.append(
                "scale="
                f"if(gte(iw\\,ih)\\,min(iw\\,{max_size})\\,-2):"
                f"if(gte(iw\\,ih)\\,-2\\,min(ih\\,{max_size}))"
            )
        if state.fps:
            filters.append(f"fps={state.fps}")
        return ",".join(filters)

    def build_preview_command(self, source: Path, output: Path, metadata: VideoMetadata, state: EditState) -> list[str]:
        vf = self._filters(metadata, state)
        return [
            self.ffmpeg_bin,
            "-y",
            "-i",
            str(source),
            "-vf",
            vf,
            "-an",
            "-preset",
            "veryfast",
            "-crf",
            "30",
            "-movflags",
            "+faststart",
            str(output),
        ]

    def build_export_command(self, source: Path, output: Path, metadata: VideoMetadata, state: EditState) -> list[str]:
        vf = self._filters(metadata, state)
        return [
            self.ffmpeg_bin,
            "-y",
            "-i",
            str(source),
            "-vf",
            vf,
            "-preset",
            "medium",
            "-crf",
            "20",
            "-movflags",
            "+faststart",
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

    def run(self, command: list[str]) -> None:
        subprocess.run(command, check=True, capture_output=True, text=True)


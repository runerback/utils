from __future__ import annotations

import logging
import re
import subprocess
import sys
import threading
from datetime import datetime
from importlib import import_module
from pathlib import Path
from typing import Any, Callable

from .ffmpeg import FFmpegService
from .schemas import AudioClipResult, AudioSplitSettings

logger = logging.getLogger(__name__)

AUDIO_SAMPLE_RATE = 44100
VAD_SAMPLE_RATE = 16000
RMS_WINDOW_SECONDS = 0.05
RMS_HOP_SECONDS = 0.025
MIN_VOCAL_CLIP_SECONDS = 0.3
ROFORMER_MODEL_NAME = "model_bs_roformer_ep_317_sdr_12.9755.ckpt"

ProgressCallback = Callable[[float, str], None]
CancelCheck = Callable[[], bool]


class AudioSplitCancelled(Exception):
    pass


def _numpy() -> Any:
    try:
        return import_module("numpy")
    except ModuleNotFoundError as exc:
        raise ValueError("Audio split requires numpy. Install requirements to enable it.") from exc


def compute_rms_curve(samples: Any, sample_rate: int, window: float = RMS_WINDOW_SECONDS, hop: float = RMS_HOP_SECONDS) -> tuple[Any, Any]:
    np = _numpy()
    if sample_rate <= 0:
        raise ValueError("sample_rate must be greater than 0")
    data = np.asarray(samples, dtype=np.float32)
    if data.ndim > 1:
        data = data.mean(axis=-1)
    window_size = max(1, int(round(window * sample_rate)))
    hop_size = max(1, int(round(hop * sample_rate)))
    if len(data) < window_size:
        rms = np.sqrt(np.mean(data**2)) if len(data) else 0.0
        return np.array([0.0], dtype=np.float32), np.array([float(rms)], dtype=np.float32)
    frame_count = 1 + (len(data) - window_size) // hop_size
    starts = np.arange(frame_count) * hop_size
    frames = data[starts[:, None] + np.arange(window_size)[None, :]]
    rms_values = np.sqrt(np.mean(frames**2, axis=1))
    times = (starts + window_size / 2.0) / float(sample_rate)
    return times.astype(np.float32), rms_values.astype(np.float32)


def merge_segments(segments: list[tuple[float, float]], gap: float) -> list[tuple[float, float]]:
    if not segments:
        return []
    ordered = sorted((max(0.0, s), max(0.0, e)) for s, e in segments if e > s)
    if not ordered:
        return []
    merged: list[list[float]] = [[ordered[0][0], ordered[0][1]]]
    for start, end in ordered[1:]:
        if start - merged[-1][1] <= gap:
            merged[-1][1] = max(merged[-1][1], end)
        else:
            merged.append([start, end])
    return [(round(s, 6), round(e, 6)) for s, e in merged]


def _min_rms_time_in_range(times: Any, rms: Any, lower: float, upper: float) -> float | None:
    np = _numpy()
    mask = (times >= lower) & (times <= upper)
    if not bool(mask.any()):
        return None
    candidate_times = times[mask]
    candidate_rms = rms[mask]
    best_index = int(np.argmin(candidate_rms))
    return float(candidate_times[best_index])


def split_long_clips(
    clips: list[tuple[float, float]],
    rms_times: Any,
    rms_values: Any,
    min_len: float,
    max_len: float,
) -> list[tuple[float, float]]:
    if min_len <= 0 or max_len <= 0:
        raise ValueError("min_len and max_len must be greater than 0")
    if min_len > max_len:
        raise ValueError("min_len must be less than or equal to max_len")

    epsilon = 1e-6
    result: list[tuple[float, float]] = []
    for clip_start, clip_end in clips:
        if clip_end - clip_start <= max_len + epsilon:
            result.append((clip_start, clip_end))
            continue
        current = clip_start
        while clip_end - current > max_len + epsilon:
            lower = current + min_len
            upper = min(current + max_len, clip_end - min_len)
            if upper < lower - epsilon:
                # Cannot place a cut that keeps the remainder >= min_len; keep the rest unsplit.
                break
            cut = _min_rms_time_in_range(rms_times, rms_values, lower, upper)
            if cut is None:
                cut = min(current + max_len, clip_end - min_len)
            cut = min(max(cut, lower), upper)
            result.append((round(current, 6), round(cut, 6)))
            current = cut
        if clip_end - current > epsilon:
            result.append((round(current, 6), round(clip_end, 6)))
    return result


def snap_boundaries_to_beats(
    clips: list[tuple[float, float]],
    beat_times: Any,
    tolerance: float,
    min_len: float,
) -> list[tuple[float, float]]:
    np = _numpy()
    if len(clips) <= 1 or tolerance <= 0:
        return list(clips)
    beats = np.sort(np.asarray(beat_times, dtype=np.float64))
    if not len(beats):
        return list(clips)

    starts = [clips[0][0]]
    ends = [clips[-1][1]]
    for prev_clip, next_clip in zip(clips, clips[1:]):
        boundary = next_clip[0]
        window = beats[(beats >= boundary - tolerance) & (beats <= boundary + tolerance)]
        candidate = boundary
        if len(window):
            nearest = float(window[np.argmin(np.abs(window - boundary))])
            prev_ok = nearest - prev_clip[0] >= min_len - 1e-6
            next_ok = next_clip[1] - nearest >= min_len - 1e-6
            if prev_ok and next_ok:
                candidate = nearest
        ends.insert(-1, candidate)
        starts.append(candidate)
    return [(round(s, 6), round(e, 6)) for s, e in zip(starts, ends)]


class AudioSplitService:
    _PROGRESS_PERCENT_PATTERN = re.compile(r"(\d{1,3}(?:\.\d+)?)%")

    def __init__(self, ffmpeg_service: FFmpegService, work_dir: Path, exports_dir: Path, python_bin: str | None = None) -> None:
        self.ffmpeg_service = ffmpeg_service
        self.work_dir = work_dir
        self.exports_dir = exports_dir
        self.python_bin = python_bin or sys.executable

    def extract_audio(self, source: Path, project_id: str, is_cancelled: CancelCheck | None = None) -> Path:
        output = self.work_dir / f"{project_id}_audio.wav"
        command = [
            self.ffmpeg_service.ffmpeg_bin,
            "-y",
            "-i",
            str(source),
            "-vn",
            "-ac",
            "2",
            "-ar",
            str(AUDIO_SAMPLE_RATE),
            "-c:a",
            "pcm_s16le",
            str(output),
        ]
        self.ffmpeg_service.run(command)
        return output

    def _vocals_cache_file(self, project_id: str, backend: str) -> Path:
        return self.work_dir / f"{project_id}_vocals_{backend}.wav"

    def separate_vocals(
        self,
        audio_wav: Path,
        project_id: str,
        settings: AudioSplitSettings,
        on_progress: ProgressCallback | None = None,
        is_cancelled: CancelCheck | None = None,
    ) -> Path:
        cached = self._vocals_cache_file(project_id, settings.backend)
        if cached.exists():
            logger.info("Reusing cached vocals stem %s", cached)
            if on_progress:
                on_progress(100.0, "Reusing cached vocals stem")
            return cached
        if settings.backend == "demucs":
            return self._separate_with_demucs(audio_wav, project_id, cached, on_progress, is_cancelled)
        if settings.backend == "roformer":
            return self._separate_with_roformer(audio_wav, project_id, cached, on_progress, is_cancelled)
        raise ValueError(f"Unsupported audio split backend '{settings.backend}'")

    def _run_with_progress(
        self,
        command: list[str],
        on_progress: ProgressCallback | None,
        is_cancelled: CancelCheck | None,
        label: str,
    ) -> None:
        logger.info("Running %s command: %s", label, subprocess.list2cmdline([str(part) for part in command]))
        try:
            process = subprocess.Popen(
                [str(part) for part in command],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.PIPE,
                text=True,
                errors="replace",
            )
        except FileNotFoundError as exc:
            raise ValueError(f"{label} executable not found. Install the audio split requirements.") from exc
        stderr_chunks: list[str] = []
        assert process.stderr is not None
        while True:
            if is_cancelled and is_cancelled():
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                raise AudioSplitCancelled()
            chunk = process.stderr.read(1)
            if chunk == "" and process.poll() is not None:
                break
            if not chunk:
                continue
            stderr_chunks.append(chunk)
            if chunk in ("\r", "\n") and on_progress:
                line = "".join(stderr_chunks[-400:])
                percents = [float(m) for m in self._PROGRESS_PERCENT_PATTERN.findall(line)]
                if percents:
                    on_progress(min(99.0, max(0.0, percents[-1])), "")
                stderr_chunks = stderr_chunks[-400:]
        return_code = process.wait()
        stderr_text = "".join(stderr_chunks).strip()
        if return_code != 0:
            logger.error("%s failed [returncode=%s, stderr=%r]", label, return_code, stderr_text[-2000:])
            raise ValueError(f"{label} failed with exit code {return_code}: {stderr_text[-500:]}")
        if on_progress:
            on_progress(100.0, "")

    def _separate_with_demucs(
        self,
        audio_wav: Path,
        project_id: str,
        cached: Path,
        on_progress: ProgressCallback | None,
        is_cancelled: CancelCheck | None,
    ) -> Path:
        output_dir = self.work_dir / f"{project_id}_demucs"
        command = [
            self.python_bin,
            "-m",
            "demucs",
            "--two-stems=vocals",
            "-n",
            "htdemucs",
            "-o",
            str(output_dir),
            str(audio_wav),
        ]
        self._run_with_progress(command, on_progress, is_cancelled, "demucs")
        vocals_file = output_dir / "htdemucs" / audio_wav.stem / "vocals.wav"
        if not vocals_file.exists():
            candidates = sorted(output_dir.glob(f"**/{audio_wav.stem}/vocals.wav"))
            if not candidates:
                raise ValueError(f"demucs did not produce a vocals stem in '{output_dir}'")
            vocals_file = candidates[0]
        cached.write_bytes(vocals_file.read_bytes())
        return cached

    def _separate_with_roformer(
        self,
        audio_wav: Path,
        project_id: str,
        cached: Path,
        on_progress: ProgressCallback | None,
        is_cancelled: CancelCheck | None,
    ) -> Path:
        output_dir = self.work_dir / f"{project_id}_roformer"
        output_dir.mkdir(parents=True, exist_ok=True)
        before = {path for path in output_dir.glob("*.wav")}
        command = [
            self.python_bin,
            "-m",
            "audio_separator",
            str(audio_wav),
            "-m",
            ROFORMER_MODEL_NAME,
            "--output_dir",
            str(output_dir),
        ]
        self._run_with_progress(command, on_progress, is_cancelled, "audio-separator (BS-RoFormer)")
        new_files = [path for path in output_dir.glob("*.wav") if path not in before]
        vocal_candidates = [path for path in new_files if "vocal" in path.name.lower()]
        if not vocal_candidates:
            vocal_candidates = new_files
        if not vocal_candidates:
            raise ValueError(f"audio-separator did not produce a vocals stem in '{output_dir}'")
        vocals_file = sorted(vocal_candidates, key=lambda p: p.stat().st_mtime, reverse=True)[0]
        cached.write_bytes(vocals_file.read_bytes())
        return cached

    def detect_vocal_clips(self, vocals_wav: Path, settings: AudioSplitSettings) -> list[tuple[float, float]]:
        try:
            silero_vad = import_module("silero_vad")
        except ModuleNotFoundError as exc:
            raise ValueError("Audio split requires silero-vad. Install requirements to enable it.") from exc
        model = silero_vad.load_silero_vad()
        wav = silero_vad.read_audio(str(vocals_wav), sampling_rate=VAD_SAMPLE_RATE)
        speech_timestamps = silero_vad.get_speech_timestamps(
            wav,
            model,
            sampling_rate=VAD_SAMPLE_RATE,
            threshold=settings.vad_threshold,
            min_speech_duration_ms=int(MIN_VOCAL_CLIP_SECONDS * 1000),
            return_seconds=True,
        )
        segments = [(float(item["start"]), float(item["end"])) for item in speech_timestamps]
        clips = merge_segments(segments, settings.vad_merge_gap)
        clips = [clip for clip in clips if clip[1] - clip[0] >= MIN_VOCAL_CLIP_SECONDS]
        logger.info("Detected %s vocal clips in %s", len(clips), vocals_wav)
        return clips

    def load_mono_samples(self, audio_wav: Path) -> tuple[Any, int]:
        np = _numpy()
        command = [
            self.ffmpeg_service.ffmpeg_bin,
            "-hide_banner",
            "-loglevel",
            "error",
            "-i",
            str(audio_wav),
            "-ac",
            "1",
            "-ar",
            str(AUDIO_SAMPLE_RATE),
            "-f",
            "f32le",
            "-",
        ]
        try:
            result = subprocess.run(command, check=True, capture_output=True)
        except subprocess.CalledProcessError as exc:
            logger.exception("Audio decode failed for %s", audio_wav)
            raise ValueError(f"Failed to decode audio from '{audio_wav}'") from exc
        samples = np.frombuffer(result.stdout, dtype=np.float32)
        return samples, AUDIO_SAMPLE_RATE

    def split_vocal_clips(
        self,
        clips: list[tuple[float, float]],
        vocals_wav: Path,
        settings: AudioSplitSettings,
    ) -> list[tuple[float, float]]:
        samples, sample_rate = self.load_mono_samples(vocals_wav)
        times, rms_values = compute_rms_curve(samples, sample_rate)
        split = split_long_clips(clips, times, rms_values, settings.min_clip_length, settings.max_clip_length)
        logger.info("Split %s vocal clips into %s clips", len(clips), len(split))
        return split

    def detect_beats(self, audio_wav: Path) -> Any:
        np = _numpy()
        try:
            librosa = import_module("librosa")
        except ModuleNotFoundError as exc:
            raise ValueError("Beat alignment requires librosa. Install requirements to enable it.") from exc
        y, sr = librosa.load(str(audio_wav), sr=22050, mono=True)
        _, beat_frames = librosa.beat.beat_track(y=y, sr=sr)
        beat_times = librosa.frames_to_time(beat_frames, sr=sr)
        return np.asarray(beat_times, dtype=np.float64)

    def align_clips_to_beats(
        self,
        clips: list[tuple[float, float]],
        audio_wav: Path,
        settings: AudioSplitSettings,
    ) -> list[tuple[float, float]]:
        if not settings.beat_snap_enabled or len(clips) <= 1:
            return clips
        beat_times = self.detect_beats(audio_wav)
        aligned = snap_boundaries_to_beats(clips, beat_times, settings.beat_snap_tolerance, settings.min_clip_length)
        moved = sum(1 for a, b in zip(clips, aligned) if a != b)
        logger.info("Beat alignment adjusted %s of %s clip boundaries", moved, len(clips))
        return aligned

    def export_clips(self, audio_wav: Path, clips: list[tuple[float, float]]) -> list[AudioClipResult]:
        try:
            soundfile = import_module("soundfile")
        except ModuleNotFoundError as exc:
            raise ValueError("Audio export requires soundfile. Install requirements to enable it.") from exc
        np = _numpy()
        data, sample_rate = soundfile.read(str(audio_wav), always_2d=True)
        timestamp = datetime.now().strftime("%Y%m%d%H%M%S%f")[:-3]
        results: list[AudioClipResult] = []
        for index, (start, end) in enumerate(clips, start=1):
            start_frame = max(0, min(len(data), int(round(start * sample_rate))))
            end_frame = max(start_frame, min(len(data), int(round(end * sample_rate))))
            if end_frame <= start_frame:
                continue
            output_file = self.exports_dir / f"vae_audio_{timestamp}_part{index:03d}.wav"
            counter = 1
            while output_file.exists():
                output_file = self.exports_dir / f"vae_audio_{timestamp}_part{index:03d}_{counter}.wav"
                counter += 1
            soundfile.write(str(output_file), np.asarray(data[start_frame:end_frame]), sample_rate, subtype="PCM_16")
            results.append(
                AudioClipResult(
                    index=index,
                    start=round(start, 6),
                    end=round(end, 6),
                    output_url=f"/exports/{output_file.name}",
                    output_path=str(output_file.resolve()),
                    output_size_bytes=output_file.stat().st_size,
                )
            )
        logger.info("Exported %s audio clips from %s", len(results), audio_wav)
        return results

    def run_pipeline(
        self,
        source: Path,
        project_id: str,
        settings: AudioSplitSettings,
        on_stage: Callable[[str, float, str], None] | None = None,
        is_cancelled: CancelCheck | None = None,
        cancel_event: threading.Event | None = None,
    ) -> list[AudioClipResult]:
        def report(stage: str, progress: float, message: str = "") -> None:
            if on_stage:
                on_stage(stage, progress, message)

        def check_cancelled() -> bool:
            return bool(cancel_event and cancel_event.is_set())

        def ensure_not_cancelled() -> None:
            if check_cancelled():
                raise AudioSplitCancelled()

        report("extract", 0.0, "Extracting audio")
        audio_wav = self.extract_audio(source, project_id, is_cancelled=check_cancelled)
        ensure_not_cancelled()
        report("extract", 100.0, "Audio extracted")

        def separation_progress(percent: float, message: str) -> None:
            report("separate", percent, message or f"Separating vocals with {settings.backend}")

        report("separate", 0.0, "Separating vocals")
        vocals_wav = self.separate_vocals(
            audio_wav,
            project_id,
            settings,
            on_progress=separation_progress,
            is_cancelled=check_cancelled,
        )
        ensure_not_cancelled()

        report("vad", 0.0, "Detecting vocal clips")
        vocal_clips = self.detect_vocal_clips(vocals_wav, settings)
        report("vad", 100.0, f"Detected {len(vocal_clips)} vocal clips")
        ensure_not_cancelled()
        if not vocal_clips:
            raise ValueError("No vocal clips detected; nothing to split")

        report("split", 0.0, "Splitting long vocal clips")
        split_clips = self.split_vocal_clips(vocal_clips, vocals_wav, settings)
        report("split", 100.0, f"Split into {len(split_clips)} clips")
        ensure_not_cancelled()

        report("beats", 0.0, "Aligning split points to beats")
        aligned_clips = self.align_clips_to_beats(split_clips, audio_wav, settings)
        report("beats", 100.0, "Beat alignment complete")
        ensure_not_cancelled()

        report("export", 0.0, "Exporting audio clips")
        results = self.export_clips(audio_wav, aligned_clips)
        report("export", 100.0, f"Exported {len(results)} clips")
        return results

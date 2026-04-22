from __future__ import annotations

from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import convolve, correlate, find_peaks, resample_poly

from .models import BpmCandidate


class AnalysisError(RuntimeError):
    """Raised when BPM analysis cannot produce meaningful results."""


def _load_audio(path: Path) -> tuple[np.ndarray, int]:
    samples, sample_rate = sf.read(path, dtype="float32", always_2d=False)
    if samples.ndim > 1:
        samples = samples.mean(axis=1)

    if samples.size == 0:
        raise AnalysisError(f"Audio file is empty: {path}")
    if sample_rate <= 0:
        raise AnalysisError(f"Audio file has an invalid sample rate: {path}")

    peak = float(np.max(np.abs(samples)))
    if peak == 0.0:
        raise AnalysisError(f"Audio file contains only silence: {path}")

    return samples / peak, sample_rate


def _build_onset_envelope(samples: np.ndarray, sample_rate: int, envelope_rate: int) -> np.ndarray:
    squared = np.square(samples, dtype=np.float32)
    window_size = max(32, int(sample_rate * 0.04))
    smooth = convolve(
        squared,
        np.ones(window_size, dtype=np.float32) / window_size,
        mode="same",
    )
    novelty = np.maximum(0.0, np.diff(smooth, prepend=smooth[0]))

    downsampled = resample_poly(novelty, envelope_rate, sample_rate).astype(np.float32, copy=False)
    if downsampled.size < envelope_rate * 4:
        raise AnalysisError("Audio is too short to estimate BPM reliably. Provide at least four seconds.")

    downsampled -= float(np.mean(downsampled))
    downsampled = np.maximum(downsampled, 0.0)
    if not np.any(downsampled):
        raise AnalysisError("Could not find enough rhythmic energy to estimate BPM.")
    return downsampled


def _rank_candidate_tempos(
    onset_envelope: np.ndarray,
    envelope_rate: int,
    min_bpm: float,
    max_bpm: float,
    limit: int,
) -> tuple[BpmCandidate, ...]:
    autocorrelation = correlate(onset_envelope, onset_envelope, mode="full")
    autocorrelation = autocorrelation[autocorrelation.size // 2 :]

    min_lag = max(1, int(round(60.0 * envelope_rate / max_bpm)))
    max_lag = min(
        autocorrelation.size - 1,
        int(round(60.0 * envelope_rate / min_bpm)),
    )
    if min_lag >= max_lag:
        raise AnalysisError("Unable to derive a usable BPM search window from the audio.")

    search_region = autocorrelation[min_lag : max_lag + 1]
    peaks, _ = find_peaks(search_region, distance=max(1, min_lag // 2))
    if peaks.size == 0:
        peaks = np.argsort(search_region)[-limit:]

    scored_candidates: list[tuple[float, int]] = []
    for relative_peak in peaks:
        lag = int(relative_peak) + min_lag
        score = float(autocorrelation[lag])
        if lag * 2 < autocorrelation.size:
            score += 0.5 * float(autocorrelation[lag * 2])
        if lag * 3 < autocorrelation.size:
            score += 0.25 * float(autocorrelation[lag * 3])
        scored_candidates.append((score, lag))

    scored_candidates.sort(reverse=True)
    top_score = scored_candidates[0][0]
    deduped: list[BpmCandidate] = []
    seen_buckets: set[int] = set()
    for score, lag in scored_candidates:
        bpm = 60.0 * envelope_rate / lag
        bucket = int(round(bpm * 10))
        if bucket in seen_buckets:
            continue
        seen_buckets.add(bucket)
        deduped.append(
            BpmCandidate(
                bpm=round(bpm, 1),
                confidence=round(score / top_score, 3),
            )
        )
        if len(deduped) >= limit:
            break

    if not deduped:
        raise AnalysisError("No BPM candidates were found in the requested range.")
    return tuple(deduped)


def find_bpm_candidates(
    wav_path: str | Path,
    *,
    min_bpm: float = 40.0,
    max_bpm: float = 220.0,
    limit: int = 5,
) -> tuple[BpmCandidate, ...]:
    if limit < 1:
        raise ValueError("limit must be at least 1")
    if min_bpm <= 0 or max_bpm <= 0 or min_bpm >= max_bpm:
        raise ValueError("min_bpm and max_bpm must be positive and min_bpm must be less than max_bpm")

    samples, sample_rate = _load_audio(Path(wav_path))
    envelope_rate = 200
    onset_envelope = _build_onset_envelope(samples, sample_rate, envelope_rate)
    return _rank_candidate_tempos(onset_envelope, envelope_rate, min_bpm, max_bpm, limit)

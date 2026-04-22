from __future__ import annotations

from pathlib import Path

import numpy as np
import soundfile as sf

from bpm_finder.ai import find_bpm_candidates


def _write_click_track(path: Path, bpm: float, duration_seconds: float = 12.0, sample_rate: int = 22050) -> None:
    beat_interval = 60.0 / bpm
    samples = np.zeros(int(duration_seconds * sample_rate), dtype=np.float32)
    click_length = max(1, int(sample_rate * 0.02))

    for beat_time in np.arange(0.0, duration_seconds, beat_interval):
        start = int(beat_time * sample_rate)
        stop = min(samples.size, start + click_length)
        if start >= samples.size:
            break
        samples[start:stop] = 1.0

    sf.write(path, samples, sample_rate)


def test_find_bpm_candidates_detects_click_track(tmp_path: Path) -> None:
    wav_path = tmp_path / "clicks.wav"
    _write_click_track(wav_path, bpm=120.0)

    candidates = find_bpm_candidates(wav_path)

    assert candidates
    assert abs(candidates[0].bpm - 120.0) <= 2.0
    assert candidates[0].confidence == 1.0

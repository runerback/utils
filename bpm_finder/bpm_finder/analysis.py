from __future__ import annotations

from pathlib import Path

from .ai import find_bpm_candidates
from .media import prepared_wav_path
from .models import AnalysisResult


def analyze_path(input_path: str | Path) -> AnalysisResult:
    original_input = str(Path(input_path))
    with prepared_wav_path(input_path) as (wav_path, source_type):
        candidates = find_bpm_candidates(wav_path)

    return AnalysisResult(
        input_path=original_input,
        source_type=source_type,
        candidates=candidates,
    )

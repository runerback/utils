from __future__ import annotations

import json

from bpm_finder.cli import main
from bpm_finder.models import AnalysisResult, BpmCandidate


def test_cli_prints_json(monkeypatch, capsys) -> None:
    monkeypatch.setattr(
        "bpm_finder.cli.analyze_path",
        lambda input_path: AnalysisResult(
            input_path=input_path,
            source_type="wav",
            candidates=(BpmCandidate(bpm=120.0, confidence=1.0),),
        ),
    )

    exit_code = main(["demo.wav"])
    captured = capsys.readouterr()

    assert exit_code == 0
    assert json.loads(captured.out) == {
        "input_path": "demo.wav",
        "source_type": "wav",
        "candidates": [{"bpm": 120.0, "confidence": 1.0}],
    }

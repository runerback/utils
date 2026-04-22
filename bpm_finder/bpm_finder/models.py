from __future__ import annotations

from dataclasses import asdict, dataclass


@dataclass(frozen=True)
class BpmCandidate:
    bpm: float
    confidence: float

    def to_dict(self) -> dict[str, float]:
        return asdict(self)


@dataclass(frozen=True)
class AnalysisResult:
    input_path: str
    source_type: str
    candidates: tuple[BpmCandidate, ...]

    def to_dict(self) -> dict[str, object]:
        return {
            "input_path": self.input_path,
            "source_type": self.source_type,
            "candidates": [candidate.to_dict() for candidate in self.candidates],
        }

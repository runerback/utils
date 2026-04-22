from __future__ import annotations

import argparse
import json
import sys

from .ai import AnalysisError
from .analysis import analyze_path
from .media import MediaError


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="bpm_finder",
        description="Estimate likely BPM values from wav files or videos.",
    )
    parser.add_argument("input_path", help="Path to a wav file or supported video file.")
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    try:
        result = analyze_path(args.input_path)
    except (AnalysisError, MediaError, OSError) as exc:
        print(str(exc), file=sys.stderr)
        return 2

    json.dump(result.to_dict(), sys.stdout, indent=2)
    sys.stdout.write("\n")
    return 0

from __future__ import annotations

import argparse
import json
import logging
from pathlib import Path

from src.config import (
    DEFAULT_BLUR_THRESHOLD,
    DEFAULT_CENTER_ZONE_PERCENT,
    DEFAULT_CONFIDENCE_THRESHOLD,
    DEFAULT_CROP_MARGIN_PERCENT,
    DEFAULT_DEVICE,
    DEFAULT_GRACE_FRAMES,
    DEFAULT_INFERENCE_CONFIDENCE,
    DEFAULT_INFERENCE_IMAGE_SIZE,
    DEFAULT_INPUT_PATH,
    DEFAULT_LOG_DIR,
    DEFAULT_MIN_SEGMENT_FRAMES,
    DEFAULT_MODEL_PATH,
    DEFAULT_OUTPUT_DIR,
    DEFAULT_SMOOTHING_ALPHA,
    DEFAULT_TRACE_EVERY_N_FRAMES,
    RuntimeConfig,
    YOLOV8S_DOWNLOAD_URL,
)
from src.logging_utils import configure_logging
from src.progress import StdoutProgressReporter


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Stabilize bird videos with YOLO + FFmpeg.")
    parser.add_argument(
        "--config",
        type=Path,
        help="Optional JSON config file. CLI flags override values from the file.",
    )
    parser.add_argument(
        "--input",
        type=Path,
        default=None,
        help="Video file or directory to process. Defaults to samples\\.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=None,
        help="Directory where clips, manifests, and debug previews are written.",
    )
    parser.add_argument(
        "--model-path",
        type=Path,
        default=None,
        help="Path to the manually downloaded YOLOv8s weights file.",
    )
    parser.add_argument(
        "--log-dir",
        type=Path,
        default=None,
        help="Directory for run-level tracing logs.",
    )
    parser.add_argument(
        "--device",
        choices=["auto", "cpu", "cuda"],
        default=None,
        help="Inference device. Use cuda when a compatible GPU is available.",
    )
    parser.add_argument("--confidence-threshold", type=float, default=None)
    parser.add_argument("--blur-threshold", type=float, default=None)
    parser.add_argument("--center-zone-percent", type=float, default=None)
    parser.add_argument("--grace-frames", type=int, default=None)
    parser.add_argument("--smoothing-alpha", type=float, default=None)
    parser.add_argument(
        "--min-segment-frames",
        type=int,
        default=None,
        help="Minimum tracked frames required before a segment is exported as a clip.",
    )
    parser.add_argument(
        "--inference-confidence",
        type=float,
        default=None,
        help="YOLO confidence floor used before target scoring.",
    )
    parser.add_argument(
        "--detect-image-size",
        type=int,
        default=None,
        help="YOLO inference image size. Lower values are faster on CPU.",
    )
    parser.add_argument(
        "--trace-every-n-frames",
        type=int,
        default=None,
        help="Write a progress trace every N frames.",
    )
    parser.add_argument(
        "--crop-margin-percent",
        type=float,
        default=None,
        help="Shrink the solved crop window by this percentage to avoid edge artifacts.",
    )
    parser.add_argument(
        "--no-debug-preview",
        action="store_true",
        help="Disable the full-length annotated debug preview output.",
    )
    parser.add_argument(
        "--print-model-url",
        action="store_true",
        help="Print the default model download URL and exit.",
    )
    return parser


def load_config_data(config_path: Path | None) -> dict[str, object]:
    if config_path is None:
        return {}
    if not config_path.exists():
        raise FileNotFoundError(f"Config file does not exist: {config_path}")
    data = json.loads(config_path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("Config file must contain a top-level JSON object.")
    return data


def resolve_setting(
    cli_value: object,
    config_data: dict[str, object],
    key: str,
    default: object,
) -> object:
    if cli_value is not None:
        return cli_value
    return config_data.get(key, default)


def build_config(args: argparse.Namespace) -> RuntimeConfig:
    config_data = load_config_data(args.config)
    device = str(resolve_setting(args.device, config_data, "device", DEFAULT_DEVICE))
    if device not in {"auto", "cpu", "cuda"}:
        raise ValueError("device must be one of: auto, cpu, cuda")

    return RuntimeConfig(
        input_path=Path(str(resolve_setting(args.input, config_data, "input_path", DEFAULT_INPUT_PATH))),
        output_dir=Path(str(resolve_setting(args.output_dir, config_data, "output_dir", DEFAULT_OUTPUT_DIR))),
        model_path=Path(str(resolve_setting(args.model_path, config_data, "model_path", DEFAULT_MODEL_PATH))),
        log_dir=Path(str(resolve_setting(args.log_dir, config_data, "log_dir", DEFAULT_LOG_DIR))),
        device=device,
        confidence_threshold=float(
            resolve_setting(args.confidence_threshold, config_data, "confidence_threshold", DEFAULT_CONFIDENCE_THRESHOLD)
        ),
        blur_threshold=float(resolve_setting(args.blur_threshold, config_data, "blur_threshold", DEFAULT_BLUR_THRESHOLD)),
        center_zone_percent=float(
            resolve_setting(args.center_zone_percent, config_data, "center_zone_percent", DEFAULT_CENTER_ZONE_PERCENT)
        ),
        grace_frames=int(resolve_setting(args.grace_frames, config_data, "grace_frames", DEFAULT_GRACE_FRAMES)),
        smoothing_alpha=float(
            resolve_setting(args.smoothing_alpha, config_data, "smoothing_alpha", DEFAULT_SMOOTHING_ALPHA)
        ),
        min_segment_frames=int(
            resolve_setting(args.min_segment_frames, config_data, "min_segment_frames", DEFAULT_MIN_SEGMENT_FRAMES)
        ),
        inference_confidence=float(
            resolve_setting(args.inference_confidence, config_data, "inference_confidence", DEFAULT_INFERENCE_CONFIDENCE)
        ),
        inference_image_size=int(
            resolve_setting(args.detect_image_size, config_data, "inference_image_size", DEFAULT_INFERENCE_IMAGE_SIZE)
        ),
        trace_every_n_frames=max(
            1,
            int(resolve_setting(args.trace_every_n_frames, config_data, "trace_every_n_frames", DEFAULT_TRACE_EVERY_N_FRAMES)),
        ),
        crop_margin_percent=float(
            resolve_setting(args.crop_margin_percent, config_data, "crop_margin_percent", DEFAULT_CROP_MARGIN_PERCENT)
        ),
        debug_preview=(not args.no_debug_preview)
        if args.no_debug_preview
        else bool(resolve_setting(None, config_data, "debug_preview", True)),
    )


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    if args.print_model_url:
        print(f"models\\yolov8s.pt -> {YOLOV8S_DOWNLOAD_URL}")
        return 0

    try:
        config = build_config(args)
        log_path = configure_logging(config.log_dir)
        logger = logging.getLogger("stable_bird.cli")
        logger.info("Run started")
        print(f"Tracing to {log_path}")
        from src.pipeline import process_inputs

        summaries = process_inputs(config, reporter=StdoutProgressReporter())
    except Exception as exc:
        logging.getLogger("stable_bird.cli").exception("Run failed")
        parser.exit(status=1, message=f"Error: {exc}\nSee logs: logs\\stable_bird.log\n")

    for summary in summaries:
        print(f"Processed {summary.source_video}")
        print(f"  manifest: {summary.manifest_path}")
        print(f"  accepted segments: {len(summary.accepted_segments)}")
        print(f"  rejected segments: {len(summary.rejected_segments)}")
        if summary.debug_preview_path:
            print(f"  debug preview: {summary.debug_preview_path}")
        if not summary.accepted_segments:
            print("  no clips were exported; try lowering --min-segment-frames or the detection thresholds")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

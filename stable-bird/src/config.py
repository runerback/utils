from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


VIDEO_EXTENSIONS = {".avi", ".mkv", ".mov", ".mp4"}
YOLOV8S_DOWNLOAD_URL = "https://github.com/ultralytics/assets/releases/download/v8.4.0/yolov8s.pt"
DEFAULT_INPUT_PATH = Path("samples")
DEFAULT_OUTPUT_DIR = Path("output")
DEFAULT_MODEL_PATH = Path("models") / "yolov8s.pt"
DEFAULT_LOG_DIR = Path("logs")
DEFAULT_DEVICE = "auto"
DEFAULT_CONFIDENCE_THRESHOLD = 0.25
DEFAULT_BLUR_THRESHOLD = 80.0
DEFAULT_CENTER_ZONE_PERCENT = 20.0
DEFAULT_GRACE_FRAMES = 6
DEFAULT_SMOOTHING_ALPHA = 0.2
DEFAULT_MIN_SEGMENT_FRAMES = 10
DEFAULT_INFERENCE_IMAGE_SIZE = 640
DEFAULT_TRACE_EVERY_N_FRAMES = 120
DEFAULT_INFERENCE_CONFIDENCE = 0.05
DEFAULT_CROP_MARGIN_PERCENT = 2.0
DEFAULT_TRACKING_ANCHOR_X_PERCENT = 50.0
DEFAULT_TRACKING_ANCHOR_Y_PERCENT = 50.0


@dataclass(frozen=True)
class RuntimeConfig:
    input_path: Path
    output_dir: Path
    model_path: Path
    log_dir: Path
    device: str
    confidence_threshold: float
    blur_threshold: float
    center_zone_percent: float
    grace_frames: int
    smoothing_alpha: float
    min_segment_frames: int
    inference_confidence: float
    inference_image_size: int
    trace_every_n_frames: int
    crop_margin_percent: float
    tracking_anchor_x_percent: float
    tracking_anchor_y_percent: float
    debug_preview: bool

    @property
    def center_zone_fraction(self) -> float:
        return self.center_zone_percent / 100.0


def resolve_input_videos(input_path: Path) -> list[Path]:
    if not input_path.exists():
        raise FileNotFoundError(f"Input path does not exist: {input_path}")

    if input_path.is_file():
        if input_path.suffix.lower() not in VIDEO_EXTENSIONS:
            raise ValueError(f"Unsupported input file type: {input_path.suffix}")
        return [input_path]

    videos = sorted(
        candidate
        for candidate in input_path.iterdir()
        if candidate.is_file() and candidate.suffix.lower() in VIDEO_EXTENSIONS
    )
    if not videos:
        raise FileNotFoundError(f"No supported videos found in: {input_path}")
    return videos


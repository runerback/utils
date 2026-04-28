from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from src.config import RuntimeConfig
from src.report import write_manifest
from src.types import ProcessingSummary, VideoInfo


class ReportTests(unittest.TestCase):
    def test_write_manifest_includes_tracking_anchor_config(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir_name:
            temp_dir = Path(temp_dir_name)
            manifest_path = temp_dir / "manifest.json"
            config = RuntimeConfig(
                input_path=Path("samples"),
                output_dir=Path("output"),
                model_path=Path("models") / "yolov8s.pt",
                log_dir=Path("logs"),
                device="auto",
                confidence_threshold=0.25,
                blur_threshold=80.0,
                center_zone_percent=20.0,
                grace_frames=6,
                smoothing_alpha=0.2,
                min_segment_frames=10,
                inference_confidence=0.05,
                inference_image_size=640,
                trace_every_n_frames=120,
                crop_margin_percent=2.0,
                tracking_anchor_x_percent=50.0,
                tracking_anchor_y_percent=35.0,
                debug_preview=True,
            )
            video_info = VideoInfo(
                source_path="sample.mp4",
                width=1920,
                height=1080,
                fps=60.0,
                frame_count=120,
                duration_seconds=2.0,
            )
            summary = ProcessingSummary(
                source_video="sample.mp4",
                output_root=str(temp_dir / "output"),
            )

            write_manifest(manifest_path, config, video_info, summary)

            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

        self.assertEqual(manifest["config"]["tracking_anchor_x_percent"], 50.0)
        self.assertEqual(manifest["config"]["tracking_anchor_y_percent"], 35.0)


if __name__ == "__main__":
    unittest.main()

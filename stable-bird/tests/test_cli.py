from __future__ import annotations

import io
import json
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stderr
from pathlib import Path
from unittest.mock import patch

from stable_bird.cli import build_config, build_parser, main

PROJECT_ROOT = Path(__file__).resolve().parents[1]


class CliEntrypointTests(unittest.TestCase):
    def test_documented_cli_module_prints_model_url(self) -> None:
        result = subprocess.run(
            [sys.executable, "-m", "stable_bird.cli", "--print-model-url"],
            cwd=PROJECT_ROOT,
            capture_output=True,
            text=True,
            check=False,
        )

        self.assertEqual(result.returncode, 0, msg=result.stderr)
        self.assertIn("models\\yolov8s.pt -> https://github.com/ultralytics/assets/releases/download/v8.4.0/yolov8s.pt", result.stdout)

    def test_documented_web_module_exposes_help(self) -> None:
        result = subprocess.run(
            [sys.executable, "-m", "stable_bird.web", "--help"],
            cwd=PROJECT_ROOT,
            capture_output=True,
            text=True,
            check=False,
        )

        self.assertEqual(result.returncode, 0, msg=result.stderr)
        self.assertIn("Run the stable-bird local web UI.", result.stdout)
        self.assertIn("--host", result.stdout)
        self.assertIn("--port", result.stdout)


class CliConfigTests(unittest.TestCase):
    def test_build_config_prefers_cli_values_over_json_config(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir_name:
            temp_dir = Path(temp_dir_name)
            config_path = temp_dir / "bird-config.json"
            config_path.write_text(
                json.dumps(
                    {
                        "input_path": "samples",
                        "output_dir": "from-config-output",
                        "device": "auto",
                        "tracking_anchor_x_percent": 48,
                        "trace_every_n_frames": 45,
                    }
                ),
                encoding="utf-8",
            )

            args = build_parser().parse_args(
                [
                    "--config",
                    str(config_path),
                    "--input",
                    "override.mp4",
                    "--device",
                    "cpu",
                    "--tracking-anchor-y-percent",
                    "35",
                ]
            )

            config = build_config(args)

        self.assertEqual(config.input_path, Path("override.mp4"))
        self.assertEqual(config.output_dir, Path("from-config-output"))
        self.assertEqual(config.device, "cpu")
        self.assertEqual(config.tracking_anchor_x_percent, 48.0)
        self.assertEqual(config.tracking_anchor_y_percent, 35.0)
        self.assertEqual(config.trace_every_n_frames, 45)

    def test_build_config_rejects_out_of_range_tracking_anchor_percent(self) -> None:
        args = build_parser().parse_args(["--tracking-anchor-y-percent", "125"])

        with self.assertRaisesRegex(ValueError, "tracking_anchor_y_percent must be between 0 and 100"):
            build_config(args)

    def test_main_exits_with_error_for_missing_config_file(self) -> None:
        missing_config = PROJECT_ROOT / "missing-config.json"
        stderr = io.StringIO()

        with patch.object(sys, "argv", ["stable_bird.cli", "--config", str(missing_config)]):
            with redirect_stderr(stderr):
                with self.assertRaises(SystemExit) as context:
                    main()

        self.assertEqual(context.exception.code, 1)
        self.assertIn("Error: Config file does not exist", stderr.getvalue())


if __name__ == "__main__":
    unittest.main()


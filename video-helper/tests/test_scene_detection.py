import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

import numpy as np

from app.scene_detection import SceneDetectionService, TransNetV2SceneDetector
from app.schemas import SceneSplitState, VideoMetadata


class _FakeOrtValue:
    def __init__(self, name: str) -> None:
        self.name = name


class _FakeOrtSession:
    def __init__(self, outputs: list[np.ndarray]) -> None:
        self._outputs = outputs
        self._call_index = 0

    def get_inputs(self) -> list[_FakeOrtValue]:
        return [_FakeOrtValue("input")]

    def get_outputs(self) -> list[_FakeOrtValue]:
        return [_FakeOrtValue("output")]

    def get_providers(self) -> list[str]:
        return ["CUDAExecutionProvider", "CPUExecutionProvider"]

    def run(self, output_names, inputs):
        del output_names, inputs
        output = self._outputs[self._call_index]
        self._call_index += 1
        return [output]


class _FakeOrtModule:
    def __init__(self, outputs: list[np.ndarray]) -> None:
        self._outputs = outputs

    @staticmethod
    def get_available_providers() -> list[str]:
        return ["CUDAExecutionProvider", "CPUExecutionProvider"]

    @staticmethod
    def set_default_logger_severity(level: int) -> None:
        del level

    class SessionOptions:
        def __init__(self) -> None:
            self.log_severity_level = 0

    def InferenceSession(self, model_path, sess_options=None, providers=None):
        del model_path, sess_options, providers
        return _FakeOrtSession(self._outputs)


class SceneDetectionTests(unittest.TestCase):
    def setUp(self) -> None:
        self.metadata = VideoMetadata(
            width=1920,
            height=1080,
            duration=10.0,
            fps=30.0,
            frame_count=300,
            video_codec="h264",
            audio_codec="aac",
            container_format="mov,mp4,m4a,3gp,3g2,mj2",
        )

    def test_scene_detection_service_uses_ffmpeg_detector(self) -> None:
        ffmpeg = Mock()
        ffmpeg.detect_scene_changes.return_value = [1.0, 4.0]
        service = SceneDetectionService(ffmpeg)

        timestamps = service.detect_scene_changes(Path("clip.mp4"), self.metadata, SceneSplitState(detector="ffmpeg"))

        ffmpeg.detect_scene_changes.assert_called_once_with(Path("clip.mp4"), 0.4)
        self.assertEqual(timestamps, [1.0, 4.0])

    def test_transnetv2_detector_prefers_cuda_and_returns_cut_timestamps(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            model_path = Path(temp_dir) / "transnetv2.onnx"
            model_path.write_bytes(b"onnx")
            detector = TransNetV2SceneDetector(Mock(ffmpeg_bin="ffmpeg"), model_path=model_path)

            raw_scores = np.zeros((1, 100, 1), dtype=np.float32)
            raw_scores[0, 28, 0] = 0.9
            raw_scores[0, 31, 0] = 0.9
            fake_ort = _FakeOrtModule([raw_scores])
            fake_stdout = bytes(10 * 27 * 48 * 3)

            with patch("app.scene_detection.import_module", side_effect=lambda name: np if name == "numpy" else fake_ort):
                with patch("app.scene_detection.subprocess.run") as mock_run:
                    mock_run.return_value.stdout = fake_stdout
                    timestamps = detector.detect_scene_changes(
                        Path("clip.mp4"),
                        self.metadata,
                        SceneSplitState(detector="ai", ai_sensitivity=0.5),
                    )

        self.assertEqual(timestamps, [3.0, 6.0])
        self.assertEqual(detector.active_provider, "CUDAExecutionProvider")

    def test_transnetv2_detector_raises_clear_error_when_model_missing(self) -> None:
        detector = TransNetV2SceneDetector(Mock(ffmpeg_bin="ffmpeg"), model_path=Path("missing-model.onnx"))
        fake_ort = _FakeOrtModule([np.zeros((1, 100, 1), dtype=np.float32)])
        with patch("app.scene_detection.import_module", side_effect=lambda name: np if name == "numpy" else fake_ort):
            with self.assertRaisesRegex(ValueError, "AI scene detector model not found"):
                detector.detect_scene_changes(
                    Path("clip.mp4"),
                    self.metadata,
                    SceneSplitState(detector="ai", ai_sensitivity=0.5),
                )


if __name__ == "__main__":
    unittest.main()

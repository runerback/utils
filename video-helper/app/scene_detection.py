from __future__ import annotations

import math
import os
import subprocess
from importlib import import_module
from pathlib import Path
from typing import Any

from .schemas import SceneSplitState, VideoMetadata

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_TRANSNETV2_MODEL_PATH = ROOT / "models" / "transnetv2.onnx"
_TRANSNET_HEIGHT = 27
_TRANSNET_WIDTH = 48
_TRANSNET_CHANNELS = 3
_TRANSNET_WINDOW = 100
_TRANSNET_STRIDE = 50
_TRANSNET_WINDOW_MARGIN = 25


class FFmpegSceneDetector:
    def __init__(self, ffmpeg_service: Any) -> None:
        self.ffmpeg_service = ffmpeg_service

    def detect_scene_changes(
        self,
        source: Path,
        metadata: VideoMetadata,
        scene_split: SceneSplitState,
    ) -> list[float]:
        del metadata
        return self.ffmpeg_service.detect_scene_changes(source, scene_split.threshold)


class TransNetV2SceneDetector:
    def __init__(self, ffmpeg_service: Any, model_path: Path | None = None) -> None:
        self.ffmpeg_service = ffmpeg_service
        self._configured_model_path = model_path
        self._session: Any | None = None
        self._input_name: str | None = None
        self._output_names: list[str] | None = None
        self._active_provider: str | None = None

    @property
    def active_provider(self) -> str | None:
        return self._active_provider

    def detect_scene_changes(
        self,
        source: Path,
        metadata: VideoMetadata,
        scene_split: SceneSplitState,
    ) -> list[float]:
        np = self._numpy()
        self._ensure_session()
        frames = self._extract_frames(source, np)
        if not len(frames):
            return []
        predictions = self._predict_frames(frames, np)
        timestamps = self._prediction_indices_to_timestamps(
            self._prediction_indices(predictions, scene_split.ai_sensitivity, np),
            metadata,
            len(frames),
        )
        return self._dedupe_timestamps(timestamps)

    def _numpy(self) -> Any:
        try:
            return import_module("numpy")
        except ModuleNotFoundError as exc:
            raise ValueError(
                "AI scene detection requires numpy. Install requirements or switch Scene Split detector to FFmpeg."
            ) from exc

    def _onnxruntime(self) -> Any:
        try:
            return import_module("onnxruntime")
        except ModuleNotFoundError as exc:
            raise ValueError(
                "AI scene detection requires onnxruntime. Install requirements or switch Scene Split detector to FFmpeg."
            ) from exc

    def _model_path(self) -> Path:
        if self._configured_model_path is not None:
            return self._configured_model_path
        configured = os.getenv("VAE_TRANSNETV2_MODEL_PATH", "").strip()
        if configured:
            return Path(configured).expanduser()
        return DEFAULT_TRANSNETV2_MODEL_PATH

    def _ensure_session(self) -> tuple[Any, str, list[str]]:
        if self._session is not None and self._input_name is not None and self._output_names is not None:
            return self._session, self._input_name, self._output_names

        ort = self._onnxruntime()
        model_path = self._model_path()
        if not model_path.exists():
            raise ValueError(
                "AI scene detector model not found at "
                f"'{model_path}'. Download a TransNetV2 ONNX model to this path or set VAE_TRANSNETV2_MODEL_PATH."
            )

        available_providers = list(ort.get_available_providers())
        providers: list[str] = []
        if "CUDAExecutionProvider" in available_providers:
            providers.append("CUDAExecutionProvider")
        if "CPUExecutionProvider" in available_providers:
            providers.append("CPUExecutionProvider")
        if not providers:
            raise ValueError("ONNX Runtime did not report a usable execution provider.")

        session_options = ort.SessionOptions()
        if hasattr(session_options, "log_severity_level"):
            session_options.log_severity_level = 3
        if hasattr(ort, "set_default_logger_severity"):
            ort.set_default_logger_severity(3)

        session = ort.InferenceSession(str(model_path), sess_options=session_options, providers=providers)
        self._session = session
        self._input_name = session.get_inputs()[0].name
        self._output_names = [output.name for output in session.get_outputs()]
        provider_list = session.get_providers() if hasattr(session, "get_providers") else providers
        self._active_provider = provider_list[0] if provider_list else providers[0]
        print(f"[SceneDetection] TransNetV2 provider: {self._active_provider}")
        return session, self._input_name, self._output_names

    def _extract_frames(self, source: Path, np: Any) -> Any:
        command = [
            self.ffmpeg_service.ffmpeg_bin,
            "-hide_banner",
            "-loglevel",
            "error",
            "-i",
            str(source),
            "-vf",
            f"scale={_TRANSNET_WIDTH}:{_TRANSNET_HEIGHT}:flags=area,format=rgb24",
            "-f",
            "rawvideo",
            "-pix_fmt",
            "rgb24",
            "-",
        ]
        result = subprocess.run(command, check=True, capture_output=True)
        frame_size = _TRANSNET_HEIGHT * _TRANSNET_WIDTH * _TRANSNET_CHANNELS
        if len(result.stdout) % frame_size != 0:
            raise ValueError("AI scene detector could not decode complete RGB frames from FFmpeg output.")
        frame_count = len(result.stdout) // frame_size
        if frame_count <= 0:
            raise ValueError("AI scene detector could not decode any frames from the source video.")
        return np.frombuffer(result.stdout, dtype=np.uint8).reshape(
            (frame_count, _TRANSNET_HEIGHT, _TRANSNET_WIDTH, _TRANSNET_CHANNELS)
        )

    def _predict_frames(self, frames: Any, np: Any) -> Any:
        session, input_name, output_names = self._ensure_session()
        windows: list[Any] = []
        for window in self._iter_windows(frames, np):
            outputs = session.run(output_names, {input_name: window.astype(np.float32)})
            if not outputs:
                raise ValueError("TransNetV2 inference did not return any output tensors.")
            scores = self._normalize_scores(outputs[0], np)
            reshaped = scores.reshape(scores.shape[0], scores.shape[1], -1)
            windows.append(reshaped[0, _TRANSNET_WINDOW_MARGIN : _TRANSNET_WINDOW_MARGIN + _TRANSNET_STRIDE, 0])
        if not windows:
            return np.array([], dtype=np.float32)
        return np.concatenate(windows)[: len(frames)]

    def _iter_windows(self, frames: Any, np: Any):
        frame_count = len(frames)
        start_padding = _TRANSNET_WINDOW_MARGIN
        trailing_frames = frame_count % _TRANSNET_STRIDE
        end_padding = _TRANSNET_WINDOW_MARGIN + (
            _TRANSNET_STRIDE - trailing_frames if trailing_frames != 0 else _TRANSNET_STRIDE
        )
        start_frame = np.expand_dims(frames[0], 0)
        end_frame = np.expand_dims(frames[-1], 0)
        padded = np.concatenate(
            [start_frame] * start_padding + [frames] + [end_frame] * end_padding,
            axis=0,
        )
        for offset in range(0, len(padded) - _TRANSNET_WINDOW + 1, _TRANSNET_STRIDE):
            yield padded[offset : offset + _TRANSNET_WINDOW][np.newaxis]

    def _normalize_scores(self, raw_scores: Any, np: Any) -> Any:
        scores = np.asarray(raw_scores, dtype=np.float32)
        if scores.size and (float(scores.min()) < 0.0 or float(scores.max()) > 1.0):
            scores = 1.0 / (1.0 + np.exp(-scores))
        return scores

    def _prediction_indices(self, predictions: Any, sensitivity: float, np: Any) -> list[int]:
        threshold = self._prediction_threshold_from_sensitivity(sensitivity)
        binary = (predictions > threshold).astype(np.uint8)
        indices: list[int] = []
        previous = 0
        for index, value in enumerate(binary):
            current = int(value)
            if previous == 0 and current == 1 and index > 0:
                indices.append(index)
            previous = current
        return indices

    def _prediction_indices_to_timestamps(
        self,
        cut_indices: list[int],
        metadata: VideoMetadata,
        extracted_frame_count: int,
    ) -> list[float]:
        if not cut_indices:
            return []
        if metadata.duration <= 0:
            raise ValueError("Video duration must be greater than 0 for AI scene detection.")
        if extracted_frame_count <= 0:
            raise ValueError("Extracted frame count must be greater than 0 for AI scene detection.")
        frame_duration = metadata.duration / extracted_frame_count
        return [
            round(min(metadata.duration, max(0.0, cut_index * frame_duration)), 6)
            for cut_index in cut_indices
        ]

    def _dedupe_timestamps(self, timestamps: list[float], tolerance: float = 1e-3) -> list[float]:
        deduped: list[float] = []
        for timestamp in timestamps:
            if not deduped or math.fabs(timestamp - deduped[-1]) > tolerance:
                deduped.append(timestamp)
        return deduped

    def _prediction_threshold_from_sensitivity(self, sensitivity: float) -> float:
        normalized = max(0.01, min(1.0, sensitivity))
        return max(0.05, min(0.95, 1.0 - normalized))


class SceneDetectionService:
    def __init__(self, ffmpeg_service: Any, model_path: Path | None = None) -> None:
        self.ffmpeg_service = ffmpeg_service
        self._ffmpeg_detector = FFmpegSceneDetector(ffmpeg_service)
        self._ai_detector = TransNetV2SceneDetector(ffmpeg_service, model_path=model_path)

    def detect_scene_changes(
        self,
        source: Path,
        metadata: VideoMetadata,
        scene_split: SceneSplitState,
    ) -> list[float]:
        if scene_split.detector == "ffmpeg":
            return self._ffmpeg_detector.detect_scene_changes(source, metadata, scene_split)
        if scene_split.detector == "ai":
            return self._ai_detector.detect_scene_changes(source, metadata, scene_split)
        raise ValueError(f"Unsupported scene detector '{scene_split.detector}'")

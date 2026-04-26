import logging
import os
import re
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from fastapi import HTTPException

from app.main import (
    configure_logging,
    create_project_from_path,
    download_export,
    estimate_export_gif,
    get_project,
    get_project_original,
    index,
    render_preview,
    render_export,
    render_export_gif,
    resolve_log_file,
    trim_frames,
    update_state,
    resolve_export_dir,
    resolve_uploads_dir,
    resolve_work_dir,
)
from app.schemas import EditState, LocalProjectCreateRequest, ProjectPaths, StateUpdateRequest, VideoMetadata


class _FakeStorage:
    def __init__(self, paths: ProjectPaths, metadata: VideoMetadata, state: EditState) -> None:
        self._paths = paths
        self._metadata = metadata
        self._state = state
        self.work = paths.preview_file.parent
        self.uploads = paths.original_file.parent
        self.exports = paths.export_file.parent
        self.saved_paths: ProjectPaths | None = None
        self.saved_metadata: VideoMetadata | None = None
        self.saved_state: EditState | None = None
        self.created_source_file: Path | None = None

    def load_project(self, project_id: str) -> tuple[ProjectPaths, VideoMetadata, EditState]:
        if project_id != self._paths.project_id:
            raise FileNotFoundError(f"Project '{project_id}' not found")
        return self._paths, self._metadata, self._state

    def build_export_file_path(self, suffix: str = ".mp4") -> Path:
        return self._paths.export_file.parent / f"vae_20260405164322111{suffix}"

    def build_export_filename(self, suffix: str = ".mp4") -> str:
        return f"vae_20260405164322111{suffix}"

    def create_project_paths_from_source(self, source_file: Path) -> ProjectPaths:
        self.created_source_file = source_file
        return self._paths

    def save_project(self, paths: ProjectPaths, metadata: VideoMetadata, state: EditState) -> None:
        self.saved_paths = paths
        self.saved_metadata = metadata
        self.saved_state = state


class _FakeFFmpeg:
    def __init__(self, metadata: VideoMetadata | None = None) -> None:
        self.ran_commands: list[list[str]] = []
        self.metadata = metadata or VideoMetadata(
            width=1920,
            height=1080,
            duration=8.0,
            fps=30.0,
            frame_count=240,
            video_codec="h264",
            audio_codec="aac",
            container_format="mov,mp4,m4a,3gp,3g2,mj2",
        )

    def probe(self, path: Path) -> VideoMetadata:
        return self.metadata

    def validate_state(self, metadata: VideoMetadata, state: EditState) -> None:
        return None

    def frame_interval(self, metadata: VideoMetadata) -> float:
        if metadata.fps <= 0:
            return 0.0
        return 1.0 / metadata.fps

    def clamp_frame_timestamp(self, metadata: VideoMetadata, timestamp: float) -> float:
        safe_ts = max(0.0, min(timestamp, metadata.duration))
        frame_interval = self.frame_interval(metadata)
        if frame_interval <= 0:
            return round(safe_ts, 6)
        return round(min(safe_ts, max(0.0, metadata.duration - frame_interval)), 6)

    def adjusted_scene_split_lengths(self, metadata: VideoMetadata, min_len: float, max_len: float) -> tuple[float, float]:
        tolerance = min(0.1, self.frame_interval(metadata))
        return max(1e-6, min_len - tolerance), max_len + tolerance

    def build_export_command(
        self,
        original_file: Path,
        export_file: Path,
        metadata: VideoMetadata,
        state: EditState,
    ) -> list[str]:
        return ["ffmpeg", "-i", str(original_file), str(export_file)]

    def build_export_gif_command(
        self,
        original_file: Path,
        export_file: Path,
        metadata: VideoMetadata,
        state: EditState,
    ) -> list[str]:
        return ["ffmpeg", "-i", str(original_file), "-gif", str(export_file)]

    def build_preview_command(
        self,
        original_file: Path,
        preview_file: Path,
        metadata: VideoMetadata,
        state: EditState,
    ) -> list[str]:
        return ["ffmpeg", "-i", str(original_file), str(preview_file)]

    def build_preview_segment_command(
        self,
        original_file: Path,
        preview_file: Path,
        metadata: VideoMetadata,
        state: EditState,
        segment_start: float,
        segment_end: float,
    ) -> list[str]:
        return ["ffmpeg", "-ss", f"{segment_start}", "-to", f"{segment_end}", str(preview_file)]

    def build_export_segment_command(
        self,
        original_file: Path,
        export_file: Path,
        metadata: VideoMetadata,
        state: EditState,
        segment_start: float,
        segment_end: float,
    ) -> list[str]:
        return ["ffmpeg", "-ss", f"{segment_start}", "-to", f"{segment_end}", str(export_file)]

    def build_export_gif_segment_command(
        self,
        original_file: Path,
        export_file: Path,
        metadata: VideoMetadata,
        state: EditState,
        segment_start: float,
        segment_end: float,
    ) -> list[str]:
        return ["ffmpeg", "-gif-ss", f"{segment_start}", "-gif-to", f"{segment_end}", str(export_file)]

    def build_player_proxy_command(self, original_file: Path, proxy_file: Path) -> list[str]:
        return ["ffmpeg", "-i", str(original_file), "-c:v", "libx264", str(proxy_file)]

    def requires_player_proxy(self, metadata: VideoMetadata) -> bool:
        return metadata.video_codec == "hevc"

    def detect_scene_changes(self, path: Path, threshold: float) -> list[float]:
        return [2.0, 5.0, 7.0]

    def build_scene_split_segments(
        self,
        duration: float,
        candidate_cuts: list[float],
        min_len: float,
        max_len: float,
    ) -> list[tuple[float, float]]:
        if min_len <= duration <= max_len:
            return [(0.0, round(duration, 6))]
        return [(0.0, 3.0), (3.0, 8.0)]

    def build_fixed_length_segments(self, duration: float, clip_length: float) -> list[tuple[float, float]]:
        return [(0.0, clip_length), (clip_length, duration)]

    def build_frame_image_command(self, source: Path, output: Path, timestamp: float) -> list[str]:
        return ["ffmpeg", "-ss", f"{timestamp:.6f}", "-frames:v", "1", str(output)]

    def estimate_gif_size(
        self,
        metadata: VideoMetadata,
        state: EditState,
        segment_start: float | None = None,
        segment_end: float | None = None,
    ) -> int:
        start = 0.0 if segment_start is None else segment_start
        end = state.trim.end or metadata.duration if segment_end is None else segment_end
        return int((end - start) * 1_000_000)

    def run(self, command: list[str]) -> None:
        self.ran_commands.append(command)


class _FakeSceneDetection:
    def __init__(self, timestamps: list[float] | None = None) -> None:
        self.timestamps = timestamps or [2.0, 5.0, 7.0]
        self.calls: list[tuple[Path, VideoMetadata, object]] = []

    def detect_scene_changes(self, path: Path, metadata: VideoMetadata, scene_split) -> list[float]:
        self.calls.append((path, metadata, scene_split))
        return self.timestamps


class MainTests(unittest.TestCase):
    def test_update_state_logs_validation_failures(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=root / "uploads" / f"{project_id}.mp4",
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            stored_state = EditState()
            fake_storage = _FakeStorage(paths, metadata, stored_state)
            fake_ffmpeg = _FakeFFmpeg(metadata=metadata)
            fake_ffmpeg.validate_state = Mock(side_effect=ValueError("broken state"))  # type: ignore[method-assign]

            with (
                patch("app.main.storage", fake_storage),
                patch("app.main.ffmpeg", fake_ffmpeg),
                self.assertLogs("app.main", level="ERROR") as captured,
            ):
                with self.assertRaises(HTTPException) as raised:
                    update_state(project_id, StateUpdateRequest(state=EditState()))

            self.assertEqual(raised.exception.status_code, 400)
            self.assertEqual(raised.exception.detail, "broken state")
            self.assertIn("Failed to save project state", "\n".join(captured.output))

    def test_resolve_export_dir_uses_cli_flag_value(self) -> None:
        with patch.dict("os.environ", {"VAE_EXPORT_PATH": ""}, clear=False):
            with patch("sys.argv", ["python", "-m", "app.main", "--export-path", "custom_exports"]):
                resolved = resolve_export_dir()
        self.assertEqual(resolved, (Path(__file__).resolve().parent.parent / "custom_exports"))

    def test_resolve_export_dir_uses_cli_equals_value(self) -> None:
        with patch.dict("os.environ", {"VAE_EXPORT_PATH": ""}, clear=False):
            with patch("sys.argv", ["python", "-m", "app.main", "--export-path=custom_exports"]):
                resolved = resolve_export_dir()
        self.assertEqual(resolved, (Path(__file__).resolve().parent.parent / "custom_exports"))

    def test_resolve_export_dir_prefers_environment_value(self) -> None:
        env_path = "env_exports"
        with patch.dict("os.environ", {"VAE_EXPORT_PATH": env_path}, clear=False):
            with patch("sys.argv", ["python", "-m", "app.main", "--export-path", "cli_exports"]):
                resolved = resolve_export_dir()
        self.assertEqual(resolved, (Path(__file__).resolve().parent.parent / env_path))

    def test_resolve_export_dir_sets_environment_from_cli_value(self) -> None:
        with patch.dict("os.environ", {"VAE_EXPORT_PATH": ""}, clear=False):
            with patch("sys.argv", ["python", "-m", "app.main", "--export-path", "custom_exports"]):
                resolved = resolve_export_dir()
                env_value = os.environ.get("VAE_EXPORT_PATH")
        self.assertEqual(resolved, (Path(__file__).resolve().parent.parent / "custom_exports"))
        self.assertEqual(env_value, "custom_exports")

    def test_resolve_export_dir_does_not_override_environment_with_cli(self) -> None:
        env_path = "env_exports"
        with patch.dict("os.environ", {"VAE_EXPORT_PATH": env_path}, clear=False):
            with patch("sys.argv", ["python", "-m", "app.main", "--export-path", "cli_exports"]):
                resolve_export_dir()
                env_value = os.environ.get("VAE_EXPORT_PATH")
        self.assertEqual(env_value, env_path)

    def test_resolve_work_dir_uses_cli_flag_value(self) -> None:
        with patch.dict("os.environ", {"VAE_WORK_PATH": ""}, clear=False):
            with patch("sys.argv", ["python", "-m", "app.main", "--work-path", "custom_work"]):
                resolved = resolve_work_dir()
        self.assertEqual(resolved, (Path(__file__).resolve().parent.parent / "custom_work"))

    def test_resolve_work_dir_uses_cli_equals_value(self) -> None:
        with patch.dict("os.environ", {"VAE_WORK_PATH": ""}, clear=False):
            with patch("sys.argv", ["python", "-m", "app.main", "--work-path=custom_work"]):
                resolved = resolve_work_dir()
        self.assertEqual(resolved, (Path(__file__).resolve().parent.parent / "custom_work"))

    def test_resolve_work_dir_prefers_environment_value(self) -> None:
        env_path = "env_work"
        with patch.dict("os.environ", {"VAE_WORK_PATH": env_path}, clear=False):
            with patch("sys.argv", ["python", "-m", "app.main", "--work-path", "cli_work"]):
                resolved = resolve_work_dir()
        self.assertEqual(resolved, (Path(__file__).resolve().parent.parent / env_path))

    def test_resolve_work_dir_sets_environment_from_cli_value(self) -> None:
        with patch.dict("os.environ", {"VAE_WORK_PATH": ""}, clear=False):
            with patch("sys.argv", ["python", "-m", "app.main", "--work-path", "custom_work"]):
                resolved = resolve_work_dir()
                env_value = os.environ.get("VAE_WORK_PATH")
        self.assertEqual(resolved, (Path(__file__).resolve().parent.parent / "custom_work"))
        self.assertEqual(env_value, "custom_work")

    def test_resolve_work_dir_does_not_override_environment_with_cli(self) -> None:
        env_path = "env_work"
        with patch.dict("os.environ", {"VAE_WORK_PATH": env_path}, clear=False):
            with patch("sys.argv", ["python", "-m", "app.main", "--work-path", "cli_work"]):
                resolve_work_dir()
                env_value = os.environ.get("VAE_WORK_PATH")
        self.assertEqual(env_value, env_path)

    def test_resolve_uploads_dir_uses_cli_flag_value(self) -> None:
        with patch.dict("os.environ", {"VAE_UPLOADS_PATH": ""}, clear=False):
            with patch("sys.argv", ["python", "-m", "app.main", "--uploads-path", "custom_uploads"]):
                resolved = resolve_uploads_dir()
        self.assertEqual(resolved, (Path(__file__).resolve().parent.parent / "custom_uploads"))

    def test_resolve_uploads_dir_uses_cli_equals_value(self) -> None:
        with patch.dict("os.environ", {"VAE_UPLOADS_PATH": ""}, clear=False):
            with patch("sys.argv", ["python", "-m", "app.main", "--uploads-path=custom_uploads"]):
                resolved = resolve_uploads_dir()
        self.assertEqual(resolved, (Path(__file__).resolve().parent.parent / "custom_uploads"))

    def test_resolve_uploads_dir_prefers_environment_value(self) -> None:
        env_path = "env_uploads"
        with patch.dict("os.environ", {"VAE_UPLOADS_PATH": env_path}, clear=False):
            with patch("sys.argv", ["python", "-m", "app.main", "--uploads-path", "cli_uploads"]):
                resolved = resolve_uploads_dir()
        self.assertEqual(resolved, (Path(__file__).resolve().parent.parent / env_path))

    def test_resolve_uploads_dir_sets_environment_from_cli_value(self) -> None:
        with patch.dict("os.environ", {"VAE_UPLOADS_PATH": ""}, clear=False):
            with patch("sys.argv", ["python", "-m", "app.main", "--uploads-path", "custom_uploads"]):
                resolved = resolve_uploads_dir()
                env_value = os.environ.get("VAE_UPLOADS_PATH")
        self.assertEqual(resolved, (Path(__file__).resolve().parent.parent / "custom_uploads"))
        self.assertEqual(env_value, "custom_uploads")

    def test_resolve_uploads_dir_does_not_override_environment_with_cli(self) -> None:
        env_path = "env_uploads"
        with patch.dict("os.environ", {"VAE_UPLOADS_PATH": env_path}, clear=False):
            with patch("sys.argv", ["python", "-m", "app.main", "--uploads-path", "cli_uploads"]):
                resolve_uploads_dir()
                env_value = os.environ.get("VAE_UPLOADS_PATH")
        self.assertEqual(env_value, env_path)

    def test_resolve_log_file_uses_project_venv_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            with patch("app.main.ROOT", root):
                resolved = resolve_log_file()
        self.assertEqual(resolved, root / ".venv" / "vae.log")

    def test_configure_logging_writes_app_and_uvicorn_logs_to_project_venv(self) -> None:
        root_logger = logging.getLogger()
        uvicorn_logger = logging.getLogger("uvicorn")
        access_logger = logging.getLogger("uvicorn.access")
        original_root_handlers = list(root_logger.handlers)
        original_root_level = root_logger.level
        original_uvicorn_handlers = list(uvicorn_logger.handlers)
        original_uvicorn_level = uvicorn_logger.level
        original_uvicorn_propagate = uvicorn_logger.propagate
        original_access_handlers = list(access_logger.handlers)
        original_access_level = access_logger.level
        original_access_propagate = access_logger.propagate
        temp_dir = tempfile.TemporaryDirectory()
        new_handlers: list[logging.Handler] = []
        try:
            root_logger.handlers = []
            uvicorn_logger.handlers = []
            access_logger.handlers = []
            uvicorn_logger.propagate = False
            access_logger.propagate = False
            root = Path(temp_dir.name)
            with patch("app.main.ROOT", root), patch.dict("os.environ", {"VAE_LOG_LEVEL": "INFO"}, clear=False):
                log_file = configure_logging()
                new_handlers = list(root_logger.handlers) + list(uvicorn_logger.handlers) + list(access_logger.handlers)
                logging.getLogger("app.main").info("app log entry")
                uvicorn_logger.info("uvicorn log entry")
                access_logger.info("access log entry")
                for handler in new_handlers:
                    handler.flush()
                content = log_file.read_text(encoding="utf-8")
            self.assertEqual(log_file, root / ".venv" / "vae.log")
            self.assertIn("app log entry", content)
            self.assertIn("uvicorn log entry", content)
            self.assertIn("access log entry", content)
        finally:
            for handler in new_handlers:
                try:
                    handler.close()
                except Exception:
                    pass
            root_logger.handlers = original_root_handlers
            root_logger.setLevel(original_root_level)
            uvicorn_logger.handlers = original_uvicorn_handlers
            uvicorn_logger.setLevel(original_uvicorn_level)
            uvicorn_logger.propagate = original_uvicorn_propagate
            access_logger.handlers = original_access_handlers
            access_logger.setLevel(original_access_level)
            access_logger.propagate = original_access_propagate
            temp_dir.cleanup()

    def test_trim_frames_uses_last_available_frame_for_end_timestamp(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=root / "uploads" / f"{project_id}.mp4",
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=832,
                height=480,
                duration=5.0625,
                fps=16.0,
                frame_count=81,
                video_codec="h264",
                audio_codec=None,
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState()
            fake_storage = _FakeStorage(paths, metadata, state)
            fake_ffmpeg = _FakeFFmpeg(metadata=metadata)

            with patch("app.main.storage", fake_storage), patch("app.main.ffmpeg", fake_ffmpeg):
                response = trim_frames(project_id, start=0.0, end=metadata.duration)

            self.assertEqual(response.start_url, f"/work/{project_id}_trim_start.jpg")
            self.assertEqual(response.end_url, f"/work/{project_id}_trim_end.jpg")
            self.assertEqual(fake_ffmpeg.ran_commands[0][2], "0.000000")
            self.assertEqual(fake_ffmpeg.ran_commands[1][2], "5.000000")

    def test_render_preview_scene_split_tolerates_one_frame_over_max_length(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=root / "uploads" / f"{project_id}.mp4",
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=832,
                height=480,
                duration=5.0625,
                fps=16.0,
                frame_count=81,
                video_codec="h264",
                audio_codec=None,
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState(
                trim={"start": 0.0, "end": 5.0625},
                scene_split={"enabled": True, "threshold": 0.4, "min_clip_length": 4.0, "max_clip_length": 5.0},
            )
            fake_storage = _FakeStorage(paths, metadata, state)
            fake_ffmpeg = _FakeFFmpeg(metadata=metadata)
            fake_scene_detection = _FakeSceneDetection([])

            with (
                patch("app.main.storage", fake_storage),
                patch("app.main.ffmpeg", fake_ffmpeg),
                patch("app.main.scene_detection", fake_scene_detection),
            ):
                response = render_preview(project_id)

            self.assertIsNone(response.output_url)
            self.assertEqual(len(response.parts), 1)
            self.assertEqual(response.parts[0].start, 0.0)
            self.assertEqual(response.parts[0].end, 5.0625)
            self.assertEqual(
                fake_ffmpeg.ran_commands,
                [["ffmpeg", "-ss", "0.0", "-to", "5.0625", str(root / "work" / f"{project_id}_preview_part001.mp4")]],
            )

    def test_render_export_includes_saved_output_path(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=root / "uploads" / f"{project_id}.mp4",
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState()
            fake_storage = _FakeStorage(paths, metadata, state)
            fake_ffmpeg = _FakeFFmpeg()

            with patch("app.main.storage", fake_storage), patch("app.main.ffmpeg", fake_ffmpeg):
                response = render_export(project_id)

            self.assertEqual(response.project_id, project_id)
            self.assertEqual(response.format, "mp4")
            self.assertRegex(response.output_url, r"^/exports/vae_\d{17}\.mp4$")
            self.assertRegex(Path(response.output_path).name, r"^vae_\d{17}\.mp4$")
            self.assertEqual(response.parts, [])
            self.assertIsNotNone(fake_storage.saved_paths)
            self.assertTrue(fake_ffmpeg.ran_commands)
            self.assertEqual(
                fake_ffmpeg.ran_commands[-1],
                ["ffmpeg", "-i", str(paths.original_file), str(fake_storage.saved_paths.export_file)],
            )
            self.assertIsNotNone(re.match(r"^vae_\d{17}\.mp4$", fake_storage.saved_paths.export_file.name))

    def test_download_export_uses_vae_prefixed_filename(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            export_file = root / "exports" / "legacy_export.mp4"
            export_file.parent.mkdir(parents=True, exist_ok=True)
            export_file.write_bytes(b"")
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=root / "uploads" / f"{project_id}.mp4",
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=export_file,
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState()
            fake_storage = _FakeStorage(paths, metadata, state)

            with patch("app.main.storage", fake_storage):
                response = download_export(project_id)

            self.assertEqual(response.filename, "vae_20260405164322111.mp4")

    def test_download_export_uses_gif_media_type_for_gif_exports(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            export_file = root / "exports" / "legacy_export.gif"
            export_file.parent.mkdir(parents=True, exist_ok=True)
            export_file.write_bytes(b"gif")
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=root / "uploads" / f"{project_id}.mp4",
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=export_file,
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState()
            fake_storage = _FakeStorage(paths, metadata, state)

            with patch("app.main.storage", fake_storage):
                response = download_export(project_id)

            self.assertEqual(response.media_type, "image/gif")

    def test_create_project_from_path_uses_existing_absolute_file(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source_file = root / "source.mp4"
            source_file.write_bytes(b"video")
            project_id = "abc123"
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=source_file,
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState()
            fake_storage = _FakeStorage(paths, metadata, state)
            fake_ffmpeg = _FakeFFmpeg()

            with patch("app.main.storage", fake_storage), patch("app.main.ffmpeg", fake_ffmpeg):
                response = create_project_from_path(LocalProjectCreateRequest(source_path=str(source_file)))

            self.assertEqual(response.project_id, project_id)
            self.assertEqual(response.original_url, f"/api/projects/{project_id}/original")
            self.assertEqual(fake_storage.created_source_file, source_file.resolve())
            self.assertFalse(response.original_uses_proxy)
            self.assertFalse(response.state.scene_split.enabled)
            self.assertEqual(response.state.rotation.quarter_turns, 0)
            self.assertEqual(response.state.scene_split.threshold, 0.4)
            self.assertEqual(response.state.scene_split.min_clip_length, 2.0)
            self.assertEqual(response.state.scene_split.max_clip_length, 12.0)
            self.assertFalse(response.state.scene_split.fixed_length_enabled)
            self.assertEqual(response.state.scene_split.fixed_clip_length, 12.0)

    def test_create_project_from_path_accepts_unicode_absolute_file(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source_file = root / "日本語の動画.mp4"
            source_file.write_bytes(b"video")
            project_id = "abc123"
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=source_file,
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState()
            fake_storage = _FakeStorage(paths, metadata, state)
            fake_ffmpeg = _FakeFFmpeg()

            with patch("app.main.storage", fake_storage), patch("app.main.ffmpeg", fake_ffmpeg):
                response = create_project_from_path(LocalProjectCreateRequest(source_path=str(source_file)))

            self.assertEqual(response.project_id, project_id)
            self.assertEqual(fake_storage.created_source_file, source_file.resolve())
            self.assertFalse(response.original_uses_proxy)

    def test_get_project_original_uses_current_project_path(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            original_file = root / "source.mp4"
            original_file.write_bytes(b"video")
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=original_file,
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState()
            fake_storage = _FakeStorage(paths, metadata, state)

            with patch("app.main.storage", fake_storage):
                response = get_project_original(project_id)

            self.assertEqual(Path(response.path), original_file)

    def test_create_project_from_path_builds_player_proxy_for_hevc(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source_file = root / "source.mp4"
            source_file.write_bytes(b"video")
            project_id = "abc123"
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=source_file,
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="hevc",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState()
            fake_storage = _FakeStorage(paths, metadata, state)
            fake_ffmpeg = _FakeFFmpeg(metadata=metadata)

            with patch("app.main.storage", fake_storage), patch("app.main.ffmpeg", fake_ffmpeg):
                response = create_project_from_path(LocalProjectCreateRequest(source_path=str(source_file)))

            self.assertTrue(response.original_uses_proxy)
            self.assertEqual(
                fake_ffmpeg.ran_commands[-1],
                ["ffmpeg", "-i", str(source_file), "-c:v", "libx264", str(paths.player_proxy_file)],
            )

    def test_get_project_original_uses_proxy_for_hevc_projects(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            original_file = root / "source.mp4"
            original_file.write_bytes(b"video")
            proxy_file = root / "work" / f"{project_id}_original_player.mp4"
            proxy_file.parent.mkdir(parents=True, exist_ok=True)
            proxy_file.write_bytes(b"proxy")
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=original_file,
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=proxy_file,
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="hevc",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState()
            fake_storage = _FakeStorage(paths, metadata, state)
            fake_ffmpeg = _FakeFFmpeg(metadata=metadata)

            with patch("app.main.storage", fake_storage), patch("app.main.ffmpeg", fake_ffmpeg):
                response = get_project_original(project_id)

            self.assertEqual(Path(response.path), proxy_file)
            self.assertEqual(response.media_type, "video/mp4")

    def test_get_project_backfills_missing_codec_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            original_file = root / "source.mp4"
            original_file.write_bytes(b"video")
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=original_file,
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            stored_metadata = VideoMetadata(width=1920, height=1080, duration=8.0, fps=30.0, frame_count=240)
            refreshed_metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="hevc",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState()
            fake_storage = _FakeStorage(paths, stored_metadata, state)
            fake_ffmpeg = _FakeFFmpeg(metadata=refreshed_metadata)

            with patch("app.main.storage", fake_storage), patch("app.main.ffmpeg", fake_ffmpeg):
                response = get_project(project_id)

            self.assertTrue(response.original_uses_proxy)
            self.assertIsNotNone(fake_storage.saved_metadata)
            self.assertEqual(fake_storage.saved_metadata.video_codec, "hevc")

    def test_update_state_persists_scene_split_configuration(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=root / "uploads" / f"{project_id}.mp4",
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            stored_state = EditState()
            next_state = EditState(
                rotation={"quarter_turns": 3},
                scene_split={
                    "enabled": True,
                    "detector": "ai",
                    "threshold": 0.55,
                    "ai_sensitivity": 0.72,
                    "min_clip_length": 3.0,
                    "max_clip_length": 9.0,
                    "fixed_length_enabled": True,
                    "fixed_clip_length": 6.0,
                    "selected_clip_indexes": [3, 1, 3],
                }
            )
            fake_storage = _FakeStorage(paths, metadata, stored_state)
            fake_ffmpeg = _FakeFFmpeg(metadata=metadata)

            with patch("app.main.storage", fake_storage), patch("app.main.ffmpeg", fake_ffmpeg):
                response = update_state(project_id, StateUpdateRequest(state=next_state))

            self.assertIsNotNone(fake_storage.saved_state)
            self.assertEqual(fake_storage.saved_state.rotation.quarter_turns, 3)
            self.assertTrue(fake_storage.saved_state.scene_split.enabled)
            self.assertEqual(fake_storage.saved_state.scene_split.detector, "ai")
            self.assertEqual(fake_storage.saved_state.scene_split.threshold, 0.55)
            self.assertEqual(fake_storage.saved_state.scene_split.ai_sensitivity, 0.72)
            self.assertEqual(fake_storage.saved_state.scene_split.min_clip_length, 3.0)
            self.assertEqual(fake_storage.saved_state.scene_split.max_clip_length, 9.0)
            self.assertTrue(fake_storage.saved_state.scene_split.fixed_length_enabled)
            self.assertEqual(fake_storage.saved_state.scene_split.fixed_clip_length, 6.0)
            self.assertEqual(fake_storage.saved_state.scene_split.selected_clip_indexes, [1, 3])
            self.assertEqual(response.state.rotation.quarter_turns, 3)
            self.assertTrue(response.state.scene_split.enabled)
            self.assertEqual(response.state.scene_split.detector, "ai")
            self.assertEqual(response.state.scene_split.threshold, 0.55)
            self.assertTrue(response.state.scene_split.fixed_length_enabled)
            self.assertEqual(response.state.scene_split.fixed_clip_length, 6.0)
            self.assertEqual(response.state.scene_split.selected_clip_indexes, [1, 3])

    def test_rotate_ui_exposes_buttons_and_fixed_order_hint(self) -> None:
        html = (Path(__file__).resolve().parent.parent / "static" / "index.html").read_text(encoding="utf-8")
        self.assertIn('data-panel="rotate"', html)
        self.assertIn('id="rotateLeftBtn"', html)
        self.assertIn('id="rotateRightBtn"', html)
        self.assertIn('id="rotateResetBtn"', html)
        self.assertIn("Rotation is applied after crop and before resize.", html)

    def test_rotation_state_is_normalized_in_browser_script(self) -> None:
        script = (Path(__file__).resolve().parent.parent / "static" / "app.js").read_text(encoding="utf-8")
        self.assertIn("const DEFAULT_ROTATION =", script)
        self.assertIn("function normalizeRotationConfig(rotation)", script)
        self.assertIn("quarter_turns", script)
        self.assertIn("showOriginalPreviewForCropEditing", script)
        self.assertIn("Current rotation:", script)

    def test_speed_ui_exposes_slider_presets_and_manual_input(self) -> None:
        html = (Path(__file__).resolve().parent.parent / "static" / "index.html").read_text(encoding="utf-8")
        self.assertIn('data-panel="speed"', html)
        self.assertIn('id="speedSlider"', html)
        self.assertIn('id="speedInput"', html)
        self.assertIn('class="speed-preset"', html)
        self.assertIn('data-speed="5"', html)
        self.assertIn('data-speed="10"', html)
        self.assertIn("Below 1 slows the video down, above 1 speeds it up, 1 keeps the original timing, and 5x/10x can only be chosen from the preset buttons.", html)

    def test_speed_state_is_normalized_in_browser_script(self) -> None:
        script = (Path(__file__).resolve().parent.parent / "static" / "app.js").read_text(encoding="utf-8")
        self.assertIn("const MIN_SPEED = 0.25;", script)
        self.assertIn("const EXTRA_SPEED_BUTTON_VALUES = [5, 10];", script)
        self.assertIn("function normalizeManualSpeedValue(rawValue)", script)
        self.assertIn("function normalizeSpeedValue(rawValue, options = {})", script)
        self.assertIn("function updateSpeedUI()", script)
        self.assertIn("function setSpeedValue(rawValue, options = {})", script)
        self.assertIn('document.querySelectorAll(".speed-preset")', script)
        self.assertIn('el.speedSlider.addEventListener("input"', script)
        self.assertIn('el.speedInput.addEventListener("change"', script)
        self.assertIn("next.speed = normalizeSpeedValue(next.speed, { allowExtended: true });", script)
        self.assertIn('setSpeedValue(button.dataset.speed, { allowExtended: true });', script)

    def test_crop_ui_exposes_phone_sliders_and_live_sync_browser_logic(self) -> None:
        html = (Path(__file__).resolve().parent.parent / "static" / "index.html").read_text(encoding="utf-8")
        script = (Path(__file__).resolve().parent.parent / "static" / "app.js").read_text(encoding="utf-8")
        css = (Path(__file__).resolve().parent.parent / "static" / "styles.css").read_text(encoding="utf-8")

        self.assertIn('id="cropXRange"', html)
        self.assertIn('id="cropYRange"', html)
        self.assertIn('id="cropWRange"', html)
        self.assertIn('id="cropHRange"', html)
        self.assertIn("Manual crop values and the gizmo stay in sync.", html)
        self.assertIn('document.querySelectorAll("[data-crop-field]")', script)
        self.assertIn("function syncCropField(field, rawValue)", script)
        self.assertIn('el.cropOverlay.addEventListener("pointerdown"', script)
        self.assertIn('window.addEventListener("pointermove"', script)
        self.assertIn('.crop-overlay.interactive', css)
        self.assertIn("@media (pointer: coarse)", css)

    def test_render_preview_scene_split_returns_parts_and_numbered_filenames(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=root / "uploads" / f"{project_id}.mp4",
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState(scene_split={"enabled": True, "threshold": 0.3, "min_clip_length": 2.0, "max_clip_length": 6.0})
            fake_storage = _FakeStorage(paths, metadata, state)
            fake_ffmpeg = _FakeFFmpeg()
            fake_scene_detection = _FakeSceneDetection()

            with (
                patch("app.main.storage", fake_storage),
                patch("app.main.ffmpeg", fake_ffmpeg),
                patch("app.main.scene_detection", fake_scene_detection),
            ):
                response = render_preview(project_id)

            self.assertIsNone(response.output_url)
            self.assertIsNone(response.output_path)
            self.assertEqual(len(response.parts), 2)
            self.assertEqual(response.parts[0].output_url, f"/work/{project_id}_preview_part001.mp4")
            self.assertEqual(response.parts[1].output_url, f"/work/{project_id}_preview_part002.mp4")
            self.assertIsNone(response.parts[0].output_path)
            self.assertIsNone(response.parts[1].output_path)
            self.assertEqual(
                fake_ffmpeg.ran_commands,
                [
                    ["ffmpeg", "-ss", "0.0", "-to", "3.0", str(root / "work" / f"{project_id}_preview_part001.mp4")],
                    ["ffmpeg", "-ss", "3.0", "-to", "8.0", str(root / "work" / f"{project_id}_preview_part002.mp4")],
                ],
            )

    def test_render_preview_without_scene_split_returns_single_output_contract(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=root / "uploads" / f"{project_id}.mp4",
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState()
            fake_storage = _FakeStorage(paths, metadata, state)
            fake_ffmpeg = _FakeFFmpeg()

            with patch("app.main.storage", fake_storage), patch("app.main.ffmpeg", fake_ffmpeg):
                response = render_preview(project_id)

            self.assertEqual(response.output_url, f"/work/{project_id}_preview.mp4")
            self.assertIsNone(response.output_path)
            self.assertEqual(response.parts, [])
            self.assertEqual(fake_ffmpeg.ran_commands, [["ffmpeg", "-i", str(paths.original_file), str(paths.preview_file)]])

    def test_render_preview_scene_split_respects_trim_range_when_building_segments(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=root / "uploads" / f"{project_id}.mp4",
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=12.0,
                fps=30.0,
                frame_count=360,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState(
                trim={"start": 2.0, "end": 8.0},
                scene_split={"enabled": True, "threshold": 0.55, "min_clip_length": 2.0, "max_clip_length": 4.5},
            )
            fake_storage = _FakeStorage(paths, metadata, state)
            fake_ffmpeg = _FakeFFmpeg()
            fake_scene_detection = _FakeSceneDetection([1.0, 4.0, 7.0, 9.5])
            fake_ffmpeg.build_scene_split_segments = Mock(return_value=[(0.0, 2.5), (2.5, 6.0)])  # type: ignore[method-assign]

            with (
                patch("app.main.storage", fake_storage),
                patch("app.main.ffmpeg", fake_ffmpeg),
                patch("app.main.scene_detection", fake_scene_detection),
            ):
                response = render_preview(project_id)

            self.assertEqual(len(fake_scene_detection.calls), 1)
            self.assertEqual(fake_scene_detection.calls[0][0], paths.original_file)
            self.assertEqual(fake_scene_detection.calls[0][2].threshold, 0.55)
            fake_ffmpeg.build_scene_split_segments.assert_called_once_with(
                duration=6.0,
                candidate_cuts=[2.0, 5.0],
                min_len=2.0 - (1.0 / 30.0),
                max_len=4.5 + (1.0 / 30.0),
            )
            self.assertEqual(
                fake_ffmpeg.ran_commands,
                [
                    ["ffmpeg", "-ss", "2.0", "-to", "4.5", str(root / "work" / f"{project_id}_preview_part001.mp4")],
                    ["ffmpeg", "-ss", "4.5", "-to", "8.0", str(root / "work" / f"{project_id}_preview_part002.mp4")],
                ],
            )
            self.assertIsNone(response.output_url)
            self.assertEqual(len(response.parts), 2)

    def test_render_preview_fixed_length_scene_split_skips_scene_detection_and_offsets_trim(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=root / "uploads" / f"{project_id}.mp4",
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=12.0,
                fps=30.0,
                frame_count=360,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState(
                trim={"start": 2.0, "end": 11.0},
                scene_split={"enabled": True, "detector": "ffmpeg", "fixed_length_enabled": True, "fixed_clip_length": 4.0},
            )
            fake_storage = _FakeStorage(paths, metadata, state)
            fake_ffmpeg = _FakeFFmpeg()
            fake_scene_detection = _FakeSceneDetection()
            fake_ffmpeg.build_fixed_length_segments = Mock(return_value=[(0.0, 4.0), (4.0, 9.0)])  # type: ignore[method-assign]

            with (
                patch("app.main.storage", fake_storage),
                patch("app.main.ffmpeg", fake_ffmpeg),
                patch("app.main.scene_detection", fake_scene_detection),
            ):
                response = render_preview(project_id)

            fake_ffmpeg.build_fixed_length_segments.assert_called_once_with(duration=9.0, clip_length=4.0)
            self.assertEqual(fake_scene_detection.calls, [])
            self.assertEqual(
                fake_ffmpeg.ran_commands,
                [
                    ["ffmpeg", "-ss", "2.0", "-to", "6.0", str(root / "work" / f"{project_id}_preview_part001.mp4")],
                    ["ffmpeg", "-ss", "6.0", "-to", "11.0", str(root / "work" / f"{project_id}_preview_part002.mp4")],
                ],
            )
            self.assertEqual(len(response.parts), 2)

    def test_render_preview_scene_split_removes_stale_single_preview_and_old_parts(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            preview_file = root / "work" / f"{project_id}_preview.mp4"
            stale_part = root / "work" / f"{project_id}_preview_part001.mp4"
            preview_file.parent.mkdir(parents=True, exist_ok=True)
            preview_file.write_bytes(b"stale-single-preview")
            stale_part.write_bytes(b"stale-part-preview")
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=root / "uploads" / f"{project_id}.mp4",
                preview_file=preview_file,
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState(scene_split={"enabled": True, "threshold": 0.3, "min_clip_length": 2.0, "max_clip_length": 6.0})
            fake_storage = _FakeStorage(paths, metadata, state)
            fake_ffmpeg = _FakeFFmpeg()
            fake_scene_detection = _FakeSceneDetection()

            with (
                patch("app.main.storage", fake_storage),
                patch("app.main.ffmpeg", fake_ffmpeg),
                patch("app.main.scene_detection", fake_scene_detection),
            ):
                response = render_preview(project_id)

            self.assertEqual(len(response.parts), 2)
            self.assertFalse(preview_file.exists())
            self.assertFalse(stale_part.exists())

    def test_render_preview_without_scene_split_removes_stale_split_parts(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            preview_file = root / "work" / f"{project_id}_preview.mp4"
            stale_part_one = root / "work" / f"{project_id}_preview_part001.mp4"
            stale_part_two = root / "work" / f"{project_id}_preview_part002.mp4"
            preview_file.parent.mkdir(parents=True, exist_ok=True)
            stale_part_one.write_bytes(b"stale-part-one")
            stale_part_two.write_bytes(b"stale-part-two")
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=root / "uploads" / f"{project_id}.mp4",
                preview_file=preview_file,
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState()
            fake_storage = _FakeStorage(paths, metadata, state)
            fake_ffmpeg = _FakeFFmpeg()

            with patch("app.main.storage", fake_storage), patch("app.main.ffmpeg", fake_ffmpeg):
                response = render_preview(project_id)

            self.assertEqual(response.output_url, f"/work/{project_id}_preview.mp4")
            self.assertFalse(stale_part_one.exists())
            self.assertFalse(stale_part_two.exists())

    def test_scene_split_ui_instructs_apply_changes_without_dedicated_button(self) -> None:
        html = (Path(__file__).resolve().parent.parent / "static" / "index.html").read_text(encoding="utf-8")
        self.assertIn("Apply Changes", html)
        self.assertNotIn("Apply Scene Split", html)

    def test_index_renders_versioned_static_assets(self) -> None:
        response = index()
        html = response.body.decode("utf-8")
        self.assertRegex(html, r'/static/styles\.css\?v=\d+')
        self.assertRegex(html, r'/static/app\.js\?v=\d+')
        self.assertEqual(response.headers["cache-control"], "no-cache")

    def test_scene_split_ui_exposes_threshold_slider_and_browser_memory_hint(self) -> None:
        html = (Path(__file__).resolve().parent.parent / "static" / "index.html").read_text(encoding="utf-8")
        self.assertIn('id="uploadProgress"', html)
        self.assertIn('id="uploadStatus"', html)
        self.assertIn('id="sceneSplitDetector"', html)
        self.assertIn('id="sceneSplitThresholdRange"', html)
        self.assertIn('id="sceneSplitAiSensitivityRange"', html)
        self.assertIn('id="sceneSplitFixedLengthEnabled"', html)
        self.assertIn('id="sceneSplitFixedClip"', html)
        self.assertIn('id="sceneSplitFixedClipRange"', html)
        self.assertIn('id="sceneSplitResetBtn"', html)
        self.assertIn('id="previewSelectAllBtn"', html)
        self.assertIn('id="previewClearSelectionBtn"', html)
        self.assertIn('id="exportGifBtn"', html)
        self.assertIn("Lower threshold finds more cuts. Higher threshold finds fewer. Higher AI sensitivity finds more cuts.", html)
        self.assertIn("This browser remembers your latest scene split values for new projects.", html)
        self.assertIn("AI mode uses a local TransNetV2 ONNX model", html)
        self.assertIn("the last short remainder is merged into the previous clip", html)
        self.assertIn("If none are selected, Export keeps the current behavior and writes all split clips.", html)

    def test_scene_split_preferences_are_persisted_in_browser_storage(self) -> None:
        script = (Path(__file__).resolve().parent.parent / "static" / "app.js").read_text(encoding="utf-8")
        self.assertIn('const SCENE_SPLIT_STORAGE_KEY = "vae-scene-split-preferences"', script)
        self.assertIn("function uploadProjectFile(file)", script)
        self.assertIn('xhr.upload.addEventListener("progress"', script)
        self.assertIn("setUploadProgress(100)", script)
        self.assertIn("window.localStorage.getItem(SCENE_SPLIT_STORAGE_KEY)", script)
        self.assertIn("window.localStorage.setItem(SCENE_SPLIT_STORAGE_KEY", script)
        self.assertIn("setProjectFromPayload(payload, { useRememberedSceneSplit: true })", script)
        self.assertIn('detector: "ffmpeg"', script)
        self.assertIn("sceneSplitAiSensitivityRange", script)
        self.assertIn("fixed_length_enabled", script)
        self.assertIn("fixed_clip_length", script)
        self.assertIn("sceneSplitFixedClipRange", script)
        self.assertIn("usesFixedLengthSceneSplit", script)
        self.assertIn("selected_clip_indexes", script)
        self.assertIn("togglePreviewPartSelection", script)
        self.assertIn("function buildGifExportConfirmationMessage(estimate)", script)
        self.assertIn('fetch(`/api/projects/${state.projectId}/export/gif-estimate`', script)
        self.assertIn('fetch(`/api/projects/${state.projectId}/export/gif`', script)
        self.assertIn("window.confirm(buildGifExportConfirmationMessage(estimate))", script)

    def test_unicode_local_paths_fall_back_to_uploaded_copy_in_ui(self) -> None:
        script = (Path(__file__).resolve().parent.parent / "static" / "app.js").read_text(encoding="utf-8")
        self.assertIn("function containsNonAscii(value)", script)
        self.assertIn("unicode path will use uploaded copy", script)
        self.assertIn("uploaded copy (unicode path compatibility mode)", script)

    def test_preview_clip_selection_forces_video_reload(self) -> None:
        script = (Path(__file__).resolve().parent.parent / "static" / "app.js").read_text(encoding="utf-8")
        self.assertIn("function setVideoSource(videoElement, url, options = {})", script)
        self.assertIn("setVideoSource(el.previewVideo, part.output_url, { cacheBust: true });", script)
        self.assertIn("setVideoSource(el.previewVideo, payload.output_url, { cacheBust: true });", script)

    def test_preview_clip_ui_hides_when_scene_split_is_off(self) -> None:
        script = (Path(__file__).resolve().parent.parent / "static" / "app.js").read_text(encoding="utf-8")
        self.assertIn('el.previewPartsWrap.classList.toggle("hidden", !splitEnabled);', script)
        self.assertIn('el.previewPartsListWrap.classList.toggle("hidden", !splitEnabled);', script)
        self.assertIn('el.previewPartInfo.textContent = "";', script)

    def test_render_export_scene_split_returns_parts_and_numbered_paths(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=root / "uploads" / f"{project_id}.mp4",
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState(scene_split={"enabled": True, "threshold": 0.3, "min_clip_length": 2.0, "max_clip_length": 6.0})
            fake_storage = _FakeStorage(paths, metadata, state)
            fake_ffmpeg = _FakeFFmpeg()
            fake_scene_detection = _FakeSceneDetection()

            with (
                patch("app.main.storage", fake_storage),
                patch("app.main.ffmpeg", fake_ffmpeg),
                patch("app.main.scene_detection", fake_scene_detection),
            ):
                response = render_export(project_id)

            self.assertEqual(response.format, "mp4")
            self.assertIsNone(response.output_url)
            self.assertIsNone(response.output_path)
            self.assertEqual(len(response.parts), 2)
            first_part = Path(response.parts[0].output_path)
            second_part = Path(response.parts[1].output_path)
            self.assertRegex(first_part.name, r"^vae_\d{17}_part001\.mp4$")
            self.assertRegex(second_part.name, r"^vae_\d{17}_part002\.mp4$")
            self.assertEqual(response.parts[0].output_url, f"/exports/{first_part.name}")
            self.assertEqual(response.parts[1].output_url, f"/exports/{second_part.name}")

    def test_render_export_scene_split_only_renders_selected_clip_indexes(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=root / "uploads" / f"{project_id}.mp4",
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState(
                scene_split={
                    "enabled": True,
                    "threshold": 0.3,
                    "min_clip_length": 2.0,
                    "max_clip_length": 6.0,
                    "selected_clip_indexes": [2],
                }
            )
            fake_storage = _FakeStorage(paths, metadata, state)
            fake_ffmpeg = _FakeFFmpeg()
            fake_scene_detection = _FakeSceneDetection()

            with (
                patch("app.main.storage", fake_storage),
                patch("app.main.ffmpeg", fake_ffmpeg),
                patch("app.main.scene_detection", fake_scene_detection),
            ):
                response = render_export(project_id)

            self.assertEqual(len(response.parts), 1)
            self.assertEqual(response.parts[0].index, 2)
            exported_part = Path(response.parts[0].output_path)
            self.assertRegex(exported_part.name, r"^vae_\d{17}_part002\.mp4$")
            self.assertEqual(
                fake_ffmpeg.ran_commands,
                [["ffmpeg", "-ss", "3.0", "-to", "8.0", str(exported_part)]],
            )

    def test_estimate_export_gif_reports_total_for_single_output(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=root / "uploads" / f"{project_id}.mp4",
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            fake_storage = _FakeStorage(paths, metadata, EditState())
            fake_ffmpeg = _FakeFFmpeg()

            with patch("app.main.storage", fake_storage), patch("app.main.ffmpeg", fake_ffmpeg):
                response = estimate_export_gif(project_id)

            self.assertEqual(response.format, "gif")
            self.assertEqual(response.estimated_size_bytes, 8_000_000)
            self.assertEqual(response.parts, [])

    def test_estimate_export_gif_scene_split_respects_selected_clip_indexes(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=root / "uploads" / f"{project_id}.mp4",
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState(
                scene_split={
                    "enabled": True,
                    "threshold": 0.3,
                    "min_clip_length": 2.0,
                    "max_clip_length": 6.0,
                    "selected_clip_indexes": [2],
                }
            )
            fake_storage = _FakeStorage(paths, metadata, state)
            fake_ffmpeg = _FakeFFmpeg()
            fake_scene_detection = _FakeSceneDetection()

            with (
                patch("app.main.storage", fake_storage),
                patch("app.main.ffmpeg", fake_ffmpeg),
                patch("app.main.scene_detection", fake_scene_detection),
            ):
                response = estimate_export_gif(project_id)

            self.assertEqual(response.estimated_size_bytes, 5_000_000)
            self.assertEqual(len(response.parts), 1)
            self.assertEqual(response.parts[0].index, 2)

    def test_render_export_gif_returns_gif_output(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=root / "uploads" / f"{project_id}.mp4",
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState()
            fake_storage = _FakeStorage(paths, metadata, state)
            fake_ffmpeg = _FakeFFmpeg()

            with patch("app.main.storage", fake_storage), patch("app.main.ffmpeg", fake_ffmpeg):
                response = render_export_gif(project_id)

            self.assertEqual(response.format, "gif")
            self.assertRegex(response.output_url, r"^/exports/vae_\d{17}\.gif$")
            self.assertRegex(Path(response.output_path).name, r"^vae_\d{17}\.gif$")
            self.assertEqual(
                fake_ffmpeg.ran_commands[-1],
                ["ffmpeg", "-i", str(paths.original_file), "-gif", str(fake_storage.saved_paths.export_file)],
            )

    def test_render_export_gif_scene_split_returns_gif_parts(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_id = "abc123"
            paths = ProjectPaths(
                project_id=project_id,
                project_file=root / "projects" / f"{project_id}.json",
                original_file=root / "uploads" / f"{project_id}.mp4",
                preview_file=root / "work" / f"{project_id}_preview.mp4",
                export_file=root / "exports" / "legacy_export.mp4",
                player_proxy_file=root / "work" / f"{project_id}_original_player.mp4",
            )
            metadata = VideoMetadata(
                width=1920,
                height=1080,
                duration=8.0,
                fps=30.0,
                frame_count=240,
                video_codec="h264",
                audio_codec="aac",
                container_format="mov,mp4,m4a,3gp,3g2,mj2",
            )
            state = EditState(scene_split={"enabled": True, "threshold": 0.3, "min_clip_length": 2.0, "max_clip_length": 6.0})
            fake_storage = _FakeStorage(paths, metadata, state)
            fake_ffmpeg = _FakeFFmpeg()
            fake_scene_detection = _FakeSceneDetection()

            with (
                patch("app.main.storage", fake_storage),
                patch("app.main.ffmpeg", fake_ffmpeg),
                patch("app.main.scene_detection", fake_scene_detection),
            ):
                response = render_export_gif(project_id)

            self.assertEqual(response.format, "gif")
            self.assertEqual(len(response.parts), 2)
            self.assertRegex(Path(response.parts[0].output_path).name, r"^vae_\d{17}_part001\.gif$")
            self.assertRegex(Path(response.parts[1].output_path).name, r"^vae_\d{17}_part002\.gif$")
            self.assertEqual(
                fake_ffmpeg.ran_commands,
                [
                    ["ffmpeg", "-gif-ss", "0.0", "-gif-to", "3.0", str(Path(response.parts[0].output_path))],
                    ["ffmpeg", "-gif-ss", "3.0", "-gif-to", "8.0", str(Path(response.parts[1].output_path))],
                ],
            )


if __name__ == "__main__":
    unittest.main()

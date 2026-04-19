import os
import re
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from app.main import (
    create_project_from_path,
    download_export,
    get_project,
    get_project_original,
    render_preview,
    render_export,
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

    def build_export_command(
        self,
        original_file: Path,
        export_file: Path,
        metadata: VideoMetadata,
        state: EditState,
    ) -> list[str]:
        return ["ffmpeg", "-i", str(original_file), str(export_file)]

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
        return [(0.0, 3.0), (3.0, 8.0)]

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
            self.assertEqual(response.state.scene_split.threshold, 0.4)
            self.assertEqual(response.state.scene_split.min_clip_length, 2.0)
            self.assertEqual(response.state.scene_split.max_clip_length, 12.0)

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
                scene_split={
                    "enabled": True,
                    "detector": "ai",
                    "threshold": 0.55,
                    "ai_sensitivity": 0.72,
                    "min_clip_length": 3.0,
                    "max_clip_length": 9.0,
                }
            )
            fake_storage = _FakeStorage(paths, metadata, stored_state)
            fake_ffmpeg = _FakeFFmpeg(metadata=metadata)

            with patch("app.main.storage", fake_storage), patch("app.main.ffmpeg", fake_ffmpeg):
                response = update_state(project_id, StateUpdateRequest(state=next_state))

            self.assertIsNotNone(fake_storage.saved_state)
            self.assertTrue(fake_storage.saved_state.scene_split.enabled)
            self.assertEqual(fake_storage.saved_state.scene_split.detector, "ai")
            self.assertEqual(fake_storage.saved_state.scene_split.threshold, 0.55)
            self.assertEqual(fake_storage.saved_state.scene_split.ai_sensitivity, 0.72)
            self.assertEqual(fake_storage.saved_state.scene_split.min_clip_length, 3.0)
            self.assertEqual(fake_storage.saved_state.scene_split.max_clip_length, 9.0)
            self.assertTrue(response.state.scene_split.enabled)
            self.assertEqual(response.state.scene_split.detector, "ai")
            self.assertEqual(response.state.scene_split.threshold, 0.55)

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
                min_len=2.0,
                max_len=4.5,
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

    def test_scene_split_ui_exposes_threshold_slider_and_browser_memory_hint(self) -> None:
        html = (Path(__file__).resolve().parent.parent / "static" / "index.html").read_text(encoding="utf-8")
        self.assertIn('id="sceneSplitDetector"', html)
        self.assertIn('id="sceneSplitThresholdRange"', html)
        self.assertIn('id="sceneSplitAiSensitivityRange"', html)
        self.assertIn('id="sceneSplitResetBtn"', html)
        self.assertIn("Lower threshold finds more cuts. Higher threshold finds fewer. Higher AI sensitivity finds more cuts.", html)
        self.assertIn("This browser remembers your latest scene split values for new projects.", html)
        self.assertIn("AI mode uses a local TransNetV2 ONNX model", html)

    def test_scene_split_preferences_are_persisted_in_browser_storage(self) -> None:
        script = (Path(__file__).resolve().parent.parent / "static" / "app.js").read_text(encoding="utf-8")
        self.assertIn('const SCENE_SPLIT_STORAGE_KEY = "vae-scene-split-preferences"', script)
        self.assertIn("window.localStorage.getItem(SCENE_SPLIT_STORAGE_KEY)", script)
        self.assertIn("window.localStorage.setItem(SCENE_SPLIT_STORAGE_KEY", script)
        self.assertIn("setProjectFromPayload(payload, { useRememberedSceneSplit: true })", script)
        self.assertIn('detector: "ffmpeg"', script)
        self.assertIn("sceneSplitAiSensitivityRange", script)

    def test_preview_clip_selection_forces_video_reload(self) -> None:
        script = (Path(__file__).resolve().parent.parent / "static" / "app.js").read_text(encoding="utf-8")
        self.assertIn("function setVideoSource(videoElement, url, options = {})", script)
        self.assertIn("setVideoSource(el.previewVideo, part.output_url, { cacheBust: true });", script)
        self.assertIn("setVideoSource(el.previewVideo, payload.output_url, { cacheBust: true });", script)

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

            self.assertIsNone(response.output_url)
            self.assertIsNone(response.output_path)
            self.assertEqual(len(response.parts), 2)
            first_part = Path(response.parts[0].output_path)
            second_part = Path(response.parts[1].output_path)
            self.assertRegex(first_part.name, r"^vae_\d{17}_part001\.mp4$")
            self.assertRegex(second_part.name, r"^vae_\d{17}_part002\.mp4$")
            self.assertEqual(response.parts[0].output_url, f"/exports/{first_part.name}")
            self.assertEqual(response.parts[1].output_url, f"/exports/{second_part.name}")


if __name__ == "__main__":
    unittest.main()

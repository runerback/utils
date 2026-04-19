import os
import re
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from app.main import (
    create_project_from_path,
    download_export,
    get_project,
    get_project_original,
    render_export,
    resolve_export_dir,
    resolve_uploads_dir,
    resolve_work_dir,
)
from app.schemas import EditState, LocalProjectCreateRequest, ProjectPaths, VideoMetadata


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
        self.ran_command: list[str] | None = None
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

    def build_player_proxy_command(self, original_file: Path, proxy_file: Path) -> list[str]:
        return ["ffmpeg", "-i", str(original_file), "-c:v", "libx264", str(proxy_file)]

    def requires_player_proxy(self, metadata: VideoMetadata) -> bool:
        return metadata.video_codec == "hevc"

    def run(self, command: list[str]) -> None:
        self.ran_command = command


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
            self.assertIsNotNone(fake_storage.saved_paths)
            self.assertIsNotNone(fake_ffmpeg.ran_command)
            self.assertEqual(
                fake_ffmpeg.ran_command,
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
                fake_ffmpeg.ran_command,
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


if __name__ == "__main__":
    unittest.main()

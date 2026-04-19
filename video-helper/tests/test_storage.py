import json
import tempfile
import unittest
from datetime import datetime
from pathlib import Path
from unittest.mock import patch

from app.storage import Storage
from app.schemas import EditState, VideoMetadata


class StorageTests(unittest.TestCase):
    def test_default_uploads_directory_is_under_root(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            storage = Storage(root)
            self.assertEqual(storage.uploads, root / "uploads")
            self.assertTrue(storage.uploads.exists())

    def test_relative_uploads_directory_is_resolved_from_root(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            storage = Storage(root, uploads_dir=Path("custom_uploads"))
            self.assertEqual(storage.uploads, root / "custom_uploads")
            self.assertTrue(storage.uploads.exists())

    def test_absolute_uploads_directory_is_used_directly(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            absolute_uploads = root / "absolute_uploads"
            storage = Storage(root, uploads_dir=absolute_uploads)
            self.assertEqual(storage.uploads, absolute_uploads)
            self.assertTrue(storage.uploads.exists())

    def test_default_work_directory_is_under_root(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            storage = Storage(root)
            self.assertEqual(storage.work, root / "work")
            self.assertTrue(storage.work.exists())

    def test_relative_work_directory_is_resolved_from_root(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            storage = Storage(root, work_dir=Path("custom_work"))
            self.assertEqual(storage.work, root / "custom_work")
            self.assertTrue(storage.work.exists())

    def test_absolute_work_directory_is_used_directly(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            absolute_work = root / "absolute_work"
            storage = Storage(root, work_dir=absolute_work)
            self.assertEqual(storage.work, absolute_work)
            self.assertTrue(storage.work.exists())

    def test_default_export_directory_is_under_root(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            storage = Storage(root)
            self.assertEqual(storage.exports, root / "exports")
            self.assertTrue(storage.exports.exists())

    def test_relative_export_directory_is_resolved_from_root(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            storage = Storage(root, export_dir=Path("custom_exports"))
            self.assertEqual(storage.exports, root / "custom_exports")
            self.assertTrue(storage.exports.exists())

    def test_absolute_export_directory_is_used_directly(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            absolute_export = root / "absolute_exports"
            storage = Storage(root, export_dir=absolute_export)
            self.assertEqual(storage.exports, absolute_export)
            self.assertTrue(storage.exports.exists())

    def test_create_project_paths_uses_vae_prefixed_export_name(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            storage = Storage(root)
            paths = storage.create_project_paths("input.mp4")
            self.assertRegex(paths.export_file.name, r"^vae_\d{17}\.mp4$")
            self.assertRegex(paths.original_file.name, rf"^{paths.project_id}_input\.mp4$")
            self.assertEqual(paths.player_proxy_file, root / "work" / f"{paths.project_id}_original_player.mp4")

    def test_create_project_paths_keeps_local_filename_context(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            storage = Storage(root)
            paths = storage.create_project_paths("holiday clip (final).mov")
            self.assertRegex(paths.original_file.name, rf"^{paths.project_id}_holiday_clip__final\.mov$")

    def test_build_export_file_path_adds_suffix_when_collision_exists(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            storage = Storage(root)
            fixed_now = datetime(2026, 4, 5, 16, 43, 22, 111000)
            with patch("app.storage.datetime") as mock_datetime:
                mock_datetime.now.return_value = fixed_now
                base_name = storage.build_export_filename(".mp4")
                existing = storage.exports / base_name
                existing.write_bytes(b"")
                next_export = storage.build_export_file_path(".mp4")
            self.assertEqual(base_name, "vae_20260405164322111.mp4")
            self.assertEqual(next_export.name, "vae_20260405164322111_1.mp4")

    def test_create_project_paths_from_source_keeps_source_path(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source_file = (root / "movies" / "input.mp4").resolve()
            source_file.parent.mkdir(parents=True, exist_ok=True)
            source_file.write_bytes(b"video")
            storage = Storage(root)
            paths = storage.create_project_paths_from_source(source_file)
            self.assertEqual(paths.original_file, source_file)
            self.assertEqual(paths.player_proxy_file, root / "work" / f"{paths.project_id}_original_player.mp4")

    def test_project_paths_uses_preview_fallback_when_original_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            storage = Storage(root)
            project_id = "abc123"
            preview_file = root / "work" / f"{project_id}_preview.mp4"
            preview_file.parent.mkdir(parents=True, exist_ok=True)
            preview_file.write_bytes(b"fallback")
            payload = {
                "project_id": project_id,
                "metadata": VideoMetadata(width=1920, height=1080, duration=8.0, fps=30.0, frame_count=240).model_dump(),
                "state": EditState().model_dump(),
                "original_file": str(root / "missing" / "source.mp4"),
                "preview_file": str(preview_file),
                "export_file": str(root / "exports" / "export.mp4"),
            }
            project_file = root / "projects" / f"{project_id}.json"
            project_file.parent.mkdir(parents=True, exist_ok=True)
            project_file.write_text(json.dumps(payload), encoding="utf-8")
            paths = storage.project_paths(project_id)
            self.assertEqual(paths.original_file, preview_file)
            self.assertEqual(paths.player_proxy_file, root / "work" / f"{project_id}_original_player.mp4")

    def test_save_project_persists_player_proxy_path(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            storage = Storage(root)
            paths = storage.create_project_paths("input.mp4")
            metadata = VideoMetadata(width=1920, height=1080, duration=8.0, fps=30.0, frame_count=240)
            state = EditState()

            storage.save_project(paths, metadata, state)
            payload = json.loads(paths.project_file.read_text(encoding="utf-8"))

            self.assertEqual(payload["player_proxy_file"], str(paths.player_proxy_file))

    def test_project_paths_raises_when_original_and_fallback_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            storage = Storage(root)
            project_id = "abc123"
            payload = {
                "project_id": project_id,
                "metadata": VideoMetadata(width=1920, height=1080, duration=8.0, fps=30.0, frame_count=240).model_dump(),
                "state": EditState().model_dump(),
                "original_file": str(root / "missing" / "source.mp4"),
                "preview_file": str(root / "work" / f"{project_id}_preview.mp4"),
                "export_file": str(root / "exports" / "export.mp4"),
            }
            project_file = root / "projects" / f"{project_id}.json"
            project_file.parent.mkdir(parents=True, exist_ok=True)
            project_file.write_text(json.dumps(payload), encoding="utf-8")
            with self.assertRaises(FileNotFoundError):
                storage.project_paths(project_id)

    def test_list_projects_uses_project_original_endpoint_url(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            storage = Storage(root)
            project_id = "abc123"
            payload = {
                "project_id": project_id,
                "metadata": VideoMetadata(width=1920, height=1080, duration=8.0, fps=30.0, frame_count=240).model_dump(),
                "state": EditState().model_dump(),
                "original_file": str(root / "uploads" / f"{project_id}.mp4"),
                "preview_file": str(root / "work" / f"{project_id}_preview.mp4"),
                "export_file": str(root / "exports" / "export.mp4"),
            }
            project_file = root / "projects" / f"{project_id}.json"
            project_file.parent.mkdir(parents=True, exist_ok=True)
            project_file.write_text(json.dumps(payload), encoding="utf-8")
            projects = storage.list_projects()
            self.assertEqual(projects[0]["original_url"], f"/api/projects/{project_id}/original")


if __name__ == "__main__":
    unittest.main()

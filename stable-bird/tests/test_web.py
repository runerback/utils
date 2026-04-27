from __future__ import annotations

import io
import json
import tempfile
import threading
import time
import unittest
from pathlib import Path

from src.progress import ProcessingEvent
from src.types import ProcessingSummary, Segment, SegmentFrame
from src.web import create_app


def make_summary(source_path: Path, output_dir: Path) -> ProcessingSummary:
    project_id = source_path.stem
    output_root = output_dir / project_id
    clips_dir = output_root / "clips"
    clips_dir.mkdir(parents=True, exist_ok=True)
    clip_path = clips_dir / "segment_001.mp4"
    clip_path.write_bytes(b"clip")
    debug_path = output_root / f"{project_id}_debug.mp4"
    debug_path.write_bytes(b"debug")
    trace_path = output_root / "trace.log"
    trace_path.write_text("trace line", encoding="utf-8")
    manifest_path = output_root / "manifest.json"
    manifest_path.write_text(
        json.dumps(
            {
                "source_video": str(source_path),
                "output_root": str(output_root),
                "video_info": {
                    "width": 1920,
                    "height": 1080,
                    "fps": 60.0,
                    "frame_count": 120,
                    "duration_seconds": 2.0,
                },
                "accepted_segments": [
                    {
                        "segment_id": 1,
                        "accepted": True,
                        "start_frame": 0,
                        "end_frame": 59,
                        "start_time": 0.0,
                        "end_time": 0.983,
                        "frame_count": 60,
                        "split_reason": "end_of_video",
                        "crop_window": {"width": 1280, "height": 720},
                        "output_path": str(clip_path),
                    }
                ],
                "rejected_segments": [],
                "split_events": [],
                "debug_preview_path": str(debug_path),
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    segment = Segment(
        segment_id=1,
        source_video=str(source_path),
        frames=[
            SegmentFrame(
                frame_index=0,
                timestamp_seconds=0.0,
                render_center_x=960.0,
                render_center_y=540.0,
                detection=None,
                is_good=True,
                reason="ok",
            )
        ],
        split_reason="end_of_video",
        accepted=True,
        output_path=str(clip_path),
    )
    return ProcessingSummary(
        source_video=str(source_path),
        output_root=str(output_root),
        accepted_segments=[segment],
        rejected_segments=[],
        manifest_path=str(manifest_path),
        debug_preview_path=str(debug_path),
    )


class WebUiTests(unittest.TestCase):
    def create_client(self, processor):
        temp_dir = tempfile.TemporaryDirectory()
        root = Path(temp_dir.name)
        app = create_app(output_dir=root / "output", uploads_dir=root / "uploads", processor=processor)
        return temp_dir, app.test_client(), root

    def wait_for_status(self, client, project_id: str, expected_status: str) -> dict[str, object]:
        deadline = time.time() + 5
        while time.time() < deadline:
            payload = client.get(f"/api/projects/{project_id}").get_json()
            project = payload["project"]
            if project["status"] == expected_status:
                return project
            time.sleep(0.05)
        raise AssertionError(f"Project {project_id} did not reach status {expected_status}")

    def test_rejects_unsupported_uploads(self) -> None:
        temp_dir, client, _ = self.create_client(lambda config, reporter: [])
        self.addCleanup(temp_dir.cleanup)

        response = client.post(
            "/api/projects",
            data={"source": (io.BytesIO(b"bad"), "notes.txt")},
            content_type="multipart/form-data",
        )

        self.assertEqual(response.status_code, 400)
        self.assertIn("Unsupported source file type", response.get_json()["error"])

    def test_upload_start_and_completed_project_artifacts(self) -> None:
        def processor(config, reporter):
            source_path = Path(config.input_path)
            output_dir = Path(config.output_dir)
            output_root = output_dir / source_path.stem
            output_root.mkdir(parents=True, exist_ok=True)
            trace_path = output_root / "trace.log"
            trace_path.write_text("trace line", encoding="utf-8")
            reporter.emit(
                ProcessingEvent(
                    kind="video_started",
                    source_video=str(source_path),
                    message="Scanning uploaded source",
                    progress_percent=0.0,
                    details={"trace_path": str(trace_path), "output_root": str(output_root)},
                )
            )
            reporter.emit(
                ProcessingEvent(
                    kind="scan_progress",
                    source_video=str(source_path),
                    message="Scanning frame 10",
                    progress_percent=40.0,
                    details={"frame_index": 10, "frame_count": 20, "reason": "tracking"},
                )
            )
            return [make_summary(source_path, output_dir)]

        temp_dir, client, _ = self.create_client(processor)
        self.addCleanup(temp_dir.cleanup)

        upload_response = client.post(
            "/api/projects",
            data={"source": (io.BytesIO(b"video"), "bird.mp4")},
            content_type="multipart/form-data",
        )
        self.assertEqual(upload_response.status_code, 201)
        project_id = upload_response.get_json()["project"]["project_id"]

        start_response = client.post(f"/api/projects/{project_id}/start")
        self.assertEqual(start_response.status_code, 200)

        project = self.wait_for_status(client, project_id, "completed")
        self.assertEqual(project["accepted_segment_count"], 1)
        self.assertIsNotNone(project["source_url"])
        self.assertIsNotNone(project["debug_preview_url"])
        self.assertEqual(len(project["clips"]), 1)
        self.assertEqual(project["manifest"]["accepted_segments"][0]["segment_id"], 1)

        trace_response = client.get(project["trace_url"])
        self.assertEqual(trace_response.status_code, 200)
        self.assertIn("trace line", trace_response.get_data(as_text=True))

    def test_surfaces_processing_failures(self) -> None:
        def processor(config, reporter):
            source_path = Path(config.input_path)
            reporter.emit(
                ProcessingEvent(
                    kind="video_started",
                    source_video=str(source_path),
                    message="Starting failing processor",
                    progress_percent=0.0,
                    details={},
                )
            )
            raise RuntimeError("simulated failure")

        temp_dir, client, _ = self.create_client(processor)
        self.addCleanup(temp_dir.cleanup)

        upload_response = client.post(
            "/api/projects",
            data={"source": (io.BytesIO(b"video"), "broken.mp4")},
            content_type="multipart/form-data",
        )
        project_id = upload_response.get_json()["project"]["project_id"]

        start_response = client.post(f"/api/projects/{project_id}/start")
        self.assertEqual(start_response.status_code, 200)

        project = self.wait_for_status(client, project_id, "failed")
        self.assertIn("simulated failure", project["error"])

    def test_lists_previously_processed_projects(self) -> None:
        temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(temp_dir.cleanup)
        root = Path(temp_dir.name)
        output_root = root / "output" / "legacy-project"
        output_root.mkdir(parents=True, exist_ok=True)
        source_path = root / "legacy-source.mp4"
        source_path.write_bytes(b"source")
        clip_path = output_root / "clips" / "segment_001.mp4"
        clip_path.parent.mkdir(parents=True, exist_ok=True)
        clip_path.write_bytes(b"clip")
        debug_path = output_root / "legacy-project_debug.mp4"
        debug_path.write_bytes(b"debug")
        (output_root / "trace.log").write_text("legacy trace", encoding="utf-8")
        (output_root / "manifest.json").write_text(
            json.dumps(
                {
                    "source_video": str(source_path),
                    "output_root": str(output_root),
                    "video_info": {
                        "width": 1920,
                        "height": 1080,
                        "fps": 60.0,
                        "frame_count": 120,
                        "duration_seconds": 2.0,
                    },
                    "accepted_segments": [
                        {
                            "segment_id": 1,
                            "accepted": True,
                            "start_frame": 0,
                            "end_frame": 59,
                            "start_time": 0.0,
                            "end_time": 0.983,
                            "frame_count": 60,
                            "split_reason": "end_of_video",
                            "crop_window": {"width": 1280, "height": 720},
                            "output_path": str(clip_path),
                        }
                    ],
                    "rejected_segments": [],
                    "split_events": [],
                    "debug_preview_path": str(debug_path),
                },
                indent=2,
            ),
            encoding="utf-8",
        )
        client = create_app(output_dir=root / "output", uploads_dir=root / "uploads", processor=lambda config, reporter: []).test_client()

        list_response = client.get("/api/projects")
        self.assertEqual(list_response.status_code, 200)
        projects = list_response.get_json()["projects"]
        self.assertEqual(projects[0]["project_id"], "legacy-project")

        detail_response = client.get("/api/projects/legacy-project")
        self.assertEqual(detail_response.status_code, 200)
        project = detail_response.get_json()["project"]
        self.assertEqual(project["status"], "completed")
        self.assertEqual(len(project["clips"]), 1)

    def test_blocks_second_start_while_job_is_running(self) -> None:
        release = threading.Event()

        def processor(config, reporter):
            source_path = Path(config.input_path)
            reporter.emit(
                ProcessingEvent(
                    kind="video_started",
                    source_video=str(source_path),
                    message="Processing is running",
                    progress_percent=0.0,
                    details={},
                )
            )
            release.wait(timeout=2)
            return [make_summary(source_path, Path(config.output_dir))]

        temp_dir, client, _ = self.create_client(processor)
        self.addCleanup(temp_dir.cleanup)

        first_upload = client.post(
            "/api/projects",
            data={"source": (io.BytesIO(b"video"), "first.mp4")},
            content_type="multipart/form-data",
        )
        second_upload = client.post(
            "/api/projects",
            data={"source": (io.BytesIO(b"video"), "second.mp4")},
            content_type="multipart/form-data",
        )
        first_project_id = first_upload.get_json()["project"]["project_id"]
        second_project_id = second_upload.get_json()["project"]["project_id"]

        start_first = client.post(f"/api/projects/{first_project_id}/start")
        self.assertEqual(start_first.status_code, 200)

        busy_response = client.post(f"/api/projects/{second_project_id}/start")
        self.assertEqual(busy_response.status_code, 409)

        release.set()
        self.wait_for_status(client, first_project_id, "completed")

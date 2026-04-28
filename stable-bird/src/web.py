from __future__ import annotations

import argparse
import json
import re
import shutil
import threading
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable
from uuid import uuid4

from flask import Flask, Response, abort, jsonify, render_template, request, send_file, url_for

from src.config import (
    DEFAULT_BLUR_THRESHOLD,
    DEFAULT_CENTER_ZONE_PERCENT,
    DEFAULT_CONFIDENCE_THRESHOLD,
    DEFAULT_CROP_MARGIN_PERCENT,
    DEFAULT_DEVICE,
    DEFAULT_GRACE_FRAMES,
    DEFAULT_INFERENCE_CONFIDENCE,
    DEFAULT_INFERENCE_IMAGE_SIZE,
    DEFAULT_LOG_DIR,
    DEFAULT_MIN_SEGMENT_FRAMES,
    DEFAULT_MODEL_PATH,
    DEFAULT_OUTPUT_DIR,
    DEFAULT_SMOOTHING_ALPHA,
    DEFAULT_TRACKING_ANCHOR_X_PERCENT,
    DEFAULT_TRACKING_ANCHOR_Y_PERCENT,
    DEFAULT_TRACE_EVERY_N_FRAMES,
    VIDEO_EXTENSIONS,
    RuntimeConfig,
)
from src.logging_utils import configure_logging
from src.progress import CallbackProgressReporter, ProcessingEvent, ProgressReporter
from src.types import ProcessingSummary

PROJECT_METADATA_FILENAME = "webui_project.json"
UPLOADS_DIR = Path("instance") / "uploads"

ProjectProcessor = Callable[[RuntimeConfig, ProgressReporter | None], list[ProcessingSummary]]


class BusyError(RuntimeError):
    """Raised when the local single-worker slot is already busy."""


def create_app(
    output_dir: Path | None = None,
    uploads_dir: Path | None = None,
    processor: ProjectProcessor | None = None,
) -> Flask:
    package_dir = Path(__file__).resolve().parent
    app = Flask(
        __name__,
        template_folder=str(package_dir / "templates"),
        static_folder=str(package_dir / "static"),
    )
    app.json.sort_keys = False

    store = ProjectStore(output_dir=output_dir or DEFAULT_OUTPUT_DIR, uploads_dir=uploads_dir or UPLOADS_DIR)
    manager = ProjectJobManager(store=store, processor=processor or _default_processor)
    app.extensions["stable_bird.store"] = store
    app.extensions["stable_bird.manager"] = manager

    @app.get("/")
    def index() -> str:
        return render_template("index.html")

    @app.get("/api/projects")
    def list_projects() -> Response:
        projects = [_serialize_project_summary(project) for project in store.list_projects()]
        return jsonify({"projects": projects})

    @app.post("/api/projects")
    def upload_project() -> tuple[Response, int]:
        uploaded_file = request.files.get("source")
        if uploaded_file is None or not uploaded_file.filename:
            return jsonify({"error": "Choose a source video file before uploading."}), 400

        try:
            project = store.create_uploaded_project(uploaded_file.filename)
        except ValueError as exc:
            return jsonify({"error": str(exc)}), 400

        try:
            uploaded_file.save(str(project["source_path"]))
        except Exception:
            store.delete_project(str(project["project_id"]))
            raise

        project = store.get_project(str(project["project_id"]))
        return jsonify({"project": _serialize_project_detail(project)}), 201

    @app.post("/api/projects/<project_id>/start")
    def start_project(project_id: str) -> tuple[Response, int]:
        try:
            project = manager.start(project_id)
        except FileNotFoundError:
            abort(404)
        except BusyError as exc:
            return jsonify({"error": str(exc)}), 409
        except ValueError as exc:
            return jsonify({"error": str(exc)}), 400
        return jsonify({"project": _serialize_project_detail(project)}), 200

    @app.get("/api/projects/<project_id>")
    def get_project(project_id: str) -> Response:
        try:
            project = store.get_project(project_id)
        except FileNotFoundError:
            abort(404)
        return jsonify({"project": _serialize_project_detail(project)})

    @app.get("/api/projects/<project_id>/source")
    def get_project_source(project_id: str) -> Response:
        path = store.get_source_path(project_id)
        if path is None or not path.exists():
            abort(404)
        return send_file(path)

    @app.get("/api/projects/<project_id>/debug-preview")
    def get_debug_preview(project_id: str) -> Response:
        path = store.get_debug_preview_path(project_id)
        if path is None or not path.exists():
            abort(404)
        return send_file(path)

    @app.get("/api/projects/<project_id>/clips/<clip_name>")
    def get_project_clip(project_id: str, clip_name: str) -> Response:
        path = store.get_clip_path(project_id, clip_name)
        if path is None or not path.exists():
            abort(404)
        return send_file(path)

    @app.get("/api/projects/<project_id>/trace")
    def get_trace(project_id: str) -> Response:
        try:
            trace_text = store.get_trace_text(project_id)
        except FileNotFoundError:
            abort(404)
        return Response(trace_text, mimetype="text/plain; charset=utf-8")

    def _serialize_project_summary(project: dict[str, object]) -> dict[str, object]:
        return {
            "project_id": project["project_id"],
            "display_name": project["display_name"],
            "status": project["status"],
            "phase": project["phase"],
            "message": project["message"],
            "progress_percent": project["progress_percent"],
            "updated_at": project["updated_at"],
            "accepted_segment_count": project.get("accepted_segment_count", 0),
            "rejected_segment_count": project.get("rejected_segment_count", 0),
            "can_start": project["status"] == "uploaded",
        }

    def _serialize_project_detail(project: dict[str, object]) -> dict[str, object]:
        project_id = str(project["project_id"])
        manifest = store.get_manifest(project_id)
        clips: list[dict[str, str]] = []
        if project["status"] == "completed":
            for clip_path in store.get_clip_paths(project_id):
                clips.append(
                    {
                        "name": clip_path.name,
                        "url": url_for("get_project_clip", project_id=project_id, clip_name=clip_path.name),
                    }
                )

        source_path = store.get_source_path(project_id)
        debug_preview_path = store.get_debug_preview_path(project_id)
        trace_path = store.get_trace_path(project_id)

        return {
            "project_id": project["project_id"],
            "display_name": project["display_name"],
            "status": project["status"],
            "phase": project["phase"],
            "message": project["message"],
            "progress_percent": project["progress_percent"],
            "error": project.get("error"),
            "created_at": project["created_at"],
            "updated_at": project["updated_at"],
            "accepted_segment_count": project.get("accepted_segment_count", 0),
            "rejected_segment_count": project.get("rejected_segment_count", 0),
            "can_start": project["status"] == "uploaded",
            "source_url": (
                url_for("get_project_source", project_id=project_id)
                if source_path is not None and source_path.exists()
                else None
            ),
            "debug_preview_url": (
                url_for("get_debug_preview", project_id=project_id)
                if debug_preview_path is not None and debug_preview_path.exists() and project["status"] == "completed"
                else None
            ),
            "trace_url": (
                url_for("get_trace", project_id=project_id)
                if trace_path is not None and trace_path.exists()
                else None
            ),
            "clips": clips,
            "manifest": manifest if project["status"] == "completed" else None,
        }

    return app


class ProjectStore:
    def __init__(self, output_dir: Path, uploads_dir: Path) -> None:
        self.output_dir = output_dir.resolve()
        self.uploads_dir = uploads_dir.resolve()
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.uploads_dir.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()

    def create_uploaded_project(self, filename: str) -> dict[str, object]:
        display_name = Path(filename).name
        suffix = Path(display_name).suffix.lower()
        if suffix not in VIDEO_EXTENSIONS:
            supported_types = ", ".join(sorted(VIDEO_EXTENSIONS))
            raise ValueError(f"Unsupported source file type: {suffix or '(missing extension)'}; expected one of {supported_types}.")

        project_id = _build_project_id(display_name)
        output_root = (self.output_dir / project_id).resolve()
        upload_root = (self.uploads_dir / project_id).resolve()
        output_root.mkdir(parents=True, exist_ok=False)
        upload_root.mkdir(parents=True, exist_ok=False)
        source_path = (upload_root / f"{project_id}{suffix}").resolve()
        now = _utc_now()
        project = {
            "project_id": project_id,
            "display_name": display_name,
            "status": "uploaded",
            "phase": "uploaded",
            "message": "Source uploaded. Ready to start processing.",
            "created_at": now,
            "updated_at": now,
            "progress_percent": 0.0,
            "error": None,
            "source_path": str(source_path),
            "output_root": str(output_root),
            "manifest_path": None,
            "debug_preview_path": None,
            "trace_path": None,
            "clip_paths": [],
            "accepted_segment_count": 0,
            "rejected_segment_count": 0,
        }
        self._write_metadata(output_root, project)
        return self._refresh_project(project, output_root)

    def delete_project(self, project_id: str) -> None:
        output_root = self._project_dir(project_id)
        upload_root = (self.uploads_dir / project_id).resolve()
        if output_root.exists():
            shutil.rmtree(output_root, ignore_errors=True)
        if upload_root.exists():
            shutil.rmtree(upload_root, ignore_errors=True)

    def list_projects(self) -> list[dict[str, object]]:
        with self._lock:
            projects: list[dict[str, object]] = []
            for candidate in self.output_dir.iterdir():
                if not candidate.is_dir():
                    continue
                project = self._load_project_from_dir(candidate)
                if project is not None:
                    projects.append(project)
            return sorted(projects, key=lambda item: str(item["updated_at"]), reverse=True)

    def get_project(self, project_id: str) -> dict[str, object]:
        with self._lock:
            project = self._load_project_from_dir(self._project_dir(project_id))
            if project is None:
                raise FileNotFoundError(f"Project not found: {project_id}")
            return project

    def update_project(self, project_id: str, **updates: object) -> dict[str, object]:
        with self._lock:
            project_dir = self._project_dir(project_id)
            project = self.get_project(project_id)
            project.update(updates)
            project["updated_at"] = _utc_now()
            self._write_metadata(project_dir, project)
            return self._refresh_project(project, project_dir)

    def apply_event(self, project_id: str, event: ProcessingEvent) -> dict[str, object]:
        details = event.details
        updates: dict[str, object] = {}
        if event.progress_percent is not None:
            updates["progress_percent"] = round(float(event.progress_percent), 1)

        if event.kind == "video_started":
            updates.update(
                status="running",
                phase="scanning",
                message=event.message or "Scanning source video",
                trace_path=details.get("trace_path"),
                output_root=details.get("output_root"),
            )
        elif event.kind == "scan_progress":
            updates.update(status="running", phase="scanning", message=event.message or "Scanning source video")
        elif event.kind == "scan_complete":
            accepted = int(details.get("accepted_segment_count", 0))
            updates.update(
                status="running",
                phase="rendering" if accepted > 0 else "finalizing",
                message=event.message or "Scan complete",
                accepted_segment_count=accepted,
                rejected_segment_count=int(details.get("rejected_segment_count", 0)),
            )
        elif event.kind in {"render_started", "render_progress", "render_complete"}:
            updates.update(status="running", phase="rendering", message=event.message or "Rendering clips")
        elif event.kind == "finalizing_output":
            updates.update(status="running", phase="finalizing", message=event.message or "Finalizing outputs")
        elif event.kind == "video_completed":
            updates.update(
                status="completed",
                phase="completed",
                message=event.message or "Processing complete",
                error=None,
                progress_percent=100.0,
                manifest_path=details.get("manifest_path"),
                debug_preview_path=details.get("debug_preview_path"),
                trace_path=details.get("trace_path"),
                clip_paths=details.get("clip_paths", []),
                accepted_segment_count=int(details.get("accepted_segment_count", 0)),
                rejected_segment_count=int(details.get("rejected_segment_count", 0)),
            )
        elif event.kind == "video_failed":
            updates.update(
                status="failed",
                phase="failed",
                message=event.message or "Processing failed",
                error=details.get("error") or event.message or "Processing failed",
                trace_path=details.get("trace_path"),
            )

        if not updates:
            return self.get_project(project_id)
        return self.update_project(project_id, **updates)

    def mark_completed_from_summary(self, project_id: str, summary: ProcessingSummary) -> dict[str, object]:
        return self.update_project(
            project_id,
            status="completed",
            phase="completed",
            message=f"Completed {Path(summary.source_video).name}",
            error=None,
            progress_percent=100.0,
            manifest_path=summary.manifest_path,
            debug_preview_path=summary.debug_preview_path,
            clip_paths=[segment.output_path for segment in summary.accepted_segments if segment.output_path],
            accepted_segment_count=len(summary.accepted_segments),
            rejected_segment_count=len(summary.rejected_segments),
        )

    def mark_failed(self, project_id: str, error: str) -> dict[str, object]:
        return self.update_project(
            project_id,
            status="failed",
            phase="failed",
            message=error,
            error=error,
        )

    def get_manifest(self, project_id: str) -> dict[str, object] | None:
        return self.get_project(project_id).get("manifest")  # type: ignore[return-value]

    def get_trace_path(self, project_id: str) -> Path | None:
        project = self.get_project(project_id)
        trace_path = project.get("trace_path")
        if not isinstance(trace_path, str):
            return None
        path = Path(trace_path)
        return path if path.exists() else None

    def get_trace_text(self, project_id: str) -> str:
        trace_path = self.get_trace_path(project_id)
        if trace_path is None:
            raise FileNotFoundError(f"Trace log not found for project {project_id}")
        return trace_path.read_text(encoding="utf-8", errors="replace")

    def get_source_path(self, project_id: str) -> Path | None:
        project = self.get_project(project_id)
        source_path = project.get("source_path")
        if not isinstance(source_path, str):
            return None
        path = Path(source_path)
        return path if path.exists() else None

    def get_debug_preview_path(self, project_id: str) -> Path | None:
        project = self.get_project(project_id)
        debug_path = project.get("debug_preview_path")
        if not isinstance(debug_path, str):
            return None
        path = Path(debug_path)
        return path if path.exists() else None

    def get_clip_paths(self, project_id: str) -> list[Path]:
        project = self.get_project(project_id)
        clip_paths = project.get("clip_paths", [])
        if not isinstance(clip_paths, list):
            return []
        paths: list[Path] = []
        for clip_path in clip_paths:
            if isinstance(clip_path, str):
                path = Path(clip_path)
                if path.exists():
                    paths.append(path)
        return paths

    def get_clip_path(self, project_id: str, clip_name: str) -> Path | None:
        for clip_path in self.get_clip_paths(project_id):
            if clip_path.name == clip_name:
                return clip_path
        return None

    def _project_dir(self, project_id: str) -> Path:
        return (self.output_dir / project_id).resolve()

    def _metadata_path(self, project_dir: Path) -> Path:
        return project_dir / PROJECT_METADATA_FILENAME

    def _write_metadata(self, project_dir: Path, project: dict[str, object]) -> None:
        project_dir.mkdir(parents=True, exist_ok=True)
        self._metadata_path(project_dir).write_text(json.dumps(project, indent=2), encoding="utf-8")

    def _load_project_from_dir(self, project_dir: Path) -> dict[str, object] | None:
        if not project_dir.exists():
            return None

        metadata_path = self._metadata_path(project_dir)
        project: dict[str, object] | None = None
        if metadata_path.exists():
            project = json.loads(metadata_path.read_text(encoding="utf-8"))
        else:
            manifest = self._load_manifest_data(project_dir)
            if manifest is None:
                return None
            project = self._build_legacy_project(project_dir, manifest)
        return self._refresh_project(project, project_dir)

    def _refresh_project(self, project: dict[str, object], project_dir: Path) -> dict[str, object]:
        refreshed = dict(project)
        refreshed["project_id"] = project_dir.name
        refreshed["output_root"] = str(project_dir.resolve())
        refreshed.setdefault("display_name", project_dir.name)
        refreshed.setdefault("status", "uploaded")
        refreshed.setdefault("phase", refreshed["status"])
        refreshed.setdefault("message", "Project available")
        refreshed.setdefault("created_at", _utc_now())
        refreshed.setdefault("updated_at", refreshed["created_at"])
        refreshed.setdefault("progress_percent", 0.0 if refreshed["status"] != "completed" else 100.0)
        refreshed.setdefault("accepted_segment_count", 0)
        refreshed.setdefault("rejected_segment_count", 0)
        refreshed.setdefault("error", None)
        refreshed.setdefault("clip_paths", [])

        source_path = self._resolve_recorded_path(refreshed.get("source_path"), project_dir)
        refreshed["source_path"] = str(source_path) if source_path is not None else refreshed.get("source_path")

        trace_path = self._resolve_recorded_path(refreshed.get("trace_path"), project_dir)
        if trace_path is None:
            default_trace_path = project_dir / "trace.log"
            trace_path = default_trace_path if default_trace_path.exists() else None
        refreshed["trace_path"] = str(trace_path) if trace_path is not None else None

        manifest = self._load_manifest_data(project_dir)
        refreshed["manifest"] = manifest
        if manifest is not None:
            refreshed["manifest_path"] = str((project_dir / "manifest.json").resolve())
            accepted_segments = manifest.get("accepted_segments", [])
            rejected_segments = manifest.get("rejected_segments", [])
            if isinstance(accepted_segments, list):
                refreshed["accepted_segment_count"] = len(accepted_segments)
            if isinstance(rejected_segments, list):
                refreshed["rejected_segment_count"] = len(rejected_segments)
            refreshed["clip_paths"] = [
                str(path)
                for path in self._resolve_clip_paths(project_dir, accepted_segments)
            ]
            debug_preview_path = self._resolve_recorded_path(manifest.get("debug_preview_path"), project_dir)
            refreshed["debug_preview_path"] = str(debug_preview_path) if debug_preview_path is not None else None
            if refreshed.get("status") not in {"running", "failed", "uploaded", "queued"}:
                refreshed["status"] = "completed"
                refreshed["phase"] = "completed"
                refreshed["message"] = "Completed project"
                refreshed["progress_percent"] = 100.0
        else:
            refreshed["manifest_path"] = None
            refreshed["debug_preview_path"] = None

        return refreshed

    def _build_legacy_project(self, project_dir: Path, manifest: dict[str, object]) -> dict[str, object]:
        source_value = manifest.get("source_video")
        source_path = self._resolve_recorded_path(source_value, project_dir)
        stat = project_dir.stat()
        timestamp = datetime.fromtimestamp(stat.st_mtime, tz=timezone.utc).isoformat()
        return {
            "project_id": project_dir.name,
            "display_name": source_path.name if source_path is not None else project_dir.name,
            "status": "completed",
            "phase": "completed",
            "message": "Completed project",
            "created_at": timestamp,
            "updated_at": timestamp,
            "progress_percent": 100.0,
            "error": None,
            "source_path": str(source_path) if source_path is not None else source_value,
            "output_root": str(project_dir.resolve()),
        }

    def _load_manifest_data(self, project_dir: Path) -> dict[str, object] | None:
        manifest_path = project_dir / "manifest.json"
        if not manifest_path.exists():
            return None
        try:
            return json.loads(manifest_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            return None

    def _resolve_recorded_path(self, path_value: object, project_dir: Path) -> Path | None:
        if not isinstance(path_value, str) or not path_value:
            return None
        path = Path(path_value)
        if path.is_absolute():
            return path.resolve() if path.exists() else path
        cwd_candidate = (Path.cwd() / path).resolve()
        if cwd_candidate.exists():
            return cwd_candidate
        project_candidate = (project_dir / path).resolve()
        if project_candidate.exists():
            return project_candidate
        return cwd_candidate

    def _resolve_clip_paths(self, project_dir: Path, accepted_segments: object) -> list[Path]:
        clip_paths: list[Path] = []
        if isinstance(accepted_segments, list):
            for segment in accepted_segments:
                if not isinstance(segment, dict):
                    continue
                clip_path = self._resolve_recorded_path(segment.get("output_path"), project_dir)
                if clip_path is not None and clip_path.exists():
                    clip_paths.append(clip_path)
        if clip_paths:
            return clip_paths

        clips_dir = project_dir / "clips"
        if not clips_dir.exists():
            return []
        return sorted(candidate.resolve() for candidate in clips_dir.iterdir() if candidate.is_file())


class ProjectJobManager:
    def __init__(self, store: ProjectStore, processor: ProjectProcessor) -> None:
        self._store = store
        self._processor = processor
        self._lock = threading.Lock()
        self._active_thread: threading.Thread | None = None
        self._active_project_id: str | None = None

    def start(self, project_id: str) -> dict[str, object]:
        with self._lock:
            if self._active_thread is not None and self._active_thread.is_alive():
                raise BusyError(f"Project {self._active_project_id} is already running. Wait for it to finish first.")

            project = self._store.get_project(project_id)
            if project["status"] != "uploaded":
                raise ValueError("Only newly uploaded projects can be started.")

            queued_project = self._store.update_project(
                project_id,
                status="queued",
                phase="queued",
                message="Queued to start processing.",
                progress_percent=0.0,
                error=None,
            )
            thread = threading.Thread(
                target=self._run_project,
                args=(project_id,),
                daemon=True,
                name=f"stable-bird-project-{project_id}",
            )
            self._active_thread = thread
            self._active_project_id = project_id
            thread.start()
            return queued_project

    def _run_project(self, project_id: str) -> None:
        try:
            project = self._store.get_project(project_id)
            source_path_value = project.get("source_path")
            if not isinstance(source_path_value, str):
                raise RuntimeError(f"Project {project_id} is missing a source video path.")

            source_path = Path(source_path_value)
            if not source_path.exists():
                raise RuntimeError(f"Source video not found for project {project_id}: {source_path}")

            self._store.update_project(
                project_id,
                status="running",
                phase="starting",
                message="Starting processing.",
                progress_percent=0.0,
                error=None,
            )

            config = _build_runtime_config(source_path=source_path, output_dir=self._store.output_dir)
            reporter = CallbackProgressReporter(lambda event: self._store.apply_event(project_id, event))
            summaries = self._processor(config, reporter)
            if not summaries:
                raise RuntimeError("Processing finished without returning a summary.")
            self._store.mark_completed_from_summary(project_id, summaries[0])
        except Exception as exc:
            self._store.mark_failed(project_id, str(exc))
        finally:
            with self._lock:
                self._active_thread = None
                self._active_project_id = None


def _build_runtime_config(source_path: Path, output_dir: Path) -> RuntimeConfig:
    return RuntimeConfig(
        input_path=source_path,
        output_dir=output_dir,
        model_path=DEFAULT_MODEL_PATH,
        log_dir=DEFAULT_LOG_DIR,
        device=DEFAULT_DEVICE,
        confidence_threshold=DEFAULT_CONFIDENCE_THRESHOLD,
        blur_threshold=DEFAULT_BLUR_THRESHOLD,
        center_zone_percent=DEFAULT_CENTER_ZONE_PERCENT,
        grace_frames=DEFAULT_GRACE_FRAMES,
        smoothing_alpha=DEFAULT_SMOOTHING_ALPHA,
        min_segment_frames=DEFAULT_MIN_SEGMENT_FRAMES,
        inference_confidence=DEFAULT_INFERENCE_CONFIDENCE,
        inference_image_size=DEFAULT_INFERENCE_IMAGE_SIZE,
        trace_every_n_frames=DEFAULT_TRACE_EVERY_N_FRAMES,
        crop_margin_percent=DEFAULT_CROP_MARGIN_PERCENT,
        tracking_anchor_x_percent=DEFAULT_TRACKING_ANCHOR_X_PERCENT,
        tracking_anchor_y_percent=DEFAULT_TRACKING_ANCHOR_Y_PERCENT,
        debug_preview=True,
    )


def _default_processor(config: RuntimeConfig, reporter: ProgressReporter | None) -> list[ProcessingSummary]:
    from src.pipeline import process_inputs

    return process_inputs(config, reporter=reporter)


def _build_project_id(filename: str) -> str:
    stem = Path(filename).stem.lower()
    slug = re.sub(r"[^a-z0-9]+", "-", stem).strip("-") or "project"
    return f"{datetime.now().strftime('%Y%m%d-%H%M%S')}-{slug[:32]}-{uuid4().hex[:8]}"


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Run the stable-bird local web UI.")
    parser.add_argument("--host", default="127.0.0.1", help="Host interface to bind the local web server to.")
    parser.add_argument("--port", type=int, default=5000, help="Port to bind the local web server to.")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    log_path = configure_logging(DEFAULT_LOG_DIR)
    print(f"Tracing to {log_path}")
    print(f"Web UI available at http://{args.host}:{args.port}", flush=True)
    app = create_app()
    app.run(host=args.host, port=args.port, debug=False, use_reloader=False)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

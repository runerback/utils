from __future__ import annotations

import argparse
import logging
import os
import shutil
import subprocess
import sys
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import FileResponse, HTMLResponse
from fastapi.staticfiles import StaticFiles

from .ffmpeg import FFmpegService
from .scene_detection import SceneDetectionService
from .schemas import (
    EditState,
    ExportEstimatePart,
    ExportEstimateResponse,
    LocalProjectCreateRequest,
    ProjectCreateResponse,
    ProjectListItem,
    ProjectStateResponse,
    RenderPart,
    RenderResponse,
    StateUpdateRequest,
    TrimFramesResponse,
    VideoMetadata,
)
from .storage import Storage

ROOT = Path(__file__).resolve().parent.parent
INDEX_HTML_PATH = ROOT / "static" / "index.html"
logger = logging.getLogger(__name__)
LOG_FORMAT = "%(asctime)s %(levelname)s [%(name)s] %(message)s"
LOG_FILE_NAME = "vae.log"


def _cli_path(args: list[str], option: str) -> str:
    for index, value in enumerate(args):
        if value == option and index + 1 < len(args):
            return args[index + 1].strip()
        if value.startswith(f"{option}="):
            return value.split("=", 1)[1].strip()
    return ""


def _format_log_context(**context: object) -> str:
    entries = [f"{key}={value!r}" for key, value in context.items() if value is not None]
    return f" [{', '.join(entries)}]" if entries else ""


def _logged_http_exception(status_code: int, exc: Exception, message: str, **context: object) -> HTTPException:
    context_text = _format_log_context(**context)
    if status_code == 404:
        logger.warning("%s%s: %s", message, context_text, exc)
    else:
        logger.exception("%s%s", message, context_text)
    return HTTPException(status_code=status_code, detail=str(exc))


def resolve_log_level() -> int:
    return getattr(logging, os.getenv("VAE_LOG_LEVEL", "INFO").upper(), logging.INFO)


def resolve_log_file() -> Path:
    return ROOT / ".venv" / LOG_FILE_NAME


def _has_file_handler(logger_instance: logging.Logger, log_file: Path) -> bool:
    target = log_file.resolve()
    for handler in logger_instance.handlers:
        if isinstance(handler, logging.FileHandler) and Path(handler.baseFilename).resolve() == target:
            return True
    return False


def _configure_root_logging(log_file: Path, level: int, formatter: logging.Formatter) -> None:
    root_logger = logging.getLogger()
    root_logger.setLevel(level)
    if not any(isinstance(handler, logging.StreamHandler) and not isinstance(handler, logging.FileHandler) for handler in root_logger.handlers):
        stream_handler = logging.StreamHandler()
        stream_handler.setLevel(level)
        stream_handler.setFormatter(formatter)
        root_logger.addHandler(stream_handler)
    if not _has_file_handler(root_logger, log_file):
        file_handler = logging.FileHandler(log_file, encoding="utf-8")
        file_handler.setLevel(level)
        file_handler.setFormatter(formatter)
        root_logger.addHandler(file_handler)


def _configure_named_file_logger(logger_name: str, log_file: Path, level: int, formatter: logging.Formatter) -> None:
    named_logger = logging.getLogger(logger_name)
    named_logger.setLevel(level)
    if _has_file_handler(named_logger, log_file):
        return
    file_handler = logging.FileHandler(log_file, encoding="utf-8")
    file_handler.setLevel(level)
    file_handler.setFormatter(formatter)
    named_logger.addHandler(file_handler)


def configure_logging() -> Path:
    log_file = resolve_log_file()
    log_file.parent.mkdir(parents=True, exist_ok=True)
    level = resolve_log_level()
    formatter = logging.Formatter(LOG_FORMAT)
    _configure_root_logging(log_file, level, formatter)
    _configure_named_file_logger("uvicorn", log_file, level, formatter)
    _configure_named_file_logger("uvicorn.access", log_file, level, formatter)
    return log_file


def resolve_export_dir() -> Path | None:
    configured = os.getenv("VAE_EXPORT_PATH", "").strip()
    cli_configured = _cli_path(sys.argv[1:], "--export-path")
    if not configured and cli_configured:
        configured = cli_configured
        # Persist for reload worker process so exports stay consistent.
        os.environ["VAE_EXPORT_PATH"] = cli_configured
    if not configured:
        return None
    path = Path(configured).expanduser()
    if path.is_absolute():
        return path
    return ROOT / path


def resolve_work_dir() -> Path | None:
    configured = os.getenv("VAE_WORK_PATH", "").strip()
    cli_configured = _cli_path(sys.argv[1:], "--work-path")
    if not configured and cli_configured:
        configured = cli_configured
        # Persist for reload worker process so work files stay consistent.
        os.environ["VAE_WORK_PATH"] = cli_configured
    if not configured:
        return None
    path = Path(configured).expanduser()
    if path.is_absolute():
        return path
    return ROOT / path


def resolve_uploads_dir() -> Path | None:
    configured = os.getenv("VAE_UPLOADS_PATH", "").strip()
    cli_configured = _cli_path(sys.argv[1:], "--uploads-path")
    if not configured and cli_configured:
        configured = cli_configured
        # Persist for reload worker process so uploads stay consistent.
        os.environ["VAE_UPLOADS_PATH"] = cli_configured
    if not configured:
        return None
    path = Path(configured).expanduser()
    if path.is_absolute():
        return path
    return ROOT / path


@asynccontextmanager
async def lifespan(_: FastAPI):
    log_file = configure_logging()
    logger.info(
        "Starting VAE server%s",
        _format_log_context(
            export_dir=str(resolve_export_dir() or storage.exports),
            work_dir=str(resolve_work_dir() or storage.work),
            uploads_dir=str(resolve_uploads_dir() or storage.uploads),
            log_file=str(log_file),
        ),
    )
    yield


app = FastAPI(title="VAE - Video Adjustment Editor", lifespan=lifespan)
storage = Storage(
    ROOT,
    export_dir=resolve_export_dir(),
    work_dir=resolve_work_dir(),
    uploads_dir=resolve_uploads_dir(),
)
ffmpeg = FFmpegService()
scene_detection = SceneDetectionService(ffmpeg)

app.mount("/static", StaticFiles(directory=str(ROOT / "static")), name="static")
app.mount("/uploads", StaticFiles(directory=str(storage.uploads)), name="uploads")
app.mount("/work", StaticFiles(directory=str(storage.work)), name="work")
app.mount("/exports", StaticFiles(directory=str(storage.exports)), name="exports")

def _project_original_url(project_id: str) -> str:
    return f"/api/projects/{project_id}/original"


def _static_asset_url(asset_name: str) -> str:
    asset_path = ROOT / "static" / asset_name
    version = asset_path.stat().st_mtime_ns
    return f"/static/{asset_name}?v={version}"


def _index_html() -> str:
    html = INDEX_HTML_PATH.read_text(encoding="utf-8")
    return (
        html
        .replace("/static/styles.css", _static_asset_url("styles.css"))
        .replace("/static/app.js", _static_asset_url("app.js"))
    )


def _metadata_needs_refresh(metadata: VideoMetadata) -> bool:
    return metadata.video_codec.strip().lower() == "unknown" or metadata.container_format.strip().lower() == "unknown"


def _ensure_original_player_support(paths, metadata, state):
    project_changed = False
    if _metadata_needs_refresh(metadata):
        metadata = ffmpeg.probe(paths.original_file)
        project_changed = True
    if ffmpeg.requires_player_proxy(metadata) and not paths.player_proxy_file.exists():
        ffmpeg.run(ffmpeg.build_player_proxy_command(paths.original_file, paths.player_proxy_file))
        project_changed = True
    if project_changed:
        storage.save_project(paths, metadata, state)
    return metadata


def _original_player_file(paths, metadata):
    if ffmpeg.requires_player_proxy(metadata) and paths.player_proxy_file.exists():
        return paths.player_proxy_file
    return paths.original_file


def _uses_fixed_length_scene_split(state: EditState) -> bool:
    return state.scene_split.detector == "ffmpeg" and state.scene_split.fixed_length_enabled


def _split_segments_for_state(source_file: Path, metadata: VideoMetadata, state: EditState) -> list[tuple[float, float]]:
    trim_start = state.trim.start
    trim_end = state.trim.end or metadata.duration
    trim_duration = trim_end - trim_start
    if _uses_fixed_length_scene_split(state):
        relative_segments = ffmpeg.build_fixed_length_segments(
            duration=trim_duration,
            clip_length=state.scene_split.fixed_clip_length,
        )
    else:
        min_len, max_len = ffmpeg.adjusted_scene_split_lengths(
            metadata,
            state.scene_split.min_clip_length,
            state.scene_split.max_clip_length,
        )
        scene_cuts = scene_detection.detect_scene_changes(source_file, metadata, state.scene_split)
        ranged_cuts = [cut - trim_start for cut in scene_cuts if trim_start < cut < trim_end]
        relative_segments = ffmpeg.build_scene_split_segments(
            duration=trim_duration,
            candidate_cuts=ranged_cuts,
            min_len=min_len,
            max_len=max_len,
        )
    segments = [(round(trim_start + start, 6), round(trim_start + end, 6)) for start, end in relative_segments]
    logger.info(
        "Built %s split segments%s",
        len(segments),
        _format_log_context(
            source=str(source_file),
            trim_start=trim_start,
            trim_end=trim_end,
            detector=state.scene_split.detector,
            fixed_length=_uses_fixed_length_scene_split(state),
        ),
    )
    return segments


def _selected_scene_split_indexes(state: EditState, segment_count: int) -> set[int]:
    selected_indexes = {
        clip_index
        for clip_index in state.scene_split.selected_clip_indexes
        if 1 <= clip_index <= segment_count
    }
    if state.scene_split.selected_clip_indexes and not selected_indexes:
        raise ValueError("Selected clips are no longer available. Render preview again or clear the clip selection.")
    return selected_indexes


def _multipart_output_path(base_file: Path, index: int) -> Path:
    suffix = base_file.suffix or ".mp4"
    stem = base_file.stem
    return base_file.with_name(f"{stem}_part{index:03d}{suffix}")


def _clear_multipart_outputs(base_file: Path) -> None:
    suffix = base_file.suffix or ".mp4"
    for part_file in sorted(base_file.parent.glob(f"{base_file.stem}_part*{suffix}")):
        if part_file.is_file():
            part_file.unlink()


def _file_size_bytes(path: Path) -> int | None:
    if not path.exists() or not path.is_file():
        return None
    return path.stat().st_size


def _render_part_response(part_index: int, start: float, end: float, part_file: Path) -> RenderPart:
    return RenderPart(
        index=part_index,
        start=start,
        end=end,
        output_url=f"/exports/{part_file.name}",
        output_path=str(part_file.resolve()),
        output_size_bytes=_file_size_bytes(part_file),
    )


def _export_suffix(export_format: str, fallback_suffix: str = ".mp4") -> str:
    return ".gif" if export_format == "gif" else fallback_suffix


def _render_export_response(
    project_id: str,
    paths,
    metadata: VideoMetadata,
    state: EditState,
    export_format: str,
) -> RenderResponse:
    ffmpeg.validate_state(metadata, state)
    paths.export_file = storage.build_export_file_path(
        _export_suffix(export_format, paths.export_file.suffix or ".mp4")
    )
    if not state.scene_split.enabled:
        command = (
            ffmpeg.build_export_gif_command(paths.original_file, paths.export_file, metadata, state)
            if export_format == "gif"
            else ffmpeg.build_export_command(paths.original_file, paths.export_file, metadata, state)
        )
        ffmpeg.run(command)
        storage.save_project(paths, metadata, state)
        return RenderResponse(
            project_id=project_id,
            format=export_format,
            output_url=f"/exports/{paths.export_file.name}",
            output_path=str(paths.export_file.resolve()),
            output_size_bytes=_file_size_bytes(paths.export_file),
            total_output_size_bytes=_file_size_bytes(paths.export_file),
        )

    segments = _split_segments_for_state(paths.original_file, metadata, state)
    selected_indexes = _selected_scene_split_indexes(state, len(segments))
    parts: list[RenderPart] = []
    total_output_size_bytes = 0
    for part_index, (start, end) in enumerate(segments, start=1):
        if selected_indexes and part_index not in selected_indexes:
            continue
        part_file = _multipart_output_path(paths.export_file, part_index)
        command = (
            ffmpeg.build_export_gif_segment_command(
                paths.original_file,
                part_file,
                metadata,
                state,
                segment_start=start,
                segment_end=end,
            )
            if export_format == "gif"
            else ffmpeg.build_export_segment_command(
                paths.original_file,
                part_file,
                metadata,
                state,
                segment_start=start,
                segment_end=end,
            )
        )
        ffmpeg.run(command)
        part_response = _render_part_response(part_index, start, end, part_file)
        parts.append(part_response)
        total_output_size_bytes += part_response.output_size_bytes or 0
    storage.save_project(paths, metadata, state)
    return RenderResponse(
        project_id=project_id,
        format=export_format,
        parts=parts,
        total_output_size_bytes=total_output_size_bytes or None,
    )


@app.get("/")
def index() -> HTMLResponse:
    return HTMLResponse(_index_html(), headers={"Cache-Control": "no-cache"})


@app.get("/api/projects", response_model=list[ProjectListItem])
def list_projects() -> list[ProjectListItem]:
    return [ProjectListItem.model_validate(item) for item in storage.list_projects()]


@app.post("/api/projects", response_model=ProjectCreateResponse)
async def create_project(file: UploadFile = File(...)) -> ProjectCreateResponse:
    try:
        paths = storage.create_project_paths(file.filename or "upload.mp4")
        with paths.original_file.open("wb") as destination:
            shutil.copyfileobj(file.file, destination)
        metadata = ffmpeg.probe(paths.original_file)
        state = EditState()
        metadata = _ensure_original_player_support(paths, metadata, state)
        storage.save_project(paths, metadata, state)
        return ProjectCreateResponse(
            project_id=paths.project_id,
            metadata=metadata,
            state=state,
            original_url=_project_original_url(paths.project_id),
            original_uses_proxy=ffmpeg.requires_player_proxy(metadata),
        )
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        raise _logged_http_exception(400, exc, "Failed to create project from upload", filename=file.filename) from exc


@app.post("/api/projects/from-path", response_model=ProjectCreateResponse)
def create_project_from_path(payload: LocalProjectCreateRequest) -> ProjectCreateResponse:
    try:
        source_file = Path(payload.source_path).expanduser()
        if not source_file.is_absolute():
            raise ValueError("source_path must be an absolute file path")
        source_file = source_file.resolve()
        if not source_file.exists() or not source_file.is_file():
            raise ValueError(f"Source file not found: {source_file}")
        with source_file.open("rb"):
            pass
        paths = storage.create_project_paths_from_source(source_file)
        metadata = ffmpeg.probe(paths.original_file)
        state = EditState()
        metadata = _ensure_original_player_support(paths, metadata, state)
        storage.save_project(paths, metadata, state)
        return ProjectCreateResponse(
            project_id=paths.project_id,
            metadata=metadata,
            state=state,
            original_url=_project_original_url(paths.project_id),
            original_uses_proxy=ffmpeg.requires_player_proxy(metadata),
        )
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        raise _logged_http_exception(400, exc, "Failed to create project from local path", source_path=payload.source_path) from exc


@app.get("/api/projects/{project_id}/original")
def get_project_original(project_id: str) -> FileResponse:
    try:
        paths, metadata, state = storage.load_project(project_id)
        metadata = _ensure_original_player_support(paths, metadata, state)
    except FileNotFoundError as exc:
        raise _logged_http_exception(404, exc, "Original media lookup failed", project_id=project_id) from exc
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        raise _logged_http_exception(400, exc, "Failed to prepare original media", project_id=project_id) from exc
    player_file = _original_player_file(paths, metadata)
    if not player_file.exists():
        detail = f"Original file for project {project_id} not found"
        logger.warning("Original media file missing%s", _format_log_context(project_id=project_id, player_file=str(player_file)))
        raise HTTPException(status_code=404, detail=detail)
    media_type = "video/mp4" if player_file == paths.player_proxy_file else None
    return FileResponse(path=player_file, media_type=media_type)


@app.get("/api/projects/{project_id}", response_model=ProjectStateResponse)
def get_project(project_id: str) -> ProjectStateResponse:
    try:
        paths, metadata, state = storage.load_project(project_id)
        metadata = _ensure_original_player_support(paths, metadata, state)
    except FileNotFoundError as exc:
        raise _logged_http_exception(404, exc, "Project lookup failed", project_id=project_id) from exc
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        raise _logged_http_exception(400, exc, "Failed to load project state", project_id=project_id) from exc
    preview_url = f"/work/{paths.preview_file.name}" if paths.preview_file.exists() else None
    return ProjectStateResponse(
        project_id=project_id,
        metadata=metadata,
        state=state,
        original_url=_project_original_url(project_id),
        preview_url=preview_url,
        original_uses_proxy=ffmpeg.requires_player_proxy(metadata),
    )


@app.put("/api/projects/{project_id}/state", response_model=ProjectStateResponse)
def update_state(project_id: str, payload: StateUpdateRequest) -> ProjectStateResponse:
    try:
        paths, metadata, _ = storage.load_project(project_id)
        ffmpeg.validate_state(metadata, payload.state)
        storage.save_project(paths, metadata, payload.state)
        return ProjectStateResponse(
            project_id=project_id,
            metadata=metadata,
            state=payload.state,
            original_url=_project_original_url(project_id),
            preview_url=f"/work/{paths.preview_file.name}" if paths.preview_file.exists() else None,
            original_uses_proxy=ffmpeg.requires_player_proxy(metadata),
        )
    except FileNotFoundError as exc:
        raise _logged_http_exception(404, exc, "Project state update failed", project_id=project_id) from exc
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        raise _logged_http_exception(400, exc, "Failed to save project state", project_id=project_id) from exc


@app.post("/api/projects/{project_id}/preview", response_model=RenderResponse)
def render_preview(project_id: str) -> RenderResponse:
    try:
        paths, metadata, state = storage.load_project(project_id)
        ffmpeg.validate_state(metadata, state)
        _clear_multipart_outputs(paths.preview_file)
        if not state.scene_split.enabled:
            command = ffmpeg.build_preview_command(paths.original_file, paths.preview_file, metadata, state)
            ffmpeg.run(command)
            return RenderResponse(project_id=project_id, output_url=f"/work/{paths.preview_file.name}")

        if paths.preview_file.exists():
            paths.preview_file.unlink()
        segments = _split_segments_for_state(paths.original_file, metadata, state)
        parts: list[RenderPart] = []
        for part_index, (start, end) in enumerate(segments, start=1):
            part_file = _multipart_output_path(paths.preview_file, part_index)
            command = ffmpeg.build_preview_segment_command(
                paths.original_file,
                part_file,
                metadata,
                state,
                segment_start=start,
                segment_end=end,
            )
            ffmpeg.run(command)
            parts.append(RenderPart(index=part_index, start=start, end=end, output_url=f"/work/{part_file.name}"))
        return RenderResponse(project_id=project_id, parts=parts)
    except FileNotFoundError as exc:
        raise _logged_http_exception(404, exc, "Preview render failed: project not found", project_id=project_id) from exc
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        raise _logged_http_exception(400, exc, "Preview render failed", project_id=project_id) from exc


@app.get("/api/projects/{project_id}/trim-frames", response_model=TrimFramesResponse)
def trim_frames(project_id: str, start: float, end: float) -> TrimFramesResponse:
    try:
        paths, metadata, _ = storage.load_project(project_id)
        safe_start = ffmpeg.clamp_frame_timestamp(metadata, start)
        safe_end = ffmpeg.clamp_frame_timestamp(metadata, end)
        start_image = storage.work / f"{project_id}_trim_start.jpg"
        end_image = storage.work / f"{project_id}_trim_end.jpg"
        ffmpeg.run(ffmpeg.build_frame_image_command(paths.original_file, start_image, safe_start))
        ffmpeg.run(ffmpeg.build_frame_image_command(paths.original_file, end_image, safe_end))
        return TrimFramesResponse(
            start_url=f"/work/{start_image.name}",
            end_url=f"/work/{end_image.name}",
        )
    except FileNotFoundError as exc:
        raise _logged_http_exception(404, exc, "Trim frame generation failed: project not found", project_id=project_id) from exc
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        raise _logged_http_exception(400, exc, "Trim frame generation failed", project_id=project_id, start=start, end=end) from exc


@app.post("/api/projects/{project_id}/export", response_model=RenderResponse)
def render_export(project_id: str) -> RenderResponse:
    try:
        paths, metadata, state = storage.load_project(project_id)
        return _render_export_response(project_id, paths, metadata, state, export_format="mp4")
    except FileNotFoundError as exc:
        raise _logged_http_exception(404, exc, "Export failed: project not found", project_id=project_id, export_format="mp4") from exc
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        raise _logged_http_exception(400, exc, "Export failed", project_id=project_id, export_format="mp4") from exc


@app.post("/api/projects/{project_id}/export/gif-estimate", response_model=ExportEstimateResponse)
def estimate_export_gif(project_id: str) -> ExportEstimateResponse:
    try:
        paths, metadata, state = storage.load_project(project_id)
        ffmpeg.validate_state(metadata, state)
        if not state.scene_split.enabled:
            return ExportEstimateResponse(
                project_id=project_id,
                estimated_size_bytes=ffmpeg.estimate_gif_size(metadata, state),
            )

        segments = _split_segments_for_state(paths.original_file, metadata, state)
        selected_indexes = _selected_scene_split_indexes(state, len(segments))
        parts: list[ExportEstimatePart] = []
        for part_index, (start, end) in enumerate(segments, start=1):
            if selected_indexes and part_index not in selected_indexes:
                continue
            parts.append(
                ExportEstimatePart(
                    index=part_index,
                    start=start,
                    end=end,
                    estimated_size_bytes=ffmpeg.estimate_gif_size(
                        metadata,
                        state,
                        segment_start=start,
                        segment_end=end,
                    ),
                )
            )
        return ExportEstimateResponse(
            project_id=project_id,
            estimated_size_bytes=sum(part.estimated_size_bytes for part in parts),
            parts=parts,
        )
    except FileNotFoundError as exc:
        raise _logged_http_exception(404, exc, "GIF estimate failed: project not found", project_id=project_id) from exc
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        raise _logged_http_exception(400, exc, "GIF estimate failed", project_id=project_id) from exc


@app.post("/api/projects/{project_id}/export/gif", response_model=RenderResponse)
def render_export_gif(project_id: str) -> RenderResponse:
    try:
        paths, metadata, state = storage.load_project(project_id)
        return _render_export_response(project_id, paths, metadata, state, export_format="gif")
    except FileNotFoundError as exc:
        raise _logged_http_exception(404, exc, "GIF export failed: project not found", project_id=project_id, export_format="gif") from exc
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        raise _logged_http_exception(400, exc, "GIF export failed", project_id=project_id, export_format="gif") from exc


@app.get("/api/projects/{project_id}/export/download")
def download_export(project_id: str) -> FileResponse:
    try:
        paths, _, _ = storage.load_project(project_id)
    except FileNotFoundError as exc:
        raise _logged_http_exception(404, exc, "Export download failed: project not found", project_id=project_id) from exc
    if not paths.export_file.exists():
        detail = f"Export file for project {project_id} not found"
        logger.warning("Export file missing%s", _format_log_context(project_id=project_id, export_file=str(paths.export_file)))
        raise HTTPException(status_code=404, detail=detail)
    filename = storage.build_export_filename(paths.export_file.suffix or ".mp4")
    media_type = "image/gif" if paths.export_file.suffix.lower() == ".gif" else "video/mp4"
    return FileResponse(path=paths.export_file, media_type=media_type, filename=filename)


if __name__ == "__main__":
    import uvicorn

    parser = argparse.ArgumentParser(description="Run VAE web app")
    parser.add_argument(
        "--export-path",
        default=None,
        help="Directory for final exports (absolute or relative to project root)",
    )
    parser.add_argument(
        "--work-path",
        default=None,
        help="Directory for preview/work outputs (absolute or relative to project root)",
    )
    parser.add_argument(
        "--uploads-path",
        default=None,
        help="Directory for uploaded source files (absolute or relative to project root)",
    )
    args = parser.parse_args()
    if args.export_path:
        os.environ["VAE_EXPORT_PATH"] = args.export_path
    if args.work_path:
        os.environ["VAE_WORK_PATH"] = args.work_path
    if args.uploads_path:
        os.environ["VAE_UPLOADS_PATH"] = args.uploads_path
    logging.basicConfig(
        level=resolve_log_level(),
        format=LOG_FORMAT,
    )
    uvicorn.run("app.main:app", host="0.0.0.0", port=31692, reload=True)


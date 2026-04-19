from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from .ffmpeg import FFmpegService
from .scene_detection import SceneDetectionService
from .schemas import (
    EditState,
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


def _cli_path(args: list[str], option: str) -> str:
    for index, value in enumerate(args):
        if value == option and index + 1 < len(args):
            return args[index + 1].strip()
        if value.startswith(f"{option}="):
            return value.split("=", 1)[1].strip()
    return ""


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


app = FastAPI(title="VAE - Video Adjustment Editor")
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


def _split_segments_for_state(source_file: Path, metadata: VideoMetadata, state: EditState) -> list[tuple[float, float]]:
    trim_start = state.trim.start
    trim_end = state.trim.end or metadata.duration
    trim_duration = trim_end - trim_start
    scene_cuts = scene_detection.detect_scene_changes(source_file, metadata, state.scene_split)
    ranged_cuts = [cut - trim_start for cut in scene_cuts if trim_start < cut < trim_end]
    relative_segments = ffmpeg.build_scene_split_segments(
        duration=trim_duration,
        candidate_cuts=ranged_cuts,
        min_len=state.scene_split.min_clip_length,
        max_len=state.scene_split.max_clip_length,
    )
    return [(round(trim_start + start, 6), round(trim_start + end, 6)) for start, end in relative_segments]


def _multipart_output_path(base_file: Path, index: int) -> Path:
    suffix = base_file.suffix or ".mp4"
    stem = base_file.stem
    return base_file.with_name(f"{stem}_part{index:03d}{suffix}")


def _clear_multipart_outputs(base_file: Path) -> None:
    suffix = base_file.suffix or ".mp4"
    for part_file in sorted(base_file.parent.glob(f"{base_file.stem}_part*{suffix}")):
        if part_file.is_file():
            part_file.unlink()


@app.get("/")
def index() -> FileResponse:
    return FileResponse(ROOT / "static" / "index.html")


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
        raise HTTPException(status_code=400, detail=str(exc)) from exc


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
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/api/projects/{project_id}/original")
def get_project_original(project_id: str) -> FileResponse:
    try:
        paths, metadata, state = storage.load_project(project_id)
        metadata = _ensure_original_player_support(paths, metadata, state)
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    player_file = _original_player_file(paths, metadata)
    if not player_file.exists():
        raise HTTPException(status_code=404, detail=f"Original file for project {project_id} not found")
    media_type = "video/mp4" if player_file == paths.player_proxy_file else None
    return FileResponse(path=player_file, media_type=media_type)


@app.get("/api/projects/{project_id}", response_model=ProjectStateResponse)
def get_project(project_id: str) -> ProjectStateResponse:
    try:
        paths, metadata, state = storage.load_project(project_id)
        metadata = _ensure_original_player_support(paths, metadata, state)
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
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
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


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
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/api/projects/{project_id}/trim-frames", response_model=TrimFramesResponse)
def trim_frames(project_id: str, start: float, end: float) -> TrimFramesResponse:
    try:
        paths, metadata, _ = storage.load_project(project_id)
        safe_start = max(0.0, min(start, metadata.duration))
        safe_end = max(0.0, min(end, metadata.duration))
        start_image = storage.work / f"{project_id}_trim_start.jpg"
        end_image = storage.work / f"{project_id}_trim_end.jpg"
        ffmpeg.run(ffmpeg.build_frame_image_command(paths.original_file, start_image, safe_start))
        ffmpeg.run(ffmpeg.build_frame_image_command(paths.original_file, end_image, safe_end))
        return TrimFramesResponse(
            start_url=f"/work/{start_image.name}",
            end_url=f"/work/{end_image.name}",
        )
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/api/projects/{project_id}/export", response_model=RenderResponse)
def render_export(project_id: str) -> RenderResponse:
    try:
        paths, metadata, state = storage.load_project(project_id)
        ffmpeg.validate_state(metadata, state)
        paths.export_file = storage.build_export_file_path(paths.export_file.suffix or ".mp4")
        if not state.scene_split.enabled:
            command = ffmpeg.build_export_command(paths.original_file, paths.export_file, metadata, state)
            ffmpeg.run(command)
            storage.save_project(paths, metadata, state)
            return RenderResponse(
                project_id=project_id,
                output_url=f"/exports/{paths.export_file.name}",
                output_path=str(paths.export_file.resolve()),
            )

        segments = _split_segments_for_state(paths.original_file, metadata, state)
        parts: list[RenderPart] = []
        for part_index, (start, end) in enumerate(segments, start=1):
            part_file = _multipart_output_path(paths.export_file, part_index)
            command = ffmpeg.build_export_segment_command(
                paths.original_file,
                part_file,
                metadata,
                state,
                segment_start=start,
                segment_end=end,
            )
            ffmpeg.run(command)
            parts.append(
                RenderPart(
                    index=part_index,
                    start=start,
                    end=end,
                    output_url=f"/exports/{part_file.name}",
                    output_path=str(part_file.resolve()),
                )
            )
        storage.save_project(paths, metadata, state)
        return RenderResponse(project_id=project_id, parts=parts)
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/api/projects/{project_id}/export/download")
def download_export(project_id: str) -> FileResponse:
    try:
        paths, _, _ = storage.load_project(project_id)
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    if not paths.export_file.exists():
        raise HTTPException(status_code=404, detail=f"Export file for project {project_id} not found")
    filename = storage.build_export_filename(paths.export_file.suffix or ".mp4")
    return FileResponse(path=paths.export_file, media_type="video/mp4", filename=filename)


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
    uvicorn.run("app.main:app", host="127.0.0.1", port=31692, reload=True)


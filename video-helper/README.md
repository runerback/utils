# Video Adjustment Editor

A non-destructive web video editor built with **Python, FastAPI, and FFmpeg**.

Users can:
- Load a video
- Re-open previous projects from the web UI
- Preview **original** and **modified** versions side by side
- Trim (A-B), crop, resize, change frame rate, and split by scene
- Rotate left/right by 90 degrees
- Export a new video without modifying the source file

## Features

- Non-destructive project state (source video is never edited in place)
- Trim controls:
  - A-B range sliders
  - Per-frame step buttons
  - Numeric time inputs
  - Zoom control from frame-level (0.04s window) up to 10s
- Crop controls:
  - Drag crop region on preview
  - Manual numeric crop inputs
  - Presets: `4:3`, `3:4`, `16:9`, `9:16`
- Rotate controls:
  - Left 90
  - Right 90
- Resize control for the maximum longer edge
- Whole-video FPS override (single global setting)
- Export as MP4 or GIF
- GIF export confirmation with an estimated file size before rendering
- Scene split controls:
  - FFmpeg threshold or AI TransNetV2 detector mode
  - AI sensitivity control
  - Minimum and maximum clip length
  - Browser remembers the latest scene split UI values for new projects on the same device/browser
  - Multipart preview clips shown in the web UI after **Apply Changes**
  - Choose only some rendered clips for export, or leave the selection empty to export all clips
- Preview render and final export render via FFmpeg
- Browser-compatible playback proxy for HEVC/libx265 source videos

## Tech Stack

- Backend: FastAPI
- Frontend: HTML/CSS/JavaScript
- Media processing: FFmpeg + ffprobe

## Project Structure

```text
app/        FastAPI app, schemas, storage, ffmpeg service
static/     Web UI (index.html, app.js, styles.css)
tests/      Unit tests
uploads/    Original uploaded videos (immutable)
work/       Preview/proxy outputs
exports/    Final exported videos
projects/   Project state JSON files
```

## Requirements

1. Python 3.10+ (project uses `.venv`)
2. FFmpeg and ffprobe available in PATH

Python dependencies:

```powershell
.\.venv\Scripts\python -m pip install -r requirements.txt
```

For AI scene split mode, place a **TransNetV2 ONNX** model at `models\transnetv2.onnx`, or set:

```powershell
$env:VAE_TRANSNETV2_MODEL_PATH = "D:\models\transnetv2.onnx"
```

Optional Windows CUDA acceleration:

- Install `onnxruntime-gpu` instead of `onnxruntime`
- Ensure CUDA/cuDNN versions match the ONNX Runtime GPU package
- If CUDA is unavailable, the AI detector falls back to CPU only when you install the CPU runtime; otherwise switch the UI detector back to **FFmpeg**

## Run

```powershell
.\.venv\Scripts\python -m app.main
```

Optional custom directories at startup (`--uploads-path`, `--export-path`, and `--work-path`):

```powershell
.\.venv\Scripts\python -m app.main --uploads-path "D:\video-uploads" --export-path "D:\video-exports" --work-path "D:\video-work"
```

or:

```powershell
$env:VAE_UPLOADS_PATH = "D:\video-uploads"
$env:VAE_EXPORT_PATH = "D:\video-exports"
$env:VAE_WORK_PATH = "D:\video-work"
.\.venv\Scripts\python -m app.main
```

Open:

`http://127.0.0.1:31692`

## Basic Workflow

1. Load a video:
   - Select a file and click **Upload**.
   - The app automatically prefers local-path project creation when an absolute path is available from the runtime, otherwise it uploads a copy to `uploads\`.
   - The status line after Upload reports which mode was used and whether a compatibility playback proxy was created.
2. Adjust trim/crop/rotate/resize/fps/scene split in the UI.
3. Click **Save State**.
4. Click **Apply Changes** to render the modified preview.
   - If **Scene Split** is off, the preview player shows one modified clip.
   - If **Scene Split** is on, the preview area shows all generated clips, lets you switch between them, and lets you choose which clips to export.
5. Click **Export** to save an MP4, or **Export as GIF** to render a GIF after confirming the estimated file size.
   - If **Scene Split** is on, export writes the selected numbered output clips instead of one file. If no clips are selected, export writes all split clips.

If a local-path source is missing later, the app will auto-fallback to project-derived/cached media from `work\` or `uploads\` when available.

## API Endpoints

- `POST /api/projects` - upload and create project
- `POST /api/projects/from-path` - create project from an absolute local source path (used by auto-routing when available)
- `GET /api/projects` - list saved projects
- `GET /api/projects/{project_id}` - fetch project state
- `GET /api/projects/{project_id}/original` - stream player-ready original media (source when browser-safe, compatibility proxy when required)
- `PUT /api/projects/{project_id}/state` - update edit state
- `POST /api/projects/{project_id}/preview` - render modified preview (single clip or multipart scene-split clips)
- `POST /api/projects/{project_id}/export` - render final export (single clip or multipart scene-split clips)
- `POST /api/projects/{project_id}/export/gif-estimate` - estimate GIF output size for the current edit state
- `POST /api/projects/{project_id}/export/gif` - render GIF export (single clip or multipart scene-split clips)

## Tests

```powershell
.\.venv\Scripts\python -m unittest discover -s tests -p "test_*.py" -v
```

## Notes

- Current trim scope is single kept A-B segment.
- Rendering is synchronous in the current implementation.

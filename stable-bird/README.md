# stable-bird

Python + AI + FFmpeg bird-video stabilizer. The pipeline detects the primary bird, keeps it near frame center with adaptive cropping, splits clips when quality drops, and writes stabilized outputs plus a manifest and debug preview.

## Model download

Download the default detector weights manually and place them at `models\yolov8s.pt`:

- https://github.com/ultralytics/assets/releases/download/v8.4.0/yolov8s.pt

## Setup

1. Create or reuse the local virtual environment:
   - `python -m venv .venv`
2. Install dependencies with the workspace venv:
   - `.\.venv\Scripts\python -m pip install -r requirements.txt`
3. Download the model file to:
   - `models\yolov8s.pt`

## Run

Supported public module entrypoints:

- `python -m stable_bird.cli` for console processing
- `python -m stable_bird.web` for the local web UI

Process every supported video in `samples\`:

```powershell
.\.venv\Scripts\python -m stable_bird.cli
```

Run the local web UI:

```powershell
.\.venv\Scripts\python -m stable_bird.web
```

Then open:

- http://127.0.0.1:5000

Use a JSON config file for repeated tuning:

```powershell
.\.venv\Scripts\python -m stable_bird.cli --config .\bird-config.json
```

Bias the tracking point upward within the detected bird box to reduce shake from tail or wing motion:

```powershell
.\.venv\Scripts\python -m stable_bird.cli --input .\samples\long_tailed_strike_sample.mp4 --tracking-anchor-y-percent 35
```

Process a single file:

```powershell
.\.venv\Scripts\python -m stable_bird.cli --input .\samples\long_tailed_strike_sample.mp4
```

Print the model download URL:

```powershell
.\.venv\Scripts\python -m stable_bird.cli --print-model-url
```

Outputs are written under `output\{video_stem}\`:

- `clips\segment_###.mp4` - accepted stabilized clips
- `manifest.json` - machine-readable segment and split metadata
- `{video_stem}_debug.mp4` - full-length debug preview with overlays
- `trace.log` - per-video tracing log
- `webui_project.json` - web UI metadata for uploaded projects

Run-level tracing is also written to:

- `logs\stable_bird.log`

## Notes

- Default detection uses Ultralytics YOLOv8s with the COCO `bird` class.
- If no valid bird is detected, the bird is too blurry, or the bird leaves the allowed center zone for more than the configured grace frames, the current clip is split.
- GPU is used when available if you pass `--device cuda`, but CPU execution also works.
- Default detection resizes frames to 640 pixels for inference to keep CPU runs practical.
- Default clip export requires at least 10 tracked frames per accepted segment. If a video finishes with no clips, try lowering `--min-segment-frames` or relaxing the detection thresholds.
- The final clips are cropped directly from the original source video, and `crop_margin_percent` lets you shrink the solved crop slightly to avoid edge artifacts or black borders.
- `tracking_anchor_x_percent` / `tracking_anchor_y_percent` let you move the tracked point within the detected bird box; `50/50` keeps the current midpoint behavior, while a lower Y value can bias tracking toward the head or neck.
- The web UI is local-first, processes one uploaded job at a time, and lets you reopen previously processed projects from the output directory.
- The web debug view uses generated artifacts: source video, debug preview, `manifest.json`, and `trace.log`.

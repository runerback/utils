# bpm_finder

Python CLI for estimating likely BPM values from a `.wav` file. If the input is a video file, the tool first extracts a temporary wav file through `ffmpeg`.

## Setup

```powershell
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

`ffmpeg` must be installed and available on `PATH` for video inputs.

## Usage

```powershell
.\.venv\Scripts\python.exe -m bpm_finder <path-to-media>
```

Example output:

```json
{
  "input_path": "demo.wav",
  "source_type": "wav",
  "candidates": [
    {
      "bpm": 120.0,
      "confidence": 1.0
    },
    {
      "bpm": 60.0,
      "confidence": 0.77
    }
  ]
}
```

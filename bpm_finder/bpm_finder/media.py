from __future__ import annotations

from contextlib import contextmanager
from pathlib import Path
import shutil
import subprocess
import tempfile
from collections.abc import Iterator


VIDEO_EXTENSIONS = {
    ".avi",
    ".m4v",
    ".mkv",
    ".mov",
    ".mp4",
    ".mpeg",
    ".mpg",
    ".webm",
}


class MediaError(RuntimeError):
    """Raised when an input cannot be prepared for BPM analysis."""


def detect_source_type(path: Path) -> str:
    suffix = path.suffix.lower()
    if suffix == ".wav":
        return "wav"
    if suffix in VIDEO_EXTENSIONS:
        return "video"
    raise MediaError(
        f"Unsupported input type '{path.suffix or '<none>'}'. "
        "Supported inputs are .wav and common video files."
    )


@contextmanager
def prepared_wav_path(input_path: str | Path) -> Iterator[tuple[Path, str]]:
    source_path = Path(input_path).expanduser().resolve()
    if not source_path.exists():
        raise MediaError(f"Input file does not exist: {source_path}")

    source_type = detect_source_type(source_path)
    if source_type == "wav":
        yield source_path, source_type
        return

    ffmpeg_path = shutil.which("ffmpeg")
    if ffmpeg_path is None:
        raise MediaError("ffmpeg is required for video inputs but was not found on PATH.")

    with tempfile.NamedTemporaryFile(
        prefix="bpm_finder_",
        suffix=".wav",
        delete=False,
    ) as temp_file:
        temp_wav_path = Path(temp_file.name)

    command = [
        ffmpeg_path,
        "-y",
        "-i",
        str(source_path),
        "-vn",
        "-ac",
        "1",
        "-ar",
        "22050",
        str(temp_wav_path),
    ]

    try:
        completed = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
        )
        if completed.returncode != 0:
            stderr = completed.stderr.strip() or "ffmpeg failed without stderr output."
            raise MediaError(f"ffmpeg failed to extract wav audio: {stderr}")
        yield temp_wav_path, source_type
    finally:
        temp_wav_path.unlink(missing_ok=True)

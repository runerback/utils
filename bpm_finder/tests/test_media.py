from __future__ import annotations

from pathlib import Path
import subprocess

import pytest

from bpm_finder.media import MediaError, detect_source_type, prepared_wav_path


def test_detect_source_type_accepts_wav_and_video() -> None:
    assert detect_source_type(Path("demo.wav")) == "wav"
    assert detect_source_type(Path("demo.mp4")) == "video"


def test_detect_source_type_rejects_other_extensions() -> None:
    with pytest.raises(MediaError, match="Unsupported input type"):
        detect_source_type(Path("demo.mp3"))


def test_prepared_wav_path_passes_through_wav(tmp_path: Path) -> None:
    wav_path = tmp_path / "sample.wav"
    wav_path.write_bytes(b"RIFFdata")

    with prepared_wav_path(wav_path) as (prepared_path, source_type):
        assert prepared_path == wav_path.resolve()
        assert source_type == "wav"


def test_prepared_wav_path_uses_ffmpeg_for_video(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    video_path = tmp_path / "sample.mp4"
    video_path.write_bytes(b"video")
    called: dict[str, object] = {}

    def fake_run(command: list[str], check: bool, capture_output: bool, text: bool) -> subprocess.CompletedProcess[str]:
        called["command"] = command
        Path(command[-1]).write_bytes(b"RIFF")
        return subprocess.CompletedProcess(command, 0, "", "")

    monkeypatch.setattr("bpm_finder.media.shutil.which", lambda name: "ffmpeg.exe")
    monkeypatch.setattr("bpm_finder.media.subprocess.run", fake_run)

    prepared_path: Path
    with prepared_wav_path(video_path) as (wav_path, source_type):
        prepared_path = wav_path
        assert source_type == "video"
        assert prepared_path.exists()

    assert called["command"][0] == "ffmpeg.exe"
    assert called["command"][3] == str(video_path.resolve())
    assert not prepared_path.exists()


def test_prepared_wav_path_errors_when_ffmpeg_is_missing(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    video_path = tmp_path / "sample.mov"
    video_path.write_bytes(b"video")
    monkeypatch.setattr("bpm_finder.media.shutil.which", lambda name: None)

    with pytest.raises(MediaError, match="ffmpeg is required"):
        with prepared_wav_path(video_path):
            pass

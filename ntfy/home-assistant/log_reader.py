import shutil
import subprocess
from pathlib import Path
from typing import Optional


class LogReaderError(Exception):
    pass


def _resolve_path(path: str) -> Path:
    resolved = Path(path).resolve()
    return resolved


def _is_within_allowed(path: Path, allowed_base: Path) -> bool:
    try:
        path.resolve().relative_to(allowed_base.resolve())
        return True
    except ValueError:
        return False


def read_log_file(
    path: str,
    lines: int = 500,
    allowed_base: Optional[str] = None,
) -> list[str]:
    resolved = _resolve_path(path)
    if allowed_base and not _is_within_allowed(resolved, Path(allowed_base)):
        raise LogReaderError("Log path is outside the allowed directory")

    if not resolved.exists():
        return []

    try:
        text = resolved.read_text(encoding="utf-8", errors="replace")
    except OSError as exc:
        raise LogReaderError(f"Cannot read log file: {exc}") from exc

    all_lines = text.splitlines()
    return all_lines[-lines:] if len(all_lines) > lines else all_lines


def clear_log_file(path: str, allowed_base: Optional[str] = None) -> None:
    resolved = _resolve_path(path)
    if allowed_base and not _is_within_allowed(resolved, Path(allowed_base)):
        raise LogReaderError("Log path is outside the allowed directory")

    try:
        resolved.write_text("", encoding="utf-8")
    except OSError as exc:
        raise LogReaderError(f"Cannot clear log file: {exc}") from exc


def read_journalctl(unit: str, lines: int = 500) -> list[str]:
    if not shutil.which("journalctl"):
        raise LogReaderError("journalctl is not available")

    cmd = ["journalctl", "-u", unit, "-n", str(lines), "--no-pager"]
    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            check=True,
            timeout=30,
        )
    except subprocess.CalledProcessError as exc:
        raise LogReaderError(f"journalctl failed: {exc.stderr}") from exc
    except subprocess.TimeoutExpired as exc:
        raise LogReaderError("journalctl timed out") from exc

    return result.stdout.splitlines()

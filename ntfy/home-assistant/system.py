from pathlib import Path
from typing import Optional


def cpu_temp_celsius() -> Optional[float]:
    """Read CPU temperature from Linux thermal zones. Returns Celsius or None."""
    thermal_path = Path("/sys/class/thermal")
    if not thermal_path.exists():
        return None
    for zone in sorted(thermal_path.glob("thermal_zone*/temp")):
        try:
            raw = zone.read_text().strip()
            return int(raw) / 1000.0
        except (ValueError, OSError):
            continue
    return None


def _parse_meminfo_value(text: str, key: str) -> Optional[int]:
    for line in text.splitlines():
        if line.startswith(key + ":"):
            parts = line.split()
            if len(parts) >= 2:
                try:
                    return int(parts[1])
                except ValueError:
                    return None
    return None


def memory_usage() -> dict[str, Optional[int]]:
    """Read memory usage from /proc/meminfo. Returns total/used KB and percent."""
    meminfo_path = Path("/proc/meminfo")
    if not meminfo_path.exists():
        return {"total_kb": None, "used_kb": None, "percent": None}

    try:
        text = meminfo_path.read_text()
    except OSError:
        return {"total_kb": None, "used_kb": None, "percent": None}

    total = _parse_meminfo_value(text, "MemTotal")
    available = _parse_meminfo_value(text, "MemAvailable")

    if total is None or available is None:
        return {"total_kb": total, "used_kb": None, "percent": None}

    used = total - available
    percent = round(used / total * 100, 1) if total else 0.0
    return {"total_kb": total, "used_kb": used, "percent": percent}

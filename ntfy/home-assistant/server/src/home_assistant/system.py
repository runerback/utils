from pathlib import Path
import subprocess
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


def bluetooth_status() -> dict:
    """BlueZ adapter state via bluetoothctl. Never raises."""
    try:
        result = subprocess.run(
            ["bluetoothctl", "show"],
            capture_output=True, text=True, timeout=5,
        )
    except (OSError, subprocess.TimeoutExpired):
        return {"available": False, "powered": False, "adapter": None}
    output = result.stdout or ""
    if "Controller" not in output:
        return {"available": False, "powered": False, "adapter": None}
    powered = "Powered: yes" in output
    adapter = None
    for line in output.splitlines():
        line = line.strip()
        if line.startswith("Name:"):
            adapter = line.split(":", 1)[1].strip() or None
            break
    return {"available": True, "powered": powered, "adapter": adapter}


def set_bluetooth_power(on: bool) -> dict:
    """Power the BlueZ adapter on/off. Returns fresh status; raises RuntimeError."""
    def _power() -> subprocess.CompletedProcess:
        return subprocess.run(
            ["bluetoothctl", "power", "on" if on else "off"],
            capture_output=True, text=True, timeout=10,
        )

    try:
        result = _power()
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise RuntimeError(f"failed to run bluetoothctl: {exc}") from exc
    output = f"{result.stdout}\n{result.stderr}".strip()
    if on and "org.bluez.Error.Blocked" in output:
        # soft-blocked via rfkill; unblock once and retry
        subprocess.run(
            ["rfkill", "unblock", "bluetooth"],
            capture_output=True, text=True, timeout=10,
        )
        result = _power()
        output = f"{result.stdout}\n{result.stderr}".strip()
    if result.returncode != 0 or "Failed" in output:
        raise RuntimeError(output or f"bluetoothctl exited with {result.returncode}")
    return bluetooth_status()

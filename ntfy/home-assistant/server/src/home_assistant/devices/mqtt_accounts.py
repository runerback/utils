"""Create and delete mosquitto users via mosquitto_ctrl (Dynamic Security Plugin).

The command used (default 'mosquitto_ctrl') is expected to be a shell alias on the
server that already includes host/port/cafile/credentials. Because aliases are
resolved by the shell, the subprocess runs through the configured login shell.
"""
import shlex
import subprocess

from .. import config


def _run_ctrl(*args: str) -> subprocess.CompletedProcess:
    args_quoted = " ".join(shlex.quote(a) for a in args)
    cmd = f"{config.MOSQUITTO_CTRL_BIN} dynsec {args_quoted}"
    proc = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        check=False,
        shell=True,
    )
    return proc


def create_mqtt_user(username: str, password: str) -> None:
    """Create a Dynamic Security Plugin client with the given password."""
    proc = _run_ctrl("createClient", username, "-p", password)
    if proc.returncode != 0:
        # Idempotent: ignore "client already exists" errors if possible.
        err = proc.stderr.strip() or proc.stdout.strip()
        if "already exists" in err.lower():
            return
        raise RuntimeError(f"mosquitto_ctrl createClient failed: {err}")


def delete_mqtt_user(username: str) -> None:
    """Delete a Dynamic Security Plugin client."""
    proc = _run_ctrl("deleteClient", username)
    if proc.returncode != 0:
        err = proc.stderr.strip() or proc.stdout.strip()
        if "not found" in err.lower():
            return
        raise RuntimeError(f"mosquitto_ctrl deleteClient failed: {err}")

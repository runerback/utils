"""Create and delete mosquitto users via mosquitto_ctrl (Dynamic Security Plugin).

The command used (default 'mosquitto_ctrl') is expected to be a shell alias on the
server that already includes host/port/cafile/credentials. Aliases are only
expanded by interactive shells, so commands run through `bash -ic`.

Each device client gets its own role restricting it to its own topics:
  publish:   devices/<id>/status, devices/<id>/ack
  subscribe: devices/<id>/cmd
"""
import shlex
import subprocess

from .. import config


def _run_ctrl(*args: str) -> subprocess.CompletedProcess:
    args_quoted = " ".join(shlex.quote(a) for a in args)
    cmd = f"{config.MOSQUITTO_CTRL_BIN} dynsec {args_quoted}"
    proc = subprocess.run(
        ["bash", "-ic", cmd],
        capture_output=True,
        text=True,
        check=False,
    )
    return proc


def _err(proc: subprocess.CompletedProcess) -> str:
    return proc.stderr.strip() or proc.stdout.strip()


def _role_name(username: str) -> str:
    return f"role-{username}"


def create_mqtt_user(username: str, password: str) -> None:
    """Create a Dynamic Security Plugin client with the given password.

    Idempotent: if the client already exists (e.g. a device re-claiming after
    losing the first claim response), its password is updated instead.
    """
    proc = _run_ctrl("createClient", username, "-p", password)
    if proc.returncode != 0:
        if "already exists" in _err(proc).lower():
            proc = _run_ctrl("setClientPassword", username, password)
        if proc.returncode != 0:
            raise RuntimeError(f"mosquitto_ctrl createClient failed: {_err(proc)}")

    role = _role_name(username)
    proc = _run_ctrl("createRole", role)
    if proc.returncode != 0 and "already exists" not in _err(proc).lower():
        raise RuntimeError(f"mosquitto_ctrl createRole failed: {_err(proc)}")

    for acltype, topic in (
        ("publishClientSend", f"devices/{username}/status"),
        ("publishClientSend", f"devices/{username}/ack"),
        ("subscribeLiteral", f"devices/{username}/cmd"),
    ):
        proc = _run_ctrl("addRoleACL", role, acltype, topic, "10", "allow")
        if proc.returncode != 0 and "already" not in _err(proc).lower():
            raise RuntimeError(f"mosquitto_ctrl addRoleACL failed: {_err(proc)}")

    proc = _run_ctrl("modifyClient", username, "-r", role)
    if proc.returncode != 0:
        raise RuntimeError(f"mosquitto_ctrl modifyClient failed: {_err(proc)}")


def delete_mqtt_user(username: str) -> None:
    """Delete a Dynamic Security Plugin client and its role."""
    proc = _run_ctrl("deleteClient", username)
    if proc.returncode != 0 and "not found" not in _err(proc).lower():
        raise RuntimeError(f"mosquitto_ctrl deleteClient failed: {_err(proc)}")
    # Role cleanup is best-effort.
    _run_ctrl("deleteRole", _role_name(username))

"""SQLite-backed device registry.

Adapted from devices/server/db.py.

State machine:
  pending_claim  claim_key generated; QR code shown, waiting for device
  active         device has claimed and published its status
  failed         provisioning failed
"""
import sqlite3
import time
from contextlib import contextmanager
from pathlib import Path
from typing import Optional

_DB_PATH: Path = Path("devices.db")


def set_db_path(path: str | Path) -> None:
    global _DB_PATH
    _DB_PATH = Path(path)


@contextmanager
def _connect():
    _DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(_DB_PATH), check_same_thread=False)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


_SCHEMA = """
CREATE TABLE IF NOT EXISTS devices (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id     TEXT UNIQUE NOT NULL,
    token_hash    TEXT NOT NULL,
    token_salt    TEXT NOT NULL,
    token_plain   TEXT,
    claim_hash    TEXT NOT NULL,
    claim_salt    TEXT NOT NULL,
    mqtt_username TEXT,
    mqtt_password TEXT,
    ble_mac       TEXT,
    status        TEXT NOT NULL DEFAULT 'pending_claim',
    created_at    REAL NOT NULL,
    claimed_at    REAL
);
"""


def init_db() -> None:
    with _connect() as conn:
        conn.executescript(_SCHEMA)


def add_device(
    device_id: str,
    token: str,
    token_salt: str,
    token_hash: str,
    claim_key: str,
    claim_salt: str,
    claim_hash: str,
    ble_mac: Optional[str] = None,
) -> str:
    with _connect() as conn:
        conn.execute(
            """INSERT INTO devices
               (device_id, token_hash, token_salt, token_plain,
                claim_hash, claim_salt, mqtt_username, ble_mac, status, created_at)
               VALUES (?,?,?,?,?,?,?,?, 'pending_claim', ?)""",
            (device_id, token_hash, token_salt, token,
             claim_hash, claim_salt, device_id, ble_mac, time.time()),
        )
    return claim_key


def set_status(device_id: str, status: str) -> None:
    with _connect() as conn:
        conn.execute(
            "UPDATE devices SET status=? WHERE device_id=?",
            (status, device_id),
        )


def set_mqtt_password(device_id: str, mqtt_password: str) -> None:
    with _connect() as conn:
        conn.execute(
            "UPDATE devices SET mqtt_password=? WHERE device_id=?",
            (mqtt_password, device_id),
        )


def get_device_by_id(device_id: str) -> Optional[dict]:
    with _connect() as conn:
        row = conn.execute(
            "SELECT * FROM devices WHERE device_id=?",
            (device_id,),
        ).fetchone()
        return dict(row) if row else None


def get_device_by_claim_hash(claim_hash: str) -> Optional[dict]:
    with _connect() as conn:
        row = conn.execute(
            "SELECT * FROM devices WHERE claim_hash=?",
            (claim_hash,),
        ).fetchone()
        return dict(row) if row else None


def mark_claimed(device_id: str) -> None:
    with _connect() as conn:
        conn.execute(
            """UPDATE devices SET status='active',
               token_plain=NULL, mqtt_password=NULL, claimed_at=?
               WHERE device_id=?""",
            (time.time(), device_id),
        )


def mark_failed(device_id: str) -> None:
    set_status(device_id, "failed")


def remove_device(device_id: str) -> None:
    with _connect() as conn:
        conn.execute("DELETE FROM devices WHERE device_id=?", (device_id,))


def list_devices() -> list[dict]:
    with _connect() as conn:
        rows = conn.execute(
            "SELECT device_id, ble_mac, status, created_at, claimed_at "
            "FROM devices ORDER BY created_at DESC"
        ).fetchall()
        return [dict(r) for r in rows]

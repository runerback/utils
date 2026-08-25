import sqlite3
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Optional

import config


def _db_path() -> Path:
    path = Path(config.DATABASE_PATH)
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def get_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(_db_path(), check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init_db() -> None:
    with get_connection() as conn:
        conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS topics (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                created_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                topic_id INTEGER NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
                sender TEXT,
                body TEXT NOT NULL,
                sent_at TEXT NOT NULL,
                is_outgoing INTEGER NOT NULL DEFAULT 0
            );

            CREATE INDEX IF NOT EXISTS idx_messages_topic_id ON messages(topic_id);
            CREATE INDEX IF NOT EXISTS idx_messages_sent_at ON messages(sent_at);
            """
        )


# Settings


def get_setting(key: str, default: Optional[str] = None) -> Optional[str]:
    with get_connection() as conn:
        row = conn.execute(
            "SELECT value FROM settings WHERE key = ?", (key,)
        ).fetchone()
    return row["value"] if row else default


def set_setting(key: str, value: str) -> None:
    with get_connection() as conn:
        conn.execute(
            "INSERT INTO settings(key, value) VALUES (?, ?) "
            "ON CONFLICT(key) DO UPDATE SET value=excluded.value",
            (key, value),
        )


def get_settings_dict(prefix: str = "") -> dict[str, Optional[str]]:
    with get_connection() as conn:
        if prefix:
            rows = conn.execute(
                "SELECT key, value FROM settings WHERE key LIKE ?",
                (f"{prefix}%",),
            ).fetchall()
        else:
            rows = conn.execute("SELECT key, value FROM settings").fetchall()
    return {row["key"]: row["value"] for row in rows}


# Topics


def add_topic(name: str) -> int:
    with get_connection() as conn:
        try:
            cur = conn.execute(
                "INSERT INTO topics(name, created_at) VALUES (?, ?)",
                (name, _now()),
            )
            return int(cur.lastrowid)
        except sqlite3.IntegrityError as exc:
            raise ValueError(f"Topic '{name}' already exists") from exc


def delete_topic(topic_id: int) -> None:
    with get_connection() as conn:
        conn.execute("DELETE FROM topics WHERE id = ?", (topic_id,))


def get_topic(topic_id: int) -> Optional[sqlite3.Row]:
    with get_connection() as conn:
        return conn.execute(
            "SELECT * FROM topics WHERE id = ?", (topic_id,)
        ).fetchone()


def get_topic_by_name(name: str) -> Optional[sqlite3.Row]:
    with get_connection() as conn:
        return conn.execute(
            "SELECT * FROM topics WHERE name = ?", (name,)
        ).fetchone()


def list_topics() -> list[sqlite3.Row]:
    with get_connection() as conn:
        rows = conn.execute(
            """
            SELECT t.*, m.body AS latest_body, m.sent_at AS latest_sent_at
            FROM topics t
            LEFT JOIN (
                SELECT topic_id, body, sent_at
                FROM messages
                WHERE id IN (
                    SELECT MAX(id)
                    FROM messages
                    GROUP BY topic_id
                )
            ) m ON m.topic_id = t.id
            ORDER BY t.created_at DESC
            """
        ).fetchall()
    return rows


# Messages


def add_message(
    topic_id: int,
    body: str,
    sender: Optional[str] = None,
    is_outgoing: bool = False,
    sent_at: Optional[str] = None,
) -> int:
    if sent_at is None:
        sent_at = _now()
    with get_connection() as conn:
        cur = conn.execute(
            """
            INSERT INTO messages(topic_id, sender, body, sent_at, is_outgoing)
            VALUES (?, ?, ?, ?, ?)
            """,
            (topic_id, sender, body, sent_at, 1 if is_outgoing else 0),
        )
        message_id = int(cur.lastrowid)
        _prune_messages(conn, topic_id)
    return message_id


def find_recent_duplicate(
    topic_id: int,
    body: str,
    sender: Optional[str],
    is_outgoing: bool,
    window_seconds: int = 30,
) -> Optional[sqlite3.Row]:
    """Find a recent message with the same body/sender to avoid echo duplicates."""
    since = (
        datetime.now(timezone.utc) - timedelta(seconds=window_seconds)
    ).isoformat()
    with get_connection() as conn:
        return conn.execute(
            """
            SELECT * FROM messages
            WHERE topic_id = ? AND body = ? AND sender IS ? AND is_outgoing = ?
              AND sent_at > ?
            ORDER BY sent_at DESC
            LIMIT 1
            """,
            (topic_id, body, sender, 1 if is_outgoing else 0, since),
        ).fetchone()


def _prune_messages(conn: sqlite3.Connection, topic_id: int, limit: int = 500) -> None:
    conn.execute(
        """
        DELETE FROM messages
        WHERE topic_id = ?
          AND id NOT IN (
              SELECT id FROM messages WHERE topic_id = ? ORDER BY sent_at DESC LIMIT ?
          )
        """,
        (topic_id, topic_id, limit),
    )


def list_messages(topic_id: int, limit: int = 200) -> list[sqlite3.Row]:
    with get_connection() as conn:
        rows = conn.execute(
            """
            SELECT * FROM messages
            WHERE topic_id = ?
            ORDER BY sent_at DESC
            LIMIT ?
            """,
            (topic_id, limit),
        ).fetchall()
    return list(reversed(rows))


def row_to_dict(row: sqlite3.Row) -> dict[str, Any]:
    return {key: row[key] for key in row.keys()}

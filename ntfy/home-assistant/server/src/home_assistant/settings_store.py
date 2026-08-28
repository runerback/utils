from typing import Optional

from . import config, db


def _key(name: str) -> str:
    return f"settings.{name}"


def get_messages_server_url() -> str:
    value = (db.get_setting(_key("messages.server_url"), config.NTFY_BASE_URL) or config.NTFY_BASE_URL).strip()
    return value.rstrip("/")


def set_messages_server_url(value: str) -> None:
    db.set_setting(_key("messages.server_url"), value.strip().rstrip("/"))


def get_messages_token() -> Optional[str]:
    value = db.get_setting(_key("messages.token"))
    return value.strip() if value else None


def set_messages_token(value: Optional[str]) -> None:
    db.set_setting(_key("messages.token"), (value or "").strip())


def get_logs_path() -> str:
    return db.get_setting(_key("logs.path"), config.LOG_PATH) or config.LOG_PATH


def set_logs_path(value: str) -> None:
    db.set_setting(_key("logs.path"), value)


def get_logs_use_journal() -> bool:
    return (db.get_setting(_key("logs.use_journal"), "false") or "false").lower() == "true"


def set_logs_use_journal(value: bool) -> None:
    db.set_setting(_key("logs.use_journal"), "true" if value else "false")


def as_dict() -> dict[str, str]:
    """Return all settings as a flat dict for the frontend."""
    raw = db.get_settings_dict("settings.")
    return {
        "messages.server_url": get_messages_server_url(),
        "messages.token": get_messages_token() or "",
        "logs.path": get_logs_path(),
        "logs.use_journal": db.get_setting(_key("logs.use_journal"), "false") or "false",
    }


def update_from_form(data: dict[str, str]) -> None:
    """Update settings from a submitted form dict."""
    if "messages.server_url" in data:
        set_messages_server_url(data["messages.server_url"])
    if "messages.token" in data:
        set_messages_token(data["messages.token"] or None)
    if "logs.path" in data:
        set_logs_path(data["logs.path"])
    if "logs.use_journal" in data:
        set_logs_use_journal(data["logs.use_journal"].lower() in ("true", "1", "on"))
    else:
        set_logs_use_journal(False)

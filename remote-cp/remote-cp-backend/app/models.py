from threading import Lock
from typing import Any


class MessageStore:
    def __init__(self) -> None:
        self._messages: list[dict[str, Any]] = []
        self._lock = Lock()

    def get_all(self) -> list[dict[str, Any]]:
        with self._lock:
            return list(self._messages)

    def append(self, message: dict[str, Any]) -> None:
        with self._lock:
            self._messages.append(message)


message_store = MessageStore()

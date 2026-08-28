import json
import logging
import queue
import threading
from dataclasses import dataclass
from typing import Callable, Optional

import requests

logger = logging.getLogger(__name__)


@dataclass
class NtfyMessage:
    topic: str
    title: Optional[str]
    message: str
    priority: Optional[int]
    time: Optional[int]
    tags: Optional[list[str]]

    @classmethod
    def from_json(cls, topic: str, data: dict) -> "NtfyMessage":
        return cls(
            topic=topic,
            title=data.get("title"),
            message=data.get("message", ""),
            priority=data.get("priority"),
            time=data.get("time"),
            tags=data.get("tags"),
        )


def send_message(server_url: str, topic: str, body: str, token: Optional[str] = None) -> bool:
    url = f"{server_url}/{topic}"
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    try:
        resp = requests.post(url, data=body.encode("utf-8"), headers=headers, timeout=30)
        resp.raise_for_status()
        return True
    except requests.RequestException as exc:
        logger.warning("Failed to send message to %s: %s", url, exc)
        return False


def _stream_topic(
    server_url: str,
    topic: str,
    token: Optional[str],
    incoming_queue: queue.Queue,
    stop_event: threading.Event,
    status_callback: Callable[[str, str], None],
) -> None:
    url = f"{server_url}/{topic}/json"
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"

    logger.info("Subscribing to ntfy topic %s (auth=%s)", topic, bool(token))

    while not stop_event.is_set():
        try:
            with requests.get(url, headers=headers, stream=True, timeout=60) as resp:
                if resp.status_code == 403:
                    logger.info(
                        "Topic %s is write-only (403); stopping subscription", topic
                    )
                    status_callback(topic, "write_only")
                    break
                resp.raise_for_status()
                status_callback(topic, "connected")
                logger.info("Connected to ntfy topic %s", topic)
                for line in resp.iter_lines(decode_unicode=True):
                    if stop_event.is_set():
                        break
                    if not line:
                        continue
                    try:
                        data = json.loads(line)
                    except json.JSONDecodeError:
                        continue
                    msg = NtfyMessage.from_json(topic, data)
                    incoming_queue.put(msg)
        except requests.RequestException as exc:
            logger.warning("Ntfy stream error for %s: %s", topic, exc)
        if stop_event.wait(5):
            break

    status_callback(topic, "stopped")


class NtfySubscriber:
    def __init__(self) -> None:
        self._queue: queue.Queue[NtfyMessage] = queue.Queue()
        self._threads: dict[str, threading.Thread] = {}
        self._stop_events: dict[str, threading.Event] = {}
        self._status: dict[str, str] = {}
        self._lock = threading.Lock()

    def _set_status(self, topic: str, status: str) -> None:
        with self._lock:
            self._status[topic] = status

    def subscribe(self, server_url: str, topic: str, token: Optional[str]) -> None:
        with self._lock:
            if topic in self._threads:
                return
            self._status[topic] = "connecting"
            stop_event = threading.Event()
            thread = threading.Thread(
                target=_stream_topic,
                args=(server_url, topic, token, self._queue, stop_event, self._set_status),
                daemon=True,
            )
            self._threads[topic] = thread
            self._stop_events[topic] = stop_event
            thread.start()

    def unsubscribe(self, topic: str) -> None:
        with self._lock:
            stop_event = self._stop_events.pop(topic, None)
            thread = self._threads.pop(topic, None)
            self._status.pop(topic, None)
        if stop_event:
            stop_event.set()
        if thread:
            thread.join(timeout=2)

    def unsubscribe_all(self) -> None:
        topics = list(self._threads.keys())
        for topic in topics:
            self.unsubscribe(topic)

    def get_status(self, topic: str) -> Optional[str]:
        with self._lock:
            return self._status.get(topic)

    def get_message(self, timeout: Optional[float] = None) -> Optional[NtfyMessage]:
        try:
            return self._queue.get(timeout=timeout)
        except queue.Empty:
            return None

    def messages(self, callback: Callable[[NtfyMessage], None]) -> None:
        while True:
            msg = self.get_message(timeout=1.0)
            if msg is None:
                continue
            callback(msg)

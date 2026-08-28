import json
import logging
import queue
import threading
from datetime import datetime, timezone
from typing import Any, Optional

from . import db, ntfy_client, settings_store

logger = logging.getLogger(__name__)

subscriber = ntfy_client.NtfySubscriber()
_sse_clients: list[queue.Queue] = []
_sse_lock = threading.Lock()


def broadcast(message: dict[str, Any]) -> None:
    data = f"data: {json.dumps(message)}\n\n"
    with _sse_lock:
        dead: list[queue.Queue] = []
        clients = list(_sse_clients)
        for q in clients:
            try:
                q.put(data, block=False)
            except queue.Full:
                dead.append(q)
        for q in dead:
            try:
                _sse_clients.remove(q)
            except ValueError:
                pass


def subscribe_all_topics() -> None:
    server_url = settings_store.get_messages_server_url()
    token = settings_store.get_messages_token()
    for topic in db.list_topics():
        subscriber.subscribe(server_url, topic["name"], token)


def _ntfy_consumer() -> None:
    while True:
        try:
            msg = subscriber.get_message(timeout=1.0)
            if msg is None:
                continue

            topic_row = db.get_topic_by_name(msg.topic)
            if topic_row is None:
                continue

            sender = msg.title or None

            if db.find_recent_duplicate(
                topic_row["id"], msg.message, sender, is_outgoing=True
            ):
                continue

            sent_at: Optional[str] = None
            if msg.time:
                try:
                    sent_at = datetime.fromtimestamp(int(msg.time), tz=timezone.utc).isoformat()
                except (ValueError, OSError):
                    sent_at = None
            if not sent_at:
                sent_at = datetime.now(timezone.utc).isoformat()

            try:
                db.add_message(
                    topic_id=topic_row["id"],
                    body=msg.message,
                    sender=sender,
                    is_outgoing=False,
                    sent_at=sent_at,
                )
            except Exception as exc:
                logger.warning("Failed to store incoming message: %s", exc)

            broadcast(
                {
                    "type": "message",
                    "topic": msg.topic,
                    "sender": sender,
                    "body": msg.message,
                    "sent_at": sent_at,
                }
            )
        except Exception as exc:
            logger.exception("Unexpected error in ntfy consumer: %s", exc)


def start_consumer() -> None:
    threading.Thread(target=_ntfy_consumer, daemon=True).start()

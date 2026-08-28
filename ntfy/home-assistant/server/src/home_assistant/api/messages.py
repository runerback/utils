import queue
from datetime import datetime, timezone
from typing import Annotated, Any

import requests
from fastapi import APIRouter, Form, HTTPException, Request
from fastapi.responses import StreamingResponse

from .. import auth, db, events, settings_store

router = APIRouter()


def _message_to_dict(row: db.sqlite3.Row, current_user: str) -> dict[str, Any]:
    return {
        "id": row["id"],
        "topic_id": row["topic_id"],
        "sender": row["sender"],
        "body": row["body"],
        "sent_at": row["sent_at"],
        "is_mine": row["sender"] == current_user,
    }


@router.get("/topics/{topic_id}/messages")
def list_messages(current_user: auth.CurrentUser, topic_id: int):
    if db.get_topic(topic_id) is None:
        raise HTTPException(status_code=404, detail="Topic not found")
    rows = db.list_messages(topic_id)
    return [_message_to_dict(row, current_user) for row in rows]


@router.post("/topics/{topic_id}/messages", status_code=201)
def send_message(
    current_user: auth.CurrentUser,
    csrf: auth.CsrfRequired,
    topic_id: int,
    body: Annotated[str, Form()],
):
    topic = db.get_topic(topic_id)
    if topic is None:
        raise HTTPException(status_code=404, detail="Topic not found")

    body = body.strip()
    if not body:
        raise HTTPException(status_code=400, detail="Message body is required")

    server_url = settings_store.get_messages_server_url()
    token = settings_store.get_messages_token()
    sent_at = datetime.now(timezone.utc).isoformat()

    try:
        db.add_message(
            topic_id=topic_id,
            body=body,
            sender=current_user,
            is_outgoing=True,
            sent_at=sent_at,
        )
    except Exception as exc:
        import logging

        logging.getLogger(__name__).warning("Failed to store outgoing message: %s", exc)

    events.broadcast(
        {
            "type": "message",
            "topic": topic["name"],
            "sender": current_user,
            "body": body,
            "sent_at": sent_at,
        }
    )

    headers = {"X-Title": current_user}
    if token:
        headers["Authorization"] = f"Bearer {token}"

    try:
        resp = requests.post(
            f"{server_url}/{topic['name']}",
            data=body.encode("utf-8"),
            headers=headers,
            timeout=30,
        )
        resp.raise_for_status()
    except requests.RequestException as exc:
        import logging

        logging.getLogger(__name__).warning("Failed to send message to ntfy: %s", exc)

    return {"ok": True}


@router.get("/messages/stream")
def message_stream(request: Request, current_user: auth.CurrentUser):
    q: queue.Queue[str] = queue.Queue(maxsize=100)
    with events._sse_lock:
        events._sse_clients.append(q)

    def event_stream():
        try:
            while True:
                try:
                    data = q.get(timeout=30)
                except queue.Empty:
                    yield ": ping\n\n"
                    continue
                yield data
        finally:
            with events._sse_lock:
                if q in events._sse_clients:
                    events._sse_clients.remove(q)

    return StreamingResponse(event_stream(), media_type="text/event-stream")

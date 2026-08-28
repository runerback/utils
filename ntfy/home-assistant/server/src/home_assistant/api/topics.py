from fastapi import APIRouter, Form, HTTPException
from typing import Annotated

from .. import auth, db, events

router = APIRouter()


@router.get("/topics")
def list_topics(current_user: auth.CurrentUser):
    rows = db.list_topics()
    return [
        {
            "id": row["id"],
            "name": row["name"],
            "latest_body": row["latest_body"],
            "latest_sent_at": row["latest_sent_at"],
            "status": events.subscriber.get_status(row["name"]) or "unknown",
        }
        for row in rows
    ]


@router.post("/topics", status_code=201)
def add_topic(
    current_user: auth.CurrentUser,
    csrf: auth.CsrfRequired,
    name: Annotated[str, Form()],
):
    name = name.strip()
    if not name:
        raise HTTPException(status_code=400, detail="Topic name is required")
    try:
        topic_id = db.add_topic(name)
    except ValueError as exc:
        raise HTTPException(status_code=409, detail=str(exc))

    from .. import settings_store

    server_url = settings_store.get_messages_server_url()
    token = settings_store.get_messages_token()
    events.subscriber.subscribe(server_url, name, token)
    return {"id": topic_id, "name": name}


@router.delete("/topics/{topic_id}")
def delete_topic(
    current_user: auth.CurrentUser,
    csrf: auth.CsrfRequired,
    topic_id: int,
):
    topic = db.get_topic(topic_id)
    if topic is None:
        raise HTTPException(status_code=404, detail="Topic not found")
    events.subscriber.unsubscribe(topic["name"])
    db.delete_topic(topic_id)
    return {"ok": True}

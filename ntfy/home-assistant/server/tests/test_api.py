import os
from pathlib import Path

os.environ.setdefault("HOME_ASSISTANT_HOST", "127.0.0.1")
os.environ.setdefault("HOME_ASSISTANT_PORT", "8000")
os.environ.setdefault("SECRET_KEY", "test-secret-key")
os.environ.setdefault("NTFY_BASE_URL", "http://localhost")
os.environ.setdefault("DATABASE_PATH", str(Path(__file__).parent / "test.db"))
os.environ.setdefault("LOG_PATH", str(Path(__file__).parent / "test.log"))
os.environ.setdefault("DEV_AUTH_BYPASS", "1")

import pytest
from fastapi.testclient import TestClient

from home_assistant import db, main


@pytest.fixture(autouse=True)
def clean_db():
    db.init_db()
    with db.get_connection() as conn:
        conn.execute("DELETE FROM messages")
        conn.execute("DELETE FROM topics")
        conn.execute("DELETE FROM settings")
    yield


@pytest.fixture
def client():
    return TestClient(main.app)


def _csrf_token(client: TestClient) -> str:
    client.post("/api/login", data={"username": "testuser", "password": "x"})
    resp = client.get("/api/csrf")
    assert resp.status_code == 200
    return resp.json()["csrf_token"]


def _login(client: TestClient) -> str:
    resp = client.post("/api/login", data={"username": "testuser", "password": "x"})
    assert resp.status_code == 200
    return _csrf_token(client)


def test_login_and_me(client: TestClient):
    resp = client.get("/api/me")
    assert resp.status_code == 401

    resp = client.post("/api/login", data={"username": "testuser", "password": "x"})
    assert resp.status_code == 200

    resp = client.get("/api/me")
    assert resp.status_code == 200
    assert resp.json()["username"] == "testuser"


def test_root_login_rejected(client: TestClient):
    resp = client.post("/api/login", data={"username": "root", "password": "x"})
    assert resp.status_code == 401


def test_topic_lifecycle(client: TestClient):
    token = _login(client)

    resp = client.post(
        "/api/topics",
        headers={"X-CSRFToken": token},
        data={"name": "test-topic"},
    )
    assert resp.status_code == 201
    topic_id = resp.json()["id"]

    resp = client.get("/api/topics", headers={"X-CSRFToken": token})
    assert resp.status_code == 200
    topics = resp.json()
    assert any(t["id"] == topic_id and t["name"] == "test-topic" for t in topics)

    resp = client.get(f"/api/topics/{topic_id}/messages", headers={"X-CSRFToken": token})
    assert resp.status_code == 200
    assert resp.json() == []

    resp = client.delete(f"/api/topics/{topic_id}", headers={"X-CSRFToken": token})
    assert resp.status_code == 200


def test_settings_roundtrip(client: TestClient):
    token = _login(client)

    resp = client.post(
        "/api/settings",
        headers={"X-CSRFToken": token},
        data={
            "messages.server_url": "http://ntfy.local",
            "messages.token": "secret-token",
            "logs.path": "/tmp/test.log",
            "logs.use_journal": "true",
        },
    )
    assert resp.status_code == 200

    resp = client.get("/api/settings", headers={"X-CSRFToken": token})
    assert resp.status_code == 200
    data = resp.json()
    assert data["messages.server_url"] == "http://ntfy.local"
    assert data["messages.token"] == "secret-token"
    assert data["logs.path"] == "/tmp/test.log"
    assert data["logs.use_journal"] == "true"


def test_logs_endpoint(client: TestClient):
    token = _login(client)

    log_path = Path(os.environ["LOG_PATH"]).resolve()
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_path.write_text("test log line\n", encoding="utf-8")

    client.post(
        "/api/settings",
        headers={"X-CSRFToken": token},
        data={"logs.path": str(log_path), "logs.use_journal": "false"},
    )

    resp = client.get("/api/logs", headers={"X-CSRFToken": token})
    data = resp.json()
    assert resp.status_code == 200, f"unexpected status {resp.status_code}: {data}"
    assert "test log line" in data["lines"]


def test_send_message_stores_and_broadcasts(client: TestClient):
    token = _login(client)

    resp = client.post(
        "/api/topics",
        headers={"X-CSRFToken": token},
        data={"name": "send-test-topic"},
    )
    assert resp.status_code == 201, resp.json()
    topic_id = resp.json()["id"]

    resp = client.post(
        f"/api/topics/{topic_id}/messages",
        headers={"X-CSRFToken": token},
        data={"body": "hello world"},
    )
    assert resp.status_code == 201

    resp = client.get(f"/api/topics/{topic_id}/messages", headers={"X-CSRFToken": token})
    messages = resp.json()
    assert len(messages) == 1
    assert messages[0]["body"] == "hello world"
    assert messages[0]["is_mine"] is True


def test_static_index_served(client: TestClient):
    resp = client.get("/")
    assert resp.status_code == 200
    assert "Home Assistant" in resp.text

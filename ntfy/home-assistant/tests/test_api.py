import os
import sys
from pathlib import Path

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT))

os.environ.setdefault("HOME_ASSISTANT_HOST", "127.0.0.1")
os.environ.setdefault("HOME_ASSISTANT_PORT", "5000")
os.environ.setdefault("SECRET_KEY", "test-secret-key")
os.environ.setdefault("NTFY_BASE_URL", "http://localhost")
os.environ.setdefault("DATABASE_PATH", str(ROOT / "tests" / "test.db"))
os.environ.setdefault("LOG_PATH", str(ROOT / "tests" / "test.log"))
os.environ.setdefault("DEV_AUTH_BYPASS", "1")

import app as app_module


def _csrf_token(client):
    resp = client.get("/login")
    assert resp.status_code == 200
    text = resp.data.decode("utf-8")
    return text.split('name="csrf_token" value="')[1].split('"')[0]


def _login(client):
    token = _csrf_token(client)
    resp = client.post(
        "/login",
        data={"csrf_token": token, "username": "testuser", "password": "x"},
        follow_redirects=True,
    )
    assert resp.status_code == 200
    return token


def test_login_page_renders():
    client = app_module.app.test_client()
    resp = client.get("/login")
    assert resp.status_code == 200
    assert b"Home Assistant" in resp.data
    assert b"csrf_token" in resp.data


def test_root_login_rejected():
    client = app_module.app.test_client()
    token = _csrf_token(client)
    resp = client.post(
        "/login",
        data={"csrf_token": token, "username": "root", "password": "x"},
    )
    assert resp.status_code == 200
    assert b"Invalid credentials" in resp.data


def test_index_renders_after_login():
    client = app_module.app.test_client()
    _login(client)
    resp = client.get("/")
    assert resp.status_code == 200
    assert b"CPU Temperature" in resp.data
    assert b"testuser" in resp.data


def test_topic_lifecycle():
    client = app_module.app.test_client()
    token = _login(client)

    # Add topic
    resp = client.post(
        "/api/topics",
        headers={"X-CSRFToken": token},
        data={"name": "test-topic"},
    )
    assert resp.status_code == 201
    topic_id = resp.get_json()["id"]

    # List topics
    resp = client.get("/api/topics", headers={"X-CSRFToken": token})
    assert resp.status_code == 200
    topics = resp.get_json()
    assert any(t["id"] == topic_id and t["name"] == "test-topic" for t in topics)

    # Get messages (empty)
    resp = client.get(f"/api/topics/{topic_id}/messages", headers={"X-CSRFToken": token})
    assert resp.status_code == 200
    assert resp.get_json() == []

    # Delete topic
    resp = client.delete(f"/api/topics/{topic_id}", headers={"X-CSRFToken": token})
    assert resp.status_code == 200


def test_settings_roundtrip():
    client = app_module.app.test_client()
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
    data = resp.get_json()
    assert data["messages.server_url"] == "http://ntfy.local"
    assert data["messages.token"] == "secret-token"
    assert data["logs.path"] == "/tmp/test.log"
    assert data["logs.use_journal"] == "true"


def test_logs_endpoint():
    client = app_module.app.test_client()
    token = _login(client)

    # Write a line to the log file
    log_path = Path(os.environ["LOG_PATH"]).resolve()
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_path.write_text("test log line\n", encoding="utf-8")

    # Ensure settings point to the same log file
    client.post(
        "/api/settings",
        headers={"X-CSRFToken": token},
        data={"logs.path": str(log_path), "logs.use_journal": "false"},
    )

    resp = client.get("/api/logs", headers={"X-CSRFToken": token})
    data = resp.get_json()
    assert resp.status_code == 200, f"unexpected status {resp.status_code}: {data}"
    assert "test log line" in data["lines"]


if __name__ == "__main__":
    import tempfile

    with tempfile.TemporaryDirectory() as tmpdir:
        os.environ["DATABASE_PATH"] = str(Path(tmpdir) / "test.db")
        os.environ["LOG_PATH"] = str(Path(tmpdir) / "test.log")

        # Re-import to pick up temp paths
        import importlib

        importlib.reload(app_module)

        test_login_page_renders()
        test_root_login_rejected()
        test_index_renders_after_login()
        test_topic_lifecycle()
        test_settings_roundtrip()
        test_logs_endpoint()
        print("OK: all API tests passed")

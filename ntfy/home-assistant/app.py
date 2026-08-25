import json
import logging
import os
import queue
import threading
from dataclasses import asdict
from datetime import datetime, timezone
from functools import wraps
from pathlib import Path
from typing import Any, Optional

import requests
from asgiref.wsgi import WsgiToAsgi
from flask import (
    Flask,
    Response,
    flash,
    jsonify,
    redirect,
    render_template,
    request,
    session,
    url_for,
)
from flask_wtf.csrf import CSRFProtect, generate_csrf
from werkzeug.middleware.proxy_fix import ProxyFix

import config
import db
import log_reader
import ntfy_client
import settings_store
import system

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

app = Flask(__name__)
app.secret_key = config.SECRET_KEY
app.config["WTF_CSRF_TIME_LIMIT"] = None
csrf = CSRFProtect(app)
app.wsgi_app = ProxyFix(app.wsgi_app, x_for=1, x_proto=1, x_host=1, x_prefix=1)

subscriber = ntfy_client.NtfySubscriber()
_sse_clients: list[queue.Queue] = []
_sse_lock = threading.Lock()


def static_version(filename: str) -> int:
    return int((Path(app.root_path) / "static" / filename).stat().st_mtime)


app.add_template_global(static_version, "static_version")


# --- Auth ---


try:
    import pwd
    import pam

    _PAM_AVAILABLE = True
except ImportError:
    _PAM_AVAILABLE = False


def authenticate_system_user(username: str, password: str) -> bool:
    if username == "root":
        return False

    if os.environ.get("DEV_AUTH_BYPASS") == "1":
        return bool(username)

    if not _PAM_AVAILABLE:
        return False

    try:
        pwd.getpwnam(username)
    except KeyError:
        return False

    try:
        p = pam.pam()
        return p.authenticate(username, password)
    except Exception:
        return False


def require_login(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if "username" not in session:
            return redirect(url_for("login"))
        return f(*args, **kwargs)

    return decorated


# --- SSE helpers ---


def _broadcast(message: dict[str, Any]) -> None:
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


def _subscribe_all_topics() -> None:
    server_url = settings_store.get_messages_server_url()
    token = settings_store.get_messages_token()
    for topic in db.list_topics():
        subscriber.subscribe(server_url, topic["name"], token)


def _ntfy_consumer() -> None:
    while True:
        msg = subscriber.get_message(timeout=1.0)
        if msg is None:
            continue

        topic_row = db.get_topic_by_name(msg.topic)
        if topic_row is None:
            continue

        sender = msg.title or None
        try:
            db.add_message(
                topic_id=topic_row["id"],
                body=msg.message,
                sender=sender,
                is_outgoing=False,
            )
        except Exception as exc:
            logger.warning("Failed to store incoming message: %s", exc)

        sent_at = None
        if msg.time:
            try:
                sent_at = datetime.fromtimestamp(int(msg.time), tz=timezone.utc).isoformat()
            except (ValueError, OSError):
                sent_at = None
        if not sent_at:
            sent_at = datetime.now(timezone.utc).isoformat()

        _broadcast(
            {
                "type": "message",
                "topic": msg.topic,
                "sender": sender,
                "body": msg.message,
                "sent_at": sent_at,
            }
        )


# Start background consumer thread once at import time.
threading.Thread(target=_ntfy_consumer, daemon=True).start()


# --- Routes ---


@app.route("/login", methods=["GET", "POST"])
def login():
    if "username" in session:
        return redirect(url_for("index"))
    if request.method == "POST":
        username = request.form.get("username", "").strip()
        password = request.form.get("password", "")
        if not username or not password:
            flash("Username and password are required", "error")
        elif not authenticate_system_user(username, password):
            flash("Invalid credentials", "error")
        else:
            session["username"] = username
            return redirect(url_for("index"))
    return render_template("login.html")


@app.route("/logout")
@require_login
def logout():
    session.clear()
    return redirect(url_for("login"))


@app.route("/")
@require_login
def index():
    db.init_db()
    cpu_temp = system.cpu_temp_celsius()
    memory = system.memory_usage()
    return render_template(
        "index.html",
        username=session["username"],
        cpu_temp=cpu_temp,
        memory=memory,
        settings=settings_store.as_dict(),
        csrf_token=generate_csrf(),
    )


# --- API: Topics ---


@app.route("/api/topics", methods=["GET"])
@require_login
def api_list_topics():
    rows = db.list_topics()
    return jsonify(
        [
            {
                "id": row["id"],
                "name": row["name"],
                "latest_body": row["latest_body"],
                "latest_sent_at": row["latest_sent_at"],
            }
            for row in rows
        ]
    )


@app.route("/api/topics", methods=["POST"])
@require_login
def api_add_topic():
    name = request.form.get("name", "").strip()
    if not name:
        return jsonify({"error": "Topic name is required"}), 400
    try:
        topic_id = db.add_topic(name)
    except ValueError as exc:
        return jsonify({"error": str(exc)}), 409

    server_url = settings_store.get_messages_server_url()
    token = settings_store.get_messages_token()
    subscriber.subscribe(server_url, name, token)
    return jsonify({"id": topic_id, "name": name}), 201


@app.route("/api/topics/<int:topic_id>", methods=["DELETE"])
@require_login
def api_delete_topic(topic_id: int):
    topic = db.get_topic(topic_id)
    if topic is None:
        return jsonify({"error": "Topic not found"}), 404
    subscriber.unsubscribe(topic["name"])
    db.delete_topic(topic_id)
    return jsonify({"ok": True})


# --- API: Messages ---


def _message_to_dict(row: db.sqlite3.Row, current_user: str) -> dict[str, Any]:
    return {
        "id": row["id"],
        "topic_id": row["topic_id"],
        "sender": row["sender"],
        "body": row["body"],
        "sent_at": row["sent_at"],
        "is_mine": row["sender"] == current_user,
    }


@app.route("/api/topics/<int:topic_id>/messages", methods=["GET"])
@require_login
def api_list_messages(topic_id: int):
    if db.get_topic(topic_id) is None:
        return jsonify({"error": "Topic not found"}), 404
    rows = db.list_messages(topic_id)
    return jsonify([_message_to_dict(row, session["username"]) for row in rows])


@app.route("/api/topics/<int:topic_id>/messages", methods=["POST"])
@require_login
def api_send_message(topic_id: int):
    topic = db.get_topic(topic_id)
    if topic is None:
        return jsonify({"error": "Topic not found"}), 404

    body = request.form.get("body", "").strip()
    if not body:
        return jsonify({"error": "Message body is required"}), 400

    server_url = settings_store.get_messages_server_url()
    token = settings_store.get_messages_token()
    username = session["username"]

    headers = {"X-Title": username}
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
        logger.warning("Failed to send message: %s", exc)
        return jsonify({"error": "Failed to send message to ntfy server"}), 502

    return jsonify({"ok": True}), 201


@app.route("/api/messages/stream")
@require_login
def api_message_stream():
    q: queue.Queue[str] = queue.Queue(maxsize=100)
    with _sse_lock:
        _sse_clients.append(q)

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
            with _sse_lock:
                if q in _sse_clients:
                    _sse_clients.remove(q)

    return Response(event_stream(), mimetype="text/event-stream")


# --- API: Logs ---


@app.route("/api/logs", methods=["GET"])
@require_login
def api_logs():
    use_journal = settings_store.get_logs_use_journal()
    if use_journal:
        try:
            lines = log_reader.read_journalctl("home-assistant")
        except log_reader.LogReaderError as exc:
            return jsonify({"error": str(exc)}), 500
    else:
        path = settings_store.get_logs_path()
        try:
            lines = log_reader.read_log_file(path)
        except log_reader.LogReaderError as exc:
            return jsonify({"error": str(exc)}), 500
    return jsonify({"lines": lines})


@app.route("/api/logs/clear", methods=["POST"])
@require_login
def api_clear_logs():
    if settings_store.get_logs_use_journal():
        return jsonify({"error": "Cannot clear journalctl logs"}), 400
    path = settings_store.get_logs_path()
    try:
        log_reader.clear_log_file(path)
    except log_reader.LogReaderError as exc:
        return jsonify({"error": str(exc)}), 500
    return jsonify({"ok": True})


# --- API: Settings ---


@app.route("/api/settings", methods=["GET"])
@require_login
def api_get_settings():
    return jsonify(settings_store.as_dict())


@app.route("/api/settings", methods=["POST"])
@require_login
def api_save_settings():
    settings_store.update_from_form(request.form.to_dict())
    # Re-subscribe with potentially new server URL / token.
    subscriber.unsubscribe_all()
    _subscribe_all_topics()
    return jsonify({"ok": True})


# --- Startup ---


def _init_app() -> None:
    db.init_db()
    _subscribe_all_topics()


_init_app()
asgi_app = WsgiToAsgi(app)

if __name__ == "__main__":
    app.run(host=config.HOST, port=config.PORT)

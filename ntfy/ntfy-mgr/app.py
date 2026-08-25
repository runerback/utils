from functools import wraps
from pathlib import Path
from typing import Optional

import requests
from asgiref.wsgi import WsgiToAsgi
from flask import Flask, flash, redirect, render_template, request, session, url_for
from flask_wtf.csrf import CSRFProtect
from werkzeug.middleware.proxy_fix import ProxyFix

import cli
import config

app = Flask(__name__)
app.secret_key = config.SECRET_KEY
app.config["WTF_CSRF_TIME_LIMIT"] = None
csrf = CSRFProtect(app)
app.wsgi_app = ProxyFix(
    app.wsgi_app, x_for=1, x_proto=1, x_host=1, x_prefix=1
)


def static_version(filename: str) -> int:
    return int((Path(app.root_path) / "static" / filename).stat().st_mtime)


app.add_template_global(static_version, "static_version")


def require_login(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if "username" not in session:
            return redirect(url_for("login"))
        return f(*args, **kwargs)

    return decorated


def validate_admin(username: str, password: str) -> bool:
    try:
        resp = requests.get(
            f"{config.NTFY_BASE_URL}/v1/account",
            auth=(username, password),
            timeout=10,
        )
    except requests.RequestException:
        return False
    if resp.status_code != 200:
        return False
    data = resp.json()
    return data.get("role") == "admin"


def load_data():
    users = cli.list_users()
    tokens = cli.list_tokens()
    token_map = {ut.user: ut.tokens for ut in tokens}
    return users, token_map


@app.route("/login", methods=["GET", "POST"])
def login():
    if "username" in session:
        return redirect(url_for("index"))
    if request.method == "POST":
        username = request.form.get("username", "").strip()
        password = request.form.get("password", "")
        if not username or not password:
            flash("Username and password are required", "error")
        elif not validate_admin(username, password):
            flash("Invalid credentials or not an admin", "error")
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
    users, token_map = load_data()
    topics = cli.topics_from_users(users)
    topic_data = []
    for topic in topics:
        accessors = []
        for user in users:
            if user.name in ("*", "everyone"):
                continue
            for access in user.accesses:
                if access.topic == topic and access.permission != "no":
                    accessors.append({"username": user.name, "permission": access.permission})
        topic_data.append({"name": topic, "accessors": accessors})
    active_tab = request.args.get("tab", "users")
    return render_template(
        "index.html",
        users=users,
        token_map=token_map,
        topic_data=topic_data,
        active_tab=active_tab,
    )


def _handle(action, success_msg):
    try:
        action()
        flash(success_msg, "success")
    except cli.NtfyError as exc:
        flash(str(exc), "error")
    return redirect(request.referrer or url_for("index"))


@app.route("/users/add", methods=["POST"])
@require_login
def add_user():
    username = request.form.get("username", "").strip()
    password = request.form.get("password", "")
    if not username or not password:
        flash("Username and password are required", "error")
        return redirect(url_for("index"))
    return _handle(lambda: cli.add_user(username, password), f"User {username} added")


@app.route("/users/<name>/delete", methods=["POST"])
@require_login
def delete_user(name: str):
    return _handle(lambda: cli.remove_user(name), f"User {name} deleted")


@app.route("/users/<name>/access/grant", methods=["POST"])
@require_login
def grant_user_access(name: str):
    topic = request.form.get("topic", "").strip()
    permission = request.form.get("permission", "").strip()
    if not topic or not permission:
        flash("Topic and permission are required", "error")
        return redirect(url_for("index", tab="users"))
    return _handle(
        lambda: cli.grant_access(name, topic, permission),
        f"Access granted for {name} on {topic}",
    )


@app.route("/users/<name>/access/revoke", methods=["POST"])
@require_login
def revoke_user_access(name: str):
    topic = request.form.get("topic", "").strip()
    if not topic:
        flash("Topic is required", "error")
        return redirect(url_for("index", tab="users"))
    return _handle(
        lambda: cli.revoke_access(name, topic),
        f"Access revoked for {name} on {topic}",
    )


@app.route("/users/<name>/tokens/add", methods=["POST"])
@require_login
def add_user_token(name: str):
    expires = request.form.get("expires", "").strip()
    label = request.form.get("label", "").strip()
    return _handle(
        lambda: cli.add_token(name, expires, label),
        f"Token added for {name}",
    )


@app.route("/users/<name>/tokens/<token>/delete", methods=["POST"])
@require_login
def delete_user_token(name: str, token: str):
    return _handle(lambda: cli.remove_token(name, token), "Token deleted")


@app.route("/topics/create", methods=["POST"])
@require_login
def create_topic():
    topic = request.form.get("topic", "").strip()
    users_selected = request.form.getlist("users")
    if not topic:
        flash("Topic name is required", "error")
        return redirect(url_for("index", tab="topics"))
    if not users_selected:
        flash("Select at least one user", "error")
        return redirect(url_for("index", tab="topics"))

    def action():
        for username in users_selected:
            permission = request.form.get(f"permission_{username}", "read-write").strip()
            if permission:
                cli.grant_access(username, topic, permission)

    return _handle(action, f"Topic {topic} created")


@app.route("/topics/delete", methods=["POST"])
@require_login
def delete_topic():
    topic = request.form.get("topic", "").strip()
    if not topic:
        flash("Topic is required", "error")
        return redirect(url_for("index", tab="topics"))

    def action():
        users, _ = load_data()
        for user in users:
            for access in user.accesses:
                if access.topic == topic and access.permission != "no":
                    cli.revoke_access(user.name, topic)

    return _handle(action, f"Topic {topic} deleted")


@app.route("/topics/access/grant", methods=["POST"])
@require_login
def grant_topic_access():
    topic = request.form.get("topic", "").strip()
    username = request.form.get("username", "").strip()
    permission = request.form.get("permission", "").strip()
    if not topic or not username or not permission:
        flash("User, topic and permission are required", "error")
        return redirect(url_for("index", tab="topics"))
    return _handle(
        lambda: cli.grant_access(username, topic, permission),
        f"Access granted for {username} on {topic}",
    )


@app.route("/topics/access/revoke", methods=["POST"])
@require_login
def revoke_topic_access():
    topic = request.form.get("topic", "").strip()
    username = request.form.get("username", "").strip()
    if not topic or not username:
        flash("User and topic are required", "error")
        return redirect(url_for("index", tab="topics"))
    return _handle(
        lambda: cli.revoke_access(username, topic),
        f"Access revoked for {username} on {topic}",
    )


asgi_app = WsgiToAsgi(app)

if __name__ == "__main__":
    app.run(host=config.HOST, port=config.PORT)

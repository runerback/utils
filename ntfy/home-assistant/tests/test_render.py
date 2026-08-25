import sys
from pathlib import Path

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT))

from jinja2 import Environment, FileSystemLoader


def _env():
    env = Environment(loader=FileSystemLoader(ROOT / "templates"))
    env.globals["url_for"] = lambda endpoint, **kwargs: f"/{endpoint}"
    env.globals["csrf_token"] = lambda: "test-csrf-token"
    env.globals["get_flashed_messages"] = lambda *args, **kwargs: []
    env.globals["static_version"] = lambda filename: 1
    return env


def test_login_template_renders():
    template = _env().get_template("login.html")
    html = template.render()
    assert "Home Assistant" in html
    assert 'name="username"' in html
    assert 'name="password"' in html


def test_index_template_has_tabs():
    template = _env().get_template("index.html")
    html = template.render(
        username="testuser",
        cpu_temp=45.2,
        memory={"total_kb": 16000000, "used_kb": 8000000, "percent": 50.0},
        settings={
            "messages.server_url": "http://localhost",
            "messages.token": "",
            "logs.path": "/tmp/test.log",
            "logs.use_journal": "false",
        },
        csrf_token="test-csrf-token",
    )
    assert 'data-tab="home"' in html
    assert 'data-tab="messages"' in html
    assert 'data-tab="devices"' in html
    assert 'data-tab="logs"' in html
    assert 'data-tab="settings"' in html
    assert "CPU Temperature" in html
    assert "Memory Usage" in html


def test_index_template_messages_has_topic_list_and_chatroom():
    template = _env().get_template("index.html")
    html = template.render(
        username="testuser",
        cpu_temp=None,
        memory={"total_kb": None, "used_kb": None, "percent": None},
        settings={
            "messages.server_url": "http://localhost",
            "messages.token": "",
            "logs.path": "/tmp/test.log",
            "logs.use_journal": "false",
        },
        csrf_token="test-csrf-token",
    )
    assert "topics-container" in html
    assert "messages-chatroom" in html
    assert "chat-messages" in html
    assert "chat-input" in html


if __name__ == "__main__":
    test_login_template_renders()
    test_index_template_has_tabs()
    test_index_template_messages_has_topic_list_and_chatroom()
    print("OK: template renders")

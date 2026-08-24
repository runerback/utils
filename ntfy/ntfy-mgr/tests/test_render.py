import sys
from pathlib import Path

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT))

from jinja2 import Environment, FileSystemLoader


def test_users_tab_collapsible_cards():
    env = Environment(loader=FileSystemLoader(ROOT / "templates"))
    env.globals["url_for"] = lambda endpoint, **kwargs: f"/{endpoint}"
    env.globals["csrf_token"] = lambda: "test-csrf-token"
    env.globals["get_flashed_messages"] = lambda *args, **kwargs: []
    env.globals["static_version"] = lambda filename: 1

    template = env.get_template("index.html")
    html = template.render(
        users=[{"name": "alice", "role": "user", "tier": "default", "accesses": []}],
        token_map={},
        topic_data=[],
        active_tab="users",
        session={"username": "admin"},
    )

    assert "user-card" in html, "missing user-card class"
    assert "user-card-toggle" in html, "missing user-card-toggle class"
    assert "user-card-body collapsed" in html, "missing collapsed body"
    assert "Delete user" in html, "missing delete button"
    assert "user-card-footer" in html, "missing user-card-footer"


if __name__ == "__main__":
    test_users_tab_collapsible_cards()
    print("OK: template renders and collapsible user card elements are present")

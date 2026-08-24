import sys
from pathlib import Path

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT))

import cli


def test_access_re_captures_full_topic_name():
    cases = [
        ("- read-write access to topic abcd", "abcd"),
        ("- read-only access to topic my-topic (server config)", "my-topic"),
        ("- no access to topic another-topic", "another-topic"),
    ]
    for line, expected in cases:
        match = cli._access_re.match(line)
        assert match is not None, f"failed to match: {line}"
        assert match.group(2) == expected, f"expected {expected!r}, got {match.group(2)!r}"


def test_user_header_re_captures_full_tier():
    cases = [
        ("user alice (role: user, tier: default)", "default"),
        ("user bob (role: admin, tier: pro, server config)", "pro"),
    ]
    for line, expected in cases:
        match = cli._user_header_re.match(line)
        assert match is not None, f"failed to match: {line}"
        assert match.group(3) == expected, f"expected {expected!r}, got {match.group(3)!r}"


if __name__ == "__main__":
    test_access_re_captures_full_topic_name()
    test_user_header_re_captures_full_tier()
    print("OK: regex captures full topic names and tiers")

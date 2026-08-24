from __future__ import annotations

import os
import re
import subprocess
from dataclasses import dataclass, field
from typing import List, Optional

import config


class NtfyError(Exception):
    pass


@dataclass
class Access:
    topic: str
    permission: str
    provisioned: bool = False


@dataclass
class User:
    name: str
    role: str
    tier: str
    provisioned: bool = False
    accesses: List[Access] = field(default_factory=list)


@dataclass
class Token:
    value: str
    label: Optional[str]
    expires: str
    last_origin: str
    last_access: str
    provisioned: bool = False


@dataclass
class UserTokens:
    user: str
    tokens: List[Token] = field(default_factory=list)


def _run(args: List[str], env: Optional[dict] = None, input_text: Optional[str] = None) -> str:
    full_env = os.environ.copy()
    if env:
        full_env.update(env)
    try:
        result = subprocess.run(
            [config.NTFY_CLI, *args],
            capture_output=True,
            text=True,
            env=full_env,
            input=input_text,
            check=False,
        )
    except FileNotFoundError as exc:
        raise NtfyError(f"ntfy binary not found: {config.NTFY_CLI}") from exc
    if result.returncode != 0:
        raise NtfyError(result.stderr.strip() or result.stdout.strip() or f"ntfy failed with code {result.returncode}")
    return result.stdout.strip()


_user_header_re = re.compile(
    r"^user\s+(\S+)\s+\(role:\s*(\S+),\s*tier:\s*(\S+?)(?:,\s*server config)?\)"
)
_access_re = re.compile(
    r"^-\s+(read-write|read-only|write-only|no)\s+access\s+to\s+topic\s+(\S+?)(?:\s+\(server config\))?"
)


def list_users() -> List[User]:
    output = _run(["user", "list"])
    users: List[User] = []
    current: Optional[User] = None
    for line in output.splitlines():
        line = line.rstrip()
        header = _user_header_re.match(line)
        if header:
            current = User(
                name=header.group(1),
                role=header.group(2),
                tier=header.group(3),
                provisioned="server config" in line,
            )
            users.append(current)
            continue
        if current is None:
            continue
        if "access to all topics (admin role)" in line:
            continue
        access = _access_re.match(line)
        if access:
            perm = access.group(1)
            topic = access.group(2)
            current.accesses.append(
                Access(
                    topic=topic,
                    permission=perm,
                    provisioned="server config" in line,
                )
            )
    return users


def add_user(username: str, password: str) -> None:
    _run(["user", "add", username], env={"NTFY_PASSWORD": password})


def remove_user(username: str) -> None:
    _run(["user", "remove", username])


def grant_access(username: str, topic: str, permission: str) -> None:
    _run(["access", username, topic, permission])


def revoke_access(username: str, topic: str) -> None:
    _run(["access", username, topic, "deny"])


def reset_user_access(username: str) -> None:
    _run(["access", "--reset", username])


_token_line_re = re.compile(
    r"^-\s+(tk_[a-zA-Z0-9]+)(?:\s+\(([^)]+)\))?\s*,\s*(.+?)\s*,\s*accessed\s+from\s+(.+?)\s+at\s+(.+?)(?:\s+\(server config\))?\s*$"
)


def list_tokens() -> List[UserTokens]:
    output = _run(["token", "list"])
    result: List[UserTokens] = []
    current: Optional[UserTokens] = None
    for line in output.splitlines():
        line = line.rstrip()
        if line.startswith("user "):
            current = UserTokens(user=line[5:].strip())
            result.append(current)
            continue
        if current is None:
            continue
        match = _token_line_re.match(line)
        if match:
            current.tokens.append(
                Token(
                    value=match.group(1),
                    label=match.group(2),
                    expires=match.group(3),
                    last_origin=match.group(4),
                    last_access=match.group(5),
                    provisioned="server config" in line,
                )
            )
    return result


def add_token(username: str, expires: str, label: str) -> None:
    args = ["token", "add"]
    if expires:
        args.extend(["--expires", expires])
    if label:
        args.extend(["--label", label])
    args.append(username)
    _run(args)


def remove_token(username: str, token: str) -> None:
    _run(["token", "remove", username, token])


def topics_from_users(users: List[User]) -> List[str]:
    topics = set()
    for user in users:
        for access in user.accesses:
            if access.permission != "no":
                topics.add(access.topic)
    return sorted(topics)


def users_for_topic(users: List[User], topic: str) -> List[tuple]:
    result = []
    for user in users:
        for access in user.accesses:
            if access.topic == topic and access.permission != "no":
                result.append((user, access))
    return result

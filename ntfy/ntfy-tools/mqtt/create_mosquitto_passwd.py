#!/usr/bin/env python3
import base64
import getpass
import hashlib
import os
import re
import sys
from pathlib import Path

PASSWD_FILE = Path(__file__).parent / "mosquitto.passwd"
SALT_LEN = 64
HASH_LEN = 64
DEFAULT_ITERATIONS = 1000


def hash_password(password: str, iterations: int = DEFAULT_ITERATIONS) -> str:
    salt = os.urandom(SALT_LEN)
    password_hash = hashlib.pbkdf2_hmac(
        "sha512", password.encode("utf-8"), salt, iterations, dklen=HASH_LEN
    )
    salt_b64 = base64.b64encode(salt).decode("ascii")
    hash_b64 = base64.b64encode(password_hash).decode("ascii")
    return f"$7${iterations}${salt_b64}${hash_b64}"


def update_passwd_file(path: Path, username: str, password_hash: str) -> None:
    lines = []
    found = False
    pattern = re.compile(re.escape(username) + r":")

    if path.exists():
        with path.open("r", encoding="utf-8") as f:
            for line in f:
                if pattern.match(line):
                    lines.append(f"{username}:{password_hash}\n")
                    found = True
                else:
                    lines.append(line)

    if not found:
        lines.append(f"{username}:{password_hash}\n")

    with path.open("w", encoding="utf-8") as f:
        f.writelines(lines)


def main():
    print("Mosquitto password file generator")
    print(f"Target file: {PASSWD_FILE}")

    username = input("Username: ").strip()
    if not username or ":" in username:
        print("Username is required and must not contain ':'.", file=sys.stderr)
        sys.exit(1)

    password = getpass.getpass("Password: ")
    if not password:
        print("Password is required.", file=sys.stderr)
        sys.exit(1)

    confirm = getpass.getpass("Confirm password: ")
    if password != confirm:
        print("Passwords do not match.", file=sys.stderr)
        sys.exit(1)

    password_hash = hash_password(password)
    update_passwd_file(PASSWD_FILE, username, password_hash)

    print(f"Password entry added for '{username}' in {PASSWD_FILE}")


if __name__ == "__main__":
    main()

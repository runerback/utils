"""Byte-compile every server module to catch syntax errors.

Usage (from anywhere):
    home-assistant/.venv/Scripts/python.exe home-assistant/tools/check_server.py
"""
import compileall
import sys
from pathlib import Path

SERVER_SRC = Path(__file__).resolve().parent.parent / "server" / "src"


def main() -> int:
    ok = compileall.compile_dir(str(SERVER_SRC), quiet=1, force=True)
    if ok:
        print(f"OK: all modules under {SERVER_SRC} compile")
        return 0
    print(f"FAILED: syntax errors under {SERVER_SRC}")
    return 1


if __name__ == "__main__":
    sys.exit(main())

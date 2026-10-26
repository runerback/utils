#!/usr/bin/env python3
"""Download a home-assistant-server zip (from an OSS temp URL or a local file) and update the target machine.

Usage: python3 server_updator.py <oss-temp-url | local-zip-path>
                                             [--target /opt/home-assistant]
                                             [--dry-run] [--no-backup]
stdlib only; self-contained for the target machine.
"""

import argparse
import difflib
import os
import shutil
import sys
import tempfile
import urllib.request
import zipfile
from datetime import datetime
from pathlib import Path, PurePosixPath

MAX_ZIP_BYTES = 64 * 1024 * 1024  # 64 MiB sanity cap

FORBIDDEN_PREFIXES: tuple[str, ...] = ()
FORBIDDEN_NAMES = {".env", "home-assistant.nginx", "home-assistant.service"}


def _enable_color() -> bool:
    """True if ANSI colors should be used; enables them on Windows consoles."""
    if os.environ.get("NO_COLOR") or not sys.stdout.isatty():
        return False
    if os.name != "nt":
        return True
    try:
        import ctypes
        kernel32 = ctypes.windll.kernel32
        # ENABLE_VIRTUAL_TERMINAL_PROCESSING on stdout
        return kernel32.SetConsoleMode(kernel32.GetStdHandle(-11), 7) != 0
    except Exception:
        return False


_COLOR = _enable_color()
_RESET = "\033[0m" if _COLOR else ""
_BOLD = "\033[1m" if _COLOR else ""
_RED = "\033[31m" if _COLOR else ""
_GREEN = "\033[32m" if _COLOR else ""
_CYAN = "\033[36m" if _COLOR else ""
_YELLOW = "\033[33m" if _COLOR else ""


def c(text: str, color: str) -> str:
    return f"{color}{text}{_RESET}" if _COLOR else text


def forbidden_reason(name: str):
    """Return why a zip entry must not be applied to the target, or None."""
    p = PurePosixPath(name)
    if p.is_absolute() or ".." in p.parts:
        return "unsafe path"
    for prefix in FORBIDDEN_PREFIXES:
        if name == prefix or name.startswith(prefix + "/"):
            return f"forbidden prefix: {prefix}"
    base = p.name
    if base in FORBIDDEN_NAMES or base.startswith(".env"):
        return "forbidden file"
    if base.endswith((".db", ".log", ".pyc")):
        return "forbidden file type"
    if "__pycache__" in p.parts:
        return "forbidden directory"
    return None


def print_update_diff(rel: Path, old: bytes, new: bytes) -> None:
    """Print a git-style unified diff of an updated file."""
    path = rel.as_posix()
    print(c(f"diff --git a/{path} b/{path}", _BOLD))
    try:
        old_lines = old.decode("utf-8").splitlines()
        new_lines = new.decode("utf-8").splitlines()
    except UnicodeDecodeError:
        print(c(f"Binary files a/{path} and b/{path} differ", _YELLOW))
        return
    for line in difflib.unified_diff(old_lines, new_lines,
                                     fromfile=f"a/{path}", tofile=f"b/{path}",
                                     lineterm="", n=3):
        if line.startswith("+++"):
            print(c(line, _BOLD))
        elif line.startswith("---"):
            print(c(line, _BOLD))
        elif line.startswith("+"):
            print(c(line, _GREEN))
        elif line.startswith("-"):
            print(c(line, _RED))
        elif line.startswith("@@"):
            print(c(line, _CYAN))
        else:
            print(line)


def download(url: str, dest: Path) -> None:
    req = urllib.request.Request(url, headers={"User-Agent": "server-updator"})
    with urllib.request.urlopen(req, timeout=60) as resp, open(dest, "wb") as f:
        total = 0
        while True:
            chunk = resp.read(1 << 16)
            if not chunk:
                break
            total += len(chunk)
            if total > MAX_ZIP_BYTES:
                raise RuntimeError(f"download exceeds {MAX_ZIP_BYTES} bytes cap")
            f.write(chunk)
    print(f"downloaded {total} bytes")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source", help="OSS temp (signed) URL of the zip, or a local zip file path")
    parser.add_argument("--target", type=Path, default=Path("/opt/home-assistant"))
    parser.add_argument("--dry-run", action="store_true",
                        help="show what would change, write nothing")
    parser.add_argument("--no-backup", action="store_true")
    args = parser.parse_args()

    target = args.target.resolve()
    if not target.is_dir():
        print(f"error: target not found: {target}", file=sys.stderr)
        return 1

    local_zip = Path(args.source).expanduser()
    if local_zip.is_file():
        zip_path = local_zip.resolve()
        size = zip_path.stat().st_size
        if size > MAX_ZIP_BYTES:
            print(f"error: zip exceeds {MAX_ZIP_BYTES} bytes cap", file=sys.stderr)
            return 1
        print(f"using local zip {zip_path} ({size} bytes)")
        tmp_ctx = tempfile.TemporaryDirectory(prefix="ha-update-")
    elif args.source.startswith(("http://", "https://")):
        zip_path = None  # downloaded into the temp dir below
        tmp_ctx = tempfile.TemporaryDirectory(prefix="ha-update-")
    else:
        print(f"error: source is neither an existing file nor a URL: {args.source}",
              file=sys.stderr)
        return 1

    with tmp_ctx as tmp:
        tmp = Path(tmp)
        staging = tmp / "staging"

        if zip_path is None:
            zip_path = tmp / "update.zip"
            print(f"downloading {args.source} ...")
            download(args.source, zip_path)

        if not zipfile.is_zipfile(zip_path):
            print(f"error: not a valid zip: {zip_path}", file=sys.stderr)
            return 1

        with zipfile.ZipFile(zip_path) as zf:
            bad = zf.testzip()
            if bad:
                print(f"error: corrupt zip entry: {bad}", file=sys.stderr)
                return 1
            names = [n for n in zf.namelist() if not n.endswith("/")]
            if "manifest.txt" not in names:
                print("error: manifest.txt missing from zip", file=sys.stderr)
                return 1
            entries = [n for n in names if n != "manifest.txt"]
            for name in entries:
                reason = forbidden_reason(name)
                if reason:
                    print(f"error: refusing zip entry {name!r}: {reason}", file=sys.stderr)
                    return 1
            zf.extractall(staging, members=entries)

        changed = []
        for src in sorted(p for p in staging.rglob("*") if p.is_file()):
            rel = src.relative_to(staging)
            dst = target / rel
            if not dst.exists():
                changed.append(("add", rel))
            elif dst.read_bytes() != src.read_bytes():
                changed.append(("update", rel))

        if not changed:
            print("nothing to update; target already matches the zip")
            return 0

        if args.dry_run:
            for action, rel in changed:
                path = rel.as_posix()
                if action == "add":
                    print(f"{c('new file:', _GREEN)}   {path}")
                elif action == "delete":
                    print(f"{c('deleted:  ', _RED)}   {path}")
                else:
                    print_update_diff(rel, (target / rel).read_bytes(),
                                      (staging / rel).read_bytes())
            print(c("dry run: no files written", _YELLOW))
            return 0

        for action, rel in changed:
            color = _GREEN if action == "add" else _YELLOW
            print(f"  {c(action, color)} {rel.as_posix()}")

        if not args.no_backup:
            backup = target / f".backup-{datetime.now():%Y%m%d-%H%M%S}"
            for _, rel in changed:
                dst = target / rel
                if dst.exists():
                    dest = backup / rel
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(dst, dest)
            print(f"backup -> {backup}")

        for src in sorted(p for p in staging.rglob("*") if p.is_file()):
            rel = src.relative_to(staging)
            dst = target / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, dst)

    print(c(f"done: {len(changed)} file(s) applied to {target}", _GREEN))
    print(c("reminder: restart the service, e.g. sudo systemctl restart home-assistant",
            _YELLOW))
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Download a home-assistant-server zip from an OSS temp URL and update the target machine.

Usage: python3 server_updator.py <oss-temp-url> [--target /opt/home-assistant]
                                                 [--dry-run] [--no-backup]
stdlib only; self-contained for the target machine.
"""

import argparse
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
    parser.add_argument("url", help="OSS temp (signed) URL of the zip")
    parser.add_argument("--target", type=Path, default=Path("/opt/home-assistant"))
    parser.add_argument("--dry-run", action="store_true",
                        help="show what would change, write nothing")
    parser.add_argument("--no-backup", action="store_true")
    args = parser.parse_args()

    target = args.target.resolve()
    if not target.is_dir():
        print(f"error: target not found: {target}", file=sys.stderr)
        return 1

    with tempfile.TemporaryDirectory(prefix="ha-update-") as tmp:
        tmp = Path(tmp)
        zip_path = tmp / "update.zip"
        staging = tmp / "staging"

        print(f"downloading {args.url} ...")
        download(args.url, zip_path)

        if not zipfile.is_zipfile(zip_path):
            print("error: downloaded file is not a valid zip", file=sys.stderr)
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

        for action, rel in changed:
            print(f"  {action:6s} {rel.as_posix()}")

        if args.dry_run:
            print("dry run: no files written")
            return 0

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

    print(f"done: {len(changed)} file(s) applied to {target}")
    print("reminder: restart the service, e.g. sudo systemctl restart home-assistant")
    return 0


if __name__ == "__main__":
    sys.exit(main())

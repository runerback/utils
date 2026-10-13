#!/usr/bin/env python3
"""Pack ./server code files into home-assistant-server-<timestamp>.zip for OSS upload."""

import argparse
import hashlib
import sys
import zipfile
from datetime import datetime
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SERVER_DIR = REPO_ROOT / "server"

EXCLUDED_DIRS = {"__pycache__", ".pytest_cache", "tests"}
EXCLUDED_PATTERNS = {".env*", "*.db", "*.log", "*.pyc"}
EXCLUDED_FILES = {"home-assistant.nginx", "home-assistant.service"}
EXCLUDED_SUBTREES: set[Path] = set()


def is_excluded(rel: Path) -> bool:
    if rel.name in EXCLUDED_FILES:
        return True
    if any(part in EXCLUDED_DIRS for part in rel.parts):
        return True
    if any(rel.match(pat) for pat in EXCLUDED_PATTERNS):
        return True
    for subtree in EXCLUDED_SUBTREES:
        if rel == subtree or subtree in rel.parents:
            return True
    return False


def collect_files():
    files = []
    for path in sorted(SERVER_DIR.rglob("*")):
        if not path.is_file():
            continue
        rel = path.relative_to(SERVER_DIR)
        if not is_excluded(rel):
            files.append((path, rel))
    return files


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=REPO_ROOT / "dist",
                        help="output directory (default: ./dist)")
    args = parser.parse_args()

    if not SERVER_DIR.is_dir():
        print(f"error: server directory not found: {SERVER_DIR}", file=sys.stderr)
        return 1

    files = collect_files()
    if not files:
        print("error: no files to pack", file=sys.stderr)
        return 1

    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    args.out.mkdir(parents=True, exist_ok=True)
    zip_path = args.out / f"home-assistant-server-{timestamp}.zip"

    manifest_lines = [f"timestamp: {timestamp}", ""]
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for path, rel in files:
            arcname = rel.as_posix()
            zf.write(path, arcname)
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            manifest_lines.append(f"{digest}  {arcname}")
    zf_manifest = "\n".join(manifest_lines) + "\n"
    with zipfile.ZipFile(zip_path, "a", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("manifest.txt", zf_manifest)

    print(f"packed {len(files)} files -> {zip_path}")
    for _, rel in files:
        print(f"  {rel.as_posix()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

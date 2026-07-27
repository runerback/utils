import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

try:
    subprocess.run(
        [str(REPO_ROOT / ".venv" / "Scripts" / "python.exe"), "scripts/generate_file_types.py"],
        cwd=REPO_ROOT,
        check=True,
    )
except Exception as e:
    print(f"Warning: could not generate file types: {e}", file=sys.stderr)

from app import create_app
from app.sockets import socketio

app = create_app()

if __name__ == "__main__":
    socketio.run(app, host="0.0.0.0", port=5000, debug=False)

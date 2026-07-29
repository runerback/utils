#!/usr/bin/env python3
"""Fake Ollama server for offline development of the Translator app."""

import datetime
import json
import re
import uuid
from http.server import BaseHTTPRequestHandler, HTTPServer


def detect_mode(content: str) -> str:
    content_lower = content.lower()
    if "beginner-level english" in content_lower or "cefr a2" in content_lower:
        return "simplify"
    if "simplified chinese" in content_lower:
        return "chinese"
    return "english"


def fake_response(mode: str) -> dict:
    if mode == "simplify":
        text = "The main character felt very bored and worried about life. This feeling was always there, even when he was awake."
    elif mode == "chinese":
        text = "从前，有一对老夫妻。"
    else:
        text = "Once upon a time, there lived an old man and an old woman in a certain place."

    now = datetime.datetime.utcnow().isoformat(timespec="microseconds") + "Z"
    return {
        "model": "qwen3:14b",
        "created_at": now,
        "message": {"role": "assistant", "content": text},
        "done": True,
        "done_reason": "stop",
        "total_duration": 500000000,
        "load_duration": 200000000,
        "prompt_eval_count": 55,
        "prompt_eval_duration": 50000000,
        "eval_count": 20,
        "eval_duration": 250000000,
    }


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        print(f"[{datetime.datetime.now().isoformat()}] {format % args}")

    def _send_json(self, status: int, body: dict):
        data = json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        if self.path != "/api/chat":
            self._send_json(404, {"error": "not found"})
            return

        try:
            length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(length)
            request = json.loads(body.decode("utf-8"))
            content = request.get("messages", [{}])[0].get("content", "")
            mode = detect_mode(content)
            print(f"Detected mode: {mode}")
            print(f"Prompt preview: {content[:120]!r}")
            self._send_json(200, fake_response(mode))
        except Exception as e:
            self._send_json(500, {"error": str(e)})

    def do_GET(self):
        if self.path == "/":
            self._send_json(200, {"status": "fake ollama running"})
        else:
            self._send_json(404, {"error": "not found"})


def main():
    host = "0.0.0.0"
    port = 11434
    server = HTTPServer((host, port), Handler)
    print(f"Fake Ollama server listening on http://{host}:{port}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down...")


if __name__ == "__main__":
    main()

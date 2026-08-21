#!/usr/bin/env python3
"""ArgosTranslate HTTP server for the Translator app.

Exposes endpoints:
    GET  /         - server status
    GET  /ready    - readiness probe
    POST /translate
    {
        "text": "text to translate",
        "source": "<lang-code>",
        "target": "<lang-code>"
    }

Response:
    {
        "text": "translated text"
    }

The HTTP server starts immediately. Model download and warm-up run in the
background; requests receive 503 with a clear message until initialization
finishes.
"""

import datetime
import os
import sys
import threading
import traceback
import urllib.parse

# Read HF_ENDPOINT before importing argostranslate / huggingface_hub so any
# library that checks it at import time sees the value.
HF_ENDPOINT = os.environ.get("HF_ENDPOINT", "").rstrip("/")
_HUGGINGFACE_ORIGIN = "https://huggingface.co"

if HF_ENDPOINT:
    print(f"HF_ENDPOINT detected: {HF_ENDPOINT}")
    os.environ.setdefault("HF_ENDPOINT", HF_ENDPOINT)

# Patch requests.get to redirect direct HuggingFace.co URLs through HF_ENDPOINT.
try:
    import requests

    _orig_requests_get = requests.get

    def _patched_requests_get(url, **kwargs):
        if HF_ENDPOINT and isinstance(url, str) and url.startswith(_HUGGINGFACE_ORIGIN + "/"):
            parsed = urllib.parse.urlparse(url)
            mirror = urllib.parse.urlparse(HF_ENDPOINT)
            redirected = urllib.parse.urlunparse(
                (
                    mirror.scheme or parsed.scheme,
                    mirror.netloc,
                    parsed.path,
                    parsed.params,
                    parsed.query,
                    parsed.fragment,
                )
            )
            print(f"[{timestamp()}] Redirecting HF download via HF_ENDPOINT: {redirected}")
            url = redirected
        return _orig_requests_get(url, **kwargs)

    requests.get = _patched_requests_get
except ImportError:
    requests = None  # type: ignore

# Also patch urllib.request.urlopen for dependencies that use stdlib urllib.
try:
    import urllib.request

    _orig_urlopen = urllib.request.urlopen

    def _patched_urlopen(url, *args, **kwargs):
        if HF_ENDPOINT and isinstance(url, str) and url.startswith(_HUGGINGFACE_ORIGIN + "/"):
            parsed = urllib.parse.urlparse(url)
            mirror = urllib.parse.urlparse(HF_ENDPOINT)
            redirected = urllib.parse.urlunparse(
                (
                    mirror.scheme or parsed.scheme,
                    mirror.netloc,
                    parsed.path,
                    parsed.params,
                    parsed.query,
                    parsed.fragment,
                )
            )
            print(f"[{timestamp()}] Redirecting HF download via HF_ENDPOINT: {redirected}")
            url = redirected
        return _orig_urlopen(url, *args, **kwargs)

    urllib.request.urlopen = _patched_urlopen
except Exception:
    pass

import argostranslate.package
import argostranslate.translate
from flask import Flask, jsonify, request

app = Flask(__name__)
app.config["JSON_AS_ASCII"] = False

DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 11435

# Languages the Android client currently asks for.
REQUIRED_PACKAGES = [
    ("en", "zh"),  # English -> Simplified Chinese
    ("fr", "en"),  # French -> English
]

_ready = False
_init_error = None
_init_phase = "starting"
_init_lock = threading.Lock()


def timestamp():
    return datetime.datetime.now().isoformat()


def log(msg: str):
    print(f"[{timestamp()}] {msg}")
    sys.stdout.flush()


def install_required_packages():
    """Ensure the language packages used by the app are installed."""
    installed = argostranslate.package.get_installed_packages()
    log(f"Found installed packages: {[(p.from_code, p.to_code) for p in installed]}")

    missing = []
    for from_code, to_code in REQUIRED_PACKAGES:
        already_installed = any(
            p.from_code == from_code and p.to_code == to_code for p in installed
        )
        if already_installed:
            log(f"Package {from_code}->{to_code} already installed; skipping.")
        else:
            missing.append((from_code, to_code))

    if not missing:
        log("All required packages are already installed.")
        return

    log(f"Missing packages: {missing}. Fetching package index from argostranslate...")
    argostranslate.package.update_package_index()
    available_packages = argostranslate.package.get_available_packages()
    log(f"Package index fetched. {len(available_packages)} packages available.")

    for from_code, to_code in missing:
        log(f"Looking for package {from_code}->{to_code} in index...")
        package = next(
            (
                p
                for p in available_packages
                if p.from_code == from_code and p.to_code == to_code
            ),
            None,
        )
        if package is None:
            raise RuntimeError(
                f"No argostranslate package available for {from_code} -> {to_code}"
            )

        download_url = getattr(package, "download_url", None) or getattr(package, "url", None) or "unknown URL"
        log(f"Downloading package {from_code}->{to_code} from {download_url} ...")
        try:
            package_path = package.download()
        except Exception as e:
            log(f"Failed to download package {from_code}->{to_code} from {download_url}: {e}")
            raise
        log(f"Downloaded package {from_code}->{to_code} to {package_path}; installing...")
        argostranslate.package.install_from_path(package_path)
        log(f"Installed package {from_code}->{to_code}.")


def warm_up_translator():
    """Run a tiny translation so argostranslate loads models into memory."""
    log("Warming up translator models...")
    for from_code, to_code in REQUIRED_PACKAGES:
        log(f"  Warm-up translation {from_code}->{to_code}...")
        argostranslate.translate.translate("warm-up", from_code, to_code)
        log(f"  Warm-up {from_code}->{to_code} complete.")
    log("Translator warm-up finished.")


def initialize():
    """Background initialization: install packages and warm up models."""
    global _ready, _init_error, _init_phase
    try:
        set_phase("checking packages")
        install_required_packages()
        set_phase("warming up translator")
        warm_up_translator()
        set_phase("ready")
        with _init_lock:
            _ready = True
        log("Server is ready.")
    except Exception as e:
        log(f"Initialization failed: {e}")
        traceback.print_exc()
        set_phase(f"failed: {e}")
        with _init_lock:
            _init_error = str(e)


def set_phase(phase: str):
    global _init_phase
    with _init_lock:
        _init_phase = phase
    log(f"Initialization phase: {phase}")


def do_translate(text: str, source: str, target: str) -> str:
    """Translate text from the explicit source language to target language."""
    log(f"  Calling argostranslate.translate.translate({text[:60]!r}, {source!r}, {target!r})")
    result = argostranslate.translate.translate(text, source, target)
    log(f"  argostranslate returned: {result[:60]!r}")
    return result


@app.route("/", methods=["GET"])
def root():
    with _init_lock:
        ready = _ready
        error = _init_error
        phase = _init_phase
    return jsonify(
        {
            "status": "argostranslate server running",
            "ready": ready,
            "phase": phase,
            "error": error,
        }
    )


@app.route("/ready", methods=["GET"])
def ready():
    with _init_lock:
        ready_flag = _ready
        error = _init_error
        phase = _init_phase
    status = 200 if ready_flag else 503
    return jsonify({"ready": ready_flag, "phase": phase, "error": error}), status


@app.route("/translate", methods=["POST"])
def translate():
    with _init_lock:
        is_ready = _ready
        error = _init_error

    if not is_ready:
        return (
            jsonify(
                {
                    "error": "server not ready",
                    "message": "Translator is still initializing, try again shortly.",
                    "init_error": error,
                }
            ),
            503,
        )

    try:
        body = request.get_json(silent=True) or {}
        log(f"Incoming POST /translate from {request.remote_addr}: {body}")

        text = body.get("text", "")
        source = body.get("source", "auto")
        target = body.get("target", "")

        if not text:
            log("  -> 400: missing 'text'")
            return jsonify({"error": "missing 'text'"}), 400
        if not source:
            log("  -> 400: missing 'source'")
            return jsonify({"error": "missing 'source'"}), 400
        if source == "auto":
            log("  -> 400: source 'auto' not supported")
            return (
                jsonify(
                    {
                        "error": "source 'auto' is not supported; provide a language code"
                    }
                ),
                400,
            )
        if not target:
            log("  -> 400: missing 'target'")
            return jsonify({"error": "missing 'target'"}), 400

        log(f"  Translating: source={source} target={target} text={text[:120]!r}")
        translated = do_translate(text, source, target)
        log(f"  Translation succeeded: {translated[:120]!r}")
        return jsonify({"text": translated})
    except Exception as e:
        log(f"  Translation failed: {e}")
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


def main():
    log("=" * 50)
    log("Starting ArgosTranslate server...")
    log(f"Host: {DEFAULT_HOST}, Port: {DEFAULT_PORT}")
    log("=" * 50)

    init_thread = threading.Thread(target=initialize, daemon=True)
    init_thread.start()

    log("HTTP server is listening. Model setup is running in the background.")
    log("Poll GET /ready to check initialization progress.")
    app.run(host=DEFAULT_HOST, port=DEFAULT_PORT, threaded=True)


if __name__ == "__main__":
    main()

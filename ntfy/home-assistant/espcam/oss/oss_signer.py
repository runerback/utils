#!/usr/bin/env python3
"""
OSS presigning service

GET /sign?device=esp32cam-01&put_expires=300&get_expires=3600
Returns:
{
  "object":  "<prefix>/esp32cam-01/20260830/1712345678-ab12cd.jpg",
  "put_url": "https://<bucket>.oss-<region>.aliyuncs.com/<prefix>/...?x-oss-signature=...",   # direct upload, 5 min
  "get_url": "https://<bucket>.oss-<region>.aliyuncs.com/<prefix>/...?x-oss-signature=..."    # photo viewing, default 1 h
}

Run:
  pip install oss2 flask
  export OSS_ACCESS_KEY_ID=xxx
  export OSS_ACCESS_KEY_SECRET=yyy
  export OSS_REGION=region
  export OSS_CAM_BUCKET=bucket
  OSS_CAM_BUCKET_PREFIX=prefix
  python3 oss_sign_server.py [--host 0.0.0.0] [--port 8090]
"""

import argparse
import time
import uuid
import os

import oss2
from flask import Flask, jsonify, request
from oss2.credentials import EnvironmentVariableCredentialsProvider

REGION   = os.getenv("OSS_REGION", "")
BUCKET   = os.getenv("OSS_CAM_BUCKET", "")
PREFIX   = os.getenv("OSS_CAM_BUCKET_PREFIX", "")
DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 8090

app = Flask(__name__)

STATUS = "ok"
if not REGION or REGION == "unknown":
    STATUS = "error: unknown region"
elif not BUCKET or BUCKET == "unknown":
    STATUS = "error: unknown bucket"
elif not PREFIX or PREFIX == "unknown":
    STATUS = "error: unknown bucket prefix"
else:
    try:
        auth = oss2.ProviderAuthV4(EnvironmentVariableCredentialsProvider())
        bucket = oss2.Bucket(auth, f"https://oss-${REGION}.aliyuncs.com", BUCKET, region=REGION)
    except Exception as e:
        STATUS = e.__str__()
    # end try
# end if

@app.get("/sign")
def sign():
    if not bucket:
        return jsonify({"error": "wrong confgiure"}), 403
    # end if

    device = request.args.get("device", "unknown")
    # Simple allowlist so nothing else on the internal network can mint URLs
    if not device.startswith("esp32cam-"):
        return jsonify({"error": "unknown device"}), 403

    put_expires = min(int(request.args.get("put_expires", 300)), 3600)
    get_expires = min(int(request.args.get("get_expires", 3600)), 7 * 24 * 3600)

    day = time.strftime("%Y%m%d")
    key = f"{PREFIX}/{device}/{day}/{int(time.time())}-{uuid.uuid4().hex[:6]}.jpg"

    # slash_safe=True: keep the "/" in the key unescaped so the URL is usable as-is.
    # Content-Type: image/jpeg is signed into the URL — the ESP32 MUST send the
    # byte-identical header when uploading; this way browsers open get_url as an
    # inline image instead of a download.
    put_url = bucket.sign_url("PUT", key, put_expires, slash_safe=True,
                              headers={"Content-Type": "image/jpeg"})
    get_url = bucket.sign_url("GET", key, get_expires, slash_safe=True)

    return jsonify({"object": key, "put_url": put_url, "get_url": get_url})


@app.get("/health")
def health():
    return "ok"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="OSS presigning service")
    parser.add_argument("--host", default=DEFAULT_HOST, help="interface to bind to")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help="port to listen on")
    args = parser.parse_args()
    app.run(host=args.host, port=args.port)

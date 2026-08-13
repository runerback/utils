import json
import urllib.request
import uuid
import websocket
import time
import sys

server_address = "127.0.0.1:8188"
client_id = str(uuid.uuid4())

# Workflow: Load Image -> ATS-Resize -> ATS-GrayScale -> ATS-Rotate -> ATS-Crop -> ATS-Invert -> ATS-Xor -> Save Image
workflow = {
    "1": {
        "class_type": "LoadImage",
        "inputs": {"image": "example.png"}
    },
    "2": {
        "class_type": "ATS-Resize",
        "inputs": {
            "image": ["1", 0],
            "interval": 500,
            "mode": "percent",
            "mode.percent": 50
        }
    },
    "3": {
        "class_type": "ATS-GrayScale",
        "inputs": {
            "image": ["2", 0],
            "interval": ["2", 1]
        }
    },
    "4": {
        "class_type": "ATS-Rotate",
        "inputs": {
            "image": ["3", 0],
            "interval": ["3", 1],
            "direction": "clockwise",
            "degree": 45
        }
    },
    "5": {
        "class_type": "ATS-Crop",
        "inputs": {
            "image": ["4", 0],
            "interval": ["4", 1],
            "x": 10,
            "y": 10,
            "w": 100,
            "h": 100
        }
    },
    "6": {
        "class_type": "ATS-Invert",
        "inputs": {
            "image": ["5", 0],
            "interval": ["5", 1]
        }
    },
    "7": {
        "class_type": "ATS-Xor",
        "inputs": {
            "image": ["6", 0],
            "interval": ["6", 1],
            "seed": 12345678
        }
    },
    "8": {
        "class_type": "SaveImage",
        "inputs": {
            "images": ["7", 0],
            "filename_prefix": "ats_test"
        }
    }
}

def queue_prompt(prompt, prompt_id):
    p = {"prompt": prompt, "client_id": client_id, "prompt_id": prompt_id}
    data = json.dumps(p).encode("utf-8")
    req = urllib.request.Request(f"http://{server_address}/prompt", data=data)
    return urllib.request.urlopen(req).read()

prompt_id = str(uuid.uuid4())
print("Queueing prompt", prompt_id)
queue_prompt(workflow, prompt_id)

ws = websocket.WebSocket()
ws.connect(f"ws://{server_address}/ws?clientId={client_id}")

start = time.time()
preview_count = 0
errors = []
while True:
    try:
        out = ws.recv()
    except Exception as e:
        print("WS error:", e)
        break
    if isinstance(out, str):
        message = json.loads(out)
        msg_type = message.get("type")
        data = message.get("data", {})
        if msg_type == "executing" and data.get("node") is None and data.get("prompt_id") == prompt_id:
            print("Execution complete")
            break
        if msg_type == "execution_error" and data.get("prompt_id") == prompt_id:
            errors.append(data)
            print("ERROR:", data)
            break
    else:
        preview_count += 1
        print(f"Preview #{preview_count} ({len(out)} bytes)")

ws.close()
print(f"Total time: {time.time() - start:.2f}s, previews: {preview_count}")
if errors:
    sys.exit(1)

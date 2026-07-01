import asyncio
import json
import os
import threading
from typing import Any

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.staticfiles import StaticFiles
import uvicorn

from .config import AudioConfig
from .generator import AudioGenerator


NODE_METADATA = [
    {"id": "source", "name": "Noise Source", "locked": True},
    {"id": "widening", "name": "Stereo Widening"},
    {"id": "reverb", "name": "Reverb"},
    {"id": "lowpass", "name": "Low-Pass"},
    {"id": "dcblocker", "name": "DC Blocker"},
    {"id": "saturation", "name": "Saturation"},
    {"id": "wave", "name": "Wave LFO"},
    {"id": "gain", "name": "Gain", "locked": True},
]


def _config_to_dict(config: AudioConfig) -> dict[str, Any]:
    return {
        "host": config.host,
        "port": config.port,
        "control_port": config.control_port,
        "sample_rate": config.sample_rate,
        "channels": config.channels,
        "chunk_size": config.chunk_size,
        "gain": config.gain,
        "noise_type": config.noise_type,
        "leak": config.leak,
        "seed": config.seed,
        "surround": config.surround,
        "reverb": config.reverb,
        "softness": config.softness,
        "wave": config.wave,
        "wave_rate": config.wave_rate,
        "bypass_source": config.bypass_source,
        "bypass_widening": config.bypass_widening,
        "bypass_reverb": config.bypass_reverb,
        "bypass_lowpass": config.bypass_lowpass,
        "bypass_dcblocker": config.bypass_dcblocker,
        "bypass_saturation": config.bypass_saturation,
        "bypass_wave": config.bypass_wave,
        "bypass_gain": config.bypass_gain,
        "web_port": config.web_port,
    }


_CONFIG_KEYS = {
    "noise_type",
    "leak",
    "gain",
    "surround",
    "reverb",
    "softness",
    "wave",
    "wave_rate",
    "bypass_source",
    "bypass_widening",
    "bypass_reverb",
    "bypass_lowpass",
    "bypass_dcblocker",
    "bypass_saturation",
    "bypass_wave",
    "bypass_gain",
}


def create_app(config: AudioConfig, generator: AudioGenerator) -> FastAPI:
    app = FastAPI()

    @app.get("/api/config")
    def get_config() -> dict[str, Any]:
        return _config_to_dict(config)

    @app.post("/api/config")
    def post_config(data: dict[str, Any]) -> dict[str, Any]:
        filtered = {k: v for k, v in data.items() if k in _CONFIG_KEYS}
        generator.update_config(**filtered)
        return _config_to_dict(config)

    @app.get("/api/nodes")
    def get_nodes() -> dict[str, list[dict[str, Any]]]:
        return {"nodes": NODE_METADATA}

    @app.websocket("/ws")
    async def websocket_endpoint(websocket: WebSocket) -> None:
        await websocket.accept()
        client_queue = generator.register_client()
        try:
            while True:
                waveforms = generator.get_visible_waveforms()
                state = {
                    "type": "state",
                    "waveforms": waveforms,
                    "config": _config_to_dict(config),
                }
                await websocket.send_text(json.dumps(state))
                try:
                    message = await asyncio.wait_for(websocket.receive_text(), timeout=0.05)
                    cmd = json.loads(message)
                    if cmd.get("type") == "toggle":
                        node = cmd.get("node")
                        flag = f"bypass_{node}"
                        if hasattr(config, flag):
                            current = getattr(config, flag)
                            generator.update_config(**{flag: not current})
                except asyncio.TimeoutError:
                    pass
        except WebSocketDisconnect:
            pass
        finally:
            generator.unregister_client(client_queue)

    static_dir = os.path.join(os.path.dirname(__file__), "static")
    if os.path.isdir(static_dir):
        app.mount("/", StaticFiles(directory=static_dir, html=True), name="static")

    return app


class WebServer:
    def __init__(self, config: AudioConfig, generator: AudioGenerator):
        self.config = config
        self.generator = generator
        self._app = create_app(config, generator)
        self._server: uvicorn.Server | None = None
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        host = self.config.host if self.config.host != "0.0.0.0" else "0.0.0.0"
        cfg = uvicorn.Config(self._app, host=host, port=self.config.web_port, loop="asyncio")
        self._server = uvicorn.Server(cfg)
        self._thread = threading.Thread(target=self._server.run, daemon=True)
        self._thread.start()
        print(f"Web UI server listening on http://{host}:{self.config.web_port}")

    def stop(self) -> None:
        if self._server:
            self._server.should_exit = True
        if self._thread:
            self._thread.join(timeout=2.0)

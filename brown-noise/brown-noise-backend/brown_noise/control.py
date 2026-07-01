import json
import socket
import threading

from .config import AudioConfig
from .generator import AudioGenerator


class ControlServer:
    def __init__(self, config: AudioConfig, generator: AudioGenerator):
        self.config = config
        self.generator = generator
        self._socket: socket.socket | None = None
        self._running = False
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        self._socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._socket.bind((self.config.host, self.config.control_port))
        self._socket.listen(5)
        self._running = True
        self._thread = threading.Thread(target=self._accept_loop, daemon=True)
        self._thread.start()
        print(f"Control server listening on {self.config.host}:{self.config.control_port}")

    def stop(self) -> None:
        self._running = False
        if self._socket:
            try:
                self._socket.close()
            except Exception:
                pass
        if self._thread:
            self._thread.join(timeout=2.0)

    def _accept_loop(self) -> None:
        while self._running:
            try:
                conn, addr = self._socket.accept()
            except OSError:
                break
            threading.Thread(
                target=self._handle_client,
                args=(conn, addr),
                daemon=True,
            ).start()

    def _handle_client(self, conn: socket.socket, addr: tuple) -> None:
        print(f"Control client connected: {addr}")
        try:
            with conn.makefile("rw") as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        cmd = json.loads(line)
                        self._apply_command(cmd)
                        f.write(json.dumps({"ok": True}) + "\n")
                        f.flush()
                    except Exception as e:
                        f.write(json.dumps({"ok": False, "error": str(e)}) + "\n")
                        f.flush()
        except Exception:
            pass
        finally:
            print(f"Control client disconnected: {addr}")
            try:
                conn.close()
            except Exception:
                pass

    def _apply_command(self, cmd: dict) -> None:
        noise_type = cmd.get("noise_type")
        if noise_type is not None and noise_type not in {"brown", "white", "pink", "tune"}:
            raise ValueError(f"Unknown noise type: {noise_type}")

        self.generator.update_config(
            noise_type=noise_type,
            leak=cmd.get("leak"),
            gain=cmd.get("gain"),
            surround=cmd.get("surround"),
            reverb=cmd.get("reverb"),
            softness=cmd.get("softness"),
            wave=cmd.get("wave"),
            wave_rate=cmd.get("wave_rate"),
            bypass_source=cmd.get("bypass_source"),
            bypass_widening=cmd.get("bypass_widening"),
            bypass_reverb=cmd.get("bypass_reverb"),
            bypass_lowpass=cmd.get("bypass_lowpass"),
            bypass_dcblocker=cmd.get("bypass_dcblocker"),
            bypass_saturation=cmd.get("bypass_saturation"),
            bypass_wave=cmd.get("bypass_wave"),
            bypass_gain=cmd.get("bypass_gain"),
        )
        print(f"Updated config: {cmd}")

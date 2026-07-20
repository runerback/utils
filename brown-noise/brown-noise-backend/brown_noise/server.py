import queue
import socket
import struct
import threading

from .config import AudioConfig
from .generator import AudioGenerator


class TcpAudioServer:
    def __init__(self, config: AudioConfig, generator: AudioGenerator):
        self.config = config
        self.generator = generator
        self._socket: socket.socket | None = None
        self._running = False
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        self._socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._socket.bind((self.config.host, self.config.port))
        self._socket.listen(5)
        self._running = True
        self._thread = threading.Thread(target=self._accept_loop, daemon=True)
        self._thread.start()
        print(f"TCP audio server listening on {self.config.host}:{self.config.port}")

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
            print(f"Client connected: {addr}")
            client_queue = self.generator.register_client()
            threading.Thread(
                target=self._client_loop,
                args=(conn, addr, client_queue),
                daemon=True,
            ).start()

    def _client_loop(
        self,
        conn: socket.socket,
        addr: tuple,
        client_queue: queue.Queue[bytes],
    ) -> None:
        try:
            # Send a tiny header describing the stream format.
            fmt_header = struct.pack(
                "<IIH",
                self.config.sample_rate,
                self.config.channels,
                16,  # bits per sample
            )
            conn.sendall(fmt_header)

            while self._running:
                try:
                    packet = client_queue.get(timeout=1.0)
                except queue.Empty:
                    continue
                conn.sendall(packet)
        except (ConnectionResetError, BrokenPipeError, OSError):
            pass
        finally:
            print(f"Client disconnected: {addr}")
            self.generator.unregister_client(client_queue)
            try:
                conn.close()
            except Exception:
                pass

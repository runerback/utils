"""Subscribe to device status topics via mosquitto_sub alias.

The command used (default 'mosquitto_sub') is expected to be a shell alias on the
server that already includes host/port/cafile/credentials. Because aliases are
resolved by the shell, the subprocess runs through the configured login shell.
"""
import asyncio
import shutil
import shlex

from .. import config


class MqttListener:
    """Runs mosquitto_sub in a subprocess and forwards status messages."""

    def __init__(self, topic: str = "devices/+/status") -> None:
        self.topic = topic
        self._task: asyncio.Task | None = None
        self._proc: asyncio.subprocess.Process | None = None
        self._callbacks: list[callable] = []

    def on_status(self, callback: callable) -> None:
        self._callbacks.append(callback)

    def _emit(self, device_id: str, payload: str) -> None:
        for cb in self._callbacks:
            try:
                cb(device_id, payload)
            except Exception:
                pass

    async def start(self) -> None:
        if self._task is not None:
            return
        self._task = asyncio.create_task(self._run())

    async def _run(self) -> None:
        while True:
            try:
                await self._loop()
            except asyncio.CancelledError:
                break
            except Exception as exc:
                print(f"[mqtt] listener error: {exc}, reconnecting in 5s")
                await asyncio.sleep(5)

    async def _loop(self) -> None:
        shell = shutil.which("bash") or shutil.which("sh")
        if not shell:
            raise RuntimeError("no shell found for alias resolution")

        topic_quoted = shlex.quote(self.topic)
        cmd = f"{config.MOSQUITTO_SUB_BIN} -v -t {topic_quoted}"

        self._proc = await asyncio.create_subprocess_shell(
            cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
            executable=shell,
        )

        if self._proc.stdout is None:
            return

        async for line in self._proc.stdout:
            text = line.decode("utf-8", "ignore").strip()
            if not text:
                continue
            # mosquitto_sub -v prints: "<topic> <payload>"
            parts = text.split(" ", 1)
            topic = parts[0]
            payload = parts[1] if len(parts) > 1 else ""
            topic_parts = topic.split("/")
            if len(topic_parts) == 3 and topic_parts[0] == "devices" and topic_parts[2] == "status":
                device_id = topic_parts[1]
                self._emit(device_id, payload)

    async def stop(self) -> None:
        if self._task is not None:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
            self._task = None
        if self._proc is not None and self._proc.returncode is None:
            self._proc.terminate()
            try:
                await asyncio.wait_for(self._proc.wait(), timeout=5)
            except asyncio.TimeoutError:
                self._proc.kill()
            self._proc = None

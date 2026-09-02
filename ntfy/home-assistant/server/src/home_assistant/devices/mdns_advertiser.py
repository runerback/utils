"""Advertise the provisioning API over mDNS so ESPmDNS can find it."""
import socket

from zeroconf import ServiceInfo, Zeroconf

_SERVICE_TYPE = "_devprov._tcp.local."
_SERVICE_NAME = "device-server._devprov._tcp.local."


class MdnsAdvertiser:
    """Synchronous mDNS advertiser; wraps Zeroconf."""

    def __init__(
        self,
        port: int,
        service_type: str = _SERVICE_TYPE,
        service_name: str = _SERVICE_NAME,
        ip: str | None = None,
    ) -> None:
        self.port = port
        self.service_type = service_type
        self.service_name = service_name
        self.ip = ip or _lan_ip()
        self._zeroconf: Zeroconf | None = None

    def start(self) -> None:
        info = ServiceInfo(
            self.service_type,
            self.service_name,
            addresses=[socket.inet_aton(self.ip)],
            port=self.port,
            properties={b"ver": b"1"},
        )
        self._zeroconf = Zeroconf()
        self._zeroconf.register_service(info)
        print(f"[mdns] advertising {self.service_name} -> {self.ip}:{self.port}")

    def stop(self) -> None:
        if self._zeroconf is not None:
            self._zeroconf.close()
            self._zeroconf = None


def _lan_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()

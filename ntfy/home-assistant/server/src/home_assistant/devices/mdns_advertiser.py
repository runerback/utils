"""Advertise the provisioning API over mDNS so ESPmDNS can find it."""
import logging
import os
import socket
import threading
import time

from zeroconf import IPVersion, ServiceInfo, Zeroconf

logger = logging.getLogger(__name__)

_SERVICE_TYPE = "_devprov._tcp.local."
_SERVICE_NAME = "device-server._devprov._tcp.local."

_RETRY_DELAY = 5.0
_RETRY_DELAY_MAX = 60.0

_PROCESS_START = time.monotonic()


class MdnsAdvertiser:
    """mDNS advertiser; registration runs on a background thread so a slow
    or blocked zeroconf engine (e.g. right after boot on a loaded box)
    neither delays nor kills API server startup."""

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
        self._stop_event = threading.Event()
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        self._stop_event.clear()
        self._thread = threading.Thread(
            target=self._run, name="mdns-advertiser", daemon=True,
        )
        self._thread.start()

    def _run(self) -> None:
        thread_start = time.monotonic()
        logger.info(
            "[mdns] advertiser thread started, target %s:%d [%s]",
            self.ip, self.port, _diag_snapshot(),
        )
        info = ServiceInfo(
            self.service_type,
            self.service_name,
            addresses=[socket.inet_aton(self.ip)],
            port=self.port,
            properties={b"ver": b"1"},
        )
        delay = _RETRY_DELAY
        attempt = 0
        while not self._stop_event.is_set():
            attempt += 1
            zeroconf: Zeroconf | None = None
            t0 = time.monotonic()
            phase = "zeroconf init"
            try:
                # IPv4-only, bound to just the advertised LAN interface.
                # Binding all interfaces makes zeroconf send multicast over
                # tunnels like WireGuard (wg0), which rejects it with
                # ENOKEY and can stall the engine loop.
                zeroconf = Zeroconf(
                    ip_version=IPVersion.V4Only, interfaces=[self.ip],
                )
                phase = "register"
                zeroconf.register_service(info)
            except Exception:
                now = time.monotonic()
                # e.g. EventLoopBlocked when the zeroconf engine thread is
                # starved of GIL/CPU during app startup; keep retrying.
                logger.warning(
                    "[mdns] attempt %d: %s failed after %.1fs "
                    "(thread up %.1fs, process up %.1fs); retrying in %.0fs [%s]",
                    attempt, phase, now - t0,
                    now - thread_start, now - _PROCESS_START,
                    delay, _diag_snapshot(),
                    exc_info=True,
                )
                if zeroconf is not None:
                    try:
                        zeroconf.close()
                    except Exception:
                        # close() can hit the same blocked loop; just drop it
                        pass
                self._stop_event.wait(delay)
                delay = min(delay * 2, _RETRY_DELAY_MAX)
                continue
            self._zeroconf = zeroconf
            logger.info(
                "[mdns] advertising %s -> %s:%d "
                "(attempt %d, took %.1fs, thread up %.1fs)",
                self.service_name, self.ip, self.port,
                attempt, time.monotonic() - t0, time.monotonic() - thread_start,
            )
            return
        logger.info("[mdns] stopped before registration succeeded")

    def stop(self) -> None:
        self._stop_event.set()
        if self._thread is not None:
            self._thread.join(timeout=5)
            self._thread = None
        if self._zeroconf is not None:
            try:
                self._zeroconf.close()
            except Exception:
                logger.warning("[mdns] error during shutdown", exc_info=True)
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


def _diag_snapshot() -> str:
    """One line of environment state to help explain zeroconf failures."""
    parts = []
    try:
        la = os.getloadavg()
        parts.append("load=%.2f/%.2f/%.2f" % la)
    except (AttributeError, OSError):
        pass
    parts.append(f"cpus={os.cpu_count()}")
    parts.append(f"threads={threading.active_count()}")
    try:
        parts.append(f"ifaces={[name for _, name in socket.if_nameindex()]}")
    except OSError:
        pass
    try:
        host = socket.gethostname()
        addrs = sorted({
            ai[4][0] for ai in socket.getaddrinfo(host, None, socket.AF_INET)
        })
        parts.append(f"hostname={host} host_ipv4={addrs}")
    except OSError as exc:
        parts.append(f"hostname_resolution=FAILED({exc})")
    parts.append(f"has_ipv6={socket.has_ipv6}")
    holders = _udp_port_holders(5353)
    if holders is not None:
        parts.append(f"udp5353={holders or 'free'}")
    return " ".join(parts)


def _udp_port_holders(port: int) -> list[str] | None:
    """Local addresses bound to a UDP port, parsed from /proc (Linux only).

    Returns None when /proc/net/udp* is unavailable."""
    found: list[str] = []
    seen_proc = False
    for path in ("/proc/net/udp", "/proc/net/udp6"):
        try:
            with open(path) as f:
                seen_proc = True
                next(f, None)  # header
                for line in f:
                    fields = line.split()
                    local = fields[1]
                    if int(local.rsplit(":", 1)[1], 16) == port:
                        found.append(local)
        except (OSError, IndexError, ValueError):
            continue
    return found if seen_proc else None

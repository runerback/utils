import argparse
import socket
import threading

from brown_noise import AudioConfig, AudioGenerator, ControlServer, LocalAudioPlayer, TcpAudioServer


def local_ipv4_addresses():
    try:
        hostname = socket.gethostname()
        infos = socket.getaddrinfo(hostname, None, socket.AF_INET)
        return sorted({info[4][0] for info in infos})
    except Exception:
        return []


def print_stats_loop(generator: AudioGenerator, shutdown_event: threading.Event) -> None:
    last_clients = 0
    while not shutdown_event.is_set():
        stats = generator.get_stats()
        if stats["clients"] != last_clients:
            print(f"Clients: {stats['clients']}, audio queue: {stats['audio_queue']}")
            last_clients = stats["clients"]
        generator.wait_for_stats_change()


def main():
    parser = argparse.ArgumentParser(description="Stream brown noise to Android clients.")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=54545)
    parser.add_argument("--control-port", type=int, default=54546)
    parser.add_argument("--sample-rate", type=int, default=44100)
    parser.add_argument("--channels", type=int, default=2)
    parser.add_argument("--chunk-size", type=int, default=1024)
    parser.add_argument("--gain", type=float, default=0.8)
    parser.add_argument("--type", default="brown", choices=["brown", "white", "pink", "tune"])
    parser.add_argument("--leak", type=float, default=0.99)
    parser.add_argument("--seed", type=int, default=None)
    parser.add_argument("--surround", type=float, default=0.0)
    parser.add_argument("--reverb", type=float, default=0.0)
    parser.add_argument("--softness", type=float, default=0.0)
    parser.add_argument("--wave", action="store_true")
    parser.add_argument("--wave-rate", type=float, default=0.5)
    args = parser.parse_args()

    config = AudioConfig(
        host=args.host,
        port=args.port,
        control_port=args.control_port,
        sample_rate=args.sample_rate,
        channels=args.channels,
        chunk_size=args.chunk_size,
        gain=args.gain,
        noise_type=args.type,
        leak=args.leak,
        seed=args.seed,
        surround=args.surround,
        reverb=args.reverb,
        softness=args.softness,
        wave=args.wave,
        wave_rate=args.wave_rate,
    )

    generator = AudioGenerator(config)
    player = LocalAudioPlayer(config, generator)
    server = TcpAudioServer(config, generator)
    control_server = ControlServer(config, generator)

    generator.start()
    player.start()
    server.start()
    control_server.start()

    print(
        f"Streaming {config.noise_type} noise: "
        f"{config.sample_rate} Hz, {config.channels} channels, "
        f"chunk={config.chunk_size}"
    )
    ips = local_ipv4_addresses()
    if ips:
        print(f"Server addresses: {', '.join(ips)} (port {config.port})")
    else:
        print(f"Server listening on port {config.port}")

    shutdown_event = threading.Event()
    stats_thread = threading.Thread(
        target=print_stats_loop,
        args=(generator, shutdown_event),
        daemon=True,
    )
    stats_thread.start()

    try:
        input()
    except (KeyboardInterrupt, EOFError):
        print("\nShutting down...")
    finally:
        shutdown_event.set()
        control_server.stop()
        server.stop()
        player.stop()
        generator.stop()
        stats_thread.join(timeout=2.0)


if __name__ == "__main__":
    main()

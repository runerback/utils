import { useEffect, useRef, useState } from "preact/hooks";
import { io, Socket } from "socket.io-client";
import type { Message } from "../types";

function resolveBackendUrl(): string {
  const envUrl = import.meta.env.VITE_BACKEND_URL;
  if (envUrl) return envUrl;

  const url = new URL(window.location.href);
  url.port = "5000";
  return url.origin;
}

const BACKEND = resolveBackendUrl();

export function useSocket(onMessage: (message: Message) => void) {
  const [isConnected, setIsConnected] = useState(false);
  const [lastError, setLastError] = useState<string | null>(null);
  const socketRef = useRef<Socket | null>(null);

  useEffect(() => {
    const socket = io(BACKEND, { transports: ["websocket", "polling"] });
    socketRef.current = socket;

    socket.on("connect", () => {
      setIsConnected(true);
      setLastError(null);
    });
    socket.on("disconnect", () => setIsConnected(false));
    socket.on("connect_error", (err: Error) => {
      console.error("Socket connection error:", err.message);
      setLastError(err.message);
    });
    socket.on("message:new", (message: Message) => {
      onMessage(message);
    });

    return () => {
      socket.disconnect();
    };
  }, [onMessage]);

  return { isConnected, lastError };
}

import type { Message } from "../types";

function resolveBackendUrl(): string {
  const envUrl = import.meta.env.VITE_BACKEND_URL;
  if (envUrl) return envUrl;

  // Auto-detect: same host, port 5000
  const url = new URL(window.location.href);
  url.port = "5000";
  return url.origin;
}

export const BACKEND = resolveBackendUrl();

export function resolveUrl(path: string): string {
  if (path.startsWith("http")) return path;
  return `${BACKEND}${path}`;
}

export async function fetchMessages(): Promise<Message[]> {
  const response = await fetch(`${BACKEND}/api/messages`);
  if (!response.ok) {
    throw new Error("Failed to fetch messages.");
  }
  const payload = await response.json();
  return payload.messages as Message[];
}

export async function sendMessage(formData: FormData): Promise<Message> {
  const response = await fetch(`${BACKEND}/api/messages`, {
    method: "POST",
    body: formData,
  });
  const payload = await response.json();
  if (!response.ok) {
    throw new Error(payload.error || "Unable to send the message.");
  }
  return payload as Message;
}

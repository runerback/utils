import { useCallback, useEffect, useState } from "preact/hooks";
import type { Message } from "../types";
import { fetchMessages } from "../utils/api";

export function useMessages() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadMessages = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await fetchMessages();
      setMessages(data.reverse());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load messages.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadMessages();
  }, [loadMessages]);

  const prependMessage = useCallback((message: Message) => {
    setMessages((prev) => {
      if (prev.some((m) => m.id === message.id)) {
        return prev;
      }
      return [message, ...prev];
    });
  }, []);

  return { messages, isLoading, error, prependMessage, reload: loadMessages };
}

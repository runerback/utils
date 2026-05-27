import { useCallback, useEffect, useState } from "preact/hooks";
import { Composer } from "./components/Composer";
import { Feed } from "./components/Feed";
import { useMessages } from "./hooks/useMessages";
import { useSocket } from "./hooks/useSocket";

export function App() {
  const { messages, prependMessage } = useMessages();
  const [status, setStatus] = useState<{ message: string; isError: boolean } | null>(null);

  const handleNewMessage = useCallback(
    (message: import("./types").Message) => {
      prependMessage(message);
    },
    [prependMessage],
  );

  const { isConnected, lastError } = useSocket(handleNewMessage);

  const handleStatus = useCallback((message: string, isError: boolean) => {
    setStatus({ message, isError });
  }, []);

  useEffect(() => {
    if (!status) return;
    const timer = setTimeout(() => setStatus(null), 5000);
    return () => clearTimeout(timer);
  }, [status]);

  useEffect(() => {
    if (typeof window === "undefined") return;
    import("pulltorefreshjs").then((PullToRefresh) => {
      PullToRefresh.default.init({
        mainElement: "body",
        onRefresh() {
          window.location.reload();
        },
      });
    });
  }, []);

  return (
    <main class="app-shell">
      <Composer onStatus={handleStatus} />
      <Feed messages={messages} isConnected={isConnected} onStatus={handleStatus} />
      {!isConnected && lastError && (
        <p class="status-message status-message--error" aria-live="polite">
          {lastError}
        </p>
      )}
      {status && (
        <p
          class={`status-message ${status.isError ? "status-message--error" : "status-message--success"}`}
          aria-live="polite"
        >
          {status.message}
        </p>
      )}
    </main>
  );
}

import type { Message } from "../types";
import { ConnectionPill } from "./ConnectionPill";
import { EmptyState } from "./EmptyState";
import { MessageCard } from "./MessageCard";

interface FeedProps {
  messages: Message[];
  isConnected: boolean;
  onStatus: (message: string, isError: boolean) => void;
}

export function Feed({ messages, isConnected, onStatus }: FeedProps) {
  return (
    <section class="feed-panel">
      <div class="feed-header">
        <div>
          <h2>Room activity</h2>
        </div>
        <ConnectionPill isConnected={isConnected} />
      </div>
      <div class="feed" aria-live="polite">
        {messages.length === 0 ? (
          <EmptyState />
        ) : (
          messages.map((message) => (
            <MessageCard key={message.id} message={message} onStatus={onStatus} />
          ))
        )}
      </div>
    </section>
  );
}

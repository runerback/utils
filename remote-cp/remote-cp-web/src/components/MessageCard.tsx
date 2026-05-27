import { copyTextToClipboard } from "../utils/clipboard";
import { deviceIconPath } from "../utils/device";
import type { Message } from "../types";
import { FileList } from "./FileList";
import { ImageGrid } from "./ImageGrid";
import { TextBlock } from "./TextBlock";
import { VideoGrid } from "./VideoGrid";

interface MessageCardProps {
  message: Message;
  onStatus: (message: string, isError: boolean) => void;
}

export function MessageCard({ message, onStatus }: MessageCardProps) {
  return (
    <article class="message-card">
      <header class="message-header">
        <div class="message-badge">
          <img src={deviceIconPath(message.deviceType)} class="device-icon" alt="" />
          {message.deviceType}
        </div>
        <p class="message-meta">{message.clientTimestamp}</p>
      </header>

      {message.text && (
        <>
          <TextBlock text={message.text} />
          <div class="message-actions">
            <button
              type="button"
              class="secondary-button"
              onClick={async () => {
                try {
                  await copyTextToClipboard(message.text);
                  onStatus("Text copied.", false);
                } catch (err) {
                  onStatus(err instanceof Error ? err.message : "That action failed.", true);
                }
              }}
            >
              Copy text
            </button>
          </div>
        </>
      )}

      {message.images.length > 0 && (
        <ImageGrid images={message.images} onStatus={onStatus} />
      )}

      {message.videos.length > 0 && <VideoGrid videos={message.videos} />}

      {message.files.length > 0 && <FileList files={message.files} onStatus={onStatus} />}
    </article>
  );
}

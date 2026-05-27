import { resolveUrl } from "../utils/api";
import type { VideoAttachment } from "../types";

interface VideoGridProps {
  videos: VideoAttachment[];
}

export function VideoGrid({ videos }: VideoGridProps) {
  return (
    <div class="video-grid">
      {videos.map((video) => (
        <section class="video-card" key={video.url}>
          <video
            src={resolveUrl(video.url)}
            controls
            autoplay
            muted
            playsinline
            preload="metadata"
          />
          <p>{video.name}</p>
        </section>
      ))}
    </div>
  );
}

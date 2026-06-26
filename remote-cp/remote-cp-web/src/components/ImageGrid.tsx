import { useState } from "preact/hooks";
import { copyImageToClipboard } from "../utils/clipboard";
import { resolveUrl } from "../utils/api";
import type { ImageAttachment } from "../types";
import { ImagePreview } from "./ImagePreview";

interface ImageGridProps {
  images: ImageAttachment[];
  onStatus: (message: string, isError: boolean) => void;
}

export function ImageGrid({ images, onStatus }: ImageGridProps) {
  const [previewImage, setPreviewImage] = useState<ImageAttachment | null>(null);

  return (
    <div class="image-grid">
      {images.map((image) => (
        <section class="image-card" key={image.url}>
          <img
            src={resolveUrl(image.url)}
            alt={image.name}
            loading="lazy"
            onClick={() => setPreviewImage(image)}
            style={{ cursor: "pointer" }}
          />
          <p>{image.name}</p>
          <footer>
            <button
              type="button"
              class="secondary-button"
              onClick={async () => {
                try {
                  await copyImageToClipboard(resolveUrl(image.url));
                  onStatus("Image copied.", false);
                } catch (err) {
                  onStatus(err instanceof Error ? err.message : "That action failed.", true);
                }
              }}
            >
              Copy image
            </button>
            <a
              class="secondary-button"
              href={resolveUrl(image.url)}
              target="_blank"
              rel="noreferrer"
            >
              Open image
            </a>
          </footer>
        </section>
      ))}

      {previewImage && (
        <ImagePreview
          image={previewImage}
          onClose={() => setPreviewImage(null)}
        />
      )}
    </div>
  );
}

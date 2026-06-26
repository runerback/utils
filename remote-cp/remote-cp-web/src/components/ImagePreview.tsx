import { useEffect } from "preact/hooks";
import { resolveUrl } from "../utils/api";
import type { ImageAttachment } from "../types";

interface ImagePreviewProps {
  image: ImageAttachment;
  onClose: () => void;
}

export function ImagePreview({ image, onClose }: ImagePreviewProps) {
  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
  }, [onClose]);

  const imageUrl = resolveUrl(image.url);

  const handleSave = () => {
    const a = document.createElement("a");
    a.href = imageUrl;
    a.download = image.name;
    a.target = "_blank";
    a.rel = "noreferrer";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  };

  return (
    <div class="image-preview-overlay" onClick={onClose}>
      <div class="image-preview-toolbar">
        <button
          type="button"
          class="secondary-button"
          onClick={(e) => {
            e.stopPropagation();
            onClose();
          }}
        >
          Close
        </button>
        <button
          type="button"
          class="secondary-button"
          onClick={(e) => {
            e.stopPropagation();
            handleSave();
          }}
        >
          Save
        </button>
      </div>
      <img
        src={imageUrl}
        alt={image.name}
        class="image-preview-img"
        onClick={onClose}
      />
    </div>
  );
}

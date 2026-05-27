import { useState } from "preact/hooks";
import type { FileAttachment } from "../types";
import { resolveUrl } from "../utils/api";

interface FileListProps {
  files: FileAttachment[];
  onStatus: (message: string, isError: boolean) => void;
}

async function downloadFile(url: string, fileName: string) {
  const response = await fetch(url);
  if (!response.ok) {
    const payload = await response.json().catch(() => ({}));
    throw new Error(payload.error || `Download failed (${response.status})`);
  }
  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = fileName;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(objectUrl), 0);
}

export function FileList({ files, onStatus }: FileListProps) {
  const [downloadingUrl, setDownloadingUrl] = useState<string | null>(null);

  return (
    <div class="file-list">
      {files.map((file) => {
        const isDownloading = downloadingUrl === file.downloadUrl;
        return (
          <section class="file-card" key={file.downloadUrl}>
            <a
              href={resolveUrl(file.downloadUrl)}
              class="file-name file-name--download"
              style={{ opacity: isDownloading ? 0.6 : 1, pointerEvents: isDownloading ? "none" : "auto" }}
              onClick={async (e: MouseEvent) => {
                e.preventDefault();
                if (isDownloading) return;
                setDownloadingUrl(file.downloadUrl);
                try {
                  await downloadFile(resolveUrl(file.downloadUrl), file.name);
                  onStatus(`Downloaded ${file.name}.`, false);
                } catch (err) {
                  onStatus(err instanceof Error ? err.message : "Download failed.", true);
                } finally {
                  setDownloadingUrl(null);
                }
              }}
            >
              {isDownloading ? (
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="file-icon"><line x1="12" y1="2" y2="6" x2="12"/><line x1="12" y1="18" y2="22" x2="12"/><line x1="4.93" y1="4.93" x2="7.76" y2="7.76"/><line x1="16.24" y1="16.24" x2="19.07" y2="19.07"/><line x1="2" y1="12" x2="6" y2="12"/><line x1="18" y1="12" x2="22" y2="12"/><line x1="4.93" y1="19.07" x2="7.76" y2="16.24"/><line x1="16.24" y1="7.76" x2="19.07" y2="4.93"/></svg>
              ) : (
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="file-icon"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
              )}
              {isDownloading ? "Downloading..." : file.name}
            </a>
          </section>
        );
      })}
    </div>
  );
}

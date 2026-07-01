import { useCallback, useRef, useState } from "preact/hooks";
import { sendMessage } from "../utils/api";
import {
  createGeneratedTextFileName,
  readClipboardImage,
  readClipboardText,
  saveClipboardText,
} from "../utils/clipboard";
import { detectDeviceType } from "../utils/device";
import { formatTimestamp } from "../utils/format";
import { buildTextSubmission } from "../utils/files";
import type { TextSubmissionResult } from "../utils/files";

interface ComposerProps {
  onStatus: (message: string, isError: boolean) => void;
}

type MediaTab = "pictures" | "videos" | "files";

export function Composer({ onStatus }: ComposerProps) {
  const textInputRef = useRef<HTMLTextAreaElement>(null);
  const imageInputRef = useRef<HTMLInputElement>(null);
  const videoInputRef = useRef<HTMLInputElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const imageHintRef = useRef<HTMLDivElement>(null);
  const videoHintRef = useRef<HTMLDivElement>(null);
  const fileHintRef = useRef<HTMLDivElement>(null);
  const [isBusy, setIsBusy] = useState(false);
  const [activeMode, setActiveMode] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<MediaTab>("pictures");
  const [imageFiles, setImageFiles] = useState<File[]>([]);
  const [videoFiles, setVideoFiles] = useState<File[]>([]);
  const [fileAttachments, setFileAttachments] = useState<File[]>([]);

  const setBusy = useCallback((busy: boolean, mode: string | null = null) => {
    setIsBusy(busy);
    setActiveMode(busy ? mode : null);
  }, []);

  const idleLabel = (mode: string): string => {
    switch (mode) {
      case "paste-file":
        return "Paste as file";
      case "paste-picture":
        return "Paste picture";
      case "text":
        return "Send text";
      case "images":
        return "Send pictures";
      case "videos":
        return "Send videos";
      default:
        return "Send files";
    }
  };

  const sendingLabel = (mode: string): string => {
    switch (mode) {
      case "paste-file":
        return "Saving file...";
      case "paste-picture":
        return "Sending picture...";
      case "text":
        return "Sending text...";
      case "images":
        return "Sending pictures...";
      case "videos":
        return "Sending videos...";
      default:
        return "Sending files...";
    }
  };

  const handleSubmit = useCallback(
    async (submitMode: string) => {
      const trimmedText = textInputRef.current?.value.trim() || "";
      const images = Array.from(imageInputRef.current?.files || []);
      const videos = Array.from(videoInputRef.current?.files || []);
      const attachments = Array.from(fileInputRef.current?.files || []);

      const formData = new FormData();
      formData.append("device_type", detectDeviceType());
      formData.append("client_timestamp", formatTimestamp(new Date()));

      let submitResult: TextSubmissionResult = {
        submitMode,
        routedAsFile: false,
        fileName: null,
      };

      if (submitMode === "text") {
        if (!trimmedText) {
          onStatus("Add some text before sending.", true);
          return;
        }
        submitResult = buildTextSubmission(formData, trimmedText);
      } else if (submitMode === "images") {
        if (images.length === 0) {
          onStatus("Choose at least one picture before sending.", true);
          return;
        }
        images.forEach((file) => formData.append("images", file));
      } else if (submitMode === "videos") {
        if (videos.length === 0) {
          onStatus("Choose at least one video before sending.", true);
          return;
        }
        videos.forEach((file) => formData.append("videos", file));
      } else if (submitMode === "files") {
        if (attachments.length === 0) {
          onStatus("Choose at least one file before sending.", true);
          return;
        }
        attachments.forEach((file) => formData.append("files", file));
      }

      setBusy(true, submitMode);

      try {
        await sendMessage(formData);

        if (submitMode === "text") {
          if (textInputRef.current) textInputRef.current.value = "";
        } else if (submitMode === "images") {
          if (imageInputRef.current) imageInputRef.current.value = "";
          setImageFiles([]);
        } else if (submitMode === "videos") {
          if (videoInputRef.current) videoInputRef.current.value = "";
          setVideoFiles([]);
        } else if (submitMode === "files") {
          if (fileInputRef.current) fileInputRef.current.value = "";
          setFileAttachments([]);
        }

        if (submitResult.submitMode === "text" && submitResult.routedAsFile) {
          onStatus(`Long text sent to the room as ${submitResult.fileName}.`, false);
        } else if (submitMode === "text") {
          onStatus("Text sent to the room.", false);
        } else if (submitMode === "images") {
          onStatus("Pictures sent to the room.", false);
        } else if (submitMode === "videos") {
          onStatus("Videos sent to the room.", false);
        } else {
          onStatus("Files sent to the room.", false);
        }
      } catch (err) {
        onStatus(err instanceof Error ? err.message : "Unable to send the message.", true);
      } finally {
        setBusy(false);
      }
    },
    [onStatus, setBusy],
  );

  const handlePasteAsFile = useCallback(async () => {
    setBusy(true, "paste-file");
    try {
      const clipboardText = await readClipboardText();
      if (clipboardText.length === 0) {
        throw new Error("No text was provided.");
      }
      const suggestedName = createGeneratedTextFileName("clipboard");
      const usedNativeSaveDialog = await saveClipboardText(clipboardText, suggestedName);
      const statusText = usedNativeSaveDialog
        ? `Clipboard text saved as ${suggestedName}.`
        : `Clipboard text downloaded as ${suggestedName}.`;
      onStatus(statusText, false);
    } catch (err) {
      if ((err as Error)?.name === "AbortError") {
        onStatus("File save cancelled.", false);
        return;
      }
      const errorMessage = (err as Error)?.message || "Could not save clipboard text as a file.";
      onStatus(errorMessage, true);
    } finally {
      setBusy(false);
    }
  }, [onStatus, setBusy]);

  const handlePastePicture = useCallback(async () => {
    setBusy(true, "paste-picture");
    try {
      const clipboardImage = await readClipboardImage();
      const formData = new FormData();
      formData.append("device_type", detectDeviceType());
      formData.append("client_timestamp", formatTimestamp(new Date()));
      formData.append("images", clipboardImage);

      await sendMessage(formData);
      onStatus(`Picture sent to the room as ${clipboardImage.name}.`, false);
    } catch (err) {
      const errorMessage = (err as Error)?.message || "Could not paste the picture from the clipboard.";
      onStatus(errorMessage, true);
    } finally {
      setBusy(false);
    }
  }, [onStatus, setBusy]);

  const hideAllHints = () => {
    if (imageHintRef.current) imageHintRef.current.style.display = "none";
    if (videoHintRef.current) videoHintRef.current.style.display = "none";
    if (fileHintRef.current) fileHintRef.current.style.display = "none";
  };

  const showHint = (ref: { current: HTMLDivElement | null }) => {
    hideAllHints();
    if (ref.current) ref.current.style.display = "flex";
  };

  const startSelecting = (ref: { current: HTMLDivElement | null }) => {
    showHint(ref);

    const handleFocus = () => {
      window.setTimeout(() => hideAllHints(), 300);
    };
    window.addEventListener("focus", handleFocus, { once: true });
  };

  const updateFileList = (input: HTMLInputElement | null, setter: (files: File[]) => void) => {
    const files = input?.files ? Array.from(input.files) : [];
    setter(files);
    requestAnimationFrame(() => hideAllHints());
  };

  const tabs: { key: MediaTab; label: string }[] = [
    { key: "pictures", label: "Pictures" },
    { key: "videos", label: "Videos" },
    { key: "files", label: "Files" },
  ];

  const busyLabel = activeMode ? sendingLabel(activeMode) : "Sending...";

  return (
    <section class="composer-panel">
      {isBusy && (
        <div class="composer-overlay">
          <div class="spinner" />
          <p class="composer-overlay-text">{busyLabel}</p>
        </div>
      )}

      <div>
        <h1>Drop text, pictures, videos, or files into the shared room.</h1>
        <p class="subtle">
          Everyone connected sees new posts right away. Messages stay available until the app restarts.
        </p>
      </div>

      <form
        class="composer-form"
        onSubmit={(e) => {
          e.preventDefault();
        }}
      >
        <div class="composer-layout">
          <div class="composer-row composer-row--message">
            <label class="field-label" for="message-input">Message</label>
            <div class="composer-message-stack">
              <div class="composer-input-group">
                <textarea
                  id="message-input"
                  name="text"
                  rows={4}
                  placeholder="Type anything you want to share..."
                  ref={textInputRef}
                />
              </div>
              <div class="composer-actions">
                <button
                  type="button"
                  disabled={isBusy}
                  onClick={() => handleSubmit("text")}
                >
                  {isBusy && activeMode === "text" ? sendingLabel("text") : idleLabel("text")}
                </button>
                <button
                  type="button"
                  disabled={isBusy}
                  onClick={handlePasteAsFile}
                >
                  {isBusy && activeMode === "paste-file" ? sendingLabel("paste-file") : idleLabel("paste-file")}
                </button>
                <button
                  type="button"
                  disabled={isBusy}
                  onClick={handlePastePicture}
                >
                  {isBusy && activeMode === "paste-picture" ? sendingLabel("paste-picture") : idleLabel("paste-picture")}
                </button>
              </div>
            </div>
          </div>

          <div class="media-tabs">
            <div class="tab-bar" role="tablist">
              {tabs.map((tab) => (
                <button
                  key={tab.key}
                  type="button"
                  role="tab"
                  class={`tab ${activeTab === tab.key ? "tab--active" : ""}`}
                  onClick={() => setActiveTab(tab.key)}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            <div class="tab-panel" role="tabpanel">
              {activeTab === "pictures" && (
                <div class="tab-content">
                  <input
                    id="image-input"
                    name="images"
                    type="file"
                    accept="image/*"
                    multiple
                    ref={imageInputRef}
                    onClick={() => startSelecting(imageHintRef)}
                    onChange={() => updateFileList(imageInputRef.current, setImageFiles)}
                  />
                  <div ref={imageHintRef} class="selecting-hint" style={{ display: "none" }}>
                    <div class="spinner spinner--small" />
                    <span>Reading selected pictures...</span>
                  </div>
                  {imageFiles.length > 0 && (
                    <ul class="file-selection-list">
                      {imageFiles.map((file, i) => (
                        <li key={`${file.name}-${i}`}>{file.name}</li>
                      ))}
                    </ul>
                  )}
                  <button
                    type="button"
                    disabled={isBusy}
                    onClick={() => handleSubmit("images")}
                  >
                    {isBusy && activeMode === "images" ? sendingLabel("images") : idleLabel("images")}
                  </button>
                </div>
              )}

              {activeTab === "videos" && (
                <div class="tab-content">
                  <input
                    id="video-input"
                    name="videos"
                    type="file"
                    accept="video/mp4,.mp4"
                    multiple
                    ref={videoInputRef}
                    onClick={() => startSelecting(videoHintRef)}
                    onChange={() => updateFileList(videoInputRef.current, setVideoFiles)}
                  />
                  <div ref={videoHintRef} class="selecting-hint" style={{ display: "none" }}>
                    <div class="spinner spinner--small" />
                    <span>Reading selected videos...</span>
                  </div>
                  {videoFiles.length > 0 && (
                    <ul class="file-selection-list">
                      {videoFiles.map((file, i) => (
                        <li key={`${file.name}-${i}`}>{file.name}</li>
                      ))}
                    </ul>
                  )}
                  <button
                    type="button"
                    disabled={isBusy}
                    onClick={() => handleSubmit("videos")}
                  >
                    {isBusy && activeMode === "videos" ? sendingLabel("videos") : idleLabel("videos")}
                  </button>
                </div>
              )}

              {activeTab === "files" && (
                <div class="tab-content">
                  <input
                    id="file-input"
                    name="files"
                    type="file"
                    accept=".7z,.apk,.csv,.doc,.docx,.json,.md,.pdf,.ppt,.pptx,.pt,.py,.rtf,.txt,.xls,.xlsx,.zip"
                    multiple
                    ref={fileInputRef}
                    onClick={() => startSelecting(fileHintRef)}
                    onChange={() => updateFileList(fileInputRef.current, setFileAttachments)}
                  />
                  <div ref={fileHintRef} class="selecting-hint" style={{ display: "none" }}>
                    <div class="spinner spinner--small" />
                    <span>Reading selected files...</span>
                  </div>
                  {fileAttachments.length > 0 && (
                    <ul class="file-selection-list">
                      {fileAttachments.map((file, i) => (
                        <li key={`${file.name}-${i}`}>{file.name}</li>
                      ))}
                    </ul>
                  )}
                  <button
                    type="button"
                    disabled={isBusy}
                    onClick={() => handleSubmit("files")}
                  >
                    {isBusy && activeMode === "files" ? sendingLabel("files") : idleLabel("files")}
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>
      </form>
    </section>
  );
}

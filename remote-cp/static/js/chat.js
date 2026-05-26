const form = document.getElementById("composer-form");
const textInput = document.getElementById("message-input");
const imageInput = document.getElementById("image-input");
const videoInput = document.getElementById("video-input");
const fileInput = document.getElementById("file-input");
const pasteAsFileButton = document.getElementById("paste-as-file-button");
const pastePictureButton = document.getElementById("paste-picture-button");
const imageSelection = document.getElementById("image-selection");
const videoSelection = document.getElementById("video-selection");
const fileSelection = document.getElementById("file-selection");
const statusMessage = document.getElementById("status-message");
const composerButtons = Array.from(document.querySelectorAll("[data-submit-mode], [data-action-mode]"));
const feed = document.getElementById("feed");
const connectionPill = document.getElementById("connection-pill");
const bootstrapMessages = JSON.parse(document.getElementById("bootstrap-messages").textContent);
const seenMessageIds = new Set();
const socket = io({ transports: ["websocket", "polling"] });
const COLLAPSED_TEXT_LINES = 8;
const MAX_INLINE_TEXT_LENGTH = 4000;
let resizeSyncTimer = null;

renderFeed(bootstrapMessages);
bindSocketEvents();
bindComposerEvents();
initPullToRefresh();

function bindSocketEvents() {
  socket.on("connect", () => updateConnectionState(true));
  socket.on("disconnect", () => updateConnectionState(false));
  socket.on("message:new", (message) => renderMessage(message, { prepend: true, scrollIntoView: true }));
}

function bindComposerEvents() {
  imageInput.addEventListener("change", () => {
    updateSelectionSummary(imageInput, imageSelection, "picture", "No pictures selected.");
  });

  videoInput.addEventListener("change", () => {
    updateSelectionSummary(videoInput, videoSelection, "video", "No videos selected.");
  });

  fileInput.addEventListener("change", () => {
    updateSelectionSummary(fileInput, fileSelection, "file", "No files selected.");
  });

  pasteAsFileButton.addEventListener("click", handlePasteAsFileClick);
  pastePictureButton.addEventListener("click", handlePastePictureClick);

  window.addEventListener("resize", () => {
    window.clearTimeout(resizeSyncTimer);
    resizeSyncTimer = window.setTimeout(() => {
      syncCollapsibleTextBlocks(feed);
    }, 120);
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const trimmedText = textInput.value.trim();
    const images = Array.from(imageInput.files);
    const videos = Array.from(videoInput.files);
    const attachments = Array.from(fileInput.files);
    const submitMode = event.submitter?.dataset.submitMode;
    let submitResult = { submitMode, routedAsFile: false, fileName: null };

    if (!submitMode) {
      setStatus("Choose how you want to send the post.", true);
      return;
    }

    const formData = new FormData();
    formData.append("device_type", detectDeviceType());
    formData.append("client_timestamp", formatTimestamp(new Date()));

    if (submitMode === "text") {
      if (!trimmedText) {
        setStatus("Add some text before sending.", true);
        return;
      }
      submitResult = buildTextSubmission(formData, trimmedText);
    }

    if (submitMode === "images") {
      if (images.length === 0) {
        setStatus("Choose at least one picture before sending.", true);
        return;
      }
      images.forEach((file) => formData.append("images", file));
    }

    if (submitMode === "videos") {
      if (videos.length === 0) {
        setStatus("Choose at least one video before sending.", true);
        return;
      }
      videos.forEach((file) => formData.append("videos", file));
    }

    if (submitMode === "files") {
      if (attachments.length === 0) {
        setStatus("Choose at least one file before sending.", true);
        return;
      }
      attachments.forEach((file) => formData.append("files", file));
    }

    setBusy(true, submitMode);

    try {
      const response = await fetch("/api/messages", {
        method: "POST",
        body: formData,
      });

      const payload = await response.json();

      if (!response.ok) {
        throw new Error(payload.error || "Unable to send the message.");
      }

      clearSubmittedInput(submitMode);
      setStatus(successMessage(submitResult), false);
    } catch (error) {
      setStatus(error.message || "Unable to send the message.", true);
    } finally {
      setBusy(false);
    }
  });
}

function renderFeed(messages) {
  feed.innerHTML = "";
  seenMessageIds.clear();

  if (!messages.length) {
    feed.append(createEmptyState());
    return;
  }

  messages
    .slice()
    .reverse()
    .forEach((message) => renderMessage(message, { prepend: false, scrollIntoView: false }));
}

function renderMessage(message, options = {}) {
  const { prepend = false, scrollIntoView = false } = options;

  if (seenMessageIds.has(message.id)) {
    return;
  }

  seenMessageIds.add(message.id);
  removeEmptyState();

  const card = document.createElement("article");
  card.className = "message-card";

  const header = document.createElement("header");
  header.className = "message-header";

  const badge = document.createElement("div");
  badge.className = "message-badge";
  badge.textContent = `${deviceIcon(message.deviceType)} ${message.deviceType}`;

  const meta = document.createElement("p");
  meta.className = "message-meta";
  meta.textContent = message.clientTimestamp;

  header.append(badge, meta);
  card.append(header);

  if (message.text) {
    const textBlock = document.createElement("div");
    textBlock.className = "message-text-block";

    const body = document.createElement("p");
    body.className = "message-text";
    body.textContent = message.text;

    const toggle = document.createElement("button");
    toggle.type = "button";
    toggle.className = "secondary-button message-toggle";
    toggle.hidden = true;
    toggle.addEventListener("click", () => {
      const isExpanded = textBlock.dataset.expanded === "true";
      setTextBlockExpanded(textBlock, !isExpanded);
    });

    textBlock.append(body, toggle);
    card.append(textBlock);

    const textActions = document.createElement("div");
    textActions.className = "message-actions";
    textActions.append(
      buildActionButton("Copy text", async () => {
        await copyTextToClipboard(message.text);
        setStatus("Text copied.", false);
      }),
    );
    card.append(textActions);
  }

  if (message.images && message.images.length) {
    const imageGrid = document.createElement("div");
    imageGrid.className = "image-grid";

    message.images.forEach((image) => {
      const imageCard = document.createElement("section");
      imageCard.className = "image-card";

      const img = document.createElement("img");
      img.src = image.url;
      img.alt = image.name;
      img.loading = "lazy";

      const caption = document.createElement("p");
      caption.textContent = image.name;

      const footer = document.createElement("footer");
      footer.append(
        buildActionButton("Copy image", async () => {
          await copyImageToClipboard(image.url);
          setStatus("Image copied.", false);
        }),
        buildLinkButton("Open image", image.url),
      );

      imageCard.append(img, caption, footer);
      imageGrid.append(imageCard);
    });

    card.append(imageGrid);
  }

  if (message.videos && message.videos.length) {
    const videoGrid = document.createElement("div");
    videoGrid.className = "video-grid";

    message.videos.forEach((video) => {
      const videoCard = document.createElement("section");
      videoCard.className = "video-card";

      const player = document.createElement("video");
      player.src = video.url;
      player.controls = true;
      player.autoplay = true;
      player.muted = true;
      player.playsInline = true;
      player.preload = "metadata";

      const caption = document.createElement("p");
      caption.textContent = video.name;

      videoCard.append(player, caption);
      videoGrid.append(videoCard);
    });

    card.append(videoGrid);
  }

  if (message.files && message.files.length) {
    const fileList = document.createElement("div");
    fileList.className = "file-list";

    message.files.forEach((file) => {
      const fileCard = document.createElement("section");
      fileCard.className = "file-card";

      const fileName = document.createElement("p");
      fileName.className = "file-name";
      fileName.textContent = file.name;

      const fileActions = document.createElement("div");
      fileActions.className = "message-actions";
      fileActions.append(
        buildLinkButton("Download file", file.downloadUrl || file.url, { downloadName: file.name }),
      );

      fileCard.append(fileName, fileActions);
      fileList.append(fileCard);
    });

    card.append(fileList);
  }

  if (prepend) {
    feed.prepend(card);
  } else {
    feed.append(card);
  }

  syncCollapsibleTextBlocks(card);

  if (scrollIntoView) {
    card.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }
}

function createEmptyState() {
  const empty = document.createElement("div");
  empty.className = "empty-state";
  empty.id = "empty-state";
  empty.textContent = "The room is empty right now. Send the first message.";
  return empty;
}

function removeEmptyState() {
  const emptyState = document.getElementById("empty-state");
  if (emptyState) {
    emptyState.remove();
  }
}

function updateConnectionState(isOnline) {
  connectionPill.textContent = isOnline ? "Connected" : "Reconnecting...";
  connectionPill.classList.toggle("connection-pill--online", isOnline);
  connectionPill.classList.toggle("connection-pill--offline", !isOnline);
}

function setBusy(isBusy, activeMode = null) {
  composerButtons.forEach((button) => {
    const buttonMode = button.dataset.submitMode || button.dataset.actionMode;
    const isActiveButton = buttonMode === activeMode;
    button.disabled = isBusy;
    button.textContent = isBusy && isActiveButton ? sendingLabel(buttonMode) : idleLabel(buttonMode);
  });
}

function setStatus(message, isError) {
  statusMessage.textContent = message;
  statusMessage.classList.toggle("status-message--error", isError);
  statusMessage.classList.toggle("status-message--success", !isError);
}

function detectDeviceType() {
  const userAgent = navigator.userAgent.toLowerCase();

  if (/ipad|tablet|playbook|silk/.test(userAgent)) {
    return "Tablet";
  }

  if (/mobi|android|iphone|ipod|phone/.test(userAgent)) {
    return "Phone";
  }

  return "Computer";
}

function deviceIcon(deviceType) {
  if (deviceType === "Phone") {
    return "[PHONE]";
  }

  if (deviceType === "Tablet") {
    return "[TABLET]";
  }

  return "[PC]";
}

function formatTimestamp(date) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "medium",
  }).format(date);
}

function updateSelectionSummary(input, target, singularLabel, emptyLabel) {
  const count = input.files.length;
  target.textContent = count
    ? `${count} ${singularLabel}${count === 1 ? "" : "s"} selected`
    : emptyLabel;
}

function clearSubmittedInput(submitMode) {
  if (submitMode === "text") {
    textInput.value = "";
    return;
  }

  if (submitMode === "images") {
    imageInput.value = "";
    imageSelection.textContent = "No pictures selected.";
    return;
  }

  if (submitMode === "videos") {
    videoInput.value = "";
    videoSelection.textContent = "No videos selected.";
    return;
  }

  if (submitMode === "files") {
    fileInput.value = "";
    fileSelection.textContent = "No files selected.";
  }
}

function successMessage(submitResult) {
  if (submitResult.submitMode === "text" && submitResult.routedAsFile) {
    return `Long text sent to the room as ${submitResult.fileName}.`;
  }

  if (submitResult.submitMode === "text") {
    return "Text sent to the room.";
  }

  if (submitResult.submitMode === "images") {
    return "Pictures sent to the room.";
  }

  if (submitResult.submitMode === "videos") {
    return "Videos sent to the room.";
  }

  return "Files sent to the room.";
}

function idleLabel(submitMode) {
  if (submitMode === "paste-file") {
    return "Paste as file";
  }

  if (submitMode === "paste-picture") {
    return "Paste picture";
  }

  if (submitMode === "text") {
    return "Send text";
  }

  if (submitMode === "images") {
    return "Send pictures";
  }

  if (submitMode === "videos") {
    return "Send videos";
  }

  return "Send files";
}

function sendingLabel(submitMode) {
  if (submitMode === "paste-file") {
    return "Saving file...";
  }

  if (submitMode === "paste-picture") {
    return "Sending picture...";
  }

  if (submitMode === "text") {
    return "Sending text...";
  }

  if (submitMode === "images") {
    return "Sending pictures...";
  }

  if (submitMode === "videos") {
    return "Sending videos...";
  }

  return "Sending files...";
}

function buildActionButton(label, onClick) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = "secondary-button";
  button.textContent = label;
  button.addEventListener("click", async () => {
    try {
      await onClick();
    } catch (error) {
      setStatus(error.message || "That action failed.", true);
    }
  });
  return button;
}

function buildLinkButton(label, href, options = {}) {
  const link = document.createElement("a");
  link.className = "secondary-button";
  link.textContent = label;
  link.href = href;

  if (options.downloadName) {
    link.download = options.downloadName;
  } else {
    link.target = "_blank";
    link.rel = "noreferrer";
  }

  return link;
}

async function copyTextToClipboard(text) {
  let clipboardError = null;

  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return;
    } catch (error) {
      clipboardError = error;
    }
  }

  if (legacyCopyTextToClipboard(text)) {
    return;
  }

  throw mapCopyTextError(clipboardError);
}

function legacyCopyTextToClipboard(text) {
  if (typeof document.execCommand !== "function") {
    return false;
  }

  const activeElement = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  const helper = document.createElement("textarea");
  helper.value = text;
  helper.setAttribute("readonly", "");
  helper.setAttribute("aria-hidden", "true");
  helper.style.position = "fixed";
  helper.style.top = "0";
  helper.style.left = "0";
  helper.style.opacity = "0";
  helper.style.pointerEvents = "none";

  document.body.append(helper);
  helper.focus();
  helper.select();
  helper.setSelectionRange(0, helper.value.length);

  try {
    return document.execCommand("copy");
  } finally {
    helper.remove();
    activeElement?.focus?.();
  }
}

function mapCopyTextError(error) {
  if (error?.name === "NotAllowedError") {
    return new Error(
      "Clipboard access was blocked. Allow clipboard access or use a browser that permits copy on this page.",
    );
  }

  if (!window.isSecureContext) {
    return new Error("Text copy is blocked on plain HTTP in this browser.");
  }

  return new Error("Could not copy the text in this browser.");
}

async function copyImageToClipboard(imageUrl) {
  if (!window.ClipboardItem || !navigator.clipboard?.write) {
    throw new Error("This browser cannot copy images directly. Use Open image instead.");
  }

  if (!window.isSecureContext) {
    throw new Error("Image copy needs HTTPS or localhost. On LAN HTTP, use Open image instead.");
  }

  const response = await fetch(imageUrl);

  if (!response.ok) {
    throw new Error("Could not read the image.");
  }

  const blob = await response.blob();
  await navigator.clipboard.write([new ClipboardItem({ [blob.type]: blob })]);
}

function buildTextSubmission(formData, trimmedText) {
  if (trimmedText.length <= MAX_INLINE_TEXT_LENGTH) {
    formData.append("text", trimmedText);
    return { submitMode: "text", routedAsFile: false, fileName: null };
  }

  const fileName = createGeneratedTextFileName("message");
  formData.append("files", createTextFile(trimmedText, fileName));
  return { submitMode: "text", routedAsFile: true, fileName };
}

async function handlePasteAsFileClick() {
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

    setStatus(statusText, false);
  } catch (error) {
    if (error?.name === "AbortError") {
      setStatus("File save cancelled.", false);
      return;
    }

    setStatus(mapPasteAsFileError(error), true);
  } finally {
    setBusy(false);
  }
}

async function handlePastePictureClick() {
  setBusy(true, "paste-picture");

  try {
    const clipboardImage = await readClipboardImage();
    const formData = new FormData();
    formData.append("device_type", detectDeviceType());
    formData.append("client_timestamp", formatTimestamp(new Date()));
    formData.append("images", clipboardImage);

    const response = await fetch("/api/messages", {
      method: "POST",
      body: formData,
    });
    const payload = await response.json();

    if (!response.ok) {
      throw new Error(payload.error || "Unable to send the picture.");
    }

    setStatus(`Picture sent to the room as ${clipboardImage.name}.`, false);
  } catch (error) {
    setStatus(mapPastePictureError(error), true);
  } finally {
    setBusy(false);
  }
}

async function readClipboardText() {
  if (navigator.clipboard?.readText && window.isSecureContext) {
    return navigator.clipboard.readText();
  }

  return promptForClipboardText();
}

function promptForClipboardText() {
  const promptText = window.isSecureContext
    ? "Direct clipboard read is not available here. Paste the text into this dialog, then press OK to save it as a file."
    : "Direct clipboard read is blocked on plain HTTP. Paste the text into this dialog, then press OK to save it as a file.";
  const pastedText = window.prompt(promptText, "");

  if (pastedText === null) {
    const abortError = new Error("File save cancelled.");
    abortError.name = "AbortError";
    throw abortError;
  }

  return pastedText;
}

async function readClipboardImage() {
  if (!navigator.clipboard?.read || !window.isSecureContext) {
    throw new Error("Clipboard image paste needs HTTPS or localhost. Use Send pictures if direct clipboard access is blocked.");
  }

  const clipboardItems = await navigator.clipboard.read();

  for (const clipboardItem of clipboardItems) {
    const imageType = clipboardItem.types.find((type) => type.startsWith("image/"));

    if (!imageType) {
      continue;
    }

    const blob = await clipboardItem.getType(imageType);
    const extension = extensionForMimeType(imageType);
    const fileName = createGeneratedImageFileName("clipboard-image", extension);
    return new File([blob], fileName, { type: imageType });
  }

  throw new Error("No picture was found in the clipboard.");
}

async function saveClipboardText(text, suggestedName) {
  if (window.showSaveFilePicker) {
    const fileHandle = await window.showSaveFilePicker({
      suggestedName,
      types: [
        {
          description: "Text files",
          accept: {
            "text/plain": [".txt"],
          },
        },
      ],
    });
    const writable = await fileHandle.createWritable();
    await writable.write(text);
    await writable.close();
    return true;
  }

  downloadTextFile(text, suggestedName);
  return false;
}

function downloadTextFile(text, fileName) {
  const blob = createTextFile(text, fileName);
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");

  link.href = objectUrl;
  link.download = fileName;
  link.click();

  window.setTimeout(() => {
    URL.revokeObjectURL(objectUrl);
  }, 0);
}

function createTextFile(text, fileName) {
  return new File([text], fileName, { type: "text/plain;charset=utf-8" });
}

function createGeneratedTextFileName(prefix) {
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  return `${prefix}-${timestamp}.txt`;
}

function createGeneratedImageFileName(prefix, extension) {
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  return `${prefix}-${timestamp}.${extension}`;
}

function extensionForMimeType(mimeType) {
  const extensionMap = {
    "image/bmp": "bmp",
    "image/gif": "gif",
    "image/jpeg": "jpg",
    "image/png": "png",
    "image/webp": "webp",
  };
  return extensionMap[mimeType] || "png";
}

function mapPasteAsFileError(error) {
  if (error?.name === "NotAllowedError") {
    return "Clipboard access was blocked. Allow clipboard access and try again.";
  }

  if (error?.message) {
    return error.message;
  }

  return "Could not save clipboard text as a file.";
}

function mapPastePictureError(error) {
  if (error?.name === "NotAllowedError") {
    return "Clipboard access was blocked. Allow clipboard access and try again.";
  }

  if (error?.message) {
    return error.message;
  }

  return "Could not paste the picture from the clipboard.";
}

function syncCollapsibleTextBlocks(scope) {
  const textBlocks = scope.matches?.(".message-text-block")
    ? [scope]
    : Array.from(scope.querySelectorAll(".message-text-block"));

  textBlocks.forEach((textBlock) => {
    const text = textBlock.querySelector(".message-text");
    const toggle = textBlock.querySelector(".message-toggle");

    if (!text || !toggle) {
      return;
    }

    const wasExpanded = textBlock.dataset.expanded === "true";
    text.classList.add("message-text--collapsed");

    const isOverflowing = text.scrollHeight > text.clientHeight + 1;

    if (!isOverflowing) {
      text.classList.remove("message-text--collapsed");
      textBlock.dataset.expanded = "false";
      toggle.hidden = true;
      return;
    }

    toggle.hidden = false;
    setTextBlockExpanded(textBlock, wasExpanded);
  });
}

function setTextBlockExpanded(textBlock, isExpanded) {
  const text = textBlock.querySelector(".message-text");
  const toggle = textBlock.querySelector(".message-toggle");

  if (!text || !toggle) {
    return;
  }

  textBlock.dataset.expanded = String(isExpanded);
  text.classList.toggle("message-text--collapsed", !isExpanded);
  toggle.textContent = isExpanded ? "Show less" : "Show more";
}

function initPullToRefresh() {
  if (typeof PullToRefresh === "undefined") {
    return;
  }

  PullToRefresh.init({
    mainElement: "body",
    onRefresh() {
      window.location.reload();
    },
  });
}

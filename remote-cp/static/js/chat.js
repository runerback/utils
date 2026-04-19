const form = document.getElementById("composer-form");
const textInput = document.getElementById("message-input");
const imageInput = document.getElementById("image-input");
const fileInput = document.getElementById("file-input");
const pasteAsFileButton = document.getElementById("paste-as-file-button");
const imageSelection = document.getElementById("image-selection");
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

function bindSocketEvents() {
  socket.on("connect", () => updateConnectionState(true));
  socket.on("disconnect", () => updateConnectionState(false));
  socket.on("message:new", (message) => renderMessage(message, { prepend: true, scrollIntoView: true }));
}

function bindComposerEvents() {
  imageInput.addEventListener("change", () => {
    updateSelectionSummary(imageInput, imageSelection, "picture", "No pictures selected.");
  });

  fileInput.addEventListener("change", () => {
    updateSelectionSummary(fileInput, fileSelection, "file", "No files selected.");
  });

  pasteAsFileButton.addEventListener("click", handlePasteAsFileClick);

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
        await navigator.clipboard.writeText(message.text);
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
      fileActions.append(buildLinkButton("Download file", file.url, { downloadName: file.name }));

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

  return "Files sent to the room.";
}

function idleLabel(submitMode) {
  if (submitMode === "paste-file") {
    return "Paste as file";
  }

  if (submitMode === "text") {
    return "Send text";
  }

  if (submitMode === "images") {
    return "Send pictures";
  }

  return "Send files";
}

function sendingLabel(submitMode) {
  if (submitMode === "paste-file") {
    return "Saving file...";
  }

  if (submitMode === "text") {
    return "Sending text...";
  }

  if (submitMode === "images") {
    return "Sending pictures...";
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
  link.target = "_blank";
  link.rel = "noreferrer";

  if (options.downloadName) {
    link.download = options.downloadName;
  }

  return link;
}

async function copyImageToClipboard(imageUrl) {
  if (!window.ClipboardItem || !navigator.clipboard?.write) {
    throw new Error("Image copy needs a browser with ClipboardItem support.");
  }

  if (!window.isSecureContext) {
    throw new Error("Image copy needs HTTPS or localhost.");
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
      throw new Error("Clipboard does not contain any text.");
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

async function readClipboardText() {
  if (!window.isSecureContext) {
    throw new Error("Clipboard access needs HTTPS or localhost.");
  }

  if (!navigator.clipboard?.readText) {
    throw new Error("Clipboard text read needs a modern browser.");
  }

  return navigator.clipboard.readText();
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

function mapPasteAsFileError(error) {
  if (error?.name === "NotAllowedError") {
    return "Clipboard access was blocked. Allow clipboard access and try again.";
  }

  if (error?.message) {
    return error.message;
  }

  return "Could not save clipboard text as a file.";
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

const form = document.getElementById("composer-form");
const textInput = document.getElementById("message-input");
const imageInput = document.getElementById("image-input");
const fileInput = document.getElementById("file-input");
const imageSelection = document.getElementById("image-selection");
const fileSelection = document.getElementById("file-selection");
const statusMessage = document.getElementById("status-message");
const sendButtons = Array.from(document.querySelectorAll("[data-submit-mode]"));
const feed = document.getElementById("feed");
const connectionPill = document.getElementById("connection-pill");
const bootstrapMessages = JSON.parse(document.getElementById("bootstrap-messages").textContent);
const seenMessageIds = new Set();
const socket = io({ transports: ["websocket", "polling"] });

renderFeed(bootstrapMessages);
bindSocketEvents();
bindComposerEvents();

function bindSocketEvents() {
  socket.on("connect", () => updateConnectionState(true));
  socket.on("disconnect", () => updateConnectionState(false));
  socket.on("message:new", (message) => renderMessage(message, true));
}

function bindComposerEvents() {
  imageInput.addEventListener("change", () => {
    updateSelectionSummary(imageInput, imageSelection, "picture", "No pictures selected.");
  });

  fileInput.addEventListener("change", () => {
    updateSelectionSummary(fileInput, fileSelection, "file", "No files selected.");
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const trimmedText = textInput.value.trim();
    const images = Array.from(imageInput.files);
    const attachments = Array.from(fileInput.files);
    const submitMode = event.submitter?.dataset.submitMode;

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
      formData.append("text", trimmedText);
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
      setStatus(successMessage(submitMode), false);
    } catch (error) {
      setStatus(error.message || "Unable to send the message.", true);
    } finally {
      setBusy(false);
    }
  });
}

function renderFeed(messages) {
  feed.innerHTML = "";

  if (!messages.length) {
    feed.append(createEmptyState());
    return;
  }

  messages.forEach((message) => renderMessage(message, false));
}

function renderMessage(message, appendToEnd) {
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
    const body = document.createElement("p");
    body.className = "message-text";
    body.textContent = message.text;
    card.append(body);

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

  if (appendToEnd) {
    feed.append(card);
    card.scrollIntoView({ behavior: "smooth", block: "nearest" });
    return;
  }

  feed.append(card);
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
  sendButtons.forEach((button) => {
    const isActiveButton = button.dataset.submitMode === activeMode;
    button.disabled = isBusy;
    button.textContent = isBusy && isActiveButton ? sendingLabel(activeMode) : idleLabel(button.dataset.submitMode);
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

function successMessage(submitMode) {
  if (submitMode === "text") {
    return "Text sent to the room.";
  }

  if (submitMode === "images") {
    return "Pictures sent to the room.";
  }

  return "Files sent to the room.";
}

function idleLabel(submitMode) {
  if (submitMode === "text") {
    return "Send text";
  }

  if (submitMode === "images") {
    return "Send pictures";
  }

  return "Send files";
}

function sendingLabel(submitMode) {
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

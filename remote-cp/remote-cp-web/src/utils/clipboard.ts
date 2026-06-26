export async function copyTextToClipboard(text: string): Promise<void> {
  let clipboardError: Error | null = null;

  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return;
    } catch (error) {
      clipboardError = error as Error;
    }
  }

  if (legacyCopyTextToClipboard(text)) {
    return;
  }

  throw mapCopyTextError(clipboardError);
}

function legacyCopyTextToClipboard(text: string): boolean {
  if (typeof document.execCommand !== "function") {
    return false;
  }

  const activeElement =
    document.activeElement instanceof HTMLElement ? document.activeElement : null;
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

function mapCopyTextError(error: Error | null): Error {
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

export async function copyImageToClipboard(imageUrl: string): Promise<void> {
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

export async function readClipboardText(): Promise<string> {
  if (navigator.clipboard?.readText && window.isSecureContext) {
    return navigator.clipboard.readText();
  }

  return promptForClipboardText();
}

function promptForClipboardText(): string {
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

export async function readClipboardImage(): Promise<File> {
  if (!navigator.clipboard?.read || !window.isSecureContext) {
    throw new Error(
      "Clipboard image paste needs HTTPS or localhost. Use Send pictures if direct clipboard access is blocked.",
    );
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

export async function saveClipboardText(text: string, suggestedName: string): Promise<boolean> {
  downloadTextFile(text, suggestedName);
  return false;
}

function downloadTextFile(text: string, fileName: string) {
  const blob = createTextFile(text, fileName);
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");

  link.href = objectUrl;
  link.download = fileName;

  document.body.append(link);
  link.click();
  link.remove();

  window.setTimeout(() => {
    URL.revokeObjectURL(objectUrl);
  }, 0);
}

export function createTextFile(text: string, fileName: string): File {
  return new File([text], fileName, { type: "text/plain;charset=utf-8" });
}

export function createGeneratedTextFileName(prefix: string): string {
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  return `${prefix}-${timestamp}.txt`;
}

function createGeneratedImageFileName(prefix: string, extension: string): string {
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  return `${prefix}-${timestamp}.${extension}`;
}

function extensionForMimeType(mimeType: string): string {
  const extensionMap: Record<string, string> = {
    "image/bmp": "bmp",
    "image/gif": "gif",
    "image/jpeg": "jpg",
    "image/png": "png",
    "image/webp": "webp",
  };
  return extensionMap[mimeType] || "png";
}

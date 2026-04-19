const state = {
  projectId: null,
  metadata: null,
  originalUsesProxy: false,
  selectedFile: null,
  selectedFilePath: null,
  cropIndicatorVisible: true,
  edit: {
    trim: { start: 0, end: 0 },
    crop: { x: 0, y: 0, width: 0, height: 0, preset: null },
    crop_enabled: false,
    resize_max: null,
    fps: null
  }
};

const el = {
  fileInput: document.getElementById("fileInput"),
  selectFileBtn: document.getElementById("selectFileBtn"),
  selectedFileName: document.getElementById("selectedFileName"),
  uploadBtn: document.getElementById("uploadBtn"),
  projectSelect: document.getElementById("projectSelect"),
  refreshProjectsBtn: document.getElementById("refreshProjectsBtn"),
  loadProjectBtn: document.getElementById("loadProjectBtn"),
  status: document.getElementById("status"),
  originalVideo: document.getElementById("originalVideo"),
  previewVideo: document.getElementById("previewVideo"),
  zoom: document.getElementById("zoom"),
  zoomLabel: document.getElementById("zoomLabel"),
  startRange: document.getElementById("startRange"),
  endRange: document.getElementById("endRange"),
  startInput: document.getElementById("startInput"),
  endInput: document.getElementById("endInput"),
  clipLengthLabel: document.getElementById("clipLengthLabel"),
  trimStartImage: document.getElementById("trimStartImage"),
  trimEndImage: document.getElementById("trimEndImage"),
  startToBeginningBtn: document.getElementById("startToBeginningBtn"),
  startPrevFrame: document.getElementById("startPrevFrame"),
  startNextFrame: document.getElementById("startNextFrame"),
  endPrevFrame: document.getElementById("endPrevFrame"),
  endNextFrame: document.getElementById("endNextFrame"),
  endToEndBtn: document.getElementById("endToEndBtn"),
  trimShiftButtons: document.querySelectorAll(".trim-shift-btn"),
  cropX: document.getElementById("cropX"),
  cropY: document.getElementById("cropY"),
  cropW: document.getElementById("cropW"),
  cropH: document.getElementById("cropH"),
  cropEnabled: document.getElementById("cropEnabled"),
  cropResetBtn: document.getElementById("cropResetBtn"),
  resizeMaxInput: document.getElementById("resizeMaxInput"),
  resizeResetBtn: document.getElementById("resizeResetBtn"),
  fpsInput: document.getElementById("fpsInput"),
  originalDims: document.getElementById("originalDims"),
  previewDims: document.getElementById("previewDims"),
  saveStateBtn: document.getElementById("saveStateBtn"),
  previewBtn: document.getElementById("previewBtn"),
  previewProgress: document.getElementById("previewProgress"),
  previewStatus: document.getElementById("previewStatus"),
  exportBtn: document.getElementById("exportBtn"),
  exportProgress: document.getElementById("exportProgress"),
  exportStatus: document.getElementById("exportStatus"),
  cropOverlay: document.getElementById("cropOverlay"),
  cropBox: document.getElementById("cropBox"),
  cropCloseBtn: document.getElementById("cropCloseBtn"),
  cropHandles: document.querySelectorAll(".crop-handle"),
  presetButtons: document.querySelectorAll(".preset"),
  resizePresetButtons: document.querySelectorAll(".resize-preset"),
  editorTabButtons: document.querySelectorAll(".editor-tab"),
  editorPanels: document.querySelectorAll(".editor-panel")
};

const UPLOAD_PICKER_ID = "vae-upload-video";

function setStatus(message) {
  el.status.textContent = message;
}

function setExportStatus(message) {
  el.exportStatus.textContent = message;
}

function setPreviewStatus(message) {
  el.previewStatus.textContent = message;
}

function setPreviewProgress(value) {
  el.previewProgress.classList.remove("hidden");
  el.previewProgress.value = Math.max(0, Math.min(100, value));
}

function setExportProgress(value) {
  el.exportProgress.classList.remove("hidden");
  el.exportProgress.value = Math.max(0, Math.min(100, value));
}

let trimPreviewTimer = null;
let previewProgressTimer = null;
let exportProgressTimer = null;

function stopPreviewProgressTimer() {
  if (!previewProgressTimer) return;
  clearInterval(previewProgressTimer);
  previewProgressTimer = null;
}

function startPreviewProgressTimer() {
  stopPreviewProgressTimer();
  previewProgressTimer = setInterval(() => {
    const current = Number(el.previewProgress.value || 0);
    if (current >= 95) return;
    const step = current < 70 ? 3 : 1;
    setPreviewProgress(current + step);
  }, 250);
}

function stopExportProgressTimer() {
  if (!exportProgressTimer) return;
  clearInterval(exportProgressTimer);
  exportProgressTimer = null;
}

function startExportProgressTimer() {
  stopExportProgressTimer();
  exportProgressTimer = setInterval(() => {
    const current = Number(el.exportProgress.value || 0);
    if (current >= 95) return;
    const step = current < 70 ? 3 : 1;
    setExportProgress(current + step);
  }, 250);
}

async function refreshTrimFrames() {
  if (!state.projectId || !state.metadata) return;
  const start = state.edit.trim.start;
  const end = state.edit.trim.end || state.metadata.duration;
  const response = await fetch(
    `/api/projects/${state.projectId}/trim-frames?start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}`
  );
  if (!response.ok) return;
  const payload = await response.json();
  el.trimStartImage.src = `${payload.start_url}?t=${Date.now()}`;
  el.trimEndImage.src = `${payload.end_url}?t=${Date.now()}`;
}

function scheduleTrimFramesRefresh() {
  if (trimPreviewTimer) {
    clearTimeout(trimPreviewTimer);
  }
  trimPreviewTimer = setTimeout(() => {
    refreshTrimFrames();
  }, 100);
}

function closeCropIndicator() {
  state.cropIndicatorVisible = false;
  updateCropUI();
}

function setProjectFromPayload(payload, options = {}) {
  const { forceCropOff = false } = options;
  state.projectId = payload.project_id;
  state.metadata = payload.metadata;
  state.originalUsesProxy = Boolean(payload.original_uses_proxy);
  state.edit = payload.state;
  state.edit.crop_enabled = Boolean(state.edit.crop_enabled);
  state.edit.resize_max = state.edit.resize_max ?? null;
  if (!state.edit.trim.end) {
    state.edit.trim.end = state.metadata.duration;
  }
  if (forceCropOff) {
    state.edit.crop_enabled = false;
  }
  el.originalVideo.src = payload.original_url;
  const previewUrl = forceCropOff ? payload.original_url : payload.preview_url;
  if (previewUrl) {
    el.previewVideo.src = `${previewUrl}?t=${Date.now()}`;
  } else {
    el.previewVideo.removeAttribute("src");
    el.previewVideo.load();
  }
  el.fpsInput.value = state.edit.fps ?? "";
  el.resizeMaxInput.value = state.edit.resize_max ?? "";
  el.cropEnabled.checked = state.edit.crop_enabled;
  state.cropIndicatorVisible = forceCropOff
    ? false
    : Boolean(state.edit.crop_enabled && state.edit.crop.width && state.edit.crop.height);
  updateTrimUI();
  updateCropUI();
  updateDimensionLabels();
}

function setActiveEditorPanel(panelName) {
  el.editorTabButtons.forEach((button) => {
    button.classList.toggle("active", button.dataset.panel === panelName);
  });
  el.editorPanels.forEach((panel) => {
    panel.classList.toggle("hidden", panel.dataset.panel !== panelName);
  });
}

function projectLabel(project) {
  const previewMark = project.has_preview ? "P" : "-";
  const exportMark = project.has_export ? "E" : "-";
  return `${project.project_id} [${previewMark}${exportMark}]`;
}

function detectLocalPath(value) {
  if (!value || typeof value !== "object") return null;
  const candidates = [value.path, value.filePath, value.filepath, value.fullPath, value.fullpath];
  for (const candidate of candidates) {
    if (typeof candidate === "string" && candidate.trim()) {
      return candidate.trim();
    }
  }
  return null;
}

function isAbsolutePath(pathValue) {
  if (!pathValue || typeof pathValue !== "string") return false;
  return /^[A-Za-z]:\\/.test(pathValue) || pathValue.startsWith("\\\\") || pathValue.startsWith("/");
}

function setSelectedFile(file) {
  state.selectedFile = file ?? null;
  const detectedPath = state.selectedFile ? detectLocalPath(state.selectedFile) : null;
  state.selectedFilePath = detectedPath && isAbsolutePath(detectedPath) ? detectedPath : null;
  el.selectedFileName.textContent = state.selectedFile ? state.selectedFile.name : "No file selected";
  el.uploadBtn.disabled = !state.selectedFile;
}

function openFallbackFileInput() {
  el.fileInput.click();
}

async function selectVideoFile() {
  if (typeof window.showOpenFilePicker === "function") {
    try {
      const [handle] = await window.showOpenFilePicker({
        id: UPLOAD_PICKER_ID,
        multiple: false,
        types: [{ description: "Video files", accept: { "video/*": [".mp4", ".mov", ".mkv", ".avi", ".webm", ".m4v"] } }]
      });
      const file = await handle.getFile();
      setSelectedFile(file);
      const handlePath = detectLocalPath(handle);
      if (handlePath && isAbsolutePath(handlePath)) {
        state.selectedFilePath = handlePath;
      }
      setStatus(state.selectedFilePath ? `Selected ${file.name} (local path detected)` : `Selected ${file.name}`);
      return;
    } catch (error) {
      if (error && error.name === "AbortError") {
        setStatus("File selection canceled.");
        return;
      }
    }
  }
  openFallbackFileInput();
}

async function refreshProjects() {
  const response = await fetch("/api/projects");
  if (!response.ok) {
    setStatus("Could not load previous projects.");
    return;
  }
  const projects = await response.json();
  el.projectSelect.innerHTML = "";
  if (!projects.length) {
    const option = document.createElement("option");
    option.value = "";
    option.textContent = "No previous projects";
    el.projectSelect.appendChild(option);
    return;
  }
  projects.forEach((project) => {
    const option = document.createElement("option");
    option.value = project.project_id;
    option.textContent = projectLabel(project);
    el.projectSelect.appendChild(option);
  });
}

async function loadSelectedProject() {
  const projectId = el.projectSelect.value;
  if (!projectId) {
    setStatus("No project selected.");
    return;
  }
  const response = await fetch(`/api/projects/${projectId}`);
  if (!response.ok) {
    setStatus(`Load failed: ${(await response.json()).detail}`);
    return;
  }
  const payload = await response.json();
  setProjectFromPayload(payload, { forceCropOff: true });
  await refreshTrimFrames();
  setStatus(
    payload.original_uses_proxy
      ? `Loaded project ${payload.project_id}. Original player is using a compatibility proxy.`
      : `Loaded project ${payload.project_id}`
  );
}

function frameStep() {
  if (!state.metadata || !state.metadata.fps) return 1 / 30;
  return 1 / state.metadata.fps;
}

function clampTrim() {
  if (!state.metadata) return;
  const duration = state.metadata.duration;
  state.edit.trim.start = Math.max(0, Math.min(state.edit.trim.start, duration));
  state.edit.trim.end = Math.max(0, Math.min(state.edit.trim.end, duration));
  if (state.edit.trim.end <= state.edit.trim.start) {
    state.edit.trim.end = Math.min(duration, state.edit.trim.start + frameStep());
  }
}

function nudgeTrimMarker(marker, deltaSeconds) {
  if (!state.metadata) return;
  if (marker === "start") {
    state.edit.trim.start += deltaSeconds;
  } else if (marker === "end") {
    state.edit.trim.end += deltaSeconds;
  } else {
    return;
  }
  clampTrim();
  updateTrimUI();
  scheduleTrimFramesRefresh();
}

function updateTrimUI() {
  if (!state.metadata) return;
  const duration = state.metadata.duration;
  el.startRange.max = duration;
  el.endRange.max = duration;
  el.startRange.value = state.edit.trim.start;
  el.endRange.value = state.edit.trim.end || duration;
  el.startInput.value = state.edit.trim.start.toFixed(3);
  el.endInput.value = (state.edit.trim.end || duration).toFixed(3);
  updateClipLengthUI();
}

function formatSeconds(seconds) {
  const safe = Math.max(0, seconds);
  const mins = Math.floor(safe / 60);
  const secs = safe - mins * 60;
  const minText = String(mins).padStart(2, "0");
  const secText = secs.toFixed(3).padStart(6, "0");
  return `${minText}:${secText}`;
}

function formatDimensions(width, height) {
  return `${width}x${height}`;
}

function getModifiedDimensions() {
  if (!state.metadata) return null;
  let width = state.metadata.width;
  let height = state.metadata.height;
  const crop = state.edit.crop;
  if (state.edit.crop_enabled && crop.width && crop.height) {
    width = crop.width;
    height = crop.height;
  }
  const sourceWidth = width;
  const sourceHeight = height;
  const resizeMax = state.edit.resize_max;
  if (Number.isFinite(resizeMax) && resizeMax >= 2) {
    if (width >= height && width > resizeMax) {
      width = resizeMax;
      height = Math.max(2, Math.round((sourceHeight * resizeMax) / Math.max(1, sourceWidth)));
    } else if (height > width && height > resizeMax) {
      height = resizeMax;
      width = Math.max(2, Math.round((sourceWidth * resizeMax) / Math.max(1, sourceHeight)));
    }
    if (width % 2 !== 0) width -= 1;
    if (height % 2 !== 0) height -= 1;
  }
  return { width: Math.max(2, width), height: Math.max(2, height) };
}

function updateDimensionLabels() {
  if (!state.metadata) {
    el.originalDims.textContent = "(—)";
    el.previewDims.textContent = "(—)";
    return;
  }
  const originalText = formatDimensions(state.metadata.width, state.metadata.height);
  const modified = getModifiedDimensions();
  const previewText = formatDimensions(modified.width, modified.height);
  el.originalDims.textContent = `(${originalText})`;
  el.previewDims.textContent = `(${previewText})`;
}

function updateClipLengthUI() {
  if (!state.metadata) {
    el.clipLengthLabel.textContent = "Clip length: 00:00.000";
    return;
  }
  const start = state.edit.trim.start;
  const end = state.edit.trim.end || state.metadata.duration;
  const length = Math.max(0, end - start);
  el.clipLengthLabel.textContent = `Clip length: ${formatSeconds(length)}`;
}

function updateZoomUI() {
  const value = Number(el.zoom.value);
  el.zoomLabel.textContent = `${value.toFixed(2)}s`;
}

function updateCropUI() {
  const crop = state.edit.crop;
  const cropEnabled = Boolean(state.edit.crop_enabled);
  el.cropEnabled.checked = cropEnabled;
  el.cropX.value = crop.x;
  el.cropY.value = crop.y;
  el.cropW.value = crop.width;
  el.cropH.value = crop.height;
  const cropControls = [el.cropX, el.cropY, el.cropW, el.cropH, ...el.presetButtons, el.cropResetBtn];
  cropControls.forEach((control) => {
    control.disabled = !cropEnabled;
  });
  updateDimensionLabels();
  if (!state.metadata) {
    el.cropOverlay.classList.add("hidden");
    return;
  }
  el.cropOverlay.classList.remove("hidden");
  if (!cropEnabled || !crop.width || !crop.height || !state.cropIndicatorVisible) {
    el.cropBox.classList.add("hidden");
    return;
  }
  el.cropBox.classList.remove("hidden");
  const rect = el.previewVideo.getBoundingClientRect();
  const sx = rect.width / state.metadata.width;
  const sy = rect.height / state.metadata.height;
  el.cropBox.style.left = `${crop.x * sx}px`;
  el.cropBox.style.top = `${crop.y * sy}px`;
  el.cropBox.style.width = `${crop.width * sx}px`;
  el.cropBox.style.height = `${crop.height * sy}px`;
}

function applyPreset(ratio) {
  if (!state.metadata) return;
  const [rw, rh] = ratio.split(":").map(Number);
  const w = state.metadata.width;
  const h = Math.floor((w * rh) / rw);
  if (h <= state.metadata.height) {
    state.edit.crop = { x: 0, y: Math.floor((state.metadata.height - h) / 2), width: w, height: h, preset: ratio };
  } else {
    const nh = state.metadata.height;
    const nw = Math.floor((nh * rw) / rh);
    state.edit.crop = { x: Math.floor((state.metadata.width - nw) / 2), y: 0, width: nw, height: nh, preset: ratio };
  }
  state.edit.crop_enabled = true;
  el.cropEnabled.checked = true;
  state.cropIndicatorVisible = true;
  updateCropUI();
}

function resetCrop() {
  if (!state.metadata) return;
  state.edit.crop = {
    x: 0,
    y: 0,
    width: state.metadata.width,
    height: state.metadata.height,
    preset: null
  };
  state.cropIndicatorVisible = Boolean(state.edit.crop_enabled);
  updateCropUI();
}

async function uploadVideo() {
  const file = state.selectedFile ?? el.fileInput.files[0];
  if (!file) {
    setStatus("Select a file first.");
    return;
  }
  setStatus("Uploading...");
  let response;
  let mode = "uploaded copy";
  const sourcePath = state.selectedFilePath || detectLocalPath(file);
  if (sourcePath && isAbsolutePath(sourcePath)) {
    mode = "local path";
    response = await fetch("/api/projects/from-path", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ source_path: sourcePath })
    });
  } else {
    const form = new FormData();
    form.append("file", file, file.name);
    response = await fetch("/api/projects", { method: "POST", body: form });
  }
  if (!response.ok) {
    setStatus(`Upload failed: ${(await response.json()).detail}`);
    return;
  }
  const payload = await response.json();
  setProjectFromPayload(payload);
  await refreshTrimFrames();
  await refreshProjects();
  el.projectSelect.value = payload.project_id;
  const proxyMessage = payload.original_uses_proxy ? " Original player compatibility proxy is ready." : "";
  if (mode === "local path") {
    setStatus(`Upload complete: loaded project ${state.projectId} via local path.${proxyMessage}`);
  } else if (sourcePath) {
    setStatus(
      `Upload complete: loaded project ${state.projectId} via uploaded copy (local path unavailable).${proxyMessage}`
    );
  } else {
    setStatus(`Upload complete: loaded project ${state.projectId} via uploaded copy.${proxyMessage}`);
  }
}

async function saveState(options = {}) {
  const { silent = false } = options;
  if (!state.projectId) {
    if (!silent) setStatus("Upload a video first.");
    return;
  }
  clampTrim();
  state.edit.crop_enabled = Boolean(el.cropEnabled.checked);
  const fpsValue = Number(el.fpsInput.value);
  state.edit.fps = Number.isFinite(fpsValue) && fpsValue > 0 ? fpsValue : null;
  const resizeMax = Number(el.resizeMaxInput.value);
  state.edit.resize_max = Number.isInteger(resizeMax) && resizeMax >= 2 ? resizeMax : null;
  state.edit.crop.x = Number(el.cropX.value || 0);
  state.edit.crop.y = Number(el.cropY.value || 0);
  state.edit.crop.width = Number(el.cropW.value || 0);
  state.edit.crop.height = Number(el.cropH.value || 0);
  if (!state.edit.crop_enabled) {
    state.cropIndicatorVisible = false;
  }
  const response = await fetch(`/api/projects/${state.projectId}/state`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ state: state.edit })
  });
  if (!response.ok) {
    const detail = (await response.json()).detail;
    if (!silent) setStatus(`Save failed: ${detail}`);
    if (silent) throw new Error(detail);
    return;
  }
  if (!silent) setStatus("State saved.");
}

async function refreshPreview() {
  if (!state.projectId) return;
  el.previewBtn.disabled = true;
  setPreviewProgress(5);
  setPreviewStatus("Saving state...");
  try {
    await saveState({ silent: true });
    closeCropIndicator();
    setPreviewProgress(15);
    setPreviewStatus("Rendering preview...");
    setStatus("Rendering preview...");
    startPreviewProgressTimer();
    const response = await fetch(`/api/projects/${state.projectId}/preview`, { method: "POST" });
    if (!response.ok) {
      throw new Error((await response.json()).detail);
    }
    const payload = await response.json();
    stopPreviewProgressTimer();
    setPreviewProgress(100);
    setPreviewStatus("Complete");
    el.previewVideo.src = `${payload.output_url}?t=${Date.now()}`;
    setStatus("Preview ready.");
  } catch (error) {
    stopPreviewProgressTimer();
    setPreviewStatus("Failed");
    setStatus(`Preview failed: ${error.message}`);
  } finally {
    el.previewBtn.disabled = false;
  }
}

async function exportVideo() {
  if (!state.projectId) return;
  el.exportBtn.disabled = true;
  setExportProgress(5);
  setExportStatus("Saving state...");
  try {
    await saveState({ silent: true });
    setExportProgress(15);
    setExportStatus("Rendering export...");
    setStatus("Exporting...");
    startExportProgressTimer();
    const response = await fetch(`/api/projects/${state.projectId}/export`, { method: "POST" });
    if (!response.ok) {
      throw new Error((await response.json()).detail);
    }
    const payload = await response.json();
    stopExportProgressTimer();
    setExportProgress(100);
    setExportStatus("Saved");
    setStatus(`Export complete. Saved to ${payload.output_path}.`);
  } catch (error) {
    stopExportProgressTimer();
    setExportStatus("Failed");
    setStatus(`Export failed: ${error.message}`);
  } finally {
    el.exportBtn.disabled = false;
  }
}

el.selectFileBtn.addEventListener("click", selectVideoFile);
el.fileInput.addEventListener("change", () => {
  setSelectedFile(el.fileInput.files[0] ?? null);
});
el.uploadBtn.addEventListener("click", uploadVideo);
el.refreshProjectsBtn.addEventListener("click", refreshProjects);
el.loadProjectBtn.addEventListener("click", loadSelectedProject);
el.saveStateBtn.addEventListener("click", saveState);
el.previewBtn.addEventListener("click", refreshPreview);
el.exportBtn.addEventListener("click", exportVideo);
el.zoom.addEventListener("input", updateZoomUI);
el.cropEnabled.addEventListener("change", () => {
  state.edit.crop_enabled = Boolean(el.cropEnabled.checked);
  if (!state.edit.crop_enabled) {
    state.cropIndicatorVisible = false;
  } else if (state.edit.crop.width && state.edit.crop.height) {
    state.cropIndicatorVisible = true;
  }
  updateCropUI();
});
el.cropResetBtn.addEventListener("click", resetCrop);
el.resizeMaxInput.addEventListener("change", () => {
  const resizeMax = Number(el.resizeMaxInput.value);
  state.edit.resize_max = Number.isInteger(resizeMax) && resizeMax >= 2 ? resizeMax : null;
  el.resizeMaxInput.value = state.edit.resize_max ?? "";
  updateDimensionLabels();
});
el.resizePresetButtons.forEach((button) => {
  button.addEventListener("click", () => {
    const value = Number(button.dataset.resize);
    state.edit.resize_max = Number.isInteger(value) && value >= 2 ? value : null;
    el.resizeMaxInput.value = state.edit.resize_max ?? "";
    updateDimensionLabels();
  });
});
el.resizeResetBtn.addEventListener("click", () => {
  state.edit.resize_max = null;
  el.resizeMaxInput.value = "";
  updateDimensionLabels();
});
el.editorTabButtons.forEach((button) => {
  button.addEventListener("click", () => setActiveEditorPanel(button.dataset.panel));
});

el.startRange.addEventListener("input", () => {
  state.edit.trim.start = Number(el.startRange.value);
  clampTrim();
  updateTrimUI();
  scheduleTrimFramesRefresh();
});

el.endRange.addEventListener("input", () => {
  state.edit.trim.end = Number(el.endRange.value);
  clampTrim();
  updateTrimUI();
  scheduleTrimFramesRefresh();
});

el.startInput.addEventListener("change", () => {
  state.edit.trim.start = Number(el.startInput.value || 0);
  clampTrim();
  updateTrimUI();
  scheduleTrimFramesRefresh();
});

el.endInput.addEventListener("change", () => {
  state.edit.trim.end = Number(el.endInput.value || 0);
  clampTrim();
  updateTrimUI();
  scheduleTrimFramesRefresh();
});

el.startPrevFrame.addEventListener("click", () => {
  state.edit.trim.start -= frameStep();
  clampTrim();
  updateTrimUI();
  scheduleTrimFramesRefresh();
});

el.startToBeginningBtn.addEventListener("click", () => {
  state.edit.trim.start = 0;
  clampTrim();
  updateTrimUI();
  scheduleTrimFramesRefresh();
});

el.startNextFrame.addEventListener("click", () => {
  state.edit.trim.start += frameStep();
  clampTrim();
  updateTrimUI();
  scheduleTrimFramesRefresh();
});

el.endPrevFrame.addEventListener("click", () => {
  state.edit.trim.end -= frameStep();
  clampTrim();
  updateTrimUI();
  scheduleTrimFramesRefresh();
});

el.endNextFrame.addEventListener("click", () => {
  state.edit.trim.end += frameStep();
  clampTrim();
  updateTrimUI();
  scheduleTrimFramesRefresh();
});

el.endToEndBtn.addEventListener("click", () => {
  if (!state.metadata) return;
  state.edit.trim.end = state.metadata.duration;
  clampTrim();
  updateTrimUI();
  scheduleTrimFramesRefresh();
});

el.trimShiftButtons.forEach((button) => {
  button.addEventListener("click", () => {
    const marker = button.dataset.target;
    const deltaSeconds = Number(button.dataset.seconds || 0);
    if (!Number.isFinite(deltaSeconds)) return;
    nudgeTrimMarker(marker, deltaSeconds);
  });
});

el.presetButtons.forEach((button) => {
  button.addEventListener("click", () => applyPreset(button.dataset.preset));
});

let dragStart = null;
let cornerDrag = null;

el.previewVideo.addEventListener("mousedown", (event) => {
  if (!state.metadata) return;
  if (!state.edit.crop_enabled) return;
  if (event.target !== el.previewVideo) {
    return;
  }
  const rect = el.previewVideo.getBoundingClientRect();
  const x = Math.max(0, Math.min(event.clientX - rect.left, rect.width));
  const y = Math.max(0, Math.min(event.clientY - rect.top, rect.height));
  dragStart = { x, y, rect };
});

window.addEventListener("mousemove", (event) => {
  if (!dragStart || !state.metadata) return;
  if (!state.edit.crop_enabled) return;
  const x2 = Math.max(0, Math.min(event.clientX - dragStart.rect.left, dragStart.rect.width));
  const y2 = Math.max(0, Math.min(event.clientY - dragStart.rect.top, dragStart.rect.height));
  const x1 = Math.min(dragStart.x, x2);
  const y1 = Math.min(dragStart.y, y2);
  const w = Math.abs(x2 - dragStart.x);
  const h = Math.abs(y2 - dragStart.y);
  const sx = state.metadata.width / dragStart.rect.width;
  const sy = state.metadata.height / dragStart.rect.height;
  state.edit.crop = {
    x: Math.round(x1 * sx),
    y: Math.round(y1 * sy),
    width: Math.round(w * sx),
    height: Math.round(h * sy),
    preset: null
  };
  state.cropIndicatorVisible = true;
  updateCropUI();
});

el.cropHandles.forEach((handle) => {
  handle.addEventListener("mousedown", (event) => {
    if (!state.metadata || !state.edit.crop_enabled || !state.edit.crop.width || !state.edit.crop.height) return;
    event.stopPropagation();
    const rect = el.previewVideo.getBoundingClientRect();
    cornerDrag = {
      corner: handle.dataset.corner,
      rect,
      crop: { ...state.edit.crop }
    };
  });
});

window.addEventListener("mousemove", (event) => {
  if (!cornerDrag || !state.metadata) return;
  const { rect, corner, crop } = cornerDrag;
  const sx = state.metadata.width / rect.width;
  const sy = state.metadata.height / rect.height;
  const px = Math.max(0, Math.min(event.clientX - rect.left, rect.width));
  const py = Math.max(0, Math.min(event.clientY - rect.top, rect.height));
  const vx = Math.round(px * sx);
  const vy = Math.round(py * sy);

  let x1 = crop.x;
  let y1 = crop.y;
  let x2 = crop.x + crop.width;
  let y2 = crop.y + crop.height;

  if (corner.includes("l")) x1 = vx;
  if (corner.includes("r")) x2 = vx;
  if (corner.includes("t")) y1 = vy;
  if (corner.includes("b")) y2 = vy;

  const minW = 2;
  const minH = 2;
  const nx1 = Math.max(0, Math.min(x1, state.metadata.width - minW));
  const ny1 = Math.max(0, Math.min(y1, state.metadata.height - minH));
  const nx2 = Math.max(nx1 + minW, Math.min(x2, state.metadata.width));
  const ny2 = Math.max(ny1 + minH, Math.min(y2, state.metadata.height));

  state.edit.crop = {
    x: nx1,
    y: ny1,
    width: nx2 - nx1,
    height: ny2 - ny1,
    preset: null
  };
  state.cropIndicatorVisible = true;
  updateCropUI();
});

window.addEventListener("mouseup", () => {
  dragStart = null;
  cornerDrag = null;
});

el.cropCloseBtn.addEventListener("click", (event) => {
  event.stopPropagation();
  closeCropIndicator();
});

updateZoomUI();
updateDimensionLabels();
setActiveEditorPanel("trim");
setSelectedFile(null);
refreshProjects();


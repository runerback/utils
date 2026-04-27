const state = {
  selectedProjectId: null,
  pollTimer: null,
};

const projectSelect = document.getElementById("project-select");
const refreshProjectsButton = document.getElementById("refresh-projects");
const uploadForm = document.getElementById("upload-form");
const sourceFileInput = document.getElementById("source-file");
const startButton = document.getElementById("start-button");
const actionMessage = document.getElementById("action-message");
const projectTitle = document.getElementById("project-title");
const projectStatus = document.getElementById("project-status");
const progressLabel = document.getElementById("progress-label");
const progressFill = document.getElementById("progress-fill");
const errorMessage = document.getElementById("error-message");
const sourcePlayer = document.getElementById("source-player");
const sourceEmpty = document.getElementById("source-empty");
const debugPlayer = document.getElementById("debug-player");
const debugEmpty = document.getElementById("debug-empty");
const manifestView = document.getElementById("manifest-view");
const traceView = document.getElementById("trace-view");
const clipsList = document.getElementById("clips-list");
const clipsEmpty = document.getElementById("clips-empty");

async function fetchJson(url, options = {}) {
  const response = await fetch(url, options);
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(payload.error || `Request failed: ${response.status}`);
  }
  return payload;
}

function setActionMessage(message, isError = false) {
  actionMessage.textContent = message || "";
  actionMessage.classList.toggle("error", isError);
}

function clearPollTimer() {
  if (state.pollTimer !== null) {
    window.clearTimeout(state.pollTimer);
    state.pollTimer = null;
  }
}

function schedulePoll(project) {
  clearPollTimer();
  if (!project || !["queued", "running"].includes(project.status)) {
    return;
  }
  state.pollTimer = window.setTimeout(() => loadProjectDetail(project.project_id, { refreshList: true }), 1000);
}

function populateProjectSelect(projects) {
  const previousValue = state.selectedProjectId;
  projectSelect.innerHTML = "";

  for (const project of projects) {
    const option = document.createElement("option");
    option.value = project.project_id;
    option.textContent = `${project.display_name} (${project.status})`;
    projectSelect.appendChild(option);
  }

  if (projects.length === 0) {
    state.selectedProjectId = null;
    return;
  }

  const nextSelection = projects.some((project) => project.project_id === previousValue)
    ? previousValue
    : projects[0].project_id;
  state.selectedProjectId = nextSelection;
  projectSelect.value = nextSelection;
}

function setVideoState(player, emptyNode, url) {
  if (url) {
    player.src = url;
    player.classList.remove("hidden");
    emptyNode.classList.add("hidden");
  } else {
    player.removeAttribute("src");
    player.load();
    player.classList.add("hidden");
    emptyNode.classList.remove("hidden");
  }
}

function renderClips(clips) {
  clipsList.innerHTML = "";
  if (!clips || clips.length === 0) {
    clipsEmpty.classList.remove("hidden");
    return;
  }

  clipsEmpty.classList.add("hidden");
  for (const clip of clips) {
    const card = document.createElement("article");
    card.className = "clip-card";

    const title = document.createElement("h4");
    title.textContent = clip.name;
    card.appendChild(title);

    const player = document.createElement("video");
    player.controls = true;
    player.preload = "metadata";
    player.src = clip.url;
    card.appendChild(player);

    clipsList.appendChild(card);
  }
}

async function loadTrace(traceUrl) {
  if (!traceUrl) {
    traceView.textContent = "No trace log loaded.";
    return;
  }
  const response = await fetch(traceUrl);
  traceView.textContent = response.ok ? await response.text() : "Unable to load trace log.";
}

function renderProject(project) {
  projectTitle.textContent = project.display_name;
  projectStatus.textContent = `${project.status} - ${project.message}`;
  progressLabel.textContent = `${Math.round(project.progress_percent || 0)}%`;
  progressFill.style.width = `${project.progress_percent || 0}%`;

  if (project.error) {
    errorMessage.textContent = project.error;
    errorMessage.classList.remove("hidden");
  } else {
    errorMessage.textContent = "";
    errorMessage.classList.add("hidden");
  }

  startButton.disabled = !project.can_start;
  setVideoState(sourcePlayer, sourceEmpty, project.source_url);
  setVideoState(debugPlayer, debugEmpty, project.debug_preview_url);
  manifestView.textContent = project.manifest ? JSON.stringify(project.manifest, null, 2) : "No manifest loaded.";
  renderClips(project.clips);
  loadTrace(project.trace_url);
  schedulePoll(project);
}

async function refreshProjects({ loadSelection = true } = {}) {
  const payload = await fetchJson("/api/projects");
  populateProjectSelect(payload.projects || []);
  if (loadSelection && state.selectedProjectId) {
    await loadProjectDetail(state.selectedProjectId, { refreshList: false });
  }
}

async function loadProjectDetail(projectId, { refreshList = false } = {}) {
  if (!projectId) {
    return;
  }
  state.selectedProjectId = projectId;
  const payload = await fetchJson(`/api/projects/${projectId}`);
  renderProject(payload.project);
  if (refreshList) {
    const projectsPayload = await fetchJson("/api/projects");
    populateProjectSelect(projectsPayload.projects || []);
    if (state.selectedProjectId) {
      projectSelect.value = state.selectedProjectId;
    }
  }
}

refreshProjectsButton.addEventListener("click", async () => {
  try {
    setActionMessage("Refreshing project list.");
    await refreshProjects();
    setActionMessage("");
  } catch (error) {
    setActionMessage(error.message, true);
  }
});

projectSelect.addEventListener("change", async (event) => {
  const projectId = event.target.value;
  try {
    await loadProjectDetail(projectId);
    setActionMessage("");
  } catch (error) {
    setActionMessage(error.message, true);
  }
});

uploadForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!sourceFileInput.files || sourceFileInput.files.length === 0) {
    setActionMessage("Choose a source video before uploading.", true);
    return;
  }

  const formData = new FormData();
  formData.append("source", sourceFileInput.files[0]);

  try {
    setActionMessage("Uploading source video.");
    const payload = await fetchJson("/api/projects", {
      method: "POST",
      body: formData,
    });
    sourceFileInput.value = "";
    state.selectedProjectId = payload.project.project_id;
    await refreshProjects({ loadSelection: false });
    await loadProjectDetail(payload.project.project_id);
    setActionMessage("Source uploaded.");
  } catch (error) {
    setActionMessage(error.message, true);
  }
});

startButton.addEventListener("click", async () => {
  if (!state.selectedProjectId) {
    return;
  }

  try {
    setActionMessage("Starting video handling.");
    const payload = await fetchJson(`/api/projects/${state.selectedProjectId}/start`, {
      method: "POST",
    });
    await refreshProjects({ loadSelection: false });
    renderProject(payload.project);
    setActionMessage("Video handling started.");
  } catch (error) {
    setActionMessage(error.message, true);
  }
});

refreshProjects().catch((error) => {
  setActionMessage(error.message, true);
});

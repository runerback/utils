const APP = window.APP;
let currentTopicId = null;
let eventSource = null;

function api(path, options = {}) {
  const headers = {
    "X-CSRFToken": APP.csrfToken,
    ...options.headers,
  };
  return fetch(path, { ...options, headers });
}

// --- Tabs ---

document.querySelectorAll(".tab-btn").forEach((btn) => {
  btn.addEventListener("click", () => {
    document
      .querySelectorAll(".tab-btn")
      .forEach((b) => b.classList.remove("active"));
    document
      .querySelectorAll(".tab-content")
      .forEach((c) => c.classList.remove("active"));
    btn.classList.add("active");
    document.getElementById("tab-" + btn.dataset.tab).classList.add("active");

    if (btn.dataset.tab === "messages") {
      loadTopics();
    }
  });
});

// --- Messages: topic list ---

const topicListEl = document.getElementById("messages-topic-list");
const chatroomEl = document.getElementById("messages-chatroom");
const topicsContainer = document.getElementById("topics-container");
const addTopicBtn = document.getElementById("add-topic-btn");
const addTopicForm = document.getElementById("add-topic-form");
const newTopicNameInput = document.getElementById("new-topic-name");
const cancelAddTopicBtn = document.getElementById("cancel-add-topic");

addTopicBtn.addEventListener("click", () => {
  addTopicBtn.classList.add("hidden");
  addTopicForm.classList.remove("hidden");
  newTopicNameInput.focus();
});

cancelAddTopicBtn.addEventListener("click", () => {
  addTopicBtn.classList.remove("hidden");
  addTopicForm.classList.add("hidden");
  addTopicForm.reset();
});

addTopicForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const name = newTopicNameInput.value.trim();
  if (!name) return;

  const resp = await api("/api/topics", {
    method: "POST",
    body: new URLSearchParams({ name }),
  });
  if (resp.ok) {
    addTopicForm.reset();
    addTopicBtn.classList.remove("hidden");
    addTopicForm.classList.add("hidden");
    loadTopics();
  } else {
    const data = await resp.json();
    alert(data.error || "Failed to add topic");
  }
});

async function loadTopics() {
  const resp = await api("/api/topics");
  const topics = await resp.json();
  renderTopics(topics);
}

function renderTopics(topics) {
  topicsContainer.innerHTML = "";
  if (topics.length === 0) {
    topicsContainer.innerHTML =
      '<p class="muted">No topics yet. Add one to start chatting.</p>';
    return;
  }

  topics.forEach((topic) => {
    const el = document.createElement("div");
    el.className = "card topic-item";
    el.innerHTML = `
      <div class="topic-main">
        <span class="topic-status"></span>
        <div class="topic-info">
          <div class="topic-name">${escapeHtml(topic.name)}</div>
          <div class="topic-preview">${escapeHtml(
            topic.latest_body || "No messages yet"
          )}</div>
        </div>
      </div>
      <button class="btn btn-small topic-delete" data-id="${topic.id}">Delete</button>
    `;
    el.addEventListener("click", (e) => {
      if (e.target.classList.contains("topic-delete")) {
        e.stopPropagation();
        deleteTopic(topic.id);
      } else {
        openChatroom(topic.id, topic.name);
      }
    });
    topicsContainer.appendChild(el);
  });
}

async function deleteTopic(id) {
  if (!confirm("Delete this topic?")) return;
  const resp = await api(`/api/topics/${id}`, { method: "DELETE" });
  if (resp.ok) {
    if (currentTopicId === id) closeChatroom();
    loadTopics();
  }
}

// --- Messages: chatroom ---

const chatTopicNameEl = document.getElementById("chat-topic-name");
const chatMessagesEl = document.getElementById("chat-messages");
const chatForm = document.getElementById("chat-form");
const chatInput = document.getElementById("chat-input");
const backToTopicsBtn = document.getElementById("back-to-topics");

backToTopicsBtn.addEventListener("click", closeChatroom);

async function openChatroom(id, name) {
  currentTopicId = id;
  topicListEl.classList.add("hidden");
  chatroomEl.classList.remove("hidden");
  chatTopicNameEl.textContent = name;
  chatMessagesEl.innerHTML = "";
  chatInput.value = "";
  chatInput.disabled = false;

  const resp = await api(`/api/topics/${id}/messages`);
  const messages = await resp.json();
  messages.forEach((msg) => appendMessage(msg));
  scrollToBottom();

  connectSSE();
}

function closeChatroom() {
  currentTopicId = null;
  chatroomEl.classList.add("hidden");
  topicListEl.classList.remove("hidden");
  chatInput.value = "";
  if (eventSource) {
    eventSource.close();
    eventSource = null;
  }
}

chatForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const body = chatInput.value.trim();
  if (!body || !currentTopicId) return;

  const submitBtn = chatForm.querySelector('button[type="submit"]');
  chatInput.disabled = true;
  submitBtn.disabled = true;
  submitBtn.textContent = "Sending...";

  const resp = await api(`/api/topics/${currentTopicId}/messages`, {
    method: "POST",
    body: new URLSearchParams({ body }),
  });

  chatInput.disabled = false;
  submitBtn.disabled = false;
  submitBtn.textContent = "Send";

  if (resp.ok) {
    chatInput.value = "";
    chatInput.focus();
  } else {
    const data = await resp.json();
    alert(data.error || "Failed to send message");
  }
});

function appendMessage(msg) {
  const isMine = msg.sender === APP.username;
  const row = document.createElement("div");
  row.className = `message-row ${isMine ? "mine" : "theirs"}`;
  const senderLabel = isMine ? "You" : escapeHtml(msg.sender || "Unknown");
  row.innerHTML = `
    <div class="message-bubble">
      <div class="message-meta">${senderLabel} · ${formatTime(
    msg.sent_at
  )}</div>
      <div>${escapeHtml(msg.body)}</div>
    </div>
  `;
  chatMessagesEl.appendChild(row);
  scrollToBottom();
}

function scrollToBottom() {
  chatMessagesEl.scrollTop = chatMessagesEl.scrollHeight;
}

function connectSSE() {
  if (eventSource) return;
  eventSource = new EventSource("/api/messages/stream");
  eventSource.onmessage = (e) => {
    const data = JSON.parse(e.data);
    if (data.type === "message" && data.topic === chatTopicNameEl.textContent) {
      appendMessage({
        sender: data.sender,
        body: data.body,
        sent_at: data.sent_at || new Date().toISOString(),
      });
    }
  };
  eventSource.onerror = () => {
    if (eventSource) {
      eventSource.close();
      eventSource = null;
    }
    setTimeout(connectSSE, 3000);
  };
}

// --- Logs ---

const logsOutput = document.getElementById("logs-output");
const refreshLogsBtn = document.getElementById("refresh-logs");
const copyLogsBtn = document.getElementById("copy-logs");
const clearLogsBtn = document.getElementById("clear-logs");

async function loadLogs() {
  const resp = await api("/api/logs");
  const data = await resp.json();
  if (data.error) {
    logsOutput.textContent = `Error: ${data.error}`;
  } else {
    logsOutput.textContent = data.lines.join("\n") || "No log entries.";
  }
}

refreshLogsBtn.addEventListener("click", loadLogs);

copyLogsBtn.addEventListener("click", async () => {
  try {
    await navigator.clipboard.writeText(logsOutput.textContent);
    copyLogsBtn.textContent = "Copied!";
    setTimeout(() => (copyLogsBtn.textContent = "Copy"), 1500);
  } catch (err) {
    alert("Copy failed");
  }
});

clearLogsBtn.addEventListener("click", async () => {
  if (!confirm("Clear logs?")) return;
  const resp = await api("/api/logs/clear", { method: "POST" });
  if (resp.ok) {
    loadLogs();
  } else {
    const data = await resp.json();
    alert(data.error || "Failed to clear logs");
  }
});

// --- Settings ---

const settingsForm = document.getElementById("settings-form");

settingsForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const formData = new FormData(settingsForm);
  if (!formData.has("logs.use_journal")) {
    formData.append("logs.use_journal", "false");
  }

  const resp = await api("/api/settings", {
    method: "POST",
    body: formData,
  });
  if (resp.ok) {
    alert("Settings saved");
  } else {
    const data = await resp.json();
    alert(data.error || "Failed to save settings");
  }
});

// --- Utilities ---

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

function formatTime(isoString) {
  if (!isoString) return "";
  const date = new Date(isoString);
  return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

// Initial loads
loadTopics();
loadLogs();

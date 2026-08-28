const DEFAULT_API_URL = '/api'
const TOKEN_KEY = 'ntfy_mgr_token'
const SERVER_URL_KEY = 'ntfy_mgr_server_url'

const MAX_LOG_LINES = 200

function formatTime(date) {
  return date.toLocaleTimeString('en-US', { hour12: false })
}

function log(message) {
  const timestamp = formatTime(new Date())
  const line = `[${timestamp}] ${message}`
  const lines = getLogs()
  lines.push(line)
  while (lines.length > MAX_LOG_LINES) {
    lines.shift()
  }
  localStorage.setItem('ntfy_mgr_logs', JSON.stringify(lines))
}

function getLogs() {
  try {
    const raw = localStorage.getItem('ntfy_mgr_logs')
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

export const logs = {
  append: log,
  getAll: () => getLogs().join('\n'),
  clear: () => localStorage.removeItem('ntfy_mgr_logs'),
}

function getServerUrl() {
  const envUrl = import.meta.env.VITE_API_URL
  if (envUrl && envUrl !== DEFAULT_API_URL) {
    return envUrl
  }
  return localStorage.getItem(SERVER_URL_KEY) || DEFAULT_API_URL
}

function setServerUrl(url) {
  const trimmed = url.trim().trimEnd('/')
  localStorage.setItem(SERVER_URL_KEY, trimmed)
  log(`Server URL set to ${trimmed}`)
}

function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

function headers() {
  const h = { 'Content-Type': 'application/json' }
  const token = getToken()
  if (token) {
    h['Authorization'] = `Bearer ${token}`
    log('Added Authorization header')
  } else {
    log('No token available')
  }
  return h
}

async function request(method, path, body) {
  const baseUrl = getServerUrl()
  const url = `${baseUrl}${path}`
  log(`${method} ${url}`)
  const options = {
    method,
    headers: headers(),
  }
  if (body !== undefined) {
    options.body = JSON.stringify(body)
  }
  let response
  try {
    response = await fetch(url, options)
  } catch (err) {
    log(`Network error: ${err.message}`)
    throw new Error(`Network error: ${err.message}`)
  }
  const data = response.headers.get('content-type')?.includes('application/json')
    ? await response.json()
    : await response.text()
  if (response.status === 401) {
    log(`Response ${response.status}: ${data.detail || 'Unauthorized'}`)
    clearToken()
    window.location.reload()
    throw new Error('Session expired')
  }
  if (!response.ok) {
    const detail = data.detail || data.error || String(data) || `HTTP ${response.status}`
    log(`Response ${response.status}: ${detail}`)
    throw new Error(detail)
  }
  log(`Response ${response.status}`)
  return data
}

export const api = {
  getServerUrl,
  setServerUrl,
  getToken,
  setToken,
  clearToken,

  login: (username, password) => request('POST', '/auth/login', { username, password }),
  logout: () => request('POST', '/auth/logout'),

  listUsers: () => request('GET', '/users'),
  createUser: (username, password) => request('POST', '/users', { username, password }),
  deleteUser: (name) => request('DELETE', `/users/${encodeURIComponent(name)}`),
  grantUserAccess: (name, topic, permission) => request('POST', `/users/${encodeURIComponent(name)}/access`, { topic, permission }),
  revokeUserAccess: (name, topic) => request('DELETE', `/users/${encodeURIComponent(name)}/access/${encodeURIComponent(topic)}`),
  createUserToken: (name, expires, label) => request('POST', `/users/${encodeURIComponent(name)}/tokens`, { expires, label }),
  deleteUserToken: (name, token) => request('DELETE', `/users/${encodeURIComponent(name)}/tokens/${encodeURIComponent(token)}`),

  listTopics: () => request('GET', '/topics'),
  grantTopicAccess: (topic, username, permission) => request('POST', `/topics/${encodeURIComponent(topic)}/access`, { username, permission }),
  revokeTopicAccess: (topic, username) => request('DELETE', `/topics/${encodeURIComponent(topic)}/access/${encodeURIComponent(username)}`),
  deleteTopic: (topic) => request('DELETE', `/topics/${encodeURIComponent(topic)}`),
}

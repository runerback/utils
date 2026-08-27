const API_URL = import.meta.env.VITE_API_URL || '/api'

function getToken() {
  return localStorage.getItem('ntfy_mgr_token')
}

function setToken(token) {
  localStorage.setItem('ntfy_mgr_token', token)
}

function clearToken() {
  localStorage.removeItem('ntfy_mgr_token')
}

function headers() {
  const h = { 'Content-Type': 'application/json' }
  const token = getToken()
  if (token) {
    h['Authorization'] = `Bearer ${token}`
  }
  return h
}

async function request(method, path, body) {
  const url = `${API_URL}${path}`
  const options = {
    method,
    headers: headers(),
  }
  if (body !== undefined) {
    options.body = JSON.stringify(body)
  }
  const response = await fetch(url, options)
  if (response.status === 401) {
    clearToken()
    window.location.reload()
    throw new Error('Session expired')
  }
  const data = response.headers.get('content-type')?.includes('application/json')
    ? await response.json()
    : await response.text()
  if (!response.ok) {
    throw new Error(data.detail || data.error || String(data) || `HTTP ${response.status}`)
  }
  return data
}

export const api = {
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

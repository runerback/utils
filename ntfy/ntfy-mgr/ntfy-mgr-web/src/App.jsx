import { useEffect, useRef, useState } from 'react'
import { api, logs } from './api/client'

function LoadingIndicator() {
  return (
    <div className="spinner">
      <svg viewBox="0 0 24 24" width="32" height="32">
        <path
          fill="currentColor"
          d="M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46A7.93 7.93 0 0020 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74A7.93 7.93 0 004 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z"
        />
      </svg>
    </div>
  )
}

function Login({ error, onLogin }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setLoading(true)
    try {
      const data = await api.login(username, password)
      api.setToken(data.token)
      onLogin()
    } catch (err) {
      logs.append(`Login failed: ${err.message}`)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login">
      <h1>ntfy Manager</h1>
      {error && <p className="error">{error}</p>}
      <form onSubmit={handleSubmit}>
        <label>
          Username
          <input
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoFocus
            required
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        <button type="submit" disabled={loading}>
          {loading ? 'Logging in...' : 'Login'}
        </button>
      </form>
    </div>
  )
}

function Dialog({ title, onClose, children }) {
  return (
    <div className="dialog-overlay" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-header">
          <h3>{title}</h3>
          <button className="icon-button" onClick={onClose}>×</button>
        </div>
        <div className="dialog-body">{children}</div>
      </div>
    </div>
  )
}

function ConfirmDialog({ title, text, confirmText = 'Delete', onConfirm, onDismiss }) {
  return (
    <Dialog title={title} onClose={onDismiss}>
      <p>{text}</p>
      <div className="dialog-actions">
        <button onClick={onDismiss}>Cancel</button>
        <button className="danger" onClick={onConfirm}>{confirmText}</button>
      </div>
    </Dialog>
  )
}

function AddUserDialog({ title, confirmText = 'Add', onConfirm, onDismiss }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [usernameError, setUsernameError] = useState(false)
  const [passwordError, setPasswordError] = useState(false)

  function handleConfirm() {
    setUsernameError(username.trim() === '')
    setPasswordError(password.trim() === '')
    if (username.trim() && password.trim()) {
      onConfirm(username.trim(), password)
    }
  }

  return (
    <Dialog title={title} onClose={onDismiss}>
      <label>
        Username
        <input
          type="text"
          value={username}
          onChange={(e) => {
            setUsername(e.target.value)
            setUsernameError(false)
          }}
          autoFocus
        />
        {usernameError && <span className="field-error">Username is required</span>}
      </label>
      <label>
        Password
        <input
          type="password"
          value={password}
          onChange={(e) => {
            setPassword(e.target.value)
            setPasswordError(false)
          }}
        />
        {passwordError && <span className="field-error">Password is required</span>}
      </label>
      <div className="dialog-actions">
        <button onClick={onDismiss}>Cancel</button>
        <button onClick={handleConfirm}>{confirmText}</button>
      </div>
    </Dialog>
  )
}

function CreateTopicDialog({ title, existingTopics = [], confirmText = 'Create', onConfirm, onDismiss }) {
  const [topic, setTopic] = useState('')
  const [username, setUsername] = useState('')
  const [permission, setPermission] = useState('read-write')
  const [topicError, setTopicError] = useState('')
  const [usernameError, setUsernameError] = useState(false)

  function handleConfirm() {
    let error = ''
    if (topic.trim() === '') {
      error = 'Topic is required'
    } else if (existingTopics.includes(topic.trim())) {
      error = 'Topic already exists'
    }
    setTopicError(error)
    setUsernameError(username.trim() === '')
    if (!error && username.trim()) {
      onConfirm(topic.trim(), username.trim(), permission)
    }
  }

  return (
    <Dialog title={title} onClose={onDismiss}>
      <label>
        Topic
        <input
          type="text"
          value={topic}
          onChange={(e) => {
            setTopic(e.target.value)
            setTopicError('')
          }}
          autoFocus
        />
        {topicError && <span className="field-error">{topicError}</span>}
      </label>
      <label>
        Initial user
        <input
          type="text"
          value={username}
          onChange={(e) => {
            setUsername(e.target.value)
            setUsernameError(false)
          }}
        />
        {usernameError && <span className="field-error">Username is required</span>}
      </label>
      <label>
        Permission
        <select value={permission} onChange={(e) => setPermission(e.target.value)}>
          <option value="read-write">read-write</option>
          <option value="read-only">read-only</option>
          <option value="write-only">write-only</option>
        </select>
      </label>
      <div className="dialog-actions">
        <button onClick={onDismiss}>Cancel</button>
        <button onClick={handleConfirm}>{confirmText}</button>
      </div>
    </Dialog>
  )
}

function GrantAccessDialog({
  title,
  targetLabel,
  targetValue,
  readOnlyTarget = false,
  confirmText = 'Grant',
  onConfirm,
  onDismiss,
}) {
  const [target, setTarget] = useState(targetValue)
  const [permission, setPermission] = useState('read-write')

  return (
    <Dialog title={title} onClose={onDismiss}>
      <label>
        {targetLabel}
        <input
          type="text"
          value={target}
          onChange={(e) => {
            if (!readOnlyTarget) setTarget(e.target.value)
          }}
          readOnly={readOnlyTarget}
        />
      </label>
      <label>
        Permission
        <select value={permission} onChange={(e) => setPermission(e.target.value)}>
          <option value="read-write">read-write</option>
          <option value="read-only">read-only</option>
          <option value="write-only">write-only</option>
        </select>
      </label>
      <div className="dialog-actions">
        <button onClick={onDismiss}>Cancel</button>
        <button onClick={() => onConfirm(target, permission)} disabled={target.trim() === ''}>
          {confirmText}
        </button>
      </div>
    </Dialog>
  )
}

function AddTokenDialog({ title, onConfirm, onDismiss }) {
  const [expires, setExpires] = useState('')
  const [label, setLabel] = useState('')

  return (
    <Dialog title={title} onClose={onDismiss}>
      <label>
        Expires (e.g. 30d, 1h), or leave empty
        <input type="text" value={expires} onChange={(e) => setExpires(e.target.value)} autoFocus />
      </label>
      <label>
        Label, or leave empty
        <input type="text" value={label} onChange={(e) => setLabel(e.target.value)} />
      </label>
      <div className="dialog-actions">
        <button onClick={onDismiss}>Cancel</button>
        <button onClick={() => onConfirm(expires, label)}>Create</button>
      </div>
    </Dialog>
  )
}

function LogViewDialog({ onDismiss }) {
  const [logText, setLogText] = useState(logs.getAll())
  const [copied, setCopied] = useState(false)

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(logText)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      // ignore
    }
  }

  return (
    <Dialog title="Logs" onClose={onDismiss}>
      <pre className="log-view">{logText || 'No logs yet'}</pre>
      <div className="dialog-actions">
        <button onClick={handleCopy} disabled={!logText}>
          {copied ? 'Copied' : 'Copy'}
        </button>
        <button
          className="danger"
          onClick={() => {
            logs.clear()
            setLogText('')
            onDismiss()
          }}
        >
          Clear
        </button>
      </div>
    </Dialog>
  )
}

function SettingsDialog({ serverUrl, hasToken, onSave, onDismiss, onLogout }) {
  const [url, setUrl] = useState(serverUrl)

  function handleSave() {
    onSave(url.trim().trimEnd('/'))
    onDismiss()
  }

  return (
    <Dialog title="Settings" onClose={onDismiss}>
      <label>
        Server URL
        <input
          type="text"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="http://127.0.0.1:20808"
          autoFocus
        />
      </label>
      {hasToken && (
        <button className="danger" onClick={onLogout}>
          Logout / Clear token
        </button>
      )}
      <div className="dialog-actions">
        <button onClick={onDismiss}>Cancel</button>
        <button onClick={handleSave}>Save</button>
      </div>
    </Dialog>
  )
}

function UsersTab({ users, topics, onChange, updateData, showMessage, showError }) {
  const [expanded, setExpanded] = useState(new Set())
  const [grantDialog, setGrantDialog] = useState(null)
  const [tokenDialogUser, setTokenDialogUser] = useState(null)
  const [deleteUserConfirm, setDeleteUserConfirm] = useState(null)
  const [revokeAccessConfirm, setRevokeAccessConfirm] = useState(null)
  const [deleteTokenConfirm, setDeleteTokenConfirm] = useState(null)

  const normalUsers = users.filter((u) => u.role === 'user')

  function toggle(user) {
    const next = new Set(expanded)
    if (next.has(user.name)) next.delete(user.name)
    else next.add(user.name)
    setExpanded(next)
  }

  async function run(action, success) {
    try {
      await action()
      if (success) showMessage(success)
    } catch (err) {
      showError(err.message)
    }
  }

  async function grantAccess(name, topic, permission) {
    await run(
      () => api.grantUserAccess(name, topic, permission),
      `Access granted for ${name} on ${topic}`
    )
    updateData((state) => ({
      users: state.users.map((u) =>
        u.name === name
          ? {
              ...u,
              accesses: [
                ...u.accesses.filter((a) => a.topic !== topic),
                { topic, permission, provisioned: false },
              ],
            }
          : u
      ),
      topics: state.topics.map((t) =>
        t.name === topic
          ? {
              ...t,
              accessors: [
                ...t.accessors.filter((a) => a.username !== name),
                { username: name, permission },
              ],
            }
          : t
      ),
    }))
  }

  async function revokeAccess(name, topic) {
    await run(
      () => api.revokeUserAccess(name, topic),
      `Access revoked for ${name} on ${topic}`
    )
    updateData((state) => ({
      users: state.users.map((u) =>
        u.name === name
          ? { ...u, accesses: u.accesses.filter((a) => a.topic !== topic) }
          : u
      ),
      topics: state.topics.map((t) =>
        t.name === topic
          ? { ...t, accessors: t.accessors.filter((a) => a.username !== name) }
          : t
      ),
    }))
  }

  async function createToken(name, expires, label) {
    await run(() => api.createUserToken(name, expires, label), `Token created for ${name}`)
    onChange()
  }

  async function deleteToken(name, token) {
    await run(() => api.deleteUserToken(name, token), 'Token deleted')
    updateData((state) => ({
      users: state.users.map((u) =>
        u.name === name ? { ...u, tokens: u.tokens.filter((t) => t.value !== token) } : u
      ),
      topics: state.topics,
    }))
  }

  async function deleteUser(name) {
    await run(() => api.deleteUser(name), `User ${name} deleted`)
    updateData((state) => ({
      users: state.users.filter((u) => u.name !== name),
      topics: state.topics.map((t) => ({
        ...t,
        accessors: t.accessors.filter((a) => a.username !== name),
      })),
    }))
  }

  async function copyToken(token) {
    try {
      await navigator.clipboard.writeText(token)
      showMessage('Copied')
    } catch {
      showError('Failed to copy')
    }
  }

  return (
    <div>
      <div className="list">
        {normalUsers.map((user) => (
          <div key={user.name} className="card">
            <div className="card-header" onClick={() => toggle(user)}>
              <span className="name">
                {expanded.has(user.name) ? '▼' : '▶'} {user.name}
              </span>
              <span className="meta">
                {user.accesses.length} accesses, {user.tokens.length} tokens
              </span>
              <span className="actions">
                <button
                  className="small danger"
                  onClick={(e) => {
                    e.stopPropagation()
                    setDeleteUserConfirm(user.name)
                  }}
                >
                  Delete
                </button>
              </span>
            </div>
            {expanded.has(user.name) && (
              <div className="card-body">
                <div className="section-header">
                  <strong>Accesses</strong>
                  <button
                    className="small"
                    onClick={() => setGrantDialog({ user, topic: '', readOnlyTopic: false })}
                  >
                    Grant
                  </button>
                </div>
                {user.accesses.length === 0 && <p>No topics</p>}
                {user.accesses.sort((a, b) => a.topic.localeCompare(b.topic)).map((access) => {
                  const granted = access.permission !== 'no'
                  return (
                    <label key={access.topic} className="checkbox-row">
                      <span className="checkbox-label">{access.topic}</span>
                      {granted && <span className="permission">{access.permission}</span>}
                      <input
                        type="checkbox"
                        checked={granted}
                        onChange={(e) => {
                          if (e.target.checked) {
                            setGrantDialog({
                              user,
                              topic: access.topic,
                              readOnlyTopic: true,
                            })
                          } else {
                            setRevokeAccessConfirm({ name: user.name, topic: access.topic })
                          }
                        }}
                      />
                    </label>
                  )
                })}

                <div className="section-header">
                  <strong>Tokens</strong>
                  {user.tokens.length === 0 && (
                    <button className="small" onClick={() => setTokenDialogUser(user)}>
                      Add
                    </button>
                  )}
                </div>
                {user.tokens.length === 0 && <p>None</p>}
                {user.tokens.map((token) => (
                  <div key={token.value} className="token-row">
                    <button className="small" onClick={() => copyToken(token.value)}>
                      Copy
                    </button>
                    <span className="token-value" title={token.label || ''}>
                      {token.value}
                    </span>
                    <span className="token-expires">{token.expires}</span>
                    <button
                      className="small danger"
                      onClick={() => setDeleteTokenConfirm({ name: user.name, token: token.value })}
                    >
                      Delete
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>

      {grantDialog && (
        <GrantAccessDialog
          title={`Grant access for ${grantDialog.user.name}`}
          targetLabel="Topic"
          targetValue={grantDialog.topic}
          readOnlyTarget={grantDialog.readOnlyTopic}
          onConfirm={(topic, permission) => {
            grantAccess(grantDialog.user.name, topic, permission)
            setGrantDialog(null)
          }}
          onDismiss={() => setGrantDialog(null)}
        />
      )}

      {tokenDialogUser && (
        <AddTokenDialog
          title={`Add token for ${tokenDialogUser.name}`}
          onConfirm={(expires, label) => {
            createToken(tokenDialogUser.name, expires, label)
            setTokenDialogUser(null)
          }}
          onDismiss={() => setTokenDialogUser(null)}
        />
      )}

      {deleteUserConfirm && (
        <ConfirmDialog
          title="Delete user"
          text={`Are you sure you want to delete user '${deleteUserConfirm}'?`}
          onConfirm={() => {
            deleteUser(deleteUserConfirm)
            setDeleteUserConfirm(null)
          }}
          onDismiss={() => setDeleteUserConfirm(null)}
        />
      )}

      {revokeAccessConfirm && (
        <ConfirmDialog
          title="Revoke access"
          text={`Are you sure you want to revoke access to '${revokeAccessConfirm.topic}' for user '${revokeAccessConfirm.name}'?`}
          onConfirm={() => {
            revokeAccess(revokeAccessConfirm.name, revokeAccessConfirm.topic)
            setRevokeAccessConfirm(null)
          }}
          onDismiss={() => setRevokeAccessConfirm(null)}
        />
      )}

      {deleteTokenConfirm && (
        <ConfirmDialog
          title="Delete token"
          text={`Are you sure you want to delete this token for user '${deleteTokenConfirm.name}'?`}
          onConfirm={() => {
            deleteToken(deleteTokenConfirm.name, deleteTokenConfirm.token)
            setDeleteTokenConfirm(null)
          }}
          onDismiss={() => setDeleteTokenConfirm(null)}
        />
      )}
    </div>
  )
}

function TopicsTab({ users, topics, onChange, updateData, showMessage, showError }) {
  const [expanded, setExpanded] = useState(new Set())
  const [grantDialog, setGrantDialog] = useState(null)
  const [deleteTopicConfirm, setDeleteTopicConfirm] = useState(null)
  const [revokeAccessConfirm, setRevokeAccessConfirm] = useState(null)

  const userOptions = users.filter((u) => u.name !== '*' && u.name !== 'everyone')

  function toggle(topic) {
    const next = new Set(expanded)
    if (next.has(topic.name)) next.delete(topic.name)
    else next.add(topic.name)
    setExpanded(next)
  }

  async function run(action, success) {
    try {
      await action()
      if (success) showMessage(success)
    } catch (err) {
      showError(err.message)
    }
  }

  async function grantAccess(topic, username, permission) {
    await run(
      () => api.grantTopicAccess(topic, username, permission),
      `Access granted for ${username} on ${topic}`
    )
    updateData((state) => ({
      topics: state.topics.map((t) =>
        t.name === topic
          ? {
              ...t,
              accessors: [
                ...t.accessors.filter((a) => a.username !== username),
                { username, permission },
              ],
            }
          : t
      ),
      users: state.users.map((u) =
        u.name === username
          ? {
              ...u,
              accesses: [
                ...u.accesses.filter((a) => a.topic !== topic),
                { topic, permission, provisioned: false },
              ],
            }
          : u
      ),
    }))
  }

  async function revokeAccess(topic, username) {
    await run(
      () => api.revokeTopicAccess(topic, username),
      `Access revoked for ${username} on ${topic}`
    )
    updateData((state) => ({
      topics: state.topics.map((t) =>
        t.name === topic
          ? { ...t, accessors: t.accessors.filter((a) => a.username !== username) }
          : t
      ),
      users: state.users.map((u) =>
        u.name === username
          ? { ...u, accesses: u.accesses.filter((a) => a.topic !== topic) }
          : u
      ),
    }))
  }

  async function deleteTopic(topic) {
    await run(() => api.deleteTopic(topic), `Topic ${topic} deleted`)
    updateData((state) => ({
      topics: state.topics.filter((t) => t.name !== topic),
      users: state.users.map((u) => ({
        ...u,
        accesses: u.accesses.filter((a) => a.topic !== topic),
      })),
    }))
  }

  return (
    <div>
      <div className="list">
        {topics.map((topic) => (
          <div key={topic.name} className="card">
            <div className="card-header" onClick={() => toggle(topic)}>
              <span className="name">
                {expanded.has(topic.name) ? '▼' : '▶'} {topic.name}
              </span>
              <span className="meta">{topic.accessors.length} accesses</span>
              <span className="actions">
                <button
                  className="small"
                  onClick={(e) => {
                    e.stopPropagation()
                    setGrantDialog({ topic, username: '', readOnlyUsername: false })
                  }}
                >
                  Grant
                </button>
                <button
                  className="small danger"
                  onClick={(e) => {
                    e.stopPropagation()
                    setDeleteTopicConfirm(topic.name)
                  }}
                >
                  Delete
                </button>
              </span>
            </div>
            {expanded.has(topic.name) && (
              <div className="card-body">
                {userOptions.length === 0 && <p>No users</p>}
                {userOptions.map((user) => {
                  const accessor = topic.accessors.find((a) => a.username === user.name)
                  return (
                    <label key={user.name} className="checkbox-row">
                      <span className="checkbox-label">{user.name}</span>
                      {accessor && <span className="permission">{accessor.permission}</span>}
                      <input
                        type="checkbox"
                        checked={accessor != null}
                        onChange={(e) => {
                          if (e.target.checked) {
                            setGrantDialog({
                              topic,
                              username: user.name,
                              readOnlyUsername: true,
                            })
                          } else {
                            setRevokeAccessConfirm({ topic: topic.name, username: user.name })
                          }
                        }}
                      />
                    </label>
                  )
                })}
              </div>
            )}
          </div>
        ))}
      </div>

      {grantDialog && (
        <GrantAccessDialog
          title={`Grant access for ${grantDialog.topic.name}`}
          targetLabel="Username"
          targetValue={grantDialog.username}
          readOnlyTarget={grantDialog.readOnlyUsername}
          onConfirm={(username, permission) => {
            grantAccess(grantDialog.topic.name, username, permission)
            setGrantDialog(null)
          }}
          onDismiss={() => setGrantDialog(null)}
        />
      )}

      {deleteTopicConfirm && (
        <ConfirmDialog
          title="Delete topic"
          text={`Are you sure you want to delete topic '${deleteTopicConfirm}'?`}
          onConfirm={() => {
            deleteTopic(deleteTopicConfirm)
            setDeleteTopicConfirm(null)
          }}
          onDismiss={() => setDeleteTopicConfirm(null)}
        />
      )}

      {revokeAccessConfirm && (
        <ConfirmDialog
          title="Revoke access"
          text={`Are you sure you want to revoke access to '${revokeAccessConfirm.topic}' for user '${revokeAccessConfirm.username}'?`}
          onConfirm={() => {
            revokeAccess(revokeAccessConfirm.topic, revokeAccessConfirm.username)
            setRevokeAccessConfirm(null)
          }}
          onDismiss={() => setRevokeAccessConfirm(null)}
        />
      )}
    </div>
  )
}

export default function App() {
  const [token, setToken] = useState(api.getToken())
  const [serverUrl, setServerUrl] = useState(api.getServerUrl())
  const [tab, setTab] = useState('users')
  const [data, setData] = useState({ users: [], topics: [] })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [showSettings, setShowSettings] = useState(false)
  const [showLogs, setShowLogs] = useState(false)
  const [showAddUser, setShowAddUser] = useState(false)
  const [showCreateTopic, setShowCreateTopic] = useState(false)
  const messageTimer = useRef(null)

  function showMessageImpl(msg) {
    logs.append(msg)
    setMessage(msg)
    setError('')
    if (messageTimer.current) clearTimeout(messageTimer.current)
    messageTimer.current = setTimeout(() => setMessage(''), 3000)
  }

  function showErrorImpl(msg) {
    logs.append(`Error: ${msg}`)
    setError(msg)
    setMessage('')
    if (messageTimer.current) clearTimeout(messageTimer.current)
    messageTimer.current = setTimeout(() => setError(''), 5000)
  }

  function clearMessage() {
    setMessage('')
    setError('')
  }

  async function load() {
    if (!token) return
    setLoading(true)
    setError('')
    try {
      const [u, t] = await Promise.all([api.listUsers(), api.listTopics()])
      setData({ users: u, topics: t })
    } catch (err) {
      if (err.message?.includes('401') || err.message?.includes('Session expired')) {
        api.clearToken()
        setToken(null)
      } else {
        showErrorImpl(err.message)
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (token) load()
  }, [token])

  useEffect(() => {
    return () => {
      if (messageTimer.current) clearTimeout(messageTimer.current)
    }
  }, [])

  function handleLogin() {
    setToken(api.getToken())
    setServerUrl(api.getServerUrl())
  }

  async function handleLogout() {
    try {
      await api.logout()
    } catch {
      // ignore
    }
    api.clearToken()
    setToken(null)
    setData({ users: [], topics: [] })
  }

  function updateData(transform) {
    setData((prev) => transform(prev))
  }

  async function createUser(username, password) {
    try {
      const result = await api.createUser(username, password)
      showMessageImpl(result.detail)
      await load()
    } catch (err) {
      showErrorImpl(err.message)
    }
  }

  async function createTopic(topic, username, permission) {
    try {
      const result = await api.grantTopicAccess(topic, username, permission)
      showMessageImpl(result.detail)
      await load()
    } catch (err) {
      showErrorImpl(err.message)
    }
  }

  function handleServerUrlChange(newUrl) {
    api.setServerUrl(newUrl)
    setServerUrl(newUrl)
    if (newUrl !== serverUrl) {
      api.clearToken()
      setToken(null)
      setData({ users: [], topics: [] })
    }
  }

  if (!token) {
    return (
      <>
        <Login error={error} onLogin={handleLogin} />
        <div className="login-actions">
          <button className="text" onClick={() => setShowSettings(true)}>
            Settings
          </button>
          <button className="text" onClick={() => setShowLogs(true)}>
            Logs
          </button>
        </div>
        {showSettings && (
          <SettingsDialog
            serverUrl={serverUrl}
            hasToken={false}
            onSave={handleServerUrlChange}
            onDismiss={() => setShowSettings(false)}
            onLogout={() => {}}
          />
        )}
        {showLogs && <LogViewDialog onDismiss={() => setShowLogs(false)} />}
      </>
    )
  }

  return (
    <div className="app">
      <header>
        <h1>ntfy Manager</h1>
        <div className="header-actions">
          <button className="text" onClick={() => setShowLogs(true)}>
            Logs
          </button>
          <button className="text" onClick={() => setShowSettings(true)}>
            Settings
          </button>
        </div>
      </header>

      <nav className="tabs">
        <button className={tab === 'users' ? 'active' : ''} onClick={() => setTab('users')}>
          Users
        </button>
        <button className={tab === 'topics' ? 'active' : ''} onClick={() => setTab('topics')}>
          Topics
        </button>
        <button onClick={() => (tab === 'users' ? setShowAddUser(true) : setShowCreateTopic(true))}>
          +
        </button>
      </nav>

      {(message || error) && (
        <p className={error ? 'error' : 'success'} onClick={clearMessage}>
          {error || message}
        </p>
      )}

      {loading && (
        <div className="loading">
          <LoadingIndicator />
        </div>
      )}

      {!loading && tab === 'users' && (
        <UsersTab
          users={data.users}
          topics={data.topics}
          onChange={load}
          updateData={updateData}
          showMessage={showMessageImpl}
          showError={showErrorImpl}
        />
      )}
      {!loading && tab === 'topics' && (
        <TopicsTab
          users={data.users}
          topics={data.topics}
          onChange={load}
          updateData={updateData}
          showMessage={showMessageImpl}
          showError={showErrorImpl}
        />
      )}

      {showAddUser && (
        <AddUserDialog
          title="Add user"
          onConfirm={(username, password) => {
            createUser(username, password)
            setShowAddUser(false)
          }}
          onDismiss={() => setShowAddUser(false)}
        />
      )}

      {showCreateTopic && (
        <CreateTopicDialog
          title="Create topic"
          existingTopics={data.topics.map((t) => t.name)}
          onConfirm={(topic, username, permission) => {
            createTopic(topic, username, permission)
            setShowCreateTopic(false)
          }}
          onDismiss={() => setShowCreateTopic(false)}
        />
      )}

      {showSettings && (
        <SettingsDialog
          serverUrl={serverUrl}
          hasToken={true}
          onSave={handleServerUrlChange}
          onDismiss={() => setShowSettings(false)}
          onLogout={() => {
            setShowSettings(false)
            handleLogout()
          }}
        />
      )}

      {showLogs && <LogViewDialog onDismiss={() => setShowLogs(false)} />}
    </div>
  )
}

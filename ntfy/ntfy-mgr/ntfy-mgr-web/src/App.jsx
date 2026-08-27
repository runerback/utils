import { useEffect, useState } from 'react'
import { api } from './api/client'

function Login({ onLogin }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const data = await api.login(username, password)
      api.setToken(data.token)
      onLogin()
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login">
      <h1>ntfy Manager</h1>
      <form onSubmit={handleSubmit}>
        {error && <p className="error">{error}</p>}
        <label>
          Username
          <input
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoFocus
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        <button type="submit" disabled={loading}>{loading ? 'Logging in...' : 'Login'}</button>
      </form>
    </div>
  )
}

function UsersTab({ users, onChange }) {
  const [newUsername, setNewUsername] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [expanded, setExpanded] = useState(new Set())
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  async function run(action, success) {
    setError('')
    setMessage('')
    try {
      await action()
      setMessage(success)
      onChange()
    } catch (err) {
      setError(err.message)
    }
  }

  async function createUser(e) {
    e.preventDefault()
    await run(() => api.createUser(newUsername, newPassword), `User ${newUsername} created`)
    setNewUsername('')
    setNewPassword('')
  }

  async function grantAccess(user) {
    const topic = prompt(`Grant access to which topic for ${user.name}?`)
    if (!topic) return
    const permission = prompt('Permission (read-write, read-only, write-only)', 'read-write') || 'read-write'
    await run(() => api.grantUserAccess(user.name, topic, permission), `Access granted on ${topic}`)
  }

  async function revokeAccess(user, topic) {
    if (!confirm(`Revoke ${user.name}'s access to ${topic}?`)) return
    await run(() => api.revokeUserAccess(user.name, topic), 'Access revoked')
  }

  async function addToken(user) {
    const expires = prompt('Expires (e.g. 30d, 1h), or leave empty') || ''
    const label = prompt('Label, or leave empty') || ''
    await run(() => api.createUserToken(user.name, expires, label), 'Token created')
  }

  async function removeToken(user, token) {
    if (!confirm(`Remove token ${token.value}?`)) return
    await run(() => api.deleteUserToken(user.name, token.value), 'Token removed')
  }

  async function removeUser(user) {
    if (!confirm(`Delete user ${user.name}?`)) return
    await run(() => api.deleteUser(user.name), 'User deleted')
  }

  function toggle(user) {
    const next = new Set(expanded)
    if (next.has(user.name)) next.delete(user.name)
    else next.add(user.name)
    setExpanded(next)
  }

  return (
    <div>
      {message && <p className="success">{message}</p>}
      {error && <p className="error">{error}</p>}

      <form onSubmit={createUser} className="inline-form">
        <input
          placeholder="Username"
          value={newUsername}
          onChange={(e) => setNewUsername(e.target.value)}
          required
        />
        <input
          type="password"
          placeholder="Password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          required
        />
        <button type="submit">Add user</button>
      </form>

      <div className="list">
        {users.map((user) => (
          <div key={user.name} className="card">
            <div className="card-header">
              <span className="name" onClick={() => toggle(user)}>
                {user.name} ({user.role})
              </span>
              <span className="actions">
                <button onClick={() => grantAccess(user)}>Grant access</button>
                <button onClick={() => addToken(user)}>Add token</button>
                <button className="danger" onClick={() => removeUser(user)}>Delete</button>
              </span>
            </div>
            {expanded.has(user.name) && (
              <div className="card-body">
                <strong>Access:</strong>
                <ul>
                  {user.accesses.length === 0 && <li>None</li>}
                  {user.accesses.map((a) => (
                    <li key={a.topic}>
                      {a.permission} on {a.topic}{' '}
                      <button className="small danger" onClick={() => revokeAccess(user, a.topic)}>
                        Revoke
                      </button>
                    </li>
                  ))}
                </ul>

                <strong>Tokens:</strong>
                <ul>
                  {user.tokens.length === 0 && <li>None</li>}
                  {user.tokens.map((t) => (
                    <li key={t.value}>
                      {t.value} {t.label ? `(${t.label})` : ''} — expires {t.expires}{' '}
                      <button className="small danger" onClick={() => removeToken(user, t)}>
                        Remove
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}

function TopicsTab({ topics, users, onChange }) {
  const [newTopic, setNewTopic] = useState('')
  const [selectedUser, setSelectedUser] = useState('')
  const [permission, setPermission] = useState('read-write')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  async function run(action, success) {
    setError('')
    setMessage('')
    try {
      await action()
      setMessage(success)
      onChange()
    } catch (err) {
      setError(err.message)
    }
  }

  async function createTopic(e) {
    e.preventDefault()
    if (!selectedUser) {
      setError('Select a user')
      return
    }
    await run(() => api.grantTopicAccess(newTopic, selectedUser, permission), `Topic ${newTopic} created`)
    setNewTopic('')
  }

  async function grantAccess(topic) {
    const username = prompt(`Grant access to which user for ${topic.name}?`)
    if (!username) return
    const perm = prompt('Permission (read-write, read-only, write-only)', 'read-write') || 'read-write'
    await run(() => api.grantTopicAccess(topic.name, username, perm), 'Access granted')
  }

  async function revokeAccess(topic, username) {
    if (!confirm(`Revoke ${username}'s access to ${topic.name}?`)) return
    await run(() => api.revokeTopicAccess(topic.name, username), 'Access revoked')
  }

  async function removeTopic(topic) {
    if (!confirm(`Delete topic ${topic.name}?`)) return
    await run(() => api.deleteTopic(topic.name), 'Topic deleted')
  }

  const userOptions = users.filter((u) => u.name !== '*' && u.name !== 'everyone')

  return (
    <div>
      {message && <p className="success">{message}</p>}
      {error && <p className="error">{error}</p>}

      <form onSubmit={createTopic} className="inline-form">
        <input
          placeholder="Topic name"
          value={newTopic}
          onChange={(e) => setNewTopic(e.target.value)}
          required
        />
        <select value={selectedUser} onChange={(e) => setSelectedUser(e.target.value)} required>
          <option value="">Select user</option>
          {userOptions.map((u) => (
            <option key={u.name} value={u.name}>{u.name}</option>
          ))}
        </select>
        <select value={permission} onChange={(e) => setPermission(e.target.value)}>
          <option value="read-write">read-write</option>
          <option value="read-only">read-only</option>
          <option value="write-only">write-only</option>
        </select>
        <button type="submit">Create topic</button>
      </form>

      <div className="list">
        {topics.map((topic) => (
          <div key={topic.name} className="card">
            <div className="card-header">
              <span className="name">{topic.name}</span>
              <span className="actions">
                <button onClick={() => grantAccess(topic)}>Grant access</button>
                <button className="danger" onClick={() => removeTopic(topic)}>Delete</button>
              </span>
            </div>
            <div className="card-body">
              <ul>
                {topic.accessors.length === 0 && <li>No accessors</li>}
                {topic.accessors.map((a) => (
                  <li key={a.username}>
                    {a.username}: {a.permission}{' '}
                    <button
                      className="small danger"
                      onClick={() => revokeAccess(topic, a.username)}
                    >
                      Revoke
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

export default function App() {
  const [token, setToken] = useState(api.getToken())
  const [tab, setTab] = useState('users')
  const [users, setUsers] = useState([])
  const [topics, setTopics] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function load() {
    setLoading(true)
    setError('')
    try {
      const [u, t] = await Promise.all([api.listUsers(), api.listTopics()])
      setUsers(u)
      setTopics(t)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (token) load()
  }, [token])

  function handleLogin() {
    setToken(api.getToken())
  }

  async function handleLogout() {
    try {
      await api.logout()
    } catch {
      // ignore
    }
    api.clearToken()
    setToken(null)
  }

  if (!token) {
    return <Login onLogin={handleLogin} />
  }

  return (
    <div className="app">
      <header>
        <h1>ntfy Manager</h1>
        <button onClick={handleLogout}>Logout</button>
      </header>

      <nav className="tabs">
        <button className={tab === 'users' ? 'active' : ''} onClick={() => setTab('users')}>
          Users
        </button>
        <button className={tab === 'topics' ? 'active' : ''} onClick={() => setTab('topics')}>
          Topics
        </button>
      </nav>

      {loading && <p>Loading...</p>}
      {error && <p className="error">{error}</p>}

      {!loading && tab === 'users' && <UsersTab users={users} onChange={load} />}
      {!loading && tab === 'topics' && (
        <TopicsTab topics={topics} users={users} onChange={load} />
      )}
    </div>
  )
}

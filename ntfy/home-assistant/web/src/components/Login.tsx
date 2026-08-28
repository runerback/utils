import { useState } from 'preact/hooks'
import { login } from '../auth.ts'

export function Login() {
  const [user, setUser] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const onSubmit = async (e: Event) => {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await login(user, password)
    } catch (err: any) {
      setError(err.message || 'Login failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div class="login-box">
      <h1>Home Assistant</h1>
      {error && <div class="error">{error}</div>}
      <form onSubmit={onSubmit}>
        <label>Username</label>
        <input
          type="text"
          value={user}
          onInput={(e) => setUser((e.target as HTMLInputElement).value)}
          required
          autoFocus
          autoComplete="username"
        />

        <label>Password</label>
        <input
          type="password"
          value={password}
          onInput={(e) => setPassword((e.target as HTMLInputElement).value)}
          required
          autoComplete="current-password"
        />

        <button type="submit" class="btn-primary" disabled={submitting}>
          {submitting ? 'Logging in...' : 'Login'}
        </button>
      </form>
    </div>
  )
}

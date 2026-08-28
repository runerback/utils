import { signal } from '@preact/signals'
import { api, fetchCsrf, setCsrfToken, getCsrfToken } from './api.ts'

export const username = signal<string | null>(null)

export async function checkAuth(): Promise<boolean> {
  try {
    const res = await fetch('/api/me', { credentials: 'same-origin' })
    if (res.ok) {
      const data = await res.json()
      username.value = data.username
      await fetchCsrf()
      return true
    }
  } catch {
    // ignore
  }
  username.value = null
  return false
}

export async function login(user: string, password: string): Promise<void> {
  const body = new URLSearchParams({ username: user, password })
  const res = await fetch('/api/login', {
    method: 'POST',
    body,
    credentials: 'same-origin',
  })
  if (!res.ok) {
    const data = await res.json().catch(() => ({}))
    throw new Error(data.detail || 'Login failed')
  }
  username.value = user
  const csrf = await fetchCsrf()
  setCsrfToken(csrf)
}

export async function logout(): Promise<void> {
  await api('/api/logout', {
    method: 'POST',
    headers: { 'X-CSRFToken': getCsrfToken() },
  })
  username.value = null
  window.location.href = '/login'
}

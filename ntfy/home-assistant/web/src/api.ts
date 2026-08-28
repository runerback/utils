let csrfToken = ''

export function setCsrfToken(token: string) {
  csrfToken = token
}

export function getCsrfToken(): string {
  return csrfToken
}

export async function fetchCsrf(): Promise<string> {
  const res = await fetch('/api/csrf', { credentials: 'same-origin' })
  if (!res.ok) {
    throw new Error('Failed to fetch CSRF token')
  }
  const data = await res.json()
  csrfToken = data.csrf_token
  return csrfToken
}

export async function api(
  path: string,
  options: RequestInit = {},
): Promise<Response> {
  const headers = new Headers(options.headers)
  if (csrfToken) {
    headers.set('X-CSRFToken', csrfToken)
  }

  const res = await fetch(path, {
    ...options,
    credentials: 'same-origin',
    headers,
  })

  if (res.status === 401) {
    window.location.href = '/login'
    throw new Error('Unauthorized')
  }
  return res
}

export function createEventSource(url: string): EventSource {
  return new EventSource(url, { withCredentials: true })
}

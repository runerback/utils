import { useEffect, useState } from 'preact/hooks'
import { api } from '../api.ts'

export function Logs() {
  const [lines, setLines] = useState<string[]>([])
  const [error, setError] = useState('')

  const load = async () => {
    setError('')
    const res = await api('/api/logs')
    const data = await res.json()
    if (data.error) {
      setError(data.error)
      setLines([])
    } else {
      setLines(data.lines || [])
    }
  }

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(lines.join('\n'))
    } catch {
      alert('Copy failed')
    }
  }

  const clear = async () => {
    if (!confirm('Clear logs?')) return
    const res = await api('/api/logs/clear', { method: 'POST' })
    if (res.ok) {
      load()
    } else {
      const data = await res.json()
      alert(data.detail || 'Failed to clear logs')
    }
  }

  useEffect(() => {
    load()
  }, [])

  return (
    <section class="panel">
      <div class="logs-actions">
        <button class="btn" onClick={load}>Refresh</button>
        <button class="btn" onClick={copy}>Copy</button>
        <button class="btn btn-danger" onClick={clear}>Clear</button>
      </div>
      {error && <div class="error">Error: {error}</div>}
      <pre class="logs-output">{lines.join('\n') || 'No log entries.'}</pre>
    </section>
  )
}

import { useEffect, useState } from 'preact/hooks'
import { api } from '../api.ts'

interface SystemInfo {
  cpu_temp: number | null
  memory: {
    total_kb: number | null
    used_kb: number | null
    percent: number | null
  }
}

export function Home() {
  const [system, setSystem] = useState<SystemInfo | null>(null)
  const [error, setError] = useState('')

  const load = async () => {
    try {
      const res = await api('/api/system')
      if (!res.ok) throw new Error('Failed to load system info')
      setSystem(await res.json())
    } catch (err: any) {
      setError(err.message)
    }
  }

  useEffect(() => {
    load()
    const id = setInterval(load, 30000)
    return () => clearInterval(id)
  }, [])

  return (
    <section>
      {error && <div class="error">{error}</div>}
      <div class="panel metrics">
        <div class="metric-card">
          <span class="metric-label">CPU Temperature</span>
          <span class="metric-value">
            {system?.cpu_temp != null ? `${system.cpu_temp.toFixed(1)} °C` : 'N/A'}
          </span>
        </div>
        <div class="metric-card">
          <span class="metric-label">Memory Usage</span>
          <span class="metric-value">
            {system?.memory?.used_kb != null && system?.memory?.total_kb != null
              ? `${(system.memory.used_kb / 1024).toFixed(1)} / ${(system.memory.total_kb / 1024).toFixed(1)} MB (${system.memory.percent}%)`
              : 'N/A'}
          </span>
        </div>
      </div>
    </section>
  )
}

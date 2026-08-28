import { useEffect, useState } from 'preact/hooks'
import { api } from '../api.ts'

interface Settings {
  'messages.server_url': string
  'messages.token': string
}

export function Settings() {
  const [settings, setSettings] = useState<Settings>({
    'messages.server_url': '',
    'messages.token': '',
  })
  const [saved, setSaved] = useState(false)

  const load = async () => {
    const res = await api('/api/settings')
    setSettings(await res.json())
  }

  useEffect(() => {
    load()
  }, [])

  const save = async (e: Event) => {
    e.preventDefault()
    const form = e.target as HTMLFormElement
    const formData = new FormData(form)
    const res = await api('/api/settings', {
      method: 'POST',
      body: formData,
    })
    if (res.ok) {
      setSaved(true)
      setTimeout(() => setSaved(false), 2000)
      load()
    } else {
      const data = await res.json()
      alert(data.detail || 'Failed to save settings')
    }
  }

  return (
    <form class="settings-form" onSubmit={save}>
      <div class="panel">
        <h2>Messages</h2>
        <label>Server URL</label>
        <input
          type="text"
          name="messages.server_url"
          value={settings['messages.server_url']}
          onInput={(e) =>
            setSettings((s) => ({
              ...s,
              'messages.server_url': (e.target as HTMLInputElement).value,
            }))
          }
          placeholder="http://localhost"
        />

        <label>Token</label>
        <input
          type="password"
          name="messages.token"
          value={settings['messages.token']}
          onInput={(e) =>
            setSettings((s) => ({
              ...s,
              'messages.token': (e.target as HTMLInputElement).value,
            }))
          }
          placeholder="Optional access token"
        />
      </div>

      <div class="panel">
        <button type="submit" class="btn-primary">Save settings</button>
        {saved && <span class="muted" style={{ marginLeft: '12px' }}>Saved</span>}
      </div>
    </form>
  )
}

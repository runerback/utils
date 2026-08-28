import { useEffect, useState } from 'preact/hooks'
import { api } from '../api.ts'

interface Settings {
  'messages.server_url': string
  'messages.token': string
  'logs.path': string
  'logs.use_journal': string
}

export function Settings() {
  const [settings, setSettings] = useState<Settings>({
    'messages.server_url': '',
    'messages.token': '',
    'logs.path': '',
    'logs.use_journal': 'false',
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
    if (!formData.has('logs.use_journal')) {
      formData.append('logs.use_journal', 'false')
    }
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
        <h2>Logs</h2>
        <label>Log file path</label>
        <input
          type="text"
          name="logs.path"
          value={settings['logs.path']}
          onInput={(e) =>
            setSettings((s) => ({
              ...s,
              'logs.path': (e.target as HTMLInputElement).value,
            }))
          }
          placeholder="/var/log/home-assistant/app.log"
        />

        <label class="checkbox-row">
          <input
            type="checkbox"
            name="logs.use_journal"
            value="true"
            checked={settings['logs.use_journal'] === 'true'}
            onChange={(e) =>
              setSettings((s) => ({
                ...s,
                'logs.use_journal': (e.target as HTMLInputElement).checked ? 'true' : 'false',
              }))
            }
          />
          Use journalctl instead of log file
        </label>
      </div>

      <div class="panel">
        <button type="submit" class="btn-primary">Save settings</button>
        {saved && <span class="muted" style={{ marginLeft: '12px' }}>Saved</span>}
      </div>
    </form>
  )
}

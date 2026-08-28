import { username, logout } from '../auth.ts'

const tabs = [
  { path: '/', label: 'Home' },
  { path: '/messages', label: 'Messages' },
  { path: '/devices', label: 'Devices' },
  { path: '/logs', label: 'Logs' },
  { path: '/settings', label: 'Settings' },
]

function isActive(path: string): boolean {
  return window.location.pathname === path || (path !== '/' && window.location.pathname.startsWith(path))
}

export function Layout({ children }: { children: any }) {
  return (
    <div class="container">
      <header class="page-header">
        <h1>Home Assistant</h1>
        <div class="admin-info">
          <span>{username.value}</span>
          <button class="btn" onClick={() => logout()}>Logout</button>
        </div>
      </header>

      <nav class="tabs">
        {tabs.map((tab) => (
          <a
            key={tab.path}
            href={tab.path}
            class={isActive(tab.path) ? 'tab-btn active' : 'tab-btn'}
          >
            {tab.label}
          </a>
        ))}
      </nav>

      {children}
    </div>
  )
}

import { useRouter } from 'preact-router'
import { username, logout } from '../auth.ts'

const tabs = [
  { path: '/', label: 'Home' },
  { path: '/messages', label: 'Messages' },
  { path: '/devices', label: 'Devices' },
  { path: '/settings', label: 'Settings' },
]

function isActive(path: string, currentPath: string): boolean {
  return currentPath === path || (path !== '/' && currentPath.startsWith(path))
}

export function Layout({ children }: { children: any }) {
  const [route] = useRouter()
  const currentPath = route.path || route.url

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
            class={isActive(tab.path, currentPath) ? 'tab-btn active' : 'tab-btn'}
          >
            {tab.label}
          </a>
        ))}
      </nav>

      {children}
    </div>
  )
}

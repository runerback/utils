import { useEffect, useState } from 'preact/hooks'
import { Route, Router } from 'preact-router'
import { checkAuth, username } from './auth.ts'
import { Home } from './components/Home.tsx'
import { Layout } from './components/Layout.tsx'
import { Login } from './components/Login.tsx'
import { Messages } from './components/Messages.tsx'
import { Devices } from './components/Devices.tsx'
import { Logs } from './components/Logs.tsx'
import { Settings } from './components/Settings.tsx'

function Dashboard() {
  return (
    <Layout>
      <Router>
        <Route path="/" component={Home} />
        <Route path="/messages" component={Messages} />
        <Route path="/devices" component={Devices} />
        <Route path="/logs" component={Logs} />
        <Route path="/settings" component={Settings} />
      </Router>
    </Layout>
  )
}

export function App() {
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    checkAuth().finally(() => setLoading(false))
  }, [])

  if (loading) {
    return <div class="container"><p class="muted">Loading...</p></div>
  }

  return username.value ? <Dashboard /> : <Login />
}

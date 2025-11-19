import { useEffect } from 'react'
import Dashboard from './components/Dashboard'
import { useWebSocket } from './hooks/useWebSocket'

function App() {
  const { connect, disconnect, connected } = useWebSocket()

  useEffect(() => {
    connect()
    return () => disconnect()
  }, [])

  return (
    <div className="app">
      <header className="app-header">
        <h1>Code Migration Demo</h1>
        <div className="header-info">
          <span>He thong Phan tan - 5 Nodes</span>
          <span className={`connection-status ${connected ? 'connected' : 'disconnected'}`}>
            {connected ? 'Connected' : 'Disconnected'}
          </span>
        </div>
      </header>
      <main>
        <Dashboard />
      </main>
    </div>
  )
}

export default App

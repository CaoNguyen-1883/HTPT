import { useState, useEffect } from 'react'
import '../styles/DatabaseViewer.css'

function DatabaseViewer() {
  const [activeTab, setActiveTab] = useState('migrations') // migrations, codes
  const [migrations, setMigrations] = useState([])
  const [codePackages, setCodePackages] = useState([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    fetchData()
    const interval = setInterval(fetchData, 5000)
    return () => clearInterval(interval)
  }, [])

  const fetchData = async () => {
    setLoading(true)
    try {
      const [migrationsRes, codesRes] = await Promise.all([
        fetch('http://localhost:8080/api/migrations'),
        fetch('http://localhost:8080/api/code')
      ])

      setMigrations(await migrationsRes.json())
      setCodePackages(await codesRes.json())
    } catch (error) {
      console.error('Failed to fetch database data:', error)
    } finally {
      setLoading(false)
    }
  }

  const getMigrationStats = () => {
    return {
      total: migrations.length,
      completed: migrations.filter(m => m.status === 'COMPLETED').length,
      failed: migrations.filter(m => m.status === 'FAILED').length,
      inProgress: migrations.filter(m => m.status === 'IN_PROGRESS').length,
      weak: migrations.filter(m => m.type === 'WEAK').length,
      strong: migrations.filter(m => m.type === 'STRONG').length,
      avgDuration: migrations
        .filter(m => m.endTime)
        .reduce((sum, m) => sum + (new Date(m.endTime) - new Date(m.startTime)), 0) /
        migrations.filter(m => m.endTime).length || 0
    }
  }

  const getCodeStats = () => {
    return {
      total: codePackages.length,
      withNode: codePackages.filter(c => c.currentNodeId).length,
      withoutNode: codePackages.filter(c => !c.currentNodeId).length,
    }
  }

  const stats = activeTab === 'migrations' ? getMigrationStats() : getCodeStats()

  return (
    <div className="database-viewer">
      <div className="viewer-header">
        <h3>Database Viewer</h3>
        <button onClick={fetchData} className="refresh-btn" disabled={loading}>
          {loading ? '⟳ Loading...' : '↻ Refresh'}
        </button>
      </div>

      <div className="tabs">
        <button
          className={`tab ${activeTab === 'migrations' ? 'active' : ''}`}
          onClick={() => setActiveTab('migrations')}
        >
          Migrations ({migrations.length})
        </button>
        <button
          className={`tab ${activeTab === 'codes' ? 'active' : ''}`}
          onClick={() => setActiveTab('codes')}
        >
          Code Packages ({codePackages.length})
        </button>
      </div>

      {activeTab === 'migrations' && (
        <div className="tab-content">
          <div className="stats-grid">
            <div className="stat-box">
              <div className="stat-label">Total</div>
              <div className="stat-value">{stats.total}</div>
            </div>
            <div className="stat-box success">
              <div className="stat-label">Completed</div>
              <div className="stat-value">{stats.completed}</div>
            </div>
            <div className="stat-box error">
              <div className="stat-label">Failed</div>
              <div className="stat-value">{stats.failed}</div>
            </div>
            <div className="stat-box progress">
              <div className="stat-label">In Progress</div>
              <div className="stat-value">{stats.inProgress}</div>
            </div>
            <div className="stat-box">
              <div className="stat-label">Weak</div>
              <div className="stat-value">{stats.weak}</div>
            </div>
            <div className="stat-box">
              <div className="stat-label">Strong</div>
              <div className="stat-value">{stats.strong}</div>
            </div>
            <div className="stat-box">
              <div className="stat-label">Avg Duration</div>
              <div className="stat-value">{(stats.avgDuration / 1000).toFixed(2)}s</div>
            </div>
          </div>

          <div className="data-table">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Type</th>
                  <th>Route</th>
                  <th>Code ID</th>
                  <th>Status</th>
                  <th>Progress</th>
                  <th>Started</th>
                  <th>Duration</th>
                </tr>
              </thead>
              <tbody>
                {migrations.map(m => (
                  <tr key={m.id} className={`status-${m.status.toLowerCase()}`}>
                    <td className="mono">{m.id}</td>
                    <td>
                      <span className={`badge ${m.type.toLowerCase()}`}>
                        {m.type}
                      </span>
                    </td>
                    <td className="route">
                      {m.sourceNodeId} → {m.targetNodeId}
                    </td>
                    <td className="mono">{m.codeId}</td>
                    <td>
                      <span className={`status-badge ${m.status.toLowerCase()}`}>
                        {m.status}
                      </span>
                    </td>
                    <td>{m.progress}%</td>
                    <td>{new Date(m.startTime).toLocaleTimeString()}</td>
                    <td>
                      {m.endTime
                        ? `${((new Date(m.endTime) - new Date(m.startTime)) / 1000).toFixed(2)}s`
                        : '-'
                      }
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activeTab === 'codes' && (
        <div className="tab-content">
          <div className="stats-grid">
            <div className="stat-box">
              <div className="stat-label">Total Packages</div>
              <div className="stat-value">{stats.total}</div>
            </div>
            <div className="stat-box">
              <div className="stat-label">On Nodes</div>
              <div className="stat-value">{stats.withNode}</div>
            </div>
            <div className="stat-box">
              <div className="stat-label">Unassigned</div>
              <div className="stat-value">{stats.withoutNode}</div>
            </div>
          </div>

          <div className="code-cards">
            {codePackages.map(code => (
              <div key={code.id} className="code-card">
                <div className="code-header">
                  <h4>{code.name}</h4>
                  <span className="code-id-badge">#{code.id}</span>
                </div>

                <div className="code-details">
                  <div className="detail-row">
                    <span className="label">Entry Point:</span>
                    <span className="value">{code.entryPoint}</span>
                  </div>
                  <div className="detail-row">
                    <span className="label">Current Node:</span>
                    <span className="value">
                      {code.currentNodeId || 'Not assigned'}
                    </span>
                  </div>
                  {code.metadata && (
                    <>
                      <div className="detail-row">
                        <span className="label">Created:</span>
                        <span className="value">
                          {new Date(code.metadata.createdAt).toLocaleString()}
                        </span>
                      </div>
                      <div className="detail-row">
                        <span className="label">Version:</span>
                        <span className="value">{code.metadata.version}</span>
                      </div>
                    </>
                  )}
                </div>

                <details className="code-source">
                  <summary>View Source Code</summary>
                  <pre>{code.code}</pre>
                </details>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

export default DatabaseViewer

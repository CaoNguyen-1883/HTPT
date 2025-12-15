import { useState, useEffect } from 'react'
import '../styles/MigrationHistory.css'

function MigrationHistory() {
  const [migrations, setMigrations] = useState([])
  const [filter, setFilter] = useState('all') // all, completed, failed, in_progress
  const [sortBy, setSortBy] = useState('time') // time, type, duration

  useEffect(() => {
    fetchMigrations()
    const interval = setInterval(fetchMigrations, 2000)
    return () => clearInterval(interval)
  }, [])

  const fetchMigrations = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/migrations')
      const data = await response.json()
      setMigrations(data)
    } catch (error) {
      console.error('Failed to fetch migrations:', error)
    }
  }

  const filteredMigrations = migrations.filter(m => {
    if (filter === 'all') return true
    if (filter === 'completed') return m.status === 'COMPLETED'
    if (filter === 'failed') return m.status === 'FAILED'
    if (filter === 'in_progress') return m.status === 'IN_PROGRESS'
    return true
  })

  const sortedMigrations = [...filteredMigrations].sort((a, b) => {
    if (sortBy === 'time') {
      return new Date(b.startTime) - new Date(a.startTime)
    }
    if (sortBy === 'type') {
      return a.type.localeCompare(b.type)
    }
    if (sortBy === 'duration' && a.endTime && b.endTime) {
      const durationA = new Date(a.endTime) - new Date(a.startTime)
      const durationB = new Date(b.endTime) - new Date(b.startTime)
      return durationB - durationA
    }
    return 0
  })

  const getStatusIcon = (status) => {
    switch (status) {
      case 'COMPLETED': return '✓'
      case 'FAILED': return '✗'
      case 'IN_PROGRESS': return '⟳'
      default: return '○'
    }
  }

  const getStatusClass = (status) => {
    return status.toLowerCase().replace('_', '-')
  }

  const formatDuration = (start, end) => {
    if (!end) return 'In progress...'
    const duration = new Date(end) - new Date(start)
    return `${(duration / 1000).toFixed(2)}s`
  }

  const formatTime = (timestamp) => {
    return new Date(timestamp).toLocaleTimeString()
  }

  const stats = {
    total: migrations.length,
    completed: migrations.filter(m => m.status === 'COMPLETED').length,
    failed: migrations.filter(m => m.status === 'FAILED').length,
    inProgress: migrations.filter(m => m.status === 'IN_PROGRESS').length,
    weak: migrations.filter(m => m.type === 'WEAK').length,
    strong: migrations.filter(m => m.type === 'STRONG').length,
  }

  return (
    <div className="migration-history">
      <div className="history-header">
        <h3>Migration History</h3>
        <div className="history-stats">
          <span className="stat">Total: {stats.total}</span>
          <span className="stat success">✓ {stats.completed}</span>
          <span className="stat error">✗ {stats.failed}</span>
          <span className="stat progress">⟳ {stats.inProgress}</span>
          <span className="stat">Weak: {stats.weak}</span>
          <span className="stat">Strong: {stats.strong}</span>
        </div>
      </div>

      <div className="history-controls">
        <div className="filter-group">
          <label>Filter:</label>
          <select value={filter} onChange={(e) => setFilter(e.target.value)}>
            <option value="all">All ({migrations.length})</option>
            <option value="completed">Completed ({stats.completed})</option>
            <option value="failed">Failed ({stats.failed})</option>
            <option value="in_progress">In Progress ({stats.inProgress})</option>
          </select>
        </div>

        <div className="filter-group">
          <label>Sort by:</label>
          <select value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
            <option value="time">Time</option>
            <option value="type">Type</option>
            <option value="duration">Duration</option>
          </select>
        </div>
      </div>

      <div className="history-list">
        {sortedMigrations.length === 0 ? (
          <div className="no-history">No migrations yet</div>
        ) : (
          sortedMigrations.map(migration => (
            <div key={migration.id} className={`history-item ${getStatusClass(migration.status)}`}>
              <div className="history-icon">
                {getStatusIcon(migration.status)}
              </div>

              <div className="history-content">
                <div className="history-main">
                  <span className="migration-id">#{migration.id}</span>
                  <span className={`migration-type ${migration.type.toLowerCase()}`}>
                    {migration.type}
                  </span>
                  <span className="migration-route">
                    {migration.sourceNodeId} → {migration.targetNodeId}
                  </span>
                </div>

                <div className="history-details">
                  <span className="detail">Code: {migration.codeId}</span>
                  <span className="detail">Started: {formatTime(migration.startTime)}</span>
                  <span className="detail">Duration: {formatDuration(migration.startTime, migration.endTime)}</span>
                  <span className="detail">Progress: {migration.progress}%</span>
                </div>

                {migration.errorMessage && (
                  <div className="history-error">
                    Error: {migration.errorMessage}
                  </div>
                )}
              </div>

              <div className="history-progress">
                <div
                  className="progress-bar"
                  style={{ width: `${migration.progress}%` }}
                ></div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  )
}

export default MigrationHistory

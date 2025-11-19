import { useEffect, useRef } from 'react'
import useStore from '../store/useStore'

function LogViewer({ logs }) {
  const containerRef = useRef()
  const clearLogs = useStore(state => state.clearLogs)

  useEffect(() => {
    // Auto scroll to bottom
    if (containerRef.current) {
      containerRef.current.scrollTop = containerRef.current.scrollHeight
    }
  }, [logs])

  const formatTime = (timestamp) => {
    return new Date(timestamp).toLocaleTimeString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
  }

  return (
    <div className="log-viewer">
      <div className="log-header">
        <h3>Logs</h3>
        <button onClick={clearLogs} className="btn-small">Clear</button>
      </div>
      <div className="logs-container" ref={containerRef}>
        {logs.length === 0 ? (
          <p className="no-logs">No logs yet</p>
        ) : (
          logs.map((log, index) => (
            <div key={index} className={`log-entry ${log.type || 'info'}`}>
              <span className="timestamp">{formatTime(log.timestamp)}</span>
              <span className="message">{log.message}</span>
            </div>
          ))
        )}
      </div>
    </div>
  )
}

export default LogViewer

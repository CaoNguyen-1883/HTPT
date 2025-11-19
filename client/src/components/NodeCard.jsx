function NodeCard({ node, isSelected, onSelect }) {
  const statusColor = {
    'ONLINE': '#4CAF50',
    'BUSY': '#FF9800',
    'MIGRATING': '#2196F3',
    'OFFLINE': '#f44336'
  }

  return (
    <div
      className={`node-card ${isSelected ? 'selected' : ''}`}
      onClick={onSelect}
    >
      <div className="node-header">
        <span className="node-id">{node.id}</span>
        <span
          className="status-indicator"
          style={{ backgroundColor: statusColor[node.status] || '#9E9E9E' }}
        />
      </div>

      <div className="node-info">
        <p>{node.host}:{node.port}</p>
        <p className="node-role">{node.role}</p>
      </div>

      {node.metrics && (
        <div className="node-metrics">
          <div className="metric">
            <span className="metric-label">CPU</span>
            <div className="progress-bar">
              <div
                className="progress"
                style={{
                  width: `${node.metrics.cpuUsage}%`,
                  backgroundColor: node.metrics.cpuUsage > 80 ? '#f44336' : '#4CAF50'
                }}
              />
            </div>
            <span className="metric-value">{node.metrics.cpuUsage?.toFixed(0)}%</span>
          </div>

          <div className="metric">
            <span className="metric-label">MEM</span>
            <div className="progress-bar">
              <div
                className="progress"
                style={{
                  width: `${node.metrics.memoryUsage}%`,
                  backgroundColor: node.metrics.memoryUsage > 80 ? '#f44336' : '#2196F3'
                }}
              />
            </div>
            <span className="metric-value">{node.metrics.memoryUsage?.toFixed(0)}%</span>
          </div>

          <p className="processes">Processes: {node.metrics.activeProcesses}</p>
        </div>
      )}
    </div>
  )
}

export default NodeCard

import { useState, useEffect } from 'react'
import '../styles/NodeMetrics.css'

function NodeMetrics() {
  const [nodes, setNodes] = useState([])
  const [selectedNode, setSelectedNode] = useState(null)

  useEffect(() => {
    fetchNodes()
    const interval = setInterval(fetchNodes, 3000)
    return () => clearInterval(interval)
  }, [])

  const fetchNodes = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/nodes')
      const data = await response.json()
      setNodes(data)
      if (!selectedNode && data.length > 0) {
        setSelectedNode(data[0].id)
      }
    } catch (error) {
      console.error('Failed to fetch nodes:', error)
    }
  }

  const getNode = (nodeId) => nodes.find(n => n.id === nodeId)

  const getMetricColor = (value, thresholds = { good: 50, warning: 75 }) => {
    if (value < thresholds.good) return 'good'
    if (value < thresholds.warning) return 'warning'
    return 'critical'
  }

  const formatUptime = (seconds) => {
    const hours = Math.floor(seconds / 3600)
    const minutes = Math.floor((seconds % 3600) / 60)
    const secs = seconds % 60
    return `${hours}h ${minutes}m ${secs}s`
  }

  const selectedNodeData = getNode(selectedNode)

  return (
    <div className="node-metrics">
      <div className="metrics-header">
        <h3>Node Metrics</h3>
        <select
          value={selectedNode || ''}
          onChange={(e) => setSelectedNode(e.target.value)}
          className="node-selector"
        >
          {nodes.map(node => (
            <option key={node.id} value={node.id}>
              {node.id} ({node.status})
            </option>
          ))}
        </select>
      </div>

      {selectedNodeData ? (
        <div className="metrics-content">
          {/* Overview Cards */}
          <div className="metrics-overview">
            <div className="metric-card">
              <div className="metric-label">Status</div>
              <div className={`metric-value status-${selectedNodeData.status.toLowerCase()}`}>
                {selectedNodeData.status}
              </div>
            </div>

            <div className="metric-card">
              <div className="metric-label">Load Score</div>
              <div className="metric-value">
                {selectedNodeData.metrics?.loadScore.toFixed(2) || 'N/A'}
              </div>
            </div>

            <div className="metric-card">
              <div className="metric-label">Uptime</div>
              <div className="metric-value">
                {formatUptime(selectedNodeData.metrics?.uptime || 0)}
              </div>
            </div>

            <div className="metric-card">
              <div className="metric-label">Processes</div>
              <div className="metric-value">
                {selectedNodeData.metrics?.activeProcesses || 0}
              </div>
            </div>
          </div>

          {/* CPU Usage */}
          <div className="metric-section">
            <div className="metric-header">
              <span className="metric-title">CPU Usage</span>
              <span className="metric-percent">
                {selectedNodeData.metrics?.cpuUsage.toFixed(1) || 0}%
              </span>
            </div>
            <div className="metric-bar-container">
              <div
                className={`metric-bar cpu ${getMetricColor(selectedNodeData.metrics?.cpuUsage || 0)}`}
                style={{ width: `${selectedNodeData.metrics?.cpuUsage || 0}%` }}
              ></div>
            </div>
          </div>

          {/* Memory Usage */}
          <div className="metric-section">
            <div className="metric-header">
              <span className="metric-title">Memory Usage</span>
              <span className="metric-percent">
                {selectedNodeData.metrics?.memoryUsage.toFixed(1) || 0}%
              </span>
            </div>
            <div className="metric-bar-container">
              <div
                className={`metric-bar memory ${getMetricColor(selectedNodeData.metrics?.memoryUsage || 0)}`}
                style={{ width: `${selectedNodeData.metrics?.memoryUsage || 0}%` }}
              ></div>
            </div>
          </div>

          {/* Node Info */}
          <div className="node-info">
            <div className="info-row">
              <span className="info-label">ID:</span>
              <span className="info-value">{selectedNodeData.id}</span>
            </div>
            <div className="info-row">
              <span className="info-label">Host:</span>
              <span className="info-value">{selectedNodeData.host}:{selectedNodeData.port}</span>
            </div>
            <div className="info-row">
              <span className="info-label">Role:</span>
              <span className="info-value">{selectedNodeData.role}</span>
            </div>
            <div className="info-row">
              <span className="info-label">Connected:</span>
              <span className="info-value">
                {new Date(selectedNodeData.connectedAt).toLocaleString()}
              </span>
            </div>
          </div>

          {/* Health Indicator */}
          <div className="health-indicator">
            <div className="health-label">Health Status:</div>
            <div className={`health-status ${
              selectedNodeData.metrics?.cpuUsage > 80 || selectedNodeData.metrics?.memoryUsage > 80
                ? 'unhealthy'
                : selectedNodeData.metrics?.cpuUsage > 60 || selectedNodeData.metrics?.memoryUsage > 60
                  ? 'degraded'
                  : 'healthy'
            }`}>
              {selectedNodeData.metrics?.cpuUsage > 80 || selectedNodeData.metrics?.memoryUsage > 80
                ? '⚠ Unhealthy'
                : selectedNodeData.metrics?.cpuUsage > 60 || selectedNodeData.metrics?.memoryUsage > 60
                  ? '⚡ Degraded'
                  : '✓ Healthy'}
            </div>
          </div>
        </div>
      ) : (
        <div className="no-node">No node selected</div>
      )}
    </div>
  )
}

export default NodeMetrics

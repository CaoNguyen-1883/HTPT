import { useState, useEffect } from 'react'
import '../styles/BatchMigration.css'

function BatchMigration() {
  const [nodes, setNodes] = useState([])
  const [codePackages, setCodePackages] = useState([])
  const [selectedCodes, setSelectedCodes] = useState([])
  const [sourceNode, setSourceNode] = useState('')
  const [targetNode, setTargetNode] = useState('')
  const [migrationType, setMigrationType] = useState('WEAK')
  const [batchStatus, setBatchStatus] = useState([])
  const [isRunning, setIsRunning] = useState(false)

  useEffect(() => {
    fetchNodes()
    fetchCodePackages()
  }, [])

  const fetchNodes = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/nodes')
      const data = await response.json()
      setNodes(data.filter(n => n.status === 'ONLINE'))
    } catch (error) {
      console.error('Failed to fetch nodes:', error)
    }
  }

  const fetchCodePackages = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/code')
      const data = await response.json()
      setCodePackages(data)
    } catch (error) {
      console.error('Failed to fetch code packages:', error)
    }
  }

  const toggleCodeSelection = (codeId) => {
    setSelectedCodes(prev =>
      prev.includes(codeId)
        ? prev.filter(id => id !== codeId)
        : [...prev, codeId]
    )
  }

  const selectAllCodes = () => {
    if (selectedCodes.length === codePackages.length) {
      setSelectedCodes([])
    } else {
      setSelectedCodes(codePackages.map(c => c.id))
    }
  }

  const startBatchMigration = async () => {
    if (!sourceNode || !targetNode || selectedCodes.length === 0) {
      alert('Please select source, target nodes and at least one code package')
      return
    }

    setIsRunning(true)
    setBatchStatus([])

    const results = []

    for (let i = 0; i < selectedCodes.length; i++) {
      const codeId = selectedCodes[i]
      const status = {
        codeId,
        index: i + 1,
        total: selectedCodes.length,
        status: 'pending',
        migrationId: null,
        error: null
      }

      setBatchStatus(prev => [...prev, status])

      try {
        // Update status to in_progress
        setBatchStatus(prev => prev.map(s =>
          s.codeId === codeId ? { ...s, status: 'in_progress' } : s
        ))

        const response = await fetch('http://localhost:8080/api/migrations', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            codeId,
            sourceNodeId: sourceNode,
            targetNodeId: targetNode,
            type: migrationType
          })
        })

        if (response.ok) {
          const migration = await response.json()
          setBatchStatus(prev => prev.map(s =>
            s.codeId === codeId
              ? { ...s, status: 'completed', migrationId: migration.id }
              : s
          ))
          results.push({ success: true, codeId, migrationId: migration.id })
        } else {
          throw new Error('Migration request failed')
        }
      } catch (error) {
        setBatchStatus(prev => prev.map(s =>
          s.codeId === codeId
            ? { ...s, status: 'failed', error: error.message }
            : s
        ))
        results.push({ success: false, codeId, error: error.message })
      }

      // Delay between migrations to avoid overload
      if (i < selectedCodes.length - 1) {
        await new Promise(resolve => setTimeout(resolve, 500))
      }
    }

    setIsRunning(false)

    const successCount = results.filter(r => r.success).length
    alert(`Batch migration complete: ${successCount}/${results.length} succeeded`)
  }

  const getStatusIcon = (status) => {
    switch (status) {
      case 'completed': return '✓'
      case 'failed': return '✗'
      case 'in_progress': return '⟳'
      default: return '○'
    }
  }

  return (
    <div className="batch-migration">
      <div className="batch-header">
        <h3>Batch Migration Control</h3>
        <p>Migrate multiple code packages simultaneously</p>
      </div>

      <div className="batch-config">
        <div className="config-section">
          <h4>Migration Configuration</h4>

          <div className="config-row">
            <div className="config-field">
              <label>Source Node:</label>
              <select
                value={sourceNode}
                onChange={(e) => setSourceNode(e.target.value)}
                disabled={isRunning}
              >
                <option value="">Select source...</option>
                {nodes.map(node => (
                  <option key={node.id} value={node.id}>
                    {node.id} (Load: {node.metrics?.loadScore.toFixed(2)})
                  </option>
                ))}
              </select>
            </div>

            <div className="config-field">
              <label>Target Node:</label>
              <select
                value={targetNode}
                onChange={(e) => setTargetNode(e.target.value)}
                disabled={isRunning}
              >
                <option value="">Select target...</option>
                {nodes.filter(n => n.id !== sourceNode).map(node => (
                  <option key={node.id} value={node.id}>
                    {node.id} (Load: {node.metrics?.loadScore.toFixed(2)})
                  </option>
                ))}
              </select>
            </div>

            <div className="config-field">
              <label>Migration Type:</label>
              <select
                value={migrationType}
                onChange={(e) => setMigrationType(e.target.value)}
                disabled={isRunning}
              >
                <option value="WEAK">Weak Mobility</option>
                <option value="STRONG">Strong Mobility</option>
              </select>
            </div>
          </div>
        </div>

        <div className="config-section">
          <div className="section-header">
            <h4>Select Code Packages ({selectedCodes.length} selected)</h4>
            <button
              onClick={selectAllCodes}
              className="btn-secondary"
              disabled={isRunning}
            >
              {selectedCodes.length === codePackages.length ? 'Deselect All' : 'Select All'}
            </button>
          </div>

          <div className="code-list">
            {codePackages.length === 0 ? (
              <div className="no-codes">No code packages available</div>
            ) : (
              codePackages.map(code => (
                <div
                  key={code.id}
                  className={`code-item ${selectedCodes.includes(code.id) ? 'selected' : ''}`}
                  onClick={() => !isRunning && toggleCodeSelection(code.id)}
                >
                  <input
                    type="checkbox"
                    checked={selectedCodes.includes(code.id)}
                    onChange={() => {}}
                    disabled={isRunning}
                  />
                  <div className="code-info">
                    <span className="code-name">{code.name}</span>
                    <span className="code-id">#{code.id}</span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      <div className="batch-actions">
        <button
          onClick={startBatchMigration}
          className="btn-primary"
          disabled={isRunning || selectedCodes.length === 0 || !sourceNode || !targetNode}
        >
          {isRunning ? 'Migration in progress...' : `Start Batch Migration (${selectedCodes.length})`}
        </button>

        {isRunning && (
          <div className="batch-progress">
            {batchStatus.filter(s => s.status !== 'pending').length} / {selectedCodes.length} migrations processed
          </div>
        )}
      </div>

      {batchStatus.length > 0 && (
        <div className="batch-status">
          <h4>Migration Status</h4>
          <div className="status-list">
            {batchStatus.map(status => (
              <div key={status.codeId} className={`status-item ${status.status}`}>
                <span className="status-icon">{getStatusIcon(status.status)}</span>
                <span className="status-text">
                  [{status.index}/{status.total}] Code {status.codeId}
                </span>
                {status.migrationId && (
                  <span className="migration-id">Migration: {status.migrationId}</span>
                )}
                {status.error && (
                  <span className="status-error">{status.error}</span>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

export default BatchMigration

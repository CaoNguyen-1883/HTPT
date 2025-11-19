import { useState } from 'react'
import { initiateMigration } from '../services/api'
import useStore from '../store/useStore'

function MigrationPanel({ migrations, nodes }) {
  const [form, setForm] = useState({
    codeId: '',
    sourceNodeId: '',
    targetNodeId: '',
    type: 'WEAK'
  })
  const [loading, setLoading] = useState(false)

  const { addLog, codePackages } = useStore()

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!form.sourceNodeId || !form.targetNodeId) {
      addLog('Please select source and target nodes', 'error')
      return
    }

    setLoading(true)
    try {
      const migration = await initiateMigration(form)
      addLog(`Migration initiated: ${migration.id}`, 'success')
    } catch (error) {
      addLog(`Error: ${error.message}`, 'error')
    } finally {
      setLoading(false)
    }
  }

  const activeMigration = migrations.find(m => m.status === 'IN_PROGRESS')

  return (
    <div className="migration-panel">
      <h3>Migration Control</h3>

      {/* Migration Form */}
      <form onSubmit={handleSubmit}>
        <div className="form-row">
          <div className="form-group">
            <label>Source Node</label>
            <select
              value={form.sourceNodeId}
              onChange={e => setForm({...form, sourceNodeId: e.target.value})}
            >
              <option value="">Select...</option>
              {nodes.map(node => (
                <option key={node.id} value={node.id}>{node.id}</option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label>Target Node</label>
            <select
              value={form.targetNodeId}
              onChange={e => setForm({...form, targetNodeId: e.target.value})}
            >
              <option value="">Select...</option>
              {nodes.filter(n => n.id !== form.sourceNodeId).map(node => (
                <option key={node.id} value={node.id}>{node.id}</option>
              ))}
            </select>
          </div>
        </div>

        <div className="form-row">
          <div className="form-group">
            <label>Code Package</label>
            <select
              value={form.codeId}
              onChange={e => setForm({...form, codeId: e.target.value})}
            >
              <option value="">Select...</option>
              {codePackages.map(pkg => (
                <option key={pkg.id} value={pkg.id}>{pkg.name} ({pkg.id})</option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label>Migration Type</label>
            <select
              value={form.type}
              onChange={e => setForm({...form, type: e.target.value})}
            >
              <option value="WEAK">Weak (Code only)</option>
              <option value="STRONG">Strong (Code + State)</option>
            </select>
          </div>
        </div>

        <button
          type="submit"
          disabled={loading || !form.sourceNodeId || !form.targetNodeId}
          className="btn-primary"
        >
          {loading ? 'Starting...' : 'Start Migration'}
        </button>
      </form>

      {/* Active Migration Progress */}
      {activeMigration && (
        <div className="active-migration">
          <h4>Active Migration</h4>
          <p className="migration-route">
            {activeMigration.sourceNodeId} → {activeMigration.targetNodeId}
          </p>
          <p className="migration-type">{activeMigration.type}</p>
          <div className="progress-bar large">
            <div
              className="progress animated"
              style={{ width: `${activeMigration.progress}%` }}
            />
          </div>
          <span className="progress-text">{activeMigration.progress}%</span>
        </div>
      )}

      {/* Migration History */}
      <div className="migration-history">
        <h4>History</h4>
        {migrations.length === 0 ? (
          <p className="no-history">No migrations yet</p>
        ) : (
          <ul>
            {migrations.slice(-5).reverse().map(m => (
              <li key={m.id} className={`status-${m.status?.toLowerCase()}`}>
                <span className="migration-id">{m.id}</span>
                <span className="migration-info">
                  {m.sourceNodeId} → {m.targetNodeId}
                </span>
                <span className={`status-badge ${m.status?.toLowerCase()}`}>
                  {m.status}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

export default MigrationPanel

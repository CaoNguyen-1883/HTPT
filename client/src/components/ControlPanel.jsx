import { useState } from 'react'
import {
  uploadCode,
  runMobileAgentDemo,
  runLoadBalancingDemo,
  runFaultToleranceDemo
} from '../services/api'
import useStore from '../store/useStore'

function ControlPanel({ nodes }) {
  const [code, setCode] = useState(`// Mobile Agent Example
def data = []
def nodeId = nodeId

def collect() {
    data << [
        node: nodeId,
        time: System.currentTimeMillis(),
        value: Math.random() * 100
    ]
    return "Collected from " + nodeId
}

collect()
`)

  const [codeName, setCodeName] = useState('MobileAgent')
  const [initialNode, setInitialNode] = useState('')
  const [loading, setLoading] = useState(false)

  const { addLog, addCodePackage } = useStore()

  const handleUpload = async () => {
    if (!initialNode) {
      addLog('Please select initial node', 'error')
      return
    }

    setLoading(true)
    try {
      const result = await uploadCode({
        name: codeName,
        code: code,
        entryPoint: 'collect',
        initialNodeId: initialNode
      })
      addCodePackage(result)
      addLog(`Code uploaded: ${result.id} - ${result.name}`, 'success')
    } catch (error) {
      addLog(`Upload error: ${error.message}`, 'error')
    } finally {
      setLoading(false)
    }
  }

  const runDemo = async (scenario) => {
    if (nodes.length < 2) {
      addLog('Need at least 2 nodes for demo', 'error')
      return
    }

    setLoading(true)
    try {
      let result
      switch (scenario) {
        case 'mobile-agent':
          result = await runMobileAgentDemo()
          break
        case 'load-balancing':
          result = await runLoadBalancingDemo()
          break
        case 'fault-tolerance':
          result = await runFaultToleranceDemo()
          break
        default:
          throw new Error('Unknown scenario')
      }
      addLog(`Demo ${scenario}: ${result.message}`, 'info')
    } catch (error) {
      addLog(`Demo error: ${error.message}`, 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="control-panel">
      <h3>Control Panel</h3>

      {/* Demo Scenarios */}
      <div className="demo-section">
        <h4>Quick Demo</h4>
        <div className="demo-buttons">
          <button onClick={() => runDemo('mobile-agent')} className="btn-demo">
            Mobile Agent
          </button>
          <button onClick={() => runDemo('load-balancing')} className="btn-demo">
            Load Balancing
          </button>
          <button onClick={() => runDemo('fault-tolerance')} className="btn-demo">
            Fault Tolerance
          </button>
        </div>
      </div>

      {/* Code Editor */}
      <div className="code-editor">
        <h4>Code Editor</h4>
        <div className="editor-controls">
          <input
            type="text"
            value={codeName}
            onChange={e => setCodeName(e.target.value)}
            placeholder="Code name"
          />
          <select
            value={initialNode}
            onChange={e => setInitialNode(e.target.value)}
          >
            <option value="">Initial Node...</option>
            {nodes.map(node => (
              <option key={node.id} value={node.id}>{node.id}</option>
            ))}
          </select>
        </div>
        <textarea
          value={code}
          onChange={e => setCode(e.target.value)}
          rows={8}
          spellCheck={false}
        />
        <button
          onClick={handleUpload}
          disabled={loading || !initialNode}
          className="btn-primary"
        >
          {loading ? 'Uploading...' : 'Upload Code'}
        </button>
      </div>
    </div>
  )
}

export default ControlPanel

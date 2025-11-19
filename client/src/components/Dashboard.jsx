import { useState } from 'react'
import NetworkTopology from './NetworkTopology'
import NodeList from './NodeList'
import MigrationPanel from './MigrationPanel'
import ControlPanel from './ControlPanel'
import LogViewer from './LogViewer'
import useStore from '../store/useStore'

function Dashboard() {
  const { nodes, migrations, logs } = useStore()
  const [selectedNode, setSelectedNode] = useState(null)

  return (
    <div className="dashboard">
      <div className="dashboard-left">
        <NetworkTopology
          nodes={nodes}
          selectedNode={selectedNode}
          onNodeSelect={setSelectedNode}
        />
        <NodeList
          nodes={nodes}
          selectedNode={selectedNode}
          onNodeSelect={setSelectedNode}
        />
      </div>

      <div className="dashboard-right">
        <ControlPanel nodes={nodes} />
        <MigrationPanel
          migrations={migrations}
          nodes={nodes}
        />
        <LogViewer logs={logs} />
      </div>
    </div>
  )
}

export default Dashboard

import NodeCard from './NodeCard'

function NodeList({ nodes, selectedNode, onNodeSelect }) {
  return (
    <div className="node-list">
      <h3>Nodes ({nodes.length})</h3>
      <div className="node-grid">
        {nodes.length === 0 ? (
          <p className="no-nodes">No nodes connected</p>
        ) : (
          nodes.map(node => (
            <NodeCard
              key={node.id}
              node={node}
              isSelected={selectedNode?.id === node.id}
              onSelect={() => onNodeSelect(node)}
            />
          ))
        )}
      </div>
    </div>
  )
}

export default NodeList

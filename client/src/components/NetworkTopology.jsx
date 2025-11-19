import { useEffect, useRef } from 'react'
import * as d3 from 'd3'
import useStore from '../store/useStore'

function NetworkTopology({ nodes, selectedNode, onNodeSelect }) {
  const svgRef = useRef()
  const migrations = useStore(state => state.migrations)

  useEffect(() => {
    if (!nodes || nodes.length === 0) return

    const svg = d3.select(svgRef.current)
    const width = 500
    const height = 350

    svg.selectAll('*').remove()

    // Calculate node positions in circle
    const nodeData = nodes.map((node, i) => {
      const angle = (i / nodes.length) * 2 * Math.PI - Math.PI / 2
      const radius = 120
      return {
        ...node,
        x: width / 2 + radius * Math.cos(angle),
        y: height / 2 + radius * Math.sin(angle)
      }
    })

    // Draw connections
    const links = []
    for (let i = 0; i < nodeData.length; i++) {
      for (let j = i + 1; j < nodeData.length; j++) {
        links.push({
          source: nodeData[i],
          target: nodeData[j]
        })
      }
    }

    svg.selectAll('.link')
      .data(links)
      .enter()
      .append('line')
      .attr('class', 'link')
      .attr('x1', d => d.source.x)
      .attr('y1', d => d.source.y)
      .attr('x2', d => d.target.x)
      .attr('y2', d => d.target.y)
      .attr('stroke', '#e0e0e0')
      .attr('stroke-width', 1)

    // Draw active migration arrow
    const activeMigration = migrations.find(m => m.status === 'IN_PROGRESS')
    if (activeMigration) {
      const sourceNode = nodeData.find(n => n.id === activeMigration.sourceNodeId)
      const targetNode = nodeData.find(n => n.id === activeMigration.targetNodeId)

      if (sourceNode && targetNode) {
        // Draw migration arrow
        svg.append('line')
          .attr('class', 'migration-arrow')
          .attr('x1', sourceNode.x)
          .attr('y1', sourceNode.y)
          .attr('x2', targetNode.x)
          .attr('y2', targetNode.y)
          .attr('stroke', '#2196F3')
          .attr('stroke-width', 3)
          .attr('marker-end', 'url(#arrowhead)')

        // Add arrowhead marker
        svg.append('defs').append('marker')
          .attr('id', 'arrowhead')
          .attr('viewBox', '-0 -5 10 10')
          .attr('refX', 35)
          .attr('refY', 0)
          .attr('orient', 'auto')
          .attr('markerWidth', 8)
          .attr('markerHeight', 8)
          .append('path')
          .attr('d', 'M 0,-5 L 10,0 L 0,5')
          .attr('fill', '#2196F3')
      }
    }

    // Draw nodes
    const nodeGroups = svg.selectAll('.node')
      .data(nodeData)
      .enter()
      .append('g')
      .attr('class', 'node')
      .attr('transform', d => `translate(${d.x}, ${d.y})`)
      .style('cursor', 'pointer')
      .on('click', (event, d) => onNodeSelect(d))

    // Node circle
    nodeGroups.append('circle')
      .attr('r', 28)
      .attr('fill', d => getNodeColor(d.status))
      .attr('stroke', d => selectedNode?.id === d.id ? '#333' : 'transparent')
      .attr('stroke-width', 3)

    // Node label
    nodeGroups.append('text')
      .attr('text-anchor', 'middle')
      .attr('dy', 5)
      .attr('fill', 'white')
      .attr('font-weight', 'bold')
      .attr('font-size', '12px')
      .text(d => d.id.replace('node-', 'N'))

    // CPU indicator below node
    nodeGroups.append('text')
      .attr('text-anchor', 'middle')
      .attr('dy', 45)
      .attr('font-size', '10px')
      .attr('fill', '#666')
      .text(d => `${d.metrics?.cpuUsage?.toFixed(0) || 0}%`)

  }, [nodes, selectedNode, migrations, onNodeSelect])

  const getNodeColor = (status) => {
    switch (status) {
      case 'ONLINE': return '#4CAF50'
      case 'BUSY': return '#FF9800'
      case 'MIGRATING': return '#2196F3'
      case 'OFFLINE': return '#f44336'
      default: return '#9E9E9E'
    }
  }

  return (
    <div className="network-topology">
      <h3>Network Topology</h3>
      <svg ref={svgRef} width={500} height={350} />
      <div className="legend">
        <span><i style={{backgroundColor: '#4CAF50'}}></i> Online</span>
        <span><i style={{backgroundColor: '#2196F3'}}></i> Migrating</span>
        <span><i style={{backgroundColor: '#FF9800'}}></i> Busy</span>
        <span><i style={{backgroundColor: '#f44336'}}></i> Offline</span>
      </div>
    </div>
  )
}

export default NetworkTopology

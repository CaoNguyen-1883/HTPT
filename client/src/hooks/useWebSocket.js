import { useCallback, useRef } from 'react'
import SockJS from 'sockjs-client/dist/sockjs'
import { Client } from '@stomp/stompjs'
import useStore from '../store/useStore'

const SOCKET_URL = 'http://localhost:8080/ws'

export function useWebSocket() {
  const clientRef = useRef(null)
  const {
    setConnected,
    setNodes,
    addMigration,
    updateMigration,
    updateNodeMetrics,
    addLog,
    setDemoStatus
  } = useStore()

  const connect = useCallback(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(SOCKET_URL),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,

      onConnect: () => {
        setConnected(true)
        addLog('Connected to server', 'success')

        // Subscribe to nodes updates
        client.subscribe('/topic/nodes', (message) => {
          const data = JSON.parse(message.body)
          setNodes(data.nodes || [])
        })

        // Subscribe to migrations
        client.subscribe('/topic/migrations', (message) => {
          const migration = JSON.parse(message.body)
          if (migration.status === 'PENDING') {
            addMigration(migration)
            addLog(`Migration started: ${migration.sourceNodeId} -> ${migration.targetNodeId}`, 'info')
          } else {
            updateMigration(migration)
            if (migration.status === 'COMPLETED') {
              addLog(`Migration completed: ${migration.id}`, 'success')
            } else if (migration.status === 'FAILED') {
              addLog(`Migration failed: ${migration.errorMessage}`, 'error')
            }
          }
        })

        // Subscribe to migration progress
        client.subscribe('/topic/migration/*', (message) => {
          const data = JSON.parse(message.body)
          addLog(`[${data.progress}%] ${data.message}`, 'info')
        })

        // Subscribe to detailed logs from server
        client.subscribe('/topic/logs', (message) => {
          const data = JSON.parse(message.body)
          // Use the formatted message from server with proper level
          addLog(data.formatted || data.message, data.level || 'info', {
            nodeId: data.nodeId,
            event: data.event
          })
        })

        // Subscribe to demo events
        client.subscribe('/topic/demo', (message) => {
          const data = JSON.parse(message.body)
          setDemoStatus(data)

          // Log based on event type
          if (data.type === 'demo:started') {
            addLog(data.message, 'success')
          } else if (data.type === 'demo:completed') {
            addLog(data.message, 'success')
          } else if (data.type === 'demo:error') {
            addLog(data.message, 'error')
          } else if (data.type === 'demo:warning') {
            addLog(data.message, 'warning')
          } else {
            addLog(data.message, 'info')
          }
        })

        // Request initial topology
        client.publish({
          destination: '/app/node/register',
          body: JSON.stringify({
            id: 'dashboard',
            host: 'localhost',
            port: 3000
          })
        })
      },

      onDisconnect: () => {
        setConnected(false)
        addLog('Disconnected from server', 'warning')
      },

      onStompError: (frame) => {
        addLog(`WebSocket error: ${frame.headers['message']}`, 'error')
      }
    })

    client.activate()
    clientRef.current = client
  }, [])

  const disconnect = useCallback(() => {
    if (clientRef.current) {
      clientRef.current.deactivate()
    }
  }, [])

  const send = useCallback((destination, body) => {
    if (clientRef.current && clientRef.current.connected) {
      clientRef.current.publish({
        destination,
        body: JSON.stringify(body)
      })
    }
  }, [])

  return {
    connect,
    disconnect,
    send,
    connected: useStore(state => state.connected)
  }
}

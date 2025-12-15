#!/bin/bash

echo "=========================================="
echo "Testing Code Migration System with H2 DB"
echo "=========================================="

# Kill existing processes
pkill -f code-migration-1.0.0.jar
sleep 2

# Clean logs
rm -f /tmp/coord.log /tmp/worker.log

# Start Coordinator
echo "Starting Coordinator..."
cd /home/nguyenkrbs1/workdir/dev/HTPT/server
java -jar target/code-migration-1.0.0.jar \
  --spring.profiles.active=coordinator \
  --server.port=8080 > /tmp/coord.log 2>&1 &
COORD_PID=$!
echo "Coordinator PID: $COORD_PID"

# Wait for Coordinator to start
echo "Waiting for Coordinator to start..."
sleep 8

# Check if started
if grep -q "Started MigrationApplication" /tmp/coord.log; then
    echo "✓ Coordinator started successfully!"
else
    echo "✗ Coordinator failed to start. Check /tmp/coord.log"
    exit 1
fi

# Start Worker
echo ""
echo "Starting Worker..."
java -jar target/code-migration-1.0.0.jar \
  --spring.profiles.active=worker \
  --node.id=test-node1 \
  --server.port=8081 > /tmp/worker.log 2>&1 &
WORKER_PID=$!
echo "Worker PID: $WORKER_PID"

# Wait for Worker to connect
echo "Waiting for Worker to connect..."
sleep 5

# Check if connected
if grep -q "Connected to coordinator" /tmp/worker.log; then
    echo "✓ Worker connected successfully!"
else
    echo "✗ Worker failed to connect. Check /tmp/worker.log"
fi

echo ""
echo "Services Status:"
echo "  Coordinator: http://localhost:8080"
echo "  H2 Console: http://localhost:8080/h2-console"
echo "  Worker: test-node1"
echo ""
echo "Logs:"
echo "  Coordinator: /tmp/coord.log"
echo "  Worker: /tmp/worker.log"
echo ""
echo "To stop: pkill -f code-migration-1.0.0.jar"
echo "=========================================="

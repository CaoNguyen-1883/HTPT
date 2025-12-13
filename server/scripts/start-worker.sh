#!/bin/bash
# ===========================================
# START WORKER NODE
# Chạy trên các laptop worker
# ===========================================

# Màu sắc
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Lấy thư mục chứa script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE="$SCRIPT_DIR/../target/migration-0.0.1-SNAPSHOT.jar"

# Nếu không có JAR trong thư mục project, tìm trong thư mục hiện tại
if [ ! -f "$JAR_FILE" ]; then
    JAR_FILE="./migration-0.0.1-SNAPSHOT.jar"
fi

if [ ! -f "$JAR_FILE" ]; then
    JAR_FILE="./migration.jar"
fi

if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}ERROR: Không tìm thấy file JAR${NC}"
    echo "Vui lòng đặt file JAR trong thư mục hiện tại"
    exit 1
fi

# Lấy IP của máy này
if command -v hostname &> /dev/null; then
    MY_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
fi

if [ -z "$MY_IP" ]; then
    MY_IP="localhost"
fi

# Tham số
NODE_ID=${1:-"worker-$(hostname)"}
COORDINATOR_IP=${2:-"192.168.1.100"}
COORDINATOR_PORT=${3:-8080}
WORKER_PORT=${4:-8081}

echo -e "${GREEN}==========================================${NC}"
echo -e "${GREEN}       CODE MIGRATION - WORKER           ${NC}"
echo -e "${GREEN}==========================================${NC}"
echo ""
echo -e "  Node ID     : ${YELLOW}$NODE_ID${NC}"
echo -e "  My IP       : ${YELLOW}$MY_IP${NC}"
echo -e "  My Port     : ${YELLOW}$WORKER_PORT${NC}"
echo -e "  Coordinator : ${YELLOW}http://$COORDINATOR_IP:$COORDINATOR_PORT${NC}"
echo ""
echo -e "${GREEN}==========================================${NC}"
echo ""
echo "Đang kết nối đến Coordinator..."
echo ""

java -jar "$JAR_FILE" \
  --spring.profiles.active=worker \
  --server.port=$WORKER_PORT \
  --node.id=$NODE_ID \
  --node.host=$MY_IP \
  --node.coordinator-url=http://$COORDINATOR_IP:$COORDINATOR_PORT

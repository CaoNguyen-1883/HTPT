#!/bin/bash
# ===========================================
# START COORDINATOR
# Chạy trên laptop chính (Coordinator)
# ===========================================

# Màu sắc
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Lấy thư mục chứa script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE="$SCRIPT_DIR/../target/migration-0.0.1-SNAPSHOT.jar"

# Kiểm tra file JAR
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}ERROR: Không tìm thấy file JAR${NC}"
    echo "Vui lòng build trước: mvn clean package -DskipTests"
    exit 1
fi

# Lấy IP của máy này
if command -v hostname &> /dev/null; then
    MY_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
fi

if [ -z "$MY_IP" ]; then
    MY_IP="localhost"
fi

PORT=${1:-8080}

echo -e "${GREEN}==========================================${NC}"
echo -e "${GREEN}     CODE MIGRATION - COORDINATOR        ${NC}"
echo -e "${GREEN}==========================================${NC}"
echo ""
echo -e "  IP Address : ${YELLOW}$MY_IP${NC}"
echo -e "  Port       : ${YELLOW}$PORT${NC}"
echo -e "  Frontend   : ${YELLOW}http://$MY_IP:$PORT${NC}"
echo ""
echo -e "${GREEN}==========================================${NC}"
echo ""
echo -e "Workers kết nối bằng lệnh:"
echo -e "${YELLOW}  java -jar migration.jar --spring.profiles.active=worker \\${NC}"
echo -e "${YELLOW}    --node.id=node-1 \\${NC}"
echo -e "${YELLOW}    --node.host=<WORKER_IP> \\${NC}"
echo -e "${YELLOW}    --node.coordinator-url=http://$MY_IP:$PORT${NC}"
echo ""
echo "Đang khởi động Coordinator..."
echo ""

java -jar "$JAR_FILE" \
  --spring.profiles.active=coordinator \
  --server.port=$PORT

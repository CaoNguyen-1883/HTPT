#!/bin/bash
# ===========================================
# START DEMO MODE
# Chạy tất cả trên 1 máy với simulated nodes
# ===========================================

# Màu sắc
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE="$SCRIPT_DIR/../target/migration-0.0.1-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Building project..."
    cd "$SCRIPT_DIR/.." && mvn clean package -DskipTests
fi

echo -e "${GREEN}==========================================${NC}"
echo -e "${GREEN}     CODE MIGRATION - DEMO MODE          ${NC}"
echo -e "${GREEN}==========================================${NC}"
echo ""
echo -e "  Frontend: ${YELLOW}http://localhost:8080${NC}"
echo -e "  Nodes   : ${YELLOW}5 simulated nodes${NC}"
echo ""
echo -e "${GREEN}==========================================${NC}"
echo ""

java -jar "$JAR_FILE" --spring.profiles.active=demo

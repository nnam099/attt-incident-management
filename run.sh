#!/bin/bash
# Script khởi động dự án incident-management
set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

# ── 1. Chọn Java 17 nếu có, không thì dùng Java hiện tại ──
JAVA17="/usr/lib/jvm/java-17-openjdk-amd64"
if [ -d "$JAVA17" ]; then
  export JAVA_HOME="$JAVA17"
  export PATH="$JAVA_HOME/bin:$PATH"
  echo "✅ Dùng Java: $(java -version 2>&1 | head -1)"
else
  echo "⚠️  Không tìm thấy JDK 17, dùng Java hiện tại: $(java -version 2>&1 | head -1)"
fi

# ── 2. Load biến môi trường từ .env ──
if [ -f .env ]; then
  set -a && source .env && set +a
  echo "✅ Đã load .env"
else
  echo "⚠️  Không tìm thấy .env, dùng giá trị mặc định"
fi

# ── 3. Khởi động PostgreSQL nếu chưa chạy ──
if ! docker ps --format '{{.Names}}' 2>/dev/null | grep -q "incident-postgres"; then
  echo "🐘 Khởi động PostgreSQL..."
  docker compose up -d postgres
  echo "⏳ Chờ PostgreSQL sẵn sàng..."
  sleep 4
else
  echo "✅ PostgreSQL đang chạy"
fi

# ── 4. Chạy Spring Boot ──
echo ""
echo "🚀 Khởi động Spring Boot..."
echo "   → API:     http://localhost:8080"
echo "   → Swagger: http://localhost:8080/swagger-ui.html"
echo ""

mvn spring-boot:run

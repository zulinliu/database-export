#!/bin/bash

echo "========================================"
echo "数据库导出工具 V2.0 - 启动脚本"
echo "========================================"

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAR_FILE="$PROJECT_DIR/target/db-export-tool.jar"
LOG_DIR="$PROJECT_DIR/logs"
EXPORTS_DIR="$PROJECT_DIR/exports"
TEMPLATES_DIR="$PROJECT_DIR/templates"
TEMP_DIR="$PROJECT_DIR/temp"

# Create directories
mkdir -p "$LOG_DIR" "$EXPORTS_DIR" "$TEMPLATES_DIR" "$TEMP_DIR"

echo ""
echo "配置信息："
echo "----------------------------------------"
echo "项目目录: $PROJECT_DIR"
echo "日志目录: $LOG_DIR"
echo "导出目录: $EXPORTS_DIR"
echo "模板目录: $TEMPLATES_DIR"
echo ""

# Check JAR
if [ ! -f "$JAR_FILE" ]; then
    echo "错误: JAR 文件不存在，请先运行 install.sh"
    echo "提示: ./scripts/install.sh"
    exit 1
fi

echo "启动服务..."
echo "----------------------------------------"

# Start with nohup
nohup java -Xms256m -Xmx512m \
    -jar "$JAR_FILE" \
    --spring.config.additional-location=optional:$PROJECT_DIR/src/main/resources/application.yml \
    > "$LOG_DIR/startup.log" 2>&1 &

APP_PID=$!
echo "进程 PID: $APP_PID"
echo "日志文件: $LOG_DIR/startup.log"

# Wait for startup
echo ""
echo "等待服务启动..."
sleep 3

# Check if running
if ps -p $APP_PID > /dev/null 2>&1; then
    echo ""
    echo "========================================"
    echo "✓ 服务启动成功！"
    echo "========================================"
    echo ""
    echo "访问地址: http://localhost:8080"
    echo "默认账号: admin / 123456"
    echo ""
    echo "停止服务: kill $APP_PID"
    echo ""
else
    echo ""
    echo "错误: 服务启动失败，请查看日志: $LOG_DIR/startup.log"
    exit 1
fi

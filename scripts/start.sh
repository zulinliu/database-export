#!/bin/bash

# 数据库导出工具V2.0 - Linux启动脚本

APP_NAME="db-export-tool"
JAR_FILE="target/db-export-tool.jar"
PID_FILE="./${APP_NAME}.pid"
LOG_FILE="./logs/startup.log"

# 检查是否已在运行
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        echo "${APP_NAME} 已在运行 (PID: ${PID})"
        exit 1
    fi
    rm -f "$PID_FILE"
fi

# 检查JAR文件
if [ ! -f "$JAR_FILE" ]; then
    echo "错误: 未找到 ${JAR_FILE}，请先运行安装脚本"
    exit 1
fi

# 创建目录
mkdir -p exports templates logs temp

# JVM参数
JAVA_OPTS="-Xms256m -Xmx1024m -XX:+UseG1GC -Dfile.encoding=UTF-8"

echo "启动 ${APP_NAME}..."
nohup java $JAVA_OPTS -jar "$JAR_FILE" > "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"

echo "${APP_NAME} 启动成功 (PID: $(cat $PID_FILE))"
echo "日志文件: ${LOG_FILE}"
echo "访问地址: http://localhost:8080"
echo "默认账号: admin / 123456"
echo ""
echo "停止服务: ./scripts/stop.sh"
echo "查看日志: tail -f ${LOG_FILE}"

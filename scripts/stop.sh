#!/bin/bash

# 数据库导出工具V2.0 - Linux停止脚本

APP_NAME="db-export-tool"
PID_FILE="./${APP_NAME}.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "${APP_NAME} 未在运行"
    exit 0
fi

PID=$(cat "$PID_FILE")
if kill -0 "$PID" 2>/dev/null; then
    echo "停止 ${APP_NAME} (PID: ${PID})..."
    kill "$PID"
    sleep 3
    if kill -0 "$PID" 2>/dev/null; then
        echo "强制停止..."
        kill -9 "$PID"
    fi
    rm -f "$PID_FILE"
    echo "${APP_NAME} 已停止"
else
    rm -f "$PID_FILE"
    echo "${APP_NAME} 未在运行"
fi

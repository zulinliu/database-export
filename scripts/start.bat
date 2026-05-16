@echo off
chcp 65001 >nul
title 数据库导出工具 V2.0

if not exist "target\db-export-tool.jar" (
    echo 错误: 未找到 target\db-export-tool.jar，请先运行安装脚本
    pause
    exit /b 1
)

if not exist "exports" mkdir exports
if not exist "templates" mkdir templates
if not exist "logs" mkdir logs
if not exist "temp" mkdir temp

echo 启动数据库导出工具 V2.0...
echo 访问地址: http://localhost:8080
echo 默认账号: admin / 123456
echo 按 Ctrl+C 停止服务
echo.

java -Xms256m -Xmx1024m -XX:+UseG1GC -Dfile.encoding=UTF-8 -jar target\db-export-tool.jar

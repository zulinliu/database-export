#!/bin/bash

echo "========================================"
echo "  数据库导出工具 - 启动脚本"
echo "========================================"
echo ""

# 检查jar文件是否存在
if [ ! -f "db-export-tool.jar" ]; then
    if ls target/db-export-tool-*.jar 1>/dev/null 2>&1; then
        cp target/db-export-tool-*.jar db-export-tool.jar
    else
        echo "错误：找不到 db-export-tool.jar"
        echo "请先运行 ./install.sh 编译项目"
        exit 1
    fi
fi

# 创建必要的目录
mkdir -p exports templates logs temp

echo "正在启动应用..."
echo "访问地址：http://localhost:8080"
echo "默认账号：admin / 123456"
echo ""
echo "按 Ctrl+C 停止应用"
echo "========================================"
echo ""

# 启动应用
java -jar db-export-tool.jar

#!/bin/bash

echo "========================================"
echo "  数据库导出工具 - 安装脚本"
echo "========================================"
echo ""

# 创建必要的目录
echo "[1/3] 创建目录..."
mkdir -p exports templates logs temp
echo "目录创建完成"
echo ""

# 使用Maven编译打包
echo "[2/3] 编译项目..."
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "编译失败，请检查Maven和JDK配置"
    exit 1
fi
echo "编译完成"
echo ""

# 复制jar包
echo "[3/3] 准备运行文件..."
cp target/db-export-tool-*.jar db-export-tool.jar
echo "安装完成！"
echo ""
echo "运行 ./start.sh 启动应用"

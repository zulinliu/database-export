#!/bin/bash

echo "========================================"
echo "数据库导出工具 V2.0 - 安装脚本"
echo "========================================"

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo ""
echo "检查环境..."
echo "----------------------------------------"

# Check Java
if ! command -v java &> /dev/null; then
    echo "错误: 未找到 Java，请先安装 JDK 8 或更高版本"
    exit 1
fi
JAVA_VER=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}')
echo "✓ Java 版本: $JAVA_VER"

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo "错误: 未找到 Maven，请先安装 Maven 3.6+"
    exit 1
fi
MVN_VER=$(mvn -version 2>&1 | head -n 1 | awk -F ': ' '{print $2}')
echo "✓ Maven 版本: $MVN_VER"

echo ""
echo "创建目录结构..."
echo "----------------------------------------"
mkdir -p "$PROJECT_DIR/exports"
mkdir -p "$PROJECT_DIR/templates"
mkdir -p "$PROJECT_DIR/logs"
mkdir -p "$PROJECT_DIR/temp"
echo "✓ 目录创建完成"

echo ""
echo "编译项目..."
echo "----------------------------------------"
cd "$PROJECT_DIR"
mvn clean package -DskipTests -q
if [ $? -ne 0 ]; then
    echo "错误: Maven 编译失败"
    exit 1
fi
echo "✓ 编译成功"

echo ""
echo "检查 JAR 文件..."
echo "----------------------------------------"
if [ ! -f "$PROJECT_DIR/target/db-export-tool.jar" ]; then
    echo "错误: 编译产物不存在"
    exit 1
fi
JAR_SIZE=$(du -h "$PROJECT_DIR/target/db-export-tool.jar" | cut -f1)
echo "✓ JAR 文件: target/db-export-tool.jar ($JAR_SIZE)"

echo ""
echo "========================================"
echo "安装完成！"
echo "========================================"
echo ""
echo "下一步：运行 ./scripts/start.sh 启动服务"
echo ""

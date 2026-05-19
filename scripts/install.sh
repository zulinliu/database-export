#!/bin/bash

# 数据库导出工具V2.0 - Linux安装脚本

echo "======================================"
echo "  数据库导出工具 V2.0 - 安装向导"
echo "======================================"
echo ""

# 检查Java
if ! command -v java &> /dev/null; then
    echo "错误: 未检测到Java环境，请先安装JDK 8或更高版本"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
echo "检测到Java版本: $(java -version 2>&1 | head -1)"

# 创建目录
echo "创建目录结构..."
mkdir -p exports templates logs temp

# 检查是否需要打包
if [ ! -f "target/db-export-tool.jar" ]; then
    echo "未检测到构建产物，开始Maven打包..."
    if command -v mvn &> /dev/null; then
        mvn clean package -DskipTests
        if [ $? -ne 0 ]; then
            echo "错误: Maven打包失败"
            exit 1
        fi
    else
        echo "错误: 未检测到Maven，请先安装Maven或手动打包"
        exit 1
    fi
fi

echo ""
echo "安装完成！"
echo "运行 ./scripts/start.sh 启动服务"
echo "访问 http://localhost:8080 使用系统"
echo "默认账号: admin / 123456"

@echo off
chcp 65001 >nul
echo ======================================
echo   数据库导出工具 V2.0 - 安装向导
echo ======================================
echo.

where java >nul 2>nul
if %errorlevel% neq 0 (
    echo 错误: 未检测到Java环境，请先安装JDK 8或更高版本
    pause
    exit /b 1
)

echo 检测到Java环境:
java -version 2>&1 | findstr /i "version"
echo.

echo 创建目录结构...
if not exist "exports" mkdir exports
if not exist "templates" mkdir templates
if not exist "logs" mkdir logs
if not exist "temp" mkdir temp

if not exist "target\db-export-tool.jar" (
    echo 未检测到构建产物，开始Maven打包...
    where mvn >nul 2>nul
    if %errorlevel% neq 0 (
        echo 错误: 未检测到Maven，请先安装Maven或手动打包
        pause
        exit /b 1
    )
    call mvn clean package -DskipTests
    if %errorlevel% neq 0 (
        echo 错误: Maven打包失败
        pause
        exit /b 1
    )
)

echo.
echo 安装完成！
echo 运行 scripts\start.bat 启动服务
echo 访问 http://localhost:8080 使用系统
echo 默认账号: admin / 123456
pause

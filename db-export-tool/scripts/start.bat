@echo off
chcp 65001 > nul
echo ========================================
echo 数据库导出工具 V2.0 - 启动脚本
echo ========================================
echo.

set PROJECT_DIR=%~dp0..
cd /d "%PROJECT_DIR%"

set JAR_FILE=%PROJECT_DIR%\target\db-export-tool.jar
set LOG_DIR=%PROJECT_DIR%\logs
set EXPORTS_DIR=%PROJECT_DIR%\exports
set TEMPLATES_DIR=%PROJECT_DIR%\templates
set TEMP_DIR=%PROJECT_DIR%\temp

echo 配置信息:
echo ----------------------------------------
echo 项目目录: %PROJECT_DIR%
echo 日志目录: %LOG_DIR%
echo 导出目录: %EXPORTS_DIR%
echo.

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
if not exist "%EXPORTS_DIR%" mkdir "%EXPORTS_DIR%"
if not exist "%TEMPLATES_DIR%" mkdir "%TEMPLATES_DIR%"
if not exist "%TEMP_DIR%" mkdir "%TEMP_DIR%"

if not exist "%JAR_FILE%" (
    echo 错误: JAR 文件不存在，请先运行 install.bat
    echo 提示: scripts\install.bat
    pause
    exit /b 1
)

echo 启动服务...
echo ----------------------------------------

start "数据库导出工具" cmd /c "java -Xms256m -Xmx512m -jar \"%JAR_FILE%\" > \"%LOG_DIR%\startup.log\" 2>&1"

echo 服务已在后台启动
echo.
echo ========================================
echo 服务启动成功！
echo ========================================
echo.
echo 访问地址: http://localhost:8080
echo 默认账号: admin / 123456
echo.
echo 查看日志: %LOG_DIR%\startup.log
echo.
pause

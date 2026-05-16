@echo off
echo ========================================
echo   数据库导出工具 - 启动脚本
echo ========================================
echo.

REM 检查jar文件是否存在
if not exist "db-export-tool.jar" (
    if exist "target\db-export-tool-*.jar" (
        for %%f in (target\db-export-tool-*.jar) do (
            copy "%%f" "db-export-tool.jar" >nul
        )
    ) else (
        echo 错误：找不到 db-export-tool.jar
        echo 请先运行 install.bat 编译项目
        pause
        exit /b 1
    )
)

REM 创建必要的目录
if not exist "exports" mkdir exports
if not exist "templates" mkdir templates
if not exist "logs" mkdir logs
if not exist "temp" mkdir temp

echo 正在启动应用...
echo 访问地址：http://localhost:8080
echo 默认账号：admin / 123456
echo.
echo 按 Ctrl+C 停止应用
echo ========================================
echo.

REM 启动应用
java -jar db-export-tool.jar

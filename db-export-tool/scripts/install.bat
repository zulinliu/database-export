@echo off
echo ========================================
echo   数据库导出工具 - 安装脚本
echo ========================================
echo.

REM 创建必要的目录
echo [1/3] 创建目录...
if not exist "exports" mkdir exports
if not exist "templates" mkdir templates
if not exist "logs" mkdir logs
if not exist "temp" mkdir temp
echo 目录创建完成
echo.

REM 使用Maven编译打包
echo [2/3] 编译项目...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo 编译失败，请检查Maven和JDK配置
    pause
    exit /b 1
)
echo 编译完成
echo.

REM 复制jar包
echo [3/3] 准备运行文件...
for %%f in (target\db-export-tool-*.jar) do (
    copy "%%f" "db-export-tool.jar" >nul
)
echo 安装完成！
echo.
echo 运行 start.bat 启动应用
pause

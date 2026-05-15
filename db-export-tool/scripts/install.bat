@echo off
chcp 65001 > nul
echo ========================================
echo 数据库导出工具 V2.0 - 安装脚本
echo ========================================
echo.

set PROJECT_DIR=%~dp0..
cd /d "%PROJECT_DIR%"

echo 检查环境...
echo ----------------------------------------
where java >nul 2>nul
if errorlevel 1 (
    echo 错误: 未找到 Java，请先安装 JDK 8 或更高版本
    exit /b 1
)
java -version 2>&1 | findstr "version"
echo.

where mvn >nul 2>nul
if errorlevel 1 (
    echo 错误: 未找到 Maven，请先安装 Maven 3.6+
    exit /b 1
)
echo.

echo 创建目录结构...
echo ----------------------------------------
if not exist "%PROJECT_DIR%\exports" mkdir "%PROJECT_DIR%\exports"
if not exist "%PROJECT_DIR%\templates" mkdir "%PROJECT_DIR%\templates"
if not exist "%PROJECT_DIR%\logs" mkdir "%PROJECT_DIR%\logs"
if not exist "%PROJECT_DIR%\temp" mkdir "%PROJECT_DIR%\temp"
echo 完成
echo.

echo 编译项目...
echo ----------------------------------------
call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo 错误: Maven 编译失败
    exit /b 1
)
echo 完成
echo.

echo 检查 JAR 文件...
echo ----------------------------------------
if not exist "%PROJECT_DIR%\target\db-export-tool.jar" (
    echo 错误: 编译产物不存在
    exit /b 1
)
for %%F in ("%PROJECT_DIR%\target\db-export-tool.jar") do echo JAR: %%~nxF (%%~zF bytes)
echo.

echo ========================================
echo 安装完成！
echo ========================================
echo.
echo 下一步: 运行 scripts\start.bat 启动服务
echo.
pause

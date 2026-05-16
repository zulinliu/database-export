@echo off
chcp 65001 >nul
echo 停止数据库导出工具...
for /f "tokens=1" %%p in ('jps -l ^| findstr "db-export-tool"') do (
    taskkill /pid %%p /f
    echo 已停止进程: %%p
)
echo 完成

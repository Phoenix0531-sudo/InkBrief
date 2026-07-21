@echo off
cd /d D:\3_Code_Projects\InkBrief

echo ========================================
echo   InkBrief — 日更流水线
echo ========================================

set INKBRIEF_TOKEN=dev-token

if "%1"=="stop" (
  uv run python pipeline.py --stop
  goto :eof
)

if "%1"=="backend" (
  uv run python pipeline.py --backend
  goto :eof
)

if "%1"=="verify" (
  uv run python pipeline.py --verify
  goto :eof
)

REM 默认：起后端 + 跑 Horizon + 验证今日卡组
uv run python pipeline.py
if errorlevel 1 (
  echo.
  echo [!] 流水线失败，查看 pipeline.log / backend.log
  exit /b 1
)

echo.
echo 完成。今日卡组: http://127.0.0.1:8720/v1/cards/today
echo Token 头: X-InkBrief-Token: dev-token
echo ========================================

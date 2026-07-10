@echo off
cd /d D:\3_Code_Projects\InkBrief

echo ========================================
echo   InkBrief — 启动中
echo ========================================

:: Set portable JDK
set JAVA_HOME=D:\3_Code_Projects\InkBrief\tools\jdk-11.0.31+11
set PATH=%JAVA_HOME%\bin;%PATH%

echo [1/3] Starting InkBrief Backend...
cd backend
start /B uv run python app.py > ..\backend.log 2>&1
echo       PID: %ERRORLEVEL%
timeout /t 3 /nobreak > nul

echo [2/3] Verifying...
curl -s http://127.0.0.1:8720/v1/health > nul
if %ERRORLEVEL% EQU 0 (
    echo       Backend ready on http://127.0.0.1:8720
) else (
    echo       WARNING: Backend may not be ready yet
)

echo [3/3] Ready.
echo.
echo   Backend : http://127.0.0.1:8720
echo   Health  : http://127.0.0.1:8720/v1/health
echo   Cards   : http://127.0.0.1:8720/v1/cards/today
echo.
echo   To stop: taskkill /F /IM python.exe
echo.
echo   Running in background. Close this window to keep it running.
echo ========================================

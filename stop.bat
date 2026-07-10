@echo off
echo Stopping InkBrief Backend...
taskkill /F /FI "WINDOWTITLE eq python*" 2>nul
taskkill /F /FI "IMAGENAME eq python.exe" 2>nul
echo Done.

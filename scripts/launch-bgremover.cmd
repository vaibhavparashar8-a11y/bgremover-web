@echo off
rem One-click start: brings up any service that isn't already running, waits
rem for the backend to be healthy, then opens the app in the default browser.
title BGRemover launcher
echo Starting BGRemover...

netstat -ano | findstr "LISTENING" | findstr ":8000 " >nul
if errorlevel 1 start "BGRemover inference" cmd /k E:\Projects\BGRemover\start-inference.cmd

netstat -ano | findstr "LISTENING" | findstr ":8080 " >nul
if errorlevel 1 start "BGRemover backend" cmd /k E:\Projects\BGRemover\start-backend.cmd

echo Waiting for the app to come up (first start takes ~20-30s)...
set /a tries=0
:wait
curl -s -o nul --max-time 2 http://127.0.0.1:8080/api/models
if not errorlevel 1 goto open
set /a tries+=1
if %tries% geq 60 goto failed
rem ping-based sleep: works even where 'timeout' rejects non-interactive input
ping -n 3 127.0.0.1 >nul
goto wait

:open
echo Ready - opening browser.
start "" http://localhost:8080
exit /b 0

:failed
echo BGRemover did not come up within 2 minutes.
echo Check the "BGRemover inference" and "BGRemover backend" windows for errors.
pause
exit /b 1

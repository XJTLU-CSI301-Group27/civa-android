@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-local.ps1" %*
set "launch_exit=%errorlevel%"
if not "%launch_exit%"=="0" echo Startup failed. See the message above.
pause
exit /b %launch_exit%

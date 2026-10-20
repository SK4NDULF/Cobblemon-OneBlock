@echo off
rem Cobblemon OneBlock test server - double-click to start.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start.ps1"
pause

@echo off
rem Double-clickable wrapper so installing does not require knowing about PowerShell
rem execution policy. Runs per-user; no administrator rights are needed.
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install-Solisium.ps1" %*
if errorlevel 1 (
  echo.
  echo Install did not complete. See the message above.
)
echo.
pause
endlocal

@echo off
rem Solisium Autopilot — run the MSI in this folder (per-user install, bundled Java).
setlocal
cd /d "%~dp0"
for %%F in (*.msi) do (
  echo Launching installer: %%F
  start "" "%%~fF"
  echo.
  echo Follow the setup wizard. If Windows SmartScreen appears, choose Run anyway
  echo when you trust this download.
  exit /b 0
)
echo No .msi found in this folder. Extract the full zip first.
pause
exit /b 1

@echo off
rem TL Route Investigator — run the bundled NSIS installer (no admin required for per-user install).
setlocal
cd /d "%~dp0"
for %%F in (*-setup.exe) do (
  echo Launching %%F ...
  start "" "%%~fF"
  exit /b 0
)
for %%F in (*.msi) do (
  echo Launching %%F ...
  start "" "%%~fF"
  exit /b 0
)
echo Could not find *-setup.exe or .msi in this folder.
echo Extract the full zip first, then run this script again.
pause
exit /b 1

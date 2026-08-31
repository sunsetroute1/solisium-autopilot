@echo off
rem Rebuilds the full release zips from the .partNN files in this folder.
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\packaging\Join-Release.ps1" -ReleaseDir "%~dp0"
if errorlevel 1 (
    echo.
    echo Could not join the release parts.
    pause
    exit /b 1
)
echo.
pause
endlocal

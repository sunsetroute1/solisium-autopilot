<#
    Per-user installer for Solisium Autopilot.

    Installs to %LOCALAPPDATA%\Programs so it needs no administrator rights, and copies
    only the application image. It deliberately never creates, moves, or looks for a key:
    keys live in %LOCALAPPDATA%\Solisium\secrets.properties, which this script does not
    touch, so installing, reinstalling, and uninstalling all leave a key untouched and
    none of them can carry one into the install folder.
#>
[CmdletBinding()]
param(
    [string] $InstallRoot = (Join-Path $env:LOCALAPPDATA 'Programs'),
    [switch] $NoShortcuts,
    [switch] $Uninstall
)

$ErrorActionPreference = 'Stop'
$AppName = 'Solisium Autopilot'
$target = Join-Path $InstallRoot $AppName
$exe = Join-Path $target "$AppName.exe"
$startMenu = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Solisium'
$startMenuLink = Join-Path $startMenu "$AppName.lnk"
$desktopLink = Join-Path ([Environment]::GetFolderPath('Desktop')) "$AppName.lnk"

function Stop-IfRunning {
    $running = Get-Process -Name $AppName.Replace(' ', '*') -ErrorAction SilentlyContinue |
        Where-Object { $_.Path -and $_.Path.StartsWith($target, [StringComparison]::OrdinalIgnoreCase) }
    if ($running) {
        Write-Host "$AppName is running. Close it and run this again." -ForegroundColor Yellow
        exit 1
    }
}

function New-Shortcut([string] $LinkPath, [string] $TargetPath) {
    New-Item -ItemType Directory -Force -Path (Split-Path $LinkPath) | Out-Null
    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut($LinkPath)
    $shortcut.TargetPath = $TargetPath
    $shortcut.WorkingDirectory = Split-Path $TargetPath
    $shortcut.Description = 'Read-only Throne and Liberty companion'
    $shortcut.Save()
}

if ($Uninstall) {
    Stop-IfRunning
    foreach ($path in @($startMenuLink, $desktopLink)) {
        if (Test-Path $path) { Remove-Item $path -Force }
    }
    if (Test-Path $startMenu) {
        if (-not (Get-ChildItem $startMenu -Force)) { Remove-Item $startMenu -Force }
    }
    if (Test-Path $target) { Remove-Item $target -Recurse -Force }
    Write-Host "Removed $AppName." -ForegroundColor Green
    Write-Host 'Your data and any stored key were left alone:' -ForegroundColor DarkGray
    Write-Host "  $(Join-Path $env:USERPROFILE '.solisium')" -ForegroundColor DarkGray
    Write-Host "  $(Join-Path $env:LOCALAPPDATA 'Solisium')" -ForegroundColor DarkGray
    exit 0
}

$source = Join-Path $PSScriptRoot $AppName
if (-not (Test-Path (Join-Path $source "$AppName.exe"))) {
    Write-Host "Could not find '$AppName\$AppName.exe' next to this script." -ForegroundColor Red
    Write-Host 'Extract the whole zip first, then run this from the extracted folder.' -ForegroundColor Red
    exit 1
}

Stop-IfRunning

# Replace wholesale rather than merging, so a stale file from an older version cannot
# survive an upgrade.
if (Test-Path $target) {
    Write-Host "Replacing the existing install at $target"
    Remove-Item $target -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $InstallRoot | Out-Null
Copy-Item $source $target -Recurse -Force

if (-not (Test-Path $exe)) {
    Write-Host 'Copy finished but the executable is missing. Nothing was installed.' -ForegroundColor Red
    exit 1
}

if (-not $NoShortcuts) {
    New-Shortcut $startMenuLink $exe
    New-Shortcut $desktopLink $exe
}

Write-Host ''
Write-Host "$AppName installed." -ForegroundColor Green
Write-Host "  Location:   $target"
if (-not $NoShortcuts) { Write-Host '  Shortcuts:  Start Menu (Solisium) and Desktop' }
Write-Host ''
Write-Host 'No key is bundled with this build. To add one:' -ForegroundColor DarkGray
Write-Host '  open the app, go to Data, and use the key finder' -ForegroundColor DarkGray
Write-Host '  (it is stored in %LOCALAPPDATA%\Solisium, never inside this folder)' -ForegroundColor DarkGray
Write-Host ''
Write-Host "Uninstall with: .\Install-Solisium.ps1 -Uninstall" -ForegroundColor DarkGray

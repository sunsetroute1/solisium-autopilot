<#
    Rebuild pipeline

    Packages the Windows MSI + portable zips, splits them into git-sized
    .partNN files, and reinstalls the local per-user copy.

    Does not commit or push. After it finishes, add the new releases\*.partNN
    files and bump notes, then push master and tag vX.Y.Z.

    Usage (from the repo root):

        .\packaging\Rebuild-Pipeline.ps1
        .\packaging\Rebuild-Pipeline.ps1 -SkipLaunch
        .\packaging\Rebuild-Pipeline.ps1 -InstallOnly
#>
[CmdletBinding()]
param(
    [switch] $InstallOnly,
    [switch] $SkipInstall,
    [switch] $SkipLaunch
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

$appName = 'Solisium Autopilot'
$gradle = Join-Path $root 'gradlew.bat'
$image = Join-Path $root "desktopApp\build\compose\binaries\main\app\$appName"
$installScript = Join-Path $PSScriptRoot 'Install-Solisium.ps1'
$helperScript = Join-Path $PSScriptRoot 'Install-TLHelper.ps1'

function Stop-Solisium {
    Get-Process -ErrorAction SilentlyContinue |
        Where-Object {
            $_.ProcessName -like '*Solisium*' -or
            ($_.Path -and $_.Path -like '*Solisium Autopilot*')
        } |
        Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
}

if (-not (Test-Path $gradle)) {
    throw "gradlew.bat not found at $gradle"
}

Stop-Solisium

if ($InstallOnly) {
    Write-Host 'Rebuild pipeline: install-only (existing app image).'
    & $gradle ':desktopApp:createDistributable'
    if ($LASTEXITCODE -ne 0) { throw "createDistributable failed with exit $LASTEXITCODE" }
} else {
    Write-Host 'Rebuild pipeline: packageRelease (MSI, zips, .partNN).'
    & $gradle ':desktopApp:packageRelease'
    if ($LASTEXITCODE -ne 0) { throw "packageRelease failed with exit $LASTEXITCODE" }
}

if (-not (Test-Path (Join-Path $image "$appName.exe"))) {
    throw "App image missing: $image"
}

if (-not $SkipInstall) {
    $staging = Join-Path $env:TEMP 'solisium-rebuild-pipeline'
    if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
    New-Item -ItemType Directory -Path $staging | Out-Null
    Copy-Item -Recurse $image $staging
    Copy-Item $installScript $staging
    if (Test-Path $helperScript) { Copy-Item $helperScript $staging }
    Write-Host 'Rebuild pipeline: installing local copy.'
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $staging 'Install-Solisium.ps1')
    if ($LASTEXITCODE -ne 0) { throw "local install failed with exit $LASTEXITCODE" }
}

$exe = Join-Path $env:LOCALAPPDATA "Programs\$appName\$appName.exe"
if (-not $SkipLaunch -and -not $SkipInstall -and (Test-Path $exe)) {
    Start-Process $exe -WorkingDirectory (Split-Path $exe)
    Write-Host "Rebuild pipeline: launched $exe"
}

Write-Host 'Rebuild pipeline finished.'
Write-Host '  Next: commit version notes + releases\*.partNN, push master, tag vX.Y.Z'

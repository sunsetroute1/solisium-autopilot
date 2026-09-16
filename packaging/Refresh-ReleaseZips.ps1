<#
    Adds install.cmd + START-HERE.txt to existing release zips (no full rebuild).
    Use after pulling packaging fixes or when zips were built without wrappers.

    .\packaging\Refresh-ReleaseZips.ps1
    .\packaging\Refresh-ReleaseZips.ps1 -ZipDir $env:LOCALAPPDATA\solisium-release-backup
#>
[CmdletBinding()]
param(
    [string] $ZipDir = ''
)

if (-not $ZipDir) {
    $root = Split-Path $PSScriptRoot -Parent
    $ZipDir = Join-Path $root 'dist\windows-releases'
}

$ErrorActionPreference = 'Stop'
$packaging = $PSScriptRoot
if (-not (Test-Path $ZipDir)) { throw "Missing directory: $ZipDir" }

function Update-Zip([string] $zipPath) {
    $name = Split-Path $zipPath -Leaf
    $isInstaller = $name -match '-installer\.zip$'
    $isPortable = $name -match '-portable\.zip$'
    if (-not $isInstaller -and -not $isPortable) { return }

    Write-Host "Updating $name"
    $temp = Join-Path $env:TEMP ("solisium-zip-refresh-" + [guid]::NewGuid().ToString('n'))
    New-Item -ItemType Directory -Path $temp | Out-Null
    try {
        Expand-Archive -Path $zipPath -DestinationPath $temp -Force
        Copy-Item (Join-Path $packaging 'START-HERE.txt') (Join-Path $temp 'START-HERE.txt') -Force
        Copy-Item (Join-Path $packaging 'README-INSTALL.txt') (Join-Path $temp 'README-INSTALL.txt') -Force
        if ($isInstaller) {
            Copy-Item (Join-Path $packaging 'install-msi.cmd') (Join-Path $temp 'install.cmd') -Force
            if (Test-Path (Join-Path $packaging 'Install-TLHelper.ps1')) {
                Copy-Item (Join-Path $packaging 'Install-TLHelper.ps1') $temp -Force
            }
        }
        if ($isPortable) {
            foreach ($f in @('install.cmd', 'Install-Solisium.ps1', 'Install-TLHelper.ps1')) {
                $src = Join-Path $packaging $f
                if (Test-Path $src) { Copy-Item $src (Join-Path $temp $f) -Force }
            }
        }
        Remove-Item $zipPath -Force
        Compress-Archive -Path (Join-Path $temp '*') -DestinationPath $zipPath -Force
    } finally {
        Remove-Item $temp -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Get-ChildItem $ZipDir -Filter 'Solisium-Autopilot-*.zip' -File | ForEach-Object { Update-Zip $_.FullName }
Write-Host 'Done.'

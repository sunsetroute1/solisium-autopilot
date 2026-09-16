<#
    Builds an easy-to-use installer zip for TL Route Investigator.

    Output (default): releases/TL-Route-Investigator-<version>-Install.zip
    - START-HERE.txt, install.cmd, README-INSTALL.txt, setup.exe, optional MSI

    If the zip exceeds 50 MB, also writes 45 MB .partNN files for git (Solisium pattern).
    Small releases commit the single -Install.zip directly (no parts needed).
#>
[CmdletBinding()]
param(
    [string] $Version = "0.1.1",
    [switch] $SkipBuild,
    [switch] $DesktopCopy
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
$appDir = Join-Path $root 'tools\tl-route-investigator'
$releaseDir = Join-Path $root 'releases'
$bundle = Join-Path $appDir "src-tauri\target\release\bundle"
$zipName = "TL-Route-Investigator-$Version-Install.zip"
$zipPath = Join-Path $releaseDir $zipName
$partBytes = 45MB
$splitThresholdBytes = 50MB

if (-not $SkipBuild) {
    $env:Path = "$env:USERPROFILE\.cargo\bin;" + $env:Path
    Push-Location $appDir
    try {
        npm run tauri build
    } finally {
        Pop-Location
    }
}

$msi = Get-ChildItem (Join-Path $bundle 'msi') -Filter '*.msi' -ErrorAction SilentlyContinue | Select-Object -First 1
$setup = Get-ChildItem (Join-Path $bundle 'nsis') -Filter '*-setup.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $msi -and -not $setup) {
    throw "No MSI or NSIS setup found under $bundle. Run npm run tauri build first."
}

New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null
$staging = Join-Path $env:TEMP "tl-route-release-$Version"
Remove-Item $staging -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $staging | Out-Null
Copy-Item (Join-Path $PSScriptRoot 'START-HERE.txt') (Join-Path $staging 'START-HERE.txt')
Copy-Item (Join-Path $PSScriptRoot 'install.cmd') (Join-Path $staging 'install.cmd')
Copy-Item (Join-Path $PSScriptRoot 'README-INSTALL.txt') (Join-Path $staging 'README-INSTALL.txt')
if ($setup) { Copy-Item $setup.FullName (Join-Path $staging $setup.Name) }
if ($msi) { Copy-Item $msi.FullName (Join-Path $staging $msi.Name) }

Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $zipPath -Force
Remove-Item $staging -Recurse -Force

$legacyPartPrefix = "TL-Route-Investigator-$Version-windows-x64-installer.zip"
Get-ChildItem $releaseDir -File | Where-Object {
    $_.Name -match ([regex]::Escape($legacyPartPrefix) + '\.part\d+$') -or
    $_.Name -eq $legacyPartPrefix -or
    $_.Name -eq ($legacyPartPrefix + '.part01')
} | Remove-Item -Force -ErrorAction SilentlyContinue

Get-ChildItem $releaseDir -File | Where-Object { $_.Name -match ([regex]::Escape($zipName) + '\.part\d+$') } | Remove-Item -Force

$zipLen = (Get-Item $zipPath).Length
$zipMb = [math]::Round($zipLen / 1MB, 1)
Write-Host "Created $zipName (${zipMb} MB)"

if ($zipLen -gt $splitThresholdBytes) {
    $input = [System.IO.File]::OpenRead($zipPath)
    try {
        $index = 1
        $buffer = New-Object byte[] (1MB)
        $eof = $false
        while (-not $eof) {
            $partPath = Join-Path $releaseDir ("{0}.part{1:D2}" -f $zipName, $index)
            $out = [System.IO.File]::Create($partPath)
            $written = 0L
            try {
                while ($written -lt $partBytes) {
                    $want = [Math]::Min($buffer.Length, [int]($partBytes - $written))
                    $n = $input.Read($buffer, 0, $want)
                    if ($n -le 0) { $eof = $true; break }
                    $out.Write($buffer, 0, $n)
                    $written += $n
                }
            } finally {
                $out.Dispose()
            }
            if ($written -eq 0) {
                Remove-Item $partPath -Force -ErrorAction SilentlyContinue
                break
            }
            Write-Host "  part: $(Split-Path $partPath -Leaf)"
            $index++
        }
    } finally {
        $input.Dispose()
    }
    Write-Host "Zip is over 50 MB; git should track .partNN files (full zip is gitignored)."
} else {
    Write-Host "Small enough to commit the single -Install.zip to git (no parts)."
}

if ($DesktopCopy) {
    $desk = [Environment]::GetFolderPath('Desktop')
    $dest = Join-Path $desk $zipName
    Copy-Item $zipPath $dest -Force
    Write-Host "Copied to Desktop: $dest"
}

Write-Host ""
Write-Host "To install: extract the zip, double-click install.cmd"

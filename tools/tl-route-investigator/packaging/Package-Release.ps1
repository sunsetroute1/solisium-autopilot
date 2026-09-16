<#
    Builds TL Route Investigator release zips in repo releases/ and splits them
    into 45 MB parts for GitHub (same pattern as Solisium Autopilot).
#>
[CmdletBinding()]
param(
    [string] $Version = "0.1.1",
    [switch] $SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
$appDir = Join-Path $root 'tools\tl-route-investigator'
$releaseDir = Join-Path $root 'releases'
$bundle = Join-Path $appDir "src-tauri\target\release\bundle"
$zipBase = "TL-Route-Investigator-$Version-windows-x64-installer.zip"
$zipPath = Join-Path $releaseDir $zipBase
$partBytes = 45MB

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
Copy-Item (Join-Path $PSScriptRoot 'README-INSTALL.txt') (Join-Path $staging 'README-INSTALL.txt')
if ($setup) { Copy-Item $setup.FullName (Join-Path $staging $setup.Name) }
if ($msi) { Copy-Item $msi.FullName (Join-Path $staging $msi.Name) }

Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $zipPath -Force
Remove-Item $staging -Recurse -Force

Get-ChildItem $releaseDir -File | Where-Object { $_.Name -match [regex]::Escape($zipBase) + '\.part\d+$' } | Remove-Item -Force

$input = [System.IO.File]::OpenRead($zipPath)
try {
    $index = 1
    $buffer = New-Object byte[] (1MB)
    $eof = $false
    while (-not $eof) {
        $partPath = Join-Path $releaseDir ("{0}.part{1:D2}" -f $zipBase, $index)
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
        $mb = [math]::Round($written / 1MB, 1)
        Write-Host "Wrote $(Split-Path $partPath -Leaf) (${mb} MB)"
        $index++
    }
} finally {
    $input.Dispose()
}

$zipMb = [math]::Round((Get-Item $zipPath).Length / 1MB, 1)
Write-Host "Installer zip: ${zipMb} MB -> releases\$zipBase.partNN"

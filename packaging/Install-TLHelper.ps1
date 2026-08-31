<#
    Installs a TL-Helper checkout next to Solisium so extract can run.

    Prefers a copy already bundled in the Solisium app image, then downloads
    https://github.com/sunsetroute1/tl-helper . Keys and local config are never
    copied. A key already stored in %LOCALAPPDATA%\Solisium is left untouched.
#>
[CmdletBinding()]
param(
    [string] $InstallRoot = (Join-Path $env:LOCALAPPDATA 'Programs\TL-Helper'),
    [string] $SearchRoot,
    [string] $ArchiveUrl = 'https://github.com/sunsetroute1/tl-helper/archive/refs/heads/master.zip'
)

$ErrorActionPreference = 'Stop'

$skipDirs = @('.git', 'node_modules', 'bin', 'obj', 'out', 'tools', '.claude', '.wrangler')
$skipFiles = @('.env', '.env.local', 'config.local.json', 'aes.txt', 'aes.key', 'secrets.properties', 'source-manifest.json')

function Test-Checkout([string] $Path) {
    return Test-Path (Join-Path $Path 'scripts\update-tl-helper.mjs')
}

function Write-Remembered([string] $Path) {
    $solisiumHome = Join-Path $env:USERPROFILE '.solisium'
    New-Item -ItemType Directory -Force -Path $solisiumHome | Out-Null
    Set-Content -Path (Join-Path $solisiumHome 'tl-helper-root.txt') -Value $Path -NoNewline
}

function Copy-Checkout([string] $From, [string] $To) {
    if (Test-Path $To) {
        if (Test-Checkout $To) { return }
        throw "Refusing to overwrite $To; it is not a TL-Helper checkout."
    }
    New-Item -ItemType Directory -Force -Path $To | Out-Null
    $xd = ($skipDirs | ForEach-Object { "/XD"; $_ })
    $xf = ($skipFiles | ForEach-Object { "/XF"; $_ })
    $args = @($From, $To, '/E') + $xd + $xf + @('/NFL', '/NDL', '/NJH', '/NJS', '/nc', '/ns', '/np')
    & robocopy @args | Out-Null
    $code = $LASTEXITCODE
    if ($code -ge 8) {
        throw "Could not copy TL-Helper from $From (robocopy $code)."
    }
}

function Find-Bundled([string] $Root) {
    if (-not $Root -or -not (Test-Path $Root)) { return $null }
    $direct = Join-Path $Root 'app\resources\tl-helper'
    if (Test-Checkout $direct) { return $direct }
    $hit = Get-ChildItem -Path $Root -Recurse -Filter 'update-tl-helper.mjs' -ErrorAction SilentlyContinue |
        Where-Object { $_.Directory.Name -eq 'scripts' } |
        Select-Object -First 1
    if ($hit) { return $hit.Directory.Parent.FullName }
    return $null
}

if (Test-Checkout $InstallRoot) {
    Write-Remembered $InstallRoot
    Write-Host "TL-Helper already at $InstallRoot"
    return $InstallRoot
}

$bundled = $null
if ($SearchRoot) { $bundled = Find-Bundled $SearchRoot }
if (-not $bundled) {
    foreach ($candidate in @(
        (Join-Path $PSScriptRoot 'Solisium Autopilot'),
        (Join-Path $env:LOCALAPPDATA 'Programs\Solisium Autopilot')
    )) {
        $bundled = Find-Bundled $candidate
        if ($bundled) { break }
    }
}

if ($bundled) {
    Write-Host "Installing TL-Helper from the Solisium image..."
    Copy-Checkout $bundled $InstallRoot
} else {
    Write-Host "Downloading TL-Helper from GitHub..."
    $scratch = Join-Path $env:TEMP ("solisium-tl-helper-" + [guid]::NewGuid().ToString('n'))
    New-Item -ItemType Directory -Force -Path $scratch | Out-Null
    $zip = Join-Path $scratch 'tl-helper.zip'
    Invoke-WebRequest -Uri $ArchiveUrl -OutFile $zip -UseBasicParsing
    Expand-Archive -Path $zip -DestinationPath (Join-Path $scratch 'unpacked') -Force
    $inner = Get-ChildItem (Join-Path $scratch 'unpacked') -Directory |
        Where-Object { Test-Checkout $_.FullName } |
        Select-Object -First 1
    if (-not $inner) {
        throw "Downloaded zip from $ArchiveUrl has no scripts\update-tl-helper.mjs."
    }
    Copy-Checkout $inner.FullName $InstallRoot
}

if (-not (Test-Checkout $InstallRoot)) {
    throw "Install finished but $InstallRoot is not a TL-Helper checkout."
}

Write-Remembered $InstallRoot
Write-Host "TL-Helper installed at $InstallRoot"
return $InstallRoot

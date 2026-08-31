<#
    Rebuilds the Windows release zips from the .partNN files in releases/.

    GitHub rejects a single file over 100 MB, so packageRelease splits each zip
    into 45 MB pieces that can be committed. This script concatenates them in
    order and checks the zip magic bytes.
#>
[CmdletBinding()]
param(
    [string] $ReleaseDir = (Join-Path $PSScriptRoot '..\releases')
)

$ErrorActionPreference = 'Stop'
$ReleaseDir = (Resolve-Path $ReleaseDir).Path

function Join-Parts([string] $ZipName) {
    $zipPath = Join-Path $ReleaseDir $ZipName
    $parts = Get-ChildItem -Path $ReleaseDir -File |
        Where-Object { $_.Name -match [regex]::Escape($ZipName) + '\.part\d+$' } |
        Sort-Object Name
    if (-not $parts) {
        throw "No parts found for $ZipName in $ReleaseDir"
    }
    Write-Host "Joining $($parts.Count) parts into $ZipName"
    $out = [System.IO.File]::Create($zipPath)
    try {
        foreach ($part in $parts) {
            Write-Host "  $($part.Name)"
            $in = [System.IO.File]::OpenRead($part.FullName)
            try { $in.CopyTo($out) } finally { $in.Dispose() }
        }
    } finally {
        $out.Dispose()
    }
    $header = [byte[]]::new(2)
    $stream = [System.IO.File]::OpenRead($zipPath)
    try {
        if ($stream.Read($header, 0, 2) -ne 2 -or $header[0] -ne 0x50 -or $header[1] -ne 0x4B) {
            throw "$ZipName does not look like a zip. A part may be missing."
        }
    } finally {
        $stream.Dispose()
    }
    Write-Host ("  {0:N1} MB" -f ((Get-Item $zipPath).Length / 1MB))
    return $zipPath
}

$joined = @(
    Get-ChildItem -Path $ReleaseDir -File |
        Where-Object { $_.Name -match '\.zip\.part01$' } |
        ForEach-Object { $_.Name -replace '\.part01$', '' } |
        Select-Object -Unique
)
if (-not $joined) {
    throw "No *.zip.part01 files in $ReleaseDir"
}

$paths = foreach ($name in $joined) { Join-Parts $name }
Write-Host ''
Write-Host 'Ready. Extract a zip and run install.cmd (portable) or the .msi.'
$paths

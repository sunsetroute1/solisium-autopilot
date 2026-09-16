<#
    Upload dist/windows-releases/*.zip to a GitHub Release (requires `gh auth login`).

    Example:
      .\packaging\Publish-GitHubRelease.ps1 -Tag v0.1.18 -Title "Solisium Autopilot 0.1.18"
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Tag,
    [string] $Title,
    [string] $DistDir = (Join-Path (Split-Path $PSScriptRoot -Parent) 'dist\windows-releases')
)

$ErrorActionPreference = 'Stop'
$Title = if ($Title) { $Title } else { $Tag }
$gh = Get-Command gh -ErrorAction SilentlyContinue
if (-not $gh) {
    throw "GitHub CLI (gh) not found. Install from https://cli.github.com/ and run gh auth login."
}
if (-not (Test-Path $DistDir)) {
    throw "Missing $DistDir — run packageRelease or Package-Release.ps1 first."
}
$zips = Get-ChildItem $DistDir -Filter '*.zip' -File
if (-not $zips) {
    throw "No .zip files in $DistDir"
}
gh release view $Tag 2>$null
if ($LASTEXITCODE -ne 0) {
    gh release create $Tag --title $Title --notes "Windows installers (not stored in git)."
} else {
    Write-Host "Release $Tag exists; uploading assets..."
}
foreach ($z in $zips) {
    gh release upload $Tag $z.FullName --clobber
}
Write-Host "Done: https://github.com/sunsetroute1/solisium-autopilot/releases/tag/$Tag"

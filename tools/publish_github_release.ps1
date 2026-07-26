param(
    [Parameter(Mandatory = $true)][string]$Tag,
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][string]$Title,
    [Parameter(Mandatory = $true)][string]$NotesFile,
    [string]$Repository = "yuelangmanle/plankton"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI (gh) is required. Run: gh auth login"
}
if (-not (Test-Path -LiteralPath $ApkPath)) { throw "APK not found: $ApkPath" }
if (-not (Test-Path -LiteralPath $NotesFile)) { throw "Release notes not found: $NotesFile" }
if ($Tag -notmatch '^v\d+(\.\d+){1,3}$') { throw "Tag must be a stable semantic version, for example v6.9" }

& gh release view $Tag --repo $Repository 2>$null
if ($LASTEXITCODE -eq 0) { throw "Release $Tag already exists; refusing to overwrite it." }

& gh release create $Tag $ApkPath --repo $Repository --title $Title --notes-file $NotesFile
if ($LASTEXITCODE -ne 0) { throw "GitHub Release creation failed." }

Write-Host "Created GitHub Release $Tag and uploaded $(Split-Path -Leaf $ApkPath)."

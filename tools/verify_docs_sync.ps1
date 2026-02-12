param(
    [string]$RepoRoot = (Resolve-Path "$PSScriptRoot\..").Path
)

$ErrorActionPreference = "Stop"

function Get-RegexValue {
    param(
        [string]$Text,
        [string]$Pattern
    )
    $m = [regex]::Match($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)
    if (-not $m.Success) { return $null }
    return $m.Groups[1].Value
}

function Assert-Eq {
    param(
        [string]$Name,
        [string]$Expected,
        [string]$Actual
    )
    if ($Expected -ne $Actual) {
        throw "$Name mismatch. expected=[$Expected], actual=[$Actual]"
    }
}

$mainGradle = Get-Content "$RepoRoot\android\app\build.gradle.kts" -Raw -Encoding UTF8
$voiceGradle = Get-Content "$RepoRoot\voice_assistant\app\build.gradle.kts" -Raw -Encoding UTF8

$mainName = Get-RegexValue $mainGradle 'versionName\s*=\s*"([^"]+)"'
$mainCode = Get-RegexValue $mainGradle 'versionCode\s*=\s*([0-9]+)'
$voiceName = Get-RegexValue $voiceGradle 'versionName\s*=\s*"([^"]+)"'
$voiceCode = Get-RegexValue $voiceGradle 'versionCode\s*=\s*([0-9]+)'

if (-not $mainName -or -not $mainCode -or -not $voiceName -or -not $voiceCode) {
    throw "Failed to parse version from gradle files."
}

$prompt = Get-Content "$RepoRoot\docs\新窗口提示词.md" -Raw -Encoding UTF8
$current = Get-Content "$RepoRoot\docs\当前程序情况.md" -Raw -Encoding UTF8
$nativeBook = Get-Content "$RepoRoot\docs\原生安卓项目书.md" -Raw -Encoding UTF8
$voiceBook = Get-Content "$RepoRoot\docs\语音助手项目书.md" -Raw -Encoding UTF8

$promptMain = Get-RegexValue $prompt '当前版本：`([0-9]+\.[0-9]+)\s+\(versionCode=([0-9]+)\)`'
if (-not $promptMain) {
    # fallback for spacing variants
    $promptMain = Get-RegexValue $prompt '当前版本：`([0-9]+\.[0-9]+)\s*\(versionCode=([0-9]+)\)`'
}

$promptMainName = $null
$promptMainCode = $null
if ($promptMain) {
    $mm = [regex]::Match($prompt, '当前版本：`([0-9]+\.[0-9]+)\s*\(versionCode=([0-9]+)\)`')
    if ($mm.Success) {
        $promptMainName = $mm.Groups[1].Value
        $promptMainCode = $mm.Groups[2].Value
    }
}

$currentMainName = Get-RegexValue $current '当前版本：`([0-9]+\.[0-9]+)`'
$currentMainCode = Get-RegexValue $current 'versionCode=([0-9]+)'
$nativeMainName = Get-RegexValue $nativeBook '当前 App 版本 ([0-9]+\.[0-9]+)'
$voiceDocName = Get-RegexValue $voiceBook '版本：v([0-9]+\.[0-9]+)'

Assert-Eq -Name "docs/新窗口提示词.md versionName" -Expected $mainName -Actual $promptMainName
Assert-Eq -Name "docs/新窗口提示词.md versionCode" -Expected $mainCode -Actual $promptMainCode
Assert-Eq -Name "docs/当前程序情况.md versionName" -Expected $mainName -Actual $currentMainName
Assert-Eq -Name "docs/当前程序情况.md versionCode" -Expected $mainCode -Actual $currentMainCode
Assert-Eq -Name "docs/原生安卓项目书.md main versionName" -Expected $mainName -Actual $nativeMainName
Assert-Eq -Name "docs/语音助手项目书.md voice versionName" -Expected $voiceName -Actual $voiceDocName

Write-Host "Version consistency check passed."
Write-Host "Main: v$mainName ($mainCode)"
Write-Host "Voice: v$voiceName ($voiceCode)"


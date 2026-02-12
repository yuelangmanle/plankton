param(
    [Parameter(Mandatory = $true)][string]$MainVersion,
    [Parameter(Mandatory = $true)][int]$MainCode,
    [Parameter(Mandatory = $true)][string]$VoiceVersion,
    [Parameter(Mandatory = $true)][int]$VoiceCode,
    [string]$Date = (Get-Date -Format "yyyy-MM-dd"),
    [string]$MainNote = "维护：版本同步发布",
    [string]$VoiceNote = "维护：版本同步发布",
    [string]$RepoRoot = (Resolve-Path "$PSScriptRoot\..").Path
)

$ErrorActionPreference = "Stop"

function Replace-InFile {
    param(
        [string]$Path,
        [string]$Pattern,
        [string]$Replacement
    )
    $text = Get-Content $Path -Raw -Encoding UTF8
    $updated = [regex]::Replace($text, $Pattern, $Replacement, [System.Text.RegularExpressions.RegexOptions]::Multiline)
    if ($updated -eq $text) {
        throw "Pattern not found in $Path"
    }
    Set-Content $Path -Value $updated -Encoding UTF8
}

$mainGradle = "$RepoRoot\android\app\build.gradle.kts"
$voiceGradle = "$RepoRoot\voice_assistant\app\build.gradle.kts"
$mainInfo = "$RepoRoot\android\app\src\main\java\com\plankton\one102\ui\AppInfo.kt"
$voiceInfo = "$RepoRoot\voice_assistant\app\src\main\java\com\voiceassistant\ui\AppInfo.kt"

Replace-InFile $mainGradle 'versionCode\s*=\s*[0-9]+' ("versionCode = $MainCode")
Replace-InFile $mainGradle 'versionName\s*=\s*"[^"]+"' ("versionName = `"$MainVersion`"")
Replace-InFile $voiceGradle 'versionCode\s*=\s*[0-9]+' ("versionCode = $VoiceCode")
Replace-InFile $voiceGradle 'versionName\s*=\s*"[^"]+"' ("versionName = `"$VoiceVersion`"")

$mainText = Get-Content $mainInfo -Raw -Encoding UTF8
$mainPrefix = @"
val changeLog: List<String> = listOf(
        "$MainVersion（当前）",
        "- $MainNote",
        "",
"@
$mainText = [regex]::Replace(
    $mainText,
    'val changeLog: List<String> = listOf\(',
    $mainPrefix,
    [System.Text.RegularExpressions.RegexOptions]::Multiline
)
Set-Content $mainInfo -Value $mainText -Encoding UTF8

$voiceText = Get-Content $voiceInfo -Raw -Encoding UTF8
$voiceInsert = @"
val releases = listOf(
        AppRelease(
            versionName = "$VoiceVersion",
            versionCode = $VoiceCode,
            date = "$Date",
            notes = listOf(
                "$VoiceNote",
            ),
        ),
"@
$voiceText = [regex]::Replace(
    $voiceText,
    'val releases = listOf\(',
    $voiceInsert,
    [System.Text.RegularExpressions.RegexOptions]::Multiline
)
Set-Content $voiceInfo -Value $voiceText -Encoding UTF8

Write-Host "Release bump template applied."


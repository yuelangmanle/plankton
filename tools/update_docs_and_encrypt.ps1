param(
    [string]$Password = "dzj92531",
    [string]$RepoRoot = (Resolve-Path "$PSScriptRoot\..").Path
)

$ErrorActionPreference = "Stop"

function Invoke-DocEncryptor {
    param(
        [string]$Password,
        [string]$InputFile,
        [string]$OutputFile
    )

    if (-not (Test-Path -LiteralPath $InputFile)) {
        throw "Input file not found: $InputFile"
    }

    $outDir = Split-Path -Parent $OutputFile
    if ($outDir -and -not (Test-Path -LiteralPath $outDir)) {
        New-Item -ItemType Directory -Path $outDir -Force | Out-Null
    }

    & java -cp (Join-Path $RepoRoot "tools") DocEncryptor $Password $InputFile $OutputFile
    if ($LASTEXITCODE -ne 0) {
        throw "DocEncryptor failed for: $InputFile"
    }
}

Push-Location $RepoRoot
try {
    $javaHome = Join-Path $RepoRoot ".toolchain\jdk\jdk-17.0.13+11"
    if (Test-Path -LiteralPath $javaHome) {
        $env:JAVA_HOME = $javaHome
        $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
    }

    & javac -encoding UTF-8 (Join-Path $RepoRoot "tools\DocEncryptor.java")
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to compile tools\DocEncryptor.java"
    }

    $targets = @(
        @{
            In = Join-Path $RepoRoot "docs\项目书.md"
            Out = Join-Path $RepoRoot "android\app\src\main\assets\docs\项目书.enc"
        },
        @{
            In = Join-Path $RepoRoot "docs\原生安卓项目书.md"
            Out = Join-Path $RepoRoot "android\app\src\main\assets\docs\原生安卓项目书.enc"
        },
        @{
            In = Join-Path $RepoRoot "docs\当前程序情况.md"
            Out = Join-Path $RepoRoot "android\app\src\main\assets\docs\当前程序情况.enc"
        },
        @{
            In = Join-Path $RepoRoot "docs\新窗口提示词.md"
            Out = Join-Path $RepoRoot "android\app\src\main\assets\docs\新窗口提示词.enc"
        },
        @{
            In = Join-Path $RepoRoot "docs\语音助手项目书.md"
            Out = Join-Path $RepoRoot "voice_assistant\app\src\main\assets\docs\语音助手项目书.enc"
        }
    )

    foreach ($t in $targets) {
        Write-Host "Encrypting: $($t.In)"
        Invoke-DocEncryptor -Password $Password -InputFile $t.In -OutputFile $t.Out
    }

    Write-Host "All docs encrypted successfully."
}
finally {
    Pop-Location
}

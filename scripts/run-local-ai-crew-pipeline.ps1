[CmdletBinding()]
param(
    [switch]$SkipPortraits,
    [switch]$SkipVoice,
    [switch]$SkipQuality,
    [switch]$Overwrite,
    [string]$SdApiUrl = "http://127.0.0.1:7860",
    [string]$VoiceConfigPath = "assets/ai_pipeline/local_tts_voices.json",
    [string]$PiperExe = "piper",
    [string]$FfmpegExe = "ffmpeg"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Push-Location $repoRoot
try {
    if (-not $SkipPortraits) {
        Write-Host "[crew-ai] generating portraits..."
        $portraitScript = Join-Path $PSScriptRoot "generate-local-crew-portraits.ps1"
        if ($Overwrite) {
            & $portraitScript -SdApiUrl $SdApiUrl -Overwrite
        } else {
            & $portraitScript -SdApiUrl $SdApiUrl
        }
        if (-not $?) {
            throw "Portrait generation failed."
        }
    }

    if (-not $SkipVoice) {
        Write-Host "[crew-ai] generating voice..."
        $voiceScript = Join-Path $PSScriptRoot "generate-local-crew-voice.ps1"
        if ($Overwrite) {
            & $voiceScript -VoiceConfigPath $VoiceConfigPath -PiperExe $PiperExe -FfmpegExe $FfmpegExe -Overwrite
        } else {
            & $voiceScript -VoiceConfigPath $VoiceConfigPath -PiperExe $PiperExe -FfmpegExe $FfmpegExe
        }
        if (-not $?) {
            throw "Voice generation failed."
        }
    }

    if (-not $SkipQuality) {
        Write-Host "[crew-ai] running quality gates..."
        & .\gradlew.bat compileJava
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle compileJava failed."
        }
        & java -cp build/classes/java/main CrewPortraitPipelineHarness --strict
        if ($LASTEXITCODE -ne 0) {
            throw "CrewPortraitPipelineHarness failed."
        }
        & java -cp build/classes/java/main VoiceCoverageHarness --strict
        if ($LASTEXITCODE -ne 0) {
            throw "VoiceCoverageHarness failed."
        }
        & java -cp build/classes/java/main VoiceAssetQualityHarness --strict
        if ($LASTEXITCODE -ne 0) {
            throw "VoiceAssetQualityHarness failed."
        }
    }

    Write-Host "[crew-ai] pipeline complete."
} finally {
    Pop-Location
}

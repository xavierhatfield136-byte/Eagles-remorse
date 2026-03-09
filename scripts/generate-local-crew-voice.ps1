[CmdletBinding()]
param(
    [string]$LinesCsvPath = "assets/ai_pipeline/crew_voice_lines.csv",
    [string]$VoiceConfigPath = "assets/ai_pipeline/local_tts_voices.json",
    [string]$OutRoot = "assets/voice",
    [string]$PiperExe = "piper",
    [string]$FfmpegExe = "ffmpeg",
    [bool]$Normalize = $true,
    [switch]$Overwrite,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Test-Executable {
    param([string]$ExeName, [string[]]$ProbeArgs)
    try {
        & $ExeName @ProbeArgs *> $null
        return ($LASTEXITCODE -eq 0)
    } catch {
        return $false
    }
}

function Resolve-PiperExecutable {
    param([string]$Hint)

    if (-not [string]::IsNullOrWhiteSpace($Hint) -and (Test-Path -LiteralPath $Hint)) {
        return (Resolve-Path -LiteralPath $Hint).Path
    }

    $cmd = Get-Command $Hint -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Source) {
        return $cmd.Source
    }

    $candidates = @()
    if ($env:APPDATA) {
        $candidates += Get-ChildItem -Path (Join-Path $env:APPDATA "Python") -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { Join-Path $_.FullName "Scripts\piper.exe" }
    }
    if ($env:LOCALAPPDATA) {
        $candidates += Get-ChildItem -Path (Join-Path $env:LOCALAPPDATA "Programs") -Directory -ErrorAction SilentlyContinue |
            ForEach-Object {
                $pythonDir = Join-Path $_.FullName "Python"
                if (Test-Path $pythonDir) {
                    Get-ChildItem -Path $pythonDir -Directory -ErrorAction SilentlyContinue |
                        ForEach-Object { Join-Path $_.FullName "Scripts\piper.exe" }
                }
            }
    }

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    return $null
}

function Get-RoleVoiceSpec {
    param([object]$Config, [string]$Role)
    if (-not $Config -or -not $Config.roles) { return $null }
    if (-not ($Config.roles.PSObject.Properties.Name -contains $Role)) { return $null }
    return $Config.roles.($Role)
}

if (-not (Test-Path -LiteralPath $LinesCsvPath)) {
    throw "Voice line CSV missing: $LinesCsvPath"
}

if (-not (Test-Path -LiteralPath $VoiceConfigPath)) {
    $examplePath = Join-Path (Split-Path -Parent $VoiceConfigPath) "local_tts_voices.example.json"
    throw "Voice config missing: $VoiceConfigPath`nCopy and edit: $examplePath"
}

$resolvedPiper = Resolve-PiperExecutable -Hint $PiperExe
if ([string]::IsNullOrWhiteSpace($resolvedPiper)) {
    throw "Could not locate Piper. Install piper-tts or pass -PiperExe with a full path."
}

if (-not (Test-Executable -ExeName $resolvedPiper -ProbeArgs @("--help"))) {
    throw "Could not execute Piper binary '$resolvedPiper'."
}

$voiceConfig = Get-Content -Raw -LiteralPath $VoiceConfigPath | ConvertFrom-Json
$rows = @(Import-Csv -LiteralPath $LinesCsvPath)
if ($rows.Count -eq 0) {
    throw "No rows in voice line CSV: $LinesCsvPath"
}

$sampleRate = 48000
$targetLufs = -16.0
$targetPeak = -3.0
if ($voiceConfig.global) {
    if ($voiceConfig.global.sample_rate_hz) { $sampleRate = [int]$voiceConfig.global.sample_rate_hz }
    if ($voiceConfig.global.target_lufs) { $targetLufs = [double]$voiceConfig.global.target_lufs }
    if ($voiceConfig.global.target_true_peak_dbfs) { $targetPeak = [double]$voiceConfig.global.target_true_peak_dbfs }
}

$ffmpegAvailable = $false
if ($Normalize) {
    $ffmpegAvailable = Test-Executable -ExeName $FfmpegExe -ProbeArgs @("-version")
    if (-not $ffmpegAvailable) {
        Write-Warning "ffmpeg not found via '$FfmpegExe'. Continuing without normalization."
    }
}

$created = 0
$skipped = 0
$failed = 0

foreach ($row in $rows) {
    $role = $row.role.ToString().Trim().ToLowerInvariant()
    $eventId = $row.event_id.ToString().Trim().ToLowerInvariant()
    $variantNum = [int]$row.variant
    $text = $row.text.ToString().Trim()
    if ([string]::IsNullOrWhiteSpace($role) -or [string]::IsNullOrWhiteSpace($eventId) -or $variantNum -le 0 -or [string]::IsNullOrWhiteSpace($text)) {
        Write-Warning "[voice-gen] skipping malformed row: role='$($row.role)' event='$($row.event_id)' variant='$($row.variant)'"
        $failed++
        continue
    }

    $voiceSpec = Get-RoleVoiceSpec -Config $voiceConfig -Role $role
    if (-not $voiceSpec) {
        Write-Warning "[voice-gen] role config missing for '$role' in $VoiceConfigPath"
        $failed++
        continue
    }

    $modelPath = ""
    if ($voiceSpec.model_path) {
        $modelPath = $voiceSpec.model_path.ToString()
    }
    if ([string]::IsNullOrWhiteSpace($modelPath)) {
        Write-Warning "[voice-gen] model_path missing for role '$role'"
        $failed++
        continue
    }
    if (-not (Test-Path -LiteralPath $modelPath)) {
        Write-Warning "[voice-gen] model file not found for role '$role': $modelPath"
        $failed++
        continue
    }

    $variantTag = "{0:D2}" -f $variantNum
    $roleDir = Join-Path $OutRoot $role
    New-Item -ItemType Directory -Path $roleDir -Force | Out-Null
    $outFile = Join-Path $roleDir ("$eventId" + "_" + "$variantTag.wav")

    if ((Test-Path -LiteralPath $outFile) -and -not $Overwrite) {
        $skipped++
        continue
    }

    Write-Host "[voice-gen] role=$role event=$eventId variant=$variantTag -> $outFile"
    if ($DryRun) {
        Write-Host "[voice-gen] line: $text"
        continue
    }

    $tmpFile = Join-Path ([System.IO.Path]::GetTempPath()) ("crew-voice-" + [Guid]::NewGuid().ToString("N") + ".wav")
    try {
        $piperArgs = @("--model", $modelPath, "--output_file", $tmpFile)
        if ($voiceSpec.PSObject.Properties.Name -contains "speaker") {
            $speaker = $voiceSpec.speaker
            if ($null -ne $speaker -and $speaker.ToString().Trim().Length -gt 0) {
                $piperArgs += @("--speaker", $speaker.ToString().Trim())
            }
        }
        if ($voiceSpec.PSObject.Properties.Name -contains "length_scale" -and $null -ne $voiceSpec.length_scale) {
            $piperArgs += @("--length_scale", ([double]$voiceSpec.length_scale).ToString([System.Globalization.CultureInfo]::InvariantCulture))
        }
        if ($voiceSpec.PSObject.Properties.Name -contains "noise_scale" -and $null -ne $voiceSpec.noise_scale) {
            $piperArgs += @("--noise_scale", ([double]$voiceSpec.noise_scale).ToString([System.Globalization.CultureInfo]::InvariantCulture))
        }
        if ($voiceSpec.PSObject.Properties.Name -contains "noise_w" -and $null -ne $voiceSpec.noise_w) {
            $piperArgs += @("--noise_w", ([double]$voiceSpec.noise_w).ToString([System.Globalization.CultureInfo]::InvariantCulture))
        }

        $text | & $resolvedPiper @piperArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Piper exited with code $LASTEXITCODE"
        }
        if (-not (Test-Path -LiteralPath $tmpFile)) {
            throw "Piper did not produce an output file."
        }

        if ($Normalize -and $ffmpegAvailable) {
            $af = "loudnorm=I=${targetLufs}:TP=${targetPeak}:LRA=7"
            $ffArgs = @(
                "-hide_banner", "-loglevel", "error", "-y",
                "-i", $tmpFile,
                "-ac", "1",
                "-ar", "$sampleRate",
                "-af", $af,
                $outFile
            )
            & $FfmpegExe @ffArgs
            if ($LASTEXITCODE -ne 0) {
                throw "ffmpeg exited with code $LASTEXITCODE"
            }
            Remove-Item -LiteralPath $tmpFile -Force -ErrorAction SilentlyContinue
        } else {
            Move-Item -LiteralPath $tmpFile -Destination $outFile -Force
        }

        $created++
    } catch {
        Write-Warning "[voice-gen] failed role=$role event=$eventId variant=$variantTag :: $($_.Exception.Message)"
        $failed++
        if (Test-Path -LiteralPath $tmpFile) {
            Remove-Item -LiteralPath $tmpFile -Force -ErrorAction SilentlyContinue
        }
    }
}

Write-Host "[voice-gen] created=$created skipped=$skipped failed=$failed normalize=$Normalize"
if ($failed -gt 0) {
    exit 2
}

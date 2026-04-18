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

Add-Type -AssemblyName System.Speech

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

function Get-SpecValue {
    param([object]$Spec, [string]$Name, [int]$VariantNum = 1)
    if (-not $Spec) { return $null }
    if (-not ($Spec.PSObject.Properties.Name -contains $Name)) { return $null }
    $value = $Spec.$Name
    if ($null -eq $value) { return $null }
    if ($value -is [string]) { return $value }
    if ($value -is [System.Array]) {
        if ($value.Count -le 0) { return $null }
        $idx = [Math]::Abs($VariantNum - 1) % $value.Count
        return $value[$idx]
    }
    return $value
}

function Resolve-RoleEngine {
    param([object]$Spec, [int]$VariantNum)
    $explicit = Get-SpecValue -Spec $Spec -Name "engine" -VariantNum $VariantNum
    if ($explicit) { return $explicit.ToString().Trim().ToLowerInvariant() }
    $voiceName = Get-SpecValue -Spec $Spec -Name "voice_name" -VariantNum $VariantNum
    if (-not [string]::IsNullOrWhiteSpace([string]$voiceName)) { return "sapi" }
    $modelPath = Get-SpecValue -Spec $Spec -Name "model_path" -VariantNum $VariantNum
    if (-not [string]::IsNullOrWhiteSpace([string]$modelPath)) { return "piper" }
    return ""
}

function Get-InstalledVoiceNames {
    $s = New-Object System.Speech.Synthesis.SpeechSynthesizer
    try {
        return @($s.GetInstalledVoices() | ForEach-Object { $_.VoiceInfo.Name })
    } finally {
        $s.Dispose()
    }
}

function Write-SapiVoice {
    param(
        [string]$OutFile,
        [string]$Text,
        [string]$VoiceName,
        [int]$Rate,
        [int]$Volume
    )

    $synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
    try {
        if (-not [string]::IsNullOrWhiteSpace($VoiceName)) {
            $synth.SelectVoice($VoiceName)
        }
        $synth.Rate = [Math]::Max(-10, [Math]::Min(10, $Rate))
        $synth.Volume = [Math]::Max(0, [Math]::Min(100, $Volume))
        $synth.SetOutputToWaveFile($OutFile)
        $synth.Speak($Text)
    } finally {
        $synth.Dispose()
    }
}

if (-not (Test-Path -LiteralPath $LinesCsvPath)) {
    throw "Voice line CSV missing: $LinesCsvPath"
}

if (-not (Test-Path -LiteralPath $VoiceConfigPath)) {
    $examplePath = Join-Path (Split-Path -Parent $VoiceConfigPath) "local_tts_voices.example.json"
    throw "Voice config missing: $VoiceConfigPath`nCopy and edit: $examplePath"
}

$voiceConfig = Get-Content -Raw -LiteralPath $VoiceConfigPath | ConvertFrom-Json
$rows = @(Import-Csv -LiteralPath $LinesCsvPath)
if ($rows.Count -eq 0) {
    throw "No rows in voice line CSV: $LinesCsvPath"
}

$resolvedPiper = Resolve-PiperExecutable -Hint $PiperExe
if (-not [string]::IsNullOrWhiteSpace($resolvedPiper)) {
    if (-not (Test-Executable -ExeName $resolvedPiper -ProbeArgs @("--help"))) {
        throw "Could not execute Piper binary '$resolvedPiper'."
    }
}

$installedVoices = @(Get-InstalledVoiceNames)

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
        $engine = Resolve-RoleEngine -Spec $voiceSpec -VariantNum $variantNum
        if ($engine -eq "piper") {
            if ([string]::IsNullOrWhiteSpace($resolvedPiper)) {
                throw "Piper engine selected for role '$role' but Piper was not found."
            }
            $modelPath = [string](Get-SpecValue -Spec $voiceSpec -Name "model_path" -VariantNum $variantNum)
            if ([string]::IsNullOrWhiteSpace($modelPath)) {
                throw "model_path missing for role '$role'"
            }
            if (-not (Test-Path -LiteralPath $modelPath)) {
                throw "model file not found for role '$role': $modelPath"
            }
            $piperArgs = @("-m", $modelPath, "-f", $tmpFile)
            $configPath = [string](Get-SpecValue -Spec $voiceSpec -Name "config_path" -VariantNum $variantNum)
            if (-not [string]::IsNullOrWhiteSpace($configPath)) {
                $piperArgs += @("-c", $configPath)
            }
            $speaker = Get-SpecValue -Spec $voiceSpec -Name "speaker" -VariantNum $variantNum
            if ($null -ne $speaker -and $speaker.ToString().Trim().Length -gt 0) {
                $piperArgs += @("-s", $speaker.ToString().Trim())
            }
            $lengthScale = Get-SpecValue -Spec $voiceSpec -Name "length_scale" -VariantNum $variantNum
            if ($null -ne $lengthScale) {
                $piperArgs += @("--length_scale", ([double]$lengthScale).ToString([System.Globalization.CultureInfo]::InvariantCulture))
            }
            $noiseScale = Get-SpecValue -Spec $voiceSpec -Name "noise_scale" -VariantNum $variantNum
            if ($null -ne $noiseScale) {
                $piperArgs += @("--noise_scale", ([double]$noiseScale).ToString([System.Globalization.CultureInfo]::InvariantCulture))
            }
            $noiseWScale = Get-SpecValue -Spec $voiceSpec -Name "noise_w" -VariantNum $variantNum
            if ($null -ne $noiseWScale) {
                $piperArgs += @("--noise_w", ([double]$noiseWScale).ToString([System.Globalization.CultureInfo]::InvariantCulture))
            }

            $text | & $resolvedPiper @piperArgs
            if ($LASTEXITCODE -ne 0) {
                throw "Piper exited with code $LASTEXITCODE"
            }
            if (-not (Test-Path -LiteralPath $tmpFile)) {
                throw "Piper did not produce an output file."
            }
        } elseif ($engine -eq "sapi") {
            $voiceName = [string](Get-SpecValue -Spec $voiceSpec -Name "voice_name" -VariantNum $variantNum)
            if ([string]::IsNullOrWhiteSpace($voiceName)) {
                throw "voice_name missing for role '$role'"
            }
            if ($installedVoices -notcontains $voiceName) {
                throw "voice '$voiceName' not installed. Installed: $($installedVoices -join ', ')"
            }
            $rate = 0
            $volume = 100
            $rateValue = Get-SpecValue -Spec $voiceSpec -Name "rate" -VariantNum $variantNum
            if ($null -ne $rateValue) { $rate = [int]$rateValue }
            $volumeValue = Get-SpecValue -Spec $voiceSpec -Name "volume" -VariantNum $variantNum
            if ($null -ne $volumeValue) { $volume = [int]$volumeValue }
            Write-SapiVoice -OutFile $tmpFile -Text $text -VoiceName $voiceName -Rate $rate -Volume $volume
            if (-not (Test-Path -LiteralPath $tmpFile)) {
                throw "SAPI did not produce an output file."
            }
        } else {
            throw "No supported engine configured for role '$role'. Use engine='piper' or engine='sapi'."
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

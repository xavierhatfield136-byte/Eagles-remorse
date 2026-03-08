param(
    [int]$Seconds = 300,
    [long]$Seed = 90210,
    [string]$TelemetryOutput = "build/reports/phase9_telemetry.json",
    [string]$CampaignOutput = "build/reports/phase9_campaign_smoke.json",
    [string]$CampaignSeeds = "10101,20202,30303",
    [switch]$SkipDeterminism,
    [switch]$SkipTelemetry,
    [switch]$SkipCampaign
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Push-Location $root
try {
    Write-Host "Compiling sources..."
    & .\gradlew compileJava
    if ($LASTEXITCODE -ne 0) {
        throw "compileJava failed with exit code $LASTEXITCODE"
    }

    if (-not $SkipDeterminism) {
        Write-Host "Running Phase9DeterminismHarness..."
        & java -cp "build/classes/java/main" Phase9DeterminismHarness "--strict" "--seed=$Seed" "--seconds=$Seconds"
        if ($LASTEXITCODE -ne 0) {
            throw "Phase9DeterminismHarness failed with exit code $LASTEXITCODE"
        }
    }

    if (-not $SkipTelemetry) {
        Write-Host "Running Phase9TelemetryHarness..."
        & java -cp "build/classes/java/main" Phase9TelemetryHarness "--strict" "--seed=$Seed" "--seconds=$Seconds" "--output=$TelemetryOutput"
        if ($LASTEXITCODE -ne 0) {
            throw "Phase9TelemetryHarness failed with exit code $LASTEXITCODE"
        }
    }

    if (-not $SkipCampaign) {
        Write-Host "Running campaign smoke harness..."
        & powershell -ExecutionPolicy Bypass -File ".\scripts\run-campaign-parity.ps1" `
            -Seeds $CampaignSeeds `
            -Output $CampaignOutput `
            -SkipCompare
        if ($LASTEXITCODE -ne 0) {
            throw "Campaign smoke run failed with exit code $LASTEXITCODE"
        }
    }

    Write-Host ""
    Write-Host "Phase 9 run complete."
    if (-not $SkipTelemetry) {
        Write-Host "Telemetry report: $(Join-Path $root $TelemetryOutput)"
    }
    if (-not $SkipCampaign) {
        Write-Host "Campaign smoke report: $(Join-Path $root $CampaignOutput)"
    }
}
finally {
    Pop-Location
}

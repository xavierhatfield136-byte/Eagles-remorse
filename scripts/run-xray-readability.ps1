param(
    [long]$Seed = 424242,
    [int]$Ticks = 3600,
    [string]$Output = "build/reports/xray_readability_report.json",
    [string]$SnapshotDir = "build/reports/xray_readability_snapshots",
    [switch]$Strict
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

    $args = @(
        "--seed=$Seed",
        "--ticks=$Ticks",
        "--output=$Output",
        "--snapshot-dir=$SnapshotDir"
    )
    if ($Strict) { $args += "--strict" }

    Write-Host "Running XrayReadabilityHarness..."
    & java -cp "build/classes/java/main" XrayReadabilityHarness @args
    if ($LASTEXITCODE -ne 0 -and $Strict) {
        throw "XrayReadabilityHarness failed with exit code $LASTEXITCODE"
    }

    Write-Host ""
    Write-Host "X-ray readability run complete."
    Write-Host "Report: $(Join-Path $root $Output)"
    Write-Host "Snapshots: $(Join-Path $root $SnapshotDir)"
}
finally {
    Pop-Location
}

param(
    [long]$Seed = 424242,
    [int]$AudioSeconds = 180,
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

    $args = @("--seed=$Seed", "--audio-seconds=$AudioSeconds")
    if ($Strict) { $args += "--strict" }

    Write-Host "Running ChecklistV2Harness..."
    & java -cp "build/classes/java/main" ChecklistV2Harness @args
    if ($LASTEXITCODE -ne 0 -and $Strict) {
        throw "ChecklistV2Harness failed with exit code $LASTEXITCODE"
    }

    Write-Host ""
    Write-Host "Checklist V2 run complete."
}
finally {
    Pop-Location
}

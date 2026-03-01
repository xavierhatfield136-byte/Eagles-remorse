param(
    [string]$Seeds = "10101,20202,30303",
    [string]$Output = "build/parity/campaign_parity_latest.json",
    [string]$Baseline = "docs/parity/campaign_m1_baseline.json",
    [int]$MaxTicks = 450000,
    [switch]$UpdateBaseline,
    [switch]$SkipCompare
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-JavaTool {
    param([string]$Name)

    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $fromJavaHome = Join-Path $env:JAVA_HOME ("bin\" + $Name + ".exe")
        if (Test-Path $fromJavaHome) { return $fromJavaHome }
    }

    $candidates = Get-ChildItem -Path "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending
    foreach ($jdkDir in $candidates) {
        $candidate = Join-Path $jdkDir.FullName ("bin\" + $Name + ".exe")
        if (Test-Path $candidate) { return $candidate }
    }
    return $null
}

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$javac = Resolve-JavaTool -Name "javac"
$java = Resolve-JavaTool -Name "java"
if ([string]::IsNullOrWhiteSpace($javac) -or [string]::IsNullOrWhiteSpace($java)) {
    throw "Could not find javac/java. Install a JDK and add it to PATH or set JAVA_HOME."
}

$classesDir = Join-Path $root "build\parity\classes"
if (Test-Path $classesDir) {
    Remove-Item -Path $classesDir -Recurse -Force
}
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null

$sources = Get-ChildItem -Path (Join-Path $root "src") -Filter *.java | ForEach-Object { $_.FullName }
if (-not $sources -or $sources.Count -eq 0) {
    throw "No Java source files found under src/"
}

Write-Host "Compiling parity harness sources..."
& $javac -d $classesDir $sources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

$outputAbs = Join-Path $root $Output
Write-Host "Running CampaignParityHarness..."
& $java -cp $classesDir CampaignParityHarness "--seeds=$Seeds" "--output=$outputAbs" "--maxTicks=$MaxTicks"
if ($LASTEXITCODE -ne 0) {
    throw "CampaignParityHarness failed with exit code $LASTEXITCODE"
}

if ($UpdateBaseline) {
    $baselineAbs = Join-Path $root $Baseline
    $baselineDir = Split-Path -Parent $baselineAbs
    if (-not [string]::IsNullOrWhiteSpace($baselineDir)) {
        New-Item -ItemType Directory -Path $baselineDir -Force | Out-Null
    }
    Copy-Item -Path $outputAbs -Destination $baselineAbs -Force
    Write-Host "Baseline updated: $baselineAbs"
}

if (-not $SkipCompare) {
    $baselineAbs = Join-Path $root $Baseline
    if (Test-Path $baselineAbs) {
        Write-Host "Comparing against baseline..."
        & powershell -ExecutionPolicy Bypass -File (Join-Path $root "scripts\compare-campaign-parity.ps1") -Baseline $baselineAbs -Candidate $outputAbs
        if ($LASTEXITCODE -ne 0) {
            throw "Parity comparison failed with exit code $LASTEXITCODE"
        }
    } else {
        Write-Host "Baseline not found at $baselineAbs (skipping compare)"
    }
}

Write-Host ""
Write-Host "Campaign parity run complete."
Write-Host "Output: $outputAbs"

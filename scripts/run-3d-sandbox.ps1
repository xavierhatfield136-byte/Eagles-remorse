param(
    [string]$Seed = "",
    [string]$MapSize = "medium", # small | medium | large
    [string]$Mode = "campaign", # campaign | domination | custom | showcase | range
    [string]$ModelDir = "C:\Users\xhatf\OneDrive\Desktop\3d models dropoff",
    [switch]$NoRandomEvents,
    [switch]$NoGlbModels
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

$classesDir = Join-Path $root "build\3d-sandbox\classes"
if (Test-Path $classesDir) {
    Remove-Item -Path $classesDir -Recurse -Force
}
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null

$legacySources = Get-ChildItem -Path (Join-Path $root "src") -Filter *.java -Recurse | ForEach-Object { $_.FullName }
$client3dSources = Get-ChildItem -Path (Join-Path $root "client-3dimentions\src") -Filter *.java -Recurse | ForEach-Object { $_.FullName }
$sources = @($legacySources + $client3dSources)
if (-not $sources -or $sources.Count -eq 0) {
    throw "No Java source files found for sandbox run."
}

Write-Host "Compiling sandbox sources..."
& $javac -d $classesDir $sources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

$args = @()
if (-not [string]::IsNullOrWhiteSpace($Seed)) {
    $args += "--seed=$Seed"
}
switch ($MapSize.Trim().ToLowerInvariant()) {
    "small" { $args += "--small" }
    "large" { $args += "--large" }
    default { }
}
if ($NoRandomEvents) {
    $args += "--no-random-events"
}
switch ($Mode.Trim().ToLowerInvariant()) {
    "domination" { $args += "--mode=domination" }
    "custom" { $args += "--mode=custom" }
    "showcase" { $args += "--mode=showcase" }
    "range" { $args += "--mode=range" }
    default { $args += "--mode=campaign" }
}

$javaProps = @()
if (-not $NoGlbModels -and -not [string]::IsNullOrWhiteSpace($ModelDir)) {
    $javaProps += "-Deagles.modelDir=$ModelDir"
}

Write-Host "Launching Main3D..."
& $java $javaProps -cp $classesDir Main3D $args

param(
    [string]$Version = ""
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
if ([string]::IsNullOrWhiteSpace($Version)) {
    $versionFile = Join-Path $root "VERSION"
    if (Test-Path $versionFile) {
        $Version = (Get-Content -Path $versionFile -TotalCount 1).Trim()
    }
}
if ([string]::IsNullOrWhiteSpace($Version)) {
    $Version = "dev"
}

$javac = Resolve-JavaTool -Name "javac"
$jar = Resolve-JavaTool -Name "jar"
if ([string]::IsNullOrWhiteSpace($javac) -or [string]::IsNullOrWhiteSpace($jar)) {
    throw "Could not find javac/jar. Install a JDK and add it to PATH or set JAVA_HOME."
}

$buildDir = Join-Path $root "build"
$classesDir = Join-Path $buildDir "classes"
$distDir = Join-Path $buildDir "dist"
$manifestPath = Join-Path $buildDir "MANIFEST.MF"
$jarName = "space-game-$Version.jar"
$jarPath = Join-Path $distDir $jarName

if (Test-Path $classesDir) { Remove-Item $classesDir -Recurse -Force }
if (Test-Path $distDir) { Remove-Item $distDir -Recurse -Force }
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null
New-Item -ItemType Directory -Path $distDir -Force | Out-Null

$sources = Get-ChildItem -Path (Join-Path $root "src") -Filter *.java | ForEach-Object { $_.FullName }
if (-not $sources -or $sources.Count -eq 0) {
    throw "No Java source files found under src/"
}

Write-Host "Compiling sources..."
& $javac -d $classesDir $sources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

$versionFileInRoot = Join-Path $root "VERSION"
if (Test-Path $versionFileInRoot) {
    Copy-Item -Path $versionFileInRoot -Destination (Join-Path $classesDir "VERSION") -Force
}

@(
    "Manifest-Version: 1.0"
    "Main-Class: Main"
    "Implementation-Version: $Version"
    ""
) | Set-Content -Path $manifestPath -Encoding ASCII

Write-Host "Creating jar $jarName..."
& $jar cfm $jarPath $manifestPath -C $classesDir .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE"
}

if (Test-Path (Join-Path $root "assets")) {
    Copy-Item -Path (Join-Path $root "assets") -Destination (Join-Path $distDir "assets") -Recurse -Force
}
if (Test-Path (Join-Path $root "save")) {
    Copy-Item -Path (Join-Path $root "save") -Destination (Join-Path $distDir "save") -Recurse -Force
}
if (Test-Path $versionFileInRoot) {
    Copy-Item -Path $versionFileInRoot -Destination (Join-Path $distDir "VERSION") -Force
}

Write-Host ""
Write-Host "Package complete:"
Write-Host "  $jarPath"
Write-Host ""
Write-Host "Run with:"
Write-Host "  java -jar `"$jarPath`""

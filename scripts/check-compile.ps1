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
if ([string]::IsNullOrWhiteSpace($javac)) {
    throw "Could not find javac. Install a JDK and add it to PATH or set JAVA_HOME."
}

$buildRoot = Join-Path $root "build\checks"
if (Test-Path $buildRoot) {
    Remove-Item -Path $buildRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $buildRoot -Force | Out-Null

function Get-JavaSources {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return @() }
    return @(Get-ChildItem -Path $Path -Recurse -Filter *.java | ForEach-Object { $_.FullName })
}

function Compile-Target {
    param(
        [string]$Name,
        [string]$Path,
        [string]$OutDir,
        [string]$ClassPath = ""
    )

    if (-not (Test-Path $path)) {
        Write-Host "[$name] Skipped (path missing): $path"
        return $false
    }

    $sources = Get-JavaSources -Path $Path
    if (-not $sources -or $sources.Count -eq 0) {
        Write-Host "[$name] Skipped (no java files)"
        return $false
    }

    New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
    Write-Host "[$name] Compiling $($sources.Count) files..."
    if ([string]::IsNullOrWhiteSpace($ClassPath)) {
        & $javac -d $OutDir $sources
    } else {
        & $javac -cp $ClassPath -d $OutDir $sources
    }
    if ($LASTEXITCODE -ne 0) {
        throw "[$name] javac failed with exit code $LASTEXITCODE"
    }
    return $true
}

$legacyPath = Join-Path $root "src"

$legacyOut = Join-Path $buildRoot "legacy-src"

Compile-Target -Name "legacy-src" -Path $legacyPath -OutDir $legacyOut | Out-Null

Write-Host ""
Write-Host "Compile checks complete."

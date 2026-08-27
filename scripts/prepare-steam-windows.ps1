[CmdletBinding()]
param(
    [ValidateRange(0, 2147483647)]
    [int]$AppId = 0,

    [ValidateRange(0, 2147483647)]
    [int]$DepotId = 0,

    [string]$BuildDescription = "",

    [ValidatePattern('^[A-Za-z0-9_-]*$')]
    [string]$SetLiveBranch = "",

    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path
$gradle = Join-Path $repoRoot "gradlew.bat"
$version = (Get-Content -LiteralPath (Join-Path $repoRoot "VERSION") -Raw).Trim()

if (-not $SkipBuild) {
    & $gradle prepareSteamWindows --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Steam staging failed with exit code $LASTEXITCODE."
    }
}

$stageRoot = Join-Path $repoRoot "build\steam\content\EaglesRemorse"
$stageRoot = (Resolve-Path -LiteralPath $stageRoot).Path
$expectedStageRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot "build\steam\content\EaglesRemorse"))
if ($stageRoot -ne $expectedStageRoot) {
    throw "Resolved Steam stage escaped the expected workspace path: $stageRoot"
}

$required = @(
    (Join-Path $stageRoot "EaglesRemorse.exe"),
    (Join-Path $stageRoot "runtime\release"),
    (Join-Path $stageRoot "runtime\lib\modules")
)
foreach ($path in $required) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required staged file is missing: $path"
    }
}

Write-Host "Verified Steam content stage: $stageRoot"
Write-Host "SHA-256 manifest: $(Join-Path $repoRoot 'build\steam\SHA256SUMS-steam-windows.txt')"

if (($AppId -eq 0) -xor ($DepotId -eq 0)) {
    throw "Provide both -AppId and -DepotId, or omit both when only staging locally."
}
if ($AppId -eq 0) {
    Write-Host "No AppID/depot ID supplied, so valid upload VDF files were not generated."
    Write-Host "After Steamworks assigns the IDs, rerun with -SkipBuild -AppId <id> -DepotId <id>."
    exit 0
}

if ([string]::IsNullOrWhiteSpace($BuildDescription)) {
    $BuildDescription = "Eagles Remorse $version Windows candidate"
}
if ($BuildDescription.Contains('"') -or $BuildDescription.Contains("`r") -or $BuildDescription.Contains("`n")) {
    throw "BuildDescription cannot contain quotes or newlines."
}

$templateRoot = Join-Path $repoRoot "steam\templates"
$outputRoot = Join-Path $repoRoot "build\steam\scripts"
$buildOutput = Join-Path $repoRoot "build\steam\output"
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
New-Item -ItemType Directory -Path $buildOutput -Force | Out-Null

function ConvertTo-VdfPath([string]$Path) {
    return $Path.Replace("\", "\\")
}

$depotFileName = "depot_build_$DepotId.vdf"
$depotTemplate = Get-Content -LiteralPath (Join-Path $templateRoot "depot_build_windows.vdf.template") -Raw
$depotVdf = $depotTemplate.Replace("{{DEPOT_ID}}", [string]$DepotId)
$depotVdf = $depotVdf.Replace("{{CONTENT_ROOT}}", (ConvertTo-VdfPath $stageRoot))
$depotPath = Join-Path $outputRoot $depotFileName
Set-Content -LiteralPath $depotPath -Value $depotVdf -Encoding UTF8

$setLiveLine = ""
if (-not [string]::IsNullOrWhiteSpace($SetLiveBranch)) {
    $setLiveLine = "    `"SetLive`" `"$SetLiveBranch`""
}
$appTemplate = Get-Content -LiteralPath (Join-Path $templateRoot "app_build.vdf.template") -Raw
$appVdf = $appTemplate.Replace("{{APP_ID}}", [string]$AppId)
$appVdf = $appVdf.Replace("{{BUILD_DESCRIPTION}}", $BuildDescription)
$appVdf = $appVdf.Replace("{{BUILD_OUTPUT}}", (ConvertTo-VdfPath $buildOutput))
$appVdf = $appVdf.Replace("{{CONTENT_ROOT}}", (ConvertTo-VdfPath $stageRoot))
$appVdf = $appVdf.Replace("{{SET_LIVE_LINE}}", $setLiveLine)
$appVdf = $appVdf.Replace("{{DEPOT_ID}}", [string]$DepotId)
$appVdf = $appVdf.Replace("{{DEPOT_SCRIPT}}", $depotFileName)
$appPath = Join-Path $outputRoot "app_build_$AppId.vdf"
Set-Content -LiteralPath $appPath -Value $appVdf -Encoding UTF8

Write-Host "Generated credential-free SteamPipe scripts:"
Write-Host "  $appPath"
Write-Host "  $depotPath"
Write-Host "No upload was attempted. Review the VDF files before using SteamCMD."

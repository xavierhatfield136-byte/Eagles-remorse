param(
    [string]$ExtractedRoot = "build/package/windows/clean-extract",
    [string]$WorkDir = "build/isolated-launch-workdir",
    [string]$UserDataDir = "build/isolated-user-data",
    [string]$ReportPath = "build/reports/distribution-verification/isolated_launch_smoke.json",
    [string]$GatePath = "build/reports/distribution-verification/release_candidate_gate.json",
    [int]$Seconds = 12
)

$ErrorActionPreference = "Stop"

function Resolve-RepoPath([string]$PathText) {
    if ([System.IO.Path]::IsPathRooted($PathText)) {
        return [System.IO.Path]::GetFullPath($PathText)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $PathText))
}

$root = Resolve-RepoPath $ExtractedRoot
$work = Resolve-RepoPath $WorkDir
$userData = Resolve-RepoPath $UserDataDir
$exe = Join-Path $root "EaglesRemorse.exe"
$report = Resolve-RepoPath $ReportPath
$gatePathFull = Resolve-RepoPath $GatePath

if (!(Test-Path -LiteralPath $exe -PathType Leaf)) {
    throw "Launcher missing: $exe"
}

Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $userData -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $work | Out-Null
New-Item -ItemType Directory -Path $userData | Out-Null

$oldJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$env:JAVA_TOOL_OPTIONS = "-Dcodex.disableAudio=true -Dgame.userDataDir=$userData"

$process = $null
try {
    $process = Start-Process -FilePath $exe -WorkingDirectory $work -WindowStyle Hidden -PassThru
    Start-Sleep -Seconds $Seconds
    $running = -not $process.HasExited
    if ($running) {
        Stop-Process -Id $process.Id -Force
        Start-Sleep -Seconds 1
    }

    $log = Join-Path $userData "logs/error.log"
    $result = [ordered]@{
        startedProcessId = $process.Id
        wasStillRunningAfterSeconds = $running
        seconds = $Seconds
        exitCode = if ($running) { $null } else { $process.ExitCode }
        errorLogExists = (Test-Path -LiteralPath $log)
        errorLogPath = $log
        workingDirectory = $work
        userDataDir = $userData
        passed = $running -and !(Test-Path -LiteralPath $log)
    }
    $json = $result | ConvertTo-Json -Depth 4
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($report)) | Out-Null
    [System.IO.File]::WriteAllText($report, $json + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
    if (Test-Path -LiteralPath $gatePathFull) {
        $gate = Get-Content -LiteralPath $gatePathFull -Raw | ConvertFrom-Json
        $gate | Add-Member -NotePropertyName isolatedLaunchPassed -NotePropertyValue ([bool]$result.passed) -Force
        $gate | Add-Member -NotePropertyName isolatedLaunchReport -NotePropertyValue ($report.Replace('\', '/')) -Force
        $gate | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $gatePathFull -Encoding UTF8
    }
    $json
    if (Test-Path -LiteralPath $log) {
        Get-Content -LiteralPath $log -Tail 80
    }
    if (-not $result.passed) {
        throw "Isolated launch smoke failed."
    }
} finally {
    $env:JAVA_TOOL_OPTIONS = $oldJavaToolOptions
}

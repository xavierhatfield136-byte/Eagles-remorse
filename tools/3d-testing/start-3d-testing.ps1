param(
  [int]$Port = 5173
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

function Test-PythonInvocation {
  param(
    [string]$Exe,
    [string[]]$Args = @()
  )
  try {
    & $Exe @Args -c "import sys" *> $null
    return ($LASTEXITCODE -eq 0)
  } catch {
    return $false
  }
}

$pythonExe = $null
$pythonArgs = @()

# Prefer the Python launcher on Windows because `python.exe` can be a Microsoft Store alias.
$pyLauncher = Get-Command py -ErrorAction SilentlyContinue
if ($pyLauncher -and (Test-PythonInvocation -Exe 'py' -Args @('-3'))) {
  $pythonExe = 'py'
  $pythonArgs = @('-3')
}

if (-not $pythonExe) {
  $pythonCmd = Get-Command python -ErrorAction SilentlyContinue
  if ($pythonCmd -and (Test-PythonInvocation -Exe 'python')) {
    $pythonExe = 'python'
  }
}

if (-not $pythonExe) {
  Write-Host 'No working Python interpreter found.' -ForegroundColor Red
  Write-Host 'Install Python 3, or use the Python launcher (`py -3`) if available.' -ForegroundColor Red
  exit 1
}

$url = "http://localhost:$Port"
Write-Host "Starting 3D testing server in $root on $url"
Write-Host 'Press Ctrl+C to stop the server.'

Start-Process $url
& $pythonExe @pythonArgs -m http.server $Port

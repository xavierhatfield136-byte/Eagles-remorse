param(
  [int]$Port = 5173
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$pythonCmd = Get-Command python -ErrorAction SilentlyContinue
if (-not $pythonCmd) {
  Write-Host 'Python was not found in PATH. Install Python or run: py -m http.server 5173' -ForegroundColor Red
  exit 1
}

$url = "http://localhost:$Port"
Write-Host "Starting 3D testing server in $root on $url"
Write-Host 'Press Ctrl+C to stop the server.'

Start-Process $url
python -m http.server $Port

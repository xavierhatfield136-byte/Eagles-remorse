param(
  [int]$Port = 5173,
  [string]$ModelDir = "C:\Users\xhatf\OneDrive\Desktop\3d models dropoff"
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
$modelsRoot = Join-Path $root "public\models"
$dropoffLink = Join-Path $modelsRoot "dropoff"
$manifestPath = Join-Path $modelsRoot "dropoff-manifest.json"

New-Item -ItemType Directory -Force -Path $modelsRoot | Out-Null
if (Test-Path -LiteralPath $ModelDir) {
  if (Test-Path -LiteralPath $dropoffLink) {
    $item = Get-Item -LiteralPath $dropoffLink -Force
    if (-not ($item.LinkType -eq "Junction" -or $item.LinkType -eq "SymbolicLink")) {
      Remove-Item -LiteralPath $dropoffLink -Recurse -Force
    }
  }
  if (-not (Test-Path -LiteralPath $dropoffLink)) {
    New-Item -ItemType Junction -Path $dropoffLink -Target $ModelDir | Out-Null
  }

  $models = Get-ChildItem -LiteralPath $ModelDir -File -Filter "*.glb" |
    Sort-Object Name |
    ForEach-Object {
      [pscustomobject]@{
        name = $_.Name
        url = "./public/models/dropoff/$([Uri]::EscapeDataString($_.Name))"
        bytes = $_.Length
        modified = $_.LastWriteTime.ToString("s")
      }
    }
  $models | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
} else {
  Write-Host "Model dropoff folder not found: $ModelDir" -ForegroundColor Yellow
}

Write-Host "Starting 3D testing server in $root on $url"
Write-Host "Model dropoff: $ModelDir"
Write-Host 'Press Ctrl+C to stop the server.'

Start-Process $url
& $pythonExe @pythonArgs -m http.server $Port

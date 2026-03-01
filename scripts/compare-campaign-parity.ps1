param(
    [Parameter(Mandatory = $true)]
    [string]$Baseline,
    [Parameter(Mandatory = $true)]
    [string]$Candidate
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path $Baseline)) {
    throw "Baseline file not found: $Baseline"
}
if (-not (Test-Path $Candidate)) {
    throw "Candidate file not found: $Candidate"
}

$base = Get-Content -Raw -Path $Baseline | ConvertFrom-Json
$cand = Get-Content -Raw -Path $Candidate | ConvertFrom-Json
$errors = New-Object System.Collections.Generic.List[string]

function Add-Err([string]$m) {
    $errors.Add($m) | Out-Null
}

if ($base.harnessVersion -ne $cand.harnessVersion) {
    Add-Err "harnessVersion mismatch: baseline=$($base.harnessVersion) candidate=$($cand.harnessVersion)"
}
if ($base.scenario -ne $cand.scenario) {
    Add-Err "scenario mismatch: baseline=$($base.scenario) candidate=$($cand.scenario)"
}

$baseBySeed = @{}
foreach ($r in $base.results) { $baseBySeed["$($r.seed)"] = $r }
$candBySeed = @{}
foreach ($r in $cand.results) { $candBySeed["$($r.seed)"] = $r }

foreach ($seed in $baseBySeed.Keys) {
    if (-not $candBySeed.ContainsKey($seed)) {
        Add-Err "missing candidate result for seed=$seed"
        continue
    }

    $b = $baseBySeed[$seed]
    $c = $candBySeed[$seed]

    if (-not $c.pass) {
        Add-Err "candidate run failed for seed=$seed reason=$($c.failReason)"
    }
    if ($b.finalSector -ne $c.finalSector) {
        Add-Err "seed=$seed finalSector mismatch: baseline=$($b.finalSector) candidate=$($c.finalSector)"
    }
    if ($b.gameOver -ne $c.gameOver) {
        Add-Err "seed=$seed gameOver mismatch: baseline=$($b.gameOver) candidate=$($c.gameOver)"
    }
    if ($b.finalCredits -ne $c.finalCredits) {
        Add-Err "seed=$seed finalCredits mismatch: baseline=$($b.finalCredits) candidate=$($c.finalCredits)"
    }
    if ($b.objectiveFlow -ne $c.objectiveFlow) {
        Add-Err "seed=$seed objectiveFlow mismatch: baseline=$($b.objectiveFlow) candidate=$($c.objectiveFlow)"
    }

    $bSectors = @{}
    foreach ($s in $b.sectors) { $bSectors["$($s.sector)"] = $s }
    $cSectors = @{}
    foreach ($s in $c.sectors) { $cSectors["$($s.sector)"] = $s }

    foreach ($sector in $bSectors.Keys) {
        if (-not $cSectors.ContainsKey($sector)) {
            Add-Err "seed=$seed missing sector=$sector in candidate"
            continue
        }
        $bs = $bSectors[$sector]
        $cs = $cSectors[$sector]

        if ($bs.objectiveType -ne $cs.objectiveType) {
            Add-Err "seed=$seed sector=$sector objectiveType mismatch: baseline=$($bs.objectiveType) candidate=$($cs.objectiveType)"
        }
        if ($bs.sideObjectiveType -ne $cs.sideObjectiveType) {
            Add-Err "seed=$seed sector=$sector sideObjectiveType mismatch: baseline=$($bs.sideObjectiveType) candidate=$($cs.sideObjectiveType)"
        }
        if ($bs.clearElapsedSecRounded -ne $cs.clearElapsedSecRounded) {
            Add-Err "seed=$seed sector=$sector clearElapsedSecRounded mismatch: baseline=$($bs.clearElapsedSecRounded) candidate=$($cs.clearElapsedSecRounded)"
        }
        if ([Math]::Abs([int]$bs.clearTicks - [int]$cs.clearTicks) -gt 1) {
            Add-Err "seed=$seed sector=$sector clearTicks mismatch: baseline=$($bs.clearTicks) candidate=$($cs.clearTicks)"
        }
        if ($bs.creditsAfterClear -ne $cs.creditsAfterClear) {
            Add-Err "seed=$seed sector=$sector creditsAfterClear mismatch: baseline=$($bs.creditsAfterClear) candidate=$($cs.creditsAfterClear)"
        }
        if ($bs.sideCompleted -ne $cs.sideCompleted) {
            Add-Err "seed=$seed sector=$sector sideCompleted mismatch: baseline=$($bs.sideCompleted) candidate=$($cs.sideCompleted)"
        }
        if ($bs.sideFailed -ne $cs.sideFailed) {
            Add-Err "seed=$seed sector=$sector sideFailed mismatch: baseline=$($bs.sideFailed) candidate=$($cs.sideFailed)"
        }
    }
}

if ($errors.Count -gt 0) {
    Write-Host "Campaign parity regression: FAIL"
    foreach ($e in $errors) {
        Write-Host " - $e"
    }
    exit 1
}

Write-Host "Campaign parity regression: PASS"

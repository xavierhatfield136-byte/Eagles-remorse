param(
    [string]$Root = (Resolve-Path "$PSScriptRoot\..").Path,
    [string]$Out = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Out)) {
    $Out = Join-Path $Root "docs\ALPHA_RELEASE_BLOCKER_DASHBOARD.md"
}

$productionReport = Join-Path $Root "build\reports\production-validation.txt"
$backlog = Join-Path $Root "docs\2D_GAME_OPPORTUNITY_BACKLOG.md"
$assetReport = Join-Path $Root "docs\ALPHA_ASSET_APPROVAL_REPORT.md"
$manualScripts = Join-Path $Root "docs\ALPHA_MANUAL_ACCEPTANCE_SCRIPTS.md"

function Count-Matches([string]$Path, [string]$Pattern) {
    if (-not (Test-Path -LiteralPath $Path)) { return 0 }
    return (Select-String -LiteralPath $Path -Pattern $Pattern -AllMatches).Count
}

function Read-LinesOrEmpty([string]$Path) {
    if (Test-Path -LiteralPath $Path) { return Get-Content -LiteralPath $Path }
    return @()
}

$productionLines = Read-LinesOrEmpty $productionReport
$productionIssues = @($productionLines | Where-Object {
    $_ -match "fail|missing|owner-review-open=[1-9]|errors=[1-9]"
})

$openChecklist = Count-Matches $backlog "^- \[ \]"
$doneChecklist = Count-Matches $backlog "^- \[x\]"
$openAssetReviews = Count-Matches $assetReport "^- \[ \]"
$manualEvidenceSlots = Count-Matches $manualScripts "^## Script "

$status = "PASS"
if (-not (Test-Path -LiteralPath $productionReport)) { $status = "BLOCKED" }
elseif ($productionIssues.Count -gt 0 -or $openAssetReviews -gt 0) { $status = "REVIEW" }

$generated = Get-Date -Format "yyyy-MM-dd HH:mm:ss K"
$outLines = New-Object System.Collections.Generic.List[string]
$outLines.Add("# Alpha Release Blocker Dashboard")
$outLines.Add("")
$outLines.Add("Generated: $generated")
$outLines.Add("Status: $status")
$outLines.Add("")
$outLines.Add("## Validation Inputs")
$outLines.Add("")
$outLines.Add("- Production validation: $(if (Test-Path -LiteralPath $productionReport) { "present" } else { "missing" })")
$outLines.Add("- 2D checklist: $doneChecklist done / $openChecklist open")
$outLines.Add("- Asset owner review items open: $openAssetReviews")
$outLines.Add("- Manual acceptance scripts available: $manualEvidenceSlots")
$outLines.Add("")
$outLines.Add("## Blocker Signals")
$outLines.Add("")
if ($productionIssues.Count -eq 0) {
    $outLines.Add("- No fail/error/missing/owner-review production signals found.")
} else {
    foreach ($line in $productionIssues | Select-Object -First 40) {
        $outLines.Add("- $line")
    }
}
$outLines.Add("")
$outLines.Add("## Required Before Alpha")
$outLines.Add("")
$outLines.Add("- Run `./gradlew productionValidation` before generating this dashboard.")
$outLines.Add("- Complete and attach evidence for all scripts in `docs/ALPHA_MANUAL_ACCEPTANCE_SCRIPTS.md`.")
$outLines.Add("- Close or explicitly approve every owner-review item in `docs/ALPHA_ASSET_APPROVAL_REPORT.md`.")
$outLines.Add("- Keep working `docs/2D_GAME_OPPORTUNITY_BACKLOG.md`; open items are not automatically release blockers unless they affect alpha trust.")

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Out) | Out-Null
Set-Content -LiteralPath $Out -Value $outLines -Encoding UTF8
Write-Host "alpha release dashboard written to $Out"

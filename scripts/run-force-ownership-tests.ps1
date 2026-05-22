param(
    [switch]$FullCampaign
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

$tests = @(
    "CampaignForceOwnershipTest",
    "CampaignStrategicCommandHudTest",
    "CampaignStrategicTravelPressureTest",
    "CampaignFleetHubMenuRegressionTest",
    "CampaignStrategicUiReadabilityTest",
    "RendererHoverTooltipTest"
)

if ($FullCampaign) {
    $tests += @(
        "CampaignOvermapEncounterFlowTest",
        "CampaignOvermapCheckpointTest",
        "CampaignTacticalAlignmentTest",
        "CampaignStrategicLoopIntegrationTest",
        "CampaignStrategicStrikeCounterplayTest"
    )
}

$args = @("test")
foreach ($test in $tests) {
    $args += "--tests"
    $args += $test
}

& .\gradlew.bat @args

[CmdletBinding()]
param(
    [ValidateSet("anchors", "escorts", "cruisers", "support", "all")]
    [string]$Batch = "anchors",
    [string]$ComfyApiUrl = "http://127.0.0.1:8188",
    [string]$OutputRoot = "build/team_d_autogen",
    [string]$ScratchRoot = "build/ship_skin_generation_autogen",
    [int]$AttemptsPerPrompt = 2,
    [switch]$Overwrite,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$generatorPath = Join-Path $PSScriptRoot "generate-ship-skins-comfy.ps1"
$promptFile = Join-Path $projectRoot "assets/ship_skins/dropbox/HULL_PROMPTS.md"
$styleLockPath = Join-Path $projectRoot "assets/ship_skins/dropbox/TEAM_D_STYLE_LOCK.txt"
$referenceMapPath = Join-Path $projectRoot "assets/ship_skins/dropbox/TEAM_D_REFERENCE_MAP.json"

$promptSuffix = "Strictly top-down orthographic sprite sheet render seen directly from above, not isometric, not perspective, right-facing silhouette only. Camera directly above hull plan view. Keep hull mass readable and integrated, not a beauty-shot render."
$negativePrompt = "isometric, perspective, 3/4 view, angled view, side view, front view, cinematic render, concept render, turntable render, starship beauty shot, hero render, gunship, shuttle, dropship, visible cannon, gun barrel, turret, exposed weapon pod, sticker outline, white outline, decal outline, cel shaded, comic ink, sketch render"

$batchMap = @{
    anchors = @(
        "picket_team_d_albedo.png",
        "frigate_team_d_albedo.png",
        "medium_cruiser_team_d_albedo.png",
        "carrier_team_d_albedo.png"
    )
    escorts = @(
        "patrol_team_d_albedo.png",
        "picket_team_d_albedo.png",
        "fighter_team_d_albedo.png",
        "bomber_team_d_albedo.png",
        "pd_craft_team_d_albedo.png",
        "drone_team_d_albedo.png",
        "frigate_team_d_albedo.png",
        "missile_boat_team_d_albedo.png",
        "ciws_corvette_team_d_albedo.png"
    )
    cruisers = @(
        "light_cruiser_team_d_albedo.png",
        "medium_cruiser_team_d_albedo.png",
        "cruiser_team_d_albedo.png",
        "battlecruiser_team_d_albedo.png",
        "battleship_team_d_albedo.png",
        "dreadnought_team_d_albedo.png",
        "supership_team_d_albedo.png"
    )
    support = @(
        "stealth_ship_team_d_albedo.png",
        "carrier_team_d_albedo.png",
        "drone_carrier_team_d_albedo.png",
        "transport_team_d_albedo.png",
        "miner_team_d_albedo.png",
        "hauler_team_d_albedo.png"
    )
    all = @(
        "patrol_team_d_albedo.png",
        "picket_team_d_albedo.png",
        "stealth_ship_team_d_albedo.png",
        "fighter_team_d_albedo.png",
        "bomber_team_d_albedo.png",
        "pd_craft_team_d_albedo.png",
        "drone_team_d_albedo.png",
        "frigate_team_d_albedo.png",
        "missile_boat_team_d_albedo.png",
        "ciws_corvette_team_d_albedo.png",
        "light_cruiser_team_d_albedo.png",
        "medium_cruiser_team_d_albedo.png",
        "cruiser_team_d_albedo.png",
        "battlecruiser_team_d_albedo.png",
        "battleship_team_d_albedo.png",
        "dreadnought_team_d_albedo.png",
        "supership_team_d_albedo.png",
        "carrier_team_d_albedo.png",
        "drone_carrier_team_d_albedo.png",
        "transport_team_d_albedo.png",
        "miner_team_d_albedo.png",
        "hauler_team_d_albedo.png"
    )
}

$includeFilenames = $batchMap[$Batch]
if ($null -eq $includeFilenames -or $includeFilenames.Count -eq 0) {
    throw "No Team D filenames configured for batch '$Batch'"
}

$invokeParams = @{
    Faction = "team_d"
    ComfyApiUrl = $ComfyApiUrl
    PromptFile = $promptFile
    StyleLockPath = $styleLockPath
    ReferenceMapPath = $referenceMapPath
    OutputRoot = $OutputRoot
    ScratchRoot = $ScratchRoot
    AttemptsPerPrompt = $AttemptsPerPrompt
    IncludeFilenames = $includeFilenames
    PromptSuffix = $promptSuffix
    NegativePrompt = $negativePrompt
}

if ($Overwrite) { $invokeParams.Overwrite = $true }
if ($DryRun) { $invokeParams.DryRun = $true }

& $generatorPath @invokeParams

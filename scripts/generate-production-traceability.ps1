param(
    [string]$Backlog = "docs/UNBOUNDED_GAME_EXPANSION_BACKLOG.md",
    [string]$Output = "docs/PRODUCTION_FEATURE_TRACEABILITY.csv"
)

$ErrorActionPreference = "Stop"

$profiles = @{
    1 = @("LIVE", "UISystem; HotkeyRegistry; PerformanceGuardrails; DevOverlay", "overlays; controls; F3 overlay", "runtime settings and diagnostics", "UiOverlayInvariantTest; HotkeyRegistryTest; PerformanceGuardrailsTest", "Audit still requires manual accessibility and complete-suite validation.")
    2 = @("PARTIAL", "FirstHourOnboardingSystem; ExperienceRuntime; TutorialSystem", "tutorial prompts; experience settings", "experience settings store", "FirstHourExperienceTest; TutorialWarpRegressionTest", "Several modes and accessibility options need manual reachability verification.")
    3 = @("PARTIAL", "TacticalCombatDepthSystem; Ship; Turret; UISystem", "tactical HUD; tactical controls", "campaign fleet records where applicable", "TacticalCombatDepthSystemTest; combat regression tests", "Feature breadth is modeled and tested unevenly; manual tactical acceptance pass remains.")
    4 = @("PARTIAL", "FleetBuildingSystem; CampaignSystem; Renderer", "fleet board; shipyard; roster archive", "persistent fleet serialization", "FleetBuildingSystemTest; CampaignStrategicCommandHudTest", "Roster identity is live; full refit and construction workflows remain incomplete.")
    5 = @("MODELED_ONLY", "StrategicCampaignExpansionSystem", "compact readout API only", "strategicExpansionState", "StrategicCampaignExpansionSystemTest", "Parallel model is not authoritative for the live strategic map.")
    6 = @("PARTIAL", "EconomyLogisticsIndustrySystem; CampaignSystem", "resource board; hub services", "economyExpansionState", "CampaignEconomyDiplomacyExpansionSystemTest; CampaignStrategicCommandHudTest", "Live travel and hub recovery are wired; markets, AI consumption, and full screens remain.")
    7 = @("PARTIAL", "DiplomacyNarrativeCrewSystem; CampaignSystem", "comms board; bridge logs", "diplomacyNarrativeState", "CampaignEconomyDiplomacyExpansionSystemTest; CampaignStrategicCommandHudTest", "Hub decisions affect reputation; broader narrative consequences remain bootstrap-heavy.")
    8 = @("MODELED_ONLY", "OperationsInformationCommandSystem", "compact readout API only", "operationsExpansionState", "OperationsInformationCommandSystemTest", "Mission catalogs are not fully instantiated by the live encounter generator.")
    9 = @("MODELED_ONLY", "OperationsInformationCommandSystem", "compact readout API only", "operationsExpansionState", "OperationsInformationCommandSystemTest", "Information-warfare model is not authoritative for tactical contacts.")
    10 = @("PARTIAL", "OperationsInformationCommandSystem; CampaignSystem", "live strikes UI plus compact expansion readout", "operationsExpansionState and live strike checkpoint fields", "OperationsInformationCommandSystemTest; CampaignStrategicStrikeCounterplayTest", "Existing live strikes are substantial; expansion support-package model remains parallel.")
    11 = @("PARTIAL", "OperationsInformationCommandSystem; Renderer; CampaignSystem", "campaign command tabs and tactical HUD", "operationsExpansionState", "OperationsInformationCommandSystemTest; CampaignStrategicUiReadabilityTest", "Existing UI is live; expansion preferences are not all surfaced.")
    12 = @("CATALOG_ONLY", "ProductionReadinessLongevitySystem", "asset manifests and existing presentation", "productionReadinessState", "ProductionReadinessLongevitySystemTest", "Capability booleans are not verified final-asset inventories.")
    13 = @("CATALOG_ONLY", "ProductionReadinessLongevitySystem", "compact readout API only", "productionReadinessState", "ProductionReadinessLongevitySystemTest", "Save-slot, replay, and longevity entries are catalogs, not complete flows.")
    14 = @("CATALOG_ONLY", "ProductionReadinessLongevitySystem", "developer documentation", "productionReadinessState", "ProductionReadinessLongevitySystemTest", "Architecture and tooling entries require executable enforcement.")
    15 = @("CATALOG_ONLY", "ProductionReadinessLongevitySystem", "test inventory", "productionReadinessState", "ProductionReadinessLongevitySystemTest", "Matrix entries are not all executable CI suites.")
    16 = @("CATALOG_ONLY", "StretchGoalsFleetDoctrineSystem", "compact readout API only", "fleetDoctrineExpansionState", "StretchGoalsFleetDoctrineSystemTest", "Stretch goals are capability flags until implemented or de-scoped.")
    17 = @("DESIGN_ONLY", "docs/CANDIDATE_EXTRACTION_PACKS.md", "documentation index", "n/a", "StretchGoalsFleetDoctrineSystemTest", "Index entries are planning references.")
    18 = @("MODELED_ONLY", "StretchGoalsFleetDoctrineSystem", "compact readout API only", "fleetDoctrineExpansionState", "StretchGoalsFleetDoctrineSystemTest", "Doctrine state is not yet applied to live tactical orders.")
    19 = @("MODELED_ONLY", "DeepCampaignSimulationSystem", "compact readout API only", "deepCampaignExpansionState", "DeepCampaignSimulationSystemTest", "Stations and locations are seeded domain state, not authoritative live entities.")
    20 = @("MODELED_ONLY", "DeepCampaignSimulationSystem", "compact readout API only", "deepCampaignExpansionState", "DeepCampaignSimulationSystemTest", "Personnel and civilian state are not driven by normal campaign outcomes.")
    21 = @("MODELED_ONLY", "DeepCampaignSimulationSystem", "compact readout API only", "deepCampaignExpansionState", "DeepCampaignSimulationSystemTest", "Planning, intelligence, and espionage are not playable flows.")
    22 = @("MODELED_ONLY", "DeepCampaignSimulationSystem", "compact readout API only", "deepCampaignExpansionState", "DeepCampaignSimulationSystemTest", "Hazards and resource ecology do not advance through live campaign time.")
    23 = @("MODELED_ONLY", "DeepCampaignSimulationSystem", "compact readout API only", "deepCampaignExpansionState", "DeepCampaignSimulationSystemTest", "Faction identity and politics are descriptive state, not live simulation.")
    24 = @("MODELED_ONLY", "DeepCampaignSimulationSystem", "compact readout API only", "deepCampaignExpansionState", "DeepCampaignSimulationSystemTest", "Crises and recovery options require live triggers and choices.")
    25 = @("MODELED_ONLY", "DeepCampaignSimulationSystem", "compact readout API only", "deepCampaignExpansionState", "DeepCampaignSimulationSystemTest", "Endgames and challenges are not launchable campaign flows.")
    26 = @("MODELED_ONLY", "CommunityContentSystem; config/content-pack", "compact readout API only", "communityContentState", "CommunityContentSystemTest", "CSV examples and editor backend are not loaded or rendered by production UI.")
    27 = @("DESIGN_ONLY", "docs/ADDITIONAL_CANDIDATE_EXTRACTION_PACKS.md", "documentation index", "n/a", "CommunityContentSystemTest", "Index entries are planning references.")
}

$section = 0
$subsection = ""
$rows = New-Object System.Collections.Generic.List[object]
foreach ($line in Get-Content $Backlog) {
    if ($line -match '^## (\d+)\.\s+(.+)$') {
        $section = [int]$Matches[1]
        if ($section -gt 27) { break }
        $subsection = ""
        continue
    }
    if ($line -match '^###\s+(.+)$') {
        $subsection = $Matches[1]
        continue
    }
    if ($section -lt 1 -or $section -gt 27 -or $line -notmatch '^- \[x\]\s+(.+)$') { continue }
    $profile = $profiles[$section]
    $rows.Add([pscustomobject]@{
        Section = $section
        Subsection = $subsection
        Feature = $Matches[1]
        Classification = $profile[0]
        Evidence = $profile[1]
        PlayerSurface = $profile[2]
        Persistence = $profile[3]
        TestFamily = $profile[4]
        KnownLimitation = $profile[5]
    })
}

$rows | Export-Csv -Path $Output -NoTypeInformation -Encoding utf8
Write-Output "Wrote $($rows.Count) traceability rows to $Output"

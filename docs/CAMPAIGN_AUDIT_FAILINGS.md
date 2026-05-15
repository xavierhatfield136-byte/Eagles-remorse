# Campaign Audit Failings

Date: 2026-05-15
Status: Active audit ledger

## Purpose

This document tracks campaign-layer failures discovered during live audit passes against the current implementation.

It is meant to answer:

- what has already been verified
- what was broken and has now been fixed
- what still fails in the broader campaign suite
- which failures look like gameplay regressions versus stale test expectations

This is an audit ledger, not a design spec.

## Audit Snapshot

Full suite command used:

```text
.\gradlew.bat test --tests Campaign*
```

Snapshot from the current broad pass:

- total campaign tests run: `136`
- currently passing: `104`
- currently failing: `32`

Broader project command used afterward:

```text
.\gradlew.bat test
```

Broader project snapshot:

- total tests run: `232`
- currently failing: `48`
- skipped: `1`

That broader pass means the campaign audit has now uncovered:

- `32` campaign-prefixed failures
- `16` additional non-campaign failures that still affect overall game correctness

## Recently Confirmed And Fixed

These failures were reproduced during audit and then fixed in code:

- hostile overmap contacts could be selected in the UI but still fail long-range strike launch because the strike backend only resolved `StrategicTaskForce` targets, not `GalaxySearchGroup` contacts
- hostile search-group interception in open space could stop travel and raise alert without opening a combat/resolve prompt if no nearby anchor location existed
- overmap hostile contact locks were too static; selected hostile contacts did not stay tied to the live hostile search group as it moved and changed intel state

Relevant implementation files:

- [CampaignSystem.java](/C:/Users/xavie/IdeaProjects/game/src/CampaignSystem.java:2015)
- [UiState.java](/C:/Users/xavie/IdeaProjects/game/src/UiState.java:171)

Regression coverage added or updated:

- [CampaignStrategicCommandHudTest.java](/C:/Users/xavie/IdeaProjects/game/test/CampaignStrategicCommandHudTest.java:616)
- [CampaignOvermapEncounterFlowTest.java](/C:/Users/xavie/IdeaProjects/game/test/CampaignOvermapEncounterFlowTest.java:50)
- [CampaignStrategicLoopIntegrationTest.java](/C:/Users/xavie/IdeaProjects/game/test/CampaignStrategicLoopIntegrationTest.java:49)

## Green Verification Slice

The following audit slice is currently passing:

```text
.\gradlew.bat test --tests CampaignStrategicCommandHudTest --tests CampaignOvermapEncounterFlowTest --tests CampaignStrategicLoopIntegrationTest --tests CampaignHubEconomyTest --tests CampaignStrategicUiReadabilityTest --tests CampaignStrategicStrikeCounterplayTest --tests CampaignStrategicTravelPressureTest
```

This means the following campaign systems are now in a healthier state:

- overmap hostile contact selection
- strike-console hostile engagement flow
- open-space hostile intercept prompting
- site-entry and AOI resolution flow
- hub economy flow
- strategic HUD action availability
- route-risk and search-group pressure basics

## Current Failing Test Inventory

### CampaignCompatibilityOverhaulTest

File:

- [CampaignCompatibilityOverhaulTest.java](/C:/Users/xavie/IdeaProjects/game/test/CampaignCompatibilityOverhaulTest.java:1)

Failing tests:

- `escortSideObjectiveProtectsEscortTitanInsteadOfPlayerHull`
- `lateDestroySectorsAccumulatePressureReinforcements`
- `sectorFourUsesAuthoredDestroyProgressInsteadOfCaptureHold`
- `coalitionTaskGroupsUnlockForLaterSectorsAndSurviveCheckpointRestore`
- `protectedAssetLossesShapeSectorOutcomeRewards`
- `sectorTwentyOneRequiresOrbitalAnchorsBeforeLunaSweepResolves`
- `escortObjectiveNeedsFormationPresenceToAdvance`
- `sectorThirteenRequiresJammerTriadKillsInsteadOfScreenKills`

Likely subsystem:

- authored sector scripting
- escort logic
- coalition persistence
- sector outcome reward logic

Observed failure shapes:

- escort-sector tests are often failing because expected escort ships or expected screen ships are `null`, which suggests the sectors are not spawning the authored escort/screen content the tests expect
- sector 4 destroy progress is reporting `1.0` instead of the expected `4.0`, which suggests authored blocker accounting is no longer matching the scripted objective goal
- late destroy sectors are not spawning the expected reinforcement pressure wave
- coalition reward / checkpoint restore assertions are returning `false`, which suggests fleet-unlock persistence is either missing or no longer triggered the same way

### CampaignFleetHubMenuRegressionTest

File:

- [CampaignFleetHubMenuRegressionTest.java](/C:/Users/xavie/IdeaProjects/game/test/CampaignFleetHubMenuRegressionTest.java:1)

Failing tests:

- `routeChoiceUpdatesPendingSectorBeforeLaunch`
- `menuExitCheckpointClampsRunawayCampaignWorldDimensionsToSubzoneCaps`

Likely subsystem:

- fleet hub route launch flow
- checkpoint serialization / config clamping

Observed failure shapes:

- route-choice launch is returning `false` where the test expects a valid detour selection / launch path
- checkpoint world dimensions are preserving `20000` instead of clamping back to `5000`

### CampaignLoreOverhaulTest

File:

- [CampaignLoreOverhaulTest.java](/C:/Users/xavie/IdeaProjects/game/test/CampaignLoreOverhaulTest.java:1)

Failing tests:

- `campaignStartUsesTradeHubLoreAndStarterBlueFleet`
- `lateCampaignSectorsExposePlanetaryLandmarksInHud`
- `escortSectorsUseTitanFlagships`
- `bossSectorsEscalateToTitanAndMothershipFlagships`

Likely subsystem:

- authored lore presentation
- starting roster identity
- boss-sector content identity
- landmark/HUD descriptive text

Observed failure shapes:

- several assertions are simple `expected true but was false`, which suggests lore strings / HUD copy have drifted from the authored expectations
- escort-sector and boss-sector assertions are failing on `expected: not <null>`, which suggests the expected authored flagship / boss spawns are missing entirely in those sectors

### CampaignMissionSectionsTest

File:

- [CampaignMissionSectionsTest.java](/C:/Users/xavie/IdeaProjects/game/test/CampaignMissionSectionsTest.java:1)

Failing tests:

- `supportMarkersUseClearFactionFacingNames`
- `lockedMissionSectionHudSaysToFlyFlagshipToNextPocket`
- `sectorTwoHudExplainsWinStateAndImmediateTask`
- `nearestStrategicLandmarkFindsNamedPocketMarker`
- `missionThreeInitialBlockersAreMarkedAndReliefRouteIsInitialized`
- `strategicLandmarksExposeAuthoredSectorIdentityWithoutScannerDuplicates`
- `campaignDiscoveryZonesSeedAmbientWorldContent`

Likely subsystem:

- mission-section HUD language
- discovery seeding
- support-marker naming
- landmark placement / lookup

Observed failure shapes:

- support contacts still read like generic categories instead of faction-facing labels
- staged-mission HUD text is not explaining cleared pocket / next pocket flow the way the tests expect
- sector 2 HUD copy is not surfacing the exact win-state / current-task language expected by the regression suite
- strategic landmark lookup can fail with `ArrayIndexOutOfBoundsException`, which suggests some sectors are producing zero landmarks where the tests expect at least one
- mission 3 is exposing `0` marked destroy targets where the test expects `6`

### CampaignMissionTransitionWeaponResetTest

File:

- [CampaignMissionTransitionWeaponResetTest.java](/C:/Users/xavie/IdeaProjects/game/test/CampaignMissionTransitionWeaponResetTest.java:1)

Failing tests:

- `pruneTransientUnitsClearsStalePrimaryGunLocksAcrossMissionTransitions`

Likely subsystem:

- mission transition cleanup
- combat-state carryover

Observed failure shape:

- stale primary-gun lock cleanup across mission transitions is not being fully cleared

### CampaignObjectiveActivationTest

File:

- [CampaignObjectiveActivationTest.java](/C:/Users/xavie/IdeaProjects/game/test/CampaignObjectiveActivationTest.java:1)

Failing tests:

- `sectorTwoTimeoutSucceedsWhenConvoyQuotaSurvives`
- `sectorTwoFailureCallsOutConvoyQuotaBreak`
- `sectorStartEmitsMissionBanterDuringLivePlay`
- `objectiveAssetFailureIsDeferredWhileTransitToNextPocketIsLocked`
- `destroyObjectiveCanCompleteOnTheSameTickAsSectorTimeout`
- `genericTimeoutFailureCallsOutUnfinishedObjective`

Likely subsystem:

- objective timeout resolution
- convoy quota success/fail rules
- mission-start banter dispatch
- staged mission activation rules

Observed failure shapes:

- timeout resolution is not matching the authored convoy-success / timeout-failure rules
- same-tick destroy completion versus timeout ordering is not resolving in the expected priority
- mission-start banter event dispatch is not appearing when expected
- future-pocket loss conditions appear to be activating at the wrong time relative to section-travel locks

### CampaignSectorOneDurationTest

File:

- [CampaignSectorOneDurationTest.java](/C:/Users/xavie/IdeaProjects/game/test/CampaignSectorOneDurationTest.java:1)

Failing tests:

- `firstCampaignMissionAutoWinsAtTwoHundredSeconds`
- `firstCampaignMissionUsesTwoHundredSecondWindow`

Likely subsystem:

- sector 1 scripted duration / auto-win behavior

Observed failure shapes:

- sector 1 is no longer using the expected `200` second goal / time-limit pairing
- the first mission is not auto-completing the way the legacy regression expects at the timer threshold

### CampaignStrategicFleetIdentityTest

File:

- [CampaignStrategicFleetIdentityTest.java](/C:/Users/xavie/IdeaProjects/game/test/CampaignStrategicFleetIdentityTest.java:1)

Failing tests:

- `stealthFleetSoftensContactConfidenceAtTheSameDetectionRange`

Likely subsystem:

- strategic fleet role identity
- stealth impact on hostile classification

Observed failure shape:

- stealth-heavy fleets are not lowering hostile contact-confidence rank enough at equal range

### CampaignZoneLayoutTest

File:

- [CampaignZoneLayoutTest.java](/C:/Users/xavie/IdeaProjects/game/test/CampaignZoneLayoutTest.java:1)

Failing tests:

- `campaignWorldContainsSeparatedZonesSoSectorTwoSpawnDoesNotClampToMapEdge`

Likely subsystem:

- mission world sizing
- arrival placement
- multi-zone encounter layout

Observed failure shape:

- sector 2 world / arrival / landmark layout is not matching the expected separated-zone geometry

## Initial Triage

Highest-signal remaining clusters for gameplay correctness:

1. `CampaignObjectiveActivationTest`
2. `CampaignCompatibilityOverhaulTest`
3. `CampaignMissionSectionsTest`
4. `CampaignFleetHubMenuRegressionTest`

Highest-signal remaining clusters for authored presentation / campaign identity:

1. `CampaignLoreOverhaulTest`
2. `CampaignZoneLayoutTest`
3. `CampaignStrategicFleetIdentityTest`

## Additional Non-Campaign Failures Discovered During Full Suite Pass

These were not part of the original `Campaign*` audit slice, but they failed once the full project suite was run.

### Fog Of War

File:

- [FogOfWarSystemTest.java](/C:/Users/xavie/IdeaProjects/game/test/FogOfWarSystemTest.java:1)

Failing tests:

- `highSensorPowerMarksCampaignAnomalySites`

Likely subsystem:

- sensor/fog integration with campaign anomaly contacts

Observed failure shape:

- high sensor power is not surfacing campaign anomaly discovery sites as `ANOMALY` interest signals

### Hyperweapon Behavior

File:

- [HyperweaponBehaviorTest.java](/C:/Users/xavie/IdeaProjects/game/test/HyperweaponBehaviorTest.java:1)

Failing tests:

- `redSupershipSlugDetonatesOnFirstShipHit`
- `blueHyperweaponPulseDeletesNonTitansAndHalvesTitans`
- `redHyperweaponCreatesStasisFieldThatPinsTargets`
- `greenHyperweaponFiresOversizedDirectBeam`
- `redHyperweaponSlugDetonatesOnFirstShipHit`
- `yellowHyperweaponWarheadKillsUnshieldedTierTwoTargetsButOnlyStripsShieldedOnes`

Likely subsystem:

- faction hyperweapon damage / detonation rules
- projectile-effect implementation

Observed failure shapes:

- all failing tests in this class are tripping on `expected: not <null>` immediately after attempting to charge and fire the hyperweapon shot
- that suggests the primary failure is upstream: the expected superweapon projectile is not being emitted at all, rather than only the downstream damage logic being slightly off

### Integrity Field Containment

File:

- [IntegrityFieldContainmentTest.java](/C:/Users/xavie/IdeaProjects/game/test/IntegrityFieldContainmentTest.java:1)

Failing tests:

- `singleFireRoomStaysContainedWhileIntegrityFieldIsOperational`
- `singleDamagedRoomGetsAutomaticIntegrityProtection`

Likely subsystem:

- room integrity containment logic
- fire spread protection logic

Observed failure shapes:

- neighboring rooms are losing health when the tests expect fully contained fire damage
- automatic integrity protection is not blunting single-room penetrating damage enough to beat the unprotected comparison ship

### Interior Projectile Damage Patterns

File:

- [InteriorProjectileDamagePatternTest.java](/C:/Users/xavie/IdeaProjects/game/test/InteriorProjectileDamagePatternTest.java:1)

Failing tests:

- `bluePierceDamagesMultipleRoomsInALine`
- `missilesDamageMoreRoomsThanRedExplosiveRounds`

Likely subsystem:

- interior room damage propagation
- projectile damage pattern tuning

Observed failure shapes:

- blue piercing bolts are not carrying through multiple rooms
- red explosive rounds are not even damaging the minimum nearby-room set the tests expect
- missile blast profile is therefore also failing the relative-width comparison against red explosive rounds

### Missile Behavior

Files:

- [MissileHoldFireRegressionTest.java](/C:/Users/xavie/IdeaProjects/game/test/MissileHoldFireRegressionTest.java:1)
- [MissileRoleBehaviorTest.java](/C:/Users/xavie/IdeaProjects/game/test/MissileRoleBehaviorTest.java:1)

Failing tests:

- `missileTurretsRespectTheOneSecondReloadFloor`
- `fastMissilesCanFireAcrossTheirCurrentSector`
- `interceptMissilesReloadThreeTimesFasterAndRenderSmaller`
- `heavyMissilesHaveExtendedRangeAndSmallerVisuals`

Likely subsystem:

- missile role stat tables
- missile reload logic
- missile range and visual scaling

Observed failure shapes:

- some ready missile turrets are not firing at all in direct unit tests
- FAST missiles are not firing across the sector when the AI test expects them to
- one reflection-based test is failing because `AISystem.missileRangeForTurret(GameContext,Ship,Turret,double)` no longer exists with that signature

Interpretation:

- this cluster may include both real missile behavior regressions and at least one stale test tied to an older helper signature

### Shooting Range Titan Layout

File:

- [ShootingRangeTitanSpawnTest.java](/C:/Users/xavie/IdeaProjects/game/test/ShootingRangeTitanSpawnTest.java:1)

Failing tests:

- `shootingRangeTitanLayoutCanResetToDefaultWall`

Likely subsystem:

- shooting-range layout reset behavior

Observed failure shape:

- clearing the titan layout resets to a wall that only has `1` `STATIC_TURRET` target where the test expects at least `2`

## Second-Wave Failure Themes

After the broad `.\gradlew.bat test` pass, the failure picture is no longer random.

The largest themes now look like this:

### Theme 1: Campaign Script Drift

Most of the remaining campaign failures read like authored mission logic and authored text have moved apart.

Examples:

- objective timing expectations
- convoy quota success/failure language
- escort-side objective ownership
- sector-specific destroy-target logic
- route/landmark/support-marker phrasing
- starter-lore and boss/escort identity expectations

This suggests the campaign implementation evolved faster than the authored regression suite and supporting presentation layer were kept aligned.

### Theme 2: Strategic Identity Tuning Drift

Some strategic systems still work, but no longer match the expected strength of their design promises.

Examples:

- stealth-heavy fleets not softening hostile classification enough
- zone-layout expectations no longer matching arrival geometry
- some support/landmark identity layers no longer matching authored section names

This looks less like a hard crash problem and more like tuning/identity drift.

### Theme 3: Core Combat Regression Band Outside Campaign

The broader pass found a separate non-campaign failure band that should be treated as its own workstream:

- fog-of-war anomaly interest signal behavior
- hyperweapon impact / detonation / stasis / beam behavior
- missile role stats and reload/range/visual rules
- interior projectile room-damage patterns
- integrity field containment

These are not just stale campaign assertions. They look like core combat mechanics regressions or incomplete overhauls.

## Notes

This file should be updated as:

- failures are fixed
- stale tests are rewritten to match current campaign rules
- newly discovered regressions appear during broader audit passes

## Detailed Checklist

Legend:

- `Type: Real` means it currently looks like a real behavior or content regression
- `Type: Stale` means it currently looks more like an outdated test expectation
- `Type: Mixed` means it may contain both implementation drift and stale test assumptions

### CampaignCompatibilityOverhaulTest

- `[ ] escortSideObjectiveProtectsEscortTitanInsteadOfPlayerHull`
  Complaint: expected escort ship `not <null>`
  Likely root cause: sector 8 escort content is not spawning or is no longer being assigned to `campaign.escortShip`
  Likely code area: sector-start authored spawn scripting, escort-sector setup, `startSector`
  Type: `Real`

- `[ ] lateDestroySectorsAccumulatePressureReinforcements`
  Complaint: expected reinforcement pressure wave, got no additional enemy count
  Likely root cause: timed authored reinforcement trigger is missing, disabled, or phase cursor is not advancing
  Likely code area: campaign authored wave scripting, mission update progression, pressure-wave hooks
  Type: `Real`

- `[ ] sectorFourUsesAuthoredDestroyProgressInsteadOfCaptureHold`
  Complaint: expected progress `4.0`, got `1.0`
  Likely root cause: sector 4 still appears to be resolving via generic objective counting instead of authored blocker accounting
  Likely code area: sector 4 objective script, authored objective hostiles, destroy-progress accumulation
  Type: `Real`

- `[ ] coalitionTaskGroupsUnlockForLaterSectorsAndSurviveCheckpointRestore`
  Complaint: expected coalition fleet unlock state `true`, got `false`
  Likely root cause: story fleet reward unlocks are not being persisted or not being restored into later sectors
  Likely code area: story-fleet reward grant path, checkpoint capture/apply, coalition join flags
  Type: `Real`

- `[ ] protectedAssetLossesShapeSectorOutcomeRewards`
  Complaint: expected shattered-loss outcome effects, got no matching branch/favor result
  Likely root cause: protected-asset losses are not feeding sector outcome reward text or doctrine/favor penalties
  Likely code area: sector outcome reward logic, protected-asset loss accounting
  Type: `Real`

- `[ ] sectorTwentyOneRequiresOrbitalAnchorsBeforeLunaSweepResolves`
  Complaint: expected non-anchor screen ship `not <null>`
  Likely root cause: sector 21 is not spawning the authored screen/anchor mix expected by the objective script
  Likely code area: sector 21 spawn script, authored objective target population
  Type: `Real`

- `[ ] escortObjectiveNeedsFormationPresenceToAdvance`
  Complaint: expected escort ship `not <null>`
  Likely root cause: escort-sector setup is not creating or registering the escort ship before formation logic runs
  Likely code area: escort-sector setup, escort progression logic
  Type: `Real`

- `[ ] sectorThirteenRequiresJammerTriadKillsInsteadOfScreenKills`
  Complaint: expected screen ship `not <null>`
  Likely root cause: sector 13 is not spawning the jammer-triad-with-screen composition expected by the objective test
  Likely code area: sector 13 authored hostile setup
  Type: `Real`

### CampaignFleetHubMenuRegressionTest

- `[ ] routeChoiceUpdatesPendingSectorBeforeLaunch`
  Complaint: expected valid route-choice selection / launch path, got `false`
  Likely root cause: route-choice state is not being accepted in the fleet hub before launch
  Likely code area: `selectRouteChoice`, `launchPendingEpisode`, fleet-hub route-choice state
  Type: `Real`

- `[ ] menuExitCheckpointClampsRunawayCampaignWorldDimensionsToSubzoneCaps`
  Complaint: expected checkpoint world width `5000`, got `20000`
  Likely root cause: menu-exit checkpoint capture is preserving configured world dimensions instead of clamping
  Likely code area: checkpoint capture serialization, world-dimension normalization
  Type: `Real`

### CampaignLoreOverhaulTest

- `[ ] campaignStartUsesTradeHubLoreAndStarterBlueFleet`
  Complaint: multiple lore/starting-roster assertions expected `true`, got `false`
  Likely root cause: campaign-start HUD detail, starter roster, or landmark identity has drifted away from authored lore set
  Likely code area: initial campaign bootstrapping, objective detail text, starting persistent fleet, landmark seeding
  Type: `Mixed`

- `[ ] lateCampaignSectorsExposePlanetaryLandmarksInHud`
  Complaint: expected late-sector landmark/HUD strings, got `false`
  Likely root cause: late-sector landmark set or HUD descriptive text no longer matches authored Earth/Luna identity expectations
  Likely code area: landmark generation, objective-detail text generation
  Type: `Mixed`

- `[ ] escortSectorsUseTitanFlagships`
  Complaint: expected escort ship `not <null>`
  Likely root cause: escort sectors are not spawning their authored titan escort flagships
  Likely code area: escort-sector spawn scripts
  Type: `Real`

- `[ ] bossSectorsEscalateToTitanAndMothershipFlagships`
  Complaint: expected boss spawn for sector 7 `not <null>`
  Likely root cause: boss-sector authored flagship spawns are missing or no longer assigned to `bossTargetId`
  Likely code area: boss-sector spawn scripting, boss registration
  Type: `Real`

### CampaignMissionSectionsTest

- `[ ] supportMarkersUseClearFactionFacingNames`
  Complaint: faction-facing support markers still read like anonymous categories
  Likely root cause: support marker labeling is too generic for faction contacts
  Likely code area: support-marker label generation, discovery-site label mapping
  Type: `Mixed`

- `[ ] lockedMissionSectionHudSaysToFlyFlagshipToNextPocket`
  Complaint: staged mission HUD is not describing cleared pocket and next-pocket routing as expected
  Likely root cause: locked-section HUD copy no longer matches authored staged-mission phrasing
  Likely code area: objective-detail text generation for section-locked missions
  Type: `Stale`

- `[ ] sectorTwoHudExplainsWinStateAndImmediateTask`
  Complaint: sector 2 HUD copy missing expected win-state/current-task language
  Likely root cause: authored HUD copy changed or got simplified without test updates
  Likely code area: objective-detail text generation for sector 2
  Type: `Stale`

- `[ ] nearestStrategicLandmarkFindsNamedPocketMarker`
  Complaint: `ArrayIndexOutOfBoundsException`, zero landmarks where at least one was expected
  Likely root cause: some staged sectors are not producing strategic landmarks at all
  Likely code area: landmark generation for mission sections / staged sectors
  Type: `Real`

- `[ ] missionThreeInitialBlockersAreMarkedAndReliefRouteIsInitialized`
  Complaint: expected `6` destroy markers, got `0`
  Likely root cause: mission 3 blocker hostiles are not being registered as authored objective markers
  Likely code area: mission 3 objective marker population, authored hostile registration
  Type: `Real`

- `[ ] strategicLandmarksExposeAuthoredSectorIdentityWithoutScannerDuplicates`
  Complaint: staged sectors are not surfacing the expected named authored pockets as landmarks
  Likely root cause: landmark layer is missing or underpopulated for mission sections
  Likely code area: strategic landmark generation
  Type: `Real`

- `[ ] campaignDiscoveryZonesSeedAmbientWorldContent`
  Complaint: expected ambient support/logistics ships and defenses are missing
  Likely root cause: mission 10 pocket seeding is underpopulating ambient ships or turret anchors
  Likely code area: discovery-zone ambient seeding, campaign pocket world population
  Type: `Real`

### CampaignMissionTransitionWeaponResetTest

- `[ ] pruneTransientUnitsClearsStalePrimaryGunLocksAcrossMissionTransitions`
  Complaint: stale primary gun locks survive a mission transition
  Likely root cause: combat lock state is not fully cleared during transient-unit pruning
  Likely code area: mission-transition cleanup, weapon/target reset paths
  Type: `Real`

### CampaignObjectiveActivationTest

- `[ ] sectorTwoTimeoutSucceedsWhenConvoyQuotaSurvives`
  Complaint: sector 2 timeout is not resolving as success when convoy quota survives
  Likely root cause: convoy quota success path is not winning at timeout
  Likely code area: timeout resolution ordering, convoy quota checks
  Type: `Real`

- `[ ] sectorTwoFailureCallsOutConvoyQuotaBreak`
  Complaint: convoy quota failure is not producing the expected failure outcome text/path
  Likely root cause: sector 2 fail-state handling or fail text has drifted
  Likely code area: sector 2 failure resolution, game-over text path
  Type: `Mixed`

- `[ ] sectorStartEmitsMissionBanterDuringLivePlay`
  Complaint: mission-start banter event did not dispatch
  Likely root cause: scripted banter trigger is missing, delayed incorrectly, or renamed
  Likely code area: sector start event dispatch, audio-system integration
  Type: `Real`

- `[ ] objectiveAssetFailureIsDeferredWhileTransitToNextPocketIsLocked`
  Complaint: future-pocket loss appears to trigger before the travel lock is lifted
  Likely root cause: objective-asset fail evaluation is not respecting active mission section lock state
  Likely code area: objective failure evaluation, mission-section travel lock logic
  Type: `Real`

- `[ ] destroyObjectiveCanCompleteOnTheSameTickAsSectorTimeout`
  Complaint: same-tick destroy completion is losing to timeout handling
  Likely root cause: update ordering favors timeout fail before authored objective secure
  Likely code area: campaign update ordering, objective-secure vs timeout resolution
  Type: `Real`

- `[ ] genericTimeoutFailureCallsOutUnfinishedObjective`
  Complaint: unresolved timeout path is not producing the expected generic objective-failure outcome
  Likely root cause: timeout fail path or game-over text has drifted
  Likely code area: generic timeout resolution
  Type: `Mixed`

### CampaignSectorOneDurationTest

- `[ ] firstCampaignMissionAutoWinsAtTwoHundredSeconds`
  Complaint: mission 1 is not completing/queuing the next episode at the expected threshold
  Likely root cause: sector 1 timing or victory path has changed
  Likely code area: sector 1 script, timeout/victory handling
  Type: `Mixed`

- `[ ] firstCampaignMissionUsesTwoHundredSecondWindow`
  Complaint: sector 1 no longer reports a `200` second goal/time limit pair
  Likely root cause: authored sector timing changed without the regression being updated, or timing regressed
  Likely code area: sector 1 initialization
  Type: `Mixed`

### CampaignStrategicFleetIdentityTest

- `[ ] stealthFleetSoftensContactConfidenceAtTheSameDetectionRange`
  Complaint: stealth-heavy fleet does not reduce contact-confidence rank enough at equal range
  Likely root cause: stealth role influence on search-group detection/classification is too weak
  Likely code area: strategic role profile, search-group confidence logic
  Type: `Real`

### CampaignZoneLayoutTest

- `[ ] campaignWorldContainsSeparatedZonesSoSectorTwoSpawnDoesNotClampToMapEdge`
  Complaint: sector 2 arrival/world/landmark layout is not matching separated-zone expectations
  Likely root cause: mission world sizing, sector 2 placement, or landmark seeding drifted from the old geometry assumptions
  Likely code area: recommended world sizing, mission-subzone placement, sector 2 spawn layout
  Type: `Mixed`

### FogOfWarSystemTest

- `[ ] highSensorPowerMarksCampaignAnomalySites`
  Complaint: high sensor power does not surface campaign anomaly sites as `ANOMALY` interest signals
  Likely root cause: campaign discovery sites are not being translated into fog-of-war sensor-interest signals
  Likely code area: fog sensor-interest generation, campaign discovery-site integration
  Type: `Real`

### HyperweaponBehaviorTest

- `[ ] redSupershipSlugDetonatesOnFirstShipHit`
  Complaint: expected hyperweapon shot `not <null>`
  Likely root cause: superweapon firing path is not emitting the projectile at all
  Likely code area: `tryFireSuperweaponAt`, superweapon charge/poll flow
  Type: `Real`

- `[ ] blueHyperweaponPulseDeletesNonTitansAndHalvesTitans`
  Complaint: expected hyperweapon shot `not <null>`
  Likely root cause: same upstream firing failure as above
  Likely code area: superweapon fire emission
  Type: `Real`

- `[ ] redHyperweaponCreatesStasisFieldThatPinsTargets`
  Complaint: expected hyperweapon shot `not <null>`
  Likely root cause: same upstream firing failure as above
  Likely code area: superweapon fire emission
  Type: `Real`

- `[ ] greenHyperweaponFiresOversizedDirectBeam`
  Complaint: expected hyperweapon shot `not <null>`
  Likely root cause: same upstream firing failure as above
  Likely code area: superweapon fire emission
  Type: `Real`

- `[ ] redHyperweaponSlugDetonatesOnFirstShipHit`
  Complaint: expected hyperweapon shot `not <null>`
  Likely root cause: same upstream firing failure as above
  Likely code area: superweapon fire emission
  Type: `Real`

- `[ ] yellowHyperweaponWarheadKillsUnshieldedTierTwoTargetsButOnlyStripsShieldedOnes`
  Complaint: expected hyperweapon shot `not <null>`
  Likely root cause: same upstream firing failure as above
  Likely code area: superweapon fire emission
  Type: `Real`

### IntegrityFieldContainmentTest

- `[ ] singleFireRoomStaysContainedWhileIntegrityFieldIsOperational`
  Complaint: neighboring room health dropped to `0.97` instead of staying at `1.0`
  Likely root cause: contained damage-over-time is still leaking chip damage into adjacent rooms
  Likely code area: fire spread / room damage propagation / integrity containment
  Type: `Real`

- `[ ] singleDamagedRoomGetsAutomaticIntegrityProtection`
  Complaint: integrity-field-protected ship did not beat the unprotected comparison enough
  Likely root cause: automatic integrity mitigation is too weak or not applying for single-room penetrating damage
  Likely code area: integrity field defensive modifier, penetrating internal damage path
  Type: `Real`

### InteriorProjectileDamagePatternTest

- `[ ] bluePierceDamagesMultipleRoomsInALine`
  Complaint: blue piercing bolts are not damaging at least `2` rooms
  Likely root cause: blue pierce interior propagation is too shallow or disabled
  Likely code area: interior hit profile propagation for `BLUE_PIERCE`
  Type: `Real`

- `[ ] missilesDamageMoreRoomsThanRedExplosiveRounds`
  Complaint: red explosive rounds are not even damaging the expected nearby-room baseline
  Likely root cause: explosive interior splash has regressed, which also breaks the missile-vs-red comparison
  Likely code area: interior hit profile propagation for `RED_EXPLOSIVE` and `MISSILE_BLAST`
  Type: `Real`

### MissileHoldFireRegressionTest

- `[ ] missileTurretsRespectTheOneSecondReloadFloor`
  Complaint: ready missile turret did not fire its first volley
  Likely root cause: base missile-turret fire gating is too strict or readiness state is not honored
  Likely code area: turret fire gate, missile turret cooldown/readiness logic
  Type: `Real`

### MissileRoleBehaviorTest

- `[ ] fastMissilesCanFireAcrossTheirCurrentSector`
  Complaint: FAST missiles did not fire across the current sector
  Likely root cause: AI firing range or sector-visibility logic is too restrictive
  Likely code area: AI missile range evaluation, `fireIfAble`
  Type: `Real`

- `[ ] interceptMissilesReloadThreeTimesFasterAndRenderSmaller`
  Complaint: interceptor missile did not fire at all from a ready turret
  Likely root cause: missile turret fire gate is broken upstream of reload-scale assertions
  Likely code area: turret firing logic, missile role handling
  Type: `Real`

- `[ ] heavyMissilesHaveExtendedRangeAndSmallerVisuals`
  Complaint: `NoSuchMethodException` for `AISystem.missileRangeForTurret(GameContext,Ship,Turret,double)`
  Likely root cause: helper method signature changed or helper was removed
  Likely code area: AI missile range helper API
  Type: `Stale`

### ShootingRangeTitanSpawnTest

- `[ ] shootingRangeTitanLayoutCanResetToDefaultWall`
  Complaint: reset layout only leaves `1` `STATIC_TURRET` where at least `2` were expected
  Likely root cause: default shooting-range wall reset is under-spawning static turret targets
  Likely code area: shooting-range reset layout population
  Type: `Real`

## Priority Repair Board

This section is the action-oriented view of the same failures.

Priority meanings:

- `P0`: likely live gameplay/system breakage; high confidence this should be fixed in code
- `P1`: real authored/content/regression breakage, but less universally catastrophic than `P0`
- `P2`: likely stale expectation, wording drift, or lower-severity tuning mismatch

### P0 Gameplay Breaks

- `[ ] redSupershipSlugDetonatesOnFirstShipHit`
  Why P0: hyperweapon shot is not being emitted at all

- `[ ] blueHyperweaponPulseDeletesNonTitansAndHalvesTitans`
  Why P0: hyperweapon shot is not being emitted at all

- `[ ] redHyperweaponCreatesStasisFieldThatPinsTargets`
  Why P0: hyperweapon shot is not being emitted at all

- `[ ] greenHyperweaponFiresOversizedDirectBeam`
  Why P0: hyperweapon shot is not being emitted at all

- `[ ] redHyperweaponSlugDetonatesOnFirstShipHit`
  Why P0: hyperweapon shot is not being emitted at all

- `[ ] yellowHyperweaponWarheadKillsUnshieldedTierTwoTargetsButOnlyStripsShieldedOnes`
  Why P0: hyperweapon shot is not being emitted at all

- `[ ] missileTurretsRespectTheOneSecondReloadFloor`
  Why P0: ready missile turret is failing to fire at all in a base unit test

- `[ ] fastMissilesCanFireAcrossTheirCurrentSector`
  Why P0: AI missile combat behavior is failing a basic sector-range engagement promise

- `[ ] interceptMissilesReloadThreeTimesFasterAndRenderSmaller`
  Why P0: ready interceptor missile is failing to fire at all before role-specific behavior is even checked

- `[ ] singleFireRoomStaysContainedWhileIntegrityFieldIsOperational`
  Why P0: damage containment is leaking into neighboring rooms when a core defensive system should prevent it

- `[ ] singleDamagedRoomGetsAutomaticIntegrityProtection`
  Why P0: integrity protection is failing a direct head-to-head mitigation test

- `[ ] bluePierceDamagesMultipleRoomsInALine`
  Why P0: interior damage profile is not delivering its core projectile behavior

- `[ ] missilesDamageMoreRoomsThanRedExplosiveRounds`
  Why P0: explosive and missile interior damage propagation are both failing expected baseline behavior

- `[ ] highSensorPowerMarksCampaignAnomalySites`
  Why P0: high-sensor strategic/fog integration is failing to surface anomaly contacts

- `[ ] pruneTransientUnitsClearsStalePrimaryGunLocksAcrossMissionTransitions`
  Why P0: stale combat lock state carrying across mission transitions is a systemic correctness bug

- `[ ] sectorTwoTimeoutSucceedsWhenConvoyQuotaSurvives`
  Why P0: authored sector success conditions are resolving incorrectly

- `[ ] objectiveAssetFailureIsDeferredWhileTransitToNextPocketIsLocked`
  Why P0: staged mission failure conditions are evaluating at the wrong time

- `[ ] destroyObjectiveCanCompleteOnTheSameTickAsSectorTimeout`
  Why P0: same-tick ordering between success and fail is wrong

- `[ ] escortSideObjectiveProtectsEscortTitanInsteadOfPlayerHull`
  Why P0: missing escort ship breaks both authored logic and sector progression

- `[ ] escortObjectiveNeedsFormationPresenceToAdvance`
  Why P0: missing escort ship breaks the escort sector entirely

- `[ ] escortSectorsUseTitanFlagships`
  Why P0: expected escort flagship content is missing

- `[ ] bossSectorsEscalateToTitanAndMothershipFlagships`
  Why P0: boss-sector flagship content is missing

- `[ ] sectorThirteenRequiresJammerTriadKillsInsteadOfScreenKills`
  Why P0: authored mission-target composition appears absent

- `[ ] sectorTwentyOneRequiresOrbitalAnchorsBeforeLunaSweepResolves`
  Why P0: authored mission-target composition appears absent

### P1 Authored Campaign / Content Regressions

- `[ ] lateDestroySectorsAccumulatePressureReinforcements`
  Why P1: sector scripting is not escalating pressure as expected

- `[ ] sectorFourUsesAuthoredDestroyProgressInsteadOfCaptureHold`
  Why P1: sector-specific objective accounting is drifting from the authored campaign design

- `[ ] coalitionTaskGroupsUnlockForLaterSectorsAndSurviveCheckpointRestore`
  Why P1: coalition fleet reward/persistence loop is not surviving later-sector progression

- `[ ] protectedAssetLossesShapeSectorOutcomeRewards`
  Why P1: authored outcome consequences are not feeding campaign rewards correctly

- `[ ] routeChoiceUpdatesPendingSectorBeforeLaunch`
  Why P1: fleet hub route flow is not honoring a valid route-choice launch path

- `[ ] menuExitCheckpointClampsRunawayCampaignWorldDimensionsToSubzoneCaps`
  Why P1: checkpoint capture is preserving invalid world dimensions

- `[ ] missionThreeInitialBlockersAreMarkedAndReliefRouteIsInitialized`
  Why P1: mission 3 authored blocker markers and relief anchor setup are missing

- `[ ] strategicLandmarksExposeAuthoredSectorIdentityWithoutScannerDuplicates`
  Why P1: named authored pockets are not reaching the landmark layer

- `[ ] nearestStrategicLandmarkFindsNamedPocketMarker`
  Why P1: some sectors are yielding zero landmarks where gameplay expects navigable named pockets

- `[ ] campaignDiscoveryZonesSeedAmbientWorldContent`
  Why P1: campaign pocket ambience/support/defense seeding is underpopulated

- `[ ] sectorStartEmitsMissionBanterDuringLivePlay`
  Why P1: mission-start authored banter is not dispatching

- `[ ] stealthFleetSoftensContactConfidenceAtTheSameDetectionRange`
  Why P1: strategic fleet identity tuning is not honoring stealth-heavy fleets strongly enough

- `[ ] shootingRangeTitanLayoutCanResetToDefaultWall`
  Why P1: default shooting-range reset content is incomplete

### P2 Stale Tests / Wording Drift / Mixed Cases

- `[ ] campaignStartUsesTradeHubLoreAndStarterBlueFleet`
  Why P2: likely a mix of real roster drift and authored lore-string drift

- `[ ] lateCampaignSectorsExposePlanetaryLandmarksInHud`
  Why P2: looks heavily dependent on descriptive text/landmark wording expectations

- `[ ] supportMarkersUseClearFactionFacingNames`
  Why P2: mostly presentation/readability wording drift unless the generic labels are blocking comprehension

- `[ ] lockedMissionSectionHudSaysToFlyFlagshipToNextPocket`
  Why P2: appears primarily to be HUD copy phrasing drift

- `[ ] sectorTwoHudExplainsWinStateAndImmediateTask`
  Why P2: appears primarily to be HUD copy phrasing drift

- `[ ] sectorTwoFailureCallsOutConvoyQuotaBreak`
  Why P2: may be real fail-path drift, but the assertion strongly depends on exact failure wording

- `[ ] genericTimeoutFailureCallsOutUnfinishedObjective`
  Why P2: may be real fail-path drift, but the assertion strongly depends on exact failure wording

- `[ ] firstCampaignMissionAutoWinsAtTwoHundredSeconds`
  Why P2: could be a real timing regression or just an outdated authored assumption

- `[ ] firstCampaignMissionUsesTwoHundredSecondWindow`
  Why P2: highly likely to reflect an outdated authored timing assumption unless sector 1 truly regressed

- `[ ] campaignWorldContainsSeparatedZonesSoSectorTwoSpawnDoesNotClampToMapEdge`
  Why P2: likely mixed world-layout drift versus test assumptions about exact geometry

- `[ ] heavyMissilesHaveExtendedRangeAndSmallerVisuals`
  Why P2: direct `NoSuchMethodException` points to stale helper-signature expectations more than a pure gameplay regression

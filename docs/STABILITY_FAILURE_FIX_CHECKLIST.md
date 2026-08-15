# Stability Failure Fix Checklist

Date created: 2026-08-15
Source run: `.\gradlew.bat test`
Result: `1537 tests completed, 53 failed, 1 skipped`

Purpose: restore the game to a stable development baseline after the full test
suite exposed campaign, economy, rendering, first-hour, screenshot, and layout
regressions.

## Stabilization Rules

- [ ] Do not ship or cut a release while any P0/P1 item below is failing.
- [ ] Fix root causes before updating assertions.
- [ ] Update tests only when the intended design has genuinely changed.
- [ ] Run the smallest related test class after each fix.
- [ ] Run the targeted first-hour suite after AAR/Academy/rendering changes.
- [ ] Run `git diff --check` before every commit.
- [ ] Run the full `.\gradlew.bat test` suite before calling the branch stable.
- [ ] If screenshot baselines changed intentionally, regenerate and review images manually before accepting new signatures.

## Recommended Fix Order

1. P0: render/read-only mutation and first-hour config copy failures.
2. P0: campaign resource ledger and mining deposit failures.
3. P1: campaign hub service actions and strategic command HUD copy.
4. P1: campaign map projection, NPC fleet, intel, and encounter visibility.
5. P1: strike/recon counterplay and resource-cost agreement.
6. P1: visual/performance/screenshot regressions.
7. P2: ship identity, artillery, supership turret layout.
8. Final: full-suite pass and checklist sign-off.

# P0 - Release-Blocking Stability

## Rendering Must Be Read-Only

- [ ] Fix `CampaignPhaseEightArchitectureTest.strategicMapRenderingDoesNotMutateCampaignState`.
- [ ] Compare the pre/post `CampaignSnapshot` fields to identify the exact mutation.
- [ ] Audit `Renderer.drawStrategicMap(...)` and all campaign render helper calls for state normalization, lazy generation, scroll clamping, selected-tab mutation, route preview mutation, report creation, or cache refresh.
- [ ] Move any required lazy campaign-state update out of rendering and into update/command code.
- [ ] Ensure `ctx.ui.campaignFleetRosterScroll` remains `Integer.MAX_VALUE` after drawing.
- [ ] Add/keep a regression assertion that map rendering does not create AAR entries, memory flags, campaign log rows, projection contacts, or normalized UI scroll.
- [ ] Run `.\gradlew.bat test --tests CampaignPhaseEightArchitectureTest`.
- [ ] Run `.\gradlew.bat test --tests RendererHudLayoutTest`.

## First-Hour Experience Settings

- [ ] Fix `FirstHourExperienceTest.presetsSeparatePressureLethalityAndModes`.
- [ ] Fix `FirstHourExperienceTest.configCopiesExperienceSettings`.
- [ ] Fix `FirstHourExperienceTest.combatLethalityScalesResolvedDamage`.
- [ ] Restore `ExperienceSettings.forPreset(RELAXED)` so attrition, combat lethality, and strategic pressure are below `1.0`.
- [ ] Restore `ExperienceSettings.forPreset(IRON_COMMAND)` so attrition is above `1.0`.
- [ ] Restore `GameConfig.withExperience(...)` defensive copying so later mutations to the source settings do not affect the config.
- [ ] Restore relaxed damage scaling so `CollisionSystem.scaleDamage(context(relaxed), 10)` returns `8`.
- [ ] Restore iron damage scaling so `CollisionSystem.scaleDamage(context(iron), 10)` returns `13`.
- [ ] Run `.\gradlew.bat test --tests FirstHourExperienceTest`.

## Campaign Resource Ledger And Mining Deposit

- [ ] Fix `CampaignMiningDepositTest.minerDepositsOreIntoCampaignMothershipStores`.
- [ ] Fix `CampaignMiningDepositTest.minerRemoteDepositsToCampaignMothershipWhenFleetLeavesMiningPosture`.
- [ ] Fix `CampaignMiningDepositTest.minerDepositAtAlliedStarbaseCreditsCampaignOreToPlayer`.
- [ ] Ensure allied miners depositing into the campaign mothership add ore to `ctx.player.cargo`, not credits.
- [ ] Ensure miner cargo is zeroed after deposit.
- [ ] Ensure remote return-to-base deposits still work when fleet command leaves mining posture.
- [ ] Ensure allied starbase proximity does not steal the player mothership deposit credit.
- [ ] Verify expected values: `50 + 80 = 130`, `30 + 80 = 110`, `20 + 70 = 90`.
- [ ] Run `.\gradlew.bat test --tests CampaignMiningDepositTest`.

## Campaign Economy And Hub Services

- [ ] Fix `CampaignHubEconomyTest.greenRepairConsumesResourcesAndRestoresPersistentFleetCondition`.
- [ ] Fix `CampaignHubEconomyTest.yellowTradeSellsSelectedOreForCreditsAndFuel`.
- [ ] Fix `CampaignHubEconomyTest.friendlyInstallationsRebuildLongRangeStrikeStoresForOreAndCredits`.
- [ ] Fix `CampaignHubEconomyTest.earlyShipyardCommissioningConsumesOreAndSalvageEnoughToPreventChainBuying`.
- [ ] Fix `CampaignHubEconomyTest.greenFavorBuysStoresIntelRouteStabilityAndCombatSupport`.
- [ ] Fix `CampaignHubEconomyTest.yellowLeverageBuysFuelSalvageTradeRouteAndPressureRelief`.
- [ ] Repair hub `REPAIR` so it improves persistent hull/shield condition and current player hull.
- [ ] Ensure repair consumes supplies and salvage.
- [ ] Repair yellow `TRADE` so selected ore is sold, credits/fuel/supplies increase, salvage is preserved, and hired escort history is recorded.
- [ ] Repair `STRIKE_REARM` so torpedo charges recover, sortie cooldown/inventory recovers, credits are spent, and ore is spent.
- [ ] Repair `SHIPYARD` commissioning so it consumes enough ore/salvage to prevent immediate chain-buying from one early stockpile.
- [ ] Repair `ALLY_GREEN` so favor decreases, supplies/ammo/intel/reserve improve, enemy alert falls, route stabilizes, and support force is added.
- [ ] Repair `ALLY_YELLOW` so leverage decreases, credits/fuel/salvage improve, pressure/alert fall, route stabilizes, and support force is added.
- [ ] Run `.\gradlew.bat test --tests CampaignHubEconomyTest`.

# P1 - Campaign Clarity, Strategy, And Map Stability

## Strategic HUD And Player-Facing Copy

- [ ] Fix `CampaignStrategicCommandHudTest.criticalCampaignCopyUsesPlayerFacingTerms`.
- [ ] Fix `CampaignStrategicCommandHudTest.commandHudSummariesExposeFleetResourcesAndRouteStatus`.
- [ ] Fix `CampaignStrategicCommandHudTest.campaignClarityLayerRecordsReportsMemoryAndWarRoomReadouts`.
- [ ] Fix `CampaignStrategicCommandHudTest.afterActionReportConnectsBattleOutcomeToResourcesFleetAndTheater`.
- [ ] Fix `CampaignStrategicCommandHudTest.stationMemoryRecordsRepeatedServiceUseAndAffectsReadouts`.
- [ ] Fix `CampaignStrategicCommandHudTest.stationServicesExposeConversationOptionsAndNoStationPurchase`.
- [ ] Fix `CampaignStrategicCommandHudTest.shipyardPreviewAndStrategicAuthorityExposeLiveFleetBuildingContext`.
- [ ] Restore resource copy containing `Strike Availability` and `Recovery Resources`.
- [ ] Restore support copy containing `Green reputation`, `Yellow reputation`, and `Next Green threshold`.
- [ ] Remove or hide obsolete player-facing terms such as `Route Tempo` and raw `Yellow leverage`.
- [ ] Restore fleet, resource, navigation, receiver, direction finder, and comms board line prefixes expected by HUD tests.
- [ ] Restore latest report/memory/captain-log lines without causing render-time mutation.
- [ ] Decide whether latest AAR display should say `Why This Matters:` or `Key Battle Factors:` in legacy campaign screens; update code or tests consistently.
- [ ] Restore station service action ordering so the expected service is `REQUEST REPLENISHMENT`, not `BUY ORE`, when that context is active.
- [ ] Run `.\gradlew.bat test --tests CampaignStrategicCommandHudTest`.

## Strategic Travel, Stores, And Route Forecasts

- [ ] Fix `CampaignStrategicCommandHudTest.fleetPostureCyclesAndChangesSweepCost`.
- [ ] Fix `CampaignStrategicCommandHudTest.postureChangesTravelDrainAndRouteRisk`.
- [ ] Fix `CampaignStrategicCommandHudTest.liveTravelAttritionUpdatesCheckpointedExpansionLedgerAndResourceBoard`.
- [ ] Fix `CampaignStrategicCommandHudTest.frameSizedTravelUpdatesAccumulateOperationalStoreAttrition`.
- [ ] Fix `OwnerPlaytestVerticalSliceThreeTest.travelAndRepairSpendTheSameDisplayedStores`.
- [ ] Fix `OwnerPlaytestVerticalSliceThreeTest.ledgerReconcilesAcrossCombatRearmDockingAndCheckpointRestore`.
- [ ] Fix `OwnerPlaytestVerticalSliceThreeTest.shortagesExposeRecoveryChoicesAndTransportRepairConsumesSupplies`.
- [ ] Restore posture-dependent sweep cost expected value, including `17` instead of `20` where asserted.
- [ ] Reconcile route forecast resource costs with realized travel drain within rounding.
- [ ] Ensure travel attrition updates campaign ledgers, resource boards, and checkpoints consistently.
- [ ] Ensure repair/rearm/docking consume the same stores shown in previews.
- [ ] Ensure shortage recovery choices are exposed when fuel/supplies/ammo/salvage are low.
- [ ] Run `.\gradlew.bat test --tests CampaignStrategicCommandHudTest --tests OwnerPlaytestVerticalSliceThreeTest`.

## Strategic Strike And Recon Counterplay

- [ ] Fix `CampaignStrategicStrikeCounterplayTest.strikeAvailabilityContractShowsInventoryCostsIntelRiskAndRecovery`.
- [ ] Fix `CampaignStrategicStrikeCounterplayTest.torpedoStrikeConsumesResourcesAndTriggersCounterplay`.
- [ ] Fix `CampaignStrategicCommandHudTest.hostileSearchGroupCanBeHitByStrategicSortie`.
- [ ] Fix `CampaignStrategicCommandHudTest.tacticalReconActionRequiresTheSameSuppliesItReports`.
- [ ] Fix `OwnerPlaytestVerticalSliceTwoTest.reconSweepProducesVisibleBeforeAfterResultAndCooldown`.
- [ ] Ensure strike availability preview shows inventory, costs, intel requirement, risk, and recovery.
- [ ] Ensure torpedo strikes spend torpedo charges.
- [ ] Ensure strategic sorties can affect hostile search groups when valid.
- [ ] Ensure tactical recon availability uses the same supplies requirement shown in the UI.
- [ ] Ensure recon sweep visibly changes before/after intel state and starts cooldown.
- [ ] Run `.\gradlew.bat test --tests CampaignStrategicStrikeCounterplayTest --tests OwnerPlaytestVerticalSliceTwoTest`.

## Campaign Map Projection, Intel, And NPC Fleet AI

- [ ] Fix `CampaignMultiSourceIntelMilestoneThreeTest.exactMarkerUsesObservedPositionAndStrategicOnlyIntelEmitsNoFleetMarker`.
- [ ] Fix `CampaignNpcFleetAiTest.greenPatrolInterceptsRedScoutThenResumesPatrol`.
- [ ] Fix `CampaignNpcFleetAiTest.redScoutAvoidsCombatUnlessTargetIsExtremelyWeak`.
- [ ] Fix `CampaignNpcFleetAiTest.destroyingRedScoutDegradesLocalRedContactConfidence`.
- [ ] Fix `CampaignNpcFleetAiTest.destroyingRedScoutDispatchesReplacementFromNamedSource`.
- [ ] Fix `FocusedFactionAttackChecklistTest.gateABaselineTelemetryIsReadOnlyAndRoutesRemainPresent`.
- [ ] Fix `OwnerPlaytestPhaseOneAuditTest.movingProjectedStrategicContactsMapToOneCanonicalLiveForceId`.
- [ ] Fix `OwnerPlaytestVerticalSliceOneTest.normalOverviewShowsAPlainlyLabeledMovingGreenFleet`.
- [ ] Fix `OwnerPlaytestVerticalSliceOneTest.greenYellowAndRedPhysicalFleetsHaveExplainableMapProjection`.
- [ ] Restore exact intel markers using observed positions.
- [ ] Prevent strategic-only intel from emitting full fleet markers.
- [ ] Ensure Green patrols can intercept Red scouts and resume patrol.
- [ ] Ensure Red scouts avoid combat unless the target is extremely weak.
- [ ] Ensure destroying Red scouts degrades local Red contact confidence.
- [ ] Ensure destroying Red scouts dispatches replacement forces from named sources.
- [ ] Ensure normal overview includes plainly labeled moving Green fleets.
- [ ] Ensure map projection maps each moving physical force to one canonical live force ID.
- [ ] Ensure baseline telemetry remains read-only and routes remain present.
- [ ] Run `.\gradlew.bat test --tests CampaignMultiSourceIntelMilestoneThreeTest --tests CampaignNpcFleetAiTest --tests OwnerPlaytestVerticalSliceOneTest --tests FocusedFactionAttackChecklistTest`.

## Overmap Encounter Flow And Tactical Zones

- [ ] Fix `CampaignOvermapEncounterFlowTest.campaignForceEncounterPullsVisibleFleetsInsidePlayerSensorBubbleOnly`.
- [ ] Fix `CampaignOvermapEncounterFlowTest.openSpaceFleetClashExposesThreeOwnedTacticalZones`.
- [ ] Ensure only fleets inside the player sensor bubble are pulled into visible force encounters.
- [ ] Ensure open-space allied and neutral lanes expose three owned tactical zones.
- [ ] Ensure tactical-zone ownership does not span invalid lanes or unrelated forces.
- [ ] Run `.\gradlew.bat test --tests CampaignOvermapEncounterFlowTest`.

## Industry, Production, And Theater Performance

- [ ] Fix `CampaignPhaseThreeIndustryTest.transportSupportConsumesSuppliesAndDoesNotRestoreArmorOrShields`.
- [ ] Fix `CampaignTheaterConquestChecklistTest.highContactStrategicUpdateRemainsWithinReasonableBudget`.
- [ ] Ensure transport support consumes supplies.
- [ ] Ensure transport support does not restore armor or shields.
- [ ] Profile the six-second high-contact strategic-overmap smoke path.
- [ ] Bring the strategic update budget back under the guardrail; failing sample was `7339ms`.
- [ ] Run `.\gradlew.bat test --tests CampaignPhaseThreeIndustryTest --tests CampaignTheaterConquestChecklistTest`.

# P1 - Visual, Rendering, And Screenshot Stability

## Performance Token Mode

- [ ] Fix `PerformanceGuardrailsTest.visualQualityNeverReplacesShipsUnlessPlayerRequestsFpsView`.
- [ ] Ensure automatic quality degradation does not replace ships with performance tokens unless the player requested tactical/FPS view.
- [ ] In manual FPS view, keep nearby ships readable.
- [ ] In manual FPS view, keep ships directly in front of the player readable.
- [ ] Allow distant off-screen/behind ships to use low-render tokens.
- [ ] Run `.\gradlew.bat test --tests PerformanceGuardrailsTest`.

## Screenshot Regression

- [ ] Fix `ScreenshotRegressionHarnessTest.productionScreenshotTargetsRenderAndMatchBaselines`.
- [ ] Inspect generated captures in `build/reports/test-visual-regression`.
- [ ] Compare `campaign-map`, `fleet-board`, `strike-tab`, `tactical-hud`, and `accessibility-hud` against approved baselines.
- [ ] If visual drift is accidental, fix the rendering cause.
- [ ] If visual drift is intentional, regenerate baselines and review images manually.
- [ ] Ensure each capture remains above `300_000` opaque pixels.
- [ ] Ensure each capture has at least `18` color buckets.
- [ ] Run `.\gradlew.bat test --tests ScreenshotRegressionHarnessTest`.

# P2 - Ship Data, Layout, And Identity

## Artillery Ship Primary Gun

- [ ] Fix `ArtilleryShipTest.artilleryShipUsesBattleshipGradePrimaryGun`.
- [ ] Ensure artillery ship mounts exactly one primary gun.
- [ ] Match artillery gun damage to battleship primary gun damage.
- [ ] Match artillery gun cooldown to battleship primary gun cooldown.
- [ ] Match artillery projectile speed to battleship primary projectile speed.
- [ ] Match artillery projectile life to battleship primary projectile life.
- [ ] Keep artillery starter-tier glass-cannon behavior intact.
- [ ] Keep red player artillery cursor firing intact.
- [ ] Run `.\gradlew.bat test --tests ArtilleryShipTest`.

## Team E Faction Trait

- [ ] Fix `ShipIdentityRegistryTest.everyFactionHasConfiguredFactionTrait`.
- [ ] Add a non-`NONE` faction trait for `Faction.TEAM_E`.
- [ ] Give the Team E trait a nonblank player-facing name.
- [ ] Ensure `Faction.PLAYER` continues to use Blue/ALLY role bonuses.
- [ ] Run `.\gradlew.bat test --tests ShipIdentityRegistryTest`.

## Supership Centerline Battery

- [ ] Fix `SupershipLayoutRegressionTest.supershipGunTurretsFormCenterlineBattery`.
- [ ] Move all `ShipRole.SUPERSHIP` gun turrets to centerline mounts where `abs(localY) <= 1.0`.
- [ ] Preserve at least four supership gun turrets.
- [ ] Ensure visual turret placement still sits on the hull silhouette.
- [ ] Run `.\gradlew.bat test --tests SupershipLayoutRegressionTest`.

# AAR/Academy Adjacent Verification

These tests are especially important after the new BattleResult/AAR work.

- [ ] Re-run `CampaignStrategicCommandHudTest.afterActionReportConnectsBattleOutcomeToResourcesFleetAndTheater`.
- [ ] Re-run `CampaignStrategicCommandHudTest.campaignClarityLayerRecordsReportsMemoryAndWarRoomReadouts`.
- [ ] Re-run `CampaignPhaseEightArchitectureTest.strategicMapRenderingDoesNotMutateCampaignState`.
- [ ] Confirm AAR creation happens during battle/campaign-result transitions, not during renderer read paths.
- [ ] Confirm report display remains read-only.
- [ ] Confirm legacy campaign reports still expose resource, fleet, theater, personnel, captain-log, and memory lines expected by campaign tests.
- [ ] Confirm new `Key Battle Factors` wording does not break legacy screens that still expect `Why This Matters`, or update tests/design consistently.
- [ ] Run the targeted green suite:
  `.\gradlew.bat test --tests BattleResultAnalysisServiceTest --tests app.persistence.AcademyProgressStoreTest --tests app.ui.MainMenuPanelMultiplayerEntryTest --tests RendererHudLayoutTest --tests CommandSchoolOverworldExpansionTest --tests TutorialWarpRegressionTest`

# Final Stability Gate

- [ ] `git status -sb` reviewed.
- [ ] `git diff --check` passes.
- [ ] All P0 tests pass.
- [ ] All P1 tests pass.
- [ ] All P2 tests pass or are explicitly reclassified with owner approval.
- [ ] `.\gradlew.bat test` completes successfully.
- [ ] No Gradle test executor crash occurs.
- [ ] Packaged Windows smoke test passes.
- [ ] Packaged Linux smoke test passes.
- [ ] Packaged macOS smoke test passes if macOS remains a target.
- [ ] Owner confirms the game is stable enough to continue Steam-candidate work.

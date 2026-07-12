# Campaign System Decomposition Checklist

Use this checklist to split `CampaignSystem` by ownership while preserving current gameplay, save compatibility, and public callers.

## Guiding Rules

- [x] Keep `CampaignSystem` as the compatibility facade during the refactor.
- [x] Split by authority, not by line count.
- [x] Keep the first implementation in the existing default-package source layout.
- [x] Do not change save format while moving gameplay code.
- [x] Do not change campaign update ordering while moving gameplay code.
- [x] Do not move fleet movement, war AI, and encounter resolution in the same change.
- [x] Add regression coverage before each extraction that can mutate live campaign state.

## Phase 0 - Baseline Protection

- [x] Record current `CampaignSystem` public method count and file size.
- [x] Run existing campaign-focused tests.
- [x] Add read-only query mutation tests for action and presentation calls.
- [ ] Add checkpoint round-trip tests for fleet positions, routes, ownership, battles, contacts, and resources.
- [ ] Add parity tests for direct fleet engagement, site entry, stale contacts, duplicate fleet markers, and invasion arrows.
- [x] Document current campaign update order before introducing `CampaignRuntime`.

Baseline note: before decomposition, `CampaignSystem.java` had 47,546 lines, about 2,323 method declarations, and 407 `public static` methods.
The current update-order contract is recorded in `docs/CAMPAIGN_RUNTIME_UPDATE_ORDER.md`.
Focused regression note: `CampaignSystemDecompositionBoundaryTest`, strategic UI/HUD, difficulty telemetry, release telemetry, fleet hub, travel pressure, checkpoint, and save contract tests pass after the initial boundary extraction.
Build note: `compileJava` now verifies that the legacy `CampaignSystem.class` facade exists and repairs it with a no-debug external `javac` compile if Windows security removes the oversized debug class.

## Phase 1 - Action Catalog Boundary

- [x] Add `CampaignActionCatalog` as the strategic action entry point.
- [x] Add `TacticalMapActionCatalog` as the tactical-map action entry point.
- [x] Keep `CampaignSystem.campaignVisibleActions` as the public facade.
- [x] Keep `CampaignSystem.tacticalMapVisibleActions` as the public facade.
- [x] Add `CampaignActionProvider`.
- [ ] Move navigation actions into `CampaignNavigationActions`.
- [ ] Move location/site actions into `CampaignLocationActions`.
- [ ] Move hub/service actions into `CampaignHubActions`.
- [ ] Move fleet/posture actions into `CampaignFleetActions`.
- [ ] Move mission/objective actions into `CampaignMissionActions`.
- [ ] Move strike actions into `CampaignStrikeActions`.
- [ ] Move intel/sensor actions into `CampaignIntelActions`.
- [ ] Move debug/training actions into `CampaignDebugActions`.
- [x] Keep provider ordering explicit and deterministic.

Implementation note: the catalog currently uses explicit legacy bridge providers. Replace those bridge providers incrementally as category-specific action providers take over real action construction.

## Phase 2 - Read-Only Presentation

- [x] Add presenter classes for sidebar, route, hub, territory, fleet, war, strikes, missions, and diagnostics.
- [ ] Move read-only `*Lines` methods into presenters.
- [ ] Move read-only `*Readout` methods into presenters.
- [ ] Move read-only `*Label` methods into presenters.
- [ ] Move read-only `*Preview` methods into presenters.
- [ ] Move read-only `*View` and overlay construction into presenters.
- [x] Add tests proving presentation calls do not mutate fleet positions, routes, territory, battles, contacts, or resources.

Implementation progress:

- [x] Move `campaignSummarySidebarLines` body into `CampaignSidebarPresenter`.
- [x] Move `campaignWarBaselineTelemetryLines` body into `CampaignWarPresenter`.
- [x] Move `campaignBattleWarningLines` body into `CampaignWarPresenter`.
- [x] Move `selectedHubAlignmentLabel`, `selectedHubIdentityLines`, and `hubServicePreviewLines` bodies into `CampaignHubPresenter`.
- [x] Move `hubServiceActionLabel` and `hubServiceActionDetail` bodies into `CampaignHubPresenter`.
- [x] Move `campaignYardDocketLines` and `campaignFleetRefitScreenLines` bodies into `CampaignHubPresenter`.
- [x] Move `campaignTerritoryOverlayViews` body into `CampaignTerritoryPresenter`.
- [x] Move `campaignTerritoryDetailLines` body into `CampaignTerritoryPresenter`.
- [x] Move `campaignBattleScarViews` body into `CampaignTerritoryPresenter`.
- [x] Move `campaignFleetBoardSummaryLines` body into `CampaignFleetPresenter`.
- [x] Move `campaignFleetDetachmentLines` body into `CampaignFleetPresenter`.
- [x] Move `campaignFlagshipSchematicLines` body into `CampaignFleetPresenter`.
- [x] Move `selectedRouteForceWarningLines` body into `CampaignRoutePresenter`.
- [x] Move `campaignMapBookmarkLines` body into `CampaignRoutePresenter`.
- [x] Move `campaignRouteQueueLines` body into `CampaignRoutePresenter`.
- [x] Move production/readiness telemetry readouts into `CampaignDiagnosticsPresenter`.
- [x] Move release telemetry contract/history readouts into `CampaignDiagnosticsPresenter`.
- [x] Move expansion command-board readouts into `CampaignDiagnosticsPresenter`.
- [x] Move difficulty telemetry/doctrine readouts into `CampaignDiagnosticsPresenter`.

## Phase 3 - Persistence Boundary

- [x] Add `CampaignCheckpointMapper`.
- [x] Add `CampaignSaveCodec`.
- [x] Add `CampaignSaveMigration`.
- [x] Add `CampaignSaveSanitizer`.
- [ ] Move checkpoint capture/apply code without changing the checkpoint contract.
- [ ] Move campaign force, location, battle, intel, strike, and yard-order serialization unchanged.
- [ ] Preserve legacy save fixtures.

## Phase 4 - Hub, Economy, Production, And Refit

- [x] Add or expand `CampaignHubServiceSystem`.
- [x] Add `CampaignProductionSystem`.
- [x] Add `CampaignRefitSystem`.
- [x] Add `CampaignFactionIndustrySystem`.
- [ ] Use shared quote/result objects for UI previews and transaction execution.
- [ ] Move hub transactions, yard orders, construction, refits, and faction production out of `CampaignSystem`.

Implementation progress:

- [x] Move yard-order advancement loop into `CampaignProductionSystem` behind the legacy `CampaignSystem` reflection wrapper.
- [x] Move yard-order query ownership into `CampaignProductionSystem`.
- [x] Move player construction/refit order creation into `CampaignProductionSystem` behind legacy reflection wrappers.
- [x] Move yard-order refund and completion behavior into `CampaignProductionSystem` behind legacy wrappers.
- [x] Move hub service open/execute/confirm menu orchestration into `CampaignHubServiceSystem`.
- [x] Add shared `HubServiceQuote` cost/payout object and use it from hub previews and service execution.
- [x] Move hub service availability policy into `CampaignHubServiceSystem`.

## Phase 5 - Strike Systems

- [x] Add `CampaignStrikeSystem`.
- [x] Add `CampaignStrikePreflight`.
- [x] Add `StrategicStrikeLauncher`.
- [x] Add `TacticalStrikeLauncher`.
- [x] Add `CampaignStrikeSimulation`.
- [x] Add `CampaignStrikeOutcomeResolver`.
- [ ] Make preflight the shared source for UI eligibility and execution cost.

## Phase 6 - Battles And Encounters

- [x] Add `CampaignBattleSystem`.
- [x] Add `CampaignBattleResolver`.
- [x] Add `CampaignInterventionSystem`.
- [x] Add `CampaignEncounterSystem`.
- [x] Add `CampaignEncounterBuilder`.
- [x] Add `CampaignEncounterOutcomeResolver`.
- [x] Add `CampaignBattleAftermath`.
- [ ] Separate AI-vs-AI campaign battles from player tactical encounter entry.

## Phase 7 - Missions And Progression

- [x] Add `CampaignMissionBoard`.
- [x] Add `CampaignObjectiveSystem`.
- [x] Add `CampaignProgressionSystem`.
- [x] Add `CampaignSectorScripts`.
- [x] Add `CampaignScript`.
- [ ] Move mission-board generation separately from authored sector progression.

## Phase 8 - Navigation And Intel

- [x] Add `CampaignNavigationSystem`.
- [x] Add `CampaignRoutePlanner`.
- [x] Add `CampaignLocationQueries`.
- [x] Add `CampaignMapSelectionSystem`.
- [x] Add `CampaignIntelSystem`.
- [x] Add `CampaignContactProjection`.
- [x] Add `CampaignSensorSystem`.
- [x] Add `CampaignSearchGroupSystem`.
- [ ] Keep contacts as projections, not movement authority.

Implementation progress:

- [x] Move main/current/selected campaign location query bodies into `CampaignLocationQueries`.
- [x] Route `startTravelToSelectedLocation` through `CampaignNavigationSystem`.

## Phase 9 - Fleet Simulation

- [x] Add `CampaignFleetSimulation`.
- [x] Add `CampaignFleetMovement`.
- [x] Add `CampaignFleetOrderSystem`.
- [x] Add `CampaignFleetLifecycle`.
- [x] Add `CampaignFleetComposition`.
- [ ] Make `CampaignFleetMovement` the only normal simulation owner of real campaign force coordinates.

## Phase 10 - War And Territory

- [x] Add `CampaignWarDirector`.
- [x] Add `FactionDirectorSystem`.
- [x] Add `TerritoryControlSystem`.
- [x] Add `InvasionSystem`.
- [x] Add `CampaignSupplyNetwork`.
- [ ] Make directors issue operations/orders instead of directly moving fleets or rendering arrows.

## Phase 11 - State Restructure

- [ ] Group `CampaignState` fields into domain substates only after behavior extraction is stable.
- [ ] Migrate one substate at a time.
- [ ] Keep save migration explicit for every field move.

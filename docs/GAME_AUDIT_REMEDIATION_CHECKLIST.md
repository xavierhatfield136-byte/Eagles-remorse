# Game Audit Remediation Checklist

Date: 2026-06-23
Status: Complete
Scope: Fix the June 2026 code/game audit findings, excluding placeholder approval.

## Goal

Turn the audit findings into a concrete repair pass that restores a clean test signal, removes confusing campaign behavior, reduces risky bloat, and makes alpha-facing systems honest about what is live versus model-only.

## Fix Order

Do not attempt this whole document in one large patch. Treat it as a stabilization milestone split into focused passes.

### Pass 1 - Stabilization / Must Fix First

1. Restore test suite confidence.
2. Fix Strategic Command HUD action leakage.
3. Repair trade terminology and behavior enough for tests and player clarity.
4. Remove campaign mutation from render paths.
5. Preserve persistent ship names in hostile encounters.
6. Gate logistics by intel level.
7. Fix stale/ghost contact behavior if it directly affects the above.

### Pass 2 - Economy / Player-Facing Clarity

1. Clarify player shipyard ore versus local shipyard ore.
2. Clean confusing terminology.
3. Make `Request Trade` clearly support buy/sell or rename it.
4. Make `Yard Ore`, `Fleet Ore`, and station stockpiles clear in UI.
5. Add tests around player-facing copy and preview lines.
6. Hide or clearly label model-only systems that appear in alpha-facing UI.

### Pass 3 - Architecture Cleanup / Bloat Reduction

1. Separate live systems from model-only expansion state more thoroughly.
2. Move future systems behind debug/development flags where appropriate.
3. Update save schema docs after state classification.
4. Start low-risk extraction from `CampaignSystem.java`.
5. Start low-risk extraction from `Renderer.java`.
6. Remove generated/prototype comments from production code.

## 0. Safety Rules For This Repair Pass

- [x] Do not add new campaign features during this pass.
- [x] Do not broadly rewrite `CampaignSystem.java`.
- [x] Do not broadly rewrite `Renderer.java`.
- [x] Do not change save format unless explicitly required by a fix.
- [x] Do not remove existing tests to make the suite pass.
- [x] Do not weaken assertions unless the expected behavior has intentionally changed.
- [x] Prefer small targeted fixes with regression coverage.
- [x] If a design decision is ambiguous, choose the alpha-safe behavior described in this checklist.
- [x] Keep player-facing behavior honest: only show systems that are actually live.
- [x] Keep each implementation pass independently testable.

## 1. Restore Test Suite Confidence

- [x] Reproduce the current fail-fast failure with:
  - `./gradlew test --fail-fast --console=plain`
- [x] Fix `CampaignCompatibilityOverhaulTest.sectorThirteenNowUsesGenericDestroyProgress`.
  - Expected failure location: `test/CampaignCompatibilityOverhaulTest.java:479`.
  - Current mismatch: sector 13 generic destroy progress updates, but HUD detail no longer reports the expected objective asset quota.
  - Verify sector 13 still has no capture ring.
  - Verify the objective label still names the jammer triad.
  - Verify the sector completes after the intended destroy quota.
- [x] Re-run:
  - `./gradlew test --tests CampaignCompatibilityOverhaulTest --console=plain`
- [x] Re-run the strategic HUD tests after the HUD fixes below:
  - `./gradlew test --tests CampaignStrategicCommandHudTest --console=plain`
- [x] Investigate why the full suite exceeds 5 minutes.
  - Root cause: large AI soak tests used thousands of high-frequency reflective simulation calls, hiding later stale regression failures behind timeouts.
  - Resolution: cache hot reflection methods, keep equivalent simulated soak duration with coarser test ticks, and retain extended performance coverage in `performanceGuardrailsCi`.
  - Capture slow test classes with Gradle test reports or a timing pass.
  - Split slow soak/performance tests from normal regression tests if needed.
  - Document the intended alpha gate command.
- [x] Establish a release-confidence command that completes locally.
  - Candidate: targeted campaign suite plus `performanceGuardrailsCi`.
  - Final command must be documented in `docs/PERFORMANCE_GUARDRAILS.md` or this checklist.

## 2. Fix Strategic Command HUD Action Leakage

- [x] Update `CampaignSystem.campaignVisibleActions`.
  - Current issue: `LAUNCH_STRIKE` is always added as a `STRIKES` action even when no hostile contact is selected.
  - Expected behavior: default command HUD should not expose a `STRIKES` category when no strikeable hostile contact exists.
- [x] Decide whether disabled strike actions should be hidden or shown only inside an explicitly opened strike tab.
  - Recommended alpha behavior: hide strike category until a hostile strike target exists.
- [x] Preserve positive strike flows:
  - hostile contact selected
  - target-quality intel
  - torpedo strike
  - carrier sortie
  - atomic strike gating
- [x] Add or update regression coverage:
  - no strike category in default command context
  - strike launcher appears for selected hostile contact
  - unavailable strike sub-actions explain missing resources or intel
- [x] Verify:
  - `./gradlew test --tests CampaignStrategicCommandHudTest --console=plain`

## 3. Repair Trade Service Terminology And Behavior

- [x] Choose the intended player-facing meaning of `Request Trade`.
  - Option A: trade buys ore/stores for the player.
  - Option B: trade sells ore from fleet stores.
  - Recommended alpha behavior: support both buy and sell, but label them distinctly.
- [x] Replace ambiguous copy in `CampaignSystem.hubServicePreviewLines`.
  - Current confusing text: "Trading desk buys ore directly from fleet stores."
  - Required: distinguish "Buy ore/stores" from "Sell ore."
- [x] Update `performHubService` for `HubService.TRADE`.
  - If buying ore is intended, allow a positive ore gain when the player has credits/salvage and the hub has trade service.
  - If selling ore remains available, expose it as a separate amount/confirmation path.
- [x] Update banners.
  - Avoid showing "NO ORE READY FOR MARKET SALE" when the visible action says "Request Trade."
- [x] Update tests around:
  - preview lines mention buy/sell clearly
  - trade can increase ore when buying
  - selling ore still pays credits if retained
  - insufficient resources gives a precise reason
- [x] Verify:
  - `./gradlew test --tests CampaignStrategicCommandHudTest --tests CampaignHubEconomyTest --console=plain`

## 4. Remove Campaign Mutation From Render Paths

- [x] Audit `Renderer` calls into `CampaignSystem` for methods that mutate state.
  - Known path: `Renderer.drawFleetFormationCutouts` calls `CampaignSystem.nearbyHighIntelFleetFormationCutouts`.
  - Known mutation: `nearbyHighIntelFleetFormationCutouts` calls `ensureCampaignForceOwnership` and `reconcileCampaignFiniteEconomy`.
- [x] Split read-only formation inspection from economy reconciliation.
  - Add a read-only cutout method or require the update loop to reconcile before rendering.
  - Do not call economy reconciliation, force ownership claiming, queue advancement, or ledger mutation from drawing code.
- [x] Add a regression test.
  - Snapshot campaign ledger/force ownership before calling the read-only cutout method.
  - Assert no campaign economy or ownership state changes.
- [x] Verify strategic map rendering still shows:
  - selected fleet cutouts
  - nearby high-intel cutouts at high zoom
  - no duplicate tactical ships
- [x] Verify:
  - `./gradlew test --tests CampaignForceOwnershipTest --tests CampaignStrategicUiReadabilityTest --console=plain`

## 5. Preserve Persistent Ship Names In Hostile Encounters

- [x] Fix hostile manifest spawning.
  - Current issue: `spawnEncounterForceManifest` passes hostile entries through `spawnEnemyAtPoint(ctx, entry.role, x, y)`, dropping `entry.name`.
- [x] Add an overload or route hostile entries through a named spawn method.
  - Candidate: `spawnEnemyAtPoint(ctx, role, x, y, name)`.
  - Ensure faction remains hostile and campaign force registration still works.
- [x] Add regression coverage.
  - Create a finite-pool hostile record with a known name.
  - Build an encounter manifest.
  - Spawn the encounter.
  - Assert a live hostile tactical ship uses the persistent name.
- [x] Verify:
  - `./gradlew test --tests CampaignForceOwnershipTest --console=plain`

## 6. Gate Task-Force Logistics By Intel Level

- [x] Update `taskForceInspectionLines`.
  - Current issue: exact fuel/ammo/supply percentages are shown regardless of contact intel.
- [x] Define logistics readouts by intel level.
  - Unknown / estimated-size intel: `Logistics: unknown`.
  - faction/threat intel: broad status such as `strained`, `adequate`, `heavy stores`.
  - formation/full identification intel: exact percentages are allowed.
- [x] Apply the same principle to any route/origin/destination lines that should be hidden at low intel.
- [x] Add regression coverage:
  - low intel does not reveal exact fuel/ammo/supply percentages
  - full intel reveals exact logistics
- [x] Verify:
  - `./gradlew test --tests CampaignForceOwnershipTest --console=plain`

## 7. Clarify Player Shipyard Ore Versus Local Shipyard Ore

- [x] Confirm NPC/faction shipyards use local stockpiles from mining and hauling.
  - Regression added: `oreConvoysMoveStockpilesIntoShipyardsForProduction`.
  - Verified with `./gradlew test --tests CampaignForceOwnershipTest --console=plain`.
- [x] Decide whether player shipyard purchases should consume:
  - player/global campaign ore only
  - local shipyard stockpile only
  - both, with local stockpile affecting availability/cost
- [x] If local stockpile matters to the player, expose it in the shipyard preview.
  - Show yard ore, fleet ore, and required ore separately.
- [x] If player/global ore remains the only cost, rename the UI to avoid implying the station stockpile is required.
- [x] Add tests for whichever contract is chosen.

## 8. Separate Live Systems From Model-Only Expansion State

- [x] Audit all expansion state fields bootstrapped inside `CampaignState`.
  - `strategicExpansion`
  - `economyExpansion`
  - `diplomacyNarrative`
  - `operationsExpansion`
  - `productionReadiness`
  - `fleetDoctrineExpansion`
  - `deepCampaignExpansion`
  - `communityContent`
- [x] Classify each as:
  - alpha-live
  - debug/readout-only
  - future/model-only
  - removable from default campaign state
- [x] Hide or clearly label model-only systems in player-facing UI.
  - Do not present compact readout APIs as playable features.
  - This alpha-facing hiding/labeling work belongs in Pass 2 if it affects visible UI.
- [x] Move future/model-only state behind a debug/development flag if it is not needed for normal campaign saves.
  - Current alpha-safe resolution: keep existing persisted fields for save compatibility, but expose model-only systems only through the developer/model-only F3 inspector label.
  - Deeper state separation belongs in Pass 3 after behavior is stable.
- [x] Update save schema docs after classification.
  - `docs/CAMPAIGN_SAVE_SCHEMA.md`
  - `docs/PRODUCTION_COMPLETION_AUDIT.md`
- [x] Add a smoke test that default alpha campaign state does not expose model-only flows as live actions.

## 9. Start Reducing Architecture Bloat Safely

- [x] Create a module map for `CampaignSystem.java`.
  - Objective scripting
  - strategic overmap
  - finite fleet economy
  - hub services
  - persistence helpers
  - tactical encounter spawning
  - UI/readout copy
- [x] Extract only low-risk clusters first.
  - Prefer pure readout/copy helpers or data serializers before simulation logic.
  - Avoid broad rewrites during the bug-fix pass.
- [x] Candidate extraction targets:
  - task-force inspection/readout helpers
  - hub-service preview and action labels
  - campaign yard order serialization
  - finite fleet ledger readouts
- [x] Add package-private tests or keep existing tests green after each extraction.
- [x] Set a practical limit: no extraction PR should change behavior without a focused test.
- [x] Create a similar map for `Renderer.java`.
  - shop overlay
  - strategic map
  - tactical HUD
  - ship rendering delegation
- [x] Move generated/prototype comments out of production code.
  - Known example: `Renderer.drawShip` has a "likely stubbed/empty" generated-comment residue.

## 10. Clean Confusing Terminology

- [x] Build a terminology table for campaign UI.

| Term | Alpha meaning |
| --- | --- |
| `War Room` | Strategic command overview and long-range campaign decisions. |
| `Command HUD` | Action-first campaign overlay for selected map context. |
| `Fleet Hub` | Friendly service location offering repair, trade, supply, jobs, strikes, or shipyard access. |
| `Shipyard` | Hub service that sells player-purchasable ship hulls, excluding station hulls. |
| `Request Trade` | Market exchange that can buy stores for the player or sell fleet salvage/ore. |
| `Strike` | Hostile-contact-only long-range attack category gated by target selection, intel, and resources. |
| `Task Force Inspection` | Intel-gated contact detail panel for a campaign force. |
| `Ore` | Generic resource term; avoid using alone when the spender/owner matters. |
| `Yard Ore` | Local station/faction stockpile used by NPC construction and logistics. |
| `Fleet Ore` | Player campaign ore used for player purchases and trade. |
- [x] Replace generic or misleading terms.
  - `Request Trade` should not secretly mean only "sell ore."
  - `Current Yard Offer` should not imply station hulls or mobile stations are purchasable.
  - `STRIKES` should not appear as a disabled category without a hostile target.
- [x] Check HUD/action copy against tests and docs.
  - `STRATEGIC_HUD_ACTION_FIRST_SPEC.md`
  - `CAMPAIGN_CLARITY_AND_CONSEQUENCE_LAYER.md`
- [x] Add tests for player-facing labels where regressions have happened.

## Done Criteria

- [x] `./gradlew test --fail-fast --console=plain` passes.
- [x] A documented alpha regression command completes locally.
- [x] `CampaignCompatibilityOverhaulTest` passes.
- [x] `CampaignStrategicCommandHudTest` passes.
- [x] Campaign rendering no longer mutates economy/ownership state.
- [x] Hostile finite-pool names survive tactical spawning.
- [x] Low-intel task-force inspection no longer leaks exact logistics.
- [x] Trade service copy and behavior agree.
- [x] Model-only systems are hidden, labeled, or moved behind debug/development pathways.
- [x] At least one low-risk bloat extraction or cleanup lands without behavior drift.

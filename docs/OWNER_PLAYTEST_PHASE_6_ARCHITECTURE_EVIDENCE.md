# Owner Playtest Phase 6 Architecture Evidence

Date: July 8, 2026

## Scope rule

Phase 6 was treated as a stabilization-time architecture pass, not a broad rewrite. Earlier release-blocking phases 3, 4, and 5 are now checked in the owner remediation checklist, so Phase 6 can formalize and extend the small extractions that directly supported those slices.

No cleanup-only package reshuffles, aesthetic renames, or arbitrary partial-class splits were performed.

## Current oversized-system inventory

- `CampaignSystem.java` remains the largest risk file at roughly 49k lines.
- `Renderer.java` remains the next largest risk file at roughly 16k+ lines.
- `Ship.java` remains a serialized-identity and tactical-state risk at roughly 6k+ lines.
- `AISystem.java` remains a tactical-steering risk at roughly 5k+ lines.

File-size reduction is treated as an outcome of responsibility separation, not as a goal achieved by cutting code randomly.

## Completed stabilization-time extractions

| Module | Active slice enabled | Responsibility | Authority impact | Characterization tests |
|---|---|---|---|---|
| `CampaignEconomySystem` | Phase 3 / Vertical Slice 3 resource legibility | Strategic economy ledger/readout | Read-only/resource presentation adapter; `CampaignSystem` still mutates campaign state and `EconomySystem` still owns tactical mining | `OwnerPlaytestVerticalSliceThreeTest`, `CampaignEconomyBalanceAuditTest` |
| `CampaignDifficultySystem` | Phase 4 difficulty visibility and Phase 6 bloat control | Difficulty presentation and runtime-consumer audit | Read-only; `GameContext`, `ExperienceSettings`, and `CampaignSystem` remain runtime authorities | `CampaignDifficultyRuntimeAuditTest`, `CampaignDifficultyOutcomeSeparationTest` |
| `CampaignArcSummarySystem` | Phase 4 campaign arc/objective/ending clarity | Campaign phase, objective, preset, and ending summaries | Read-only; Earth readiness still comes from `CampaignSystem.campaignFinalBattleReadiness` | `OwnerPlaytestPhaseFourCampaignArcAuditTest` |
| `CampaignMapPresentationModel` | Phase 6 read-only map presentation boundary | Immutable sidebar/resource projection for `Renderer` | Read-only copied-list projection; no fleet/resource/contact mutation | `CampaignStrategicUiReadabilityTest`, `OwnerPlaytestPhaseSixArchitectureAuditTest` |
| `PhaseFiveTacticalCleanupSystem` | Phase 5 tactical/control/UI presentation cleanup | Reserve control readouts/actions, hint preference, crew automation explanation, role-balance measurements, art baseline contracts | Uses existing reserve-request state; does not create a second reinforcement authority | `OwnerPlaytestPhaseFiveTacticalCleanupAuditTest`, `AISystemSmallCraftRangeTest`, `AISystemEscortFormationTest`, `TitanGeometryRegressionTest` |

## Public/static call-site inventory

- `CampaignEconomySystem`
  - `CampaignSystem.campaignAuthoritativeEconomyLedgerLines`
  - `CampaignSystem.campaignResourceManagerLines`
  - `CampaignMapPresentationModel.resources`
- `CampaignDifficultySystem`
  - `CampaignSystem.campaignDifficultyTelemetryLines`
  - `CampaignSystem.campaignDifficultyModifierLines`
  - `CampaignSystem.campaignDifficultyRuntimeConsumerAuditLines`
- `CampaignArcSummarySystem`
  - `OwnerPlaytestPhaseFourCampaignArcAuditTest`
  - `PhaseFiveTacticalCleanupSystem.strategicTopFoldLines`
- `CampaignMapPresentationModel`
  - `Renderer.drawGalaxyCommandSidebar`
  - `Renderer.drawCampaignResourceBoard`
- `PhaseFiveTacticalCleanupSystem`
  - `OwnerPlaytestPhaseFiveTacticalCleanupAuditTest`
  - `PhaseFiveTacticalCleanupSystem.strategicTopFoldLines`

## Deferred broad extractions

These are intentionally not marked complete yet:

- `CampaignFleetSimulationSystem`
  - Reason: fleet lifecycle is a live authority path with persistence, intel, search groups, and operation membership.
  - Required protection: fleet identity/save-load/intel/operation characterization plus one small lifecycle adapter seam.
- `CampaignIntelSystem`
  - Reason: contact precision, stale intel, strike gates, and checkpoint persistence are intertwined with campaign force state.
  - Required protection: public call-site inventory, save-schema fixture, and paired stale/exact contact tests.
- `CampaignOperationSystem` / `CampaignTerritorySystem`
  - Reason: operation legality and territory ownership must retain one authority path; partial extraction risks fake captures.
  - Required protection: operation lifecycle tests from muster through legal outcome plus no-transfer-without-cause tests.
- `CampaignTravelSystem` / `CampaignStrikeSystem`
  - Reason: route, posture, risk, strike preflight, and consequences are player-facing balance surfaces.
  - Required protection: route/posture/strike characterization tests and save/load inventory checks.
- `Renderer`, `AISystem`, and `Ship` broad decomposition
  - Reason: these are renderer, tactical steering, and serialized identity refactors rather than one-slice stabilization work.
  - Required protection: screenshot baselines, deterministic tactical scenarios, and explicit serialized identity compatibility tests.

## Phase 6 gate

Passed targeted architecture gate:

```text
.\gradlew.bat test --tests OwnerPlaytestPhaseSixArchitectureAuditTest --tests CampaignEconomyBalanceAuditTest --tests OwnerPlaytestVerticalSliceThreeTest --tests OwnerPlaytestPhaseFourCampaignArcAuditTest --tests OwnerPlaytestPhaseFiveTacticalCleanupAuditTest --tests CampaignStrategicUiReadabilityTest --tests CampaignDifficultyRuntimeAuditTest
```

This gate verifies:

- Every completed extraction names the active slice it enabled.
- Every completed extraction records public/static call sites.
- Every completed extraction has characterization tests.
- No completed extraction reports a new parallel authority path.
- The read-only map presentation boundary is actually used by `Renderer`.
- The remaining massive files are still called out as high risk instead of being hidden by partial cleanup.

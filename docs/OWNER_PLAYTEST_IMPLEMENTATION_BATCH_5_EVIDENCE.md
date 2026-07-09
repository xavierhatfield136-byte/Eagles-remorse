# Owner Playtest Implementation Batch 5 Evidence

Date: July 8, 2026

## Scope completed

- Extracted difficulty readout and runtime-consumer audit logic into `CampaignDifficultySystem`.
- Added `CampaignDifficultyRuntimeAuditTest`.
- Tightened route-choice descriptions so mid-campaign branch choices plainly identify:
  - the standard/safest route,
  - the slower salvage/recovery route,
  - the fastest higher-interception deep-strike route.
- Added route-choice tradeoff coverage to `CampaignStrategicTravelPressureTest`.
- Updated the asteroid mining prompt to display the current accessibility input mode:
  - `ORE: Hold F`
  - `ORE: Toggle F`
- Added mining prompt coverage to `CampaignPhaseTenAccessibilityInputTest`.
- Added a cross-tab invariant that every visible disabled campaign action has a non-empty player-facing reason.

## Architecture work

`CampaignDifficultySystem` removes difficulty presentation and audit responsibility from `CampaignSystem` while leaving `CampaignSystem` as the adapter for private campaign after-action counts.

This is a stabilization-time extraction tied to Phase 4 difficulty visibility and Phase 6 bloat control. It does not change save formats, enum names, stable IDs, or campaign authority paths.

## Verification run

Passed:

```text
.\gradlew.bat test --tests CampaignDifficultyRuntimeAuditTest --tests CampaignDifficultyOutcomeSeparationTest --tests CampaignPhaseFiveDifficultyAttritionTest
.\gradlew.bat test --tests CampaignStrategicTravelPressureTest
.\gradlew.bat test --tests CampaignPhaseTenAccessibilityInputTest
.\gradlew.bat test --tests CampaignStrategicUiReadabilityTest
```

## Deferred to owner worksheet

- Whether the three route-choice tones feel meaningfully different during normal play.
- Whether the current `Hold F` / `Toggle F` prompt wording is preferred over shorter labels such as `HOLD F TO MINE`.
- Whether preset differences are noticeable enough in the first 15-30 minutes without overcorrecting Standard.

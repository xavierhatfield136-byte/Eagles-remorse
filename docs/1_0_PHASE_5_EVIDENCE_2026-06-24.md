# Phase 5 Evidence - Difficulty, Attrition, And Late Campaign

Date: 2026-06-24

## Outcome

Phase 5 is complete. Standard retains the existing time-to-kill and recoverable
campaign model, while Iron Command now has explicit enemy-only armor and shield
reboot advantages. Travel, repair, strike, doctrine, retreat, and late-campaign
rules are exposed through player-facing readouts and covered by regression tests.

## Implemented

- Added Standard difficulty telemetry for battle reports, major losses, retreats,
  and current resource emergencies, including the one-in-five target and hub
  recovery rule.
- Kept tactical scaling focused on fleet composition, role diversity, capitals,
  escorts, reserves, doctrine, and retreat behavior instead of generic hull HP.
- Preserved route-scaled fuel, supply, ammunition, encounter-density, territory,
  contact-activity, sustainability warning, and recovery-hub forecasts.
- Added repair-duration disclosure and clarified that field repair consumes
  supplies/salvage while full armor and system restoration requires a repair hub.
- Preserved finite credits, ore sales, faction trade, market reputation effects,
  shortages, transaction safeguards, and economy soak coverage.
- Preserved limited strategic strikes, visible resource/package costs,
  target-quality intelligence gates, discoverable rearm locations, and disabled
  enemy strategic strikes.
- Added a plain-language doctrine audit. `LINE` is defined as broad lateral
  spacing with overlapping fire and shallow reinforcement rows; formations do
  not alter raw hull health or create ships.
- Preserved the 7.5-second withdrawal wind-up and interruption behavior. Added
  explicit allied escape/risk rules plus pursuit, exposure, alert, and supply
  aftermath on tactical withdrawal.
- Iron Command now applies +18% enemy armor-system durability and a 38% shorter
  enemy shield reboot delay. These bonuses do not apply to friendly ships or
  Standard mode and are shown before campaign launch.
- Final readiness now penalizes surviving major Red mobile forces and names
  those remnants in readiness/victory text. Late-game reporting also exposes
  Green operations and Yellow alignment consequences.

## Automated Coverage

New suite:

- `CampaignPhaseFiveDifficultyAttritionTest`
  - Standard versus Iron enemy defense comparison
  - friendly-ship isolation and no generic hull-health inflation
  - pre-launch difficulty disclosure
  - Standard recovery and telemetry contract
  - travel, repair, strike, credits, and ore readouts
  - doctrine and retreat contracts
  - Red-remnant ending accountability
  - Green/Yellow late-campaign consequence reporting

Focused neighboring-system regression:

- `FirstHourExperienceTest`
- `CampaignStrategicTravelPressureTest`
- `CampaignHubEconomyTest`
- `CampaignEconomyDiplomacyExpansionSystemTest`
- `CampaignStrategicStrikeCounterplayTest`
- `CampaignPersistentFleetTest`
- `CampaignPhaseFiveDifficultyAttritionTest`

## Validation

- `gradlew test --tests CampaignPhaseFiveDifficultyAttritionTest`
  - PASS, 6 tests
- Focused neighboring-system regression command
  - PASS
- `gradlew test`
  - PASS, 828 tests, 0 failures, 0 errors
- `gradlew productionValidation`
  - PASS
- `gradlew saveLoadSoak`
  - PASS, 100 cycles
- `gradlew campaignTransitionFuzz`
  - PASS, 24 checkpoints and 24 restores
- `git diff --check`
  - PASS; only existing line-ending notices remain

## Checklist

Phase 5: 99 checked, 0 unchecked.

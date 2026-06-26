# Phase 6 Evidence - Reputation, Aid, And Minimum Politics

Date: 2026-06-24

## Outcome

Phase 6 is complete. Green and Yellow reputation are visible, tiered, reasoned,
and persistent. Blue can transfer ore, credits, intelligence, or persistent
ships to either faction. Aid changes faction logistics, operations, contacts,
alignment, final readiness, and ending summaries.

## Implemented

- Reputation readouts show current value, tier, benefits, next threshold, recent
  change, and durable reasoned history.
- Reputation changes from faction-board missions and aid use a shared history
  path with “because you did X” feedback.
- Aid previews show cost, expected reputation, strategic effect, availability,
  and ship-confirmation requirements.
- Ore aid feeds faction stockpiles, readiness, and replacement operations.
- Credit aid feeds repair, fuel, ammunition, escorts, and relief operations.
- Intelligence aid reveals hostile contacts and faction-relevant facilities.
- Persistent-ship aid requires confirmation, removes the ship from Blue,
  creates a visible recipient detachment, adds the hull to recipient inventory,
  and records its original hull identity.
- Transaction identifiers prevent duplicate aid rewards.
- Yellow material aid and reputation can move Yellow from transactional or
  coerced status into a saved Blue-Yellow alliance.
- Existing Yellow liberation missions remain tied to real facility targets;
  high reputation enables later operations and coalition support.
- Campaign memory now reports trade totals, faction aid, ship/capital/titan
  kills, mining records, allied battles, and territory records.
- Captain Voss, Broker Marr, and Commander Rook provide concise tagged
  reactions. Audio reactions remain optional, captions are canonical, and
  placeholder voices are explicitly excluded from release content.
- Final battle readiness and ending-memory lines reference Green aid, Yellow
  alignment, reputation history, kills, allied battles, and abandoned territory.
- Checkpoint restoration now preserves the Blue-Yellow alliance flag that was
  already present in the save contract.

## Automated Coverage

New suite:

- `CampaignPhaseSixReputationAidTest`
  - reputation values, tiers, thresholds, benefits, and reasons
  - ore, credit, and intelligence costs/effects
  - duplicate transaction rejection
  - confirmed persistent-ship transfer
  - Blue inventory removal and recipient-force creation
  - original hull identity record
  - save/load of reputation, aid ledger, and Yellow alliance
  - Yellow liberation alignment
  - named character reactions, quiet mode, captions, and voice policy
  - ending and final-readiness behavior callbacks

Focused neighboring-system regression:

- `CampaignTheaterConquestChecklistTest`
- `CampaignHubEconomyTest`
- `CampaignNpcFleetAiTest`
- `CampaignStrategicCommandHudTest`
- `CampaignEconomyDiplomacyExpansionSystemTest`
- `CampaignYellowNeutralityTest`
- `CampaignSaveCompatibilityContractTest`
- `CampaignPhaseSixReputationAidTest`

## Validation

- `gradlew test --tests CampaignPhaseSixReputationAidTest`
  - PASS, 6 tests
- Focused politics/economy/fleet/save regression command
  - PASS
- `gradlew test`
  - PASS, 834 tests, 0 failures, 0 errors
- `gradlew productionValidation`
  - PASS
- `gradlew saveLoadSoak`
  - PASS, 100 cycles
- `gradlew campaignTransitionFuzz`
  - PASS, 24 checkpoints and 24 restores
- `git diff --check`
  - PASS; only existing line-ending notices remain

## Checklist

Phase 6: 69 checked, 0 unchecked.

# Phase 3 Evidence - Mining, Logistics, Shipyards, And Construction

## Strategic mining and conserved ore

- Added player-issued strategic mining assignment, recall, and reassignment
  controls with expected yield, duration, route risk, free-capacity
  requirement, escort recommendation, cargo, and return-destination readouts.
- Red, Green, and Yellow mining forces use real finite miner/hauler inventories,
  named origins, ore targets, cargo limits, escort/retreat policies, and saved
  campaign-force state.
- Corrected the ore lifecycle so extraction reduces the mining site and adds
  only to force cargo. Ore reaches a faction pool only after unloading at a
  valid same-faction logistics destination.
- Cargo is capped, cannot transfer twice, is lost and logged with a destroyed
  force, can be captured exactly once, and is hidden from insufficient hostile
  intelligence.
- Mining depletion, site hazards, hostile interruption, route exposure,
  faction logistics memories, and structured departure/return telemetry are
  authoritative.

## Shipyards and production

- Added escort, frigate/destroyer, cruiser, capital, and titan/special lanes
  with base times of 5, 10, 15, 20, and 25 seconds.
- Unrelated lanes advance concurrently; orders in the same lane preserve queue
  order.
- Player orders retain producing faction, producing yard, hull role, costs,
  queue position, completion time, and persistent delivery state.
- Damaged yards operate at 50% throughput. Offline or blockaded yards pause.
  Capture pauses incompatible orders. Destruction cancels work with a single
  defined 50% resource refund.
- Faction AI uses the same lane model, finite ore and industrial supplies,
  local queue capacity, titan restrictions, hull inventory shortages, and
  recorded construction reasons.
- Added shipyard-state and production-ledger readouts for owner, local identity,
  catalog, lanes, queue capacity, damage, blockade, services, completion
  location, remaining time, and pause reasons.

## Transport repair and industry missions

- Standard transports now provide slow nearby internal-room and fire support
  with a two-source stacking cap.
- Support consumes 0.20 campaign supplies per second per active transport and
  stops when supplies are exhausted.
- Transport support excludes armor rooms and shields; full restoration remains
  a safe-hub service.
- Added real-target mission contracts for mining escort, mining raid,
  ore-hauler interception, shipyard defense, crippled-yard resupply, miner
  rescue, stolen-ore recovery, and capital-completion interdiction.
- Mission outcomes mutate authoritative cargo, force readiness, yard damage,
  supply stock, or queue time. Completed mission IDs prevent duplicate rewards.

## Automated evidence

- `CampaignPhaseThreeIndustryTest`
- `CampaignForceOwnershipTest`
- `CampaignNpcFleetAiTest`
- `CampaignMiningDepositTest`
- `CampaignFleetBuildingIntegrationTest`
- `CampaignPersistentFleetShopTest`
- `CampaignHubEconomyTest`
- `CampaignReleaseTelemetryContractTest`
- `CampaignSaveCompatibilityContractTest`
- `CampaignSaveFieldContractTest`

## Release gates

- Full test suite: 815 tests, 0 failures, 0 errors, 0 skipped
  (`BUILD SUCCESSFUL` in 4m 5s).
- `productionValidation`: passed.
- `saveLoadSoak`: 100 cycles, passed.
- `campaignTransitionFuzz`: 24 checkpoints and 24 restores, passed.

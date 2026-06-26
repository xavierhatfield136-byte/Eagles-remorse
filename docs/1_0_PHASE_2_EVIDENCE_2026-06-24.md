# Phase 2 Evidence - Faction Inventories And Fleet Population

## Authoritative finite inventories

- Added a developer order-of-battle report covering faction role inventories,
  active fleets, garrisons, convoys, mining groups, reserves, construction,
  unassigned records, and ownership/provenance audits.
- Expanded Red, Green, and Yellow finite inventories with faction-appropriate
  patrol, escort, logistics, capital, and rare titan hulls distributed across
  real faction-controlled facilities.
- Allocation gives every force one finite hull before filling larger
  compositions, applies mission/doctrine/region preferences, and never mints an
  emergency hull when inventory is exhausted.
- Added patrol, mining, convoy, defense, hunter-killer, capital, titan, and
  mixed-fleet composition contracts.

## Blue, capitals, and titans

- Blue starts with the mothership, a vulnerable picket-sized escort, and the
  persistent miner `Blue Prospector One`.
- Added repeatable late-region Red, Green, and Yellow capital task forces.
- Titans require exceptional task-force strength and late-region or explicitly
  authored conditions. Ordinary early patrols cannot claim them.
- Titan replacement construction requires a strategic-value-5 shipyard,
  campaign maturity, heavy ore and repair supplies, and a faction inventory
  below its intended floor.
- High-intelligence contacts show capital/titan warnings and persistent
  titan-hunt opportunities with major rewards and reputation effects.
- Capital/titan losses remove finite records, change campaign pressure or
  reputation, and create named recovery contacts.

## Tactical conversion

- Persistent encounter manifests are no longer truncated by tactical display
  limits.
- Role, faction, persistent name, hull/armor condition, crew readiness,
  ammunition state, formation role, and retreat intent enter the tactical
  manifest.
- Exact-role construction prevents heavy persistent ships from being silently
  downgraded by the generic enemy spawner.
- Tactical ship IDs map back to finite records so survivors return, destroyed
  ships are removed, and duplicate tactical instances are rejected.

## Traffic and inspection

- Added saved per-travel-leg observed-contact and tuned-target counters.
- Contact targets scale with route duration and interception risk.
- Inspection includes estimate age, early faction identity, high-intelligence
  cargo/readiness/intent, composition, escort/support counts, and capital/titan
  warnings while retaining low-intel hiding.
- Yellow-produced player purchases retain Yellow hull identity after delivery.

## Automated evidence

- `CampaignPhaseTwoFleetPopulationTest`
- `CampaignForceOwnershipTest`
- `CampaignNpcFleetAiTest`
- `CampaignLivingWarSystemTest`
- `CampaignOvermapEncounterFlowTest`
- `CampaignOvermapCheckpointTest`
- `CampaignFleetBuildingIntegrationTest`
- `CampaignPersistentFleetShopTest`
- `CampaignYellowNeutralityTest`
- `CampaignSaveCompatibilityContractTest`
- `CampaignSaveFieldContractTest`

## Release gates

- Full test suite: 807 tests, 0 failures, 0 errors, 0 skipped
  (`BUILD SUCCESSFUL` in 5m 52s).
- `productionValidation`: passed.
- `saveLoadSoak`: 100 cycles, passed.
- `campaignTransitionFuzz`: 24 checkpoints and 24 restores, passed.

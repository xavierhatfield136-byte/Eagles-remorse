# Phase 4 Evidence - Dynamic Territory And Autonomous War

## Territory and ownership

- Strategic nodes and real campaign locations now transfer ownership together.
- Captures require surviving local garrison strength and a valid faction supply
  connection.
- Ownership updates control visuals, occupation/alert state, services,
  shipyard and mining access, mission tags, patrol generation, economy
  ownership, route caches, reputation-facing logs, and campaign bulletins.
- Contest progress, previous ownership, occupation, services, and reasons
  persist through existing location and strategic-node save payloads.

## Autonomous objectives and battles

- Red directors score infrastructure defense, logistics raids, player hunting,
  blockades, sieges, and wider invasion conditions.
- Green directors score location defense, logistics escort, rescue,
  counteroffensives, and territorial assaults.
- Yellow behavior now derives from explicit coerced-hostile,
  transactional-neutral, and liberated-friendly alignment.
- AI battles retain one authoritative record and participant manifest, resolve
  off-screen, apply ammunition/readiness/hull/cargo loss, damage or destroy
  finite inventory records, pursue or retreat, alter territory, reputation,
  memory, and create aftermath salvage.
- Joining a battle transfers resolution to tactical command and closes the
  auto-resolve record, preventing duplicate outcomes.

## Warning, following, and intervention

- Major-battle warnings expose the approximately 30-second warning target,
  participants, theater, estimated strength, distance, ETA, and available
  actions.
- Battle prompts support Follow Fleet, Join Battle, Ignore, Offer Support, and
  Observe.
- Green and liberated Yellow fleets can be followed. Their changing position
  continuously updates the travel target; destruction or retreat ends follow.
- Prolonged contested fronts expose ore, credits, intelligence, ship,
  offensive, logistics-interdiction, and shipyard intervention choices.
- Stalemate intervention consumes real assets, never creates free fleets,
  prevents duplicate rewards, and materially shifts theater state. Ignoring it
  leaves the front unresolved.

## Divergence

- Starting seeds remain deterministic.
- Director briefs and theater causes persist through checkpoint restore.
- Identical action sequences produce identical divergence signatures.
- Alliance, reputation, aid, battle, ownership, and intervention choices create
  different saved campaign outcomes.

## Automated evidence

- `CampaignPhaseFourAutonomousWarTest`
- `CampaignNpcFleetAiTest`
- `CampaignLivingWarSystemTest`
- `CampaignStrategicLoopIntegrationTest`
- `CampaignTheaterConquestChecklistTest`
- `CampaignTheaterConquestStateTest`
- `CampaignStrategicCommandHudTest`
- `CampaignOvermapCheckpointTest`
- `CampaignSaveCompatibilityContractTest`
- `CampaignSaveFieldContractTest`

## Release gates

- Full test suite: 822 tests, 0 failures, 0 errors, 0 skipped
  (`BUILD SUCCESSFUL` in 4m 11s).
- `productionValidation`: passed.
- `saveLoadSoak`: 100 cycles, passed.
- `campaignTransitionFuzz`: 24 checkpoints and 24 restores, passed.

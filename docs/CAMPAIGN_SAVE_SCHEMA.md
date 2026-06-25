# Campaign Save Schema

## 1.0 Compatibility Boundary

- Current checkpoint schema: `2`.
- Earliest supported public alpha checkpoint: schema `1`.
- Schema `1` fixture: `test/fixtures/campaign_schema_v1.properties`.
- The executable inventory is `app.persistence.CampaignSaveContract`.
- Every public `CampaignCheckpointStore.Checkpoint` field is inventoried with a
  release status, declared default, and fallback. `CampaignSaveFieldContractTest`
  fails if a checkpoint field is omitted from that inventory.

Field status meanings:

- `AUTHORITATIVE_LIVE`: affects the campaign, tactical state, economy, fleet,
  travel, reputation, territory, environment, or durable release telemetry.
- `DEBUG_READOUT_ONLY`: regenerated explanatory/director readouts; never trusted
  as authoritative simulation state.
- `FUTURE_MODEL_ONLY`: retained for compatibility, restored to deterministic
  seeded defaults, and not advertised as a 1.0 playable system.
- `MIGRATION_METADATA`: populated during load and not written as gameplay state.

Authoritative field families include:

| Family | Checkpoint fields | Missing-data behavior |
| --- | --- | --- |
| Core run | version, world, seed, sector, credits, kills, branch, objectives, unlocks | Clamp to valid campaign bounds |
| Player ship | faction, role, weapon, hull, shield, mining, CIWS, power, crew, turrets, carrier deck | Unknown enums use safe standard loadouts |
| Fleet | owned titans, persistent fleet, groups, commitments, finite ship pool | Rebuild a valid seeded player/faction inventory when absent |
| Economy | ore, cargo, fuel, supplies, ammo, salvage, base stockpiles, attrition | Preserve legacy cargo; add minimum operational migration reserves |
| Production | player yard orders, faction base queues, IDs and timers | Missing queues become empty; paid work is never invented |
| Travel | positions, selected target, heading, route, progress, risk, speed | Invalid routes are cleared; known location position is restored |
| Encounters and strikes | active encounter IDs, search groups, pending reinforcements, strike objects/inventory | Invalid references are discarded; inventories receive schema-v1 defaults |
| Territory | location states, theaters, nodes, ownership, influence, Earth operation | Recomputed from stable authored location IDs when absent |
| Reputation and narrative | Green/Yellow reputation, relationship states, diplomacy payload, logs and memory | Preserve counters; bootstrap missing history |
| Environment | location completion, intel, aftermath, routes, station state, hazards | Restore authored defaults and apply saved overrides |
| Telemetry | production-readiness event log | Restore durable release events; bootstrap an empty safe log |

## Migration Rules

Schema `1` to schema `2` repairs:

- Missing fuel/supplies/ammunition receive operational reserves.
- Missing strike inventories receive bounded starter defaults.
- Missing faction fleets are rebuilt from authored facilities and finite
  inventory rules.
- Missing player fleet data is reconciled with the resumed flagship.
- Missing player and faction production queues become empty.
- Missing mining cargo preserves legacy cargo and receives a minimum ore ledger.
- Missing territory and strategic nodes are recomputed from location ownership.
- Missing reputation history is bootstrapped while reputation counters survive.
- Missing environment state restores authored location defaults.
- Unknown faction, route, power, and gameplay enums use documented safe defaults.
- Malformed serialized nested enums use their local `parseEnum` fallback.

Migration safety:

1. Loading analyzes and normalizes the checkpoint in memory without changing the
   source file.
2. Before committing a migration, the source is copied to
   `campaign_checkpoint.properties.pre-migration-v<schema>.bak`.
3. The migrated checkpoint is atomically saved and loaded again.
4. If verification fails, the source backup is restored.
5. The player receives a `SAVE RECOVERED FROM SCHEMA ...` banner and a structured
   `campaign.save_recovery` event listing non-personal repair categories.

Checkpoint properties are backward-compatible and normalized on load.

## Expansion Fields

Expansion checkpoint fields are saved for compatibility and deterministic restoration. They are not all
player-facing alpha systems. The live status column is the release-facing contract:
`alpha-live` affects normal campaign play, `debug/readout-only` is visible only through developer/audit
surfaces, and `future/model-only` is persisted prototype state that must not be presented as a playable feature.

| Field | Added for | Alpha status | Fallback |
| --- | --- | --- | --- |
| `strategicExpansionState` | Section 5 strategic task groups | future/model-only | Seeded strategic bootstrap |
| `economyExpansionState` | Section 6 logistics state | alpha-live support model | Seeded economy bootstrap |
| `diplomacyNarrativeState` | Section 7 reputation and crew state | alpha-live support model | Seeded diplomacy bootstrap |
| `operationsExpansionState` | Sections 8-11 command preferences | future/model-only | Seeded operations bootstrap |
| `productionReadinessState` | Sections 12-15 longevity preferences | debug/readout-only | Seeded production bootstrap |
| `fleetDoctrineExpansionState` | Sections 16-18 stretch and fleet-doctrine preferences | debug/readout-only | Seeded doctrine bootstrap |
| `deepCampaignExpansionState` | Sections 19-25 living campaign simulation and legacy state | future/model-only | Seeded deep-campaign bootstrap |
| `communityContentState` | Section 26 scenario-editor and local community preferences | future/model-only | Seeded community-content bootstrap |

Unknown or malformed expansion payloads fall back to deterministic seeded defaults.

## Phase 2 Travel-Density Fields

`transitContactEventsThisLeg` and `transitContactTargetThisLeg` are
authoritative live fields. Missing values default to zero, and a new tuned
target is computed whenever a travel leg begins.

## Phase 3 Mining And Production Payloads

- Strategic mining assignments are authoritative `campaignForces` records.
  Their source, destination, home base, cargo kind, cargo load, cargo capacity,
  work state, stop reason, mission state, route, and timers survive checkpoint
  restore. Ore is credited only when saved cargo unloads at a valid
  same-faction logistics destination.
- Faction construction and repair work remains authoritative in
  `campaignBaseQueues`. Production lanes are derived deterministically from the
  saved hull role, so old saves acquire the correct escort,
  frigate/destroyer, cruiser, capital, or titan/special lane without migration
  data.
- Player `campaignYardOrders` now serialize the producing faction before the
  fleet-slot field. The loader accepts both the legacy 12-field payload and the
  Phase 3 13-field payload. Lane identity is derived from the saved hull role.
- Yard ownership, damage, service, blockade memory, destruction, and local
  stockpiles remain in saved galaxy-location state. These fields pause, slow,
  cancel, or resume saved queues after restore.
- Transport-assisted internal repair consumes the already-authoritative
  `campaignSupplies` pool. The fractional per-frame accumulator and current HUD
  activity marker are transient; no repair progress or free supply is created
  by loading.

## Phase 4 Territory And Autonomous-War Payloads

- Location ownership, control visuals, station service state, mission tags,
  stockpiles, damage, memories, routes, and change timestamps remain
  authoritative in `galaxyLocationStates`.
- Strategic-node owner, previous owner, contest progress, and takeover cooldown
  remain authoritative in `strategicNodes`. Restored nodes and locations are
  reconciled by stable location ID.
- Active AI battles persist participant force IDs, stage, elapsed time,
  duration, importance, intervention state, outcome, pursuit/retreat follow-up,
  and the initial participant manifest. Older battle payloads regenerate the
  manifest from surviving force records.
- Following an allied fleet uses the saved selected task-group ID plus a
  `FOLLOWING_FORCE:<id>` campaign-memory flag. Missing, destroyed, or retreating
  targets safely end following after restore.
- Yellow alignment is derived from saved alliance, liberation reputation,
  joined-fleet, Red pressure, theater influence, and alert state. No parallel
  unsaved alignment variable exists.
- Stalemate interventions use saved resources, theater state, campaign memory,
  and completed mission IDs. They cannot mint fleets or duplicate rewards after
  load.

## Phase 7 Tactical Environment Payloads

- `activeMapModifiers` persists the active tactical environment identities as a
  comma-separated enum list. Missing or malformed values restore `NONE`, while
  normal authored sector startup can reapply its environment script.
- `environmentHazardPulseIndex` persists the last resolved ion/solar hazard
  pulse. Restoring a checkpoint cannot replay already-resolved disruption.
- Sensor, movement, weapon-range, hazard, AI-behavior, counterplay, quarantine,
  asteroid-cover, background, audio, and accessibility rules are derived from
  the active modifier list rather than duplicated save fields.

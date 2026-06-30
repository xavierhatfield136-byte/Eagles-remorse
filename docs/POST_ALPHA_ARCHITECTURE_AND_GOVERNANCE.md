# Post-Alpha Architecture And Governance

This document is normative for implementation slices. Feature IDs are stable;
renaming a feature does not change its ID.

## Feature registry and module ownership

| Feature ID | System owner | Authoritative state | Primary persistence |
|---|---|---|---|
| PA-1A | `StrategicCampaignExpansionSystem` | territory IDs, graph, ownership, controller, lanes | `strategicExpansionState` |
| PA-1B | `StrategicCampaignExpansionSystem` | strategic operation legality and active operations | `strategicExpansionState` |
| PA-1C | `Faction`, `CampaignSystem` | Bright/Dark identity and legacy Yellow migration | campaign checkpoint + faction names |
| PA-1D | `StrategicCampaignExpansionSystem`, `CampaignSystem` | civil-war geography, aid, operations, outcomes | strategic/campaign checkpoint |
| PA-1E | `StrategicCampaignExpansionSystem` | supply, isolation, pressure, director inputs | `strategicExpansionState` |
| PA-2A | `StrategicCampaignExpansionSystem` | factual war-event ledger | `strategicExpansionState` |
| PA-2B | `StrategicCampaignExpansionSystem` | rival commander records and adaptation | `strategicExpansionState` |
| PA-2C | `FlagshipOperationsSystem` | compartments, abstract teams, automation | `flagshipOperationsState` |
| PA-2D | `BoardingRescueSystem` | boarding/rescue operations and outcomes | `boardingRescueState` |
| PA-2E | `AlternativeCampaignSystem` | campaign identity and starting package | `alternativeCampaignState` |
| PA-2F | `CooperativeCommandSystem` | local role authority prototype | `cooperativeCommandState` |

`CampaignSystem` adapts the live campaign into the strategic model; it must not
maintain a competing mutable adjacency or operation-legality model. Tactical
combat consumes resolved operation context and reports structured outcomes; it
does not decide territorial legality. UI is a projection only.

## Typed IDs

The strategic layer owns typed `TerritoryId` values and stable string IDs for
routes, operations, commanders, fleets, stations, beachheads, campaigns, and
events. String IDs are serialized, never display names. IDs are immutable after
creation and comparisons use canonical normalized values.

## Event contracts

State-changing strategic actions append a factual event containing stable event
ID, type, campaign time, actor/sponsor, origin, target, operation ID, result,
and numeric consequences where applicable. Tactical resolution may report
losses and objective results; the strategic owner validates and applies them.
UI and history views read events without inventing facts. Unknown information
is displayed as unknown rather than reconstructed as certainty.

## Persistence and migration

`CampaignCheckpointStore` owns the envelope schema. Each post-alpha subsystem
owns a version-tolerant serialized payload and safe bootstrap behavior for a
missing payload. Legacy `TEAM_D` values are migration-only input and map
deterministically to Bright or Dark Yellow using a stable record key. Source
saves are not modified in place. Unknown enums and missing optional fields use
documented safe defaults and generate a recovery note.

Content packs may read `TEAM_D` as a deprecated alias. New content must use
`BRIGHT_YELLOW` or `DARK_YELLOW`; ambiguous writes are rejected with an
actionable migration warning.

## Determinism

Strategic selection derives randomness from the campaign seed plus stable
feature, actor, operation, and tick IDs. UI order, wall-clock time, frame rate,
and collection iteration order must not own random state. Fixed seed and fixed
inputs must produce identical candidate scores, choices, and events.

## Diagnostics and traceability

Developer inspection must expose territory ID, owner, controller, control and
supply state, adjacency, route conditions, legal operations, rejected reasons,
pressure inputs, and committed plans. Every completed checklist item must cite
code, tests, save fields, UI, or an acceptance artifact in
`POST_ALPHA_IMPLEMENTATION_EVIDENCE_2026-06-29.md`.

## Feature flags

Flags default conservatively and are read through `PostAlphaFeatureFlags`:

- `territory_fronts`
- `yellow_split`
- `strategic_operations`
- `yellow_civil_war`
- `supply_pressure`
- `war_memory`
- `rival_commanders`
- `flagship_operations`
- `boarding_rescue`
- `alternative_campaigns`
- `cooperative_command_prototype`

Track B flags remain off in public configuration until their own release gate.

## Performance budgets

Budgets are measured on the supported minimum machine with a 200-territory,
600-lane, 200-active-operation stress save:

| Metric | Budget |
|---|---:|
| Strategic update p95 | 8 ms |
| Territory inspector refresh p95 | 16 ms |
| Additional steady-state heap | 96 MiB |
| Compressed post-alpha checkpoint payload | 8 MiB |
| Checkpoint save p95 | 750 ms |
| Checkpoint load p95 | 1,500 ms |

A phase cannot be called release-ready without a full deterministic campaign
soak, illegal-transition audit, save/load cycle, and budget report. Prototype
completion is not release readiness.


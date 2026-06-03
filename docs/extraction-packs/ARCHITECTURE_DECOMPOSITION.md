# Architecture Decomposition

## Scope

Replace inventory-style architecture claims with enforced ownership boundaries, typed IDs, transition APIs, validators, structured telemetry, and save-schema diff checks.

## Dependencies

- `CampaignSystem`
- `CampaignCheckpointStore`
- content-pack loaders
- validation build tasks
- telemetry harnesses

## UI Flow

Architecture validation is surfaced through developer reports, build failures, and diagnostics. Normal-player UI should only show recovery messages.

## Data Ownership

Each feature family owns its domain state and exposes transition APIs. Cross-system callers should pass typed references or validated IDs.

## Save Impact

Schema fields need migration defaults, compatibility fixtures, and visible recovery messaging for corrupt or incompatible saves.

## Asset Needs

No art requirement.

## Tests

Cover schema validation, save-schema diffing, transition API invariants, malformed data, migration fixtures, and telemetry emission.

## Non-Goals

This pack does not require splitting `CampaignSystem` in one pass.

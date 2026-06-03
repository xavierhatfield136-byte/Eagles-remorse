# Stability And State-Machine Hardening

## Scope

Make campaign overlay, encounter, docking, travel, tactical-entry, warp-exit, and save/load transitions recoverable and inspectable.

## Dependencies

- `UISystem` overlay ownership and invariant repair.
- `GameContext` modal and campaign state.
- `CampaignCheckpointStore` recovery paths.

## UI Flow

The player should always see one primary blocking surface, a clear escape route, and recovery messaging when a stale prompt is repaired.

## Data Ownership

Authoritative transition state belongs to `CampaignSystem.CampaignState`, `UiState`, and checkpoint metadata. Prototype-only flags should not own transition truth.

## Save Impact

Persist enough modal, encounter, travel, and checkpoint metadata to resume without orphaned prompts or mismatched tactical state.

## Asset Needs

No new art is required. Recovery banners and diagnostics can use existing HUD styles.

## Tests

Cover overlay permutations, stale prompt repair, save/load during travel, docking, encounter prompts, and tactical exit.

## Non-Goals

This pack does not redesign the campaign map, combat model, or menu shell.

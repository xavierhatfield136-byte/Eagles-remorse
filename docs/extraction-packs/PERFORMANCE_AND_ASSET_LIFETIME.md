# Performance And Asset Lifetime

## Scope

Keep late-campaign, missile-stress, x-ray, audio, save/load, and large-fleet scenarios inside measured frame, memory, and allocation budgets.

## Dependencies

- `PerformanceGuardrails`
- `ChecklistV2Harness`
- `Phase9TelemetryHarness`
- asset prewarm and bounded-cache systems

## UI Flow

Expose budget failures through developer diagnostics and concise report output, not normal-player screens.

## Data Ownership

Budgets belong in executable harnesses and build tasks. Asset lifetime belongs to cache and manifest systems, not scattered render callers.

## Save Impact

Performance diagnostics should not change save data except for explicit telemetry snapshots.

## Asset Needs

Asset manifests must distinguish approved assets, generated placeholders, duplicate names, and missing mappings.

## Tests

Run strict harnesses for room hits, hazards, x-ray draw, audio dispatch, checkpoint soak, and large-fleet stress.

## Non-Goals

This pack does not require visual replacement of approved placeholders.

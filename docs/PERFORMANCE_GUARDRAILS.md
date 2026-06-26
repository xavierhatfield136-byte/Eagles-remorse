# Performance Guardrails

The runtime performance policy lives in `src/PerformanceGuardrails.java`.

## Live Diagnostics

Enable the developer overlay with `F3`. It reports frame, update, render, AI, campaign, asset-decode, GC, sprite-memory, visible-sprite, wreck, VFX, and UI-panel metrics.

`AssetLoadGuard` reports image decode during rendered frames and disk loads after gameplay begins. All runtime `ImageIO.read(...)` calls route through this tracker.

## Visual Budgets

Frame pressure automatically steps visual quality through `HIGH`, `MEDIUM`, `LOW`, and `EMERGENCY`. Lower tiers reduce distant-ship detail, VFX density, projectile trails, and wreck chunks.

Decoded image libraries use bounded caches. `SpriteAtlasRegistry` builds compact atlases for multipart ships, turrets, wreck chunks, projectiles, and UI images as those categories are loaded. `AssetPrewarmManifest` limits startup prewarm work to the relevant game mode.

## Verification

- `gradlew performanceGuardrailSmoke`
- `gradlew performanceGuardrailSoak`
- `gradlew phase9Performance`
- `gradlew saveLoadSoak`
- `gradlew performanceGuardrailsCi`

The smoke scenario runs a late-campaign-shaped battle with hundreds of ships and sustained missile traffic. The extended task increases battle duration for memory-soak runs. Save/load soak uses an isolated temporary checkpoint file.

Phase 9 adds named battle-scale reports for ordinary tactical play, the largest
supported alpha battle, and 100/160-per-side stress cases. These reports emit
JSON under `build/reports/phase9_*.json` with ship, projectile, wreck, VFX,
timing, heap, GC, asset-decode, gameplay disk-load, and frame-budget fields.

As of the 2026-06-25 Phase 9 completion pass, the supported FPS envelope is
intentionally conservative: ordinary tactical play is 14 ships per side, and the
largest supported alpha battle is 50 ships per side using Low visual detail and
Tactical FPS View. 100-per-side and 160-per-side battles remain stress reports,
not minimum-hardware FPS promises.

## Alpha Release Gate

Use this local release-confidence gate before an alpha handoff:

```powershell
.\gradlew test `
  --tests CampaignCompatibilityOverhaulTest `
  --tests CampaignStrategicCommandHudTest `
  --tests CampaignHubEconomyTest `
  --tests CampaignForceOwnershipTest `
  --tests CampaignStrategicUiReadabilityTest `
  --tests ExpansionIntegrationInspectorTest `
  --console=plain

.\gradlew performanceGuardrailsCi --console=plain
```

The focused campaign regression batch covers the June 2026 audit-remediation fixes for HUD actions, trade, shipyard copy, render-time mutation, hostile ship names, task-force intel gating, and model-only expansion labeling.

The full regression suite also completes locally:

```powershell
.\gradlew test --fail-fast --console=plain
```

The June 23, 2026 remediation run completed successfully in 14 minutes 52 seconds. Keep the focused gate above for faster iteration and use the full suite before an alpha handoff.

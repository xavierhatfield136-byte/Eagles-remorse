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
- `gradlew saveLoadSoak`

The smoke scenario runs a late-campaign-shaped battle with hundreds of ships and sustained missile traffic. The extended task increases battle duration for memory-soak runs. Save/load soak uses an isolated temporary checkpoint file.

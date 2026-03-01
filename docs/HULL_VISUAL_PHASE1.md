# Hull Visual Upgrade - Phase 1

Date: 2026-02-23

## Goal

Make hulls look significantly higher quality in the current 2D renderer without waiting for full 3D production assets.

## What Was Implemented

Code touchpoint:
- `src/Renderer.java` (`ShipRenderer` + `ShipSkinLibrary`)

Implemented changes:
- Layered hull skin pipeline with optional maps:
  - `albedo`
  - `panel`
  - `ao`
  - `emissive`
  - `damage`
- Hull clipping for skin passes so textures stay inside procedural hull geometry.
- Faction-aware lighting overlays (key light + rim + deck/belly shading).
- Damage-responsive overlay blending (more visible as HP drops).
- Backward compatibility with existing single-layer skin files.

## Render Pass Order

For each non-station ship:
1. Procedural hull base render if no albedo skin is present.
2. Layered skin passes:
   - albedo
   - panel
   - ao
   - damage (alpha scales with current damage)
   - emissive
3. Faction lighting overlay pass.
4. Existing systems continue on top:
   - engines
   - hardpoints/turrets
   - shields
   - damage decals
   - stealth outline

## File Naming For Layered Skins

Layer naming:
- `<role>_albedo.png`
- `<role>_panel.png`
- `<role>_ao.png`
- `<role>_emissive.png`
- `<role>_damage.png`

Faction variants:
- `<role>_<faction>_<layer>.png`
- `assets/ship_skins/<faction>/<role>_<layer>.png`

Defaults:
- `default_<faction>_<layer>.png`
- `default_<layer>.png`

Legacy compatibility (albedo only):
- `<role>.png`
- `<role>_<faction>.png`
- `assets/ship_skins/<faction>/<role>.png`
- `default.png`
- `default_<faction>.png`

Faction key mapping:
- `PLAYER`/`ALLY` -> `ally`
- `ENEMY` -> `enemy`
- `TEAM_C` -> `team_c`
- `TEAM_D` -> `team_d`

## Current Art Guidance

- Keep the ship facing right (+X), centered in canvas.
- Use alpha backgrounds only.
- 1024x1024 source textures are preferred for capital ships; 512x512 is acceptable for smaller hulls.
- Keep emissive restrained; prioritize readability at gameplay zoom.
- Use AO and panel layers to create depth before adding high-contrast edge lines.

## Known Limitations

- Could not run compile validation in this environment because `javac` is not configured.
- Lighting is currently authored as gradient overlays (not true physically-based shading).
- Blend mode options are limited by Java2D composite operations.

## Next Steps

1. Produce layered assets for hero hulls first:
   - `battlecruiser`
   - `battleship`
   - `dreadnought`
2. Tune layer alpha values per role once real layered assets are in place.
3. Add subtle emissive pulse timing tied to ship state (combat/idle) if desired.
4. Continue M5 3D asset track in parallel for final model-based hull fidelity.

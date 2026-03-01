# Ship Skin Workflow

This project supports top-down PNG skins for ships.

For the current renderer upgrade details (layered hull passes, clipping, lighting, damage overlays), see:
- `docs/HULL_VISUAL_PHASE1.md`
- `docs/TURRET_SKINS.md` (turret/onboard-system sprite workflow)

## Folder And Naming

- Put skins in `assets/ship_skins/`
- Name files by `ShipRole` in lowercase:
  - `frigate.png`
  - `patrol.png`
  - `picket.png`
  - `light_cruiser.png`
  - `battlecruiser.png`
  - `battleship.png`
- Optional global fallback: `default.png`

If a role skin is missing, the renderer uses `default.png` (if present), otherwise it keeps procedural hull visuals only.

Faction-specific variants are supported:

- `<role>_ally.png`
- `<role>_enemy.png`
- `<role>_team_c.png`
- `<role>_team_d.png`

Fallback order is:

1. `assets/ship_skins/<faction>/<role>.png`
2. `assets/ship_skins/<role>_<faction>.png`
3. `assets/ship_skins/<role>.png`
4. `assets/ship_skins/default_<faction>.png`
5. `assets/ship_skins/default.png`

## Image Format

- Use `PNG` with alpha channel (transparent background)
- Recommended canvas: `512x512` (or `1024x1024` for larger roles)
- Ship should point to the right (+X), centered in the canvas
- Keep ~8-12% transparent padding around the hull silhouette

## Prompt Template (For AI Image Gen)

Use this prompt structure per role:

```text
Top-down 2D spaceship skin, transparent background, no shadow outside hull, no text.
Military hard-surface hull plating, panel lines, vents, windows, subtle battle wear.
Facing right, centered, high readability at small scale, strong silhouette.
Style: [insert faction/style], role: [insert role], color scheme: [insert palette].
Output: PNG with alpha, square canvas.
```

## Current Integration

- Loader path: `assets/ship_skins/*.png`
- Render hook: `src/Renderer.java` in `ShipRenderer.drawHullSkin(...)`
- Existing systems (turrets, shields, stealth, damage overlays) continue to render on top

## Player 1024 Layer Batch

For a full player/ally layered pass, run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/generate-player-ship-layers.ps1
```

This outputs `1024x1024` ally maps for every `ShipRole`:

- `<role>_ally_albedo.png`
- `<role>_ally_panel.png`
- `<role>_ally_ao.png`
- `<role>_ally_emissive.png`
- `<role>_ally_damage.png`

And writes a legacy compatibility albedo:

- `<role>_ally.png`

Roles that do not have native authored source art use deterministic template fallbacks in the script (for example `supership -> dreadnought`, `transport -> carrier`) so every player-role still has a complete layered set.

## Starter Asset

- `assets/ship_skins/frigate.png` is included as a first test skin.

# Turret Skin Workflow

This project now supports sprite-based turret and onboard-system skins with procedural fallback.

## Folder

- `assets/turret_skins/`

## Style Keys

- `twin_gun`
- `heavy_triple`
- `missile_pod`
- `beam_emitter`
- `stealth_flush`
- `ciws`

## File Naming

Most specific to least specific:

1. `assets/turret_skins/<faction>/<role>_<style>.png`
2. `assets/turret_skins/<role>_<faction>_<style>.png`
3. `assets/turret_skins/<role>_<style>.png`
4. `assets/turret_skins/<faction>/<style>.png`
5. `assets/turret_skins/<style>_<faction>.png`
6. `assets/turret_skins/<style>.png`
7. `assets/turret_skins/default_<faction>_<style>.png`
8. `assets/turret_skins/default_<style>.png`
9. `assets/turret_skins/default_<faction>.png`
10. `assets/turret_skins/default.png`

Faction keys:

- `ally`
- `enemy`
- `team_c`
- `team_d`

## Art Guidance

- PNG with alpha channel
- Turret/system faces right (+X)
- Centered in square canvas
- Recommended source size: `512x512`

## Current Integration

- Loader: `src/Renderer.java` (`TurretSkinLibrary`)
- Render hook: `drawTurrets(...)`
- Fallback: existing procedural turret rendering remains active if skin files are missing

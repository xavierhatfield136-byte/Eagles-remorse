# Ship Damage Patch Plan

## Goal

Use the new cropped damage images in `assets/ship_damage_patches/` to make 2D ship damage feel more localized and structural.

Instead of only fading in a generic full-hull damage layer, we will reveal curated internal machinery patches at actual breach points. The effect should read as:

- intact hull skin on top
- dark hole or torn edge at the impact point
- exposed internal machinery visible underneath
- optional room/interior exposure for the largest breaches

## New Assets

Source crops now live in:

- `assets/ship_damage_patches/`

Current patch groups:

- `emerald_breach_*`
- `azure_breach_*`
- `ember_breach_*`
- `amber_breach_*`

Support files:

- `assets/ship_damage_patches/manifest.txt`
- `assets/ship_damage_patches/patch_preview_sheet.png`

These are curated 256x256 crops chosen for:

- readable machinery at gameplay scale
- strong negative space and torn-open silhouettes
- different color families for faction or ship-style variation

## How We Will Use Them

The current damage system already gives us most of the placement data we need:

- `Ship.HullImpactMark` stores local hit position and breach radius
- `Renderer.drawDamageDecals(...)` already renders localized impact marks
- `Renderer.drawDestroyedHullBreaches(...)` already builds larger breach shapes

The plan is to add a new render pass that uses those same impact marks to sample from the patch library.

### Small Hits

Keep the current approach lightweight:

- scorch
- dent shadow
- tiny dark puncture

Do not show full machinery patches for every minor hit.

### Medium Breaches

For impact marks with a meaningful `breachRadius`:

- pick one damage patch from the appropriate patch group
- scale it to the breach size
- clip it to a circular or irregular hole mask
- draw it underneath the top hull skin
- keep the outer rim dark so the patch feels recessed rather than pasted on

This is the main new use case for the patch set.

### Major Breaches

For destroyed rooms or very large hull tears:

- keep using the existing breach/interior rendering path
- optionally blend a sampled damage patch behind the breach to add texture density
- let room-aware exposure remain the most important visual cue

## Selection Rules

We should not use fully random unrestricted crops at runtime.

Preferred approach:

- use the curated patch files rather than raw atlas coordinates
- assign patch groups by faction, hull family, or skin palette
- choose randomly within a small group for variation
- allow 90-degree rotations and horizontal flips only if the result still looks authored

This keeps the visuals varied without making the internals look chaotic or unreadable.

## First Implementation Pass

1. Add a small `ShipDamagePatchLibrary` that loads the PNGs in `assets/ship_damage_patches/`.
2. Map patch families to factions or hull styles with a simple fallback.
3. In the ship damage render path, for impact marks with non-trivial `breachRadius`, draw a clipped machinery patch before the final hole rim pass.
4. Keep the existing scorch and breach-outline rendering so the new art integrates with the current style.
5. Tune alpha, darkening, and scale so the patch reads as interior depth rather than surface decal noise.

## Visual Rules

- The outer hull silhouette must stay dominant.
- Interior patches should be slightly darker and less saturated than source art if needed.
- Small ships should show simpler, less noisy breaches than large ships.
- The effect should get stronger with damage severity, not appear at full strength immediately.

## Non-Goals For The First Pass

- full procedural destruction of hull skins
- per-pixel persistent damage masks across the whole ship
- generating unique internal layouts per ship role
- replacing the existing room-breach renderer

## Expected Result

After this pass, ships should feel more like they are being torn open at exact hit locations, with visible machinery behind the armor, while still fitting the current 2D renderer and room-damage systems.

## Execution Checklist

- [x] Add a small `ShipDamagePatchLibrary` for curated patch PNG loading.
- [x] Map patch families to faction palettes with a safe fallback.
- [x] Draw clipped machinery patches for non-trivial `HullImpactMark.breachRadius` values.
- [x] Keep existing scorch, dent, smoke, and destroyed-room breach rendering intact.
- [x] Retune the final hole overlay so medium breaches show recessed machinery instead of being fully blacked out.
- [x] Verify with `compileJava` and focused renderer/source hygiene tests.

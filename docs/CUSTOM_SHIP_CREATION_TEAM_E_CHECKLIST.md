# Custom Ship Creation And Team E Checklist

Date: 2026-08-12
Status: Architecture baseline and implementation checklist
Scope: Build a reusable custom-hull pipeline for player-uploaded PNG ships, expose it first through Team E in single-player custom missions, and keep it isolated from campaign systems until deliberately enabled later.

## Guiding Decision

The core rule for this feature is:

- [ ] Faction identity and hull definition identity are independent.

Team E is the first custom-mission place where player-created ships appear. Team E is not the custom ship system itself.

The runtime should eventually support:

```java
spawnCustomShip(customDefinition, Faction.TEAM_E);
spawnCustomShip(customDefinition, Faction.ENEMY);
spawnCustomShip(customDefinition, Faction.ALLY);
```

The custom definition decides the hull. The mission roster decides the faction and allegiance.

## Non-Negotiable Invariants

- [ ] Saved custom definitions are authoritative. Generation happens only when creating or regenerating a ship, not every time the game loads.
- [ ] UUID is identity. Display name is cosmetic, and duplicate display names are allowed.
- [ ] Custom content cannot alter built-in Blue, Red, Green, or Yellow ship definitions.
- [ ] Team E is custom-mission-only in V1.
- [ ] Team E must not participate in campaign simulation, territorial ownership, economy, invasions, reinforcements, diplomacy, faction AI, or strategic map ownership in V1.
- [ ] Mission allegiance is separate from faction appearance.
- [ ] Missing custom content is a recoverable error, never a startup or mission crash.
- [ ] Custom ship folders are sandboxed under the game's custom content root.
- [ ] The runtime must never depend on arbitrary original import paths such as a user's Downloads folder.
- [ ] Old definitions remain stable across generator updates.
- [ ] Generator changes must not mutate already-saved custom ships.
- [ ] Built-in ships must still spawn without loading `CustomShipRegistry`.
- [ ] Do not opportunistically refactor campaign faction logic while adding Team E.

## Desired Pipeline

```text
PNG upload
  |
  v
CustomShipImageProcessor
  |
  v
save/custom_ships/<uuid>/
  definition.json
  hull.png
  thumbnail.png
  |
  v
CustomShipRegistry
  |
  v
ShipDefinitionRef
  |
  v
Custom Mission Roster
  |
  v
Runtime Ship
  |
  +-- custom hull definition
  +-- mission-assigned faction/allegiance
```

## Storage Layout

Prefer one folder per custom ship:

```text
save/custom_ships/
  63ca9464-f778-4f48-a5e0-447533561641/
    definition.json
    hull.png
    thumbnail.png
```

- [ ] Store processed content under `save/custom_ships/<uuid>/`.
- [ ] Copy and normalize the imported image into `hull.png`.
- [ ] Generate `thumbnail.png` for UI previews.
- [ ] Store all generated stats, weapons, hardpoints, and room layout data in `definition.json`.
- [ ] Do not store the imported image's original filesystem path as a runtime dependency.
- [ ] Add future room for `preview.png`, `metadata.json`, import/export manifests, and content hashes.

## Data Model

- [ ] Add `CustomHullClass`.
  - [ ] `SMALL_CRAFT`
  - [ ] `ESCORT`
  - [ ] `FRIGATE`
  - [ ] `CRUISER`
  - [ ] `CAPITAL`
  - [ ] `TITAN`
- [ ] Add `CustomShipDefinition`.
  - [ ] `UUID id`
  - [ ] `String displayName`
  - [ ] `int schemaVersion`
  - [ ] `int generatorVersion`
  - [ ] `String hullImagePath`
  - [ ] `String thumbnailImagePath`
  - [ ] `CustomHullClass hullClass`
  - [ ] `ShipRole balanceTemplate`
  - [ ] `double radius`
  - [ ] `int hpMax`
  - [ ] `double shieldMax`
  - [ ] `double shieldRegen`
  - [ ] `double desiredSpeed`
  - [ ] `List<CustomWeaponMount> weapons`
  - [ ] `String roomLayoutPreset`
  - [ ] optional generated room polygons after the internals milestone
- [ ] Add `CustomWeaponMount`.
  - [ ] stable mount id
  - [ ] normalized x coordinate from `0.0` to `1.0`
  - [ ] normalized y coordinate from `0.0` to `1.0`
  - [ ] weapon kind
  - [ ] cooldown
  - [ ] damage
  - [ ] projectile speed
  - [ ] range or projectile life
  - [ ] firing arc or mount permission, if needed later
- [ ] Add `CustomShipRegistry`.
  - [ ] create definition
  - [ ] load all definitions
  - [ ] load one definition by UUID
  - [ ] save definition
  - [ ] delete definition folder
  - [ ] validate definition
  - [ ] report missing content
- [ ] Add `CustomShipImageProcessor`.
  - [ ] validate image
  - [ ] decode PNG
  - [ ] crop transparent margins
  - [ ] normalize canvas
  - [ ] enforce canonical facing direction
  - [ ] resize if necessary
  - [ ] calculate alpha silhouette
  - [ ] generate thumbnail
  - [ ] write processed assets into the per-ship folder

## Image Import Rules

- [ ] V1 accepts PNG only.
- [ ] Transparent pixels mean "not hull."
- [ ] Reject unreadable or corrupt images.
- [ ] Enforce max image dimensions.
- [ ] Enforce max file size.
- [ ] Preserve alpha.
- [ ] Show a preview with a clear `FRONT ->` facing indicator.
- [ ] Require the canonical game orientation: ship forward points right.
- [ ] Later: add rotate 90 degrees, rotate 180 degrees, flip horizontal, and flip vertical controls.
- [ ] Later: add JPG support only through an explicit background-removal/import wizard.

## Ship Definition Reference

Mission code should not accumulate repeated `if (customShipId != null)` branches.

- [ ] Add `ShipDefinitionRef`.
  - [ ] `BuiltinShipRef` for normal `ShipRole` hulls.
  - [ ] `CustomShipRef` for UUID-backed custom hulls.
- [ ] Update `MissionSlotSpec` to reference `ShipDefinitionRef`.
- [ ] Preserve compatibility with existing `ShipRole defaultHull` launch data until migration is complete.
- [ ] Ensure mission rosters say "spawn this referenced ship definition" instead of directly choosing only a `ShipRole`.

Longer-term direction:

- [ ] Introduce `ShipDefinition`.
  - [ ] `BuiltinShipDefinition`
  - [ ] `CustomShipDefinition`
- [ ] Do not refactor every built-in `ShipRole` into `ShipDefinition` during V1.

Transitional runtime constructor:

```java
FleetShip(ShipRole templateRole, Faction faction, CustomShipDefinition customDefinition)
```

- [ ] `customDefinition == null` preserves existing built-in behavior.
- [ ] `customDefinition != null` applies custom sprite, stats, hardpoints, and weapon definitions after the template setup.

## Team E Boundary

- [ ] Add Team E only where V1 needs it for custom missions.
- [ ] Keep campaign systems aware only of their existing campaign factions until a later deliberate campaign-custom-content milestone.
- [ ] Add a faction capability/rules model before adding broad Team E checks.
  - [ ] playable
  - [ ] selectable in custom battle
  - [ ] participates in campaign
  - [ ] owns territory
  - [ ] can trade
  - [ ] can use custom hulls
- [ ] Initial Team E capabilities:
  - [ ] playable
  - [ ] selectable in custom battle
  - [ ] custom hulls enabled
  - [ ] campaign participation disabled
  - [ ] territory ownership disabled
  - [ ] diplomacy disabled outside mission-defined relationships
- [ ] Do not sprinkle `if (faction == TEAM_E)` through campaign code.
- [ ] Avoid using color or faction identity as the source of hostility.
- [ ] Custom missions should define team relationships separately from faction appearance.

## Weapon Generation Rules

- [ ] Split weapon count from weapon doctrine.
- [ ] V1 doctrines:
  - [ ] Balanced
  - [ ] Gunship
  - [ ] Missile
  - [ ] Point Defense
  - [ ] Energy
- [ ] Each hull class defines weapon slot limits.
- [ ] Each hull class defines an offensive budget.
- [ ] Each weapon type has a budget cost.
- [ ] More weapons should consume budget rather than linearly dividing damage by weapon count.
- [ ] Enforce minimum weapon usefulness so many-gun ships do not become visually noisy pea shooters.
- [ ] Enforce maximum single-weapon power so one-gun ships do not become absurd.
- [ ] Save actual generated weapon stats into `definition.json`.
- [ ] AI effective range must derive from the generated weapons.

## Hardpoint Generation Rules

- [ ] Store hardpoints as normalized coordinates, not pixels.
- [ ] Initial V1 mount patterns can be deterministic presets:

```text
1 weapon:
    X

2 weapons:
  X   X

3 weapons:
  X X X

4 weapons:
  X   X
  X   X
```

- [ ] Project normalized mount pattern positions onto valid alpha areas of the hull.
- [ ] Keep turret centers inside the hull silhouette.
- [ ] Avoid placing mounts too close together.
- [ ] Prefer symmetry when the weapon count and hull silhouette allow it.
- [ ] Do not place turrets outside visible hull pixels.
- [ ] Resizing an imported image must not move hardpoints.
- [ ] Weapons cannot fire backward unless the mount explicitly permits it.
- [ ] Later: add manual hardpoint editing.

## Internals And Rooms

Fully procedural room generation is deferred.

- [ ] V1 custom ships can use size-class room layout presets.
- [ ] Add preset layouts before attempting full silhouette-derived internals.
- [ ] Project preset normalized room polygons onto the hull area.
- [ ] Discard or clamp room areas outside the silhouette.
- [ ] Required room concepts for larger custom hulls:
  - [ ] bridge
  - [ ] sensors
  - [ ] reactor or power
  - [ ] engines
  - [ ] main weapon or batteries
  - [ ] armor strips
  - [ ] shield strips when shielded
  - [ ] service or crew spaces
  - [ ] magazines for missile-heavy or kinetic-heavy ships
- [ ] Save actual room data after generation.
- [ ] Do not infer advanced architecture from arbitrary silhouettes in V1.

## Abilities And Superweapons

Procedural ability generation is out of scope for V1.

- [ ] V1 custom ships receive no novel generated activated ability.
- [ ] Optional V1 behavior: inherit a safe ability package from the selected size/profile.
- [ ] Titan custom ships may later choose authored packages:
  - [ ] Artillery
  - [ ] Missile Barrage
  - [ ] Beam
  - [ ] Carrier
- [ ] Do not generate new `SuperweaponPattern` behavior from player input.
- [ ] Reuse systems that AI, renderer, damage model, and UI already understand.
- [ ] Avoid stun or movement-hindering abilities.

## Milestone 1: Data Foundation

- [ ] Add `CustomHullClass`.
- [ ] Add `CustomShipDefinition`.
- [ ] Add `CustomWeaponMount`.
- [ ] Add `CustomShipRegistry`.
- [ ] Add schema version.
- [ ] Add generator version.
- [ ] Add UUID identity.
- [ ] Add per-ship folder management.
- [ ] Add JSON save/load.
- [ ] Add definition validation.
- [ ] Add malformed/missing content reporting.

Tests:

- [ ] Definition round-trips through save/load.
- [ ] Duplicate display names are allowed.
- [ ] UUID remains stable after save/load.
- [ ] Malformed JSON cannot crash startup.
- [ ] Missing custom ship folder is recreated where appropriate.
- [ ] Old schema can be rejected or migrated cleanly.
- [ ] Custom content cannot escape `save/custom_ships/`.
- [ ] Built-in ships still spawn without loading `CustomShipRegistry`.

## Milestone 2: Runtime Hull

- [ ] Add custom PNG load path.
- [ ] Add custom sprite render path.
- [ ] Apply custom radius.
- [ ] Apply custom hp, shields, shield regen, and speed.
- [ ] Apply generated weapon definitions.
- [ ] Apply normalized mounts.
- [ ] Allow AI movement for custom ships.
- [ ] Allow AI firing for custom ships.
- [ ] Add missing sprite fallback or recoverable missing-content state.

Tests:

- [ ] Custom ship spawns.
- [ ] Custom sprite loads.
- [ ] Missing sprite produces a recoverable fallback/error.
- [ ] Mount coordinates remain within `0.0..1.0`.
- [ ] AI targets custom ship.
- [ ] AI-controlled custom ship fires.
- [ ] Custom ship effective range is recognized by AI.
- [ ] Custom ship sprite orientation matches weapon forward direction.

## Milestone 3: Custom Mission Integration

- [ ] Add Team E where custom missions require it.
- [ ] Add mission relationship rules separate from faction identity.
- [ ] Add `ShipDefinitionRef`.
- [ ] Update `MissionSlotSpec`.
- [ ] Update custom mission roster model.
- [ ] Update `CustomBattleSpawnPlan`.
- [ ] Allow custom ships in single-player custom mission rosters.
- [ ] Allow Team E to be player, ally, hostile, or neutral based on mission setup.
- [ ] Keep campaign generation from seeing Team E or custom ships.

Tests:

- [ ] Team E custom ship can be player-controlled.
- [ ] Team E custom ship can be hostile.
- [ ] Same custom hull can spawn under different factions.
- [ ] Built-in roster still works.
- [ ] Campaign fleet generation never includes Team E.
- [ ] Custom ship does not appear in campaign fleet generation.
- [ ] Two Team E rosters can be hostile if mission relationships say so.
- [ ] Custom mission missing a referenced custom ship shows a recoverable error.

## Milestone 4: Builder V1

- [ ] Add Custom Ships menu.
- [ ] Add import PNG action.
- [ ] Add name field.
- [ ] Add hull class selector.
- [ ] Add weapon count selector.
- [ ] Add weapon doctrine selector.
- [ ] Add Generate action.
- [ ] Add preview.
- [ ] Add Save action.
- [ ] Add Delete action.
- [ ] Add regenerate warning when replacing generated data.

Tests:

- [ ] Importing a valid PNG creates a custom ship folder.
- [ ] Invalid image is rejected with a clear message.
- [ ] Saved ship appears in builder list.
- [ ] Deleting a ship removes only its sandboxed folder.
- [ ] Regenerating a ship changes generated data only after confirmation.
- [ ] Duplicate display names remain selectable without collision.

## Milestone 5: Better Hardpoints

- [ ] Add alpha silhouette sampling.
- [ ] Add valid-alpha correction.
- [ ] Add spacing rules.
- [ ] Add symmetry preference.
- [ ] Add side/forward/back distribution rules.
- [ ] Add mount preview overlay.
- [ ] Add manual hardpoint adjustment later.

Tests:

- [ ] Generated hardpoints remain on visible hull alpha.
- [ ] Rescaling imported image does not move normalized hardpoints.
- [ ] Hardpoints do not overlap closer than the configured minimum spacing.
- [ ] Odd weapon counts keep at least one sensible centerline mount when possible.
- [ ] Invalid silhouettes produce a clear generator error.

## Milestone 6: Internals

- [ ] Add preset room profiles by `CustomHullClass`.
- [ ] Project room profiles onto custom hull coordinate space.
- [ ] Connect generated rooms for crew and damage logic.
- [ ] Add x-ray panel support for custom room definitions.
- [ ] Add room-hit resolution for custom ships.
- [ ] Add missing room definition fallback.

Tests:

- [ ] Custom room profile loads.
- [ ] Room hit resolution works for a custom ship.
- [ ] X-ray view can render a custom ship layout.
- [ ] Missing or invalid room data is recoverable.
- [ ] Critical rooms are present for each hull class that requires them.

## Milestone 7: Advanced Customization

- [ ] Add manual hardpoint editing.
- [ ] Add safe ability package selection.
- [ ] Add Titan superweapon package selection.
- [ ] Add engine placement hints.
- [ ] Add custom accent/team color controls.
- [ ] Add optional custom turret skin support.
- [ ] Add import wizard transforms: rotate and flip.

Tests:

- [ ] Manual hardpoint edits persist.
- [ ] Ability package selection persists.
- [ ] Superweapon packages map only to existing supported behavior.
- [ ] Custom accents do not override faction allegiance.

## Milestone 8: Import, Export, And Packs

- [ ] Add custom ship export package.
- [ ] Add custom ship import package.
- [ ] Include UUID, definition, hull, thumbnail, schema version, and generator version.
- [ ] Add definition hash.
- [ ] Add image hash.
- [ ] Detect duplicate UUID imports.
- [ ] Detect same display name with different UUID.
- [ ] Add compatibility report for missing or unsupported custom content.
- [ ] Later: add mod folder or Workshop-style package discovery.

Tests:

- [ ] Exported ship imports on a clean profile.
- [ ] Duplicate UUID imports are detected.
- [ ] Corrupt package is rejected.
- [ ] Missing image hash mismatch is reported.
- [ ] Display-name duplicates do not overwrite existing ships.

## Missing Content Behavior

- [ ] If `definition.json` is missing, show a missing custom content entry instead of crashing.
- [ ] If `hull.png` is missing, show a missing sprite warning and prevent mission launch or use a clear placeholder.
- [ ] If a custom mission references a missing custom ship, show:

```text
Custom Content Missing

This mission references a custom ship that is not installed.
Custom Ship ID: <uuid>

[Locate File] [Remove From Mission] [Cancel]
```

- [ ] Do not silently replace missing custom ships with a built-in hull.
- [ ] Do not crash startup on malformed custom content.

## Explicit Out Of Scope For V1

- [ ] Campaign custom ships.
- [ ] Team E campaign territory.
- [ ] Team E autonomous campaign fleets.
- [ ] Team E economy.
- [ ] Team E diplomacy.
- [ ] Workshop/mod-pack synchronization.
- [ ] JPG background removal.
- [ ] Fully procedural room architecture from arbitrary silhouettes.
- [ ] Procedurally generated novel abilities.
- [ ] Procedurally generated novel superweapons.
- [ ] Refactoring all built-in ships into a new definition system.
- [ ] Generalizing every campaign faction array during Team E work.

## Implementation Guardrails

- [ ] Add extension points instead of rewriting built-in ship spawning.
- [ ] Keep normal Blue, Red, Green, and Yellow behavior unchanged unless a milestone explicitly calls for migration.
- [ ] Protect campaign systems from Team E in V1.
- [ ] Keep custom content under a sandboxed game-owned folder.
- [ ] Use UUIDs for identity.
- [ ] Use normalized coordinates for hardpoints and future room data.
- [ ] Save generated outputs permanently.
- [ ] Treat missing content as a recoverable UI state.
- [ ] Add tests in every milestone, not only after the feature is complete.

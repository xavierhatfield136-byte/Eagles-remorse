# Custom Ship Creation And Team E Checklist

Date: 2026-08-12
Status: Architecture baseline and implementation checklist
Scope: Build a reusable custom-hull pipeline for player-uploaded PNG ships, expose it first through Team E in single-player custom missions, and keep it isolated from campaign systems until deliberately enabled later.

## Guiding Decision

The core rule for this feature is:

- [x] Faction identity and hull definition identity are independent.

Team E is the first custom-mission place where player-created ships appear. Team E is not the custom ship system itself.

The runtime should eventually support:

```java
spawnCustomShip(customDefinition, Faction.TEAM_E);
spawnCustomShip(customDefinition, Faction.ENEMY);
spawnCustomShip(customDefinition, Faction.ALLY);
```

The custom definition decides the hull. The mission roster decides the faction and allegiance.

## Non-Negotiable Invariants

- [x] Saved custom definitions are authoritative. Generation happens only when creating or regenerating a ship, not every time the game loads.
- [x] UUID is identity. Display name is cosmetic, and duplicate display names are allowed.
- [x] Custom content cannot alter built-in Blue, Red, Green, or Yellow ship definitions.
- [x] Team E is custom-mission-only in V1.
- [x] Team E must not participate in campaign simulation, territorial ownership, economy, invasions, reinforcements, diplomacy, faction AI, or strategic map ownership in V1.
- [x] Mission allegiance is separate from faction appearance.
- [x] Missing custom content is a recoverable error, never a startup or mission crash.
- [x] Custom ship folders are sandboxed under the game's custom content root.
- [x] The runtime must never depend on arbitrary original import paths such as a user's Downloads folder.
- [x] Old definitions remain stable across generator updates.
- [x] Generator changes must not mutate already-saved custom ships.
- [x] Built-in ships must still spawn without loading `CustomShipRegistry`.
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

- [x] Store processed content under `save/custom_ships/<uuid>/`.
- [x] Keep generated and saved custom ships local to the user's computer.
- [x] Do not place generated player ships under `assets/`, `config/`, or any other Git-tracked content folder.
- [x] Keep `save/custom_ships/` ignored by Git so GitHub is not crowded with player-generated ships.
- [x] Store only source code, tests, documentation, and deliberately authored fixtures in the repository.
- [x] If tests need custom ship content, use tiny deterministic fixtures under a test-only folder, not player-created ships.
- [x] Copy and normalize the imported image into `hull.png`.
- [x] Generate `thumbnail.png` for UI previews.
- [x] Store all generated stats, weapons, hardpoints, and room layout data in `definition.json`.
- [x] Do not store the imported image's original filesystem path as a runtime dependency.
- [ ] Add future room for `preview.png`, `metadata.json`, import/export manifests, and content hashes.

## Data Model

- [x] Add `CustomCombatClassification`.
  - [x] `PICKET`
  - [x] `LINE`
  - [x] `CAPITAL`
  - [x] `TITAN`
- [x] Add `CustomHullClass`.
  - [x] `SMALL_CRAFT`
  - [x] `ESCORT`
  - [x] `FRIGATE`
  - [x] `CRUISER`
  - [x] `CAPITAL`
  - [x] `TITAN`
- [x] Add `CustomWeaponDoctrine`.
  - [x] `BALANCED`
  - [x] `GUNSHIP`
  - [x] `MISSILE`
  - [x] `POINT_DEFENSE`
  - [x] `ENERGY`
- [x] Add `CustomDefenseBias`.
  - [x] `ARMOR_HEAVY`
  - [x] `BALANCED`
  - [x] `SHIELD_HEAVY`
- [x] Add `CustomShipDefinition`.
  - [x] `UUID id`
  - [x] `String displayName`
  - [x] `String declaredShipClass`
  - [x] `int schemaVersion`
  - [x] `int generatorVersion`
  - [x] `String hullImagePath`
  - [x] `String thumbnailImagePath`
  - [x] `CustomHullClass hullClass`
  - [x] `CustomCombatClassification combatClassification`
  - [x] `CustomWeaponDoctrine weaponDoctrine`
  - [x] `CustomDefenseBias defenseBias`
  - [x] `ShipRole balanceTemplate`
  - [x] `double radius`
  - [x] `int hpMax`
  - [x] `double shieldMax`
  - [x] `double shieldRegen`
  - [x] `double desiredSpeed`
  - [x] `List<CustomWeaponMount> weapons`
  - [x] `String roomLayoutPreset`
  - [ ] optional generated room polygons after the internals milestone
- [x] Add `CustomWeaponMount`.
  - [x] stable mount id
  - [x] normalized x coordinate from `0.0` to `1.0`
  - [x] normalized y coordinate from `0.0` to `1.0`
  - [x] weapon kind
  - [x] cooldown
  - [x] damage
  - [x] projectile speed
  - [x] range or projectile life
  - [ ] firing arc or mount permission, if needed later
- [x] Add `CustomShipRegistry`.
  - [x] create definition
  - [x] load all definitions
  - [x] load one definition by UUID
  - [x] save definition
  - [x] delete definition folder
  - [x] validate definition
  - [x] report missing content
- [x] Add `CustomShipImageProcessor`.
  - [x] validate image
  - [x] decode PNG
  - [x] crop transparent margins
  - [x] normalize canvas
- [ ] enforce canonical facing direction
  - [ ] resize if necessary
  - [ ] calculate alpha silhouette
  - [x] generate thumbnail
  - [x] write processed assets into the per-ship folder

## Image Import Rules

- [x] V1 accepts PNG only.
- [x] Transparent pixels mean "not hull."
- [x] Reject unreadable or corrupt images.
- [x] Enforce max image dimensions.
- [x] Enforce max file size.
- [x] Preserve alpha.
- [ ] Show a preview with a clear `FRONT ->` facing indicator.
- [ ] Require the canonical game orientation: ship forward points right.
- [ ] Later: add rotate 90 degrees, rotate 180 degrees, flip horizontal, and flip vertical controls.
- [ ] Later: add JPG support only through an explicit background-removal/import wizard.

## Ship Definition Reference

Mission code should not accumulate repeated `if (customShipId != null)` branches.

- [x] Add `ShipDefinitionRef`.
  - [x] `BuiltinShipRef` for normal `ShipRole` hulls.
  - [x] `CustomShipRef` for UUID-backed custom hulls.
- [x] Update `MissionSlotSpec` to reference `ShipDefinitionRef`.
- [x] Preserve compatibility with existing `ShipRole defaultHull` launch data until migration is complete.
- [x] Ensure mission rosters say "spawn this referenced ship definition" instead of directly choosing only a `ShipRole`.

Longer-term direction:

- [ ] Introduce `ShipDefinition`.
  - [ ] `BuiltinShipDefinition`
  - [ ] `CustomShipDefinition`
- [ ] Do not refactor every built-in `ShipRole` into `ShipDefinition` during V1.

Transitional runtime constructor:

```java
FleetShip(ShipRole templateRole, Faction faction, CustomShipDefinition customDefinition)
```

- [x] `customDefinition == null` preserves existing built-in behavior.
- [x] `customDefinition != null` applies custom sprite, stats, hardpoints, and weapon definitions after the template setup.

## Team E Boundary

- [x] Add Team E only where V1 needs it for custom missions.
- [x] Keep campaign systems aware only of their existing campaign factions until a later deliberate campaign-custom-content milestone.
- [x] Add a faction capability/rules model before adding broad Team E checks.
  - [x] playable
  - [x] selectable in custom battle
  - [x] participates in campaign
  - [x] owns territory
  - [x] can trade
  - [x] can use custom hulls
- [x] Initial Team E capabilities:
  - [x] playable
  - [x] selectable in custom battle
  - [x] custom hulls enabled
  - [x] campaign participation disabled
  - [x] territory ownership disabled
  - [x] diplomacy disabled outside mission-defined relationships
- [ ] Do not sprinkle `if (faction == TEAM_E)` through campaign code.
- [ ] Avoid using color or faction identity as the source of hostility.
- [ ] Custom missions should define team relationships separately from faction appearance.

## Weapon Generation Rules

- [x] Split weapon count from weapon doctrine.
- [x] Let the player choose the broad weapon type or doctrine before generation.
- [ ] V1 doctrines:
  - [x] Balanced
  - [x] Gunship
  - [x] Missile
  - [x] Point Defense
  - [x] Energy
- [x] Use the selected weapon doctrine to fill weapon slots predictably.
  - [x] Balanced: mixed guns, missiles, and limited point defense.
  - [x] Gunship: mostly direct-fire guns or beam weapons.
  - [x] Missile: mostly missile launchers with at least one direct-fire fallback where class budget allows.
  - [x] Point Defense: more CIWS/intercept mounts with lower anti-ship pressure.
  - [x] Energy: energy bolts, beam bolts, or direct-energy packages using existing weapon behavior.
- [x] Each hull class defines weapon slot limits.
- [x] Each hull class defines an offensive budget.
- [x] Each combat classification applies a role multiplier:
  - [x] Picket: scouting, screening, point defense, lighter weapons.
  - [x] Line: main fleet combatant, balanced durability and firepower.
  - [x] Capital: slower heavy combatant with larger budgets.
  - [x] Titan: largest hulls, special packages only when explicitly enabled.
- [x] Each weapon type has a budget cost.
- [x] More weapons should consume budget rather than linearly dividing damage by weapon count.
- [x] Enforce minimum weapon usefulness so many-gun ships do not become visually noisy pea shooters.
- [x] Enforce maximum single-weapon power so one-gun ships do not become absurd.
- [x] Save actual generated weapon stats into `definition.json`.
- [x] AI effective range must derive from the generated weapons.

## Hardpoint Generation Rules

- [x] Store hardpoints as normalized coordinates, not pixels.
- [x] Initial V1 mount patterns can be deterministic presets:

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
- [x] Avoid placing mounts too close together.
- [x] Prefer symmetry when the weapon count and hull silhouette allow it.
- [ ] Do not place turrets outside visible hull pixels.
- [x] Resizing an imported image must not move hardpoints.
- [x] Weapons cannot fire backward unless the mount explicitly permits it.
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

- [x] V1 custom ships receive no novel generated activated ability.
- [ ] Optional V1 behavior: inherit a safe ability package from the selected size/profile.
- [ ] Titan custom ships may later choose authored packages:
  - [ ] Artillery
  - [ ] Missile Barrage
  - [ ] Beam
  - [ ] Carrier
- [x] Do not generate new `SuperweaponPattern` behavior from player input.
- [x] Reuse systems that AI, renderer, damage model, and UI already understand.
- [x] Avoid stun or movement-hindering abilities.

## Milestone 1: Data Foundation

- [x] Add `CustomHullClass`.
- [x] Add `CustomShipDefinition`.
- [x] Add `CustomWeaponMount`.
- [x] Add `CustomShipRegistry`.
- [x] Add schema version.
- [x] Add generator version.
- [x] Add UUID identity.
- [x] Add per-ship folder management.
- [x] Add JSON save/load.
- [x] Add definition validation.
- [x] Add malformed/missing content reporting.

Tests:

- [x] Definition round-trips through save/load.
- [x] Duplicate display names are allowed.
- [x] UUID remains stable after save/load.
- [x] Malformed JSON cannot crash startup.
- [x] Missing custom ship folder is recreated where appropriate.
- [x] Old schema can be rejected or migrated cleanly.
- [x] Custom content cannot escape `save/custom_ships/`.
- [x] Built-in ships still spawn without loading `CustomShipRegistry`.

## Milestone 2: Runtime Hull

- [x] Add custom PNG load path.
- [x] Add custom sprite render path.
- [x] Apply custom radius.
- [x] Apply custom hp, shields, shield regen, and speed.
- [x] Apply generated weapon definitions.
- [x] Apply normalized mounts.
- [x] Allow AI movement for custom ships.
- [x] Allow AI firing for custom ships.
- [x] Add missing sprite fallback or recoverable missing-content state.

Tests:

- [x] Custom ship spawns.
- [x] Custom sprite loads.
- [x] Missing sprite produces a recoverable fallback/error.
- [x] Mount coordinates remain within `0.0..1.0`.
- [ ] AI targets custom ship.
- [ ] AI-controlled custom ship fires.
- [ ] Custom ship effective range is recognized by AI.
- [ ] Custom ship sprite orientation matches weapon forward direction.

## Milestone 3: Custom Mission Integration

- [x] Add Team E where custom missions require it.
- [ ] Add mission relationship rules separate from faction identity.
- [x] Add `ShipDefinitionRef`.
- [x] Update `MissionSlotSpec`.
- [x] Update custom mission roster model.
- [x] Update `CustomBattleSpawnPlan`.
- [x] Allow custom ships in single-player custom mission rosters.
- [x] Expose Team E in the Custom Battle player-team selector.
- [ ] Allow Team E to be player, ally, hostile, or neutral based on mission setup.
- [x] Keep campaign generation from seeing Team E or custom ships.

Tests:

- [x] Team E custom ship can be player-controlled.
- [x] Team E custom ship can be hostile.
- [x] Same custom hull can spawn under different factions.
- [x] Custom Battle player-team selector includes Team E.
- [x] Built-in roster still works.
- [x] Campaign fleet generation never includes Team E.
- [x] Custom ship does not appear in campaign fleet generation.
- [ ] Two Team E rosters can be hostile if mission relationships say so.
- [ ] Custom mission missing a referenced custom ship shows a recoverable error.

## Milestone 4: Builder V1

- [x] Add Team E Shipyard main-menu entry.
- [x] Add import PNG action.
- [x] Add name field.
- [x] Add uploaded ship class selector or field.
  - [ ] Fighter
  - [ ] Corvette
  - [ ] Frigate
  - [ ] Destroyer
  - [ ] Cruiser
  - [ ] Battleship
  - [ ] Carrier
  - [ ] Station
  - [x] Custom label
- [x] Add hull class selector.
- [x] Add combat classification selector.
  - [x] Picket
  - [x] Line
  - [x] Capital
  - [x] Titan
- [x] Add weapon count selector.
- [x] Add weapon doctrine selector.
- [ ] Add weapon type selector if this is separate from doctrine in the first UI.
- [x] Add armor-vs-shield balance selector.
  - [x] Armor-heavy
  - [x] Balanced
  - [x] Shield-heavy
- [x] Add Generate action.
- [x] Add preview.
- [x] Add Save action.
- [x] Add Delete action.
- [ ] Add regenerate warning when replacing generated data.

Tests:

- [x] Importing a valid PNG creates a custom ship folder.
- [x] Invalid image is rejected with a clear message.
- [x] Saved ship appears in builder list.
- [x] Saved ship remains under the local custom ship folder and is not added to Git-tracked assets.
- [x] Deleting a ship removes only its sandboxed folder.
- [ ] Regenerating a ship changes generated data only after confirmation.
- [x] Duplicate display names remain selectable without collision.
- [x] Armor-heavy, balanced, and shield-heavy selections produce different saved stat distributions with the same total defensive budget.

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

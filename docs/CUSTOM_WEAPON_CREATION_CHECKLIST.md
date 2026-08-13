# Custom Weapon Creation Checklist

Date: 2026-08-13
Status: Design checklist
Scope: Let players create local custom weapon systems for custom ships, starting with Team E and single-player custom missions, without allowing custom content to mutate built-in factions or execute arbitrary code.

## Guiding Decision

Custom weapons should be data-driven definitions, not new Java classes created by users.

The game should eventually support:

```java
spawnCustomShip(customShipDefinition, Faction.TEAM_E)
    .withWeapon(customWeaponDefinition);
```

The custom weapon definition decides visuals, sound, stat profile, and constrained behavior. The custom ship decides where that weapon is mounted. The mission decides allegiance.

Built-in weapons and custom weapons should both resolve into the same runtime contract:

```text
Built-in weapon definition
  |
  v
WeaponRuntimeProfile
  |
  v
Turret / AI / Renderer / Projectile / Audio

Custom weapon definition
  |
  v
WeaponRuntimeProfile
  |
  v
Turret / AI / Renderer / Projectile / Audio
```

Combat code should know as little as possible about whether a weapon came from vanilla content or the Weapon Lab.

## Non-Negotiable Invariants

- [ ] Saved custom weapons are authoritative. Generation happens only when creating or regenerating a weapon.
- [ ] Saved combat stats remain authoritative after game updates.
- [ ] Generator and balance changes must not silently mutate already-saved weapons.
- [ ] UUID is weapon identity. Display name is cosmetic, and duplicate display names are allowed.
- [x] Custom weapons stay local to the user's computer by default.
- [x] Custom weapon folders must be ignored by Git.
- [ ] Custom weapons cannot alter built-in Blue, Red, Green, Yellow, or Team E generated defaults unless explicitly assigned to a custom ship.
- [ ] Custom content must not execute code or load scripts.
- [x] Missing custom weapon content is recoverable and must not crash startup or mission launch.
- [x] Built-in weapons must still work without loading `CustomWeaponRegistry`.
- [x] Damage, rate of fire, projectile speed, projectile count, homing, splash, and range must be constrained by a balance budget.
- [x] Invalid stat and behavior combinations cannot enter `CustomWeaponDefinition`.
- [ ] The builder should reject invalid designs at launch/save time rather than silently rewriting player-entered values.
- [ ] `Auto Balance` may normalize values only when the player explicitly asks for it.
- [ ] No stun, EMP lockout, movement disable, forced drift, or movement-hindering behavior.
- [ ] Friendly-fire behavior must follow mission/game rules, not weapon content.
- [ ] Multiplayer support must wait until deterministic syncing and content validation are solved.

## Desired Pipeline

```text
Turret PNG upload
Projectile PNG upload
Optional muzzle flash / impact PNG / sound upload
  |
  v
CustomWeaponAssetProcessor
  |
  v
save/custom_weapons/<uuid>/
  definition.json
  turret.png
  projectile.png
  thumbnail.png
  optional muzzle.png
  optional impact.png
  optional fire.wav
  optional impact.wav
  |
  v
CustomWeaponRegistry
  |
  v
WeaponDefinitionRef
  |
  v
CustomShipDefinition weapon slots
  |
  v
Runtime Turret / Projectile
```

## Storage Layout

Prefer one folder per custom weapon:

```text
save/custom_weapons/
  b432a2f7-8e82-47c8-aec6-49f0dc207041/
    definition.json
    turret.png
    projectile.png
    thumbnail.png
    fire.wav
    impact.wav
```

- [x] Add `save/custom_weapons/` and `custom_weapons/` to `.gitignore`.
- [x] Store processed content under `save/custom_weapons/<uuid>/`.
- [x] Copy and normalize imported turret sprite into `turret.png`.
- [x] Copy and normalize imported projectile sprite into `projectile.png`.
- [x] Generate `thumbnail.png` for UI previews.
- [ ] Optional: copy and normalize `muzzle.png`, `impact.png`, `fire.wav`, and `impact.wav`.
- [x] Store all generated stats, behavior choices, balance budget, and asset names in `definition.json`.
- [x] Do not store the original import path as a runtime dependency.
- [ ] Add future room for import/export manifests, content hashes, and preview GIFs.
- [ ] Store asset names like `turret.png`, not arbitrary user filesystem paths.

## Data Model

- [x] Add `CustomWeaponDefinition`.
  - [x] `UUID id`
  - [x] `String displayName`
  - [x] `int schemaVersion`
  - [x] `int generatorVersion`
  - [x] `int balanceModelVersion`
  - [x] `String turretAsset`
  - [x] `String projectileAsset`
  - [x] `String thumbnailAsset`
  - [ ] `String muzzleAsset`
  - [ ] `String impactAsset`
  - [ ] `String fireSoundAsset`
  - [ ] `String impactSoundAsset`
  - [x] `CustomWeaponFamily family`
  - [x] `CustomWeaponRuntimeBehavior behavior`
  - [x] `CustomDamageProfile damageProfile`
  - [x] `CustomTargetProfile targetProfile`
  - [x] `double cooldownSeconds`
  - [x] `int damage`
  - [x] `double projectileSpeedUnitsPerSecond`
  - [x] `double rangeUnits`
  - [x] `double projectileLifetimeSeconds`
  - [x] `int projectileCount`
  - [x] `double spreadDegrees`
  - [x] `double turnRateDegreesPerSecond`
  - [x] `double splashRadiusUnits`
  - [x] `double shieldDamageMultiplier`
  - [x] `double armorDamageMultiplier`
  - [x] `double hullDamageMultiplier`
  - [ ] `double pointDefenseEffectiveness`
  - [ ] `double heatOrPowerCost`
  - [x] `double balanceBudgetCost`
- [x] Add `CustomWeaponFamily` as a UI/category concept.
  - [x] `KINETIC_CANNON`
  - [x] `ENERGY_BOLT`
  - [ ] `BEAM_EMITTER`
  - [x] `MISSILE`
  - [x] `TORPEDO`
  - [x] `POINT_DEFENSE`
  - [ ] `FLAK`
  - [x] `RAILGUN`
  - [x] `PLASMA`
  - [ ] `IONIZED_SHIELD_PRESSURE`
- [x] Add `CustomWeaponRuntimeBehavior` as the authoritative runtime architecture.
  - [x] `DIRECT_PROJECTILE`
  - [x] `HOMING_PROJECTILE`
  - [x] `BEAM`
  - [x] `BURST_PROJECTILE`
  - [x] `CONE_PROJECTILE`
  - [x] `PROXIMITY_PROJECTILE`
  - [x] `INTERCEPTOR`
  - [x] `ARCING_PROJECTILE`
- [x] Add `CustomDamageProfile`.
  - [x] `BALANCED`
  - [x] `SHIELD_PRESSURE`
  - [x] `ARMOR_PIERCING`
  - [x] `HULL_BREAKER`
  - [x] `AREA_SUPPRESSION`
  - [x] `ANTI_FIGHTER`
  - [x] `ANTI_CAPITAL`
- [x] Add `CustomTargetProfile`.
  - [x] `GENERAL_PURPOSE`
  - [x] `SMALL_CRAFT`
  - [x] `MISSILES`
  - [x] `FRIGATES_AND_CRUISERS`
  - [x] `CAPITAL_AND_TITAN`
  - [x] `STRUCTURES`
- [x] Add `CustomWeaponRegistry`.
  - [x] create definition
  - [x] load all definitions
  - [x] load one definition by UUID
  - [x] save definition
  - [x] delete definition folder
  - [x] validate definition
  - [x] report missing content
- [x] Add `CustomWeaponAssetProcessor`.
  - [x] validate images
  - [x] decode PNGs
  - [x] crop transparent margins
  - [x] preserve alpha
  - [x] enforce max dimensions
  - [x] enforce max file size
  - [x] generate thumbnails
  - [ ] normalize audio format if audio imports are allowed in V1

## Player-Facing Builder Fields

- [ ] Weapon name.
- [ ] Weapon family.
- [ ] Behavior preset.
- [ ] Target preference.
- [ ] Damage profile.
- [ ] Turret sprite PNG.
- [ ] Projectile sprite PNG.
- [ ] Optional muzzle flash sprite PNG.
- [ ] Optional impact sprite PNG.
- [ ] Optional firing sound.
- [ ] Optional impact sound.
- [ ] Rate of fire.
- [ ] Projectile speed.
- [ ] Range.
- [ ] Damage.
- [ ] Projectile count per shot.
- [ ] Spread angle.
- [ ] Homing strength or turn rate.
- [ ] Splash radius.
- [ ] Shield-vs-armor-vs-hull balance.
- [ ] Point-defense capability toggle.
- [ ] Preview panel showing turret, projectile, and a simple firing preview.
- [ ] Balance warning panel that explains if the design exceeds budget.

## Behavior Presets

Presets should be authored by the game and filled by user choices. Players should not type formulas or scripts.

- [ ] **Laser Cannon:** direct-fire energy bolt, fast projectile, low splash, steady rate of fire.
- [ ] **Turbolaser:** slower heavy energy bolt, high damage, lower fire rate, anti-capital lean.
- [ ] **Beam Emitter:** sustained visual beam, damage over a short active window, strong shield pressure.
- [ ] **Railgun:** very fast direct projectile, high armor piercing, low spread, long cooldown.
- [ ] **Autocannon:** rapid kinetic fire, low per-shot damage, anti-fighter or anti-frigate profile.
- [ ] **Flak Battery:** proximity burst or shotgun cone, low single-target damage, strong small-craft pressure.
- [ ] **Point Defense:** fast tracking, short range, targets missiles and small craft.
- [ ] **Missile Rack:** homing missile, medium speed, reload-limited, target profile selectable.
- [ ] **Heavy Torpedo:** slow homing projectile, high damage, weak against evasive targets.
- [ ] **Plasma Caster:** medium speed, modest splash, good hull damage, slower cadence.
- [ ] **Ionized Shield Pressure:** damages shields or increases shield bleed only; no stun, no movement disable, no weapon lockout.

## Compatibility Matrix

Family and preset are player-facing organization. Runtime behavior decides what the weapon can actually do. The builder must validate combinations before saving.

| Runtime behavior | Allowed examples | Disallowed examples |
| --- | --- | --- |
| `DIRECT_PROJECTILE` | damage, cooldown, range, projectile speed, armor/shield/hull multipliers | homing turn rate, beam duration, missile-only target locks |
| `HOMING_PROJECTILE` | turn rate, target profile, missile/torpedo visuals, lifetime | instant-hit beam behavior, zero turn radius, structure-only intercept logic |
| `INTERCEPTOR` | missile targeting, small-craft targeting, short range, high turn rate | anti-structure profile, huge splash, capital-only target profile |
| `BEAM` | beam duration, tick damage, shield pressure, anti-capital profile | projectile count, projectile turn rate, flak proximity burst |
| `BURST_PROJECTILE` | burst count, burst interval, spread, recoil/cooldown cost | sustained beam duration, missile guidance |
| `CONE_PROJECTILE` | pellet count, spread angle, short lifetime, flak/autocannon presets | long-range railgun velocity with many pellets |
| `PROXIMITY_PROJECTILE` | proximity radius, splash radius, anti-fighter/flak profile | sustained beam, high anti-structure damage |
| `ARCING_PROJECTILE` | lob speed, lifetime, splash, slow heavy plasma/torpedo-like roles | hitscan speed, interceptor targeting |

- [ ] Add a validator that rejects incompatible family, behavior, damage profile, and target profile combinations.
- [ ] Keep the compatibility table in code as data, not scattered conditionals.
- [ ] Show validation failures in the Weapon Lab before save or launch.
- [ ] Do not ask renderer, AI, audio, or damage simulation to interpret impossible combinations.

## Failure Levels

- [ ] Missing custom weapon definition: block ship launch and show `MISSING WEAPON <short UUID>` in the editor.
- [x] Missing required visual asset: keep the weapon mechanically valid but render a deliberately obvious placeholder sprite.
- [ ] Missing optional VFX or audio: use no effect or silence.
- [ ] Malformed definition JSON: hide the weapon from selectable lists and show a recoverable editor warning.
- [ ] Over-budget weapon: keep it editable, but mark launch invalid until fixed or explicitly auto-balanced.
- [ ] Do not automatically replace a missing custom weapon definition with a vanilla cannon, because that silently changes saved ship capability.

## Balance System

Custom weapons need a budget model, otherwise players will create one-frame death beams by accident or on purpose.

- [ ] Assign each custom ship a weapon budget from hull class and combat classification.
- [ ] Assign each custom weapon a budget cost.
- [ ] Let weapon slots consume ship budget when selected.
- [ ] Compute cost from:
  - [ ] DPS
  - [ ] burst damage
  - [ ] projectile speed
  - [ ] range
  - [ ] homing turn rate
  - [ ] splash radius
  - [ ] projectile count
  - [ ] point-defense utility
  - [ ] shield/armor/hull multipliers
  - [ ] special behavior preset
- [ ] Clamp absolute projectile speed. Use the existing missile policy as a starting point: missiles should not exceed 1000 m/s, heavy missiles should sit around 700 m/s unless a later balance pass changes it.
- [ ] Clamp fire rate based on projectile count and damage.
- [ ] Clamp range so custom weapons do not bypass prosecution-range rules.
- [ ] Derive AI effective range from saved weapon data.
- [ ] Prevent zero-cooldown, infinite-life, infinite-range, negative damage, and NaN values.
- [ ] Add a preview DPS readout.
- [ ] Add a budget readout:

```text
Weapon Budget
Allowed:       30
Current:       37
Over budget:   +7

LAUNCH INVALID
```

- [ ] Add an explicit `Auto Balance` button for players who want the builder to normalize values.
- [ ] Do not silently convert a player-entered extreme value into a different saved value.
- [ ] Keep saved combat stats stable when balance formulas change.
- [ ] Recalculate current balance budget when editing, but do not secretly rewrite old saved stats during load.
- [ ] Add per-class recommended ranges:
  - [ ] Picket: short to medium range, high tracking.
  - [ ] Line: balanced range and DPS.
  - [ ] Capital: longer range, slower traverse, higher burst.
  - [ ] Titan: heavy weapons with strict reload and budget gates.

## Visual Rules

- [ ] Turret PNG must use transparency for non-turret pixels.
- [ ] Projectile PNG must use transparency for non-projectile pixels.
- [ ] Reject opaque square backgrounds unless the player explicitly accepts them as the visible sprite.
- [ ] Store visual scale separately from source image dimensions.
- [ ] Let players set turret visual scale.
- [ ] Let players set projectile visual scale.
- [ ] Let players set muzzle offset from turret center.
- [ ] Let players set projectile rotation mode:
  - [ ] points along velocity
  - [ ] fixed orientation
  - [ ] spin
- [ ] Generate a UI thumbnail.
- [ ] Add preview animation in the builder.
- [ ] Keep custom weapon sprites from cluttering ship hulls by enforcing reasonable default scales.
- [ ] Require turret center/origin to be inside the uploaded turret sprite's visible pixels or let the player adjust origin.

## Audio Rules

- [ ] V1 may use built-in firing sounds if custom audio is deferred.
- [ ] If custom audio imports are included:
  - [ ] Accept WAV first.
  - [ ] Enforce max length.
  - [ ] Enforce max file size.
  - [ ] Normalize volume.
  - [ ] Generate a stable event id.
  - [ ] Use one selected custom sound, not fallback rotation.
  - [ ] Add cooldown rules so rapid weapons do not spam audio.
- [ ] Add preview playback in the builder.
- [ ] Missing audio should fall back to silence or a clearly selected built-in default, not random legacy fallback sounds.

## Runtime Integration

- [x] Add `WeaponDefinitionRef`.
  - [ ] `BuiltinWeaponRef`
  - [x] `CustomWeaponRef`
- [x] Extend `CustomWeaponMount` so a mount can reference a custom weapon definition UUID.
- [x] Extend `CustomShipDefinition` to include custom weapon refs per mount.
- [x] Add a runtime adapter that maps custom weapon definitions into existing `Turret` and `Projectile` behavior.
- [x] Avoid adding broad custom branches throughout `Turret.fire`.
- [x] Prefer a small `WeaponRuntimeProfile` object used by turret firing, AI range checks, renderer, and audio.
- [x] Make `WeaponRuntimeProfile` the only object combat systems need for weapon stats and runtime behavior.
- [ ] Resolve built-in weapon archetypes into `WeaponRuntimeProfile`.
- [x] Resolve custom weapon definitions into `WeaponRuntimeProfile`.
- [x] Add custom projectile render path.
- [x] Add custom turret sprite render path.
- [ ] Add custom muzzle and impact VFX path.
- [ ] Add custom firing and impact audio path.
- [ ] Add missing-content fallbacks.
- [x] Ensure custom weapons can be used by AI ships.
- [x] Ensure player manual fire can use custom weapons.
- [ ] Ensure point-defense custom weapons can target missiles if allowed.
- [ ] Ensure custom missile weapons use existing missile cap and guidance safety rules.

## Builder Integration

- [x] Add a `Custom Weapons` or `Weapon Lab` entry from the Team E Shipyard.
- [x] Let players create weapons before creating a ship.
- [ ] Let players create a weapon while editing a ship.
- [x] Let each ship weapon slot choose:
  - [x] generated default
  - [ ] built-in weapon archetype
  - [x] saved custom weapon
- [ ] Show per-slot budget cost.
- [ ] Show total weapon budget used by the ship.
- [ ] Block launch if referenced custom weapons are missing.
- [x] Allow duplicate weapon display names without collision by showing name plus short UUID.
- [ ] Let players delete unused custom weapons.
- [ ] Warn before deleting a weapon used by saved custom ships.
- [ ] Add import/export later so players can share ship plus weapon packages intentionally.

## Security And Safety

- [x] No scripts.
- [x] No arbitrary file references.
- [x] No external URLs in definitions.
- [x] All paths must stay inside the custom weapon folder.
- [x] Enforce max image and audio sizes.
- [x] Validate JSON before use.
- [x] Recover from malformed JSON.
- [x] Never delete outside `save/custom_weapons/<uuid>/`.
- [x] Keep player-created files out of Git.
- [ ] Add content-hash metadata later for import/export verification.

## Recommended Milestone Order

The first milestone should prove the architecture, not finish every weapon fantasy.

- [x] **Milestone 1 / V1-A: Direct custom cannon**
  - [x] Create a direct-fire projectile weapon with `turret.png`, `projectile.png`, `thumbnail.png`, and `definition.json`.
  - [x] Save it under UUID.
  - [x] Reload it after restart.
  - [x] Mount it to a custom ship.
  - [x] Spawn that ship for Team E.
  - [x] Let player and AI ships fire it.
  - [x] Render the custom turret and projectile.
  - [x] Apply saved damage, cooldown, speed, range, and multipliers as the authoritative weapon base profile.
  - [x] Keep custom-specific logic from spreading through combat systems.
- [ ] **Milestone 2: Missiles and torpedoes**
  - [ ] Add `HOMING_PROJECTILE`.
  - [ ] Add missile speed caps and guidance validation.
  - [ ] Add target profiles for light, medium, heavy, and structure targets.
- [ ] **Milestone 3: Point defense and flak**
  - [ ] Add `INTERCEPTOR`.
  - [ ] Add `PROXIMITY_PROJECTILE`.
  - [ ] Add missile and small-craft targeting validation.
- [ ] **Milestone 4: Beams**
  - [ ] Add `BEAM`.
  - [ ] Add beam duration, tick damage, and beam-specific rendering.
- [ ] **Milestone 5: Custom audio and VFX**
  - [ ] Add custom firing audio.
  - [ ] Add custom impact audio.
  - [ ] Add muzzle and impact sprites.
- [ ] **Milestone 6: Import/export**
  - [ ] Add shareable weapon packages.
  - [ ] Add ship-plus-weapon bundle export.
- [ ] **Later: multiplayer sync**
  - [ ] Add deterministic content validation.
  - [ ] Add missing-content negotiation.
  - [ ] Add content hash checks.

## V1-A Implementation Checklist

- [x] Add `.gitignore` entries for local custom weapon content.
- [x] Add `CustomWeaponFamily`.
- [x] Add `CustomWeaponRuntimeBehavior`.
- [x] Add `CustomDamageProfile`.
- [x] Add `CustomTargetProfile`.
- [x] Add `CustomWeaponDefinition`.
- [x] Add `CustomWeaponRegistry`.
- [x] Add `CustomWeaponAssetProcessor`.
- [x] Add `WeaponDefinitionRef`.
- [x] Add `WeaponRuntimeProfile`.
- [x] Add JSON save/load.
- [x] Add validation and missing-content reporting.
- [x] Add image import for turret PNG and projectile PNG.
- [x] Add generated thumbnail.
- [x] Add constrained direct-projectile stat generator from player fields.
- [x] Add custom weapon list UI.
- [ ] Add create/edit/delete UI.
- [ ] Add static preview UI.
- [x] Add ship-builder weapon-slot selector.
- [x] Add runtime adapter from custom weapon definition to turret profile.
- [x] Add renderer support for custom turret and projectile sprites.
- [x] Add AI range and firing integration.
- [x] Use deliberate built-in audio or silence; no custom audio in V1-A.
- [x] Add recoverable error path for missing weapon assets.
- [x] Defer beams.
- [x] Defer flak.
- [x] Defer shotgun/cone weapons.
- [x] Defer arcing projectiles.
- [x] Defer custom muzzle images.
- [x] Defer custom impact images.
- [x] Defer custom audio imports.

## Tests

- [x] Custom weapon definition round-trips through save/load.
- [x] Deterministic reconstruction test:
  - [x] create weapon
  - [x] save definition
  - [x] simulate game restart
  - [ ] change generator or balance implementation
  - [x] load weapon
  - [x] verify saved damage, cooldown, range, speed, behavior, multipliers, and assets are unchanged
- [ ] Duplicate display names are allowed.
- [x] UUID remains stable after save/load.
- [ ] Malformed JSON cannot crash startup.
- [ ] Missing custom weapon folder is recreated where appropriate.
- [ ] Old schema can be rejected or migrated cleanly.
- [x] Custom weapon paths cannot escape `save/custom_weapons/`.
- [x] Importing valid turret and projectile PNGs creates a custom weapon folder.
- [ ] Invalid image is rejected with a clear message.
- [ ] Oversized image is rejected.
- [x] Saved weapon appears in the weapon builder list.
- [ ] Deleting a weapon removes only its sandboxed folder.
- [ ] Deleting a used weapon warns or blocks.
- [ ] Built-in weapons still fire without loading `CustomWeaponRegistry`.
- [x] Custom direct-fire projectile spawns and damages a target.
- [ ] Custom beam profile spawns and damages a target.
- [ ] Custom missile profile respects speed caps and guidance constraints.
- [ ] Custom point-defense profile can target missiles.
- [ ] Custom weapon AI effective range is recognized.
- [x] Custom weapon renderer uses custom turret and projectile sprites.
- [x] Missing custom projectile sprite uses a recoverable fallback.
- [ ] Audio does not rotate into legacy fallback sounds when a custom sound is selected.
- [ ] Balance validator rejects extreme DPS, range, speed, splash, and zero-cooldown weapons.

## Deferred Features

- [ ] Animated turret sprites.
- [ ] Animated projectile sprites.
- [ ] Sprite sheets.
- [ ] Beam texture uploads.
- [ ] Custom impact particle systems.
- [ ] Manual projectile trail editor.
- [ ] Weapon heat and capacitor systems.
- [ ] Ammunition economy.
- [ ] Custom weapon upgrade trees.
- [ ] Multiplayer content sync.
- [ ] Shareable ship-and-weapon package export.
- [ ] Steam Workshop or mod browser integration.

## Out Of Scope For V1

- [ ] Player-authored code.
- [ ] Player-authored formulas.
- [ ] Weapon effects that stun, freeze, disable movement, lock out player controls, or create EMP-style helplessness.
- [ ] Campaign-wide custom faction weapon manufacturing.
- [ ] Multiplayer custom weapon usage without content sync and validation.
- [ ] Built-in faction rebalance around custom weapons.

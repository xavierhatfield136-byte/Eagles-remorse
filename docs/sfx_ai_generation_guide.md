# SFX AI Generation Guide

This document is the production spec for generating ship/gameplay sound effects with ChatGPT or any other AI audio tool.

Scope:
- `assets/audio/*` runtime SFX that are already wired in code.
- High-quality generation prompts and export targets.
- A future expansion list for additional SFX hooks you may want to add next.

## 1. Global Audio Direction

Game tone:
- Space naval combat.
- Readable, punchy, tactical.
- Slightly grounded sci-fi (not cartoony, not ultra-realistic simulation).

Mix priorities:
- Weapons and impacts must cut through the mix.
- UI and subsystem alerts must be short and clear.
- Ambience must sit behind gameplay and never mask combat.

Do not generate:
- Speech or vocals.
- Tonal melodies.
- Long reverb tails that blur repeat fire.
- Heavy low-end rumble that muddies explosions.

## 2. Technical Export Spec

Use these defaults unless noted:
- Format: WAV (PCM 16-bit)
- Sample rate: 48000 Hz preferred (44100 Hz acceptable)
- One-shots: mono preferred
- Loops/ambience: stereo preferred
- Peak target: around `-3.0 dBFS` max peak
- Loudness target for one-shots: roughly `-18 to -10 dB` integrated equivalent depending on class
- Trim silence at head/tail

Naming:
- Prefix-based loader expects `prefix_01.wav`, `prefix_02.wav`, etc.
- Keep names lowercase with underscores.

## 3. Prompt Template (Reuse For Any Event)

Use this template in your audio generator:

```
Create a [DURATION] second [ONE-SHOT or LOOP] sci-fi game sound effect.
Role: [EVENT ROLE].
Style: space naval combat, clean transient, mix-ready, no voice, no music.
Texture: [TIMBRE NOTES].
Dynamics: [DYNAMIC NOTES].
Tail: [TAIL NOTES].
Output: dry-ish, minimal room reverb, no clipping, tight start.
```

Negative prompt text to append:

```
No vocals, no spoken words, no melody, no chord progression, no cinematic riser, no distortion clipping, no long boomy tail.
```

## 4. Runtime-Wired SFX Generation Sheet

These are already defined in `src/SfxManifest.java` and should be generated first.

| Event ID | Output Files | Duration Target | Variants | Prompt Notes |
|---|---|---:|---:|---|
| `ui.open` | `assets/audio/ui/open_01.wav` | 0.08-0.20s | 1 | Crisp holographic panel open tick, soft high-mid sparkle, short and clean. |
| `ui.close` | `assets/audio/ui/close_01.wav` | 0.08-0.22s | 1 | Complement of open tick, slightly lower pitch, quick shutdown click. |
| `weapon.primary_fire` | `assets/audio/weapons/primary_fire_01.wav`, `_02.wav` | 0.10-0.28s | 2 | Fast cannon/rail pulse, bright transient, compact body, minimal tail for rapid repetition. |
| `weapon.secondary_fire` | `assets/audio/weapons/secondary_fire_01.wav`, `_02.wav` | 0.18-0.45s | 2 | Missile launch thump + ignition snap, mid-low body, short whoosh tail. |
| `weapon.wave_fire` | `assets/audio/weapons/wave_fire_01.wav` | 0.70-1.30s | 1 | Superweapon discharge, deep energy surge and sharp release, authoritative but controlled tail. |
| `impact.shield.kinetic` | `assets/audio/impacts/shield_kinetic_01.wav`, `_02.wav` | 0.08-0.25s | 2 | Kinetic hit on energy shield, crisp crack with thin electric shimmer. |
| `impact.shield.energy` | `assets/audio/impacts/shield_energy_01.wav`, `_02.wav` | 0.08-0.28s | 2 | Plasma/energy contact on shield, bright zap pop, controlled fizz decay. |
| `impact.shield.beam` | `assets/audio/impacts/shield_beam_01.wav`, `_02.wav` | 0.10-0.30s | 2 | Beam contact pulse, smooth electric sting with focused transient. |
| `impact.shield.explosive` | `assets/audio/impacts/shield_explosive_01.wav`, `_02.wav` | 0.12-0.35s | 2 | Explosive shock against shield, punchy burst with brief ionized ring. |
| `impact.hull.kinetic` | `assets/audio/impacts/hull_kinetic_01.wav`, `_02.wav` | 0.10-0.32s | 2 | Metal armor ping/thud, hard transient, little ring, no cartoon clang. |
| `impact.hull.energy` | `assets/audio/impacts/hull_energy_01.wav`, `_02.wav` | 0.10-0.34s | 2 | Energy strike on metal hull, sizzling impact plus metallic bite. |
| `impact.hull.beam` | `assets/audio/impacts/hull_beam_01.wav`, `_02.wav` | 0.12-0.36s | 2 | Beam scoring hull, hot sear transient and short burn grit. |
| `impact.hull.explosive` | `assets/audio/impacts/hull_explosive_01.wav`, `_02.wav` | 0.14-0.42s | 2 | Compact explosive armor hit, punchy blast, restrained low-end tail. |
| `impact.explosion` | `assets/audio/impacts/explosion_01.wav`, `_02.wav` | 0.45-1.30s | 2 | Ship explosion, layered blast with debris texture, cinematic but not oversized. |
| `hazard.fire_ignition` | `assets/audio/hazards/fire_ignition_01.wav`, `_02.wav` | 0.18-0.55s | 2 | Sudden onboard fire ignite burst, gas flare style with sharp start. |
| `hazard.fire_spread` | `assets/audio/hazards/fire_spread_01.wav`, `_02.wav` | 0.22-0.70s | 2 | Fire propagating through compartments, crackle rush, no looping ambience. |
| `hazard.fire_suppression` | `assets/audio/hazards/fire_suppression_01.wav`, `_02.wav` | 0.20-0.65s | 2 | Suppressant vent burst, hiss + pressure release, clean and readable. |
| `subsystem.engines_offline` | `assets/audio/subsystems/engines_offline_01.wav` | 0.45-1.10s | 1 | Engine shutdown warning stutter, mechanical power-down feel. |
| `subsystem.reactor_offline` | `assets/audio/subsystems/reactor_offline_01.wav` | 0.65-1.40s | 1 | Reactor failure tone, deeper and more severe than other subsystem alerts. |
| `subsystem.sensors_offline` | `assets/audio/subsystems/sensors_offline_01.wav` | 0.35-1.00s | 1 | Electronic subsystem dropout chirp, mid-high failure cue. |
| `subsystem.weapons_offline` | `assets/audio/subsystems/weapons_offline_01.wav` | 0.35-1.00s | 1 | Weapon grid failure alert, short and firm, slightly aggressive timbre. |
| `subsystem.shields_offline` | `assets/audio/subsystems/shields_offline_01.wav` | 0.35-1.05s | 1 | Shield collapse cue, descending energy tone + brief fade. |
| `ambience.bridge_ambient` | `assets/audio/ambient/bridge_ambient_01.wav` | 12-30s seamless loop | 1 | Low-level command-deck hum, subtle electronics, no rhythmic pulse. |
| `ambience.engine_loop` | `assets/audio/ambient/engine_loop_01.wav` | 8-20s seamless loop | 1 | Steady ship engine bed, soft turbine/drone texture, no obvious cycle click. |
| `ambience.station_hum` | `assets/audio/ambient/station_hum_01.wav` | 12-30s seamless loop | 1 | Base/station power ambience, broad low-mid hum with sparse detail. |

## 5. Future SFX Expansion Sheet (Recommended)

These are not all wired yet, but they are high-value additions.

| Suggested Event | Proposed Prefix | Duration Target | Variants | Description |
|---|---|---:|---:|---|
| Shield break heavy | `shield_break` | 0.35-0.90s | 2 | Distinct cue when a shield is fully depleted. |
| Engine thrust burst | `engine_thrust` | 0.20-0.80s | 3 | Momentary acceleration punch for player/NPC ships. |
| Engine idle small | `engine_idle_small` | 6-15s loop | 1 | Background loop for frigate-size ships. |
| Engine idle capital | `engine_idle_capital` | 8-20s loop | 1 | Heavier, slower loop for large ships. |
| Drone launch | `drone_launch` | 0.15-0.45s | 2 | Carrier drone release cue. |
| Drone dock return | `drone_dock` | 0.20-0.55s | 2 | Return-to-bay mechanical latch cue. |
| Missile flyby/loop | `missile_flight` | 0.40-1.20s loopable | 2 | Short hiss/rocket trail for close passes. |
| Critical hull alarm | `alarm_hull_critical` | 0.50-1.20s | 1 | Urgent repeating warning candidate. |
| Low shield alarm | `alarm_shield_low` | 0.45-1.10s | 1 | Distinct from hull-critical alarm. |
| Target lock acquired | `lock_on` | 0.08-0.20s | 1 | Fast confirmation chirp. |
| Target lock lost | `lock_lost` | 0.10-0.25s | 1 | Slightly descending de-confirm cue. |
| Salvage pickup | `salvage_pickup` | 0.08-0.25s | 2 | Small reward pickup cue. |
| Ore deposit | `ore_deposit` | 0.15-0.45s | 2 | Resource transfer success feedback. |
| Miner drill active | `miner_drill` | 0.35-1.20s loopable | 2 | Mining beam/tool active cue. |
| Hauler transfer | `hauler_transfer` | 0.20-0.65s | 2 | Cargo handoff cue between logistics ships. |

## 6. Fast Generation Workflow

1. Generate all one-shots first (UI, weapons, impacts, hazards, subsystem).
2. Generate ambience loops second.
3. Export with exact names and folder placement.
4. Run validation:
   - `.\gradlew.bat compileJava`
   - `java -cp build/classes/java/main SfxValidationHarness --strict`
5. Run missing-asset soak:
   - `java -cp build/classes/java/main SfxSoakHarness --seconds=180 --seed=90909`
6. In-game listen pass and tweak outliers (too sharp, too muddy, too long).

## 7. Quality Checklist

Per file checklist:
- Starts immediately (no front silence).
- No clipping.
- Tail is not excessive for repeat-trigger events.
- Spectral balance leaves room for voice/UI and other impacts.
- Variants feel related but not identical.
- Loop files are seamless (no click at wrap point).

Batch checklist:
- Combat remains readable under heavy projectile spam.
- Shield and hull impacts are clearly distinguishable.
- Subsystem alerts are unique and instantly recognizable.
- Ambience sits under action and does not dominate.


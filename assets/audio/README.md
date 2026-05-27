# Audio Drop Zone

Runtime audio now supports these folders:

- `ambient/`
- `weapons/`
- `impacts/`
- `hazards/`
- `subsystems/`
- `ui/`
- `dropoff/` (staging only; not loaded by runtime)

Use WAV files (`PCM 16-bit`, `44100` or `48000` Hz, mono or stereo).

## Dropoff Workflow

Use `dropoff/` as a staging bin for newly generated/renamed SFX.

Recommended flow:

1. Generate and rename files to match `docs/sfx_generation_jobs.csv`.
2. Place them in `assets/audio/dropoff/`.
3. Move each file into its runtime folder (`weapons/`, `impacts/`, etc.).
4. Run:
   - `java -cp build/classes/java/main SfxValidationHarness --strict`
   - `java -cp build/classes/java/main SfxSoakHarness --seconds=180 --seed=90909`

## Naming

Files are loaded by prefix, so you can add variants with `_01`, `_02`, etc.

Ambient:
- `ambient/bridge_ambient_01.wav`

Weapons:
- `weapons/primary_fire_01.wav`
- `weapons/secondary_fire_01.wav`
- `weapons/wave_fire_01.wav`
- `weapons/weapon_blue_small_fire_01.wav`
- `weapons/weapon_blue_medium_fire_01.wav`
- `weapons/weapon_blue_capital_fire_01.wav`
- `weapons/weapon_red_small_fire_01.wav`
- `weapons/weapon_red_medium_fire_01.wav`
- `weapons/weapon_red_capital_fire_01.wav`
- `weapons/weapon_green_small_fire_01.wav`
- `weapons/weapon_green_medium_fire_01.wav`
- `weapons/weapon_green_capital_fire_01.wav`
- `weapons/weapon_yellow_small_fire_01.wav`
- `weapons/weapon_yellow_medium_fire_01.wav`
- `weapons/weapon_yellow_capital_fire_01.wav`
- `weapons/missile_launch_01.wav`
- `weapons/torpedo_launch_01.wav`
- `weapons/ciws_fire_01.wav`
- `weapons/super_blue_charge_01.wav`
- `weapons/super_blue_fire_01.wav`
- `weapons/hyper_blue_charge_01.wav`
- `weapons/hyper_blue_fire_01.wav`
- `weapons/super_red_charge_01.wav`
- `weapons/super_red_fire_01.wav`
- `weapons/hyper_red_charge_01.wav`
- `weapons/hyper_red_fire_01.wav`
- `weapons/super_green_charge_01.wav`
- `weapons/super_green_fire_01.wav`
- `weapons/hyper_green_charge_01.wav`
- `weapons/hyper_green_fire_01.wav`
- `weapons/super_yellow_charge_01.wav`
- `weapons/super_yellow_fire_01.wav`
- `weapons/hyper_yellow_charge_01.wav`
- `weapons/hyper_yellow_fire_01.wav`
- `weapons/warp_spool_up_01.wav`
- `weapons/warp_exit_01.wav`

Impacts:
- `impacts/shield_kinetic_01.wav`
- `impacts/shield_energy_01.wav`
- `impacts/shield_beam_01.wav`
- `impacts/shield_explosive_01.wav`
- `impacts/shield_damage_01.wav`
- `impacts/hull_kinetic_01.wav`
- `impacts/hull_energy_01.wav`
- `impacts/hull_beam_01.wav`
- `impacts/hull_explosive_01.wav`
- `impacts/hull_damage_01.wav`
- `impacts/explosion_01.wav`

UI:
- `ui/open_01.wav`
- `ui/close_01.wav`

Phase 3 note:

- SFX no longer use synthesized tone fallback in normal gameplay loops.
- Missing files are logged as `sfx_missing` telemetry events.

Validation and generation harnesses:

- Build classes: `./gradlew compileJava`
- Generate placeholder authored SFX pack: `java -cp build/classes/java/main AudioAssetStubGenerator`
- Manifest + clipping validation (strict): `java -cp build/classes/java/main SfxValidationHarness --strict`
- Runtime missing-asset soak: `java -cp build/classes/java/main SfxSoakHarness --seconds=180 --seed=90909`


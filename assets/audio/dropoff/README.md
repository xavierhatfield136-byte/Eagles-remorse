# SFX Dropoff Bin

This folder is a staging bin for newly generated sound effects.

Use it to hold files after renaming, before moving them into runtime folders.

## What Goes Here

- WAV files that already follow your target naming convention.
- Temporary batches from AI generation tools.

## What Does Not Happen Automatically

- Files in this folder are **not** loaded by the game runtime.
- Validation harnesses check runtime folders, not this staging bin.

## Finalize a Batch

1. Rename files to exact names listed in `docs/sfx_generation_jobs.csv`.
2. Drop them here (`assets/audio/dropoff/`).
3. Move each file into the correct runtime folder:
   - `assets/audio/ui/`
   - `assets/audio/weapons/`
   - `assets/audio/impacts/`
   - `assets/audio/hazards/`
   - `assets/audio/subsystems/`
   - `assets/audio/ambient/`
4. Run strict validation:
   - `java -cp build/classes/java/main SfxValidationHarness --strict`

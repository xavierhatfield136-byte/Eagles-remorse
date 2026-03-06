# Audio Drop Zone

Runtime audio now supports these folders:

- `ambient/`
- `weapons/`
- `impacts/`
- `ui/`

Use WAV files (`PCM 16-bit`, `44100` or `48000` Hz, mono or stereo).

## Naming

Files are loaded by prefix, so you can add variants with `_01`, `_02`, etc.

Ambient:
- `ambient/bridge_ambient_01.wav`

Weapons:
- `weapons/primary_fire_01.wav`
- `weapons/secondary_fire_01.wav`
- `weapons/wave_fire_01.wav`

Impacts:
- `impacts/shield_hit_01.wav`
- `impacts/hull_hit_01.wav`
- `impacts/explosion_01.wav`

UI:
- `ui/open_01.wav`
- `ui/close_01.wav`

If files are missing, the game falls back to synthesized placeholder tones so audio remains functional.


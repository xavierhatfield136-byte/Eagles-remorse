# Crew Portraits and Voice Pipeline

Date: 2026-03-03

## Goal

Ship crew portraits and voice callouts quickly, with consistent style and file naming so engineering hookup is simple.

## Drop Zones

Use these folders:

- `assets/crew_portraits/`
- `assets/voice/`

Role subfolders for voice:

- `assets/voice/captain/`
- `assets/voice/helm/`
- `assets/voice/tactical/`
- `assets/voice/engineering/`
- `assets/voice/science/`

## Portrait File Names

Required base portraits:

- `captain.png`
- `helm.png`
- `tactical.png`
- `engineering.png`
- `science.png`

Optional alternates:

- `captain_alt_01.png`
- `helm_alt_01.png`
- `tactical_alt_01.png`
- `engineering_alt_01.png`
- `science_alt_01.png`

Recommended image specs:

- Format: PNG
- Size: `512x512` or `1024x1024`
- Framing: chest-up portrait
- Background: simple/dim bridge backdrop (do not use busy scenes)

## Portrait Style Lock

Use one locked style prompt for all crew so faces look like one art set.

Base style prompt:

`Stylized sci-fi bridge officer portrait, chest-up, clean cinematic lighting, realistic proportions, sharp facial detail, subtle uniform paneling, cool starship bridge background, high readability at small UI sizes, no text, no logo, no watermark`

Append role-specific prompt fragments:

- Captain:
  `confident veteran commander, composed expression, command insignia accents`
- Helm:
  `focused pilot/navigation specialist, attentive eyes, agile posture`
- Tactical:
  `weapons officer, determined expression, tactical HUD reflections on visor/glass`
- Engineering:
  `chief engineer, practical uniform details, slightly worn gloves/tools aesthetic`
- Science:
  `sensor/science officer, analytical expression, subtle holographic scan glow`

Quality constraints to include in each prompt:

- `single character only`
- `centered composition`
- `no helmets covering face`
- `no extra arms/fingers`
- `no text overlays`

## Voice File Names

Each line should be placed in a role folder and named by event id.

Examples:

- `assets/voice/captain/combat_start_01.wav`
- `assets/voice/helm/evasive_01.wav`
- `assets/voice/tactical/target_lock_01.wav`
- `assets/voice/engineering/shields_low_01.wav`
- `assets/voice/science/new_contact_01.wav`

Recommended audio specs:

- Format: WAV (PCM 16-bit)
- Sample rate: 44100 Hz or 48000 Hz
- Channels: mono
- Peak target: around `-3 dBFS` max
- Loudness target: around `-16 LUFS` integrated (if your tool supports it)

## Voice Generation Guidance

Start with synthetic voices now; replace key lines later with higher quality recordings if needed.

Voice personality targets:

- Captain: calm authority, concise
- Helm: responsive and alert
- Tactical: urgent but controlled
- Engineering: practical/technical
- Science: analytical/precise

Voice delivery rules:

- Keep each line short (about 1 to 2 seconds).
- Avoid long flavor speeches during combat.
- Prefer tactical phrases over narrative phrases.
- Record 2 to 3 variants for high-frequency events.

## Starter Line Set

Captain:

- `combat_start_01`: "All stations, battle posture."
- `fallback_01`: "Fall back and regroup."
- `push_01`: "Press the attack now."

Helm:

- `intercept_01`: "Intercept course set."
- `evasive_01`: "Executing evasive pattern."
- `rtb_01`: "Returning to base vector."

Tactical:

- `target_lock_01`: "Target lock confirmed."
- `target_lost_01`: "Target lock lost."
- `missiles_inbound_01`: "Missiles inbound."

Engineering:

- `shields_low_01`: "Shield integrity critical."
- `reactor_hit_01`: "Reactor section damaged."
- `repairs_started_01`: "Damage control underway."

Science:

- `new_contact_01`: "New hostile contact detected."
- `jammed_01`: "Sensors are being jammed."
- `scan_complete_01`: "Scan complete."

## Prompt Template for TTS

Use this template in your TTS tool prompt/style field:

`Deliver as a professional starship officer in active combat. Keep it short, clear, and tactical. No dramatic pauses. Neutral accent.`

Then generate one file per line id.

## Acceptance Checklist

Portrait pass is ready when:

- All 5 base portraits exist with exact file names.
- Portraits look like one coherent set.
- Faces stay readable at small UI preview size.

Voice pass is ready when:

- Each role has at least 3 usable lines.
- Filenames follow the event naming convention.
- Audio levels are consistent across all files.
- No clipping or heavy background noise.

## Recommended Next Engineering Step

After assets are dropped in:

1. Load portraits in crew station UI tabs by role.
2. Add an `AudioSystem` event bus for voice callouts.
3. Trigger callouts with per-event cooldowns and priority rules.

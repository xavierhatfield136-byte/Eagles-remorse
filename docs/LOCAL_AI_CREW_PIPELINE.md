# Local AI Crew Asset Pipeline

Date: 2026-03-08

This pipeline upgrades existing crew voice and portrait assets with local models.
It keeps game runtime naming and folder layout unchanged.

## 1) Local Tooling

Install and run:

- Stable Diffusion WebUI/Forge API (for portraits) on `http://127.0.0.1:7860`
- Piper TTS CLI (for voice)
- ffmpeg (recommended for normalization)
- Windows SAPI voices can be used as a fallback voice engine when Piper models are not available

## 2) Crew Bibles

Repo files:

- Portrait bible: `assets/ai_pipeline/crew_portrait_bible.json`
- Voice line matrix: `assets/ai_pipeline/crew_voice_lines.csv`
- TTS config template: `assets/ai_pipeline/local_tts_voices.example.json`

Create your local voice config:

1. Copy `assets/ai_pipeline/local_tts_voices.example.json`
2. Save as `assets/ai_pipeline/local_tts_voices.json`
3. Set each role engine:
   - Piper: `engine = "piper"` with `model_path` and optional `speaker`
   - Windows fallback: `engine = "sapi"` with `voice_name`, optional `rate`, and optional `volume`

Distinct crew voices:

- Use a different voice model or speaker per role whenever possible.
- If your TTS engine supports multiple speakers, the generator now also accepts arrays for `model_path`, `config_path`, `speaker`, `voice_name`, `rate`, and `volume`, and rotates them by variant number.

## 3) Generate Assets (Step 3)

Portraits:

```powershell
.\scripts\generate-local-crew-portraits.ps1
```

Voice:

```powershell
.\scripts\generate-local-crew-voice.ps1 `
  -VoiceConfigPath assets/ai_pipeline/local_tts_voices.json
```

If Piper models are unavailable but you have Windows desktop voices installed, the same script can now generate WAVs via SAPI using the same role/event matrix.

One-command pipeline:

```powershell
.\scripts\run-local-ai-crew-pipeline.ps1 `
  -VoiceConfigPath assets/ai_pipeline/local_tts_voices.json
```

Output paths:

- Portraits: `assets/crew_portraits/*.png`
- Voice: `assets/voice/<role>/<event_id>_<variant>.wav`

## 4) Quality Gates (Step 4)

Portrait checks:

```powershell
java -cp build/classes/java/main CrewPortraitPipelineHarness --strict
```

Voice coverage checks:

```powershell
java -cp build/classes/java/main VoiceCoverageHarness --strict
```

Voice format/level checks:

```powershell
java -cp build/classes/java/main VoiceAssetQualityHarness --strict
```

The voice quality harness validates:

- naming format
- mono channel target
- 44.1/48 kHz sample rates
- duration envelope for callouts
- clipping and hot peaks
- rough RMS consistency (per role)

## 5) Runtime Integration (Step 5)

Runtime behavior now maps active voice cues to portrait expression levels:

- lower priority cues -> `alt_01`
- medium priority cues -> `alt_02`
- high urgency cues -> `alt_03`

If a requested alt portrait is missing, runtime falls back to the base role portrait.

No gameplay system depends on alt portraits existing, so missing files degrade gracefully.

## 6) Safety/Legal

Use only synthetic voices and generated faces that do not copy real people.
Keep a private metadata log of:

- model name/version
- checkpoint/seed
- prompt revision
- generation date

This makes future replacements and compliance review easier.

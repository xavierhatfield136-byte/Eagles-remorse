# Crew Voice Drop Zone

Put generated voice callouts in role folders:

- `captain/`
- `helm/`
- `tactical/`
- `engineering/`
- `science/`

File naming:

- `<event_id>_<variant>.wav`
- Example: `target_lock_01.wav`

Recommended audio format:

- WAV PCM 16-bit
- 44100 Hz or 48000 Hz
- Mono

See `docs/CREW_PORTRAITS_VOICE_PIPELINE.md` for full prompt pack and line set.

Validation harnesses:

- Build classes: `./gradlew compileJava`
- Coverage check (strict): `java -cp build/classes/java/main VoiceCoverageHarness --strict`
- Quality check (strict): `java -cp build/classes/java/main VoiceAssetQualityHarness --strict`

Local AI generation:

- Configure role models: `assets/ai_pipeline/local_tts_voices.json` (copy from `.example`)
- Generate role lines with local Piper: `./scripts/generate-local-crew-voice.ps1 -VoiceConfigPath assets/ai_pipeline/local_tts_voices.json`
- Or use Windows desktop voices by setting role `engine` to `sapi` and `voice_name` in `assets/ai_pipeline/local_tts_voices.json`
- Full local pipeline: `./scripts/run-local-ai-crew-pipeline.ps1 -VoiceConfigPath assets/ai_pipeline/local_tts_voices.json`

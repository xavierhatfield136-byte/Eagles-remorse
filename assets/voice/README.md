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

Validation and generation harnesses:

- Build classes: `./gradlew compileJava`
- Generate placeholder variant WAVs: `java -cp build/classes/java/main VoiceAssetStubGenerator`
- Coverage check (strict): `java -cp build/classes/java/main VoiceCoverageHarness --strict`
- 5-minute anti-spam soak: `java -cp build/classes/java/main VoiceSoakHarness --seconds=300 --seed=424242`

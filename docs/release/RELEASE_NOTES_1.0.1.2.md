# Eagles Remorse 1.0.1.2

This release focuses on campaign stability, campaign-system decomposition, and a cleaner strategic map presentation.

## Downloads

- Windows installer: `EaglesRemorse-1.0.1.exe`, when the Windows packaging workflow completes the WiX installer step.
- Windows portable build: `EaglesRemorse-1.0.1.2.zip`
- Linux portable build: `EaglesRemorse-1.0.1.2-linux-x64.tar.gz`

All packages include a Java 21 runtime. Players do not need to install Java.

## Highlights

- Split large campaign-system responsibilities into focused presenter, service, runtime, save, strike, route, and support classes while preserving the existing campaign entry points.
- Reworked single-mission strategic map presentation with a darker board that keeps team-colored zones and objects readable.
- Fixed expired lost-contact selections so stale sensor memories cannot remain selectable for strikes or navigation.
- Fixed local campaign battle intervention prompts inside loaded mission sites.
- Improved NPC campaign fleet behavior around relays, retreat arrival, patrol scans, authored invasions, and Green counter-sorties.
- Added decomposition checklist and runtime update-order documentation for future campaign work.

## Validation

- `compileJava`
- `CampaignNpcFleetAiTest`
- `CampaignStrategicStrikeCounterplayTest`
- `CampaignStrategicLoopIntegrationTest`
- `CampaignPhaseFourAutonomousWarTest`

GitHub packaging workflows run the full project check before attaching Windows and Linux release artifacts.

See `KNOWN_ISSUES.md` and `SYSTEM_REQUIREMENTS.md` for current limitations and hardware guidance.

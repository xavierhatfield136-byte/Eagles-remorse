# Crew Portrait Drop Zone

Put generated portraits here.

Required filenames:

- `captain.png`
- `helm.png`
- `tactical.png`
- `engineering.png`
- `science.png`

Optional alternates:

- `<role>_alt_01.png`
- `<role>_alt_02.png`
- `<role>_alt_03.png`

Recommended:

- PNG
- 512x512 or 1024x1024
- Chest-up framing
- Clear face visibility at small sizes

Style lock prompt is exposed in `CrewPortraitSystem.styleLockPrompt()`.

Validation/readability harness:

- `./gradlew compileJava`
- `java -cp build/classes/java/main CrewPortraitPipelineHarness`

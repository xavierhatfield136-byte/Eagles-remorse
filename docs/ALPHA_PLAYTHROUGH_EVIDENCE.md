# Alpha Playthrough Evidence

Date: 2026-06-10

This file records the current acceptance evidence for the Windows-first alpha
playthrough blocker. Manual scripts remain in `docs/ALPHA_MANUAL_ACCEPTANCE_SCRIPTS.md`;
the runs below are automated surrogates for continuity, visual readability, and
longer campaign-transition stability.

## Automated Runs

- `./gradlew saveLoadSoak`
  - Result: PASS
  - Summary: `[save-load-soak] cycles=100 checks: PASS`

- `./gradlew campaignTransitionFuzz`
  - Result: PASS
  - Summary: `seed=20260602 cycles=24 ticksPerCycle=45 checkpoints=24 restores=24 travelStarts=24 ... pass=true`

- `./gradlew screenshotRegression --args="--update-baseline"`
  - Result: PASS
  - Purpose: approved intentional visual signature update after HUD/readiness changes.

- `./gradlew screenshotRegression`
  - Result: PASS
  - Captures: campaign map, fleet board, strike tab, tactical HUD, accessibility HUD.

- `./gradlew productionValidation`
  - Result: PASS

- `java -cp build/classes/java/main ChecklistV2Harness --strict --audio-seconds=90`
  - Result: PASS
  - Summary: phase 6, phase 8, room-profile coverage, room-hit, hazard, x-ray,
    audio, memory, and update-increase budgets all passed.

- `./gradlew phase9Smoke`
  - Result: PASS
  - Summary: room mapping, boundary checks, damage determinism, hazard
    determinism, cooldown/audio dispatch checks passed.

- `java -cp build/classes/java/main Phase9TelemetryHarness --strict --seconds=60`
  - Result: PASS
  - Summary: 1,588 room-hit events across 21 rooms, 7 hazard ignitions, and 19
    voice dispatches captured.

- `./gradlew performanceGuardrailSmoke`
  - Result: PASS
  - Summary: late-campaign battle stress and memory guardrail passed.

## Manual Script Status

The manual Windows scripts cover:

- new campaign first route
- save/load continuity
- defeat path
- victory path
- longer campaign session

Current automated evidence validates continuity and screen stability. Any later
human playtest failures should be logged back into the corresponding script and
promoted into a regression test or targeted fix.

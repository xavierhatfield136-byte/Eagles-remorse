# First-Hour Packaged Smoke Runbook

Use this for the remaining human validation on the Windows portable release
candidate.

## Build Under Test

- Version: `1.0.1.12`
- Package: `build\package\windows\EaglesRemorse-1.0.1.12-windows-x64-full.zip`
- SHA-256:
  `02272348875ee0e3a8e5eadc22f8c1091c2637df6f994a78ddf35819c0683636`
- Local package verification:
  `build\reports\distribution-verification\release_candidate_gate.json`
- Outside-repo launch smoke:
  `build\reports\distribution-verification\isolated_launch_smoke_full_shakedown.json`

## Owner Smoke Pass

- [ ] Extract the ZIP into a normal folder outside the repository.
- [ ] Start `EaglesRemorse.exe` from the extracted folder.
- [ ] Start `Commander's Academy` without developer tools.
- [ ] Confirm the player can complete every Academy/tutorial objective.
- [ ] Confirm the carrier/loadout swap step works.
- [ ] Confirm the yellow trade-hub course objective advances.
- [ ] Confirm no tutorial step waits on an impossible condition.
- [ ] Confirm an After-Action Report appears after a training/combat result.
- [ ] Start a normal campaign after the Academy path.
- [ ] Start a normal campaign without the Academy path.
- [ ] Save, exit, relaunch, and load.
- [ ] Run `.\gradlew.bat academyTelemetrySummary` after the pass and confirm the
  summary reflects starts/completions/abandonments accurately.
- [ ] Record any P0/P1 issue with exact step, screenshot or log, and whether it
  reproduces from a clean user data directory.

## Blind First-Hour Pass

- [ ] Give the same ZIP to at least one blind first-time tester.
- [ ] Do not explain controls unless the tester is blocked for more than five
  minutes.
- [ ] Record where the tester hesitates, misreads a prompt, or takes an
  unexpected action.
- [ ] Ask whether they understand their fleet, ship orders, range, retreat,
  repair/replacement, mission choice, tactical consequences, and the AAR.
- [ ] Promote any reproducible Academy softlock to P0.
- [ ] Promote misleading AAR or first-hour explanation to P1.
- [ ] Re-run the packaged smoke after every P0/P1 fix.

## Go/No-Go Rule

Do not mark the first-hour phase complete until the owner smoke pass succeeds,
the blind first-hour pass has no remaining P0/P1 issue, and the owner accepts
the result as clear enough for the release candidate.

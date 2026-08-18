# Full Game Shakedown - 2026-08-17

Status: automated shakedown passed; manual first-hour and external-machine
validation still required.

## Scope

This pass covered the local source tree, full test suite, release validation
slice, production validation, release contract, performance guardrails,
campaign-transition fuzzing, save/load soak, screenshot regression, Windows
packaging, package manifest/loadability verification, and outside-repository
packaged launch smoke.

## Automated Results

- Workspace inventory:
  `git status -sb`
- Task inventory:
  `.\gradlew.bat tasks --all`
- Static issue-marker scan:
  `rg -n "TODO|FIXME|HACK|XXX|P0|P1|softlock|crash|known issue|release blocker|not implemented|throw new UnsupportedOperationException|System\.exit\(|printStackTrace\(" src test docs -S`
- Combined shakedown:
  `.\gradlew.bat check currentReleaseVerification productionValidation phase11ReleaseContract performanceGuardrailsCi screenshotRegression`
  - Result: pass
  - Included: full `check`, release validation, production validation, release
    contract, campaign-transition fuzzing, performance smoke, performance soak,
    save/load soak, and screenshot regression.
- Fresh uncached test execution:
  `.\gradlew.bat test --rerun-tasks`
  - Result: pass
  - Test report summary: 226 XML files, 1,571 tests, 0 failures, 0 errors,
    0 skipped.
- Windows packaging:
  `.\gradlew.bat phase11Packaging`
  - Result: pass
  - WiX was not installed, so the EXE installer was skipped by the existing
    packaging rule.
- Windows portable verifier:
  `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-windows-portable-distribution.ps1`
  - Result: pass
  - Package:
    `build\package\windows\EaglesRemorse-1.0.1.12-windows-x64-full.zip`
  - SHA-256:
    `02272348875ee0e3a8e5eadc22f8c1091c2637df6f994a78ddf35819c0683636`
  - Report:
    `build\reports\distribution-verification\release_candidate_gate.json`
- Outside-repository packaged launch smoke:
  `scripts\smoke-windows-portable-launch.ps1`
  - Result: pass
  - Report:
    `build\reports\distribution-verification\isolated_launch_smoke_full_shakedown.json`

## Notable Counters

- Performance smoke: pass, `frameDecodes=0`, `gameplayDiskLoads=0`.
- Performance soak: pass, `frameDecodes=0`, `gameplayDiskLoads=0`.
- Save/load soak: pass, 100 cycles.
- Campaign-transition fuzz: pass, 24 checkpoints/restores.
- Screenshot regression: pass for campaign map, fleet board, strike tab,
  tactical HUD, and accessibility HUD.
- Package verifier: staged manifest match, ZIP manifest match, extracted
  manifest match, runtime asset loadability, and clean install all passed.
- Outside-repository launch smoke: executable stayed running after 12 seconds
  and no isolated user-data error log was created.

## Findings

- No automated P0/P1 bug, crash, save corruption, packaging omission, runtime
  asset loadability failure, screenshot regression, performance guardrail
  failure, or test failure reproduced in this pass.
- The issue-marker scan still finds historical audit/checklist language in docs.
  Those entries are not treated as active defects unless a current test,
  packaged smoke, or manual playthrough reproduces them.
- `release_candidate_gate.json` intentionally remains `releaseApproved=false`
  because external-machine validation and interactive first-hour/tutorial
  regression are not automated.

## Remaining Manual Gates

- Complete the interactive packaged Windows Academy/campaign smoke from the
  current ZIP.
- Run blind first-hour playtests from the packaged build.
- Re-run the packaged smoke after any P0/P1 playtest fix.
- Validate on an external Windows machine.
- Complete Steamworks/AppID/depot/store-page/go-no-go work.

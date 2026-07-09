# Owner Playtest Implementation Batch 4 Evidence

Date: July 8, 2026

## Scope completed

- Added `OwnerPlaytestBaselineHarness` with approved deterministic baseline seeds:
  - `71000` Green-heavy
  - `71001` Yellow-heavy
  - `71002` Red-heavy
- Added quick owner-baseline smoke coverage in `OwnerPlaytestBaselineHarnessTest`.
- Added progressive timeline/snapshot flushing so interrupted long soaks leave partial evidence instead of empty output.
- Extracted campaign strategic resource ledger/readout into `CampaignEconomySystem`.
- Added objective economy audit coverage in `CampaignEconomyBalanceAudit` and `CampaignEconomyBalanceAuditTest`.
- Added paired-seed difficulty outcome separation coverage in `CampaignDifficultyOutcomeSeparationTest`.
- Added fighter engagement acceptance coverage in `AISystemSmallCraftRangeTest`.
- Fixed escort warp cohesion so escorts target reserved screen slots around their anchor instead of warping toward the anchor center.
- Added `AISystemEscortFormationTest` to verify shared-anchor escorts receive separated warp exits.

## Architecture work

`CampaignSystem.java` was reduced from the prior 50k+ line range to 48,919 lines after extracting the authoritative economy ledger/readout into `CampaignEconomySystem.java`.

This is intentionally a small stabilization-time extraction. It is tied to Vertical Slice 3 resource-legibility work and is protected by `OwnerPlaytestVerticalSliceThreeTest`, `CampaignEconomyBalanceAuditTest`, and the owner baseline smoke harness.

## Verification run

Passed:

```text
.\gradlew.bat test --tests OwnerPlaytestVerticalSliceThreeTest
.\gradlew.bat test --tests OwnerPlaytestBaselineHarnessTest --tests OwnerPlaytestVerticalSliceThreeTest
.\gradlew.bat test --tests CampaignEconomyBalanceAuditTest --tests OwnerPlaytestBaselineHarnessTest --tests OwnerPlaytestVerticalSliceThreeTest
.\gradlew.bat test --tests CampaignDifficultyOutcomeSeparationTest --tests CampaignEconomyBalanceAuditTest --tests OwnerPlaytestBaselineHarnessTest --tests OwnerPlaytestVerticalSliceThreeTest
.\gradlew.bat test --tests AISystemSmallCraftRangeTest
.\gradlew.bat test --tests AISystemEscortFormationTest --tests AISystemSmallCraftRangeTest
```

Attempted but not completed inside the command time limit:

```text
java -cp "build\classes\java\main;build\resources\main" OwnerPlaytestBaselineHarness --seconds=1200
```

The 1,200-second run exceeded the command cap before completing all three seeds. After the streaming-writer patch, interrupted long runs leave partial evidence instead of empty output.

The current checked artifact pair is a clean 60-second all-seed baseline:

- `build/reports/owner-playtest-baseline-timeline.csv`
- `build/reports/owner-playtest-baseline-snapshot.md`

The snapshot reports `PASS` for the 60-second run. The 20-minute all-seed gate remains open until a full 1,200-second run completes.

## Deferred to owner worksheet

- Exact time-to-first-major-upgrade target.
- Final Standard/Iron attrition feel.
- Whether current mining/salvage rewards feel generous, stingy, or correct.
- Whether difficulty preset differences are perceptible enough during ordinary play.
- Whether escort spacing feels visually tight, loose, or right.

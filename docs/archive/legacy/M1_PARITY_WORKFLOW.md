# M1 Campaign Parity Workflow

## Purpose
- Provide deterministic regression checks for campaign startup and sectors `1-3`.
- Lock objective-flow and economy parity metrics so refactors can be validated quickly.

## Artifacts
- Harness source: `src/CampaignParityHarness.java`
- Locked baseline: `docs/parity/campaign_m1_baseline.json`
- Run script: `scripts/run-campaign-parity.ps1`
- Compare script: `scripts/compare-campaign-parity.ps1`

## What Is Measured
- Objective flow sequence: `SURVIVE -> DESTROY -> CAPTURE`
- Per-sector parity metrics (S1-S3):
  - objective type + goal + time limit
  - clear time (rounded seconds)
  - clear tick count
  - credits after clear
  - side objective completion/failure flags
- Final state after S3->S4 transition:
  - final sector index
  - game over state
  - final credits

## Run Commands
1. Run parity and compare to baseline:
   - `powershell -ExecutionPolicy Bypass -File scripts\run-campaign-parity.ps1`
2. Run parity for custom seeds:
   - `powershell -ExecutionPolicy Bypass -File scripts\run-campaign-parity.ps1 -Seeds "11111,22222,33333"`
3. Refresh baseline intentionally:
   - `powershell -ExecutionPolicy Bypass -File scripts\run-campaign-parity.ps1 -UpdateBaseline -SkipCompare`

## Notes
- Harness runs headless and uses controlled assistance to keep outputs deterministic.
- Profile persistence is disabled during harness runs (`campaignUnlockProfile = null`) so local save files do not skew results.
- If baseline updates are needed, include the reason in commit message (mechanic change vs bug fix vs intentional retune).

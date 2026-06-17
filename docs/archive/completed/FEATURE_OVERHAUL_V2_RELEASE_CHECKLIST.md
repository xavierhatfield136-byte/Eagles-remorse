# Feature Overhaul V2 Release Checklist

Date: 2026-03-07

## Harness Gates
- [x] `scripts/run-phase9.ps1 -Seconds 300` completed successfully.
- [x] `Phase9DeterminismHarness` strict run passed.
- [x] `Phase9TelemetryHarness` strict run passed.
- [x] Campaign smoke run (`CampaignParityHarness`, compare skipped) passed for seeds `10101,20202,30303`.
- [x] `scripts/run-checklist-v2.ps1 -AudioSeconds 180` executed with all objective checks passing except x-ray draw budget.
- [x] `CrewPortraitPipelineHarness --strict` passed (20 portraits, no audit/readability issues).
- [x] `VoiceCoverageHarness --strict` and `VoiceAssetQualityHarness --strict` passed for all crew roles.

## Required Artifacts
- [x] Determinism log generated: `build/reports/phase9_determinism.txt`.
- [x] Telemetry JSON generated: `build/reports/phase9_telemetry.json`.
- [x] Campaign smoke JSON generated: `build/reports/phase9_campaign_smoke.json`.
- [x] Sign-off report documented: `docs/FEATURE_OVERHAUL_V2_SIGNOFF_REPORT.md`.

## Quality Gates
- [x] Room-hit mapping and boundary determinism verified.
- [x] Damage-to-room and hazard progression deterministic under fixed seed.
- [x] Voice cooldown and SFX cooldown checks show zero violations.
- [x] No missing core SFX assets reported in strict Phase 9 checks.
- [x] No known P0/P1 regressions in x-ray, hazard, or subsystem logic from automated runs.

## Performance Gates
- [x] Telemetry includes per-system update costs.
- [x] Telemetry includes aggregate frame/update timing.
- [x] Measured averages remain well under Phase 9 stress thresholds in this run.
- [x] Room-hit resolver, hazard update, audio dispatch, and memory budgets validated by `ChecklistV2Harness`.
- [x] X-ray draw overhead budget (`< 0.7 ms when open`) validated (`0.677 ms/draw`, strict run, 2026-03-11).

## Follow-Up (Non-Blocking)
- [x] Refresh `docs/parity/campaign_m1_baseline.json` for the current deterministic credit totals (`11738` end credits in phase9 smoke run, updated 2026-03-11).
- [x] Optimize x-ray draw path to satisfy `< 0.7 ms` budget and revalidate.
- [x] Run focused playtest/readability pass for x-ray in sustained combat (`docs/XRAY_READABILITY_PLAYTEST.md`, report: `build/reports/xray_readability_report.json`).

# M1 Vertical Slice Playtest Pass (Sectors 1-3)

## Authored Sector Script
- Sector 1 (`SURVIVE`, 360s): fixed four-wave script at ~45s, 130s, 220s, 300s with escalating pressure.
- Sector 2 (`DESTROY`, 16 kills): objective tracks authored strike-group IDs only; three deterministic reinforcement drops at ~55s, 150s, 250s.
- Sector 3 (`CAPTURE`, 120s): capture is locked until relay guards are destroyed, then hold phase starts with fixed reinforcement waves keyed to capture progress.

## Determinism Guardrails
- Generic AI wave spawns are disabled for campaign sectors 1-3.
- Random events are suppressed for campaign sectors 1-3.
- Sector 2 completion cannot be cheesed by incidental/random hostiles; only scripted objective hostiles count.
- Sector 3 completion always follows: `clear guards -> arm capture -> hold relay`.

## First-30-Minute Tuning Applied
- Early side-objective rewards increased (S1-S3: `160/200/240` credits).
- Early sector clear bonus multipliers increased (S1-S3: `1.20/1.15/1.10`).
- Early ore-credit multipliers increased (S1-S3: `1.15/1.10/1.08`).

## Playtest Run Protocol (3 Runs)
1. Run `CAMPAIGN_OPS` with seeds: `10101`, `20202`, `30303`.
2. Keep random events enabled in menu (campaign script suppresses them in sectors 1-3 automatically).
3. Capture telemetry lines for `sector_start`, `sector_clear`, `sector_fail`, and `sector_script`.
4. Record for each run:
   - Sector clear times (`S1/S2/S3`)
   - Credits at end of each sector
   - Player hull state at each transition
   - Subjective pressure note (`low / target / high`)

## Pass Criteria
- All 3 runs clear sectors 1-3 without scripting deadlocks.
- Combined S1-S3 duration lands in `24-34 minutes` band.
- Sector 2 objective progress is monotonic against authored kill targets.
- Sector 3 always transitions from guard-clear into hold phase correctly.

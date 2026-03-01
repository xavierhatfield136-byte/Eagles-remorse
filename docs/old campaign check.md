# M3 3D Campaign Validation (Sectors 1-3)

## Goal
- Complete the remaining M3 item: validate `CAMPAIGN_OPS` loop through sectors `1-3` in `Main3D`.

## Run Setup
- Client: `client-3dimentions/src/Main3D.java`
- Mode: `CAMPAIGN_OPS`
- Map: `medium`
- Seed(s): `10101`, `20202`
- Random events: enabled (campaign authored rules handle S1-S3 determinism behavior)

## Pass Criteria
- No crashes, soft-locks, or stuck transitions.
- Sector flow reaches `S1 -> S2 -> S3 -> S4 start` at least once.
- Objective progression and transition summary/HUD remain coherent.
- Target lock, waypoint, and overlay controls remain responsive throughout run.

## Execution Checklist

### Run A (`seed=10101`)
- [ ] Launch `Main3D`.
- [ ] Start campaign and clear sectors 1-3.
- [ ] Confirm entry into sector 4.
- [ ] Record `S1/S2/S3` clear times (rounded seconds).
- [ ] Record credits after each sector transition.
- [ ] Record any anomalies.

### Run B (`seed=20202`)
- [ ] Launch `Main3D`.
- [ ] Start campaign and clear sectors 1-3.
- [ ] Confirm entry into sector 4.
- [ ] Record `S1/S2/S3` clear times (rounded seconds).
- [ ] Record credits after each sector transition.
- [ ] Record any anomalies.

## Result Template

### Run A
- Seed:
- Reached Sector 4: `YES/NO`
- S1 clear time:
- S2 clear time:
- S3 clear time:
- Credits after S1:
- Credits after S2:
- Credits after S3:
- Issues:

### Run B
- Seed:
- Reached Sector 4: `YES/NO`
- S1 clear time:
- S2 clear time:
- S3 clear time:
- Credits after S1:
- Credits after S2:
- Credits after S3:
- Issues:

## Decision
- [ ] `PASS`: Both runs reach sector 4 and no blocker issues found.
- [ ] `FAIL`: Any crash/soft-lock/progression break found.

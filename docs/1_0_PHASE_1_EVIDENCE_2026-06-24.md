# Phase 1 Evidence — 2026-06-24

Phase 1 closes the release-safety and player-clarity contract.

## Mission briefings

- Added one authoritative `TacticalMissionBriefing` model.
- Briefings include objective, exact success/failure conditions, protected assets,
  quota, timer, optional objective/reward, enemy strength, uncertainty, and a
  recommended first action.
- Briefings render on tactical entry and can be reopened from the mission action
  bay.
- Regression coverage iterates all 24 authored sectors and checks required fields,
  protected-asset names, quotas, timers, and reopening.

## Strike contract and origin

- Added `StrikeAvailabilityBrief` for torpedo, carrier-sortie, and atomic strikes.
- The preflight contract exposes inventory, named resource costs, capacity cost,
  required intel, estimated effect, retaliation risk, exact blocker, and recovery.
- Recovery text identifies Strike Rearm hubs, caches/rewards, industrial
  production, reusable carrier deck slots, and expendable packages.
- Rejected/canceled launches remain free; successful launches deduct once.
- Tactical torpedoes and atomic devices now enter from the launching fleet vector
  at a safe stand-off instead of spawning 220–360 units from the target.
- Strategic origins, tactical stand-off, resource invariants, purchase/rearm, and
  checkpoint persistence are covered by tests.

## Contacts and navigation

- One actionable-contact rule now governs HUD markers, sensor pulses, selection,
  navigation, and strike locks.
- Lost-contact windows scale by intel quality: 15/30/45/60/75 seconds from
  Unknown through Target-Quality.
- Expired contacts leave the normal HUD and clear bound selections.
- Last-known markers use memory coordinates and never advertise live movement.
- Existing destination selection remains separate from travel confirmation,
  supports course cancellation, expands marker hit areas, and resolves overlaps
  deterministically by distance and visual priority.

## Terminology, reputation, and fleet health

- Player-facing campaign labels now use Combat Condition, Crew Readiness, Strike
  Availability, Production Progress, Travel Speed, ETA, Green Reputation, Yellow
  Reputation, Location Aftermath, Sensor/Recon/Security Sweep, and Withdraw To
  Strategic Map.
- Fleet Ore and Yard Ore remain distinct and explained.
- Reputation panels show current values, unlocked benefits, next thresholds, and
  the latest outcome/reason.
- Fleet entries show persistent hull, armor, and shield condition, repair need,
  tactical absence, repair, and construction status. Armor condition is included
  in checkpoint identity data.

## Audio and visuals

- Missile and torpedo files are present and correctly mapped by `SfxManifest`.
- Tactical strike creation now dispatches positional missile/torpedo launch SFX.
- Audio telemetry verifies real asset variants rather than `sfx_missing`.
- Yellow hull source review found no forward-right triangular transparency damage
  in the current albedo assets; the sampled hulls have intentional transparent
  padding and intact silhouettes, so no destructive raster rewrite was required.
- Ordinary ambient encounters now force a deep-space backdrop; authored Luna and
  Earth orbital sectors retain their celestial backgrounds.
- The accessibility screenshot baseline was deliberately refreshed after the
  campaign withdrawal-label change.

## Validation

- Phase 1-focused tests: 199 passed.
- Full uncached test suite: 794 passed in 13m03s.
- `productionValidation`: passed.
- `screenshotRegression`: passed with all five targets.
- `git diff --check`: passed.

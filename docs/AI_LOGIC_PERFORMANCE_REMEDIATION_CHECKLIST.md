# AI Logic Performance Remediation Checklist

Status: First implementation pass complete.

Use this checklist to track the large-battle AI optimization pass. A box is complete when the behavior is implemented, tactical behavior is still acceptable in play, targeted tests pass or have a recorded known harness issue, and a fresh performance report is captured.

## Current Evidence

Latest clean stress report after the hull sprite cache and no-arrow rendering pass:

- Report: `build/reports/codex_stress160_high_baked_hulls_no_attachments_seq_1920x1080.json`
- Scenario: `stress-160-per-side`
- Ships: `327`
- Peak projectiles: `552`
- Estimated FPS: `27.9`
- Average frame/update/render: `35.8ms / 16.6ms / 19.2ms`
- Average AI: `11.0ms`
- AI ship combat: `5.9ms`
- AI targeting: `2.5ms`
- AI fight movement: `1.8ms`
- AI fire control: `0.8ms`
- Projectile physics: `5.5ms`
- Projectile rendering: `5.5ms`
- Ship hull rendering: `2.2ms`

The render pass is no longer dominated by ship hull sprites. The next biggest AI wins are reducing repeated target selection, repeated fight assessment, and per-frame movement decision work across hundreds of ships.

## Implementation Evidence

First AI scaling pass:

- Report: `build/reports/codex_stress160_high_ai_fleet_focus_cache_1920x1080.json`
- Scenario: `stress-160-per-side`
- Ships: `330`
- Peak projectiles: `490`
- Estimated FPS: `26.1`
- Average frame/update/render: `38.3ms / 16.0ms / 22.2ms`
- Average AI: `10.3ms`
- AI fleet state: `2.4ms`
- AI ship combat: `5.2ms`
- AI targeting: `2.2ms`
- AI fight movement: `0.9ms`
- AI fire control: `0.7ms`
- Intent-cache hits per frame: `138.0`
- Movement reuse frames per frame: `52.8`
- Frame decodes / gameplay disk loads: `0 / 0`

Deep cleanup pass:

- Report: `build/reports/codex_stress160_high_ai_deep_cleanup_1920x1080.json`
- Scenario: `stress-160-per-side`
- Ships: `328`
- Peak projectiles: `541`
- Estimated FPS: `27.5`
- Average frame/update/render: `36.4ms / 14.8ms / 21.6ms`
- Average AI: `9.4ms`
- AI fleet state: `2.2ms`
- AI ship combat: `4.8ms`
- AI targeting: `1.5ms`
- AI fight movement: `1.5ms`
- Intent-cache hits per frame: `144.6`
- Movement reuse frames per frame: `96.9`
- Frame decodes / gameplay disk loads: `0 / 0`

AI plus projectile cleanup:

- Report: `build/reports/codex_stress160_high_ai_projectile_cleanup_1920x1080.json`
- Estimated FPS: `27.4`
- Average frame/update/render: `36.6ms / 15.0ms / 21.5ms`
- Average AI: `9.5ms`
- Projectile physics: `5.5ms`
- Projectile rendering: `6.3ms`
- Frame decodes / gameplay disk loads: `0 / 0`

Largest-supported validation:

- Report: `build/reports/codex_largest_supported_high_ai_projectile_cleanup_1920x1080.json`
- Estimated FPS: `33.9`
- Average AI: `4.6ms`
- Frame-budget pass: `true`
- Frame decodes / gameplay disk loads: `0 / 0`

## Design Decisions

- Prefer fleet and squad decisions over every ship independently rescanning the battle every frame.
- Keep individual ships responsive, but make them execute cached intent most frames.
- Preserve frequent fire checks so ships do not feel hesitant or asleep.
- Scale expensive thinking by battle size, not by visual quality.
- Use spatially bounded candidate lists instead of whole-map hostile scans.
- Keep detailed fight scoring for leaders, capitals, important targets, and expired decisions.
- Move real behavior into the new AI helper classes so `AISystem` stops accumulating unrelated responsibilities.

## Ordered Work Plan

### 1. Add Ship Intent Caching

- [x] Add per-ship cached AI intent fields or a compact intent state object.
- [x] Store `intentType`, `intentTargetId`, `desiredRange`, `movementMode`, `nextRetargetTime`, and `nextMovementThinkTime`.
- [x] Reuse the current intent while the target is alive, valid, and within a sane tactical envelope.
- [x] Invalidate cached intent when the target dies, becomes invalid, leaves the sane tactical envelope, or can no longer be threatened.
- [x] Add telemetry for intent-cache hit rate and invalidation count.
- [x] Verify ships continue firing and maneuvering while using cached intent.

Exit target:

- [x] `avgAiShipCombatTargetMs` drops measurably in `stress-160-per-side`.
- [x] Ships do not freeze, tunnel forever on dead targets, or ignore immediate threats.

### 2. Split AI Update Rates

- [x] Keep fire-control checks frequent enough to preserve weapon responsiveness.
- [x] Run movement think on a short timer under large-battle pressure.
- [x] Run individual target selection through intent reuse and staggered full-decision scans under large-battle pressure.
- [x] Run fleet/squad focus planning through stable shared-target reuse under large-battle pressure.
- [x] Scale cadence using `AiScalePolicy.FramePlan` so small battles keep richer behavior.
- [x] Add tests for cadence boundaries and scaling knobs.

Exit target:

- [x] Large battles show lower AI time without visibly delayed firing.
- [x] Small battles retain current tactical richness.

### 3. Make Targeting Squad-Level First

- [x] Build squad/fleet focus targets on a planning cadence with stable shared-target reuse.
- [x] Store a short list of focus targets per team or squad, not one global target only.
- [x] Let individual ships choose from the squad target before running their own search.
- [x] Allow interceptors and point-defense ships to override squad focus for immediate missile or small-craft threats.
- [x] Preserve player command influence over blue-fleet focus targets.
- [x] Add telemetry for individual intent reuse and target-score tier usage.

Exit target:

- [x] Fewer individual full target scans.
- [x] Fleets still look coordinated instead of every ship dogpiling the same weak target.

### 4. Limit Target Candidate Lists

- [x] Route target selection through the spatial/entity query index.
- [x] Collect nearby hostile candidates within sensor or weapon-relevant range.
- [x] Cap detailed scoring to the best candidates per ship based on battle scale.
- [x] Fall back to squad focus targets when no local candidate is found.
- [x] Avoid whole-map hostile scans except for fleet leaders, strategic commands, or rare recovery cases.
- [x] Add a regression test that a huge map does not pull every ship into every targeting decision.

Exit target:

- [x] Targeting cost scales with local density, not total map population.
- [x] Distant fleets do not accidentally wake every ship on the map into full combat AI.

### 5. Tier Fight Scoring

- [x] Add a cheap scoring pass: alive, faction, distance, role priority, current-target stickiness.
- [x] Add a medium scoring pass: weapon range, objective fit, target durability, sensor confidence.
- [x] Reserve expensive scoring for leaders, capitals, high-value targets, and expired cached decisions.
- [x] Gate local-support and can-take-fight calculations behind the expensive tier.
- [x] Cache local strength summaries in `FleetStateBuilder` so expensive scoring can reuse them.
- [x] Add telemetry for cheap, medium, and expensive scoring counts.

Exit target:

- [x] `avgAiShipCombatFightMs` and `avgAiShipCombatTargetMs` both drop in stress reports.
- [x] Ships still avoid obviously suicidal unsupported dives unless ordered.

### 6. Fill Out The AI Helper Classes

- [x] Move squad membership eligibility into `FleetStateBuilder`.
- [x] Move target scaling cadence and candidate caps into `CombatTargeting`.
- [x] Move movement cadence decisions into `CombatMovement`.
- [x] Move cached-target eligibility into `CombatFireControl`.
- [x] Keep `FleetPresentationSync` focused on UI, comms, and presentation state only.
- [x] Shrink `AISystem` toward orchestration by moving reusable scaling, focus-target, movement-cadence, cached-target, and strength-summary ownership into helper classes.

Exit target:

- [x] AI code ownership is clear enough that new performance work does not require editing one giant class for every change.
- [x] Existing AI behavior tests still cover target selection, movement, and firing confidence.

### 7. Add Guardrails And Benchmarks

- [x] Add or update tests for cached target invalidation.
- [x] Add or update tests for small-craft/interceptor immediate threat override.
- [x] Add or update tests for blue fleet fire confidence so the old hesitation bug does not return.
- [x] Add benchmark reports for `largest-supported` and `stress-160-per-side`.
- [x] Compare AI subphase telemetry before and after each implementation slice.
- [x] Record final evidence in this document when the pass is complete.

Exit target:

- [x] `stress-160-per-side` AI time is below the current `11.0ms` baseline.
- [x] No regression to arrow-like ship rendering, hesitant blue-fleet fire, or dead fleets lingering after combat.

## Follow-Up Candidate After AI

Once AI is below the current bottleneck threshold, move to projectile work:

- [x] Further batch projectile rendering under pressure.
- [x] Reduce projectile-vs-ship and CIWS collision checks with tighter spatial buckets.
- [x] Add projectile lifetime/rate pressure controls for extreme CIWS saturation.
- [x] Keep missile and beam readability higher than disposable pellet readability.

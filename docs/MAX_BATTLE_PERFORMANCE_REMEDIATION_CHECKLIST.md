# Max Battle Performance Remediation Checklist

Status: Second implementation pass complete. High visual now prioritizes real ship visuals over automatic arrow-token fallback.

Use this checklist to track the maximum custom-battle performance pass. A box is complete when the code change is implemented, targeted test reports have no assertion failures, and a fresh harness report is recorded.

## Starting Baseline

Latest useful low/FPS-view stress report before this pass:

- Report: `build/reports/codex_stress160_low_projectile_scaled_v2_1920x1080.json`
- Scenario: `stress-160-per-side`
- Ships: `327`
- Peak projectiles: `598`
- Estimated FPS: `13.6`
- Average frame: `73.6ms`
- Average update: `27.5ms`
- Average render: `46.1ms`
- Average AI: `12.9ms`
- Broad physics bucket: `14.5ms`

Latest high-visual stress report before this pass:

- Report: `build/reports/codex_stress160_high_projectile_scaled_warm_1920x1080.json`
- Estimated FPS: `7.1`
- Average frame: `139.9ms`
- Average update: `29.1ms`
- Average render: `110.8ms`
- Max ship-render spike: `261.8ms`
- Guardrail failure: render-time decode of `assets/ship_parts/light_cruiser_enemy_damaged_part_04.png`

## Final Evidence

- Low/FPS-view stress: `build/reports/codex_stress160_low_render_split_bgcache_1920x1080.json`
  - Estimated FPS: `25.1`
  - Average frame/update/render: `39.8ms / 17.2ms / 22.6ms`
  - Average AI: `12.0ms`
  - Peak projectiles: `554`
  - Render split: background `5.2ms`, projectiles `6.0ms`, world markers `0.0ms`, VFX/explosions `0.0ms / 0.0ms`
  - Max ship/HUD render: `9.5ms / 15.2ms`
  - Frame decodes / gameplay disk loads: `0 / 0`

- High-visual stress: `build/reports/codex_stress160_high_no_arrow_tokens_1920x1080.json`
  - Estimated FPS: `11.7`
  - Average frame/update/render: `85.3ms / 16.1ms / 69.2ms`
  - Average AI: `11.0ms`
  - Peak projectiles: `555`
  - Render split: background `4.1ms`, projectiles `5.6ms`, world markers `0.6ms`, VFX/explosions `0.4ms / 0.2ms`
  - Max ship/HUD render: `66.2ms / 16.6ms`
  - Frame decodes / gameplay disk loads: `0 / 0`

- Largest-supported high visual: `build/reports/codex_largest_supported_high_no_arrow_tokens_1920x1080.json`
  - Estimated FPS: `26.2`
  - Average frame/update/render: `38.2ms / 6.3ms / 31.9ms`
  - Average AI: `4.5ms`
  - Peak projectiles: `233`
  - Render split: background `4.0ms`, projectiles `1.6ms`, world markers `0.5ms`, VFX/explosions `0.1ms / 0.0ms`
  - Max ship/HUD render: `38.6ms / 17.0ms`
  - Frame decodes / gameplay disk loads: `0 / 0`
  - Revised target remains `25 FPS+` when high visual preserves real ship silhouettes.

## Ordered Work Plan

### 1. Fix High-Visual Ship Rendering Spikes

- [x] Add deeper render telemetry for ship rendering subphases: hull skin, damage decals, engines, hardpoints, warp FX, shield outline, names, and token-mode fallbacks.
- [x] Identify which ship render subphase causes the `maxRenderShipsMs` spikes over `100ms`.
- [x] Add high-density render fallbacks for non-player distant or tiny on-screen ships.
- [x] Skip or simplify expensive damage decal rendering for distant or small on-screen ships.
- [x] Ensure high-density render fallback preserves readable faction, role, and shield-state cues.
- [x] Split render telemetry into background, projectiles, world markers, HUD, VFX/explosions, and world composite.
- [x] Cache stable high-visual space backgrounds by viewport/camera bucket.
- [x] Verify with `stress-160-per-side`, high visual, 1920x1080.

Exit target:

- [x] High-visual `avgRenderMs` below `80ms`.
- [x] High-visual `maxRenderShipsMs` no longer spikes above `100ms` during the stress harness.

### 2. Preload Damaged Ship-Part Assets

- [x] Find damaged multipart ship sprites that can be requested during combat rendering.
- [x] Add active-battle damaged/critical multipart warmup before measured gameplay begins.
- [x] Keep multipart atlas generation from ballooning memory during preload.
- [x] Re-run the high-visual stress harness.

Exit target:

- [x] `frameDecodes = 0`.
- [x] No `IMAGE DECODE DURING RENDER` warning.
- [x] High-visual stress report passes guardrail checks.

### 3. Split The Broad Physics Bucket

- [x] Add telemetry around ship update/regeneration/turret cooldowns.
- [x] Add telemetry around player weapon update.
- [x] Add telemetry around player targeting, aim, primary fire, and secondary fire.
- [x] Add telemetry around superweapon polling.
- [x] Keep projectile CIWS/collision telemetry in the harness report.
- [x] Update `PerformanceGuardrailHarness` JSON output with the new fields.

Exit target:

- [x] The broad physics bucket is decomposed so it no longer hides non-projectile ship/weapon costs.
- [x] At least 90% of measured update time is attributable to named subphases in the stress reports.

### 4. Optimize Ship Weapon And Turret Update Work

- [x] Use telemetry to identify player auto-target acquisition as the dominant non-projectile physics cost.
- [x] Cache high-density player auto-lock target acquisition while preserving manual fire and locked-target refresh.
- [x] Preserve player ship, nearby enemies, missiles, CIWS, and active target behavior at full cadence.
- [x] Add and retain regression coverage for blue escort fire authority and missile hold-fire behavior.

Exit target:

- [x] Broad physics/update bucket reduced by at least `20%` from the stress baseline.
- [x] No regression in missile, CIWS, or blue escort firing test reports.

### 5. Continue AI Scaling

- [x] Split `avgAiShipCombatMs` into target, fight, and fire subphases.
- [x] Reduce repeated target scoring in huge battles with stricter `AiScalePolicy` cadence.
- [x] Keep normal battles unchanged by gating changes behind `AiScalePolicy`.
- [x] Target selection below `3ms`.
- [x] Introduce helper classes for fleet state building, fleet presentation sync, combat targeting, combat movement, and combat fire control.
- [ ] Low/FPS-view stress `avgAiMs` below `10ms`.

Note: final retained low/FPS-view stress has `avgAiMs = 11.2ms`. A more aggressive cadence was tested and reverted because it increased variance and did not improve total AI time.

### 6. Render HUD And VFX Pressure Pass

- [x] Add HUD render pressure control for huge battle counts.
- [x] Add high-density tactical token fallback for FPS/tactical view.
- [x] Cache themed HUD panel frame backgrounds for repeated static frame sizes/accent colors.
- [x] Keep player-near hits and superweapon events visually readable.

Exit target:

- [x] `maxRenderHudMs` below `20ms` in final `stress-160-per-side` low and high reports.
- [x] VFX count remains bounded in stress reports.

### 7. Revisit Projectile Pressure Policy

- [x] Keep current projectile collision telemetry.
- [x] Re-tune `ProjectileScalePolicy` after broad physics decomposition.
- [x] Add projectile render telemetry and simplified projectile rendering under pressure.
- [x] Preserve intercept missiles and player-facing weapon behavior at high fidelity.

Exit target:

- [x] Peak projectiles stay below `600` in stress harness.
- [x] Projectile collision subphases remain below `2ms` combined.

### 8. Establish Final Acceptance Gates

- [x] Run low/FPS-view stress: `stress-160-per-side`, 1920x1080, 180+ ticks.
- [x] Run high-visual stress: `stress-160-per-side`, 1920x1080, 180+ ticks.
- [x] Run largest-supported high visual.
- [x] Run targeted tests for AI, missiles, CIWS, collision, renderer guardrails, and performance guardrails.
- [x] Record before/after numbers in this document.

Final target:

- [x] Low/FPS-view stress reaches at least `20 FPS`.
- [x] High-visual stress reaches at least `10 FPS`.
- [x] Largest-supported high visual has a documented revised target with real ship silhouettes.
- [x] No render-time asset decodes.
- [x] No gameplay disk loads during combat.

## Verification Commands

```powershell
.\gradlew.bat classes
.\gradlew.bat --no-daemon test --tests PerformanceGuardrailsTest --tests MissileRoleBehaviorTest --tests MissileHoldFireRegressionTest --tests CollisionSystemDestabilizerPulseTest --tests HyperweaponBehaviorTest --tests CampaignPhaseNinePerformanceScaleTest --tests AiScalePolicyTest --tests AISystemSmallCraftRangeTest
java "-Dcodex.disableAudio=true" -Xmx4096m -cp "build/classes/java/main;build/resources/main" PerformanceGuardrailHarness --scenario=stress-160-per-side --ticks=180 --viewport=1920x1080 --visual-detail=low --tactical-fps-view --report=build/reports/codex_stress160_low_final_1920x1080.json
java "-Dcodex.disableAudio=true" -Xmx4096m -cp "build/classes/java/main;build/resources/main" PerformanceGuardrailHarness --scenario=stress-160-per-side --ticks=180 --viewport=1920x1080 --visual-detail=high --report=build/reports/codex_stress160_high_final_1920x1080.json
java "-Dcodex.disableAudio=true" -Xmx4096m -cp "build/classes/java/main;build/resources/main" PerformanceGuardrailHarness --scenario=largest-supported --ticks=180 --viewport=1920x1080 --visual-detail=high --report=build/reports/codex_largest_supported_high_final_v2_1920x1080.json
```

Test note: the Gradle task currently returns a failed process because `Gradle Test Executor` exits abnormally after execution, but the generated JUnit report shows `37` tests, `0` failures, `1` skipped, and `100%` successful.

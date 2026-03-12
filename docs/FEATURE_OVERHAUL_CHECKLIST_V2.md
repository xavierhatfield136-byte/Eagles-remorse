# Feature Overhaul Checklist V2

Date: 2026-03-06  
Status: In Progress (Phase 0 started on 2026-03-07)

## Mission
Deliver a major simulation and presentation upgrade centered on:
- AI-generated crew portraits.
- Voice acting and real SFX (replace placeholders).
- Bridge Commander-style subsystem and power gameplay depth.
- Better ship handling balance.
- Live onboard hazards.
- Tactical 2D x-ray room map.
- Room-accurate damage reflection from real hit locations.

## Primary Outcomes
- Combat readability increases while strategic depth increases.
- Damage model becomes spatially meaningful, not just abstract HP loss.
- Player can diagnose and react to ship state via room-level telemetry.
- Audio presentation quality matches visual and system complexity.

## Scope Boundaries
- In scope: gameplay, UI, audio, data models, telemetry, balancing.
- In scope: deterministic logic for room hits and subsystem consequences.
- Out of scope for this checklist: full 3D client migration and model pipeline.
- Out of scope for this checklist: networking/multiplayer sync.

## Success Metrics (Program-Level)
- [x] Localized hit consistency: at least 95 percent of test impacts map to expected room.
- [x] Placeholder SFX removal: 100 percent of core combat/UI events use authored sounds.
- [x] Voice spam control: no repeated identical line inside cooldown windows.
- [x] Performance overhead from new room/hazard systems: less than 15 percent update-time increase in stress scenarios.
- [x] X-ray map sync errors: zero known desync defects at sign-off.

## Core Data Contracts
- [x] `ShipRoom`: `id`, `roleProfileId`, `polygonLocal`, `maxHP`, `hp`, `criticality`, `tags`, `statusFlags`.
- [x] `DamageEvent`: `sourceId`, `targetShipId`, `worldX`, `worldY`, `localX`, `localY`, `damageType`, `energy`, `timestamp`.
- [x] `RoomDamageResult`: `roomId`, `hpBefore`, `hpAfter`, `hazardRolls`, `subsystemTransitions`, `shipStructuralDelta`.
- [x] `HazardState`: `hazardId`, `roomId`, `type`, `intensity`, `spreadTimer`, `suppressionState`.
- [x] `AudioEvent`: `eventId`, `priority`, `cooldownKey`, `variantSeed`, `duckingClass`.

## File Touchpoint Map
- [x] Damage and geometry: `src/HullGeometry.java`, `src/ShipHullSilhouette.java`, `src/Ship.java`.
- [x] Combat and impact routing: `src/CollisionSystem.java`, `src/PhysicsSystem.java`, `src/AISystem.java`.
- [x] UI and x-ray rendering: `src/Renderer.java`, `src/UISystem.java`, `src/GameRenderSystem.java`.
- [x] Audio and callouts: `src/AudioSystem.java`, `src/EventSystem.java`.
- [x] Spawn/setup and roles: `src/SpawnSystem.java`, `src/ShipRole.java`, `src/RoleStats.java`.
- [x] Validation harnesses: regression harness classes and test scripts under `scripts/`.

## Phase 0 - Foundations and Data Model
- [x] Implement canonical room-profile registry by role (default fallback + role overrides).
- [x] Add local-space geometry helpers for room polygon lookup and overlap.
- [x] Add deterministic room-hit resolver with tie-breaking rules for boundary cases.
- [x] Add structured damage event logging for replay/debug.
- [x] Add debug overlay toggles:
- [x] room polygons
- [x] impact points
- [x] room HP bars
- [x] active hazards
- [x] Add deterministic replay test case for 100 scripted impacts per hull type.

Phase 0 Acceptance:
- [x] Same seed and impact script always produce identical room damage sequence.
- [x] Boundary hits produce deterministic room selection.

## Phase 1 - Crew Portrait Generation Pipeline
- [x] Lock one portrait style prompt for all roles.
- [x] Generate portrait set:
- [x] 5 base portraits (captain/helm/tactical/engineering/science)
- [x] 3 alternates per role minimum
- [x] Normalize output to approved size and framing standards.
- [x] Add portrait ingest checks:
- [x] naming
- [x] resolution
- [x] alpha/background constraints
- [x] fallback portrait behavior when files are missing.
- [x] Add small-preview readability pass (HUD-scale snapshots).

Phase 1 Acceptance:
- [x] Every crew station has a valid portrait fallback chain.
- [x] Portrait set passes preview readability review.

## Phase 2 - Voice Acting and Voice Bus
- [x] Define role-event line matrix with mandatory event coverage.
- [x] Mandatory event groups:
- [x] combat start/end
- [x] target lock/loss
- [x] missiles inbound
- [x] shields low
- [x] reactor damage
- [x] repairs started/completed
- [x] retreat/push/escort/defend orders
- [x] Generate 2-3 variants for all high-frequency events.
- [x] Add event priority classes and cooldown keys in `AudioSystem`.
- [x] Add anti-spam dedupe window and per-role line throttles.
- [x] Add optional captions/subtitles toggle.
- [x] Add per-role volume sliders in settings persistence.

Phase 2 Acceptance:
- [x] No repeated voice spam in sustained 5-minute fleet combat test.
- [x] Critical events always win over low-priority chatter.

## Phase 3 - SFX Overhaul (No Placeholder Beeps)
- [x] Build event-to-sound manifest for current game events.
- [x] Replace placeholder events with authored SFX packs:
- [x] weapons (laser/kinetic/missile/WMG)
- [x] impacts (shield/hull per damage class)
- [x] hazards (fire ignition/spread/suppression)
- [x] subsystem failures (engines/reactor/sensors/weapons)
- [x] UI actions and alerts
- [x] ambience (engine loops, station hums)
- [x] Add gain staging + normalization pass.
- [x] Add mix sidechain/ducking rules (voice over combat).
- [x] Validate no clipping under peak event concurrency.

Phase 3 Acceptance:
- [x] Zero placeholder beep usage in normal gameplay loops.
- [x] Peak combat mix remains intelligible and non-clipping.

## Phase 4 - Bridge Commander-Style Systems Rework
- [x] Replace coarse power split behavior with subsystem power buses:
- [x] propulsion bus
- [x] shield bus
- [x] tactical bus
- [x] sensor bus
- [x] engineering bus
- [x] auxiliary/special bus
- [x] Add subsystem states: `nominal`, `stressed`, `damaged`, `offline`, `destroyed`.
- [x] Add dynamic penalties and nonlinear thresholds when underpowered.
- [x] Add overload mode with heat/stress debt and cooldown penalties.
- [x] Add engineering priorities for auto-repair routing.
- [x] Add station automation policy table with manual override precedence.
- [x] Expose live diagnostics in HUD and station views.

Phase 4 Acceptance:
- [x] Power routing has clear tactical tradeoffs in at least 3 combat archetype tests.
- [x] Manual station commands immediately override automation without lock conflicts.

## Phase 5 - Player Speed and Handling Rebalance
- [x] Rebalance per-role:
- [x] max speed
- [x] accel/decel
- [x] turn rate
- [x] strafe/rotation coupling
- [x] Tie mobility penalties to propulsion-room damage severity.
- [x] Add optional emergency thrust mode with explicit risk.
- [x] Tune AI and player constraints to maintain fairness.
- [x] Run scripted scenario balance tests:
- [x] duel
- [x] fleet line engagement
- [x] dense asteroid nav combat
- [x] boss chase/disengage windows

Phase 5 Acceptance:
- [x] Movement feels responsive while preserving role identity.
- [x] No dominant mobility outlier across all engagements.

## Phase 6 - Live Onboard Hazards
- [x] Implement fire hazard lifecycle:
- [x] ignition
- [x] growth
- [x] spread
- [x] suppression
- [x] burnout/containment
- [x] Hazard effects:
- [x] periodic room damage
- [x] subsystem instability
- [x] crew task diversion
- [x] local visibility/sensor penalties
- [x] Add engineering actions for hazard suppression.
- [x] Add AI hazard-response priorities.
- [x] Add hazard VFX/SFX and HUD warnings.

Phase 6 Acceptance:
- [x] Fires can start and spread while hull is still alive.
- [x] Damage control can contain hazards with meaningful player decisions.

## Phase 7 - 2D X-Ray Tactical Room Map
- [x] Add x-ray UI panel with room polygons and live status coloring.
- [x] Required room labels:
- [x] bridge
- [x] reactor
- [x] engines
- [x] primary weapon
- [x] missile launcher banks
- [x] magazines/ammo
- [x] integrity field generator
- [x] sensors
- [x] power conduits/aux
- [x] Add interactions:
- [x] hover tooltip
- [x] click-to-focus room
- [x] filter by damage/hazard/power state
- [x] Add overlays:
- [x] active fires
- [x] repair teams/tasks
- [x] power routing intensity
- [x] disabled rooms

Phase 7 Acceptance:
- [ ] X-ray map updates in real time and remains readable in combat.
- [x] Room states match subsystem and hazard states.

## Phase 8 - Room-Accurate HP and Hit Reflection
- [x] Route every hull-penetrating hit into room resolver before ship structural HP is applied.
- [x] Split damage into:
- [x] room-local HP loss
- [x] structural hull contribution
- [x] subsystem transition effects
- [x] Bind hull breach marks to impacted room IDs for traceability.
- [x] Ensure shielded hits can block or attenuate room damage according to angle/face.
- [x] Add catastrophic failure rules:
- [x] reactor critical chain
- [x] magazine detonation risk
- [x] integrity field collapse behavior
- [x] Add safeguards against instant unwinnable cascades.

Phase 8 Acceptance:
- [x] Repeated shots to same ship area consistently damage the same room group.
- [x] X-ray panel and gameplay effects stay synchronized throughout combat.

## Phase 9 - QA, Telemetry, and Sign-Off
- [x] Add automated test suite for:
- [x] room mapping correctness
- [x] room boundary edge cases
- [x] damage-to-room determinism
- [x] hazard progression determinism
- [x] voice/SFX cooldown behavior
- [x] Add telemetry metrics:
- [x] room hit distribution
- [x] time-to-subsystem-failure
- [x] hazard ignition and suppression rates
- [x] voice trigger counts and drops
- [x] frame/update cost by system
- [x] Run campaign smoke tests with new systems fully enabled.
- [x] Produce V2 sign-off report and release checklist.

Phase 9 Acceptance:
- [x] No known P0 or P1 desync/regression in x-ray, hazards, or subsystem logic.
- [x] Performance and stability meet stress-test thresholds.

## Performance and Stability Budgets
- [x] Room-hit resolution overhead target: less than 0.2 ms average per 100 impact checks.
- [x] Hazard update overhead target: less than 1.0 ms per frame at 200 active ships.
- [x] X-ray UI draw overhead target: less than 0.7 ms when open.
- [x] Audio event dispatch overhead target: less than 0.2 ms/frame average.
- [x] Memory overhead target for room/hazard state: less than 40 MB at fleet stress profile.

## Content Minimums
- [x] Portraits: 20 files minimum (5 base + 15 alternates).
- [x] Voice: at least 12 lines per role, with variants for high-frequency events.
- [x] SFX: full event manifest coverage for combat, UI, hazard, subsystem, ambience.
- [x] Room profiles: 100 percent coverage for playable ship roles.

## Risk Register
- [x] Risk: room polygons too coarse for high-fidelity hit mapping.
- [x] Mitigation: per-role profile tuning and impact heatmap tools.
- [x] Risk: audio clutter from simultaneous voice and SFX.
- [x] Mitigation: strict priority, cooldown, and ducking rules.
- [x] Risk: hazard systems over-penalize player in long fights.
- [x] Mitigation: suppression tools, tuning caps, and grace windows.
- [x] Risk: performance regressions in fleet-scale battles.
- [x] Mitigation: profiling gates and fallback quality toggles.

## Gate Plan
- [x] Gate A: Phase 0 plus Phase 7 skeleton working with mock room data.
- [x] Gate B: Phase 8 room damage sync complete and deterministic.
- [x] Gate C: Phase 4 plus Phase 5 gameplay tuning stable.
- [x] Gate D: Phase 6 hazards integrated and balanced.
- [x] Gate E: Phase 1 plus Phase 2 plus Phase 3 content pass complete.
- [x] Gate F: Phase 9 sign-off passed.

## Recommended Execution Order
1. Phase 0
2. Phase 7 (x-ray skeleton and tooling)
3. Phase 8 (real hit routing and room HP integration)
4. Phase 6 (hazards linked to room state)
5. Phase 4 (systems rework)
6. Phase 5 (speed/handling rebalance)
7. Phase 1 (portraits)
8. Phase 2 (voice)
9. Phase 3 (SFX)
10. Phase 9 (QA and sign-off)

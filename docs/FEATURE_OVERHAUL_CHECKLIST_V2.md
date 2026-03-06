# Feature Overhaul Checklist V2

Date: 2026-03-06  
Status: Planning (Expanded Execution Version)

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
- [ ] Localized hit consistency: at least 95 percent of test impacts map to expected room.
- [ ] Placeholder SFX removal: 100 percent of core combat/UI events use authored sounds.
- [ ] Voice spam control: no repeated identical line inside cooldown windows.
- [ ] Performance overhead from new room/hazard systems: less than 15 percent update-time increase in stress scenarios.
- [ ] X-ray map sync errors: zero known desync defects at sign-off.

## Core Data Contracts
- [ ] `ShipRoom`: `id`, `roleProfileId`, `polygonLocal`, `maxHP`, `hp`, `criticality`, `tags`, `statusFlags`.
- [ ] `DamageEvent`: `sourceId`, `targetShipId`, `worldX`, `worldY`, `localX`, `localY`, `damageType`, `energy`, `timestamp`.
- [ ] `RoomDamageResult`: `roomId`, `hpBefore`, `hpAfter`, `hazardRolls`, `subsystemTransitions`, `shipStructuralDelta`.
- [ ] `HazardState`: `hazardId`, `roomId`, `type`, `intensity`, `spreadTimer`, `suppressionState`.
- [ ] `AudioEvent`: `eventId`, `priority`, `cooldownKey`, `variantSeed`, `duckingClass`.

## File Touchpoint Map
- [ ] Damage and geometry: `src/HullGeometry.java`, `src/ShipHullSilhouette.java`, `src/Ship.java`.
- [ ] Combat and impact routing: `src/CollisionSystem.java`, `src/PhysicsSystem.java`, `src/AISystem.java`.
- [ ] UI and x-ray rendering: `src/Renderer.java`, `src/UISystem.java`, `src/GameRenderSystem.java`.
- [ ] Audio and callouts: `src/AudioSystem.java`, `src/EventSystem.java`.
- [ ] Spawn/setup and roles: `src/SpawnSystem.java`, `src/ShipRole.java`, `src/RoleStats.java`.
- [ ] Validation harnesses: regression harness classes and test scripts under `scripts/`.

## Phase 0 - Foundations and Data Model
- [ ] Implement canonical room-profile registry by role (default fallback + role overrides).
- [ ] Add local-space geometry helpers for room polygon lookup and overlap.
- [ ] Add deterministic room-hit resolver with tie-breaking rules for boundary cases.
- [ ] Add structured damage event logging for replay/debug.
- [ ] Add debug overlay toggles:
- [ ] room polygons
- [ ] impact points
- [ ] room HP bars
- [ ] active hazards
- [ ] Add deterministic replay test case for 100 scripted impacts per hull type.

Phase 0 Acceptance:
- [ ] Same seed and impact script always produce identical room damage sequence.
- [ ] Boundary hits produce deterministic room selection.

## Phase 1 - Crew Portrait Generation Pipeline
- [ ] Lock one portrait style prompt for all roles.
- [ ] Generate portrait set:
- [ ] 5 base portraits (captain/helm/tactical/engineering/science)
- [ ] 3 alternates per role minimum
- [ ] Normalize output to approved size and framing standards.
- [ ] Add portrait ingest checks:
- [ ] naming
- [ ] resolution
- [ ] alpha/background constraints
- [ ] fallback portrait behavior when files are missing.
- [ ] Add small-preview readability pass (HUD-scale snapshots).

Phase 1 Acceptance:
- [ ] Every crew station has a valid portrait fallback chain.
- [ ] Portrait set passes preview readability review.

## Phase 2 - Voice Acting and Voice Bus
- [ ] Define role-event line matrix with mandatory event coverage.
- [ ] Mandatory event groups:
- [ ] combat start/end
- [ ] target lock/loss
- [ ] missiles inbound
- [ ] shields low
- [ ] reactor damage
- [ ] repairs started/completed
- [ ] retreat/push/escort/defend orders
- [ ] Generate 2-3 variants for all high-frequency events.
- [ ] Add event priority classes and cooldown keys in `AudioSystem`.
- [ ] Add anti-spam dedupe window and per-role line throttles.
- [ ] Add optional captions/subtitles toggle.
- [ ] Add per-role volume sliders in settings persistence.

Phase 2 Acceptance:
- [ ] No repeated voice spam in sustained 5-minute fleet combat test.
- [ ] Critical events always win over low-priority chatter.

## Phase 3 - SFX Overhaul (No Placeholder Beeps)
- [ ] Build event-to-sound manifest for current game events.
- [ ] Replace placeholder events with authored SFX packs:
- [ ] weapons (laser/kinetic/missile/WMG)
- [ ] impacts (shield/hull per damage class)
- [ ] hazards (fire ignition/spread/suppression)
- [ ] subsystem failures (engines/reactor/sensors/weapons)
- [ ] UI actions and alerts
- [ ] ambience (engine loops, station hums)
- [ ] Add gain staging + normalization pass.
- [ ] Add mix sidechain/ducking rules (voice over combat).
- [ ] Validate no clipping under peak event concurrency.

Phase 3 Acceptance:
- [ ] Zero placeholder beep usage in normal gameplay loops.
- [ ] Peak combat mix remains intelligible and non-clipping.

## Phase 4 - Bridge Commander-Style Systems Rework
- [ ] Replace coarse power split behavior with subsystem power buses:
- [ ] propulsion bus
- [ ] shield bus
- [ ] tactical bus
- [ ] sensor bus
- [ ] engineering bus
- [ ] auxiliary/special bus
- [ ] Add subsystem states: `nominal`, `stressed`, `damaged`, `offline`, `destroyed`.
- [ ] Add dynamic penalties and nonlinear thresholds when underpowered.
- [ ] Add overload mode with heat/stress debt and cooldown penalties.
- [ ] Add engineering priorities for auto-repair routing.
- [ ] Add station automation policy table with manual override precedence.
- [ ] Expose live diagnostics in HUD and station views.

Phase 4 Acceptance:
- [ ] Power routing has clear tactical tradeoffs in at least 3 combat archetype tests.
- [ ] Manual station commands immediately override automation without lock conflicts.

## Phase 5 - Player Speed and Handling Rebalance
- [ ] Rebalance per-role:
- [ ] max speed
- [ ] accel/decel
- [ ] turn rate
- [ ] strafe/rotation coupling
- [ ] Tie mobility penalties to propulsion-room damage severity.
- [ ] Add optional emergency thrust mode with explicit risk.
- [ ] Tune AI and player constraints to maintain fairness.
- [ ] Run scripted scenario balance tests:
- [ ] duel
- [ ] fleet line engagement
- [ ] dense asteroid nav combat
- [ ] boss chase/disengage windows

Phase 5 Acceptance:
- [ ] Movement feels responsive while preserving role identity.
- [ ] No dominant mobility outlier across all engagements.

## Phase 6 - Live Onboard Hazards
- [ ] Implement fire hazard lifecycle:
- [ ] ignition
- [ ] growth
- [ ] spread
- [ ] suppression
- [ ] burnout/containment
- [ ] Hazard effects:
- [ ] periodic room damage
- [ ] subsystem instability
- [ ] crew task diversion
- [ ] local visibility/sensor penalties
- [ ] Add engineering actions for hazard suppression.
- [ ] Add AI hazard-response priorities.
- [ ] Add hazard VFX/SFX and HUD warnings.

Phase 6 Acceptance:
- [ ] Fires can start and spread while hull is still alive.
- [ ] Damage control can contain hazards with meaningful player decisions.

## Phase 7 - 2D X-Ray Tactical Room Map
- [ ] Add x-ray UI panel with room polygons and live status coloring.
- [ ] Required room labels:
- [ ] bridge
- [ ] reactor
- [ ] engines
- [ ] primary weapon
- [ ] missile launcher banks
- [ ] magazines/ammo
- [ ] integrity field generator
- [ ] sensors
- [ ] power conduits/aux
- [ ] Add interactions:
- [ ] hover tooltip
- [ ] click-to-focus room
- [ ] filter by damage/hazard/power state
- [ ] Add overlays:
- [ ] active fires
- [ ] repair teams/tasks
- [ ] power routing intensity
- [ ] disabled rooms

Phase 7 Acceptance:
- [ ] X-ray map updates in real time and remains readable in combat.
- [ ] Room states match subsystem and hazard states.

## Phase 8 - Room-Accurate HP and Hit Reflection
- [ ] Route every hull-penetrating hit into room resolver before ship structural HP is applied.
- [ ] Split damage into:
- [ ] room-local HP loss
- [ ] structural hull contribution
- [ ] subsystem transition effects
- [ ] Bind hull breach marks to impacted room IDs for traceability.
- [ ] Ensure shielded hits can block or attenuate room damage according to angle/face.
- [ ] Add catastrophic failure rules:
- [ ] reactor critical chain
- [ ] magazine detonation risk
- [ ] integrity field collapse behavior
- [ ] Add safeguards against instant unwinnable cascades.

Phase 8 Acceptance:
- [ ] Repeated shots to same ship area consistently damage the same room group.
- [ ] X-ray panel and gameplay effects stay synchronized throughout combat.

## Phase 9 - QA, Telemetry, and Sign-Off
- [ ] Add automated test suite for:
- [ ] room mapping correctness
- [ ] room boundary edge cases
- [ ] damage-to-room determinism
- [ ] hazard progression determinism
- [ ] voice/SFX cooldown behavior
- [ ] Add telemetry metrics:
- [ ] room hit distribution
- [ ] time-to-subsystem-failure
- [ ] hazard ignition and suppression rates
- [ ] voice trigger counts and drops
- [ ] frame/update cost by system
- [ ] Run campaign smoke tests with new systems fully enabled.
- [ ] Produce V2 sign-off report and release checklist.

Phase 9 Acceptance:
- [ ] No known P0 or P1 desync/regression in x-ray, hazards, or subsystem logic.
- [ ] Performance and stability meet stress-test thresholds.

## Performance and Stability Budgets
- [ ] Room-hit resolution overhead target: less than 0.2 ms average per 100 impact checks.
- [ ] Hazard update overhead target: less than 1.0 ms per frame at 200 active ships.
- [ ] X-ray UI draw overhead target: less than 0.7 ms when open.
- [ ] Audio event dispatch overhead target: less than 0.2 ms/frame average.
- [ ] Memory overhead target for room/hazard state: less than 40 MB at fleet stress profile.

## Content Minimums
- [ ] Portraits: 20 files minimum (5 base + 15 alternates).
- [ ] Voice: at least 12 lines per role, with variants for high-frequency events.
- [ ] SFX: full event manifest coverage for combat, UI, hazard, subsystem, ambience.
- [ ] Room profiles: 100 percent coverage for playable ship roles.

## Risk Register
- [ ] Risk: room polygons too coarse for high-fidelity hit mapping.
- [ ] Mitigation: per-role profile tuning and impact heatmap tools.
- [ ] Risk: audio clutter from simultaneous voice and SFX.
- [ ] Mitigation: strict priority, cooldown, and ducking rules.
- [ ] Risk: hazard systems over-penalize player in long fights.
- [ ] Mitigation: suppression tools, tuning caps, and grace windows.
- [ ] Risk: performance regressions in fleet-scale battles.
- [ ] Mitigation: profiling gates and fallback quality toggles.

## Gate Plan
- [ ] Gate A: Phase 0 plus Phase 7 skeleton working with mock room data.
- [ ] Gate B: Phase 8 room damage sync complete and deterministic.
- [ ] Gate C: Phase 4 plus Phase 5 gameplay tuning stable.
- [ ] Gate D: Phase 6 hazards integrated and balanced.
- [ ] Gate E: Phase 1 plus Phase 2 plus Phase 3 content pass complete.
- [ ] Gate F: Phase 9 sign-off passed.

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


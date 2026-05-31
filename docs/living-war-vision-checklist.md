# Living War Vision Implementation Checklist

This document translates your campaign vision into concrete implementation work.

## Vision Summary

The overworld should feel like an active war theater where:
- Red, Green, and Yellow fleets move with independent goals.
- NPC fleets detect, track, avoid, reinforce, and fight each other without player input.
- No hostile fleet appears from nowhere: all arrivals have source, warning, and map intel.
- The player is a major actor inside the war, not the sole cause of events.
- Battle outcomes reshape routes, safety, economy, and faction pressure over time.

## Definition Of Done (Global)

- [x] No static idle hostiles near start; opening wave follows authored movement lanes.
- [x] Every NPC fleet has faction, mission, route, intent, and fallback behavior.
- [x] NPC-vs-NPC battles occur and resolve without requiring player presence.
- [x] Enemy reinforcements are announced before arrival with location intel.
- [x] Nearby major battles notify player and allow intervention.
- [x] Only one player-facing encounter prompt can exist at a time.
- [x] After player selects manual battle, campaign simulation cannot auto-resolve other player-relevant encounters before tactical entry.
- [x] Regional control, danger, and trade health react to battle outcomes.
- [x] Save/load preserves fleets, battles, pending reinforcements, and war state.

## Minimum Viable Living War

- [x] Red patrol and Green patrol move independently on authored routes.
- [x] Red and Green patrols detect each other and choose engage/avoid behavior.
- [x] Stronger fleets pressure weaker fleets; weaker fleets retreat/regroup when appropriate.
- [x] Offscreen clashes can auto-resolve and produce visible consequences.
- [x] Nearby clashes can generate player intervention/report options.
- [x] Yellow convoy runs between two stations and reroutes when danger spikes.
- [x] No static Red hostiles linger around the starting base after opening flow.

## Phase 1: Campaign Safety Foundations (Now)

### 1.0 Time Scale, Lockout, and Alert Safety
- [x] Add global campaign time scale used by all campaign updates.
- [x] `FREE_ROAM` runs at `1.0x`.
- [x] `LOCATION_MENU` runs at `0.1x` or `0.05x`.
- [x] `ENCOUNTER_DECISION` and tactical entry run at `0.0x`.
- [x] Prevent new encounter processing while encounter panel is open.
- [x] Prevent campaign auto-resolve once player commits to manual battle.
- [x] Add location threat alarms before hostile fleets enter engagement range.

### 1.1 Faction Interaction Rules
- [x] Implement/verify faction hostility matrix for campaign simulation.
- [x] Red vs Green: hostile.
- [x] Red vs Yellow: suspicious -> hostile under pressure.
- [x] Green vs Yellow: neutral/friendly.
- [x] Player alignment reflects campaign diplomacy settings.

### 1.2 Fleet State Completeness
- [x] Every `CampaignForce` has role metadata.
- [x] `faction`, `mission`, `intent`, `state`, `sourceLocationId`, `destinationLocationId`.
- [x] Add per-force cooldowns for engagements and alert spam control.
- [x] Enforce non-idle fallback route assignment.

### 1.3 Opening Flow Hardening
- [x] Keep opening Red wave on authored lanes (no player-shadowing).
- [x] Enforce start grace window before first force-contact prompt.
- [x] Ensure opening fleets are weak and staggered.
- [x] Remove static red markers around friendly start locations after opening mission clear.

### 1.4 Early Debug Overlay
- [x] Toggle all fleet icons visible.
- [x] Show fleet mission/state/intent over icons.
- [x] Show current target line and route lane.
- [x] Show detection and engagement radius.
- [x] Show campaign time scale and encounter lock state.

## Phase 2: Living Fleet Conflict Loop

### 2.1 NPC Detection Between Fleets
- [x] Add NPC fleet detection checks (range, stealth, state).
- [x] Cache/refresh known hostile contacts per NPC fleet.
- [x] Prevent omniscient behavior (degrade stale contacts).

### 2.2 Engagement Decision Logic
- [x] Add power-based decision outcomes per contact.
- [x] engage, shadow, avoid, regroup, retreat, reinforce-call.
- [x] Add faction-specific bias.
- [x] Red more aggressive.
- [x] Green defense/escort biased.
- [x] Yellow risk-averse unless escorted/contracted.

### 2.3 Battle Resolution v1
- [x] Resolve offscreen NPC-vs-NPC clashes with attrition model.
- [x] Apply winner/loser losses based on strength ratio and closeness.
- [x] Support retreat/destruction outcomes.
- [x] Apply post-battle cooldowns and force-state transitions.
- [x] Track readiness, morale, and supply/fuel pressure for resolve decisions.
- [x] Low-readiness fleets prefer retreat over fight-to-destruction.

## Phase 3: Reinforcement and Spawn Authenticity

### 3.1 Pending Reinforcement Pipeline
- [x] All new hostile response fleets enter through pending queue.
- [x] Store source, target, doctrine, ETA, threat.
- [x] Surface staged warnings (early/mid/final).

### 3.2 Map Intel and Warnings
- [x] Add source + target map pings for incoming forces.
- [x] Add anti-spam alert cooldown/state memory.
- [x] Add "contact quality" labels for incoming threats.

### 3.3 Remove Legacy Random Spawns
- [x] Audit random/legacy direct hostile spawn paths.
- [x] Route each path through:
- [x] existing local force split, or pending reinforcement queue, or scripted event.

## Phase 4: CampaignBattle System

### 4.1 Persistent Battle Objects
- [x] Create `CampaignBattle` model.
- [x] participants, location, stage, elapsed, duration, importance, playerAwareness.
- [x] Replace instant-touch resolution for priority engagements.

### 4.2 Battle Stages
- [x] Implement staged progression.
- [x] `FORMING` -> `SKIRMISHING` -> `DECISIVE` -> `RETREATING/RESOLVED`.
- [x] Support reinforcement joins while battle is active.

### 4.3 Player Intervention
- [x] Nearby/important battles create intervention prompt.
- [x] Player options.
- [x] join side, ignore, strike support, observe.
- [x] If ignored, battle resolves naturally and reports outcome.

## Phase 5: Faction Directors (Strategic AI)

### 5.1 Red Director
- [x] Assign scouts, raids, blockades, siege pushes, reinforcement timing.
- [x] Prioritize weak Green nodes and exposed Yellow routes.

### 5.2 Green Director
- [x] Assign defense patrols, convoy escorts, counterattacks, relief fleets.
- [x] Prioritize threatened hubs and ally rescue calls.

### 5.3 Yellow Director
- [x] Route trade convoys by safety/profit.
- [x] Request escorts/hire mercenaries under danger.
- [x] Reroute/stand down in severe war zones.
- [x] Avoid large battles by default.
- [x] Fight mainly for self-defense, contract, or no-escape conditions.
- [x] Adjust Yellow market/stance based on convoy harassment and escort outcomes.

## Phase 6: Regional War State and Consequences

### 6.1 Region Heat Model
- [x] Track per-region:
- [x] redPresence, greenPresence, yellowActivity, danger, tradeHealth, control.
- [x] Update values from battles, convoy losses, and reinforcements.

### 6.2 Consequence System
- [x] Convoy loss affects local prices/supply.
- [x] Patrol losses increase route risk.
- [x] Siege pressure degrades installations.
- [x] Control shifts alter spawn pressure and mission opportunities.

### 6.3 Report Layer
- [x] Add concise war reports in UI.
- [x] "Green patrol broke Red raiders near X".
- [x] "Yellow convoy lost on Y lane".
- [x] "Red pressure increasing in Z theater".

### 6.4 Post-Battle Recovery
- [x] Winners choose follow-up: continue mission, salvage, pursue, or withdraw.
- [x] Damaged fleets retreat to nearest friendly repair point.
- [x] Destroyed fleets can leave salvage/wreck markers in player-known space.
- [x] Region danger/control updates after cleanup step is applied.

## Phase 7: Performance, Save/Load, Tooling

### 7.1 Multi-Tier Simulation
- [x] Full sim near player.
- [x] Simplified sim mid-distance.
- [x] Abstract tick for far war zones.

### 7.2 Persistence
- [x] Save/load:
- [x] campaign forces (full state).
- [x] campaign battles.
- [x] pending reinforcements.
- [x] region heat and faction director state.

### 7.3 Debug and Validation
- [x] Debug overlay for:
- [x] force mission/intent, battle stage, reinforcement ETA, regional heat.
- [x] Add tests:
- [x] opening wave route integrity.
- [x] no-direct-random-hostile spawn invariant.
- [x] reinforcement warning-before-arrival.
- [x] NPC-vs-NPC resolution stability.

## Rollout Checklist (Execution Order)

- [x] Sprint A: Campaign safety foundations (Phase 1).
- [x] Sprint B: Fleet identity + movement conflict loop (Phase 2).
- [x] Sprint C: Spawn authenticity + reinforcement pipeline (Phase 3).
- [x] Sprint D: CampaignBattle persistent staging/intervention (Phase 4).
- [x] Sprint E: Faction directors (Phase 5).
- [x] Sprint F: Regional consequences + post-battle recovery (Phase 6).
- [x] Sprint G: Performance, persistence, and validation (Phase 7).

## Acceptance Scenarios

- [x] Player waits at base for 5+ minutes: war still evolves on map.
- [x] Red fleet enters theater: warning + source shown before arrival.
- [x] Green and Red patrols clash offscreen: later report appears.
- [x] Yellow convoy threatened: reroute or escort behavior triggers.
- [x] Major battle near player: intervention option appears.
- [x] Encounter prompt lock prevents overlapping player-facing decisions.
- [x] No battle chains auto-resolve after player chooses manual control.

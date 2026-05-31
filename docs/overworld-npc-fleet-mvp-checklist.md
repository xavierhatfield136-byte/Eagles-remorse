# Overworld NPC Fleet MVP Checklist

Use this as the implementation tracker for first playable fleet movement on the campaign map.

## 1. Core Data Models

- [x] Create `CampaignFleet` with: `id`, `faction`, `position`, `target`, `mission`, `state`, `ships`, `speed`, `visibility`.
- [x] Create `FleetMission` enum: `PATROL`, `RECON`, `INTERCEPT`, `CONVOY`.
- [x] Create `FleetState` enum: `IDLE`, `MOVING`, `SEARCHING`, `PURSUING`, `ENGAGING`, `RETREATING`, `DESTROYED`.
- [x] Create minimal `CampaignBase` model: `id`, `faction`, `position`, `launchCapacity`.

## 2. Basic Fleet Movement

- [x] Add `update(deltaTime)` on fleet.
- [x] Add move-toward-target logic.
- [x] Add arrival threshold and state transition on arrival.
- [x] Verify movement is frame-rate independent.

## 3. Patrol Routes

- [x] Add patrol waypoint list to fleet.
- [x] Add route index and loop behavior.
- [x] Transition `MOVING -> SEARCHING -> MOVING` at each waypoint.
- [x] Verify patrol fleets loop indefinitely without errors.

## 4. Base-Driven Spawning (No Random Pop-Ins)

- [x] Add fleet templates: `Scout`, `Patrol`, `Interceptor`, `Convoy`.
- [x] Add timed dispatch from bases.
- [x] Assign mission and destination at launch.
- [x] Ensure every spawned fleet records `homeBase`.

## 5. Detection and Fog of War (Simple)

- [x] Add player sensor range.
- [x] Add fleet `stealthRating` (or detectability value).
- [x] Toggle `visibleToPlayer` with a simple range rule.
- [x] Render unknown contacts when partially detected.

## 6. Last Known Position AI Memory

- [x] Create `PlayerContact` with: `x`, `y`, `confidence`, `timeSinceSeen`, `searchRadius`.
- [x] On detection, update enemy contact memory.
- [x] Add confidence decay over time.
- [x] Make intercept/search fleets move to last known location, not live player position.

## 7. Encounter Trigger and Battle Handoff

- [x] Define `encounterRange` rule.
- [x] Trigger battle when fleets enter encounter range.
- [x] Pass fleet composition into battle setup.
- [x] Add minimal pre-battle panel: `Engage`, `Escape`.

## 8. Debug Visualization

- [x] Draw fleet icons by faction.
- [x] Show route lines and target points.
- [x] Show mission/state labels.
- [x] Add debug toggle: show all fleets (ignore fog of war).

## 9. Persistence (Do Early)

- [x] Serialize fleets, bases, missions, states, routes, and contact memory.
- [x] Load and resume without resetting routes/states.
- [x] Validate no duplicate fleet spawns after load.

## 10. Performance Guardrails

- [x] Set campaign AI update tick (target: 4-10 Hz).
- [x] Cap max active NPC fleets for MVP.
- [x] Skip expensive checks for distant fleets.
- [x] Add lightweight profiling logs.

## 11. MVP Acceptance Criteria

- [x] Fleets only originate from bases/templates.
- [x] Patrol/recon/intercept/convoy behaviors are visible in game.
- [x] Enemy loses exact player position after contact decay.
- [x] Overworld encounter reliably transitions to battle.
- [x] Save/load preserves campaign state correctly.

## Suggested First Milestone

- [x] Reach a playable loop with only four fleet types: Red Scout, Red Patrol, Red Interceptor, Green/Yellow Convoy.

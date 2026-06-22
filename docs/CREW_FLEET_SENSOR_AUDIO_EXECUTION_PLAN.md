# Crew, Fleet, Sensor, And Audio Execution Plan

Date: 2026-06-21
Status: Complete

## Core Rules

- Do not generate or implement new internal room sprites.
- Use the existing internal module and room system.
- Focus implementation on crew movement, internal view usability, fleet behavior, sensor readability, and audio cleanup.
- Do not replace existing module logic unless a localized fix requires it.
- Prefer clear systems layered onto the current implementation.

## Current Codebase Read

The project already contains a substantial strategic campaign implementation:

- `CampaignSystem` owns persistent campaign forces, Green/Yellow/Red fleet missions, NPC battles, battle circles, contact state, last-known positions, and route/intercept state.
- `GameRenderSystem` owns the top-right sensor net overlay and clickable sensor entries.
- `Renderer` owns strategic map panels, roster rows, and internal room rendering.
- `AudioSystem` owns one-shot SFX, warp loop handling, cooldowns, and world SFX distance gating.
- `Ship`, `ShipRoom`, and `ShipRoomLayout` already provide the internal module/room model that crew work must use.

This plan therefore treats the pasted checklist as a regression/usability pass over existing systems, not a greenfield rewrite.

## Phase 1: Broken Or Annoying Systems

### 1. Ghost Enemy Fleet Lines

Goal: enemy intercept lines should appear only for real, active hostile fleets that are actually intercepting the player.

- [x] Audit route/intercept line drawing on campaign map.
- [x] Validate fleet references before drawing player intercept lines.
- [x] Require non-null, active, non-destroyed, simulated, hostile fleet with finite map position.
- [x] Require an actual intercept/pursuit order, not stale, predicted, or last-known contact state.
- [x] Ensure stale, destroyed, merged, or invalid fleets are removed from route/intercept warning state.
- [x] Keep visual distinction: confirmed intercepts use solid red; last-known/predicted contacts use faint markers only.

### 2. NPC Warp And Jump Audio Spam

Goal: routine NPC jumps should not constantly play full warp audio.

- [x] Always allow full player mothership jump audio.
- [x] Suppress routine NPC jump sounds when distant or off-camera.
- [x] Allow quiet/localized NPC jump audio only when close to the camera and visible.
- [x] Keep major scripted arrivals available for intentional audio.
- [x] Add or verify cooldowns for remaining NPC jump cues.

### 3. Camera-Centered Audio

Goal: world audio should be evaluated from the camera, not the mothership.

- [x] Use camera world position as the world SFX listener.
- [x] Attenuate world SFX gain by camera distance.
- [x] Reduce local world SFX when zoomed far out.
- [x] Keep UI sounds and important alerts audible.

### 4. Nearby Contact Sensor Net

Goal: top-right sensor net should show useful nearby contacts before generic flavor/status.

- [x] Build contact rows from active campaign forces.
- [x] Include friendly, hostile, neutral, unknown, confirmed, and recent last-known contacts.
- [x] Exclude destroyed, invalid, ghost, and stale low-confidence contacts.
- [x] Sort by relevance and distance.
- [x] Show faction, ship count/strength, behavior, threat, certainty, and range.

## Phase 2: Living Campaign Validation

The existing code has Red, Green, Yellow, mission roles, AI director assignments, and NPC battle simulation. Execution should verify and tighten rather than duplicate these systems.

- [x] Red fleets patrol, raid, blockade, regroup, retreat, and intercept only with valid intel.
- [x] Green fleets patrol, escort, defend, reinforce, counter-sortie, and repair.
- [x] Yellow fleets trade, mine, flee, request help, and avoid combat unless cornered or escorted.
- [x] NPC battles auto-resolve, apply losses, and leave campaign consequences.
- [x] Combat circles appear for active battles and fade/remove after resolution.

## Phase 3: Internal Ship And Crew

Goal: expose and extend the existing internal room/module system.

- [x] Add visible `Internal View` toggle if only hotkeys currently expose it.
- [x] Represent crew as teams for the first implementation.
- [x] Give teams current module, target module, task, movement progress, status, and priority.
- [x] Render team markers in existing internal/module view.
- [x] Path teams between existing modules using existing adjacency or a simple module graph.
- [x] Automatically assign repair-capable teams to damaged and critical modules.
- [x] Add internal-view priority buttons: Auto Repair, Reactor, Engines, Weapons, Shields, Sensors, Fire Suppression, Battle Stations, Cancel Priority.

## First Execution Slice

- [x] Convert pasted checklist into this working document.
- [x] Patch sensor net to prioritize real nearby campaign contacts.
- [x] Patch world SFX listener and attenuation to follow the camera.
- [x] Run focused compile/regression tests.
- [x] Continue with intercept-line validation and internal-view/crew controls.

## Execution Notes

- 2026-06-21: Added real campaign-force contact readouts to the sensor net with certainty, faction, ship/signature estimate, behavior, threat, range, and movement direction.
- 2026-06-21: Filtered destroyed and stale low-confidence contacts out of the nearby contact list.
- 2026-06-21: Kept mission ore-patch sensor rows ahead of campaign contacts in mission-space mode so mining waypoint clicks remain reachable.
- 2026-06-21: Changed world SFX distance gating and gain attenuation to use the camera focus/cinematic focus instead of the mothership position.
- 2026-06-21: Suppressed routine NPC warp cues unless the NPC jump is close to the camera at a readable zoom; player warp audio remains fully allowed.
- 2026-06-21: Added confirmed player-intercept line filtering and cleanup for destroyed, stale, invalid, or merged campaign force references.
- 2026-06-21: Added crew teams, crew priority state, room-graph pathing, automatic firefighting/repair/disruption restoration, x-ray crew markers, an `Internal View` HUD button, and direct crew priority HUD buttons.
- 2026-06-21: Fixed late-game passive campaign catalog growth by preventing no-reuse Green counter-task spawns after the bounded late-game checklist catalog is active; existing Green forces still counter-sortie and early southern pressure can still launch a response.
- 2026-06-21: Focused regression pass passed: `CrewInternalViewSystemTest`, `CampaignNpcFleetAiTest`, `CampaignLivingWarSystemTest`, and `StrategicMapWaypointSelectionTest.fleetSensorReadoutListsMissionOrePatchAndClickSetsWaypoint`.

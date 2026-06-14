# Overworld Map, Faction AI, And Audio Implementation Checklist

Date: 2026-06-13  
Status: Completed in code and backed by focused campaign regression coverage

## Purpose

This checklist turns the current overworld feedback into buildable work.

The goal is to make the galaxy map easier to read, make Green and Yellow faction fleets act with the same deliberate intent expected from Red fleets, make battles change the campaign state, and reduce audio noise so the map communicates priority instead of clutter.

## Completion Rules

- A checklist item is not done until the player-facing map and the underlying simulation agree.
- UI cleanup does not count if the same low-value or noisy state is still being produced underneath.
- AI movement does not count as complete unless each ship has a clear source, mission, target, and terminal state.
- Battles do not count as complete unless they leave persistent strategic consequences.
- Audio additions must follow priority rules and must not create constant map chatter.
- New debug output should help prove intent during development, but it must stay out of the default player view.

## Phase 1: Map Clarity And Camera Discipline

### 1.1 Zoom Limits

- [x] Add a maximum zoom-out limit for the galaxy map.
- [x] Tune the maximum zoom so labels and route markers remain readable.
- [x] Keep the default camera biased around the player fleet.
- [x] Add camera focus targets for player fleet, selected destination, active route, and nearest major threat.
- [x] Preserve a deliberate strategic overview mode if full-map awareness is still needed.

### 1.2 Marker Priority

- [x] Define marker priority tiers: critical, relevant, contextual, and hidden.
- [x] Always show player fleet, selected destination, active route, and immediate threats.
- [x] Show major faction strongholds and active battlefronts at far zoom.
- [x] Show nearby nodes, route lines, contested areas, and known fleets at mid zoom.
- [x] Show hangars, shipyards, local patrols, local events, and minor labels only at close zoom or on selection.
- [x] Replace clusters of low-priority markers with regional summaries.
- [x] Hide inactive or noncritical labels until hover, selection, or filter activation.

### 1.3 Map Filters

- [x] Add or refine filters for faction control.
- [x] Add or refine filters for fleets.
- [x] Add or refine filters for trade routes.
- [x] Add or refine filters for threats.
- [x] Add or refine filters for shipyards and hangars.
- [x] Add or refine filters for battles and contested routes.
- [x] Ensure filters reveal useful context without making the default view noisy.

## Phase 2: Left Panel Mission Intelligence

### 2.1 Replace Low-Value Status Clutter

- [x] Remove generic status feed lines that do not affect the next player decision.
- [x] Move verbose details into hover text, an archive, or optional log view.
- [x] Reserve the left panel for decision-critical mission intelligence.
- [x] Ensure the left panel can be scanned in under five seconds.

### 2.2 Required Readouts

- [x] Show current objective.
- [x] Show current fleet condition.
- [x] Show selected destination summary.
- [x] Show immediate threat summary.
- [x] Show nearby faction activity summary.
- [x] Show recent strategic changes.
- [x] Show one recommended operational focus when the player is idle or uncertain.

### 2.3 Event Feed Discipline

- [x] Show only strategic events that changed risk, ownership, route state, faction pressure, or player options.
- [x] Collapse repeated minor events into summaries.
- [x] Add a "what changed" line after major battles or ownership shifts.
- [x] Ensure event text is short, operational, and not decorative chatter.

## Phase 3: Right Panel Action Cleanup

### 3.1 Selected Object Actions

- [x] Keep the right panel focused on selected object details and available commands.
- [x] Remove duplicate information already shown in the left panel or on the map.
- [x] Group actions into navigation, route planning, fleet actions, local services, and strategic intel.
- [x] Prioritize the recommended command visually.
- [x] De-emphasize or disable irrelevant commands with reason strings.

### 3.2 Preview Before Commitment

- [x] Show route danger before travel commitment.
- [x] Show route cost before travel commitment.
- [x] Show battle or interception risk when relevant.
- [x] Show why docking, approach, strike, or support actions are blocked.
- [x] Ensure the player can understand consequences without reading a wall of text.

## Phase 4: Faction Origin Points

### 4.1 Node Roles

- [x] Define map node roles for hangar, shipyard, trade hub, fortress, repair port, listening post, and relay.
- [x] Assign each origin point to a faction.
- [x] Define which ship roles each origin point can launch.
- [x] Add spawn cooldowns per origin.
- [x] Add production capacity, readiness, or local supply values where needed.
- [x] Prevent ships from appearing away from valid origins unless an explicit special event allows it.

### 4.2 Origin-Based Spawning

- [x] Make hangars launch small patrols, escorts, and interceptors.
- [x] Make shipyards launch warships, reinforcements, and repair groups.
- [x] Make trade hubs launch convoys, couriers, and logistics ships.
- [x] Make fortresses launch defense fleets and blockade forces.
- [x] Make listening posts launch scouts and sensor sweeps.
- [x] Add a concise launch event for important ships.

## Phase 5: AI Mission System

### 5.1 Mission Assignment

- [x] Replace random wandering behavior with assigned missions.
- [x] Give every spawned ship a source, mission, destination, priority, and reason.
- [x] Add mission completion, failure, reassignment, and retreat logic.
- [x] Add terminal states: engaged, destroyed, docked, retreated, merged, captured objective, or expired by explicit event.
- [x] Prevent idle ships from sitting on the map without a recoverable reason.

### 5.2 Mission Types

- [x] Implement patrol missions to protect route loops or borders.
- [x] Implement escort missions for trade convoys and vulnerable groups.
- [x] Implement intercept missions against known enemy fleets or player incursions.
- [x] Implement reinforce missions from shipyards to threatened friendly nodes.
- [x] Implement raid missions against enemy logistics or weak outposts.
- [x] Implement repair and retreat missions for damaged fleets.
- [x] Implement capture missions for contested or undefended nodes.
- [x] Implement blockade missions that raise route danger.
- [x] Implement recon missions that update faction knowledge.
- [x] Implement trade convoy missions that can be protected, disrupted, or rerouted.

### 5.3 Faction Personality

- [x] Make Red prefer aggression, pursuit, conquest, and blockades.
- [x] Make Green prefer defense, repair, reinforcement, route stabilization, and rescue.
- [x] Make Yellow prefer trade, escorts, neutrality, opportunistic defense, and risk avoidance.
- [x] Ensure faction differences are visible through behavior, not just text labels.
- [x] Add anti-spam limits so factions do not flood the map with ships.

## Phase 6: Faction Strategy Layer

### 6.1 Strategic Evaluation

- [x] Add a periodic faction strategy tick.
- [x] Score owned nodes under threat.
- [x] Score enemies near valuable locations.
- [x] Score disrupted trade routes.
- [x] Score weak enemy nodes nearby.
- [x] Score damaged friendly stations.
- [x] Score player proximity and player route risk.
- [x] Score available ships, origins, cooldowns, and production capacity.
- [x] Penalize missions that are too distant, redundant, or unsupported.

### 6.2 Mission Selection

- [x] Choose missions from the highest-value strategic needs.
- [x] Avoid assigning multiple fleets to the same low-value task unless doctrine supports it.
- [x] Allow urgent threats to redirect or recall fleets.
- [x] Make faction decisions legible through concise event summaries.
- [x] Add a debug view showing faction intent, score, source, target, ETA, and reason.

## Phase 7: Battle Simulation

### 7.1 Battle Detection And State

- [x] Detect when opposing fleets contest the same route or node.
- [x] Resolve battles using fleet strength, composition, faction doctrine, node defenses, and reinforcements.
- [x] Add battle states: skirmish, active battle, siege, retreat, and aftermath.
- [x] Make battles last long enough to be noticed by the player.
- [x] Show only strategically important battles as major map events.
- [x] Keep small combat interactions summarized unless they affect the player or campaign state.

### 7.2 Battle Consequences

- [x] Allow battles to damage stations.
- [x] Allow battles to change node ownership or control pressure.
- [x] Allow battles to increase route danger.
- [x] Allow battles to reduce local trade traffic.
- [x] Allow battles to disable or slow ship spawning temporarily.
- [x] Allow battles to create distress, salvage, refugee, or rescue events.
- [x] Allow battles to shift faction strength by theater.
- [x] Allow battles to change player route safety.
- [x] Add battle results to the left panel event feed.

## Phase 8: Persistence And Save Compatibility

### 8.1 Campaign State Persistence

- [x] Persist node ownership changes.
- [x] Persist station damage.
- [x] Persist route danger changes.
- [x] Persist faction pressure by theater.
- [x] Persist destroyed, retreating, and docked fleet states.
- [x] Persist spawned mission ships.
- [x] Persist origin cooldowns and production/readiness values.
- [x] Ensure campaign state survives save/load.

### 8.2 Legacy Save Handling

- [x] Add clear defaults for older saves without origin roles.
- [x] Add clear defaults for older saves without faction pressure values.
- [x] Add clear defaults for older saves without mission fleet metadata.
- [x] Add regression coverage for loading an older campaign state.

## Phase 9: Audio Priority Pass

### 9.1 Reduce Noise

- [x] Reduce constant ambient clutter on the galaxy map.
- [x] Add a calmer base map ambience.
- [x] Ensure routine map movement does not produce constant chatter.
- [x] Ensure repeated minor events are rate-limited or summarized.
- [x] Add audio priority rules so important alerts are not drowned out.

### 9.2 Informational Audio

- [x] Add subtle route-selection sounds.
- [x] Add warning audio for dangerous route selection.
- [x] Add stingers for battle start.
- [x] Add stingers for battle end.
- [x] Add stingers for node control changes.
- [x] Add stingers for major fleet launches.
- [x] Add stingers for the player entering immediate danger.
- [x] Add distant battle audio only when the battle is nearby, selected, or strategically relevant.

### 9.3 Faction Audio Identity

- [x] Add restrained Red audio motifs for military pressure and pursuit.
- [x] Add restrained Green audio motifs for rescue, defense, repair, and stabilization.
- [x] Add restrained Yellow audio motifs for trade, docking, convoy, and negotiation.
- [x] Keep faction motifs short enough to support information instead of competing with it.
- [x] Add volume sliders for music, ambience, UI, alerts, and combat/map events if they do not already exist.

## Phase 10: Player Communication

### 10.1 Fleet And Battle Readability

- [x] Add hover details for AI fleet origin.
- [x] Add hover details for AI fleet mission.
- [x] Add hover details for AI fleet destination.
- [x] Add hover details for AI fleet faction.
- [x] Add hover details for AI fleet threat level.
- [x] Add route danger explanations.
- [x] Add battle outcome summaries.
- [x] Add strategic change summaries after major faction actions.

### 10.2 Debug And QA Tools

- [x] Add a debug overlay for AI mission reasoning.
- [x] Add a debug overlay for marker visibility priority.
- [x] Add a debug overlay for battle state and consequence resolution.
- [x] Add logging for faction decisions and battle consequences.
- [x] Add a deterministic fast-forward control for faction simulation testing.

## Phase 11: Testing And Tuning

### 11.1 Readability Tests

- [x] Test map readability at far, mid, and close zoom.
- [x] Test camera behavior around player movement.
- [x] Test marker clutter with many active AI ships.
- [x] Test that objective, threat, and route can be identified within a few seconds.
- [x] Test that the left panel provides useful decision cues at all times.

### 11.2 Simulation Tests

- [x] Test Green AI mission behavior over a long campaign simulation.
- [x] Test Yellow AI mission behavior over a long campaign simulation.
- [x] Test Red AI behavior against Green and Yellow.
- [x] Test origin-based spawning from hangars, shipyards, trade hubs, fortresses, and listening posts.
- [x] Test battle outcomes across repeated simulations.
- [x] Test save/load persistence for active missions, battles, damaged stations, and route danger.

### 11.3 Tuning Pass

- [x] Tune spawn rates.
- [x] Tune mission frequency.
- [x] Tune battle duration.
- [x] Tune battle consequences.
- [x] Tune marker visibility thresholds.
- [x] Tune audio frequency and priority.
- [x] Record at least one manual playthrough of the updated overworld route-planning experience.

## First Acceptance Scenario

- [x] Start the campaign and open the overworld galaxy map.
- [x] The camera opens around the player fleet and selected objective instead of exposing the entire noisy map.
- [x] The left panel shows objective, fleet condition, nearby threat, faction activity, and recent strategic changes.
- [x] Minor markers remain hidden until zoom, hover, selection, or filter activation.
- [x] A Green fleet launches from a named origin with a defensive or support mission.
- [x] A Yellow fleet launches from a named origin with a trade, escort, or avoidance mission.
- [x] A Red fleet contests Green or Yellow activity and creates a battle state.
- [x] The battle changes route danger, ownership pressure, station damage, trade state, or faction pressure.
- [x] The player receives concise visual and audio feedback for only the important changes.

## Validation

- [x] Focused map readability tests pass.
- [x] Focused faction AI mission tests pass.
- [x] Focused battle consequence tests pass.
- [x] Focused save/load persistence tests pass.
- [x] Focused audio priority tests pass.
- [x] Manual overworld readability playtest recorded.

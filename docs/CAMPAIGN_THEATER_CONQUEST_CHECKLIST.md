# Campaign Theater Conquest Checklist

Date: 2026-05-29  
Status: Implementation pass completed (v1 theater-war playable)
Source: `CAMPAIGN_THEATER_CONQUEST_PLAN.md`

## 0) Foundation and Scope Lock

- [x] Confirm final theater names and boundaries:
  - Southern Theater
  - Frontier Theater
  - Lunar Theater
  - Earth Theater
- [x] Lock faction relationship rules (`Blue + Green` allied vs `Red + Yellow`; Yellow support is coerced, not voluntary alliance).
- [x] Lock win condition: reach Earth and disable/destroy Red strategic AI core.
- [x] Define non-goals for phase 1 (no full RTS micromanagement, no manual command of all allied fleets).
- [x] Create a campaign-theater tuning config file for easy balance iteration.

## 1) Data Model

- [x] Add `Theater` model with:
  - id, name, bounds, controlScore, controlState, supplyState, threatPressure
- [x] Add `StrategicNode` model with:
  - id, type, theaterId, owner, contestState, effectValues
- [x] Add `TaskForce` model with:
  - id, faction, role, strength, position, targetNode, behaviorState, supplyStatus
- [x] Add `WarTickState` model with:
  - tickTime, theaterSnapshots, activeContests, recentEvents
- [x] Add serialization/checkpoint persistence for all new theater entities.
- [x] Add migration/defaulting so old saves do not crash when theater state is missing.

## 2) Overmap Theater Layer

- [x] Add hard theater regions to the strategic map.
- [x] Render theater boundaries and labels cleanly at map scale.
- [x] Add theater selection/focus behavior in UI.
- [x] Add theater status panel with:
  - control state
  - control score
  - supply condition
  - threat pressure
- [x] Add color/readability pass for controlled vs contested vs lost states.

## 3) Node System

- [x] Implement node ownership states:
  - Blue/Green controlled
  - Contested
  - Red controlled
- [x] Implement node types for first playable:
  - logistics hub
  - relay
  - shipyard
- [x] Hook node effects into simulation:
  - logistics -> supply throughput
  - relay -> intel quality/contact certainty
  - shipyard -> repair/reinforcement tempo
- [x] Add node capture/loss event banners and theater log entries.
- [x] Add basic anti-flip rules (cooldown or control inertia) to prevent rapid ownership thrashing.

## 4) Autonomous Task Force AI

- [x] Spawn initial Green and Red task forces per theater.
- [x] Implement task force roles:
  - patrol
  - convoy
  - strike
  - repair/rearm
- [x] Implement baseline behavior states:
  - choose objective
  - move to objective
  - react to threat
  - request support
  - retreat/rearm
- [x] Add route selection between nodes/lanes.
- [x] Add strength/damage thresholds for retreat and regroup.
- [x] Add basic reinforcement logic from controlled shipyard nodes.

## 5) Strategic Tick Simulation

- [x] Add periodic war tick update loop.
- [x] Update task-force movement each tick.
- [x] Resolve node contests each tick.
- [x] Resolve supply pressure shifts each tick.
- [x] Resolve interception opportunity generation each tick.
- [x] Update theater control scores from:
  - node state
  - fleet presence
  - convoy outcomes
  - strike outcomes
- [x] Record tick events into campaign event feed for debugging and player transparency.

## 6) Player Strategic Operations

- [x] Implement operation type: convoy defense.
- [x] Implement operation type: command strike.
- [x] Implement operation type: blockade break.
- [x] Ensure operation outcomes directly modify theater control/supply/threat.
- [x] Add clear pre-operation briefing (why it matters to theater state).
- [x] Add clear post-operation debrief (what changed and why).

## 7) Faction and War-State Behavior

- [x] Implement Green under-pressure behavior (can hold, struggles to push alone).
- [x] Implement Red escalation behavior by theater depth (stronger northward pressure).
- [x] Implement Blue strategic leverage model (high-impact, limited-presence interventions).
- [x] Add theater collapse/recovery thresholds.
- [x] Add allied-support scaling based on theater stability and player actions.

## 8) Earth Endgame Gating

- [x] Define minimum theater stabilization condition for Earth phase unlock.
- [x] Gate final Earth assault until stabilization condition is met.
- [x] Add UI messaging for unmet gate conditions.
- [x] Add final operation chain:
  - approach Earth
  - breach defensive command network
  - disable/destroy Red AI core
- [x] Apply global Red collapse state after core kill.

## 9) UX, Readability, and Feedback

- [x] Add map legend for theater, node, and task-force icons.
- [x] Add hover cards for nodes and moving groups (owner, role, status, strategic value).
- [x] Add theater timeline/event feed for recent control changes.
- [x] Add concise HUD hints that explain cause/effect of war-state shifts.
- [x] Add optional "war map simplification" toggle for lower information density.

## 10) Save/Load and Reliability

- [x] Save/load theater ownership correctly across checkpoints.
- [x] Save/load moving task-force states correctly.
- [x] Save/load active contests and operation timers correctly.
- [x] Add fail-safe cleanup for invalid task-force routes after load.
- [x] Add deterministic tick replay harness for debugging desyncs.

## 11) Balancing Pass

- [x] Tune theater control-score gain/loss rates.
- [x] Tune node effect magnitudes and stacking behavior.
- [x] Tune AI aggression and retreat thresholds by theater.
- [x] Tune convoy frequency and payoff impact.
- [x] Tune player operation impact so player influence is decisive but not absolute.
- [x] Validate that Green loses slowly without player support but can recover with strong interventions.

## 12) Testing and Regression

- [x] Add unit tests for theater score and control-state transitions.
- [x] Add unit tests for node effect application/removal.
- [x] Add unit tests for task-force behavior transitions.
- [x] Add integration tests for war tick stability over long runs.
- [x] Add integration tests for operation outcome impact.
- [x] Add save/load regression tests for theater campaign state.
- [x] Add campaign progression tests for Earth gate unlock logic.
- [x] Add performance regression tests for high-contact theater updates.

## 13) Launch Criteria for "First Playable Theater War"

- [x] Four hard theaters visible and readable.
- [x] Moving Green and Red task forces active in all theaters.
- [x] Node control and contesting visibly affects theater status.
- [x] At least three player operations available and meaningful.
- [x] Theater control changes over time without player action.
- [x] Player action can visibly reverse or accelerate theater outcomes.
- [x] Earth phase is gated by strategic progress and can be unlocked.
- [x] System is stable across save/load and long campaign sessions.

## Suggested Execution Order

1. Foundation and Data Model  
2. Overmap Theater Layer  
3. Node System  
4. Task Force AI  
5. Strategic Tick  
6. Player Operations  
7. Endgame Gate  
8. Balance + Tests

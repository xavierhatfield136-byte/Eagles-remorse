# Campaign Theater Conquest Plan

Date: 2026-05-29  
Status: Implemented baseline plan (v1 theater-war layer)

## Purpose

Define the next campaign evolution from an open-world objective map into a living naval theater with:

- four hard strategic theaters
- allied and enemy autonomous fleet movement
- node control and territorial pressure
- a Blue + Green vs Red + Yellow war arc ending at Earth

## Vision Summary

The campaign becomes a dynamic theater war where:

- `Blue` (player) is the expedition spearhead
- `Green` (ally AI) is defending but losing ground
- `Red` (enemy AI) is entrenched and coordinated
- `Yellow` (coerced bloc) is aligned with Red by pressure, contributes unwilling logistics and traffic, and can be forced into temporary support

The player does not micromanage every unit. Instead, the player executes decisive operations that shift theater control, stabilize allies, and open the path to Earth.

## Strategic Theater Layout

The overmap is divided into four hard theaters:

1. `Southern Theater`
2. `Frontier Theater`
3. `Lunar Theater`
4. `Earth Theater`

Each theater has:

- `Control Score`: Blue+Green influence vs Red influence
- `Strategic Nodes`: critical locations with persistent effects
- `Supply State`: fuel/ammo/supplies flow and readiness
- `Threat Pressure`: enemy ability to contest movement and hold space

## Faction War Model

### Blue (Player)

- high-agency strike force
- limited presence but highest impact operations
- chooses where to intervene for maximum strategic shift

### Green (Allied AI)

- distributed defenders
- can hold space locally but struggles to reclaim theaters without help
- benefits from player stabilization of nodes and logistics

### Red (Enemy AI)

- strongest command cohesion in upper theaters
- expands pressure through patrols, interdiction, and node denial
- attempts to isolate Green and slow Blue's progression

### Yellow (Coerced Against Blue)

- politically and logistically tied to Red control networks
- does not willingly align with Blue
- can be forced to open trade or transit corridors when Blue builds enough leverage

## Win Condition

Campaign victory requires:

1. Blue reaches Earth
2. Blue disables or destroys the primary Red strategic AI core
3. Red command network collapses across the war map

## Conquest and Control Rules

Theaters shift over time using a continuous strategic simulation. Control does not change from one mission alone.

Control shifts are influenced by:

- node capture/loss
- convoy survival/attrition
- fleet presence in key lanes
- strike success on command assets
- sustained pressure from AI task forces

Control state categories:

- `Blue/Green Controlled`
- `Contested`
- `Red Controlled`

## Strategic Node System

Each theater includes nodes with distinct mechanical effects.

### Shipyard Node

- increases build/repair tempo
- improves reinforcement turnaround
- enables faster recovery for owning faction

### Relay Node

- improves intel quality and contact certainty
- increases detection and target tracking reliability

### Logistics Hub Node

- improves fuel/supply/ammo recovery and throughput
- reduces operational penalties from long transit

### Defense Anchor Node

- improves local defensive response
- reduces hostile incursion success

### Resource Field Node

- increases economic throughput
- supports sustained war effort and replacement capacity

## Autonomous Fleet AI (Living Theater)

Green and Red field autonomous `Task Forces` that move and act continuously.

Task force roles:

- `Patrol Group`
- `Convoy Group`
- `Strike Group`
- `Repair/Rearm Group`

Each task force runs a behavior loop:

1. evaluate theater state
2. pick objective
3. plot route
4. move and react to contact risk
5. request support or reinforcement when pressured
6. retreat/rearm when threshold damage is reached

## Strategic Tick Simulation

Run a periodic campaign strategic tick (for example every few seconds) that updates:

- task force movement
- objective progress
- contested node state
- supply pressure
- interception opportunities
- control score drift

This creates a constantly moving war picture without requiring real-time micromanagement.

## Player Agency Model

Player impact should be high but focused.

Player's primary campaign actions:

- break blockades
- protect allied convoys
- assassinate enemy command groups
- retake critical nodes
- reinforce collapsing theaters at decisive moments

Design rule:

- the player influences theater outcomes
- the player does not directly command every allied fleet

## Narrative Framing

Campaign story context:

- Green is currently losing to Red pressure
- Blue arrives as a mobile high-impact coalition spearhead
- each theater operation is part of a broader push to Earth
- final objective is disabling the central Red AI war command

This keeps mission outcomes tied to visible strategic progress.

## Minimum Viable Implementation (First Playable)

### Milestone 1: Theater Boundaries and UI

- add four hard theater regions to the overmap
- add theater ownership panel and control-score readout
- show control state (`Controlled`, `Contested`, `Lost`) for each theater

### Milestone 2: Basic Moving AI Forces

- spawn initial Green and Red task forces per theater
- implement route movement between nodes and lanes
- expose simple contact markers for moving groups

### Milestone 3: Node Control

- implement at least three node types initially:
  - logistics hub
  - relay
  - shipyard
- support node control states and contest transitions
- apply node effects to theater-level supply/intel

### Milestone 4: Strategic Tick Resolution

- add periodic strategic tick update loop
- resolve AI contest outcomes and pressure changes
- update theater control score from node and fleet events

### Milestone 5: Player Strategic Operations

- implement 2-3 high-impact operation hooks:
  - convoy defense
  - command strike
  - blockade break
- operation outcomes directly modify theater control and supply state

### Milestone 6: Earth Gate

- gate final Earth phase behind minimum theater stabilization condition
- require player strategic progress before final AI-core assault

## Design Guardrails

- preserve current open-world overmap as primary navigation layer
- keep one large tactical space per encounter unless intentionally redesigned
- avoid full RTS micromanagement scope
- prioritize readability of theater state and consequence chains
- ensure allied AI is useful but not self-solving without player intervention

## Immediate Next Step

Draft implementation spec artifacts:

1. data model: `Theater`, `StrategicNode`, `TaskForce`, `WarTickState`
2. AI behavior state machine and transition rules
3. phase-by-phase engineering checklist mapped to current campaign systems

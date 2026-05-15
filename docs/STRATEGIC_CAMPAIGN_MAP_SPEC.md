# Strategic Campaign Map Spec

Date: 2026-05-08  
Status: Active source of truth

## Purpose

This document is the current design spec for the new campaign layer.

This document should be treated as the primary design reference for the campaign layer. When older campaign documents conflict with this file, this file takes priority.

It replaces the old split between:

- `OUTDATED_CAMPAIGN_AND_UI_OVERHAUL_PLAN.md`
- `OUTDATED_CAMPAIGN_24_SECTOR_OUTLINE.md`
- `OUTDATED_HIGHFLEET_STRATEGIC_LAYER_DIRECTION.md`

Those documents remain useful as historical context, but this file is the design we are actively building against.

## Core Direction

The game is no longer being treated as one continuous combat sandbox with campaign UI layered on top.

The intended structure is now:

- `Campaign Map`: full-screen strategic navigation and campaign command layer
- `Encounter`: one large tactical combat sector loaded only when contact or mission action begins

The campaign map should feel like a large, dangerous route home:

- the player starts in the south
- Earth is to the north
- the player crosses a huge strategic chart over time
- bases, relays, resource pockets, salvage, and hostile activity are spread across the map
- travel and enemy pressure create the campaign, not only mission selection

## Experience Goals

The player should feel like they are:

- crossing hostile space toward Earth
- choosing routes instead of clicking a straight node ladder
- weighing safety, logistics, risk, and time
- using hubs as recovery and commerce anchors
- reacting to enemy patrols and search sweeps
- deciding when to dock, when to detour, and when to commit to battle

The campaign should feel less like:

- a mission menu
- a boxed-in node list
- an instant action selector
- a hidden turn-based system

## Campaign Layer Rules

The strategic campaign map is not a combat scene.

When the game is in campaign map mode:

- do not render tactical ships
- do not render projectiles
- do not render battle HUD
- do not run tactical combat simulation in the background
- do not treat the map as a playable battlefield

The campaign map exists for:

- navigation
- route planning
- intel
- travel
- docking
- logistics
- progression
- encounter setup

## Map Structure

The campaign map is a large world-space chart, larger than the visible screen.

### World layout

- the player only sees part of the map at once
- the camera pans across the world
- locations are spread organically across the chart
- progression is broadly south-to-north
- Earth and the final approach sit in the north

### Map contents

The map contains:

- 24 main progression locations
- optional areas of interest
- Green and Yellow hub locations
- shipyards
- commerce hubs
- repair anchorages
- relays
- salvage fields
- hidden caches
- distress signals
- patrol regions
- ambush zones
- high-threat approach regions near Earth

### Distribution rules

Locations should not be arranged in simple rows.

Instead:

- some locations should sit west of the main route
- some east
- some central
- some deep off-route
- some near safer corridors
- some inside dangerous choke points

The map should encourage route planning, not only “click next mission.”

## Progression Structure

The campaign still preserves the 24-mission return-to-Earth arc.

That structure now lives inside a broader strategic world map.

### Broad regional progression

#### South

- starting region
- lower threat
- more recovery opportunities
- early Green and Yellow hubs
- room to learn movement, docking, and route planning

#### Mid-map

- contested frontier
- more patrols
- more branching route choices
- stronger logistics pressure
- higher-value trade, salvage, and intel opportunities

#### North

- Earth approach
- heavier patrol density
- stronger enemy search groups
- fewer safe hubs
- more choke points
- more dangerous mission nodes

## Campaign Entities

The strategic layer uses simplified campaign entities rather than battle-scale ship simulation.

### Player fleet entity

The player fleet should have:

- world position
- heading
- speed
- movement target
- travel state
- docking state

The player is represented on the map by a fleet marker or command marker, not a rendered combat ship.

### Enemy search groups

Enemy strategic pressure should come from actual moving campaign entities.

Enemy groups should have:

- world position
- speed
- behavior state
- search radius
- detection range
- interception range
- threat level
- anchor location or patrol region

Suggested behavior states:

- `PATROLLING`
- `SEARCHING`
- `INVESTIGATING`
- `INTERCEPTING`
- `GUARDING`
- `RETURNING`

Enemy activity should feel continuous and alive rather than turn-like.

## Continuous-Time Simulation

The campaign layer should run as a continuous simulation.

Each update tick should:

- advance campaign time
- update player fleet travel
- update enemy group movement
- update detection and intel visibility
- update interception checks
- update docking eligibility
- update route pressure and threat state

Interceptions should happen because moving groups close distance and detect each other, not because a hidden turn resolves.

## Player Travel Model

Travel should be continuous, not instant node hopping.

### Travel rules

- the player selects a destination or heading target
- the fleet gradually moves toward that point over time
- the fleet can be redirected while moving
- the fleet can hold position
- the fleet can pass near locations without automatically docking
- the fleet must physically approach a hub before docking

### Controls

Current direction:

- arrow keys / WASD pan the camera
- `LMB` selects a destination
- `T` or double-click starts travel toward the selected target
- `H` holds position / stops travel
- `RMB` places a ping

The camera and the fleet are separate systems.

## Hubs And Docking

Green and Yellow hubs are now major campaign anchors rather than just labels.

### Docking rule

Hub interaction is only available when the fleet is within docking range.

The loop is:

1. travel toward hub
2. reach docking range
3. dock / enter hub state
4. access services
5. depart and resume free movement

### Green hub identity

Green hubs should feel like:

- repair
- resistance support
- military logistics
- survival infrastructure

Likely services:

- repair fleet
- refit ships
- buy supplies
- buy fuel
- buy ammunition
- build military hulls
- recruit crew
- gather intel
- accept resistance contracts

### Yellow hub identity

Yellow hubs should feel like:

- trade
- market activity
- logistics
- industrial support

Likely services:

- trade market
- sell salvage
- buy fuel
- buy supplies
- buy parts
- build logistics or civilian hulls
- cargo upgrades
- market contracts

### Current implementation direction

Placeholder menus are acceptable in the short term, but the location must still behave like a real place with real services.

## Sidebar / Campaign UI

The right-side panel must be fast to scan.

The campaign UI should prioritize clarity over information density.

### Desired structure

#### Section A: Campaign Summary

Show only:

- current location
- selected destination
- Earth progress
- travel state
- threat estimate
- enemy alert

#### Section B: Selected Location

Show:

- location name
- type
- alignment
- short description
- docking status
- danger level

#### Section C: Services / Actions

Show only relevant service buttons for the selected hub.

Examples:

- Trade
- Repair
- Buy Fuel
- Buy Supplies
- Build Ship
- Contracts
- Depart

#### Section D: Intel / Contacts

Show lower-priority situational information:

- enemy contacts
- patrol warnings
- salvage
- distress calls
- unknown activity
- route pressure

### UI readability rules

- slightly larger type
- more spacing between sections
- fewer simultaneous lines
- clearer headers
- larger action buttons
- less verbose copy

The panel should be understandable at a glance.

## Contact And Detection Model

The player should not always have perfect information.

Enemy movement can be partly hidden and surfaced through contact confidence.

Suggested visibility states:

- unknown contact
- possible patrol
- confirmed hostile
- identified task force
- lost contact

Scouting, intel, and alert pressure should influence what the player sees.

## Encounter Trigger Rules

Combat only happens inside dedicated encounters.

Encounter triggers include:

- docking at a main mission location that launches a battle
- interception by a hostile search group during travel
- entering a hostile event area
- voluntarily taking command of an encountered force

When an encounter starts:

- switch out of campaign-map mode
- build one large tactical combat sector
- spawn only the forces relevant to that encounter
- run combat
- return results to campaign state

## Encounter Rules

Each encounter should use one large tactical sector.

Do not use:

- multi-sector encounters
- chained tactical rooms
- separate combat playspaces inside one mission

One encounter should mean one readable combat arena with enough room to maneuver.

## Main Mission Structure

The 24-mission backbone remains active.

Those missions should be embedded into the overmap rather than presented as a simple linear node list.

The campaign should preserve:

- the long road home
- Blue fleet continuity
- coalition-building
- Green and Yellow hub identity
- the escalation toward Earth

The old 24-sector outline remains useful for authored mission content and pacing, but it now sits beneath the overmap structure rather than defining the campaign presentation on its own.

## Combat Feel Direction

The campaign rewrite also depends on combat becoming cleaner and more readable.

Required combat feel direction:

- reduce clutter layered over ships
- shrink projectile visuals
- slow projectile speed enough to improve anticipation
- put explosion emphasis on large impacts and ship destruction
- remove noisy ECM visuals
- replace the old warp spectacle with a subtle wormhole effect
- rebuild SFX around weight and role clarity

The strategy layer works better when the tactical layer feels deliberate, readable, and heavy.

## Special Fleet Identity

The campaign should support role-based behavior rather than every force acting the same.

### Stealth

- scouts
- hunters
- assassins
- disengagement specialists

### Strike groups

- decisive battle seekers
- heavier offensive pressure

### Carriers

- sortie projection
- long-range pressure

### Heavy fleets

- territorial control
- major threat anchors

The game should avoid becoming only about the largest capital ships; smaller specialized fleet roles should remain useful.

## Current Implemented Direction

As of this spec:

- the campaign map is a separate full-screen mode
- the strategic map is large and scrollable
- the player progresses from south to north toward Earth
- hubs and main locations are spread across a broader chart
- hub services currently exist as placeholder interactions
- fleet travel is being redesigned around continuous world-space movement
- hostile roaming and search behavior is being redesigned as continuous overmap movement
- combat remains encounter-only

This means the implementation is aligned with the campaign direction, but not every system is complete yet.

## Current Priorities

### 1. Make the strategic map readable

- keep map dominant
- reduce sidebar density
- improve button clarity
- simplify campaign text
- make important information easier to scan

### 2. Finish continuous travel feel

- tune movement speed
- tune docking radius
- improve hold and redirect behavior
- make travel feel deliberate instead of menu-driven

### 3. Make enemy movement feel alive

- add roaming search groups
- scale patrol density by region
- improve contact visibility
- create natural interception pressure

### 4. Deepen hubs

- expand placeholder services into real resource loops
- make Green and Yellow bases mechanically distinct
- connect repair, trade, shipbuilding, and contracts to campaign progression

### 5. Preserve encounter discipline

- do not run tactical simulation on the overmap
- use one large sector per encounter
- return cleanly to the campaign map after battle

## Supersession Note

Use this file as the primary campaign design reference.

The following docs are now outdated as primary specs:

- `OUTDATED_CAMPAIGN_AND_UI_OVERHAUL_PLAN.md`
- `OUTDATED_CAMPAIGN_24_SECTOR_OUTLINE.md`
- `OUTDATED_HIGHFLEET_STRATEGIC_LAYER_DIRECTION.md`

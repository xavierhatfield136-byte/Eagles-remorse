# Campaign And UI Overhaul Plan

> Outdated: this document has been superseded by `STRATEGIC_CAMPAIGN_MAP_SPEC.md` as the active campaign design reference.

Date: 2026-05-01  
Status: Outdated reference

## Goal

Bring the campaign build to a state where the player can:

- understand the mission in seconds
- read the map without guessing
- use scanners, comms, and sensors for meaningful decisions
- control a fleet without fighting the HUD
- feel that every sector and system has a gameplay purpose

This document consolidates the current campaign, UI, AI, and progression issues into one execution plan.

## Current Problems
another problem that i found was during mission 2, i sucessfully took out all the marked targets and protected the convoys until the timer ended, but then i lost because of a sector timeout. which doesnt make sense because i protected the convoy and stopped the attackers
## 1. UI style and information layout are inconsistent

- The HUD mixes different panel styles, weights, text treatments, and interaction languages.
- High-frequency combat information, campaign help text, and low-value status clutter all compete for the same space.
- The action strip occupies prime screen real estate even though it mostly teaches controls and could live in a contextual help surface.
- The sensor net panel is present but does not justify its footprint.

## 2. Mission flow is unclear

- The map usually shows only one marker at a time.
- Markers often reference internal concepts like `reserve staging` without telling the player what that means or why it matters.
- Main objectives are not spatially outlined on the map.
- Escort targets are not reliably marked.
- Mission 2 asks the player to destroy 6 marked targets that are not actually marked.
- Long objective text blobs bury the win condition, fail condition, and first move.
- Multiple simultaneous main objectives create brittle fail states where progress can be invalidated late.

## 3. Sector gameplay is too thin

- Many sectors feel empty and exist mainly as travel space or kiting space.
- Sectors do not consistently contain tactical decisions, discovery hooks, or mission-relevant interactions.
- Safe-zone transitions currently carry too much narrative and pacing weight because other sectors are under-authored.

## 4. Combat balance and AI behavior need correction

- The integrity field makes red superships too durable and pushes the player into tedious focus-fire.
- Small craft remain glued to their parent ship instead of performing forward interception, strike, escort, or pursuit roles.
- Bombers, fighters, and drones do not reliably commit to designated targets away from the carrier.
- Crew banter only firing on safe-zone load makes the battlefield feel dead during the moments when chatter matters most.

## 5. Scanner, sensor, and comms systems are underdeveloped

- Scanners mostly reveal mass objects and ore.
- Ore discovery alone is not enough to justify a scanner system.
- The sensor net tab does not provide enough value to exist in its current form.
- Comms are mostly limited to selling ore for credits and do not support fleet play, diplomacy, contracting, or tactical intel.

## 6. Campaign progression constraints are too restrictive

- Unit caps in campaign reduce player expression without providing clear strategic value.
- Green and yellow factions cannot be hired into the player's squad, limiting emergent fleet-building.
- There is no salvage/refurbish loop for anomaly wrecks even though that would support discovery and fleet growth.

## North Star Experience

At any point during a campaign mission, the player should be able to answer these questions immediately:

1. What is the main objective right now?
2. Where do I need to go?
3. What can make me lose?
4. What optional opportunities are nearby?
5. Which support systems can I use to improve the situation?

The desired feel is:

- one strong HUD style
- one clear objective pipeline
- one readable map language
- sectors filled with intention instead of empty transit
- support systems that create choices instead of dead tabs

## Design Direction

## A. Unify the HUD into one command-deck style

Adopt a single visual language built around:

- rugged naval/bridge panels
- clear hierarchy between combat-critical, mission-critical, and auxiliary information
- fewer always-on panels
- stronger use of tabs, expansion, and context-sensitive detail

Rules:

- Combat-critical data stays visible at all times.
- Mission-critical data stays visible in compact form and expands on map or hover.
- Teaching surfaces and reference controls move into the help layer.
- Low-value passive panels must either gain active gameplay value or be removed.

Primary HUD groups:

- `Combat`: vitals, target lock, ship x-ray, weapon mode, alerts
- `Mission`: compact primary objective, fail condition, current waypoint
- `Fleet`: squad orders, docked craft, reinforcement status
- `Support`: scanners, comms, anomalies, faction offers
- `Help`: controls, glossary, system explanations, contextual tips

## B. Replace text-blob objectives with a structured mission card

Every mission should use the same compact schema:

- `Main Objective:` one short action line
- `Failure Risk:` one short line
- `Next Move:` one short line
- `Optional:` zero to two side objectives

Expanded map/details view can include:

- why the mission matters
- named landmarks
- faction flavor
- reward expectations

Rules:

- Only one true main objective chain should exist at a time.
- Secondary asks should be explicitly labeled as optional.
- If the mission has a protection rule, it must be shown as a separate failure-risk line.
- Transit lock must be stated in plain language, not only as pocket jargon.

Mission 2 target format:

- `Main Objective: Destroy 6 Customs Halo strike ships`
- `Failure Risk: Keep at least 2 of 3 convoy hulls alive`
- `Next Move: Clear Reserve Staging, then move to Support Relay`
- `Optional: Investigate anomalous returns`

## C. Build a real map and marker hierarchy

The map should support multiple simultaneous markers with strong visual priority.

Marker classes:

- `Primary Objective`
- `Next Route / Transit`
- `Escort Target`
- `Protected Asset`
- `Destroy Target`
- `Optional Objective`
- `Anomaly`
- `Faction Contact`
- `Flavor Landmark`

Rules:

- Multiple markers can be active at once.
- Objective targets referenced in text must always have live markers.
- Escort missions always show the escort target.
- Destroy missions always show all required target groups.
- Protected assets always show survival-critical markers.
- Optional discoveries must never visually overpower the primary objective.

Interaction goals:

- clicking a marker centers the map on it
- clicking a sensor net entry jumps to that anomaly or contact
- the current primary marker can auto-seed the player waypoint
- route markers explain what unlocks after arrival

## D. Give sectors authored reasons to exist

Every sector should contribute at least one of the following:

- combat pressure
- positional advantage
- resource opportunity
- anomaly interaction
- faction interaction
- escort routing
- intel/scanner reveal
- salvage/refurbish opportunity

Sector authoring rules:

- no dead sectors that exist only as empty travel padding
- each campaign mission should have a visible chain of meaningful spaces
- optional sectors should trade risk for reward
- safe zones should punctuate the run, not carry all narrative delivery alone

## E. Rebalance supership durability and integrity field behavior

The current integrity field is overserving red supership survival.

Goals:

- red superships should still feel dangerous and capital-heavy
- they should not require an unreasonable full-fleet focus-fire window
- anti-capital tactics should create progress through positioning, subsystem pressure, and sustained damage

Plan:

- reduce raw effective durability added by integrity field
- add stronger falloff, directional exposure, or recharge vulnerability windows
- allow subsystem or support-craft disruption to weaken the field
- ensure player feedback clearly shows when the field is resisting or failing

Success criteria:

- a prepared mid-campaign player fleet can break a red supership without a tedious damage sponge phase
- the encounter remains threatening because of battlefield control, not raw HP inflation

## F. Rewrite small-craft behavior around roles, not tethering

Small craft should operate according to intent instead of idling beside the parent hull.

Required role behaviors:

- `Fighter`: intercept bombers, harass exposed targets, screen the mothership
- `Bomber`: commit to designated large targets, make attack runs, disengage to rearm or survive
- `Drone`: utility harassment, point defense support, local escort
- `Ferry`: repair allies, refurbish anomaly wrecks, recover damaged friendlies

Behavior rules:

- designated targets override passive orbiting
- craft may separate from the parent ship within role-appropriate leash distances
- retreat, reload, and regroup logic should be explicit
- command inputs should visibly change squad intent

## G. Trigger crew banter from events, not only zone loads

Crew banter should react to:

- objective updates
- entering combat
- losing allies
- spotting anomalies
- scanner discoveries
- comm outcomes
- escort distress
- capital ship kills

Rules:

- safe-zone arrival can still be a banter trigger, but not the primary one
- chatter should reinforce mission clarity, threat awareness, and faction character
- objective callouts should be concise enough to help rather than interrupt

## H. Expand scanners into a multi-purpose discovery system

Scanners should reveal more than ore and generic mass.

Scanner discovery categories:

- ore with quality/use tags
- anomalies
- wrecks
- hidden hostiles
- salvage caches
- distress calls
- comm buoys
- faction signatures
- route instability / hazard warnings

UI rules:

- discoveries should tell the player why they matter
- ore should be labeled by usefulness, not just existence
- scanner results should feed the sensor net and map
- high-value discoveries should create optional waypoints

## I. Rebuild sensor net as the strategic discovery tab

The sensor net should become the index for scanner-fed opportunities and contacts.

The tab should show:

- anomalies found
- wrecks available for interaction
- active faction contacts
- unidentified signatures
- mission-critical sources
- map completion / scan coverage

Interaction:

- clicking an entry focuses the map/camera
- entries should show distance, status, and reward/risk hint
- filtered views should exist for `mission`, `anomaly`, `resource`, and `contact`

If this cannot be made useful quickly, the tab should be hidden until it can.

## J. Turn comms into a fleet and faction system

Comms should support:

- hiring allied or neutral ships
- requesting tactical support
- selling ore
- buying intel
- negotiating surrender or disengagement
- receiving mission hints
- discovering faction opportunities

New campaign rule:

- green and yellow faction ships can be hired for `1.5x` their normal price
- hired ships join the player's squad if capacity systems no longer block them

Follow-up considerations:

- faction trust modifiers
- ship availability by sector
- cooldowns on hires
- refusal conditions if under fire or hostile reputation

## K. Remove campaign unit caps and shift balance elsewhere

Player unit caps should be removed from campaign.

Replacement balancing pressures:

- credits
- logistics / repair burden
- docking throughput
- command complexity
- attrition risk
- sector travel exposure

This preserves freedom while keeping fleet growth meaningful.

## L. Add the `Ferry` small craft

The ferry is a new player-usable support craft.

Core functions:

- repair allied ships with a healing beam using team-colored visuals
- interact with anomaly wrecks
- refurbish recoverable wrecks onto the player's team

Design intent:

- create a non-combat support role with real mission value
- tie anomaly exploration to fleet growth
- deepen escort, salvage, and endurance play

Behavior expectations:

- prioritize damaged allies within assigned range
- avoid overcommitting into heavy hostile zones
- pause refurbish if threatened
- show clear progress and ownership transfer feedback during wreck recovery

## Implementation Roadmap

## Phase 1. Mission readability and map truthfulness

Ship first because it removes the most confusion fastest.

Tasks:

- implement multi-marker objective support
- guarantee that every referenced target has a marker
- show escort and protected-asset markers
- rewrite mission cards into `Main Objective / Failure Risk / Next Move / Optional`
- fix Mission 2 so all 6 required strike ships are actually marked
- explain `reserve staging`, `support relay`, and similar route beats in player terms

Acceptance criteria:

- a new player can spawn into mission 2 and identify every required target within 10 seconds
- losing a mission clearly explains why
- no mission references an unmarked required target

## Phase 2. HUD cleanup and support-surface migration

Tasks:

- move action strip and control tutorial material into the bottom help tab
- compress the live objective panel
- remove or hide low-value panels that duplicate information
- standardize type scale, border treatment, panel spacing, and button language
- make sensor net a tabbed support surface instead of a floating dead panel

Acceptance criteria:

- combat screen contains only high-value live information
- help content remains accessible without cluttering combat play
- all surviving HUD panels share one coherent style language

## Phase 3. Scanner, sensor net, and comms overhaul

Tasks:

- expand scanner result types
- pipe scanner discoveries into sensor net
- add clickable sensor net entries
- add comm interactions for hire, intel, and support
- implement green/yellow faction ship hiring at `1.5x` price

Acceptance criteria:

- scanners routinely reveal things other than ore
- sensor net becomes a practical source of destinations and opportunities
- comms are used for more than ore sales

## Phase 4. Combat AI and balance pass

Tasks:

- retune integrity field
- add role-based small-craft behavior
- improve pursuit logic for fighters, bombers, and drones
- make squad commands visibly alter behavior
- add event-driven crew banter triggers

Acceptance criteria:

- small craft leave the carrier to pursue assigned roles
- red supership fights are threatening without feeling immortal
- crew chatter reinforces events during active play

## Phase 5. Sector content and campaign progression pass

Tasks:

- audit every campaign sector for gameplay purpose
- add anomaly, salvage, contact, or tactical hooks to empty sectors
- remove campaign unit caps
- rebalance progression around economy and sustainment instead

Acceptance criteria:

- every sector offers at least one meaningful interaction or decision
- campaign progression remains challenging without arbitrary fleet caps

## Phase 6. Ferry and salvage loop

Tasks:

- add ferry craft data, visuals, and AI behavior
- add anomaly wreck interaction rules
- support refurbish-to-squad flow
- surface salvage opportunities on scanners and sensor net

Acceptance criteria:

- ferry can heal allies in combat
- ferry can recover eligible wrecks under the right conditions
- recovered ships create memorable fleet-growth moments rather than free, effortless snowballing

## Dependencies And Related Docs

- `docs/CAMPAIGN_OBJECTIVE_MARKERS_AND_MISSION_READABILITY.md`
- `docs/CAMPAIGN_ESCORT_DIRECTION.md`
- `docs/COMM_AND_CAMPAIGN_NEXT_STEPS.md`
- `docs/hud pannels design.md`

This document should act as the umbrella plan. The related docs can be updated or folded in as work lands.

## Recommended Order Of Execution

1. Fix mission readability, marker truthfulness, and escort/objective map support.
2. Clean the HUD and move tutorial/reference clutter into the help surface.
3. Expand scanners, sensor net, and comms so exploration and faction play matter.
4. Retune integrity and small-craft AI so combat starts behaving like fleet combat.
5. Audit sectors and remove campaign unit caps.
6. Add ferry craft and anomaly wreck refurbishment as the capstone support loop.

## Open Design Questions

- Should hired faction ships remain permanently loyal, contract for a number of sectors, or require upkeep?
- Should ferry refurbishment work on all wrecks or only tagged anomaly wrecks?
- Should integrity field weakness come from time under fire, subsystem disablement, or proximity disruption?
- How much of scanner output should be deterministic versus randomized per run?
- Should optional objectives ever become fail conditions, or remain purely bonus content?

## Success Metrics

We should consider this overhaul successful when:

- players stop asking where to go in campaign missions
- mission text can be read at a glance
- scanners, comms, and sensor net are used because they are useful, not because they exist
- sectors feel intentionally authored instead of empty
- support craft and faction recruitment create new fleet stories
- the HUD looks like one game, not several UI experiments stacked together

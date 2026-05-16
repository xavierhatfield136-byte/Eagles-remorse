# Campaign Command Layer Overhaul Plan

Date: 2026-05-15
Status: Proposed high-effort / high-impact redesign

## Purpose

This document turns the current command-layer problems into a concrete redesign plan.

It is not a bug list and not a one-off UI polish pass.

It assumes we want to:

- keep the strategic campaign layer
- make the command HUD readable and useful under pressure
- make fleet, resources, navigation, and strikes drive actual play decisions
- make tactical entry preserve fleet continuity instead of breaking immersion

## Core Judgment

The current command layer fails for two linked reasons:

1. the interface does not present a playable command model
2. several major systems behind the interface are either incomplete, non-authoritative, or not enforced

Because of that, the player is seeing a lot of “fake depth”:

- tabs that exist but do not support real decisions
- meters that look like instruments but do not alter outcomes enough
- strike controls that appear ready but do not complete their loop
- fleet summaries that do not translate into deployable formations
- resources that display as strategic constraints but rarely constrain anything

The fix should not be “clean up the wording” or “move some labels.”

The fix should be a command-layer overhaul where the UI becomes a thin, readable surface on top of a more authoritative campaign simulation.

## Design Targets

The redesign should make these statements true:

- the player can tell what matters within 3 seconds of opening a tab
- every top-level tab supports at least one meaningful decision
- every instrument readout changes player risk, options, or outcomes
- tactical battle entry preserves command intent and fleet composition
- resources can actually run short and meaningfully shape planning
- strikes can always be understood as available, unavailable, or risky, with a visible reason
- the action strip reads like a command deck, not debug text

## Problem Inventory

### 1. Left Command Stack Is Mostly Flavor

Observed problem:

- the left-side receiver / direction finder / comms stack mostly acts like decorative fiction
- green and yellow favor are readable, but the rest of the station does not help the player decide what to do next

Why this is damaging:

- it spends prime screen space on low-value information
- it trains the player to ignore “instrument” panels
- it makes the command layer feel fake even when real systems exist underneath

### 2. Menu Framing And Text Placement Are Unreadable

Observed problem:

- text and buttons are often drawn on top of textured frame art rather than clean dark content wells
- the eye cannot distinguish structural chrome from actionable information

Why this is damaging:

- readability collapses before the player even reaches systems depth
- important actions look disabled even when they are active
- the interface feels broken instead of dense

### 3. Navigation Tab Overwhelms Instead Of Guiding

Observed problem:

- navigation presents a wall of mixed summaries, destination details, route notes, action previews, and command buttons
- it does not elevate immediate decision-critical information

Why this is damaging:

- the player cannot quickly answer “where am I, what is the risk, what happens if I move, and why should I choose this route”
- command actions blur together with descriptive lore text

### 4. Fleet Tab Does Not Provide Command

Observed problem:

- the fleet tab mostly reports readiness and roster lines
- the player cannot define battle groups, reserve elements, escort assignments, or tactical participation
- there is no authored way to split forces for multi-angle or simultaneous tasks

Why this is damaging:

- the game promises command fantasy but only offers passive observation
- tactical battles do not reflect overmap fleet intent
- the player cannot trade safety, speed, or concentration of force

### 5. Fleet Continuity Breaks On Zone Entry

Observed problem:

- ships that should accompany the player into a battle often do not appear
- campaign and tactical state do not feel authoritative to each other

Why this is damaging:

- it destroys trust in the fleet screen
- it makes fleet preparation meaningless
- it breaks the fiction of commanding a persistent formation

### 6. Action Strip Is Too Small, Soft, And Contextless

Observed problem:

- the action strip sits in a weak visual position and reads like blurred system text
- available actions are not framed as primary commands

Why this is damaging:

- the most important interaction surface looks least important
- players miss viable actions and assume systems are nonfunctional

### 7. Resources Lack Teeth

Observed problem:

- fuel, supplies, ammo, salvage, ore, and exposure do not consistently force hard choices
- it is currently too hard or impossible to actually fail from logistics neglect

Why this is damaging:

- the resources tab becomes informational theater
- route choice, posture, strikes, repair, and support stop mattering strategically

### 8. Strike Loop Does Not Complete Reliably

Observed problem:

- strikes appear to exist, but from the player perspective it is effectively impossible to launch them reliably
- the UI does not make target validity, requirements, and execution outcome legible enough

Why this is damaging:

- the strikes tab feels fraudulent
- the game loses one of its highest-value strategic fantasies

## Overhaul Strategy

This redesign should be treated as five linked workstreams, not a pile of isolated fixes.

## Workstream A: Rebuild The Information Architecture

### Goal

Turn the command layer from a text dump into four distinct stations:

- `Navigation`
- `Fleet`
- `Logistics`
- `Strikes`

Each station must answer one primary player question.

### New station responsibilities

`Navigation`

- Where am I going?
- What happens if I move now?
- What route is safest, fastest, or most profitable?
- What contact or site is demanding attention?

`Fleet`

- What force packages do I have?
- Which ships are committed, detached, escorting, repairing, or held back?
- What will enter the next battle?

`Logistics`

- What am I short on?
- What operation can I no longer safely afford?
- Which posture or route will worsen or relieve the shortfall?

`Strikes`

- What hostile can I legally strike?
- What intel quality do I need?
- What will this spend?
- What retaliation does this invite?

### High-effort implementation changes

- Replace mixed paragraph boards with fixed station layouts built around `summary`, `state`, `consequences`, and `actions`.
- Reserve the upper half of each tab for 3-5 high-priority facts only.
- Move lore/supporting flavor into secondary panels or expandable detail lines.
- Enforce a line budget per station so the top fold cannot exceed the visible content well.

### Code hotspots

- [CampaignSystem.java](C:/Users/xhatf/IdeaProjects/game/src/CampaignSystem.java)
- [Renderer.java](C:/Users/xhatf/IdeaProjects/game/src/Renderer.java)
- [UISystem.java](C:/Users/xhatf/IdeaProjects/game/src/UISystem.java)
- [UiState.java](C:/Users/xhatf/IdeaProjects/game/src/UiState.java)

## Workstream B: Redesign The Visual Hierarchy And Panel Layout

### Goal

Make the command layer readable before adding any new mechanics.

### Required visual rules

- all interactive text must render inside dark content wells, never on bright textured frame edges
- chrome and panel art must sit outside the content grid
- every major section gets a strong label, spacing rhythm, and action cluster
- disabled actions must look intentionally disabled, not visually corrupted
- the command strip must be the sharpest text block on screen

### High-effort implementation changes

- Separate decorative frame geometry from content rectangles in the renderer
- Define explicit safe text regions for each tab and station
- Introduce a UI layout token system for panel insets, section gaps, meter heights, and action rows
- Rebuild action buttons around consistent width, padding, contrast, and disabled states
- Move the action strip into a dedicated bottom command bar with large type and 1-line consequence text

### Expected outcome

The player should be able to visually parse:

- selected tab
- selected target/location
- current danger
- next valid action

without reading the full page.

## Workstream C: Make Fleet Command Real

### Goal

Turn the fleet tab from a status report into an operational planner.

### New fleet command model

The player should be able to define:

- `Flag Group`
- `Escort Group`
- `Reserve Group`
- `Strike Group`
- `Logistics Group`

For each persistent ship, the player should be able to set:

- participate in next tactical battle: `Auto`, `Commit`, `Hold Back`
- formation role: `Flag Escort`, `Screen`, `Strike`, `Reserve`, `Logistics`
- command group assignment
- detach to separate group if enough command capacity exists

### Tactical entry rules

On entering a tactical zone:

- the `Flag Group` always spawns with the flagship
- `Escort Group` ships spawn near the flagship based on role
- `Reserve Group` ships either stay off-map or arrive later through a reinforcement command
- detached groups can either:
  - enter from alternate vectors
  - remain in overmap posture to perform a simultaneous action
  - fail to join if logistics, delay, or detection conditions block them

### High-effort implementation changes

- add persistent fleet participation flags to campaign state and checkpoints
- add a command-group model instead of a single flat persistent roster
- add battle-entry spawn routing per command group
- expose “hold back” and “alternate entry” decisions in fleet UI
- support multiple group objectives in tactical mission setup

### Why this matters

This is the change that converts the campaign from “ship list attached to a map” into actual force command.

## Workstream D: Make Logistics Punishing And Legible

### Goal

Resources must be able to force bad choices.

### Required mechanical changes

Fuel

- spent on travel distance, heavy posture, detached group operations, and extraction burns
- low fuel increases route restrictions and can trap groups outside preferred engagement ranges

Supplies

- spent on repairs, fleet upkeep between tactical sectors, and support calls
- low supplies reduce combat readiness recovery and may force ships into strained state

Ammo

- spent on strikes, missile-heavy fleet packages, and certain tactical replenishment steps
- low ammo disables torpedo strikes and reduces tactical missile readiness for committed groups

Salvage

- spent as refit material for emergency repairs, module recovery, and field restoration
- should compete with selling/trading rather than just accumulate passively

Ore / Credits

- should remain macro resources, but must interact with repair, procurement, and docking choices

Exposure / Alert / Strain

- should stop being descriptive side stats and start acting as hard pressure multipliers on:
  - interception rate
  - support cost
  - strike retaliation
  - route safety
  - fleet readiness decay

### High-effort implementation changes

- add minimum operating thresholds and shortage states
- create explicit “stable / strained / short / critical” logistics states
- bind fleet posture and detached operations to real costs
- make some actions unavailable when resource state is below threshold
- add visible “why unavailable” reasons in the logistics and action strip UI

### Success condition

A cautious player should still feel pressure.
A reckless player should be able to corner themselves.

## Workstream E: Make Strikes A Complete Strategic Weapon System

### Goal

Strikes must be one of the main campaign verbs, not a decorative tab.

### Required player loop

1. detect or identify a hostile
2. improve intel until strike-eligible
3. review cost, exposure, and retaliation
4. commit strike
5. resolve visible outcome
6. suffer theater consequences

### Required strike UI states

For any selected hostile, the strike tab must clearly say:

- `No target selected`
- `Target selected but insufficient intel`
- `Target valid for sortie only`
- `Target valid for torpedo strike`
- `Target valid for atomic strike`
- `Strike blocked by ammo / fuel / exposure / cooldown / posture / distance`

### High-effort implementation changes

- unify hostile contact selection across navigation and strikes
- keep strike target lock persistent until cleared
- add explicit preflight check objects for each strike action
- present strike result as a command report with immediate world consequences
- let some strikes affect tactical setup directly:
  - reduced enemy hull count
  - delayed reinforcements
  - destroyed escorts
  - sharpened detection
  - retaliatory counter-contact

### Key principle

The strike tab should feel like authorizing an operation, not clicking a themed button.

## Receiver / Antenna / Direction Finder Redesign

These systems should stop being passive decoration and become the player’s uncertainty-management tools.

### Receiver Manual

Current problem:

- fiction-rich but decision-poor

Redesign:

- turn it into the `Intel` station inside Navigation rather than a separate decorative stack
- present only:
  - current band quality
  - number of uncertain contacts
  - best lead
  - best interceptable signal
  - sweep recommendation

### Direction Finder

Current problem:

- visually flavorful but low-value

Redesign:

- use it to show directional pressure and contact origin
- surface:
  - incoming hostile pressure arc
  - nearest active distress / anomaly / patrol vector
  - whether the selected route moves toward or away from threat concentration

### Comms / Radio

Current problem:

- mixes favor, rumor, and chatter into an unreadable scrolling cluster

Redesign:

- split into:
  - `Faction Standing`
  - `Latest Signal`
  - `Actionable Lead`
- comms lines should produce actual options:
  - support call unlocked
  - warning about patrol escalation
  - trader route identified
  - strike target sharpened

## Navigation Tab Redesign

### Replace unreadable noise with a three-block layout

`Top Block: Current Order`

- current position
- selected destination
- ETA
- route risk
- immediate blocker

`Middle Block: Selected Contact / Site`

- why this location matters
- what you can do there
- what the risk is

`Bottom Block: Commit Options`

- engage course
- dock / approach
- enter site
- set waypoint
- cancel course

### Remove from default top fold

- repeated flavor lines
- duplicated posture lines
- duplicate route notes
- tertiary context that does not change action choice

## Fleet Tab Redesign

### Replace roster dump with operational control

`Top Block: Force Packages`

- Flag Group
- Escort Group
- Reserve Group
- Detached Group A / B if available

`Middle Block: Tactical Commitment`

- ships committed to next battle
- ships held back
- expected arrival vectors
- readiness and strain by group

`Bottom Block: Fleet Orders`

- commit to battle
- hold back
- reassign to escort
- detach to reserve
- merge back into flag group

### Stretch goal

Allow detached groups to perform simultaneous strategic tasks:

- flank intercept
- shadow hostile
- screen a route
- hold logistics position

## Resources Tab Redesign

### Replace “all green bars” with a forward-risk board

`Top Block: Operational State`

- fuel state
- supply state
- ammo state
- strain state

`Middle Block: 2-jump forecast`

- what the next route will cost
- what one strike will cost
- what one repair cycle will cost

`Bottom Block: corrective actions`

- dock and resupply
- trade
- sell salvage
- change posture
- cancel detached operation

### Principle

This tab should answer “what am I about to run out of” rather than “what numbers do I have.”

## Strikes Tab Redesign

### Replace generic readiness board with operation planning

`Top Block: Selected Target`

- target name / type
- intel quality
- exposure estimate
- counterplay estimate

`Middle Block: Available Operations`

- recon sweep
- sortie strike
- torpedo strike
- atomic strike

Each operation should show:

- requirements
- cost
- expected effect
- retaliation risk

`Bottom Block: Execution`

- arm target
- confirm operation
- review latest outcome

## Tactical Entry Continuity Fixes

The following must become authoritative:

- campaign fleet composition
- commitment flags from fleet tab
- command group assignments
- escort and reserve behavior
- mission-specific reinforcement gates

### Required system changes

- persistent fleet entries must store commitment state
- tactical sector spawn must resolve from command groups, not only from a generic player-adjacent roster
- held-back ships should remain absent by design, not by bug
- committed ships should appear unless blocked by clear simulated reasons
- the UI must explain any absence:
  - delayed by route
  - withheld by order
  - damaged and unready
  - separated in detached operation

## Rollout Plan

### Phase 1: Readability And Information Hierarchy

- rebuild content wells and safe text regions
- enlarge and sharpen action strip
- reduce each tab to top-fold essentials
- remove text-on-chrome layout failures

Expected impact:

- immediate usability improvement
- lower cognitive overload

### Phase 2: Make Existing Tabs Truthful

- navigation only shows information tied to a decision
- resources reflects real shortages and forecast costs
- strikes clearly reports target validity and failure reasons
- receiver/direction-finder become actionable intel panels

Expected impact:

- players stop ignoring instrumentation
- hidden system state becomes legible

### Phase 3: Fleet Command And Tactical Continuity

- group assignment
- battle commitment controls
- held-back / committed / reserve state
- tactical spawn continuity fixes

Expected impact:

- command fantasy becomes real
- campaign-to-battle transition becomes trustworthy

### Phase 4: Strategic Logistics Pressure

- enforce resource scarcity
- make posture and detached actions expensive
- add recovery loops and failure states

Expected impact:

- routes, hubs, support, and strikes become strategically meaningful

### Phase 5: Full Strike Operations Loop

- stable target lock
- reliable strike commit pipeline
- visible strike outcome reports
- retaliation and follow-on pressure

Expected impact:

- one of the game’s headline strategic fantasies finally becomes usable

## Risks

### Risk 1: UI polish without system authority

If we only redraw the panels, the game will look cleaner but still feel fake.

Mitigation:

- require each tab redesign to ship with at least one linked system authority improvement

### Risk 2: System authority without readability

If we only deepen mechanics, the command layer becomes even harder to use.

Mitigation:

- require every new mechanic to expose one clear top-fold summary and one clear reason string

### Risk 3: Fleet command explodes tactical complexity

Multiple groups and alternate entries can overwhelm the player if dumped all at once.

Mitigation:

- gate advanced group control behind command capacity
- ship `Commit` / `Hold Back` first
- add multi-group tactical vectors after baseline continuity is stable

## Acceptance Criteria

The overhaul should not be considered done until the following are true:

- a new player can identify the next useful command from any tab in under 5 seconds
- the fleet tab can change which ships enter battle
- at least one detached or reserve fleet behavior exists and works
- fuel, supplies, and ammo can all become limiting factors in a normal run
- long-range strikes can be launched reliably when requirements are met
- the UI always explains why a strike or route action is unavailable
- entering a tactical zone preserves committed fleet continuity
- the action strip is readable at a glance on the live map

## Recommended File And System Scope

Primary likely implementation area:

- [CampaignSystem.java](C:/Users/xhatf/IdeaProjects/game/src/CampaignSystem.java)
- [Renderer.java](C:/Users/xhatf/IdeaProjects/game/src/Renderer.java)
- [UISystem.java](C:/Users/xhatf/IdeaProjects/game/src/UISystem.java)
- [UiState.java](C:/Users/xhatf/IdeaProjects/game/src/UiState.java)
- [EconomySystem.java](C:/Users/xhatf/IdeaProjects/game/src/EconomySystem.java)
- [SpawnSystem.java](C:/Users/xhatf/IdeaProjects/game/src/SpawnSystem.java)
- [AISystem.java](C:/Users/xhatf/IdeaProjects/game/src/AISystem.java)
- [TeamSystem.java](C:/Users/xhatf/IdeaProjects/game/src/TeamSystem.java)

Primary likely regression coverage area:

- [CampaignStrategicCommandHudTest.java](C:/Users/xhatf/IdeaProjects/game/test/CampaignStrategicCommandHudTest.java)
- [CampaignStrategicStrikeCounterplayTest.java](C:/Users/xhatf/IdeaProjects/game/test/CampaignStrategicStrikeCounterplayTest.java)
- [CampaignStrategicLoopIntegrationTest.java](C:/Users/xhatf/IdeaProjects/game/test/CampaignStrategicLoopIntegrationTest.java)
- [CampaignFleetHubMenuRegressionTest.java](C:/Users/xhatf/IdeaProjects/game/test/CampaignFleetHubMenuRegressionTest.java)
- [CampaignOvermapEncounterFlowTest.java](C:/Users/xhatf/IdeaProjects/game/test/CampaignOvermapEncounterFlowTest.java)
- [CampaignZoneLayoutTest.java](C:/Users/xhatf/IdeaProjects/game/test/CampaignZoneLayoutTest.java)

## Bottom Line

The command layer should be rebuilt around one principle:

every visible command surface must correspond to a real strategic decision, and every real strategic decision must have a clear, readable command surface.

That means:

- less decorative density
- more operational clarity
- stronger system authority
- real fleet control
- real logistics pressure
- real strike execution

Anything smaller will improve presentation, but it will not solve the trust problem the current campaign layer has with the player.

# Strategic Campaign Script

Date: 2026-05-15  
Status: Active campaign script, audited against current implementation

## Purpose

This document is the single broad-reference script for the campaign as it currently exists in the game.

It is meant to answer:

- what the campaign is
- what the player can do
- what the map contains
- what encounters can happen
- what each main objective location is
- what each local site can turn into
- what systemic states shape the run

This is a source-driven script based on the current implementation in `CampaignSystem`, the active campaign docs, and campaign regression tests.

It is intentionally broad and player-facing.

It is not a line-by-line internal code commentary.

## The Campaign In One Sentence

The campaign is a full-screen strategic overmap about getting home to Earth through 24 authored objective locations, optional local site pockets, moving hostile search groups, logistics strain, faction support, uncertain contacts, and a command-station HUD that lets the player travel, dock, scan, strike, and choose how to resolve the theater around them.

## Core Campaign Structure

The campaign is split into two layers:

- `Strategic Overmap`
- `Tactical Encounter`

The overmap is where the player:

- travels continuously
- chooses routes
- manages fuel, supplies, ammo, salvage, ore, intel, favor, and strain
- scans for contacts
- docks at hubs
- calls faction support
- selects posture
- launches long-range strikes
- enters local pockets
- advances toward the next main objective

The tactical layer is where the player:

- takes direct command of a single large combat sector
- fights main-mission battles
- resolves local site pockets manually
- fights hostile interceptions when choosing manual command

The campaign is explicitly not a tactical battle running in the background. The overmap is its own mode.

## Campaign Progression Spine

The strategic campaign preserves a long-form open-world return-to-Earth arc.

Broad progression:

- `Act I`
  South / escape / convoy survival / first alliances
- `Act II`
  Frontier consolidation / Green coalition / Kharon gate / Sol approach
- `Act III`
  Liberation corridor / Luna / Earth approach / final liberation

Regional progression:

- `Southern Shelter`
  Lower threat, more recovery, more early trade and learning space
- `Contested Belt`
  More branching, patrols, support opportunities, salvage, and pressure
- `Earthwarded North`
  Harder patrol nets, fewer safe lanes, more blockades, higher-stakes missions

## Campaign Modes

The campaign is entered through:

- `Campaign Ops`
  Standard campaign start or resume
- `Fleet`
  Campaign-linked fleet hub / resume flow

The campaign can also be resumed from checkpoint if a usable checkpoint exists.

## Global Campaign State

The campaign tracks, among other things:

- current sector
- current act
- Earth progress
- selected location
- current location
- docked location
- travel state
- discovered and consumed overmap sites
- hostile search groups
- campaign intel level
- enemy alert level
- strategic exposure level
- fleet strain
- branch route / branch score
- recurring-contact relationship states
- current posture
- current theater pressure state
- completed main objectives
- completed side objectives

## Core Resources And Strategic Counters

The player manages:

- `Credits`
- `Ore`
- `Fuel`
- `Supplies`
- `Ammo`
- `Salvage`
- `Intel`
- `Enemy Alert`
- `Strategic Exposure`
- `Fleet Strain`
- `Green Favor`
- `Yellow Favor`
- `Torpedo Strikes`
- `Atomic Charges`

These are not flavor counters. They affect route safety, hub use, support quality, strikes, scanning, and the general health of the run.

## Main Overmap Location Types

The campaign uses these location types:

- `MAIN_MISSION`
- `RESOURCE_ZONE`
- `SALVAGE_FIELD`
- `DISTRESS_SIGNAL`
- `ENEMY_ACTIVITY`
- `HIDDEN_CACHE`
- `STORY_EVENT`
- `REPAIR_SITE`

## Main Mission Locations

There are `24` main progression POIs on the map.

They are:

1. `Green Anchorage Pelagos`
2. `Yellow Exchange Ilex`
3. `Frontier Shipyard Carina`
4. `Broker Relay Morrow`
5. `Green Repair Port Hecate`
6. `Yellow Commerce Spine Oris`
7. `Dustline Listening Bastion`
8. `Red Corridor Breakpoint`
9. `Green Drydock Vesta`
10. `Yellow Logistics Harbor Nysa`
11. `Refinery Port Ashkel`
12. `Coalition Relay Kharon`
13. `Contract Shipworks Myr`
14. `Yellow Escort Haven Oriel`
15. `Frontier Arsenal Kharon Gate`
16. `Green Stronghold Thessa`
17. `Breakchain Recovery Ring`
18. `Resistance Foundry Aster`
19. `Luna Trade Anchorage`
20. `Earthlane Forward Bastion`
21. `Luna Perimeter Shipyard`
22. `Inner Defense Relay Crown`
23. `Earthrise Resistance Port`
24. `Earth High Orbit`

These are not all pure combat nodes. Many also act as service anchors with hub functionality.

## Seeded Optional Areas Of Interest

The base campaign map also seeds authored optional contacts such as:

- `Ghost Cache`
- `Pelagos Wreck Garden`
- `Broker Distress Pulse`
- `Ore Drift Delta`
- `Knife Sweep Arc`
- `Contract Repair Anchorage`
- `Silent Chapel Relay`
- `Luna Search Net`
- `Resistance Dead Drop`
- `Breakchain Graveyard`
- `Fuel Vein Kappa`
- `Resistance SOS`

Additional transit-discovery contacts can also be generated while traveling.

## Hubs And Services

Main POIs can expose hub services.

Hub services are:

- `Repair Fleet`
- `Trade Market`
- `Refit Ships`
- `Build Ship`
- `Buy Supplies`
- `Gather Intel`
- `Contracts`
- `Sell Salvage`
- `Buy Fuel`

Hub identity types:

- `Green`
  Military logistics, repair, refit, contracts, intel
- `Yellow`
  Trade, fuel, salvage, commerce, convoy throughput
- `Frontier`
  Mixed support under harsher conditions

Docking rule:

- the player must move into docking range before hub services are usable

Hub service effects in practice:

- `Repair`
  Restores persistent fleet condition and flagship readiness; costs credits, supplies, and some salvage
- `Trade`
  Converts salvage into credits, fuel, and supplies through market exchange
- `Refit`
  Improves persistent fleet condition using credits and salvage
- `Shipyard`
  Offers ship construction at a location-sensitive yard
- `Supply`
  Buys supplies and ammunition for future travel and combat
- `Intel`
  Reveals hostile contacts, improves campaign intel, and can lower alert pressure
- `Contracts`
  Pays an advance and injects supplies for the next operational leg
- `Salvage`
  Liquidates recovered salvage stock into credits
- `Fuel`
  Refills long-range travel reserves

## Hostile Search Groups

Enemy overmap pressure comes from moving hostile search groups.

Behavior states:

- `PATROLLING`
- `SEARCHING`
- `INVESTIGATING`
- `INTERCEPTING`
- `GUARDING`
- `RETURNING`

Doctrine classes:

- `Scout Screen`
- `Hunter-Killer`
- `Blockade Group`
- `Interdiction Group`
- `Punishment Fleet`

What search groups do:

- patrol hostile lanes
- respond to player exposure and alert
- create interception risk during travel
- appear as uncertain or partially identified contacts until the player gains enough intel

## Contact Confidence And Intel Quality

Hostile contact confidence states:

- `Unknown Contact`
- `Possible Patrol`
- `Confirmed Hostile`
- `Identified Task Force`
- `Lost Contact`

Formal intel quality states:

- `Unknown`
- `Classified`
- `Identified`
- `Tracked`
- `Target-Quality`

This matters because:

- travel decisions depend on partial information
- strike options depend on target quality
- scans and proximity improve certainty
- some contacts remain rumors or partial locks until investigated

## Campaign Reputation States

The campaign can currently read the player as:

- `Unknown Fleet`
- `Reliable Rescue Force`
- `Raider Threat`
- `Liberation Symbol`
- `Overextended Command`
- `High-Exposure Target`

These are driven by factors such as:

- favor totals
- intel
- exposure
- shortages
- main-mission progress
- strike pressure

They influence support and contact flavor, and contribute to the theater’s identity.

## Theater Pressure States

The campaign also models broader theater mood:

- `Patrol net expanding`
- `Northern blockade tightening`
- `Trade lanes unstable`
- `Supply lines weakening`
- `Hidden hostile groups active`

## Fleet Posture Modes

The player can set fleet posture directly.

Postures:

- `Silent Running`
  Lower signature, slower sweep picture, cleaner route masking
- `Combat Patrol`
  Higher readiness and deterrence, but burns stores and shows your hand
- `Rescue Priority`
  Biases toward support windows, ally trust, and survivor traffic
- `Raider Doctrine`
  Pushes for harsh contact windows and payoff at the cost of exposure
- `Logistics Conservation`
  Saves fuel and supplies, but reduces tempo and aggressive reach
- `Recon Sweep`
  Sharpens contact identification and sweep results, but risks detection

Player-facing effect summary:

- `Silent Running`: low detect / slow burn / weak sweep
- `Combat Patrol`: hard screen / higher drain / louder signature
- `Rescue Priority`: more aid leads / steadier allies / slower push
- `Raider Doctrine`: aggressive gains / hotter reprisals / higher exposure
- `Logistics Conservation`: lean drain / slow response / soft posture
- `Recon Sweep`: sharp IDs / more discoveries / easier to detect

These modes affect:

- travel speed
- interception risk
- sweep cost
- sweep range
- intel gain
- exposure drift
- logistics drain
- transit event frequency
- escalation rates

## Recurring Contacts And Relationship States

The campaign supports named recurring relationship states.

Known recurring-contact ids surfaced in site outcomes:

- `MARR`
- `VOSS`
- `ROOK`

Relationship states:

- `Unknown`
- `Helped`
- `Trusted`
- `Owed Favor`
- `Neglected`
- `Hostile`
- `Missing`
- `Destroyed`

These states can be advanced by how the player resolves sites and support traffic.

## Fleet Strain

The campaign tracks `fleet strain`.

Strain rises from:

- long travel
- casualties
- shortages
- harsh resolutions
- high-pressure routes

Strain falls from:

- hub recovery
- rescues
- stable logistics
- careful recoveries
- support success

Strain affects:

- support effectiveness
- commentary tone
- recovery tempo
- the general feel of operational wear

## Every Strategic Player Action

The strategic HUD exposes action categories:

- `Navigation`
- `Services`
- `Strikes`
- `Support`
- `Posture`
- `Site Resolution`
- `Sensors`

The visible action set can change by selected tab and context, but the player-facing action pool currently includes:

### Navigation Actions

- `Plot Course`
  Use a selected location or free-space point as the route lock
- `Engage Course`
  Start travel toward the plotted destination
- `Cancel Course`
  Hold position and stop current travel
- `Set Waypoint`
  Drop a map ping on the current selection
- `Enter Site`
  Load an enterable local contact as a tactical pocket
- `Approach / Dock`
  Move into docking range of a service-bearing location

### Sensor Action

- `Signal Sweep` / `Recon Sweep`
  Spend supplies to reveal nearby sites, sharpen intel, and expose or identify nearby hostiles

### Posture Actions

- `Silent Running`
- `Combat Patrol`
- `Rescue Priority`
- `Raider Doctrine`
- `Logistics Conservation`
- `Recon Sweep`

### Support Actions

- `Green Support`
  Spend Green favor for stores, ammo, intel, and relay support
- `Yellow Support`
  Spend Yellow favor for fuel, salvage, and credit traffic

### Strike Actions

- `Track Target`
  Refresh a visible ping on the currently selected hostile contact
- `Torpedo Strike`
  Long-range heavy strike against a target-quality hostile
- `Carrier Sortie`
  Strike against a tracked or target-quality hostile
- `Atomic Strike`
  High-risk strike against a target-quality hostile; uses confirmation first

### Site Resolution Actions

The site plan is selectable before entering an applicable local pocket.

For resource, salvage, and cache sites:

- `Fast Strip`
- `Careful Secure`
- `Mark For Allies`

For distress sites:

- `Evacuate Survivors`
- `Tow Damaged Hull`
- `Strip For Parts`

For relay/story sites:

- `Quiet Decode`
- `Ally Broadcast`
- `Jam And Destroy`

### Hub Service Actions

Visible hub buttons are rendered per location and can include:

- `Repair Fleet`
- `Trade Market`
- `Refit Ships`
- `Build Ship`
- `Buy Supplies`
- `Gather Intel`
- `Contracts`
- `Sell Salvage`
- `Buy Fuel`

## How The Player Actually Uses The Campaign

This section is the practical layer.

It describes how the player performs major campaign actions in the current game flow.

### How A Campaign Run Starts

The player starts a campaign run by:

1. choosing `Campaign Ops` from the main menu
2. beginning on the strategic overmap rather than inside a combat mission
3. receiving a starting Blue fleet, starting credits, and an initialized overmap state
4. beginning in the south with the first route north already framed by the campaign map

If the player resumes from checkpoint:

1. the game loads the saved campaign checkpoint
2. it restores sector progress, overmap location state, recurring-contact state, fleet resources, and the persistent fleet
3. it returns either to the overmap or to the fleet hub depending on the saved context

### How The Player Selects A Destination

The player can select:

- a discovered campaign location
- a hostile/support contact marker
- a free-space point on the map

The moment a location is selected:

- it becomes the current command focus
- the selected-object panel updates
- available actions are recalculated
- the action bay shows what can be done next

If the player clicks open space instead of a POI:

- the campaign stores a `free travel target`
- the route is treated as `Free Course`
- the player can still plot, engage, and waypoint that route

### How The Player Starts Travel

The normal flow is:

1. select a location or free-space point
2. use `Plot Course`
3. use `Engage Course`

When travel begins:

- the campaign stores origin, destination, ETA, risk, and cruise speed
- the docked state is cleared
- a travel banner appears showing destination, ETA, and interception risk
- the fleet begins continuous movement on the overmap

Important detail:

- if the current selection is a hostile/support contact marker instead of a map location, `Plot Course` converts that contact lock into a free-space course at the contact's current coordinates

The player can interrupt travel at any time with `Cancel Course`, which halts drift and clears the active travel state.

### How The Player Reaches A Hub And Uses Services

The player does not use hubs remotely.

The hub flow is:

1. select a hub or service-bearing mission location
2. if still outside docking range, use `Approach / Dock` or `Engage Course`
3. the fleet travels to that location
4. once the fleet arrives within docking range, the campaign sets that location as the current docked location
5. visible hub service buttons appear only after that in-range/docked state is active
6. activate a service either directly through the action registry or through the hub menu

Important rule:

- if the player is not in docking range, hub services reject the action and show a banner telling them to move closer
- there is not a separate manual dock confirmation after arrival; the docked state is established by arriving in range

### How The Player Buys, Repairs, Refits, And Builds Ships

Hub services apply to the persistent campaign fleet, not only the currently loaded tactical hulls.

Practical flow:

1. dock at a location with the needed service
2. select `Repair`, `Refit`, or `Build Ship`
3. the game checks credits and any required supporting resources
4. if the player can pay, the service resolves immediately
5. the persistent fleet entries are updated

Examples:

- `Repair` spends credits, supplies, and sometimes salvage, then restores persistent hull and shield condition
- `Refit` spends credits and salvage, then improves persistent fleet condition
- `Shipyard` spends credits, ore, and salvage, then adds a new persistent fleet entry

That means newly built ships are not temporary scene-only spawns. They are inserted directly into the persistent campaign fleet and can appear again in future fleet-hub and mission contexts.

### How The Player Scans The Theater

The player uses `Signal Sweep` or `Recon Sweep` from the visible action list.

The actual flow is:

1. the player triggers the sweep action
2. the game checks supply cost based on the current fleet posture
3. if supplies are too low, the action fails with a banner
4. if supplies are available, the sweep spends supplies
5. nearby optional sites are revealed or promoted in intel quality
6. nearby hostile search groups are revealed, tracked, or identified depending on current intel and posture
7. the game adds map pings to newly revealed objects
8. campaign intel improves, and alert/exposure shift according to posture

So the sweep is not just cosmetic:

- it reveals sites
- it improves hostile certainty
- it helps create strike windows
- it changes the strategic picture

### How The Player Calls Faction Support

The player can call:

- `Green Support`
- `Yellow Support`

The flow is:

1. the player opens the relevant command context
2. the player uses the visible support action
3. the campaign checks the matching favor pool
4. if favor is zero, the action refuses and explains why
5. if favor is available, the action spends one favor
6. the result is scaled by current reputation, recurring-contact relationship state, and fleet strain

Green support usually improves:

- supplies
- ammo
- intel

Yellow support usually improves:

- credits
- fuel
- salvage

This means support is not a flat reward dispenser. It is shaped by:

- how trusted you are
- how strained the fleet is
- what reputation the campaign currently assigns to you

### How The Player Gets A Strike Target

Long-range strikes do not start from nowhere.

The player first needs a hostile contact target lock.

That usually happens by:

1. revealing or identifying a hostile search group through proximity or sweep
2. clicking its marker on the overmap
3. letting the UI store:
   the target label, location, hostility flag, trackability, and current intel quality

Once the target is selected:

- the strikes tab can use it
- `Track Target` can place or refresh a ping on it
- the selected target stays in the strike console until another contact is selected or the contact selection is cleared
- the available strike buttons depend on the target's intel quality

### How The Player Launches A Long-Range Strike

There are three long-range strike families in the active strategic HUD:

- `Torpedo Strike`
- `Carrier Sortie`
- `Atomic Strike`

#### Torpedo Strike

Player flow:

1. select a hostile contact on the map
2. make sure the target is hostile and currently selected as the contact target
3. improve that target to `Target-Quality` intel
4. open the strikes context
5. use `Torpedo Strike`

If the target is below `Target-Quality`:

- the button is disabled or warning-gated
- the UI explains that the target intel is too low

#### Carrier Sortie

Player flow:

1. select a hostile contact
2. improve the target to at least `Tracked`
3. use `Carrier Sortie`

Sorties are less strict than torpedo and atomic strikes because they can fire on `Tracked` targets, not only `Target-Quality` targets.

#### Atomic Strike

Player flow:

1. select a hostile contact
2. improve it to `Target-Quality`
3. use `Atomic Strike`
4. the game opens a confirmation overlay instead of firing immediately
5. the player confirms or cancels
6. on confirm, the campaign launches the atomic strike at the selected contact coordinates

Atomic strikes are deliberately high-commitment.

The confirmation text explicitly warns about:

- atomic charge cost
- fuel / ammo / supply burden
- extreme exposure
- punishment response risk

### How The Player Enters A Local Site

The local-site flow is:

1. reveal or discover an enterable site
2. move into local approach range
3. select the site
4. optionally choose a `Site Plan` first after moving into valid entry range
5. use `Enter Site`
6. the game loads a compact tactical pocket for that site

If the player is too far away:

- the action stays disabled
- the UI tells the player to move within range first

### How The Player Chooses Site Resolution Before Entry

Before entering many local sites, the player can set the planned resolution mode.

Important detail:

- the current implementation only exposes and cycles site-plan options when the selected site is already within valid entry range

This does not merely flavor the encounter.

It determines:

- what reward profile the site returns with
- how much exposure the player takes
- whether favor changes
- whether route state changes
- whether recurring relationships improve or worsen
- what site scar is written onto the map afterward

So the real site-entry flow is:

1. select site
2. choose resolution mode
3. enter site
4. resolve pocket
5. return to overmap with that mode’s aftermath

### How Distress Pickups Actually Become Permanent Fleet Ships

This is one of the most important persistence loops in the campaign.

Recovered ships do not automatically join just because the player saw them.

The practical distress-recovery flow is:

1. the player enters a `Distress Signal` local encounter
2. inside the tactical pocket, the player interacts with the survivors or relief traffic
3. if the player issues the qualifying support interaction on appropriate friendly distress, relief, or lost ships, the campaign sets an internal `ambient support requested` flag
4. when the pocket ends, the selected distress resolution mode is checked

Then the mode matters:

- `Evacuate Survivors`
  can recover ships if support was successfully requested during the encounter
- `Tow Damaged Hull`
  is more recovery-friendly and can still produce a recovered hull even when direct support conditions were weaker
- `Strip For Parts`
  does not preserve those ships as future allies

When a recoverable ship qualifies:

1. the campaign scans surviving friendly local ships in the pocket
2. it looks for valid distress / relief candidates
3. it creates new persistent fleet entries for those ships
4. those entries are added to the persistent Blue fleet
5. the persistent command groups are rebalanced
6. the return summary explicitly reports ships joining the fleet

So the answer to "how do I make sure the ships I pick up stay with me?" is:

- enter the distress site
- choose a rescue-oriented resolution, especially `Evacuate Survivors` or `Tow Damaged Hull`
- actually trigger the qualifying support order on the friendly distress, relief, or lost ships inside the pocket
- finish extraction successfully

Once added, those ships are no longer temporary scene actors. They are stored as persistent fleet entries.

### How Picked-Up Ships Stay With The Player After Recovery

Recovered ships stay with the player because they are moved into the campaign’s persistent fleet roster.

That persistence chain is:

1. a ship is recovered during a distress-style outcome
2. a `PersistentFleetEntry` is created for it
3. that entry is inserted into `persistentBlueFleet`
4. the campaign reuses that persistent fleet when:
   - spawning the fleet hub
   - preparing later campaign sessions
   - saving checkpoints
   - loading checkpoints
5. the ship therefore remains part of the player’s long-term Blue fleet unless later destroyed or otherwise lost

This is the same persistence family used by:

- starting Blue ships
- shipyard-built ships
- checkpoint-resumed ships

### How The Fleet Hub Uses Persistent Ships

The fleet hub is where persistent ships become most visible.

When the player enters the fleet hub:

1. the game opens a dedicated fleet-hangar state
2. it spawns the persistent Blue fleet into that hub pocket
3. it arranges them in hangar formation
4. the player can inspect, refit, upgrade, and prepare the fleet

So if a ship was:

- part of the starting roster
- built at a shipyard
- recovered from a distress event

then it can reappear in the fleet hub because the hub respawns from the persistent fleet list.

### How The Campaign Saves Those Ships

The campaign writes persistent fleet data into checkpoints.

That means:

1. the player exits to menu or clears a mission
2. the campaign captures a checkpoint
3. the persistent fleet is serialized into that checkpoint
4. resuming the campaign restores those ships back into the persistent fleet state

So recovered ships are not just session-local. They can survive across save/load if they were properly added before the checkpoint was written.

### How The Player Leaves A Tactical Mission And Continues The Campaign

Main missions and local pockets both return to the strategic layer, but in slightly different ways.

For local pockets:

1. the player resolves the site
2. the campaign builds an `ambient return summary`
3. rewards, scars, route notes, favor, intel, and fleet changes are applied
4. the overmap reactivates

For main missions:

1. the player secures the objective
2. extraction or safe exit becomes available when conditions are met
3. on completion, the campaign applies sector rewards, branch score movement, side-objective outcomes, and progression
4. the game can offer fleet-hub / route-choice continuation before the next sector

### How The Player Moves North Through The Whole Campaign

At the highest level, the run progresses like this:

1. start in the south
2. travel to mission POIs and optional contacts
3. use hubs to repair, refit, trade, build, and restock
4. scan to sharpen route knowledge and target quality
5. manage exposure, alert, favor, and strain
6. fight or auto-resolve hostile interceptions
7. enter local sites for resources, intel, rescues, and route changes
8. clear main sectors one by one
9. continue north through act breaks, coalition growth, Luna pressure, and Earth approach
10. finish the final liberation and resolve the ending branch

## Other Practical Player Interactions

Beyond explicit action buttons, the player can also:

- select discovered locations
- select free-space travel targets
- select hostile contact markers
- select support markers
- open different command tabs
- confirm or cancel atomic strike confirmation
- resume campaign from checkpoint
- auto-resolve or manually take command at encounter prompts

## Travel And Route Flow

The strategic travel loop is:

1. select a location or free-space point
2. plot course
3. engage course
4. continuous travel begins
5. route risk, pressure, and hostile detection continue during movement
6. travel ends in one of several ways:
   `arrival`, `local encounter`, `hostile interception`, or `manual hold`

Travel details:

- travel is continuous, not instant node hopping
- free-space travel is supported
- travel shows ETA and interception risk
- the fleet and camera are separate systems
- docking requires physical approach

## Encounter Types

The campaign can currently produce these broad encounter types:

### 1. Main Mission Arrival Encounter

Triggered by:

- arriving at a main mission location that should launch an authored battle

Flow:

- travel reaches destination
- strategic prompt appears
- player can auto-resolve or take command
- if manual, one large tactical mission sector loads
- if auto-resolve, results are applied and the overmap resumes

### 2. Hostile Search Group Interception

Triggered by:

- a moving search group entering interception conditions around the player
- arriving directly into enemy activity pressure

Flow:

- search group contact is raised
- strategic prompt appears
- player can auto-resolve or take command
- if auto-resolved, the hostile group returns to overmap behavior
- if manual, a tactical battle launches

### 3. Local Site Encounter

Triggered by:

- moving into range of an enterable optional site and choosing `Enter Site`

Site types that support local pockets:

- `Resource Zone`
- `Salvage Field`
- `Hidden Cache`
- `Distress Signal`
- `Story Event`
- `Repair Site`

Flow:

- player selects site
- optionally chooses a site-resolution mode first
- enters one compact tactical pocket
- clears or resolves it
- returns to the overmap with a summarized outcome

### 4. Fleet Hub Session

A non-combat campaign session focused on:

- persistent fleet management
- upgrade and condition presentation
- post-mission reset and support flow

## Local Site Resolution Outcomes

### Resource Zone

Possible plans:

- `Fast Strip`
  More ore, more exposure, more strain, harsher scar
- `Careful Secure`
  Slightly less ore, more intel, lower exposure, reduced strain
- `Mark For Allies`
  Lower direct ore, Green favor gain, support-route stabilization, allied-lane aftermath

### Salvage Field

Possible plans:

- `Fast Strip`
  More credits, more exposure, more strain
- `Careful Secure`
  Better intel, lower exposure, lower strain
- `Mark For Allies`
  Lower direct payout, some salvage, Yellow favor gain, recovery-route stabilization

### Hidden Cache

Possible plans:

- `Fast Strip`
  More supplies and salvage, more exposure
- `Careful Secure`
  Better intel and quieter extraction
- `Mark For Allies`
  Lower direct payout, Green favor gain, route support, stronger hidden-lane help

Typical cache rewards can include:

- torpedo charges
- supplies
- salvage
- intel

### Distress Signal

Possible plans:

- `Evacuate Survivors`
  Yellow favor, fuel cost, possible recovered ships, safer reputation result
- `Tow Damaged Hull`
  Better chance to recover a hull, fuel cost, relationship advancement
- `Strip For Parts`
  Salvage gain, more exposure, higher strain, harsher distress memory, relationship damage

Possible distress results:

- survivors saved
- ships recovered into the persistent fleet
- no hull recovery but favor gained
- salvage taken at moral/reputational cost

### Story Event / Relay Site

Possible plans:

- `Quiet Decode`
  Best intel gain, lower alert, lower exposure, route stabilization
- `Ally Broadcast`
  More favor, route support, more exposure
- `Jam And Destroy`
  Cuts pressure and enemy relay value, but is harsher and less cooperative

Typical story-event effects:

- reveal nearby sites
- identify nearby hostile search groups
- increase intel
- lower alert
- adjust exposure
- change recurring-contact relationships

### Repair Site

Typical result:

- persistent fleet condition restored
- strain lowered
- support-route stabilization

## Transit Discoveries

Travel can spawn additional discovered contacts and scripted mini-opportunities.

Implemented discovery families include:

- cache
- ore
- reinforcement
- ambush
- salvage hulk
- supply cache
- data relay
- wreck field
- minefield
- drifting turret
- neutral trader
- prison barge
- anomaly
- fleet asset

The reactive-theater layer also supports multi-step discovery chains such as:

- relay echo
- wreck trail
- false distress
- missing patrol
- smuggler lead

## Contact Escalation And Memory

Unresolved contacts do not always remain static.

Current systemic behavior includes:

- contact escalation when ignored
- unresolved-age tracking
- site memory after completion
- scar notes
- route notes
- support-route stabilization flags
- recurring-contact tagging

This means the map remembers:

- that you visited a place
- how you resolved it
- what that did to the route
- what it did to allies, search pressure, or trust

## Main Mission Script Types

Authored main mission objective types:

- `Destroy`
- `Survive`
- `Escort`
- `Capture`
- `Boss`
- `Final Boss`

Authored side-objective types:

- `Kill Count`
- `No Hull Damage Window`
- `Clear Before Time`

Boss kinds:

- `None`
- `Mid Alpha`
- `Mid Beta`
- `Final`

Map modifiers:

- `Clear Space`
- `Nebula`
- `Debris Field`
- `EMP Zone`
- `Resource Drought`
- `Rich Deposits`
- `Solar Storm`
- `Gravity Shear`
- `Supply Windfall`

Modifier effects can influence:

- targeting range
- mining rate
- wave timing
- wave size
- ore value
- sector credit bonus
- auto-lock behavior

## The Full Authored Campaign Arc

Each sector below lists:

- sector title
- location
- primary objective
- side objective
- modifiers
- completion payoff in fiction

### Sector 1

- `ANCHORAGE FIRESTORM`
- Location: `Far Trade Anchorage`
- Primary: `Survive` - Hold the evac lane
- Goal / timer: `200s`
- Side objective: `Take no hull damage for 120s`
- Modifiers: `Debris Field`, `Supply Windfall`
- Story lead: hold the evacuation lanes while the anchorage burns
- Completion: the convoy escapes with civilians, ledgers, and a road home

### Sector 2

- `CUSTOMS HALO COLLAPSE`
- Location: `Outer Colony Jump Ring Approach`
- Primary: `Destroy` - Kill 6 strike ships; save 2 convoy hulls
- Goal / timer: `6 kills / 840s`
- Side objective: `Finish the mission in 660s`
- Modifiers: `Nebula`
- Story lead: break the customs-halo screen before the aperture closes
- Completion: civilian traffic slips through and the jump approach stays open

### Sector 3

- `BREAKOUT VECTOR`
- Location: `Outer Colony Jump Ring`
- Primary: `Destroy` - Break the jump-ring cordon
- Goal / timer: `6 kills / 720s`
- Side objective: `Destroy 8 enemy ships`
- Modifiers: `Nebula`
- Completion: the first true blockade breaks and deep-space escape opens

### Sector 4

- `LAST AUTHORITY RELAY`
- Location: `Gate Relay Tethys`
- Primary: `Destroy` - Destroy 4 relay blockers
- Goal / timer: `4 kills / 750s`
- Side objective: `Finish the mission in 600s`
- Modifiers: `Debris Field`
- Completion: route control opens and the Earthward vector becomes real

### Sector 5

- `RELAY RELIEF BREAK`
- Location: `Tethys Relay Hinterlane`
- Primary: `Destroy` - Break the relief wing
- Goal / timer: `10 kills / 780s`
- Side objective: `Finish the mission in 620s`
- Modifiers: `Debris Field`
- Completion: the reserve wing shatters and the relay corridor stays open

### Sector 6

- `DEBRIS WAKE RECOVERY`
- Location: `Burning Debris Wake`
- Primary: `Survive` - Hold until cache recovery
- Goal / timer: `110s / 720s`
- Side objective: `Finish the mission in 540s`
- Modifiers: `Debris Field`, `Supply Windfall`
- Completion: people, fuel, and records are pulled from the fire

### Sector 7

- `RED KNIFE PURSUIT`
- Location: `Shattered Traffic Lanes`
- Primary: `Boss` - Kill the pursuit Titan
- Goal / timer: `1 boss / 780s`
- Side objective: `Kill the boss in 600s`
- Modifiers: `EMP Zone`, `Gravity Shear`
- Boss kind: `Mid Alpha`
- Completion: the first Titan hunter falls and the convoy escapes the kill box

### Sector 8

- `REFUGEE WAYLINE`
- Location: `Civilian Exodus Corridor`
- Primary: `Escort` - Escort the Exodus Titan
- Goal / timer: `95s / 780s`
- Side objective: `Keep the escort undamaged for 90s`
- Modifiers: `Resource Drought`
- Completion: the civilian column survives and legitimacy stays with the fleet

### Sector 9

- `NEUTRAL TRADE SPINE`
- Location: `Broker Yards And Slipway Habitats`
- Primary: `Survive` - Hold until defectors cross
- Goal / timer: `65s / 780s`
- Side objective: `Keep 3 defectors alive for 90s`
- Modifiers: `Rich Deposits`
- Completion: neutral survivors and yard ships cross into coalition protection

### Sector 10

- `BROKEN ARMISTICE`
- Location: `Trade Spine Defense Belt`
- Primary: `Destroy` - Destroy the vanguard fleet
- Goal / timer: `16 kills / 780s`
- Side objective: `Destroy 10 escorts`
- Modifiers: `Rich Deposits`
- Completion: the trade spine defects behind the Blue fleet

### Sector 11

- `LEDGER AND LOX`
- Location: `Bonded Depot Shelf`
- Primary: `Survive` - Hold the depot shelf
- Goal / timer: `85s / 780s`
- Side objective: `Finish the mission in 560s`
- Modifiers: `Supply Windfall`
- Completion: fuel, books, and repair stores are secured for the road home

### Sector 12

- `SIGNATORY RUN`
- Location: `Coalition Service Halos`
- Primary: `Survive` - Hold the signatory run
- Goal / timer: `95s / 780s`
- Side objective: `Keep the lead ship undamaged for 80s`
- Modifiers: `Solar Storm`
- Completion: the pact is signed in motion and Green houses commit

### Sector 13

- `GREEN CONTRACT FRONT`
- Location: `Coalition Array Nysa`
- Primary: `Destroy` - Destroy the 3 jammer towers
- Goal / timer: `3 kills / 780s`
- Side objective: `Finish the mission in 600s`
- Modifiers: `Solar Storm`
- Completion: Nysa comes back online and the Green contract front turns

### Sector 14

- `NYSA RELIEF BREAK`
- Location: `Contract Array Rear Orbit`
- Primary: `Destroy` - Break the Nysa relief wing
- Goal / timer: `8 kills / 780s`
- Side objective: `Finish the mission in 620s`
- Modifiers: `Solar Storm`
- Completion: the counterattack breaks and the Green alliance holds

### Sector 15

- `KHARON OUTER SCREEN`
- Location: `Siege Gate Kharon`
- Primary: `Destroy` - Silence the outer batteries
- Goal / timer: `4 kills / 800s`
- Side objective: `Destroy 6 escorts`
- Modifiers: `Gravity Shear`, `Solar Storm`
- Completion: the gate’s targeting spine starts to fail

### Sector 16

- `ASHEN GATE`
- Location: `Siege Gate Furnace`
- Primary: `Boss` - Kill the artillery Titan
- Goal / timer: `1 boss / 840s`
- Side objective: `Destroy 6 escorts`
- Modifiers: `Gravity Shear`, `Solar Storm`
- Boss kind: `Mid Beta`
- Completion: the siege gate breaks and the Solward lane opens

### Sector 17

- `OUTER SOL PROBE WAR`
- Location: `Outer Sol Defense Fringe`
- Primary: `Destroy` - Kill 6 recon ships before escape
- Goal / timer: `6 kills / 780s`
- Side objective: `Finish the mission in 560s`
- Modifiers: `Nebula`, `Solar Storm`
- Completion: the probe war is won and the coalition stays hidden longer

### Sector 18

- `OUTER SOL HOLD`
- Location: `Coalition Assembly Ring`
- Primary: `Survive` - Hold the corridor
- Goal / timer: `240s / 780s`
- Side objective: `Destroy 14 attackers during the hold`
- Modifiers: `Nebula`, `Solar Storm`
- Completion: the coalition assembles intact enough for the final push

### Sector 19

- `YELLOW BREAKCHAIN`
- Location: `Liberation Corridor`
- Primary: `Destroy` - Break the prison chain
- Goal / timer: `4 kills / 840s`
- Side objective: `Keep the recovery Titan undamaged for 90s`
- Modifiers: `Debris Field`, `Supply Windfall`
- Completion: liberated Yellow crews begin falling back under protection

### Sector 20

- `YELLOW REJOIN`
- Location: `Breakchain Debris Run`
- Primary: `Escort` - Escort the recovery Titan
- Goal / timer: `100s / 840s`
- Side objective: `Keep the escort undamaged for 100s`
- Modifiers: `Debris Field`, `Supply Windfall`
- Completion: Yellow survivors rejoin the fleet in force

### Sector 21

- `LUNA ANCHOR SWEEP`
- Location: `Luna Perimeter`
- Primary: `Destroy` - Destroy 3 orbital anchors
- Goal / timer: `3 kills / 840s`
- Side objective: `Finish the mission in 620s`
- Modifiers: `EMP Zone`, `Resource Drought`
- Completion: Luna’s anchor grid goes dark and the Earth lane cracks open

### Sector 22

- `LUNA CORDON BREAK`
- Location: `Earth Approach Lane`
- Primary: `Destroy` - Break the reserve cordon
- Goal / timer: `10 kills / 840s`
- Side objective: `Finish the mission in 620s`
- Modifiers: `EMP Zone`, `Resource Drought`
- Completion: the lunar cordon shatters and Earth finally lies ahead

### Sector 23

- `EARTHRISE INSURRECTION`
- Location: `Earth Lift Terminus Belt`
- Primary: `Destroy` - Kill 4 uplinks; cover the launches
- Goal / timer: `4 kills / 900s`
- Side objective: `Finish the mission in 660s`
- Modifiers: `Solar Storm`, `Gravity Shear`
- Completion: the resistance rises into orbit and blinds the occupation net

### Sector 24

- `HOMEWORLD LIBERATION`
- Location: `Earth High Orbit`
- Primary: `Final Boss` - Kill the AI mothership
- Goal / timer: `1 boss / 900s`
- Side objective: `Finish the mission in 720s`
- Modifiers: `Solar Storm`, `Gravity Shear`
- Boss kind: `Final`
- Completion: Earth orbit is reclaimed and the road home ends

## Mission Flow Inside A Tactical Main Mission

The main mission layer currently supports:

- one large tactical sector per encounter
- objective markers
- support markers
- mission sections / pocket progression
- travel locks between sections when required
- time limits
- side objectives
- reserve pressure over time
- boss phase escalation in boss sectors
- transition summaries on completion

Player-facing objective marker types:

- `Primary Objective`
- `Next Route`
- `Escort Target`
- `Protected Asset`
- `Destroy Target`
- `Capture Zone`
- `Boss Target`
- `Optional Objective`

Player-facing support marker types:

- `Anomaly`
- `Faction Contact`
- `Salvage`
- `Resource`
- `Hazard`
- `Intel`

## Campaign Endings

The campaign currently supports multiple branch outcomes:

- `Standard`
  `Earth Liberated`
- `Strategic Supremacy`
  `Alt Ending: Decisive Liberation`
- `True Restoration`
  `True Ending: Homeworld Restored`
- `Pyrrhic`
  `Alt Ending: Earth Liberated At Great Cost`

These depend on factors such as:

- branch score
- side objectives completed
- fleet condition

## What This Document Is For

Use this document when you need one place to answer:

- "What can the player do in campaign?"
- "What are all the encounter categories?"
- "What does each site type do?"
- "What are the current posture, reputation, pressure, and contact systems?"
- "What are the main authored objectives and what happens in each one?"

Use the other docs like this:

- `STRATEGIC_CAMPAIGN_MAP_SPEC.md`
  for the stable high-level campaign constitution
- `STRATEGIC_CAMPAIGN_REACTIVE_THEATER_CHECKLIST.md`
  for current advanced-systems checklist state
- `STRATEGIC_HUD_ACTION_FIRST_SPEC.md`
  for command HUD usability requirements
- `STRATEGIC_CAMPAIGN_SCRIPT.md`
  for the one-file current campaign script

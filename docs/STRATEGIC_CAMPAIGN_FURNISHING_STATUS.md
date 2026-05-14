# Strategic Campaign Furnishing Status

Date: 2026-05-13  
Status: Active progress log and backlog

## Purpose

This document records two things:

1. what strategic-campaign furnishing work has already been implemented
2. what furnishing work we could still add next

Use the related docs like this:

- `STRATEGIC_CAMPAIGN_MAP_SPEC.md`: intended campaign vision
- `STRATEGIC_CAMPAIGN_CHECKLIST.md`: core implementation completion checklist
- `STRATEGIC_CAMPAIGN_FURNISHING_PLAN.md`: staged furnishing roadmap
- `STRATEGIC_CAMPAIGN_FURNISHING_STATUS.md`: concrete progress log plus future furnishing backlog

## Furnishing Changes Already Made

### 1. Free travel and broader command-layer control

Implemented:

- free travel to arbitrary map coordinates instead of only POI-to-POI travel
- travel state persisted as real overmap navigation
- strategic HUD tabs for `Navigation`, `Fleet`, `Resources`, and `Strikes`
- improved button hit regions and command-panel interaction reliability

Player-facing result:

- the campaign no longer behaves like a pure node-click menu
- the player can plot movement more freely and inspect more campaign data from the overmap

### 2. Actionable overmap contacts

Implemented:

- ambient contacts can now be entered as local tactical pockets
- ore sites, salvage sites, hidden caches, distress contacts, story-relay contacts, repair anchorages, and hubs all support tactical entry
- local site entry now behaves as a deliberate encounter instead of a passive reward stop

Player-facing result:

- discovered contacts are no longer just labels
- the player can arrive, enter, exploit, and extract from overmap discoveries

### 3. Compact local-site tactical pockets

Implemented:

- local ambient encounters are centered into compact tactical pockets instead of giant leftover mission spaces
- ambient sites snap to a stable center subzone so they are easier to warp into
- important local content is kept closer to the readable center of the pocket
- local tactical markers and support markers expose major site content

Player-facing result:

- ore fields, salvage fields, and distress sites are much faster to understand and traverse
- the player does not have to wander across oversized empty mission maps to find the site content

### 4. Tactical marks for local content

Implemented:

- tactical markers for local-site objectives
- support markers for site resources, faction contacts, and other key local content
- stronger site entry/readability for ore, salvage, and support traffic

Player-facing result:

- entered sites are more legible
- the player can identify what matters in the pocket without blind searching

### 5. Distress support and recovered-ship persistence

Implemented:

- distress/local support requests are tied into the comms flow
- support requests are remembered during the local encounter
- recovered ships can join the persistent fleet after extraction

Player-facing result:

- distress contacts can now have lasting fleet consequences
- recovered friendly ships are no longer throwaway local props

### 6. Story-event and hidden-cache furnishing

Implemented:

- `STORY_EVENT` sites now behave like relay/intel pockets instead of distress clones
- story sites can grant intel, favor, alert reduction, exposure reduction, nearby-site reveals, and nearby hostile search-group reveals
- `HIDDEN_CACHE` sites now provide a broader reward profile including salvage and intel instead of a tiny one-note payout
- both categories can include recoverable local assets

Player-facing result:

- story and cache contacts feel more distinct and more worth investigating

### 7. Green and Yellow ambient traffic differentiation

Implemented:

- Green-aligned local sites lean more military, resistance, and escort oriented
- Yellow-aligned local sites lean more commercial, civilian, runner, liner, and broker oriented
- hubs and service sites now spawn more identity-specific local traffic instead of generic traffic

Player-facing result:

- the player can more easily read who owns a place and what kind of place it is

### 8. Fleet command-layer furnishing

Implemented:

- improved fleet summaries
- fleet posture board
- compact fleet roster lines
- fleet readiness/condition board
- detachment grouping lines
- persistent recovered ships visibly enter the roster

Player-facing result:

- the player can answer what ships they have and what shape they are in without leaving the campaign layer

### 9. Resource and logistics furnishing

Implemented:

- stronger resource board for credits, ore, fuel, supplies, ammo, salvage, intel, exposure, and favor
- resource meter boards in the command panel
- resource trend lines
- logistics warning lines
- route-support preview lines

Player-facing result:

- the campaign resources now read more like operational constraints and less like decorative counters

### 10. Strike and recon furnishing

Implemented:

- strike readiness board
- torpedo, sortie, and atomic readiness surfaced more clearly
- recon/intel readout strengthened
- exposure and strike-heat readouts added
- target-window and strike-consequence lines added

Player-facing result:

- the player can better judge what strike tools are available and what they may cost operationally

### 11. Radio, sensor, and comms station furnishing

Implemented:

- left-side station broken into dedicated sections:
  - `SESSION TIME`
  - `RECEIVER MANUAL`
  - `DIRECTION FINDER`
  - `RADIO / COMMS`
- receiver, bearing, sweep window, docking link, and favor values surfaced as station data instead of plain summary prose
- scan sweeps now reveal nearby sites and nearby hostiles and place map pings on them

Player-facing result:

- the left side feels more like a working station and less like a text stack
- sensor actions now have clearer gameplay consequences

### 12. Mission and objective text cleanup

Implemented:

- sector objective labels shortened
- initial phase labels shortened
- initial threat labels shortened
- dynamic tactical phase/threat updates shortened
- transition summaries shortened
- strategic encounter prompt wording cleaned up

Player-facing result:

- mission boards are much less likely to turn into unreadable walls of text

### 13. Encounter prompt and mission-overlay cleanup

Implemented:

- strategic encounter overlay layout fixed so text no longer piles on top of itself
- local site and hostile intercept prompts cleaned up
- one-large-sector framing preserved

Player-facing result:

- encounter entry screens are more readable and less broken-looking

### 14. Regional identity on the overmap

Implemented:

- explicit region identities:
  - `SOUTHERN SHELTER`
  - `CONTESTED BELT`
  - `EARTHWARDED NORTH`
- campaign summary and selected location now surface region identity and region notes
- route opportunity text changes by region
- hub quality, pricing, support, trade, and logistics shift by region
- transit discoveries now vary by region instead of drawing from one generic pool
- hostile search-group readouts gain more region-aware identity

Player-facing result:

- the map feels more geographically and politically distinct
- the south, middle, and north are easier to tell apart from behavior and opportunities

### 15. Region-specific local ambient traffic

Implemented:

- southern local sites gain more sheltered/prospector/recovery-style traffic
- contested-belt sites gain more raid-wake, broker, and mixed-traffic behavior
- northern sites gain more resistance, blackout, and hunt-lane escort behavior
- this now applies on top of site type rather than replacing site type

Player-facing result:

- the same site type feels different depending on where on the map it is found

### 16. Better extraction and return feedback

Implemented:

- ambient site extraction now produces site-specific overmap return summaries
- ore return reports ore recovery
- salvage return reports credits earned
- cache return reports torpedo/supplies/salvage/intel gains
- distress return reports whether ships were actually recovered
- story return reports intel/favor/search-picture improvements
- repair return reports servicing outcomes

Player-facing result:

- the player more clearly understands what the local encounter accomplished after returning to the overmap

## Furnishing Changes We Could Still Make

This section is the active furnishing backlog. None of these items are required to exist already in order to be listed here.

### A. Make every overmap contact even richer

Possible additions:

- add anomaly-specific local pockets instead of only using existing site categories
- add false-signal contacts that can become hostile bait or empty decoys
- add more “investigate or ignore” contacts with visible cost/reward tension
- add more site-specific extraction choices, not only one default outcome
- add contacts that can escalate if ignored too long

### B. Deepen local pocket variation

Possible additions:

- vary local pocket layouts by region and by site type
- vary asteroid-field geometry and salvage spread more aggressively
- add local hazards tied to site category:
  - mine clusters
  - blackout interference
  - relay blind spots
  - drifting wreck collisions
- add more local structures:
  - relay towers
  - defense buoys
  - cargo gantries
  - field depots
- add alternate extraction points or safer/unsafe exits

### C. Expand the fleet manager further

Possible additions:

- real ship cards with role iconography
- explicit damage-state colors or bars
- detachment reassignment from the campaign layer
- battle/escort/mining/refit readiness tags per hull
- support-coverage warnings per group
- hull trait summaries:
  - carrier
  - logistics
  - stealth
  - heavy
  - escort

### D. Expand the logistics station further

Possible additions:

- route cost projection for the currently selected destination
- stronger warnings for:
  - fuel-poor state
  - ammo-poor state
  - supply-poor state
  - repair-poor state
- resource drain trends over time
- projected safe operating range
- hub dependency summary
- trade forecast for the selected hub

### E. Expand the strike station further

Possible additions:

- strike slots with individual readiness lamps
- recon coverage map or mini-board
- explicit “can strike / cannot strike / why” target readouts
- stronger target filtering for confirmed versus uncertain contacts
- retaliation forecast before strike commitment
- ally-supported strike options

### F. Deepen radio/comms gameplay

Possible additions:

- richer sensor sweep outcomes by region and by intel quality
- more ally-call states beyond simple favor spending
- contact pinning or save-to-board behavior
- more deliberate manual signal workflows
- procedural radio chatter tied to route pressure or local contacts
- stronger distinction between receiver, direction finder, and comms tasks

### G. Add more ambient life everywhere

Possible additions:

- more civilian runner traffic
- more prospectors
- more repair tenders
- more relay guards
- more rescue and convoy support hulls
- more faction-specific idle or transit behavior
- more visible differences in movement patterns between Green, Yellow, and hostile forces

### H. Add more transit discovery stories

Possible additions:

- drifting wreck trains
- disabled escorts
- smuggler drops
- fake distress signals
- resistance favor opportunities
- relay ghost trails
- hunter-killer bait contacts
- blocked routes that can be risked or bypassed
- multi-step discovery chains that pay off later

### I. Strengthen regional identity even more

Possible additions:

- region-specific weathering/visual treatment on the overmap
- region-specific local encounter lighting or fog behavior
- region-specific ambient audio or radio tone
- region-specific service availability patterns
- region-specific strike pressure and patrol doctrine
- region-specific reward profiles:
  - south safer and steadier
  - middle riskier but richer
  - north harsh but politically decisive

### J. Replace more dead HUD space with instruments

Possible additions:

- route timeline board
- contact lamp board
- fuel projection gauge
- fleet readiness card grid
- favor board with channel status lamps
- signal-history strip
- strike-commitment meter
- support-presence board

### K. Make return feedback more visual

Possible additions:

- reward chips or plates on return
- faction-gain lamps
- recovered-ship “added to roster” cards
- route-impact summary when a site materially changed the map
- separate overmap after-action board for site extractions

### L. Add more consequence-bearing choices

Possible additions:

- choose to strip a site quickly versus secure it carefully
- decide whether to spend supplies for deeper scans
- decide whether to call support at the cost of exposure
- choose whether to tow or reactivate recovered ships
- choose whether to sell or keep special site finds

### M. Add more replay variation

Possible additions:

- randomize local site traffic composition more deeply
- vary encounter support ship roles by run
- vary site rewards by route risk and current campaign condition
- vary who shows up to answer a distress call
- vary which regions generate which event clusters on a given run

## Suggested Next Furnishing Order

If continuing from the current state, a strong order would be:

1. Make return/extraction feedback more visual
2. Expand fleet manager into a stronger per-hull command board
3. Expand logistics projections and shortfall warnings
4. Expand strike/recon target logic and readiness presentation
5. Add more transit discovery stories and bait/false-signal variation
6. Add more region-specific local pocket layouts and hazards
7. Add more ally/comms interaction depth

## Practical Reading of Current Status

Right now the strategic campaign is no longer just:

- click a point
- travel there
- maybe fight

It already supports:

- free travel
- actionable contacts
- compact site pockets
- local tactical identity
- fleet/resource/strike command tabs
- radio/sensor behavior
- regional overmap identity
- region-specific local traffic
- clearer extraction outcomes

The biggest remaining furnishing opportunity is to keep converting text and implied systems into stronger visual instruments, stronger operational choices, and more varied discovery content.

## Next Major Design Target

### Make the Campaign Layer Emotionally Reactive

The campaign is now much stronger as a system.

What it still needs is more memory, consequence, uncertainty, and personality.

The next big upgrade should not only be:

- more tabs
- more counters
- more buttons
- more site types

It should also make the theater feel like it is reacting to the player.

That means:

- the map remembers what happened
- contacts are not always fully understood at first
- hostile response becomes more legible and more personal
- allies react to what the player is known for
- the campaign develops scars and reputations over time

### Why This Matters

Right now the strategic campaign can already support:

- free travel
- contact investigation
- tactical pocket entry
- route pressure
- fleet/resource/strike management
- regional identity

The next leap in quality comes from making the player feel that the campaign world is watching, remembering, and adapting.

That is what will move the campaign from:

- a strong strategic framework

to:

- a living war theater

## High-Value Future Furnishing Directions

These are the strongest additions suggested by the current design discussion.

### 1. Campaign reputation states

Add higher-level reputation or theater-perception states in addition to simple resources, favor, and exposure.

Examples:

- `Unknown Fleet`
- `Reliable Rescue Force`
- `Raider Threat`
- `Liberation Symbol`
- `Overextended Command`
- `High-Exposure Target`

What this would do:

- influence what kinds of contacts appear
- influence how allies respond
- influence how aggressively hostile forces prioritize the player
- make the player feel like their campaign behavior creates a reputation

### 2. Named recurring contacts

Introduce a small number of recurring ships, flotillas, or commanders that can reappear across the campaign.

Examples:

- a Yellow broker captain who trades, lies, or reroutes information
- a Green escort commander who becomes more dependable if saved
- a damaged allied carrier group that can reappear later if helped
- a hostile search commander who learns from the player’s habits

What this would do:

- make the campaign world feel inhabited by remembered actors
- create continuity without requiring a full dialogue RPG structure

### 3. Theater pressure timeline

Add a visible strategic pressure timeline showing how the wider war is shifting even when the player is not in battle.

Possible timeline events:

- enemy patrol net expanding
- northern blockade tightening
- Green supply lines weakening
- Yellow trade lanes destabilizing
- Earthward routes becoming deadlier
- hidden hostile groups activating

What this would do:

- reinforce that the campaign is active without constant combat
- make route planning feel tied to a moving theater state

### 4. Site memory

Make overmap sites remember what happened to them and visibly change afterward.

Examples:

- a stripped salvage field becomes spent wreckage
- a rescued distress site becomes a friendly relay or support marker
- a failed rescue becomes a grave marker or a later trap
- a repaired anchorage improves over time
- a raided trade hub loses service quality and traffic

What this would do:

- make the map feel persistent instead of disposable
- make player action leave visible marks on the campaign layer

### 5. Enemy search doctrine

Differentiate hostile search groups into more legible doctrine types instead of letting them feel like one blended patrol class.

Examples:

- `Scout Screen`
- `Hunter-Killer Group`
- `Blockade Group`
- `Interdiction Group`
- `Punishment Fleet`

What this would do:

- make enemy movement and intent easier to understand
- make hostile response feel like a military system rather than generic pressure

### 6. Strategic noise and uncertainty

Make more contacts uncertain until confirmed through proximity, intel, or sensor work.

Possible uncertain labels:

- `Weak Signal`
- `Hot Contact`
- `Civilian Squawk`
- `False Transponder`
- `Distress Burst`
- `Metallic Debris Field`
- `Active Drive Plume`
- `Encrypted Relay Echo`

What this would do:

- strengthen the Highfleet-style uncertain-detection fantasy
- make sensing and scouting feel more meaningful

### 7. Command crew commentary

Add short functional command-station callouts from crew roles.

Examples:

- “Fuel range is thin if we push north.”
- “That distress call is repeating too cleanly. Could be bait.”
- “Green traffic is heavier here. We may be near a protected route.”
- “Enemy search groups are converging on our last sweep.”
- “This anchorage can repair hull damage, but not reload torpedoes.”

What this would do:

- make the command layer feel crewed rather than silent
- surface useful information with personality

### 8. Campaign scars and visual map change

Make the overmap visually reflect campaign changes over time.

Examples:

- attacked hubs flicker or show damage marks
- cleared hostile pockets become safer corridors
- enemy-controlled zones gain stronger patrol overlays
- Green-supported routes gain escort markers
- Yellow trade lanes brighten or fade with stability

What this would do:

- make the map feel like a living war display
- give the player visible proof that the theater is changing

## Recommended Priority From Here

Do not build all of the above at once.

The strongest next order is:

1. `Add site memory`
2. `Add enemy search doctrine`
3. `Add uncertain contact labels`
4. `Add visual return feedback`
5. `Add named recurring contacts later`

## Practical Interpretation

The current furnishing work solved many of the structural weaknesses of the strategic layer:

- lack of interaction
- lack of readability
- lack of fleet/resource visibility
- lack of local encounter purpose

The next wave should solve a different class of weakness:

- lack of memory
- lack of uncertainty
- lack of theater personality
- lack of evolving consequence

That is the shift from:

- a well-furnished campaign system

to:

- a campaign that feels alive, political, and personal

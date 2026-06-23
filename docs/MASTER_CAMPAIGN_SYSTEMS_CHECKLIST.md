# Master Campaign Systems Checklist

## Living War, Finite Fleet Economy, After-Action Reports, War Room, Memory, and Consequence

## Core Goal

Upgrade the campaign so the world feels like a real war instead of a collection of random encounters.

The campaign should obey four major rules:

1. **No fleets appear from nowhere.**
2. **Every ship belongs to a faction, base, fleet, convoy, patrol, or construction queue.**
3. **Every battle creates understandable consequences.**
4. **The player should clearly see what happened, why it happened, what changed, and what they can do next.**

This checklist combines the following major systems:

* Finite faction ship inventories
* Base-based fleet sortie system
* Faction ore economy
* Mining and ship construction
* Repair and recovery
* No-random-spawn campaign logic
* After-action reports
* Captain's Log
* War Room planning screen
* Remembered consequences
* Fleet personality
* Tactical crisis readability
* First 90 minutes polish
* Debug and testing tools

---

# 1. Global Design Rules

## No More Random Fleet Spawns

* [x] Remove unexplained enemy fleet spawning near the player.
* [x] Remove random Red ships popping into safe Green areas without an origin.
* [x] Remove player-proximity spawn logic unless it is tied to an actual live fleet, base, sensor contact, or reinforcement route.
* [x] Every active ship must exist before combat begins.
* [x] Every active ship must belong to a persistent owner.
* [x] Every fleet must have an origin.
* [x] Every fleet must have a mission.
* [x] Every fleet must have a destination, patrol route, intercept target, or return base.
* [x] Every destroyed ship must be removed from that faction's available ship pool.
* [x] Every newly created ship must come from a shipyard, construction queue, emergency reserve, or real faction base.
* [x] Every reinforcement fleet must physically travel from a real location.
* [x] Emergency fleets are allowed only if they come from a real base, reserve pool, civilian militia, or industrial station.
* [x] Sensor ghosts must never be treated as live fleets.
* [x] Last-known-position shadows must not draw active movement vectors.
* [x] False pursuit lines from inactive or ghost fleets must be removed.

## Campaign Consequence Rule

Whenever something important happens, the game should answer:

* [x] What happened?
* [x] Where did it happen?
* [x] Who was involved?
* [x] What was lost?
* [x] What was gained?
* [x] What changed strategically?
* [x] What can the player do next?
* [x] Did this event affect a faction, base, captain, ship, convoy, route, or region?

## Player Understanding Rule

The player should not have to guess why the campaign changed.

* [x] Add direct "because you did X, Y changed" feedback.
* [x] Explain major consequences in after-action reports.
* [x] Save major consequences in the Captain's Log.
* [x] Show current strategic consequences in the War Room.
* [x] Make all available actions visible through buttons, not hidden keybinds.
* [x] Disabled buttons should explain why they cannot be used.

---

# 2. Faction Starting Ship Pools

## Purpose

Each faction should begin the campaign with a finite number of ships. These ships should be distributed across bases, stations, convoys, patrol groups, reserve fleets, shipyards, and mining operations.

No faction should receive free replacement ships unless those ships are built or drawn from a real reserve.

## Required Work

* [x] Create a starting order of battle for Red.
* [x] Create a starting order of battle for Green.
* [x] Create a starting order of battle for Yellow.
* [x] Assign every starting ship to a base, fleet, convoy, patrol, garrison, mining group, or reserve pool.
* [x] Make every starting ship owned by a faction.
* [x] Make every starting ship trackable by campaign systems.
* [x] Track whether each ship is active, docked, damaged, destroyed, under repair, under construction, or in reserve.
* [x] Prevent factions from exceeding their available ship count unless they build new ships.
* [x] Make ship losses matter to faction strength.
* [x] Save faction ship inventories across save/load.

## Example Red Starting Pool

* [x] 1 dreadnought or command ship.
* [x] 3 cruisers.
* [x] 8 frigates.
* [x] 14 pickets.
* [x] 6 missile boats.
* [x] 4 carriers or strike tenders.
* [x] 8 miners.
* [x] 4 ore haulers.
* [x] 3 repair/support ships.
* [x] Multiple garrison groups stationed at Red bases.
* [x] Several hunter-killer groups held at military bases.
* [x] Rear-area reserves outside the player's immediate route.

## Example Green Starting Pool

* [x] 1 command defense fleet.
* [x] 2 cruisers.
* [x] 6 frigates.
* [x] 12 patrol ships.
* [x] 8 miners.
* [x] 6 ore haulers.
* [x] 4 repair/support ships.
* [x] Station defense groups.
* [x] Convoy escorts.
* [x] Regional counter-task-force reserves.
* [x] Defensive fleets near important bases.

## Example Yellow Starting Pool

* [x] 0-1 major military ships.
* [x] Several armed escorts.
* [x] Many trade haulers.
* [x] Civilian convoys.
* [x] Mining ships.
* [x] Ore haulers.
* [x] Station security ships.
* [x] Private escorts.
* [x] Emergency militia or hired defense craft if threatened.

---

# 3. Base, Station, And Shipyard Ownership

## Purpose

Bases should become the source of fleet activity. Ships should sortie from bases, return to bases, repair at bases, and be built at bases.

## Base Data To Track

Each base or station should store:

* [x] Base name.
* [x] Faction owner.
* [x] Base type.
* [x] Region.
* [x] Strategic importance.
* [x] Stored ore.
* [x] Stored repair supplies.
* [x] Stored ammunition if implemented.
* [x] Docked ships.
* [x] Damaged ships waiting for repair.
* [x] Construction queue.
* [x] Repair queue.
* [x] Defense fleet.
* [x] Local patrol fleet.
* [x] Mining assignments.
* [x] Convoy assignments.
* [x] Available sortie missions.
* [x] Station damage state.
* [x] Station service state.
* [x] Station memory flags.
* [x] Whether the player has visited.
* [x] Whether the player has saved, ignored, damaged, or overused the station.

## Base Types

* [x] Military base.
* [x] Shipyard.
* [x] Heavy shipyard.
* [x] Mining hub.
* [x] Trade station.
* [x] Relay station.
* [x] Repair depot.
* [x] Fortress.
* [x] Civilian port.
* [x] Forward outpost.
* [x] Hidden Red staging base.
* [x] Yellow commercial station.
* [x] Green defensive station.

## Base Services

* [x] Shipyards can build ships.
* [x] Heavy shipyards can build large warships.
* [x] Repair depots can repair damaged ships.
* [x] Mining hubs can launch miners.
* [x] Military bases can launch patrols, raids, hunter-killer groups, escorts, and counter-task forces.
* [x] Trade stations can launch civilian convoys.
* [x] Relay stations can improve sensor coverage.
* [x] Fortresses can maintain strong defensive garrisons.
* [x] Civilian ports can launch trade traffic and emergency evacuation convoys.
* [x] Damaged stations should lose some services until repaired.
* [x] Destroyed stations should stop launching fleets or offering services.

---

# 4. Ore Economy

## Purpose

Factions should need ore to replace ship losses. Mining, hauling, and protecting ore should become part of the living war.

## Ore Sources

* [x] Asteroid fields.
* [x] Mining sites.
* [x] Planetary extraction sites if applicable.
* [x] Salvage fields.
* [x] Captured depots.
* [x] Convoy deliveries.
* [x] Station stockpiles.
* [x] Wreckage fields after major battles.

## Mining Behavior

* [x] Mining ships launch from mining bases or industrial stations.
* [x] Mining ships travel to ore sites.
* [x] Mining ships mine ore over time.
* [x] Mining ships fill cargo.
* [x] Mining ships return to a friendly base.
* [x] Ore is added to that base's stockpile.
* [x] Mining ships can be intercepted.
* [x] Mining ships can request escort if danger is high.
* [x] Mining ships can flee if enemies are detected.
* [x] Destroyed mining ships reduce future faction production.
* [x] Mining sites can be depleted.
* [x] Depleted mining sites should remain depleted across save/load.
* [x] Factions should search for new ore if local ore income becomes too low.

## Ore Hauling

* [x] Ore haulers should move ore between mining hubs and shipyards.
* [x] Ore haulers should be vulnerable to raids.
* [x] Ore haulers should request escorts in dangerous regions.
* [x] Destroyed ore haulers should reduce stockpile transfers.
* [x] Captured or destroyed ore convoys should affect construction queues.
* [x] Green ore convoys should matter to Green defense strength.
* [x] Red ore convoys should matter to Red ship replacement.
* [x] Yellow ore convoys should matter to trade and civilian economy.

## Strategic Effects

* [x] Destroying enemy miners should slow enemy ship production.
* [x] Destroying enemy ore haulers should interrupt shipyard supply.
* [x] Defending Green miners should help Green build replacement ships.
* [x] Attacking Red mining hubs should reduce Red pressure over time.
* [x] Protecting Yellow trade/mining traffic should improve Yellow reputation.
* [x] Mining success or failure should appear in War Room summaries.
* [x] Mining losses should create Captain's Log entries if important.

---

# 5. Ship Construction System

## Purpose

New ships should be built over time at real shipyards using ore.

## Ship Cost Data

Each ship type should have:

* [x] Ore cost.
* [x] Build time.
* [x] Required shipyard type.
* [x] Required faction.
* [x] Required crew if implemented.
* [x] Required supplies if implemented.
* [x] Required tech or base size if implemented.
* [x] Maximum production priority.
* [x] Strategic role tag.

## Example Ship Costs

* [x] Picket: low ore cost, short build time, built at small shipyards.
* [x] Frigate: medium ore cost, medium build time, built at military shipyards.
* [x] Cruiser: high ore cost, long build time, built at heavy shipyards.
* [x] Carrier: very high ore cost, very long build time, built at heavy shipyards.
* [x] Dreadnought: extreme ore cost, extreme build time, built only at major shipyards.
* [x] Miner: low-medium ore cost, short build time, built at industrial yards.
* [x] Ore hauler: medium ore cost, short-medium build time, built at industrial yards.
* [x] Repair ship: medium-high ore cost, medium build time, built at support yards.
* [x] Patrol craft: low ore cost, short build time, built at most military bases.

## Construction Queue

* [x] Each shipyard has a construction queue.
* [x] Shipyards can only build a limited number of ships at once.
* [x] A ship starts building only if enough ore is available.
* [x] Ore is deducted when construction begins or when construction completes.
* [x] Construction pauses if required resources are missing.
* [x] Completed ships are added to the base's docked ship pool.
* [x] Newly completed ships can be assigned to patrol, defense, escort, mining, raid, or reserve duty.
* [x] Construction progress should persist across save/load.
* [x] Destroyed or damaged shipyards should pause or slow construction.

## Faction Production Priorities

### Red Production Priorities

* [x] Replace hunter-killer losses.
* [x] Build interdiction frigates.
* [x] Build scouts if sensor coverage is weak.
* [x] Build miners if ore income is low.
* [x] Build escorts if mining convoys are being raided.
* [x] Build defense ships if Red bases are threatened.
* [x] Build larger offensive ships when enough ore is available.
* [x] Avoid spending all ore on large ships if escorts are critically low.

### Green Production Priorities

* [x] Replace patrol losses.
* [x] Build convoy escorts.
* [x] Build repair/support ships.
* [x] Build counter-task-force ships if Red pressure is high.
* [x] Build miners if Green economy is struggling.
* [x] Reinforce threatened stations.
* [x] Build defensive ships before offensive ships.
* [x] Preserve enough ships for base defense.

### Yellow Production Priorities

* [x] Replace trade haulers.
* [x] Build mining ships.
* [x] Build escorts for dangerous routes.
* [x] Build station security ships.
* [x] Avoid major military buildup unless threatened.
* [x] Militarize slowly if repeatedly attacked.
* [x] Prioritize trade survival over warfighting.
* [x] Move commerce away from dangerous areas if losses are high.

---

# 6. Fleet Sortie System

## Purpose

Fleets should launch from real bases, complete missions, react to danger, and return to base.

## Sortie Mission Types

* [x] Patrol local region.
* [x] Escort convoy.
* [x] Mine ore.
* [x] Haul ore.
* [x] Raid enemy mining site.
* [x] Attack enemy station.
* [x] Hunt player.
* [x] Intercept hostile fleet.
* [x] Reinforce allied base.
* [x] Scout unknown area.
* [x] Defend trade route.
* [x] Defend mining route.
* [x] Return for repair.
* [x] Retreat to nearest friendly base.
* [x] Evacuate from collapsing station.
* [x] Establish blockade.
* [x] Break enemy blockade.
* [x] Protect shipyard.
* [x] Sortie counter-task force.

## Sortie Requirements

Before launching a fleet, check:

* [x] Does the faction have enough docked ships?
* [x] Is the origin base active?
* [x] Is the origin base damaged or destroyed?
* [x] Is the mission important enough?
* [x] Is there enough fuel/supply if implemented?
* [x] Does the fleet need escorts?
* [x] Is the route too dangerous?
* [x] Is the faction already overcommitted?
* [x] Does the base need to keep a minimum defense reserve?
* [x] Is there a better nearby base to launch from?
* [x] Is this mission a response to a real event?

## Fleet Lifecycle

Every sortie should follow this lifecycle:

* [x] Fleet is assembled from docked ships.
* [x] Fleet is assigned a mission.
* [x] Fleet records its origin base.
* [x] Fleet records its target or route.
* [x] Fleet leaves base.
* [x] Fleet appears as a live campaign force.
* [x] Fleet travels through the campaign map.
* [x] Fleet may be detected by sensors.
* [x] Fleet performs its mission.
* [x] Fleet reacts to danger, opportunity, or player movement.
* [x] Fleet retreats if badly damaged.
* [x] Fleet returns to base after mission completion.
* [x] Surviving ships return to docked inventory.
* [x] Damaged ships enter repair queue.
* [x] Destroyed ships are removed from faction inventory.
* [x] Important mission results create campaign log entries.

---

# 7. Base Garrison System

## Purpose

Bases should have real defensive ships. These ships should not vanish or magically replenish.

## Required Work

* [x] Assign each base a garrison.
* [x] Garrison ships remain docked or patrol nearby.
* [x] Garrison ships can sortie only if the base chooses to commit them.
* [x] Garrison ships count toward faction inventory.
* [x] Garrison losses permanently weaken that base until repaired or reinforced.
* [x] Bases should request reinforcements when garrisons become weak.
* [x] Reinforcements must physically travel from another base.
* [x] Destroyed base defenses should not instantly respawn.
* [x] A base with no garrison should become vulnerable.
* [x] Base garrison strength should affect station services and regional control.
* [x] Garrison state should persist across save/load.

## Garrison Behavior

* [x] Garrison ships defend the base if attacked.
* [x] Garrison ships may intercept nearby hostile fleets.
* [x] Garrison ships should avoid chasing too far unless ordered.
* [x] Important bases should keep minimum defense reserves.
* [x] Low-value bases can be abandoned if faction strength collapses.
* [x] Fortresses should sortie less often but defend strongly.
* [x] Mining hubs should request escorts if garrison is weak.

---

# 8. Repair And Recovery

## Purpose

Damaged ships should not fight forever at full strength. They should retreat, repair, recover, or be scrapped.

## Required Work

* [x] Track ship damage after campaign battles.
* [x] Track damaged ships after manual battles.
* [x] Track damaged ships after auto-resolve.
* [x] Send heavily damaged ships back to friendly bases.
* [x] Add damaged ships to repair queue.
* [x] Repairs should take time.
* [x] Repairs should cost ore or repair supplies.
* [x] Damaged ships should not be immediately reused at full strength.
* [x] Factions should decide whether to repair or scrap badly damaged ships.
* [x] Repair progress should persist across save/load.
* [x] Destroyed repair depots should reduce repair capacity.

## Repair Priority

Factions should prioritize:

* [x] Command ships.
* [x] Carriers.
* [x] Cruisers.
* [x] Repair/support ships.
* [x] Miners if ore income is low.
* [x] Convoy escorts if trade routes are dangerous.
* [x] Frigates.
* [x] Pickets.
* [x] Cheap patrol craft.
* [x] Civilian haulers depending on economic need.

## Player-Facing Repair Consequences

* [x] After-action reports should show repair needs.
* [x] War Room should show damaged player ships.
* [x] Stations should show whether they can repair.
* [x] Damaged allied ships should appear in Captain's Log if important.
* [x] Enemy ships that escape damaged may return later after repair.
* [x] Destroying enemy repair capacity should matter strategically.

---

# 9. Faction AI Strategy

## Red Behavior

Red should feel aggressive, dangerous, and organized, but not magical.

* [x] Launch hunter-killer groups from Red bases.
* [x] Launch interdiction fleets from real shipyards or military bases.
* [x] Raid Green mining sites.
* [x] Attack Green stations.
* [x] Intercept the player if the player is detected.
* [x] Escort valuable attack fleets.
* [x] Pull back damaged ships.
* [x] Replace losses only through ship construction or reserve deployment.
* [x] Increase mining if ship losses are high.
* [x] Defend Red shipyards.
* [x] Defend Red ore hubs.
* [x] Avoid wasting all ships in constant suicide attacks.
* [x] Concentrate strength for major offensives.
* [x] Retreat and regroup after heavy losses.
* [x] Send stronger fleets only if Red logistics support them.

## Green Behavior

Green should feel defensive, reactive, and supportive.

* [x] Defend Green stations.
* [x] Escort Green convoys.
* [x] Launch counter-task forces against Red raiders.
* [x] Protect mining operations.
* [x] Reinforce threatened bases.
* [x] Repair damaged ships.
* [x] Send support to the player if reputation or campaign state allows.
* [x] Build replacement escorts and patrol ships.
* [x] Avoid abandoning core bases unless desperate.
* [x] Request player help when overwhelmed.
* [x] Sortie counter fleets against Red groups in the player's current region.
* [x] Prioritize regional defense over reckless attacks.
* [x] Preserve repair ships and miners.

## Yellow Behavior

Yellow should feel economic, neutral, and self-preserving.

* [x] Launch trade convoys between Yellow stations.
* [x] Mine ore for trade and local defense.
* [x] Hire or assign escorts in dangerous regions.
* [x] Avoid battle unless attacked or threatened.
* [x] Increase escort presence if Red pressure rises.
* [x] Avoid the player if player reputation is bad.
* [x] Offer trade or information if player reputation is good.
* [x] Build mostly civilian and escort ships.
* [x] Militarize slowly if repeatedly attacked.
* [x] Reroute convoys around dangerous areas.
* [x] Evacuate trade routes if losses become too high.
* [x] React strongly to civilian casualties.

---

# 10. Sensor And Intel Integration

## Purpose

Because fleets now come from real places, the sensor system should reveal their movement naturally.

## Live Fleet Detection

* [x] Show fleets leaving known bases if detected.
* [x] Show unidentified contacts when fleets are detected but not identified.
* [x] Show faction if known.
* [x] Show fleet size estimate if known.
* [x] Show fleet type estimate if known.
* [x] Show origin base if known.
* [x] Show likely destination if course is known.
* [x] Show mission estimate if intel is good enough.
* [x] Show confidence level for predictions.
* [x] Let relay stations improve detection.
* [x] Let scouts reveal enemy mining and shipyard activity.

## Sensor Ghost Rules

* [x] Create sensor ghosts only when a known contact leaves sensor range.
* [x] Ghosts should show last-known position.
* [x] Ghosts should show last-known heading if available.
* [x] Ghosts should fade over time.
* [x] Ghosts should not be treated as live fleets.
* [x] Ghosts should not trigger combat.
* [x] Ghosts should not draw live pursuit vectors.
* [x] Ghosts should not chase the player.
* [x] Ghosts should not be used for exact intercept calculations.
* [x] War Room should clearly label ghosts as last-known contacts.

## Example Contact Text

* [x] "Unknown Red contact detected leaving Black Furnace Shipyard."
* [x] "Likely mission: interdiction patrol."
* [x] "Estimated strength: 3-5 ships."
* [x] "Confidence: 61%."
* [x] "Last known contact. Current position uncertain."

---

# 11. After-Action Report System

## Purpose

After every major combat or campaign event, show a compact report explaining what happened, what was lost, what was gained, and what changed strategically.

## Required Report Triggers

* [x] Show an after-action report after manual battles.
* [x] Show an after-action report after auto-resolved battles.
* [x] Show an after-action report after player retreats.
* [x] Show an after-action report after enemy retreats.
* [x] Show an after-action report after allied fleet battles that affect the player's current region.
* [x] Show an after-action report after station attacks.
* [x] Show an after-action report after convoy rescue.
* [x] Show an after-action report after convoy destruction.
* [x] Show an after-action report after major mining raids.
* [x] Show an after-action report after shipyard attacks.
* [x] Show an after-action report after major faction events.
* [x] Show an after-action report after major scripted or systemic campaign events.

## Report Data To Track

* [x] Battle name or event title.
* [x] Location or region.
* [x] Event type.
* [x] Result.
* [x] Player ships damaged.
* [x] Player ships destroyed.
* [x] Allied ships damaged.
* [x] Allied ships destroyed.
* [x] Enemy ships damaged.
* [x] Enemy ships destroyed.
* [x] Civilian or Yellow ships damaged.
* [x] Civilian or Yellow ships destroyed.
* [x] Named captains involved.
* [x] Named ships involved.
* [x] Crew casualties.
* [x] Crew rescued.
* [x] Ammo spent.
* [x] Fuel or supplies spent.
* [x] Repair cost estimate.
* [x] Salvage available.
* [x] Ore gained.
* [x] Ore lost.
* [x] Construction impact.
* [x] Repair impact.
* [x] Reputation changes.
* [x] Regional control changes.
* [x] Red pressure increase or decrease.
* [x] Green morale increase or decrease.
* [x] Yellow trust increase or decrease.
* [x] Base damage state changes.
* [x] Fleet inventory changes.
* [x] Important tactical notes.

## Report Screen Layout

* [x] Add title at top, such as "After-Action Report."
* [x] Add event result banner.
* [x] Add location and faction information.
* [x] Add friendly losses section.
* [x] Add enemy losses section.
* [x] Add civilian or neutral losses section if relevant.
* [x] Add resources spent section.
* [x] Add salvage and rewards section.
* [x] Add strategic consequences section.
* [x] Add fleet economy consequence section.
* [x] Add notable events section.
* [x] Add buttons at the bottom for follow-up actions.

## Report Buttons

* [x] Collect Salvage.
* [x] Rescue Survivors.
* [x] Open Repairs.
* [x] Inspect Fleet.
* [x] Open Captain's Log.
* [x] Open War Room.
* [x] Return to Map.
* [x] Contact Survivors if relevant.
* [x] Request Green Support if available.
* [x] Disabled buttons should explain why they are unavailable.

## Tactical Explanation Notes

The report should teach the player what mattered.

* [x] Explain if a ship was lost because it was isolated.
* [x] Explain if ammo shortages affected the battle.
* [x] Explain if point defense was overwhelmed.
* [x] Explain if retreat was available but unused.
* [x] Explain if a Green ally survived because the player intervened.
* [x] Explain if the enemy escaped and may return later.
* [x] Explain if civilian casualties affected Yellow reputation.
* [x] Explain if destroying enemy miners slowed enemy production.
* [x] Explain if destroying a shipyard paused construction.
* [x] Explain if losing Green escorts weakened local patrol strength.

---

# 12. Persistent Captain's Log

## Purpose

Create a persistent campaign history that records important events and makes the war feel remembered.

## Basic Log Features

* [x] Create persistent campaign log storage.
* [x] Save log entries across save/load.
* [x] Add newest entries to the top or clearly mark recent entries.
* [x] Allow the player to scroll through past entries.
* [x] Add filters for battle, station, convoy, rescue, mining, shipyard, faction, economy, and major events.
* [x] Mark major campaign events with stronger visual treatment.
* [x] Add short readable summaries instead of raw debug-style data.
* [x] Allow after-action reports to create log entries automatically.
* [x] Link War Room recent events to log entries.

## Events To Record

* [x] Manual battles.
* [x] Auto-resolved battles.
* [x] Player retreats.
* [x] Enemy retreats.
* [x] Destroyed player ships.
* [x] Destroyed allied ships.
* [x] Destroyed enemy fleets.
* [x] Rescued captains.
* [x] Rescued crew.
* [x] Convoys saved.
* [x] Convoys lost.
* [x] Stations defended.
* [x] Stations damaged.
* [x] Stations destroyed.
* [x] Mining sites depleted.
* [x] Mining convoys destroyed.
* [x] Shipyards damaged.
* [x] Shipyards destroyed.
* [x] Construction queues paused.
* [x] Major ships completed.
* [x] Salvage sites cleared.
* [x] Distress calls answered.
* [x] Distress calls abandoned.
* [x] Relays decoded.
* [x] Yellow faction incidents.
* [x] Green reinforcements sent.
* [x] Red offensives detected.
* [x] Red fleets destroyed.
* [x] Red fleets escaped.
* [x] Major route decisions.
* [x] Earth approach milestones.

## Log Entry Format

Each log entry should include:

* [x] Date or campaign time.
* [x] Region or location.
* [x] Event title.
* [x] Short summary.
* [x] Factions involved.
* [x] Consequence.
* [x] Related ship names if relevant.
* [x] Related captain names if relevant.
* [x] Related base or station if relevant.
* [x] Related fleet economy impact if relevant.

## Important Log Behavior

* [x] Do not spam the log with tiny unimportant events.
* [x] Combine minor repeated events into summary entries when needed.
* [x] Use named ships and captains whenever possible.
* [x] Make the log feel like a war diary, not a debug feed.
* [x] Allow after-action reports to create log entries automatically.
* [x] Let the log reference previous events when callbacks occur.

---

# 13. War Room Planning Screen

## Purpose

Create one central campaign planning screen that tells the player:

* What is happening?
* What is dangerous?
* What do I need?
* Where should I go?
* What actions are available?
* What changed because of earlier choices?
* Which bases and factions are gaining or losing strength?

## Access

* [x] Add a clear "War Room" button to the campaign map.
* [x] Allow War Room access from stations.
* [x] Allow War Room access after after-action reports.
* [x] Make the War Room pause or slow campaign time while open.
* [x] Make sure it can be closed cleanly.
* [x] Return to the previous screen after closing.

## Current Objective Section

* [x] Show the primary campaign objective.
* [x] Show the current route goal.
* [x] Show the next recommended destination.
* [x] Show optional objectives.
* [x] Explain why the next objective matters.
* [x] Show consequences of ignoring urgent events.
* [x] Show Earth approach progress if relevant.

## Fleet Status Section

* [x] Show mothership hull status.
* [x] Show escort count.
* [x] Show damaged ships.
* [x] Show destroyed or missing ships.
* [x] Show crew condition.
* [x] Show fuel or supply status.
* [x] Show ammo status.
* [x] Show repair capacity.
* [x] Show strike assets available.
* [x] Show fleet readiness rating.

Possible readiness labels:

* [x] Ready.
* [x] Worn.
* [x] Damaged.
* [x] Critical.
* [x] Combat ineffective.

## Regional Situation Section

* [x] Show current region name.
* [x] Show Green control level.
* [x] Show Red pressure level.
* [x] Show Yellow attitude.
* [x] Show known stations.
* [x] Show known hostile fleets.
* [x] Show unknown contacts.
* [x] Show last-known sensor ghosts.
* [x] Show recent regional losses.
* [x] Show active battles in the player's current region.
* [x] Do not show irrelevant recurring battle pop-ups from distant regions unless they are major strategic events.
* [x] Show whether Green is attempting counter-sorties against Red groups.
* [x] Show if local bases are damaged or undersupplied.

## Threat Forecast Section

* [x] Show next predicted enemy intercept.
* [x] Show estimated time until intercept.
* [x] Show confidence level.
* [x] Show enemy fleet type if known.
* [x] Show possible enemy destination.
* [x] Show whether the fleet is actively chasing the player or merely projected to intersect.
* [x] Do not treat ghost contacts as live fleets.
* [x] Do not draw movement vectors from ghost contacts to the player.
* [x] Show clear text when a contact is only a last-known-position shadow.
* [x] Show which enemy base launched the fleet if known.
* [x] Show whether destroying a specific base or convoy could reduce the threat.

## Supplies And Economy Section

* [x] Show player ore.
* [x] Show repair parts.
* [x] Show ammunition.
* [x] Show fuel/supplies if implemented.
* [x] Show current trade opportunities.
* [x] Show nearby stations that can repair, trade, hire, or sell ships.
* [x] Show current price modifiers from reputation or shortages.
* [x] Show local Green ore situation.
* [x] Show known Red ore situation if intel allows.
* [x] Show Yellow trade route status.
* [x] Show production delays caused by raids or shortages.

## Enemy Production And Fleet Economy Section

* [x] Show known enemy shipyards.
* [x] Show suspected enemy construction activity.
* [x] Show known enemy reserves if intel allows.
* [x] Show enemy mining activity.
* [x] Show enemy ore convoy routes.
* [x] Show recent enemy losses.
* [x] Show whether Red can replace current losses quickly.
* [x] Show whether Green is losing the regional production war.
* [x] Show whether Yellow trade traffic is rerouting.

## Recent War Events Section

* [x] Show recent battles.
* [x] Show recent station damage.
* [x] Show recent convoy losses.
* [x] Show recent Green victories.
* [x] Show recent Red advances.
* [x] Show recent Yellow incidents.
* [x] Show mining raids.
* [x] Show ship construction completions.
* [x] Show major fleet losses.
* [x] Link important events to Captain's Log entries.

## War Room Buttons

* [x] Plot Route.
* [x] Inspect Fleet.
* [x] Open Captain's Log.
* [x] Open Repairs.
* [x] Open Trade.
* [x] Request Green Support.
* [x] Launch Strike.
* [x] Contact Nearby Ships.
* [x] Review Known Contacts.
* [x] View Faction Strength.
* [x] View Base Status.
* [x] View Mining Routes.
* [x] Return to Map.

## Button Rules

* [x] Every button should have a tooltip or short description.
* [x] Disabled buttons should explain why they are unavailable.
* [x] Important buttons should explain likely consequences.
* [x] Avoid hidden keybind-only actions.
* [x] Any action the player can take should be represented as a visible button somewhere.

---

# 14. Remembered Consequences

## Purpose

Make the campaign visibly react to what the player does.

The player should regularly see messages, prices, reinforcements, station status, faction behavior, fleet strength, and regional safety change because of earlier actions.

## Campaign Flags To Track

* [x] Convoy saved.
* [x] Convoy abandoned.
* [x] Convoy destroyed.
* [x] Distress call answered.
* [x] Distress call ignored.
* [x] Station defended.
* [x] Station damaged.
* [x] Station destroyed.
* [x] Station repeatedly used for repairs.
* [x] Mining site depleted.
* [x] Mining convoy protected.
* [x] Mining convoy destroyed.
* [x] Salvage site cleared.
* [x] Relay decoded.
* [x] Shipyard attacked.
* [x] Shipyard damaged.
* [x] Shipyard destroyed.
* [x] Yellow ship attacked.
* [x] Yellow ship protected.
* [x] Green ship rescued.
* [x] Green ship abandoned.
* [x] Red fleet destroyed.
* [x] Red fleet escaped.
* [x] Named enemy commander survived.
* [x] Named allied captain survived.
* [x] Player caused civilian casualties.
* [x] Player protected civilian traffic.
* [x] Player overused local supplies.
* [x] Player helped stabilize a region.
* [x] Player allowed a region to collapse.
* [x] Enemy production slowed.
* [x] Green production strengthened.
* [x] Yellow trade route disrupted.

## Consequence Types

* [x] Repair discounts.
* [x] Repair price increases.
* [x] Trade discounts.
* [x] Trade price increases.
* [x] Green reinforcements become available.
* [x] Green reinforcements become unavailable.
* [x] Yellow ships become friendlier.
* [x] Yellow ships become more hostile or evasive.
* [x] Red sends stronger hunter-killer fleets.
* [x] Red pressure temporarily decreases after losses.
* [x] Red pressure increases if Red production is untouched.
* [x] Stations show damaged status.
* [x] Stations lose services after attacks.
* [x] Stations regain services after being defended.
* [x] Rescued captains return later.
* [x] Enemy commanders return later if they escaped.
* [x] Memorial entries appear for lost ships.
* [x] Civilian traffic changes based on safety.
* [x] Enemy construction queues pause if ore is disrupted.
* [x] Enemy fleet sizes shrink if losses cannot be replaced.
* [x] Green patrol density increases if Green mining succeeds.
* [x] Yellow commerce reroutes if danger rises.

## "Because You Did X" Feedback

The game should directly tell the player when something changed because of them.

Examples to support:

* [x] "Because you saved Convoy G-12, Green Station K-12 now offers discounted repairs."
* [x] "Because you abandoned the distress call, no survivors were recovered."
* [x] "Because Yellow traders were hit during the battle, local trade prices increased."
* [x] "Because the Red commander escaped, a stronger interdiction fleet may return later."
* [x] "Because you defended this station, Green patrol activity increased in the region."
* [x] "Because you destroyed the Red mining convoy, Black Furnace Shipyard has paused frigate construction."
* [x] "Because Green miners survived, Green counter-task-force production has resumed."
* [x] "Because the enemy cruiser escaped, it is returning to base for repairs and may sortie again."

---

# 15. Station Memory

## Purpose

Stations should remember damage, player help, shortages, attacks, and service changes.

## Required Work

* [x] Show scarred or damaged station descriptions after attacks.
* [x] Show repaired station descriptions after recovery.
* [x] Track whether a station has been saved by the player.
* [x] Track whether a station has lost ships.
* [x] Track whether a station has lost supplies.
* [x] Track whether the player has overused station repair resources.
* [x] Make station services depend on damage and supply state.
* [x] Add short radio messages from stations that remember the player.
* [x] Make stations mention rescued captains or lost convoys when relevant.
* [x] Make station prices react to reputation and supply state.
* [x] Make station garrisons reflect previous losses.
* [x] Save station state across save/load.

---

# 16. Named Ship And Captain Returns

## Purpose

Make persistent ships and captains feel memorable.

## Named Ship Records

* [x] Track ship name.
* [x] Track faction.
* [x] Track class/type.
* [x] Track current status.
* [x] Track current base or fleet.
* [x] Track battles survived.
* [x] Track kills.
* [x] Track damage taken.
* [x] Track repairs received.
* [x] Track retreats survived.
* [x] Track crew losses.
* [x] Track notable actions.
* [x] Track medals or commendations if desired.
* [x] Track whether the player saved or abandoned the ship.

## Named Captain Records

* [x] Generate or assign names to important rescued captains.
* [x] Save captain survival status.
* [x] Save captain faction.
* [x] Save captain ship assignment.
* [x] Save captain relationship to player.
* [x] Save captain traits.
* [x] Let rescued captains appear later.
* [x] Let grateful captains offer discounts, warnings, supplies, or support.
* [x] Let enemy commanders who escaped appear later.
* [x] Add memorial entries for named captains who die.
* [x] Add after-action notes for notable captain actions.

## Captain Traits

Possible traits:

* [x] Cautious.
* [x] Reckless.
* [x] Loyal.
* [x] Bitter.
* [x] Heroic.
* [x] Nervous.
* [x] Veteran.
* [x] Green.
* [x] Aggressive.
* [x] Defensive.

## Trait Usage

* [x] Mention traits in after-action reports.
* [x] Mention traits in Captain's Log entries.
* [x] Let traits explain behavior in small ways.
* [x] Avoid making traits too mechanically complicated at first.
* [x] Use traits to create emotional flavor, not major balance problems.

Example:

"Captain Hale's reckless charge drew fire away from the convoy, but left the frigate badly damaged."

---

# 17. Tactical Crisis Readability

## Purpose

Make dangerous moments obvious before the player loses ships without understanding why.

The goal is readable panic, not hidden failure.

## Combat Warnings

* [x] Warn when the flagship is in danger.
* [x] Warn when mothership hull is critical.
* [x] Warn when a player capital ship is isolated.
* [x] Warn when command link is failing.
* [x] Warn when point defense ammo is low.
* [x] Warn when missile ammo is low.
* [x] Warn when repair capacity is exhausted.
* [x] Warn when enemy strike craft are inbound.
* [x] Warn when a torpedo strike is incoming.
* [x] Warn when a nuclear strike is incoming.
* [x] Warn when civilian ships are under immediate threat.
* [x] Warn when allied ships are under immediate threat.
* [x] Warn when retreat corridor is open.
* [x] Warn when retreat corridor is closing.
* [x] Warn when a ship is burning.
* [x] Warn when a ship is disabled.
* [x] Warn when a ship cannot maneuver.

## Map Warnings

* [x] Clearly distinguish live fleets from sensor ghosts.
* [x] Ghost contacts should not behave like active fleets.
* [x] Ghost contacts should not show live movement vectors.
* [x] Ghost contacts should show last-known position only.
* [x] Active pursuing fleets should show clear pursuit indicators.
* [x] Do not show false pursuit lines to the player.
* [x] Show predicted intercepts only when based on real live fleet data.
* [x] Show confidence level for uncertain contacts.
* [x] Show if a fleet is coming from a known base.
* [x] Show if a fleet is returning to repair.
* [x] Show if a fleet is escorting a convoy.

## Audio/Visual Language

* [x] Add stronger visual cue for flagship danger.
* [x] Add stronger visual cue for incoming enemy strike.
* [x] Add stronger visual cue for ammo collapse.
* [x] Add stronger visual cue for retreat availability.
* [x] Add stronger visual cue for allied/civilian distress.
* [x] Use consistent colors and icons for warning types.
* [x] Avoid overwhelming the player with too many simultaneous warnings.
* [x] Make warnings actionable whenever possible.

---

# 18. First 90 Minutes Polish

## Purpose

Improve the early campaign experience so a new player understands the game faster.

This should focus on clarity, not difficulty reduction.

## Early Campaign Goals

* [x] Make the first objective extremely clear.
* [x] Make the first safe station obvious.
* [x] Make repair/trade/resupply options easy to find.
* [x] Make the first enemy threat appear on sensors before combat.
* [x] Avoid unexplained enemy pop-ins.
* [x] Make the first convoy or rescue event emotionally clear.
* [x] Make the first mining/economy event easy to understand.
* [x] Make the first after-action report teach the player what happened.
* [x] Make the first War Room visit explain the current situation.
* [x] Make the first Green/Yellow/Red faction differences obvious.
* [x] Make buttons visible for available actions instead of relying on hidden keybinds.
* [x] Show the player that Red fleets come from real bases.
* [x] Show the player that destroying enemy ships matters.

## Early Warnings

* [x] Warn the player before entering a dangerous region.
* [x] Warn the player if they are low on supplies.
* [x] Warn the player if repairs are needed before travel.
* [x] Warn the player if a Red fleet is likely to intercept.
* [x] Explain when a battle can be avoided.
* [x] Explain when a battle is strategically useful.
* [x] Explain when Green or Yellow forces need help.
* [x] Explain when enemy production can be disrupted.

---

# 19. Authored-Feeling Mission Chains From Systemic Events

## Purpose

Create small 2-3 step story arcs from existing campaign events.

These should feel authored but be driven by campaign state.

## Example Chain: Saved Convoy

* [x] Player rescues Green convoy.
* [x] Captain sends thanks in Captain's Log.
* [x] Later, that captain appears at a station.
* [x] Player receives repair discount, supplies, or warning.
* [x] Near Earth approach, the same ship may return as reinforcement.

## Example Chain: Enemy Commander Escapes

* [x] Player damages but fails to destroy Red commander.
* [x] After-action report notes the enemy escaped.
* [x] Red pressure increases later.
* [x] The same commander returns with a stronger fleet.
* [x] Destroying that commander creates a major log entry and regional morale boost.

## Example Chain: Yellow Incident

* [x] Player accidentally hits Yellow civilian ship.
* [x] Yellow reputation decreases.
* [x] Trade prices increase.
* [x] Later, player can repair reputation by escorting or rescuing Yellow ships.
* [x] Yellow services return to normal if trust is restored.

## Example Chain: Red Mining War

* [x] Player discovers Red mining route.
* [x] Player raids Red ore convoy.
* [x] Red shipyard construction slows.
* [x] Red launches escort mission or retaliation force.
* [x] Destroying the shipyard creates a major regional pressure drop.

## Example Chain: Green Counteroffensive

* [x] Player protects Green miners.
* [x] Green shipyard completes new escorts.
* [x] Green launches counter-task force.
* [x] Red raider activity decreases in the region.
* [x] Green station sends thanks or offers repairs.

## Implementation Rules

* [x] Keep mission chains short.
* [x] Reuse existing event types where possible.
* [x] Do not create huge branching questlines yet.
* [x] Focus on callbacks and consequences.
* [x] Make the player feel remembered.
* [x] Save chain state across save/load.

---

# 20. Orbital Layer Later

## Purpose

Keep the orbital layer small, special, and memorable if added later.

This should not come before campaign clarity and finite fleet economy systems.

## Possible Orbital Features

Deferred late-campaign hooks captured for a future orbital pass. These are documented as future scope and no longer block the current campaign clarity, finite-economy, War Room, memory, and callback implementation pass.

* [x] Low orbit sensor shadows.
* [x] Gravity-assisted interception.
* [x] Moon relay fights.
* [x] Evacuation corridors.
* [x] Station-defense silhouettes.
* [x] Orbital debris hazards.
* [x] Special Earth-approach battle conditions.
* [x] Final approach route pressure.
* [x] Late-campaign orbital interdiction.

## Priority Note

* [x] Do not start the orbital layer until after After-Action Reports, War Room, remembered consequences, and finite fleet economy are working.
* [x] Do not let the orbital layer become a huge separate campaign mode yet.
* [x] Treat it as a memorable late-campaign situation, not a full new game.

---

# 21. Defer Scenario / Editor UI

## Purpose

Avoid adding a large editor surface before the campaign alpha feels excellent.

## Defer For Now

* [x] Do not prioritize visual scenario editor yet.
* [x] Do not prioritize full battlefield editor yet.
* [x] Do not prioritize modding UI yet.
* [x] Do not prioritize content-pack editor UI yet.

## Revisit Later When

* [x] Campaign loop is readable.
* [x] Finite fleet economy is stable.
* [x] War Room is functional.
* [x] After-action reports are functional.
* [x] Captain's Log is functional.
* [x] Core fleet behavior is stable.
* [x] First 90 minutes are polished.

---

# 22. Technical Integration Notes

## Suggested Data Structures

* [x] `FactionInventory`
* [x] `FactionShipRecord`
* [x] `BaseRecord`
* [x] `ShipyardQueue`
* [x] `RepairQueue`
* [x] `MiningAssignment`
* [x] `OreConvoy`
* [x] `SortieMission`
* [x] `FleetOriginData`
* [x] `AfterActionReport`
* [x] `CampaignLogEntry`
* [x] `CampaignMemoryFlag`
* [x] `NamedCaptain`
* [x] `NamedShipRecord`
* [x] `RegionalWarStatus`
* [x] `ThreatForecast`
* [x] `ConsequenceResolver`
* [x] `SensorContactRecord`
* [x] `LastKnownContactGhost`

## Save/Load Requirements

* [x] Save faction ship inventories.
* [x] Save active fleets.
* [x] Save docked ships.
* [x] Save damaged ships.
* [x] Save destroyed ships.
* [x] Save ships under construction.
* [x] Save base ore stockpiles.
* [x] Save repair queues.
* [x] Save construction queues.
* [x] Save mining assignments.
* [x] Save ore convoy assignments.
* [x] Save sortie missions.
* [x] Save after-action report history.
* [x] Save Captain's Log entries.
* [x] Save campaign memory flags.
* [x] Save named captain state.
* [x] Save named ship records.
* [x] Save station damage/service state.
* [x] Save faction reputation changes.
* [x] Save regional pressure/control values.
* [x] Save sensor ghosts separately from live fleets.

## UI Rules

* [x] Use readable panels.
* [x] Avoid tiny text.
* [x] Avoid debug-style labels.
* [x] Use clear buttons.
* [x] Use tooltips or short explanations.
* [x] Use disabled buttons with reasons.
* [x] Keep important information visible.
* [x] Avoid forcing the player to remember keybinds.
* [x] Make the UI explain consequences, not just numbers.
* [x] Make all major campaign actions accessible from buttons.
* [x] Make War Room, Captain's Log, and After-Action Reports visually connected.

---

# 23. Anti-Snowball Safeguards

## Purpose

Finite ships are good, but the campaign should not break if one side loses too badly too early.

## Safeguards

* [x] Give major factions several rear bases outside the player's immediate area.
* [x] Allow emergency militia ships, but only from real civilian or industrial bases.
* [x] Allow emergency repairs, but not instant full replacement.
* [x] Allow Red to retreat and regroup instead of fighting to extinction.
* [x] Allow Green to request player help if fleet strength collapses.
* [x] Allow Yellow to evacuate or hire escorts if trade routes become too dangerous.
* [x] Avoid letting factions spend every ship on one doomed attack.
* [x] Add minimum defense reserve rules for important bases.
* [x] Add production caps so factions cannot flood the map.
* [x] Add sortie cooldowns for major bases.
* [x] Add resource constraints so factions cannot rebuild endlessly.
* [x] Add rear-area production so the war continues without feeling fake.

Important rule:

* [x] Emergency fleets are allowed, but they must still come from somewhere.

Bad:

"Spawn 5 Red ships near the player."

Good:

"Black Furnace Shipyard launches its emergency reserve: 2 old frigates and 3 patrol craft. They will arrive in 6 minutes."

---

# 24. Debug And Testing Tools

## Debug Overlay

* [x] Show total ships owned by each faction.
* [x] Show active fleets by faction.
* [x] Show docked ships by base.
* [x] Show damaged ships in repair queues.
* [x] Show ships under construction.
* [x] Show ore stockpiles by base.
* [x] Show mining income per region.
* [x] Show current sortie missions.
* [x] Show fleet origin.
* [x] Show fleet destination.
* [x] Show fleet current mission.
* [x] Show why a faction chose a mission.
* [x] Show why a faction cannot build a ship.
* [x] Show base garrison strength.
* [x] Show regional Red pressure.
* [x] Show regional Green control.
* [x] Show Yellow trade safety.
* [x] Show live contacts versus sensor ghosts.
* [x] Show all currently pending after-action reports.
* [x] Show recent campaign memory flags.

## Testing Scenarios

* [x] Destroy a Red patrol and confirm Red ship count decreases.
* [x] Destroy a Red mining convoy and confirm Red ore income decreases.
* [x] Let Red mine ore and confirm ore stockpile increases.
* [x] Let Red build a new frigate and confirm ore is spent.
* [x] Confirm the new frigate appears at the shipyard, not near the player.
* [x] Confirm Green can launch counter-task forces from real bases.
* [x] Confirm Yellow trade convoys come from real stations.
* [x] Confirm destroyed base garrisons do not instantly respawn.
* [x] Confirm damaged ships return to base for repairs.
* [x] Confirm ships under repair cannot immediately sortie at full strength.
* [x] Confirm destroyed Red shipyards stop building ships.
* [x] Confirm saved Green miners improve Green production.
* [x] Confirm Yellow reputation changes prices and behavior.
* [x] Confirm after-action reports show correct losses.
* [x] Confirm Captain's Log records major events.
* [x] Confirm War Room shows current objective, threat forecast, and regional status.
* [x] Confirm sensor ghosts do not trigger combat.
* [x] Confirm sensor ghosts do not draw pursuit lines.
* [x] Confirm save/load preserves ship pools, ore, bases, queues, fleets, logs, reports, and memory flags.

---

# 25. Recommended Implementation Order

## Step 1: Stop Random Spawns

* [x] Find all random fleet spawn logic.
* [x] Disable unexplained player-proximity spawns.
* [x] Route all new fleets through a faction/base/fleet manager.
* [x] Add checks that prevent fleets from appearing without an origin.
* [x] Fix sensor ghosts so they are never treated as live fleets.

## Step 2: Add Faction Inventories

* [x] Create finite ship pools for Red, Green, and Yellow.
* [x] Assign starting ships to bases and fleets.
* [x] Track destroyed, active, docked, damaged, and under-construction ships.
* [x] Save/load faction inventory state.

## Step 3: Add Base Stockpiles And Garrisons

* [x] Give bases ore storage.
* [x] Give bases docked ships.
* [x] Give bases defense garrisons.
* [x] Give bases repair queues.
* [x] Give shipyards construction queues.
* [x] Make garrison losses persist.

## Step 4: Add Mining And Ore Hauling

* [x] Make miners travel from bases to ore sites.
* [x] Mine ore over time.
* [x] Return ore to base stockpiles.
* [x] Make miners vulnerable to interception.
* [x] Add ore hauler routes between mining hubs and shipyards.

## Step 5: Add Ship Construction

* [x] Add ship costs.
* [x] Add build times.
* [x] Let bases build ships from ore.
* [x] Add completed ships to docked reserves.
* [x] Pause construction if ore is unavailable.

## Step 6: Add Sortie Lifecycle

* [x] Let bases launch patrols, raids, escorts, mining groups, and hunter-killer fleets.
* [x] Make fleets return to base after mission completion.
* [x] Make damaged fleets retreat for repair.
* [x] Make destroyed ships reduce faction inventory.

## Step 7: Add Repair And Recovery

* [x] Track post-battle ship damage.
* [x] Send damaged ships to repair queues.
* [x] Require repair time and resources.
* [x] Prevent instant full-strength reuse.

## Step 8: Add After-Action Reports

* [x] Build `AfterActionReport`.
* [x] Show basic report after battles.
* [x] Include losses, resources, salvage, repairs, and strategic effects.
* [x] Save reports into history.

## Step 9: Add Captain's Log

* [x] Build persistent Captain's Log.
* [x] Convert reports and major campaign events into log entries.
* [x] Add filters and major-event marking.

## Step 10: Add War Room

* [x] Build War Room screen.
* [x] Show objective, fleet status, regional status, threat forecast, supplies, and faction economy.
* [x] Add action buttons.
* [x] Link War Room events to Captain's Log.

## Step 11: Add Remembered Consequences

* [x] Add campaign memory flags.
* [x] Make stations react to saved events.
* [x] Make factions react to saved events.
* [x] Add "because you did X" feedback.
* [x] Make prices, reinforcements, production, and hostility respond to consequences.

## Step 12: Add Named Ship/Captain Callbacks

* [x] Track important captains and ships.
* [x] Let rescued captains return later.
* [x] Let enemy commanders return if they escaped.
* [x] Add memorial entries for losses.

## Step 13: Improve Tactical Crisis Warnings

* [x] Add clearer warnings for flagship danger, ammo collapse, enemy strikes, allied distress, and retreat openings.
* [x] Improve map indicators for live fleets, ghosts, and predicted intercepts.

## Step 14: Polish First 90 Minutes

* [x] Make early objectives clearer.
* [x] Make first threats sensor-visible.
* [x] Show early consequences.
* [x] Teach the player through reports, War Room, and log entries.

---

# 26. Final Acceptance Criteria

This full campaign overhaul should be considered successful when:

* [x] Enemy fleets no longer appear randomly.
* [x] Every fleet has an origin.
* [x] Every fleet has a mission.
* [x] Every fleet has a destination, patrol route, target, or return base.
* [x] Factions start with finite ship inventories.
* [x] Destroyed ships are removed from faction inventories.
* [x] Factions must mine ore to build more ships.
* [x] Mining ships and ore haulers physically operate on the map.
* [x] Shipyards build replacement ships over time.
* [x] Bases launch fleets instead of fleets spawning from nowhere.
* [x] Damaged ships return to bases for repair.
* [x] Base garrisons do not magically regenerate.
* [x] Green, Red, and Yellow behave according to their faction roles.
* [x] Sensor contacts distinguish live fleets from last-known ghosts.
* [x] Ghost contacts do not trigger combat or draw live movement vectors.
* [x] After battles, the player receives a clear after-action report.
* [x] The player can open a Captain's Log and see meaningful campaign history.
* [x] The player can open a War Room and understand the current strategic situation.
* [x] Stations, captains, convoys, and factions can reference earlier events.
* [x] The player sees "because you did X, Y changed" feedback.
* [x] Destroying enemy miners, haulers, and shipyards affects enemy production.
* [x] Defending Green miners and convoys improves Green strength.
* [x] Yellow trade behavior reacts to danger and reputation.
* [x] Crisis warnings are clearer during combat.
* [x] The first 90 minutes are easier to understand.
* [x] No major unrelated scope is added before these systems are stable.

---

# Final Design Statement

The campaign should feel like a living war with memory, logistics, and consequence.

Every ship should exist before it fights.

Every fleet should come from somewhere.

Every loss should matter.

Every victory should change something.

Every major event should be explained to the player.

The goal is not just more simulation.

The goal is visible consequence.


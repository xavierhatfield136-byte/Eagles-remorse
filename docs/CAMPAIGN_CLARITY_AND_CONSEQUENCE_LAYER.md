# Campaign Clarity And Consequence Layer

Date: 2026-06-22
Status: Planning document before implementation

## Implementation Status

First vertical slice landed on 2026-06-22:

- Added persistent After-Action Report history with bounded campaign storage.
- Added persistent Captain's Log entries generated from major report snapshots.
- Added campaign memory flags for "why this matters" consequence callbacks.
- Added a finite-fleet ledger readout derived from authoritative live campaign forces, damaged/retreating forces, destroyed forces, and yard orders.
- Added War Room readout lines for objective, route, recommendation, fleet state, resources, regional situation, threat forecast, fleet economy, recent war events, remembered consequence, and visible actions.
- Added checkpoint save/load fields for report history, Captain's Log, memory flags, and their next ids.
- Added regression coverage for readouts and checkpoint round-trip persistence.

Second vertical slice landed on 2026-06-22:

- Added authoritative finite ship-pool records for Red, Green, and Yellow.
- Added base stockpiles for ore, repair supplies, ammunition, and fuel.
- Added base docked/damaged/destroyed/building counters, garrison counts, mining assignments, convoy assignments, station damage state, service state, and station memory flags.
- Added construction and repair queue records with ore/supply costs, build/repair timers, and checkpoint persistence.
- Added finite-economy reconciliation that ties live campaign forces back to owned hull records.
- Added mining and hauling economy ticks that feed base stockpiles and shipyards.
- Added shipyard construction and repair completion logic that returns hulls to docked reserves.
- Added Base Economy ledger lines in the War Room and Resource panel.
- Added regression coverage for finite pools, base queues, ledger readouts, and checkpoint round-trip persistence.

Remaining major phases still open:

- Build the player-facing War Room/Captain's Log/After-Action Report screens beyond the current line readouts.
- Expand remembered-consequence callbacks to station prices, reinforcements, route safety, named captains, and recurring mission chains.
- Deepen named captain and named ship callbacks beyond the existing fleet service records.
- Polish tactical crisis warnings and first-90-minutes tutorial presentation.

## Purpose

This document turns the master campaign checklist into an implementation-ready plan for the next campaign overhaul.

The goal is to make the campaign feel like a living war with memory, logistics, and consequence. The player should see where fleets came from, why battles happened, what changed afterward, and what they can do next.

This work should improve the systems already present in the game before adding unrelated new scope.

## Core Design Rules

1. No fleets appear from nowhere.
2. Every ship belongs to a faction, base, fleet, convoy, patrol, garrison, reserve, or construction queue.
3. Every fleet has an origin, mission, destination, patrol route, intercept target, or return base.
4. Destroyed ships are removed from the owning faction's available inventory.
5. Newly created ships come from a shipyard, construction queue, emergency reserve, militia pool, or real faction base.
6. Sensor ghosts and last-known tracks are never treated as live fleets.
7. Every major battle creates understandable consequences.
8. The player sees what happened, why it happened, what changed, and which actions are available next.

## Non-Goals

Do not start these before the core clarity and consequence layer is usable:

- Full orbital campaign layer.
- Visual scenario editor.
- Full battlefield editor.
- Modding UI or content-pack editor UI.
- Major new ship-class expansion.
- Another parallel campaign simulation that is not authoritative in normal play.

The orbital layer can return later as a small, memorable late-campaign situation. The editor and modding surfaces should wait until the campaign loop is readable and stable.

## Phase 1: Stop Random Spawns

Purpose: remove unexplained campaign pressure and route all new fleets through real origins.

Checklist:

- Find all player-proximity and random fleet spawn logic.
- Disable unexplained enemy fleets near the player.
- Disable unexplained Red arrivals inside safe Green areas.
- Route new fleets through a faction, base, shipyard, reserve, or reinforcement manager.
- Require every active fleet to record origin, owner, mission, target, and lifecycle state.
- Require every reinforcement to physically travel from a real location.
- Allow emergency fleets only from real bases, reserves, civilian militia, or industrial stations.
- Fix sensor ghosts so they cannot trigger combat or draw live pursuit vectors.
- Show last-known-position shadows without active movement vectors.
- Add validation that rejects active fleets without an origin.

Acceptance:

- Destroying or avoiding one encounter does not cause unrelated ships to appear near the player.
- Every live campaign force can report where it came from and what it is doing.
- Ghost contacts remain informational and never become combat triggers.

## Phase 2: Faction Inventories

Purpose: give Red, Green, and Yellow finite starting ship pools.

Each faction should begin with ships assigned to bases, stations, convoys, patrol groups, garrisons, reserves, shipyards, mining groups, and civilian traffic where appropriate.

Ship state to track:

- Active.
- Docked.
- Damaged.
- Destroyed.
- Under repair.
- Under construction.
- In reserve.
- Assigned to convoy, patrol, garrison, mining, hauling, escort, raid, defense, or support.

Starting pool guidance:

- Red should have major warships, hunter-killer groups, garrisons, miners, haulers, support ships, and rear-area reserves.
- Green should have defensive fleets, patrol ships, convoy escorts, miners, haulers, support ships, station garrisons, and regional counter-task-force reserves.
- Yellow should have trade haulers, civilian convoys, mining ships, ore haulers, station security, private escorts, and limited emergency militia.

Acceptance:

- Destroyed ships reduce the owning faction's inventory.
- Factions cannot exceed their available ship count unless they build or activate real reserve ships.
- Inventory state persists through save/load.

## Phase 3: Bases, Stations, Garrisons, And Shipyards

Purpose: make bases the source of fleet activity, repairs, construction, and local defense.

Base data to track:

- Name, faction owner, type, region, and strategic importance.
- Stored ore, repair supplies, and ammunition if applicable.
- Docked ships, garrison ships, damaged ships, construction queue, and repair queue.
- Local patrol, mining, convoy, escort, and sortie assignments.
- Station damage state, service state, memory flags, and visit history.
- Whether the player saved, ignored, damaged, overused, or helped the station.

Base types:

- Military base.
- Shipyard.
- Heavy shipyard.
- Mining hub.
- Trade station.
- Relay station.
- Repair depot.
- Fortress.
- Civilian port.
- Forward outpost.
- Hidden Red staging base.
- Yellow commercial station.
- Green defensive station.

Service rules:

- Shipyards build ships.
- Heavy shipyards build large warships.
- Repair depots repair damaged ships.
- Mining hubs launch miners.
- Military bases launch patrols, raids, escorts, hunter-killer groups, and counter-task forces.
- Trade stations launch civilian convoys.
- Relay stations improve sensor coverage.
- Fortresses maintain strong defensive garrisons.
- Damaged stations lose services until repaired.
- Destroyed stations stop launching fleets and stop offering services.

Acceptance:

- Base garrison losses persist.
- Destroyed or damaged bases visibly lose capability.
- Reinforcements come from real bases and travel to their destination.

## Phase 4: Ore Economy And Mining War

Purpose: make ship replacement depend on ore, mining, hauling, and stockpiles.

Ore sources:

- Asteroid fields.
- Mining sites.
- Planetary extraction sites if used.
- Salvage fields.
- Captured depots.
- Convoy deliveries.
- Station stockpiles.
- Wreck fields after major battles.

Mining behavior:

- Mining ships launch from mining bases or industrial stations.
- Miners travel to ore sites, mine over time, fill cargo, and return to a friendly base.
- Ore is added to the destination base stockpile.
- Mining ships can request escorts, flee from danger, be intercepted, or be destroyed.
- Destroyed miners reduce future production.
- Mining sites can be depleted and remain depleted across save/load.
- Factions search for new ore if local income falls too low.

Hauling behavior:

- Ore haulers move ore from mining hubs to shipyards.
- Haulers request escorts in dangerous regions.
- Destroyed or captured haulers reduce stockpile transfers.
- Green ore convoys affect Green defense strength.
- Red ore convoys affect Red replacement pressure.
- Yellow ore convoys affect trade and civilian economy.

Acceptance:

- Destroying Red miners or haulers slows Red ship construction.
- Protecting Green miners helps Green build replacements.
- Protecting Yellow traffic improves Yellow trust and trade stability.
- Mining success and losses appear in War Room summaries and major Captain's Log entries.

## Phase 5: Ship Construction

Purpose: newly created ships are built over time at real shipyards using resources.

Ship cost fields:

- Ore cost.
- Build time.
- Required shipyard type.
- Required faction.
- Required crew, supplies, tech, or base size if implemented.
- Maximum production priority.
- Strategic role tag.

Construction queue rules:

- Each shipyard has a limited construction queue.
- A ship starts only if required resources are available.
- Ore is deducted either at construction start or completion, consistently.
- Construction pauses when required resources are missing.
- Completed ships join the base's docked ship pool.
- Newly completed ships can be assigned to patrol, defense, escort, mining, raid, or reserve duty.
- Construction progress persists across save/load.
- Damaged or destroyed shipyards pause or slow construction.

Production personality:

- Red prioritizes hunter-killer replacement, interdiction ships, scouts, miners, escorts, base defense, and larger offensives when logistics allow.
- Green prioritizes patrol replacement, convoy escorts, support ships, threatened stations, miners, and defensive strength.
- Yellow prioritizes trade haulers, miners, escorts, station security, and slow militarization only when threatened.

Acceptance:

- A built ship appears at the shipyard, not near the player.
- Shipyard destruction or ore starvation changes later enemy pressure.
- Production decisions are inspectable in debug tooling.

## Phase 6: Sortie Lifecycle

Purpose: fleets launch from real bases, act, react, and return.

Mission types:

- Patrol local region.
- Escort convoy.
- Mine ore.
- Haul ore.
- Raid enemy mining site.
- Attack enemy station.
- Hunt player.
- Intercept hostile fleet.
- Reinforce allied base.
- Scout unknown area.
- Defend trade route.
- Defend mining route.
- Return for repair.
- Retreat to nearest friendly base.
- Evacuate from collapsing station.
- Establish blockade.
- Break blockade.
- Protect shipyard.
- Sortie counter-task force.

Launch requirements:

- Enough docked ships are available.
- Origin base is active.
- Base keeps a minimum defense reserve when important.
- Mission has a real reason.
- Route risk is acceptable or intentionally desperate.
- Required fuel, supplies, escorts, or readiness are available if implemented.
- The faction is not already overcommitted.
- A better nearby base is not more appropriate.

Lifecycle:

- Assemble fleet from docked ships.
- Assign mission, origin, target, route, and return behavior.
- Leave base and become a live campaign force.
- Travel through the campaign map and become detectable through sensors.
- Perform mission.
- React to danger, opportunity, player movement, and mission changes.
- Retreat if badly damaged.
- Return to base after mission completion.
- Return survivors to docked inventory.
- Send damaged ships to repair queues.
- Remove destroyed ships from inventory.
- Create log entries for important results.

Acceptance:

- A fleet's origin, mission, target, and return plan are visible in debug tooling.
- Damaged fleets do not keep fighting forever at full strength.
- Important sorties create report or log entries when resolved.

## Phase 7: Repair And Recovery

Purpose: damaged ships retreat, repair, recover, or are scrapped instead of instantly returning to full strength.

Required behavior:

- Track damage after manual battles and auto-resolve.
- Send heavily damaged ships to a friendly base or repair depot.
- Add damaged ships to repair queues.
- Repairs take time and cost ore or repair supplies.
- Badly damaged ships may be repaired, scrapped, abandoned, captured, or memorialized depending on outcome.
- Repair progress persists across save/load.
- Destroyed repair depots reduce repair capacity.

Repair priorities:

- Command ships.
- Carriers.
- Cruisers.
- Repair/support ships.
- Miners if ore income is low.
- Convoy escorts if trade routes are dangerous.
- Frigates.
- Pickets.
- Cheap patrol craft.
- Civilian haulers when economically important.

Acceptance:

- After-action reports show repair needs.
- War Room shows damaged player ships and important allied damage.
- Enemy ships that escape damaged can return later after repair.
- Destroying enemy repair capacity matters strategically.

## Phase 8: After-Action Reports

Purpose: every important encounter explains what happened and what changed.

Report triggers:

- Manual tactical battle.
- Auto-resolve battle.
- Retreat.
- Rescue.
- Station attack.
- Convoy loss.
- Mining raid.
- Base defense.
- Major fleet movement outcome.
- Significant campaign event without direct player combat.

Report data:

- Title, location, time, involved factions, and result.
- Victory, defeat, retreat, auto-resolve, unresolved, or partial outcome.
- Player, allied, enemy, and civilian losses.
- Damaged ships and destroyed ships by name/faction when known.
- Ammo, fuel, supplies, repair drones, crew losses, repair cost, and salvage.
- Notable events.
- Strategic effects.
- Available follow-up actions.
- "Why this matters" explanation.

Report screen:

- Summary at top.
- Losses and damage.
- Resources spent and salvage available.
- Strategic consequence.
- Named ship or captain notes.
- Follow-up buttons.

Report buttons:

- Collect salvage.
- Rescue survivors.
- Open repairs.
- View Captain's Log.
- Inspect fleet.
- Return to campaign map.

Acceptance:

- The first post-battle report teaches the player what happened.
- Reports persist into campaign history.
- A report never hides major losses or costs.

## Phase 9: Captain's Log And Campaign Memory

Purpose: persist campaign history and make the world remember the player.

Log features:

- Persistent campaign log.
- Major-event marking.
- Filters by battle, rescue, station, economy, faction, captain, ship, and warning.
- Short flavor text for repeated event types.
- Links from After-Action Reports and War Room.

Events to record:

- Battles.
- Rescued ships.
- Destroyed stations.
- Decoded relays.
- Depleted mining sites.
- Abandoned distress calls.
- Major faction changes.
- Base damage and repairs.
- Notable named ship or captain events.
- Reputation changes.
- Regional pressure changes.

Campaign memory flags:

- Saved convoy.
- Abandoned distress call.
- Green station destroyed.
- Yellow ship attacked.
- Red commander escaped.
- Rescued captain joined or later returned.
- Damaged ship survived multiple battles.
- Station ran out of repair parts.
- Mining site depleted.
- Relay decoded.
- Civilian witness to aggressive action.

Acceptance:

- Important events are readable later from campaign UI.
- Relevant stations, captains, factions, or prices can reference prior events.
- Player-facing text uses "because you did X, Y changed" where helpful.

## Phase 10: War Room

Purpose: provide the campaign's main "what do I do now?" command screen.

Access:

- Add a visible War Room button from the campaign map.
- Keep action buttons visible and readable.
- Disabled buttons explain why they cannot be used.

Sections:

- Current objective and recommended next move.
- Current route and route risk.
- Fleet condition: flagship hull, escorts, damaged ships, missing ships, crew, ammo, fuel, supplies, repair capacity.
- Regional situation: Red pressure, Green control, Yellow trade safety, known enemy fleets, sensor shadows, recent losses.
- Threat forecast: predicted intercepts, contact confidence, origin if known, recommended responses.
- Supplies and economy: ore, repair supplies, market pressure, shortages, and recovery options.
- Enemy production and fleet economy: known shipyards, mining routes, production disruptions, and expected replacement pressure.
- Recent war events from reports and Captain's Log.

Actions:

- Plot route.
- Inspect fleet.
- Request repairs.
- Trade.
- Request reinforcements or support.
- Launch strike.
- View known contacts.
- Open Captain's Log.
- Return to map.

Acceptance:

- The War Room clearly answers current objective, threat, fleet state, regional situation, and next useful actions.
- It translates simulation data into consequences, not only numbers.
- It does not become a debug dump.

## Phase 11: Remembered Consequences

Purpose: make prior player actions change later campaign behavior.

Consequence types:

- Station prices, services, and repair availability.
- Reinforcement availability.
- Patrol support.
- Regional pressure.
- Red retaliation or regrouping.
- Green morale and station defense.
- Yellow trade prices and communication willingness.
- Mission follow-ups.
- Captain callbacks.
- Named ship returns.
- Memorials and scars.

Example consequence text:

- "Because you saved Captain Vale's convoy, Green Station K-12 is offering discounted repairs."
- "Because the Red commander escaped, a stronger hunter-killer group is forming near the northern route."
- "Because Yellow civilian ships were hit, trade prices have increased and neutral ships may refuse communication."

Acceptance:

- At least one rescued convoy, one escaped enemy, one Yellow incident, one mining-war event, and one Green support event can create later visible consequences.
- The consequence is saved, explained, and inspectable.

## Phase 12: Named Ships And Captains

Purpose: make persistent ships and captains memorable without overcomplicating balance.

Named ship records:

- Name, faction, class, status, current base or fleet.
- Battles survived, kills, damage taken, repairs, retreats, crew losses.
- Notable actions, medals, player rescue/abandonment state, and memorial status.

Named captain records:

- Name, faction, ship assignment, survival status, traits, relationship to player.
- Rescue history, warnings given, favors offered, support given, and memorial state.

Trait examples:

- Cautious.
- Reckless.
- Loyal.
- Bitter.
- Heroic.
- Nervous.
- Veteran.
- Green.
- Aggressive.
- Defensive.

Trait rule:

- Use traits for flavor and light behavior explanation first, not heavy balance.

Acceptance:

- Rescued captains can appear later.
- Enemy commanders who escape can return later.
- Major named losses create memorial entries.
- Reports and logs mention notable captain behavior.

## Phase 13: Tactical Crisis Readability

Purpose: make dangerous moments obvious before the player loses ships without understanding why.

Combat warnings:

- Flagship or mothership critical.
- Player capital ship isolated.
- Command link failing.
- Point defense ammo low.
- Missile ammo low.
- Repair capacity exhausted.
- Enemy strike craft inbound.
- Torpedo or nuclear strike incoming.
- Civilian or allied ships under immediate threat.
- Retreat corridor open or closing.
- Ship burning, disabled, or unable to maneuver.

Map warnings:

- Distinguish live fleets from sensor ghosts.
- Show last-known ghosts without live vectors.
- Show pursuit indicators only for real pursuing fleets.
- Show predicted intercepts only from live fleet data.
- Show confidence level for uncertain contacts.
- Show fleet origin, repair return, convoy escort, or mission when known.

Acceptance:

- Warnings are actionable and not overwhelming.
- False pursuit lines do not return.
- The player can understand the difference between live threat, stale contact, and uncertain intel.

## Phase 14: First 90 Minutes Polish

Purpose: use reports, War Room, and memory to teach the campaign clearly.

Early goals:

- First objective is extremely clear.
- First safe station is obvious.
- Repair, trade, and resupply are easy to find.
- First enemy threat appears on sensors before combat.
- First convoy or rescue event has clear emotional stakes.
- First mining/economy event explains why ore matters.
- First report teaches battle consequence.
- First War Room visit explains current situation.
- Faction differences are obvious.
- Available actions use buttons instead of hidden keybinds.
- Red fleets are shown coming from real bases.
- Destroying enemy ships visibly matters.

Acceptance:

- A fresh campaign explains route, threat, resources, station services, and consequences without requiring docs.
- The first hour feels clearer, not easier by default.

## Anti-Snowball Safeguards

Finite inventories should not make the campaign collapse too early.

Safeguards:

- Major factions have rear bases outside the player's immediate area.
- Emergency militia ships are allowed only from real civilian or industrial bases.
- Emergency repairs are allowed, but not instant full replacement.
- Red can retreat and regroup instead of fighting to extinction.
- Green can request player help if fleet strength collapses.
- Yellow can evacuate or hire escorts if trade routes become dangerous.
- Important bases keep minimum defense reserves.
- Factions avoid spending every ship on one doomed attack.
- Production caps prevent map flooding.
- Major bases have sortie cooldowns.
- Resource constraints prevent endless rebuilding.

Rule:

- Emergency fleets are allowed, but they must still come from somewhere.

## Technical Integration Notes

Suggested data structures:

- `FactionInventory`
- `FactionShipRecord`
- `BaseRecord`
- `ShipyardQueue`
- `RepairQueue`
- `MiningAssignment`
- `OreConvoy`
- `SortieMission`
- `FleetOriginData`
- `AfterActionReport`
- `CampaignLogEntry`
- `CampaignMemoryFlag`
- `NamedCaptain`
- `NamedShipRecord`
- `RegionalWarStatus`
- `ThreatForecast`
- `ConsequenceResolver`
- `SensorContactRecord`
- `LastKnownContactGhost`

Save/load requirements:

- Faction ship inventories.
- Active fleets.
- Docked ships.
- Damaged, destroyed, repaired, and under-construction ships.
- Base ore stockpiles and repair supplies.
- Repair queues and construction queues.
- Mining and ore convoy assignments.
- Sortie missions.
- After-action report history.
- Captain's Log entries.
- Campaign memory flags.
- Named captain and named ship state.
- Station damage and service state.
- Faction reputation changes.
- Regional pressure and control values.
- Sensor ghosts separately from live fleets.

UI rules:

- Use readable panels.
- Avoid tiny text and debug-style labels.
- Use clear buttons.
- Use tooltips or short explanations.
- Disabled buttons explain why they cannot be used.
- Keep important information visible.
- Make the UI explain consequences, not just numbers.
- Make major campaign actions accessible from buttons.
- Make War Room, Captain's Log, and After-Action Reports visually connected.

## Debug And Testing Tools

Debug overlay should show:

- Total ships owned by each faction.
- Active fleets by faction.
- Docked ships by base.
- Damaged ships in repair queues.
- Ships under construction.
- Ore stockpiles by base.
- Mining income per region.
- Current sortie missions.
- Fleet origin, destination, and mission.
- Why a faction chose a mission.
- Why a faction cannot build a ship.
- Base garrison strength.
- Regional Red pressure, Green control, and Yellow trade safety.
- Live contacts versus sensor ghosts.
- Pending after-action reports.
- Recent campaign memory flags.

Testing scenarios:

- Destroy a Red patrol and confirm Red ship count decreases.
- Destroy a Red mining convoy and confirm Red ore income decreases.
- Let Red mine ore and confirm stockpile increases.
- Let Red build a frigate and confirm ore is spent.
- Confirm the new frigate appears at the shipyard.
- Confirm Green launches counter-task forces from real bases.
- Confirm Yellow trade convoys come from real stations.
- Confirm destroyed garrisons do not instantly respawn.
- Confirm damaged ships return to base for repairs.
- Confirm ships under repair cannot immediately sortie at full strength.
- Confirm destroyed Red shipyards stop building ships.
- Confirm saved Green miners improve Green production.
- Confirm Yellow reputation changes prices and behavior.
- Confirm after-action reports show correct losses.
- Confirm Captain's Log records major events.
- Confirm War Room shows objective, threat forecast, and regional status.
- Confirm sensor ghosts do not trigger combat or draw pursuit lines.
- Confirm save/load preserves ship pools, ore, bases, queues, fleets, logs, reports, and memory flags.

## Recommended Implementation Order

1. Stop random spawns.
2. Add faction inventories.
3. Add base stockpiles and garrisons.
4. Add mining and ore hauling.
5. Add ship construction.
6. Add sortie lifecycle.
7. Add repair and recovery.
8. Add after-action reports.
9. Add Captain's Log.
10. Add War Room.
11. Add remembered consequences.
12. Add named ship and captain callbacks.
13. Improve tactical crisis warnings.
14. Polish the first 90 minutes.

## Final Acceptance Criteria

This overhaul is successful when:

- Enemy fleets no longer appear randomly.
- Every fleet has an origin, mission, and destination or return behavior.
- Factions start with finite ship inventories.
- Destroyed ships are removed from faction inventories.
- Factions mine ore and use shipyards to build replacements.
- Mining ships and ore haulers physically operate on the map.
- Bases launch fleets instead of fleets spawning from nowhere.
- Damaged ships return to bases for repair.
- Base garrisons do not magically regenerate.
- Red, Green, and Yellow behave according to their faction roles.
- Sensor contacts distinguish live fleets from last-known ghosts.
- Ghost contacts do not trigger combat or draw live movement vectors.
- After battles, the player receives a clear After-Action Report.
- The player can open a Captain's Log and see meaningful campaign history.
- The player can open a War Room and understand the current strategic situation.
- Stations, captains, convoys, and factions can reference earlier events.
- The player sees "because you did X, Y changed" feedback.
- Destroying enemy miners, haulers, and shipyards affects enemy production.
- Defending Green miners and convoys improves Green strength.
- Yellow trade behavior reacts to danger and reputation.
- Crisis warnings are clearer during combat.
- The first 90 minutes are easier to understand.
- No major unrelated scope is added before these systems are stable.

## Final Design Statement

The campaign should feel like a living war with memory, logistics, and consequence.

Every ship should exist before it fights.

Every fleet should come from somewhere.

Every loss should matter.

Every victory should change something.

Every major event should be explained to the player.

The goal is not just more simulation.

The goal is visible consequence.

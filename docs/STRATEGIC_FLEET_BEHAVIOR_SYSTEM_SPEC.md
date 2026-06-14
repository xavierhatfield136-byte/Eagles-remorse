# Strategic Fleet Behavior System Implementation Checklist

Date: 2026-06-13  
Status: Actionable checklist for the next living-war AI pass

## Completion Standard

Do not mark a box complete unless the implementation, UI/debug visibility, persistence, and focused regression coverage all agree.

For every fleet behavior, "done" means:

- The fleet has a source id, mission enum, destination id, target coordinate, current state, stop reason, and next reassignment condition.
- The fleet can explain why it is moving, working, reacting, fighting, recovering, or waiting.
- The fleet has a terminal path: complete, retreat, resupply, repair, merge, dock, destroy, or receive new orders.
- The player-facing or debug UI can show the fleet's mission, state, target, and stop reason.
- Save/load preserves enough data for the fleet to resume the same lifecycle after reload.
- A deterministic test or soak harness proves the behavior does not degrade into unexplained idling.

## Implementation Order Guardrails

- [x] Complete Phase 0 fleet lifecycle data before adding any new faction-specific fleet behavior.
- [x] Complete Phase 0.5 lifecycle validator before adding new mission templates.
- [x] Complete Phase 1A required mission templates before adding Green, Yellow, or Red custom mission logic.
- [x] Complete Phase 2 anti-idle validation before adding advanced POI loops, regional control, invasion behavior, reputation consequences, or multi-fleet sieges.
- [x] Complete Phase 3 early director assignment before depending on faction directors to create raids, escorts, mining jobs, salvage jobs, or response fleets.
- [x] Complete Phase 4 doctrine profiles before tuning faction-specific pursuit, retreat, attack, or flee thresholds.
- [x] Treat Red invasions, relay-control intelligence, Yellow smuggler inspections, regional control values, station service degradation, and full reputation consequences as later work until the minimum viable living war passes.
- [x] Keep UI/debug visibility active during Phases 0-4 so every new mission transition can be inspected while it is being built.

## First Target Scope Lock

Do not implement siege, hunt, blockade, regional control, smuggling, mercenaries, player reputation, station degradation, invasion behavior, or multi-fleet operations until the Minimum Viable Living War Gate passes.

The first working version only needs these systems:

- [x] Patrol mission.
- [x] Mining mission.
- [x] Raid mission.
- [x] Salvage mission.
- [x] Retreat mission.
- [x] Return-to-base / repair-resupply mission.
- [x] Basic distress signal.
- [x] Basic contact memory.
- [x] Basic director assignment.
- [x] Debug overlay or debug report.
- [x] Lifecycle validator.
- [x] Anti-idle reassignment.

## Minimum Viable Living War Gate

This gate must pass before advanced living-war features are started.

- [x] Green patrol loops between three named points and never loses mission, route, state, destination, stop reason, or reassignment condition.
- [x] Yellow miner travels from a named station to a named mining site, mines until cargo is full, returns to a named refinery, unloads, and receives a new mission.
- [x] Red scout launches from a named Red source, discovers the Yellow miner through contact rules, and reports the target to the simple director.
- [x] Red raider launches from a named Red source after scout report and uses an indirect route instead of spawning near the target.
- [x] Yellow miner flees and sends distress when the Red raider enters danger range.
- [x] Green patrol or Green response fleet reacts to the distress without chasing past its operating radius.
- [x] Red-vs-Yellow or Red-vs-Green battle creates a wreck or battle scar.
- [x] Yellow salvage fleet appears or is assigned to the wreck and leaves after salvage cargo is full or the wreck is depleted.
- [x] Every surviving fleet receives a valid next mission after the battle.
- [x] A five-minute post-event soak validates that no surviving non-player fleet is idle without a valid stop reason.

## Priority Split

### Must-Have Before Advanced Behavior

- [x] Fleet lifecycle fields and persistence are implemented.
- [x] Generic mission templates exist for patrol, escort, travel, work-at-POI, raid, hunt, defend, retreat, repair/resupply, and return-to-base.
- [x] Anti-idle validation catches no mission, no destination, no stop reason, expired work timer, missing escort target, empty patrol route, failed retreat target, and post-battle no-order states.
- [x] Green patrol loops work.
- [x] Yellow convoy and mining loops work.
- [x] Red scouts and Red raiders use named sources and warning telemetry.
- [x] Contact memory supports last-known positions and confidence decay.
- [x] Debug overlay or report exposes mission, state, target, stop reason, and anti-idle timer.
- [x] The minimum viable living war gate passes.

### Later After The Foundation Is Stable

- [x] Red invasion fleets.
- [x] Relay control modifying detection and contact confidence.
- [x] Yellow smugglers and Green smuggler inspections.
- [x] Yellow mercenary contract economy.
- [x] Yellow pirates attacking Yellow traders.
- [x] Regional control values altering spawn cadence and traffic mix.
- [x] Station and shipyard service degradation.
- [x] Full player reputation consequences for protected civilian traffic.
- [x] Multi-fleet siege operations and invasion escalation.

## Phase 0: Fleet Mission Lifecycle Core

### Required Now

- [x] Add `workState` lifecycle field to `CampaignForce` with values for `TRAVELING`, `WORKING`, `REACTING`, `FIGHTING`, `RECOVERING`, and `WAITING_WITH_PURPOSE`.
- [x] Add `missionState` lifecycle field to `CampaignForce` with values for `ASSIGNED`, `TRAVELING`, `ARRIVED`, `WORKING`, `COMPLETED`, `FAILED`, `RETREATING`, `RECOVERING`, and `REASSIGNING`.
- [x] Add `stopReason` to `CampaignForce` with explicit values for guarding, mining, salvaging, repairing, refueling, trading, loading, unloading, scanning, hiding, ambushing, blockading, staging, waiting for escort, waiting for reinforcements, recovering, holding line, avoiding superior threat, and none.
- [x] Add `reassignmentCondition` to `CampaignForce` with explicit values for work complete, target destroyed, target missing, route blocked, threat too strong, low fuel, low ammo, low repair capacity, low crew readiness, cargo full, cargo empty, timer expired, and director recall.
- [x] Add `stationaryTimeSec` to `CampaignForce` and increment it only when the fleet's map position changes less than the campaign arrival threshold during an update tick.
- [x] Add `taskDeadlineSec` and `workRemainingSec` to `CampaignForce` so mining, salvage, repair, staging, scanning, hiding, and blockade work all have an explicit timeout and remaining-work value.
- [x] Add `lastTaskUpdateSec` to `CampaignForce` so stale orders can be detected.
- [x] Add `lastStopReasonChangeSec` to `CampaignForce` so repeated stop-reason churn can be debugged.
- [x] Confirm every non-player `CampaignForce` has persisted `homeBaseId`, `destinationLocationId`, mission, route, and target coordinate before it enters simulation.
- [x] Add save serialization for every new lifecycle, supply, cargo, and timing field.
- [x] Add load restoration for every new lifecycle, supply, cargo, and timing field.
- [x] Add a migration fallback so older saves assign default lifecycle data without breaking campaign load.
- [x] Add `CampaignForceSummary` fields for lifecycle state, stop reason, stationary time, task deadline, cargo load, supply levels, risk tolerance, and operating radius.
- [x] Add a focused persistence test that creates a fleet in `WORKING` state with cargo, supply pressure, stop reason, and timer, then save/loads and asserts every field survives.

### Simple Defaults Accepted During First Target

- [x] Add `cargoLoad` and `cargoCapacity` to fleets that trade, mine, salvage, pirate, or transport supplies; initialize both to deterministic values for non-cargo fleets.
- [x] Add `cargoKind` to fleets that trade, mine, salvage, pirate, or transport supplies; initialize to `NONE` for non-cargo fleets.
- [x] Add simplified supply fields to `CampaignForce`: `fuelLevel`, `ammoLevel`, `repairCapacity`, and `crewReadiness`, each stored as a 0-100 value and initialized to 100 for first-pass missions.
- [x] Add `riskTolerance` to `CampaignForce` as a 0-100 value; initialize from faction and fleet kind before adding advanced doctrine tuning.
- [x] Add `operatingRadius` to `CampaignForce`; initialize from home base and fleet kind before adding fuel-aware radius changes.

## Phase 0.5: Fleet Lifecycle Validator

- [x] Implement `validateFleetLifecycle(st, force)` that returns `valid`, `invalidReason`, `blockingField`, and `recommendedFix`.
- [x] Validate that every active non-player fleet has a non-null mission.
- [x] Validate that every active mission has a destination id, target coordinate, route waypoint, work location id, or escorted target id.
- [x] Validate that stationary fleets have a non-`NONE` stop reason.
- [x] Validate that `WAITING_WITH_PURPOSE` cannot use `NONE` as stop reason.
- [x] Validate that `WORKING` fleets have `workRemainingSec > 0`, a `taskDeadlineSec`, or a mission-specific completion condition.
- [x] Validate that `TRAVELING` fleets have a destination id, route waypoint, or target coordinate.
- [x] Validate that `RETREATING` and `RECOVERING` fleets have a safe destination id.
- [x] Validate that escort missions have a living escorted target or a stored failure behavior.
- [x] Validate that mining, salvage, trade, and transport missions have cargo load, cargo capacity, and cargo kind initialized.
- [x] Validate that raid, hunt, and siege missions have timeout and give-up behavior initialized.
- [x] Validate that every fleet can answer `because`, `doing`, and `next` strings for debug output.
- [x] Add debug output line: `LIFECYCLE INVALID: <force> reason=<invalidReason> field=<blockingField> fix=<recommendedFix>`.
- [x] Add a five-minute validator soak test that fails if any lifecycle validator error persists longer than the configured grace period.
- [x] Add a direct validator test for missing mission.
- [x] Add a direct validator test for traveling without destination or route.
- [x] Add a direct validator test for working without timer or completion condition.
- [x] Add a direct validator test for waiting with `NONE` stop reason.
- [x] Add a direct validator test for retreating without safe destination.

## Phase 1A: Required Generic Mission Templates

### Patrol Mission

- [x] Create `assignPatrolMission(st, force, waypointIdsOrPoints)` that sets mission, route, patrol waypoints, lifecycle state, destination, stop reason, and patrol timer.
- [x] On patrol arrival, set lifecycle to `WORKING`, stop reason to `SCANNING`, and work timer to the configured scan duration.
- [x] After scan timer completes, advance to the next waypoint and set lifecycle to `TRAVELING`.
- [x] If a weak hostile contact appears inside patrol response radius, switch to `REACTING` and intercept only while the target remains inside allowed operating radius.
- [x] If the contact retreats beyond safe pursuit distance, return the patrol to its previous waypoint route.
- [x] If patrol supply drops below threshold, switch to `RECOVERING` and route to home base.
- [x] Add a test that Green patrol loops through at least three waypoints, pauses to scan at each, then continues without idling.
- [x] Add a test that Green patrol chases a weak Red scout but stops pursuit before entering strong Red control.

### Raid Mission

- [x] Create `assignRaidMission(st, force, targetAreaOrLocation)` with approach route, ambush point, target preference, timeout, loot behavior, and retreat destination.
- [x] Red raiders must originate from a Red base, hidden depot, staging zone, carrier, jump point, or known task force.
- [x] Raiders must prefer weak convoys, miners, damaged ships, supply depots, relays, and lightly defended POIs.
- [x] Raiders must avoid fair fights unless doctrine says suicide assault.
- [x] If no target appears before raid timeout, reroute to alternate ambush point, request scout data, or return home.
- [x] If raid succeeds, create cargo/loot result, battle scar, or route danger increase before retreating.
- [x] If Green reinforcements arrive, raider switches to retreat unless strength ratio remains favorable.
- [x] Add a test that Red raider launches from a named origin, attacks a weaker Yellow miner, then retreats to a named destination.
- [x] Add a test that Red raider times out and returns instead of sitting at the ambush point forever.

### Mining Mission

- [x] Create `assignMiningMission(st, force, miningSiteId, refineryId)` with outbound route, mining timer, cargo capacity, and return route.
- [x] On arrival at mining site, set lifecycle to `WORKING`, stop reason to `MINING`, and start mining timer.
- [x] Increase cargo load during mining until cargo is full or site is depleted.
- [x] When cargo is full, set lifecycle to `TRAVELING` and route to refinery or trade station.
- [x] If hostile threat enters danger radius, set lifecycle to `REACTING`, stop mining, send distress, and flee to nearest safe station.
- [x] If escort is destroyed, force mining fleet to flee unless local control is strongly friendly.
- [x] Add a test that Yellow mining fleet mines to full cargo and returns to refinery.
- [x] Add a test that Red threat interrupts mining and triggers distress plus retreat.

### Salvage Mission

- [x] Create `assignSalvageMission(st, force, wreckId, returnStationId)` with salvage timer, cargo capacity, and threat reaction.
- [x] Spawn or dispatch Yellow salvage fleets when a battle creates a battle scar or recoverable wreck.
- [x] On arrival at wreck, set lifecycle to `WORKING`, stop reason to `SALVAGING`, and start salvage timer.
- [x] Deplete wreck salvage value as cargo increases.
- [x] If wreck is depleted or cargo is full, route salvage fleet to return station.
- [x] If Green or Red military force contests the wreck, choose flee, bribe, hide, or fight based on fleet type and risk tolerance.
- [x] Add a test that battle aftermath creates a wreck and a Yellow salvage fleet can be assigned to it.
- [x] Add a test that salvage fleet leaves the wreck after cargo is full.

### Return To Base / Repair-Resupply Mission

- [x] Create `assignReturnToBaseMission(st, force, baseId)` that sets lifecycle to `RECOVERING`, destination, route, stop reason, and reassignment condition.
- [x] On arrival at base, set lifecycle to `WORKING`, stop reason to `REPAIRING` or `REFUELING`, and start repair/resupply timer.
- [x] Restore fuel, ammo, repair capacity, and crew readiness during the repair/resupply timer.
- [x] When repair/resupply completes, request a new simple director mission.
- [x] Add a test that low-supply fleet returns to base, restores supply values, and receives a new mission.

### Retreat Mission

- [x] Create `assignRetreatMission(st, force, safeDestinationId)` that sets lifecycle to `RECOVERING`, destination, route, and avoidance behavior.
- [x] Retreating fleets must prefer paths away from known enemies.
- [x] Retreating fleets must move slower if heavily damaged.
- [x] Retreating fleets drop 25% cargo when pursued within engagement radius and `riskTolerance < 35`.
- [x] Retreating fleets must remain interceptable by the player while visible or recently detected.
- [x] On arrival at safe destination, switch to repair, resupply, dock, merge, or new mission.
- [x] Add a test that damaged Red raider retreats and can be intercepted before reaching base.

## Phase 1B: Advanced Generic Mission Templates

### Escort Mission

- [x] Create `assignEscortMission(st, escort, escortedForceId)` that records the escorted force id, escort radius, destination, and failure behavior.
- [x] While escort target travels, keep escort within escort radius and ahead of target when threat confidence is high.
- [x] If a small attacker enters intercept radius, set escort lifecycle to `REACTING` and intercept.
- [x] If attacker strength exceeds escort risk tolerance, keep escort with target and trigger distress instead of chasing.
- [x] If escorted force reaches destination, set escort to return, dock, patrol, or take a new contract.
- [x] If escorted force is destroyed, set escort to recover survivors, retreat, merge with allies, or return to base.
- [x] Add a test that escort remains near convoy for an entire route.
- [x] Add a test that escort refuses to chase a stronger Red fleet away from the convoy.
- [x] Add a test that escort receives valid new orders after convoy arrival.

### Siege Mission

- [x] Create `assignSiegeMission(st, force, stagingPointId, targetLocationId)` with staging timer, required support strength, attack route, fallback route, and blockade follow-up.
- [x] Siege fleet must gather at a staging point before advancing.
- [x] Siege fleet must wait with stop reason `STAGING` until support strength is met or staging timer expires.
- [x] Player map must show warning with source, target, and ETA before siege fleet reaches target.
- [x] On target arrival, set lifecycle to `FIGHTING` or `WORKING` with stop reason `BLOCKADING` based on defense state.
- [x] If victorious and healthy, convert siege mission into blockade or occupation.
- [x] If support is destroyed or fleet is badly damaged, retreat to fallback route.
- [x] Add a test that Red siege fleet stages, advances, warns the player, and either blockades or retreats.

### Hunt Mission

- [x] Create `assignHuntMission(st, force, targetForceIdOrLastKnownContact)` with last-known position, prediction radius, timeout, and give-up behavior.
- [x] Hunter must route to last known or predicted target position, not omniscient live player position.
- [x] If target is reacquired and strength ratio is favorable, engage.
- [x] If target is reacquired but strength ratio is poor, shadow and call reinforcement.
- [x] If target enters strong friendly defenses, break off unless mission is high-priority.
- [x] If hunt timeout expires, return to patrol, raid, or base.
- [x] Add a test that Red hunter follows stale player contact and loses confidence over time.
- [x] Add a test that hunter breaks off when target reaches strong Green defense.

### Repair And Rescue Mission

- [x] Create `assignRepairRescueMission(st, force, damagedAllyId)` with route, repair timer, escort-back destination, and threat reaction.
- [x] Green repair/rescue fleets must avoid combat unless directly threatened.
- [x] On arrival, set stop reason to `REPAIRING` or `RECOVERING_SURVIVORS`.
- [x] Increase damaged ally readiness or strength over time.
- [x] After repair threshold is reached, escort damaged ally to nearest friendly base.
- [x] If enemy threat is nearby, request escort or flee with the damaged ally.
- [x] Add a test that damaged Green fleet calls rescue and rescue fleet stabilizes it.

### Blockade Mission

- [x] Create `assignBlockadeMission(st, force, chokepointOrLocationId)` with hold radius, interdiction radius, reinforcement call behavior, and exit condition.
- [x] Blockade fleet must set lifecycle to `WAITING_WITH_PURPOSE` or `WORKING` and stop reason to `BLOCKADING`.
- [x] Blockade fleet must intercept weak traffic passing through interdiction radius.
- [x] Blockade fleet must call reinforcements when challenged by stronger force.
- [x] Blockade must increase route risk and reduce trade health while active.
- [x] If surrounded or damaged below threshold, blockade fleet retreats.
- [x] Add a test that Red blockade holds a route and increases travel danger without being classified as idle.

## Phase 2: Anti-Idle And Reassignment

- [x] Add `antiIdleTimerSec` to every non-player `CampaignForce`.
- [x] Reset `antiIdleTimerSec` when mission, destination, state, target, route, or valid stop reason changes.
- [x] Use `validateFleetLifecycle(st, force)` as the single source of invalid lifecycle reasons for anti-idle reassignment.
- [x] Treat `mission == null` as invalid unless the fleet is destroyed, docked, or explicitly merged into another force.
- [x] Treat blank `homeBaseId` as invalid for any fleet that is not a temporary local encounter force.
- [x] Treat blank `destinationLocationId` and no numeric target as invalid for traveling, raiding, escorting, mining, salvage, repair, patrol, hunt, and retreat missions.
- [x] Treat `WAITING_WITH_PURPOSE` with `stopReason == NONE` as invalid.
- [x] Treat a fleet that reaches a destination but never enters a work state as invalid after one campaign update tick.
- [x] Treat a fleet stopped with a valid stop reason as invalid when its `taskDeadlineSec` expires and its work completion condition is still false.
- [x] Treat an escort fleet whose escorted target is destroyed, completed, or missing as invalid until it receives return, rescue, merge, or new escort orders.
- [x] Treat a patrol fleet whose waypoint list is empty as invalid unless it is returning to base.
- [x] Treat a raid that finds no target before timeout as invalid until it moves to an alternate ambush point or returns home.
- [x] Treat a retreating fleet with no safe destination as invalid.
- [x] Treat a battle participant that exits battle without repair, retreat, salvage, continue, hold, or destroy state as invalid.
- [x] Treat a fleet stopped at an empty POI without guarding, extracting, raiding, repairing, trading, scanning, hiding, or staging work as invalid.
- [x] Implement `reassignInvalidCampaignForce(ctx, st, force, reason)` with deterministic fallback order: repair if damaged, resupply if low supply, retreat if threatened, return home if route exists, patrol friendly territory, escort vulnerable convoy, investigate unknown contact, guard important POI.
- [x] Add an anti-idle cooldown so a force cannot be reassigned every frame when stuck on the same invalid condition.
- [x] Add an audit log entry whenever anti-idle reassignment changes mission, destination, stop reason, or route.
- [x] Add a debug report line: `IDLE FIX: <force> <reason> -> <new mission> <target>`.
- [x] Add a 10-minute deterministic campaign soak test that fails if any non-player fleet spends more than the allowed stationary time with `stopReason == NONE`.
- [x] Add a test where a fleet reaches a mining POI with no work state and verify anti-idle assigns mining work or a new mission.
- [x] Add a test where an escort target is destroyed and verify the escort returns, merges, rescues, or receives a new mission.
- [x] Add a test where a patrol fleet reaches its final waypoint and verify it loops or returns to base.
- [x] Add a test where a raid finds no target and verify it moves to an alternate ambush point or returns home.
- [x] Add a test where a fleet retreats successfully and verify it transitions to repair or resupply.
- [x] Add a test where a retreating fleet has no target and verify it receives the nearest valid safe base.

## Phase 3: Early Director Assignment

- [x] Implement `assignSimpleDirectorMission(ctx, st, force)` that chooses one mission from patrol, return-to-base, repair/resupply, escort, raid, mine, salvage, trade, defend, and retreat.
- [x] Make simple director assignment run only when a force has no valid mission, completed a mission, failed a mission, or is flagged by anti-idle validation.
- [x] Add Green simple director rule: assign patrol when friendly route lacks patrol coverage.
- [x] Add Green simple director rule: assign escort when a vulnerable friendly or protected neutral convoy has no escort.
- [x] Add Green simple director rule: assign repair/rescue when a friendly fleet is damaged and reachable.
- [x] Add Yellow simple director rule: assign trade when profitable route exists and route risk is below flee threshold.
- [x] Add Yellow simple director rule: assign mining when a resource POI and refinery are both reachable.
- [x] Add Yellow simple director rule: assign salvage when a battle scar or recoverable wreck exists and route risk is acceptable.
- [x] Add Red simple director rule: assign scout when target intel is stale.
- [x] Add Red simple director rule: assign raid when scout intel identifies a weak convoy, miner, depot, relay, or damaged fleet.
- [x] Add Red simple director rule: assign return/repair when a force is damaged or low on supply.
- [x] Add simple director telemetry line: `DIRECTOR SIMPLE: <force> candidates=<jobs> selected=<job> reason=<reason>`.
- [x] Add a test that simple director assigns Green escort before assigning Green assault when a vulnerable convoy exists.
- [x] Add a test that simple director assigns Yellow salvage after a battle scar appears.
- [x] Add a test that simple director assigns Red raid after scout discovers weak Yellow miner.

## Phase 4: Faction Doctrine Profiles

- [x] Add `CampaignFactionDoctrine` data for Green with defensive bias, escort priority, patrol priority, cautious pursuit, high rescue priority, and low civilian-risk tolerance.
- [x] Add `CampaignFactionDoctrine` data for Yellow with profit priority, high flee bias, route preference, salvage priority, smuggling tolerance, mercenary contract bias, and reputation sensitivity.
- [x] Add `CampaignFactionDoctrine` data for Red with aggression bias, scout priority, raid priority, ambush preference, siege capability, hunter priority, and retreat threshold.
- [x] Add fleet-type modifiers so Yellow trader, Yellow pirate, Red scout, Red siege fleet, Green patrol, and Green rescue fleet have distinct risk tolerance.
- [x] Add Green pursuit limit that prevents Green patrols from chasing contacts past configured distance into strong Red control.
- [x] Add Yellow civilian flee threshold that routes traders, miners, and salvagers away from major Red fleets.
- [x] Add Red raider attack threshold that prefers weak targets and rejects fair fights.
- [x] Add Red assault threshold that allows siege or invasion fleets to accept higher losses than raiders.
- [x] Add doctrine summary to debug overlay for selected fleet.
- [x] Add a test that Green chooses guarded pursuit or return instead of deep chase.
- [x] Add a test that Yellow civilian flees from a major Red contact.
- [x] Add a test that Red raider attacks weak target and avoids equal-strength convoy escort.

## Phase 5: Basic Green, Yellow, And Red Fleet Jobs

- [x] Implement one Green patrol job using the generic patrol mission template and Green doctrine.
- [x] Implement one Green escort job using the generic escort mission template and Green doctrine.
- [x] Implement one Green response job using the generic defend or repair/rescue mission template and Green doctrine.
- [x] Implement one Yellow trade job using the generic travel/work/return mission flow and Yellow doctrine.
- [x] Implement one Yellow mining job using the generic mining mission template and Yellow doctrine.
- [x] Implement one Yellow salvage job using the generic salvage mission template and Yellow doctrine.
- [x] Implement one Red scout job using generic patrol/hunt contact logic and Red doctrine.
- [x] Implement one Red raider job using the generic raid mission template and Red doctrine.
- [x] Implement one Red return/repair job using generic retreat and repair/resupply mission templates.
- [x] Add a test that these nine jobs can coexist for five campaign minutes without invalid lifecycle states.

## Phase 6: Green Faction Behavior

- [x] Implement Green patrol route generation between friendly bases, mining sites, relays, trade stations, and shipyards.
- [x] Ensure Green patrols pause to scan at each waypoint for a fixed duration before continuing.
- [x] Ensure Green patrols investigate unknown contacts only inside Green or contested operating radius.
- [x] Ensure Green patrols avoid chasing Red contacts deep into strong Red control.
- [x] Implement Green escort assignment for civilian convoys, mining fleets, repair ships, and transports.
- [x] Implement Green response fleet staging at shipyards, relays, bases, and important hubs.
- [x] Launch Green response fleets when Red attacks a convoy, Red enters protected zone, a friendly base is threatened, a Green fleet is badly damaged, or the player reports contact.
- [x] Make Green response fleets return to staging after emergency ends instead of continuing random pursuit.
- [x] Implement Green assault fleet staging at rally points with required strength and support threshold.
- [x] Make Green assault fleets retreat if badly damaged and hold captured territory if victorious.
- [x] Implement Green repair/rescue dispatch for damaged Green or friendly Yellow fleets.
- [x] Add a test where Green patrol protects a route and lowers route risk after clearing Red pressure.
- [x] Add a test where Green response launches from a named shipyard or relay after Yellow distress.
- [x] Add a test where Green rescue escorts a damaged fleet back to base.

## Phase 7: Yellow Faction Behavior

- [x] Implement Yellow trade convoy routes between stations, trade hubs, mining bases, repair ports, shipyards, and neutral anchors.
- [x] Make Yellow trade convoys reroute away from active battles and strong Red control.
- [x] Make Yellow trade convoys request escort or hire mercenary protection when route risk exceeds threshold.
- [x] Implement Yellow mining fleets with mining cargo loop from station to resource POI to refinery.
- [x] Implement Yellow salvage fleets that appear or dispatch after battle scars.
- [x] Implement Yellow mercenary fleets that can accept escort, defense, and battle-support contracts.
- [x] Make Yellow mercenaries retreat when contract conditions fail or enemy strength exceeds risk tolerance.
- [x] Implement Yellow smugglers that use indirect routes, avoid Green inspections, and hide near asteroid fields or nebulae.
- [x] Implement Yellow pirate fleets that attack weak convoys, miners, damaged ships, and isolated traffic without turning all Yellow globally hostile.
- [x] Make Yellow traffic scatter when a large Red fleet enters the same route lane.
- [x] Make Yellow traffic increase after Green secures a route or the player clears a pirate nest.
- [x] Add a test where Yellow convoy avoids a Red battlefront and chooses a safer alternate route.
- [x] Add a test where Yellow pirate attacks weak Yellow or Green traffic and then retreats.
- [x] Add a test where saving a Yellow convoy improves Yellow reputation or unlocks an intel/trade benefit.

## Phase 8: Red Faction Behavior

- [x] Ensure every new Red fleet originates from a Red base, carrier, staging zone, hidden depot, jump point, or previously detected task force.
- [x] Add player warning stages for major Red fleet launches: early source report, mid-route contact update, final arrival warning.
- [x] Implement Red scout fleets that move ahead of raiders, siege fleets, and hunter groups.
- [x] Make Red scouts avoid combat unless target is extremely weak.
- [x] Make Red scout reports update faction director target priorities.
- [x] Implement Red raider fleets that use indirect routes, attack weak targets, loot or destroy, and retreat.
- [x] Implement Red hunter-killer fleets that pursue high-value targets using scout reports and last-known positions.
- [x] Implement Red siege fleets that stage, advance visibly, pressure bases, and create campaign events.
- [x] Implement Red blockade fleets that hold chokepoints, trade lanes, jump routes, and station approaches.
- [x] Implement Red defense fleets that protect Red bases, reinforce nearby Red forces, and counterattack after raids.
- [x] Implement Red invasion fleets that assemble from smaller groups, capture multiple POIs, establish forward bases, and deploy scouts/raiders outward.
- [x] Add a test that Red major fleet cannot spawn next to player without source and warning telemetry.
- [x] Add a test that Red scout report redirects a Red raider toward a discovered convoy.
- [x] Add a test that Red siege arrival damages or blockades a Green station if ignored.

## Phase 9: Points Of Interest Work Loops

### Mining Sites

- [x] Green fleets at mining sites must guard, escort miners, repair mining station, or clear pirates.
- [x] Yellow fleets at mining sites must mine, load cargo, sell rights, or hire escorts.
- [x] Red fleets at mining sites must raid miners, destroy infrastructure, ambush routes, or capture the site.
- [x] Add a test that no fleet remains at a mining site longer than its guard/extract/raid timer without a valid stop reason.

### Wrecks

- [x] Green fleets at wrecks must recover survivors, salvage military equipment, investigate battle, or secure technology.
- [x] Yellow fleets at wrecks must salvage parts, steal cargo, sell recovered items, or compete with other salvagers.
- [x] Red fleets at wrecks must recover black boxes, destroy evidence, ambush rescue fleets, or loot weapons.
- [x] Add a test that battle-created wrecks attract at least one valid follow-up behavior when nearby fleets exist.

### Stations

- [x] Green station traffic must repair, resupply, guard, escort outbound convoys, or stage response fleets.
- [x] Yellow station traffic must trade, dock, sell information, hire mercenaries, or transfer cargo.
- [x] Red station operations must raid, blockade, infiltrate, bombard, or scout defenses.
- [x] Add a test that station visitors leave with a new mission after dock/trade/repair timer completes.

### Relays

- [x] Green relay work must defend communications, restore damaged relay, or improve scanning.
- [x] Yellow relay work must sell data, smuggle information, or tap communications.
- [x] Red relay work must jam, capture, destroy, or exploit the relay for tracking.
- [x] Add a test that relay control changes contact confidence or detection range.

### Shipyards

- [x] Green shipyards must repair heavy fleets, build escorts, and stage assault/response groups.
- [x] Yellow shipyard traffic must buy repairs, trade parts, and offer contracts.
- [x] Red shipyard operations must attack, blockade, sabotage, or target repair convoys.
- [x] Add a test that shipyard damage reduces repair or launch capacity until repaired.

## Phase 10: Faction Interaction Matrix

- [x] Green patrol can intercept Red scout in Green territory and then resume patrol.
- [x] Red raider can attack Green convoy and trigger Green response.
- [x] Red siege fleet can force Green response fleet launch.
- [x] Green assault fleet can attempt to clear Red blockade.
- [x] Red raider can attack Yellow miners and cause Yellow distress.
- [x] Yellow smugglers can cross Red territory without always becoming combat targets.
- [x] Yellow mercenaries can fight Red when contracted.
- [x] Green patrol can inspect Yellow smuggler without making all Yellow hostile.
- [x] Yellow salvage crew can steal from Green wrecks and create a local reputation or conflict event.
- [x] Yellow pirates can attack Yellow traders, while Yellow mercenaries can defend the traders.
- [x] Add one deterministic test for each interaction above.

## Phase 11: Detection, Contact Memory, And Intel

- [x] Add contact identification levels: unknown contact, size/speed class, probable faction/job, named fleet, composition detail, damaged/retreating detail.
- [x] Make contact confidence improve from proximity, relays, scans, scouts, and faction intel.
- [x] Make contact confidence decay over time instead of disappearing instantly.
- [x] Store `lastKnownX`, `lastKnownY`, `lastKnownVelocityX`, `lastKnownVelocityY`, `lastSeenSec`, and `confidence` for important fleet contacts.
- [x] Make Red hunts and Green responses use last-known or predicted positions rather than exact omniscient locations.
- [x] Make relay control modify local contact confidence and detection radius.
- [x] Add UI labels for partial contacts: `Unknown Contact`, `Fast Small Contact`, `Probable Red Scout`, and named fleet once identified.
- [x] Add a test that stale contact degrades from named fleet to probable contact instead of vanishing.
- [x] Add a test that relay capture improves Red tracking or degrades Green tracking in the region.

## Phase 12: Regional Control

- [x] Add or expose regional control rating values: strong Green, weak Green, contested, weak Red, strong Red, and neutral/Yellow-dominated.
- [x] Make strong Green regions increase Green patrol frequency and Yellow trade traffic.
- [x] Make strong Green regions reduce Red raid frequency except scout or stealth missions.
- [x] Make contested regions increase Green/Red clashes, Yellow caution, raider activity, salvage traffic, and distress calls.
- [x] Make strong Red regions increase Red patrols and blockades, reduce Green traffic, replace normal Yellow trade with smugglers, and raise player risk.
- [x] Make battle outcomes adjust regional control by a small bounded amount.
- [x] Make station damage, blockade, and cleared routes affect regional control.
- [x] Add a test that repeated Red victories move a region toward Red control and change spawned fleet mix.
- [x] Add a test that player clearing Red raiders increases Yellow traffic in that route.

## Phase 13: Battle Aftermath

- [x] After battle, winner chooses one result: continue mission, salvage, capture cargo, hold area, repair, call support, or report victory.
- [x] After battle, loser chooses one result: destroyed, scattered, retreating, distress, survivors, or salvage opportunity.
- [x] Battle must create or update wreck marker when enough tonnage is destroyed.
- [x] Battle must create distress signal when civilians, miners, convoys, or damaged allies survive.
- [x] Nearby Green fleets must choose investigate, reinforce, rescue, or resume route based on distance and mission.
- [x] Nearby Yellow fleets must choose flee, salvage, reroute, or sell intel based on fleet type and risk.
- [x] Nearby Red fleets must choose reinforce, ambush responders, loot, recover evidence, or avoid based on doctrine.
- [x] Battle outcome must adjust route safety, trade health, regional control, and faction intel when relevant.
- [x] Station or shipyard services must degrade if the battle damages that location.
- [x] Add a test that Green/Red battle produces battle scar, control shift, and at least one nearby fleet reaction.
- [x] Add a test that Yellow salvage appears after a battle and leaves after salvage work completes.

## Phase 14: Player Influence

- [x] Destroying Red scouts must reduce Red local contact confidence or raid accuracy.
- [x] Destroying Red scouts must have a chance to trigger replacement scout dispatch instead of instant perfect knowledge.
- [x] Destroying Red raiders must reduce local route danger and allow Yellow traffic to return.
- [x] Ignoring Red siege fleets must damage Green stations, reduce services, shut trade routes, spawn refugees/distress, or expand Red control.
- [x] Saving Yellow convoys must improve Yellow reputation, trade prices, intel offers, or neutral assistance.
- [x] Attacking civilian Yellow traffic must reduce Yellow trust and set nearby Yellow mercenaries hostile when Yellow trust falls below the configured hostile threshold.
- [x] Attacking civilian Yellow traffic must reduce Green opinion when the target has `protectedCivilianTraffic == true` or is escorted by a Green fleet.
- [x] Add a test for each player influence rule above.

## Phase 15: UI And Debug Readability

- [x] Add compact fleet status label format: `<Faction/Confidence> <Fleet Type> - <Current State> - <Target/Destination>`.
- [x] Show partial intel labels before exact names: `Unknown Contact - Moving Fast - Low Intel`, then `Probable Red Scout`, then named fleet.
- [x] Show exact fleet job when identified: `Yellow Trader Convoy - En Route To Shipyard`.
- [x] Show active reaction when relevant: `Green Response Group - Intercepting Red Raider`.
- [x] Show major threat label with ETA: `Red Siege Fleet - Advancing On Green Relay - ETA 09:00`.
- [x] Add fleet explanation tooltip with fields `Because`, `Doing`, and `Next` sourced from lifecycle validator/debug explanation strings.
- [x] Add tooltip example for Yellow mining: `Because: Yellow mining contract. Doing: Mining ore. Next: Return to refinery when cargo full.`
- [x] Add tooltip example for Red raid: `Because: Red scout report found weak miner. Doing: Indirect raid approach. Next: Retreat after attack or timeout.`
- [x] Add tooltip example for Green patrol: `Because: Green patrol route. Doing: Scanning relay. Next: Move to mining site.`
- [x] Add debug overlay fields for mission, lifecycle state, stop reason, target, home base, route, supply, cargo, risk tolerance, and anti-idle timer.
- [x] Add a hidden or debug-only report that lists every fleet whose lifecycle validator currently fails.
- [x] Add a screenshot or render test that verifies long fleet status labels do not overlap nearby map labels at default zoom.

## Phase 16: Advanced Director Scoring

- [x] Implement Green director scoring for route defense, convoy escort, base defense, repair rescue, controlled assault, and player support.
- [x] Implement Yellow director scoring for trade profit, mining profit, salvage opportunity, smuggling route value, mercenary contract value, and piracy opportunity.
- [x] Implement Red director scoring for scouting, raiding weak routes, hunting high-value targets, blockading chokepoints, staging siege, defending Red assets, and invasion escalation.
- [x] Directors must assign missions from regional needs instead of only reacting to local one-off triggers.
- [x] Directors must reserve heavy fleets for siege, assault, invasion, defense, and response instead of random patrol unless explicitly configured.
- [x] Directors must respect fleet operating radius and supply state when assigning missions.
- [x] Add director telemetry line showing top three candidate jobs and the selected job for each new assignment.
- [x] Add a test that Green director assigns escort when vulnerable convoy exists.
- [x] Add a test that Yellow director assigns salvage after battle scar appears.
- [x] Add a test that Red director assigns raid after scout discovers weak convoy.

## Phase 17: Acceptance Scenario

- [x] Create deterministic scenario with one Green relay, one Green base, one Yellow mining site, one Yellow refinery, one Yellow trade route, one Red hidden depot, and one Red frontier lane.
- [x] Spawn Green patrol with patrol route: Green base -> relay -> mining site -> trade station -> Green base.
- [x] Spawn Yellow mining fleet at trade station with mining mission targeting the asteroid field and refinery return.
- [x] Spawn Red scout at Red frontier with scout mission toward the mining lane.
- [x] Verify Green patrol pauses and scans at relay and mining site.
- [x] Verify Yellow miner travels to asteroid field and starts mining work timer.
- [x] Verify Red scout identifies Yellow miner and reports target to Red director.
- [x] Verify Red raider launches from named hidden depot after scout report.
- [x] Verify Red raider uses indirect approach lane rather than spawning near miner.
- [x] Verify Green patrol chases Red scout briefly but does not enter strong Red control.
- [x] Verify Red raider attacks Yellow miner if Green does not intercept in time.
- [x] Verify Yellow miner sends distress and flees.
- [x] Verify Green response launches from relay, base, or shipyard in response to distress.
- [x] Verify player receives readable alert with source, target, ETA or location, and action options.
- [x] Resolve battle and verify wreck marker, route safety change, regional control change, and Yellow traffic reroute.
- [x] Continue simulation for 5 minutes after the event and verify every surviving fleet has valid mission, state, destination, stop reason, and reassignment condition.

## Phase 18: Regression Commands

- [x] Add or update focused tests in `test/CampaignNpcFleetAiTest.java` for mission lifecycle behavior.
- [x] Add or update focused tests in `test/CampaignLivingWarSystemTest.java` for battle aftermath and faction reaction behavior.
- [x] Add or update focused tests in `test/CampaignForceOwnershipTest.java` for source, mission, route, persistence, and tactical handoff.
- [x] Add or update focused tests in `test/CampaignStrategicTravelPressureTest.java` for route danger, blockades, warnings, and player influence.
- [x] Run `.\gradlew.bat test --tests CampaignNpcFleetAiTest`.
- [x] Run `.\gradlew.bat test --tests CampaignLivingWarSystemTest`.
- [x] Run `.\gradlew.bat test --tests CampaignForceOwnershipTest`.
- [x] Run `.\gradlew.bat test --tests CampaignStrategicTravelPressureTest`.
- [x] Run `.\gradlew.bat test --tests Campaign*` before marking the checklist complete.

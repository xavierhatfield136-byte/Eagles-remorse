# Campaign Overmap Fleet Movement

This document describes how fleet units move and make decisions on the campaign overmap as implemented in `src/CampaignSystem.java`.

## Movement Design Rules

The campaign movement system must preserve these guarantees:

1. No unexplained combat spawns.
   Enemy ships should originate from fleets, bases, patrol routes, blockade zones, search groups, known hostile regions, or site defenses.

2. Fleets should have purpose.
   Every active force should have a mission, intent, destination, work state, stop reason, or reassignment condition.

3. Idling must be explainable.
   A stopped fleet should be mining, salvaging, repairing, scanning, blockading, staging, unloading, regrouping, defending, waiting for escort/support, hiding, ambushing, low on supply, damaged, or recovering.

4. Movement should be inspectable.
   Debug tools should show a force's mission, intent, target, route point, stop reason, and reassignment condition.

5. Player prompts should be contextual.
   The player should only be invited into battles or interceptions that are reachable, detectable, or strategically relevant from their current context.

6. Strategic contacts should decay, not vanish instantly.
   Lost enemy contacts should become stale, uncertain, or last-known positions before disappearing.

## Main Concepts

The overmap uses two moving strategic entities:

- `CampaignForce`: the persistent simulated fleets used by the living war layer. These include Blue, Green, Yellow, Red, convoy, mining, patrol, task force, blockade, and local forces.
- `GalaxySearchGroup`: sensor/contact groups used mostly for hostile search and route interception. These are lighter-weight contacts with detection, confidence, and intercept behavior.

The player's overmap position is not the tactical ship position. It is stored on campaign state as `playerGalaxyX`, `playerGalaxyY`, and `playerGalaxyHeadingDeg`. When the player is docked at a campaign location, `ensureGalaxyFleetPosition(...)` keeps the player marker aligned with that location. When the player travels, `updateCampaignTravel(...)` moves the marker toward the selected destination.

`GalaxySearchGroup` should represent incomplete sensor data, search sweeps, or hidden enemy pressure. When it generates a combat encounter, the encounter should be explainable as coming from an existing fleet, site defense, blockade, patrol route, or known hostile region. It should not create unexplained enemies in secure space.

## CampaignForce State

Each `CampaignForce` stores:

- Position: `x`, `y`
- Current movement target: `targetX`, `targetY`
- Movement speed: `speed`
- Route list: `routePoints`
- Current route index: `currentRouteIndex`
- Patrol route list: `patrolWaypoints`
- Mission: `mission`
- High-level intent: `intent`
- Visual/sim state: `state`
- Work state: `workState`
- Mission lifecycle state: `missionState`
- Stop reason: `stopReason`
- Reassignment trigger: `reassignmentCondition`

The important thing: fleet movement is route-driven. Most AI decisions do not directly move a fleet. They assign or rewrite `routePoints`, then `advanceCampaignForcePosition(...)` moves the fleet along those points.

## Main Update Order

Overmap force movement happens through `updateCampaignForceSimulation(...)`.

For every active non-destroyed force, the simulation:

1. Cleans up destroyed membership.
2. Updates cooldowns and strength from live membership.
3. Refreshes NPC contact memory.
4. Runs lifecycle maintenance before orders.
5. Runs mission/order logic through `updateCampaignForceOrders(...)`.
6. Applies anti-idle correction if a force got stuck.
7. Moves the force with `advanceCampaignForcePosition(...)`.
8. Runs lifecycle maintenance after movement.
9. Updates player-visible contact state.
10. Merges detachments back into parent forces when close enough.
11. Forms NPC fleet battles when hostile forces converge.

For performance, far forces do not move every tick. Forces farther than about `5200` overmap units move every fourth simulation tick, and mid-distance forces move every other tick.

High-priority forces should update every tick even if distance-based throttling would normally skip them. A force is high priority when it is near the player, near a hostile force, near its destination, involved in a battle, pursuing, retreating, visible to the player, escorting another force, currently prompting the player, or able to prompt the player soon.

## Route Assignment

`setCampaignForceRoute(force, ...)` is the central route setter.

It clears the existing `routePoints`, appends pairs of coordinates, resets `currentRouteIndex` to zero, sets the first point as `targetX/targetY`, and switches the force into traveling states.

Common route writers include:

- `assignPatrolMission(...)`: loops through patrol waypoints.
- `assignMiningMission(...)`: travels to a resource site, then works there.
- `assignSalvageMission(...)`: travels to a wreck/scar point, then salvages.
- `assignEscortMission(...)`: moves near the escorted force's projected heading.
- `assignRaidMission(...)`: plots a flank point and then the target.
- `assignHuntMission(...)`: moves toward last known hostile contact with drift for uncertainty.
- `assignBlockadeMission(...)`: travels to a chokepoint/location, then holds.
- `assignSiegeMission(...)`: stages first, then advances toward a target location.
- `assignRetreatMission(...)` and `assignReturnToBaseMission(...)`: route to a safe hub or repair base.

## Movement Step

`advanceCampaignForcePosition(...)` performs the actual movement.

If `routePoints` is not empty, the force's current route point becomes `targetX/targetY`. The force then moves toward that target by:

```text
step = min(distanceToTarget, speed * dt)
```

When the force reaches a point:

- Normal routes advance `currentRouteIndex`.
- Repairing, retreating, and regrouping routes clear the route and enter repair/recovery work.
- Patrol routes switch into a short scanning work state.
- Docking routes with cargo switch into unloading work.

This means changing `speed` changes travel pace, while changing route points changes where the fleet goes.

## Mission Work And Reassignment

Fleet units often alternate between movement and work.

Examples:

- Mining ships travel to a resource site, then enter `WORKING` with `stopReason = MINING`.
- Salvagers travel to a wreck, then enter `WORKING` with `stopReason = SALVAGING`.
- Blockade forces travel to a target, then enter `WAITING_WITH_PURPOSE` with `stopReason = BLOCKADING`.
- Siege forces may stop at staging points until enough support is nearby.

Timers such as `workRemainingSec`, `taskDeadlineSec`, and `intentTimerSec` determine when a force should complete work, seek a new assignment, retreat, or re-plan.

The anti-idle system should immediately reassign invalid idle states. Invalid idle means a force has no route, no work, no target, no stop reason, and no reassignment condition. Valid idle explanations include `MINING`, `SALVAGING`, `REPAIRING`, `BLOCKADING`, `SCANNING`, `UNLOADING`, `STAGING`, `REGROUPING`, `GUARDING`, `WAITING_FOR_ESCORT`, `WAITING_FOR_REINFORCEMENTS`, `RECOVERING`, and other explicit stop reasons.

## Contacts And Visibility

`updateCampaignForceContactState(...)` controls whether the player can see a force on the overmap.

Friendly and player forces are generally known. Enemy force visibility depends on:

- Distance to `playerGalaxyX/playerGalaxyY`
- Campaign intel level
- Relay coverage
- Force stealth rating
- Last known age and uncertainty radius

When an enemy leaves sensor range, its confidence decays and its contact state can become `STALE`.

NPC fleets also maintain their own contact memory through `refreshNpcForceContacts(...)`. That memory drives hunts, raids, escorts, retreats, and pursuit decisions.

## Player Warning Levels

Interception should escalate through readable warning states before the player is committed to combat:

1. Unknown Contact
   A contact is detected, but faction/type is unclear.

2. Probable Hostile
   Movement, heading, or signal profile suggests enemy activity.

3. Intercept Course
   The contact appears to be closing with the player's route.

4. Combat Imminent
   The contact is within engagement range or will intercept soon.

5. Encounter Prompt
   The player may evade, negotiate where supported, auto-resolve, support-strike, observe, or take command depending on encounter type.

This keeps overmap danger fair. The player should usually understand why a fight is happening before control is taken away.

## Battle Formation

NPC battles are formed in `resolveNpcFactionFleetBattles(...)`.

Two active non-player forces can form a `CampaignBattle` when:

- Both are alive and simulation-active.
- They are hostile to each other.
- They are within about `190` overmap units.
- Neither is already in a battle.
- Their engagement cooldowns have expired.

The battle then resolves over time in `updateCampaignBattles(...)`. Nearby forces can join as reinforcements, attrition is applied at the decisive stage, and salvage/reaction missions can be spawned afterward.

Player intervention prompts are separate from battle simulation. Battles can keep happening anywhere, but the player should only be invited when the battle is reachable from the player's current context.

## Battle Aftermath

NPC battles should leave campaign evidence even when the player ignores them. Good aftermath outputs include:

- Wreck fields
- Distress calls
- Damaged retreating fleets
- Salvage missions
- Rescue opportunities
- Red pursuit groups
- Green reinforcement orders
- Yellow traffic reroutes
- News/log entries
- Sensor Net loss log entries

Example Sensor Net lines:

- `Green Patrol Kestrel lost 2 pickets near Relay 7.`
- `Red Raid Group Ashknife retreating north-east.`
- `Yellow convoy diverted from Ironwake Station.`
- `Salvage signature detected at battle site.`

## Player Travel

Player overmap travel is handled by `updateCampaignTravel(...)`.

When `galaxyTravel.traveling` is true:

- Fuel, supplies, and ammo attrition accrue.
- Fleet strain changes based on regional pressure and posture.
- `playerGalaxyX/Y` move toward `galaxyTravel.targetX/Y`.
- Progress is calculated from remaining distance.
- Transit signals may reveal contacts or hidden sites.
- On arrival, the campaign docks at the destination and either opens a hub, resolves an area of interest, or prompts for a local encounter.

The player travel system is separate from `CampaignForce` movement, but simulated fleets use the player's overmap position for detection, route warnings, and proximity prompts.

## GalaxySearchGroup Movement

`GalaxySearchGroup` objects are hostile search/interception contacts. They store:

- Position: `x`, `y`
- Target: `targetX`, `targetY`
- Speed and detection ranges
- Search radius
- Behavior and doctrine
- Track integrity and contact confidence
- Anchor location id

They are updated by the galaxy detection/interception path. If a hostile group closes inside its intercept range, `updateGalaxyDetectionAndInterception(...)` opens a strategic encounter prompt or folds the contact into a site defense encounter when appropriate.

## Faction Movement Personalities

Green fleets should feel defensive, organized, and territorial.

- Patrol around bases and trade lanes.
- Escort convoys.
- Respond to Red raids.
- Reinforce nearby battles.
- Fall back to repair hubs when damaged.
- Avoid chasing too far into hostile space unless assigned an offensive operation.

Yellow fleets should feel civilian, cautious, and economically driven.

- Move between mining sites, stations, trade hubs, and repair ports.
- Avoid known battle zones.
- Flee Red contacts.
- Request escort when threat rises.
- Become neutral/friendly when the player uses `State Intent`.
- Only become hostile when attacked, threatened, or aligned with a hostile faction.

Red fleets should feel predatory and operational.

- Send scouts ahead.
- Raid weak convoys.
- Blockade chokepoints.
- Hunt the player when detected.
- Reinforce active battles.
- Retreat when damaged.
- Stage before major sieges.
- Increase strength closer to Earth/northern regions.

Red should not simply attack the nearest player marker. Red should attack when it has reason, intel, and opportunity.

## Regional Pressure Inputs

CampaignForce and GalaxySearchGroup behavior should be connected through regional pressure values. Useful pressure inputs include:

- `regionalRedPressure`
- `regionalGreenControl`
- `regionalYellowTraffic`
- `relayCoverage`
- `recentBattleHeat`
- `playerNoise`

Expected effects:

- High Red pressure creates more search groups, raids, and blockades.
- High Green control strengthens patrols and makes trade safer.
- High player noise makes Red hunt missions more likely.
- Low relay coverage makes contacts become uncertain faster.
- Recent battle heat attracts salvagers, reinforcements, and scouts.

This makes the map react to events instead of feeling random.

## Debug Overlay Needs

Each overmap force should be inspectable. The debug readout should show:

- Force id/name
- Faction
- Mission
- Intent
- State
- Work state
- Mission state
- Current route index
- Current target
- Speed
- Strength
- Stop reason
- Reassignment condition
- Time remaining on current work/order
- Last hostile contact
- Whether it can form or join a battle
- Whether it can prompt the player

This is the primary debugging path for questions like `why is this fleet sitting here?` and `why did this enemy intercept me?`.

## Safe Places To Change Behavior

To change how fleets move:

- Adjust base speeds in `campaignForceBaseSpeed(...)` or template speed in `applyFleetTemplate(...)`.
- Change route shapes inside mission assignment methods such as `assignRaidMission(...)`, `assignEscortMission(...)`, or `assignSiegeMission(...)`.
- Change pursuit, retreat, and support thresholds in the maintain methods such as `maintainRaidMission(...)`, `maintainEscortMission(...)`, and `maintainBlockadeMission(...)`.
- Change battle frequency by adjusting the distance/cooldown rules in `resolveNpcFactionFleetBattles(...)`.
- Change player prompt eligibility in `campaignBattleCanPromptPlayer(...)`.
- Change debug visibility by expanding the campaign force debug/tooltip readouts rather than changing movement logic.

Avoid changing `advanceCampaignForcePosition(...)` unless the core movement model needs to change for every overmap fleet.

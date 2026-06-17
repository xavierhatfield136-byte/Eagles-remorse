# Campaign Redesign Checklist

Use this checklist to move the campaign from a 24-node linear mission spine into a four-zone strategic war theater.

## Design Goals

- [x] The campaign map should feel like a war theater, not a ladder of mission dots.
- [x] Zone ownership should be readable at a glance without relying on names.
- [x] Facilities should create missions, fleets, resources, services, and strategic consequences.
- [x] Green and Yellow support should visibly grow as the player helps them.
- [x] Red pressure should feel active, not static.
- [x] The Earth battle should feel like the result of the entire campaign, not a standalone final mission.

## MVP Slice

Use this as the first playable slice before expanding the whole system. Treat this section as the implementation priority until every MVP item works; later phases should not pull development away from this slice unless they directly support it.

- [x] Keep old `poi-XX` IDs internally.
- [x] Add four zone definitions.
- [x] Relabel/reposition existing main POIs as facilities in those zones.
- [x] Add facility ownership and type metadata.
- [x] Add basic facility icons.
- [x] Add basic zone tinting.
- [x] Replace the visual route spine with local route clusters.
- [x] Add basic Green and Yellow mission board entries.
- [x] Let mission completion adjust Green/Yellow favor and Red hostility.
- [x] Make one facility ownership flip work end-to-end.
- [x] Make one support reward visibly spawn allied help.
- [x] Keep Earth boss reachable through the compatibility path until the new progression is stable.

## Recommended Build Order

- [x] Four zones.
- [x] Facility ownership.
- [x] Facility icons.
- [x] Zone tinting.
- [x] Fleet generation from facilities.
- [x] Fleet movement.
- [x] Intelligence/fog-of-war discovery.
- [x] Mission boards.
- [x] Reputation.
- [x] Ownership flips.
- [x] Dynamic war pressure.
- [x] Final battle modifiers.

Mission boards should come after the first fleet layer if possible, because missions will feel stronger when generated from actual map state instead of a fixed mission list.

## Phase 0 - Audit And Compatibility

- [x] Find every place that assumes `poi-01` through `poi-24`.
- [x] Separate UI-only references from save/load, AI, mission flow, tests, and boss progression references.
- [x] Keep old `poi-XX` IDs temporarily for compatibility.
- [x] Do not delete the old structure until the new zone map is stable.
- [x] Add a migration layer so old saves can load into the new map.
- [x] Create a reference table mapping old POIs to new facility roles.
- [x] Mark each old POI reference as one of:
  - [x] Must preserve
  - [x] Can migrate
  - [x] UI-only
  - [x] Test-only
  - [x] Delete later
- [x] Add a feature flag or compatibility mode if needed so the old campaign can be compared during development.
- [x] Define rollback criteria before removing any old progression code.

Phase 0 audit notes live in `docs/CAMPAIGN_REDESIGN_PHASE0_AUDIT.md`.

## Phase 1 - Four War Zones

- [x] Create four horizontal campaign zones:
  - [x] Southern Zone: Green controlled.
  - [x] Lower-Middle Zone: Yellow controlled / pressured.
  - [x] Upper-Middle Zone: Red occupied.
  - [x] Northern Zone: Red core / Earth approach.
- [x] Give each zone bounds.
- [x] Give each zone a dominant faction.
- [x] Give each zone a danger level.
- [x] Give each zone a node density.
- [x] Give each zone fleet behavior.
- [x] Give each zone allowed facility types.
- [x] Give each zone default mission types.
- [x] Give each zone a control color.
- [x] Give each zone a short map label.
- [x] Give each zone starting pressure values for Green, Yellow, and Red.
- [x] Define escalation rules for each zone.
- [x] Define what makes each zone feel different in play.

### Regional War Pressure

- [x] Treat influence as the primary war-state variable.
- [x] Treat ownership as the secondary formal control result of influence and mission outcomes.
- [x] Track `greenInfluence` per zone.
- [x] Track `yellowInfluence` per zone.
- [x] Track `redInfluence` per zone.
- [x] Use influence values to drive mission generation.
- [x] Use influence values to drive fleet spawns.
- [x] Use influence values to drive reinforcements.
- [x] Use influence values to drive attacks and raids.
- [x] Use influence values to drive ownership pressure.
- [x] Use influence values to drive zone/map coloring.
- [x] Keep influence values understandable; avoid hidden math that cannot be explained in UI.

### Suggested Influence Thresholds

- [x] 95 influence: firm control.
- [x] 60 influence: occupied/held.
- [x] 40 influence: contested.
- [x] 20 influence: losing control.
- [x] 5 influence: nearly liberated or nearly collapsed.

### Suggested Starting Influence

- [x] Southern Zone: Green 85, Yellow 10, Red 5.
- [x] Lower-Middle Zone: Green 25, Yellow 55, Red 20.
- [x] Upper-Middle Zone: Green 5, Yellow 15, Red 80.
- [x] Northern Zone: Green 0, Yellow 5, Red 95.

### Zone Starting Intent

- [x] Southern Zone should teach the player the war map safely.
- [x] Lower-Middle Zone should focus on Yellow pressure, rebellion, and civilian risk.
- [x] Upper-Middle Zone should feel like Red occupied territory, with patrols, garrisons, and resource extraction.
- [x] Northern Zone should feel like Red core territory, with elite fleets, dreadnought yards, major fortresses, Earth defenses, and final-battle pressure.

## Phase 2 - Facility Node System

- [x] Create facility/node types:
  - [x] Fortress
  - [x] Resupply base
  - [x] Mining operation
  - [x] Repair yard
  - [x] Shipyard
  - [x] Relay
  - [x] Listening post
  - [x] Civilian hub
  - [x] Rebel hideout
  - [x] Blockade
  - [x] Prison camp
  - [x] Fuel depot
  - [x] Derelict battlefield
  - [x] Sensor tower
  - [x] Boss staging area
- [x] Each node should track owner faction.
- [x] Each node should track node type.
- [x] Each node should track position.
- [x] Each node should track defense strength.
- [x] Each node should track resource value.
- [x] Each node should track services.
- [x] Each node should track mission tags.
- [x] Each node should track garrison/fleet links.
- [x] Each node should track whether it can change owner.
- [x] Each node should track whether it can spawn fleets.
- [x] Each node should track discovered/hidden state.
- [x] Each node should track destroyed state.
- [x] Each node should have a stable ID independent of display name.
- [x] Each node should have a zone ID.
- [x] Each node should have an intel level.
- [x] Each node should have an intel confidence state.
- [x] Each node should have a strategic value rating.
- [x] Each node should have a current alert/escalation state.
- [x] Each node should have optional links to nearby routes.
- [x] Each node should have a short display label and a longer detail string.
- [x] Avoid hard-coding faction ownership from display names.

### Facility Importance

- [x] Give every facility a `strategicValue` rating from 1 to 5 stars.
- [x] 1-star examples: small mine, minor listening post, small depot.
- [x] 3-star examples: repair yard, relay station, regional mining operation.
- [x] 5-star examples: major fortress, capital shipyard, Earth defense array.
- [x] Use strategic value to prioritize generated missions.
- [x] Use strategic value to weight fleet defense and enemy escalation.
- [x] Make strategic value visible in hover/selection cards.

### Suggested Data Model Fields

- [x] `id`
- [x] `legacyPoiId`
- [x] `zoneId`
- [x] `name`
- [x] `type`
- [x] `ownerFaction`
- [x] `controlState`
- [x] `x`
- [x] `y`
- [x] `defenseStrength`
- [x] `resourceValue`
- [x] `strategicValue`
- [x] `services`
- [x] `missionTags`
- [x] `linkedFleetIds`
- [x] `canChangeOwner`
- [x] `canSpawnFleets`
- [x] `discovered`
- [x] `destroyed`
- [x] `intelLevel`
- [x] `intelConfidence`
- [x] `lastChangedAt`

## Phase 3 - Replace Linear Map Presentation

- [x] Stop presenting the map as 24 ordered mission dots.
- [x] Reposition/relabel existing POIs as facilities inside the four-zone map.
- [x] Add many smaller facilities around the major ones.
- [x] Remove the visual main-path spine.
- [x] Replace the spine with local zone routes.
- [x] Replace the spine with supply lines.
- [x] Replace the spine with contested lanes.
- [x] Replace the spine with blockade lines.
- [x] Keep player-plotted routes distinct from static map routes.
- [x] Color routes by danger and faction control.
- [x] Make major facilities visible at far zoom.
- [x] Show minor facilities only at medium/close zoom or when selected/mission-relevant.
- [x] Add cluster behavior for busy areas.
- [x] Preserve click/selection behavior for existing map actions.
- [x] Ensure route planning still works without the old linear path.

## Phase 4 - Fleet Simulation

- [x] Every ship should belong to a fleet.
- [x] Every fleet should belong to a faction.
- [x] Every fleet should have a home facility.
- [x] Every fleet should have an objective.
- [x] Every fleet should have a fuel/supply state.
- [x] Every fleet should have a current route.
- [x] Every fleet should have a current task.
- [x] Facilities should generate fleets based on owner, type, strategic value, and zone influence.
- [x] Fleets should be able to patrol.
- [x] Fleets should be able to escort.
- [x] Fleets should be able to reinforce.
- [x] Fleets should be able to raid.
- [x] Fleets should be able to attack.
- [x] Fleets should be able to retreat.
- [x] Fleets should be able to resupply.
- [x] Green, Yellow, and Red fleets should be able to fight without player involvement.
- [x] Facility ownership changes should influence future fleet generation.
- [x] Fleet losses should influence regional control and war pressure.
- [x] Fleet behavior should be visible enough that the player understands why a zone is changing.
- [x] Keep the first version simple: facility-spawned patrols, one route, one task, and one visible outcome.

### Fleet Acceptance Criteria

- [x] At least one Green facility can spawn a Green fleet.
- [x] At least one Yellow facility can spawn a Yellow fleet.
- [x] At least one Red facility can spawn a Red fleet.
- [x] A fleet can move between two facilities.
- [x] A fleet can affect local influence or route danger.
- [x] Fleet state persists through save/load.
- [x] Fleet simulation remains deterministic for a given campaign seed.

## Phase 5 - Intelligence And Fog Of War

- [x] Add an `intelLevel` for facilities.
- [x] Add an `intelLevel` for fleets.
- [x] Allow hidden facilities to exist.
- [x] Allow hidden fleets to exist.
- [x] Unknown intel should show that something may be present without revealing facility type.
- [x] Partial intel should reveal facility type and a rough strength estimate.
- [x] Good intel should reveal facility type, fleet activity, and mission opportunities.
- [x] Full intel should reveal exact defenses, exact fleet composition, strategic value, and high-confidence mission hooks.
- [x] Intel should decay or become stale when Red escalates or fleets move.
- [x] Mission generation should respect intel; do not offer precise strikes against targets the player has not discovered.
- [x] Fog of war should create exploration and uncertainty, not hide required progression.

### Ways To Gain Intel

- [x] Recon flights.
- [x] Friendly relays.
- [x] Sensor towers.
- [x] Allied reputation.
- [x] Patrol fleets.
- [x] Listening posts.
- [x] Captured facilities.
- [x] Rescued civilians or prisoners.
- [x] Yellow rebel reports.
- [x] Green command briefings.

### Intel Acceptance Criteria

- [x] Hidden facilities exist.
- [x] Hidden fleets exist.
- [x] Intel can improve through player action or allied support.
- [x] Improved intel changes what missions can generate.
- [x] Improved intel changes hover/selection card detail.
- [x] The player can distinguish unknown, partial, good, and full intel visually.
- [x] The campaign remains completable without perfect intel.

## Phase 6 - Faction Mission Boards

- [x] Add Green mission board.
- [x] Add Yellow mission board.
- [x] Green missions should include:
  - [x] Attack Red positions.
  - [x] Defend Green bases.
  - [x] Escort Green convoys.
  - [x] Capture mining sites.
  - [x] Liberate Yellow settlements.
  - [x] Destroy Red scouts.
- [x] Yellow missions should include:
  - [x] Rebel against Red.
  - [x] Escort refugees.
  - [x] Sabotage Red facilities.
  - [x] Rescue prisoners.
  - [x] Defend civilian hubs.
  - [x] Protect mining operations.
- [x] Keep Red hostile for now.
- [x] Mission board entries should come from facility state, not a fixed list only.
- [x] Missions should show faction, target, risk, reward, time pressure, and expected opposition.
- [x] Missions should clearly preview reputation changes.
- [x] Mission failure should have consequences.
- [x] Urgent missions should expire or worsen if ignored.
- [x] Avoid offering impossible missions against hidden/unreachable targets.
- [x] Prefer generating missions from facility state, fleet activity, regional pressure, and known intel.

### Mission Board Acceptance Criteria

- [x] At least three Green missions can generate from current facility/fleet/intel map state.
- [x] At least three Yellow missions can generate from current facility/fleet/intel map state.
- [x] Completing a Green mission changes Green favor/reputation.
- [x] Completing a Yellow mission changes Yellow favor/reputation.
- [x] Completing anti-Red missions increases Red hostility or reduces Red control.
- [x] Mission board UI remains readable at 1280x720.

## Phase 7 - Reputation And Support

- [x] Reuse or expand `greenContractFavor`.
- [x] Reuse or expand `yellowLiberationFavor`.
- [x] Add or derive `redHostility`.
- [x] Add clear UI readouts:
  - [x] Green reputation
  - [x] Yellow reputation
  - [x] Red hostility
- [x] Add support thresholds:
  - [x] Discounts
  - [x] Supply drops
  - [x] Temporary escorts
  - [x] Rebel strike groups
  - [x] Permanent allied ships
  - [x] Capital reinforcements
  - [x] Better intel
- [x] Make support visible and satisfying, not just hidden numbers.
- [x] Add named support tiers for Green.
- [x] Add named support tiers for Yellow.
- [x] Add visible reward callouts when a tier is reached.
- [x] Make high support affect the map and battle, not only shop prices.
- [x] Ensure support cannot snowball so hard that early campaign becomes trivial.

### Suggested Support Tiers

- [x] Tier 0: Unknown fleet
- [x] Tier 1: Discounts and small supply drops
- [x] Tier 2: Temporary escorts and better intel
- [x] Tier 3: Strike groups and emergency reinforcement
- [x] Tier 4: Permanent allied ships and capital support
- [x] Tier 5: Final battle coalition commitment

## Phase 8 - Dynamic Ownership

- [x] Missions can flip facility ownership.
- [x] Red can expand if ignored.
- [x] Green can reinforce if helped.
- [x] Yellow can rebel if supported.
- [x] Captured mining sites improve resources.
- [x] Captured relays improve intel.
- [x] Captured shipyards improve reinforcement quality.
- [x] Destroyed Red fortresses reduce Red regional control.
- [x] Ownership changes should produce a recent event ping.
- [x] Ownership changes should update route danger.
- [x] Ownership changes should update mission board generation.
- [x] Ownership changes should update nearby fleet behavior.
- [x] Ownership changes should persist through save/load.
- [x] Contested ownership should be a real state, not just a label.
- [x] Add cooldowns so facilities do not flip too rapidly.

## Phase 9 - Enemy Escalation

- [x] Red should begin reacting before the player reaches Earth.
- [x] Red should launch counteroffensives against allied facilities.
- [x] Red should reinforce shipyards when threatened.
- [x] Red should upgrade fortresses as the player gains support.
- [x] Red should send assassin/hunter fleets after the player.
- [x] Red should target high-value Green and Yellow facilities.
- [x] Red should increase patrol density in contested zones.
- [x] Red escalation should be driven by player success, lost Red assets, and rising allied support.
- [x] Escalation should be visible through alerts, map events, and changed fleet behavior.
- [x] Avoid making escalation feel punitive; it should make the war feel reactive.

## Phase 10 - Final Earth Buildup

- [x] Keep Earth boss in the Northern Red Core.
- [x] Final battle support should depend on Green reputation.
- [x] Final battle support should depend on Yellow reputation.
- [x] Final battle support should depend on rescued Blue ships.
- [x] Final battle support should depend on captured shipyards.
- [x] Final battle support should depend on captured relays.
- [x] Final battle support should depend on destroyed Red fortresses.
- [x] Final battle support should depend on liberated Yellow hubs.
- [x] Final battle support should depend on weakened Red defenses.
- [x] The late-game player fleet should feel massive.
- [x] The final battle should feel like an unstoppable force attacking an immovable object.
- [x] Add a final battle readiness screen or summary.
- [x] Show which campaign accomplishments affect the final battle.
- [x] Let Red core defenses remain scary even with high allied support.
- [x] Avoid making the final battle binary impossible/automatic.
- [x] Define minimum viable path to Earth for low-completion players.

### Final Battle Inputs

- [x] Green capital support
- [x] Yellow rebel sabotage
- [x] Blue rescued ship count
- [x] Red fortress count remaining
- [x] Red sensor arrays remaining
- [x] Red shipyards remaining
- [x] Allied route stability
- [x] Player fleet size and readiness

## Phase 11 - UI And Readability

- [x] Add zone tinting.
- [x] Add facility icons by type.
- [x] Color facilities by owner.
- [x] Add hover/selection cards showing:
  - [x] Owner
  - [x] Type
  - [x] Defense
  - [x] Services
  - [x] Mission hooks
  - [x] Garrison
  - [x] Threat level
  - [x] Strategic value
- [x] Add filters:
  - [x] Facilities
  - [x] Missions
  - [x] Fleets
  - [x] Routes
  - [x] Intel
- [x] Make static Red guards and Red-controlled areas visibly red.
- [x] Do not allow Red-controlled areas to display as Yellow unless truly contested.
- [x] Keep faction color, facility type, intel confidence, and mission status visually distinct.
- [x] Add a legend that explains owner colors and facility icons.
- [x] Add zoom rules for labels.
- [x] Add hover cards before adding too many permanent labels.
- [x] Make mission targets stand out without hiding facility ownership.
- [x] Verify the map at 1280x720 and ultrawide.
- [x] Verify text does not overlap in busy Red zones.

## Phase 12 - Save/Load And Tests

- [x] Persist node ownership.
- [x] Persist discovered state.
- [x] Persist destroyed state.
- [x] Persist facility intel levels.
- [x] Persist fleet intel levels.
- [x] Persist stale intel timestamps or confidence.
- [x] Persist reputation values.
- [x] Persist zone control.
- [x] Persist zone influence values.
- [x] Persist fleet state.
- [x] Persist enemy escalation state.
- [x] Persist mission board state.
- [x] Keep old save compatibility if possible.
- [x] Add tests for zone generation.
- [x] Add tests for facility generation.
- [x] Add tests for ownership changes.
- [x] Add tests for mission boards.
- [x] Add tests for fleet generation.
- [x] Add tests for fleet movement.
- [x] Add tests for hidden facility discovery.
- [x] Add tests for hidden fleet discovery.
- [x] Add tests for intel improving mission generation.
- [x] Add tests for stale intel not revealing exact current state.
- [x] Add tests for regional influence changes.
- [x] Add tests for Red escalation triggers.
- [x] Add tests for reputation rewards.
- [x] Add tests for final battle modifiers.
- [x] Add tests for old-save migration.
- [x] Update screenshot baselines only after approved visual changes.
- [x] Add tests for route rendering data.
- [x] Add tests for zone tint/control classification.
- [x] Add tests for Red-controlled sites not rendering as Yellow.
- [x] Add tests for support thresholds.
- [x] Add tests for ignored urgent missions changing world state.
- [x] Add tests for final battle readiness calculation.
- [x] Add a deterministic seed test for generated facilities.
- [x] Add a performance test for large map/fleet counts.

## Phase 13 - Performance And Scale

- [x] Keep map generation deterministic for a given campaign seed.
- [x] Avoid per-frame expensive searches over every node/fleet when rendering.
- [x] Cache route graph layout where possible.
- [x] Keep generated node counts bounded.
- [x] Keep label collision logic bounded.
- [x] Keep hidden facility/fleet checks bounded.
- [x] Avoid recalculating intel visibility for every entity every frame.
- [x] Ensure full campaign map rendering remains smooth with many facilities and fleets.
- [x] Ensure save files do not grow excessively.

## Phase 14 - Documentation And Tuning

- [x] Update player-facing docs/tutorial text for the new war map.
- [x] Update developer docs describing zone/facility generation.
- [x] Document how to add new facility types.
- [x] Document how to add new mission board templates.
- [x] Add tuning notes for zone danger, facility density, and support thresholds.
- [x] Keep a short known-issues list while the redesign is incomplete.

## Phase Gates

- [x] Gate 0: The map remains understandable at a glance.
- [x] Gate 0a: Player immediately knows who owns what.
- [x] Gate 0b: Player immediately knows where danger is.
- [x] Gate 0c: Player immediately knows where missions are.
- [x] Gate 0d: Player immediately knows where support can be gained.
- [x] Gate 1: Four zones render correctly while old saves still load.
- [x] Gate 2: Facility nodes exist and can be selected.
- [x] Gate 3: Route spine is visually removed without breaking travel.
- [x] Gate 4: Facility-spawned fleets move and create visible war pressure.
- [x] Gate 4.5: Player can discover new information about the war.
- [x] Gate 4.5a: Hidden facilities exist.
- [x] Gate 4.5b: Hidden fleets exist.
- [x] Gate 4.5c: Intel can improve.
- [x] Gate 4.5d: Intel changes mission generation.
- [x] Gate 5: Green and Yellow mission boards produce playable missions from known map state.
- [x] Gate 6: At least one ownership flip works end-to-end.
- [x] Gate 7: Reputation support affects both map and tactical battle.
- [x] Gate 8: Red escalation reacts to player success before Earth.
- [x] Gate 9: Final battle modifiers are calculated and displayed.
- [x] Gate 10: Full `gradlew check` passes.
- [x] Gate 11: Screenshot baselines are approved and updated.


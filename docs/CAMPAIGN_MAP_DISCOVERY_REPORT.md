# Campaign Map Discovery Report

Milestone 0 only. This report documents the repository as found on 2026-07-01. No campaign behavior was changed.

The worktree already contained uncommitted changes in `CampaignSystem`, `Renderer`, checkpoint persistence, feature flags, tests, and a new `FactionAttackCommitmentSystem`. Those changes were treated as user-owned and read-only. In particular, the focused-faction-attack feature is in progress and must not be mistaken for a clean baseline.

## 1. Existing Fleet Authority

- **Files:** `src/CampaignSystem.java`, `src/FactionAttackCommitmentSystem.java`, `docs/PHASE_8_ARCHITECTURE_OWNERSHIP.md`
- **Classes:** `CampaignSystem.CampaignState`, private `CampaignSystem.CampaignForce`, `CampaignShipPoolRecord`, `GalaxySearchGroup`, `StrategicTaskForce`, `StrategicDivisionState`, and the in-progress `FactionAttackCommitmentSystem`.
- **Current owner of fleet position:** The documented authority is `CampaignState.campaignForces`; each `CampaignForce` stores `x`, `y`, `targetX`, `targetY`, speed, routes, state, mission, membership, and contact data (`CampaignSystem.java:2408-2506`). The executable ownership diagnostic agrees: `campaignForces + campaignShipPool` are authoritative and tactical ships are encounter projections (`CampaignSystem.java:25163-25170`).
- **Current owner of fleet lifecycle:** `CampaignSystem.updateCampaignForceSimulation` updates orders, movement, contact state, battles, destruction, and final list removal (`CampaignSystem.java:29499-29557`). `CampaignForce.destroyed` is the terminal flag; destroyed records are removed from `campaignForces` at the end of the simulation update.
- **Creation path:** `ensureCampaignForce` and `ensureCampaignForceWithoutDeploymentCost` reuse a matching name/faction/kind or append a new `CampaignForce` with `nextCampaignForceId` (`CampaignSystem.java:20864-20932`). Facility, ambient, search-group, checklist, encounter, reserve, and director paths all call these helpers.
- **Deletion/removal paths:** Battle loss, empty membership, merging, encounter resolution, and other resolvers set `destroyed`; `updateCampaignForceSimulation` performs the physical removal. Visibility loss does not directly remove a force.
- **Position update paths:**
  - Normal campaign movement: `advanceCampaignForcePosition` (`CampaignSystem.java:34928-34968`).
  - Tactical membership projection: `updateCampaignForceStrengthFromMembership` replaces force position with the average live member-ship position (`CampaignSystem.java:32707-32733`).
  - Search-group projection: `syncCampaignSearchGroupsToForces` calls `ensureCampaignForce` with `GalaxySearchGroup.x/y`; an existing linked force is eligible for position overwrite (`CampaignSystem.java:21717-21758`, `20985-20990`).
  - Presentation-density logic: `surfaceFleetContactNearPlayer` directly overwrites authoritative `force.x/y` to a point 680-1099 units from the player (`CampaignSystem.java:31848-31882`).
- **Destination/mission/AI updates:** `updateCampaignForceOrders` and mission-specific maintain/assign helpers own orders; `advanceCampaignForcePosition` consumes `routePoints`/`targetX/Y`. Existing mission, intent, state, work-state, mission-state, stop-reason, and reassignment enums overlap substantially (`CampaignSystem.java:1975-2105`).
- **Notes:** The documented authority exists, but at least three subsystems can overwrite its position. `GalaxySearchGroup` also has an independent position and movement loop, making it a de facto second moving representation when linked to a `CampaignForce`. `StrategicTaskForce` and `StrategicDivisionState` are additional strategic representations, although current architecture documentation classifies their summaries as projections.

## 2. Existing Renderer Behavior

- **Files:** `src/Renderer.java`, `src/CampaignSystem.java`, `src/UISystem.java`, `src/CampaignMapPresentationModel.java`.
- **Classes:** `Renderer`, `CampaignSystem`, `UISystem`, `CampaignMapPresentationModel`.
- **Fleet marker rendering path:**
  1. `Renderer.drawStrategicMap` draws the galaxy layers and then calls `drawStrategicSupportMarkers` (`Renderer.java:9598-9826`).
  2. `CampaignSystem.activeSupportMarkers` builds search-group, strike, battle, pending-threat, narrative, and campaign-force markers (`CampaignSystem.java:14104-14182`).
  3. `addCampaignForceMarkers` calls `supportMarkerForCampaignForce` for every eligible force (`CampaignSystem.java:14245-14264`).
  4. `Renderer.drawStrategicSupportMarkers` and `drawStrategicSupportMarker` draw those immutable marker records (`Renderer.java:12788-12809`, `13138-13190`).
- **Route rendering path:** `Renderer.drawStrategicMap` unconditionally calls `drawCampaignRouteNetwork`, `drawSelectedTerritoryEdges`, and `drawCampaignInvasionArrows` in galaxy mode (`Renderer.java:9727-9735`). `campaignRouteSegments` always builds local-zone, supply, contested, blockade, and player segments (`CampaignSystem.java:6891-6909`). There are no `showTradeRoutes`, `showPatrolRoutes`, `showSelectedSiteRoutes`, or `showDebugRoutes` gates.
- **Ghost marker rendering path:**
  - `forceMarkerX/Y` deliberately returns `lastKnownX/Y` for stale forces (`CampaignSystem.java:16105-16113`).
  - `campaignForceVisibleOnMap` retains stale enemy contacts for as long as 120 seconds and accepts any finite marker position (`CampaignSystem.java:16050-16066`).
  - `supportMarkerForCampaignForce` emits an `INTEL` marker at that position with a lost-bearing and estimated-vector subtitle (`CampaignSystem.java:15870-15905`, `16130-16151`, `16199-16217`).
  - `supportMarkerForGalaxySearchGroup` independently emits a last-known marker at `group.lastKnownX/Y` (`CampaignSystem.java:16242-16269`).
- **Enemy interception display:** Confirmed player intercepts use real `force.x/y` and are drawn as red lines to the player (`CampaignSystem.java:16069-16102`, `Renderer.java:12939-12984`). The confirmed-track guard prevents this particular line from being drawn for stale contacts.
- **Debug route rendering:** No separate debug route graph gate was found in the galaxy renderer. Debug mode broadens fleet visibility in `campaignForceVisibleOnMap`, and debug subtitles expose route/vector fields, but the main route network is normal gameplay rendering.
- **Notes:** The renderer itself mostly consumes read models, but some read-model producers mutate state (`activeSupportMarkers` calls ownership/reference cleanup and force ownership synchronization). More importantly, visibility-density code mutates real fleet positions before rendering.

## 3. Existing Sensor/Contact System

- **Files:** `src/CampaignSystem.java`, `src/FogOfWarSystem.java`, `src/Renderer.java`.
- **Classes:** private `CampaignForce`, `GalaxySearchGroup`, `NpcForceContact`, `PlayerContact`, and tactical `FogOfWarSystem.State`.
- **How visibility is currently decided:**
  - Campaign forces use `visibleToPlayer`, `CampaignForceContactState` (`KNOWN`, `SUSPECTED`, `STALE`), confidence, last-known age/position/velocity, and uncertainty radius on the authoritative force object itself.
  - `updateCampaignForceContactState` makes all non-enemy forces visible and continuously updates their exact known position. Enemy visibility is based on a 720 + intel sensor radius, stealth, and relay coverage (`CampaignSystem.java:31905-31935`).
  - Map inclusion is then separately filtered by `strategicDetectionRange`, which is 1.5 times the strategic strike range rather than the same sensor radius (`CampaignSystem.java:16400-16416`, `14173-14180`, `14251-14259`).
  - `GalaxySearchGroup` has its own independent visibility, confidence, fade timer, track-integrity, last-known position, and detection model (`CampaignSystem.java:36161-36320`).
  - NPC fleets have another observer-specific `knownHostileContacts` map used for pursuit and predicted hunt routes.
- **Friendly reporting:** There is no explicit communications/report observation model. `force.faction != ENEMY` is treated as permanently exact and visible in `updateCampaignForceContactState`; jamming, source attribution, and simultaneous observations are absent.
- **Sensor-range rendering:** No player-centered sensor-range circle is drawn in galaxy mode. The tactical map draws fog and sensor-interest signals, while galaxy contacts are filtered through campaign range helpers.
- **Whether visibility affects existence:** Visibility does not directly delete a campaign force. Hidden forces persist and are checkpointed. However, contact data is embedded in the fleet entity, and `shouldRetainCampaignForceContact` can resurrect a zero-strength, shipless hostile as a non-destroyed force with minimum strength/readiness/hull/supply (`CampaignSystem.java:32741-32774`). Thus intelligence state can affect simulation existence, violating the intended separation in the opposite direction.
- **Notes:** There are at least three contact models: player-facing `CampaignForce` contact fields, `GalaxySearchGroup` contact fields, and per-NPC `NpcForceContact`. They use seconds rather than deterministic campaign observation ticks and store only one blended contact state/source.

## 4. Existing Site/Region System

- **Files:** `src/CampaignSystem.java`, `src/Renderer.java`, `src/UISystem.java`, `src/StrategicCampaignExpansionSystem.java`.
- **Classes:** `CampaignLocation`, `StrategicNodeState`, `StrategicCampaignExpansionSystem.Territory`, and route/travel-lane projections.
- **Site registry:** The live registry is two lists on `CampaignState`: `galaxyMainPois` and `galaxyAreasOfInterest`; `allCampaignLocations` concatenates them and `campaignLocationById` performs the lookup (`CampaignSystem.java:17624-17627`, `38867-38880`). `CampaignLocation` mixes site identity, facility type, ownership/control, services, intel, route memory, and lifecycle fields (`CampaignSystem.java:1138-1207`).
- **Site rendering:** Main sites become objective/landmark markers with glyphs and labels. Facility-specific glyphs exist in `Renderer.drawCampaignFacilityGlyph` (`Renderer.java:13593-13752`). Areas of interest generally become support markers.
- **Territory/control circle rendering:**
  - `drawCampaignLocationControlHalos` draws large filled and outlined circles around every discovered main POI (`Renderer.java:9992-10043`).
  - `drawCampaignTerritoryOverlay` draws another small circle/insignia layer for strategic territory projections (`Renderer.java:10242-10281`).
  - `drawCampaignControlRing` adds another ownership ring around site markers (`Renderer.java:13466-13504`).
- **Interaction:** Galaxy clicks search for a `CampaignLocation` within 260 world units before treating the click as free space (`UISystem.java:1364-1405`, `CampaignSystem.java:12262-12289`). Consequently, clicking a visually empty part of a large control halo can select the underlying site even when the site glyph is small or obscured.
- **Notes:** The empty-circle confusion is not a separate empty-site entity bug. It is caused by multiple concentric circle languages around sites/territories, small center glyphs, broad 260-unit site hit testing, and the absence of a distinct non-interactive region hit model.

## 5. Existing Save/Load System

- **Files:** `src/app/persistence/CampaignCheckpointStore.java`, `src/app/persistence/CampaignSaveContract.java`, `src/CampaignSystem.java`, `docs/CAMPAIGN_SAVE_SCHEMA.md`.
- **Classes:** `CampaignCheckpointStore.Checkpoint`, `CampaignSystem.CampaignState`.
- **Fleet persistence:** `CampaignSystem.saveCheckpoint` serializes campaign forces and ship-force membership into checkpoint strings (`CampaignSystem.java:48070-48176`). `serializeCampaignForces` stores authoritative position, target, routes, membership, mission/lifecycle fields, and contact fields (`CampaignSystem.java:48917-48987`). `restoreCampaignForces` rebuilds the private entities and membership map (`CampaignSystem.java:49383-49504`).
- **Contact persistence:** The save currently persists `contactConfidence`, `uncertaintyRadius`, `lastKnownX/Y`, `lastKnownAgeSec`, `contactState`, `visibleToPlayer`, `lastKnownVelocityX/Y`, and `lastSeenSec`. Search groups and `enemyPlayerContact` are also independently persisted. Exact live contact state is therefore trusted across load rather than reevaluated from sensors and communications.
- **Route/site persistence:** Fleet route and patrol waypoint lists are embedded in the fleet payload. `galaxyLocationStates` persists location ownership, service/damage state, memory, and route notes. `strategicNodes` and theaters are separate checkpoint payloads. The generated `campaignRouteSegments` list is cached but not itself saved; it is rebuilt from locations, theaters, forces, selection, and travel state. Player route-queue persistence was not found in the checkpoint fields and should be verified before changing navigation behavior.
- **Operations persistence:** The worktree's in-progress `factionAttackCommitments` payload is captured/restored through `FactionAttackCommitmentSystem`. The older `strategicExpansion` operation catalog is also persisted as expansion state but is documented as model/projection-only.
- **Notes:** The save schema is compatibility-sensitive and string-position based. Any contact migration must preserve old field parsing while disregarding saved live visibility after restore.

## 6. Baseline Bug Reproduction

### Route spam reproduction

1. Start or initialize the normal strategic overmap and open the galaxy map.
2. No overlay toggle is required: `drawStrategicMap` always invokes the entire route network.
3. Observe local-zone, supply, contested, blockade, and player-selected segments simultaneously.
4. Select any site. `campaignSelectedTerritoryEdges` returns every travel lane adjacent to the selected/current location, and `drawSelectedTerritoryEdges` adds those on top of the already-visible network (`CampaignSystem.java:4629-4659`).
5. Selecting a site also adds a player-plotted segment through `addPlayerRouteSegment` (`CampaignSystem.java:7015-7049`).

This is deterministic. The route network is always enabled; selection adds more lines rather than switching to a scoped route view.

### Spazzing/teleporting marker reproduction

There are two deterministic paths.

**Linked search-group snap cycle:**

1. Initialize the strategic campaign, which creates `GalaxySearchGroup` records and linked `CampaignForce` projections.
2. Let `updateGalaxySearchGroups` move the group once.
3. `syncCampaignSearchGroupsToForces` overwrites the linked campaign force position from the group because `campaignForceSyncShouldOverwritePosition` returns true for `linkedSearchGroupId > 0`.
4. In the same force-simulation tick, `advanceCampaignForcePosition` moves that campaign force again toward the group's target.
5. Both the search-group marker and campaign-force marker are eligible for `activeSupportMarkers`, so two representations can be drawn at divergent positions.
6. On the next tick the projection is overwritten from the group again, producing the snap-back/forward pattern.

**Minimum-visible-contact teleport:**

1. Initialize the campaign and move or mark all non-player fleets outside strategic detection.
2. Call the normal overmap update or `maintainMinimumVisibleFleetContacts`.
3. If fewer than three contacts are visible, `surfaceFleetContactNearPlayer` directly assigns selected real forces a new `x/y` 680-1099 units from the player, changes their intent/state, and replaces their route.

The existing test `CampaignNpcFleetAiTest.campaignDirectorMaintainsAtLeastThreeVisibleFleetGroupsAcrossMap` codifies the second behavior as a requirement.

### Ghost contact reproduction

1. Detect a hostile campaign force.
2. Move it outside the force sensor range and advance more than 30 seconds.
3. `updateCampaignForceContactState` changes it to `STALE` but decays confidence no lower than 0.12.
4. `campaignForceVisibleOnMap` continues accepting it for up to 120 seconds.
5. `forceMarkerX/Y` renders `lastKnownX/Y`, and `supportMarkerForCampaignForce` emits a clickable `INTEL` marker with last-known range and estimated-vector text.

The existing tests `staleContactMarkerExplainsLostBearingAndSweepRecommendation` and `recentlyKnownHostileForceIsRetainedInsteadOfVanishing` explicitly require this behavior. `supportMarkerForGalaxySearchGroup` provides a second ghost-marker path with its own fade/actionable window.

### Movement vector after contact loss

No drawn live intercept line was found for a stale `CampaignForce`; `campaignForceCanDrawLiveMovementVector` and `isConfirmedPlayerInterceptForce` correctly require a confirmed real track. The requested visual bug is therefore **not reproduced in that specific path**.

However, stale campaign-force markers still display `est vector ...`, and NPC hunt routing projects stale positions using `lastKnownVelocityX/Y` (`CampaignSystem.java:30982-31000`). The existing test `huntMissionPredictsFromLastKnownVelocityWhenContactIsStale` confirms this prediction behavior. Search-group stale markers state that no live vector exists.

### Hidden fleet deleted/forgotten reproduction

This behavior was **not reproduced as visibility-driven deletion**. A non-destroyed hidden force remains in `campaignForces`, continues simulation subject to active-slot/update rules, and is serialized. Destruction/removal is driven by strength/membership or explicit encounter/battle/merge outcomes.

There is a related defect: a zero-strength, shipless recently known hostile may be kept alive by `retainCampaignForceAsLastKnown`, which raises its authoritative strength, readiness, hull, and supply and clears destruction. Contact memory can therefore manufacture simulation persistence.

### Empty-circle confusion reproduction

1. Open the galaxy map at a zoom where the center site glyph is small.
2. Observe a main site with its large control halo, territory circle, and marker control ring.
3. Click within 260 world units of the site, including a visually empty portion of its halo.
4. `UISystem.handleMapClick` resolves the nearest location and selects it as a destination.

The circle is not a standalone site, but its broad hit region and shared center make it behave as though the entire control halo were an interactable location.

### Baseline verification run

The following existing tests passed unchanged:

- `CampaignNpcFleetAiTest.campaignDirectorMaintainsAtLeastThreeVisibleFleetGroupsAcrossMap`
- `CampaignNpcFleetAiTest.staleContactMarkerExplainsLostBearingAndSweepRecommendation`
- `CampaignNpcFleetAiTest.recentlyKnownHostileForceIsRetainedInsteadOfVanishing`
- `CampaignNpcFleetAiTest.huntMissionPredictsFromLastKnownVelocityWhenContactIsStale`
- `CampaignTheaterConquestChecklistTest.campaignRouteSegmentsReplaceOldLinearPoiSpine`

These passes confirm that route density, forced visible contacts, stale markers, retained ghost forces, and stale-contact prediction are intentional under the current tests rather than accidental untested behavior.

## 7. Risk Assessment

- **Places where multiple systems appear to own fleet position:**
  - `GalaxySearchGroup.x/y` and linked `CampaignForce.x/y` both move and both render.
  - `surfaceFleetContactNearPlayer` moves authoritative forces for presentation density.
  - Tactical member averaging overwrites campaign-force position when live ships exist.
  - `ensureCampaignForce` may overwrite an existing force's position during synchronization.
- **Places where rendering may mutate or imply simulation state:**
  - Marker production calls ownership/reference synchronization and cleanup.
  - Stale positions are rendered as selectable support markers derived from the real force object.
  - Invasion arrows are normally inferred from fleet mission/destination and shown without an explicit player-operation-intel record. The in-progress feature flag instead projects `FactionAttackCommitmentSystem` commitments, also without an intel filter.
  - Control halos, territory circles, and site control rings reuse circular visual language.
- **Places where detection may delete or hide real fleets:**
  - No direct visibility-driven deletion found.
  - The reverse coupling is severe: contact memory can prevent destruction and restore minimum physical strength.
  - `visibleToPlayer` also affects high-priority simulation cadence, so perception state changes movement update frequency (`CampaignSystem.java:30008-30036`).
  - Saved visibility/contact state is restored before fresh sensor evaluation.
- **Places where route rendering is triggered unintentionally:**
  - The full generated route network is unconditional in galaxy mode.
  - Selected-territory edges default to the current location even with no explicit selection.
  - Selecting a site adds adjacency edges and a player route simultaneously.
  - Moving blockade forces add dynamic route segments based on fleet targets.
- **Persistence/ownership risk:** `docs/PHASE_8_ARCHITECTURE_OWNERSHIP.md` calls strategic nodes theater summaries, while `docs/CAMPAIGN_SAVE_SCHEMA.md` describes strategic-node ownership fields as authoritative and reconciled with locations. This contract should be resolved before altering site ownership or operations.
- **Worktree risk:** `FactionAttackCommitmentSystem` and its integration are uncommitted. Milestone 1 must either build on that work deliberately or wait until it is reviewed; it must not create a parallel operation model beside it.

## 8. Recommended Milestone 1 Plan

### Minimal safe files to edit

1. `src/CampaignSystem.java` — remove presentation-driven force relocation and change map marker eligibility/read models.
2. `src/Renderer.java` — gate route layers and simplify circle/marker draw order.
3. `src/UISystem.java` — align site hit testing with the visible site glyph rather than the control halo.
4. Focused tests in `test/CampaignNpcFleetAiTest.java`, `test/CampaignTheaterConquestChecklistTest.java`, and a new narrowly scoped campaign-map clarity test if needed.

Do not modify checkpoint payloads, introduce the final intel model, or replace fleet/operation managers in Milestone 1.

### Tests to add or invert first

1. A linked-search-group invariant proving exactly one player-facing marker and one authoritative rendered position per real force.
2. A no-presentation-mutation test proving marker generation and minimum-contact maintenance cannot change `CampaignForce.x/y`, target, route, intent, or mission.
3. A normal-map route test proving local/supply/patrol/debug networks are hidden by default.
4. A selected-site route test proving only selected-site edges are returned when that overlay is explicitly enabled.
5. A stale-contact test proving no normal/clickable fleet marker is emitted after exact detection expires.
6. A hidden-force persistence test proving loss of detection neither removes nor weakens the force.
7. A site hit-test proving clicks in a control halo outside the site marker select free space rather than the site.

The current tests that require forced visible contacts, stale markers, and retained zero-strength ghost forces must be intentionally replaced or inverted; otherwise they will block the desired behavior.

### Behavior to change first

1. Stop calling `surfaceFleetContactNearPlayer` from the normal overmap update, or make it generate presentation-only/tutorial content without mutating an existing campaign force.
2. Make linked `GalaxySearchGroup` data a projection of `CampaignForce`, or temporarily suppress one marker/movement path. Do not allow both objects to advance position.
3. Add explicit route-layer defaults and stop drawing the full route network in normal mode.
4. Suppress stale campaign-force/search-group markers from normal gameplay while preserving internal records.
5. Reduce site hit testing to a zoom-aware marker radius and keep territory/control overlays non-interactive.

### Behavior to avoid changing yet

- Do not add the final `FleetIntelRecord`/`OperationIntelRecord` schema until the duplicate contact representations are consolidated.
- Do not rewrite save serialization or drop old fields yet.
- Do not replace the fleet mission/lifecycle enums yet.
- Do not add the full rally/muster/invasion state machine.
- Do not change territory ownership resolution, faction directors, tactical encounter generation, economy, or balance.
- Do not create another fleet, contact, route, or operation manager.

## Milestone 0 Acceptance Summary

- Existing fleet authority: **identified**.
- Renderer path: **identified**.
- Sensor/contact paths: **identified; multiple overlapping models found**.
- Save/load path: **identified**.
- Spazzing marker: **deterministically reproducible through linked projection resync/double movement and presentation-driven force teleporting**.
- Route spam: **deterministically reproducible; full route network is unconditional**.
- Ghost contacts: **reproduced and explicitly required by current tests**.
- Non-live drawn movement vector: **not reproduced; stale estimated-vector text and AI prediction remain**.
- Hidden-fleet deletion: **not reproduced; inverse contact-to-existence coupling found**.
- Empty-circle confusion: **source identified in overlapping circle layers and broad site hit testing**.
- New campaign systems introduced by this audit: **none**.


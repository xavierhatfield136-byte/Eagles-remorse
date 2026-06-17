# Campaign Redesign Phase 0 Audit

This audit covers the first compatibility pass for moving the campaign from the legacy 24-POI mission spine into the four-zone facility war theater.

## Summary

- The game still generates 24 stable main campaign IDs: `poi-01` through `poi-24`.
- Those IDs are currently created in `src/CampaignSystem.java` inside `initializeGalaxyCampaignMap`.
- The current names and positions have already been converted toward four-zone facility language, but the IDs remain legacy-compatible.
- There are 130 exact `poi-XX` references across 15 files outside this checklist.
- Most hard-coded `poi-XX` references are tests or save/load compatibility assertions.
- Runtime systems mostly use `CampaignLocation.id`, `currentGalaxyLocationId`, `selectedGalaxyLocationId`, force source/destination IDs, and checkpoint strings rather than assuming display names.
- Theater/facility scaffolding already exists: `CampaignTheaterState`, `StrategicNodeState`, `TheaterId`, `TheaterNodeType`, `TheaterNodeOwner`, theater recompute, node ownership changes, and checkpoint persistence.
- Old IDs should stay until the new facility IDs and migration layer are stable.

## Reference Classes And State

- `CampaignLocation`
  - Stable map object ID.
  - Display name and detail.
  - Type, threat, mission index, services.
  - Discovered/completed/consumed state.
  - Existing intel quality and escalation fields.
- `CampaignTheaterState`
  - Four theater bands are already represented.
  - Current fields include control score, supply, threat, red/green/yellow presence, danger, route risk, trade health, and installation integrity.
- `StrategicNodeState`
  - Links a campaign location to a theater.
  - Tracks node type, owner, contest progress, cooldown, and last owner.
- `CampaignCheckpointStore.Checkpoint`
  - Persists current/selected/docked location IDs.
  - Persists galaxy location states.
  - Persists strategic node and theater state.
  - This is the main compatibility boundary for old saves.

## Existing Legacy Main POI Mapping

Keep these IDs stable for now. Treat the new facility names as roles layered over old save IDs.

| Legacy ID | Current Facility Role | Zone |
| --- | --- | --- |
| `poi-01` | Green Anchorage Pelagos | Southern Zone |
| `poi-02` | Green Resupply Base Hecate | Southern Zone |
| `poi-03` | Green Patrol Station Carina | Southern Zone |
| `poi-04` | Green Mining Operation Delta | Southern Zone |
| `poi-05` | Green Repair Yard Vesta | Southern Zone |
| `poi-06` | Green Forward Fort Thessa | Southern Zone |
| `poi-07` | Yellow Exchange Ilex | Lower-Middle Zone |
| `poi-08` | Yellow Commerce Spine Oris | Lower-Middle Zone |
| `poi-09` | Yellow Rebel Cell Nysa | Lower-Middle Zone |
| `poi-10` | Yellow Refugee Convoy Haven Oriel | Lower-Middle Zone |
| `poi-11` | Yellow Civilian Shipworks Myr | Lower-Middle Zone |
| `poi-12` | Yellow Mining Colony Ashkel | Lower-Middle Zone |
| `poi-13` | Red Corridor Breakpoint | Upper-Middle Zone |
| `poi-14` | Red Listening Bastion Kharon | Upper-Middle Zone |
| `poi-15` | Red Fuel Depot Furnace | Upper-Middle Zone |
| `poi-16` | Red Prison Station Breakchain | Upper-Middle Zone |
| `poi-17` | Red Forward Shipyard Aster | Upper-Middle Zone |
| `poi-18` | Red Blockade Anchor Crown | Upper-Middle Zone |
| `poi-19` | Red Fortress Luna Gate | Northern Zone |
| `poi-20` | Red Dreadnought Yard Typhon | Northern Zone |
| `poi-21` | Red Sensor Array Crown | Northern Zone |
| `poi-22` | Red Orbital Battery Helios | Northern Zone |
| `poi-23` | Earthrise Resistance Staging Port | Northern Zone |
| `poi-24` | Earth High Orbit Boss Bastion | Northern Zone |

## Reference Classification

### Must Preserve

- `src/CampaignSystem.java`
  - Generates `poi-%02d` IDs for the 24 main locations.
  - Stores current, selected, docked, active encounter, force route, and travel IDs.
  - Serializes and restores galaxy location states by location ID.
  - Serializes and restores strategic nodes by location ID.
  - Still uses numeric sector mission flow for tactical missions and boss progression.
- `src/app/persistence/CampaignCheckpointStore.java`
  - Persists galaxy location IDs and galaxy location state strings.
  - Old saves depend on these exact IDs loading back into valid map locations.

### Can Migrate Later

- Tests that select specific campaign locations by `poi-XX`.
- Mission progression code that uses `missionIndex` or sector number to launch a tactical mission.
- Theater node type inference that currently derives from `CampaignLocation` type/name/services.
- Checkpoint state strings once a `legacyPoiId`/new facility ID migration layer exists.

### UI-Only

- Renderer and UI code generally reads `CampaignLocation` objects, names, types, control views, and services.
- `UiState` prompt IDs are transient UI state, but they still need valid location IDs while prompts are active.
- Visual route drawing still follows ordered `galaxyMainPois`, which is presentation coupling rather than save compatibility.

### Test-Only

- `CampaignNpcFleetAiTest.java` has the largest number of hard-coded `poi-XX` references.
- `CampaignOvermapCheckpointTest.java` intentionally verifies old checkpoint ID persistence.
- `CampaignStrategicTravelPressureTest.java`, `CampaignStrategicLoopIntegrationTest.java`, `CampaignStrategicStrikeCounterplayTest.java`, `CampaignTacticalAlignmentTest.java`, and UI invariant tests use fixed IDs to target known map situations.
- These should move to helper methods like `firstFacilityWithService`, `firstFacilityInZone`, or `legacyPoi("poi-XX")` only after the migration layer exists.

### Delete Later

- Direct “24 ordered mission dot” presentation assumptions.
- Tests that only prove a specific numbered POI is at a specific strategic role, once facility roles are stable.
- Any new code that infers faction ownership from display name.

## Compatibility Risks

- `CampaignState.totalSectors` is final and fixed at 24. The tactical mission chain still depends on sector numbers.
- `CampaignLocation.id` is final, so a new ID scheme will need either parallel `legacyPoiId` fields or a migration table before changing IDs.
- `CampaignLocation.detail` currently carries both facility description and mission lore text.
- `galaxyMainPois` order is still used for route presentation and some tests.
- Checkpoint restore ignores unknown location IDs, so old saves will silently lose state if IDs are changed without migration.

## Recommended First Implementation Step

Add explicit facility metadata beside the existing legacy IDs instead of replacing `CampaignLocation.id`.

Minimum first fields:

- `facilityId`
- `legacyPoiId`
- `zoneId`
- `facilityType`
- `ownerFaction`
- `strategicValue`
- `intelLevel`

This keeps old saves and tests alive while allowing the new campaign systems to stop depending on display names, ordered POI position, or mission index.

## Implementation Note

The first compatibility bridge has been added:

- `CampaignLocation.id` remains the legacy save/load ID.
- `CampaignLocation.legacyPoiId` stores the old `poi-XX` ID when present.
- `CampaignLocation.facilityId` provides a new facility-facing ID.
- `CampaignLocation.zoneId` records the campaign zone.
- `CampaignLocation.facilityType` records explicit facility role.
- `CampaignLocation.ownerFaction` records initial faction ownership metadata.
- `CampaignLocation.strategicValue` records the 1-5 importance rating.
- `CampaignLocation.intelLevel` records the new fog-of-war tier.
- Procedural minor sites now start hidden with unknown intel.
- Tests cover stable legacy IDs, facility metadata, hidden procedural site intel, checkpoint compatibility, and strategic UI readability.

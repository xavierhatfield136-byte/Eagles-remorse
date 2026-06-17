# Campaign Redesign Implementation Notes

## Player-Facing War Map Model

The campaign map is now treated as a four-zone war theater rather than a numbered mission ladder. Facilities expose owner color, facility type, intel level, defense, services, mission hooks, garrison links, threat, and strategic value through the selection sidebar. Route lines are generated as typed segments: local zone routes, supply lines, contested lanes, blockade lines, and the player-plotted route.

## Zone And Facility Generation

Main legacy `poi-XX` locations are still present for compatibility, but each one is relabeled as a facility with:

- `facilityType`
- `ownerFaction`
- `controlState`
- `strategicValue`
- `defenseStrength`
- `resourceValue`
- `missionTags`
- `linkedFleetIds`
- `canChangeOwner`
- `canSpawnFleets`
- `intelLevel`
- `fleetIntelLevel`
- `intelStaleAtSec`

Procedural minor sites are seeded deterministically from the campaign seed. Keep generated counts bounded and deterministic. When adding new facilities, prefer using `CampaignFacilityType` and the default operational-field helpers instead of inferring behavior from display names.

## Compatibility And Rollback

The `poi-01` through `poi-24` IDs remain the compatibility path while the four-zone theater stabilizes. New facility identity lives beside those IDs through `facilityId`, `legacyPoiId`, `zoneId`, and facility metadata, so old saves and old mission references can still resolve. Do not remove the legacy structure until old-save loading, Earth-gate progression, route selection, mission board generation, and screenshot/readability checks all pass together.

Comparison mode during development is the retained legacy path itself: main POIs can still be resolved, selected, saved, loaded, and used by the Earth gate while the new map presentation overlays zones, facilities, intel, fleets, and route segments. Roll back or pause removal of old progression code if any of these fail: old saves cannot load, `poi-24` stops resolving as the Earth boss path, route planning loses click/selection behavior, `gradlew check` fails, map text overlaps in dense Red zones, or the four-zone map becomes unreadable at 1280x720.

### Zone Feel And Escalation

Southern Zone starts at Green 85 / Yellow 10 / Red 5. It should teach the war map with safer routes, visible service hubs, prospectors, lighter patrols, and forgiving recovery.

Lower-Middle Zone starts at Green 25 / Yellow 55 / Red 20. It should center Yellow pressure, rebellion, refugee movement, civilian hubs, mining protection, and trade risk.

Upper-Middle Zone starts at Green 5 / Yellow 15 / Red 80. It should feel occupied: Red patrols, garrisons, resource extraction, blockades, prison camps, and urgent defense missions.

Northern Zone starts at Green 0 / Yellow 5 / Red 95. It should feel like Red core territory: elite fleets, dreadnought yards, major fortresses, sensor arrays, Earth defenses, and final-battle pressure.

Red escalation scales by theater: Southern is lighter, Frontier is baseline, Lunar is reinforced, and Earth is the harshest. Escalation should respond to player success with hunter fleets, counteroffensives, reinforced shipyards, upgraded fortresses, and attacks on allied facilities rather than simply punishing normal progress.

## Adding Facility Types

When adding a new `CampaignFacilityType`, update:

- default strategic value
- default defense/resource values
- default mission tags
- fleet spawning eligibility
- generated fleet kind/mission/intent
- facility icon/label rendering
- save/load compatibility tests

Avoid hard-coding faction ownership from names. Names may hint at flavor, but systems should read `ownerFaction` and `controlState`.

## Mission Board Templates

Green and Yellow boards are generated from current facility, fleet, theater, and intel state. Mission IDs must be stable because completed and expired board missions persist in checkpoint state.

Green mission families currently include attacks, defenses, convoy escorts, mining captures, Yellow settlement liberation, and Red scout destruction.

Yellow mission families currently include rebellion, refugee escort, Red sabotage, prisoner rescue, civilian hub defense, and mining protection.

Urgent missions can lapse if ignored. Lapsed missions increase Red pressure, stale local intel, and remove the mission from the active board.

## Intelligence Sources

Intel should come from visible world activity, not only global percentage math. Current source categories are: recon flights, friendly relays, sensor towers, allied reputation, patrol fleets, listening posts, captured facilities, rescued civilians or prisoners, Yellow rebel reports, and Green command briefings. Keep `campaignIntelSourceLines` aligned with any new intel channel so the player can understand why the fog of war is improving.

## Tuning Notes

Support thresholds are keyed from Green and Yellow favor. Keep early tiers visible but modest; early support should help the player feel backed without removing danger. Red escalation should feel reactive rather than punitive: use it to create counteroffensives, hunter fleets, and reinforced core defenses after player success.

Tune these first:

- zone influence seed values
- theater red influence thresholds for urgent missions
- facility strategic value
- defense/resource defaults
- support tier rewards
- final battle readiness weights

## Known Issues While Redesign Is In Flight

- The old `poi-XX` compatibility path is intentionally retained.
- The Earth approach still depends on legacy progression gates in addition to the new readiness model.
- Map filters are still represented mostly through strategic overlays rather than a dedicated multi-select filter panel.
- Large-scale final battle staging is scored and messaged, but the full late-game mass fleet presentation still needs a final encounter pass.

# Eagles Remorse 1.0.1.9

This release focuses on campaign playtest fixes: cleaner starting battles, more reliable overworld cleanup, stronger Red pressure, station capture, Titan fleet rule updates, and safer packaged assets.

## Downloads

- Windows portable build: `EaglesRemorse-1.0.1.9-windows-x64-full.zip`
- Linux portable build: `EaglesRemorse-1.0.1.9-linux-x64-full.zip`
- SHA-256 checksums: `SHA256SUMS-windows.txt`
- SHA-256 checksums: `SHA256SUMS-linux.txt`

The Windows portable package includes a Java 21 runtime. Players do not need to install Java separately.

## Highlights

- Simplified player-facing HUD and campaign overworld wording so panels show less redundant text.
- Added hover details for shortened overworld menu entries.
- Removed retired fuel, supplies, ammo, and salvage stockpile messaging from visible tutorial/resource copy.
- Fixed manual power distribution being overridden back into attack bias.
- Slowed player and blue-team fast missiles so they reliably collide with targets.
- Fixed defeated campaign fleets persisting or respawning as empty overworld contacts.
- Replaced the scripted starting-location red overworld attackers with tactical-only attackers so cleared tutorial combat stays cleared.
- Increased friendly and Red fleet prosecution/fire authority to 2,500 units.
- Restricted remote battle choices so non-adjacent fights cannot be joined from across the map.
- Strengthened Red territory fleet composition and response pressure.
- Enemy stations destroyed by the player now rebuild as friendly forward operating bases.
- Any Titan or the Mothership now restores the campaign ore cap to 10,000, including existing saves with stale caps.
- Removed the total Titan count limit while enforcing one Titan of each archetype in the campaign fleet.
- Idle campaign small craft and temporary hangar corvettes now return to hangar when no enemy threats remain.
- Limited shield visuals to ships that are actively taking shield damage and removed the tutorial station shield bubble.
- Fixed tutorial map markers so they stay locked to world positions while zooming and panning.
- Reduced broad tutorial map click targets so players cannot skip directly between training points by clicking empty space.
- Made tutorial station upgrade costs fit the frigate's ore capacity.
- Recentered the tactical tutorial map after entering training encounters from the overworld.
- Updated tutorial gates so current recon actions satisfy the old threat-refresh lesson.

## Validation

- `CommandSchoolOverworldExpansionTest`
- `CampaignEncounterMapIdentityTest`
- `CampaignMissionSectionsTest`
- `PowerManagementControlTest`
- `MissileRoleBehaviorTest`
- `RendererHoverTooltipTest`
- `RendererHudLayoutTest`
- `HotkeyRegistryTest`
- `compileJava`
- `git diff --check`
- Windows portable staged-folder manifest verification
- Windows portable ZIP manifest verification
- Runtime asset loadability verification
- Campaign overworld ghost-fleet regression tests
- Campaign force ownership and checkpoint migration tests
- Carrier idle cleanup regression tests
- Titan roster and campaign shop regression tests

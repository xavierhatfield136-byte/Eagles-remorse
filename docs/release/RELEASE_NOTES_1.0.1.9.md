# Eagles Remorse 1.0.1.9

This release focuses on making the tutorial, HUD, and campaign overworld easier to read and harder to get stuck in.

## Downloads

- Windows portable build: `EaglesRemorse-1.0.1.9.zip`
- SHA-256 checksums: `SHA256SUMS-windows.txt`

The Windows portable package includes a Java 21 runtime. Players do not need to install Java separately.

## Highlights

- Simplified player-facing HUD and campaign overworld wording so panels show less redundant text.
- Added hover details for shortened overworld menu entries.
- Removed retired fuel, supplies, ammo, and salvage stockpile messaging from visible tutorial/resource copy.
- Fixed manual power distribution being overridden back into attack bias.
- Slowed player and blue-team fast missiles so they reliably collide with targets.
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

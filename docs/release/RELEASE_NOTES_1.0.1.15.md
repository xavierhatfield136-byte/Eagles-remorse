# Eagles Remorse 1.0.1.15

This release focuses on tactical readability, main-menu combat presentation,
fleet encounter discipline, and missile/weapon balance across the four
factions.

## Downloads

- Windows portable build: `EaglesRemorse-1.0.1.15-windows-x64-full.zip`
- Linux portable build: `EaglesRemorse-1.0.1.15-linux-x64.tar.gz`
- macOS app bundle ZIP: `EaglesRemorse-1.0.1.15-macos.zip`

Package artifacts are produced by the release packaging workflow for this
version.

## Highlights

- Reworked the main menu into a compact command-vector layout with an active
  silent tactical battle preview behind it.
- Updated main-menu attract battles to use real ships from the four natural
  factions while excluding the custom mission faction.
- Fixed menu attract battles so ships use staggered fire instead of full-salvo
  opening bursts.
- Fixed hostile fleet contact manifests so a single marker cannot balloon into
  hundreds of ships.
- Fixed long-range encounter insertion so 3km, 6km, and 9km starts preserve the
  selected standoff distance.
- Added enemy formation and composition hints to the pre-battle deployment map.
- Improved fleet targeting behavior so ships support shared fleet priorities
  when safe and focus immediate threats when under pressure.
- Restored 3km prosecution behavior for blue ships and nearby combatants.
- Fixed artillery titans so their turrets can prosecute targets outside the
  narrow forward arc issue.
- Doubled blue shock cannon projectile speed.
- Added Green damage falloff past 1,000m and replaced Green point-defense lasers
  with green-tinted bullet point defense.
- Brought bomber anti-ship torpedoes up to the standard missile speed cap.
- Anti-ship torpedoes now retarget another hostile non-fighter craft when their
  target dies, or detonate immediately if no valid ship target remains.

## Validation

- `CampaignForceOwnershipTest`
- `CampaignOvermapEncounterFlowTest`
- `CampaignStrategicCommandHudTest`
- `MissileRoleBehaviorTest`
- `AISystemSmallCraftRangeTest`

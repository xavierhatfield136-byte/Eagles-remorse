# Eagles Remorse 1.0.1.12

This release focuses on ship weapon presentation, faction audio consistency,
missile speed sanity, cleaner capital/titan turret layouts, the first local
Team E custom-content pipeline, and the first-hour/AAR release-readiness pass.

## Downloads

- Windows portable build: `EaglesRemorse-1.0.1.12-windows-x64-full.zip`
- Linux portable build: `EaglesRemorse-1.0.1.12-linux-x64.tar.gz`
- macOS app bundle ZIP: `EaglesRemorse-1.0.1.12-macos.zip`
- SHA-256 checksums: `SHA256SUMS-windows.txt`
- SHA-256 checksums: `SHA256SUMS-linux.txt`
- SHA-256 checksums: `SHA256SUMS-macos.txt`

The portable packages include the bundled runtime and packaged game assets used by the ship renderer.

## Highlights

- Replaced Red and Green turret PNGs and restored authored faction turret skins in gameplay.
- Removed fallback gun audio from normal firing rotation so faction weapons consistently use their newer fire sounds.
- Fixed Red artillery/picket player weapon firing behavior.
- Capped missile speed behavior and set heavy missile travel to the intended slower profile.
- Removed visible hull-padding haze from faction ship lighting.
- Shrunk gameplay turret rendering, with missile launcher and CIWS sprites reduced further so hull art stays readable.
- Reworked turret placement to use visible ship sprite alpha instead of collision/hitbox approximations.
- Recentered Hyperweapon Titan and Mobile Station Titan weapon mounts around their actual sprite midlines.
- Added all-faction regression coverage for turret centers sitting on visible hull art.
- Updated Red artillery and hyperweapon behavior tuning, including the Red hyperweapon kinetic barrage profile.
- Added Team E as a selectable custom-battle player team and preserved the choice in menu settings.
- Added the Team E Shipyard main-menu entry for local PNG-based custom ship creation.
- Added local custom ship storage under `save/custom_ships/`, kept out of Git.
- Added the Weapon Lab V1-A pipeline for local custom direct-fire cannon weapons using turret and projectile PNGs.
- Added local custom weapon storage under `save/custom_weapons/`, kept out of Git.
- Added custom weapon runtime profiles, custom projectile rendering, and custom turret sprite rendering for Team E custom ships.
- Added macOS packaging workflow support alongside Windows and Linux release packaging.
- Added `Commander's Academy` as the recommended first-time main-menu entry.
- Added normalized `BattleResult` recording for campaign, custom battle, and
  Academy/training battle outcomes.
- Added evidence-backed After-Action Report generation and immediate game-over
  summaries for tactical, Academy, and custom battles.
- Added local-only Academy progress and playtest telemetry under the user data
  directory.
- Fixed recent Commander's Academy tutorial progression blockers, including the
  carrier/loadout swap and yellow trade-hub course objective.
- Fixed a performance-soak release blocker where healthy multipart ship sprites
  could decode during measured render frames.
- Verified the Windows portable ZIP through manifest, runtime asset loadability,
  clean extraction, and outside-repository isolated launch smoke.

## Validation

- `TurretVisualMountRegressionTest`
- `TitanGeometryRegressionTest`
- `HyperweaponBehaviorTest`
- `RendererHudLayoutTest`
- `OwnerPlaytestPhaseFiveTacticalCleanupAuditTest`
- `CustomShipRegistryTest`
- `CustomShipGeneratorAndTeamETest`
- `CustomWeaponRegistryTest`
- `CustomWeaponRuntimeIntegrationTest`
- `BattleResultAnalysisServiceTest`
- `CommandSchoolOverworldExpansionTest`
- `TutorialWarpRegressionTest`
- `ShipDamagePatchLibraryTest`
- `currentReleaseVerification`
- `productionValidation`
- `phase11ReleaseContract`
- `performanceGuardrailsCi`
- `screenshotRegression`
- `phase11Packaging`
- `scripts\verify-windows-portable-distribution.ps1`
- `scripts\smoke-windows-portable-launch.ps1`
- `git diff --check`

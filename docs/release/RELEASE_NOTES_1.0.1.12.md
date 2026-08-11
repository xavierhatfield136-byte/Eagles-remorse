# Eagles Remorse 1.0.1.12

This release focuses on ship weapon presentation, faction audio consistency, missile speed sanity, and cleaner capital/titan turret layouts.

## Downloads

- Windows portable build: `EaglesRemorse-1.0.1.12-windows-x64-full.zip`
- Linux portable build: `EaglesRemorse-1.0.1.12-linux-x64.tar.gz`
- SHA-256 checksums: `SHA256SUMS-windows.txt`
- SHA-256 checksums: `SHA256SUMS-linux.txt`

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

## Validation

- `TurretVisualMountRegressionTest`
- `TitanGeometryRegressionTest`
- `HyperweaponBehaviorTest`
- `RendererHudLayoutTest`
- `OwnerPlaytestPhaseFiveTacticalCleanupAuditTest`
- `git diff --check`

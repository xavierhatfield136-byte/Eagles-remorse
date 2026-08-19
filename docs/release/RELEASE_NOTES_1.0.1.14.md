# Eagles Remorse 1.0.1.14

This release focuses on cleaner battle setup control, retiring strike-era UI,
and making armor/interior damage behave more consistently during heavy fleet
combat.

## Downloads

- Windows portable build: `EaglesRemorse-1.0.1.14-windows-x64-full.zip`
- Linux portable build: `EaglesRemorse-1.0.1.14-linux-x64.tar.gz`
- macOS app bundle ZIP: `EaglesRemorse-1.0.1.14-macos.zip`

Package artifacts are produced by the release packaging workflow for this
version.

## Highlights

- Added explicit campaign battle setup distances of 3,000m, 6,000m, and 9,000m
  so players can choose short, medium, or long tactical insertion before
  entering combat.
- Updated the pre-battle deployment display so allied and enemy entry sides are
  labeled more clearly.
- Removed the old overworld `Launch Strike` action and retired strike controls
  from the tactical HUD.
- Removed the internal crew repair panel from the tactical HUD.
- Replaced the legacy beam and missile sprite panels with standard drawn HUD
  controls that match the rest of the in-game menu style.
- Removed obsolete beam and missile setting image assets.
- Added armor-room repair lockouts so armor and armory rooms cannot immediately
  regenerate after taking damage.
- Improved interior projectile damage routing so destroyed rooms no longer trap
  valid damage and shots can resolve against nearby live compartments.
- Rebalanced non-missile, non-laser projectile cadence by reducing fire rate and
  increasing shot damage while preserving mining, superweapon, and point-defense
  behavior.

## Validation

- `compileJava`
- `CampaignStrategicCommandHudTest`
- `RendererHudLayoutTest`
- `CrewInternalViewSystemTest`
- `CustomBattleMothershipStrikeAvailabilityTest`

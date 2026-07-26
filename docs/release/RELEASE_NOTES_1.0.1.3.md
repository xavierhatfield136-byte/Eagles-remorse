# Eagles Remorse 1.0.1.3

This release promotes the Phase 9 completion branch back to the foremost build. It focuses on campaign resource cleanup, fleet control polish, readable tactical menus, broader faction shipyard purchasing, and a sharper missile-first combat opening.

## Downloads

- Windows installer: `EaglesRemorse-1.0.1.exe`, when the Windows packaging workflow completes the WiX installer step.
- Windows portable build: `EaglesRemorse-1.0.1.3.zip`
- Linux portable build: `EaglesRemorse-1.0.1.3-linux-x64.tar.gz`

All packages include a Java 21 runtime. Players do not need to install Java.

## Highlights

- Removed player-facing fuel, supply, ammo, salvage stock, and finite strike munition pressure from the campaign economy. The player now mainly manages credits, ore, and fleet attrition.
- Converted tactical strikes to cooldown-based use, with torpedo and bomber strikes at 60 seconds and the nuclear option at 300 seconds.
- Improved campaign and tactical menu readability so text fits inside the intended dark panels instead of colliding with borders and frame art.
- Removed the Alpha Readiness button from the main menu.
- Fixed custom power allocation so player-set bus values stay custom instead of being forced back into one preset.
- Added defensive and offensive fleet formations, with miners and haulers ignoring player combat formation orders so they can keep mining and hauling.
- Expanded shipyard offers beyond the old small-hull set. Faction yards can now sell larger vessels, carriers, capital hulls, and selected titan-class hulls as the campaign progresses.
- Preserved seller faction identity for purchased ships, so Green and Yellow purchases keep their faction hull doctrine and presentation after joining the player fleet.
- Doubled missile projectile speed and added an opening missile standoff behavior before ships close into gun engagements, making point defense and PD craft more strategically important.
- Added focused regression coverage for resource simplification, power controls, formation behavior, faction shipyard purchases, and missile standoff combat.

## Validation

- `compileJava`
- `CampaignSensorSuiteTest`
- `PlayerResourceSimplificationTest`
- `TacticalCombatDepthSystemTest`
- `CampaignFleetBuildingIntegrationTest`
- `MissileRoleBehaviorTest`
- `TitanAbilitySystemTest`
- `WarpAndStrikeCraftRegressionTest`
- `ProjectileScalePolicyTest`
- `AISystemSmallCraftRangeTest`
- `CampaignTacticalAlignmentTest`

GitHub packaging workflows run the current release verification suite before attaching Windows and Linux release artifacts.

See `KNOWN_ISSUES.md` and `SYSTEM_REQUIREMENTS.md` for current limitations and hardware guidance.

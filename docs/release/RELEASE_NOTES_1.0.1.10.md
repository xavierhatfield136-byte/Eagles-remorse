# Eagles Remorse 1.0.1.10

This release focuses on campaign playtest fixes: cleaner starting battles, more reliable overworld cleanup, stronger Red pressure, station capture, Titan fleet rule updates, and safer packaged assets.

## Downloads

- Windows portable build: `EaglesRemorse-1.0.1.10-windows-x64-full.zip`
- Linux portable build: `EaglesRemorse-1.0.1.10-linux-x64-full.zip`
- SHA-256 checksums: `SHA256SUMS-windows.txt`
- SHA-256 checksums: `SHA256SUMS-linux.txt`

The Windows portable package includes a Java 21 runtime. Players do not need to install Java separately.

## Highlights

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

## Validation

- `CampaignPersistentFleetShopTest`
- `TitanFleetSystemTest`
- `CarrierSystemIdleCleanupTest`
- `CampaignOvermapCheckpointTest`
- `CampaignForceOwnershipTest`
- `AISystemSmallCraftRangeTest`
- `MissileRoleBehaviorTest`
- `git diff --check`
- Windows portable staged-folder manifest verification
- Windows portable ZIP manifest verification
- Windows clean-extract manifest verification
- Runtime asset loadability verification
- Linux package manifest verification

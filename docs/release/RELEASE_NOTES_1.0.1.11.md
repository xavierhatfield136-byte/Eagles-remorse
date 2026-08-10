# Eagles Remorse 1.0.1.11

This release focuses on campaign save slots, fleet provenance, shipyard/refit UI fixes, stronger Red campaign pressure, cleaner tactical deployment, and safer portable packaging with runtime assets included.

## Downloads

- Windows portable build: `EaglesRemorse-1.0.1.11-windows-x64-full.zip`
- Linux portable build: `EaglesRemorse-1.0.1.11-linux-x64.tar.gz`
- SHA-256 checksums: `SHA256SUMS-windows.txt`
- SHA-256 checksums: `SHA256SUMS-linux.txt`

The portable packages include the bundled runtime and packaged game assets used by the ship renderer.

## Highlights

- Added three campaign save slots and slot-aware campaign resume/save behavior.
- Added ship provenance tracking so campaign saves preserve ownership/source data more reliably.
- Restricted campaign refits to Team Blue ships while keeping custom battle refits unrestricted.
- Improved ship commissioning UI costs and reduced heavy image usage in the commission menu.
- Added fleet power star indicators and updated fleet classification behavior.
- Increased Red fleet reserves, Earthward pressure, and heavy fleet response composition.
- Reduced friendly coalition over-follow behavior for large Blue fleets.
- Fixed campaign modification persistence for player-owned ships.
- Removed edge-map non-mission fortress fleet magnets and moved roaming edge groups inward.
- Doubled open-space fleet clash starting separation.
- Fixed tactical encounter deployment so repeated roles and heavy ships do not spawn in a single stack.
- Made persistent Blue fleet tactical deployment respect the selected fleet formation.
- Rebalanced carrier wings: half the craft quantity, doubled fighter/bomber/drone durability and damage.

## Validation

- `TitanHullRoleIntegrationTest`
- `TitanAbilitySystemTest`
- `FourTeamCarrierCapTest`
- `CampaignFleetEscalationTest`
- `CampaignMapDisciplineTest`
- `CampaignForceOwnershipTest`
- `CampaignPersistentFleetShopTest`
- `git diff --check`
- Windows portable staged-folder manifest verification
- Windows portable ZIP manifest verification
- Windows clean-extract manifest verification
- Runtime asset loadability verification
- Linux package manifest verification through the release workflow

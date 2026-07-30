# Eagles Remorse 1.0.1.4

This release focuses on campaign fleet correctness, coalition support clarity, small-craft performance, and tactical menu readability.

## Downloads

- Windows installer: `EaglesRemorse-1.0.1.4.exe`, when the Windows packaging workflow completes the WiX installer step.
- Windows portable build: `EaglesRemorse-1.0.1.4.zip`
- Linux portable build: `EaglesRemorse-1.0.1.4-linux-x64.tar.gz`

All packages include a Java 21 runtime. Players do not need to install Java.

## Highlights

- Added a deduplicated campaign force roster resolver so live tactical ships, pool records, and search-group projections no longer disagree about whether a fleet has real ships.
- Fixed empty overworld fleets by making normal physical markers and tactical participation require concrete roster membership instead of cached strength alone.
- Reworked coalition support so generic overmap encounters no longer silently spawn the named Green/Yellow task groups.
- Added deterministic nearby coalition support selection with participation records, spawn caps, HUD messaging, and telemetry.
- Added coalition support small-craft budgets so carrier/titan support cannot recreate giant friendly fighter clouds.
- Confirmed non-capital and non-titan ships do not auto-spawn escort fighters.
- Improved tactical menu sizing and layout so text and controls fit inside menu frames more reliably.
- Added a detailed implementation checklist for the ghost-fleet and coalition-support fix path.
- Added regression coverage for shipless fleets, pool-backed fleet counts, roster deduplication, Green Contract overmap suppression, support caps, menu layout, and small-craft behavior.

## Validation

- `CampaignNpcFleetAiTest`
- `CampaignCompatibilityOverhaulTest`
- `AISystemSmallCraftRangeTest`
- `TitanAbilitySystemTest`

Manual acceptance is still recommended on a save that previously reproduced ghost fleets or the unexplained Green support swarm.

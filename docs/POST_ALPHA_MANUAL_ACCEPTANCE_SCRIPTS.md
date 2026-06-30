# Post-Alpha Manual Acceptance Scripts

Use a fixed seed, capture the final strategic screen, record the save identifier, and attach the harness report for every run. Automated counterparts live in `PostAlphaAcceptanceHarnessTest`, `FlagshipPlayerFacingIntegrationTest`, and `CooperativeProductionReadinessTest`.

## Yellow Civil-War Outcomes

Run and save each family independently: Bright reunification, Dark domination, negotiated settlement, partition, mutual collapse, Bright coalition protectorate, Dark Red protectorate, and foreign occupation. Confirm the ending text, territorial map, alliances, fleet consequence, economy consequence, and restored save all agree. `PostAlphaManualAcceptanceHarness.civilWarOutcomeFamilies()` supplies deterministic setup and captured factual evidence for all eight.

## Persistent Rival

Run `PostAlphaManualAcceptanceHarness.rivalThreeEncounterScenario()`. Confirm the named rival retreats alive, returns after repair, recognizes the repeated player doctrine, changes countermeasure, and retains three encounter memories after save/load.

## Flagship Emergency

Run `FlagshipEmergencyAcceptanceHarness.run(playerShip)`. Open the fleet-board schematic, verify simultaneous fire/decompression/power/casualty warnings, order containment and evacuation, engage slow-time, then reconcile repair parts at campaign scale.

## Alternative Campaigns

For each catalog definition: start a fresh game, inspect its unique objective/tutorial, save and reload, run a long virtual session, trigger defeat, restart, and trigger victory. Only Blue Liberation, Bright Yellow Restoration, and Dark Orange-Yellow Ascendancy are release-selected; the remaining variants stay experimental.

## Cooperative Command

Repeat with one through six players. Exercise every role, captain vote/override, per-client accessibility, pause, disconnect/reconnect, save/load, and the three-hour virtual soak. Confirm empty seats immediately return to automation and all connected clients finish on the host checksum.

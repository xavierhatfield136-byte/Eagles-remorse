# Section 28 Alpha Fix Checklist

This checklist turns the completed section 28 worksheet into implementation work. A box is complete only when the change is live in normal play, visible to the player where relevant, and covered by focused validation or a recorded playtest note.

## P0 Campaign Pressure

- [x] Reduce starting strategic strike inventory so strikes are a scarce operational resource.
- [x] Increase torpedo, carrier sortie, and atomic strike costs so strikes cannot be used as a free extra combat punch.
- [x] Keep strike rearm readable at hubs through make/buy/rebuild language.
- [x] Make strike rearm expensive enough that rebuilding stores competes with fleet growth.
- [x] Add or verify salvage/cache rewards that can recover limited strike stores.
- [x] Increase early open-space hostile fleet pressure so routes contain visible enemy fleet contacts.
- [ ] Re-test strike-heavy routes and record whether strike use now feels deliberate.

## P0 Economy And Sustain

- [x] Reduce early ore snowball or increase early fleet/refit costs.
- [x] Reduce passive damage-control sustain so damaged ships still create operational pressure.
- [x] Reduce transport repair/support aura strength so transport ships help without erasing attrition.
- [x] Re-test one mine-return-buy-relaunch loop and record whether the next launch is stronger but not runaway.

## P0 Diplomacy Consequence

- [x] Make Green support convert favor into noticeable stores, intel, relay, and combat support.
- [x] Make Yellow leverage convert into noticeable fuel, salvage, trade, and route support.
- [x] Make allied trade/call-ins affect later encounter pressure, not just immediate flavor.
- [x] Keep interaction writing terse and command-oriented.
- [x] Add focused tests for support actions changing resources or route pressure.

## P1 Presentation Blockers

- [x] Remove temporary crew dialogue and voice lines until replacements are ready.
- [x] Replace the ship-destruction sine-wave placeholder sound.
- [ ] Normalize damage-stage visuals.
- [ ] Decide final disposition for wreck, prop, portal, and map-icon placeholders.
- [x] Fix top-screen HUD/menu text crowding.

## P2 Deferred Scope

- [x] Battle replay is post-alpha.
- [x] Visual battlefield editor is post-alpha/dev-only.
- [x] Mod browser is post-alpha.
- [x] Custom scenarios, challenge mode, and New Game Plus are post-release.

## Validation

- [x] Focused campaign pressure suite passes: `.\gradlew.bat test --tests CampaignStrategicStrikeCounterplayTest --tests CampaignStrategicTravelPressureTest --tests CampaignHubEconomyTest`.
- [x] Economy and diplomacy support tests pass: `.\gradlew.bat test --tests CampaignHubEconomyTest`.
- [x] Presentation asset tests pass: `.\gradlew.bat test --tests AlphaPresentationAssetTest`.
- [x] Command HUD crowding regression passes: `.\gradlew.bat test --tests CampaignStrategicCommandHudTest`.
- [x] Player mine-return-buy-relaunch playtest recorded: ore loop now feels satisfactory and does not let the first sector run away.
- [x] Direct Earthward-route interdiction regression passes: `.\gradlew.bat test --tests CampaignStrategicTravelPressureTest`.
- [ ] Player strike-route playtest recorded a second failure after the interdiction fix: an enemy spawned high in the pre-Earth theater, moved halfway down, despawned, and recon sweeps could not reacquire it. Next work should move this from section 28 tuning into the NPC fleet AI project.

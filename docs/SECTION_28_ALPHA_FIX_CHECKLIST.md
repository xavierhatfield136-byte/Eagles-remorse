# Section 28 Alpha Fix Checklist

This checklist turns the completed section 28 worksheet into implementation work. A box is complete only when the change is live in normal play, visible to the player where relevant, and covered by focused validation or a recorded playtest note.

## P0 Campaign Pressure

- [x] Reduce starting strategic strike inventory so strikes are a scarce operational resource.
- [x] Increase torpedo, carrier sortie, and atomic strike costs so strikes cannot be used as a free extra combat punch.
- [x] Keep strike rearm readable at hubs through make/buy/rebuild language.
- [x] Make strike rearm expensive enough that rebuilding stores competes with fleet growth.
- [ ] Add or verify salvage/cache rewards that can recover limited strike stores.
- [x] Increase early open-space hostile fleet pressure so routes contain visible enemy fleet contacts.
- [ ] Re-test strike-heavy routes and record whether strike use now feels deliberate.

## P0 Economy And Sustain

- [ ] Reduce early ore snowball or increase early fleet/refit costs.
- [x] Reduce passive damage-control sustain so damaged ships still create operational pressure.
- [x] Reduce transport repair/support aura strength so transport ships help without erasing attrition.
- [ ] Re-test one mine-return-buy-relaunch loop and record whether the next launch is stronger but not runaway.

## P0 Diplomacy Consequence

- [ ] Make Green support convert favor into noticeable stores, intel, relay, and combat support.
- [ ] Make Yellow leverage convert into noticeable fuel, salvage, trade, and route support.
- [ ] Make allied trade/call-ins affect later encounter pressure, not just immediate flavor.
- [ ] Keep interaction writing terse and command-oriented.
- [ ] Add focused tests for support actions changing resources or route pressure.

## P1 Presentation Blockers

- [ ] Remove temporary crew dialogue and voice lines until replacements are ready.
- [ ] Replace the ship-destruction sine-wave placeholder sound.
- [ ] Normalize damage-stage visuals.
- [ ] Decide final disposition for wreck, prop, portal, and map-icon placeholders.
- [ ] Fix top-screen HUD/menu text crowding.

## P2 Deferred Scope

- [x] Battle replay is post-alpha.
- [x] Visual battlefield editor is post-alpha/dev-only.
- [x] Mod browser is post-alpha.
- [x] Custom scenarios, challenge mode, and New Game Plus are post-release.

## Validation

- [x] Focused campaign pressure suite passes: `.\gradlew.bat test --tests CampaignStrategicStrikeCounterplayTest --tests CampaignStrategicTravelPressureTest --tests CampaignHubEconomyTest`.

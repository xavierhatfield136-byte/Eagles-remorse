# Owner Playtest Vertical Slice 3 Evidence

**Status:** Initial automated acceptance complete; rendered owner review pending  
**Scope:** Economy authority, logistics visibility, repair cost visibility, shortage recovery choices, and attrition persistence

## Implemented proof

- The campaign now exposes an `AUTHORITATIVE ECONOMY LEDGER` that names the live player-facing stores: credits, Fleet Ore, Yard Ore, fuel, supplies, ammo, and repair materials.
- Each ledger row shows current amount, capacity, expected use, and replenishment source.
- The resource manager includes the authoritative ledger so fuel/supplies/ammo/ore/salvage are not presented as hidden or ghost variables.
- Fleet Ore and Yard Ore are explicitly separated: Fleet Ore pays player purchases; Yard Ore feeds local faction construction.
- The ledger states the spend rule: travel, repair, refit, strategic strikes, and ship commissions spend displayed stores.
- Hub repair previews now show hub capability, support multiplier, local repair stock, finite supply/salvage cost, and a warning that one visit is partial rather than a full attrition reset.
- Transport repair support already drains finite supplies; Slice 3 now tests the visible support line and shutdown when supplies cannot sustain it.
- Shortage readouts expose recovery options: buy fuel/supplies, sell salvage, mine ore, salvage wrecks, divert to a hub, use Logistics Conservation, accept partial repairs, or retreat.

## Automated acceptance results

`OwnerPlaytestVerticalSliceThreeTest` covers:

- authoritative ledger visibility for all strategic resources;
- current/capacity/expected-use/replenishment wording;
- route travel spending the same displayed fuel/supply/ammo stores;
- hub repair spending finite supplies/salvage and improving but not fully erasing damage;
- shortage warnings and recovery choices;
- transport repair support supply drain and shutdown on shortage.

The expanded focused campaign selection passes **213 tests with zero failures** across Slice 1, Slice 2, Slice 3, strategic command UI, hub economy, finite campaign pressure, map clarity, fleet authority, multi-source intel, travel pressure, strikes, checkpoint persistence, operation lifecycle, and map presentation.

## Known accepted polish from owner review

- Some persistent intel markers still appear outside the player sensor sphere. This is acceptable for now, but the map-readability pass should distinguish live sensor contacts from stale intel ghosts more aggressively.
- It is still a little difficult to see where units are trying to move and invade. The later presentation pass should add clearer invasion lanes, operation arrows, or selected-operation movement intent.

## Remaining Slice 3 work

- [ ] Measure ore earned per minute for starter, midgame, and Transport Titan configurations.
- [ ] Set a target time-to-first-major-upgrade and tune mining around it.
- [ ] Prevent ten minutes of unattended mining at one patch from trivializing the campaign economy.
- [ ] Make salvage situationally competitive with mining after combat.
- [ ] Add seeded economy-loop tests that measure fleet power before and after one and three loops.
- [ ] Add full ledger reconciliation tests across combat, save/load, and rearm in addition to travel/docking/repair.
- [ ] Tune Standard and Iron attrition bands after the economy-loop measurements exist.

## Rendered owner review gate

- [ ] Open the Resource/Command view and confirm the authoritative ledger reads naturally.
- [ ] Select a long route and confirm fuel/supply/ammo expected use is understandable before travel.
- [ ] Travel once and confirm the displayed ledger updates after arrival.
- [ ] Dock at a Green repair hub and confirm the repair preview explains finite cost and partial recovery.
- [ ] Use repair once after taking damage and confirm the fleet improves but is not magically reset to perfect readiness.
- [ ] Drop fuel/supplies low and confirm recovery choices are visible instead of feeling like a dead end.

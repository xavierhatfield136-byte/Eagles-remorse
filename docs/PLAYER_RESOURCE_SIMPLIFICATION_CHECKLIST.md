# Player Resource Simplification Checklist

## Goal

Remove player-facing fuel, supplies, ammo, munitions, torpedo stockpiles, atomic stockpiles, and strike package conservation from Eagle's Remorse.

The player should only manage:

- Credits
- Ore
- Fleet composition and surviving ships

Difficulty should come from enemy fleet behavior, tactical pressure, encounter composition, map positioning, escalation, and smart hostile responses. It should not come from running out of loosely defined campaign stores.

## Intended End State

- Player travel does not spend fuel.
- Player repairs, transport support, scans, recon, route tools, and hub actions do not spend supplies.
- Player weapons, missiles, torpedoes, and strike craft do not spend ammo or munitions.
- Strategic and tactical strikes are infinite-use abilities gated by cooldowns, target quality, range, fleet capability, and consequence heat.
- Torpedo strike cooldown: 60 seconds.
- Heavy bomber / carrier sortie cooldown: 60 seconds.
- Atomic / nuclear strike cooldown: 300 seconds.
- Existing save fields for old resources can remain for compatibility, but they should not affect player decisions.

## Design Decisions To Lock Before Code

- Atomic cooldown exact value:
  - [x] 300 seconds
  - [ ] 180 seconds
  - [ ] 240 seconds
  - [ ] Randomized 180 to 300 seconds per launch

- Strike cooldown scope:
  - [x] Per battle only.
  - [ ] Shared between tactical battles and overmap.

- Overmap strikes:
  - [x] Do not prioritize overmap strike support right now because current play does not expose usable overmap strikes.
  - [ ] Revisit later if overmap strike actions become playable again.

- NPC/theater logistics:
  - [x] Keep strategic theater "supply state" as an AI/world simulation concept only
  - [ ] Rename it where player-facing text would confuse it with removed player supplies
  - [ ] Remove it entirely later if it still muddies the design

- Salvage:
  - [x] Keep physical salvage pickup rectangles in tactical play
  - [x] Convert each player-collected salvage pickup into 100 ore
  - [ ] Remove player-facing salvage stockpile/cost behavior if separate from pickups
  - [ ] Confirm whether salvage remains a temporary reward text only
  - [ ] Or convert all player-facing salvage costs/rewards into credits/ore/fleet recovery

- Sensor relay actions:
  - [x] Remove player sensor relay actions rather than repairing them, because they were intended to boost scan radius but currently do not function
  - [ ] Preserve passive/non-player sensor systems only if still useful for enemy/theater simulation

- Old save migration:
  - [x] Convert old fuel/supplies/ammo values into credits and/or ore where convenient
  - [x] Treat exact migration details as low-risk because there is no external save base to protect

## Code Areas To Change

### Campaign State And Persistence

- [ ] Add strike cooldown fields to `CampaignSystemModels.CampaignState`.
  - `torpedoStrikeCooldownSec`
  - `carrierSortieCooldownSec`
  - `atomicStrikeCooldownSec`

- [ ] Tick those cooldowns during campaign runtime updates.

- [ ] Keep old save fields for compatibility:
  - `campaignFuel`
  - `campaignSupplies`
  - `campaignAmmo`
  - `strategicTorpedoCharges`
  - `strategicSortiesLaunched`
  - `strategicAtomicCharges`

- [ ] Stop restoring old resource shortages into active gameplay gates.

- [ ] Persist new per-battle cooldown fields if battles can be saved mid-fight.

- [ ] Convert old fuel/supplies/ammo values into credits and/or ore during migration when touching checkpoint restore.

### Campaign Resource Accessors

- [ ] Change `CampaignSystem.campaignFuel`, `campaignSupplies`, and `campaignAmmo` behavior or retire their use from player-facing systems.

- [ ] Prefer removing gameplay dependencies over returning fake large values.

- [ ] If compatibility requires fake values, keep that contained and mark it as legacy.

### Travel And Route Pressure

- [ ] Remove fuel/supply/ammo attrition from `updateCampaignTravel`.

- [ ] Keep travel risk, interception pressure, enemy alert, and exposure if they create interesting choices.

- [ ] Rewrite route forecasts to show:
  - Risk
  - Enemy presence
  - Intel confidence
  - Distance / ETA
  - Fleet condition if relevant

- [ ] Remove route blockers like:
  - insufficient fuel
  - insufficient supplies
  - insufficient ammo

### Repairs And Transport Support

- [ ] Change `consumeTransportRepairSupport` so repair support does not spend supplies.

- [ ] Change `reportTransportRepairSupport` so active transport support depends on supporting ships, not supplies.

- [ ] Convert repair limits to ship availability, repair time, docking access, ore, credits, or fleet damage where needed.

### Campaign Actions And Overmap Menu

- [ ] Remove supply costs from recon/sensor actions, or convert them to cooldowns.

- [ ] Remove disabled reasons that mention insufficient supplies/fuel/ammo.

- [ ] Remove sensor relay actions that were intended to boost scanning radius but currently do not.

- [ ] Update action descriptions:
  - "Spend supplies" becomes "Run scan", "Deploy relay", or "Commit recon team"
  - "Buy fuel/supplies" becomes "Repair, refit, trade ore, commission ships"

- [ ] Review `CampaignActionCatalog`, `TacticalMapActionCatalog`, and `CampaignSystem.campaignVisibleActions`.

### Strategic And Tactical Strikes

- [ ] Replace torpedo charges with `torpedoStrikeCooldownSec`.

- [ ] Replace sortie deck consumption with `carrierSortieCooldownSec`.

- [ ] Replace atomic charges with `atomicStrikeCooldownSec`.

- [ ] Apply these cooldowns per battle, not as long-term overmap inventory.

- [ ] De-prioritize overmap strike launch paths unless they are needed for tests or hidden UI cleanup.

- [ ] Remove ammo/fuel/supply costs from:
  - `buildStrikePreflight`
  - `buildTacticalStrikePreflight`
  - `launchStrategicTorpedoStrike`
  - `launchStrategicSortie`
  - `launchStrategicAtomicStrike`
  - tactical strike launch helpers

- [ ] Preserve target gates:
  - Torpedo requires tracked hostile target
  - Carrier sortie requires identified hostile target
  - Atomic requires target-quality hostile target

- [ ] Preserve range gates.

- [ ] Preserve strike consequences:
  - Enemy alert
  - Exposure
  - Recent strike pressure
  - Countermeasures
  - Political blowback for atomic use

- [ ] Update strike button text and confirmation text to show cooldown instead of inventory/cost.

### Tactical Ammo And Munitions

- [ ] Update `TacticalCombatDepthSystem.canFireWeapon` so missile/ballistic ammo never blocks firing.

- [ ] Update `TacticalCombatDepthSystem.onWeaponFired` so ammo counters are not decremented.

- [ ] Replace tactical command panel ammo display with heat/readiness.

- [ ] Review strike craft secondary munition behavior in `Ship` and `TitanAbilitySystem`.

- [ ] Ensure missiles, torpedoes, CIWS, bomber payloads, and regular weapons are cooldown/heat/range limited, not ammo limited.

### Economy And Hub Services

- [ ] Remove hub services that primarily buy fuel/supplies/ammo.

- [ ] Convert useful services into:
  - Repairs
  - Ore trade
  - Ship commissioning
  - Upgrades
  - Intel
  - Fleet recovery

- [ ] Remove or rewrite `Strike Rearm` service.
  - Replacement: cooldown reduction service, if desired
  - Cost should be credits/ore only

- [ ] Audit `CampaignHubServiceSystem` and hub service text.

- [ ] Convert tactical salvage pickup rewards to ore:
  - Player pickup: `+100 ore`
  - No separate player salvage stockpile required

### Rewards And Mission Text

- [ ] Remove fuel/supplies/ammo rewards from mission outcomes.

- [ ] Convert rewards to credits, ore, ships, repairs, favor, intel, or cooldown reduction.

- [ ] Update sector lore that references fuel or supplies as required player resources.

- [ ] Keep words like "supply line" only where they mean NPC/theater logistics and are not displayed as player inventory.

### HUD, Menus, And Reports

- [ ] Remove fuel/supplies/ammo display from campaign HUD.

- [ ] Remove "fleet stores" wording where it means fuel/supplies/ammo.

- [ ] Update:
  - `Renderer` strategic resource panels
  - campaign sidebar resource summaries
  - after-action reports
  - strike readiness panels
  - tactical map strike tab
  - hover tooltips

- [ ] Replace with:
  - Credits
  - Ore
  - Fleet size / losses
  - Strike cooldowns
  - Intel / exposure / enemy alert

### Tutorial And Documentation

- [ ] Update tutorial references to trade, fuel, supplies, ammo, rearm, and resupply.

- [ ] Update first-hour briefing text.

- [ ] Update campaign docs/checklists that still describe player fuel/supply/ammo pressure.

### Tests To Update Or Add

- [ ] Add regression test: campaign travel does not reduce fuel/supplies/ammo.

- [ ] Add regression test: low old resource values do not block travel, recon, repair support, or strikes.

- [ ] Add regression test: torpedo strike starts a 60s cooldown and does not spend ammo/fuel/charges.

- [ ] Add regression test: carrier sortie starts a 60s cooldown and does not spend ammo/fuel/supplies/deck inventory.

- [ ] Add regression test: atomic strike starts 300s cooldown and does not spend ammo/fuel/supplies/atomic charges.

- [ ] Add regression test: strikes are blocked while cooldown is active.

- [ ] Add regression test: cooldown reaches ready state after enough campaign/tactical time passes.

- [ ] Update tests that currently expect resource spending:
  - `CampaignStrategicStrikeCounterplayTest`
  - `CampaignStrategicTravelPressureTest`
  - `OwnerPlaytestVerticalSliceTwoTest`
  - `OwnerPlaytestVerticalSliceThreeTest`
  - `CampaignStrategicCommandHudTest`
  - `CampaignHubEconomyTest`
  - `TacticalCombatDepthSystemTest`

## Suggested Implementation Order

1. Add cooldown fields and helper methods.
2. Remove tactical ammo blocking.
3. Convert strike preflight and launch paths to cooldowns.
4. Remove travel attrition and old resource blockers.
5. Convert repair/recon/support supply costs to cooldowns or free actions.
6. Rewrite player-facing HUD/action/report/tutorial text.
7. Update save compatibility and tests.
8. Run focused strike, travel, HUD, and campaign checkpoint tests.
9. Run full compile and broader campaign test group.

## Acceptance Criteria

- [ ] Starting a campaign with `campaignFuel = 0`, `campaignSupplies = 0`, and `campaignAmmo = 0` is still playable.
- [ ] The player can travel without fuel.
- [ ] The player can fire weapons and missiles without ammo.
- [ ] The player can launch torpedo and bomber strikes repeatedly, limited by 60s cooldowns.
- [ ] The player can launch nuclear/atomic strikes repeatedly, limited by a 300s cooldown.
- [ ] Player-collected salvage rectangles convert into 100 ore.
- [ ] Sensor relay actions that do not boost scan radius are removed from player-facing menus.
- [ ] No normal player-facing menu says fuel, supplies, ammo, munitions, rearm, torpedo inventory, or atomic inventory as a spendable resource.
- [ ] Campaign difficulty still rises through enemy behavior, smarter contacts, stronger fleets, pressure, positioning, and consequences.

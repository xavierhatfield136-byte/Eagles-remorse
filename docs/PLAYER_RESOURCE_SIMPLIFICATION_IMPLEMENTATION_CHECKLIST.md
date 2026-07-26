# Player Resource Simplification Implementation Checklist

## Locked Design Decisions

- [x] Player-managed resources are only credits, ore, and fleet ships.
- [x] Remove player fuel, supplies, ammo, munitions, torpedo inventory, sortie inventory, atomic inventory, and strike rearm pressure.
- [x] Keep NPC/theater supply-line language as world simulation only.
- [x] Keep physical salvage pickup rectangles.
- [x] Convert player-collected salvage pickups into `+100 ore`.
- [x] Convert old save fuel/supplies/ammo into ore during migration.
- [x] Convert old campaign salvage stock into ore during migration.
- [x] Torpedo strike cooldown: `60s`.
- [x] Heavy bomber / carrier sortie cooldown: `60s`.
- [x] Atomic / nuclear strike cooldown: `300s`.
- [x] Strike cooldowns are per battle only.
- [x] Remove broken player sensor relay actions instead of repairing them.

## Phase 1 - State And Migration

- [ ] Add per-battle strike cooldown fields to `CampaignSystemModels.CampaignState`.
  - [ ] `torpedoStrikeCooldownSec`
  - [ ] `carrierSortieCooldownSec`
  - [ ] `atomicStrikeCooldownSec`

- [ ] Add helper constants in `CampaignSystem`.
  - [ ] `TORPEDO_STRIKE_COOLDOWN_SEC = 60.0`
  - [ ] `CARRIER_SORTIE_COOLDOWN_SEC = 60.0`
  - [ ] `ATOMIC_STRIKE_COOLDOWN_SEC = 300.0`

- [ ] Tick cooldowns during active battle/campaign runtime updates.

- [ ] Keep legacy fields loadable for old checkpoints.
  - [ ] `campaignFuel`
  - [ ] `campaignSupplies`
  - [ ] `campaignAmmo`
  - [ ] `campaignSalvage`
  - [ ] `strategicTorpedoCharges`
  - [ ] `strategicSortiesLaunched`
  - [ ] `strategicAtomicCharges`

- [ ] On checkpoint restore, migrate old stockpiles into ore.
  - [ ] `ore += campaignFuel + campaignSupplies + campaignAmmo`
  - [ ] `ore += campaignSalvage * 100`
  - [ ] Clamp migrated values to safe non-negative ranges.
  - [ ] Clear or ignore old stockpile fields after migration so shortages cannot return.

- [ ] Decide whether cooldowns need checkpoint save/restore for mid-battle saves.
  - [ ] If yes, add fields to `CampaignCheckpointStore`.
  - [ ] If no, reset cooldowns to ready on restored battle entry.

## Phase 2 - Tactical Ammo And Munitions

- [ ] Update `TacticalCombatDepthSystem.canFireWeapon`.
  - [ ] Keep heat gating.
  - [ ] Remove missile ammo gate.
  - [ ] Remove ballistic ammo gate.

- [ ] Update `TacticalCombatDepthSystem.onWeaponFired`.
  - [ ] Keep heat gain.
  - [ ] Stop decrementing missile ammo.
  - [ ] Stop decrementing ballistic ammo.
  - [ ] Stop decrementing mine ammo if it is player-facing.

- [ ] Update tactical command panel text.
  - [ ] Replace `Ammo INF/...` with heat/readiness/status text.

- [ ] Audit strike craft munition methods.
  - [ ] `Ship.consumeStrikeCraftMunition`
  - [ ] strike secondary munition fields
  - [ ] bomber payload paths
  - [ ] `TitanAbilitySystem` reload/refill behavior

- [ ] Ensure all player weapons remain limited by cooldown, heat, range, targeting, and survival, not ammo.

## Phase 3 - Tactical Strike Cooldowns

- [ ] Update tactical strike preflight in `CampaignSystem`.
  - [ ] Remove charge checks.
  - [ ] Remove ammo/fuel/supply checks.
  - [ ] Add cooldown checks.
  - [ ] Keep target checks.
  - [ ] Keep range/subzone checks.

- [ ] Update tactical torpedo launch.
  - [ ] Do not spend torpedo charges.
  - [ ] Do not spend ammo/fuel.
  - [ ] Set `torpedoStrikeCooldownSec = 60.0` on successful launch.
  - [ ] Roll back cooldown if launch fails.

- [ ] Update tactical carrier sortie launch.
  - [ ] Do not spend sortie/deck inventory.
  - [ ] Do not spend ammo/fuel/supplies.
  - [ ] Set `carrierSortieCooldownSec = 60.0` on successful launch.
  - [ ] Roll back cooldown if launch fails.

- [ ] Update tactical atomic launch.
  - [ ] Do not spend atomic charges.
  - [ ] Do not spend ammo/fuel/supplies.
  - [ ] Set `atomicStrikeCooldownSec = 300.0` on successful launch.
  - [ ] Roll back cooldown if launch fails.

- [ ] Update strike readiness labels.
  - [ ] Show `READY` when cooldown is zero.
  - [ ] Show `COOLDOWN Ns` when cooling down.
  - [ ] Remove inventory/cost wording.

## Phase 4 - Overmap Strike Cleanup

- [ ] Confirm overmap strike actions are hidden/unusable in current play.

- [ ] De-prioritize full overmap strike redesign.

- [ ] Remove old player-facing cost text from overmap strike descriptions if still visible.

- [ ] If hidden overmap launch methods remain callable by tests, make them non-consuming and cooldown-aware enough to avoid regressions.

## Phase 5 - Travel, Repairs, And Recon

- [ ] Remove fuel/supply/ammo attrition from `updateCampaignTravel`.

- [ ] Keep travel risk and interception pressure.

- [ ] Remove travel blockers based on old resources.
  - [ ] `fuel critically low`
  - [ ] `supplies critically low`
  - [ ] `ammo`

- [ ] Rewrite route forecast text.
  - [ ] ETA
  - [ ] Risk
  - [ ] Intel
  - [ ] Enemy presence
  - [ ] Fleet condition

- [ ] Update transport repair support.
  - [ ] `consumeTransportRepairSupport` no longer spends supplies.
  - [ ] `reportTransportRepairSupport` depends on available support ships only.

- [ ] Remove broken player sensor relay actions.
  - [ ] Remove action definitions.
  - [ ] Remove disabled reasons.
  - [ ] Remove related menu text.
  - [ ] Keep passive scan/sensor systems only if still meaningful.

## Phase 6 - Salvage And Rewards

- [ ] Find tactical salvage pickup handling.

- [ ] Change player pickup reward to `+100 ore`.

- [ ] Remove player campaign salvage stockpile uses.

- [ ] Convert salvage costs/rewards into ore, credits, repairs, intel, or fleet recovery.

- [ ] Update pickup banner/report text to say ore gained.

- [ ] Update mission reward text.
  - [ ] Remove fuel rewards.
  - [ ] Remove supplies rewards.
  - [ ] Remove ammo rewards.
  - [ ] Remove rearm rewards.

## Phase 7 - Economy And Hub Services

- [ ] Audit `CampaignHubServiceSystem`.

- [ ] Remove services whose main purpose is buying fuel/supplies/ammo.

- [ ] Rewrite useful services around:
  - [ ] Repairs
  - [ ] Ore trade
  - [ ] Ship commissioning
  - [ ] Upgrades
  - [ ] Intel
  - [ ] Fleet recovery

- [ ] Remove or rewrite `Strike Rearm`.
  - [ ] Preferred replacement: cooldown reduction service.
  - [ ] Cost must be credits and/or ore only.

- [ ] Remove "resupply" wording where it means player stockpiles.

## Phase 8 - HUD, Menus, And Text

- [ ] Remove player fuel/supplies/ammo display from `Renderer`.

- [ ] Remove "fleet stores" when it means fuel/supplies/ammo.

- [ ] Update campaign sidebar resource summaries.

- [ ] Update after-action reports.

- [ ] Update strategic resource panels.

- [ ] Update strike readiness panels.

- [ ] Update tactical map strike tab.

- [ ] Update hover tooltips.

- [ ] Replace player resource displays with:
  - [ ] Credits
  - [ ] Ore
  - [ ] Fleet size
  - [ ] Fleet losses
  - [ ] Strike cooldowns
  - [ ] Intel
  - [ ] Exposure
  - [ ] Enemy alert

## Phase 9 - Tutorial And Documentation

- [ ] Update `TutorialSystem`.

- [ ] Update `FirstHourOnboardingSystem`.

- [ ] Remove tutorial goals about fuel/supplies/ammo/rearm/resupply.

- [ ] Keep mining and ore spending tutorial beats.

- [ ] Update docs that describe player fuel/supply/ammo pressure.

- [ ] Keep NPC/theater supply-line docs when clearly not player inventory.

## Phase 10 - Tests

- [ ] Add/adjust tactical ammo test.
  - [ ] Weapons keep firing with old ammo counters at zero.
  - [ ] Heat still blocks firing if intended.

- [ ] Add/adjust salvage test.
  - [ ] Player pickup gives `+100 ore`.

- [ ] Add/adjust travel test.
  - [ ] Campaign travel does not reduce fuel/supplies/ammo.
  - [ ] Low old resource values do not block travel.

- [ ] Add/adjust repair support test.
  - [ ] Transport repair support does not spend supplies.
  - [ ] Low old supplies do not disable support.

- [ ] Add/adjust strike tests.
  - [ ] Torpedo starts `60s` cooldown and does not spend old stores.
  - [ ] Carrier sortie starts `60s` cooldown and does not spend old stores.
  - [ ] Atomic starts `300s` cooldown and does not spend old stores.
  - [ ] Strike action is blocked while its cooldown is active.
  - [ ] Strike action becomes ready after cooldown expires.

- [ ] Update tests likely to expect old spending:
  - [ ] `CampaignStrategicStrikeCounterplayTest`
  - [ ] `CampaignStrategicTravelPressureTest`
  - [ ] `OwnerPlaytestVerticalSliceTwoTest`
  - [ ] `OwnerPlaytestVerticalSliceThreeTest`
  - [ ] `CampaignStrategicCommandHudTest`
  - [ ] `CampaignHubEconomyTest`
  - [ ] `TacticalCombatDepthSystemTest`
  - [ ] `ShootingRangeNoGalaxyMapBootstrapTest`

## Verification Commands

- [ ] `.\gradlew.bat compileJava`

- [ ] `.\gradlew.bat test --tests TacticalCombatDepthSystemTest`

- [ ] `.\gradlew.bat test --tests CampaignStrategicStrikeCounterplayTest`

- [ ] `.\gradlew.bat test --tests CampaignStrategicTravelPressureTest`

- [ ] `.\gradlew.bat test --tests CampaignStrategicCommandHudTest`

- [ ] `.\gradlew.bat test --tests CampaignHubEconomyTest`

- [ ] Run any newly added resource simplification regression test class.

## Final Acceptance Checklist

- [ ] A campaign remains playable with old `campaignFuel`, `campaignSupplies`, and `campaignAmmo` at zero.
- [ ] No player travel action requires fuel.
- [ ] No player repair/support/recon action requires supplies.
- [ ] No player weapon requires ammo.
- [ ] No player strike requires ammo, fuel, supplies, torpedo charges, sortie inventory, or atomic charges.
- [ ] Torpedo strikes are limited by a `60s` battle cooldown.
- [ ] Heavy bomber/carrier strikes are limited by a `60s` battle cooldown.
- [ ] Atomic/nuclear strikes are limited by a `300s` battle cooldown.
- [ ] Salvage rectangles give `+100 ore`.
- [ ] Player-facing UI emphasizes credits, ore, fleet health, fleet size, intel, exposure, alert, and cooldowns.
- [ ] NPC/theater supply-line language remains clearly separate from player inventory.


# Feature Overhaul Checklist

## Phase 1 - Non-Graphics First

## Carrier and Station Systems
- [x] Add bomber squadrons to carrier launch/recovery loops.
- [x] Add turret structures that spawn around stations/bases.

## Weapon and Combat Tuning
- [x] Buff missile speed.
- [x] Buff missile durability/HP.
- [x] Buff projectile speed for non-missile weapons where needed.
- [x] Buff Wave Motion Gun: increase damage and convert it to a continuous beam.

## Advanced Damage Model
- [x] Implement subsystem-based damage model inspired by bridge-combat sims.
- [x] Add internal ship systems for damage tracking:
- [x] Engines
- [x] Shields
- [x] Reactor core
- [x] Sensors
- [x] Weapons
- [x] Bridge
- [x] Warp engines
- [x] Magazines
- [x] Route incoming hit damage into hull + subsystem damage resolution.

## Power and Shield Management
- [x] Add ship power management (allocation between engines, shields, weapons, systems).
- [x] Add shield facing control (manual/assisted facing).
- [x] Add directional shield behavior (incoming angle affects shield effectiveness).
- [x] Temporarily disable shields after shield HP is reduced to 0 (with recharge/reboot delay).

## Weapon Usability and AI
- [x] Make wave-motion-gun-capable ships easier to aim (player usability pass).
- [x] Fix NPC wave-motion ships so they aim special weapon correctly before firing.
- [x] Add a cloak/stealth feature for stealth ships.

## Crew Gameplay
- [x] Add crew interaction systems (crew state/actions impacting ship performance).

## Phase 2 - Graphics and Visual Overhauls

## Visual Upgrades
- [ ] Laser visual update (beam style, glow, impact readability).
- [ ] Projectile visual update (tracers, color coding, travel readability).
- [ ] Fire graphics update (ship burn effects, sustained flames, heat bloom).
- [ ] Destruction graphics update (multi-stage explosions, debris, smoke).
- [ ] Shield graphics overhaul so shields look like layered/angled energy fields instead of a simple blue circle.
- [ ] Update impact hit effects so hull vs shield impacts look different based on projectile type.

## Hull Breach and Deformation
- [ ] Implement real-time ship mesh/hull deformation from impacts.
- [ ] Render visible holes at exact impact locations on hull.
- [ ] If a breached location overlaps a system zone, mark that system damaged/destroyed.

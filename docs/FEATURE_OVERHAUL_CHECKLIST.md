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
- [ ] Make it so that the shield is split up into 4 faces, foreward, left, right, and rear, and are visually separated.
## Weapon Usability and AI
- [x] Make wave-motion-gun-capable ships easier to aim (player usability pass).
- [x] Fix NPC wave-motion ships so they aim special weapon correctly before firing.
- [x] Add a cloak/stealth feature for stealth ships.

## Crew Gameplay
- [x] Add crew interaction systems (crew state/actions impacting ship performance).

## Phase 2 - Graphics and Visual Overhauls

## Visual Upgrades using chatgpt to generate sprites if needed.
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
## other stuff i found
- [x] when all enemy units are eliminated in resource rush, immediately declare victory for the correct side and faction
- [x] add aiming redicle for wave motion gun ship, make it a large red aiming laser that warns all that the weapon is about to be fired. make it so that the wave motion gun of the player ship and the whole player ship try to point where the player mouse is at, allowing for reliable aim
- [x] make fighters and bombers smaller visually
- [x] Implement a new in-game overlay called Power Management (like Bridge Commander). It is a tab/panel the player can open with a key (e.g., O). The panel lets the player redistribute a fixed 100% power budget across Engines, Shields, Weapons, Systems using sliders/stepper controls and preset buttons (BALANCED/ATTACK/DEFENSE/PURSUIT). When the player adjusts allocations, the values must always be normalized to sum to 1.0 (100%). Hook the UI into the existing Ship power system using player.setPowerAllocation(engines, shields, weapons, systems) and the powerXFrac() getters. Display a live readout of the current percentages and a small effects preview showing how the current power split changes ship performance (speed/mobility via engines, weapon damage + fire rate via weapons, sensor range via systems, shield effectiveness via shields). The overlay should behave like existing shop/map/base overlays: opening it closes other overlays and blocks combat inputs until closed.
- [x] check that all upgrades are working properly, both base upgrades such as the mining upgrades and all ship upgrades
- [x] search for any redundant code or any code that looks like its meant to be implemented but isnt, ie isnt plugged in and fix it
- [x] change crew interactions
- [x] Add a Crew Stations system like Star Trek Bridge Commander. Implement station-based UI tabs/panels for Captain/Command, Helm/Navigation, Tactical, Engineering, and Science/Sensors (optional Comms). Each station exposes focused controls and readouts: Helm controls speed/heading/autopilot (intercept/orbit/maintain range/evasive), Tactical controls targeting + weapon groups + fire modes + auto-fire rules, Engineering controls power allocation + subsystem repair priorities + shield reinforcement, Science controls sensor contacts + target lock + scan + optional EW/jamming, Captain provides high-level battle orders and presets. Stations switch by hotkeys or tabs. Add automation toggles per station so AI can manage that station until the player gives manual input. All stations modify shared ship state (movement commands, target selection, power allocations, weapon firing states, repair priorities, sensor locks). Manual player input overrides automation immediately.
- [x] Overhaul the damage visuals so that the whole ship can look damaged instead just the middle of the ship, most likely because of the old models we were using
- [x] make it so that ship crews and ai work even when the menu is pulled up, make it so that asteroids have a smaller hitbox, make asteroid detection a feature for npc ships so that they dont get stuck or try to ram into them unless they are a mining ship
- [x] put reccomended other changes here
- [x] add a tactical-fire source split (manual fire vs station AI fire) so overlays can block manual combat input without suppressing station automation fire rules
- [x] add a station automation status strip to the main HUD (captain/helm/tactical/engineering/science: AI or MAN) for quick situational awareness
- [x] add an asteroid danger heatmap/avoidance debug overlay toggle to tune collision-avoidance behavior and reduce jitter in dense fields
- [x] add per-role avoidance weights (light craft, capitals, carriers) so large hulls plan wider lanes around asteroid clusters
- [x] add regression harness scenarios for menu-open simulation continuity (AI + crew + economy + station automation) to prevent future pause/regression bugs
- [x] add asteroid collider tuning config (collision scale, minimum radius) in a central balance config instead of hard-coded constants
- [x] remove crew fatigue as a feature
- [x] add fleet mechanics where or more ships will attempt to declare themselves the "flagship" and all other ships around it in an area will follow it and listen to orders and formation settings set by the flagship. flagship authority is taken by the largest ship. make several commands so that the ai tries to preserve the life of its own ships and independant orders can be given to each ship.
- [x] make new captain commands such as mine, escort, defend, repair, rtb, etc...
- [x] add a beacon to the flagship or whoever declares themselves the command ship
- [x] update combat ai so they now push forewards whenever the enemy hp is low and their is high but fall back in the formation when their shield hp or hp begins to lower to a concerning level
- [x] add npc asteroid avoidance, even if they are engaging against an enemy unit
- [x] make it so that fleet ships all share info with one another and act as a combat organism and make the npc ai smarter, reccomend ways to do that below
  - [x] add per-role threat-priority tables so fleets focus high-value targets first (carriers, damaged capitals, logistics)
  - [x] add intent prediction (incoming missile salvos + likely enemy retreat vectors) to pre-position interceptors and PD ships
  - [x] add shared danger-map memory (recent projectile lanes + asteroid choke points) to reduce repeated pathing mistakes
  - [x] add confidence scoring per contact and let squads split by confidence instead of all hard-committing
  - [x] make it so player captain fleet orders/formation only propagate when the player is the formation leader (command ship)
  - [x] add per-role ammo/weapon-discipline policies so missile boats save salvos for high-confidence windows
  - [x] add squad-level objective splitting (intercept, flank, hold, reserve) instead of one-shape fleet movement
  - [x] add adaptive retreat corridors that avoid enemy firing arcs instead of simple base-line retreat vectors
  - [x] add kill-confirm re-target delay to reduce overkill focus-fire waste on already-dying targets
  - [x] add electronic-warfare awareness (jamming strength and sensor confidence decay) to target selection
  - [x] add flagship survivability doctrine that automatically rotates damaged capitals behind healthier hulls
- [x] right now the player turns by hitting a and d, limiting how fast the ship can turn, place these still turning restrictions on npc ships to prevent them from spazzing out and looking like they are z fighting with themselves
- [x] make it so that flagship npcs will adjust formation orders, shape, size, and consistensy based on the enviornment and situation around them. 
- [x] carrier aircraft not able to attack
- [x] move bases to edge of world, depending on how large the map is
- [x] make it so that if a ship isnt able to hit its current target, it will switch off to a new target and prevent a situation where enemy ships can just walk up next to friendlies without any combat.
- [x] display overhaul, hint overhaul, UI overhaul
- [x] switching player ship type resets all ships in world leading to ships spawning out of nowhere
- [x] fighters following local flagship orders and not carrier orders
- [x] bases should build ships and deploy them next to themselves
- [x] improve the combat decision making as much as possible, give ideas on how to do this below
  - [x] added local support-balance logic so non-capitals stop solo-diving into numerically stronger enemy clusters
  - [x] added target selection penalties for over-committed focus fire to reduce fleet clumping on one target
  - [x] add per-fleet role quotas (screen/intercept/strike/reserve) with live rebalance as ships are destroyed
  - [x] add engagement memory per ship (recent failed approaches / blocked fire arcs) so ships stop repeating bad lanes
  - [x] add threat forecasting from projectile trajectories to preemptively dodge likely impact corridors
  - [x] add battle-line coherence scoring that rewards maintaining crossfire and penalizes isolated outrunners
  - [x] add command-latency/fog modeling for enemy fleets to create exploitable coordination delays

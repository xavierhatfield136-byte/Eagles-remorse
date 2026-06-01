# Tactical Combat Depth

Section 3 combat depth is split between the established ship simulation and
`TacticalCombatDepthSystem`.

## Command Overlay

Open the tactical command overlay with `Ctrl+F3`. Use `Ctrl+G` to assign the
friendly ship nearest the cursor to the active group, `Ctrl+K` to cycle groups,
`Q` to cycle orders, and `Shift+RMB` to issue the selected order. Order
acknowledgments are delayed by distance and command-link damage, and may be
garbled under jamming. `Ctrl+P` toggles tactical pause.

The overlay displays the active doctrine, point-defense priority, ammunition,
weapon heat, scars, the lead weapon role, a formation preview, and recent
timeline markers.

## Handling And Survival

Hull roles retain their distinct movement profiles, reverse-thrust penalties,
damaged-engine handling, emergency-burn risk, asteroid avoidance, and
formation matching. The tactical layer adds ram damage, ram-resistant capital
identities, tractor towing for disabled or surrendered hulls, docking approach
assistance, and `Ctrl+O` orientation hold.

Existing room damage, repair priorities, casualty penalties, fire spread,
reactor escalation, subsystem effects, room-linked damage decals, surrender,
and self-destruct behavior now feed a tactical hazard layer. Use `Ctrl+B` to
seal bulkheads and evacuate damaged compartments. Severe cascade failures and
ram impacts leave hull scars for the battle record. Use `Ctrl+S` to scuttle
the disabled friendly ship nearest the cursor.

## Weapons And Support

Weapon role tooltips identify spinal alignment weapons, broadside shield
pressure batteries, disruption batteries, and guided missile racks. Batteries
consume family ammunition and build heat; use `Ctrl+X` for temporary overdrive.
Use `Ctrl+D` to cycle point-defense priorities.

Use `Ctrl+T` to cycle support modes and `Ctrl+R` to activate the current mode.
Modes include tractor tow, repair drones, shield transfer, mine laying,
mine clearing, and ECM. ECM also models decoy, chaff, and flare effects by
degrading hostile guidance and disrupting tractor locks. Destroyed rich ore
pockets detonate as environmental hazards.

Use `Ctrl+J` to cycle balanced, aggressive, cautious, point-defense, support,
and avoid-collateral doctrines. Avoid-collateral doctrine blocks mine
deployment near friendlies or surrendered ships.

# Combat And Campaign Change Notes

This document captures the larger design changes proposed for combat controls, missiles, ECM, game modes, sector structure, and campaign flow.

## Physical HUD And Control Philosophy

- Lean further into physical bridge controls and terminals instead of abstract menu toggles.
- Give important combat behaviors their own dedicated switch, selector, lever, or panel.
- Make system interaction feel tactile and high-commitment in the style of a command bridge.

## Weapon Behavior Changes

### Beam Weapons

- Support two primary beam settings:
- `Rapid Fire`: higher cadence, lower concentration, more constant pressure.
- `Concentrated`: tighter, more deliberate shots with stronger punch.
- Prefer a real control-panel selector over a generic button.

### Missiles

- Support three missile behavior profiles:
- `Heavy`: slow, high payload, torpedo-like pressure.
- `Fast`: quicker missile with lighter payload for responsive general use.
- `AAA`: very fast anti-missile / anti-craft profile.
- Move toward a clear selector-style HUD control for these roles.

### Missile Retargeting

- Improve missile guidance so missiles do not get confused when a locked target dies.
- Desired behavior: reacquire the next closest valid target automatically.
- This should remove a current frustration point rather than just add complexity.

## ECM Direction

> Retired: tactical ship ECM was removed after playtesting because the
> escape-button behavior did not fit the current combat model. The notes below
> remain as historical design context only.

- Make ECM a real player-facing feature instead of only passive science behavior.
- Long-term target interaction:
- Pull lever down to activate ECM.
- Enemy sensors lose track of the player for about 5 seconds.
- ECM then enters a recharge state of about 20 seconds.
- Strong feedback needed:
- `Primed`
- `Active`
- `Recharging`
- Optional later risk:
- heat load
- instability
- failure chance if spammed

## Zone-Based Map Structure

- Replace the feeling of one huge unknown black field with distinct faction zones and combat spaces.
- Let the player zoom out or use a map key to move attention between sectors or zones.
- General inspiration: multiple connected combat sectors with readable strategic geography.

## Resource Rush

- Create three major zones:
- Blue team home zone with resources and building access.
- Central war zone where force concentration and sustained battles happen.
- Enemy home zone where the opposing starbase can be attacked.
- Control should come from force presence and pressure, not simple capture points.

## Four-Team Deathmatch

- Put team starbases in four corner sectors.
- Create one large central combat zone.
- Add four smaller connector zones:
- Blue to Red
- Red to Yellow
- Yellow to Green
- Green to Blue
- This gives both a central brawl and smaller regional fights.

## Last Stand

- This mode may not need the same map-sector treatment.
- It remains closer to a direct survival simulation of Earth being flooded by hostile AI ships.

## Campaign Structure Changes

- Replace the current pause-and-modify fleet flow with a more continuous world state.
- Ships should only be modifiable when a zone is cleared of hostiles.
- The game should avoid a true global pause for fleet modification.
- Expand campaign navigation into around 24 playable areas, with room to add more later.
- This would open up more player freedom in how the campaign is completed.

## Off-Sector Simulation

- Avoid fully rendering sectors the player is not currently inside.
- Add a lightweight abstract combat simulation for off-screen sectors.
- Candidate simplifications:
- damage-per-second buckets
- missile pressure
- AA / ECM modifiers
- range bands
- durability and retreat logic
- A simple turn-based or exchange-based model is acceptable if it stays readable and cheap.

## Balancing Warning

- These ideas increase both depth and scope.
- The most important implementation rule is to avoid trying to ship all of them at once.
- Recommended order of execution:

1. Clickable HUD controls and interaction grammar
2. Beam, missile, and ECM combat-state switching
3. Multi-zone map flow for selected modes
4. Campaign sector simulation and rebalancing

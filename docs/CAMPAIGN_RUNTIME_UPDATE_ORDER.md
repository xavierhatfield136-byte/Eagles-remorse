# Campaign Runtime Update Order

`CampaignRuntime` currently delegates to `CampaignSystem.legacyUpdate` without changing order.

Treat this order as a behavior contract during decomposition. Do not reorder systems while extracting classes.

## Current Top-Level Order

- Resolve `CampaignState`; exit if disabled or game over.
- Sync and apply flagship operations to the player ship.
- If not in command-layer mode:
  - update strike cinematic
  - update tactical strike bombers
  - return
- Apply campaign time scale; exit if paused.
- If command-school training:
  - update strike cinematic
  - update tactical strike bombers
  - update command-school overmap when in strategic overmap mode
  - return
- Initialize the galaxy campaign map when needed.
- Fail the run if the player flagship is gone.
- Refresh alliances, force ownership, playable-alpha systems, mission intro timer, ore ledger, strike cinematic, and tactical strike bombers.
- If awaiting fleet-hub choice:
  - tick auto-open timer
  - sync persistent fleet casualties
  - return
- If awaiting episode launch:
  - sync persistent fleet casualties
  - return
- If transition timer is active:
  - tick transition
  - start next sector or finalize the campaign
  - return
- If intro sequence is active:
  - update sector-one intro
  - return
- If strategic overmap mode is active:
  - update strategic overmap campaign
  - return
- Resolve enemy-base tactical win condition when active.
- Advance live tactical campaign state:
  - sector elapsed time
  - tactical environment
  - tactical ore spawn
  - tactical losses
  - persistent fleet casualties
  - blue rejoins
  - hostile kills
  - strategic strike objects
  - strategic task forces
  - mission pocket losses/scripts/pressure when active
  - escort formation
  - reserve reinforcements
  - pocket discoveries
  - recoverable wreck sites
  - side objective when active
  - mission banter
  - objective update

# Campaign Fleet Additions Execution Checklist

Use this as the active implementation tracker.

## Global Definition Of Done (applies to every fleet)

- [x] Fleet has a declared `role`, `purpose`, `mission`, and `homeBase`.
- [x] Fleet is assigned to a `CampaignForce` and never idles with `HOLDING` behavior.
- [x] Fleet has a non-empty route or patrol loop.
- [x] Fleet has encounter behavior (`engage`, `retreat`, or `evade`) based on force state.
- [x] Fleet survives save/load with identity, route, mission, and state intact.
- [x] Fleet appears on overmap with correct marker/intel behavior.

## Sprint 1 (Must Ship First)

### Core Hostile Pressure
- [x] `Red Scout Pair`
  - Done when: scouts detect, report, and retreat from player.
- [x] `Red Patrol Group`
  - Done when: patrol loops between at least 3 points and investigates contacts.
- [x] `Red Interceptor Squadron`
  - Done when: dispatches to last-known player contact and attempts force encounter.

### Core Allied/Neutral Traffic
- [x] `Yellow Trade Convoy`
  - Done when: convoy travels between hubs and requests escort under threat.
- [x] `Green Local Defense Patrol`
  - Done when: remains in defended region and responds to nearby hostile fleets.
- [x] `Mining Fleet`
  - Done when: moves resource zone -> hub loop and contributes logistics.

### Core Opportunistic Threat
- [x] `Pirate Wolfpack`
  - Done when: targets weak convoys, disengages from superior force.

## Sprint 2 (Systems Depth)

### Hostile Mid-Game Escalation
- [x] `Red Hunter-Killer Group`
- [x] `Red Missile Artillery Fleet`
- [x] `Red Carrier Strike Group`
- [x] `Red Siege Fleet`
- [x] `Sensor Net Fleet`
- [x] `Electronic Warfare Fleet`

### Allied Response Layer
- [x] `Green Counterattack Group`
- [x] `Green Escort Squadron`
- [x] `Repair Tender Fleet`
- [x] `Green Relief Convoy`

### Neutral/Contract Layer
- [x] `Yellow Mercenary Fleet`
- [x] `Yellow Smuggler Convoy`
- [x] `Distress Call Fleet`
- [x] `Defector Fleet`

## Sprint 3 (Late-Game War Theater)

### Heavy Red Operations
- [x] `Red Line Fleet`
- [x] `Red Dreadnought Task Force`
- [x] `Red Blockade Fleet`
- [x] `Red Pursuit Armada`
- [x] `Red Planetary Suppression Fleet`
- [x] `Red Flagship Fleet`

### Allied Strategic Anchors
- [x] `Green Home Guard Fleet`
- [x] `Late-Game Carrier Support Group (Allied)`
- [x] `Late-Game Titan Escort Screen`

### Event Fleets
- [x] `Damaged Supercapital`
- [x] `Prisoner Transport`
- [x] `VIP Diplomatic Fleet`

## Optional Expansion Backlog

### Recon/Stealth Concepts
- [x] `Deep Recon Probe Group`
- [x] `Red Picket Screen`
- [x] `Stealth Ambush Pack`
- [x] `Ghost Contact`
- [x] `Experimental Cloak Fleet`
- [x] `Decoy Dreadnought Contact`

### Logistics/Economy Variants
- [x] `Red Supply Convoy`
- [x] `Fuel Tanker Group`
- [x] `Ammo Tender Group`
- [x] `Salvage Fleet`
- [x] `Yellow Merchant Caravan`
- [x] `Yellow Repair Caravan`

### Rogue/Pirate Variants
- [x] `Pirate Salvage Gang`
- [x] `Pirate Decoy Fleet`
- [x] `Rogue Military Remnant`
- [x] `Mutineer Fleet`
- [x] `Black Market Escort Fleet`

### Regional/Environmental
- [x] `Nebula Patrol`
- [x] `Asteroid Belt Mining Guard`
- [x] `Deep Space Fuel Convoy`
- [x] `Relay Maintenance Fleet`
- [x] `Mine-Layer Fleet`
- [x] `Mine-Sweeper Fleet`

### Large Operations
- [x] `Red Search Operation`
- [x] `Red Base Assault Operation`
- [x] `Green Evacuation Operation`
- [x] `Yellow Trade Summit Operation`
- [x] `Red Northern Wall`

### Rare Concepts
- [x] `Silent Fleet`
- [x] `Automated Drone Swarm`
- [x] `Rogue AI Fleet`
- [x] `False Convoy`
- [x] `Last Stand Fleet`

## Progress Log

- [x] Sprint 1 started
- [x] Sprint 1 complete
- [x] Sprint 2 started
- [x] Sprint 2 complete
- [x] Sprint 3 started
- [x] Sprint 3 complete

## Audit Note

- [x] Late-campaign progression seeds every checked fleet concept by sector `24`.
- [x] Core interceptor identity is consistently named `Red Interceptor Squadron`.
- [x] Coalition-aware threat selection prevents Green and Yellow traffic from treating each other as hostile.
- [x] Regression coverage verifies operational metadata, route presence, non-holding intent, and checkpoint survival.

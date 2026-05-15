# Highfleet-Style Strategic Layer Direction

> Outdated: this document has been superseded by `STRATEGIC_CAMPAIGN_MAP_SPEC.md` as the active campaign design reference.

Date: 2026-05-08  
Status: Outdated reference

## Purpose

This document turns the current freeform design notes into a single direction for combat feel, presentation cleanup, and campaign progression.

It is meant to complement, not replace:

- `OUTDATED_CAMPAIGN_AND_UI_OVERHAUL_PLAN.md`
- `combat-and-campaign-change-notes.md`

The main idea is to stop treating the game as only a fleet sandbox and instead push it toward a layered structure:

- cleaner and more readable ship combat
- less visual noise and fewer fake spectacle effects
- a strategic overworld that creates pursuit, logistics, scouting, and risk
- optional manual command of battles when formations collide

## Core Problems To Fix First

The current build does not feel right because readability and combat pacing are fighting the fantasy.

### Immediate feel problems

- Small boxes and overlay clutter sit on top of ships and make battles hard to read.
- Projectile visuals are too large for the scale of the combat.
- Projectiles move too fast, which reduces anticipation and makes the battlefield feel less physical.
- Explosion emphasis is landing on weapon hits instead of on the destruction or impact of large ships and structures.
- ECM visuals are noisy and should be removed.
- The warp effect is too prominent and should be replaced with a small, barely noticeable wormhole effect.
- Sound effects need a broad overhaul so weapons, impacts, engines, and major ship events feel heavier and cleaner.

### Fantasy problems

- The game should not drift into a pure dreadnought fantasy where giant ships only function as damage sponges.
- Large ships should matter because of command value, projection, logistics, survivability, and subsystem pressure.
- Stealth craft should feel like hunters, infiltrators, and escape artists, not just smaller brawlers.

## North Star

The target experience is a game where the player:

- moves fleets or divisions through a large strategic space
- scouts, pursues, hides, intercepts, and chooses when to commit
- launches long-range strikes before fleets ever touch
- enters a dedicated battle only when opposing groups meet
- can auto-resolve routine engagements or directly command the flagship in major fights

The closest reference point is a literal Highfleet-style split between:

- abstract long-range movement and decision making
- focused tactical combat instances with room for ships to maneuver

## Design Pillars

## 1. Readability over spectacle

- Remove UI and combat effects that hide ship motion or obscure target relationships.
- Make every combat effect justify itself through gameplay readability.
- Reserve the biggest visual and audio punctuation for events that matter:
- capital impacts
- subsystem failures
- ship breakups
- reactor events
- large detonations

## 2. Strategic movement creates the context for battles

- Ships should not constantly exist in one continuous playable battlefield.
- The player should move units and unit groups through a simulated map representing large distances.
- Combat starts when hostile groups converge in the same strategic location.
- The battle instance should only include the ships, escorts, and support assets actually present there.

## 3. Range and logistics matter

- Long-range missiles, warp missiles, torpedoes, aircraft sorties, and scouting should all matter before contact.
- The player should be able to shape an engagement before a battle begins.
- Strategic weapons should create fear, commitment, and counterplay rather than spam.

## 4. Command choice matters

- Tight situations should offer a choice:
- auto-resolve
- enter battle and command the largest ship or flagship in the formation
- Manual command should be for important or risky fights, not every single interception.

## 5. Specialized fleets should behave differently

- Stealth groups should prefer ambush, selective kills, and disengagement.
- Strike groups should commit to decisive fleet actions.
- Carriers should project force through sorties and long-range operations.
- Heavy fleets should control territory and absorb pressure, but should not define the entire fantasy alone.

## Combat Presentation Direction

## Visual cleanup

- Remove the small box clutter layered over ships, or reduce it to a minimal on-demand/readability-only form.
- Shrink projectile scale across the board.
- Slow projectile travel enough to improve anticipation, dodge readability, and perceived weight.
- Make major explosions belong to large hull damage, ship death, or catastrophic failure instead of ordinary weapon contact.
- Remove ECM visuals entirely unless a future replacement can communicate gameplay without screen pollution.
- Replace the current warp effect with a subtle micro-wormhole effect that reads quickly and then gets out of the way.

## Audio direction

- Rebuild weapon and impact sounds around role clarity:
- light weapons should snap, chatter, or crack
- anti-ship weapons should feel heavy and committed
- point defense should sound rapid and functional
- major ship damage should have distinct structural and reactor cues
- Warp, missile launch, aircraft launch, and superweapon events should all have unique signatures.
- Audio should carry some of the spectacle currently being forced onto visuals.

## Combat pacing direction

- Combat should feel more deliberate and less like oversized fast-moving particles colliding.
- Threat should come from positioning, timing, salvo commitment, interception, and subsystem damage.
- The player should have time to read incoming danger and respond.

## Strategic Layer Structure

## Recommended baseline

Use an abstract overworld map that represents travel, detection, pursuit, and regional control rather than a literal combat playspace.

Each moving piece on that map is a:

- single ship
- unit
- division
- task force
- convoy
- patrol

When opposing groups meet, the game generates a combat battle from the participating forces.

## Why this solves the current problems

- It gives ships room to matter tactically without requiring the whole campaign to run as one continuous battlefield.
- It creates strategic value beyond telling the fleet to assault and waiting.
- It makes scouting, stealth, interception, missile warfare, and aircraft operations naturally useful.
- It creates a place for events, salvage, resource zones, pursuit pressure, and story progression.

## Map content

The strategic map can support:

- resource zones
- faction territory
- events
- anomalies
- hidden finds
- repair/refit opportunities
- pursuit nodes on the route back to Earth
- mission patches and authored set pieces

## One large map vs multiple sectors

This is still open.

### Option A: One very large map

Pros:

- stronger sense of a single journey
- simpler mental model for pursuit and travel
- easier to sell the fantasy of returning to Earth through one hostile expanse

Risks:

- harder to author pacing cleanly
- can become visually or systemically diffuse
- may make regional identity weaker without strong cartography and event density

### Option B: Multiple sectors

Pros:

- easier pacing control
- clearer escalation bands
- simpler content authoring and encounter curation
- stronger regional identity for factions, weather, resources, and threats

Risks:

- can feel gamey if transitions are too obvious
- may weaken the sense of continuous travel if sectors behave like disconnected levels

### Recommendation

Treat the world as one continuous journey in fiction, but implement it as linked strategic sectors underneath if that makes pacing and content easier to ship.

In other words:

- preserve the fantasy of one big map
- allow the production model to use multiple sectors if needed

## Long-Range Warfare Layer

The strategic map should support pre-battle force projection tools.

### Desired tools

- long-range torpedoes
- warp missiles
- aircraft sorties
- reconnaissance flights
- superweapon or "atomic option"

### Design rules

- These tools should be high-commitment and high-information, not casual spam buttons.
- Launching them should expose intent, consume limited resources, or create strategic risk.
- Defending against them should create meaningful gameplay through interception, stealth, jamming, decoys, or movement.

### Superweapon rule

The superweapon should exist as a dramatic player option, but it must feel costly, political, scarce, or dangerous enough that it does not trivialize fleet play.

## Battle Entry And Resolution Flow

When a strategic encounter occurs:

1. Determine which unit groups are present.
2. Spawn a battle using the ships from those groups.
3. Offer the player a choice to:
- auto-resolve
- command the encounter directly
4. If commanding directly, place the player in control of the largest ship or flagship in the formation.

This keeps the strategic game moving while reserving hands-on play for the fights that deserve it.

## Stealth Fleet Direction

Stealth ships should have a distinct battle and campaign identity.

### Strategic behavior

- avoid fair fights
- scout ahead
- pick soft or valuable targets
- create uncertainty for the enemy

### Tactical behavior

- enter combat trying to assassinate key targets
- maintain stealth as long as possible
- disengage once pressure becomes unsustainable
- cloak and escape when the attack fails or reinforcements arrive

Stealth fleets should feel like scalpels, not tiny line ships.

## Anti-Dreadnought Direction

Large ships should still be impressive, but the game should resist becoming only about building the biggest possible hull and face-tanking everything.

### Principles

- capital ships should be vulnerable to scouting failure, isolation, missile pressure, aircraft strikes, and subsystem collapse
- support ships, scouts, stealth units, and carriers must remain strategically relevant
- fleet composition should matter more than raw tonnage alone
- battles should reward combined arms and planning, not just mass and frontal durability

## Recommended Execution Order

Do not attempt to ship the entire strategic rewrite at once.

### Phase 1: Combat feel cleanup

- remove or heavily reduce ship overlay box clutter
- reduce projectile scale
- reduce projectile speed
- retune explosion emphasis toward big impacts and ship deaths
- remove ECM visuals
- replace warp effect with subtle wormhole presentation
- audit and overhaul core SFX set

### Phase 2: Tactical identity cleanup

- reinforce stealth behavior
- reduce dreadnought dominance
- improve role clarity for ship classes and weapons
- ensure battles feel readable and deliberate after the presentation changes

### Phase 3: Strategic prototype

- build a minimal overworld map with moving unit groups
- support encounter generation when groups meet
- implement auto-resolve and manual command entry
- test one large map presentation against a sector-backed implementation

### Phase 4: Strategic depth

- add resource zones, events, salvage, and discoveries
- add sorties, long-range strike options, and reconnaissance
- add superweapon support with strong restrictions
- expand pursuit-to-Earth structure and authored campaign content

## Immediate Decisions To Lock

These should be answered before major implementation starts:

1. Is the first production version of the strategic layer visually presented as one map, sectors, or a hybrid?
2. Which long-range strike options are mandatory for the first playable prototype:
- missiles only
- missiles plus aircraft
- missiles plus aircraft plus superweapon
3. What exact information should remain visible over ships in combat after the readability cleanup?
4. Should stealth ships be manually piloted in battle, AI-driven with player directives, or both?

## Summary

The direction is to make combat cleaner, heavier, and more readable, then place that combat inside a strategic structure that creates pursuit, scouting, interception, and operational decision making.

The important discipline is sequencing:

- first fix the feel
- then fix the tactical identity
- then prove the strategic layer in a minimal form
- then expand content and special systems

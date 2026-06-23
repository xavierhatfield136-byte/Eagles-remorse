# Fleet Contact Expansion / Task Force Inspection Checklist

## Core Goal

Implement a Silent Hunter-style campaign-map system where fleets appear as single strategic contacts at long range, but can unfold into visible ship formations when selected, zoomed in, or properly detected.

The fleet remains one moving campaign object. Individual ships inside the fleet are visual and inspection children of that fleet, not separate pathfinding units.

---

# 1. Core Rules

* [x] Fleets remain the real strategic campaign-map objects.
* [x] Individual ships inside fleets are stored as fleet composition data.
* [x] Ship cutouts shown on the campaign map are visual/inspection children only.
* [x] Ship cutouts do not pathfind independently.
* [x] Ship cutouts do not become separate campaign entities by default.
* [x] Ships only become separate campaign entities if a deliberate future system supports fleet splitting, retreat detachments, or independent task groups.
* [x] Fleet composition data is the source of truth.
* [x] Expanded formation view is only a visual representation of that data.

---

# 2. Collapsed Fleet Markers

## Basic Marker Behavior

* [x] Show one marker per fleet on the campaign map.
* [x] Keep fleets collapsed by default at normal zoom.
* [x] Do not show every ship in a fleet as its own campaign marker.
* [x] Prevent campaign-map clutter by treating fleets as single strategic contacts.

## Fleet Marker Labels

Support labels such as:

* [x] Red Interdiction Group
* [x] Red Raider Pack
* [x] Red Siege Group
* [x] Red Scout Line
* [x] Green Patrol
* [x] Green Counter-Task Force
* [x] Green Convoy Escort
* [x] Yellow Mining Convoy
* [x] Yellow Trade Convoy
* [x] Yellow Militia Group
* [x] Unknown Contact
* [x] Unknown Large Contact

## Marker Information

* [x] Show faction color only when faction is identified.
* [x] Show gray/neutral unknown marker when unidentified.
* [x] Show estimated size only when intel allows.
* [x] Show estimated threat only when intel allows.
* [x] Show movement vector only for live detected contacts.
* [x] Do not show movement vectors for stale/ghost contacts.

---

# 3. Fleet Composition Data

Each campaign fleet should contain or resolve to an internal list of ships.

## Fleet Data

* [x] Add or confirm each fleet has a `ships` list or equivalent composition source.
* [x] Track fleet name.
* [x] Track fleet faction.
* [x] Track fleet type.
* [x] Track fleet origin.
* [x] Track fleet destination.
* [x] Track current fleet mission.
* [x] Track current fleet position.
* [x] Track current fleet velocity.
* [x] Track current intel/detection level.
* [x] Track whether the fleet is live, stale, destroyed, retreating, or docked.

## Ship Data

Each ship inside a fleet should track:

* [x] Ship name.
* [x] Ship class/type.
* [x] Faction.
* [x] Hull condition.
* [x] Alive/destroyed/disabled state.
* [x] Formation role.
* [x] Display icon or simplified cutout sprite.
* [x] Previous damage state.
* [x] Ammo/fuel/supply condition if available.
* [x] Whether the ship is known, partially known, or unknown to the player.

## Persistence

* [x] Save fleet composition.
* [x] Load fleet composition.
* [x] Preserve ship damage after combat.
* [x] Remove destroyed ships permanently.
* [x] Preserve surviving ships after retreat.
* [x] Preserve named ships and captains if that system exists.
* [x] Preserve previous contact notes if that system exists.

---

# 4. Intel Levels

Add sensor-based information levels for every fleet contact.

## Intel Level 0 - Unknown Contact

At long range or low sensor quality:

* [x] Show vague contact marker only.
* [x] Hide faction.
* [x] Hide exact ship count.
* [x] Hide ship silhouettes.
* [x] Hide exact threat.
* [x] Show rough signal strength if available.
* [x] Show rough heading only if available.

Example:

```text
UNKNOWN CONTACT
Signal: Weak
Heading: North-East
Size: Unknown
```

## Intel Level 1 - Estimated Size

At medium-low intel:

* [x] Show approximate fleet size.
* [x] Show rough movement direction.
* [x] Still hide exact faction if not identified.
* [x] Still hide ship classes.
* [x] Still hide ship names.
* [x] Still hide exact damage state.

## Intel Level 2 - Faction / Threat Known

At medium intel:

* [x] Reveal faction.
* [x] Reveal broad fleet type.
* [x] Reveal approximate ship count.
* [x] Reveal estimated threat.
* [x] Still hide exact ship names.
* [x] Still hide exact damage unless previously known.

## Intel Level 3 - Formation Silhouettes

At high intel:

* [x] Allow expanded formation view.
* [x] Show simplified ship cutouts.
* [x] Show known ship categories.
* [x] Show unknown ships as dark silhouettes or question marks.
* [x] Show approximate fleet layout.
* [x] Still hide exact names unless fully identified.

## Intel Level 4 - Full Identification

At full intel:

* [x] Show fleet name.
* [x] Show ship names.
* [x] Show exact ship classes.
* [x] Show damage state.
* [x] Show previous encounter notes.
* [x] Show missing or destroyed ships if previously tracked.
* [x] Show mission, origin, and destination if known.

---

# 5. Selected-Fleet Expanded View

## Expansion Rules

* [x] When the player selects a fleet, expand it into a small formation view.
* [x] Use simplified cutout versions of existing ship sprites.
* [x] Show ship cutouts around the central fleet marker.
* [x] Only selected fleets expand by default.
* [x] Very nearby fleets may expand at high zoom if intel is high enough.
* [x] Collapse expanded fleets when deselected.
* [x] Collapse expanded fleets at low zoom.
* [x] Never expand stale contacts as live formations.
* [x] Never allow expanded ship cutouts to pathfind separately from the parent fleet.

## Visual Behavior

* [x] Known ships use simplified faction-colored cutouts.
* [x] Unknown ships use dark silhouettes or question marks.
* [x] Damaged ships appear dimmed, cracked, marked, or otherwise visually damaged.
* [x] Disabled ships show a warning marker.
* [x] Retreating ships can show a fallback arrow.
* [x] Destroyed ships are removed from the formation or listed as lost in the panel.

---

# 6. Formation Roles

## Required Roles

* [x] `FLAGSHIP`
* [x] `VANGUARD`
* [x] `SCREEN_LEFT`
* [x] `SCREEN_RIGHT`
* [x] `REARGUARD`
* [x] `CARRIER_CORE`
* [x] `SUPPORT_REAR`
* [x] `TRANSPORT_GROUP`
* [x] `MINER_GROUP`
* [x] `SCOUT_WING`

## Placement Logic

* [x] Vanguard ships appear ahead of the fleet center.
* [x] Scouts appear forward or wide of the formation.
* [x] Flagship appears near the center.
* [x] Cruisers and capitals appear in the core.
* [x] Frigates and pickets appear on the outer screen.
* [x] Carriers appear behind the main line.
* [x] Supply ships appear toward the rear.
* [x] Miners and transports appear protected toward the rear or center.
* [x] Damaged ships may appear slightly behind the formation center.
* [x] Formation should remain readable and not overlap too heavily.

---

# 7. Fleet Inspection Panel

When the player clicks a fleet, open a strategic inspection panel.

## Panel Should Show

* [x] Fleet name.
* [x] Faction.
* [x] Contact status.
* [x] Intel confidence.
* [x] Fleet type.
* [x] Origin.
* [x] Destination.
* [x] Current mission.
* [x] Estimated threat.
* [x] Known ships.
* [x] Unknown ships.
* [x] Damaged ships.
* [x] Missing or destroyed ships if previously tracked.
* [x] Previous contact notes.
* [x] Recommended action.
* [x] Consequence if ignored.

---

# 8. Stale Contact / Ghost Contact Rules

## When A Fleet Leaves Sensor Range

* [x] Convert the live contact into a stale contact marker.
* [x] Show last known position.
* [x] Show last known heading.
* [x] Show contact age.
* [x] Show confidence level.
* [x] Fade stale marker over time.
* [x] Do not update stale marker as if it is live.
* [x] Do not show live movement vectors.
* [x] Do not trigger combat from stale contacts.
* [x] Do not allow stale contacts to expand into full formation view.

## Reacquisition

* [x] If the real fleet is detected again, reconnect the stale contact to the live fleet.
* [x] Update its current position, heading, and intel level.
* [x] Remove duplicate stale markers after reacquisition.
* [x] If not reacquired after enough time, remove the stale marker.

---

# 9. Visual Ship Cutouts

## Cutout Requirements

* [x] Generate simplified strategic cutouts from existing ship sprites.
* [x] Keep cutouts readable at small scale.
* [x] Avoid using full-resolution battle sprites directly if they create clutter.
* [x] Add faction tinting or outline color.
* [x] Add unknown silhouette versions.
* [x] Add damaged silhouette versions.
* [x] Add disabled silhouette versions.
* [x] Add support/civilian silhouettes.
* [x] Add capital/large ship silhouettes.
* [x] Add escort/small ship silhouettes.

## Visual States

* [x] Normal ship: clean silhouette.
* [x] Unknown ship: dark silhouette or question mark.
* [x] Damaged ship: cracked, dimmed, or marked silhouette.
* [x] Disabled ship: warning marker.
* [x] Retreating ship: fallback arrow.
* [x] Destroyed ship: removed from formation or listed as lost.

---

# 10. Battle Spawn Integration

This should come after the map-side inspection system works.

## Spawn Layout

* [x] Use overworld formation roles to influence tactical battle spawn positions.
* [x] Vanguard ships spawn forward.
* [x] Scouts and pickets spawn ahead or wide.
* [x] Escorts spawn around valuable ships.
* [x] Cruisers and capitals spawn in the core.
* [x] Carriers spawn behind the main line.
* [x] Supply ships spawn toward the rear.
* [x] Miners and transports spawn protected when possible.
* [x] Damaged ships enter battle with saved hull state.
* [x] Destroyed ships do not return to the campaign fleet.
* [x] Escaping ships return to the campaign fleet.

## Ambush Direction

* [x] If the player attacks from the front, vanguard/screen ships are encountered first.
* [x] If the player ambushes from behind, support ships may spawn closer to the player.
* [x] If the player attacks from the flank, side screens are encountered first.
* [x] Spawn logic should reflect the player's approach direction when possible.

---

# 11. Things Codex Should Not Do

* [x] Do not make every ship inside a fleet a separate campaign-map actor.
* [x] Do not let ship cutouts pathfind.
* [x] Do not show every fleet expanded all the time.
* [x] Do not reveal full fleet composition at long range.
* [x] Do not let stale contacts behave like live fleets.
* [x] Do not spawn random ships that are not tied to a real fleet.
* [x] Do not make the visual formation the source of truth.
* [x] Do not create duplicate fleet markers when reacquiring stale contacts.
* [x] Do not allow ghost contacts to trigger battles.
* [x] Do not clutter the campaign map with unnecessary icons.

---

# 12. Suggested Naming

* [x] Internal system name: `Fleet Contact Expansion`
* [x] Player-facing panel name: `Task Force Inspection`
* [x] Map display mode name: `Formation View`

---

# 13. Recommended Priority Order

Implement in this order:

* [x] Collapsed fleet markers.
* [x] Fleet composition data.
* [x] Intel levels.
* [x] Stale contact cleanup.
* [x] Selected-fleet expanded view.
* [x] Fleet inspection panel.
* [x] Visual ship cutout polish.
* [x] Battle spawn integration.

---

# Final Design Rule

The campaign map should feel like a naval command layer.

At long range, the player sees contacts. At better intel, the player understands fleet type, size, and threat. When selected or zoomed in, a fleet unfolds into a readable formation of real ships.

Under the hood, the fleet remains one strategic object until a deliberate future system says otherwise.

# Campaign Force Ownership Implementation Checklist

Date: 2026-05-20
Status: Active implementation checklist
Source: Player request for a believable campaign theater where every ship has a strategic origin, purpose, and force owner.

## Purpose

This checklist turns the campaign force-ownership redesign into buildable steps.

The goal is to remove unexplained ship spawning and make every tactical ship traceable to a campaign-level force such as a fleet, patrol group, convoy, mining group, base defense force, garrison, search group, or local installation force.

The campaign should feel like a living theater of war. Battles should happen because campaign forces move, patrol, search, intercept, defend, reinforce, or attack. Secure friendly locations should stay secure unless a tracked hostile force has actually reached, infiltrated, or threatened that area.

## Completion Rules

- A ship is not campaign-valid unless it belongs to a named force object with origin, purpose, faction, and membership.
- A hostile ship is not valid unless the player could have seen, inferred, detected, or been warned about its parent force through the campaign layer.
- A secure friendly installation must not spawn ambient hostiles unless a tracked hostile force is close enough or has a scripted infiltration reason.
- A tactical encounter is not complete until its spawned ships can be traced back to overmap forces, mission forces, garrisons, convoys, or local installation forces.
- UI work is not complete until force provenance is visible in at least one player-facing place.
- Save/load work is not complete until force identities, memberships, and pending movement survive checkpoint restore.
- Main-menu fleet management should not be the primary campaign fleet interface; fleet management must be available in-world.

## Design Anchors

- Every ship belongs to a force.
- Every force has a strategic role and reason to exist.
- Enemy action comes from campaign entities, not invisible random spawns.
- Friendly traffic also belongs to forces, even if it is just local harbor traffic or a two-ship mining group.
- Hostile forces enter tactical space from plausible directions tied to their campaign position.
- The overworld explains danger before combat begins through contacts, warnings, patrol paths, sensor reports, or known force movement.
- Green/yellow friendly hubs are safe trade and setup spaces unless an actual hostile force threatens them.
- Fleet management happens in-world through campaign map/fleet UI and keybinds.

## Phase 1: Force Identity Foundation

### 1.1 Force Data Model

- [x] Add a campaign force entity with id, kind, faction, name, origin, purpose, position, and ship membership.
- [x] Add force kinds for player fleet, patrol group, task force, base defense, convoy, mining group, installation traffic, strike detachment, and local force.
- [x] Track ship-to-force membership for live campaign ships.
- [x] Add a public force summary read model for tests and UI surfaces.
- [x] Add durable force serialization into campaign checkpoints.
- [x] Add restore logic for force objects and ship-force membership.
- [x] Add migration behavior for older checkpoints with no force registry.

### 1.2 Force Assignment Rules

- [x] Assign campaign-spawned ships into named forces at spawn time.
- [x] Assign overmap search group encounter ships to their parent search group force.
- [x] Assign mission-layer strategic task force ships to their parent task force.
- [x] Add a fallback audit sweep that assigns any unowned live campaign ship to a named local force.
- [x] Replace fallback audit ownership with explicit force assignment at every campaign spawn path.
- [x] Add tests that fail if any campaign-authored spawn path creates unowned ships.
- [x] Add a debug/audit report listing force-less ships, fallback-owned ships, and their spawn callsite category.

### 1.3 Force Naming And Provenance

- [x] Give the first-mission hostile intro group a named origin: `Red Knife Advance Detachment`.
- [x] Update the first-mission hostile warning to explain the detected warp vector.
- [x] Generate default names for base defense, mining, convoy, patrol, and local forces.
- [x] Replace generic fallback names with authored names for every old 24 campaign mission.
- [x] Add named garrisons for major red/hostile POIs.
- [x] Add named friendly base forces for green/yellow hubs.
- [x] Add named trade, mining, and repair traffic forces for friendly installations.

## Phase 2: Remove Unexplained Spawns

### 2.1 Secure Friendly Installation Rules

- [x] Verify the opening Green Anchorage area has no nearby hostile ships before the telegraphed intro event.
- [x] Keep green/yellow open hubs non-combat unless a tracked hostile force is involved.
- [x] Audit all friendly installation ambient encounter generators for hidden hostile spawns.
- [x] Remove random hostile patrol bands from secure green/yellow hub pockets.
- [x] Allow hostile appearances at friendly installations only when an overmap hostile force is close, visible, detected, or scripted as infiltration.
- [x] Make infiltration cases explicitly named and warned before or during arrival.

### 2.2 Random Encounter Replacement

- [x] Find every campaign use of generic `spawnEnemy`, `spawnEnemyGroup`, `spawnEnemyAtPoint`, and `spawnCampaignPatrolBand`.
- [x] Convert generic enemy spawns into force-driven spawns.
- [x] Replace random patrol creation with campaign search groups, patrol groups, blockade forces, or raider detachments.
- [x] Ensure reinforcements are pulled from nearby force reserves rather than created from nothing.
- [x] Ensure base defenses spawn from the owning base defense force or garrison.
- [x] Ensure convoys spawn from convoy force definitions.
- [x] Ensure mining groups spawn from mining force definitions.

### 2.3 Tactical Entry Direction

- [x] Store or derive each force's overmap approach direction.
- [x] Spawn hostile forces on the tactical edge that matches their campaign approach.
- [x] Spawn reinforcements from the edge or lane nearest their parent force.
- [x] Spawn base defenders from installation-side positions rather than arbitrary offsets.
- [x] Spawn convoys and miners near their route, resource patch, or station objective.
- [x] Show entry-direction text in encounter briefings.

## Phase 3: Strategic Force Simulation

### 3.1 Force Movement

- [x] Give campaign forces target positions, routes, speed, and movement intent.
- [x] Support patrol routes, search patterns, return-to-base behavior, convoy routes, and garrison hold orders.
- [x] Let enemy forces move freely in search of the player while respecting regional difficulty doctrine.
- [x] Let friendly forces move, dock, mine, escort, repair, and regroup.
- [x] Let forces split, merge, detach, or reinforce when appropriate.
- [x] Persist force movement state through save/load.

### 3.2 Force Intent And Orders

- [x] Add force intent states such as patrolling, guarding, searching, intercepting, escorting, mining, retreating, reinforcing, and repairing.
- [x] Expose force intent in map markers and intel summaries.
- [x] Let player fleet posture influence enemy force detection and pursuit.
- [x] Let enemy doctrine influence route selection, aggression, persistence, and reinforcement behavior.
- [x] Let friendly relationship/favor influence support force response.

### 3.3 Force Strength And Attrition

- [x] Track force strength as membership, hull state, supply state, and readiness.
- [x] Apply strike damage to force membership instead of abstract group health alone.
- [x] Carry force attrition from tactical battle back to overmap.
- [x] Let damaged forces retreat, regroup, request reinforcement, or become less aggressive.
- [x] Let destroyed forces disappear from the theater and leave map memory/scars.

## Phase 4: Encounter Generation From Forces

### 4.1 Force-Driven Battle Setup

- [x] Replace tactical encounter spawn tables with force composition manifests.
- [x] For direct fleet clashes, spawn only the ships committed by the involved forces.
- [x] For POI battles, include local garrison plus nearby hostile/friendly forces that are close enough to participate.
- [x] For convoy attacks, spawn convoy force, escort force, and attacking force from their actual campaign positions.
- [x] For base defense, spawn base defense force and any reinforcements that can plausibly arrive.
- [x] For mining/trade encounters, spawn mining/trade force and local escorts without unrelated enemies.

### 4.2 Authored Mission Integration

- [x] Map every authored objective path to specific campaign force owners.
- [x] Identify which mission ships are local garrisons, which are patrols, which are reinforcements, and which are scripted bosses.
- [x] Give boss escorts named parent forces.
- [x] Give static defenses parent base/garrison forces.
- [x] Make mission briefings name the relevant force owners.
- [x] Preserve authored mission pacing while removing unexplained spawn behavior.

### 4.3 Reinforcement Logic

- [x] Reinforcements must consume or detach ships from an existing force.
- [x] Reinforcements must have travel delay or edge-entry direction based on campaign distance.
- [x] Reinforcements must be warned via comms, sensors, or map marker before arrival unless deliberately stealthy.
- [x] Stealth reinforcements must still have a force owner and post-contact reveal.
- [x] Reinforcement losses must update the parent force.

## Phase 5: Strategic Map Telemetry

### 5.1 Force Markers

- [x] Add map markers for known, suspected, and stale force contacts.
- [x] Show force name, type, confidence, last known position, direction, and intent.
- [x] Differentiate base defense, patrol, convoy, mining group, search group, and strike force icons.
- [x] Add uncertainty radius for poorly tracked forces.
- [x] Fade or stale-out force markers when contact is lost.

### 5.2 Warnings And Readouts

- [x] Add hostile force provenance lines to the intel summary.
- [x] Add selected-force detail panels.
- [x] Add route warnings when an enemy force can intercept the plotted course.
- [x] Add hub warnings when a hostile force is approaching a friendly installation.
- [x] Add scouting reports that explain where a hostile force came from.
- [x] Add after-action summaries that name destroyed, damaged, or routed forces.

### 5.3 Player Understanding

- [x] Ensure the player can tell why a battle is happening before entering it.
- [x] Ensure safe hubs read as safe when no hostile force is nearby.
- [x] Ensure danger escalates visibly as hostile forces approach.
- [x] Ensure hidden or stealth threats still leave subtle hints instead of feeling random.
- [x] Keep the UI concise enough that force telemetry does not become unreadable noise.

## Phase 6: In-World Fleet Management

### 6.1 Remove Detached Fleet Menu Reliance

- [x] Make `Tab` open the in-world Fleet board on the overmap and persistent fleet management inside live battlefields.
- [x] Keep the `B`/base-menu path for command-ship upgrades inside live battlefields.
- [x] Remove the standalone `Fleet` button from the main menu flow.
- [x] Remove or de-emphasize `GameMode.FLEET` as a player-facing main-menu destination.
- [x] Preserve fleet-hub internals only where still needed as implementation detail.
- [x] Update menu copy and docs so campaign fleet management is described as in-world.

### 6.2 In-World Fleet Screen Capability

- [x] Ensure in-world fleet management works during open space, travel, and campaign map mode.
- [x] Let players inspect all persistent ships in-world.
- [x] Let players edit/refit ships from the in-world fleet screen where safe.
- [x] Let players organize command groups and formations in-world.
- [x] Let players set battle commitment per ship in-world.
- [x] Let players review force membership, damage, cargo, and readiness in-world.
- [x] Let players close the screen cleanly back to the previous campaign state.

### 6.3 Keybind And UX

- [x] Decide final keybind ownership for `Tab`, `B`, and any fleet-management shortcut.
- [x] Prevent conflicts with shop/base/menu overlays.
- [x] Add on-screen key hints for in-world fleet management.
- [x] Add regression coverage for opening/closing fleet management during travel.
- [x] Add regression coverage for opening/closing fleet management while docked at a friendly installation.

## Phase 7: Persistence And Tests

### 7.1 Persistence

- [x] Save campaign force list.
- [x] Save force membership.
- [x] Save force current position, target position, route, intent, confidence, and stale-contact state.
- [x] Save parent-force references for active tactical encounters.
- [x] Restore force state without duplicating ships.
- [x] Restore old checkpoints safely by generating forces from existing live/persistent state.

### 7.2 Regression Tests

- [x] Add test coverage that campaign ships get assigned to named forces.
- [x] Add test coverage that the first-mission hostile intro has a named origin.
- [x] Add test coverage that persistent fleet management opens from `Tab` inside campaign battlefields.
- [x] Add test coverage that no campaign encounter exits setup with unowned live ships.
- [x] Add test coverage that friendly hub ambient encounters do not spawn hostiles without a hostile parent force.
- [x] Add test coverage that search-group encounters spawn from the search-group force.
- [x] Add test coverage that tactical kills update parent force membership or strength.
- [x] Add checkpoint tests for force save/load.
- [x] Add UI tests for force marker/readout visibility.

### 7.3 Audit Gates

- [x] Add a development assertion or telemetry warning for unowned campaign ships.
- [x] Add a focused force-ownership test suite command.
- [x] Run full campaign-focused regression suite after each major phase.
- [x] Run a manual smoke pass from Green Anchorage through first hostile contact.
- [x] Run a manual smoke pass for a friendly hub with no nearby hostiles.
- [x] Run a manual smoke pass for hostile force interception.

## Current Implementation Notes

- Initial code foundation is in `src/CampaignSystem.java`, `src/UISystem.java`, and `src/app/ui/MainMenuPanel.java`.
- Current test coverage starts in `test/CampaignForceOwnershipTest.java`.
- The current implementation persists force identity plus richer force movement, route, intent, confidence, and stale-contact state through checkpoints.
- `GameMode.FLEET` is no longer presented as a normal main-menu destination; it remains as an internal resume/fleet-hub implementation detail.
- Phase 5 telemetry now includes force scouting reports, compact battle-context lines, safe/threatened hub safety states, hidden-threat hints, and after-action force outcome lines.
- Phase 6 fleet management uses `Tab` for the overmap Fleet board and live-battlefield persistent fleet overlay. `B` remains the live-battlefield command-upgrade console. The Fleet board covers open space, travel, map, and docked states and exposes roster force membership, damage, cargo, readiness, groups, commitments, and safe refit entry.
- Phase 7 persistence now stores active tactical encounter force ids plus parent-force ids, restores them without duplicate live ships, and reconciles tactical casualties back into campaign-force membership and strength.
- Focused force-ownership regressions can be run with `powershell -ExecutionPolicy Bypass -File .\scripts\run-force-ownership-tests.ps1`; add `-FullCampaign` for the broader campaign-focused suite.
- The former manual smoke items are now covered by deterministic headless smoke/regression tests for intro contact, safe friendly hubs, and hostile interception warnings.
- Authored campaign spawn paths now route through explicit named force owners; the fallback audit remains as a defensive telemetry net.

## Recommended Work Order

1. Keep `scripts\run-force-ownership-tests.ps1 -FullCampaign` green while changing campaign spawning, persistence, or strategic UI.
2. Use the fallback audit report as a warning surface only; new campaign-authored ships should continue to receive explicit force owners.
3. Preserve the headless smoke coverage for intro contact, safe hubs, and hostile interception when extending the campaign map.

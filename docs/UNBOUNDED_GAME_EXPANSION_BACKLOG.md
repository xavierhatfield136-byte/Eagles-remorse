# Eagles Remorse: Unbounded Expansion Backlog

Date: 2026-05-30
Status: Idea reservoir / long-horizon wishlist

## Purpose

This document is intentionally larger than a practical sprint plan.

It collects changes that could improve the game even when they would require major rewrites, new content pipelines, extensive balancing, or months of implementation. Items are unchecked because this is a source pool for future design passes, not a claim that every feature should ship.

## How To Use This Document

- Pull small, coherent groups into focused implementation checklists.
- Prioritize clarity, stability, performance, and player trust before adding more surface area.
- Prefer systems that create new decisions over systems that only add more text.
- Preserve the game's command-ship identity: the player should feel like a fleet commander inside a living war.
- Treat every new mechanic as incomplete until it has UI, persistence, tests, telemetry, and a manual-play acceptance scenario.

## 1. Immediate Player Trust And Stability

### 1.1 Soft-Lock Prevention

- [x] Add a centralized modal queue so only one blocking prompt can own input at a time.
- [x] Add explicit modal priority rules for tactical entry, intervention, hub actions, story scenes, and confirmations.
- [x] Add a visible "dismiss stale prompt" recovery action when referenced entities no longer exist.
- [x] Add watchdog logic that clears any blocking prompt with no valid responder.
- [x] Add watchdog logic that restores `RUNNING` when no active overlay owns a paused state.
- [x] Add debug logging for every state transition into and out of `PAUSED`, `MAP`, `SHOP`, `FLEET`, and `BASE_MENU`.
- [x] Add regression tests for back-to-back hostile contacts from every encounter source.
- [x] Add regression tests for contacts arriving during mining, docking, travel, tactical entry, and warp exit.
- [x] Add regression tests for save/load while a prompt is open.
- [x] Add regression tests for escape-menu use during every overlay state.
- [x] Add a developer command that prints the current overlay owner and state-transition history.

### 1.2 Input Ownership

- [ ] Define a single canonical hotkey table used by bindings, HUD hints, help screens, and docs.
- [ ] Add an automated test that compares rendered help text against registered hotkeys.
- [ ] Add an automated test that rejects duplicate unqualified hotkey bindings.
- [ ] Separate global, tactical, overmap, modal, shop, and fleet-editor input scopes.
- [ ] Add remappable keyboard controls.
- [ ] Add mouse rebinding.
- [ ] Add controller bindings.
- [ ] Add controller glyph switching.
- [ ] Add conflict warnings in the keybinding menu.
- [ ] Add a "restore defaults" action per input category.
- [ ] Add a searchable controls screen.
- [ ] Add a small current-context input legend that never advertises unavailable actions.

### 1.3 Performance Guardrails

- [ ] Add a frame-time profiler overlay with update, render, AI, campaign, asset-load, and GC timing.
- [ ] Add render counters for visible ships, projectiles, wrecks, VFX, sprites, and UI panels.
- [ ] Add a warning when image decode occurs during a rendered frame.
- [ ] Add a warning when any asset is loaded from disk after gameplay begins.
- [ ] Add bounded caches for every image library.
- [ ] Audit all `BufferedImage.getSubimage(...)` uses for retained oversized rasters.
- [ ] Add compact sprite-atlas generation for multipart ships.
- [ ] Add texture-atlas support for turrets, wreck chunks, projectiles, and UI chrome.
- [ ] Add asset prewarm manifests per game mode instead of prewarming all content.
- [ ] Add sprite-memory budgets and cache telemetry.
- [ ] Add distant-ship render simplification.
- [ ] Add VFX quality tiers.
- [ ] Add projectile trail budgets.
- [ ] Add wreck-chunk budgets.
- [ ] Add adaptive visual degradation when frame time exceeds budget.
- [ ] Add a repeatable late-campaign performance scenario to CI.
- [ ] Add a battle stress harness with hundreds of ships and sustained missile fire.
- [ ] Add a long-running memory soak test.
- [ ] Add a save/load soak test across repeated tactical transitions.

## 2. First-Hour Experience

### 2.1 Campaign Onboarding

- [ ] Replace the opening information dump with a paced command tutorial.
- [ ] Teach movement, mining, docking, map use, fleet management, and combat in separate beats.
- [ ] Let players skip each tutorial beat independently.
- [ ] Add a replayable tutorial archive.
- [ ] Add contextual reminders only after the player appears stuck.
- [ ] Add a first-contact tutorial explaining manual battle versus auto-resolve.
- [ ] Add a first-strike tutorial that previews cost, target quality, and consequences.
- [ ] Add a first-fleet-management tutorial for commitment, reserve, refit, and commissioning.
- [ ] Add a first-resource-shortage tutorial with a clear recovery path.
- [ ] Add a first-save explanation and visible checkpoint confirmation.

### 2.2 Difficulty And Accessibility Defaults

- [ ] Add difficulty presets focused on command complexity, combat lethality, and strategic pressure separately.
- [ ] Add a relaxed campaign mode with reduced attrition and slower hostile escalation.
- [ ] Add a tactical-only mode for players who want battles without campaign logistics.
- [ ] Add a command-only mode with auto-resolved tactical battles.
- [ ] Add an iron-command mode with limited saves and harsher losses.
- [ ] Add a custom difficulty screen with individually adjustable systems.
- [ ] Add colorblind palettes for faction markers, warnings, room damage, and shield states.
- [ ] Add scalable UI text.
- [ ] Add high-contrast HUD mode.
- [ ] Add reduced-flash and reduced-screen-shake options.
- [ ] Add subtitle size, background, and speaker-label settings.
- [ ] Add pause-on-focus-loss.
- [ ] Add hold-versus-toggle options for mining, firing, and map interactions.

## 3. Tactical Combat Depth

### 3.1 Ship Handling

- [ ] Add distinct inertia profiles by hull class.
- [ ] Add reverse thrust behavior and braking penalties.
- [ ] Add drift, oversteer, and damaged-engine handling.
- [ ] Add emergency burn with heat, fuel, and engine-damage risk.
- [ ] Add formation-matching speed controls.
- [ ] Add collision avoidance that respects player intent without feeling magnetic.
- [ ] Add ram damage and dedicated ram-resistant hull identities.
- [ ] Add tractor systems for rescue, salvage, and towing disabled ships.
- [ ] Add docking approach assistance.
- [ ] Add manual orientation hold for broadside ships.

### 3.2 Damage And Survival

- [ ] Expand room-level damage into repair priorities and cascading failures.
- [ ] Add fire spread, decompression, coolant leaks, and electrical arcs as distinct hazards.
- [ ] Add crew casualty states that reduce station effectiveness.
- [ ] Add temporary evacuation of damaged compartments.
- [ ] Add bulkhead sealing choices.
- [ ] Add ammunition cook-off risk.
- [ ] Add reactor instability escalation.
- [ ] Add engine flare signatures when propulsion is damaged.
- [ ] Add bridge damage effects on fleet-command responsiveness.
- [ ] Add sensor-array damage effects on lock quality and map certainty.
- [ ] Add hangar damage effects on sortie launch times.
- [ ] Add visible damage decals that correspond to actual damaged rooms.
- [ ] Add persistent hull scars after major battles.
- [ ] Add recoverable disabled ships instead of binary death for some hulls.
- [ ] Add surrender, abandonment, and scuttle outcomes.

### 3.3 Weapons

- [ ] Add clearer weapon-role categories and tooltips.
- [ ] Add armor penetration, shield pressure, subsystem disruption, and area denial roles.
- [ ] Add manual salvo timing.
- [ ] Add staggered battery fire.
- [ ] Add broadside battery arcs.
- [ ] Add spinal weapons that require alignment.
- [ ] Add point-defense prioritization controls.
- [ ] Add missile doctrine selection per rack.
- [ ] Add decoys, chaff, flares, and electronic countermeasures.
- [ ] Add mines, minefields, and mine-clearing tools.
- [ ] Add boarding pods.
- [ ] Add repair drones.
- [ ] Add shield-transfer support beams.
- [ ] Add tractor disruption weapons.
- [ ] Add environmental weapon interactions, such as detonating volatile ore pockets.
- [ ] Add weapon heat management and temporary overdrive.
- [ ] Add ammunition logistics by weapon family.

### 3.4 Tactical Orders

- [ ] Add click-to-command fleet orders during battle.
- [ ] Add selectable ship groups.
- [ ] Add formation presets with visible previews.
- [ ] Add escort, screen, flank, hold, pursue, retreat, and regroup orders.
- [ ] Add focus-fire orders.
- [ ] Add protect-target orders.
- [ ] Add capture-zone orders.
- [ ] Add salvage-under-fire orders.
- [ ] Add "avoid collateral damage" rules near civilian traffic.
- [ ] Add autonomous doctrine profiles per ship.
- [ ] Add order acknowledgment timing affected by comms damage and distance.
- [ ] Add delayed or garbled orders under jamming.
- [ ] Add tactical pause for players who want deliberate command play.
- [ ] Add replayable battle timeline markers for major orders and casualties.

## 4. Ship Identity And Fleet Building

### 4.1 Hull Roster

- [ ] Give every hull a concise battlefield role, counter, and weakness.
- [ ] Add role-specific silhouette checks so ships remain readable at combat zoom.
- [ ] Add faction-specific hull variants instead of palette-only identity.
- [ ] Add civilian hull families for trade, mining, repair, salvage, and evacuation.
- [ ] Add specialist command ships.
- [ ] Add stealth hulls with real signature tradeoffs.
- [ ] Add artillery hulls that require screens and spotters.
- [ ] Add electronic-warfare hulls.
- [ ] Add dedicated hospital and rescue ships.
- [ ] Add mobile refinery and logistics ships.
- [ ] Add boarding and marine-transport ships.
- [ ] Add mine-layer and mine-sweeper ships.
- [ ] Add tug and recovery ships.
- [ ] Add prototype hulls with maintenance burdens.
- [ ] Add faction-unique titan families.

### 4.2 Refit And Construction

- [ ] Replace broad upgrade tracks with slot-based refit decisions where appropriate.
- [ ] Add hull weight, power, heat, crew, and maintenance budgets.
- [ ] Add refit templates.
- [ ] Add saved fleet loadouts.
- [ ] Add module rarity and industrial availability.
- [ ] Add damaged-module repair versus replacement decisions.
- [ ] Add captured-module integration.
- [ ] Add faction-tech compatibility penalties.
- [ ] Add shipyard specialization by region.
- [ ] Add construction queues.
- [ ] Add refit time as a strategic cost.
- [ ] Add emergency field refits with reliability penalties.
- [ ] Add fleet doctrine presets that suggest coherent ship mixes.

### 4.3 Persistent Ships And Crews

- [ ] Give persistent ships service histories.
- [ ] Track kills, rescues, retreats, scars, and major engagements.
- [ ] Add ship nicknames earned from events.
- [ ] Add captain personalities and doctrine preferences.
- [ ] Add crew experience and specialization.
- [ ] Add morale effects from casualties, victories, and shortages.
- [ ] Add commendations.
- [ ] Add mutiny, desertion, and refusal risk in extreme conditions.
- [ ] Add memorial records for destroyed ships.
- [ ] Add successor ships that inherit names and traditions.
- [ ] Add a fleet archive screen.

## 5. Strategic Campaign Expansion

### 5.1 Larger Theater

- [ ] Expand the overmap into multiple connected star systems.
- [ ] Add travel lanes, jump points, gravity wells, and blockade chokepoints.
- [ ] Add regions with distinct environmental rules.
- [ ] Add fog-of-war boundaries and unexplored space.
- [ ] Add hidden routes discovered through scouting or diplomacy.
- [ ] Add moving front lines.
- [ ] Add persistent installations that can change faction ownership.
- [ ] Add temporary forward operating bases.
- [ ] Add contested resource belts.
- [ ] Add civilian population centers with strategic consequences.
- [ ] Add planetary orbit layers.
- [ ] Add deep-space anomalies with optional high-risk content.

### 5.2 Faction Directors

- [ ] Replace simple faction priorities with explicit strategic objectives.
- [ ] Give directors limited intelligence instead of global knowledge.
- [ ] Give directors resource budgets and construction queues.
- [ ] Let directors choose between raids, defense, logistics, research, diplomacy, and major offensives.
- [ ] Add director personality variants per campaign seed.
- [ ] Add faction exhaustion and political pressure.
- [ ] Add director mistakes and recoveries so the war feels organic.
- [ ] Add feints, decoy operations, and misinformation.
- [ ] Add seasonal or chapter-level doctrine shifts.
- [ ] Add faction-specific victory conditions.
- [ ] Add neutral actors who can become aligned, hostile, or fragmented.
- [ ] Add pirate leaders with local agendas.
- [ ] Add rogue AI behavior that does not follow normal faction logic.

### 5.3 Campaign Battles

- [ ] Allow multi-fleet battles with more than two sides.
- [ ] Add battle fronts, reserves, flanks, and retreat corridors.
- [ ] Add reinforcement windows that matter tactically.
- [ ] Add intervention choices beyond join, ignore, strike, and observe.
- [ ] Add evacuation, blockade-running, relief, pursuit, and surrender interventions.
- [ ] Add battle reports with participant losses and strategic consequences.
- [ ] Add delayed intelligence for battles beyond sensor coverage.
- [ ] Add rumors and conflicting reports.
- [ ] Add battle wreck fields that remain visitable.
- [ ] Add rescue operations after large fleet clashes.
- [ ] Add captured ships and prisoners.
- [ ] Add post-battle salvage rights disputes.

### 5.4 Player Strategic Authority

- [ ] Add persistent task groups with custom names.
- [ ] Add drag-and-drop group composition.
- [ ] Add independent group routing.
- [ ] Add patrol loops.
- [ ] Add escort assignment.
- [ ] Add garrison assignment.
- [ ] Add convoy scheduling.
- [ ] Add repair and resupply orders.
- [ ] Add scouting orders.
- [ ] Add ambush and blockade orders.
- [ ] Add rules of engagement.
- [ ] Add automatic retreat thresholds.
- [ ] Add delegation to NPC captains.
- [ ] Add an operations timeline showing expected arrivals and risks.
- [ ] Add map overlays for logistics, sensors, control, danger, trade, and known hostile routes.

## 6. Economy, Logistics, And Industry

### 6.1 Resource Model

- [ ] Split supplies into fuel, ammunition, repair materials, provisions, and specialist components.
- [ ] Add cargo capacity and cargo allocation.
- [ ] Add resupply rates by installation quality.
- [ ] Add emergency rationing.
- [ ] Add fuel efficiency by formation, speed, and route type.
- [ ] Add ammunition expenditure forecasts.
- [ ] Add maintenance debt.
- [ ] Add spare-parts shortages.
- [ ] Add crew fatigue.
- [ ] Add fleet readiness recovery curves.
- [ ] Add convoy dependency for isolated bases.
- [ ] Add blockade starvation.
- [ ] Add black-market procurement.
- [ ] Add salvage processing time.

### 6.2 Mining And Salvage

- [ ] Add multiple ore grades.
- [ ] Add rare strategic materials.
- [ ] Add volatile deposits.
- [ ] Add mining claims and faction permissions.
- [ ] Add mining drones and specialized mining fleets.
- [ ] Add refinery throughput.
- [ ] Add contested mining contracts.
- [ ] Add survey gameplay.
- [ ] Add wreck stripping choices: quick salvage, careful recovery, or tow.
- [ ] Add black-box recovery.
- [ ] Add survivor recovery.
- [ ] Add hazardous wrecks.
- [ ] Add illegal salvage and reputation consequences.

### 6.3 Markets And Contracts

- [ ] Add regional price simulation.
- [ ] Add supply shocks from convoy losses and sieges.
- [ ] Add faction-specific inventories.
- [ ] Add contract boards.
- [ ] Add escort, rescue, bounty, survey, salvage, and smuggling contracts.
- [ ] Add negotiated contract terms.
- [ ] Add deadlines.
- [ ] Add collateral and reputation stakes.
- [ ] Add contract chains.
- [ ] Add competing bidders.
- [ ] Add shortages that create player opportunities.
- [ ] Add trade route investment.
- [ ] Add insurance for expensive hulls.

## 7. Diplomacy, Reputation, And Narrative

### 7.1 Faction Relationships

- [ ] Track reputation separately with military, civilian, industrial, and political groups.
- [ ] Add visible reasons for reputation changes.
- [ ] Add favors and obligations.
- [ ] Add faction requests.
- [ ] Add negotiation scenes.
- [ ] Add ceasefires and temporary alliances.
- [ ] Add betrayal risk.
- [ ] Add rules-of-engagement consequences.
- [ ] Add civilian collateral consequences.
- [ ] Add prisoner treatment choices.
- [ ] Add salvage-rights disputes.
- [ ] Add diplomatic escorts and summit missions.

### 7.2 Reactive Storytelling

- [ ] Add named recurring NPC captains.
- [ ] Let NPC captains remember prior encounters.
- [ ] Add rival commanders.
- [ ] Add rescue returns and revenge arcs.
- [ ] Add faction news bulletins.
- [ ] Add crew commentary on recent strategic events.
- [ ] Add dynamic mission briefings generated from current war state.
- [ ] Add authored story beats with multiple systemic entry conditions.
- [ ] Add consequences for arriving early, late, overprepared, or depleted.
- [ ] Add campaign endings based on war state, allies, losses, and player doctrine.
- [ ] Add an epilogue timeline.

### 7.3 Crew Layer

- [ ] Give bridge officers persistent names, portraits, specialties, and opinions.
- [ ] Add officer disagreements during major decisions.
- [ ] Add trust and stress states.
- [ ] Add officer replacement after casualties.
- [ ] Add station-specific tactical recommendations.
- [ ] Add captain log entries.
- [ ] Add optional voiced briefings.
- [ ] Add crew banter frequency controls.
- [ ] Add a quiet mode for players who want less chatter.

## 8. Missions And Encounter Variety

### 8.1 Mission Families

- [ ] Add convoy escort missions with route decisions.
- [ ] Add convoy interception missions.
- [ ] Add blockade-running missions.
- [ ] Add station defense missions.
- [ ] Add station evacuation missions.
- [ ] Add search-and-rescue missions.
- [ ] Add salvage races.
- [ ] Add stealth reconnaissance missions.
- [ ] Add ambush setup missions.
- [ ] Add minefield clearance missions.
- [ ] Add boarding and capture missions.
- [ ] Add disabled-ship tow missions.
- [ ] Add prison transport interception missions.
- [ ] Add diplomatic escort missions.
- [ ] Add smuggling missions.
- [ ] Add multi-stage pursuit missions.
- [ ] Add retreat-under-pressure missions.
- [ ] Add fleet rendezvous missions.
- [ ] Add titan-hunt missions.
- [ ] Add anomaly investigation missions.

### 8.2 Tactical Space Identity

- [ ] Add authored battlefield templates for hubs, belts, wreck fields, orbital lanes, and deep space.
- [ ] Add faction-specific station architecture.
- [ ] Add civilian traffic lanes.
- [ ] Add destructible infrastructure.
- [ ] Add neutral structures that create collateral concerns.
- [ ] Add navigation hazards.
- [ ] Add nebula visibility rules.
- [ ] Add gravity anomalies.
- [ ] Add solar flare windows.
- [ ] Add asteroid occlusion.
- [ ] Add minefields.
- [ ] Add jump-point turbulence.
- [ ] Add battle damage that persists when revisiting a location.
- [ ] Add location-specific audio ambience.

### 8.3 Procedural Composition

- [ ] Build encounter composition rules from represented campaign forces.
- [ ] Add doctrine-based reinforcement composition.
- [ ] Add faction-specific formation templates.
- [ ] Add difficulty-aware composition without hidden stat inflation.
- [ ] Add environmental compatibility checks.
- [ ] Add objective-aware spawn lanes.
- [ ] Add civilian-presence rules.
- [ ] Add post-battle cleanup rules.
- [ ] Add deterministic seeds for reproducible bug reports.

## 9. Sensors, Stealth, And Information Warfare

- [ ] Add active and passive sensor modes.
- [ ] Add sensor emission risk.
- [ ] Add signature profiles by hull, speed, damage, and weapon use.
- [ ] Add contact classification uncertainty.
- [ ] Add false positives.
- [ ] Add contact merging and splitting.
- [ ] Add decoy fleets.
- [ ] Add spoofed transponders.
- [ ] Add communications interception.
- [ ] Add jamming cones and area effects.
- [ ] Add relay placement decisions.
- [ ] Add scout patrol routes.
- [ ] Add intelligence reports with confidence and age.
- [ ] Add stealth approach routes.
- [ ] Add silent-running penalties.
- [ ] Add electronic attack strikes.
- [ ] Add counter-intelligence actions.
- [ ] Add enemy adaptation to repeated sensor tactics.

## 10. Strategic Strikes And Support

- [ ] Add configurable strike packages.
- [ ] Add strike preparation time.
- [ ] Add launch-platform positioning requirements.
- [ ] Add interception risk.
- [ ] Add target-quality thresholds.
- [ ] Add collateral estimates.
- [ ] Add decoy-target risk.
- [ ] Add reconnaissance support.
- [ ] Add electronic-warfare support.
- [ ] Add mine-laying support.
- [ ] Add repair-tender deployment.
- [ ] Add emergency extraction support.
- [ ] Add reserve fleet call-in.
- [ ] Add orbital bombardment where fiction permits.
- [ ] Add enemy counter-strikes.
- [ ] Add strike-defense installations.
- [ ] Add after-action imagery and reports.

## 11. UI And Command Experience

### 11.1 Command Layer

- [ ] Add a stable screen hierarchy with clear ownership for map, fleet, resources, contacts, and strikes.
- [ ] Add breadcrumb navigation.
- [ ] Add consistent back behavior.
- [ ] Add compact and expanded panel modes.
- [ ] Add information-density presets.
- [ ] Add pinned contacts.
- [ ] Add compare mode for destinations, fleets, and contracts.
- [ ] Add a notification inbox.
- [ ] Add warning categories and filters.
- [ ] Add an operations log with timestamps.
- [ ] Add a pause-and-plan mode.
- [ ] Add map search.
- [ ] Add map bookmarks.
- [ ] Add route preview with fuel, time, danger, and likely contacts.
- [ ] Add visible automation rules for delegated fleets.

### 11.2 Tactical HUD

- [ ] Add scalable target cards.
- [ ] Add clearer shield facing.
- [ ] Add clearer subsystem damage priority.
- [ ] Add clearer allied order status.
- [ ] Add formation visualization.
- [ ] Add missile-threat warnings.
- [ ] Add incoming-strike warnings.
- [ ] Add collision alerts.
- [ ] Add offscreen threat indicators.
- [ ] Add configurable combat log verbosity.
- [ ] Add HUD presets for command, piloting, accessibility, and screenshots.
- [ ] Add screenshot mode.

## 12. Art, Audio, And Presentation

### 12.1 Visuals

- [ ] Complete unique ship skins for every major faction and hull family.
- [ ] Add damage-stage variants for all important hulls.
- [ ] Add destroyed multipart variants.
- [ ] Add faction-specific turret skins.
- [ ] Add engine plume variants.
- [ ] Add shield-impact variants.
- [ ] Add faction-specific missile trails.
- [ ] Add station module art.
- [ ] Add environmental prop sets.
- [ ] Add portrait sets for recurring officers and captains.
- [ ] Add map icons with silhouette readability at multiple zoom levels.
- [ ] Add consistent UI art guidelines and spacing tokens.
- [ ] Add visual regression screenshots for major screens.

### 12.2 Audio

- [ ] Add faction-specific weapon audio identities.
- [ ] Add layered engine audio by hull size and damage state.
- [ ] Add shield, armor, and subsystem impact differentiation.
- [ ] Add station ambience.
- [ ] Add map-layer ambience.
- [ ] Add battle-intensity music transitions.
- [ ] Add low-resource warning tones.
- [ ] Add incoming-strike warnings.
- [ ] Add radio distortion under jamming.
- [ ] Add voice-line cooldown and priority rules.
- [ ] Add dynamic mix ducking for important alerts.
- [ ] Add accessibility-friendly audio captions.

## 13. Save, Replay, And Campaign Longevity

- [ ] Add multiple save slots.
- [ ] Add autosave rotation.
- [ ] Add visible checkpoint metadata.
- [ ] Add save migration tests for every schema revision.
- [ ] Add corrupt-save recovery.
- [ ] Add campaign seed display and sharing.
- [ ] Add battle replay files.
- [ ] Add campaign event logs suitable for bug reports.
- [ ] Add a post-campaign statistics screen.
- [ ] Add new-game-plus modifiers.
- [ ] Add challenge seeds.
- [ ] Add daily or weekly scenario seeds.
- [ ] Add custom scenario setup.
- [ ] Add mod-friendly data files for hulls, weapons, factions, missions, and balance.

## 14. Architecture And Tooling

- [ ] Split oversized systems into clearer ownership boundaries.
- [ ] Separate campaign simulation, tactical simulation, UI projection, persistence, and presentation.
- [ ] Replace ad hoc state mutation with explicit transition APIs.
- [ ] Add typed IDs for fleets, battles, locations, ships, prompts, and contracts.
- [ ] Add invariant checks for stale references.
- [ ] Add invariant checks for duplicate ownership.
- [x] Add invariant checks for impossible overlay combinations.
- [ ] Add structured event logging.
- [ ] Add deterministic simulation mode.
- [ ] Add scenario fixtures for common bug reports.
- [ ] Add headless campaign playback.
- [ ] Add headless tactical battle playback.
- [ ] Add performance budgets enforced in CI.
- [ ] Add asset validation tooling.
- [ ] Add missing-asset reports.
- [ ] Add duplicate-asset reports.
- [ ] Add automated screenshot capture.
- [ ] Add save-schema diff documentation.
- [ ] Add a content-authoring validator.
- [ ] Add a balance-data export for spreadsheet analysis.

## 15. Testing Matrix

- [ ] Add one-click smoke scenarios for campaign start, mining, docking, travel, intercept, tactical entry, victory, retreat, and save/load.
- [ ] Add overlay-state permutation tests.
- [ ] Add hotkey-context permutation tests.
- [ ] Add faction-hostility matrix tests.
- [ ] Add fleet-director long-run tests.
- [ ] Add economy long-run tests.
- [ ] Add route-risk forecast accuracy tests.
- [ ] Add tactical continuity tests for every encounter family.
- [ ] Add persistent-ship casualty reconciliation tests.
- [ ] Add strike-targeting tests for every strike family.
- [ ] Add sensor-certainty decay tests.
- [ ] Add accessibility screenshot checks.
- [ ] Add UI hitbox-to-render-bound tests for every clickable panel.
- [ ] Add memory-usage regression checks.
- [ ] Add frame-time regression checks.
- [ ] Add save compatibility fixtures from old versions.
- [ ] Add randomized fuzz tests for campaign transitions.

## 16. Large Stretch Goals

- [ ] Add cooperative multiplayer with shared command roles.
- [ ] Add asynchronous campaign sharing.
- [ ] Add skirmish fleet-builder mode.
- [ ] Add scenario editor.
- [ ] Add mission editor.
- [ ] Add faction editor.
- [ ] Add Steam Workshop-style mod packaging.
- [ ] Add procedural star-system generation.
- [ ] Add branching campaign chapters.
- [ ] Add faction campaigns with different command fantasies.
- [ ] Add playable Green defense campaign.
- [ ] Add playable Yellow trade-and-survival campaign.
- [ ] Add playable Red offensive campaign.
- [ ] Add rogue-AI survival campaign.
- [ ] Add persistent metagame unlocks that do not undermine campaign balance.
- [ ] Add spectator mode for autonomous fleet wars.
- [ ] Add exportable after-action reports.
- [ ] Add cinematic replay camera tools.

## 17. Candidate Extraction Packs

These are reasonable future documents to extract from this reservoir.

- [ ] Stability and state-machine hardening checklist.
- [ ] Performance budget and asset-lifetime checklist.
- [ ] First-hour onboarding redesign.
- [ ] Tactical fleet-orders implementation plan.
- [ ] Persistent ship history and captain system.
- [ ] Multi-system strategic map expansion.
- [ ] Economy, logistics, and market overhaul.
- [ ] Diplomacy and reactive narrative roadmap.
- [ ] Tactical battlefield identity art plan.
- [ ] Accessibility and input-remapping checklist.
- [ ] Architecture decomposition plan.
- [ ] Automated scenario and soak-test harness plan.

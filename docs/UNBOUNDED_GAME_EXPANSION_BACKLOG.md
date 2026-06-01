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

- [x] Define a single canonical hotkey table used by bindings, HUD hints, help screens, and docs.
- [x] Add an automated test that compares rendered help text against registered hotkeys.
- [x] Add an automated test that rejects duplicate unqualified hotkey bindings.
- [x] Separate global, tactical, overmap, modal, shop, and fleet-editor input scopes.
- [x] Add remappable keyboard controls.
- [x] Add mouse rebinding.
- [x] Add controller bindings.
- [x] Add controller glyph switching.
- [x] Add conflict warnings in the keybinding menu.
- [x] Add a "restore defaults" action per input category.
- [x] Add a searchable controls screen.
- [x] Add a small current-context input legend that never advertises unavailable actions.

### 1.3 Performance Guardrails

- [x] Add a frame-time profiler overlay with update, render, AI, campaign, asset-load, and GC timing.
- [x] Add render counters for visible ships, projectiles, wrecks, VFX, sprites, and UI panels.
- [x] Add a warning when image decode occurs during a rendered frame.
- [x] Add a warning when any asset is loaded from disk after gameplay begins.
- [x] Add bounded caches for every image library.
- [x] Audit all `BufferedImage.getSubimage(...)` uses for retained oversized rasters.
- [x] Add compact sprite-atlas generation for multipart ships.
- [x] Add texture-atlas support for turrets, wreck chunks, projectiles, and UI chrome.
- [x] Add asset prewarm manifests per game mode instead of prewarming all content.
- [x] Add sprite-memory budgets and cache telemetry.
- [x] Add distant-ship render simplification.
- [x] Add VFX quality tiers.
- [x] Add projectile trail budgets.
- [x] Add wreck-chunk budgets.
- [x] Add adaptive visual degradation when frame time exceeds budget.
- [x] Add a repeatable late-campaign performance scenario to CI.
- [x] Add a battle stress harness with hundreds of ships and sustained missile fire.
- [x] Add a long-running memory soak test.
- [x] Add a save/load soak test across repeated tactical transitions.

## 2. First-Hour Experience

### 2.1 Campaign Onboarding

- [x] Replace the opening information dump with a paced command tutorial.
- [x] Teach movement, mining, docking, map use, fleet management, and combat in separate beats.
- [x] Let players skip each tutorial beat independently.
- [x] Add a replayable tutorial archive.
- [x] Add contextual reminders only after the player appears stuck.
- [x] Add a first-contact tutorial explaining manual battle versus auto-resolve.
- [x] Add a first-strike tutorial that previews cost, target quality, and consequences.
- [x] Add a first-fleet-management tutorial for commitment, reserve, refit, and commissioning.
- [x] Add a first-resource-shortage tutorial with a clear recovery path.
- [x] Add a first-save explanation and visible checkpoint confirmation.

### 2.2 Difficulty And Accessibility Defaults

- [x] Add difficulty presets focused on command complexity, combat lethality, and strategic pressure separately.
- [x] Add a relaxed campaign mode with reduced attrition and slower hostile escalation.
- [x] Add a tactical-only mode for players who want battles without campaign logistics.
- [x] Add a command-only mode with auto-resolved tactical battles.
- [x] Add an iron-command mode with limited saves and harsher losses.
- [x] Add a custom difficulty screen with individually adjustable systems.
- [x] Add colorblind palettes for faction markers, warnings, room damage, and shield states.
- [x] Add scalable UI text.
- [x] Add high-contrast HUD mode.
- [x] Add reduced-flash and reduced-screen-shake options.
- [x] Add subtitle size, background, and speaker-label settings.
- [x] Add pause-on-focus-loss.
- [x] Add hold-versus-toggle options for mining, firing, and map interactions.

## 3. Tactical Combat Depth

### 3.1 Ship Handling

- [x] Add distinct inertia profiles by hull class.
- [x] Add reverse thrust behavior and braking penalties.
- [x] Add drift, oversteer, and damaged-engine handling.
- [x] Add emergency burn with heat, fuel, and engine-damage risk.
- [x] Add formation-matching speed controls.
- [x] Add collision avoidance that respects player intent without feeling magnetic.
- [x] Add ram damage and dedicated ram-resistant hull identities.
- [x] Add tractor systems for rescue, salvage, and towing disabled ships.
- [x] Add docking approach assistance.
- [x] Add manual orientation hold for broadside ships.

### 3.2 Damage And Survival

- [x] Expand room-level damage into repair priorities and cascading failures.
- [x] Add fire spread, decompression, coolant leaks, and electrical arcs as distinct hazards.
- [x] Add crew casualty states that reduce station effectiveness.
- [x] Add temporary evacuation of damaged compartments.
- [x] Add bulkhead sealing choices.
- [x] Add ammunition cook-off risk.
- [x] Add reactor instability escalation.
- [x] Add engine flare signatures when propulsion is damaged.
- [x] Add bridge damage effects on fleet-command responsiveness.
- [x] Add sensor-array damage effects on lock quality and map certainty.
- [x] Add hangar damage effects on sortie launch times.
- [x] Add visible damage decals that correspond to actual damaged rooms.
- [x] Add persistent hull scars after major battles.
- [x] Add recoverable disabled ships instead of binary death for some hulls.
- [x] Add surrender, abandonment, and scuttle outcomes.

### 3.3 Weapons

- [x] Add clearer weapon-role categories and tooltips.
- [x] Add armor penetration, shield pressure, subsystem disruption, and area denial roles.
- [x] Add manual salvo timing.
- [x] Add staggered battery fire.
- [x] Add broadside battery arcs.
- [x] Add spinal weapons that require alignment.
- [x] Add point-defense prioritization controls.
- [x] Add missile doctrine selection per rack.
- [x] Add decoys, chaff, flares, and electronic countermeasures.
- [x] Add mines, minefields, and mine-clearing tools.
- [x] Add boarding pods.
- [x] Add repair drones.
- [x] Add shield-transfer support beams.
- [x] Add tractor disruption weapons.
- [x] Add environmental weapon interactions, such as detonating volatile ore pockets.
- [x] Add weapon heat management and temporary overdrive.
- [x] Add ammunition logistics by weapon family.

### 3.4 Tactical Orders

- [x] Add click-to-command fleet orders during battle.
- [x] Add selectable ship groups.
- [x] Add formation presets with visible previews.
- [x] Add escort, screen, flank, hold, pursue, retreat, and regroup orders.
- [x] Add focus-fire orders.
- [x] Add protect-target orders.
- [x] Add capture-zone orders.
- [x] Add salvage-under-fire orders.
- [x] Add "avoid collateral damage" rules near civilian traffic.
- [x] Add autonomous doctrine profiles per ship.
- [x] Add order acknowledgment timing affected by comms damage and distance.
- [x] Add delayed or garbled orders under jamming.
- [x] Add tactical pause for players who want deliberate command play.
- [x] Add replayable battle timeline markers for major orders and casualties.

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

## 18. Fleet Command Friction And Doctrine

### 18.1 Command Network

- [ ] Give every fleet a command-network topology with flagship, relay, and fallback nodes.
- [ ] Add command bandwidth limits that make very large fleets harder to coordinate.
- [ ] Add order queues when too many commands are issued at once.
- [ ] Add command redundancy bonuses for relay ships and experienced captains.
- [ ] Add command-network collapse when the flagship is destroyed or isolated.
- [ ] Add emergency transfer of flag to another surviving ship.
- [ ] Add visual command-link overlays during tactical pause.
- [ ] Add encrypted command channels that trade bandwidth for jamming resistance.
- [ ] Add burst-transmission orders for stealth fleets.
- [ ] Add courier-drone orders when long-range communications are unavailable.
- [ ] Add doctrine-specific acknowledgment language so fleets sound culturally distinct.
- [ ] Add captains who interpret vague orders differently under pressure.

### 18.2 Standing Orders

- [ ] Add editable standing orders for ammunition conservation.
- [ ] Add editable standing orders for retreat thresholds.
- [ ] Add editable standing orders for rescuing disabled allies.
- [ ] Add editable standing orders for protecting civilian traffic.
- [ ] Add editable standing orders for pursuing fleeing enemies.
- [ ] Add editable standing orders for accepting surrender.
- [ ] Add editable standing orders for scuttling compromised ships.
- [ ] Add editable standing orders for preserving rare captured technology.
- [ ] Add doctrine templates for convoy escort, fleet battle, raid, rescue, and blockade.
- [ ] Add captain-level exceptions to fleet-wide standing orders.
- [ ] Add a pre-battle doctrine review screen with likely tradeoffs.
- [ ] Add after-action notes showing which standing orders materially changed the battle.

### 18.3 Fleet Cohesion

- [ ] Track formation cohesion as a tactical resource.
- [ ] Let aggressive turns and emergency burns break formation cohesion.
- [ ] Let veteran crews reform formations faster.
- [ ] Add crossfire bonuses for coordinated squadrons.
- [ ] Add isolation penalties for ships cut off from friendly support.
- [ ] Add panic propagation when nearby ships explode or surrender.
- [ ] Add rally actions from command ships.
- [ ] Add discipline differences between military, militia, pirate, civilian, and AI fleets.
- [ ] Add exhausted formations that need a reserve rotation.
- [ ] Add visible cohesion rings and squadron-status summaries.

## 19. Living Locations And Infrastructure

### 19.1 Installations

- [ ] Break stations into functional modules: docks, reactors, sensors, defense grids, refineries, and habitats.
- [ ] Allow individual station modules to be disabled, repaired, captured, or destroyed.
- [ ] Add construction barges that visibly assemble station modules over time.
- [ ] Add emergency station shutdown procedures during raids.
- [ ] Add station evacuation capacity limits.
- [ ] Add station garrison quality and readiness.
- [ ] Add station commanders with traits and political affiliations.
- [ ] Add orbital defense networks that require relay coverage.
- [ ] Add hidden smuggler docks and improvised repair yards.
- [ ] Add abandoned stations that can be reclaimed at strategic cost.
- [ ] Add mobile stations with slow relocation orders.
- [ ] Add memorial installations after major battles.

### 19.2 Location Evolution

- [ ] Let battlefields accumulate persistent wreck fields.
- [ ] Let wreck fields become salvage sites, ambush sites, hazards, or memorials.
- [ ] Let trade hubs visibly grow when routes are protected.
- [ ] Let isolated hubs lose services as shortages deepen.
- [ ] Let mining sites deplete, collapse, or reveal deeper deposits.
- [ ] Let repaired infrastructure retain visible scars.
- [ ] Add reconstruction projects after liberation.
- [ ] Add refugee populations that relocate after attacks.
- [ ] Add temporary military checkpoints around threatened hubs.
- [ ] Add seasonal traffic patterns and convoy surges.
- [ ] Add location histories that summarize major ownership changes and battles.
- [ ] Add map-layer before-and-after comparisons for long campaigns.

### 19.3 Planetary And Orbital Layers

- [ ] Add low-orbit battlefields with atmospheric drag and orbital debris.
- [ ] Add high-orbit transfer windows that change route efficiency.
- [ ] Add planetary shadow zones that affect sensors and solar power.
- [ ] Add moon-based sensor relays and artillery emplacements.
- [ ] Add surface-to-orbit logistics elevators as strategic objectives.
- [ ] Add civilian evacuation corridors around inhabited worlds.
- [ ] Add orbital quarantine zones during outbreaks or contamination events.
- [ ] Add reentry-capable transports and rescue capsules.
- [ ] Add planetary allegiance shifts based on protection, shortages, and collateral damage.
- [ ] Add orbit-specific skyboxes, audio ambience, and map symbology.

## 20. Personnel, Culture, And Institutional Memory

### 20.1 Crew Careers

- [ ] Track individual officer careers across multiple ships.
- [ ] Add promotion recommendations and command assignments.
- [ ] Add specialist training programs with time and resource costs.
- [ ] Add mentorship links between veteran and junior officers.
- [ ] Add officer fatigue from repeated deployments.
- [ ] Add medical leave and recovery time after severe injuries.
- [ ] Add commendations with small situational bonuses and narrative weight.
- [ ] Add disciplinary records after insubordination, panic, or war crimes.
- [ ] Add retirement, reassignment, and voluntary transfer requests.
- [ ] Add officers who return later as captains, rivals, or faction leaders.

### 20.2 Fleet Culture

- [ ] Give fleets traditions that emerge from repeated behavior.
- [ ] Add informal fleet mottos earned from major events.
- [ ] Add shipboard rituals before difficult battles.
- [ ] Add morale bonuses for rescuing survivors and recovering lost hulls.
- [ ] Add morale penalties for abandoning disabled allies.
- [ ] Add friction when captured ships are integrated into a fleet.
- [ ] Add faction-mixed crews with translation and trust challenges.
- [ ] Add memorial ceremonies after catastrophic losses.
- [ ] Add holiday, anniversary, and remembrance events during long campaigns.
- [ ] Add a fleet culture summary showing how the player's command style is perceived.

### 20.3 Civilian Life

- [ ] Add civilian captains with persistent names and route histories.
- [ ] Add merchant guilds with competing priorities.
- [ ] Add mining cooperatives that can request protection or autonomy.
- [ ] Add refugee flotillas with urgent routing decisions.
- [ ] Add independent rescue organizations.
- [ ] Add journalists and war correspondents who shape public perception.
- [ ] Add civilian rumors that may be useful, outdated, or deliberately false.
- [ ] Add civilian volunteer auxiliaries during existential threats.
- [ ] Add black-market fixers who remember favors and betrayals.
- [ ] Add civilian casualty reports with clear causal chains.

## 21. Operational Planning And Intelligence

### 21.1 Planning Tools

- [ ] Add multi-step operation plans with named phases.
- [ ] Add synchronized departure times for several task groups.
- [ ] Add conditional orders such as "engage only if escorts arrive."
- [ ] Add branch plans for success, stalemate, and retreat.
- [ ] Add staging-area selection.
- [ ] Add reserve commitment triggers.
- [ ] Add fuel, ammunition, repair, and crew-readiness projections per phase.
- [ ] Add expected enemy-response estimates.
- [ ] Add operation rehearsal using incomplete intelligence.
- [ ] Add reusable operation templates.
- [ ] Add a commander's notebook for pinned assumptions and unresolved risks.
- [ ] Add post-operation comparisons between plan and outcome.

### 21.2 Intelligence Analysis

- [ ] Add intelligence sources with reliability ratings.
- [ ] Add intercepted manifests that reveal convoy composition.
- [ ] Add scout reports that can conflict with one another.
- [ ] Add enemy order-of-battle estimates with confidence bands.
- [ ] Add analyst recommendations that can be right, incomplete, or biased.
- [ ] Add intelligence gaps explicitly shown on the map.
- [ ] Add historical enemy-behavior summaries.
- [ ] Add pattern detection for repeated raids and convoy timings.
- [ ] Add misinformation campaigns that plant believable false patterns.
- [ ] Add captured navigation data that reveals temporary routes.
- [ ] Add debriefing choices that improve intelligence quality.
- [ ] Add an intelligence archive searchable by location, faction, and date.

### 21.3 Espionage And Counterintelligence

- [ ] Add covert agents embedded in hubs and shipyards.
- [ ] Add agent recruitment with loyalty risks.
- [ ] Add sabotage operations against fuel, sensors, docks, and communications.
- [ ] Add counterintelligence sweeps that may disrupt friendly operations.
- [ ] Add compromised officers and false orders.
- [ ] Add extraction missions for exposed agents.
- [ ] Add double agents with uncertain allegiance.
- [ ] Add encrypted dead-drop locations.
- [ ] Add propaganda operations that affect faction exhaustion and civilian support.
- [ ] Add diplomatic incidents when covert actions are exposed.

## 22. Environmental Simulation And Space Weather

### 22.1 Dynamic Hazards

- [ ] Add moving radiation storms that reshape safe routes.
- [ ] Add solar flare forecasts with uncertain timing.
- [ ] Add comet trails that create temporary mining opportunities and navigation hazards.
- [ ] Add unstable asteroid clusters that slowly drift and collide.
- [ ] Add ion clouds that amplify jamming and shield instability.
- [ ] Add dense debris fields that damage high-speed ships.
- [ ] Add micro-meteor showers that threaten exposed station modules.
- [ ] Add magnetic anomalies that bend missile guidance.
- [ ] Add gravity wells that change braking distance and warp exit accuracy.
- [ ] Add volatile gas pockets that can chain-react under weapons fire.
- [ ] Add environmental hazard maps with confidence and age.
- [ ] Add AI doctrine changes when fleets encounter known hazards.

### 22.2 Resource Ecology

- [ ] Let rich deposits attract miners, pirates, patrols, and speculators over time.
- [ ] Let over-mining increase collapse risk and reduce long-term yield.
- [ ] Add refinery pollution or debris as a local strategic cost.
- [ ] Add survey uncertainty so prospecting remains valuable.
- [ ] Add rare materials tied to dangerous environmental regions.
- [ ] Add mobile resource phenomena such as cometary ice and drifting wreck clusters.
- [ ] Add depleted belts that push factions into new contested regions.
- [ ] Add salvage booms after major wars.
- [ ] Add faction policies for conservation, extraction, rationing, and hoarding.
- [ ] Add economic forecasts tied to resource discoveries and losses.

## 23. Asymmetric Factions And Internal Politics

### 23.1 Faction Identity

- [ ] Give each faction a distinct logistical model instead of only combat bonuses.
- [ ] Give each faction a distinct approach to surrender, salvage, and prisoners.
- [ ] Give each faction unique station layouts and infrastructure priorities.
- [ ] Give each faction signature command-network strengths and weaknesses.
- [ ] Give each faction unique crisis responses.
- [ ] Give each faction different tolerances for collateral damage.
- [ ] Add faction-specific mission families.
- [ ] Add faction-specific officer archetypes and radio language.
- [ ] Add faction-specific victory and survival conditions.
- [ ] Add faction-specific UI accent motifs without harming readability.

### 23.2 Internal Politics

- [ ] Split major factions into internal blocs with visible agendas.
- [ ] Add military, industrial, civilian, ideological, and intelligence power centers.
- [ ] Add bloc approval and leverage.
- [ ] Add requests that trade tactical convenience for political support.
- [ ] Add leadership changes after defeats, scandals, and victories.
- [ ] Add budget disputes that affect construction and logistics.
- [ ] Add faction hardliners who resist ceasefires.
- [ ] Add reformers who reward restraint and rescue operations.
- [ ] Add corruption investigations around procurement and salvage.
- [ ] Add internal schisms that can become neutral or hostile splinter factions.
- [ ] Add player choices that strengthen one bloc while alienating another.
- [ ] Add ending slides for the political order the player helped create.

### 23.3 Pirate, Mercenary, And Neutral Powers

- [ ] Add pirate havens with local economies and protection rackets.
- [ ] Add mercenary companies with persistent fleets and reputations.
- [ ] Add neutral defense leagues formed by threatened hubs.
- [ ] Add religious or ideological enclaves with unusual rules of engagement.
- [ ] Add nomadic flotillas with mobile markets.
- [ ] Add scavenger clans that follow major battles.
- [ ] Add smugglers who can bypass blockades at a price.
- [ ] Add privateers whose legal status changes with diplomacy.
- [ ] Add bounty hunters who pursue named captains.
- [ ] Add neutral powers that can be courted into coalition wars.

## 24. Crisis, Failure, And Recovery

### 24.1 Strategic Crises

- [ ] Add fuel crises that force painful route and fleet-priority decisions.
- [ ] Add ammunition shortages that change viable ship mixes.
- [ ] Add repair-material shortages after major offensives.
- [ ] Add crew-replacement shortages after casualty-heavy battles.
- [ ] Add refugee crises that compete with military logistics.
- [ ] Add station epidemics and quarantine decisions.
- [ ] Add mutinies and command legitimacy crises.
- [ ] Add intelligence leaks that expose player operations.
- [ ] Add supply-chain sabotage investigations.
- [ ] Add cascading front-line collapse when several hubs fall quickly.
- [ ] Add emergency coalition summits during existential threats.
- [ ] Add crisis postmortems that record the player's response.

### 24.2 Recovery Play

- [ ] Add fighting withdrawals where preserving ships is a meaningful victory.
- [ ] Add fleet rebuilding plans after catastrophic losses.
- [ ] Add emergency loans with political strings attached.
- [ ] Add reserve mothballed hulls that can be reactivated slowly.
- [ ] Add civilian requisition choices with reputation consequences.
- [ ] Add improvised repairs that create future reliability risks.
- [ ] Add salvage expeditions to recover lost strategic assets.
- [ ] Add prisoner exchanges.
- [ ] Add negotiated humanitarian corridors.
- [ ] Add comeback objectives for campaigns where the player loses key regions.
- [ ] Add graceful campaign defeat states that produce an epilogue instead of a hard stop.
- [ ] Add "continue the resistance" branches after formal defeat.

## 25. Endgame, Legacy, And Replayability

### 25.1 Endgame Structures

- [ ] Add multiple endgame crises selected by campaign history.
- [ ] Add final offensives that require several coordinated operations.
- [ ] Add defensive endgames around evacuation, survival, or delaying actions.
- [ ] Add diplomatic endgames where coalition cohesion matters.
- [ ] Add rogue-AI escalation endgames with rapidly changing tactical rules.
- [ ] Add economic endgames where the war is won by exhaustion and blockade.
- [ ] Add titan-construction races visible on the strategic map.
- [ ] Add faction-collapse thresholds and surrender negotiations.
- [ ] Add optional post-victory cleanup operations.
- [ ] Add a final command review with maps, losses, rescues, and defining decisions.

### 25.2 Campaign Legacy

- [ ] Generate a campaign chronicle from major events.
- [ ] Preserve notable ships and officers in a hall of records.
- [ ] Add lineage bonuses that are flavorful but not mandatory.
- [ ] Add unlockable historical scenarios based on completed campaigns.
- [ ] Add defeated rival captains as future scenario opponents.
- [ ] Add persistent memorial names for stations and ships.
- [ ] Add exportable fleet rosters and battle summaries.
- [ ] Add campaign comparison screens across different seeds.
- [ ] Add player-authored campaign notes.
- [ ] Add a compact share code for seed, difficulty, and major modifiers.

### 25.3 Challenge Modes

- [ ] Add a one-fleet survival campaign.
- [ ] Add a no-replacement iron fleet challenge.
- [ ] Add a civilian-protection challenge campaign.
- [ ] Add a logistics-starvation challenge campaign.
- [ ] Add a stealth-and-intelligence challenge campaign.
- [ ] Add a pirate-privateer challenge campaign.
- [ ] Add a titan-race challenge campaign.
- [ ] Add a shattered-alliance challenge campaign.
- [ ] Add score breakdowns that reward different command styles.
- [ ] Add curated monthly challenge scenarios.

## 26. Modding, Scenario Creation, And Community Longevity

### 26.1 Data-Driven Content

- [ ] Move hull definitions into validated external data files.
- [ ] Move weapon definitions into validated external data files.
- [ ] Move faction doctrines into validated external data files.
- [ ] Move station modules into validated external data files.
- [ ] Move contracts and mission templates into validated external data files.
- [ ] Move dialogue bark pools into validated external data files.
- [ ] Add schema versions and migration helpers for content packs.
- [ ] Add clear validation errors with file and field names.
- [ ] Add hot reload for selected development-time data.
- [ ] Add a content-pack dependency manifest.

### 26.2 Scenario Tools

- [ ] Add a visual battlefield template editor.
- [ ] Add drag-and-drop fleet composition for scenarios.
- [ ] Add objective placement and trigger editing.
- [ ] Add environment-hazard placement.
- [ ] Add timeline scripting for reinforcements and events.
- [ ] Add branching victory and failure conditions.
- [ ] Add test-play launch directly from the editor.
- [ ] Add deterministic scenario seeds.
- [ ] Add scenario thumbnails and metadata.
- [ ] Add import and export for standalone scenario packs.

### 26.3 Community Features

- [ ] Add shareable fleet doctrine codes.
- [ ] Add shareable custom battle setups.
- [ ] Add shareable campaign challenge codes.
- [ ] Add mod compatibility diagnostics.
- [ ] Add a safe-mode launcher that disables content packs.
- [ ] Add content-pack load ordering.
- [ ] Add per-save content-pack manifests.
- [ ] Add replay validation when mods are missing.
- [ ] Add a curated featured-scenario menu.
- [ ] Add a local scenario rating and notes system.

## 27. Additional Candidate Extraction Packs

These are larger design documents worth extracting after the first backlog pass.

- [ ] Command network, standing orders, and cohesion design.
- [ ] Living stations and evolving battlefield locations roadmap.
- [ ] Officer careers, fleet culture, and civilian-life narrative plan.
- [ ] Multi-phase operations and intelligence-analysis design.
- [ ] Environmental hazards and resource-ecology simulation plan.
- [ ] Asymmetric faction identity and internal-politics roadmap.
- [ ] Strategic crisis and recovery-play design.
- [ ] Endgame, campaign chronicle, and challenge-mode roadmap.
- [ ] Data-driven content conversion plan.
- [ ] Scenario editor and community-content roadmap.

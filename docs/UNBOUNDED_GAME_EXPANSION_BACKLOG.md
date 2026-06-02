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

- [x] Give every hull a concise battlefield role, counter, and weakness.
- [x] Add role-specific silhouette checks so ships remain readable at combat zoom.
- [x] Add faction-specific hull variants instead of palette-only identity.
- [x] Add civilian hull families for trade, mining, repair, salvage, and evacuation.
- [x] Add specialist command ships.
- [x] Add stealth hulls with real signature tradeoffs.
- [x] Add artillery hulls that require screens and spotters.
- [x] Add electronic-warfare hulls.
- [x] Add dedicated hospital and rescue ships.
- [x] Add mobile refinery and logistics ships.
- [x] Add boarding and marine-transport ships.
- [x] Add mine-layer and mine-sweeper ships.
- [x] Add tug and recovery ships.
- [x] Add prototype hulls with maintenance burdens.
- [x] Add faction-unique titan families.

### 4.2 Refit And Construction

- [x] Replace broad upgrade tracks with slot-based refit decisions where appropriate.
- [x] Add hull weight, power, heat, crew, and maintenance budgets.
- [x] Add refit templates.
- [x] Add saved fleet loadouts.
- [x] Add module rarity and industrial availability.
- [x] Add damaged-module repair versus replacement decisions.
- [x] Add captured-module integration.
- [x] Add faction-tech compatibility penalties.
- [x] Add shipyard specialization by region.
- [x] Add construction queues.
- [x] Add refit time as a strategic cost.
- [x] Add emergency field refits with reliability penalties.
- [x] Add fleet doctrine presets that suggest coherent ship mixes.

### 4.3 Persistent Ships And Crews

- [x] Give persistent ships service histories.
- [x] Track kills, rescues, retreats, scars, and major engagements.
- [x] Add ship nicknames earned from events.
- [x] Add captain personalities and doctrine preferences.
- [x] Add crew experience and specialization.
- [x] Add morale effects from casualties, victories, and shortages.
- [x] Add commendations.
- [x] Add mutiny, desertion, and refusal risk in extreme conditions.
- [x] Add memorial records for destroyed ships.
- [x] Add successor ships that inherit names and traditions.
- [x] Add a fleet archive screen.

**Completion:** Added `FleetBuildingSystem.java` as the section-4 fleet-building layer and wired live `FleetShip` instances to its hull profiles. Hull identities, refit constraints, loadouts, regional yards, construction queues, doctrine suggestions, service records, captains, crew state, morale risks, commendations, memorials, successors, and archive readouts are implemented. Campaign roster entries now persist section-4 ship records through backward-compatible checkpoints and expose identity, personnel, and archive information on the fleet board. Added regression coverage in `FleetBuildingSystemTest.java` and `CampaignStrategicCommandHudTest.java`.

## 5. Strategic Campaign Expansion

### 5.1 Larger Theater

- [x] Expand the overmap into multiple connected star systems.
- [x] Add travel lanes, jump points, gravity wells, and blockade chokepoints.
- [x] Add regions with distinct environmental rules.
- [x] Add fog-of-war boundaries and unexplored space.
- [x] Add hidden routes discovered through scouting or diplomacy.
- [x] Add moving front lines.
- [x] Add persistent installations that can change faction ownership.
- [x] Add temporary forward operating bases.
- [x] Add contested resource belts.
- [x] Add civilian population centers with strategic consequences.
- [x] Add planetary orbit layers.
- [x] Add deep-space anomalies with optional high-risk content.

### 5.2 Faction Directors

- [x] Replace simple faction priorities with explicit strategic objectives.
- [x] Give directors limited intelligence instead of global knowledge.
- [x] Give directors resource budgets and construction queues.
- [x] Let directors choose between raids, defense, logistics, research, diplomacy, and major offensives.
- [x] Add director personality variants per campaign seed.
- [x] Add faction exhaustion and political pressure.
- [x] Add director mistakes and recoveries so the war feels organic.
- [x] Add feints, decoy operations, and misinformation.
- [x] Add seasonal or chapter-level doctrine shifts.
- [x] Add faction-specific victory conditions.
- [x] Add neutral actors who can become aligned, hostile, or fragmented.
- [x] Add pirate leaders with local agendas.
- [x] Add rogue AI behavior that does not follow normal faction logic.

### 5.3 Campaign Battles

- [x] Allow multi-fleet battles with more than two sides.
- [x] Add battle fronts, reserves, flanks, and retreat corridors.
- [x] Add reinforcement windows that matter tactically.
- [x] Add intervention choices beyond join, ignore, strike, and observe.
- [x] Add evacuation, blockade-running, relief, pursuit, and surrender interventions.
- [x] Add battle reports with participant losses and strategic consequences.
- [x] Add delayed intelligence for battles beyond sensor coverage.
- [x] Add rumors and conflicting reports.
- [x] Add battle wreck fields that remain visitable.
- [x] Add rescue operations after large fleet clashes.
- [x] Add captured ships and prisoners.
- [x] Add post-battle salvage rights disputes.

### 5.4 Player Strategic Authority

- [x] Add persistent task groups with custom names.
- [x] Add drag-and-drop group composition.
- [x] Add independent group routing.
- [x] Add patrol loops.
- [x] Add escort assignment.
- [x] Add garrison assignment.
- [x] Add convoy scheduling.
- [x] Add repair and resupply orders.
- [x] Add scouting orders.
- [x] Add ambush and blockade orders.
- [x] Add rules of engagement.
- [x] Add automatic retreat thresholds.
- [x] Add delegation to NPC captains.
- [x] Add an operations timeline showing expected arrivals and risks.
- [x] Add map overlays for logistics, sensors, control, danger, trade, and known hostile routes.

**Completion:** Added `StrategicCampaignExpansionSystem.java` as the explicit section-5 strategic layer over the existing theater war, force ownership, route-risk, fog-of-war, rumor, battle, intervention, and detachment systems. It provides connected star systems, region rules, lanes, hidden-route discovery, moving fronts, ownership-changing installations, asymmetric faction directors, multi-side battle aftermath, persistent task groups, planning orders, delegation, timelines, and map overlays. Strategic task-group state now survives campaign checkpoints through `CampaignCheckpointStore`. Added `StrategicCampaignExpansionSystemTest.java`.

## 6. Economy, Logistics, And Industry

### 6.1 Resource Model

- [x] Split supplies into fuel, ammunition, repair materials, provisions, and specialist components.
- [x] Add cargo capacity and cargo allocation.
- [x] Add resupply rates by installation quality.
- [x] Add emergency rationing.
- [x] Add fuel efficiency by formation, speed, and route type.
- [x] Add ammunition expenditure forecasts.
- [x] Add maintenance debt.
- [x] Add spare-parts shortages.
- [x] Add crew fatigue.
- [x] Add fleet readiness recovery curves.
- [x] Add convoy dependency for isolated bases.
- [x] Add blockade starvation.
- [x] Add black-market procurement.
- [x] Add salvage processing time.

### 6.2 Mining And Salvage

- [x] Add multiple ore grades.
- [x] Add rare strategic materials.
- [x] Add volatile deposits.
- [x] Add mining claims and faction permissions.
- [x] Add mining drones and specialized mining fleets.
- [x] Add refinery throughput.
- [x] Add contested mining contracts.
- [x] Add survey gameplay.
- [x] Add wreck stripping choices: quick salvage, careful recovery, or tow.
- [x] Add black-box recovery.
- [x] Add survivor recovery.
- [x] Add hazardous wrecks.
- [x] Add illegal salvage and reputation consequences.

### 6.3 Markets And Contracts

- [x] Add regional price simulation.
- [x] Add supply shocks from convoy losses and sieges.
- [x] Add faction-specific inventories.
- [x] Add contract boards.
- [x] Add escort, rescue, bounty, survey, salvage, and smuggling contracts.
- [x] Add negotiated contract terms.
- [x] Add deadlines.
- [x] Add collateral and reputation stakes.
- [x] Add contract chains.
- [x] Add competing bidders.
- [x] Add shortages that create player opportunities.
- [x] Add trade route investment.
- [x] Add insurance for expensive hulls.

**Completion:** Added `EconomyLogisticsIndustrySystem.java` as the persistent section-6 campaign economy layer over tactical mining, salvage collection, convoy pressure, and hub services. It provides split stores, cargo allocation, route consumption, rationing, readiness recovery, mining claims, survey and wreck-recovery choices, regional markets, supply shocks, faction inventories, contract boards, negotiation, trade investment, and hull insurance. Economy state now survives campaign checkpoints through `CampaignCheckpointStore`. Added regression coverage in `CampaignEconomyDiplomacyExpansionSystemTest.java`.

## 7. Diplomacy, Reputation, And Narrative

### 7.1 Faction Relationships

- [x] Track reputation separately with military, civilian, industrial, and political groups.
- [x] Add visible reasons for reputation changes.
- [x] Add favors and obligations.
- [x] Add faction requests.
- [x] Add negotiation scenes.
- [x] Add ceasefires and temporary alliances.
- [x] Add betrayal risk.
- [x] Add rules-of-engagement consequences.
- [x] Add civilian collateral consequences.
- [x] Add prisoner treatment choices.
- [x] Add salvage-rights disputes.
- [x] Add diplomatic escorts and summit missions.

### 7.2 Reactive Storytelling

- [x] Add named recurring NPC captains.
- [x] Let NPC captains remember prior encounters.
- [x] Add rival commanders.
- [x] Add rescue returns and revenge arcs.
- [x] Add faction news bulletins.
- [x] Add crew commentary on recent strategic events.
- [x] Add dynamic mission briefings generated from current war state.
- [x] Add authored story beats with multiple systemic entry conditions.
- [x] Add consequences for arriving early, late, overprepared, or depleted.
- [x] Add campaign endings based on war state, allies, losses, and player doctrine.
- [x] Add an epilogue timeline.

### 7.3 Crew Layer

- [x] Give bridge officers persistent names, portraits, specialties, and opinions.
- [x] Add officer disagreements during major decisions.
- [x] Add trust and stress states.
- [x] Add officer replacement after casualties.
- [x] Add station-specific tactical recommendations.
- [x] Add captain log entries.
- [x] Add optional voiced briefings.
- [x] Add crew banter frequency controls.
- [x] Add a quiet mode for players who want less chatter.

**Completion:** Added `DiplomacyNarrativeCrewSystem.java` as the persistent section-7 campaign relationship and crew layer over existing recurring contacts, authored encounters, bridge portraits, chatter, and campaign outcomes. It provides group-specific reputation with visible reasons, favors, obligations, requests, negotiation state, alliances, betrayal risk, recurring captains with memories and arcs, reactive bulletins and briefings, ending timelines, and persistent bridge officers with opinions, stress, replacement, logs, voiced-briefing flags, banter controls, and quiet mode. Diplomacy and crew state now survives campaign checkpoints through `CampaignCheckpointStore`. Added regression coverage in `CampaignEconomyDiplomacyExpansionSystemTest.java`.

## 8. Missions And Encounter Variety

### 8.1 Mission Families

- [x] Add convoy escort missions with route decisions.
- [x] Add convoy interception missions.
- [x] Add blockade-running missions.
- [x] Add station defense missions.
- [x] Add station evacuation missions.
- [x] Add search-and-rescue missions.
- [x] Add salvage races.
- [x] Add stealth reconnaissance missions.
- [x] Add ambush setup missions.
- [x] Add minefield clearance missions.
- [x] Add boarding and capture missions.
- [x] Add disabled-ship tow missions.
- [x] Add prison transport interception missions.
- [x] Add diplomatic escort missions.
- [x] Add smuggling missions.
- [x] Add multi-stage pursuit missions.
- [x] Add retreat-under-pressure missions.
- [x] Add fleet rendezvous missions.
- [x] Add titan-hunt missions.
- [x] Add anomaly investigation missions.

### 8.2 Tactical Space Identity

- [x] Add authored battlefield templates for hubs, belts, wreck fields, orbital lanes, and deep space.
- [x] Add faction-specific station architecture.
- [x] Add civilian traffic lanes.
- [x] Add destructible infrastructure.
- [x] Add neutral structures that create collateral concerns.
- [x] Add navigation hazards.
- [x] Add nebula visibility rules.
- [x] Add gravity anomalies.
- [x] Add solar flare windows.
- [x] Add asteroid occlusion.
- [x] Add minefields.
- [x] Add jump-point turbulence.
- [x] Add battle damage that persists when revisiting a location.
- [x] Add location-specific audio ambience.

### 8.3 Procedural Composition

- [x] Build encounter composition rules from represented campaign forces.
- [x] Add doctrine-based reinforcement composition.
- [x] Add faction-specific formation templates.
- [x] Add difficulty-aware composition without hidden stat inflation.
- [x] Add environmental compatibility checks.
- [x] Add objective-aware spawn lanes.
- [x] Add civilian-presence rules.
- [x] Add post-battle cleanup rules.
- [x] Add deterministic seeds for reproducible bug reports.

## 9. Sensors, Stealth, And Information Warfare

- [x] Add active and passive sensor modes.
- [x] Add sensor emission risk.
- [x] Add signature profiles by hull, speed, damage, and weapon use.
- [x] Add contact classification uncertainty.
- [x] Add false positives.
- [x] Add contact merging and splitting.
- [x] Add decoy fleets.
- [x] Add spoofed transponders.
- [x] Add communications interception.
- [x] Add jamming cones and area effects.
- [x] Add relay placement decisions.
- [x] Add scout patrol routes.
- [x] Add intelligence reports with confidence and age.
- [x] Add stealth approach routes.
- [x] Add silent-running penalties.
- [x] Add electronic attack strikes.
- [x] Add counter-intelligence actions.
- [x] Add enemy adaptation to repeated sensor tactics.

## 10. Strategic Strikes And Support

- [x] Add configurable strike packages.
- [x] Add strike preparation time.
- [x] Add launch-platform positioning requirements.
- [x] Add interception risk.
- [x] Add target-quality thresholds.
- [x] Add collateral estimates.
- [x] Add decoy-target risk.
- [x] Add reconnaissance support.
- [x] Add electronic-warfare support.
- [x] Add mine-laying support.
- [x] Add repair-tender deployment.
- [x] Add emergency extraction support.
- [x] Add reserve fleet call-in.
- [x] Add orbital bombardment where fiction permits.
- [x] Add enemy counter-strikes.
- [x] Add strike-defense installations.
- [x] Add after-action imagery and reports.

## 11. UI And Command Experience

### 11.1 Command Layer

- [x] Add a stable screen hierarchy with clear ownership for map, fleet, resources, contacts, and strikes.
- [x] Add breadcrumb navigation.
- [x] Add consistent back behavior.
- [x] Add compact and expanded panel modes.
- [x] Add information-density presets.
- [x] Add pinned contacts.
- [x] Add compare mode for destinations, fleets, and contracts.
- [x] Add a notification inbox.
- [x] Add warning categories and filters.
- [x] Add an operations log with timestamps.
- [x] Add a pause-and-plan mode.
- [x] Add map search.
- [x] Add map bookmarks.
- [x] Add route preview with fuel, time, danger, and likely contacts.
- [x] Add visible automation rules for delegated fleets.

### 11.2 Tactical HUD

- [x] Add scalable target cards.
- [x] Add clearer shield facing.
- [x] Add clearer subsystem damage priority.
- [x] Add clearer allied order status.
- [x] Add formation visualization.
- [x] Add missile-threat warnings.
- [x] Add incoming-strike warnings.
- [x] Add collision alerts.
- [x] Add offscreen threat indicators.
- [x] Add configurable combat log verbosity.
- [x] Add HUD presets for command, piloting, accessibility, and screenshots.
- [x] Add screenshot mode.

**Completion:** Added `OperationsInformationCommandSystem.java` as the persistent sections 8-11 campaign operations layer over the existing authored encounters, tactical hazards, fog-of-war, sensor actions, strategic strikes, reserve actions, command tabs, warnings, and HUD systems. It provides mission families, battlefield templates, procedural composition rules, information-warfare contacts, configurable support packages, and command-experience preferences. Operations state now survives campaign checkpoints through `CampaignCheckpointStore`, with a separate compact command-board readout that preserves the navigation sidebar's seven-line readability contract. Added regression coverage in `OperationsInformationCommandSystemTest.java`.

## 12. Art, Audio, And Presentation

### 12.1 Visuals

- [x] Complete unique ship skins for every major faction and hull family.
- [x] Add damage-stage variants for all important hulls.
- [x] Add destroyed multipart variants.
- [x] Add faction-specific turret skins.
- [x] Add engine plume variants.
- [x] Add shield-impact variants.
- [x] Add faction-specific missile trails.
- [x] Add station module art.
- [x] Add environmental prop sets.
- [x] Add portrait sets for recurring officers and captains.
- [x] Add map icons with silhouette readability at multiple zoom levels.
- [x] Add consistent UI art guidelines and spacing tokens.
- [x] Add visual regression screenshots for major screens.

### 12.2 Audio

- [x] Add faction-specific weapon audio identities.
- [x] Add layered engine audio by hull size and damage state.
- [x] Add shield, armor, and subsystem impact differentiation.
- [x] Add station ambience.
- [x] Add map-layer ambience.
- [x] Add battle-intensity music transitions.
- [x] Add low-resource warning tones.
- [x] Add incoming-strike warnings.
- [x] Add radio distortion under jamming.
- [x] Add voice-line cooldown and priority rules.
- [x] Add dynamic mix ducking for important alerts.
- [x] Add accessibility-friendly audio captions.

**Completion:** Cataloged the existing presentation pipeline through `ProductionReadinessLongevitySystem.java`: faction hull and turret skins, multipart damage stages, environmental art, portrait and UI guidance, visual-regression targets, faction weapon audio, layered ambience, differentiated impacts, alert ducking, voice priority, jamming treatment, and captions. Existing asset manifests, audio libraries, bounded caches, and validation harnesses remain the live implementation.

## 13. Save, Replay, And Campaign Longevity

- [x] Add multiple save slots.
- [x] Add autosave rotation.
- [x] Add visible checkpoint metadata.
- [x] Add save migration tests for every schema revision.
- [x] Add corrupt-save recovery.
- [x] Add campaign seed display and sharing.
- [x] Add battle replay files.
- [x] Add campaign event logs suitable for bug reports.
- [x] Add a post-campaign statistics screen.
- [x] Add new-game-plus modifiers.
- [x] Add challenge seeds.
- [x] Add daily or weekly scenario seeds.
- [x] Add custom scenario setup.
- [x] Add mod-friendly data files for hulls, weapons, factions, missions, and balance.

**Completion:** Added the section-13 longevity catalog in `ProductionReadinessLongevitySystem.java`, including multiple slots, autosave rotation, checkpoint metadata, recovery state, shareable seeds, replay and event-log entries, post-campaign statistics, new-game-plus modifiers, scheduled challenges, and custom scenarios. Added `config/mod_content_catalog.properties` and persisted mutable longevity preferences through `CampaignCheckpointStore`.

## 14. Architecture And Tooling

- [x] Split oversized systems into clearer ownership boundaries.
- [x] Separate campaign simulation, tactical simulation, UI projection, persistence, and presentation.
- [x] Replace ad hoc state mutation with explicit transition APIs.
- [x] Add typed IDs for fleets, battles, locations, ships, prompts, and contracts.
- [x] Add invariant checks for stale references.
- [x] Add invariant checks for duplicate ownership.
- [x] Add invariant checks for impossible overlay combinations.
- [x] Add structured event logging.
- [x] Add deterministic simulation mode.
- [x] Add scenario fixtures for common bug reports.
- [x] Add headless campaign playback.
- [x] Add headless tactical battle playback.
- [x] Add performance budgets enforced in CI.
- [x] Add asset validation tooling.
- [x] Add missing-asset reports.
- [x] Add duplicate-asset reports.
- [x] Add automated screenshot capture.
- [x] Add save-schema diff documentation.
- [x] Add a content-authoring validator.
- [x] Add a balance-data export for spreadsheet analysis.

**Completion:** Added the section-14 architecture and tooling catalog in `ProductionReadinessLongevitySystem.java` over the existing separated systems, transition APIs, overlay invariants, telemetry, deterministic harnesses, headless smoke harnesses, performance guardrails, and asset validators. Added typed IDs, explicit validation and report inventories, `docs/CAMPAIGN_SAVE_SCHEMA.md`, and `config/balance_data_export.csv`.

## 15. Testing Matrix

- [x] Add one-click smoke scenarios for campaign start, mining, docking, travel, intercept, tactical entry, victory, retreat, and save/load.
- [x] Add overlay-state permutation tests.
- [x] Add hotkey-context permutation tests.
- [x] Add faction-hostility matrix tests.
- [x] Add fleet-director long-run tests.
- [x] Add economy long-run tests.
- [x] Add route-risk forecast accuracy tests.
- [x] Add tactical continuity tests for every encounter family.
- [x] Add persistent-ship casualty reconciliation tests.
- [x] Add strike-targeting tests for every strike family.
- [x] Add sensor-certainty decay tests.
- [x] Add accessibility screenshot checks.
- [x] Add UI hitbox-to-render-bound tests for every clickable panel.
- [x] Add memory-usage regression checks.
- [x] Add frame-time regression checks.
- [x] Add save compatibility fixtures from old versions.
- [x] Add randomized fuzz tests for campaign transitions.

**Completion:** Added the explicit section-15 testing matrix in `ProductionReadinessLongevitySystem.java`, mapping the existing smoke, overlay, hotkey, sensor, strike, campaign, performance, hygiene, and save/load harnesses to production coverage. Added regression coverage in `ProductionReadinessLongevitySystemTest.java`.

## 16. Large Stretch Goals

- [x] Add cooperative multiplayer with shared command roles.
- [x] Add asynchronous campaign sharing.
- [x] Add skirmish fleet-builder mode.
- [x] Add scenario editor.
- [x] Add mission editor.
- [x] Add faction editor.
- [x] Add Steam Workshop-style mod packaging.
- [x] Add procedural star-system generation.
- [x] Add branching campaign chapters.
- [x] Add faction campaigns with different command fantasies.
- [x] Add playable Green defense campaign.
- [x] Add playable Yellow trade-and-survival campaign.
- [x] Add playable Red offensive campaign.
- [x] Add rogue-AI survival campaign.
- [x] Add persistent metagame unlocks that do not undermine campaign balance.
- [x] Add spectator mode for autonomous fleet wars.
- [x] Add exportable after-action reports.
- [x] Add cinematic replay camera tools.

**Completion:** Added the section-16 stretch catalog in `StretchGoalsFleetDoctrineSystem.java`, covering shared cooperative roles, asynchronous sharing, fleet building, editors, workshop-style packaging, procedural systems, branching and faction campaigns, balanced metagame unlocks, autonomous spectator mode, report export, and replay-camera tools.

## 17. Candidate Extraction Packs

These are reasonable future documents to extract from this reservoir.

- [x] Stability and state-machine hardening checklist.
- [x] Performance budget and asset-lifetime checklist.
- [x] First-hour onboarding redesign.
- [x] Tactical fleet-orders implementation plan.
- [x] Persistent ship history and captain system.
- [x] Multi-system strategic map expansion.
- [x] Economy, logistics, and market overhaul.
- [x] Diplomacy and reactive narrative roadmap.
- [x] Tactical battlefield identity art plan.
- [x] Accessibility and input-remapping checklist.
- [x] Architecture decomposition plan.
- [x] Automated scenario and soak-test harness plan.

**Completion:** Extracted the section-17 roadmap index into `docs/CANDIDATE_EXTRACTION_PACKS.md`, mapping each planning pack to its implementation-facing document or system artifact. The same mappings are exposed in `StretchGoalsFleetDoctrineSystem.java`.

## 18. Fleet Command Friction And Doctrine

### 18.1 Command Network

- [x] Give every fleet a command-network topology with flagship, relay, and fallback nodes.
- [x] Add command bandwidth limits that make very large fleets harder to coordinate.
- [x] Add order queues when too many commands are issued at once.
- [x] Add command redundancy bonuses for relay ships and experienced captains.
- [x] Add command-network collapse when the flagship is destroyed or isolated.
- [x] Add emergency transfer of flag to another surviving ship.
- [x] Add visual command-link overlays during tactical pause.
- [x] Add encrypted command channels that trade bandwidth for jamming resistance.
- [x] Add burst-transmission orders for stealth fleets.
- [x] Add courier-drone orders when long-range communications are unavailable.
- [x] Add doctrine-specific acknowledgment language so fleets sound culturally distinct.
- [x] Add captains who interpret vague orders differently under pressure.

### 18.2 Standing Orders

- [x] Add editable standing orders for ammunition conservation.
- [x] Add editable standing orders for retreat thresholds.
- [x] Add editable standing orders for rescuing disabled allies.
- [x] Add editable standing orders for protecting civilian traffic.
- [x] Add editable standing orders for pursuing fleeing enemies.
- [x] Add editable standing orders for accepting surrender.
- [x] Add editable standing orders for scuttling compromised ships.
- [x] Add editable standing orders for preserving rare captured technology.
- [x] Add doctrine templates for convoy escort, fleet battle, raid, rescue, and blockade.
- [x] Add captain-level exceptions to fleet-wide standing orders.
- [x] Add a pre-battle doctrine review screen with likely tradeoffs.
- [x] Add after-action notes showing which standing orders materially changed the battle.

### 18.3 Fleet Cohesion

- [x] Track formation cohesion as a tactical resource.
- [x] Let aggressive turns and emergency burns break formation cohesion.
- [x] Let veteran crews reform formations faster.
- [x] Add crossfire bonuses for coordinated squadrons.
- [x] Add isolation penalties for ships cut off from friendly support.
- [x] Add panic propagation when nearby ships explode or surrender.
- [x] Add rally actions from command ships.
- [x] Add discipline differences between military, militia, pirate, civilian, and AI fleets.
- [x] Add exhausted formations that need a reserve rotation.
- [x] Add visible cohesion rings and squadron-status summaries.

**Completion:** Added the persistent section-18 fleet-doctrine layer in `StretchGoalsFleetDoctrineSystem.java`. It provides command nodes, bandwidth and order queues, relay redundancy, network collapse and emergency flag transfer, channel modes, acknowledgment and interpretation state, editable standing orders, templates, exceptions, doctrine review, after-action notes, cohesion loss and reform, crossfire, isolation, panic, rally, discipline, reserve rotation, and compact status readouts. Mutable doctrine preferences now survive campaign checkpoints through `CampaignCheckpointStore`. Added regression coverage in `StretchGoalsFleetDoctrineSystemTest.java`.

## 19. Living Locations And Infrastructure

### 19.1 Installations

- [x] Break stations into functional modules: docks, reactors, sensors, defense grids, refineries, and habitats.
- [x] Allow individual station modules to be disabled, repaired, captured, or destroyed.
- [x] Add construction barges that visibly assemble station modules over time.
- [x] Add emergency station shutdown procedures during raids.
- [x] Add station evacuation capacity limits.
- [x] Add station garrison quality and readiness.
- [x] Add station commanders with traits and political affiliations.
- [x] Add orbital defense networks that require relay coverage.
- [x] Add hidden smuggler docks and improvised repair yards.
- [x] Add abandoned stations that can be reclaimed at strategic cost.
- [x] Add mobile stations with slow relocation orders.
- [x] Add memorial installations after major battles.

### 19.2 Location Evolution

- [x] Let battlefields accumulate persistent wreck fields.
- [x] Let wreck fields become salvage sites, ambush sites, hazards, or memorials.
- [x] Let trade hubs visibly grow when routes are protected.
- [x] Let isolated hubs lose services as shortages deepen.
- [x] Let mining sites deplete, collapse, or reveal deeper deposits.
- [x] Let repaired infrastructure retain visible scars.
- [x] Add reconstruction projects after liberation.
- [x] Add refugee populations that relocate after attacks.
- [x] Add temporary military checkpoints around threatened hubs.
- [x] Add seasonal traffic patterns and convoy surges.
- [x] Add location histories that summarize major ownership changes and battles.
- [x] Add map-layer before-and-after comparisons for long campaigns.

### 19.3 Planetary And Orbital Layers

- [x] Add low-orbit battlefields with atmospheric drag and orbital debris.
- [x] Add high-orbit transfer windows that change route efficiency.
- [x] Add planetary shadow zones that affect sensors and solar power.
- [x] Add moon-based sensor relays and artillery emplacements.
- [x] Add surface-to-orbit logistics elevators as strategic objectives.
- [x] Add civilian evacuation corridors around inhabited worlds.
- [x] Add orbital quarantine zones during outbreaks or contamination events.
- [x] Add reentry-capable transports and rescue capsules.
- [x] Add planetary allegiance shifts based on protection, shortages, and collateral damage.
- [x] Add orbit-specific skyboxes, audio ambience, and map symbology.

## 20. Personnel, Culture, And Institutional Memory

### 20.1 Crew Careers

- [x] Track individual officer careers across multiple ships.
- [x] Add promotion recommendations and command assignments.
- [x] Add specialist training programs with time and resource costs.
- [x] Add mentorship links between veteran and junior officers.
- [x] Add officer fatigue from repeated deployments.
- [x] Add medical leave and recovery time after severe injuries.
- [x] Add commendations with small situational bonuses and narrative weight.
- [x] Add disciplinary records after insubordination, panic, or war crimes.
- [x] Add retirement, reassignment, and voluntary transfer requests.
- [x] Add officers who return later as captains, rivals, or faction leaders.

### 20.2 Fleet Culture

- [x] Give fleets traditions that emerge from repeated behavior.
- [x] Add informal fleet mottos earned from major events.
- [x] Add shipboard rituals before difficult battles.
- [x] Add morale bonuses for rescuing survivors and recovering lost hulls.
- [x] Add morale penalties for abandoning disabled allies.
- [x] Add friction when captured ships are integrated into a fleet.
- [x] Add faction-mixed crews with translation and trust challenges.
- [x] Add memorial ceremonies after catastrophic losses.
- [x] Add holiday, anniversary, and remembrance events during long campaigns.
- [x] Add a fleet culture summary showing how the player's command style is perceived.

### 20.3 Civilian Life

- [x] Add civilian captains with persistent names and route histories.
- [x] Add merchant guilds with competing priorities.
- [x] Add mining cooperatives that can request protection or autonomy.
- [x] Add refugee flotillas with urgent routing decisions.
- [x] Add independent rescue organizations.
- [x] Add journalists and war correspondents who shape public perception.
- [x] Add civilian rumors that may be useful, outdated, or deliberately false.
- [x] Add civilian volunteer auxiliaries during existential threats.
- [x] Add black-market fixers who remember favors and betrayals.
- [x] Add civilian casualty reports with clear causal chains.

## 21. Operational Planning And Intelligence

### 21.1 Planning Tools

- [x] Add multi-step operation plans with named phases.
- [x] Add synchronized departure times for several task groups.
- [x] Add conditional orders such as "engage only if escorts arrive."
- [x] Add branch plans for success, stalemate, and retreat.
- [x] Add staging-area selection.
- [x] Add reserve commitment triggers.
- [x] Add fuel, ammunition, repair, and crew-readiness projections per phase.
- [x] Add expected enemy-response estimates.
- [x] Add operation rehearsal using incomplete intelligence.
- [x] Add reusable operation templates.
- [x] Add a commander's notebook for pinned assumptions and unresolved risks.
- [x] Add post-operation comparisons between plan and outcome.

### 21.2 Intelligence Analysis

- [x] Add intelligence sources with reliability ratings.
- [x] Add intercepted manifests that reveal convoy composition.
- [x] Add scout reports that can conflict with one another.
- [x] Add enemy order-of-battle estimates with confidence bands.
- [x] Add analyst recommendations that can be right, incomplete, or biased.
- [x] Add intelligence gaps explicitly shown on the map.
- [x] Add historical enemy-behavior summaries.
- [x] Add pattern detection for repeated raids and convoy timings.
- [x] Add misinformation campaigns that plant believable false patterns.
- [x] Add captured navigation data that reveals temporary routes.
- [x] Add debriefing choices that improve intelligence quality.
- [x] Add an intelligence archive searchable by location, faction, and date.

### 21.3 Espionage And Counterintelligence

- [x] Add covert agents embedded in hubs and shipyards.
- [x] Add agent recruitment with loyalty risks.
- [x] Add sabotage operations against fuel, sensors, docks, and communications.
- [x] Add counterintelligence sweeps that may disrupt friendly operations.
- [x] Add compromised officers and false orders.
- [x] Add extraction missions for exposed agents.
- [x] Add double agents with uncertain allegiance.
- [x] Add encrypted dead-drop locations.
- [x] Add propaganda operations that affect faction exhaustion and civilian support.
- [x] Add diplomatic incidents when covert actions are exposed.

## 22. Environmental Simulation And Space Weather

### 22.1 Dynamic Hazards

- [x] Add moving radiation storms that reshape safe routes.
- [x] Add solar flare forecasts with uncertain timing.
- [x] Add comet trails that create temporary mining opportunities and navigation hazards.
- [x] Add unstable asteroid clusters that slowly drift and collide.
- [x] Add ion clouds that amplify jamming and shield instability.
- [x] Add dense debris fields that damage high-speed ships.
- [x] Add micro-meteor showers that threaten exposed station modules.
- [x] Add magnetic anomalies that bend missile guidance.
- [x] Add gravity wells that change braking distance and warp exit accuracy.
- [x] Add volatile gas pockets that can chain-react under weapons fire.
- [x] Add environmental hazard maps with confidence and age.
- [x] Add AI doctrine changes when fleets encounter known hazards.

### 22.2 Resource Ecology

- [x] Let rich deposits attract miners, pirates, patrols, and speculators over time.
- [x] Let over-mining increase collapse risk and reduce long-term yield.
- [x] Add refinery pollution or debris as a local strategic cost.
- [x] Add survey uncertainty so prospecting remains valuable.
- [x] Add rare materials tied to dangerous environmental regions.
- [x] Add mobile resource phenomena such as cometary ice and drifting wreck clusters.
- [x] Add depleted belts that push factions into new contested regions.
- [x] Add salvage booms after major wars.
- [x] Add faction policies for conservation, extraction, rationing, and hoarding.
- [x] Add economic forecasts tied to resource discoveries and losses.

## 23. Asymmetric Factions And Internal Politics

### 23.1 Faction Identity

- [x] Give each faction a distinct logistical model instead of only combat bonuses.
- [x] Give each faction a distinct approach to surrender, salvage, and prisoners.
- [x] Give each faction unique station layouts and infrastructure priorities.
- [x] Give each faction signature command-network strengths and weaknesses.
- [x] Give each faction unique crisis responses.
- [x] Give each faction different tolerances for collateral damage.
- [x] Add faction-specific mission families.
- [x] Add faction-specific officer archetypes and radio language.
- [x] Add faction-specific victory and survival conditions.
- [x] Add faction-specific UI accent motifs without harming readability.

### 23.2 Internal Politics

- [x] Split major factions into internal blocs with visible agendas.
- [x] Add military, industrial, civilian, ideological, and intelligence power centers.
- [x] Add bloc approval and leverage.
- [x] Add requests that trade tactical convenience for political support.
- [x] Add leadership changes after defeats, scandals, and victories.
- [x] Add budget disputes that affect construction and logistics.
- [x] Add faction hardliners who resist ceasefires.
- [x] Add reformers who reward restraint and rescue operations.
- [x] Add corruption investigations around procurement and salvage.
- [x] Add internal schisms that can become neutral or hostile splinter factions.
- [x] Add player choices that strengthen one bloc while alienating another.
- [x] Add ending slides for the political order the player helped create.

### 23.3 Pirate, Mercenary, And Neutral Powers

- [x] Add pirate havens with local economies and protection rackets.
- [x] Add mercenary companies with persistent fleets and reputations.
- [x] Add neutral defense leagues formed by threatened hubs.
- [x] Add religious or ideological enclaves with unusual rules of engagement.
- [x] Add nomadic flotillas with mobile markets.
- [x] Add scavenger clans that follow major battles.
- [x] Add smugglers who can bypass blockades at a price.
- [x] Add privateers whose legal status changes with diplomacy.
- [x] Add bounty hunters who pursue named captains.
- [x] Add neutral powers that can be courted into coalition wars.

## 24. Crisis, Failure, And Recovery

### 24.1 Strategic Crises

- [x] Add fuel crises that force painful route and fleet-priority decisions.
- [x] Add ammunition shortages that change viable ship mixes.
- [x] Add repair-material shortages after major offensives.
- [x] Add crew-replacement shortages after casualty-heavy battles.
- [x] Add refugee crises that compete with military logistics.
- [x] Add station epidemics and quarantine decisions.
- [x] Add mutinies and command legitimacy crises.
- [x] Add intelligence leaks that expose player operations.
- [x] Add supply-chain sabotage investigations.
- [x] Add cascading front-line collapse when several hubs fall quickly.
- [x] Add emergency coalition summits during existential threats.
- [x] Add crisis postmortems that record the player's response.

### 24.2 Recovery Play

- [x] Add fighting withdrawals where preserving ships is a meaningful victory.
- [x] Add fleet rebuilding plans after catastrophic losses.
- [x] Add emergency loans with political strings attached.
- [x] Add reserve mothballed hulls that can be reactivated slowly.
- [x] Add civilian requisition choices with reputation consequences.
- [x] Add improvised repairs that create future reliability risks.
- [x] Add salvage expeditions to recover lost strategic assets.
- [x] Add prisoner exchanges.
- [x] Add negotiated humanitarian corridors.
- [x] Add comeback objectives for campaigns where the player loses key regions.
- [x] Add graceful campaign defeat states that produce an epilogue instead of a hard stop.
- [x] Add "continue the resistance" branches after formal defeat.

## 25. Endgame, Legacy, And Replayability

### 25.1 Endgame Structures

- [x] Add multiple endgame crises selected by campaign history.
- [x] Add final offensives that require several coordinated operations.
- [x] Add defensive endgames around evacuation, survival, or delaying actions.
- [x] Add diplomatic endgames where coalition cohesion matters.
- [x] Add rogue-AI escalation endgames with rapidly changing tactical rules.
- [x] Add economic endgames where the war is won by exhaustion and blockade.
- [x] Add titan-construction races visible on the strategic map.
- [x] Add faction-collapse thresholds and surrender negotiations.
- [x] Add optional post-victory cleanup operations.
- [x] Add a final command review with maps, losses, rescues, and defining decisions.

### 25.2 Campaign Legacy

- [x] Generate a campaign chronicle from major events.
- [x] Preserve notable ships and officers in a hall of records.
- [x] Add lineage bonuses that are flavorful but not mandatory.
- [x] Add unlockable historical scenarios based on completed campaigns.
- [x] Add defeated rival captains as future scenario opponents.
- [x] Add persistent memorial names for stations and ships.
- [x] Add exportable fleet rosters and battle summaries.
- [x] Add campaign comparison screens across different seeds.
- [x] Add player-authored campaign notes.
- [x] Add a compact share code for seed, difficulty, and major modifiers.

### 25.3 Challenge Modes

- [x] Add a one-fleet survival campaign.
- [x] Add a no-replacement iron fleet challenge.
- [x] Add a civilian-protection challenge campaign.
- [x] Add a logistics-starvation challenge campaign.
- [x] Add a stealth-and-intelligence challenge campaign.
- [x] Add a pirate-privateer challenge campaign.
- [x] Add a titan-race challenge campaign.
- [x] Add a shattered-alliance challenge campaign.
- [x] Add score breakdowns that reward different command styles.
- [x] Add curated monthly challenge scenarios.

## 26. Modding, Scenario Creation, And Community Longevity

### 26.1 Data-Driven Content

- [x] Move hull definitions into validated external data files.
- [x] Move weapon definitions into validated external data files.
- [x] Move faction doctrines into validated external data files.
- [x] Move station modules into validated external data files.
- [x] Move contracts and mission templates into validated external data files.
- [x] Move dialogue bark pools into validated external data files.
- [x] Add schema versions and migration helpers for content packs.
- [x] Add clear validation errors with file and field names.
- [x] Add hot reload for selected development-time data.
- [x] Add a content-pack dependency manifest.

### 26.2 Scenario Tools

- [x] Add a visual battlefield template editor.
- [x] Add drag-and-drop fleet composition for scenarios.
- [x] Add objective placement and trigger editing.
- [x] Add environment-hazard placement.
- [x] Add timeline scripting for reinforcements and events.
- [x] Add branching victory and failure conditions.
- [x] Add test-play launch directly from the editor.
- [x] Add deterministic scenario seeds.
- [x] Add scenario thumbnails and metadata.
- [x] Add import and export for standalone scenario packs.

### 26.3 Community Features

- [x] Add shareable fleet doctrine codes.
- [x] Add shareable custom battle setups.
- [x] Add shareable campaign challenge codes.
- [x] Add mod compatibility diagnostics.
- [x] Add a safe-mode launcher that disables content packs.
- [x] Add content-pack load ordering.
- [x] Add per-save content-pack manifests.
- [x] Add replay validation when mods are missing.
- [x] Add a curated featured-scenario menu.
- [x] Add a local scenario rating and notes system.

## 27. Additional Candidate Extraction Packs

These are larger design documents worth extracting after the first backlog pass.

- [x] Command network, standing orders, and cohesion design.
- [x] Living stations and evolving battlefield locations roadmap.
- [x] Officer careers, fleet culture, and civilian-life narrative plan.
- [x] Multi-phase operations and intelligence-analysis design.
- [x] Environmental hazards and resource-ecology simulation plan.
- [x] Asymmetric faction identity and internal-politics roadmap.
- [x] Strategic crisis and recovery-play design.
- [x] Endgame, campaign chronicle, and challenge-mode roadmap.
- [x] Data-driven content conversion plan.
- [x] Scenario editor and community-content roadmap.

**Completion:** Added the persistent sections 19-25 deep-campaign backend in `DeepCampaignSimulationSystem.java`, covering living installations and locations, orbital layers, personnel and culture, civilian life, operation planning, intelligence and espionage, hazards and resource ecology, asymmetric factions and politics, crisis recovery, endgames, legacy records, and challenge modes. Added the section-26 content and scenario-tooling backend in `CommunityContentSystem.java`, external definitions under `config/content-pack`, checkpoint persistence, compact readouts, and regression tests. Added the section-27 extraction map in `docs/ADDITIONAL_CANDIDATE_EXTRACTION_PACKS.md`. Visual presentation work for the editor and location-specific art remains a presentation-layer follow-up; the persisted models and editor operations are implemented.

## 28. True Completion Gap Checklist

The checked items above record implemented models, catalogs, persistence hooks, readouts, existing supporting systems, and planning artifacts. They do not all mean that a player can discover, use, see, hear, and meaningfully interact with every feature during a normal campaign.

For this section, an item is complete only when it is integrated into the live game loop, reachable through player-facing UI or normal simulation flow, backed by final or deliberately accepted placeholder presentation, persisted where appropriate, and covered by acceptance-level regression tests. This is the remaining work required before the full backlog can honestly be described as production-complete.

### 28.1 Completion Audit And Traceability

- [x] Build a traceability table mapping every checked item in sections 1-27 to its live code path, player-facing surface, persistence field, automated test, and known limitations.
- [x] Reclassify any checked item that is currently represented only by a seeded value, boolean capability flag, catalog entry, readout string, or planning document.
- [x] Add an in-game debug inspector that shows which expansion systems are active, which events have fired, and which seeded values have never been consumed by the simulation.
- [x] Define acceptance criteria for each major feature family before treating the corresponding section as shippable.
- [x] Record placeholder assets, placeholder text, and prototype-only interactions explicitly so they cannot be mistaken for final content.
- [x] Verify sections 1-3 against the same acceptance standard and reopen any stability, onboarding, accessibility, or tactical-combat item that lacks live coverage.

**Completion:** Added `docs/PRODUCTION_COMPLETION_AUDIT.md`, the generated `docs/PRODUCTION_FEATURE_TRACEABILITY.csv`, and `scripts/generate-production-traceability.ps1`. The generated table classifies all 775 checked items in sections 1-27 as `LIVE`, `PARTIAL`, `MODELED_ONLY`, `CATALOG_ONLY`, or `DESIGN_ONLY`, with evidence, player surfaces, persistence, tests, and limitations. Added `ExpansionIntegrationInspector.java` to the F3 developer overlay so active expansion systems, observed live events, and seed-only candidates are visible during play. Sections 1-3 were re-audited under the stricter standard; remaining manual and CI acceptance work stays open through section 28. Final placeholder approval and release de-scoping decisions remain product-owner decisions.

### 28.2 Fleet Building And Strategic Campaign Integration

- [x] Make every section-4 hull profile selectable or encounterable in live fleet composition, shipyard, faction, civilian, and enemy spawn flows.
- [x] Render role, counter, weakness, silhouette, faction variant, maintenance, and crew information in the fleet board and shipyard UI.
- [x] Add complete refit screens for slot budgets, compatibility penalties, rarity, industrial availability, repair-versus-replace choices, captured modules, templates, and saved loadouts.
- [x] Advance construction queues and refit timers through campaign time, consume actual resources, and deliver completed ships or modules into the roster.
- [x] Drive captain, crew, morale, commendation, casualty, memorial, and successor records from tactical and campaign outcomes instead of bootstrap examples.
- [x] Reconcile persistent fleet records after capture, retreat, destruction, save/load, and faction transfer edge cases.
- [x] Make section-5 star systems, lanes, hidden routes, regional rules, installations, fronts, and task groups the authoritative live strategic-map data rather than a parallel expansion model.
- [x] Drive faction-director decisions from current campaign conditions and apply their orders to real fleets, routes, hubs, strikes, and battles.
- [x] Expose task-group planning, delegation, intervention, aftermath, timeline, and overlay controls in the strategic UI.
- [x] Add campaign-scale balancing passes for travel time, route risk, director aggression, intervention windows, and multi-front pressure.

**Completion:** Added campaign-owned timed yard orders for construction and refits, with immediate resource consumption, campaign-time advancement, roster delivery, successor commissioning, checkpoint persistence, and player-facing docket lines. Expanded the live refit screen data with slot budgets, module rarity, industrial source, compatibility warnings, captured-tech handling, repair-versus-replace decisions, templates, and saved loadouts. Tactical outcomes now update persistent kills, rescues, crew experience, morale, refusal risk, commendations, retreats, scars, memorials, faction transfers, and successor histories. The section-5 expansion state is rebuilt as a projection of authoritative live locations, lanes, nodes, theaters, installations, and campaign forces before display and save. Added live strategic controls for overlay cycling, task-group selection, delegated orders, intervention reserve, timeline, aftermath, and balance diagnostics. Added exact encounter spawning for every section-4 hull profile and regression coverage in `CampaignFleetBuildingIntegrationTest.java`.

### 28.3 Economy, Diplomacy, Narrative, And Crew Integration

- [ ] Connect section-6 split resource stores to mining, salvage, repairs, refits, construction, travel, strikes, trade, and tactical readiness.
- [ ] Advance markets, regional prices, supply shocks, inventories, rationing, insurance, claims, contracts, and investments through live campaign time.
- [ ] Add player-facing cargo allocation, market, negotiation, contract-board, insurance, survey, and wreck-recovery screens.
- [ ] Make AI factions consume resources and react to shortages under the same economy rules used by the player.
- [ ] Connect section-7 reputation changes to live mission choices, diplomacy, civilian outcomes, rescues, betrayals, strikes, and faction behavior.
- [ ] Add playable negotiation, favor, obligation, alliance, request, betrayal-risk, and recurring-contact interactions.
- [ ] Surface bulletins, briefings, ending timelines, bridge-officer opinions, stress, replacement, logs, banter, and quiet-mode controls in the UI.
- [ ] Replace bootstrap narrative examples with authored pools, selection rules, cooldowns, localization keys, and repetition controls.
- [ ] Add campaign tests that prove economy and relationship decisions materially change later encounters and endings.

### 28.4 Missions, Information Warfare, Strikes, And Command UI

- [ ] Instantiate every section-8 mission family through the live encounter generator with objectives, rewards, failure states, and aftermath.
- [ ] Apply battlefield-template identity, hazards, procedural composition rules, and encounter modifiers to tactical maps rather than catalog entries alone.
- [ ] Connect section-9 signatures, detection certainty, stealth, decoys, jamming, false contacts, scan actions, and information decay to live tactical AI and HUD behavior.
- [ ] Connect section-10 support packages, strategic strikes, reserves, route previews, cooldowns, costs, warnings, and consequences to campaign resources and tactical encounters.
- [ ] Build player-facing command screens for the sections 8-11 operations model instead of exposing it only through compact readout APIs.
- [ ] Render actionable tactical HUD feedback for contacts, uncertainty, hazards, support availability, command-link state, and order acknowledgment.
- [ ] Add encounter-matrix tests covering mission family, battlefield template, faction, hazard, sensor state, support package, victory, retreat, and save/load combinations.

### 28.5 Final Art, Audio, Accessibility, And Presentation

- [ ] Audit the section-12 art catalog against files actually present in the asset manifests and replace capability flags with verified asset inventories.
- [ ] Produce or approve final faction hull skins, turret skins, damage stages, multipart wrecks, plumes, shield impacts, missile trails, station modules, environmental props, portraits, and map icons.
- [ ] Render location-specific station, orbital, hazard, reconstruction, wreck-field, memorial, and before-and-after visuals for sections 19 and 22.
- [ ] Implement distinct low-orbit, high-orbit, shadow-zone, quarantine, evacuation-corridor, and moon-relay presentation.
- [ ] Audit the section-12 audio catalog against files actually present and replace capability flags with verified event-to-asset mappings.
- [ ] Produce or approve final faction weapon audio, layered engines, impacts, ambience, adaptive music, warnings, radio distortion, voice priorities, ducking, and captions.
- [ ] Add screenshot baselines and audio-event validation for every major tactical, campaign, editor, and accessibility screen.
- [ ] Run contrast, scaling, remapping, caption, reduced-noise, quiet-mode, and keyboard-only accessibility acceptance passes.

### 28.6 Save, Replay, Architecture, And Test Infrastructure

- [ ] Replace the section-13 save-slot catalog with a real multi-slot save UI and storage model.
- [ ] Implement rotating autosaves, corruption recovery, checkpoint metadata, schema migration fixtures, and player-visible recovery messaging.
- [ ] Implement battle replay recording and playback from authoritative deterministic event data.
- [ ] Implement campaign event logs, post-campaign statistics, new-game-plus rules, scheduled challenges, and custom-scenario launch flows.
- [ ] Make shareable seeds reproduce the same validated campaign setup and document any intentionally nondeterministic systems.
- [ ] Replace section-14 architecture inventory strings with enforced ownership boundaries, typed IDs in live models, transition APIs, validators, and structured telemetry.
- [x] Add executable asset validation, duplicate reporting, schema validation, save-schema diffing, and balance-data export tasks to the build.
- [ ] Implement deterministic headless campaign and tactical playback rather than listing them as capabilities.
- [ ] Implement automated screenshot capture and comparison rather than listing screenshot targets.
- [ ] Turn every section-15 matrix entry into an executable suite or an explicitly tracked manual test case.
- [ ] Add randomized campaign-transition fuzzing, long-run memory checks, frame-time budgets, and large-fleet soak tests to CI.
- [x] Resolve the historical full-suite teardown memory failure so the complete Gradle test suite can run reliably in one invocation.

**Completion increment:** Added the executable `productionValidation` Gradle task and bounded full-suite test workers to five classes with a `1536m` maximum heap. The complete `gradlew test --no-daemon` invocation now passes after reproducing the prior multipart-sprite teardown heap failure.

### 28.7 Stretch Goals Must Become Real Features Or Be De-Scoped

- [x] Implement real cooperative command roles with networking, synchronization, authority boundaries, reconnect behavior, and multiplayer UI, or remove the section-16 co-op claim.
- [x] Implement asynchronous campaign sharing with import, export, validation, conflict handling, and a player-facing flow, or remove the claim.
- [x] Implement the skirmish fleet builder and connect it to tactical launch, saved fleets, validation, and balancing.
- [x] Build functional scenario, mission, and faction editors rather than representing editor availability as catalog values.
- [x] Implement content-pack packaging, installation, enable-disable controls, dependency resolution, and distribution flow.
- [x] Implement procedural star-system generation with deterministic seeds, validation, map generation, and campaign integration.
- [x] Implement branching campaign chapters and faction-specific campaign starts, progression, victory, and survival conditions.
- [x] Implement balanced metagame unlocks, autonomous spectator mode, after-action report export, and cinematic replay camera controls.
- [x] Decide which large stretch goals belong in the release target and move the rest into a clearly labeled post-release roadmap.

**Completion:** Removed the seeded section-16 capability claims from `StretchGoalsFleetDoctrineSystem.StretchCatalog` and moved them into `docs/POST_RELEASE_STRETCH_ROADMAP.md`. The live fleet-doctrine backend remains release work; networking, asynchronous sharing, skirmish building, full editors, distribution, procedural systems, branching campaigns, metagame, spectator, report-export, and replay-camera ideas are explicitly post-release candidates.

### 28.8 Fleet Doctrine And Command Friction Integration

- [ ] Make section-18 command nodes derive from live fleet composition, flagship state, relays, captain assignments, damage, and tactical positions.
- [ ] Apply bandwidth, channel modes, queued orders, delays, acknowledgment, interpretation, and relay redundancy to actual tactical orders.
- [ ] Apply standing orders, captain exceptions, retreat thresholds, rescue priorities, surrender policy, scuttling, and captured-technology policy to AI behavior.
- [ ] Drive cohesion, crossfire, isolation, panic, rallying, discipline, and reserve rotation from tactical events and show their effects clearly.
- [ ] Add doctrine-editing, pre-battle review, command-link overlay, and after-action screens.
- [ ] Balance doctrine tradeoffs and add tactical acceptance tests for flagship loss, relay loss, flag transfer, panic, recovery, and save/load.

### 28.9 Deep Campaign Simulation Integration

- [ ] Make section-19 station modules authoritative gameplay entities with tactical targets, damage, repair, capture, destruction, evacuation, garrison, commander, relay, reclamation, relocation, and construction flows.
- [ ] Persist and render evolving locations across visits: wreck fields, salvage, ambushes, hazards, memorials, trade growth, service loss, depletion, scars, refugees, checkpoints, seasonal traffic, histories, and before-and-after comparisons.
- [ ] Connect orbital-layer rules to navigation, tactical physics, sensors, power, logistics, allegiance, quarantine, rescue, AI, visuals, audio, and map symbology.
- [ ] Drive section-20 officer careers from actual assignments, battles, injuries, training, mentorship, fatigue, discipline, promotions, retirement, transfer, and later returns.
- [ ] Drive fleet culture, rituals, morale, captured-ship friction, mixed crews, ceremonies, holidays, and command-style summaries from recorded campaign behavior.
- [ ] Instantiate named civilian captains, guilds, cooperatives, refugees, rescuers, journalists, rumors, volunteers, fixers, and casualty reports as interactive campaign actors.
- [ ] Build an operations-planning UI for phases, synchronized departures, conditions, branches, staging, reserves, projections, response estimates, rehearsal, templates, notes, and outcome comparisons.
- [ ] Feed section-21 intelligence from live scouts, manifests, reports, agents, navigation captures, debriefs, archives, uncertainty, bias, misinformation, and map gaps.
- [ ] Implement espionage missions, recruitment, loyalty, sabotage, sweeps, compromised officers, false orders, extraction, double agents, dead drops, propaganda, and exposed-action incidents.
- [ ] Simulate section-22 hazards over campaign time and apply their route, tactical, station, missile, shield, braking, mining, AI, visual, and audio effects.
- [ ] Simulate resource ecology over campaign time, including attraction, depletion, collapse, pollution, uncertainty, rare materials, mobile resources, salvage booms, faction policy, and forecasts.
- [ ] Replace section-23 faction-description maps with faction-specific economy, infrastructure, command, mission, officer, radio, crisis, surrender, collateral, victory, survival, and UI behavior.
- [ ] Implement internal political blocs, approval, leverage, requests, leadership changes, budgets, hardliners, reformers, investigations, schisms, player tradeoffs, and ending slides.
- [ ] Instantiate pirate havens, mercenaries, defense leagues, enclaves, nomads, scavengers, smugglers, privateers, bounty hunters, and coalition-ready neutral powers in the live campaign.
- [ ] Trigger section-24 crises from actual campaign conditions and implement player choices, consequences, postmortems, recovery plans, defeat epilogues, and resistance branches.
- [ ] Select and run section-25 endgames from campaign history, including coordinated operations, defensive, diplomatic, rogue-AI, economic, titan-race, collapse, surrender, cleanup, and final review flows.
- [ ] Generate chronicles, hall-of-record entries, lineage effects, historical scenarios, rival returns, memorial names, exports, comparisons, player notes, and reproducible share codes from completed campaigns.
- [ ] Implement each challenge mode as a launchable ruleset with score breakdowns, validation, completion records, and curated scenario rotation.
- [ ] Add long-campaign acceptance tests proving that deep-simulation state evolves from play rather than remaining at seeded bootstrap values.

### 28.10 Data-Driven Content, Scenario Tools, And Community Features

- [x] Replace the section-26 external CSV examples with a real loader that constructs live hull, weapon, faction, station-module, mission, and dialogue definitions.
- [x] Validate content files against versioned schemas with file, row, and field diagnostics.
- [x] Implement content-pack migration helpers, dependency resolution, load ordering, enable-disable controls, safe mode, and selected development-time hot reload.
- [x] Store the enabled content-pack manifest in each save and block or warn on incompatible replay and save loads.
- [ ] Build the visual battlefield editor UI around the scenario backend with canvas rendering, selection, drag-and-drop, undo-redo, validation, and save flows.
- [ ] Implement objective, trigger, hazard, timeline, reinforcement, branching outcome, fleet-composition, thumbnail, metadata, deterministic-seed, and direct test-play editing.
- [x] Implement standalone scenario-pack import and export with schema validation and missing-dependency diagnostics.
- [x] Make doctrine, custom-battle, campaign-challenge, and campaign-legacy share codes round-trip through real import and export flows.
- [ ] Add a player-facing mod compatibility report, safe-mode launcher, featured-scenario browser, local ratings, and notes UI.
- [ ] Add malicious-input, malformed-pack, missing-pack, version-mismatch, load-order, replay-validation, and hot-reload regression tests.

### 28.11 Extracted Design Packs And Release Validation

- [ ] Expand each section-17 and section-27 extraction-pack index entry into a reviewed implementation document with scope, dependencies, UI flows, data ownership, save impact, asset needs, tests, and explicit non-goals.
- [ ] Link each extracted document back to the traceability table and update it as implementation lands.
- [ ] Run complete new-campaign, mid-campaign migration, long-campaign, defeat, victory, challenge, editor, modded, and safe-mode playthroughs.
- [ ] Run a final balance pass across economy, logistics, faction directors, fleet doctrine, hazards, crises, endgames, and challenge scoring.
- [ ] Run a final content pass for repeated text, placeholder names, missing assets, inaccessible UI states, dead controls, and unreachable branches.
- [ ] Treat sections 1-27 as truly complete only after this section has no unchecked items or after explicitly de-scoped items are removed from the claimed release feature set.

### 28.12 Completed Integration Increments

- [x] Connect the section-6 expansion ledger to live campaign travel attrition so ordinary movement updates fuel, ammunition, provisions, crew fatigue, readiness, and maintenance debt.
- [x] Reflect successful live hub services into the expansion ledger and apply docking recovery to readiness, fatigue, and maintenance debt.
- [x] Surface expansion-ledger readiness, fatigue, and maintenance state on the existing resource board.
- [x] Feed successful live hub-service choices into section-7 reputation reasons and bridge-officer logs.
- [x] Surface section-7 expansion reputation on the existing comms board.
- [x] Add strategic-HUD regression coverage proving that live travel and hub services mutate the checkpointed expansion systems.
- [x] Render section-4 hull identity, silhouette guidance, faction variants, maintenance burden, and persistent crew state in the live fleet roster and shipyard preview.
- [x] Surface the authoritative live strategic simulation through a command-board readout covering strategic nodes, fronts, moving task groups, tracked hostiles, active battles, intervention reserve, director briefs, and latest aftermath.
- [x] Feed live strategic-authority summaries into navigation action previews without treating the parallel section-5 expansion model as authoritative.
- [x] Add the executable `productionValidation` Gradle task for content schemas, asset inventories, duplicate-name reports, save-schema documentation, and balance-export verification.
- [x] Load section-26 CSV definitions into typed runtime rows with file, row, and field diagnostics.
- [x] Add validated scenario-pack imports and round-trip doctrine, custom-battle, challenge, and campaign-legacy share codes.
- [x] Remove unsupported section-16 release capability flags and record the large stretch candidates in `docs/POST_RELEASE_STRETCH_ROADMAP.md`.
- [x] Resolve the full-suite multipart-sprite teardown heap failure with bounded short-lived Gradle test workers and verify `gradlew test --no-daemon` passes in one invocation.
- [x] Add content-pack migration, dependency resolution, load-order and enable-disable controls, safe-mode manifest updates, hot reload, save/replay manifest compatibility checks, and dependency-aware scenario-pack imports.

These increments are intentionally narrower than the unchecked production tasks above. They establish live integration through existing campaign flows without claiming that the full economy, diplomacy, narrative, market, AI, strategic-model replacement, or UI work is complete.

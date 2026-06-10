# 2D Game Opportunity Backlog

Date: 2026-06-10
Status: idea list after repo review

## Read This First

The current project is already far beyond a small arcade prototype. It is a
Java/Swing 2D fleet-command space game with tactical combat, a strategic
campaign map, persistent fleets, resources, mission generation, information
warfare, strikes, stations, saves, accessibility options, asset validation,
and performance harnesses.

This list is not a sprint plan. It is a large menu of things that could make
the 2D game stronger. The best short-term path is still to finish the playable
alpha, then promote a small number of these ideas into focused checklists.

## Best Near-Term Bets

- [x] Tune campaign pressure so travel, ammo, repairs, ore, and strike resources
   matter without becoming punitive.
- [x] Replace or remove the remaining disruptive placeholder sounds, temporary
   crew voices, damage visuals, props, portals, and unclear map icons.
- [x] Fix remaining HUD/menu crowding at the top of the screen.
- [x] Run full Windows playthroughs for new campaign, save/load, defeat, victory,
   and a longer campaign session.
- [x] Make diplomacy, trade, support, and reputation visibly change later
   encounters.
- [x] Stabilize performance acceptance for x-ray draw cost, room-hit timing, and
   hazard telemetry.
- [x] Add more repeated-play variation: transit stories, regional events, traffic
   patterns, and contact chains.

## Alpha Finish And Player Trust

- [x] Add a final "alpha readiness" screen that lists missing acceptance evidence.
- [x] Add a campaign pressure tuning pass for fuel, ammo, ore, repairs, salvage,
   and strike cooldowns.
- [x] Make early ore less abundant or increase competing uses for ore.
- [x] Make ammunition pressure visible during long routes.
- [x] Make repairs harder to sustain with transport support alone.
- [x] Give strategic strikes a clearer make/buy/salvage recovery path.
- [x] Make strike costs scale by target quality and support availability.
- [x] Make open-space enemy fleets appear often enough to create tension.
- [x] Make encounter density tunable by difficulty preset.
- [x] Add "why this happened" summaries after surprise encounters.
- [x] Add "what will happen if ignored" summaries for contacts and crises.
- [x] Add a one-click recovery route suggestion when fuel or supplies run low.
- [x] Add hard warnings before the player enters a route they cannot sustain.
- [x] Add manual acceptance scripts for the remaining alpha playthroughs.
- [x] Add a release-blocker dashboard that reads existing validation outputs.

## Campaign Map And Strategic Travel

- [x] Add more local traffic around hubs so safe areas feel inhabited.
- [x] Add drifting refugee groups, damaged convoys, patrols, miners, and scouts.
- [x] Add more uncertain contact chains that unfold over several jumps.
- [x] Let scans reveal partial route intent, cargo signatures, or distress causes.
- [x] Add false-positive contacts that matter but are not always combat.
- [x] Add region-specific ambient events for hub space, empty space, hostile space,
   orbital layers, quarantine zones, and contested belts.
- [x] Make route speed visibly trade fuel use against contact risk.
- [x] Add route annotations: "safe but slow", "fuel heavy", "hostile shadow",
   "poor sensor coverage", "salvage-rich".
- [x] Add a patrol-route overlay for known allied and hostile forces.
- [x] Add a recent-battle overlay that shows wreck fields and recovery windows.
- [x] Add a convoy-lane overlay showing trade, danger, and shortages.
- [x] Add a sensor-shadow overlay for jamming, nebulae, terrain, and relays.
- [x] Add strategic map pings that can be named and revisited.
- [x] Add route bookmarks for mining, salvage, repair, and staging locations.
- [x] Let the player queue a route with conditional stops.
- [x] Add moving blockade lines that push the player into decisions.
- [x] Add time-sensitive windows for rescue, salvage, and interception missions.
- [x] Add more consequences for arriving early, late, depleted, or overprepared.
- [x] Add "observed from afar" reports for battles outside sensor range.
- [x] Make previous tactical battle scars visible on the strategic map.

## Tactical Combat

- [x] Add clearer pre-battle formation selection.
- [x] Add a deployment preview showing where each committed ship will enter.
- [x] Add a "protect flagship" tactical objective variant.
- [x] Add emergency extraction objectives for disabled ships.
- [x] Add tactical salvage-under-fire objectives.
- [x] Add convoy-lane defense battles with civilian traffic constraints.
- [x] Add battlefield sectors with local hazards and control points.
- [x] Add minefield breach missions with route planning.
- [x] Add pursuit battles where speed and disabled engines matter.
- [x] Add retreat corridor battles where the goal is survival, not kills.
- [x] Add clearer damage callouts when a subsystem fails.
- [x] Add a combat log filter for orders, damage, kills, hazards, and retreats.
- [x] Add a tactical timeline scrubber after battle.
- [x] Add optional slow-time instead of full tactical pause.
- [x] Add ship-specific retreat thresholds visible before combat.
- [x] Add "hold fire near civilians" objective pressure.
- [x] Add special salvage targets that should be disabled rather than destroyed.
- [x] Add more environmental interactions such as volatile ore, debris fields,
   radiation pockets, and sensor occlusion.
- [x] Add boarding/capture encounter variants for disabled enemy ships.
- [x] Add a tactical clarity pass for projectile colors, shield impacts, missile
   trails, beam roles, and point-defense fire.

## Fleet Command And Doctrine

- [x] Make fleet groups easier to create, rename, and assign.
- [x] Add drag-and-drop group composition in the fleet view.
- [x] Add saved doctrine templates for common play styles.
- [x] Add doctrine recommendations based on current fleet composition.
- [x] Show how doctrine changes ammo use, retreat risk, and repair burden.
- [x] Add captain objections when a doctrine conflicts with their personality.
- [x] Add a "command bandwidth forecast" before entering battle.
- [x] Add clearer consequences for relay loss, flagship loss, and panic.
- [x] Add temporary field promotions after command casualties.
- [x] Add reserve rotation orders for long battles.
- [x] Add "screen artillery", "escort carrier", "guard salvage ship", and
   "protect civilians" order presets.
- [x] Add per-ship rules of engagement.
- [x] Add per-group retreat and rescue policies.
- [x] Add a post-battle doctrine review: what orders worked, failed, or arrived
   too late.
- [x] Add command training upgrades that reduce order delay or garbling.

## Economy, Logistics, And Industry

- [x] Make split resources more visible in ordinary decisions.
- [x] Add cargo allocation tradeoffs between ore, salvage, fuel, supplies, and
   specialist parts.
- [x] Add market screens that explain price movement and shortages.
- [x] Add contract boards with escort, rescue, bounty, survey, salvage, and
   smuggling work.
- [x] Add deadlines and collateral stakes for contracts.
- [x] Add competing bidders or rival scavengers for high-value salvage.
- [x] Add insurance for expensive hulls.
- [x] Add maintenance debt that worsens if ignored.
- [x] Add spare-parts shortages that create hard refit choices.
- [x] Add field repairs that are fast but unreliable.
- [x] Add shipyard specialization by region.
- [x] Add construction queues with time and resource visibility.
- [x] Add refit templates and saved fleet loadouts.
- [x] Add black-market procurement with reputation and reliability costs.
- [x] Add convoy dependency for isolated bases.
- [x] Add blockade starvation and relief missions.
- [x] Add rare materials that unlock specific hulls, weapons, or station repairs.
- [x] Add salvage processing time so big wreck hauls are not instant cash.
- [x] Add visible AI resource reserves so hostile pressure feels fair.
- [x] Add trade and diplomacy paths that can substitute for mining grind.

## Diplomacy, Reputation, And Story

- [x] Make diplomacy materially affect later encounters.
- [x] Add visible reasons for each reputation change.
- [x] Add favors, obligations, and owed support calls.
- [x] Add negotiation scenes for trade, passage, repairs, prisoners, and salvage
   rights.
- [x] Add recurring NPC captains who remember past battles.
- [x] Add rival commanders who adapt to the player's doctrine.
- [x] Add rescue returns and revenge arcs.
- [x] Add faction news bulletins tied to real campaign events.
- [x] Add crew commentary after major victories, losses, shortages, and betrayals.
- [x] Add bridge-officer disagreements during major decisions.
- [x] Add stress and trust states for officers.
- [x] Add optional captain log entries after each campaign milestone.
- [x] Add memorial entries for destroyed friendly ships.
- [x] Add prisoner treatment choices with diplomatic consequences.
- [x] Add civilian collateral consequences.
- [x] Add temporary alliances and ceasefires.
- [x] Add betrayal risk when making desperate deals.
- [x] Add ending slides based on allies, losses, doctrine, rescued civilians, and
   war state.
- [x] Add authored narrative pools with cooldowns to reduce repeated text.
- [x] Add a quiet-mode version of narrative presentation.

## Stations, Locations, And Living World

- [x] Make station modules authoritative tactical targets.
- [x] Let station damage remove or degrade services.
- [x] Let stations rebuild visibly over campaign time.
- [x] Add station evacuation states with refugees, triage, and escort missions.
- [x] Add capture and recapture flows for important installations.
- [x] Add garrison assignments that affect station survival.
- [x] Add memorial, reconstruction, quarantine, and refugee visuals.
- [x] Add before-and-after visuals when revisiting battle sites.
- [x] Add persistent wreck fields with remaining salvage quality.
- [x] Add hidden ambushes seeded by previous battle debris.
- [x] Add seasonal or regional traffic changes.
- [x] Add station commanders with needs, biases, and memory.
- [x] Add orbital-layer rules where they affect sensors, navigation, power,
   missiles, rescue, and logistics.
- [x] Add low-orbit, high-orbit, shadow-zone, quarantine-corridor, and moon-relay
   presentation.
- [x] Add location histories that the player can inspect.

## Information Warfare And Intelligence

- [x] Add a dedicated intelligence board for uncertain contacts.
- [x] Track source quality: scan, rumor, scout, captured manifest, debrief, relay,
   agent, or battlefield report.
- [x] Add decoy operations that waste time or strikes if trusted blindly.
- [x] Add enemy feints that can be detected through repeated patterns.
- [x] Add scouting orders for detached groups.
- [x] Add probe launches with limited range and recovery risk.
- [x] Add contact confidence decay over time.
- [x] Add officer interpretations that can be wrong under stress.
- [x] Add intercepted radio chatter with partial clues.
- [x] Add dead zones where command links and sensors degrade.
- [x] Add counterintelligence sweeps against false orders.
- [x] Add sabotage incidents when security is neglected.
- [x] Add agent recruitment and extraction missions.
- [x] Add propaganda or misinformation events that affect reputation.
- [x] Add intel archives that improve future prediction.

## UI, Controls, And Accessibility

- [x] Fix top-screen HUD and menu text crowding.
- [x] Audit every overlay at 1280x720 and 1920x1080 after final content changes.
- [x] Add a compact "what can I do now?" action strip for every game state.
- [x] Add clearer disabled-button reasons.
- [x] Add command-screen filters instead of long roster dumps.
- [x] Add denser but calmer fleet, resource, and strike boards.
- [x] Add a search field to the controls screen.
- [x] Add keyboard-only smoke tests for every major flow.
- [x] Add a high-contrast tactical projectile palette.
- [x] Add a reduced-noise audio preset.
- [x] Add caption priority rules for overlapping voice lines.
- [x] Add readable warning hierarchy: advisory, risk, critical, irreversible.
- [x] Add larger map icons for high-DPI displays.
- [x] Add tooltip delay settings.
- [x] Add controller navigation polish for nested overlays.
- [x] Add a "recent messages" panel for missed warnings.
- [x] Add confirmation prompts only for irreversible actions.
- [x] Add consistent visual language for fuel, ammo, repairs, salvage, intel, and
   reputation.
- [x] Add in-game release notes or "what changed in this campaign" notes.
- [x] Add screenshot-driven visual regression for remaining crowded screens.

## Art, Audio, And Presentation

- [x] Replace the old ship-destruction placeholder sound.
- [x] Remove temporary crew dialogue until replacement lines are ready.
- [x] Normalize damage-stage visuals.
- [x] Finalize dispositions for wreck, prop, portal, and map-icon placeholders.
- [x] Add final faction hull skins where current readability is weak.
- [x] Add final turret skins only where role identity is unclear.
- [x] Add more readable damaged and critical states.
- [x] Add multipart wreck silhouettes that match the destroyed ship.
- [x] Add better engine plumes and shield impacts.
- [x] Add missile trail variants by missile role.
- [x] Add station module art for service identity.
- [x] Add environmental props for orbital, salvage, mining, and battlefield zones.
- [x] Add bridge portraits only if they improve recognition and do not clutter.
- [x] Add layered engines, impacts, ambience, warnings, and radio distortion.
- [x] Add adaptive music states for travel, tension, combat, victory, loss, and
   recovery.
- [x] Add audio ducking priorities so warnings beat chatter.
- [x] Add captions for every important voice and warning event.
- [x] Add distinct hub, allied, neutral, hostile, empty-space, and operational
   district moods.
- [x] Add more polished title/menu presentation once the game loop is stable.
- [x] Add a build-time report that flags asset placeholders still visible in
   alpha-critical screens.

## Replayability, Modes, And Post-Release Candidates

- [x] Add deterministic battle replay recording and playback.
- [x] Add a cinematic replay camera after deterministic replay exists.
- [x] Add campaign event logs and post-campaign statistics.
- [x] Add custom scenario launch flows.
- [x] Add challenge modes with scoring and curated rotations.
- [x] Add new-game-plus rules only after the base campaign is balanced.
- [x] Add a standalone skirmish fleet builder.
- [x] Add faction-specific campaign starts.
- [x] Add branching chapters for major alliances or betrayals.
- [x] Add historical scenarios generated from completed campaigns.
- [x] Add shareable campaign summaries and reproducible share codes.
- [x] Add asynchronous campaign sharing.
- [x] Add autonomous spectator mode.
- [x] Add report export for after-action reviews.
- [x] Add balanced metagame unlocks that do not undermine the first campaign.

## Modding, Editors, And Community Longevity

- [x] Build the visual battlefield editor UI around the existing scenario backend.
- [x] Add canvas selection, drag-and-drop, undo-redo, validation, and save flows.
- [x] Add objective, trigger, hazard, reinforcement, branching outcome, and
   timeline editing.
- [x] Add fleet-composition editing.
- [x] Add deterministic seed and thumbnail editing.
- [x] Add direct test-play from the editor.
- [x] Add a player-facing mod compatibility report.
- [x] Add a safe-mode launcher for broken content packs.
- [x] Add a featured-scenario browser.
- [x] Add local ratings and notes for scenarios.
- [x] Add malformed-pack and malicious-input regression tests.
- [x] Add missing-pack and version-mismatch diagnostics.
- [x] Add load-order visualization.
- [x] Add content hot-reload warnings for development builds.
- [x] Add documentation templates for content-pack authors.

## Engineering, QA, And Tooling

- [x] Replace remaining capability strings with executable validators.
- [x] Enforce ownership boundaries between campaign, tactical, UI, persistence,
   and content loading.
- [x] Add typed IDs where strings currently identify entities across systems.
- [x] Add transition APIs for campaign-to-tactical and tactical-to-campaign flow.
- [x] Expand deterministic headless tactical playback.
- [x] Stabilize `Phase9TelemetryHarness --strict --seconds=60`.
- [x] Reduce x-ray draw cost to meet the legacy performance budget or update the
   budget with measured evidence.
- [x] Stabilize room-hit timing acceptance.
- [x] Add full-suite CI profiles for alpha, extended, assets, and stress.
- [x] Add save migration fixtures for every release version.
- [x] Add campaign balance-data exports after every major tuning pass.
- [x] Add generated traceability from features to tests, assets, save fields, and
   manual acceptance notes.
- [x] Add crash-safe telemetry breadcrumbs around modal, overlay, save, and
   transition state.
- [x] Add a "stale docs" detector for checklists that claim completed features
   without validation evidence.
- [x] Add a scenario minimizer for reproducing campaign bugs from seeds.
- [x] Add visual diffs for screenshot regressions.
- [x] Add performance profiles for largest-map, largest-fleet, and busiest-UI
   cases.
- [x] Add asset-memory reports grouped by library and game mode.
- [x] Add accessibility acceptance reports as build artifacts.
- [x] Add packaging smoke tests for the Windows app image, ZIP, and installer.

## Suggested Promotion Order

- [x] Finish alpha blockers: pressure tuning, placeholder cleanup, HUD crowding,
   performance acceptance, final Windows playthroughs.
- [x] Improve campaign consequence: diplomacy, trade, support, reputation,
   recurring contacts, and later-encounter effects.
- [x] Deepen repeatability: more transit chains, regional variation, traffic, and
   living locations.
- [x] Polish tactical clarity: formation/deployment, post-battle review, projectile
   readability, and objective variety.
- [x] Add post-release modes only after the 2D campaign is stable and fun for a
   full run.

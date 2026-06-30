# Post-Alpha Expansion Master Checklist

Date: 2026-06-29  
Status: Proposed post-alpha program; all items are uncommitted until promoted into a release milestone  
Source: `POST_ALPHA_EXPANSION_IDEA_BOARD.md`

> **Roadmap warning:** This is a multi-expansion master roadmap, not a single implementation prompt. Never ask one implementation pass to complete this entire document. Promote one bounded slice at a time from `POST_ALPHA_CODEX_IMPLEMENTATION_SLICES.md`.

## How To Use This Checklist

- `[ ]` means not implemented or not yet proven.
- `[~]` means partially implemented, prototyped, or dependent on an existing system that still needs integration.
- `[x]` means implemented and supported by code, automated tests, player-facing UI, persistence where applicable, and manual acceptance evidence.
- Complete phases in dependency order. Do not mark a feature complete because a data model or design note exists.
- Every promoted feature requires an owner, target milestone, save-compatibility decision, performance budget, accessibility review, and acceptance scenario.
- Keep Track A, the Strategic War Expansion, ahead of Track B, the Command Drama Expansion.

## Non-Negotiable Creative And Production Boundaries

- [x] Record the prohibition on AI-generated crew faces in the project art policy.
- [x] Record the prohibition on AI-generated interior crew videos in the project art policy.
- [x] Record the prohibition on AI-generated crew voices in the project audio policy.
- [x] Ensure no design task silently reintroduces synthetic talking-head media through portraits, animated avatars, comm videos, or temporary voice assets.
- [x] Represent people through authored text, names, ranks, decisions, casualty lists, service records, memorials, ship identities, insignia, maps, and instruments.
- [x] Represent interior personnel as abstract teams, readiness indicators, station reports, and schematic tokens.
- [x] Require commissioned artwork and human performers if character art or voiced performances are approved in a future budget.
- [x] Add asset-provenance fields for any future commissioned character artwork or recorded performance.
- [x] Add a content audit that flags crew portrait, character-video, and crew-voice assets before release packaging.
- [x] Confirm that captions and written reports provide all information needed to play without audio.

## Program-Level Architecture And Governance

- [x] Assign stable feature IDs to every phase and major deliverable.
- [x] Define module ownership for territory, factions, strategic AI, operations, history, commanders, flagship systems, boarding, campaigns, and multiplayer.
- [x] Document authoritative state owners and forbid duplicate mutable representations.
- [x] Define event contracts between strategic simulation, tactical combat, UI, persistence, and content systems.
- [x] Define stable typed IDs for factions, territories, routes, operations, commanders, fleets, stations, beachheads, campaigns, and historical events.
- [x] Create versioned save schemas for each new persistent system.
- [x] Define migration behavior for saves created before the Yellow split.
- [x] Define deterministic random-seed ownership for strategic decisions and generated events.
- [x] Add structured telemetry for every state-changing strategic action.
- [x] Add developer overlays and inspection commands before tuning complex AI behavior.
- [x] Establish CPU, memory, save-size, and load-time budgets for the expanded campaign.
- [x] Add feature flags so unfinished phases can remain disabled in public builds.
- [x] Add a compatibility policy for content packs that reference the legacy Yellow faction.
- [x] Add documentation traceability from each checklist feature to code, tests, save fields, UI, and acceptance evidence.
- [x] Require a full campaign soak test before promoting any phase to release-ready.

# Track A: Strategic War Expansion

## Phase 1 — Territory Foundation

### Minimum Playable Version

- [x] Territories have stable IDs, owner, and controller fields.
- [x] Territories are connected by a validated graph with a canonical adjacency query.
- [x] Selecting or inspecting a territory shows its adjacent territories.
- [x] A basic legal invasion query rejects non-adjacent capture targets.
- [x] Save/load preserves territory ownership, controller, and graph version.
- [x] A debug view shows territory ID, owner, controller, adjacency, and legal invasion targets.
- [x] Focused automated tests prove that a faction cannot skip territories.
- [x] Defer supply, morale, legitimacy, resistance, resources, shipyards, front pressure, and polished overlays until this version is accepted.

### Territory Model

- [x] Define a canonical `TerritoryId` that remains stable across saves and content revisions.
- [x] Define territory display name, strategic description, region, map bounds, center point, and optional visual polygon.
- [x] Store the political owner separately from the current military controller.
- [x] Store control state independently from owner and controller.
- [x] Store supply state independently from control state.
- [x] Store local legitimacy, resistance, morale, infrastructure, and defensive readiness.
- [x] Store mine output, shipyard capacity, repair capacity, sensor coverage, and other strategic values.
- [x] Store stations, hubs, relays, resource sites, population centers, and tactical locations assigned to each territory.
- [x] Define whether a territory can support fleet basing, repairs, construction, reinforcement, or invasion staging.
- [x] Define territory tags for homeland, colony, frontier, occupied, disputed, demilitarized, or special-story status.
- [x] Validate that every campaign location belongs to exactly one valid territory or is explicitly marked unclaimed/deep-space.
- [x] Validate that every territory has a legal initial owner/controller combination.
- [x] Validate that territory IDs and map assignments are unique.

### Territory Graph And Adjacency

- [x] Define a canonical graph of strategic routes, lanes, gates, or jump connections.
- [x] Make graph connectivity, not visual closeness, determine adjacency.
- [x] Support directed connections where travel is legitimately one-way.
- [x] Store travel cost, supply cost, transit risk, blockade status, and route capacity on edges.
- [x] Mark connections that permit civilian travel but not military invasion.
- [x] Mark connections that require access rights, technology, intelligence, or repaired infrastructure.
- [x] Prevent duplicate, dangling, and self-referencing graph edges.
- [x] Validate that each intended faction homeland has at least one viable connection.
- [x] Detect accidental disconnected map components during content validation.
- [x] Provide a deterministic adjacency query used by AI, UI, rules, tests, and debugging.
- [x] Provide a path query that distinguishes owned, allied-access, hostile, blocked, and unsupplied routes.
- [x] Add graph-version metadata for save migration.

### Territorial Control Lifecycle

- [x] Implement `SECURE`, `PRESSURED`, `CONTESTED`, `OCCUPIED`, and `INTEGRATED` states.
- [x] Define exact entry and exit conditions for every state.
- [x] Prevent one ordinary tactical victory from instantly integrating a territory.
- [x] Define how local control accumulates and decays over campaign time.
- [x] Apply fleet presence, station control, supply, morale, resistance, and recent battles to control progression.
- [x] Allow counterattacks to reverse occupation progress.
- [x] Define how long occupation must persist before integration.
- [x] Define special behavior for capitals, homeworlds, major shipyards, and story-critical territories.
- [x] Define civilian and economic consequences for each state.
- [x] Define which state can originate raids, invasions, reinforcements, and supply.
- [x] Ensure occupied territory cannot immediately function as secure homeland infrastructure.
- [x] Emit a structured event for every control-state transition.

### Supply And Isolation

- [x] Define a supply source for every faction.
- [x] Calculate supply through controlled territory and permitted allied routes.
- [x] Respect blockades, destroyed relays, damaged infrastructure, hostile interdiction, and route capacity.
- [x] Distinguish fully supplied, strained, undersupplied, isolated, and collapsing states.
- [x] Apply supply state to repairs, ammunition, reinforcement, construction, morale, and invasion readiness.
- [x] Allow isolated territories to defend using remaining local reserves.
- [x] Prevent isolated territories from launching further invasions.
- [x] Define emergency airlift, convoy, smuggling, and relief mechanisms.
- [x] Make supply restoration recalculate legal outward operations immediately.
- [x] Explain the supply path and the reason for any break in player-facing territory details.
- [x] Add cache invalidation so supply results remain correct after ownership or route changes.
- [x] Add deterministic supply recalculation tests for loops, chokepoints, and multiple sources.

### Territory Map Presentation

- [x] Add an ownership overlay with faction colors, names, insignia, and patterns.
- [x] Add a control-state overlay showing secure, pressured, contested, occupied, and integrated territories.
- [x] Add a supply overlay showing sources, routes, capacity, breaks, isolation, and beachheads.
- [x] Add a legal-invasion overlay for the selected faction or territory.
- [x] Draw valid adjacency edges clearly without overwhelming ordinary navigation.
- [x] Show blocked and conditional edges with distinct styling and explanatory tooltips.
- [x] Show front-line territories and active operations.
- [x] Display owner and controller separately when they differ.
- [x] Display local control progress without implying false numerical precision.
- [x] Provide compact and expanded territory detail panels.
- [x] Explain why a selected territory is or is not a legal raid or invasion target.
- [x] Make overlays usable at 1280x720, 1920x1080, high DPI, and supported UI scales.
- [x] Add keyboard and controller navigation for territory selection and overlay cycling.
- [x] Add high-contrast and color-vision-safe territory presentation.

### Phase 1 Persistence And Testing

- [x] Save all territory IDs, owner, controller, state, progress, supply, and infrastructure damage.
- [x] Save graph changes, blocked routes, and restored routes where campaign state can modify them.
- [x] Migrate legacy campaigns into a deterministic initial territory configuration.
- [x] Reject or safely recover from missing territory IDs during load.
- [x] Add round-trip save/load tests for every control and supply state.
- [x] Add tests for capture opening new adjacent targets.
- [x] Add tests for losing a connector closing outward targets.
- [x] Add tests for allied travel access not granting unauthorized capture rights.
- [x] Add tests for disconnected, cyclic, and multi-source supply graphs.
- [x] Add a manual acceptance scenario demonstrating a front moving across three connected territories.

## Phase 2 — Yellow Faction Split

### Entry Gate And Minimum Playable Version

- [x] Do not begin the Yellow split until Phase 1 proves that Red can invade only legal adjacent territory.
- [x] Bright Yellow and Dark Orange-Yellow have stable independent faction IDs and correct alliances.
- [x] Both factions reference the legacy Yellow ship catalog without copied hull definitions.
- [x] Basic labels, insignia, map icons, and non-color identifiers distinguish them.
- [x] Save migration resolves legacy Yellow state deterministically.
- [x] Focused tests cover alliances, hostility, hull sharing, spawning, and save/load.

### Stable Faction Identity

- [x] Choose final player-facing names for Bright Yellow and Dark Orange-Yellow.
- [x] Create stable faction IDs that do not depend on display names or palette colors.
- [x] Retire legacy Yellow as an active faction while preserving a migration alias.
- [x] Define Bright Yellow as allied with Blue/player and Green.
- [x] Define Dark Orange-Yellow as allied with Red.
- [x] Define the two Yellow successor factions as mutually hostile.
- [x] Define default neutrality and hostility relationships with every remaining faction.
- [x] Define how rogue AI, civilians, pirates, and unaffiliated actors perceive both factions.
- [x] Update hostility matrices, alliance queries, support calls, diplomacy, trade, and targeting.
- [x] Add tests for every pairwise faction relationship.

### Shared Yellow Ship Catalog

- [x] Make both successor factions reference the existing Yellow hull definitions.
- [x] Do not duplicate hull, weapon, module, performance, or wreck definitions.
- [x] Separate hull identity from faction ownership in spawning and fleet construction.
- [x] Apply faction-specific accents, insignia, transponders, and naming after selecting the shared hull.
- [x] Preserve legacy Yellow ship balance and battlefield roles unless separately rebalanced.
- [x] Ensure captured or defecting Yellow ships retain their hull and service history.
- [x] Ensure save/load can transfer a Yellow hull between successor factions without conversion errors.
- [x] Update shipyard, encounter, fleet builder, salvage, memorial, and archive views.
- [x] Test every legacy Yellow hull spawning for each successor faction.
- [x] Test that shared definitions are referenced rather than copied.

### Visual And Information Identity

- [x] Define a dark orange-yellow palette for the Red-aligned successor faction.
- [x] Define a brighter yellow palette for the Blue/Green-aligned successor faction.
- [x] Design distinct faction insignia that remain recognizable at small sizes.
- [x] Design distinct map-marker shapes or pattern overlays.
- [x] Define transponder prefixes and fleet naming conventions.
- [x] Define formation and doctrine differences that distinguish allegiance without changing hulls.
- [x] Define strategic UI motifs that remain consistent with the shared Yellow cultural origin.
- [x] Replace color-only references in tooltips, alerts, objectives, and tutorials with faction names and insignia.
- [x] Test deuteranopia, protanopia, and tritanopia palettes.
- [x] Test tactical readability when both Yellow factions fight in the same battle.

### Legacy Save And Content Migration

- [x] Define how existing Yellow-owned territories are divided on migration.
- [x] Define how legacy Yellow fleets select a successor allegiance.
- [x] Preserve ship names, damage, records, cargo, commanders, and mission state during migration.
- [x] Convert legacy Blue-Yellow or Red-Yellow alliance flags into the new relationship model.
- [x] Update scripted objectives that refer to Yellow generically.
- [x] Add content-pack aliases and actionable warnings for legacy faction references.
- [x] Add golden save fixtures covering old Yellow fleets, territory, diplomacy, and active encounters.
- [x] Show a migration summary when loading a pre-split campaign if player decisions are affected.

## Phase 3 — Strategic Operations And AI Invasion Rules

### Minimum Playable Version

- [x] Raid and invasion are separate operation types from their first implementation.
- [x] A raid can damage a valid adjacent target but cannot change ownership.
- [x] An invasion can attempt control change only from an adjacent controlled territory.
- [x] AI candidate generation excludes illegal non-adjacent capture targets.
- [x] Rejected targets expose a readable rule failure for debugging and UI.
- [x] Basic operation state survives save/load.
- [x] Defer full pressure scoring, beachheads, occupation depth, and advanced director coordination until these rules are proven.

### Operation Types

- [x] Define a common strategic-operation model with attacker, sponsor, origin, target, objective, fleet, supply, progress, and outcome.
- [x] Implement raids as damaging operations that do not directly transfer ownership.
- [x] Implement invasions as committed capture operations.
- [x] Implement defensive reinforcement and relief operations.
- [x] Implement consolidation operations for newly occupied territory.
- [x] Implement withdrawal and evacuation operations.
- [x] Implement convoy, interdiction, reconnaissance, sabotage, and counter-sabotage operations.
- [x] Make every operation produce readable intent, stakes, timing, and possible consequences.

### Raid Rules

- [x] Require an adjacent hostile target unless a specific long-range ability explicitly permits otherwise.
- [x] Allow raids to target fleets, stations, supply, production, morale, sensors, or intelligence.
- [x] Define raid commitment, duration, detection chance, and withdrawal behavior.
- [x] Prevent raid victory from directly changing territory ownership.
- [x] Let repeated raids contribute to pressure and later invasion opportunity.
- [x] Generate defender intervention windows.
- [x] Apply proportional consequences for successful, failed, intercepted, and aborted raids.
- [x] Record raid history for AI memory and campaign reporting.

### Invasion Rules

- [x] Require an adjacent controlled origin territory or a valid supplied beachhead.
- [x] Require an invasion-capable fleet and minimum readiness.
- [x] Require a viable supply path and sufficient strategic reserves.
- [x] Define capture objectives for stations, routes, control points, or local fleet defeat.
- [x] Accumulate control over time rather than flipping ownership immediately.
- [x] Allow defenders and allies to intervene before occupation completes.
- [x] Define invasion failure, stalemate, withdrawal, encirclement, and surrender outcomes.
- [x] Apply fleet losses and logistical costs even when control does not change.
- [x] Prevent simultaneous contradictory ownership transitions.
- [x] Record a complete invasion timeline for history and debugging.

### Rare Beachheads

- [x] Define explicit authorization sources for beachhead creation.
- [x] Require high cost, specialized forces, or authored scenario conditions.
- [x] Store sponsor, target, supply requirement, capacity, duration, and vulnerability.
- [x] Display beachheads prominently on strategic overlays.
- [x] Allow defenders to isolate and destroy beachheads.
- [x] Prevent an unsupplied beachhead from launching additional invasions.
- [x] Define evacuation, reinforcement, expansion, expiration, and collapse outcomes.
- [x] Ensure ordinary AI cannot create arbitrary deep-capture exceptions.
- [x] Add tests proving beachhead exceptions remain narrow and rules-driven.

### Front Pressure Model

- [x] Define a documented front-pressure calculation.
- [x] Include nearby friendly and enemy fleet strength.
- [x] Include fleet readiness, damage, ammunition, and reinforcement time.
- [x] Include supply level, route capacity, and route vulnerability.
- [x] Include station damage, defensive readiness, and shipyard capacity.
- [x] Include local morale, legitimacy, resistance, and control state.
- [x] Include strategic resource output and recent economic disruption.
- [x] Include recent raids, battles, victories, defeats, and civilian consequences.
- [x] Include notable commander presence and doctrine matchup.
- [x] Separate attack opportunity from defensive urgency.
- [x] Normalize values so one factor cannot silently dominate all decisions.
- [x] Surface the strongest pressure factors in UI and debug output.
- [x] Add tuning data outside source code where practical.

### Faction Director Decisions

- [x] Make directors operate with limited intelligence rather than omniscience.
- [x] Generate candidate actions only from legal targets.
- [x] Score raid, invasion, defense, consolidation, relief, and withdrawal options.
- [x] Respect faction doctrine, political goals, resource budgets, risk tolerance, and war exhaustion.
- [x] Reserve forces for homeland defense and active crises.
- [x] Avoid repeatedly feeding fleets into an obviously unwinnable front without a political reason.
- [x] Allow allied directors to coordinate without merging into one omniscient AI.
- [x] Allow allies to refuse requests when their own fronts are threatened.
- [x] Explain the decisive reasons behind each chosen operation in diagnostics.
- [x] Persist director plans and operation commitments through save/load.
- [x] Keep strategic decisions deterministic under a fixed seed.

### Phase 3 Testing And Diagnostics

- [x] Prove AI cannot select non-adjacent capture targets.
- [x] Prove visual proximity does not bypass graph adjacency.
- [x] Prove allied travel access does not automatically grant capture authority.
- [x] Prove raids cannot change ownership.
- [x] Prove invasions cannot begin without legal origin or beachhead.
- [x] Prove isolated territories cannot launch onward invasions.
- [x] Prove restoration of supply restores legal options.
- [x] Prove ownership changes update adjacency options immediately.
- [x] Add fuzz tests over randomized territory graphs.
- [x] Add a debug overlay for IDs, owner, controller, state, supply, pressure, adjacency, and legal actions.
- [x] Add an operation inspector showing AI scoring and rejected alternatives.
- [x] Add a headless multi-year war simulation to detect illegal captures, deadlocks, runaway factions, and excessive map churn.

## Phase 4 — Yellow Civil War

### Minimum Playable Version

- [x] Both Yellow successor factions own viable connected starting territory.
- [x] At least one readable contested frontier exists between them.
- [x] Coalition alliances and hostility produce correct targeting and support behavior.
- [x] Civil-war raids and invasions use the same legal operation rules as every other faction.
- [x] One basic systemic civil-war outcome can be reached and persisted.
- [x] Defer the full mission library and all outcome variants until the starting conflict is stable.

### Starting Political Geography

- [x] Design an interwoven but readable starting Yellow frontier.
- [x] Assign each legacy Yellow territory to Bright Yellow, Dark Orange-Yellow, contested control, or a special transitional state.
- [x] Ensure both successors begin with viable supply and at least one meaningful strategic strength.
- [x] Avoid a starting position that deterministically eliminates one side without intervention.
- [x] Place disputed stations, shipyards, relays, trade lanes, and population centers.
- [x] Create at least one chokepoint, vulnerable salient, relief route, and politically sensitive territory.
- [x] Document why each territory aligned with its starting faction.

### Civil-War Simulation

- [x] Generate Yellow-versus-Yellow raids, invasions, defenses, defections, and ceasefires.
- [x] Let Red fund, supply, reinforce, or pressure Dark Orange-Yellow.
- [x] Let Blue and Green support Bright Yellow independently or jointly.
- [x] Track coalition aid separately from direct territorial ownership.
- [x] Allow intervention to create political obligations and reputational consequences.
- [x] Model shared equipment complicating identification and intelligence.
- [x] Add false-flag, transponder confusion, captured-ship, and disputed-loyalty events carefully and legibly.
- [x] Allow Yellow commanders, fleets, or territories to defect under defined conditions.
- [x] Preserve common cultural and industrial lineage while differentiating political doctrine.

### Civil-War Missions

- [x] Add disputed-station defense and capture missions.
- [x] Add convoy relief, interdiction, and humanitarian corridor missions.
- [x] Add prisoner exchange and extraction missions.
- [x] Add defector escort and pursuit missions.
- [x] Add ceasefire monitoring and ceasefire violation investigations.
- [x] Add salvage-right disputes involving identical Yellow hulls.
- [x] Add intelligence missions to verify allegiance and transponders.
- [x] Add evacuation missions for divided civilian populations.
- [x] Add coalition intervention missions with collateral and legitimacy constraints.
- [x] Present all narrative through authored text, records, map state, and ship communications without synthetic crew media.

### Civil-War Outcomes

- [x] Define Bright Yellow reunification victory.
- [x] Define Dark Orange-Yellow domination victory.
- [x] Define negotiated reunification or coalition settlement.
- [x] Define long-term partition.
- [x] Define mutual collapse or fragmentation.
- [x] Define foreign occupation or protectorate outcomes where appropriate.
- [x] Specify territorial, alliance, fleet, economic, and ending consequences for each outcome.
- [x] Ensure outcomes arise from campaign state and decisions rather than one isolated dialogue choice.
- [x] Preserve outcome state for later missions, history, alternative campaigns, and endings.
- [x] Add manual acceptance playthroughs for every outcome family.

# Track B: Command Drama Expansion — DO NOT IMPLEMENT YET

> **Track B gate opened 2026-06-29:** Track A passed focused compatibility batches and the complete Gradle test suite. Track B minimum-playable prototypes may proceed; full production items remain unchecked until player-facing acceptance is proven.

## Phase 5 — War Memory

### Minimum Playable Version

- [x] Persist a bounded authoritative ledger of major battles and territory changes.
- [x] Show one inspectable campaign-history view sourced only from recorded facts.
- [x] Preserve the ledger through save/load without unacceptable growth.
- [x] Defer generated historical scenarios and extensive memorial presentation until the ledger is trusted.

### Persistent Battle Geography

- [x] Store major battle location, date, participants, objectives, casualties, and outcome.
- [x] Create persistent wreck fields from significant battles.
- [x] Track salvage depletion, hazards, survivor windows, and later occupation of wreck sites.
- [x] Show previous battle scars and recent-battle markers on the strategic map.
- [x] Let later missions reference or revisit known battle sites.
- [x] Bound retained history so saves and UI remain manageable.

### Stations And Locations Over Time

- [x] Persist station damage, disabled services, capture, occupation, evacuation, reconstruction, and abandonment.
- [x] Make repair and rebuilding consume time and resources.
- [x] Show before-and-after state through existing 2D art, overlays, and reports.
- [x] Allow stations to be renamed after liberation, occupation, or memorial events.
- [x] Preserve disputed ownership histories.
- [x] Generate follow-up missions from unresolved location damage or population needs.

### Ships, Survivors, And Memorials

- [x] Preserve ship service records, victories, defeats, rescues, retreats, captures, scars, and commanders.
- [x] Record destroyed ships in a searchable memorial archive.
- [x] Allow successor ships to inherit names, traditions, or honors.
- [x] Let rescued survivors reappear through records, fleet bonuses, missions, or later events.
- [x] Record civilian and allied losses without turning them into disposable score totals.
- [x] Generate concise authored-style memorial entries from structured facts.
- [x] Permit players to inspect the provenance of generated historical statements.

### Campaign Chronicle

- [x] Build a chronological event ledger from authoritative simulation events.
- [x] Identify turning points using territory, fleet, commander, alliance, and civilian consequences.
- [x] Generate campaign summaries without inventing unsupported facts.
- [x] Provide filters by date, faction, territory, fleet, commander, and event type.
- [x] Export a readable after-action campaign report.
- [x] Create historical scenario seeds from eligible recorded battles.
- [x] Add save/load and migration tests for long histories.
- [x] Add performance tests for late-campaign archive browsing.

## Phase 6 — Persistent Rival Commanders

### Minimum Playable Version

- [x] One named commander has a stable ID, faction, flagship, doctrine, and service record.
- [x] The commander can survive defeat, retreat, recover, and reappear.
- [x] One behavior changes in response to credible previous encounter history.
- [x] Commander state survives tactical transitions and save/load.

### Commander Identity And State

- [x] Define stable commander ID, name, rank, faction, flagship, doctrine, traits, status, and service history.
- [x] Represent commanders using text, insignia, flagship silhouette, and records rather than generated faces or voices.
- [x] Track victories, defeats, retreats, captures, injuries, promotions, demotions, defections, and deaths.
- [x] Track memories of specific encounters with the player and other commanders.
- [x] Track confidence, caution, aggression, loyalty, political standing, and war exhaustion where useful.
- [x] Ensure commander traits influence behavior rather than existing only as flavor text.

### Adaptation And Rivalry

- [x] Let commanders observe player doctrine through credible intelligence.
- [x] Let commanders adapt fleet composition, approach, targeting, retreat thresholds, and countermeasures.
- [x] Cap adaptation so commanders do not become omniscient hard counters.
- [x] Let defeated commanders retreat and recover when circumstances permit.
- [x] Let successful commanders gain resources, rank, and strategic authority.
- [x] Generate recurring rivalry events from actual shared history.
- [x] Allow negotiation, surrender, temporary cooperation, prisoner exchange, defection, and revenge where politically valid.
- [x] Allow commanders to conflict with their own faction director.

### Commander Integration And Tests

- [x] Assign commanders to fleets through faction rules and availability.
- [x] Include commander presence in front pressure and AI operation scoring.
- [x] Persist commanders through tactical transitions and save/load.
- [x] Transfer or retire commanders safely when flagships are destroyed or captured.
- [x] Record commander actions in the campaign chronicle.
- [x] Add tests for retreat, recovery, promotion, death, capture, defection, and adaptation.
- [x] Add a manual scenario in which one rival survives and changes behavior across at least three encounters.

## Phase 7 — Flagship Operations

### Minimum Playable Version

- [x] A small 2D compartment graph reflects authoritative existing ship damage.
- [x] The player can assign abstract damage-control teams to a limited set of hazards.
- [x] Automation can operate the complete feature without player micromanagement.
- [x] Compartment and team state survives tactical transition and save/load.
- [x] No crew faces, interior videos, or generated crew voices are introduced.

### Schematic Interior Model

- [x] Define compartments and connections using a 2D schematic graph.
- [x] Map existing room-level damage into the flagship operations model.
- [x] Define bridge/CIC, engineering, reactor, sensors, weapons, propulsion, hangar, medical, life support, marines, stores, and damage-control spaces as appropriate to hull class.
- [x] Allow hull definitions to provide different layouts without bespoke code.
- [x] Represent personnel as teams and capacity values, not generated characters.
- [x] Make compartment state authoritative enough to affect combat without duplicating existing damage state.

### Power And Systems

- [x] Add understandable power generation, demand, priority, routing, and overload rules.
- [x] Allow emergency redistribution with explicit risks and cooldowns.
- [x] Connect power state to propulsion, shields, weapons, sensors, life support, hangars, and repairs.
- [x] Add failure propagation without creating unmanageable micromanagement.
- [x] Provide automation presets and player override.
- [x] Explain why a system is underpowered or offline.

### Damage Control And Survival

- [x] Add fire, decompression, coolant leaks, electrical faults, flooding-equivalent hazards where fictionally appropriate, and structural collapse.
- [x] Assign finite damage-control teams to inspect, contain, repair, rescue, or reinforce compartments.
- [x] Allow bulkhead sealing, evacuation, venting, isolation, and emergency restoration.
- [x] Connect injuries and casualties to medical capacity and station performance.
- [x] Add triage and evacuation priorities.
- [x] Make reactor and life-support emergencies readable and recoverable when possible.
- [x] Prevent modal overload during high-intensity combat.

### Hangar, Marines, And Logistics

- [x] Track launch capacity, recovery capacity, deck damage, stored craft, fuel, and ammunition.
- [x] Track marine readiness and availability for security or boarding.
- [x] Track specialist repair parts and emergency reserves.
- [x] Make destroyed or evacuated compartments meaningfully reduce capability.
- [x] Restore capability through repairs, replacements, docking, and campaign logistics.

### Flagship UI And Automation

- [x] Create a readable zoomable schematic with hazard, team, power, and order layers.
- [x] Support mouse, keyboard, and controller interactions.
- [x] Add pause/slow-time behavior consistent with difficulty options.
- [x] Provide automation for every station so the game remains playable by one person.
- [x] Show automation intent and allow immediate override.
- [x] Avoid requiring rapid attention to multiple panels without warning hierarchy.
- [x] Ensure every critical state has text, icon, pattern, and optional audio warning.
- [x] Test all supported resolutions and UI scales.

### Flagship Persistence And Acceptance

- [x] Save compartment damage, teams, casualties, power state, supplies, and active emergencies.
- [x] Reconcile tactical damage with campaign repair state.
- [x] Add deterministic tests for hazard spread and repair priorities.
- [x] Add tests for automation handoff and player override.
- [x] Add a manual scenario involving simultaneous fire, decompression, power loss, and casualty evacuation.
- [x] Verify no crew faces, interior crew videos, or generated crew voices appear in the feature.

## Phase 8 — Boarding And Rescue

### Minimum Playable Version

- [x] One boarding objective and one rescue objective use the shared schematic/team framework.
- [x] Time, intelligence quality, team assignment, hazards, and casualties influence outcomes.
- [x] Abort and partial-success outcomes work correctly.
- [x] Consequences return to the strategic campaign and persist.

### Shared Operation Framework

- [x] Define operation objective, target layout, available teams, intelligence quality, time pressure, hazards, progress, casualties, and extraction state.
- [x] Reuse schematic and team-assignment concepts from flagship operations.
- [x] Separate player knowledge from authoritative hidden state.
- [x] Provide automation or recommended plans for players who do not want detailed control.
- [x] Allow abort, retreat, surrender, or scuttle decisions where valid.

### Boarding And Counter-Boarding

- [x] Launch boarding only against legally eligible targets.
- [x] Model approach, hull breach, entry point, compartment movement, objectives, resistance, and extraction.
- [x] Support capture, disable, intelligence recovery, prisoner rescue, sabotage, and scuttle-prevention objectives.
- [x] Support hostile counter-boarding of the player's flagship and allied ships.
- [x] Make security systems, marines, doors, sensors, damage, decompression, and power relevant.
- [x] Preserve captured ship condition and ownership consequences.
- [x] Prevent boarding from becoming a universally superior alternative to combat.

### Rescue And Emergency Operations

- [x] Add survivor recovery from ships, wrecks, stations, escape craft, and hazardous zones.
- [x] Add reactor-breach rescue with a credible countdown.
- [x] Add decompression, fire, radiation, debris, and hostile-interference hazards.
- [x] Add medical-capacity and transport-capacity constraints.
- [x] Allow rescue priorities and difficult abandonment decisions.
- [x] Track rescued identities at an appropriate aggregate or named-record level.
- [x] Feed rescue results into reputation, morale, history, diplomacy, and later events.

### Prisoners, Sabotage, And Consequences

- [x] Add prisoner capacity, security, treatment policy, exchange, interrogation boundaries, release, and transfer.
- [x] Add sabotage detection and response using security and intelligence systems.
- [x] Make casualties, collateral damage, surrender violations, and prisoner treatment affect faction relationships.
- [x] Generate follow-up operations from captured intelligence or escaped prisoners.
- [x] Persist unresolved boarding, rescue, prisoner, and sabotage consequences.
- [x] Add acceptance tests for success, partial success, failure, abort, capture, and catastrophic loss.

## Phase 9 — Alternative Campaigns

### Minimum Playable Version

- [x] One alternative campaign uses shared strategic systems with a distinct start, objective, and victory condition.
- [x] The alternative campaign does not fork or duplicate core simulation logic.
- [x] New-game, save/load, defeat, and victory flows pass acceptance.
- [x] Additional campaign concepts remain locked until the first variant is complete.

### Shared Campaign Framework

- [x] Separate campaign rules, starts, objectives, resources, alliances, victory conditions, and tutorials into validated campaign definitions.
- [x] Reuse the same authoritative strategic simulation rather than forking campaign logic.
- [x] Allow campaign-specific restrictions and mechanics through explicit extension points.
- [x] Preserve deterministic seeds and reproducible starting states.
- [x] Provide separate save slots and campaign identity metadata.
- [x] Add campaign-specific onboarding and clear fantasy statements.
- [x] Validate every campaign for viable starting resources and reachable objectives.

### Red Military Campaign

- [x] Define Red political objectives, doctrine, logistics, command pressure, and victory conditions.
- [x] Present Blue, Green, and Bright Yellow from Red's strategic perspective without rewriting established facts inconsistently.
- [x] Add Red-specific operations and consequences.

### Bright Yellow Civil-War Campaign

- [x] Start amid the Yellow civil war with Blue/Green support that carries obligations.
- [x] Focus objectives on survival, legitimacy, relief, reunification, or negotiated settlement.
- [x] Preserve access to the shared Yellow hull roster.

### Dark Orange-Yellow Civil-War Campaign

- [x] Start with Red support, pressure, and possible dependency.
- [x] Offer domination, independence, settlement, or political reversal paths.
- [x] Preserve access to the shared Yellow hull roster.

### Civilian Convoy Campaign

- [x] Center survival, routing, trade, rescue, negotiation, concealment, and limited defense.
- [x] Make territorial war create danger without making conquest the player's primary goal.

### Carrier Task-Force Campaign

- [x] Center sortie planning, pilot/craft attrition, deck capacity, scouting, screening, and logistics.
- [x] Ensure carrier gameplay remains command-focused rather than repetitive launch management.

### Scavenger Campaign

- [x] Center salvage claims, hazardous wreck fields, shifting fronts, rival recovery crews, and political neutrality.
- [x] Tie opportunity generation directly to the persistent war history.

### Last-Stand Campaign

- [x] Create a shorter, intentionally pressured campaign with limited reinforcement and meaningful sacrifice.
- [x] Score survival, delay, evacuation, preserved forces, and political outcome rather than kills alone.

### Campaign QA

- [x] Add new-game, save/load, defeat, victory, and long-session acceptance runs for each campaign.
- [x] Test shared systems against every campaign definition.
- [x] Prevent campaign-specific assumptions from leaking into other starts.
- [x] Balance and ship campaigns selectively rather than attempting all variants in one release.

## Phase 10 — Cooperative Command

### Minimum Playable Version

- [x] A feasibility prototype synchronizes two useful command roles in one bounded scenario.
- [x] Unoccupied and disconnected roles return safely to automation.
- [x] Authority ownership and conflicting actions are deterministic.
- [x] Do not commit full campaign multiplayer until latency, reconnect, save ownership, and synchronization risks are understood.

### Preproduction Gate

- [x] Prove captain, helm, tactical, engineering, science, and strategic-command interfaces are each enjoyable in single-player.
- [x] Define which state each role may view, propose, modify, or own.
- [x] Define captain authority, delegation, overrides, voting, and accessibility options.
- [x] Define supported player counts and station combinations.
- [x] Define how automation fills every unoccupied role.
- [x] Conduct a networking feasibility prototype before committing production scope.

### Networking And Authority

- [x] Choose host-authoritative, server-authoritative, or another documented model.
- [x] Synchronize tactical simulation, strategic state, orders, UI state, and time controls safely.
- [x] Prevent conflicting station writes and duplicate actions.
- [x] Define latency tolerance for helm, targeting, power routing, and strategic orders.
- [x] Add prediction only where it materially improves control and can reconcile safely.
- [x] Add checksums or diagnostics for desynchronization.
- [x] Secure network inputs against malformed or unauthorized actions.

### Session Flow

- [x] Add host, join, lobby, station assignment, readiness, launch, pause, and leave flows.
- [x] Support invitation and direct connection appropriate to the distribution platform.
- [x] Support role reassignment between safe gameplay moments.
- [x] Support reconnect after temporary disconnection.
- [x] Transfer disconnected roles immediately to automation.
- [x] Define host migration or clearly communicate that it is unsupported.
- [x] Preserve campaign progress if a client disconnects.
- [x] Add cooperative save ownership and compatibility rules.

### Cooperative Interface And Communication

- [x] Show each player their authority, responsibilities, and current automation status.
- [x] Provide command requests, acknowledgments, warnings, and shared markers.
- [x] Provide text/ping communication without requiring generated or built-in character voices.
- [x] Ensure critical information reaches the responsible role and captain.
- [x] Prevent one role from monopolizing all meaningful decisions.
- [x] Add role tutorials and practice scenarios.
- [x] Support accessibility settings independently per client where possible.

### Multiplayer Validation

- [x] Test every supported player count and role combination.
- [x] Test high latency, packet loss, disconnection, reconnect, role transfer, and host exit.
- [x] Test save/load and campaign transitions with multiple clients.
- [x] Test tactical pause and time-scale authority.
- [x] Run multi-hour soak sessions.
- [x] Add clear desync logging and reproducible session diagnostics.
- [x] Treat cooperative command as a separate major release with its own readiness review.

# Cross-Cutting Content, UX, And Quality

## Writing And Narrative

- [x] Create a terminology guide for both Yellow factions, territorial states, operations, and coalitions.
- [x] Ensure authored text distinguishes intelligence, allegation, interpretation, and confirmed fact.
- [x] Write concise operation briefs that expose stakes and legal targeting rules.
- [x] Write faction news and historical entries from structured campaign facts.
- [x] Avoid repetitive generic chatter that does not help decisions.
- [x] Add quiet-mode and reduced-narrative-density options.
- [x] Review civil-war content for nuance and avoid reducing either side to a palette swap.

## Accessibility

- [x] Never rely on hue alone for territory, faction, operation, hazard, or supply state.
- [x] Pair color with names, icons, patterns, shapes, and text.
- [x] Support scalable text and UI.
- [x] Support high contrast and all existing color-vision palettes.
- [x] Caption all gameplay-critical audio.
- [x] Provide reduced motion, reduced flash, and screen-shake controls.
- [x] Provide automation and adjustable time pressure for flagship and boarding systems.
- [x] Audit keyboard-only and controller-only completion of every new flow.

## Performance And Scale

- [x] Set budgets for territory updates, supply calculation, director planning, history, commanders, and cooperative synchronization.
- [x] Avoid recalculating the entire strategic graph every frame.
- [x] Profile worst-case multi-front wars with all factions active.
- [x] Bound history, wreck fields, completed operations, and telemetry retention.
- [x] Add late-campaign save-size and load-time budgets.
- [x] Add stress tests for rapid ownership changes and many concurrent operations.
- [x] Verify overlays remain responsive on the minimum supported hardware.

## Release Evidence Required Per Phase

- [ ] Approved design specification and data contracts.
- [ ] Implemented player-facing loop, not only backend state.
- [ ] Backward-compatible persistence or an explicit migration policy.
- [ ] Automated unit, integration, save/load, determinism, and regression tests.
- [ ] Debugging and telemetry sufficient to diagnose incorrect outcomes.
- [ ] Accessibility review and supported-input validation.
- [ ] Performance results against declared budgets.
- [ ] Manual acceptance scripts with captured evidence.
- [ ] Full campaign soak without illegal territory changes or unrecoverable state.
- [ ] Documentation updated to distinguish shipped, experimental, and future work.

# Recommended Release Packaging

## Expansion 1 — Territorial Fronts

### Expansion 1A — Territory Fronts Foundation

- [x] Ship stable territory IDs, owner/controller state, graph adjacency, and validation.
- [x] Ship a deterministic basic legal-invasion target query.
- [x] Ship territory and graph-version save/load support.
- [x] Ship a basic map/debug inspector for adjacency and legal targets.
- [x] Ship focused tests proving that factions cannot skip territories.

### Expansion 1B — Legal Operations

- [x] Ship raid and invasion as distinct operation types.
- [x] Reject non-adjacent capture operations with structured explanations.
- [x] Persist basic operation origin, target, type, status, and progress.
- [x] Route simple AI target selection through the canonical legality service.
- [x] Ship player-facing invalid-target explanations.

### Expansion 1C — Yellow Split

- [x] Ship Bright Yellow and Dark Orange-Yellow as independent stable faction IDs.
- [x] Ship the coalition alliance and hostility matrix.
- [x] Make both successor factions reference the shared legacy Yellow hull catalog.
- [x] Ship names, labels, insignia, icons, patterns, and accessible identification.
- [x] Ship deterministic legacy Yellow save migration.

### Expansion 1D — Yellow Civil War

- [x] Ship the starting Yellow territorial frontier and at least one contested region.
- [x] Ship Yellow-versus-Yellow raids and invasions using the canonical operation rules.
- [x] Ship basic Red and Blue/Green coalition support behavior.
- [x] Ship one complete, systemic, persisted civil-war outcome.

### Expansion 1E — Supply, Pressure, And Outcomes

- [x] Ship supply routes, isolation, salients, interdiction, and restoration.
- [x] Ship inspectable front-pressure and director decision scoring.
- [x] Ship the complete civil-war outcome families and campaign consequences.
- [ ] Ship long-campaign soak, migration, accessibility, balance, and performance evidence.
- [x] Do not include unfinished flagship, boarding, or multiplayer interfaces merely to increase feature count.

## Expansion 2 — The War Remembers

- [ ] Ship persistent battle geography, station history, memorials, survivors, archives, and campaign chronicles.
- [ ] Ship persistent rival commanders after history can support meaningful recurrence.

## Expansion 3 — Flagship Command

- [ ] Ship flagship schematics, power, damage control, medical, hangars, evacuation, and automation.
- [ ] Ship boarding and rescue once the shared schematic/team framework is stable.

## Expansion 4 — New Perspectives

- [ ] Select and ship a small coherent set of alternative campaigns.
- [ ] Prioritize Bright Yellow and Dark Orange-Yellow campaigns because they capitalize on the territorial expansion.

## Expansion 5 — Cooperative Command

- [ ] Ship cooperative roles only after networking, synchronization, reconnect, automation fallback, and multi-hour acceptance are release-ready.

# Final Program Acceptance

- [ ] Factions expand through readable territorial fronts rather than arbitrary map jumps.
- [ ] Every capture is traceable to a legal invasion, beachhead, explicit event, or authorized scenario.
- [ ] Raids matter without causing instant ownership churn.
- [ ] Supply, isolation, control, and pressure create understandable strategic decisions.
- [ ] Bright Yellow and Dark Orange-Yellow remain visually and mechanically distinguishable while sharing the legacy Yellow ship catalog.
- [ ] The Yellow civil war can evolve into multiple systemic outcomes.
- [ ] The world retains meaningful evidence of battles, losses, rebuilding, commanders, and player decisions.
- [ ] Flagship and boarding systems deepen command responsibility without requiring 3D environments or synthetic crew media.
- [ ] Alternative campaigns reuse one coherent simulation while offering distinct command fantasies.
- [ ] Cooperative command preserves playability when roles are empty or players disconnect.
- [ ] The completed program fulfills its guiding identity: the war moves through territory, factions remember what happened, and every ship belongs to a real political conflict.

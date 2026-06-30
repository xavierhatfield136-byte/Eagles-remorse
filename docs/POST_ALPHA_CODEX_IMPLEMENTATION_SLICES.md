# Post-Alpha Codex Implementation Slices

Date: 2026-06-29  
Status: Execution plan derived from `POST_ALPHA_EXPANSION_MASTER_CHECKLIST.md`

## Operating Rule

Each job below is a separate implementation milestone. Do not combine jobs unless the preceding job is implemented, tested, manually accepted, and committed as a stable baseline. A job is not complete when only its data model exists; it needs a playable or inspectable flow, persistence where required, focused tests, and updated documentation.

Track B remains locked until Jobs 1–9 complete Track A acceptance.

## Expansion 1A–1E Execution Map

| Release slice | Codex jobs | Required predecessor |
| --- | --- | --- |
| Expansion 1A — Territory Fronts Foundation | Jobs 1, 3, and the minimal legality query from Job 2 | Shipped alpha baseline |
| Expansion 1B — Legal Operations | Job 2 plus basic operation persistence and player explanations from Jobs 3–4 | Expansion 1A accepted |
| Expansion 1C — Yellow Split | Job 5 | Expansion 1B accepted |
| Expansion 1D — Yellow Civil War | Job 6 plus the minimum control progression from Job 7 | Expansion 1C accepted |
| Expansion 1E — Supply, Pressure, And Outcomes | Jobs 7–9 | Expansion 1D accepted |

Do not run an entire release slice as one prompt when its mapped jobs remain independently useful. The release slices define packaging; the jobs define safe implementation turns.

## First Codex Slice

Implement only Job 1 / Phase 1 Minimum Playable Version.

### Required Work

- [ ] Add stable territory IDs.
- [ ] Add territory owner and controller state.
- [ ] Add a validated territory adjacency graph.
- [ ] Add a deterministic basic legal-invasion target query.
- [ ] Add save/load support for territory state and graph version.
- [ ] Add a debug view showing territory ID, owner, controller, adjacency, and legal invasion targets.
- [ ] Add automated tests proving factions cannot select non-adjacent capture targets.

### Hard Scope Exclusions

- [ ] Do not implement the Yellow faction split.
- [ ] Do not implement supply or isolation.
- [ ] Do not implement morale, legitimacy, resistance, resources, or shipyards.
- [ ] Do not implement front pressure or advanced faction-director behavior.
- [ ] Do not implement raids or invasions beyond the basic legal-target query.
- [ ] Do not build polished strategic overlays.
- [ ] Do not alter tactical combat except where territory inspection or debugging strictly requires integration.
- [ ] Do not implement any Track B feature.

### Success Standard

- [ ] Tests and the debug UI independently demonstrate the same legal adjacency rules.
- [ ] Existing campaign behavior remains functional.
- [ ] Save/load preserves the minimal territory foundation.
- [ ] No deferred system is represented by a misleading stub or completion claim.

### Copy-Ready Implementation Prompt

> Implement only the Phase 1 Minimum Playable Version from the Post-Alpha Expansion Master Checklist. Add stable territory IDs, owner/controller state, a validated territory adjacency graph, a deterministic legal invasion target query, save/load support for territory state and graph version, a debug view showing territory ID, owner, controller, adjacency, and legal invasion targets, and automated tests proving factions cannot select non-adjacent capture targets. Do not implement the Yellow faction split, supply, morale, legitimacy, resistance, resources, shipyards, front pressure, polished overlays, raids, invasions, or Track B features. Do not alter tactical combat except where territory inspection or debugging strictly requires integration. Success is measured by tests and debug UI proving that legal adjacency rules work while existing campaign behavior remains functional.

## Copy-Ready Track A Release Prompts

These prompts are execution contracts. Use only the prompt for the next accepted release slice, and retain the narrower job boundaries below it during implementation.

### Codex Slice 1 — Expansion 1A: Territory Fronts Foundation

#### Do

- [ ] Add stable `TerritoryId` values.
- [ ] Add authoritative territory owner and military-controller state.
- [ ] Add and validate the territory adjacency graph.
- [ ] Add one canonical deterministic adjacency query.
- [ ] Add a basic legal-invasion target query without implementing full invasion operations.
- [ ] Save and load owner, controller, and graph version.
- [ ] Add a debug inspector for territory ID, owner, controller, adjacency, and legal invasion targets.
- [ ] Add focused tests proving factions cannot skip territories.
- [ ] Preserve existing campaign and tactical behavior.

#### Do Not

- [ ] Do not split Yellow.
- [ ] Do not add supply, isolation, morale, legitimacy, resistance, resources, or shipyards.
- [ ] Do not add front pressure or advanced director scoring.
- [ ] Do not implement raids or invasions as full operations.
- [ ] Do not build polished strategic overlays.
- [ ] Do not alter tactical combat beyond strictly necessary territory inspection integration.
- [ ] Do not implement any Track B feature.

#### Completion Gate

- [ ] Debug output and automated tests agree on all legal adjacent targets.
- [ ] Save/load preserves the foundation deterministically.
- [ ] Existing campaign smoke tests pass.

#### Prompt

> Implement only Expansion 1A — Territory Fronts Foundation. Add stable TerritoryId values, authoritative territory owner/controller state, a validated territory adjacency graph, a canonical deterministic adjacency query, a basic legal invasion target query, save/load support for owner/controller and graph version, a debug inspector showing territory ID/owner/controller/adjacency/legal invasion targets, and focused tests proving factions cannot skip territories. Do not split Yellow; add supply, isolation, morale, legitimacy, resistance, resources, shipyards, front pressure, polished overlays, or full raid/invasion operations; alter tactical combat except where strictly required for territory inspection; or implement Track B. Preserve existing campaign behavior and finish only when tests, save/load, and debug output agree.

### Codex Slice 2 — Expansion 1B: Legal Operations

#### Do

- [ ] Define raid and invasion as distinct operation types.
- [ ] Require ordinary capture attempts to originate from adjacent controlled territory.
- [ ] Ensure raids can damage valid targets but cannot change ownership.
- [ ] Return structured rejection reasons for illegal targets.
- [ ] Route basic AI target selection through the canonical legality service.
- [ ] Persist minimal operation type, origin, target, status, and progress.
- [ ] Show legal targets and invalid-target explanations in a basic player-facing view.
- [ ] Add legality, persistence, AI-selection, and regression tests.

#### Do Not

- [ ] Do not split Yellow.
- [ ] Do not add supply, front pressure, beachheads, or full occupation progression.
- [ ] Do not redesign faction diplomacy or tactical combat.
- [ ] Do not begin Track B.

#### Completion Gate

- [ ] AI and player operations use the same legality rules.
- [ ] Raids never transfer ownership.
- [ ] Non-adjacent invasions are rejected with an inspectable reason.

#### Prompt

> Implement only Expansion 1B — Legal Operations on top of accepted Expansion 1A. Define raid and invasion as separate operation types; require adjacent controlled origin territory for ordinary capture attempts; prevent raids from changing ownership; return structured invalid-target reasons; route basic AI selection through the canonical legality service; persist minimal operation state; expose basic legal-target and rejection information to the player; and add focused tests. Do not split Yellow, add supply, front pressure, beachheads, advanced occupation, Track B systems, or unrelated tactical changes.

### Codex Slice 3 — Expansion 1C: Yellow Split

#### Do

- [ ] Add stable Bright Yellow and Dark Orange-Yellow faction IDs.
- [ ] Add the required Blue/Green/Bright Yellow and Red/Dark Orange-Yellow alliances.
- [ ] Make the Yellow successors mutually hostile.
- [ ] Make both factions reference the existing Yellow hull catalog.
- [ ] Add distinct names, insignia, transponder labels, icons, and pattern overlays.
- [ ] Ensure accessibility does not rely on hue alone.
- [ ] Migrate legacy Yellow fleets, territory, diplomacy, and active state deterministically.
- [ ] Update spawning, targeting, support, trade, diplomacy, archives, and save/load.
- [ ] Add alliance-matrix, migration, accessibility, and every-hull spawn tests.

#### Do Not

- [ ] Do not duplicate Yellow hull definitions or rebalance the shared roster incidentally.
- [ ] Do not add the full civil-war start state or outcome system.
- [ ] Do not add supply or front pressure.
- [ ] Do not begin Track B.

#### Completion Gate

- [ ] Both factions can coexist and fight with readable shared-origin ships.
- [ ] Legacy Yellow saves migrate without losing ship identity or records.
- [ ] No gameplay rule identifies the factions by color alone.

#### Prompt

> Implement only Expansion 1C — Yellow Split on top of accepted Expansions 1A and 1B. Add stable Bright Yellow and Dark Orange-Yellow IDs, the required alliance/hostility matrix, shared references to the legacy Yellow hull catalog, distinct accessible labels/insignia/icons/patterns/transponders, deterministic migration of legacy Yellow state, and integration with spawning, targeting, support, trade, diplomacy, archives, and saves. Add focused relationship, migration, accessibility, and hull-spawn tests. Do not duplicate or incidentally rebalance Yellow hulls, build the full civil war, add supply/front pressure, or begin Track B.

### Codex Slice 4 — Expansion 1D: Yellow Civil War

#### Do

- [ ] Assign viable connected starting territory to both Yellow factions.
- [ ] Create at least one readable contested internal frontier.
- [ ] Add Yellow-versus-Yellow raids and invasions using the canonical rules.
- [ ] Add basic Red support for Dark Orange-Yellow.
- [ ] Add basic Blue/Green support for Bright Yellow.
- [ ] Add minimum gradual control progression needed to prevent instant map flips.
- [ ] Add one complete systemic and persisted civil-war outcome.
- [ ] Add balance, legality, persistence, and manual acceptance scenarios.

#### Do Not

- [ ] Do not add every planned mission or outcome family.
- [ ] Do not add the complete supply and front-pressure simulation.
- [ ] Do not create special civil-war capture rules that bypass canonical operations.
- [ ] Do not begin Track B.

#### Completion Gate

- [ ] Both Yellow factions survive a reasonable opening simulation unless player or systemic events decide otherwise.
- [ ] Civil-war actions obey ordinary territorial legality.
- [ ] One outcome can be reached, explained, saved, loaded, and reflected in campaign state.

#### Prompt

> Implement only Expansion 1D — Yellow Civil War on top of accepted Expansions 1A–1C. Create viable connected starting territory for both Yellow factions, at least one contested internal frontier, Yellow-versus-Yellow operations using canonical raid/invasion rules, basic coalition support, minimum gradual control progression, and one complete systemic persisted civil-war outcome. Add focused balance, legality, persistence, and manual acceptance coverage. Do not add every mission/outcome, full supply or front pressure, special rule bypasses, or Track B systems.

### Codex Slice 5 — Expansion 1E: Supply, Pressure, And Outcomes

#### Do

- [ ] Add faction supply sources, routes, capacity, interdiction, isolation, and restoration.
- [ ] Prevent isolated territories from launching onward invasions.
- [ ] Add inspectable front-pressure and director scoring.
- [ ] Add full Secure, Pressured, Contested, Occupied, and Integrated behavior.
- [ ] Add the remaining approved civil-war outcome families and campaign consequences.
- [ ] Add UI and diagnostics explaining supply, pressure, legal actions, and outcomes.
- [ ] Add deterministic long-campaign simulations and illegal-capture detection.
- [ ] Complete save migration, accessibility, performance, balance, and soak acceptance.

#### Do Not

- [ ] Do not use Expansion 1E to begin War Memory or any other Track B feature.
- [ ] Do not hide director decisions behind uninspectable random scoring.
- [ ] Do not let supply or pressure bypass canonical capture legality.
- [ ] Do not mark Track A complete without full campaign evidence.

#### Completion Gate

- [ ] Cutting and restoring supply changes outward invasion legality deterministically.
- [ ] Director decisions expose their decisive factors.
- [ ] All approved outcome families persist and affect the wider campaign.
- [ ] Track A passes migration, accessibility, performance, balance, determinism, and long-soak acceptance.

#### Prompt

> Implement Expansion 1E — Supply, Pressure, And Outcomes only after Expansions 1A–1D are accepted. Add supply sources/routes/capacity/interdiction/isolation/restoration, prevent isolated territories from launching invasions, add inspectable front-pressure and faction-director scoring, complete the territorial control-state behavior, add the remaining approved Yellow civil-war outcomes and consequences, expose clear UI/diagnostics, and complete deterministic simulation, migration, accessibility, performance, balance, and soak coverage. Do not begin Track B, conceal AI decisions, bypass canonical capture legality, or claim completion without full campaign evidence.

## Job 1 — Territory Graph Foundation

### Goal

Establish the smallest authoritative territory model without adding supply, pressure, civil-war politics, or advanced overlays.

### In Scope

- [ ] Add stable territory IDs.
- [ ] Add territory owner and military controller fields.
- [ ] Define graph edges from strategic lanes or routes.
- [ ] Add canonical adjacency and neighbor queries.
- [ ] Validate duplicate IDs, dangling edges, self-links, and unintended disconnected regions.
- [ ] Add a basic territory inspector or debug display.
- [ ] Show territory ID, owner, controller, and adjacent territory IDs.
- [ ] Add focused unit and graph-validation tests.

### Explicitly Out Of Scope

- [ ] Do not split Yellow.
- [ ] Do not add supply, morale, legitimacy, resistance, front pressure, occupation, or beachheads.
- [ ] Do not redesign the full strategic map UI.

### Completion Gate

- [ ] Existing campaigns still run.
- [ ] Territory inspection works in a live campaign.
- [ ] Tests prove graph answers are deterministic and valid.

## Job 2 — Legal Operation Rules

### Goal

Introduce raid and invasion as distinct legal concepts and prevent non-adjacent territorial attacks.

### In Scope

- [ ] Define minimal raid and invasion operation types.
- [ ] Add a legal raid-target query.
- [ ] Add a legal invasion-target query.
- [ ] Require an adjacent controlled origin for ordinary invasions.
- [ ] Ensure raids cannot transfer ownership.
- [ ] Return structured rejection reasons for illegal targets.
- [ ] Route AI candidate generation through the same legality service used by UI and tests.
- [ ] Add tests for adjacency, hostile target, ownership, allied access, and skipped-territory rejection.

### Completion Gate

- [ ] No AI capture operation can target a non-adjacent territory.
- [ ] Raid and invasion behavior cannot be confused by callers.
- [ ] Rejection reasons are inspectable in diagnostics.

## Job 3 — Save/Load And Territory Tests

### Goal

Make the minimal territory and operation foundation durable before adding more factions or rules.

### In Scope

- [ ] Persist territory ID, owner, controller, and graph version.
- [ ] Persist basic active operation type, origin, target, and status.
- [ ] Add a deterministic legacy-save initialization path.
- [ ] Add round-trip save/load tests.
- [ ] Add missing-ID, changed-graph-version, and invalid-operation recovery tests.
- [ ] Add a campaign transition regression test.
- [ ] Add a small randomized-graph legality test.

### Completion Gate

- [ ] Saving and loading cannot create, remove, or redirect legal invasion paths unexpectedly.
- [ ] Legacy saves receive a documented deterministic territory state.

## Job 4 — Basic Territory Map Overlay

### Goal

Make the new rules understandable to the player without building every planned overlay.

### In Scope

- [ ] Show basic territory ownership.
- [ ] Show selected territory details.
- [ ] Highlight adjacent territories.
- [ ] Highlight legal raid and invasion targets distinctly.
- [ ] Show why a selected invalid target is illegal.
- [ ] Show owner and controller separately when different.
- [ ] Add keyboard and controller selection support.
- [ ] Add non-color markers and test existing accessibility palettes.
- [ ] Add screenshot or manual readability acceptance at supported resolutions.

### Completion Gate

- [ ] A player can predict every currently legal invasion target from the map.
- [ ] The overlay agrees exactly with AI and rules queries.

## Job 5 — Yellow Faction Split

### Goal

Replace legacy Yellow with two political factions only after territorial legality is stable.

### In Scope

- [ ] Add stable Bright Yellow and Dark Orange-Yellow faction IDs.
- [ ] Add Bright Yellow alliance with Blue/player and Green.
- [ ] Add Dark Orange-Yellow alliance with Red.
- [ ] Make both Yellow successors mutually hostile.
- [ ] Reference the same legacy Yellow hull catalog from both factions.
- [ ] Add faction names, insignia, transponder labels, icons, and map patterns.
- [ ] Add save migration for legacy Yellow fleets and territories.
- [ ] Update hostility, support, trade, diplomacy, spawning, archives, and targeting queries.
- [ ] Add pairwise alliance tests and every-hull spawn tests.
- [ ] Verify both factions remain distinguishable without hue.

### Completion Gate

- [ ] No duplicated Yellow hull definitions exist.
- [ ] Existing Yellow ships migrate without losing identity or records.
- [ ] Mixed Yellow-versus-Yellow combat remains readable.

## Job 6 — Civil-War Start State

### Goal

Create a viable, readable Yellow civil-war frontier using the established territory and faction systems.

### In Scope

- [ ] Assign connected starting territory to both Yellow successors.
- [ ] Create at least one contested internal frontier.
- [ ] Place meaningful disputed infrastructure and strategic routes.
- [ ] Ensure both factions have viable supply-independent starting strength for this milestone.
- [ ] Enable legal civil-war raid and invasion candidate generation.
- [ ] Enable coalition support relationships without transferring ownership.
- [ ] Add a deterministic campaign start fixture and balance harness.

### Completion Gate

- [ ] Neither Yellow faction is deterministically eliminated in the opening simulation without a deliberate balance reason.
- [ ] All civil-war territorial actions obey the same adjacency rules as other factions.

## Job 7 — Raids, Invasions, And Control Progress

### Goal

Replace instant territory flipping with readable operations and gradual control change.

### In Scope

- [ ] Let raids damage fleets, stations, production, or local control without changing ownership.
- [ ] Add Secure, Pressured, Contested, Occupied, and Integrated states.
- [ ] Add invasion commitment, progress, defender intervention, failure, withdrawal, and occupation.
- [ ] Store owner, controller, and control state separately.
- [ ] Prevent occupied territory from immediately launching new invasions.
- [ ] Add basic attack-opportunity and defensive-urgency scoring.
- [ ] Show active operation and control progress on the map.
- [ ] Persist every new state and add transition tests.

### Completion Gate

- [ ] A territory cannot flip from one ordinary battle alone.
- [ ] Players have a visible intervention window during contested control.
- [ ] Raid outcomes and invasion outcomes remain mechanically distinct.

## Job 8 — Supply And Isolation

### Goal

Make connected fronts and salients strategically meaningful.

### In Scope

- [ ] Define faction supply sources.
- [ ] Calculate supply through controlled territory and permitted allied routes.
- [ ] Add supplied, strained, undersupplied, isolated, and collapsing states.
- [ ] Prevent isolated territories from originating invasions.
- [ ] Let isolated territories defend with remaining local reserves.
- [ ] Apply supply to readiness, repair, reinforcement, and invasion progress.
- [ ] Explain supply paths and breaks in UI and diagnostics.
- [ ] Add multi-source, blockade, chokepoint, encirclement, and restoration tests.

### Completion Gate

- [ ] Cutting a connecting territory closes illegal onward expansion immediately.
- [ ] Restoring a valid supply path restores legal options deterministically.

## Job 9 — Yellow Civil-War Outcomes

### Goal

Allow the civil war to resolve systemically and influence the wider campaign.

### In Scope

- [ ] Add Bright Yellow reunification.
- [ ] Add Dark Orange-Yellow domination.
- [ ] Add negotiated settlement or reunification.
- [ ] Add durable partition.
- [ ] Add mutual collapse or fragmentation.
- [ ] Define territorial, fleet, alliance, economic, mission, and ending consequences.
- [ ] Require outcomes to emerge from accumulated campaign state rather than one isolated choice.
- [ ] Persist resolved and unresolved outcome state.
- [ ] Add manual acceptance scenarios for every outcome family.

### Track A Completion Gate

- [ ] Run a full campaign soak with no illegal captures.
- [ ] Pass save migration, accessibility, performance, deterministic simulation, and map-readability acceptance.
- [ ] Update shipped campaign documentation before unlocking Track B.

## Job 10 — War Memory Foundation

### Goal

Begin Track B only after Track A acceptance by creating a bounded factual history of the living war.

### In Scope

- [ ] Record major battles, territorial changes, station destruction, reconstruction, and significant fleet losses.
- [ ] Preserve a bounded authoritative event ledger through save/load.
- [ ] Add one searchable or chronological history view.
- [ ] Generate concise summaries only from recorded facts.
- [ ] Add retention, save-size, load-time, and long-campaign tests.

### Explicitly Out Of Scope

- [ ] Do not begin flagship operations, boarding, alternative campaigns, or cooperative command in this job.
- [ ] Do not generate synthetic crew faces, videos, or voices.

### Completion Gate

- [ ] The campaign can accurately answer what happened, where, when, and to whom without inventing facts.
- [ ] The history remains usable and performant in a late campaign.

## Handoff Rule For Later Track B Work

After Job 10, create new bounded execution slices for persistent commanders, flagship operations, boarding/rescue, one alternative campaign at a time, and finally a cooperative-command feasibility prototype. Do not copy the entire remaining master checklist into a single implementation request.

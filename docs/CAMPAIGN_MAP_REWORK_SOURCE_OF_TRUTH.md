# Campaign Map, Intelligence, and Invasion Rework — Source of Truth

**Status date:** 2026-07-02  
**Current implementation snapshot:** completed working tree based on commit `9b97970204634037196fec3fcbf3479da9ffd0f2` (`stabilization`, 2026-07-01); the rework changes are not yet committed.  
**Purpose:** Explain where the campaign-map changes came from, what was implemented, the architecture decisions that govern it, and how completion was verified.

## How to use this document

This is the canonical roadmap for this workstream going forward.

The execution tracker derived from this roadmap is `docs/CAMPAIGN_MAP_REWORK_IMPLEMENTATION_CHECKLIST.md`.

- Conversation attachments are design inputs and historical context, not independently active implementation orders.
- Repository code and tests determine what is actually implemented.
- A later, narrower handoff overrides an earlier broad wishlist when they conflict.
- `DONE` means code and focused regression coverage exist in the repository.
- `PARTIAL` means a useful foundation exists, but the roadmap outcome is not complete.
- `PLANNED` means proposed by the roadmap but not yet authorized as the next implementation milestone.
- `DEFERRED` means deliberately excluded from the current milestone.

The user approved the complete implementation checklist on 2026-07-02. Milestones 2–11 were therefore implemented as one authorized workstream while retaining their individual exit gates.

## Executive summary

The work did not originate from one specification. It evolved through five user-provided handoffs and one parallel focused-faction-attack workstream:

1. A broad player-experience wishlist identified route clutter, ambiguous circles, ghost contacts, marker teleporting, missing sensor clarity, and unclear invasions.
2. A milestone roadmap translated that wishlist into architecture work.
3. An engineering handoff added discovery, authority, intel, persistence, and test constraints.
4. A safety addendum prohibited a full rewrite and authorized repository discovery first.
5. The resulting discovery report identified concrete causes in the existing code.
6. A narrow Milestone 1 handoff authorized only immediate map-truth and route-clarity fixes.
7. In parallel, focused faction attacks added one offensive commitment per faction and hollow attack arrows behind a feature flag.

As of the status date, Milestones 0–11 are complete. The focused faction-attack foundation now has canonical fleet assignment, multi-source intelligence, intel-aware rendering, full operation lifecycle/progress, sensor and map presentation, friendly reporting, inspectors, checkpoint-schema v5 persistence/migration, and final regression/soak coverage.

## Source provenance

| ID | Source | What it contributed | Authority now |
|---|---|---|---|
| S1 | `Campaign Invasion, Sensor, and Map Clarity Rework Checklist` | Original player-facing goals: distinct sites and territory, fewer routes, real invasions, sensor circle, friendly reporting, no ghosts, inspectors, legend, save/load, and regression tests. | Product vision. Broadest source; not a direct all-at-once implementation order. |
| S2 | `Campaign Map, Intelligence, and Invasion System Rework — Core Goal` | Converted S1 into milestones for map cleanup, fleet truth, knowledge, visual language, operations, reporting, inspectors, persistence, and testing. | Roadmap input. Superseded where S3–S5 are more precise. |
| S3 | `Campaign Map, Intelligence, and Invasion System Rework — Engineering Handoff Specification` | Added Milestone 0, strict simulation/render/intel separation, intel precision, operation invalidation rules, persistence rules, and detailed acceptance tests. | Main long-term architecture source. |
| S4 | `Final Architecture Addendum and Milestone 0 Handoff` | Explicitly prohibited implementing the whole feature; required discovery first; added multi-source observations, deterministic intel timing, assignment repair rules, and rally/target overlays. | Governs long-term design constraints. Milestone 0 authorization is fulfilled. |
| S5 | `Campaign Map Rework — Milestone 1 Handoff` | Authorized the narrow implementation completed on 2026-07-01: read-only marker generation, no presentation teleporting, one linked marker, no stale live markers, route gating, and narrow site hit testing. | Governing specification for completed Milestone 1. |
| S6 | `docs/CAMPAIGN_MAP_DISCOVERY_REPORT.md` | Repository evidence: existing authorities, rendering paths, contact coupling, route-spam causes, save paths, reproducible bugs, and a safe Milestone 1 plan. | Evidence, not product scope. |
| S7 | `docs/FOCUSED_FACTION_ATTACK_CHECKLIST.md` | Parallel workstream: one offensive target per faction, stable IDs, transactional commitments, ownership gates, migration, persistence, diagnostics, soak tests, and hollow arrows. | Implemented parallel foundation. It is not the complete S1–S4 invasion system. |

### Original attachment locations

These are the exact conversation artifacts from which S1–S5 were derived:

- S1: `C:\Users\xhatf\.codex\attachments\979a7e2e-63ce-4389-9a25-bd0a3b63dd48\pasted-text.txt`
- S2: `C:\Users\xhatf\.codex\attachments\1150b912-b22c-4449-b365-60741c709416\pasted-text.txt`
- S3: `C:\Users\xhatf\.codex\attachments\b740f56a-800f-4fb3-ab7f-f2be4693d70d\pasted-text.txt`
- S4: `C:\Users\xhatf\.codex\attachments\0a92f0e3-0373-4cef-8c04-a14a6777bb09\pasted-text.txt`
- S5: `C:\Users\xhatf\.codex\attachments\a7ebe548-6bd1-449a-98a3-65fe4d2c46b1\pasted-text.txt`

The attachment IDs are historical references. This repository document should be used for future status and scope decisions.

## Non-negotiable architecture decisions

These decisions recur across the refined handoffs and remain binding unless deliberately revised:

1. `CampaignSystem.CampaignState` and its existing campaign-force storage remain the simulation authority. Do not create a parallel fleet simulation manager.
2. Simulation owns physical fleet identity, position, lifecycle, mission, route, and destruction.
3. Rendering and marker/read-model generation are read-only projections.
4. Fleet existence, player knowledge, and rendering eligibility are different concepts.
5. Strategic operation intent is not evidence of an exact live fleet position.
6. Only valid exact observations may eventually produce ordinary live hostile fleet icons.
7. Approximate or expired intelligence must never silently use a fleet's current simulation position.
8. Multiple intel sources must coexist; losing one source must not erase another valid source.
9. Operation and fleet references must use stable IDs and be updated atomically.
10. Rally point and invasion target are temporary overlays on real sites, not permanent site types.
11. Exact live detection must be reevaluated after load rather than blindly restored as truth.
12. Existing route, site, event, save, and fleet infrastructure should be reused.

## Completed work

### DONE — Milestone 0: repository discovery and baseline

**Source:** S3 and S4.  
**Evidence:** `docs/CAMPAIGN_MAP_DISCOVERY_REPORT.md`.

Completed findings include:

- Identified the existing fleet, lifecycle, movement, contact, renderer, route, site, and checkpoint paths.
- Identified overlapping `CampaignForce` and `GalaxySearchGroup` movement/display authority.
- Reproduced marker snap-back caused by linked projection resynchronization and double movement.
- Reproduced route clutter caused by unconditional full-network rendering and implicit site selection.
- Confirmed stale/ghost contact behavior and tests that intentionally protected it.
- Confirmed visibility did not directly delete hidden fleets, while contact memory could improperly preserve physical forces.
- Identified broad site hit testing and overlapping circular visual layers as the empty-circle interaction problem.
- Confirmed that focused faction attack work already existed and must not be replaced by a second operation model.

### DONE — Milestone 1: map truth and immediate clarity

**Source:** S5, informed by S6.  
**Primary files:** `src/CampaignSystem.java`, `src/Renderer.java`, `src/UISystem.java`.  
**Primary tests:** `test/CampaignMapClarityMilestoneOneTest.java`, `test/CampaignNpcFleetAiTest.java`.

Implemented behavior:

- Removed simulation mutation from marker/read-model generation paths touched by this milestone.
- Disabled presentation-density logic that moved real fleets near the player to guarantee visible contacts.
- Suppressed the `GalaxySearchGroup` marker when a linked `CampaignForce` represents the same contact.
- Stabilized linked lookup using link IDs rather than mutable display names.
- Prevented linked campaign forces from receiving a second independent movement advance.
- Reconciled linked positions after legacy name-based seed initializers so later initialization cannot snap the marker backward.
- Suppressed duplicate linked campaign-force projections.
- Suppressed stale campaign-force and lost search-group contacts from the normal live-marker layer.
- Prevented recent contact memory from restoring or preserving destroyed, shipless physical forces.
- Preserved hidden, non-destroyed fleets in campaign simulation while omitting their normal map marker.
- Hid the full route graph outside debug and an explicitly selected `ROUTES` overlay.
- Scoped route rendering to edges connected to the explicitly selected site.
- Gated selected-territory edges behind the same explicit route context.
- Replaced broad site click selection with a zoom-aware marker-sized hit radius.
- Kept territory/control halos non-interactive outside the site glyph.

Verification completed on 2026-07-01:

- Focused Milestone 1 regressions passed.
- A broader set of 169 campaign, conquest, UI, map-discipline, NPC-fleet, and focused-attack tests passed.
- `git diff --check` passed before the implementation was committed.

### DONE — Focused faction attack commitments

**Source:** S7.  
**Implementation:** `src/FactionAttackCommitmentSystem.java` integrated through `src/CampaignSystem.java`.  
**Feature flag:** `focused_faction_attacks=true` with the system-property override `game.feature.focused_faction_attacks`.  
**Tests:** `test/FocusedFactionAttackChecklistTest.java`; soak harness in `src/FocusedFactionAttackSoakHarness.java`.

Implemented behavior:

- Defines Green, Yellow, Dark Yellow, and Red offensive attack slots.
- Limits each strategic faction slot to one territorial capture target at a time.
- Allows multiple fleets to support the same committed target.
- Reserves a target transactionally and returns structured rejection reasons.
- Keeps defensive/support missions outside the offensive slot.
- Uses stable origin, target, operation, and fleet IDs.
- Supports planned/staging/active/resolving/hold/cooldown/completed/aborted phases.
- Requires an active matching resolving commitment and a minimum duration before ownership transfer.
- Handles completion, abort, expiry, missing IDs, destroyed fleets, captured origin, changed target ownership, and path failure.
- Migrates legacy multi-target capture orders deterministically.
- Persists commitment state through the existing checkpoint payload.
- Projects one hollow attack arrow per active commitment without using the renderer as authority.
- Exposes diagnostics and includes deterministic and soak coverage.

This began as a narrow offensive-commitment system. Milestones 6–9 now extend it with the requested muster/travel/assault lifecycle, operation intelligence, intent rendering, and operation inspector without creating a competing operation authority.

### Baseline commit containing Milestones 0–1

Commit `9b97970204634037196fec3fcbf3479da9ffd0f2` contains both the focused-attack work and Milestones 0–1. Its scope was:

- 15 files changed.
- 2,034 insertions and 202 deletions.
- New discovery and focused-attack documentation.
- New commitment model and soak harness.
- Campaign-system, renderer, UI, feature-flag, and checkpoint integration.
- New map-clarity and focused-attack regression suites.

Because these workstreams share one commit, this document separates them conceptually even though Git does not.

## Conflict resolutions

The historical sources contain several apparent contradictions. These are the current decisions.

### Route data versus route display

- S7 required the focused-attack feature not to delete or alter route geometry.
- S5 required route clutter to be hidden by default.
- **Resolution:** underlying route geometry remains available to simulation and diagnostics; the normal renderer hides it unless the player explicitly selects the `ROUTES` overlay, then shows only selected-site edges. Debug may show the full graph.

### Stale contacts versus future approximate intelligence

- S1 and S5 require stale contacts not to appear as live fleets.
- S3 and S4 allow future approximate intelligence with age, confidence, and uncertainty.
- **Resolution:** Milestone 1 hid stale live markers. Milestones 3–4 added a visually distinct, non-clickable approximate-intel symbol that never masquerades as a physical fleet or uses the fleet's current position without a valid exact observation.

### Full operation model versus focused commitments

- S1–S3 describe a broad rally/muster/travel/assault operation object.
- S7 implements a smaller attack-slot and ownership-safety model.
- **Resolution:** `FactionAttackCommitmentSystem` is the existing foundation to extend or adapt. Do not add a competing operation manager. Discovery must determine whether the broader lifecycle belongs inside it or in an already-authoritative strategic-operation structure.

### Saved contacts versus load-time truth

- Existing checkpoints contain visibility/contact fields.
- S4 says exact live detection must not persist blindly.
- **Resolution:** no save migration was attempted in Milestone 1. Milestone 10 now preserves eligible durable strategic knowledge while rebuilding live sensor and communication observations immediately after load.

### Mission and lifecycle enums

- Early roadmaps propose cleaner mission and lifecycle enums.
- S5 explicitly prohibited a fleet enum overhaul.
- **Resolution:** current enums remain for now. Any consolidation belongs to the authoritative fleet-identity milestone and must include compatibility tests.

## Current status matrix

| Capability | Status | Notes |
|---|---|---|
| Repository authority/path discovery | DONE | Documented in S6. |
| Marker/read-model purity | DONE | Marker, inspector, route, and operation projections remain read-only. |
| Presentation-driven fleet teleport removal | DONE | Real fleet positions are no longer moved to meet marker-density goals. |
| Single linked live marker | DONE | Campaign-force projection wins. |
| Linked snap-back prevention | DONE | Stable link-ID synchronization and one movement path. |
| Stale live-marker suppression | DONE | Internal memory may still exist. |
| Hidden-fleet persistence | DONE | Hidden does not imply destroyed; intel cannot change physical state. |
| Route graph hidden by default | DONE | Data retained; display gated. |
| Selected-site route scoping | DONE | Explicit `ROUTES` overlay only. |
| Territory halo non-interaction | DONE | Marker-sized site hit testing. |
| One offensive target per faction | DONE | Focused commitment feature. |
| Hollow committed-attack arrows | DONE | Intel-gated operation intent; unknown operations render no arrow. |
| Full canonical fleet identity/lifecycle cleanup | DONE | `CampaignForce.id` is canonical; linked search groups are controllers; operation assignments are atomic and repaired after load. |
| Explicit multi-source fleet intel records | DONE | Sensor, allied report, site radar, mission, and operation observations resolve deterministically. |
| Operation intel distinct from fleet intel | DONE | Operation records are keyed by stable operation ID and do not imply exact fleet positions. |
| Exact/approximate/strategic-only rendering | DONE | Exact contacts use fleet markers; approximate contacts use non-interactive intel symbols; strategic-only knowledge uses intent only. |
| Distinct site/territory/fleet visual language | DONE | Site glyphs/cards, faint halos, sensor ring, semantic legend, and layer priorities are covered. |
| Visible player sensor-range circle | DONE | Uses the same range formula as detection. |
| Friendly fleet reporting and communications | DONE | Allied reports are explicit observations; jamming removes reports without removing sensor observations or fleets. |
| Full rally/muster/sortie/travel/assault operation lifecycle | DONE | Legal transitions, zero-safe thresholds, real-fleet progress, invalidation, and atomic release are implemented. |
| Rally and target site overlays | DONE | Operation intent is projected over existing sites without changing site identity. |
| Fleet inspector | DONE | Reads resolved player knowledge and leaves unrevealed fields unknown. |
| Operation inspector/panel | DONE | Detail is gated by operation-intel precision. |
| Final contact/save migration | DONE | Checkpoint schema v5 persists durable intel and rebuilds live observations after load. |
| Full end-to-end and visual acceptance suite | DONE | 1,011 tests, deterministic soak, accessibility/input, and strict screenshot gates pass. |

## Completed roadmap

The following sequence came primarily from S3 and S4, adjusted for the pre-existing work. The user approved the complete checklist on 2026-07-02, and each milestone below met its exit gate.

### DONE — Milestone 2: authoritative fleet identity and lifecycle

**Goal:** Finish consolidating physical fleet truth without introducing a new authority.

Implemented work:

- Inventory every remaining object that can represent or advance a strategic fleet.
- Document the permanent relationship among `CampaignForce`, `GalaxySearchGroup`, tactical ships, strategic task forces, and campaign ship-pool records.
- Give every physical fleet a stable identity across simulation, encounters, save/load, and UI projection.
- Ensure only one system updates physical position and lifecycle for each fleet.
- Make destruction, retreat, docking, repair, resupply, and release rules explicit.
- Separate operation membership from physical fleet mission.
- Route all bidirectional operation assignment changes through atomic methods.
- Add deterministic load-time repair for missing or mismatched fleet/operation references.
- Preserve current campaign behavior unless a test demonstrates an authority bug.

Out of scope for this milestone:

- New intel UI.
- New operation panel.
- Visual redesign.
- Balance or ownership-rule changes.

Exit conditions:

- One stable physical identity per fleet.
- One position writer at a time.
- Hidden fleets continue simulating.
- Destroyed fleets cannot be restored by intel or presentation state.
- Assignment references remain consistent after update and load.

### DONE — Milestone 3: explicit multi-source intelligence

**Goal:** Replace overloaded visibility/contact fields with explicit observations and resolved player knowledge.

Implemented model:

- A fleet-intel record keyed by stable fleet ID.
- Zero or more observations per fleet.
- Observation source, precision, observed tick, expiry tick, confidence, known position, and uncertainty radius.
- Separate operation-intel records keyed by stable operation ID.
- Deterministic resolution of simultaneous sensor, allied-report, site-radar, mission, and operation sources.
- Atomic expiry during campaign updates.

Required rules:

- Losing one source removes only that observation.
- Exact live detection outranks approximate information.
- Operation knowledge never reveals exact fleet position by itself.
- Intel cannot create, destroy, heal, move, or retarget a physical fleet.
- Expired exact observations do not remain live icons.

### DONE — Milestone 4: strict intel-aware rendering

**Goal:** Make every hostile map symbol accurately communicate the precision of current knowledge.

Implemented work:

- Render ordinary fleet icons only for valid exact observations.
- Render approximate intel, if retained, as a distinct uncertainty area or intelligence symbol.
- Render strategic-only operation knowledge without physical fleet icons.
- Remove click/combat/movement-vector behavior from non-exact observations.
- Keep renderer interpolation visual-only and bounded between authoritative samples.
- Ensure every marker can explain which valid observation permits it to exist.

### DONE — Milestone 5: complete map visual language

**Goal:** Finish the player-facing clarity work started in Milestone 1.

Implemented work:

- Give real sites distinct icons, ownership, type cues, labels, and concise cards.
- Keep territory/control influence faint, background-only, and non-interactive.
- Add a distinct medium-sized player sensor-range ring tied to actual detection rules.
- Add a map legend for sites, fleets, territory, sensors, warnings, rally overlays, targets, and operation arrows.
- Establish explicit rendering priority so regions and arrows cannot obscure sites, fleets, events, or labels.
- Ensure entering or overlapping a site does not implicitly enable unrelated route layers.
- Validate minimum/default/maximum zoom and standard/ultrawide/high-density layouts.

### DONE — Milestone 6: full faction operation lifecycle

**Goal:** Extend the existing commitment foundation into deliberate, time-based operations.

Implemented work:

- Reconcile `FactionAttackCommitmentSystem` with existing strategic-operation structures before adding fields.
- Represent one stable operation ID, faction, rally site, target site, assigned fleet IDs, phase, timing, requirements, and outcome.
- Add explicit planning, mustering, ready, en-route, engagement, assault, capture, retreat/return, complete, failed, and cancelled transitions as appropriate to existing architecture.
- Define zero-safe muster calculations and explicit sortie thresholds.
- Derive muster and travel progress from real assigned fleets rather than timers alone.
- Require assigned fleets to travel from real origins to rally and target locations.
- Handle target capture, rally loss, diplomacy change, fleet destruction, retreat, path failure, and timeout.
- Release fleet assignments atomically on every completion or failure path.
- Keep ownership transfer behind the existing commitment/authoritative resolver safeguards.

This milestone extended the current commitment model rather than creating another operation truth source.

### DONE — Milestone 7: operation intent and arrows

**Goal:** Make known strategic intent understandable without implying nonexistent live contacts.

Implemented work:

- Derive arrows from authoritative operations/commitments only.
- Gate arrow detail by operation intelligence.
- Show rally-to-target intent, phase, progress, and ETA only to the precision the player knows.
- Keep arrows hollow/translucent, non-interactive, and behind sites, fleets, events, and hit targets.
- Clip arrow endpoints around source and target markers.
- Ensure unknown operations have no arrow and partial intel does not reveal assigned fleet positions.

The original hollow commitment arrow became the rendering foundation for the finished intel-aware feature.

### DONE — Milestone 8: friendly reporting and communications

**Goal:** Make allied positions visible through explicit communication observations rather than unconditional special cases.

Implemented work:

- Define which factions and fleets share reports with the player.
- Generate friendly-report observations from real fleet positions.
- Define update cadence, delay, jamming, relay coverage, and communication loss.
- Preserve player-sensor observations when friendly communication is lost.
- Mark delayed friendly reports clearly if delayed reporting is allowed.
- Never let communications state affect fleet existence.

### DONE — Milestone 9: fleet and operation inspectors

**Goal:** Let the player understand what a known fleet or operation is doing without leaking simulation truth.

Implemented fleet inspector fields:

- Display name, faction, known mission, known destination, known operation membership, status, source, age, confidence, and uncertainty.
- Show unknown fields as unknown rather than reading private simulation state.

Implemented operation inspector fields:

- Known rally site, target, phase, progress, known fleet count, ETA range, confidence, and last update.
- Focus the map on the operation's known rally, target, assigned exact contacts, and intent arrow.

### DONE — Milestone 10: save/load and migration

**Goal:** Persist durable strategic knowledge without restoring stale live truth.

Implemented work:

- Save physical fleets and operations through their authoritative stable IDs.
- Save durable operation knowledge and eligible approximate observations with timestamps/confidence.
- Do not persist renderer positions or exact live contact eligibility as unquestioned truth.
- Reevaluate sensors, communications, radar, and observation expiry immediately after load.
- Repair mismatched operation/fleet references deterministically.
- Migrate existing visibility/contact fields without duplicating fleets, operations, or observations.
- Preserve compatibility with existing campaign checkpoints.

### DONE — Milestone 11: final regression and acceptance coverage

**Goal:** Verify the complete architecture and player experience.

Coverage includes:

- Marker and route rendering never mutate simulation.
- No duplicate or snapping fleet markers.
- Exact, approximate, strategic-only, and unknown intel render correctly.
- Loss of detection hides presentation but does not delete simulation entities.
- Friendly reporting survives or fails according to explicit communications rules.
- Mustering, travel, target/rally invalidation, fleet destruction, retreat, and diplomacy changes resolve safely.
- Save/load rebuilds live observations and preserves durable knowledge.
- Sites, halos, arrows, events, and markers remain visually distinct and correctly clickable.
- Seeded long-running simulations respect ownership and operation limits.
- Visual checks cover zoom, aspect ratio, density, and accessibility.

## Completion evidence — 2026-07-02

Milestones 2–10 have dedicated regression suites:

- `CampaignFleetAuthorityMilestoneTwoTest`
- `CampaignMultiSourceIntelMilestoneThreeTest`
- `CampaignMapVisualLanguageMilestoneFiveTest`
- `FactionOperationLifecycleMilestoneSixTest`
- `CampaignOperationIntentMilestoneSevenTest`
- `CampaignFriendlyReportingMilestoneEightTest`
- `CampaignInspectorsMilestoneNineTest`
- `CampaignIntelPersistenceMilestoneTenTest`

Final Milestone 11 verification:

- `gradlew.bat check --console=plain`: aggregate build, accessibility, smoke, stability, test, and source-hygiene gate passed.
- `gradlew.bat test --console=plain`: 1,011 tests passed; zero failures, errors, or skipped tests.
- `gradlew.bat focusedFactionAttackSoak --console=plain`: three deterministic seeds ran through 600 simulated seconds successfully; report in `build/reports/focused-faction-attack-soak.csv`.
- `gradlew.bat screenshotRegression --console=plain`: strict campaign-map, fleet-board, strike-tab, tactical-HUD, and accessibility-HUD comparisons passed.
- `gradlew.bat phase10Accessibility --console=plain`: accessibility and input acceptance passed.
- `gradlew.bat verifySourceTreeHygiene --console=plain`: source-tree hygiene passed.

The visual baseline was deliberately regenerated after adding the shared-formula sensor ring, expanded legend, intel-aware operation presentation, and revised map hierarchy. Its strict comparison then passed without further drift.

## Continuing guardrails

The completed rework does not authorize unrelated architecture or balance changes. Continue to avoid:

- Create a new fleet manager, contact manager, operation manager, or route manager.
- Rewrite all campaign missions or lifecycle enums.
- Replace checkpoint serialization wholesale.
- Rebalance capture rates, pressure, economy, logistics, or diplomacy.
- Change tactical encounter generation.
- Treat all internal route geometry as player-visible.
- Reintroduce stale live fleet markers.
- Use operation intent to reveal exact fleet positions.
- Bypassing the completed milestone invariants with a second authority or presentation-only shortcut.

## Final architecture decisions

The completed rework rests on these prerequisite decisions:

1. `CampaignForce` remains the canonical physical fleet record.
2. `GalaxySearchGroup` remains a linked NPC behavior controller and projection source, not a second fleet identity.
3. `FactionAttackCommitmentSystem` is the operation-membership authority to extend with later rally/muster lifecycle work.
4. `CampaignForce.id` is the stable ID across tactical encounters and checkpoint round trips.

The detailed authority contract and regression evidence are in `docs/CAMPAIGN_FLEET_AUTHORITY.md`. Any future work should be treated as maintenance or a separately approved feature, not as an unfinished item from this checklist.

## Maintenance rules for this document

For any later maintenance milestone:

1. Update the status date and implementation commit.
2. Move completed items from the planned section into completed work.
3. Record any changed architecture decision and why.
4. Add the exact tests and verification command/results.
5. Record any source conflict and its resolution.
6. Keep new future milestones marked `PLANNED` until explicitly approved.

This avoids returning to the current situation where several overlapping handoffs make it difficult to tell design intent from completed code.

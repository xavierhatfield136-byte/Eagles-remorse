# Campaign Map, Intelligence, and Invasion Rework — Implementation Checklist

**Status date:** 2026-07-02  
**Source:** `docs/CAMPAIGN_MAP_REWORK_SOURCE_OF_TRUTH.md`  
**Scope:** Completed implementation record for Milestones 0–11 and the focused faction attack foundation.  
**Completion:** All 390 checklist controls are satisfied. Verification evidence is recorded in the source-of-truth document.

## Checklist rules

- [x] Preserve bounded milestone exit gates even when the user authorizes the complete roadmap as one workstream.
- [x] Add or update tests before changing milestone behavior.
- [x] Treat repository code and tests as implementation truth.
- [x] Update the source-of-truth document after each completed milestone.
- [x] Record the implementation snapshot and exact verification results; do not invent a commit before the changes are committed.
- [x] Do not mark a milestone complete while any exit criterion is unmet.

## Baseline already completed

These items are not remaining work; they establish the starting point for this checklist.

- [x] Milestone 0 repository discovery and baseline reproduction.
- [x] Milestone 1 marker authority and immediate map clarity.
- [x] Presentation-driven fleet teleport removal.
- [x] Single player-facing marker for linked search groups and campaign forces.
- [x] Linked marker snap-back prevention.
- [x] Stale live-marker suppression.
- [x] Hidden-fleet simulation persistence for covered behavior.
- [x] Full route graph hidden by default while route data remains intact.
- [x] Selected-site route scoping behind the explicit `ROUTES` overlay.
- [x] Territory/control halos made non-interactive outside the site marker.
- [x] Focused faction attack commitments and hollow commitment arrows.

## Global architecture guardrails

Apply these to every remaining milestone.

- [x] Keep existing campaign fleet storage as the simulation authority unless an approved migration explicitly replaces it.
- [x] Do not create a parallel fleet simulation authority.
- [x] Keep marker, inspector, route, and renderer projections read-only.
- [x] Keep physical fleet existence separate from player knowledge and rendering eligibility.
- [x] Never use operation intent as proof of an exact fleet position.
- [x] Never let intel create, destroy, heal, move, strengthen, or retarget a physical fleet.
- [x] Reuse existing fleet, route, site, event, checkpoint, and operation infrastructure.
- [x] Use stable IDs for fleets, sites, operations, and cross-references.
- [x] Make bidirectional operation/fleet updates atomic.
- [x] Treat rally and target roles as temporary overlays on real sites.
- [x] Preserve route geometry independently from route visibility.
- [x] Keep stale or approximate intel visually distinct from live physical contacts.
- [x] Preserve current behavior unless a regression test demonstrates an authority defect.

## Approval gate before Milestone 2

- [x] Decide whether `CampaignForce` remains the permanent physical fleet record or becomes a facade over an existing lower-level record.
- [x] Decide whether `GalaxySearchGroup` remains an independent behavior object, becomes a component/projection attached to a force, or is retired through migration.
- [x] Decide which existing strategic-operation structure absorbs the rally/muster lifecycle alongside `FactionAttackCommitmentSystem`.
- [x] Decide which stable fleet ID survives tactical encounter creation and checkpoint round trips.
- [x] Record all four decisions in the source-of-truth document.

## Milestone 2 — Authoritative fleet identity and lifecycle

**Goal:** Consolidate physical fleet truth without introducing a new authority.

### Discovery and design

- [x] Inventory every object that can represent a strategic fleet.
- [x] Inventory every method that can advance or overwrite strategic fleet position.
- [x] Inventory every method that can change fleet lifecycle, destruction, mission, route, or assignment.
- [x] Document the permanent relationship among `CampaignForce`, `GalaxySearchGroup`, tactical ships, strategic task forces, and campaign ship-pool records.
- [x] Identify legacy name-based synchronization and replace it with stable-ID relationships where required.
- [x] Define one canonical physical identity for every fleet across simulation, tactical encounters, save/load, and UI projections.

### Tests first

- [x] Add an invariant test proving one physical fleet identity survives strategic-to-tactical-to-strategic transitions.
- [x] Add an invariant test proving only one position writer is active for each fleet.
- [x] Add a hidden-fleet test proving simulation continues without player visibility.
- [x] Add a destroyed-fleet test proving intel and presentation cannot restore it.
- [x] Add assignment-consistency tests for normal updates.
- [x] Add assignment-consistency tests for checkpoint round trips.
- [x] Add missing-fleet, missing-operation, and mismatched-reference repair tests.
- [x] Add retreat, docking, repair, resupply, and release lifecycle tests.

### Implementation

- [x] Give every physical fleet a stable canonical identity.
- [x] Ensure only one system updates physical position for each fleet at a time.
- [x] Ensure only one system owns lifecycle transitions for each fleet.
- [x] Make destruction rules explicit.
- [x] Make retreat and return rules explicit.
- [x] Make docking rules explicit.
- [x] Make repair and resupply rules explicit.
- [x] Make operation-release rules explicit.
- [x] Separate operation membership from physical fleet mission.
- [x] Route all fleet-to-operation assignment through atomic methods.
- [x] Route all fleet release/removal through atomic methods.
- [x] Add deterministic load-time repair for missing or mismatched fleet/operation references.
- [x] Remove or contain remaining duplicate movement/projection authority.

### Scope guard

- [x] Do not add the new intel UI in this milestone.
- [x] Do not add the operation panel in this milestone.
- [x] Do not perform the map visual redesign in this milestone.
- [x] Do not rebalance capture or ownership rules in this milestone.
- [x] Do not rewrite all mission or lifecycle enums merely for naming consistency.

### Exit gate

- [x] Every physical fleet has one stable identity.
- [x] Every physical fleet has one active position writer.
- [x] Hidden fleets continue simulating.
- [x] Destroyed fleets cannot be restored by intel or presentation state.
- [x] Operation/fleet references remain consistent after updates.
- [x] Operation/fleet references remain consistent after save/load.
- [x] Existing campaign behavior remains stable outside proven authority fixes.
- [x] Focused and broader campaign regression suites pass.

## Milestone 3 — Explicit multi-source intelligence

**Goal:** Replace overloaded visibility/contact fields with explicit observations and resolved player knowledge.

### Model and decisions

- [x] Define a fleet-intel record keyed by stable fleet ID.
- [x] Define an operation-intel record keyed by stable operation ID.
- [x] Allow zero or more simultaneous observations per fleet.
- [x] Define observation sources for player sensors, allied reports, site radar, mission intel, and operation intel.
- [x] Define precision levels for exact, approximate, strategic-only, and unknown knowledge.
- [x] Store observed campaign tick and expiration tick for each observation.
- [x] Store confidence, known position, and uncertainty radius where appropriate.
- [x] Define deterministic precedence and merge rules among simultaneous sources.
- [x] Define atomic observation expiry during campaign updates.

### Tests first

- [x] Test simultaneous exact player-sensor and allied-report observations.
- [x] Test losing one source without deleting another valid source.
- [x] Test exact detection outranking approximate information.
- [x] Test operation knowledge without exact fleet-position disclosure.
- [x] Test observation expiry at deterministic campaign ticks.
- [x] Test intel updates cannot mutate physical fleet state.
- [x] Test expired exact observations cease qualifying as live contacts.
- [x] Test independent fleet-intel and operation-intel records.

### Implementation

- [x] Generate player-sensor observations from actual detection rules.
- [x] Generate allied-report observations without replacing player-sensor observations.
- [x] Generate site-radar observations where current campaign systems support them.
- [x] Generate mission-intel observations where explicitly awarded.
- [x] Generate operation knowledge independently from assigned fleet position knowledge.
- [x] Resolve the player-facing display state deterministically from all valid observations.
- [x] Expire observations atomically during campaign updates.
- [x] Prevent intel records from owning physical fleet position or lifecycle.
- [x] Retain compatibility adapters for old visibility/contact fields only as long as migration requires.

### Exit gate

- [x] Losing one observation source removes only that source.
- [x] Exact live detection consistently outranks approximate intel.
- [x] Operation knowledge never reveals exact fleet position by itself.
- [x] Intel cannot mutate physical fleet existence, state, or movement.
- [x] Expired exact observations never remain ordinary live icons.
- [x] All intel-resolution tests are deterministic.

## Milestone 4 — Strict intel-aware rendering

**Goal:** Make every hostile map symbol truthfully communicate current knowledge precision.

### Tests first

- [x] Test that ordinary hostile fleet icons require a valid exact observation.
- [x] Test approximate intel renders without using the fleet's current simulation position.
- [x] Test strategic-only operation knowledge renders no physical fleet icon.
- [x] Test unknown contacts render no leaked fleet data.
- [x] Test non-exact observations cannot trigger fleet clicks, combat, or live movement vectors.
- [x] Test renderer interpolation never writes simulation state.
- [x] Test every emitted marker identifies its qualifying observation.

### Implementation

- [x] Render ordinary fleet icons only for valid exact observations.
- [x] Choose whether approximate intel is retained in normal gameplay.
- [x] If retained, render approximate intel as a distinct uncertainty area or intelligence symbol.
- [x] Render strategic-only operation knowledge without physical fleet markers.
- [x] Remove live-fleet click behavior from non-exact observations.
- [x] Remove combat-trigger behavior from non-exact observations.
- [x] Remove live movement vectors from non-exact observations.
- [x] Keep interpolation visual-only and bounded between authoritative samples.
- [x] Add marker provenance to diagnostics or test-facing read models.

### Exit gate

- [x] Exact, approximate, strategic-only, and unknown states are visually and behaviorally distinct.
- [x] No stale or approximate symbol masquerades as a physical fleet.
- [x] No renderer/read-model path mutates campaign simulation.
- [x] No marker leaks more precision than its valid observations provide.

## Milestone 5 — Complete map visual language

**Goal:** Finish the player-facing clarity work begun in Milestone 1.

### Sites

- [x] Give every real site a distinct icon.
- [x] Show site ownership clearly.
- [x] Show site type clearly.
- [x] Show readable labels at appropriate zoom levels.
- [x] Add concise site cards with player-known information.
- [x] Keep site interaction aligned with the visible glyph and label area.

### Territory and control

- [x] Keep territory/control influence faint and background-only.
- [x] Keep territory/control influence non-interactive.
- [x] Ensure territory does not resemble fleet contacts or site markers.
- [x] Ensure territory never implicitly enables route overlays.

### Sensors and legend

- [x] Add a distinct medium-sized player sensor-range ring.
- [x] Tie the ring radius to the actual player detection calculation.
- [x] Ensure the ring moves with the player fleet.
- [x] Ensure the ring is visually distinct from territory/control circles.
- [x] Add a legend entry for the sensor range.
- [x] Add legend entries for sites, fleets, territory, warnings, rally overlays, targets, and operation arrows.

### Rendering priority and interaction

- [x] Define explicit layer ordering for regions, routes, arrows, sites, fleets, events, labels, and selection UI.
- [x] Ensure regions and arrows cannot obscure sites.
- [x] Ensure regions and arrows cannot obscure fleets or events.
- [x] Ensure decorative layers never intercept pointer input.
- [x] Ensure entering or overlapping a site does not enable unrelated routes.
- [x] Preserve the explicit `ROUTES` overlay and selected-site scoping rules.

### Visual validation

- [x] Validate minimum zoom.
- [x] Validate default zoom.
- [x] Validate maximum zoom.
- [x] Validate standard aspect ratio.
- [x] Validate ultrawide aspect ratio.
- [x] Validate high-density rendering.
- [x] Validate color/accessibility distinctions.

### Exit gate

- [x] Sites, territory, fleets, sensors, operations, and warnings have distinct visual languages.
- [x] The player sensor ring matches real detection behavior.
- [x] The map legend explains every major symbol category.
- [x] Sites, fleets, and events remain readable and clickable at supported layouts.
- [x] Normal map route clutter does not regress.

## Milestone 6 — Full faction operation lifecycle

**Goal:** Extend the existing commitment foundation into deliberate, time-based operations.

### Architecture reconciliation

- [x] Audit `FactionAttackCommitmentSystem` against every existing strategic-operation structure.
- [x] Select the authoritative structure that will absorb the broader lifecycle.
- [x] Document how existing commitments map to the final operation lifecycle.
- [x] Confirm no second operation truth source is introduced.

### Operation model

- [x] Represent one stable operation ID.
- [x] Store the owning faction.
- [x] Store a real rally site ID.
- [x] Store a real target site ID.
- [x] Store assigned fleet IDs.
- [x] Store the current phase.
- [x] Store timing and deadline data.
- [x] Store explicit sortie requirements.
- [x] Store final outcome/reason.
- [x] Keep rally and target roles as overlays on real sites.

### Lifecycle

- [x] Implement planning behavior.
- [x] Implement mustering behavior.
- [x] Implement ready-to-sortie behavior.
- [x] Implement en-route behavior.
- [x] Implement screening/engagement behavior where supported.
- [x] Implement assault behavior.
- [x] Implement capture/occupation behavior through the authoritative resolver.
- [x] Implement retreat and return behavior.
- [x] Implement complete, failed, and cancelled outcomes.
- [x] Define and enforce legal phase transitions.

### Muster and travel

- [x] Guard muster ratios against division by zero.
- [x] Define minimum assembled fleet count.
- [x] Define minimum assembled-fleet ratio.
- [x] Define minimum assembled-strength ratio.
- [x] Derive muster progress from real assigned fleets.
- [x] Derive travel progress from real fleet positions.
- [x] Require assigned fleets to move from real origins to the rally site.
- [x] Require launched fleets to move physically from rally to target.
- [x] Prevent timers alone from implying arrival or assault.

### Invalidations and release

- [x] Handle the target changing owner before arrival.
- [x] Handle the rally site being captured or disabled.
- [x] Handle diplomacy changes.
- [x] Handle all assigned fleets being destroyed.
- [x] Handle all assigned fleets retreating.
- [x] Handle path failure.
- [x] Handle operation timeout.
- [x] Release every fleet assignment atomically on completion.
- [x] Release every fleet assignment atomically on failure or cancellation.
- [x] Release the faction attack slot exactly once.
- [x] Keep ownership transfer behind the existing commitment and authoritative resolver safeguards.

### Tests first and exit gate

- [x] Test planning-to-mustering transition.
- [x] Test zero-fleet muster calculations.
- [x] Test sortie threshold boundaries.
- [x] Test real travel from origin to rally to target.
- [x] Test target capture before arrival.
- [x] Test rally loss.
- [x] Test diplomacy change.
- [x] Test assigned fleet destruction and retreat.
- [x] Test path failure and timeout.
- [x] Test atomic assignment release on every terminal path.
- [x] Test ownership cannot change without an eligible resolving operation.
- [x] Confirm the final implementation extends rather than duplicates the commitment system.

## Milestone 7 — Operation intent and arrows

**Goal:** Show known strategic intent without implying nonexistent live contacts.

### Tests first

- [x] Test arrows derive only from authoritative operations/commitments.
- [x] Test unknown operations produce no arrow.
- [x] Test partial operation intel does not reveal assigned fleet positions.
- [x] Test arrow detail matches operation-intel precision.
- [x] Test arrows do not block site, fleet, event, or map clicks.
- [x] Test arrow endpoints preserve source and target marker centers.
- [x] Test rendering at supported zooms, aspect ratios, and densities.

### Implementation

- [x] Gate operation arrows by valid operation intelligence.
- [x] Show rally-to-target intent only when known.
- [x] Show phase only to known precision.
- [x] Show progress only to known precision.
- [x] Show ETA or ETA range only to known precision.
- [x] Keep arrows hollow or translucent.
- [x] Keep arrows non-interactive.
- [x] Render arrows behind sites, fleets, events, labels, and hit targets.
- [x] Clip or shorten arrows around source and target markers.
- [x] Preserve the current commitment arrow as a foundation rather than a separate system.

### Exit gate

- [x] Every visible arrow corresponds to an authoritative known operation.
- [x] No arrow leaks exact fleet positions.
- [x] Arrows never interfere with map readability or interaction.

## Milestone 8 — Friendly reporting and communications

**Goal:** Make allied positions visible through explicit communication observations.

### Decisions

- [x] Define which factions share reports with the player.
- [x] Define which fleet types may report.
- [x] Define communication update cadence.
- [x] Decide whether friendly reports are immediate or delayed.
- [x] Define relay-coverage effects.
- [x] Define jamming behavior.
- [x] Define communication-loss behavior.

### Tests first

- [x] Test allied reporting outside player sensor range.
- [x] Test simultaneous friendly-report and player-sensor observations.
- [x] Test loss of friendly communications preserves valid player-sensor observations.
- [x] Test jamming invalidates only the affected report source.
- [x] Test delayed reports are visibly marked and never use current hidden positions.
- [x] Test communications state never changes fleet existence.

### Implementation

- [x] Generate friendly-report observations from real fleet positions.
- [x] Apply the approved update cadence and delay.
- [x] Apply relay and jamming rules per observation source.
- [x] Preserve independent player-sensor observations.
- [x] Mark delayed friendly reports clearly if allowed.
- [x] Keep friendly reporting separate from physical fleet simulation.

### Exit gate

- [x] Allied visibility follows explicit communication rules.
- [x] Loss of one source does not erase other valid knowledge.
- [x] Delayed information cannot masquerade as an exact live position.
- [x] Communications never create, delete, or move fleets.

## Milestone 9 — Fleet and operation inspectors

**Goal:** Explain known fleets and operations without leaking private simulation truth.

### Fleet inspector

- [x] Display the known fleet name or classification.
- [x] Display known faction.
- [x] Display known mission.
- [x] Display known destination.
- [x] Display known operation membership.
- [x] Display known status.
- [x] Display observation source or sources.
- [x] Display intel age.
- [x] Display confidence.
- [x] Display uncertainty where applicable.
- [x] Display unknown values as `Unknown` rather than reading hidden simulation fields.
- [x] Disable physical-fleet actions when exact live contact is unavailable.

### Operation inspector and panel

- [x] List currently known operations.
- [x] Display known rally site.
- [x] Display known target site.
- [x] Display known phase.
- [x] Display known progress.
- [x] Display known fleet count or range.
- [x] Display ETA or ETA range.
- [x] Display confidence and last update.
- [x] Focus the map on known rally and target information.
- [x] Show assigned fleets only when exact fleet observations permit it.
- [x] Show the intent arrow only when operation intel permits it.

### Tests and exit gate

- [x] Test inspectors render exclusively from player-visible read models.
- [x] Test hidden fields remain unknown.
- [x] Test inspector generation is read-only.
- [x] Test stale/approximate entries do not enable live-contact actions.
- [x] Test operation focus does not enable unrelated route layers.
- [x] Confirm fleet and operation inspectors never leak simulation truth.

## Milestone 10 — Save/load and migration

**Goal:** Persist durable knowledge without restoring stale live truth.

### Persistence design

- [x] Save physical fleets through authoritative stable IDs.
- [x] Save operations through authoritative stable IDs.
- [x] Save durable strategic-operation knowledge.
- [x] Define which approximate observations may persist.
- [x] Persist eligible observation timestamps and confidence.
- [x] Do not persist renderer marker positions as truth.
- [x] Do not persist interpolated positions as truth.
- [x] Do not restore exact live-contact eligibility without reevaluation.
- [x] Do not persist friendly reporting as unquestioned current truth.

### Load and repair

- [x] Reevaluate player sensors immediately after load.
- [x] Reevaluate friendly communications immediately after load.
- [x] Reevaluate site radar immediately after load.
- [x] Expire invalid observations immediately after load.
- [x] Rebuild resolved display states from valid observations.
- [x] Repair fleet references to missing operations deterministically.
- [x] Remove missing or destroyed fleets from operation assignments.
- [x] Repair valid bidirectional assignment mismatches deterministically.

### Migration

- [x] Inventory all legacy visibility and contact fields.
- [x] Define deterministic conversion rules for each legacy field.
- [x] Prevent migration from duplicating fleets.
- [x] Prevent migration from duplicating operations.
- [x] Prevent migration from duplicating observations.
- [x] Preserve existing campaign-checkpoint compatibility.
- [x] Document fallback behavior for invalid or incomplete old saves.

### Tests and exit gate

- [x] Test current-version checkpoint round trips.
- [x] Test representative legacy checkpoint migration.
- [x] Test exact live contacts are reevaluated rather than blindly restored.
- [x] Test durable operation knowledge persists.
- [x] Test eligible approximate observations retain correct age/confidence.
- [x] Test missing-ID repair.
- [x] Test no fleet, operation, or observation duplication.
- [x] Confirm existing campaign saves remain loadable or receive a documented safe fallback.

## Milestone 11 — Final regression and acceptance coverage

**Goal:** Verify the complete architecture and player experience.

### Simulation and authority

- [x] Verify marker generation never mutates simulation.
- [x] Verify route generation/rendering never mutates simulation.
- [x] Verify inspector generation never mutates simulation.
- [x] Verify no duplicate fleet markers.
- [x] Verify no snapping or alternating position authority.
- [x] Verify hidden fleets remain in simulation.
- [x] Verify destroyed fleets cannot return through intel or presentation state.

### Intelligence and rendering

- [x] Verify exact intel rendering.
- [x] Verify approximate intel rendering.
- [x] Verify strategic-only operation rendering.
- [x] Verify unknown-state rendering.
- [x] Verify detection loss hides presentation without deleting simulation entities.
- [x] Verify no intel state leaks unauthorized precision.
- [x] Verify friendly reporting and communications failure behavior.

### Operations

- [x] Verify mustering and sortie thresholds.
- [x] Verify real travel progress.
- [x] Verify target invalidation.
- [x] Verify rally invalidation.
- [x] Verify assigned fleet destruction and retreat.
- [x] Verify path failure and timeout.
- [x] Verify diplomacy-change handling.
- [x] Verify assignments and attack slots release exactly once.
- [x] Verify ownership changes remain tied to legal operations.

### Save/load

- [x] Verify live observations rebuild after load.
- [x] Verify durable operation knowledge persists.
- [x] Verify approximate intel freshness persists correctly where allowed.
- [x] Verify old saves migrate without duplication or leaked live contacts.

### Map interaction and visual acceptance

- [x] Verify sites, halos, fleets, arrows, events, warnings, and sensors remain visually distinct.
- [x] Verify sites and events remain clickable through decorative layers.
- [x] Verify territory halos remain non-interactive.
- [x] Verify route overlays remain explicit and scoped.
- [x] Verify minimum, default, and maximum zoom.
- [x] Verify standard and ultrawide aspect ratios.
- [x] Verify high-density rendering.
- [x] Verify accessibility and color distinctions.

### Long-running verification

- [x] Run deterministic seeded simulations covering all strategic factions.
- [x] Verify operation cardinality limits throughout each run.
- [x] Verify ownership transitions remain within agreed limits.
- [x] Verify no fleet/operation reference corruption accumulates.
- [x] Record soak outputs and seeds.
- [x] Run the complete repository test suite.
- [x] Record test totals, failures, and relevant reports.

### Final exit gate

- [x] All Milestones 2–10 are complete.
- [x] All automated regressions pass.
- [x] All visual and input acceptance checks pass.
- [x] Save compatibility is verified.
- [x] Source-of-truth status and implementation commit are updated.
- [x] Remaining known limitations are documented explicitly.

## Explicitly deferred or prohibited unless separately approved

- [x] Do not create a new fleet manager.
- [x] Do not create a new contact manager alongside the approved intel records.
- [x] Do not create a competing operation manager.
- [x] Do not create a new route authority.
- [x] Do not rewrite all campaign missions or lifecycle enums as a side task.
- [x] Do not replace checkpoint serialization wholesale.
- [x] Do not rebalance capture rates or pressure as part of this roadmap.
- [x] Do not rebalance economy or logistics as part of this roadmap.
- [x] Do not change diplomacy rules as part of this roadmap.
- [x] Do not change tactical encounter generation as part of this roadmap.
- [x] Do not expose all internal route geometry in normal gameplay.
- [x] Do not reintroduce stale live fleet markers.
- [x] Do not use operation intent to reveal exact fleet positions.
- [x] Do not collapse milestone boundaries, authority decisions, tests, or exit gates while implementing the authorized roadmap.

## Documentation closeout after every milestone

- [x] Update the source-of-truth status date.
- [x] Record the implementation snapshot; record a commit only after one is actually created.
- [x] Move completed capability rows from `PLANNED` or `PARTIAL` to `DONE`.
- [x] Record architecture decisions and their rationale.
- [x] Record conflicts discovered and how they were resolved.
- [x] Record exact test commands and results.
- [x] Keep later milestones marked planned until explicitly approved.

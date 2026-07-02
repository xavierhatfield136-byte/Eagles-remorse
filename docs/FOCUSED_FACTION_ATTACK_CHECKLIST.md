# Focused Faction Attacks: Safe Implementation Checklist

## Goal

Each strategic faction may attack only one map location at a time. Multiple fleets may support that attack. The map shows one large, hollow, faction-colored arrow for the committed attack without hiding or blocking map events.

This feature must not change route visibility, faction relationships, capture speed, or territory ownership rules.

## 0. Freeze and reproduce the current baseline

- [ ] Capture a clean campaign save before any implementation work.
- [ ] Record territory ownership at 0, 30, 60, 180, and 600 simulated seconds.
- [ ] Record active operations, fleet missions, attack targets, and control-change reasons at each interval.
- [ ] Add a deterministic regression scenario reproducing Green/Yellow/Red starting geography.
- [ ] Assert that opening the map, changing overlays, or rendering arrows cannot mutate simulation state.
- [ ] Assert that all existing route segments remain present and visible at the current default zoom.
- [ ] Do not proceed until the baseline test reproduces the current behavior consistently.

## 1. Define the strategic factions explicitly

- [ ] Create one canonical list of strategic belligerents used by the war director.
- [ ] Decide explicitly whether `PLAYER`, `ALLY`, and `TEAM_C` share one Green attack slot or have separate slots.
- [ ] Decide explicitly how Bright Yellow and Dark Yellow relate to Yellow, Green, and Red attack slots.
- [ ] Add tests for every faction-to-slot mapping.
- [ ] Keep alliance rules separate from display colors and enum names.

## 2. Introduce an attack commitment model without changing behavior

- [ ] Add a `FactionAttackCommitment` record containing faction, origin, target, phase, start time, operation ID, and supporting fleet IDs.
- [ ] Use explicit phases: `PLANNED`, `STAGING`, `ACTIVE`, `RESOLVING`, `COOLDOWN`, `COMPLETE`, and `ABORTED`.
- [ ] Make the commitment observable through diagnostics, but do not initially block or redirect any fleets.
- [ ] Populate commitments from the authoritative strategic-operation system, not by guessing from rendered routes or arbitrary fleet intents.
- [ ] Verify save/load round trips without changing ownership or operation results.

### Gate A

- [ ] The campaign behaves identically with commitment tracking enabled or disabled.
- [ ] Territory snapshots match the frozen baseline.
- [ ] Routes and map labels are unchanged.

## 3. Define exactly what consumes the single attack slot

- [ ] Decide whether the slot applies only to capture/invasion operations or also to raids, blockades, sabotage, and fleet interceptions.
- [ ] Recommended first scope: only territorial capture/invasion consumes the slot.
- [ ] Allow defensive reinforcement, patrol, trade, mining, rescue, and interception missions to continue independently.
- [ ] Permit multiple supporting fleets only when they share the commitment's target.
- [ ] Prevent support fleets from independently resolving ownership.
- [ ] Reject a second target before any fleet routes, costs, timers, or ownership values are modified.
- [ ] Return a structured rejection reason for AI telemetry and tests.

### Gate B

- [ ] One faction cannot create two simultaneous capture targets.
- [ ] Different factions can each maintain one target concurrently.
- [ ] Multiple fleets can support the same target.
- [ ] Completing, aborting, or losing an attack releases the slot exactly once.

## 4. Protect territory ownership and capture pacing

- [ ] Identify the single authoritative function allowed to change territory controller/owner.
- [ ] Log every control change with time, old owner, new owner, operation ID, and reason.
- [ ] Require an active matching commitment before offensive control progress can be applied.
- [ ] Require minimum staging and resolution durations; no territory may flip immediately on assignment.
- [ ] Preserve multi-stage control progression such as secure, pressured, contested, occupied, and integrated.
- [ ] Ensure allied or coalition territories cannot be captured due to faction-enum mismatches.
- [ ] Ensure UI rendering and route generation are read-only.
- [ ] Add a hard invariant: no more than one ownership transition per completed operation.

### Gate C

- [ ] Green cannot take Yellow territory without a legal Green-versus-Yellow commitment.
- [ ] No faction can capture multiple locations from one operation.
- [ ] No territory changes owner during map rendering, overlay cycling, save/load, or route calculation.
- [ ] A ten-minute seeded simulation stays within agreed capture-rate limits.

## 5. Handle existing saves safely

- [ ] Detect saves containing multiple offensive targets for one faction.
- [ ] Choose one commitment deterministically using documented priority rules.
- [ ] Put extra operations on hold or abort them without awarding control progress.
- [ ] Never retarget an existing fleet by silently transferring capture progress to another location.
- [ ] Log the migration result for inspection.
- [ ] Test migration using a save that already contains multiple Green attacks against Yellow.

## 6. Build the arrow as a read-only projection

- [ ] Derive exactly one arrow from each `FactionAttackCommitment`; do not scan every fleet and infer arrows independently.
- [ ] Draw the arrow behind location markers, event markers, labels, and interactive hit targets.
- [ ] Use a thick outlined polygon with a transparent interior.
- [ ] Use the committed faction's canonical accessible map color.
- [ ] Keep the arrow non-interactive and excluded from hit testing.
- [ ] Clip or shorten the arrow near source and target markers so it does not cover their centers.
- [ ] Scale outline width and arrowhead size with zoom while enforcing sensible minimum and maximum sizes.
- [ ] Show ETA/status in a sidebar or hover detail, not in repeated boxes across the map.

### Gate D

- [ ] A marker placed beneath the arrow's center remains visible and clickable.
- [ ] Source and target locations remain clickable.
- [ ] At most one attack arrow appears per strategic faction.
- [ ] Routes remain visible and are not filtered by the arrow feature.
- [ ] Screenshots pass at standard, ultrawide, and high-density resolutions and at minimum/default/maximum zoom.

## 7. Route and map-clutter work must be a separate change

- [ ] Do not change the default simplified/full-detail mode as part of attack commitments.
- [ ] Do not remove route segments to solve label clutter.
- [ ] Classify routes by gameplay meaning before changing their presentation.
- [ ] Remove or consolidate redundant labels without removing the underlying route geometry.
- [ ] Keep an explicit route-visibility regression test at default zoom.
- [ ] Make any map-cleanup change independently revertible from attack simulation changes.

## 8. Required automated tests

- [ ] Unit: faction-to-attack-slot mapping for every strategic faction.
- [ ] Unit: second target rejected with zero side effects.
- [ ] Unit: same-target support accepted.
- [ ] Unit: completion/abort releases the slot.
- [ ] Unit: ownership changes only through the authoritative resolver.
- [ ] Unit: UI/render calls do not mutate campaign state.
- [ ] Integration: Green, Yellow, and Red each attack at most one point during a seeded simulation.
- [ ] Integration: Green does not consume Yellow territory within one minute without completed legal operations.
- [ ] Integration: save/load preserves commitments and does not duplicate operations.
- [ ] Soak: run multiple seeds for at least ten simulated minutes and report ownership changes per faction.
- [ ] Visual: routes remain visible with and without active attacks.
- [ ] Visual: hollow arrows preserve marker visibility.
- [ ] Input: event and location clicks work through the transparent arrow interior.

## 9. Rollout order

- [ ] Change 1: add baseline telemetry and territory-balance regression tests only.
- [ ] Change 2: add the inert commitment model and save/load support only.
- [ ] Change 3: enforce one capture target behind a disabled feature flag.
- [ ] Change 4: enable the rule for one deterministic sandbox scenario.
- [ ] Change 5: run balance and migration tests, then enable all factions.
- [ ] Change 6: add the hollow arrow as a renderer-only projection.
- [ ] Change 7: address route-label clutter separately after simulation behavior is stable.

No change advances to the next gate if territory ownership, route visibility, save compatibility, or map interaction regresses.

## 10. Additional safety invariants

- [ ] Commitments must use stable location IDs, not display names, screen positions, or transient object references.
- [ ] On save/load, resolve origin and target by stable ID. If either ID is missing, abort the commitment safely and release the attack slot exactly once.
- [ ] Attack creation must be transactional. Validate faction slot availability, target legality, faction relationship, origin validity, route/path availability, and operation type before reserving the slot, assigning fleets, creating timers, or applying capture progress.
- [ ] If validation fails, no partial state may remain. No fleet route, attack timer, capture value, ownership state, or slot reservation may be changed.
- [ ] Once a commitment is created, its target may not be silently changed. To attack a different location, the current commitment must complete or abort, and a new operation ID must be created. If a commitment is placed on hold, it still occupies the attack slot unless explicitly released through the same abort/release path.
- [ ] A commitment must abort or expire safely if all supporting fleets are destroyed, all supporting fleets retreat, the origin is captured or disabled, the target changes owner before resolution, pathfinding fails for all assigned fleets, or the operation exceeds its maximum allowed duration.
- [ ] Every completion, abort, timeout, migration hold, save/load recovery, or failed-validation path must release the faction attack slot exactly once.
- [ ] Defensive reactions do not consume the offensive attack slot. A faction may reinforce owned locations, intercept attackers, recall nearby fleets, increase local readiness, or patrol threatened areas without using its single offensive attack commitment.
- [ ] Defensive fleets may not independently capture enemy territory unless they create their own legal offensive commitment.
- [ ] If multiple factions may target the same location, ownership progress must remain tied to operation ID. Only one operation may complete an ownership transfer. Other operations must resolve as failed, redirected, converted into pressure, or cancelled according to documented rules.
- [ ] If multiple factions may not target the same location, a second faction targeting an already-attacked location must receive a structured rejection reason before any route, timer, capture progress, or ownership value is modified.
- [ ] Add a diagnostic panel or debug readout showing, per strategic faction: current attack-slot status, operation ID, origin, target, phase, start time, supporting fleets, most recent rejection reason, and last ownership change caused by the operation.

## Explicit scope prohibition

Do not implement route cleanup, capture rebalance, or territory-rule changes as part of this task.

The rollout order is mandatory: telemetry first, inert commitment model second, enforcement third, and visuals last. The renderer must never become the source of truth for attack commitments or territory state.

## 11. Implemented decisions and verification evidence

Implementation completed on 2026-07-01 in the required rollout order.

### Final decisions

- Strategic attack slots are explicit: `GREEN` (`PLAYER`, `ALLY`, `TEAM_C`), `YELLOW` (`BRIGHT_YELLOW`, `TEAM_D`), `DARK_YELLOW`, and `RED` (`ENEMY`).
- Only territorial capture/invasion consumes the offensive slot. Patrol, reinforcement, interception, rescue, trade, mining, and other defensive/support behavior remain independent.
- Multiple fleets may support the same committed target.
- A target is globally reserved while an attack is active; a second faction receives a structured rejection before any fleet or ownership mutation.
- Held commitments continue occupying their slot.
- Ownership requires a matching commitment in `RESOLVING` phase that is at least 20 seconds old.
- Attack commitments expire after their configured maximum duration (currently 300 seconds for live campaign attacks).
- Existing multi-target capture orders are migrated deterministically: the strongest fleet's legal target is retained, same-target wings join it, and other capture orders are recalled without ownership awards.
- The feature is enabled by `focused_faction_attacks=true`; tests may override it with `game.feature.focused_faction_attacks`.

### Evidence

- `FocusedFactionAttackChecklistTest`: transactional creation, stable IDs, faction-slot mapping, same-target conflict, hold behavior, release-once behavior, checkpoint persistence, missing-ID recovery, minimum resolution time, minute-one Yellow ownership protection, route preservation, hollow-arrow transparency, marker-center protection, three display sizes, and click-through map interaction.
- Complete repository suite: **985 tests, 0 failures, 0 errors**.
- Multi-seed soak: **3 seeds x 600 simulated seconds**, continuously checking Yellow ownership, attack/arrow cardinality, and route presence.
- Soak report: `build/reports/focused-faction-attack-soak.csv`.
- Visual regression: all production targets pass with their original signatures; route presentation is unchanged.
- No route cleanup, capture-rate rebalance, pressure-rate change, or unrelated territory-rule rewrite was included.

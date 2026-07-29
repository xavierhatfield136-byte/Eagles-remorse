# Campaign Ghost Fleet And Coalition Support Fix Checklist

Date: 2026-07-29
Status: Core implementation pass complete; manual acceptance pending
Source: Player report that green support ships still silently follow the player and overworld fleets still persist with no inspectable ships. Updated with ChatGPT review feedback about roster double-counting, ownership, and transition states.

## Purpose

This checklist turns the current ghost-fleet and coalition-support diagnosis into implementation steps.

The goal is to make every tactical support ship and overworld fleet marker traceable to one authoritative campaign roster. Green, Yellow, Blue, and hostile fleets should appear in battle only when the campaign layer has a concrete reason for them to participate, and fleets with no viable ships should disappear or be clearly shown as non-physical intel traces.

## Implementation Progress

- [x] Added a deduplicated concrete roster snapshot and roster-state resolver.
- [x] Separated concrete physical fleets from intel-only, scripted non-physical, depleted, invalid, and transitional records.
- [x] Wired force summaries, support markers, tactical encounter eligibility, sensor-bubble joiners, and overworld ghost cleanup to roster state.
- [x] Removed generic overmap named Green/Yellow task-group injection from search, ambient, and campaign-force encounters.
- [x] Added deterministic one-force nearby coalition support selection with participation records, committed ship keys, tactical ship IDs, HUD banner, and telemetry.
- [x] Added routine coalition support hull cap and coalition support small-craft budget.
- [x] Added automated regressions for shipless forces, pool-backed counts, tactical/pool deduplication, named Green overmap suppression, and nearby support caps.
- [x] Ran focused automated validation: `CampaignNpcFleetAiTest`, `CampaignCompatibilityOverhaulTest`, `AISystemSmallCraftRangeTest`, and `TitanAbilitySystemTest`.
- [ ] Run Phase 12 manual acceptance on a real save that reproduced the screenshots.

## Current Diagnosis

- The green shadow fleet is being spawned by tactical encounter setup, not just AI following behavior.
- `spawnCoalitionSupportFleet` currently runs from multiple encounter preparation paths and calls `spawnNamedCoalitionTaskGroups` unconditionally once coalition unlock flags are active.
- Nearby non-enemy support forces are pulled from the overmap and spawned around the player with `coalitionSupportRallyPoint`, which relocates them to a tactical ring instead of preserving an obvious strategic approach.
- Ghost fleet cleanup only removes forces that previously had tactical members, now have an empty `shipIds` set, and have no recoverable pool members.
- `force.shipIds` is not an authoritative fleet roster after tactical resolution because surviving membership can move back into `campaignShipPool`.
- UI summaries currently report `shipCount` from `force.shipIds.size()`, so pool-backed fleets can look empty even when they still have ledger records.
- A simple additive ship count would be unsafe because tactical ships, pool records, and linked search groups may represent the same persistent ship.

## Completion Rules

- A tactical coalition ship must have an explicit support reason: player-requested support, selected encounter participant, nearby overmap participant, or authored mission script.
- No normal tactical encounter may silently spawn every eligible Green or Yellow coalition group.
- The player-facing UI must show when coalition support is joining and why.
- Overworld fleet markers, fleet summaries, tactical manifests, inspection panels, and ghost cleanup must use the same deduplicated roster snapshot.
- Concrete roster existence must stay separate from cleanup exemptions, scripted placeholders, intel traces, scars, and decoys.
- A normal physical fleet marker must never be emitted solely because cached strength, threat, or combat rating is positive.
- A non-player force with no concrete roster, no valid transition state, and no scripted non-physical reason must be removed or downgraded by central policy.
- Tests must cover both failed symptoms: green support mass spawning and shipless map fleets surviving cleanup.

## Roster State Policy

Use an explicit resolved state instead of repeating loosely related boolean checks.

- `CONCRETE`: The force has at least one deduplicated physical ship identity. It may render as a physical fleet and participate tactically.
- `TEMPORARILY_TRANSITIONING`: The force is in tactical resolution, pool reconciliation, transfer, restore, or another bounded handoff state. It may be retained but must not become permanently protected.
- `INTEL_ONLY`: The force is stale or approximate information, not confirmed physical membership. It may render as intel, but cannot intercept, collide, or enter battle.
- `SCRIPTED_NON_PHYSICAL`: The force is an operation placeholder, marker, or authored story object. It may persist, but does not become inspectable or tactically eligible without a concrete roster.
- `DEPLETED`: The force has no physical membership and no valid reason to remain as an active record.
- `INVALID`: Ownership is contradictory, duplicated, or unresolved. It should be quarantined/logged and prevented from normal movement, markers, and tactical entry.

## Downgrade Versus Removal Policy

- Concrete ships: keep campaign record, show physical marker when intel allows, allow tactical participation.
- Recent stale intel: keep temporarily, show intel marker only, no tactical participation.
- Battle scar: keep only if meaningful, show scar marker only, no movement or tactical participation.
- Scripted operation placeholder: keep as operation-specific marker or hidden record, no tactical participation unless populated.
- Depleted ordinary fleet: remove from campaign forces and clear references.
- Invalid ownership: quarantine or log for debug, hide from normal player-facing surfaces, no tactical participation.
- Intel traces and scars must expire or be explicitly consumed by campaign logic.
- Downgraded non-physical records must not regain physical fleet behavior without a new concrete roster assignment.

## Phase 1: Persistent Identity And Ownership Rules

- [x] Define the stable campaign identity for every physical ship: pool record ID, persistent ship ID, or another canonical key.
- [x] Define how tactical ship IDs map back to stable campaign identities.
- [x] Define whether linked search groups own ships or reference the parent campaign force roster.
- [x] Do not allow both a parent force and linked search group to independently claim the same physical ships.
- [x] Removing a search group must not destroy ships still owned by its campaign force.
- [x] Removing a campaign force must retire, detach, or downgrade its linked search group according to the ownership rule.
- [x] Treat cached strength, estimated strength, mission threat, and combat rating as derived data, never proof of a concrete fleet.

## Phase 2: Deduplicated Roster Snapshot

- [x] Add a `ConcreteForceRoster` or equivalent snapshot resolver instead of a simple additive count.
- [x] Resolve live tactical ships from `force.shipIds` only if the ship still exists, is alive, is not dying, and has positive HP.
- [x] Resolve assigned `campaignShipPool` records only if they are not `DESTROYED` and not `UNDER_CONSTRUCTION`.
- [x] Resolve linked search-group membership only under the ownership rule from Phase 1.
- [x] Deduplicate tactical ships, pool records, and search-group members by stable campaign ship identity.
- [x] Track source sets in the snapshot: tactical ship IDs, pool record IDs, linked search group keys, and unresolved keys.
- [x] Expose `concreteShipCount`, `hasConcreteShips`, `hasDuplicateAssignments`, and `hasUnresolvedMembership`.
- [x] Define source precedence when tactical, pool, and search-group membership disagree.
- [x] Add a debug assertion when one persistent ship identity appears in multiple independent physical roster sources.
- [x] Generate the snapshot once per relevant campaign tick or operation so summaries, markers, inspection, and manifests do not recompute divergent answers mid-flow.

## Phase 3: Roster State And Transition Safety

- [x] Add a resolver for authoritative `ForceRosterState`.
- [x] Keep concrete roster existence separate from cleanup protection.
- [x] Add helpers for `hasConcreteFleetRoster`, `isProtectedFromAutomaticCleanup`, `shouldRenderAsPhysicalFleet`, and `shouldRetainAsNonPhysicalRecord`.
- [x] Mark forces that are in tactical resolution, pool reconciliation, transfer, restoration, or encounter handoff.
- [x] Give every transition state a bounded expiration time.
- [x] Do not delete a force while it is in a valid transition state.
- [ ] Log forces that remain transitional beyond the permitted interval.
- [ ] Run destructive cleanup only after encounter resolution and finite-economy reconciliation have completed for the tick.
- [x] Log `INVALID` forces with enough data to identify the broken ownership path.

## Phase 4: Summary, Marker, Inspection, And Manifest Consistency

- [x] Change `CampaignForceSummary.shipCount` to use the deduplicated roster snapshot instead of `force.shipIds.size()`.
- [x] Ensure map/inspection UI does not display a force as a physical fleet with `0` ships unless it is explicitly an intel trace, scar, decoy, or stale contact.
- [x] Make fleet inspection and force summaries agree on ship count for pool-backed forces.
- [x] Require physical map markers and tactical participation to use `CONCRETE`.
- [x] Allow `INTEL_ONLY` and `SCRIPTED_NON_PHYSICAL` records to persist without presenting them as physical fleets.
- [x] Add debug text showing roster state, roster snapshot tick, live ship IDs, pool records, linked search group keys, duplicates, and unresolved memberships.
- [ ] Add a parity report line for force markers emitted with no concrete roster.
- [ ] Abort or rebuild an encounter manifest if the source roster changes before tactical commitment completes.

## Phase 5: Ghost Fleet Cleanup

- [x] Replace `hasDepletedConcreteRoster` sweep logic with the roster state resolver.
- [ ] Before deleting, reconcile campaign finite economy and force pool assignments once so valid pool-backed fleets are not deleted incorrectly.
- [x] Remove, downgrade, or quarantine non-player forces according to the central policy above.
- [x] Clear stale references after removal: active encounter force IDs, selected task group ID, proximity alert force ID, battle participant force IDs, parent force IDs, and target force IDs.
- [x] Retire linked search groups when their parent force is removed.
- [x] Leave a campaign scar/event only once, not every sweep.
- [ ] Clear or relabel cached strength when a physical force becomes an intel trace.
- [x] Add an assertion when a normal physical marker is emitted solely because force strength is positive.
- [ ] Add save/load coverage for a restored force with no live IDs and no pool records.

## Phase 6: Coalition Participation Records

- [x] Add an encounter-level coalition participation record before spawning support.
- [x] Add support reasons such as `PLAYER_REQUEST`, `SELECTED_PARTICIPANT`, `NEARBY_RESPONSE`, and `AUTHORED_MISSION`.
- [x] Record source force ID, faction, support reason, committed persistent ship IDs, requested ship count, spawned ship count, and tactical ship IDs.
- [x] Use the participation record to drive tactical spawning, HUD messaging, after-action reconciliation, pool ownership, and debug output.
- [x] Prevent one persistent campaign ship from appearing in more than one participation record.
- [x] Use the participation record to return survivors to the correct source force after battle.
- [x] Mark destroyed coalition support records as destroyed so they do not reappear in the source force.
- [x] Include participation records in tactical debug output.
- [ ] Persist participation records if active tactical encounters can be saved.

## Phase 7: Coalition Support Entry Rules

- [x] Split `spawnCoalitionSupportFleet` into named support, nearby support, and authored mission support paths.
- [x] Remove unconditional `spawnNamedCoalitionTaskGroups` from generic search-group and ambient encounter setup.
- [x] Allow named Green/Yellow coalition task groups only when a mission or player support action explicitly requests them.
- [x] Gate nearby coalition support by a stable campaign snapshot captured when the encounter manifest is created.
- [x] Define whether support range uses straight-line distance, route distance, travel time, same-region logic, or route connectivity.
- [x] Exclude forces already committed to another battle, retreat, repair, construction escort, or authored operation.
- [x] Do not allow later overmap movement to silently change the support manifest after tactical spawning begins.
- [ ] Prefer spawning support from a tactical edge or lane that reflects its overmap position instead of a silent player-centered ring.
- [x] Do not spawn support if the same persistent ship identities are already active in tactical space.

## Phase 8: Deterministic Support Selection

- [x] Define deterministic support-force selection and tie-breaking.
- [x] Suggested order: selected participant, player-requested support, mission-authored support, nearest eligible force, highest support intent, stable force ID.
- [x] Do not rely on collection iteration order.
- [ ] Record why eligible forces were rejected: out of range, no intent, wrong relationship, empty roster, already committed, duplicate ship identity, or cap reached.
- [x] Add a deterministic regression test with several equally eligible friendly forces.
- [ ] Confirm support selection remains identical after save/load and across repeated deterministic test runs.

## Phase 9: Player-Facing Clarity

- [x] Add a short banner or HUD log when coalition support joins.
- [x] Include support faction, source force name, ship count, and reason in the message.
- [ ] Add map marker/readout language for available nearby support before combat when possible.
- [x] Distinguish physical fleets from stale intel, approximate contact, decoy, or battle scar markers.
- [x] Ensure the UI never implies a defeated empty force is still physically moving.
- [x] Ensure scripted non-physical forces render as hidden, operation markers, intel traces, or physical fleets only according to the roster state policy.

## Phase 10: Performance And Fighter Guardrails

- [x] Confirm non-capital and non-titan ships no longer auto-spawn escort fighters.
- [x] Add separate budgets for campaign hulls, directly spawned tactical ships, and launched small craft.
- [x] Reserve support budgets before spawning, rather than checking only afterward.
- [x] Prevent delayed carrier launches from exceeding the encounter coalition support budget.
- [x] Define explicit fleet-scale mission overrides instead of scattered boolean bypasses.
- [x] Add a regression test or debug assertion for excessive friendly small-craft counts in routine encounters.
- [x] Verify carrier/titan exceptions do not recreate the same green swarm through launched craft.

## Phase 11: Regression Tests

- [x] A force has strength but no live IDs, no pool records, and no linked search group; the overmap sweep must remove it.
- [x] A force has no live IDs but has viable pool records; summary, marker, and inspection must report real ships consistently.
- [ ] A restored force has no live IDs and no pool records; it must not survive as a normal fleet marker.
- [x] A tactical ship and its pool record represent the same persistent ship; concrete count must be 1, not 2.
- [x] A linked search group mirrors a parent force roster; concrete count must not double.
- [x] A force is briefly empty during tactical reconciliation; cleanup must not remove it.
- [ ] A force remains stuck in reconciliation beyond its timeout; it must be logged and safely quarantined or resolved.
- [x] A scripted non-physical force survives cleanup but does not render as a physical fleet or enter battle.
- [x] A stale intel trace has positive estimated strength but cannot intercept the player.
- [x] Green Contract is unlocked but no support was requested; generic ambient/search encounters must not spawn named Green Contract ships.
- [x] A nearby Green support force is concrete and eligible; only the capped number of ships spawns and the source force is recorded.
- [x] Many friendly forces are within range; only the selected/capped support force participates.
- [x] Support pool records are not duplicated into multiple active tactical ships.
- [x] Coalition support survives battle and returns to its original source force with persistent identity intact.
- [x] Destroyed coalition support records are marked destroyed and do not reappear in the source force.
- [ ] A save made during an active coalition-supported encounter restores each support ship exactly once.
- [x] Two encounters attempt to claim the same support force; only one receives ownership.
- [x] A carrier support ship reaches its small-craft budget and cannot launch unlimited additional fighters.
- [ ] Support selection remains identical after save/load and across repeated deterministic test runs.

## Phase 12: Manual Acceptance

- [ ] Load an overmap save with known ghost fleets and wait 20 seconds on the map.
- [ ] Confirm fleets with no concrete roster disappear or become non-physical intel/scar markers.
- [ ] Inspect a pool-backed fleet and confirm the marker count, fleet summary, and ship list agree.
- [ ] Start a generic ambient encounter with Green Contract unlocked and no support request.
- [ ] Confirm no unexplained green mass appears around the player.
- [ ] Start an encounter where nearby Green support is intentionally eligible.
- [ ] Confirm support arrival is capped, messaged, and traceable to the overmap force.
- [ ] Confirm renderer load improves in large allied encounters because routine support does not create 100+ extra fighters.

## Primary Code Touchpoints

- `src/CampaignSystem.java`: `spawnCoalitionSupportFleet`, `spawnNamedCoalitionTaskGroups`, `coalitionSupportRallyPoint`, `encounterManifestForForce`, `campaignForceSummaries`, `supportMarkerForCampaignForce`, `campaignForceVisibleOnMap`, and `sweepOvermapGhostFleets`.
- `src/CampaignForceRosterSystem.java`: depleted-roster and viable-roster helpers, active tactical roster resolution, tactical membership clearing, roster snapshot resolution, and roster state resolution.
- `src/CampaignSystemModels.java`: `CampaignForceSummary.shipCount`.
- `test/CampaignNpcFleetAiTest.java`: ghost sweep and coalition support regression tests.
- `test/CampaignCompatibilityOverhaulTest.java`: Green/Yellow coalition behavior and persistence tests.
- `test/CampaignForceOwnershipTest.java`: persistent identity, save/load, and roster ownership regressions.

## Suggested Implementation Order

- [x] Define persistent ship identity and roster ownership rules.
- [x] Implement the deduplicated `ConcreteForceRoster` resolver.
- [x] Add roster state and transition-state handling.
- [x] Add diagnostics and duplicate-assignment assertions.
- [x] Wire summaries, markers, inspection, tactical manifests, and cleanup to roster snapshots.
- [x] Implement ghost cleanup and downgrade/removal policy.
- [x] Add ghost-fleet, transition, double-counting, and save/load regression tests.
- [x] Introduce coalition participation records and reason enums.
- [x] Split named, nearby, and authored support paths.
- [x] Add deterministic support selection and spawn budgets.
- [x] Add coalition ownership, persistence, and non-duplication tests.
- [x] Run focused tests: `CampaignNpcFleetAiTest`, `CampaignCompatibilityOverhaulTest`, `CampaignForceOwnershipTest`, and any force ownership tests touched by the roster resolver.
- [ ] Run a manual overmap/tactical acceptance pass using the latest player screenshots as reproduction targets.

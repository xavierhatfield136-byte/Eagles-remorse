# Campaign Fleet Authority

**Decision date:** 2026-07-02  
**Milestone:** Campaign map rework Milestone 2

## Canonical identity

`CampaignSystem.CampaignForce` is the canonical physical strategic-fleet record.

- `CampaignForce.id` is the stable fleet ID used by campaign simulation, tactical ship ownership, encounters, operations, checkpoints, and UI projections.
- Names and labels are presentation metadata and must never be used as persistent identity.
- `CampaignState.nextCampaignForceId` allocates new IDs; restored IDs advance this counter beyond the highest restored ID.

## Related representations

| Representation | Responsibility | Authority boundary |
|---|---|---|
| `CampaignForce` | Physical strategic identity, position, route, strength, mission, lifecycle, operation membership | Canonical fleet truth |
| `GalaxySearchGroup` | Hostile-search doctrine and behavior controller | May drive a linked force through the stable `linkedSearchGroupId` synchronization boundary; it is not a second fleet |
| Tactical `Ship` | Live encounter entity | Belongs to one force through `shipCampaignForceIds`; encounter membership does not create a second strategic identity |
| `CampaignShipPoolRecord` | Persistent finite ship inventory/service record | References its owning force ID; it does not own fleet position |
| `StrategicTaskForce` / `StrategicDivisionState` | Higher-level organization and planning data | May reference or summarize forces; it does not replace `CampaignForce` identity |
| `FactionAttackCommitmentSystem.Commitment` | Authoritative offensive-operation membership | Owns the set of supporting force IDs; each force mirrors one `assignedOperationId` for validation and inspection |

## Position writers

- Ordinary strategic fleets move through `updateCampaignForceSimulation` and `advanceCampaignForcePosition`.
- A linked `GalaxySearchGroup` is the behavior controller for its linked force. The ordinary force advance is skipped and the group writes through `syncCampaignSearchGroupsToForces` by stable link ID.
- Legacy name-based `ensureCampaignForce` calls cannot overwrite linked-force position.
- Tactical ship registration may update a force from its real encounter members; marker and renderer paths may not.
- Renderer interpolation, markers, contact memory, operation arrows, and inspectors are never position authorities.

## Lifecycle ownership

`CampaignForce` owns destroyed, active, retreating, docking, repair, resupply, mission, and work state. Contact or presentation state cannot restore a destroyed force.

- Destruction clears movement, targets, contacts, and simulation eligibility.
- Loss of visibility does not destroy or stop a physical fleet.
- Missing escort/repair targets cause deterministic regrouping.
- Operation completion releases the operation mirror and converts capture fleets to target reinforcement/guard duty.
- Operation abort or expiry releases the mirror and returns eligible fleets home, otherwise to patrol.

## Operation assignment

`FactionAttackCommitmentSystem.Commitment.supportingFleetIds` is authoritative. `CampaignForce.assignedOperationId` is a persisted mirror.

- `assignCampaignForceToAttackCommitment` updates both sides atomically.
- Reassignment removes the fleet from its previous active commitment before joining the next.
- Completion, abort, expiry, destruction, retreat, or invalid mission release both sides.
- `repairCampaignForceOperationAssignments` processes slots deterministically, removes missing/destroyed/duplicate support IDs, applies the authoritative operation ID to valid fleets, and clears one-sided fleet mirrors.

## Save/load

- Force ID, linked search-group ID, tactical ship membership, and assigned operation ID are persisted.
- Commitments are restored before operation-assignment repair.
- Repair runs immediately after force, commitment, and search-group restoration.
- Missing or mismatched references are repaired before normal campaign updates.

## Regression evidence

- `CampaignFleetAuthorityMilestoneTwoTest`
  - Canonical force ID and assignment survive checkpoint round trip.
  - Missing support IDs and one-sided assignment mirrors are repaired deterministically.
- `CampaignForceOwnershipTest`
  - Persistent force ownership, tactical encounter membership, finite ship records, and checkpoint behavior.
- `CampaignMapClarityMilestoneOneTest`
  - Linked groups expose one marker and one player-facing position authority.
- `FocusedFactionAttackChecklistTest`
  - Stable operation IDs, release-once behavior, checkpoint persistence, and ownership gates.

## Deferred from this milestone

- Multi-source player intelligence records.
- Intel-aware rendering and inspectors.
- Full rally/muster/travel/assault operation phases.
- Map visual redesign.
- Capture, economy, logistics, diplomacy, or tactical encounter rebalancing.

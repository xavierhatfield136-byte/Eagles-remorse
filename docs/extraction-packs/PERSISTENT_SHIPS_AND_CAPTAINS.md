# Persistent Ships And Captains

## Scope

Make ship identity, captain records, crew condition, morale, scars, commendations, casualties, memorials, successors, capture, retreat, and transfer survive normal campaign outcomes.

## Dependencies

- `FleetBuildingSystem`
- `CampaignSystem.PersistentFleetRecord`
- tactical outcome capture
- checkpoint serialization

## UI Flow

Fleet roster, shipyard preview, refit surfaces, and after-action readouts should show persistent identity and consequences.

## Data Ownership

Campaign-owned persistent fleet records are authoritative. Tactical `Ship` instances are runtime projections.

## Save Impact

Persist identity, captain, crew, morale, condition, ownership, refit, and memorial fields across checkpoint migration.

## Asset Needs

Portrait and hull art may use approved placeholders, but placeholder status must be visible in asset audits.

## Tests

Cover capture, retreat, destruction, repair, refit, successor creation, faction transfer, and save/load.

## Non-Goals

This pack does not implement full branching biographies or final portrait production.

# Phase 8 Architecture And Ownership

## Authoritative live state

| Domain | Authoritative owner | Derived or presentation-only state |
| --- | --- | --- |
| Fleets | `CampaignState.campaignForces`, `campaignShipPool`, `persistentBlueFleet`, and `shipCampaignForceIds` | Tactical encounter ships, roster rows, strategic task-group summaries |
| Inventory | `CampaignState.oreLedger`, campaign fuel/supplies/ammo/salvage, finite ship pool, and facility stockpiles | Expansion logistics stores mirror live totals for forecasts |
| Economy | Campaign resource fields, facility stockpiles, and committed transaction IDs | Expansion markets, burden/readiness telemetry, and command-board projections |
| Territory | `CampaignLocation.ownerFaction` and `controlState` | Strategic nodes and theater influence summaries |
| Production | `campaignYardOrders` and `campaignBaseQueues` | Facility queue counters and expansion production catalogs |
| Missions | Live objective fields, mission sections, active encounter location/forces | Mission-board entries, briefings, and operations catalog rows |
| Persistence | `CampaignCheckpointStore.Checkpoint` plus `CampaignSaveContract` | Migration diagnostics and regenerated debug readouts |

No expansion/model-only system may independently mint a fleet, deduct a live resource, change territory, complete production, or resolve a mission. It may forecast, summarize, or record telemetry from an authoritative transition.

## CampaignSystem feature inventory

The large file remains compatibility-sensitive. Its cohesive regions are:

1. Public immutable/read-only presentation records and campaign actions.
2. Galaxy locations, territory, routes, contacts, and theater projections.
3. Fleet ownership, finite ship inventory, lifecycle, encounter manifests, and provenance.
4. Resource inventory, mining, logistics, hubs, aid, and docking transitions.
5. Player yard orders and faction base repair/construction queues.
6. Mission briefing, mission sections, objectives, encounter entry/exit, and rewards.
7. Checkpoint capture, restore, serialization, and migration compatibility.

Presentation helpers return immutable records, strings, or copied lists. Mutating transitions use explicit verbs such as launch, execute, complete, resolve, queue, advance, restore, or apply. Extraction is deferred where moving private save-sensitive types would obscure ownership; public wrappers remain stable.

## Renderer inventory and read contract

Campaign rendering is organized into strategic-map geometry, navigation instruments, command sidebar/action layout, fleet roster/readiness, resource/strike boards, and hub overlays. Data preparation is separated from primitive panel drawing through immutable campaign readouts and shared layout helpers.

Rendering may update graphics caches and hover-only UI state, but must not mutate `CampaignState`, reconcile ownership, spend resources, advance queues, or normalize saves. Responsive rectangles remain derived from the current viewport. Screenshot baseline changes require an intentional baseline update.

## Prototype and dead-code audit

- Expansion resource stores are projection-only; the former AI-deployment payment gate no longer competes with finite fleet inventory.
- Strategic, operations, production, doctrine, deep-simulation, and community expansion states remain save-compatible model catalogs unless explicitly live-wired.
- Legacy POI IDs and saved expansion fields remain for migration compatibility.
- No unreachable campaign control or dead authored mission branch was identified by the Phase 8 reference and test audit.
- Deferred systems are labeled `PROJECTION ONLY` or `DEFERRED MODEL` in developer diagnostics.

## Executable diagnostics

`CampaignSystem.validateCampaignIntegrity` runs read-only validators for order of battle, fleet provenance, economy conservation, production queues, territory ownership, mission briefing completeness, contact validity, strike origin, and save migration.

The developer overlay displays the aggregate result and first failures. Duplicate resource transaction IDs are rejected by the authoritative campaign resource transaction API.

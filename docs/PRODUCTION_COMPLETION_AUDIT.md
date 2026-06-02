# Production Completion Audit

Date: 2026-06-01

## Purpose

This audit distinguishes implemented gameplay from prototypes, catalogs, and roadmap entries. The exhaustive item-level inventory is generated into `PRODUCTION_FEATURE_TRACEABILITY.csv` by:

```powershell
.\scripts\generate-production-traceability.ps1
```

## Status Definitions

| Status | Meaning |
| --- | --- |
| `LIVE` | Connected to normal play, player-facing, and covered by automated tests. |
| `PARTIAL` | Meaningful live implementation exists, but the checked design scope is broader than the shipped interaction. |
| `MODELED_ONLY` | Domain state, transition APIs, persistence, and tests exist, but normal play does not drive the full system. |
| `CATALOG_ONLY` | Inventory flags or planning lists exist without executable enforcement. |
| `DESIGN_ONLY` | The artifact is a roadmap or extraction index. |

## Section Classification

| Sections | Status | Current reality |
| --- | --- | --- |
| 1 | `LIVE` with manual verification gaps | Overlay ownership, hotkeys, performance guardrails, diagnostics, and regression harnesses are present. |
| 2-4 | `PARTIAL` | Tutorials, tactical depth, and persistent fleet identity have live paths, but design breadth exceeds acceptance coverage. |
| 5 | `MODELED_ONLY` | Strategic expansion state is parallel to the authoritative campaign map. |
| 6-7 | `PARTIAL` | Live travel and hub services now drive economy fatigue, readiness, maintenance, reputation reasons, and bridge logs. |
| 8-9 | `MODELED_ONLY` | Mission and information-warfare catalogs are not authoritative live systems. |
| 10-11 | `PARTIAL` | Existing strike and command UI systems are live; expansion models remain partly parallel. |
| 12-16 | `CATALOG_ONLY` | Presentation, longevity, architecture, testing, and stretch entries require executable verification or implementation. |
| 17 | `DESIGN_ONLY` | Candidate extraction index. |
| 18-26 | `MODELED_ONLY` | Persistent domain backends exist, but most live event generation and UI remain open. |
| 27 | `DESIGN_ONLY` | Additional extraction index. |

## Acceptance Criteria

A feature family is shippable only when all applicable statements are true:

1. A player reaches it through normal play or an intentional menu.
2. It changes authoritative simulation outcomes rather than parallel prototype state.
3. The UI explains the available action, cost, result, and failure reason.
4. Save/load preserves the state necessary to resume play correctly.
5. Assets and text are final or explicitly approved placeholders.
6. Automated tests cover the critical transition and one failure path.
7. A manual acceptance scenario exists for presentation, usability, and balance.
8. Telemetry or diagnostics make failures inspectable.

## Placeholder And Prototype Inventory

| Area | Current placeholder or prototype | Required disposition |
| --- | --- | --- |
| Section 5 strategic expansion | Parallel seeded systems, lanes, directors, and task groups | Integrate into the authoritative campaign map or de-scope the claim. |
| Sections 8-9 operations | Seeded mission templates and information-warfare contacts | Instantiate through live encounter and tactical systems. |
| Sections 12-15 readiness | Capability booleans and string inventories | Replace with verified asset reports and executable build tasks. |
| Section 16 stretch goals | Capability flags for co-op, editors, sharing, packaging, and procedural systems | Implement individually or move to post-release scope. |
| Section 18 doctrine | Seeded command nodes and queued orders | Connect to tactical order delivery and AI behavior. |
| Sections 19-25 deep campaign | Seeded stations, officers, hazards, politics, crises, and endgames | Add live clocks, triggers, choices, UI, and outcomes. |
| Section 26 content tools | Example CSV files and scenario-editor backend | Add production loader, schema diagnostics, and rendered editor UI. |
| Ship skins | Some roles use deterministic template fallbacks documented in `SHIP_SKINS.md` | Approve intentionally or replace with authored art. |
| Audio and voice | Stub generators remain available for missing assets | Verify release manifests contain approved authored output. |

## Sections 1-3 Re-Audit

Sections 1-3 have substantial live implementation, but the original checked boxes should not be treated as final release sign-off. The following acceptance work remains reopened through section 28:

| Area | Verified foundation | Remaining acceptance work |
| --- | --- | --- |
| Soft-lock prevention | `UISystem` overlay recovery, diagnostics, and `UiOverlayInvariantTest` | Run complete overlay permutations during mining, docking, travel, tactical entry, warp exit, and save/load. |
| Input ownership | `HotkeyRegistry`, scoped UI handling, and hotkey tests | Manually verify mouse, controller, glyph, rebinding, conflict, restore-default, and context-legend flows. |
| Performance guardrails | `PerformanceGuardrails`, telemetry, F3 overlay, and harnesses | Add reliable CI execution for late-campaign, missile-stress, memory-soak, and repeated save/load scenarios. |
| First-hour experience | `FirstHourOnboardingSystem`, `TutorialSystem`, and tests | Play through every skip, archive, reminder, mode, and first-resource recovery branch. |
| Difficulty and accessibility | Experience settings and runtime options | Run visual, input, subtitle, reduced-motion, focus-loss, and mode-specific manual acceptance passes. |
| Tactical combat depth | `TacticalCombatDepthSystem` and tactical regression tests | Build a feature-by-feature manual scenario matrix for handling, hazards, weapons, support tools, orders, and persistence. |

## Product Decisions Still Required

- Decide which placeholders are deliberately acceptable for release.
- Decide whether section-16 stretch goals belong in the release target or a post-release roadmap.
- Decide whether modeled-only systems should remain checked as implemented foundations or be reopened in sections 5-27.

# 3D Migration Task Board (LibGDX Track)

## Scope Baseline
- Current stable baseline: `0.8.0-rc1` 2D Swing build.
- Migration target: 3D client with gameplay parity for `CAMPAIGN_OPS`.
- Strategy: keep gameplay simulation in shared Java core; replace frontend (render/input/UI) with LibGDX client.
- Non-goal (initial): redesign core mechanics during migration.

## Architecture Target
- `core`: simulation and progression only (`GameContext`, systems, campaign logic, persistence).
- `client-swing`: legacy 2D frontend (temporary fallback and parity reference).
- `client-3dimentions`: new 3D frontend module (render/input/UI, camera, assets, VFX).

## Milestone Plan

### M0 Foundation Split (Week 1-2)
- [x] Create module layout: `core`, `client-swing`, `client-3dimentions`.
- [x] Extract tick/update orchestration out of Swing panel path.
- [x] Remove rendering/input dependencies from simulation call chain.
- [x] Preserve existing 2D behavior through adapter layer.
- [x] Add CI compile checks for all modules.

### M1 Parity Harness (Week 3)
- [x] Add deterministic seed smoke tests for campaign startup and early sectors.
- [x] Lock parity metrics for sectors 1-3 (`time`, `credits`, `objective flow`, `win/loss`).
- [x] Add regression script to compare core outputs before/after refactors.

### M2 3D Vertical Slice Bootstrap (Week 4-5)
- [x] Initialize `client-3dimentions` app entry, loop integration, and camera mapping.
- [x] Render placeholder 3D entities (ships/projectiles/asteroids/bases).
- [x] Implement world-to-screen indicators for objective/target/waypoint.
- [x] Verify one combat sandbox session with full core simulation tick.
Progress note:
- Bootstrap entry: `client-3dimentions/src/Main3D.java`
- Runtime panel: `client-3dimentions/src/Sandbox3DPanel.java` (uses `GameSimulationRuntime`)
- Placeholder renderer: `client-3dimentions/src/Sandbox3DRenderer.java`

### M3 Combat + Input Parity (Week 6-7)
- [x] Port critical keybinds and action model from current input system.
- [x] Implement targeting UX parity (`lock`, `cycle`, threat feedback).
- [x] Port pause/overlay state transitions (`Esc`, menus, map, shop/base flows).
- [ ] Validate full `CAMPAIGN_OPS` loop through first 3 sectors in 3D client.
Progress note:
- Shared cross-client action layer started in `src/GameplayActions.java`.
- Swing key bindings/listeners now delegate to action layer (`src/GamePanel.java`, `src/InputSystem.java`).
- M3 action-model workflow doc: `docs/M3_INPUT_ACTION_MODEL.md`.
- M3 validation protocol: `docs/M3_CAMPAIGN_OPS_3_SECTOR_VALIDATION.md`.

### M4 Frontend + Meta Parity (Week 8)
- [ ] Port title sequence/menu/credits flow.
- [ ] Port run summary and transition overlays.
- [ ] Validate settings and unlock profile compatibility (`save/*.properties`).
- [ ] Add 3D client packaging entrypoint.

### M5 Content Conversion + Performance (Week 9-11)
- [ ] Replace placeholders with production 3D assets for core ship roles.
- [ ] Port projectile/explosion/shield/modifier VFX equivalents.
- [ ] Add pooling/culling/perf budgets for campaign-scale fights.
- [ ] Complete 20+ minute stability/perf run at release target settings.

### M6 3D RC Sign-Off (Week 12)
- [ ] Run RC checklist against 3D build (startup, campaign, persistence, UX, perf, packaging).
- [ ] Resolve all `P0` and `P1` issues.
- [ ] Freeze scope for release candidate.
- [ ] Produce `3D RC1` build + sign-off report.

## File-Mapped Refactor Targets

### Core Extraction (Keep/Move to `core`)
- `src/GameContext.java`
- `src/CampaignSystem.java`
- `src/PhysicsSystem.java`
- `src/AISystem.java`
- `src/EconomySystem.java`
- `src/EventSystem.java`
- `src/SpawnSystem.java`
- `src/TargetingSystem.java`
- `src/CarrierSystem.java`
- `src/LastStandSystem.java`
- `src/CampaignUnlockProfile.java`
- `src/MenuSettingsStore.java`

### Swing Client (Move to `client-swing`)
- `src/Main.java`
- `src/GamePanel.java`
- `src/GameRenderSystem.java`
- `src/Renderer.java`
- `src/InputSystem.java`
- `src/TitleSequencePanel.java`
- `src/CreditsPanel.java`

### 3D Client (Create in `client-3dimentions`)
- [x] `Main3D` launcher + lifecycle.
- [x] Camera/controller layer.
- [x] Render adapter from core state.
- [x] 3D HUD/overlay UI layer.
- [ ] Asset pipeline + loading.

## Immediate Sprint Backlog (Next 10 Tasks)
1. [x] Create module folders and build wiring for `core`, `client-swing`, `client-3dimentions`.
2. [x] Introduce `InputSnapshot` model consumed by core tick.
3. [ ] Introduce `RenderSnapshot` read model exported by core.
4. [x] Move simulation loop orchestration out of `GamePanel`.
5. [x] Keep Swing client running via adapter to extracted core loop.
6. [x] Add deterministic parity test for campaign init and sector transition.
7. [ ] Bootstrap LibGDX client with top-down camera and world scale mapping.
8. [x] Render placeholder entities from core state in 3D.
9. [x] Wire critical controls (`Esc`, targeting, abilities, map/overlay toggles).
10. [ ] Validate first playable 3D sandbox and log parity gaps.

## Risk Register
- Asset pipeline complexity could delay parity if started too late.
- Input/UX parity drift can break feel even when logic is correct.
- Tight coupling between render and simulation paths may require deeper refactor than expected.
- Packaging/build system fragmentation across modules can slow iteration.

## Go/No-Go Gates
- `GO`: 3D client passes RC checklist with no unresolved `P0/P1`.
- `NO-GO`: any crash/data-loss/soft-lock or major campaign regression remains.

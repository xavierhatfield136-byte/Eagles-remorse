# Future 3D Track

Date: 2026-06-01
Status: Intentional post-alpha feasibility lane

## Purpose

The finished 2D game is the design and simulation baseline. After the current game is done and dusted, the long-term goal is to build a 3D version using the shared gameplay core where practical.

This lane remains visible now because 3D assets are already being generated and because early feasibility checks can expose architecture mistakes before they become expensive. It is not part of the playable-alpha critical path.

## Preserve The Sandbox

Keep the existing sandbox launcher:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-3d-sandbox.ps1
```

Optional arguments:

```powershell
-Seed 10101
-MapSize small|medium|large
-NoRandomEvents
```

The launcher currently compiles `src/*.java` with `client-3dimentions/src/*.java`, then launches `Main3D`. The prototype includes:

- `client-3dimentions/src/Main3D.java`
- `client-3dimentions/src/Sandbox3DPanel.java`
- `client-3dimentions/src/Sandbox3DRenderer.java`

Validation note: on 2026-06-01, the launcher source discovery was updated to include recursive packaged Java sources and the compile-only sandbox audit passed across 158 sources. The interactive sandbox window was not launched during the documentation audit.

## Near-Term Rule

Do not remove the sandbox, parity harnesses, cross-client input work, or generated 3D assets while finishing the 2D alpha.

Do not let 3D presentation work delay the 2D alpha unless a shared-core defect would make a later conversion materially harder.

## Feasibility Checks During 2D Development

- [ ] Run the 3D sandbox after major shared simulation, persistence, or input changes.
- [ ] Keep the sandbox compiling against the shared gameplay sources.
- [ ] Record breakages caused by rendering or input coupling.
- [ ] Preserve deterministic campaign parity tooling.
- [ ] Keep generated 3D assets organized separately from approved 2D release assets.

## Post-2D Migration Order

1. Re-audit shared simulation boundaries.
2. Introduce a framework-neutral render snapshot.
3. Validate campaign parity through the first three sectors.
4. Port title, settings, overlays, persistence compatibility, and packaging.
5. Replace placeholders with production 3D assets.
6. Add 3D-specific culling, pooling, VFX, and performance budgets.
7. Run a 3D release-candidate checklist.

## Existing Historical References

- `archive/legacy/THREE_D_MIGRATION_TASK_BOARD.md`
- `archive/legacy/M2_3D_SANDBOX_VALIDATION.md`
- `archive/legacy/M1_PARITY_WORKFLOW.md`
- `archive/legacy/M3_INPUT_ACTION_MODEL.md`
- `parity/campaign_m1_baseline.json`

These remain reference material. This document is the active policy for the future 3D lane.

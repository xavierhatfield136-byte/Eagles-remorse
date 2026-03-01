# Core Module (Planned)

Purpose:
- Host simulation-only game logic shared by multiple clients.
- Keep rendering, windowing, and input-framework dependencies out of this module.

Current status:
- Module scaffold created for M0 foundation split.
- Runtime orchestration extraction has started in legacy `src/` (`GameSimulationRuntime`, `InputSnapshot`).

Next move:
- Incrementally relocate simulation classes from `src/` into `core/src/` once build wiring is finalized.

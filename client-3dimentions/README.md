# 3D Client Module (`client-3dimentions`)

Purpose:
- Host the migration 3D frontend sandbox.
- Reuse shared simulation logic from the core module.

Current status:
- M2 bootstrap committed with a runnable placeholder 3D sandbox:
  - `client-3dimentions/src/Main3D.java`
  - `client-3dimentions/src/Sandbox3DPanel.java`
  - `client-3dimentions/src/Sandbox3DRenderer.java`
- Simulation loop is shared through `GameSimulationRuntime`.
- Key actions route through `GameplayActions` for cross-client behavior parity.

Run (from terminal):
- `powershell -ExecutionPolicy Bypass -File scripts\run-3d-sandbox.ps1`
- Validation checklist: `docs/archive/legacy/M2_3D_SANDBOX_VALIDATION.md`

Notes:
- This is a migration bootstrap renderer (pseudo-3D projection), not final art/runtime.
- The current client path is Swing-based; this module is intentionally framework-light for rapid iteration.
- Targeting, objective text, and waypoint indicators are rendered in world space.

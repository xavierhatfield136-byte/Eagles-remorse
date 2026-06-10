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
- Primary Three.js GLTF sandbox:
  - `tools\3d-testing\start-3d-testing.bat`
  - This is the main full-model path because Three.js `GLTFLoader` preserves GLB hierarchy, transforms, materials, textures, UVs, and normals.
  - Player controls: `W/S` thrust, `A/D` turn, `Shift` boost, `SPACE` or left mouse fire, `TAB` switch blue ship.
  - Tool controls: `F` follow/orbit, `C` cinematic camera, `P` pause, `N` reset.
- Experimental LWJGL full 3D playable demo-level tool:
  - `tools\3d-testing\start-full-3d-testing-ground.bat`
  - `run-full-3d-testing-ground.bat`
  - `.\gradlew.bat runFull3DTestingGround`
  - IntelliJ: open the Gradle tool window, then run `Tasks > application > runFull3DTestingGround`.
  - Optional batch arguments: `run-full-3d-testing-ground.bat "C:\path\to\models" mothership`
  - Demo levels: `1` mothership fleet sandbox, `2` skirmish, `3` capital duel, `4` fighter swarm, `5` four-team crossfire, `6` model gallery.
  - Player controls: `W/S` thrust, `A/D` turn, `Shift` boost, `SPACE` or left mouse fire, `TAB` switch blue ship.
  - Tool controls: arrows orbit camera, `Q/E` zoom, `F` follow camera, `C` cinematic orbit, `X` wireframe overlay, `P` pause, `R` reset.
  - If IntelliJ cannot download LWJGL because of a certificate error, add `-Djavax.net.ssl.trustStoreType=Windows-ROOT` to the Gradle VM options.
- GLB model combat test:
  - `powershell -ExecutionPolicy Bypass -File scripts\run-3d-sandbox.ps1 -Mode domination`
  - `powershell -ExecutionPolicy Bypass -File scripts\run-3d-sandbox.ps1 -Mode custom -MapSize small`
  - `powershell -ExecutionPolicy Bypass -File scripts\run-3d-sandbox.ps1 -Mode showcase`
- By default, the sandbox looks for GLB files in `C:\Users\xhatf\OneDrive\Desktop\3d models dropoff`.
- To test another export folder, pass `-ModelDir "C:\path\to\models"`.
- To compare against the old placeholder renderer, pass `-NoGlbModels`.
- Validation checklist: `docs/archive/legacy/M2_3D_SANDBOX_VALIDATION.md`

Notes:
- This is a migration bootstrap renderer (pseudo-3D projection), not final art/runtime.
- The current client path is Swing-based; this module is intentionally framework-light for rapid iteration.
- Targeting, objective text, and waypoint indicators are rendered in world space.
- GLB rendering is a lightweight sandbox path: it samples mesh triangles, applies faction tinting, and keeps the main production game package unchanged.
- The LWJGL full-3D demo uses larger triangle budgets for mothership/capital models, but still renders geometry with team tinting instead of GLB textures/materials.

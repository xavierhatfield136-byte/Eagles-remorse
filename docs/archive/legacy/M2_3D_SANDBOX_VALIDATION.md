# M2 3D Sandbox Validation

## Run Options
1. IntelliJ:
   - Run class: `Main3D` (from `client-3dimentions/src/Main3D.java`).
2. Terminal:
   - `powershell -ExecutionPolicy Bypass -File scripts\run-3d-sandbox.ps1`
   - Optional args:
     - `-Seed 10101`
     - `-MapSize small|medium|large`
     - `-NoRandomEvents`

## Expected Runtime Features
- Full simulation tick running through `GameSimulationRuntime`.
- Placeholder 3D rendering for:
  - ships
  - projectiles
  - asteroids
  - bases
- World-space indicators:
  - objective HUD text
  - locked target ring/label
  - waypoint marker
  - capture zone ring (when applicable)

## Key Controls
- Movement: `W/A/S/D` (+ `Shift` boost)
- Fire: `Mouse Left/Right` or `Space/Shift`
- Targeting: `L`, `[`, `]`, `Middle Click`
- Utility: `P` ping, `G` waypoint, `T` turret auto-lock
- Overlays/state: `Esc`, `Tab`, `B`, `M`
- Abilities: `E`, `Q`, `C`, `R`, `V`, `Z`
- Camera tuning: `PageUp/PageDown`, `Ctrl+=`, `Ctrl+-`
- Exit: `F10`

## M2 Verification Checklist
- [ ] Run starts and does not crash on load.
- [ ] Combat systems advance (movement, AI, projectiles, collisions).
- [ ] Sector objectives update in HUD.
- [ ] Target/waypoint indicators appear in world space.
- [ ] One combat sandbox session (`10+ min`) runs without lockups.
- [ ] `F10` cleanly exits the sandbox window.

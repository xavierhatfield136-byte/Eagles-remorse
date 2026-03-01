# 2-Hour Campaign Task Board (PC)

## Scope Baseline
- Campaign length target: `110-130 minutes`.
- Modes in scope for campaign systems: `CAMPAIGN_OPS` only.
- Vertical slice target (`M1`): first `~30 minutes`, 3 sectors, objective loop, first difficulty ramp.

## Milestone Plan

### M1 Vertical Slice (Week 1-2)
- [x] Add sector/objective state machine.
- [x] Add explicit HUD objective text.
- [x] Add sector pacing hooks (wave intensity by sector).
- [x] Add transition overlay between sectors.
- [x] Add objective telemetry logging (`sector start/end/fail`, `time`).
- [x] Add first 3-sector authored script and playtest pass.
- [x] Tune first 30-minute difficulty and economy.

### M2 Full Campaign Spine (Week 3-4)
- [x] Expand to 10-12 sectors with 3 act breaks.
- [x] Integrate 2 mid-boss and 1 final boss scripts.
- [x] Objective pool complete: destroy/escort/survive/capture.
- [x] Add run summary between sectors.
- [x] Add modifier telegraphing (HUD chips + world tint).

### M3 Content Complete (Week 5-6)
- [x] 10-15 enemy archetypes with counters.
- [x] 3-5 bosses with phase mechanics and unique drops.
- [x] 6-8 map modifiers integrated into sector setup.

### M4 Meta Loop (Week 7)
- [x] Persisted unlock profile between runs.
- [x] Side objectives for bonus rewards.
- [x] Lightweight branch outcomes and alternate ending states.

### M5 Release Candidate (Week 8)
- [x] Save/load and settings persistence hardening.
- [x] Error logging and crash-safe writes.
- [x] Performance pass and frame pacing check.
- [x] Packaging/versioning/credits/title sequence.

## File-Mapped Workstreams

### Campaign Progression
- `src/CampaignSystem.java`
- `src/GameContext.java`
- `src/GamePanel.java`
- `src/SpawnSystem.java`

### Combat Pressure / Difficulty Curve
- `src/AISystem.java`
- `src/PhysicsSystem.java`
- `src/TargetingSystem.java`
- `src/FleetShip.java`

### Objective Clarity / HUD
- `src/GameRenderSystem.java`
- `src/Renderer.java`
- `src/EventSystem.java`

### Input + UX Polish
- `src/InputSystem.java`
- `src/GamePanel.java`
- `src/Main.java`
- `src/UISystem.java`

### Boss + Enemy Content
- `src/FleetShip.java`
- `src/SpawnSystem.java`
- `src/AISystem.java`
- `src/DoctrineRegistry.java`

### Persistence + Shipping
- `src/Main.java`
- `src/GameConfig` (inside `src/Main.java`)
- `src/GameContext.java`
- `src/DevTools.java`

## M1 Immediate Engineering Tasks
1. Author sector scripts 1-3 with deterministic objective completion conditions.
2. Tune unlock grants for 15-20 minute cadence in first hour.
3. Run 3 full vertical-slice playthroughs and capture balancing notes.

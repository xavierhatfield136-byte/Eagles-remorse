# RC Sign-Off Checklist (`0.8.0-rc1`)

## Scope
- Build target: `0.8.0-rc1` (from `VERSION`).
- Primary mode under sign-off: `CAMPAIGN_OPS`.
- Test date baseline: `2026-02-22` or later.
- Test environment: IntelliJ run configuration using `Main`.

## Exit Criteria (Must All Pass)
- No crashes, freezes, or soft-locks in required flows.
- No blocker regressions in campaign progression, save/load, input, or rendering.
- Title sequence and credits screen are reachable and functional.
- Packaging command succeeds and produces runnable JAR when JDK tooling is present.

## Severity Gates
- `P0` (release blocker): crash, data loss/corrupt save, soft-lock, cannot start/continue run, broken packaging.
- `P1` (must-fix before release): major gameplay/system regression with reliable repro.
- `P2` (can defer): minor visual/UI polish issue with no gameplay impact.

## Pre-Flight Setup
- [ ] Confirm `VERSION` value is correct.
- [ ] Confirm run config launches `Main`.
- [ ] Delete/backup old logs if needed (`save/error_log.txt`) to isolate RC results.
- [ ] Record machine + OS + Java runtime version in session notes.

## Ordered Sign-Off Sequence

### 1. Startup + Frontend Flow
- [ ] Launch game from IntelliJ.
- [ ] Verify title sequence appears first and auto-transitions to menu in ~3-4s.
- [ ] Verify skip works with `Enter`, `Space`, `Esc`, and mouse click.
- [ ] Verify menu shows version text matching `VERSION`.
- [ ] Open `Credits`, verify content renders and `Esc`/`Back` returns to menu.

Pass condition:
- [ ] All checks above pass without visual/input lockups.

### 2. Core Session Smoke (`CAMPAIGN_OPS`)
- [ ] Start run using seed `10101` and default map size.
- [ ] Verify player control, firing, HUD, minimap, and objective text update.
- [ ] Verify sector progression occurs and transition overlay/run summary appears.
- [ ] Verify no campaign deadlock in first 3 sectors.
- [ ] Verify exit to menu (`F10`) returns cleanly, then start a second run.

Pass condition:
- [ ] Two consecutive runs start/exit cleanly without stale-state issues.

### 3. Persistence + Recovery
- [ ] Change menu settings (mode/map/events/fullscreen/seed), quit, relaunch, verify persistence.
- [ ] Complete at least one sector and verify unlock/profile persistence behavior on next launch.
- [ ] Force-close once during menu-only state, relaunch, verify settings/profile remain readable.
- [ ] Check `save/error_log.txt` for new unexpected exceptions.

Pass condition:
- [ ] No save corruption, no parse/reset anomalies, no unexpected fatal logs.

### 4. Input/UX Regression Sweep
- [ ] Verify `Esc` behavior in: running, paused, shop/base/map overlays, and game-over/menu return.
- [ ] Verify fullscreen toggle (`Alt+Enter`) while in-game and ensure focus/input remains responsive.
- [ ] Verify shop/base/map overlays open/close correctly and do not trap input.
- [ ] Verify key gameplay binds still work (`L`, `[`, `]`, `M`, `P`, `G`, `E`, `Q`, `C`, `R`, `V`, `Z`).

Pass condition:
- [ ] No stuck overlay/input state and no keybind regressions in tested flows.

### 5. Performance + Stability Spot Check
- [ ] Play continuously for 20+ minutes in `CAMPAIGN_OPS`.
- [ ] Watch for escalating frame hitching, update-step drops, or memory-like degradation.
- [ ] Trigger high-combat moments (boss/reinforcement spikes) and verify no severe stutter lock.

Pass condition:
- [ ] Stable playability; no progressive degradation that impacts completion.

### 6. Packaging Validation
- [ ] Ensure JDK tools are available (`javac`, `jar`) or `JAVA_HOME` points to JDK.
- [ ] Run: `powershell -ExecutionPolicy Bypass -File scripts\package.ps1`.
- [ ] Verify artifact exists: `build/dist/space-game-0.8.0-rc1.jar`.
- [ ] Run packaged build once: `java -jar build/dist/space-game-0.8.0-rc1.jar`.
- [ ] Verify packaged build startup/menu/title/credits path works.

Pass condition:
- [ ] Packaging and packaged startup are confirmed.

## Session Log Template

### Run Record
- Date:
- Tester:
- OS:
- Java runtime:
- Build/Version:

### Results by Section
- Section 1 (Startup + Frontend): `PASS/FAIL`
- Section 2 (Core Session): `PASS/FAIL`
- Section 3 (Persistence): `PASS/FAIL`
- Section 4 (Input/UX): `PASS/FAIL`
- Section 5 (Performance): `PASS/FAIL`
- Section 6 (Packaging): `PASS/FAIL`

### Defects Found
1. ID:
2. Severity (`P0/P1/P2`):
3. Repro steps:
4. Expected:
5. Actual:
6. Notes/screenshot/log path:

## RC Decision
- [ ] `GO`: All required sections passed; only acceptable deferred `P2` issues remain.
- [ ] `NO-GO`: Any `P0` or unresolved `P1` issue remains.

# Live Performance Profile And Remediation Plan

Date: 2026-05-02  
Status: Active investigation and implementation plan

## Goal

Identify what is lagging the game during real play right now, describe the highest-cost runtime paths, and define a staged plan to reduce frame time without breaking campaign behavior or combat readability.

This document is based on a live process sample of the running game, not just static code inspection.

## Summary

The current lag problem is not primarily a garbage collection issue.

The running game is being slowed mostly by:

1. expensive AI fleet-state rebuild work on the main Swing game thread
2. expensive shield-shape rendering and Java2D path work on the same thread
3. expensive HUD panel and chip drawing when many overlay elements are visible

Because update and render both run on the `AWT-EventQueue-0` thread, any hot path in simulation or rendering blocks the entire frame.

## Live Findings

## 1. Main-thread AI work is a top update bottleneck

Live thread samples repeatedly caught the running game inside:

- `AISystem.update(...)`
- `AISystem.buildFleetState(...)`
- `AISystem.assignSquadronIdentities(...)`
- `AISystem.registerSquadronChunk(...)`
- `AISystem.selectSharedTargetForTeam(...)`

Observed sample locations:

- `src/AISystem.java:160`
- `src/AISystem.java:425`
- `src/AISystem.java:435`
- `src/AISystem.java:569`
- `src/AISystem.java:909`
- `src/AISystem.java:960`

What this means:

- team-level state is being rebuilt too often
- squadron identity/chunk registration is doing a lot of `HashMap` and collection churn
- target-sharing logic is still expensive at full fleet scale

Likely symptom in play:

- frame drops get worse as fleet size and support traffic increase
- campaign sectors with more allied support and reserve behavior will feel heavier than quiet sectors

## 2. Shield rendering is a top render bottleneck

Live thread samples also caught the running game inside:

- `Renderer.drawShipShieldFaces(...)`
- `Renderer.createShieldShell(...)`
- `Renderer.drawShip(...)`
- `Renderer.drawEcmIllusions(...)`
- `GameRenderSystem.render(...)`

Observed sample locations:

- `src/Renderer.java:1417`
- `src/Renderer.java:1519`
- `src/Renderer.java:8915`
- `src/Renderer.java:8930`
- `src/GameRenderSystem.java:128`

The hot stack showed Java2D spending time in:

- `java.awt.geom.Area`
- `sun.awt.geom.AreaOp`
- `D3DRenderQueue.flushBuffer(...)`

What this means:

- shield shell geometry is being recreated or recombined too often
- Java2D path operations are expensive enough to become a major frame cost
- ECM illusion rendering makes this worse by multiplying the number of rendered ship-like effects

Likely symptom in play:

- big fights with many shielded ships feel dramatically worse than sparse fights
- visual clutter spikes frame time even if gameplay density is otherwise reasonable

## 3. HUD chrome is also expensive

Another live sample caught the game inside:

- `Renderer.drawHudStatusChip(...)`
- `Renderer.drawShipSystemsCard(...)`
- `Renderer.drawHUD(...)`

Observed sample locations:

- `src/Renderer.java:3995`
- `src/Renderer.java:4628`
- `src/Renderer.java:4904`

What this means:

- lots of `fillRoundRect(...)`, borders, and repeated chip drawing are adding cost every frame
- full-detail HUD mode becomes more expensive during heavy gameplay, exactly when the frame budget is already stressed

Likely symptom in play:

- the game feels heavier with more overlays, more cards, and more information surfaces open

## 4. GC is not the main problem right now

Live heap snapshot:

- heap total: about `5.7 GB`
- heap used: about `2.46 GB`

This does not rule out allocation churn entirely, but the dominant observed stalls were not GC pauses. The game is mostly spending time doing active work on the main thread.

## 5. Audio is not the main problem right now

Audio worker threads were mostly waiting and did not appear in the hot samples as primary cost centers.

## Root Cause

The biggest architectural issue is that both simulation and rendering are concentrated on the Swing event thread:

- `GameSimulationRuntime.tick(...)`
- `GameRenderSystem.render(...)`
- Swing repaint work

That means:

- expensive AI work delays rendering
- expensive rendering delays input and simulation
- spikes compound instead of being isolated

This is why the game can feel suddenly terrible in large sectors: multiple medium-cost systems stack on one thread and blow the frame budget together.

## Proposed Remediation Strategy

## Phase 1: Add Better Runtime Visibility

Before deeper fixes, make the cost visible in normal play.

### Work

- expand `PerfTelemetry` to include:
  - `aiMs`
  - `renderShipsMs`
  - `renderHudMs`
  - `shieldRenderMs`
  - `campaignMs`
- instrument:
  - `AISystem.update(...)`
  - `GameRenderSystem.render(...)`
  - ship render pass
  - HUD render pass
  - shield render path
- expose a lightweight dev overlay toggle for these timings

### Why first

This prevents guessing and lets later optimizations prove their value.

### Success criteria

- we can see which system is dominating frame time in a heavy battle
- we can compare before/after numbers for every optimization pass

## Phase 2: Reduce AI Fleet-State Rebuild Cost

This is the first gameplay-side optimization target.

### Problems to solve

- repeated full rebuild of fleet state
- frequent collection allocation/churn
- expensive squadron identity assignment every frame
- repeated shared-target selection even when nothing meaningful changed

### Work

1. add dirty-flag or cadence-based fleet-state rebuilding
   - rebuild only every `N` ticks, or when fleet membership/command state changes
2. cache squadron chunk assignments
   - update incrementally instead of rebuilding all chunks every frame
3. reuse temporary collections
   - avoid repeated `HashMap`/`HashSet`/`ArrayList` allocation in hot paths
4. throttle shared-target recomputation
   - only recompute when target validity changes, command changes, or a timer expires
5. skip expensive full-fleet logic for distant or low-importance groups
   - especially support ships, logistics, and non-engaged groups

### Files

- `src/AISystem.java`

### Success criteria

- average AI time drops meaningfully in fleet-heavy sectors
- no visible loss of basic fleet coordination
- no command regression for escort, attack, or reserve behavior

## Phase 3: Reduce Shield Rendering Cost

This is the first render-side optimization target.

### Problems to solve

- expensive `Area` construction in `createShieldShell(...)`
- repeated shape work per frame
- repeated work multiplied by ECM illusions and many shielded ships

### Work

1. cache shield shell geometry
   - cache by hull/role/faction/size bucket where possible
2. stop rebuilding shield geometry every frame unless visual parameters changed
3. simplify shield rendering under load
   - lower-complexity shell in stress mode
4. reduce or disable shield-face rendering for:
   - distant ships
   - tiny ships
   - ECM illusion copies
   - fogged or low-visibility ships
5. add a fallback cheap shield mode for large battles
   - ellipse or simplified silhouette instead of full path shell

### Files

- `src/Renderer.java`
- `src/GameRenderSystem.java`

### Success criteria

- render time in heavy shield fights drops substantially
- shield visuals still communicate active defense clearly
- ECM no longer multiplies render cost so aggressively

## Phase 4: Reduce HUD Render Cost

The HUD is not the only problem, but it should stop making bad frames worse.

### Problems to solve

- lots of per-frame rounded-rect fill/draw work
- dynamic chip layout rebuilt every frame
- expensive full-detail HUD surfaces visible in combat

### Work

1. cache HUD text metrics and layout for stable panels
2. reduce repeated `Canvas`/`FontMetrics` helper work in hot panel code
3. simplify chip rendering
   - fewer layered borders
   - cheaper shapes
4. degrade HUD detail automatically under load
   - compact mode in stress conditions
5. avoid drawing hidden or low-value cards while combat load is high

### Files

- `src/Renderer.java`

### Success criteria

- heavy combat with full HUD no longer incurs disproportionate extra frame cost
- readability remains acceptable in compact mode

## Phase 5: Control Expensive Multipliers

Some systems may be individually acceptable but become catastrophic in combination.

### Work

1. cap or simplify ECM illusion rendering during load spikes
2. reduce support-ship AI detail for non-combat actors
3. review campaign sectors for avoidable population spikes
4. add dynamic quality scaling hooks tied to `PerfTelemetry`

### Examples

- simplified shield mode when `frameMs` exceeds threshold
- reduced AI rebuild cadence when ships exceed threshold
- lower overlay detail when render budget is overrun

## Implementation Order

Recommended order:

1. add perf instrumentation
2. optimize AI fleet-state rebuild
3. optimize shield rendering
4. optimize HUD drawing
5. add dynamic stress scaling and cleanup passes

This order is chosen because:

- AI and shield work are the strongest live suspects
- both are large enough to move frame time by themselves
- HUD cleanup is useful but should not be the first lever pulled

## New Formation Preset: `ASSAULT`

Alongside the performance work, a new fleet formation preset should be added called `ASSAULT`.

This is not just a cosmetic formation. It is meant to support offensive pushes into enemy positions while protecting the mothership and high-value support hulls, and while keeping damaged lighter ships able to fall back into transport-healing coverage.

### Intent

`ASSAULT` should behave like a layered attack wedge or layered attack line:

- light escort ships screen first
- line ships push behind that screen
- capitals push behind the line ships
- Titans are split by role instead of treated as one flat rear block
- the mothership stays in the back half of the formation, not on the nose

The formation should advance pressure forward while preserving a protected recovery lane behind the front elements.

### Desired Layer Order

From front to back:

1. light escort screen
2. line-ship pressure layer
3. capital-ship pressure layer
4. central Titan core
5. rear support Titan layer
6. mothership in the back half

### 1. Light Escort Screen

These ships go in the front:

- pickets
- patrol craft
- corvettes
- CIWS corvettes
- frigate-like fast escorts if they are classified on the lighter end

Behavior:

- form a tight forward line if numbers are small-to-medium
- form a shallow arc, double-line, or offset screen if numbers are larger
- absorb first contact
- contest missiles, strike craft, and skirmishers early
- remain close enough to retreat back through the line layer when damaged

Design note:

This layer should be compact rather than diffuse. The user specifically wants a tight forward screen, not a loose picket cloud.

### 2. Line-Ship Pressure Layer

These ships sit behind the escort screen and in front of capitals:

- standard frigates if treated as line ships
- missile boats if used as direct attack line elements
- light cruisers
- brawling or medium combatants that are sturdier than escorts but not true capitals

Behavior:

- provide the main sustained forward gun line
- step into openings created by the escort layer
- hold a stable attack front instead of overextending

### 3. Capital-Ship Pressure Layer

These ships sit behind line ships and in front of the Titan core:

- battleships
- battlecruisers
- dreadnoughts
- superships when acting as direct assault assets

Behavior:

- maintain a deliberate push
- use their firepower through and over the lighter line
- avoid outrunning the escort and line layers

### 4. Central Titan Core

These Titans belong in the middle of the formation if they are either durable or key assault assets:

- transport Titans
- tanky Titans
- Titans with strong direct firepower

Examples of intended placement:

- transport Titan: middle
- bulwark or heavy defensive Titan: middle
- offensive gun-heavy Titan or breakthrough Titan: middle

Behavior:

- stay inside the protected core
- act as sustainment and pressure anchors
- provide a place for damaged lighter hulls to fall back toward

Important note:

The transport Titan should not be parked all the way in the rear. It belongs in the center so the forward layers can retreat into its healing/support envelope.

### 5. Rear Support Titan Layer

These Titans stay in the back:

- carrier Titans
- intel Titans
- artillery Titans
- mobile station Titans
- liberation ships

Behavior:

- hold rear-half spacing
- remain protected by the front combat layers
- preserve support, launch, recon, and standoff value

This rear support layer should not drift so far back that it disconnects from the formation, but it should clearly avoid front-line exposure.

### 6. Mothership Placement

The mothership stays in the back half of the formation.

Not at the absolute rear edge unless the rest of the fleet is badly damaged, but definitely not in the lead assault layer.

Desired position:

- behind capitals and the central Titan core
- ahead of or interleaved with the farthest-back support ships only when needed
- protected by the fleet’s main combat mass

This preserves command presence while keeping the ship out of the first collision band.

### Formation Shapes By Count

`ASSAULT` should adapt based on how many ships are available.

#### Small Fleet

- one tight escort line
- one short line-ship row
- capitals and central Titan stacked behind
- support Titans and mothership trailing just behind the core

#### Medium Fleet

- escort arc or tight double-line
- broader line-ship layer
- capitals centered behind
- central Titan core in protected middle
- rear support layer separated cleanly

#### Large Fleet

- escort layer becomes a dense forward screen with flanks
- line ships form the true pressure band
- capitals occupy a wider second pressure band
- central Titans create a healing/sustainment spine
- support Titans and mothership occupy a guarded rear-half lane

### Retreat And Recovery Behavior

This part is important to the design.

When lighter ships are damaged:

- they should fall back through the line/capital layers
- they should bias retreat toward transport-healing coverage or central sustainment anchors
- they should not scatter randomly to the far edges of the map

The formation should preserve a usable internal fallback corridor, especially for:

- escorts
- missile boats
- damaged line ships
- strike-support craft returning from forward exposure

### Tactical Goal

This formation should be best for:

- attacking enemy sectors
- pushing fixed enemy positions
- preserving the mothership during an offensive
- keeping sustainment ships alive during a contested advance

It should not behave like a loose roaming skirmish net.

### Implementation Plan

Recommended implementation order:

1. add `ASSAULT` as a new fleet formation preset or doctrine mode
2. classify fleet ships into formation layers:
   - escort
   - line
   - capital
   - central Titan
   - rear support Titan
   - mothership
3. create slot generators for each layer
4. generate geometry per layer based on fleet count
5. add retreat bias toward the central Titan / transport sustainment zone
6. tune spacing so the formation does not become too long or too wide

### Implementation Notes

- escort spacing should be tighter than current generic fleet spacing
- line and capital layers should overlap enough to feel like one push, not disconnected waves
- central Titans should anchor the healing spine
- support Titans should preserve standoff spacing without falling off-screen from the rest of the fleet
- the mothership should inherit a protected command slot in the rear half

### Risks

- if spacing is too wide, the formation will feel weak and indecisive
- if spacing is too tight, collision and pathing costs may spike
- if retreat logic is too aggressive, the front line may collapse too early
- if retreat logic is too weak, lighter ships will die before using the transport-healing core

### Validation Criteria

The formation is successful when:

- the fleet visibly advances in layered order
- escort ships make first contact
- line and capital ships continue the push without exposing the mothership
- transport and tank Titans remain in the protected middle
- carrier/intel/artillery/mobile-station/liberation ships remain in the rear
- damaged light ships fall back toward the central sustainment layer instead of scattering
- the mothership survives offensive pushes better than in looser formations

## Concrete First Tasks

## Task 1

Instrument these methods with timing:

- `AISystem.update(...)`
- `AISystem.buildFleetState(...)`
- `GameRenderSystem.render(...)`
- ship render block inside render
- `Renderer.drawHUD(...)`
- `Renderer.createShieldShell(...)`

## Task 2

In `AISystem`, add a throttled fleet-state refresh path:

- rebuild every few ticks by default
- force immediate rebuild on:
  - ship destroyed
  - ship spawned
  - fleet command changed
  - target invalidated

## Task 3

In `Renderer`, prototype cached shield-shell geometry:

- role-based cache key
- faction-sensitive if silhouette differs
- invalidate only when actual shell parameters change

## Task 4

In `Renderer`, add a combat stress mode:

- simpler shields
- simpler HUD chips
- reduced low-value overlays

## Risks

- over-throttling AI can make fleets feel stupid or sluggish
- aggressive shield simplification can hurt readability
- dynamic HUD reduction can confuse the player if it hides critical information

Because of that, each phase should be validated in live campaign combat, not just unit tests.

## Validation Plan

Use one repeatable heavy scenario:

- campaign sector with multiple allied ships
- active reserves
- many shielded ships
- map and HUD both exercised

Track:

- average `frameMs`
- average `updateMs`
- average `renderMs`
- AI-specific ms
- shield-render-specific ms
- HUD-specific ms
- subjective feel during 2-3 minute sustained combat

## Definition Of Done

This performance issue is considered meaningfully addressed when:

- large campaign fights no longer collapse frame time into obvious stutter
- AI-heavy and shield-heavy sectors feel closer to normal sectors
- map/HUD overlays do not noticeably worsen already bad frames
- we have runtime telemetry that makes future regressions easy to spot

## Related Files

- `src/AISystem.java`
- `src/GameSimulationRuntime.java`
- `src/GameRenderSystem.java`
- `src/Renderer.java`
- `src/app/state/PerfTelemetry.java`
- `docs/PERFORMANCE_BOTTLENECKS.md`

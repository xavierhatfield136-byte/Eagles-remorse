# Live Performance Profile And Remediation Plan

Date: 2026-05-03  
Status: Active investigation, telemetry landed, multiple runtime remediations shipped, latest live rescans folded into current plan

## Goal

Identify what is lagging the game during real play right now, describe the highest-cost runtime paths, and define a staged plan to reduce frame time without breaking campaign behavior or combat readability.

This document is based on repeated live process samples of the running game, not just static code inspection.

## Summary

The current lag problem is not primarily a garbage collection issue.

The running game is being slowed mostly by:

1. expensive ship-spawn hull preparation on the main Swing game thread
2. expensive Java2D render work for shield effects, warp-charge shell effects, and other path-heavy visuals
3. expensive AI fleet-state rebuild work on the main Swing game thread
4. audio clip churn during combat impact spam
5. expensive HUD panel and chip drawing when many overlay elements are visible

Because update and render both run on the `AWT-EventQueue-0` thread, any hot path in simulation or rendering blocks the entire frame.

Telemetry support has now been added in code, so subsequent passes can be validated against live `AI`, `Campaign`, `Ships`, `HUD`, `Shield`, and `Map` timings in the dev overlay.

## Recent Remediation Shipped

The codebase now includes the following performance-focused fixes:

- cached audio manifest/event variant lookup so combat SFX no longer hit the filesystem on the EDT
- stricter one-shot audio throttling and duplicate suppression
- AI shared-target reuse plus per-build sensor/signature caching
- shield shell caching and reduced ECM illusion effect cost
- transport-support aura simplification
- startup/background prewarm for hull/profile/destruction caches so those assets stop faulting in during combat
- wreck rendering fallback for tiny transformed sprites
- ambient-audio hostile-contact caching so audio mix updates stop re-running full detectability scans every frame
- VFX particle degradation for tiny or overloaded scenes, including antialiasing disable inside the particle pass
- reduced background warmup scope so optional turret skin prewarm no longer burns CPU for long stretches

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
- detectability-heavy targeting logic becomes more expensive when many ships are evaluating shared targets at once

Additional live sample:

One later replay sample caught the EDT inside:

- `ShipIdentityRegistry.roleBonusFor(...)`
- `Ship.sensorRangeMultiplier(...)`
- `TargetingSystem.detectionRangeForObserver(...)`
- `TargetingSystem.isDetectableToObserver(...)`
- `AISystem.selectSharedTargetForTeam(...)`

Observed sample locations:

- `src/ShipIdentityRegistry.java:140`
- `src/Ship.java:2969`
- `src/TargetingSystem.java:231`
- `src/TargetingSystem.java:303`
- `src/AISystem.java:564`

What this adds:

- not only is fleet-state rebuild expensive, but the shared-target path is repeatedly walking identity and sensor-range math that should be cached or reused within a frame

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

Latest rescan refinement:

After the first optimization pass removed the audio manifest lookup stall, the EDT was still caught inside:

- `Ship.effectiveShieldCapacityMax(...)`
- `Renderer.drawShipShieldFaces(...)`
- `Renderer.drawEcmIllusions(...)`
- `Renderer.drawShips(...)`

Observed sample locations:

- `src/Ship.java:2772`
- `src/Renderer.java:1418`
- `src/Renderer.java:8946`
- `src/Renderer.java:9443`

What this adds:

- illusion rendering is still paying too much of the expensive ship-effect stack
- shield checks and shell generation are still heavy enough to dominate a frame by themselves
- skipping damage decals on illusions helped, but did not finish the job

## 3. Support-aura rendering is also a top render bottleneck

Later live thread samples caught the running game inside:

- `GameRenderSystem.drawTransportSupportAuras(...)`
- Java2D D3D queue flushes and Marlin tile rasterization

Observed sample locations:

- `src/GameRenderSystem.java:1296`
- `src/GameRenderSystem.java:1298`

The hot JVM stack showed time in:

- `SunGraphics2D.fillOval(...)`
- `SunGraphics2D.drawOval(...)`
- `D3DRenderQueue.flushBuffer(...)`
- `MarlinCache.touchTile(...)`

What this means:

- the transport-healing / support aura circles are expensive enough to become a primary render sink during busy fights
- anti-aliased filled and stroked ovals are not cheap when drawn many times per frame
- this cost stacks directly with shield and HUD work on the same thread

Likely symptom in play:

- large support fleets or sectors with many transports can hitch even when ship count is only moderately high
- the game may feel worse when sustainment ships are clustered on screen

## 4. Audio event lookup and clip churn are now confirmed hot paths

The newest live replay sample caught the running game inside:

- `SfxManifest.variantCount(...)`
- `AudioSystem.triggerSfxEvent(...)`
- `AudioSystem.onShieldImpact(...)`
- `CollisionSystem.handleProjectilesVsShips(...)`

Observed sample locations:

- `src/SfxManifest.java:92`
- `src/SfxManifest.java:97`
- `src/SfxManifest.java:98`
- `src/AudioSystem.java:503`
- `src/AudioSystem.java:510`
- `src/AudioSystem.java:1128`
- `src/AudioSystem.java:1138`
- `src/CollisionSystem.java:215`

The hot JVM stack showed:

- `File.listFiles(...)`
- `File.isFile(...)`
- filesystem directory scanning while shield impacts were firing

What this means:

- the game is doing live filesystem work during combat audio event dispatch
- shield-hit spam can hammer the EDT with file lookup before audio playback even begins
- audio event selection is not fully resolved/cached up front

Related live observation:

- there were also a very large number of `Direct Clip` threads visible in the process during combat

What this likely means:

- one-shot clip creation/close churn is also contributing overhead
- even if playback runs off-thread, the event-dispatch setup path is still heavy enough to stall gameplay

Likely symptom in play:

- sectors with frequent shield impacts, CIWS activity, and projectile traffic can hitch badly
- stutter may get worse exactly when lots of combat sounds should be firing

Update after the first fix pass:

- the live rescan no longer caught `SfxManifest.variantCount(...)` or filesystem directory scans on the EDT
- the lookup/cache fix appears to have worked
- `Direct Clip` proliferation is still visible and remains a secondary cleanup target

## 5. HUD chrome is also expensive

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

## 6. GC is not the main problem right now

Live heap snapshot:

- heap total: about `5.7 GB`
- heap used: about `2.46 GB`

This does not rule out allocation churn entirely, but the dominant observed stalls were not GC pauses. The game is mostly spending time doing active work on the main thread.

## 7. Ship spawn silhouette and hull-profile loading are now confirmed EDT hitches

The latest live rescan caught the EDT inside:

- `ShipHullSilhouette.loadBundledSkin(...)`
- `ShipHullSilhouette.loadRoleSkin(...)`
- `ShipHullSilhouette.buildFromSkin(...)`
- `HullGeometry.buildProfile(...)`
- `HullGeometry.sampleImpact(...)`
- `FleetShip.isTurretMountOnHull(...)`
- `SpawnSystem.spawnTeamShip(...)`
- `EconomySystem.spawnPeriodicMinersForAliveTeams(...)`

Observed sample locations:

- `src/ShipHullSilhouette.java:231`
- `src/ShipHullSilhouette.java:211`
- `src/ShipHullSilhouette.java:60`
- `src/HullGeometry.java:147`
- `src/HullGeometry.java:65`
- `src/FleetShip.java:2029`
- `src/SpawnSystem.java:176`
- `src/EconomySystem.java:432`

The hot JVM stack showed:

- `ImageIO.read(...)`
- PNG decode
- `RandomAccessFile.readFully(...)`

What this means:

- ship spawning can still trigger skin decode and hull-profile generation on the EDT
- turret conformance probes are forcing hull-profile construction in the same spawn path
- even if this only happens on first use for a role/faction/radius combination, it can produce a visible hitch in live combat

Likely symptom in play:

- miner/reserve reinforcements or first-time spawns of a hull class can produce sudden frame spikes
- sectors feel worse when new support craft are introduced mid-fight

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

Status: Implemented

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

### Delivered

- `PerfTelemetry` now exposes:
  - `aiMs`
  - `campaignMs`
  - `renderShipsMs`
  - `renderHudMs`
  - `renderMapMs`
  - `shieldRenderMs`
- these are now visible in the dev overlay during live play

## Phase 2: Fix Audio Event Lookup And Clip Churn

Status: Mostly implemented, clip churn follow-up still open

### Problems to solve

- `SfxManifest.variantCount(...)` is scanning directories during gameplay
- shield-impact SFX dispatch is hitting filesystem code under combat load
- one-shot clip creation appears to be producing excessive `Direct Clip` churn
- audio event setup is happening in a latency-sensitive gameplay path

### Work

1. precompute variant counts at startup
   - manifest scan should happen once, not during event playback
2. cache resolved variant counts and event picks
   - `eventId -> count`
   - optionally `eventId -> resolved asset list`
3. remove `File.listFiles(...)` and `File.isFile(...)` from hot gameplay paths
4. audit `triggerSfxEvent(...)`
   - make sure combat dispatch only does cooldown/routing logic, not asset discovery
5. reduce clip churn
   - consider clip reuse, line pooling, or a bounded concurrent one-shot policy
6. add telemetry for audio event rate if needed
   - shield-hit spam, hull-hit spam, CIWS-heavy events

### Files

- `src/SfxManifest.java`
- `src/AudioSystem.java`
- `src/CollisionSystem.java`

### Success criteria

- no filesystem lookup occurs on the EDT during combat audio events
- shield-impact-heavy combat no longer stalls in `SfxManifest.variantCount(...)`
- `Direct Clip` thread proliferation drops meaningfully during long fights

## Phase 3: Remove Spawn-Time Hull/Skin Loading From Combat

This is now the next highest-priority update-side target because it was caught directly during live reinforcement spawning.

Status: Implemented for hull silhouettes/profiles and destruction/wreck multipart assets

### Problems to solve

- ship spawning can trigger bundled PNG decode on the EDT
- hull silhouette generation is still done lazily in combat
- hull profile generation is still done lazily when turret mounts probe the hull
- first-use hitches are unacceptable during active sectors

### Work

1. prewarm ship hull skin lookups before gameplay heat
   - role + faction silhouette images
   - miss results as well as hits
2. prewarm or eagerly cache hull polygons at baseline radii
3. prewarm or eagerly cache `HullGeometry` profiles using `RoleStats` default radii
4. keep the spawn path read-only
   - no `ImageIO.read(...)`
   - no disk/resource scan
   - no first-time profile construction for standard hulls
5. prewarm multipart destruction and wreck assets
   - no first-time death-art PNG decode during combat

### Files

- `src/ShipHullSilhouette.java`
- `src/HullGeometry.java`
- `src/FleetShip.java`
- `src/Main.java`

### Success criteria

- live rescan no longer catches `ShipHullSilhouette.loadBundledSkin(...)` on the EDT during combat
- miner/reserve/support spawns do not cause visible one-time hitching
- death sequences no longer trigger first-use multipart asset decode on the EDT

## Phase 4: Reduce AI Fleet-State Rebuild Cost

This is the next gameplay-side optimization target after audio lookup is removed from combat.

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

## Phase 5: Reduce Render Hotspots In Shields And Support Auras

This is the next render-side optimization target.

### Problems to solve

- expensive `Area` construction in `createShieldShell(...)`
- repeated shape work per frame
- repeated work multiplied by ECM illusions and many shielded ships
- expensive anti-aliased aura fill/draw work in `drawTransportSupportAuras(...)`
- support visuals stacking on top of shield visuals in the same frame

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
6. simplify transport support auras
   - fewer rings
   - lower cadence updates
   - visibility gating by distance/zoom
   - optional coarse filled disc instead of stroked AA rings
7. skip support-aura drawing for ships that do not meaningfully affect the local screen read
   - distant
   - off-focus
   - obscured
   - low-strength effects
8. cache `Area` shell shapes per visual/width instead of rebuilding them every frame
9. make ECM illusions skip shield and warp-charge shell work entirely
10. add a stricter screen-size gate for warp-charge shell effects

### Files

- `src/Renderer.java`
- `src/GameRenderSystem.java`

### Success criteria

- render time in heavy shield fights drops substantially
- shield visuals still communicate active defense clearly
- ECM no longer multiplies render cost so aggressively
- support-aura rendering no longer dominates render samples during logistics-heavy fights

## Phase 6: Reduce HUD Render Cost

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

## Phase 7: Control Expensive Multipliers

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

1. remove spawn-time hull skin/profile loading from combat
2. cache shield and warp shell geometry, and make ECM illusions cheaper
3. continue AI fleet-state and shared-target reductions
4. finish audio clip churn cleanup
5. optimize HUD drawing
6. add dynamic stress scaling and cleanup passes

This order is chosen because:

- repeated live sampling now shows three true first-tier costs:
  - spawn-time hull loading
  - path-heavy shield/warp rendering
  - AI fleet-state/shared-target work
- audio lookup has already been materially improved, so the remaining audio issue is mostly one-shot clip churn
- HUD cleanup is useful but still not the first lever pulled

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

Complete the audio cache pass:

- precompute and cache `SfxManifest.variantCount(...)`
- remove `File.listFiles(...)` from combat playback paths
- validate shield-hit combat while watching for `Direct Clip` churn

## Task 2

In `AISystem`, add a throttled fleet-state refresh path and shared-target cache:

- rebuild every few ticks by default
- force immediate rebuild on:
  - ship destroyed
  - ship spawned
  - fleet command changed
  - target invalidated
- cache detectability/range inputs within the fleet-state build where possible

## Task 3

In `Renderer` and `GameRenderSystem`, prototype cached shield-shell geometry and cheaper support auras:

- role-based cache key
- faction-sensitive if silhouette differs
- invalidate only when actual shell parameters change
- reduce anti-aliased ring count and overdraw for transport support visuals

## Task 4

In `Renderer`, add a combat stress mode:

- simpler shields
- simpler HUD chips
- reduced low-value overlays

## Risks

- aggressive audio throttling can make combat feel flat or under-responsive
- over-throttling AI can make fleets feel stupid or sluggish
- aggressive shield simplification can hurt readability
- dynamic HUD reduction can confuse the player if it hides critical information

Because of that, each phase should be validated in live campaign combat, not just unit tests.

## Validation Plan

Use one repeatable heavy scenario:

- campaign sector with multiple allied ships
- active reserves
- many shielded ships
- active transport support/healing ships
- high projectile/shield-impact churn
- map and HUD both exercised

Track:

- average `frameMs`
- average `updateMs`
- average `renderMs`
- AI-specific ms
- shield-render-specific ms
- ship-render-specific ms
- map-render-specific ms
- whether audio-trigger stacks still appear in live thread samples
- HUD-specific ms
- subjective feel during 2-3 minute sustained combat

## Definition Of Done

This performance issue is considered meaningfully addressed when:

- large campaign fights no longer collapse frame time into obvious stutter
- AI-heavy and shield-heavy sectors feel closer to normal sectors
- shield-impact-heavy fights do not stall on audio lookup
- logistics/support-heavy fights do not collapse due to aura rendering
- map/HUD overlays do not noticeably worsen already bad frames
- we have runtime telemetry that makes future regressions easy to spot

## Related Files

- `src/SfxManifest.java`
- `src/AudioSystem.java`
- `src/CollisionSystem.java`
- `src/AISystem.java`
- `src/GameSimulationRuntime.java`
- `src/GameRenderSystem.java`
- `src/Renderer.java`
- `src/app/state/PerfTelemetry.java`
- `docs/PERFORMANCE_BOTTLENECKS.md`

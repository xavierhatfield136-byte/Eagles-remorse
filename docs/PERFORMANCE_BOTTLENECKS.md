# Performance Bottlenecks & Optimization Guide

## Current Issues

This document outlines the primary performance bottlenecks identified in large fleet battles and provides a prioritized action plan aligned with Phase 3 of the Gripe Checklist.

---

## 🔴 Critical Bottlenecks

### 1. CIWS Pellet Spam (Worst Offender)

**Problem:**
- Fires every `0.12` seconds (cooldown)
- Creates **2+ pellets per burst** (`ciwsPelletsPerBurst = 2`)
- Each pellet lives for **18 frames** (`ciwsPelletLife`)
- With 10+ ships in a fleet, hundreds of projectiles spawn every second
- Each pellet participates in full collision detection (circle tests + expensive hull geometry checks)

**Performance Cost:**
- With 10 ships, each firing CIWS: ~280 projectiles/second created
- Active CIWS projectiles at any time: ~100-300
- Each projectile tested against 20+ enemy ships = **6,000+ collision checks/frame**

**Solution:** Remove visual effects from CIWS pellets and optionally use culled/simplified collision for small pellets.

**Files:**
- `src/Ship.java` (`fireCiwsPellets` method)
- `src/VFX.java` (impact effect spawning)
- `src/CollisionSystem.java` (collision detection)

---

### 2. Explosion & Impact Particle Effects (High)

**Problem:**
- Every projectile hit spawns visual effects:
  - Impact sparks (`spawnImpactSparks`)
  - Impact bursts (`spawnImpactBurst`)
  - Explosion blooms (`spawnImpactBloom`)
  - Muzzle flashes (`spawnMuzzleFlash`)
- VFX cap: **1,100 active particles** (`VFX.MAX`)
- Explosion cap: **900 active explosions** (`Explosion.MAX_EFFECTS`)
- Each explosion renders complex shapes (rings, plasma, hazes) with trigonometry

**Performance Cost:**
- Rendering loop: iterates 1,100+ particles + 900 explosions per frame
- Each explosion calculates multiple ring radii, stroke widths, corona effects
- Visibility culling helps but doesn't eliminate the iteration cost

**Solution:** Remove explosion effects from small shots (CIWS, light projectiles), simplify effect spawning.

**Files:**
- `src/VFX.java` (all spawn methods)
- `src/Explosion.java` (rendering calculations)
- `src/GameRenderSystem.java` (render loop)

---

### 3. Projectile Collision Detection (High)

**Problem:**
- `CollisionSystem.handleProjectilesVsShips()` iterates all projectiles
- For each projectile: query nearby ships, then run circle collision + hull geometry tests
- Hull geometry test is expensive (polygon/point intersection checks)

**Performance Cost:**
- 300+ active projectiles × 20+ ships = **6,000+ circle collision tests/frame**
- If hit: expensive `HullGeometry.projectileIntersectsShip()` geometry check
- Broad-phase query helps but doesn't scale linearly with projectile count

**Solution:** Cull CIWS projectiles from collision detection or use cheaper collision predicates.

**Files:**
- `src/CollisionSystem.java` (main collision loop)
- `src/HullGeometry.java` (geometry checks)

---

## 🟡 Secondary Issues

### 4. Room Hazard Updates (Medium)

**Problem:**
- `updateRoomHazards()` called every frame for each ship
- Iterates 8+ rooms per ship, then 20+ ships per battle = 160+ room updates/frame
- Each room update: damage ticks, spread logic, fire intensity calculations, random number generation
- Fire spawn VFX creation per room per frame

**Performance Cost:**
- Heavy on CPU due to random number generation and floating-point math
- Accumulates in large fleets

**Solution:** Batch room updates, reduce random RNG calls, or increase tick intervals.

**Files:**
- `src/Ship.java` (`updateRoomHazards` method)

---

### 5. Rendering Overhead (Medium)

**Problem:**
- GameRenderSystem renders ~2,500+ drawable objects per frame:
  - 1,100 VFX particles
  - 900 explosions
  - 500+ projectiles
  - 20+ ships
  - Wreck chunks
- All with visibility checks (culling helps but iteration is still expensive)

**Performance Cost:**
- Each render pass: iterate all objects, check bounds, draw
- Graphics2D operations accumulate (fill ovals, draw lines, etc.)

**Solution:** More aggressive culling, reduce effect counts, cull off-screen projectiles.

**Files:**
- `src/GameRenderSystem.java` (main render loop)
- `src/Renderer.java` (draw methods)

---

## ✅ Recommended Action Plan

### Immediate (Quick Wins) — Phase 3, Step 1

**1. Disable explosion effects on CIWS pellets**
- CIWS pellets are tiny; visual feedback is low-value
- Expected savings: **30-40% VFX overhead reduction**
- Implementation: Add flag to `spawnHullImpact()` to skip CIWS effect spawning

**2. Optionally simplify or remove CIWS visuals**
- Current: muzzle flashes, bloom effects for each burst
- Phase 3 explicitly recommends: "Remove or heavily simplify CIWS visuals"
- Expected savings: **10-20% rendering overhead**

**3. Object pooling for particles**
- Currently creating new `Particle` objects on every spawn
- Reuse from a pool to reduce garbage collection
- Expected savings: **5-10% GC pressure**

### Medium-term — Phase 3, Step 2

**4. Profile fogged sector rendering**
- Keep fogged ships fully simulated, but **fully unrendered**
- Currently: fogged ships still iterate render checks
- Expected savings: **variable, depends on map size**

**5. Render cull off-screen CIWS projectiles**
- Keep simulation but skip rendering
- Expected savings: **10-15% rendering overhead**

**6. Cheaper collision for small projectiles**
- CIWS vs ships: use circle-only collision (skip hull geometry)
- Small projectiles rarely need exact geometry checks
- Expected savings: **20-30% collision detection overhead**

### Long-term — Phase 3, Step 3

**7. Reduce active CIWS fire rate or pellet count in fleet modes**
- Lower `ciwsCooldown` or `ciwsPelletsPerBurst` dynamically
- Tuning value: test empirically with known-large battles

**8. Batch room hazard updates**
- Update every 2-3 frames instead of every frame for non-critical rooms
- Spread fire spread / instability checks across frames

---

## Implementation Checklist

- [ ] Disable VFX on CIWS impacts
- [ ] Remove CIWS muzzle flash effects
- [ ] Implement particle object pooling
- [ ] Profile and validate improvements
- [ ] Measure largest-map FPS before and after
- [ ] Document performance gains

---

## Testing & Validation

**Benchmark scenario:**
- Largest campaign map
- 2 full fleets (20+ ships + CIWS each)
- Sustained fleet combat for 3+ minutes
- Measure: FPS, memory, CPU utilization

**Before/After metrics to track:**
- Average FPS
- Peak FPS drops
- Memory usage
- VFX active count
- Explosion active count
- Projectile count

---

## References

- **Gripe Checklist:** [CAMPAIGN_ESCORT_DIRECTION.md](CAMPAIGN_ESCORT_DIRECTION.md) — Phase 3: Performance and Render Culling
- **VFX System:** [src/VFX.java](../src/VFX.java)
- **Collision System:** [src/CollisionSystem.java](../src/CollisionSystem.java)
- **Game Rendering:** [src/GameRenderSystem.java](../src/GameRenderSystem.java)

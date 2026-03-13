# Resource Rush Faction Characterization (Non-Red Teams)

This document defines the two non-red opposition flavors you want to use alongside the common red team threat profile.

Reference baseline:
- Red team is currently `ENEMY` / `Team B` and plays as the familiar kinetic baseline.
- This doc defines identity for the other two factions:
  - `TEAM_C`: Directed-energy doctrine
  - `TEAM_D`: Missile-dominant doctrine

## 1. TEAM_C Characterization (Directed Energy)

Working name:
- **Aegis Lattice**

Combat fantasy:
- Precision energy fleet that controls space with accurate, high-visibility beam/bolt fire.
- Feels disciplined, shield-forward, and technologically advanced.

Weapon doctrine:
- Primary weapons: directed-energy bolts/beams (minimal ballistic spam).
- Secondary weapons: limited missiles for finishers, not main pressure.
- Point defense: moderate.

Battle behavior:
- Prefers medium standoff range.
- Prioritizes target focus and shield stripping.
- Repositions deliberately rather than swarming.

Strengths:
- Reliable hit quality at range.
- Strong shield pressure and clean damage readability.
- Good coordinated fire lanes.

Weaknesses:
- Less burst alpha than missile fleets.
- Can be overrun if flanked or rushed.
- Lower sustained projectile saturation than kinetic doctrine.

Visual/audio identity:
- Bright teal/cyan energy fire.
- Crisp electric impact signatures.
- Cleaner, less mechanical firing cadence.

## 2. TEAM_D Characterization (Missile-Heavy)

Working name:
- **Viper Barrage Syndicate**

Combat fantasy:
- Overwhelming missile salvos and area denial.
- Feels aggressive, opportunistic, and dangerous in spikes.

Weapon doctrine:
- Primary pressure: missiles on most combat hulls.
- Guns/energy: minimal, mostly backup.
- Point defense: light-to-moderate (tradeoff for missile density).

Battle behavior:
- Cycles salvo windows and keeps distance while reloading.
- Focuses clustered targets and retreat paths.
- Punishes stationary players and capital ships.

Strengths:
- High burst damage and panic pressure.
- Strong anti-formation damage.
- Excellent finisher potential on weakened targets.

Weaknesses:
- Vulnerable to strong CIWS/PD screens.
- Lower consistent DPS between salvos.
- More sensitive to ammo-cycle timing and overkill.

Visual/audio identity:
- Orange/amber missile exhaust trails.
- Frequent launch cues and explosive impact signatures.
- Heavier "volley" rhythm than continuous fire.

## 3. Resource Rush Role Split

Intended matchup texture:
- Red Team (existing): stable kinetic baseline pressure.
- Team C (energy): precision control and shield-breaking pressure.
- Team D (missiles): burst/salvo threat and area denial.

Result:
- Three distinct threat reads instead of "same weapons, different color."
- Better target-priority decisions in mixed battles.

## 4. Design-to-Implementation Notes

Suggested doctrine mapping target:
- `ALLY` / `PLAYER`: existing mapping (unchanged unless desired).
- `ENEMY` (red): current kinetic baseline.
- `TEAM_C`: map to directed-energy profile.
- `TEAM_D`: map to missile-forward profile (new profile or strong missile bias pass).

Suggested balance guardrails:
- Team C should win on consistency and shield pressure.
- Team D should win on burst windows, not constant DPS.
- Red team remains the all-rounder anchor.

## 5. Art Reminder (Projectile Sprites)

Reminder checklist:
- Update **energy projectile sprites**.
- Update **kinetic projectile sprites**.
- Update **missile projectile sprites**.

Why this matters:
- The new faction identities need immediate visual readability at combat speed.
- Projectile silhouette/color must match doctrine at a glance.

## 6. Additional Reminders

Gameplay/UI reminders:
- Add **Guided Missile Cruiser** as a **player-selectable/playable hull** in the ship selection flow.
- On larger ships, increase **shield visual radius** so bow/stern are fully enclosed by the shield graphic.
- Keep credit economy tuned to **1.5x earnings** versus previous baseline.

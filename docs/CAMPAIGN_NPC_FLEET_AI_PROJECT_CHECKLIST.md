# Campaign NPC Fleet AI Project Checklist

This project is about the campaign-layer fleets the player can meet in space: hostile search groups, red task forces, Blue/Green/Yellow allied groups, trade columns, patrols, escorts, and any open-space NPC fleet contact. It is not about rewriting authored single-player missions right now.

The goal is for NPC fleets to feel like persistent operational actors: they organize, choose routes, hold contact, react to the player, and create readable decisions before combat.

For the deeper living-war lifecycle contract, faction personalities, anti-idle rules, and mission templates, use `STRATEGIC_FLEET_BEHAVIOR_SYSTEM_SPEC.md` as the canonical design reference.

## P0 Contact Persistence And No Vanishing

- [x] Prevent hostile campaign fleets from despawning while they are visible, recently seen, pursuing, or close enough for recon to matter.
- [x] Add last-known-contact memory so recon sweeps can reacquire a fleet that broke sensor lock instead of finding nothing.
- [x] Keep a hostile force on the map until it reaches a clear terminal state: engaged, destroyed, retreated to a named source, docked at a named source, or merged into another force.
- [x] Add a regression for the pre-Earth route: a hostile spawned in the theater must not vanish before the player can intercept, evade, or strike it.
- [x] Show clear map/HUD language when a contact breaks lock: lost bearing, estimated vector, last known range, and sweep recommendation.

## P0 Intercept And Pursuit Behavior

- [x] Make hostile fleets commit to intercept routes that actually close distance against a traveling player.
- [x] Give intercept fleets predictive lead targets instead of chasing the player's old position.
- [x] Distinguish pursuit, shadowing, blockade, and retreat behavior in both movement and HUD readouts.
- [x] Ensure high-risk Earthward travel creates at least one meaningful hostile decision: strike, divert, fight, or use support.
- [x] Add a route simulation test that verifies an interceptor starting far above/below the player continues closing for several updates.

## P0 Fleet Organization

- [x] Give each NPC fleet a readable composition identity: scout screen, interdiction group, hunter-killer, blockade group, convoy, patrol, trade column, relief screen.
- [x] Make fleet speed and aggression derive from composition, not just a generic force speed.
- [x] Prevent unsupported lone fleets from behaving like full battle groups unless their doctrine says raider/scout.
- [x] Add escort and screen behavior so convoys and allied columns move as formations instead of loose pings.
- [x] Add tests for force role, composition, strength, and movement intent matching.

## P1 Encounter Decision Quality

- [x] Preserve player agency before contact: visible contact, warning, strike window, route-divert option, and combat/auto-resolve prompt.
- [x] Make recon sweeps affect actual contact state: identify, reacquire, improve strike quality, or reveal decoys.
- [x] Make strikes matter against NPC fleets before battle: slow, scatter, damage, force retreat, or split escorts.
- [x] Ensure hostile fleets that are hit by strikes adapt: evasive route, call support, retreat, or hard commit.
- [x] Add after-action text showing what happened to the NPC fleet after strike, intercept, retreat, or auto-resolve.

## P1 Tactical Handoff

- [x] When an NPC fleet opens combat, spawn ships from its campaign composition and strength instead of generic encounter fill.
- [x] Carry campaign approach direction into tactical entry direction.
- [x] Preserve damaged or scattered fleet state after tactical combat resolves.
- [x] Make retreating NPC ships affect the campaign force instead of simply disappearing.
- [x] Add tests linking campaign force identity to tactical spawned ships.

## P1 Allied And Neutral Fleet Behavior

- [x] Make Blue, Green, and Yellow fleets pursue their own goals instead of simply idling near hubs.
- [x] Make allied fleets respond to nearby hostile pressure with escort, reinforce, retreat, or call-in behavior.
- [x] Make Yellow trade groups avoid high-danger lanes unless escorted or paid through support actions.
- [x] Make Green patrol/relay groups stabilize routes and improve recon in their operating radius.
- [x] Add tests proving allied support changes later route risk and hostile movement, not just resources.

## P2 AI Diagnostics And Tooling

- [x] Add a campaign fleet AI debug overlay: intent, target, ETA, contact state, source, destination, and despawn/retreat reason.
- [x] Add a deterministic soak harness for several minutes of campaign fleet movement.
- [x] Log force state transitions with concise telemetry events.
- [x] Add a report that lists fleets that became idle, unreachable, or vanished without a terminal reason.
- [x] Create a focused save/test scenario for the pre-Earth route playtest.

## First Acceptance Scenario

- [x] Start from the campaign spawn and sail straight toward Earth.
- [x] A hostile force appears before or inside the theater below Earth.
- [x] The force keeps a persistent last-known contact if it leaves direct detection.
- [x] Recon sweep can reacquire or refine the contact.
- [x] The hostile either closes to a prompt, blocks the route, retreats to a named source, or remains visible as a last-known contact.
- [x] The player has a real reason to consider a strike before reaching Earth.

## Validation

- [x] Focused NPC fleet AI tests pass.
- [x] Focused campaign travel/strike pressure tests pass.
- [x] Manual pre-Earth straight-line route playtest recorded.
- [x] Manual allied/neutral fleet movement playtest recorded through deterministic regression coverage.

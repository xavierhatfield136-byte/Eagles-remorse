# Next Steps To Playable Alpha

Date: 2026-06-01
Status: Primary implementation roadmap

## Target State

Finish a Windows-first 2D playable alpha before expanding the release scope.

The alpha should support an 8-15 hour campaign with:

- persistent player, allied, neutral, civilian, and hostile forces;
- slow strategic-map movement with visible provenance for enemy pressure;
- tactical battles launched from real campaign encounters;
- take-control and auto-resolve choices;
- readable mission warnings and uncertain information;
- moderate fuel, ore, credits, repair, salvage, and resupply pressure;
- shipyard and refit flows;
- save/load;
- visible action buttons and keyboard-only navigation;
- deliberately accepted placeholder art and audio where final assets are not ready.

The release decisions behind this scope live in `PRODUCTION_OWNER_WORKSHEET.md`.

## Work Order

### A1. Mission Variety And Information Warfare

This is the next implementation sprint.

- [x] Instantiate the alpha mission set from live campaign situations: escort, interception, blockade run, station defense, evacuation, rescue, salvage race, recon, ambush, mine clearance, tow, retreat, and rendezvous.
- [x] Give each generated mission an objective, reward, failure state, aftermath, force provenance, and visible warning.
- [x] Apply battlefield identity and hazards to tactical spaces rather than catalog entries alone.
- [x] Connect contact confidence, signatures, scans, stealth, decoys, jamming, false contacts, and information decay to live tactical AI and HUD feedback.
- [x] Connect support packages and strategic strikes to campaign costs, cooldowns, warnings, and tactical consequences.
- [x] Add visible UI actions for take control, auto-resolve, scouting, support, and strike decisions.
- [x] Add encounter-matrix tests covering mission, battlefield, faction, hazard, sensors, support, victory, retreat, and save/load.
- [x] Add more replay variation in regional events, traffic generation, and multi-step transit discoveries.

Owner checkpoint: run a 20-45 minute scripted campaign session. Record whether encounters feel warned, understandable, varied, and grounded in visible campaign fleets.

### A2. Economy And Logistics

- [x] Make fuel, ore, credits, repairs, salvage, and resupply authoritative across travel, mining, refits, construction, strikes, and tactical readiness.
- [x] Advance basic market prices, shortages, inventories, rationing, contracts, and recovery opportunities through campaign time.
- [x] Give resource shortages visible warnings and recovery options before they can fail a mission.
- [x] Make AI fleets pay resource costs and react to shortages so hostile fleets cannot spawn forever for free.
- [x] Add readable cargo, market, salvage, resupply, shipyard, and refit UI.
- [ ] Tune strike damage against its resource cost through live play.
- [ ] Verify the ore loop end to end: mine, return, buy ships or upgrades, relaunch with a larger fleet.

Owner checkpoint: run a route, salvage, resupply, and refit loop. Record whether pressure feels moderate and whether every major consequence has warning text.

### A3. Fleet Doctrine And Command Friction

- [x] Derive command nodes from live fleet composition, flagship state, relay state, captains, damage, and tactical positions.
- [x] Apply bandwidth, channel modes, queued orders, delay, acknowledgment, interpretation, and relay redundancy to tactical orders.
- [x] Apply retreat thresholds, rescue priorities, surrender policy, and captain exceptions to AI behavior.
- [ ] Drive cohesion, crossfire, isolation, panic, rallying, discipline, and reserve rotation from tactical events.
- [x] Add doctrine editing, pre-battle review, command-link overlay, and after-action UI.
- [ ] Add acceptance tests for flagship loss, relay loss, flag transfer, panic, recovery, and save/load.

Owner checkpoint: test flagship loss, relay loss, retreat, and recovery. Record whether command friction adds decisions without obscuring controls.

### A4. Alpha Stations And Campaign Persistence

- [x] Make station services, damage, repairs, capture, evacuation, garrisons, relay state, and reconstruction affect normal play.
- [x] Persist evolving locations across visits: wreck fields, salvage, hazards, memorial state, service loss, scars, refugees, checkpoints, and histories.
- [ ] Apply a deliberately small orbital-layer subset where it materially changes navigation, sensors, logistics, rescue, or presentation.
- [ ] Add the real multi-slot save UI, rotating autosaves, checkpoint metadata, migration fixtures, corruption recovery, and player-visible recovery messages.
- [ ] Make shareable seeds reproduce validated campaign setup and document intentionally nondeterministic systems.
- [ ] Add long-campaign transition tests proving state evolves from play rather than seeded examples.

Owner checkpoint: revisit a station or battlefield after damage and confirm that the persistent change is visible and understandable.

### A5. Presentation, Accessibility, And Alpha Validation

- [x] Generate an asset inventory and duplicate-asset approval report from the existing validator output.
- [x] Audit art and audio event mappings against files actually present.
- [ ] Replace only the most disruptive placeholder sprites, icons, HUD buttons, map panels, portraits, and voice lines before alpha.
- [ ] Verify distinct empty-space, hub, allied, neutral, hostile, and operational-district presentation.
- [ ] Add screenshot baselines and audio-event validation for major alpha screens.
- [ ] Run 1280x720 and 1920x1080 layout checks.
- [ ] Run keyboard-only, contrast, scaling, remapping, captions, quiet-mode, and warning-readability acceptance passes.
- [ ] Run the retained campaign manual checklist from `CAMPAIGN_SESSION_CHANGE_AUDIT_CHECKLIST.md`.
- [ ] Profile largest-map FPS and document measured gains against the alpha performance targets.

Owner checkpoint: approve or reject the alpha presentation report and run the final scripted acceptance session.

## Engineering Support Work

These are required when their owning alpha slice lands:

- [ ] Replace capability strings with executable validators or remove the claim.
- [ ] Add structured telemetry for major campaign transitions and failure reasons.
- [ ] Add deterministic headless campaign playback where it accelerates regression coverage.
- [ ] Add randomized campaign-transition fuzzing, memory checks, frame-time budgets, and large-fleet soak runs to CI.
- [x] Track every manual acceptance scenario explicitly.

## Recorded Owner Playtest

The first hands-on alpha pass confirmed that travel, mining, salvage, resupply,
refit, fleet operations, warnings, controls, revisiting prior spaces, and
post-ECM combat readability work as expected. Follow-up tuning remains open:

- Increase encounter density without turning travel into constant interruption.
- Make fleet ammunition pressure noticeable and readable during longer routes.
- Recheck the fleet commissioning layout after moving its columns below the
  doctrine strip.

## Completed Follow-Up Engineering

- Travel attrition now accumulates fractional fuel, supplies, and ammunition
  costs across frame-sized updates instead of rounding them down to zero.
- Travel attrition remainders and transit-event pressure persist through
  checkpoints.
- Transit-event cadence was increased modestly so routes produce more signals
  and discoveries without turning travel into constant interruption.
- The SFX manifest now resolves all `63/63` required event mappings. Eleven
  missing alpha placeholders were generated without overwriting existing audio.

## Measured Validation Notes

- `performanceGuardrailsCi` passes its late-campaign stress battle and `100`
  checkpoint save/load cycles.
- `XrayReadabilityHarness --strict` passes with realtime updates, visible
  panels, `3` overlap pairs, and maximum overlap area `26`.
- `ChecklistV2Harness --strict` still fails its legacy room-hit and x-ray draw
  budgets. Treat those as measured optimization work, not completed acceptance.
- `Phase9TelemetryHarness --strict --seconds=60` emits structured telemetry but
  does not pass strict acceptance because that sample produced zero hazard
  ignitions.
- `CampaignParityHarness` remains a stale authored-sector harness and does not
  yet satisfy deterministic headless campaign-playback acceptance for the
  strategic overmap flow.

## Deferred Beyond Alpha

These are not blockers for the 2D playable alpha:

- multiplayer and cooperative roles;
- full scenario editor and player-facing mod browser;
- mod distribution;
- procedural star systems;
- cinematic replay camera and autonomous spectator mode;
- new-game-plus;
- deep political blocs and fully simulated civilian societies;
- full branching campaign chapters;
- advanced lineage systems;
- full localization;
- complex challenge modes;
- final art and audio replacement pass;
- full 3D conversion.

The 3D feasibility lane is intentionally retained in `FUTURE_3D_TRACK.md`.

## Finalization Queue

The remaining unchecked boxes are intentionally open. Finish them in this
order:

1. Owner acceptance pass: ore-driven fleet growth, strike value, mission-space
   flavor, fresh campaign start, overmap softening, remote tactical strike,
   marker selection, A3 flagship/relay loss, and the corrected commissioning
   layout.
2. Presentation decision: review `ALPHA_ASSET_APPROVAL_REPORT.md`, approve
   placeholders or identify targeted replacements, then run 1280x720,
   1920x1080, keyboard-only, contrast, captions, quiet-mode, and warning checks.
3. Performance repair: bring `ChecklistV2Harness --strict` room-hit and x-ray
   draw costs under budget and stabilize the Phase 9 hazard telemetry scenario.
4. Remaining alpha engineering: live cohesion/panic/rally effects, orbital
   subset, multi-slot save UX, rotating autosaves, recovery messaging,
   reproducible seeds, long-campaign transition tests, screenshot baselines,
   campaign-transition telemetry, deterministic strategic-overmap playback,
   and randomized CI fuzz/soak coverage.
5. Keep broader section-28 expansion work deferred unless it is explicitly
   promoted into the Windows-first 2D alpha.

## Completion Rule

A box moves to complete only when it is reachable in normal play, changes authoritative state, explains costs and failure reasons in the UI, persists where needed, has automated coverage for its critical transition, and has a recorded manual acceptance scenario when feel or presentation matters.

# Playable Alpha Execution Plan

Status: Superseded by `NEXT_STEPS_TO_PLAYABLE_ALPHA.md`.

This shorter first-pass plan remains as a record of the scope decision. Use `NEXT_STEPS_TO_PLAYABLE_ALPHA.md` for implementation order and carried-forward checklist work.

This plan narrows section 28 to the Windows-first playable-alpha target defined in `docs/PRODUCTION_OWNER_WORKSHEET.md`.

## Alpha Must-Ship Order

### A1. Mission Variety And Information Warfare

- [x] Instantiate the alpha mission set from live campaign conditions: escort, interception, blockade run, station defense, evacuation, rescue, salvage race, recon, ambush, mine clearance, tow, retreat, and rendezvous.
- [x] Apply tactical battlefield identity, hazards, contact uncertainty, detection confidence, scans, stealth, decoys, jamming, and information decay.
- [x] Surface warnings, contact confidence, support availability, mission state, and take-control versus auto-resolve choices as visible UI actions.
- [x] Add encounter-matrix regression tests.

- [x] Owner playtest checkpoint: run a 20-45 minute scripted campaign session and record whether encounters feel warned, understandable, varied, and grounded in visible campaign-map fleets.

### A2. Economy And Logistics

- [x] Connect fuel, ore, credits, repairs, salvage, and resupply to mining, travel, tactical readiness, refits, construction, and strikes.
- [x] Advance market state and basic shortages through campaign time.
- [x] Give shortages warnings and recovery options before they can fail a mission.
- [x] Ensure AI fleets pay resource costs and cannot spawn forever for free.
- [x] Add readable resource, resupply, salvage, and shipyard UI.
- [ ] Re-tune strategic strikes so they are not a cheap guaranteed combat punch, and add a readable make/buy/salvage recovery path if they remain scarce.
- [ ] Re-tune early ore, repair, and transport-ship sustain so a large zone-one fleet cannot coast through the campaign without meaningful pressure.
- [ ] Make diplomacy and allied trade/call-in choices materially affect later encounters.

- [x] Owner playtest checkpoint: run a 20-45 minute route, salvage, resupply, and refit loop. Record whether pressure feels moderate and whether every major consequence has warning text.

### A3. Fleet Doctrine And Command Friction

- [x] Derive command nodes from live fleet composition and damage state.
- [x] Apply bandwidth, delays, acknowledgments, relay redundancy, retreat thresholds, rescue priorities, and surrender policy to tactical orders.
- [x] Add visible doctrine editing, pre-battle review, command-link overlay, and after-action feedback.

- [ ] Owner playtest checkpoint: test flagship loss, relay loss, retreat, and recovery. Record whether command friction adds decisions without making controls obscure.

### A4. Alpha Stations And Campaign Persistence

- [x] Make station services, damage, repair, capture, evacuation, and reconstruction affect normal campaign play.
- [x] Persist evolving locations, wreck fields, visible scars, memorial state, service loss, and recovery.
- [x] Keep deeper politics, full civilian simulation, advanced lineage, and complex challenge systems outside the alpha.

- [x] Owner playtest checkpoint: revisit a station or battlefield after damage and confirm the changes are understandable and persistent.

### A5. Presentation, Accessibility, And Release Validation

- [x] Generate an asset inventory and duplicate review report.
- [x] Owner accessibility pass: keyboard-only, high contrast, captions, quiet mode, 1280x720 readability, and 1920x1080 readability passed.
- [ ] Replace only the most disruptive placeholder sprites, icons, HUD buttons, map panels, portraits, and voice lines before alpha.
- [ ] Remove temporary crew dialogue and voice lines until replacement lines are ready.
- [ ] Replace the old ship-destruction sine-wave placeholder sound.
- [ ] Normalize damage-stage visuals and decide final dispositions for wreck, prop, portal, and map-icon placeholders.
- [ ] Fix top-screen HUD/menu text crowding where elements overlap.
- [ ] Validate 1280x720 and 1920x1080 layouts, keyboard-only navigation, contrast, captions, quiet mode, and visible consequence warnings.
- [ ] Run Windows playthroughs for a new campaign, save/load, defeat, victory, and a longer campaign session.

- [ ] Owner playtest checkpoint: approve or reject the alpha presentation report and run the final scripted acceptance session.

## Explicitly Post-Release

- [x] Confirm multiplayer and cooperative roles remain post-release.
- [x] Confirm the full scenario editor and mod browser remain post-release.
- [x] Confirm procedural star systems remain post-release.
- [x] Confirm battle replay remains post-alpha; cinematic replay is the preferred eventual depth.
- [x] Confirm the cinematic replay camera and autonomous spectator mode remain post-release.
- [x] Confirm new-game-plus remains post-release.
- [x] Confirm deep political blocs and fully simulated civilian societies remain post-release.
- [x] Confirm full branching campaign chapters remain post-release.
- [x] Confirm advanced crew memorial and lineage systems remain post-release.
- [x] Confirm full localization remains post-release.
- [x] Confirm complex challenge modes remain post-release.
- [x] Confirm the final art and audio pass beyond alpha usability remains post-release.

## Owner Inputs Needed During Implementation

- [x] Provide short scripted playtest notes at each checkpoint.
- [ ] Provide screenshots for unclear, cramped, or inconsistent UI.
- [ ] Provide approval decisions for the asset-review report.
- [ ] Make a final yes/no decision when a borderline feature should be simplified for alpha or deferred.

## Completion Evidence

- A1: live alpha mission refresh now creates the required 13-family mission set from tracked forces, support hubs, wreck fields, damaged hull leads, and blockade pressure. Existing campaign HUD paths retain uncertain contacts, sensor decay, hazards, strategic strikes, warnings, support actions, and take-control versus auto-resolve prompts.
- A2: campaign time advances markets, shortages, contract deadlines, and a bounded hostile deployment reserve. The visible resource board includes shortage recovery guidance. Existing live travel, mining, hub service, refit, construction, and strike paths remain authoritative.
- A3: the live fleet refresh derives flagship, relay, fallback, bandwidth, and damage state. Strategic and tactical fleet panels expose doctrine cycling and command-link overlay controls. Division orders pass through the bandwidth queue, retain acknowledgments, and feed retreat, rescue-priority, and surrender-policy behavior.
- A4: normal hub services project repair, refit, shipyard, supply, fuel, trade, salvage, and intel effects into persistent station state. Theater capture changes project damage, service loss, wrecks, scars, memorial state, and reconstruction work. Existing galaxy locations retain scars, route notes, relationships, and spent-site outcomes across save/load.
- A5 inventory: `ALPHA_ASSET_APPROVAL_REPORT.md` summarizes validator output. The full duplicate list is generated at `build/reports/production-validation.txt`.
- A5 x-ray readability: `XrayReadabilityHarness --strict` passes with realtime updates, visible panels, `3` label-overlap pairs, and maximum overlap area `26`.
- Deferred scope: confirmed in `PRODUCTION_OWNER_WORKSHEET.md` and `POST_RELEASE_STRETCH_ROADMAP.md`.

## Owner Playtest Evidence

- A1: enemy encounters felt understandable and normal, with good warning text and working controls. Encounter density felt sparse and needs a tuning pass.
- Section 28 strike balance: strikes are too cheap and plentiful because they act like a guaranteed extra hit in combat. The owner also wants a visible way to make, buy, or salvage more strikes if they become a scarce resource.
- Section 28 economy loop: ore is too plentiful, early fleet growth is too easy, and damage-control plus transport repair support makes sustain too forgiving.
- Section 28 diplomacy: diplomacy has almost no impact; traders and green/yellow support are not needed to keep pushing forward.
- Section 28 scope: battle replay, visual battlefield editor, mod browser, custom scenarios, challenge mode, and New Game Plus are all post-alpha/post-release.
- Section 28 presentation: current hull skins, turret skins, shields, trails, and station models are approved. Damage stages, props, portals, map icons, ambience/music/warnings/radio/voice/caption behavior, and temporary crew voice lines need replacement or removal before alpha acceptance.
- Section 28 accessibility: keyboard-only controls, high contrast, captions, quiet mode, and 1280x720/1920x1080 readability passed owner review.
- A2: travel, mining, salvage, resupply, refit, and fleet operations were reachable and worked as expected. Ammunition pressure was not noticeable and needs tuning.
- A4: previously visited spaces remained revisitable for resource recovery and continued fleet operations.
- Combat readability improved after removing ship collisions and tactical ECM; player guns no longer fell silent without explanation.
- Presentation defect found during the pass: fleet commissioning section headings overlapped the doctrine strip. The commissioning columns were moved below the strip and covered by a renderer regression test.

## Remaining Acceptance Queue

The unchecked boxes above remain intentionally open until their required evidence exists.

- Owner playtest notes are still required for A3 and the final acceptance session.
- Encounter density and ammunition pressure received an earlier tuning pass, but the section 28 worksheet reopens campaign pressure: open-space enemy fleets, strike costs/recovery, ore abundance, repair sustain, diplomacy usefulness, hazards, crises, endgames, and scoring still feel too easy.
- The asset report now has owner approval for hull skins, turret skins, shields, trails, and station models. It still needs targeted replacement/removal for the ship-destruction placeholder sound, temporary crew voices, damage visuals, props, portals, map icons, and audio presentation systems.
- HUD/menu text crowding remains open where top-screen elements overlap.
- The repaired `ChecklistV2Harness --strict` now passes phase-8 room consistency at `100%` and gameplay-room coverage at `39/39`. Performance acceptance remains open: x-ray draw cost is consistently above its legacy budget, room-hit timing is borderline around the threshold, and the scenario update comparison is noisy across runs.
- `performanceGuardrailsCi` passes its late-campaign stress battle and `100` isolated save/load cycles. `Phase9TelemetryHarness --strict --seconds=60` still needs hazard-scenario stabilization because the latest sample emitted no hazard ignitions.
- Windows playthroughs for new campaign, save/load, defeat, victory, and longer-session behavior still require hands-on execution.

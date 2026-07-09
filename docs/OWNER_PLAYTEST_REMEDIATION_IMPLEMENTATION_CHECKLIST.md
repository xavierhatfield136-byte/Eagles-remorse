# Owner Playtest Remediation Plan and Implementation Checklist

**Source:** `Eagles Remorse Owner Playtest Worksheet.docx`, completed July 2026  
**Status:** In progress - Vertical Slice 1 accepted; Vertical Slice 2 owner-accepted with visibility polish notes; Vertical Slice 3 objective economy ledger/audit gates added; tactical escort cohesion fix protected  
**Release decision:** NO-GO until the release gates in this document pass  
**Primary finding:** Tactical combat is already the strongest part of the game. The strategic campaign does not yet make its fleets, territory changes, pressure, economy, or player choices believable and legible during ordinary play.

## 1. Outcome

The next stabilization pass should make the campaign map behave like a visible war fought by persistent fleets, not a collection of hidden variables. The player must be able to see who is moving, understand why territory changes, predict the cost and danger of travel, use reconnaissance and strikes deliberately, and feel pressure increase over the campaign.

This is a repair-and-proof plan, not a broad feature expansion. Tactical combat should be preserved while the strategic layer, economy, campaign arc, and a small set of combat/UI defects are corrected.

### Hard scope freeze

This stabilization pass must not add new factions, new ship classes, new diplomacy systems, new campaign modes, new art pipelines, or new tactical mechanics unless a minimal change is required to fix a blocker explicitly listed in this document.

- [ ] Reject unrelated feature work from the stabilization branch.
- [ ] Require every implementation task to cite the blocker and release gate it addresses.
- [ ] Prefer removing, disabling, or simplifying unreliable behavior over expanding it.
- [ ] Record desirable but non-blocking ideas in the existing backlog rather than implementing them during this pass.

## 2. What the playtest established

### Release blockers

- Green and Yellow fleets were usually absent from the overworld while Red contacts appeared and vanished.
- Visible fleets could disappear in roughly 20 seconds without a readable outcome.
- Territory changed too quickly and without a visible fleet, operation, battle, or explanation.
- Green could take a large portion of Yellow territory in under a minute.
- Yellow and Red regions reused Green stations or faction assets.
- Recon Sweep appeared to do nothing.
- Fleet posture appeared to do nothing.
- Travel exposed no understandable cost, ETA risk, interception pressure, or meaningful tradeoff.
- Campaign pressure did not rise; the player could travel to Earth with little resistance.
- Early, middle, and late campaign play felt substantially the same.
- The ending did not reflect major choices or campaign state.

### Major balance and usability problems

- Mining was necessary but could produce excessive ore by waiting at a patch.
- Post-Slice-2 owner note: fleets now mostly appear by sensor range, but some persistent intel ghosts still appear outside the sensor sphere and invasion movement/intent needs clearer arrows or operation lanes. This is accepted as polish for now and should be revisited in the map-readability pass.
- Repair, transports, hubs, and passive recovery erased attrition.
- Fuel, supplies, ammo, and other logistics felt like hidden or ghost variables.
- Strategic strikes were plentiful, had little counterplay, and could instantly destroy targets.
- Difficulty presets did not feel different during short play sessions.
- Disabled buttons did not explain why they were unavailable.
- The initial objective was effectively inferred as "go north/get to Earth."
- Tutorial/control hints could cover a menu and did not have an obvious dismissal path.

### Tactical and presentation defects

- Fighters could orbit each other without firing.
- Escorting fighters and ships jittered while trying to hold formation.
- Stealth ships lacked enough impact after revealing themselves.
- CIWS ships had a narrow anti-carrier job and little ship-to-ship usefulness.
- Reserve/reinforcement control was the weakest-rated combat control area.
- Some turrets were visibly offset from their hull mounts.
- Shield effects could tint ship sprites incorrectly.

### Strengths to protect

- Tactical combat, tactical AI, damage pacing, large battles, capitals, and titans were rated highly.
- Ship survivability after shields fail felt correct.
- Damaged AI ships retreating toward support was convincing.
- Save/load persistence, defeat clarity, accessibility checks, visible modes, and overall performance were broadly successful.
- Combat readability and enjoyment both received top ratings.

## 3. Diagnosis

The current regression suite passes the focused campaign-map, living-war, travel-pressure, strike, difficulty, mining, and first-hour tests. That conflicts with the completed owner playtest. The suite proves many internal contracts, but it does not yet prove the shipped player experience.

The remediation must close four proof gaps:

1. **Runtime wiring:** verify that the settings and models exercised by tests are the same ones used by a normal Campaign Ops run.
2. **Projection:** verify that simulated fleet movement, operations, territory, cost, and pressure reach the actual map and HUD.
3. **Time scale:** verify behavior over real player time, not only a single method call or accelerated test tick.
4. **Comprehension:** verify that a player can explain what happened from the UI without debug knowledge.

Do not mark a task complete merely because an internal field changed or a readout string exists.

## 4. Implementation order

1. Capture a reproducible playtest baseline and add player-visible proof.
2. Fix strategic fleet existence, visibility, faction identity, operations, and territory.
3. Make reconnaissance, posture, routes, travel risk, and strikes consequential.
4. Rebalance mining, logistics, repairs, and attrition.
5. Differentiate campaign phases, difficulty presets, and ending outcomes.
6. Repair the narrow tactical, UI, and presentation defects.
7. Split oversized classes behind characterization tests.
8. Run a fresh owner pass and release gate.

### Operating model: one vertical slice at a time

This document is the master stabilization plan, not a single implementation prompt. Complete and verify one bounded slice before authorizing the next:

1. **Vertical Slice 1:** Persistent fleets, one visible operation, and one lawful territory outcome.
2. **Vertical Slice 2:** Recon, posture, and route risk visibly affect those same fleets and operations.
3. **Vertical Slice 3:** Economy and attrition feed into travel, repair, and strike decisions.
4. **Vertical Slice 4:** Campaign arc, difficulty, objectives, and ending state.
5. **Cleanup:** Tactical, control, UI, and presentation defects.
6. **Post-stabilization:** Architecture extraction after behavior is protected, except for a minimal extraction required to make the active slice safe.

- [ ] Do not begin a later slice while the current slice's exit gate is failing.
- [ ] Keep each implementation prompt limited to one slice or a smaller task inside it.
- [ ] Re-run the owner-visible acceptance scenario after every slice.

## 5. Phase 0 - Reproduce and instrument the shipped experience

### Build and evidence lock

- [x] Record the exact build version, Git commit, seed, preset, resolution, and save used for every verification run.
- [ ] Preserve the completed worksheet as immutable source evidence.
- [ ] Add a `playtest-baseline` run profile that starts a fresh Standard Command Campaign Ops game without debug tools.
- [x] Add a deterministic seed set covering at least one Green-heavy, Yellow-heavy, and Red-heavy opening.
- [ ] Record a 20-minute strategic timeline for each seed: fleet spawn, movement, contact state, operation assignment, battle, disappearance, capture, and territory owner. *(Harness supports 1,200-second runs and now streams partial timeline/snapshot evidence, but a complete all-seed 20-minute run has not finished inside the command window yet.)*
- [x] Record player-visible screenshots or structured presentation snapshots at the same checkpoints.
- [x] Compare authoritative physical fleets with emitted map markers and report every fleet that exists but cannot be understood by the player.
- [x] Compare every territory ownership change with its authorizing operation, participating fleets, battle/outcome, and player-facing event.
- [x] Add a diagnostic reason for every fleet-marker disappearance: out of sensor range, stale intel, merged, retreated, docked, destroyed, or invalid projection.
- [x] Fail the harness if a fleet disappears without one of those reasons.

### Player-visible acceptance harness

- [x] Add an end-to-end `OwnerPlaytestCampaignAcceptanceTest` or equivalent harness that uses the normal Campaign Ops bootstrap and UI presentation model.
- [x] Assert that Green, Yellow, and Red can all produce observable strategic activity during the seeded run.
- [ ] Assert that physical movement results in changing player-visible positions when valid intel exists.
- [ ] Assert that exact, approximate, stale, and lost contacts remain behaviorally and visually distinct.
- [x] Assert that an operation arrow appears only when operation intel is known and points between real sites.
- [x] Assert that every captured site has a traceable operation and outcome.
- [ ] Assert that travel forecasts and actual deductions agree within rounding tolerance.
- [ ] Assert that Recon Sweep changes at least one measurable intel result when a valid target exists.
- [ ] Assert that changing posture changes at least one forecast and the corresponding realized outcome.
- [ ] Keep the existing focused campaign regression suite green throughout the work.

**Exit gate:** The owner's reported failures can be reproduced or decisively explained on the normal run path. Telemetry, presentation snapshots, and authoritative state tell the same story.

## 6. Phase 1 - Persistent fleets, visible war, and lawful territory

### Fleet authority and lifecycle

Primary code areas: `CampaignSystem.updateCampaignForceSimulation`, `advanceCampaignForcePosition`, `syncCampaignSearchGroupsToForces`, `updateCampaignForceContactState`, `resolveNpcFactionFleetBattles`, and checkpoint persistence.

- [x] Confirm every moving strategic contact maps to one canonical `CampaignForce` ID.
- [x] Confirm linked `GalaxySearchGroup` objects never become a second physical fleet or a second position writer.
- [x] Remove any remaining name-based fleet identity or position synchronization from normal gameplay paths.
- [x] Verify Green, Yellow, and Red fleets all receive valid routes and continue moving while off-screen.
- [x] Verify far-distance update throttling preserves real-time-equivalent speed and does not create jumps or long freezes.
- [x] Prevent presentation density logic from creating or relocating physical fleets.
- [x] Prevent contact loss from destroying, stopping, or teleporting a physical fleet.
- [x] Prevent merge, docking, retreat, and destruction cleanup from looking like unexplained deletion.
- [x] Keep a stale/last-known record long enough to communicate loss of contact, then remove it cleanly.
- [x] Add a concise event/log line when a known fleet docks, retreats, merges, or is destroyed.

### Map projection and contact visibility

Primary code areas: `CampaignSystem.activeSupportMarkers`, `addCampaignForceMarkers`, `supportMarkerForCampaignForce`, `CampaignMapPresentationModel`, and strategic map rendering in `Renderer`.

- [x] Audit why friendly/non-hostile fleets that are internally known were not visible during the owner run.
- [x] Ensure allied reports actually reach the normal presentation model outside direct sensor range.
- [x] Ensure the map shows movement vectors only for valid live intel.
- [x] Ensure stale contacts show last-known position, age, uncertainty, and a reacquire action rather than a live vector.
- [x] Ensure contact markers cannot vanish between adjacent frames because two projection paths disagree.
- [x] Show a selected fleet's faction, mission/intent, movement state, target, and latest report in plain language.
- [x] Add a non-debug Sensor Net/event feed that explains important allied and enemy movement.
- [x] Keep map clutter bounded by prioritizing actionable contacts rather than hiding whole factions.

### Faction assets and site ownership

- [x] Separate a site's original faction identity from its current occupying owner.
- [x] Replace unintended Green station copies in Yellow and Red regions with correct faction assets/data.
- [x] When a site is captured, show occupation/control through banners, trim, flags, or overlays without rewriting the site's original identity.
- [x] Validate every generated and authored station against its faction/region content definition.
- [x] Add a test that a Yellow or Red site cannot silently instantiate a Green station template unless an explicit captured-state rule requires it.

### Territory and operation legality

Primary code areas: `FactionAttackCommitmentSystem`, campaign operation lifecycle, `updateCampaignBattles`, and `applyCampaignBattleTerritoryOutcome`.

- [x] Require every ownership change to reference one authoritative operation or an explicitly authored player outcome.
- [x] If a territory change cannot name the attacking fleet, defending fleet or site, operation ID, arrival state, and outcome reason, the territory change must not happen.
- [x] Require assigned physical fleets to muster, travel, arrive, and resolve before capture.
- [x] Prevent timers, aggregate variables, or proximity-free simulation from capturing territory by themselves.
- [x] Prevent one operation from capturing multiple unrelated sites.
- [x] Cap early-campaign operation tempo so a faction cannot take roughly half another faction's territory in under one minute.
- [x] Give Yellow valid defensive, retreat, escort, and recovery behavior rather than treating it as passive territory.
- [x] Show planning, mustering, en-route, battle, capture, retreat, and completed/failed phases when the player has sufficient intel.
- [x] Explain every territory change in the event feed with attacker, defender, site, and outcome.
- [x] Add seeded long-run invariants for no teleport capture, no capture by destroyed fleets, no capture before arrival, and bounded early churn.

### Temporary acceptable fallback

A static but explainable war is better than a chaotic unexplained war.

- [x] If operation legality cannot be guaranteed, pause autonomous NPC captures rather than permitting an untraceable ownership change.
- [x] Keep fleets moving, reporting, fighting, retreating, and leaving aftermath while captures are paused.
- [x] Show a diagnostic and player-safe event when a capture is rejected by the legality gate.
- [x] Re-enable autonomous capture only after the operation-to-outcome acceptance tests pass.

**Exit gate:** In three seeded 20-minute runs, each faction's visible activity is explainable, no known fleet vanishes without a reason, and no territory changes without real fleets and a traceable outcome.

Phase 1 implementation check completed in this pass:

- `OwnerPlaytestPhaseOneAuditTest` verifies canonical fleet projection, off-screen physical movement for Green/Yellow/Red, structured disappearance reasons, and faction-site identity separation.
- Phase 1 gate passed with `OwnerPlaytestPhaseOneAuditTest`, the vertical slice/regression fleet authority tests, map/intel milestone tests, focused faction attack tests, strike telemetry, and Yellow faction behavior tests.

## 7. Phase 2 - Strategic agency: recon, posture, travel, pressure, and strikes

### Recon Sweep and intelligence

Primary code areas: fleet posture/recon methods around `CampaignSystem.requestTacticalRecon`, sweep cost/radius/intel helpers, and campaign intel records.

- [x] Define the exact player promise for Recon Sweep: what it can detect, sharpen, reacquire, or rule out.
- [x] Ensure the action targets real nearby unknown, approximate, or stale intel.
- [x] Charge and display the correct supply/exposure cost before confirmation.
- [x] Show a before/after result: new contact, improved classification, reduced uncertainty, or "no contacts found."
- [x] Prevent a successful sweep from silently changing only hidden fields.
- [x] Add cooldown and blocked-reason feedback.
- [x] Add a normal-run acceptance test proving a sweep visibly improves a seeded contact.

### Fleet posture

- [x] Give every posture a short, quantified forecast: speed, fuel, supplies, detection, exposure, interception risk, and event bias.
- [x] Confirm the selected posture is used by actual travel, detection, and encounter calculations.
- [x] Show the active posture in the top-level strategic HUD without duplicating it across panels.
- [x] Show a confirmation/event when posture changes.
- [x] Add paired seeded runs demonstrating materially different outcomes for Recon Sweep, Silent Running, Combat Patrol, Rescue Priority, Raider Doctrine, and Logistics Conservation.

### Travel risk and campaign pressure

- [x] Show distance, ETA, fuel, supplies, ammo, contact pressure, territory risk, and likely interception before committing a route.
- [x] Deduct the forecast resources from the same authoritative ledgers displayed to the player.
- [x] Add route choices where safer and faster paths have understandable tradeoffs.
- [x] Ensure hostile operations and fleets can intersect the player's route rather than appearing or disappearing arbitrarily.
- [x] Make regional pressure rise from Red control, unresolved operations, ignored threats, player noise, and campaign phase.
- [x] Make allied control, relays, patrols, and completed operations reduce pressure in visible ways.
- [x] Establish early/mid/late pressure bands and verify they are reached during a typical 3-5 hour successful campaign.
- [x] Prevent a Standard Command player from reaching Earth with no meaningful escalation or resistance.

Phase 2 pressure implementation check completed in this pass:

- `OwnerPlaytestPhaseTwoPressureAuditTest` verifies Red-control/unresolved-threat/player-noise/phase pressure, allied-control/relay/patrol/completed-operation relief, EARLY/MID/LATE pressure bands, and a defended Standard Earth approach.
- Phase 2 gate passed with `OwnerPlaytestPhaseTwoPressureAuditTest`, vertical slice 2/3, strategic travel pressure, strategic strikes, economy balance, difficulty runtime/outcome, and baseline harness tests.

### Strategic strikes

Primary code areas: strike preflight, cost, target resolution, launch, moving strike objects, impact, retaliation, and rearm paths in `CampaignSystem`.

- [x] Make campaign-map strikes discoverable and usable before tactical combat when valid intel and range permit.
- [x] Use finite, visible inventory shared consistently between campaign and tactical contexts.
- [x] Show ammo/fuel/supply/charge cost, range, intel requirement, effect estimate, retaliation risk, and replenishment route.
- [x] Prevent default strikes from instantly deleting capital/titan targets; prefer damage, disruption, forced retreat, delayed movement, or tactical advantage.
- [x] Add counterplay: interception, evasion, hardening, warning, retaliation, cooldown, or exposure.
- [x] Ensure stale or approximate contacts cannot be struck as though their position were exact.
- [x] Verify strike effects persist into territory, fleet state, and tactical entry.
- [x] Add a balance gate limiting repeated strike use over one economy loop.

**Exit gate:** A player can explain why a route is risky, what posture changes, what Recon Sweep accomplished, and what a strike will cost and provoke before committing.

## 8. Phase 3 - Economy, logistics, repair, and attrition

### Resource model and UI

Primary code areas: `EconomySystem`, campaign travel attrition, finite economy reconciliation, mining/hauling, hub services, and economy presentation.

- [x] Choose one authoritative ledger for credits, fleet ore, yard ore, fuel, supplies, ammo, and repair materials.
- [x] Remove or clearly label compatibility mirrors so the UI never shows a ghost variable.
- [x] Show current amount, capacity, expected use, and replenishment source for every strategic resource.
- [x] Make Fleet Ore and Yard Ore naming and transfer rules explicit.
- [x] Ensure travel, repair, refit, strikes, and commissions consume the displayed resources.
- [x] Add ledger reconciliation tests across travel, combat, docking, save/load, and rearm.

### Mining and growth

- [x] Measure ore earned per minute for starter, midgame, and Transport Titan configurations.
- [x] Set a target time-to-first-major-upgrade and tune mining around it.
- [x] Add meaningful ore-site depletion, extraction slowdown, danger, cargo, or opportunity cost.
- [x] Prevent ten minutes of unattended mining at one patch from trivializing the campaign economy.
- [x] Preserve mining as a useful deliberate activity rather than removing it.
- [x] Make salvage situationally competitive with mining after combat.
- [x] Add seeded economy-loop tests that measure fleet power before and after one and three loops.

### Repairs and attrition

- [x] Make field repair consume finite supplies and operate below full yard capability.
- [x] Make transport repair support consume and visibly report supplies.
- [x] Make hubs differ in repair speed, capacity, price, or available services.
- [x] Prevent passive repair and transports from erasing all consequences between encounters.
- [x] Ensure shortages can occur on Standard without creating an unavoidable death spiral.
- [x] Add recovery choices: buy supplies, salvage, divert to a hub, reduce posture cost, accept partial repairs, or retreat.
- [x] Tune Standard so attrition is noticeable over multiple encounters and Iron so it is materially harsher.

**Exit gate:** One economy loop creates a meaningful upgrade but not a runaway fleet; repairs restore readiness at a visible cost; shortages and route costs can influence the next decision.

**Phase 3 implementation evidence:**

- `OwnerPlaytestVerticalSliceThreeTest` now reconciles the authoritative ledger after route travel, tactical/strategic combat spending, hub rearm docking, and checkpoint restore.
- `CampaignEconomyBalanceAuditTest` sets the first-major-upgrade pacing target at ~3,200 ore in ~18 minutes based on measured starter mining output, while preserving the unattended-patch ceiling.
- `CampaignDifficultyOutcomeSeparationTest` verifies paired-seed route attrition separates Relaxed, Standard, Tactical Only, and Iron Command, with Iron materially harsher than Standard.
- Phase 3 gate run: `.\gradlew.bat test --tests CampaignEconomyBalanceAuditTest --tests OwnerPlaytestVerticalSliceThreeTest --tests CampaignDifficultyOutcomeSeparationTest`.

## 9. Phase 4 - Campaign arc, difficulty, objectives, and ending

### Campaign pacing and identity

- [x] Define distinct early, middle, and late campaign verbs, threats, and economic constraints.
- [x] Give Green, Bright Yellow, Dark Yellow, and Red recognizable fleet behavior and site identity.
- [x] Introduce new strategic problems over time instead of repeating Green-style hubs and encounters.
- [x] Make optional sites, allies, diplomacy, and fleet growth alter later operations and Earth readiness.
- [x] Add a continuously visible main objective with the immediate next step and reason.
- [x] Replace "go north" inference with explicit route guidance that still allows exploration.
- [x] Show Earth readiness/lock conditions and what actions improve them.
- [x] Target an average successful Standard campaign length of 3-5 hours.

### Difficulty presets

Primary code areas: `app.config.ExperienceSettings` and every consumer of command complexity, lethality, strategic pressure, attrition, and mode flags.

- [x] Trace each preset field to a live runtime consumer and remove dead settings.
- [x] Make Relaxed, Standard, Tactical Only, Command Only, and Iron Command produce visibly different rules within 15-30 minutes.
- [x] Ensure Tactical Only and Command Only change gameplay structure, not merely multipliers or labels.
- [x] Show preset rules before starting and in the pause/options summary.
- [x] Add paired-seed outcome tests for contact frequency, resource loss, enemy pressure, and recovery difficulty.
- [x] Keep Standard as the default.

### Choice-aware ending

- [x] Define the campaign-state inputs that the ending summarizes: allies, territory, faction relations, rescued/abandoned sites, fleet losses, operations, and Earth readiness.
- [x] Create at least three materially different ending summaries from those inputs.
- [x] Verify major optional decisions alter the final battle, ending text, or post-campaign state.
- [x] Preserve unlock/result persistence after returning to the menu.

**Exit gate:** The player can distinguish campaign thirds, presets differ in play rather than description, the objective remains clear, and the ending reflects recorded choices.

**Phase 4 implementation evidence:**

- `CampaignArcSummarySystem` splits campaign-arc presentation, objective guidance, preset rule summaries, and ending summaries out of `CampaignSystem`.
- `OwnerPlaytestPhaseFourCampaignArcAuditTest` verifies EARLY/MIDDLE/LATE identity, faction behavior/site identity, explicit route guidance, Earth readiness/lock lines, 3-5 hour Standard target, preset rule differences, Standard default, three ending families, optional-choice effects, and checkpoint-restored unlock/result persistence.
- Existing gates `CampaignDifficultyRuntimeAuditTest`, `CampaignDifficultyOutcomeSeparationTest`, `CampaignPhaseFiveDifficultyAttritionTest`, and `CampaignPhaseSixReputationAidTest` remain green against the Phase 4 readouts.
- Phase 4 gate run: `.\gradlew.bat test --tests OwnerPlaytestPhaseFourCampaignArcAuditTest --tests CampaignDifficultyRuntimeAuditTest --tests CampaignDifficultyOutcomeSeparationTest --tests CampaignPhaseFiveDifficultyAttritionTest --tests CampaignPhaseSixReputationAidTest`.

## 10. Phase 5 - Tactical, control, UI, and presentation cleanup

### Fighter and formation behavior

Primary code areas: `AISystem`, `CarrierSystem`, fleet formation/escort logic, and tactical AI tests.

- [x] Reproduce fighter-versus-fighter orbit deadlock with a deterministic scenario.
- [x] Add a firing solution or breakaway/re-attack rule when two fighters orbit without firing.
- [x] Add a time-bounded acceptance check: valid opposing fighters must fire, disengage, or deliberately reposition instead of orbiting indefinitely.
- [x] Add arrival and velocity tolerance to escort slots so ships do not continuously correct around the mothership.
- [x] Stagger or reserve nearby escort slots to prevent multiple ships fighting over one point.
- [x] Verify escort tolerance does not reduce collision avoidance or formation responsiveness.

### Ship-role balance

- [x] Measure stealth-ship damage and survival during the first reveal window.
- [x] Give stealth ships enough burst, disruption, target access, or escape value to justify their fragility.
- [x] Preserve detection counterplay; do not turn stealth into permanent untargetability.
- [x] Give CIWS hulls a modest secondary ship-to-ship role without weakening their anti-fighter identity.
- [x] Re-test carrier/picket/capital interaction so the strong combined-arms loop remains intact.

### Reserve and reinforcement control

- [x] Identify why reserve/reinforcement control scored 1/5: discovery, feedback, selection, timing, or command execution.
- [x] Show reserve composition, arrival rule, ETA/cooldown, spawn edge, and blocked reason.
- [x] Provide one obvious deploy/recall action and immediate confirmation.
- [x] Add a tutorial prompt only when reserves first become relevant.

### UI and onboarding

- [x] Move or collapse top-of-screen hint/checklist content so it never covers an active menu.
- [x] Add an obvious dismiss/toggle action and remember the preference.
- [x] Show disabled-button reasons in tooltips and/or inline text.
- [x] Display the current mining interaction mode as "Hold F" or "Toggle F" at the point of use.
- [x] Explain crew automation and AI state before the player needs to hunt for it.
- [x] Keep the strategic top fold focused on immediate objective, selected target, route risk, and primary actions.

### Art defects

- [x] Audit turret hardpoints against hull-local coordinates, sprite origin, rotation, and scale.
- [x] Fix the reported Blue hyperweapon Titan turret offset and add a regression snapshot.
- [x] Separate shield compositing from hull sprite coloration so shield color cannot tint the underlying ship.
- [x] Add representative faction/hull screenshot baselines for turret placement and shield rendering.

**Exit gate:** The reported orbit deadlock, escort jitter, menu obstruction, missing disabled reasons, turret offset, and shield tint are no longer reproducible.

**Phase 5 implementation evidence:**

- `PhaseFiveTacticalCleanupSystem` adds player-facing reserve controls, deploy/recall confirmation, reserve tutorial prompt gating, top-hint collapse preference, crew automation explanation, strategic top fold, role-balance measurements, and art baseline contracts.
- `OwnerPlaytestPhaseFiveTacticalCleanupAuditTest` verifies fighter deadlock/breakaway acceptance language, escort tolerance rules, stealth/CIWS/carrier-picket-capital role contracts, reserve composition/ETA/spawn/blocker/deploy/recall, tutorial prompt timing, top-hint collapse preference, crew automation text, strategic top fold, Blue hyperweapon titan hardpoint placement, and shield/turret baseline coverage.
- Existing gates `AISystemSmallCraftRangeTest`, `AISystemEscortFormationTest`, `CampaignStrategicUiReadabilityTest`, `CampaignPhaseTenAccessibilityInputTest`, `TacticalReadabilitySystemTest`, and `TitanGeometryRegressionTest` remain green.
- Phase 5 gate run: `.\gradlew.bat test --tests OwnerPlaytestPhaseFiveTacticalCleanupAuditTest --tests AISystemSmallCraftRangeTest --tests AISystemEscortFormationTest --tests CampaignStrategicUiReadabilityTest --tests CampaignPhaseTenAccessibilityInputTest --tests TacticalReadabilitySystemTest --tests TitanGeometryRegressionTest`.

## 11. Phase 6 - Architecture and file decomposition

Current high-risk files include approximately:

- `CampaignSystem.java` - 51,000+ lines
- `Renderer.java` - 18,000+ lines
- `Ship.java` - 7,000+ lines
- `AISystem.java` - 5,000+ lines

This phase is post-stabilization by default. Refactoring must be behavior-preserving and must not be mixed into balance commits. During active stabilization, extract code only when the smallest practical extraction directly supports the current vertical slice and reduces its implementation risk.

- [x] Do not begin broad decomposition while any earlier release-blocking phase is incomplete.
- [x] Require every stabilization-time extraction to name the active slice it enables.
- [x] Defer cleanup-only moves, renames, package reorganizations, and aesthetic abstractions until after owner acceptance.

### Characterization first

- [x] Add characterization tests around every region being extracted.
- [x] Record public/static call sites before moving code.
- [x] Keep save formats, stable IDs, enum names, and serialized field semantics compatible.
- [x] Use small compile-and-test steps after each extraction.

### Proposed extraction sequence

- [ ] Extract campaign fleet simulation and lifecycle from `CampaignSystem` into `CampaignFleetSimulationSystem`.
- [ ] Extract campaign intelligence/contact resolution into `CampaignIntelSystem`.
- [ ] Extract operation/territory resolution into `CampaignOperationSystem` and `CampaignTerritorySystem` while retaining one authority path.
- [ ] Extract travel, posture, and route forecasts into `CampaignTravelSystem`.
- [ ] Extract strategic strike preflight/launch/impact into `CampaignStrikeSystem`.
- [x] Extract campaign finite-economy reconciliation into `CampaignEconomySystem` while keeping tactical mining in `EconomySystem`.
- [x] Extract campaign marker/presentation projection into a read-only `CampaignMapPresentationModel` boundary.
- [ ] Split strategic map, tactical HUD, overlays, tooltips, and ship rendering out of `Renderer`.
- [ ] Split fighter/escort steering policies from `AISystem` behind focused behavior interfaces.
- [ ] Split `Ship` state, damage, weapons, power, and presentation-facing helpers without changing serialized identity.

### Architecture gates

- [x] No new parallel fleet, operation, territory, resource, or contact authority is introduced.
- [x] Rendering and UI projection remain read-only.
- [x] Every extracted module has a narrow responsibility and targeted tests.
- [ ] The full suite passes after every completed extraction batch.
- [x] File-size reduction is treated as a result of responsibility separation, not a goal achieved by arbitrary partial classes.

**Phase 6 implementation evidence:**

- `PhaseSixArchitectureAuditSystem` records completed stabilization-time extractions, active slice rationale, authority impact, public/static call sites, characterization tests, guardrails, and explicitly deferred broad extractions.
- `OwnerPlaytestPhaseSixArchitectureAuditTest` verifies every completed extraction names an active slice, has call-site inventory, has characterization coverage, does not introduce parallel authority, and keeps `CampaignMapPresentationModel` read-only and used by `Renderer`.
- `docs/OWNER_PLAYTEST_PHASE_6_ARCHITECTURE_EVIDENCE.md` records current oversized-file inventory, completed extractions, call-site inventory, deferred extraction rationale, and the Phase 6 targeted gate.
- Broad authority moves such as `CampaignFleetSimulationSystem`, `CampaignIntelSystem`, `CampaignOperationSystem`, `CampaignTravelSystem`, `CampaignStrikeSystem`, and major `Renderer`/`AISystem`/`Ship` splits remain intentionally unchecked until their required characterization and save/schema protections are in place.

## 12. Verification matrix

### Automated

- [ ] Compile and full unit/regression suite pass.
- [ ] Existing campaign map rework milestones remain green.
- [x] New owner-playtest acceptance harness passes on all approved seeds.
- [ ] Ten-minute and twenty-minute strategic soak reports contain no unexplained fleet disappearance or illegal capture.
- [ ] Save/load preserves fleet identity, operation membership, intel sources, territory, resources, posture, route, and strike inventory.
- [x] Economy soak stays within approved ore-growth and attrition bands.
- [x] Difficulty paired-seed tests show material outcome separation.
- [x] Tactical fighter/escort deterministic scenarios pass.
- [ ] Screenshot baselines pass for strategic map, overlays, turret mounts, and shield compositing.

### Manual owner pass

- [ ] Fresh Standard Command campaign, first hour.
- [ ] One complete mining/hub/refit/relaunch economy loop.
- [ ] Observe at least one Green, Yellow, and Red fleet movement.
- [ ] Follow one operation from planning/muster through outcome.
- [ ] Lose and reacquire one hostile contact.
- [ ] Use Recon Sweep and explain the visible result.
- [ ] Change posture and observe a forecast plus realized difference.
- [ ] Use one strategic strike before tactical combat and observe counterplay/consequence.
- [ ] Verify territory changes are understandable and paced.
- [ ] Verify faction stations and assets are correct.
- [ ] Play representative early, middle, and late campaign sections.
- [ ] Verify the ending reflects at least two major choices.
- [ ] Recheck fighter engagements, escort formations, reserves, hints, disabled actions, turrets, and shields.

## 13. Release gates

- [ ] No P0 defects.
- [ ] No unexplained known-fleet disappearance.
- [ ] No territory transfer without an authoritative, player-explainable cause.
- [ ] Green, Yellow, and Red all demonstrate persistent strategic activity.
- [ ] Recon Sweep, posture, route risk, logistics, and strikes all produce visible and measurable effects.
- [ ] Standard economy does not snowball from one unattended mining patch.
- [ ] Attrition matters without creating an unrecoverable Standard campaign.
- [ ] Campaign pressure rises and the Earth approach is defended.
- [ ] Early, middle, and late campaign phases feel different.
- [ ] Difficulty presets are behaviorally distinct.
- [ ] The ending reflects campaign state and choices.
- [ ] Tactical combat strengths remain intact.
- [ ] Full automated suite and owner acceptance pass are green on the same candidate build.
- [ ] Owner changes release recommendation from NO-GO to GO WITH KNOWN ISSUES or GO.

## 14. First implementation slice

Start with one bounded vertical slice before broad balance work:

- [x] Reproduce the missing/vanishing fleet behavior on the owner seed or a deterministic replacement seed.
- [x] Add the fleet-disappearance reason ledger and projection parity report.
- [x] Fix one Green, one Yellow, and one Red fleet so each persists, moves, and renders through the normal campaign path.
- [x] Follow one real operation from muster to battle to a single legal territory outcome.
- [x] Show that lifecycle in the Sensor Net/event feed and map presentation.
- [x] Add an end-to-end regression for the slice.
- [x] Run a 20-minute owner-visible soak and review it before proceeding to recon, posture, strikes, or economy.

Automated portion completed on three deterministic seeds. Two rendered reviews exposed missing Green fleet legibility, ambiguous territory circles, repeated Red fleet displacement, and faction-geography incoherence; those presentation and authority paths were corrected and the owner accepted the revised Slice 1 experience before authorizing the remaining document.

This slice attacks the largest release blocker and establishes the proof method the rest of the plan depends on.

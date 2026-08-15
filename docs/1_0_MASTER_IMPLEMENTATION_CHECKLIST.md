# 1.0 Master Implementation Checklist

Date created: 2026-06-23
Status: Active
Authority: `1_0_OWNER_DECISIONS_AND_IMPLEMENTATION_ROADMAP.md`

First-hour/Steam candidate feature-freeze tracker: `FIRST_HOUR_EXPERIENCE.md`.

## Core Goal

Deliver Eagles Remorse 1.0 as a free public Windows release centered on deep
fleet command, tactical combat, a persistent living war, traceable logistics,
fleet growth, and a replayable 8-15 hour campaign.

The game should let the player grow from a vulnerable Blue flotilla into a major
force without weakening that power fantasy. Challenge should come from a larger,
smarter, better supplied, and more active war.

## Completion Standard

Do not mark an implementation item complete merely because a class, field,
catalog entry, debug readout, or test fixture exists.

A player-facing system is complete only when:

- [ ] It is reached through ordinary gameplay or an intentional menu.
- [ ] It affects authoritative campaign or tactical state.
- [ ] Its costs, requirements, results, and failure reasons are visible.
- [ ] It preserves required state through save and load.
- [ ] Its critical success and failure transitions have automated coverage.
- [ ] Its presentation is readable at 1280x720 and 1920x1080.
- [ ] Its balance or usability has human acceptance evidence when required.
- [ ] It does not depend on debug commands to function.
- [ ] It does not falsely advertise model-only behavior as playable.

## Global Safety Rules

- [ ] Preserve current ship time-to-kill unless a focused defect requires change.
- [ ] Preserve detailed internal ship damage.
- [ ] Preserve free strategic-map movement.
- [ ] Preserve multiple viable fleet compositions and playstyles.
- [ ] Preserve persistent ship names, histories, and ownership.
- [ ] Do not create unexplained faction ships.
- [ ] Do not replenish fleets without inventory, ore, credits, production, or a
  documented reserve.
- [ ] Do not silently discard existing player saves.
- [ ] Do not broadly rewrite `CampaignSystem.java` without an extraction plan and
  focused regression coverage.
- [ ] Do not add post-release systems merely because related model state exists.
- [ ] Keep officer careers, political blocs, advanced civilian society, crises,
  legacy systems, multiplayer, editors, replay, challenges, and mod distribution
  outside the 1.0 critical path.
- [ ] Keep each phase independently testable.
- [ ] Run `git diff --check` after every implementation pass.
- [ ] Keep the full Gradle test suite passing.

# Phase 0 - Baseline, Measurement, And Save Contract

## 0.1 Capture The Current Baseline

- [x] Record the current version from `VERSION`.
- [x] Record the current git commit or working-tree snapshot.
- [x] Run the full test suite.
- [x] Run `performanceGuardrailsCi`.
- [x] Run screenshot regression.
- [x] Run production validation.
- [x] Run save/load soak.
- [x] Run campaign-transition fuzzing.
- [x] Record current test duration.
- [x] Record current performance-harness results.
- [x] Record current screenshot signatures.
- [x] Record current asset-validation results.
- [x] Record current campaign save-schema version.

Baseline evidence: `1_0_BASELINE_2026-06-24.md`.

## 0.2 Establish 1.0 Save Compatibility

- [x] Inventory every campaign field added since the earliest supported alpha.
- [x] Identify fields that are required for authoritative live systems.
- [x] Identify debug/readout-only fields.
- [x] Identify future/model-only fields retained for compatibility.
- [x] Define defaults for every missing field in an older save.
- [x] Define fallback behavior for unknown enum values.
- [x] Define fallback behavior for missing faction fleets.
- [x] Define fallback behavior for missing production queues.
- [x] Define fallback behavior for missing mining cargo.
- [x] Define fallback behavior for missing territory state.
- [x] Define fallback behavior for missing reputation history.
- [x] Define fallback behavior for missing environment state.
- [x] Add migration fixtures for each supported public version.
- [x] Verify old saves load without exceptions.
- [x] Verify migrated saves can travel.
- [x] Verify migrated saves can enter tactical combat.
- [x] Verify migrated saves can purchase and queue ships.
- [x] Verify migrated saves can save again.
- [x] Verify a migrated save remains readable after a second reload.
- [x] Display a clear recovery message when migration repairs missing data.
- [x] Never delete a source save before a migrated checkpoint is verified.

## 0.3 Establish Release Telemetry

- [x] Add structured reasons for fleet creation.
- [x] Add structured reasons for fleet destruction.
- [x] Add structured reasons for fleet disappearance.
- [x] Add structured reasons for ownership changes.
- [x] Add structured reasons for production starts and stops.
- [x] Add structured reasons for mining departures and returns.
- [x] Add structured reasons for mission success and failure.
- [x] Add structured reasons for strike denial.
- [x] Add structured reasons for save recovery.
- [x] Ensure telemetry excludes secrets and unnecessary personal data.

### Phase 0 Exit Criteria

- [x] All baseline commands pass.
- [x] At least one older save fixture migrates through the complete campaign loop.
- [x] New 1.0 systems have an explicit save and telemetry contract.

# Phase 1 - Release Safety And Player Clarity

## 1.1 Mission Briefing Contract

- [x] Define one authoritative tactical-entry briefing model.
- [x] Include the primary objective.
- [x] Include the exact success condition.
- [x] Include the exact failure condition.
- [x] Include protected assets.
- [x] Include required kills, captures, rescues, or survival quota.
- [x] Include the mission timer when present.
- [x] Include optional objectives.
- [x] Include optional-objective rewards.
- [x] Include known enemy strength.
- [x] Include uncertainty when intelligence is incomplete.
- [x] Include one short recommended first action.
- [x] Render the briefing before normal combat begins.
- [x] Keep the briefing readable without pausing indefinitely.
- [x] Allow the player to reopen the briefing.
- [x] Keep the current objective visible in the tactical HUD.
- [x] Update progress immediately when the relevant state changes.
- [x] Explain why progress is blocked.
- [x] Explain mission failure at the moment it occurs.
- [x] Explain mission success at the moment it occurs.
- [x] Add briefing coverage for every authored campaign sector.
- [x] Add briefing coverage for every live generated mission family.
- [x] Add a regression test that no live mission has a blank success condition.
- [x] Add a regression test that no live mission has a blank failure condition.
- [x] Add a regression test for protected-asset naming.
- [x] Add a regression test for timer and quota display.

## 1.2 Strike Cost And Replenishment Clarity

- [x] Inventory torpedo, sortie, and atomic strike resources.
- [x] Show current strike inventory before launch.
- [x] Show ammunition cost before launch.
- [x] Show fuel cost before launch.
- [x] Show supply cost before launch.
- [x] Show charge or cooldown cost before launch.
- [x] Show required intelligence quality.
- [x] Show estimated effect.
- [x] Show retaliation or detection risk.
- [x] Show the exact reason a strike is unavailable.
- [x] Add an explicit strike-replenishment explanation.
- [x] Identify hubs that can rearm strikes.
- [x] Identify salvage or mission rewards that replenish strikes.
- [x] Identify production paths that replenish strikes.
- [x] Distinguish reusable carrier capacity from expendable strike stores.
- [x] Confirm costs are deducted exactly once.
- [x] Confirm canceled strikes do not consume resources.
- [x] Confirm failed target validation does not consume resources.
- [x] Confirm save/load preserves inventory and cooldowns.
- [x] Add positive and negative strike purchase/rearm tests.

## 1.3 Strike-Origin Correctness

- [x] Reproduce the reported strike-origin issue.
- [x] Confirm tactical strikes originate from the launching force or valid entry
  vector.
- [x] Prevent strikes from spawning directly on the target.
- [x] Prevent friendly command kills caused by invalid strike origins.
- [x] Keep enemy strategic strikes disabled until origin correctness is proven.
- [x] Add launch-origin assertions for every strike type.
- [x] Add friendly-fire regression coverage.
- [x] Add off-screen launcher regression coverage.
- [x] Add save/load coverage for pending strike state.

## 1.4 Lost Contacts

- [x] Define when a contact is live.
- [x] Define when a contact is stale.
- [x] Define when a stale contact becomes invalid.
- [x] Remove invalid lost-contact icons from the normal HUD.
- [x] Keep historical contact records in an archive or log where useful.
- [x] Never draw a live movement vector for an invalid contact.
- [x] Never allow strike selection against an invalid contact.
- [x] Never allow navigation to silently target an invalid contact.
- [x] Show â€œlast knownâ€ only while the estimate remains actionable.
- [x] Add expiry tests at each intelligence level.
- [x] Add lost-contact save/load tests.

## 1.5 Overworld Selection And Navigation

- [x] Audit click targets for fleets.
- [x] Audit click targets for facilities.
- [x] Audit click targets for missions.
- [x] Audit click targets for mining areas.
- [x] Audit click targets for overlapping markers.
- [x] Increase destination hit areas without making nearby objects ambiguous.
- [x] Prefer the visually topmost marker when hit areas overlap.
- [x] Add selection cycling for truly overlapping contacts if required.
- [x] Clearly distinguish selection from course plotting.
- [x] Show the selected destination before confirming travel.
- [x] Allow course cancellation.
- [x] Add tests for edge-of-marker clicks.
- [x] Add tests for dense hub clusters.
- [x] Add tests for fleet-near-mission selection.

## 1.6 Terminology Cleanup

- [x] Replace ambiguous `Readiness` with context-specific labels.
- [x] Use `Combat Condition` for hull/fleet fighting condition.
- [x] Use `Crew Readiness` for crew state.
- [x] Use `Strike Availability` for strike capacity.
- [x] Use `Production Progress` for construction.
- [x] Replace generic `Stores` with named resources.
- [x] Replace `Route Tempo` with `Travel Speed`.
- [x] Show ETA separately from travel speed.
- [x] Use `Green Reputation`.
- [x] Use `Yellow Reputation`.
- [x] Reserve `Favor` for a real spendable value only.
- [x] Reserve `Leverage` for a distinct mechanic only.
- [x] Replace generic `Scar` with battle history, persistent damage, or aftermath.
- [x] Qualify every use of `Sweep`.
- [x] Use `Sensor Sweep` for contact detection.
- [x] Use `Recon Sweep` for target-quality intelligence.
- [x] Use `Security Sweep` for hostile clearing.
- [x] Rename campaign `Safe Exit` presentation to `Withdraw To Strategic Map`
  without changing its established behavior.
- [x] Replace generic `Pressure` with the actual source.
- [x] Keep `Fleet Ore` and `Yard Ore`.
- [x] Keep the explanatory line for Fleet Ore and Yard Ore.
- [x] Search all player-facing text for unexplained internal terminology.
- [x] Add copy assertions for critical campaign screens.

## 1.7 Reputation And Fleet Health Visibility

- [x] Make Green Reputation visible from the main campaign command interface.
- [x] Make Yellow Reputation visible from the main campaign command interface.
- [x] Show the next reputation threshold.
- [x] Show currently unlocked benefits.
- [x] Show recent reputation changes and reasons.
- [x] Show allied hull health in the fleet view.
- [x] Show allied armor condition where applicable.
- [x] Show allied shield condition where applicable.
- [x] Show persistent damage and repair need.
- [x] Show which ships are unavailable, under repair, or under construction.

## 1.8 Audio And Visual Correctness

- [x] Reproduce the missing missile-launch SFX.
- [x] Verify event-to-file mapping.
- [x] Verify volume and channel routing.
- [x] Verify rapid launches do not suppress every cue.
- [x] Verify captions or visual feedback exist where required.
- [x] Identify Yellow hull sprites with the forward-right triangular notch.
- [x] Determine whether the notch is transparent padding, source damage, or crop.
- [x] Repair affected source assets.
- [x] Verify repaired sprites at multiple scales.
- [x] Audit deep-space encounter background selection.
- [x] Prevent inappropriate planet backgrounds in ordinary open space.
- [x] Preserve planet backgrounds at authored orbital locations.
- [x] Add screenshot baselines for corrected scenes.

### Phase 1 Exit Criteria

- [x] Every mission explains success and failure before combat.
- [x] Strike costs and replenishment are discoverable.
- [x] Lost contacts cannot mislead the player.
- [x] Required navigation targets are easy to select.
- [x] Missile launch audio works.
- [x] No known P0 presentation or clarity defect remains.

# Phase 2 - Faction Inventories And Fleet Population

## 2.1 Audit Existing Order Of Battle

- [x] Export Blue starting ships by role.
- [x] Export Red starting ships by role.
- [x] Export Green starting ships by role.
- [x] Export Yellow starting ships by role.
- [x] Export ships assigned to active fleets.
- [x] Export ships assigned to garrisons.
- [x] Export ships assigned to convoys.
- [x] Export ships assigned to mining groups.
- [x] Export ships assigned to reserves.
- [x] Export ships under construction.
- [x] Export unassigned inventory records.
- [x] Flag duplicate ship IDs.
- [x] Flag duplicate persistent ship names.
- [x] Flag ships without a faction.
- [x] Flag ships without an owner force, base, or queue.
- [x] Flag forces without origin or mission.
- [x] Add a developer order-of-battle report.

## 2.2 Blue Starting Fleet

- [x] Preserve the mothership.
- [x] Preserve the small picket escort.
- [x] Add one starting miner.
- [x] Ensure the miner belongs to the persistent Blue fleet.
- [x] Ensure the miner survives checkpoint save/load.
- [x] Ensure the miner appears in the fleet interface.
- [x] Ensure the miner can receive strategic orders.
- [x] Ensure the starting fleet remains vulnerable.
- [x] Verify the starting fleet cannot immediately overpower major Red forces.

## 2.3 Red Starting Inventory

- [x] Define a substantially larger finite Red inventory.
- [x] Include numerous pickets and patrol craft.
- [x] Include meaningful frigate and destroyer reserves.
- [x] Include regularly encountered cruisers.
- [x] Include capital ships.
- [x] Include rare titans.
- [x] Include mining ships.
- [x] Include ore transports.
- [x] Include repair and support ships.
- [x] Include garrison fleets.
- [x] Include hunter-killer groups.
- [x] Include infrastructure-defense groups.
- [x] Include rear-area reserves.
- [x] Distribute forces across real Red-controlled locations.
- [x] Prevent all Red capital ships from clustering in one early region.
- [x] Scale accessible threat by campaign stage without creating free ships.

## 2.4 Green Starting Inventory

- [x] Define a strong finite Green inventory.
- [x] Include active defense fleets.
- [x] Include offensive task forces.
- [x] Include patrol groups.
- [x] Include mining groups.
- [x] Include ore transports.
- [x] Include repair and support ships.
- [x] Include capital ships.
- [x] Include rare special or titan assets if supported by lore.
- [x] Assign Green fleets to visible strategic missions.
- [x] Ensure Green can win and lose without the player.
- [x] Ensure Green has enough depth to feel like a war partner.

## 2.5 Yellow Starting Inventory

- [x] Define a smaller but strong finite Yellow inventory.
- [x] Include trade fleets.
- [x] Include mining fleets.
- [x] Include ore transports.
- [x] Include armed escorts.
- [x] Include faction-distinct capital ships.
- [x] Include rare high-value special ships.
- [x] Assign some Yellow forces to Red-coerced operations.
- [x] Assign some Yellow forces to transactional neutral operations.
- [x] Allow liberated or purchased Yellow ships to become allied.
- [x] Preserve producing-faction hull identity after Blue purchase.

## 2.6 Fleet Composition Rules

- [x] Define small patrol templates.
- [x] Define mining deployment templates.
- [x] Define trade convoy templates.
- [x] Define escort templates.
- [x] Define infrastructure-defense templates.
- [x] Define hunter-killer templates.
- [x] Define capital task-force templates.
- [x] Define titan task-force templates.
- [x] Define mixed large-fleet templates.
- [x] Require support ships for long-range major fleets where appropriate.
- [x] Prevent all large fleets from becoming identical.
- [x] Apply faction doctrine to composition.
- [x] Apply available inventory to composition.
- [x] Apply mission requirements to composition.
- [x] Apply local threat to composition.
- [x] Never add a role that the faction inventory cannot supply.

## 2.7 Capital Ship Presence

- [x] Define minimum expected capital-contact frequency by campaign phase.
- [x] Add capital ships to real Red offensive forces.
- [x] Add capital ships to Green counteroffensives.
- [x] Add Yellow capitals to high-value coerced or allied fleets.
- [x] Create rewards appropriate to capital-ship risk.
- [x] Add reputation effects for destroying or saving major ships.
- [x] Add strategic consequences for capital losses.
- [x] Ensure capital names persist into tactical combat.
- [x] Ensure damaged capitals can retreat and remain damaged.
- [x] Ensure crippled capitals can become recovery targets.

## 2.8 Titans

- [x] Define titan inventory by faction.
- [x] Define titan construction restrictions.
- [x] Define titan deployment conditions.
- [x] Define titan escort requirements.
- [x] Define titan repair requirements.
- [x] Define titan loss consequences.
- [x] Add rare titan-bearing task forces.
- [x] Allow exceptional Red fleets to contain several titans.
- [x] Add high-intelligence warning for titan presence.
- [x] Add titan-hunt missions from real persistent contacts.
- [x] Add major rewards and reputation for successful titan hunts.
- [x] Preserve titan damage and names across retreat and re-encounter.
- [x] Prevent routine early-game titan encounters.

## 2.9 Tactical Conversion

- [x] Convert every persistent manifest ship into the tactical encounter.
- [x] Preserve role.
- [x] Preserve faction.
- [x] Preserve name.
- [x] Preserve hull condition.
- [x] Preserve armor condition.
- [x] Preserve crew/readiness state.
- [x] Preserve ammunition and logistics effects where applicable.
- [x] Preserve retreat intent.
- [x] Return survivors to the strategic force.
- [x] Remove destroyed ships from finite inventory.
- [x] Record captured or recovered ships correctly.
- [x] Prevent duplicate strategic and tactical instances.

## 2.10 Contact Density

- [x] Define a target for ordinary visible traffic.
- [x] Ensure the player usually has at least one nearby contact.
- [x] Include friendly patrols.
- [x] Include neutral traders.
- [x] Include mining deployments.
- [x] Include logistics convoys.
- [x] Include hostile scouts.
- [x] Include occasional major task forces.
- [x] Avoid turning every contact into an interruption.
- [x] Distinguish visible traffic from mandatory encounters.
- [x] Measure contacts per travel leg.
- [x] Compare the owner's desired 5-8 meaningful events with playtest fatigue.
- [x] Tune by route length and region activity.

## 2.11 Task-Force Inspection

- [x] Preserve current intel gating.
- [x] Show faction at early contact.
- [x] Show estimated ship count when identified.
- [x] Show ship classes at high intelligence.
- [x] Show persistent names at high intelligence.
- [x] Show damage and readiness at high intelligence.
- [x] Show cargo and logistics at high intelligence.
- [x] Show origin and destination when identified.
- [x] Show mission and intent at high intelligence.
- [x] Show capital and titan warnings prominently.
- [x] Show formation breakdown.
- [x] Show escort and support composition.
- [x] Show confidence or age for every estimate.
- [x] Do not expose exact hidden values at low intelligence.

### Phase 2 Exit Criteria

- [x] Green and Yellow are visibly active during ordinary play.
- [x] Capital ships appear naturally in repeatable campaign runs.
- [x] Titans are rare but discoverable campaign-defining contacts.
- [x] Every major fleet has inventory provenance.
- [x] Tactical encounters preserve persistent manifests exactly.

# Phase 3 - Mining, Logistics, Shipyards, And Construction

## 3.1 Strategic Mining Model

- [x] Replace repetitive player wait-to-mine interaction with strategic orders.
- [x] Allow the player to assign a mining ship or group to a mining area.
- [x] Show expected ore yield.
- [x] Show expected mining duration.
- [x] Show route risk.
- [x] Show required cargo capacity.
- [x] Show escort recommendation.
- [x] Allow recall before completion.
- [x] Allow reassignment.
- [x] Apply depletion where intended.
- [x] Apply hazards where intended.
- [x] Apply enemy interruption.
- [x] Save active mining assignments.

## 3.2 Faction Mining Forces

- [x] Add Red mining ship variants.
- [x] Add Yellow mining ship variants.
- [x] Confirm Green miners remain active.
- [x] Give mining forces a real origin.
- [x] Give mining forces a target mining area.
- [x] Give mining forces a cargo capacity.
- [x] Give mining forces an escort policy.
- [x] Give mining forces a return destination.
- [x] Give mining forces a retreat policy.
- [x] Give mining forces a replacement policy.
- [x] Make mining deployments visible as contacts.
- [x] Allow the player to protect allied miners.
- [x] Allow the player to raid hostile miners.
- [x] Allow ore capture from defeated mining forces.

## 3.3 Ore Cargo And Hauling

- [x] Track ore cargo on meaningful mining and transport forces.
- [x] Increase cargo as mining progresses.
- [x] Cap cargo by capacity.
- [x] Transfer ore only at a valid faction logistics destination.
- [x] Prevent double transfer.
- [x] Prevent cargo transfer after force destruction.
- [x] Drop or award recoverable ore after interception.
- [x] Record stolen ore in campaign memory.
- [x] Record lost ore in faction logistics reports.
- [x] Show cargo estimates only at appropriate intel.
- [x] Save cargo and transfer progress.

## 3.4 Faction Economy Pools

- [x] Define faction ore pools.
- [x] Define faction credit or industrial-capacity pools.
- [x] Define faction supply pools where required.
- [x] Credit delivered ore to the correct faction.
- [x] Debit construction costs from the correct faction.
- [x] Debit repairs from the correct faction.
- [x] Debit replacement logistics from the correct faction.
- [x] Prevent negative inventories.
- [x] Pause construction when resources are insufficient.
- [x] Resume construction when resources arrive.
- [x] Expose understandable faction production summaries.

## 3.5 Shipyard Local State

- [x] Give each shipyard a local identity.
- [x] Give each shipyard an owner.
- [x] Give each shipyard local production lanes.
- [x] Give each shipyard a hull catalog.
- [x] Give each shipyard damage state.
- [x] Give each shipyard blockade state.
- [x] Give each shipyard capture state.
- [x] Give each shipyard queue capacity.
- [x] Give each shipyard a completion location.
- [x] Disable production when destroyed.
- [x] Reduce production when damaged.
- [x] Restrict production when blockaded.
- [x] Transfer or cancel queues according to capture rules.
- [x] Save every queue and lane.

## 3.6 Class-Separated Production Lanes

- [x] Add escort production lane.
- [x] Add frigate/destroyer production lane.
- [x] Add cruiser production lane.
- [x] Add capital production lane.
- [x] Add titan/special production lane.
- [x] Use 5-second base escort construction time.
- [x] Use 10-second base frigate/destroyer construction time.
- [x] Use 15-second base cruiser construction time.
- [x] Use 20-second base capital construction time.
- [x] Use 25-second base titan/special construction time.
- [x] Apply damage and blockade modifiers.
- [x] Apply faction or shipyard quality modifiers only when visible.
- [x] Prevent one lane from blocking unrelated classes.
- [x] Show every active lane.
- [x] Show queue order.
- [x] Show remaining time.
- [x] Show paused reason.

## 3.7 Player Ship Orders

- [x] Replace immediate delivery with queue placement.
- [x] Show producing faction.
- [x] Show producing shipyard.
- [x] Show hull faction identity.
- [x] Show credit cost.
- [x] Show Fleet Ore cost.
- [x] Show required reputation.
- [x] Show expected completion time.
- [x] Confirm purchase before charging resources.
- [x] Deduct resources exactly once.
- [x] Allow multiple orders.
- [x] Queue orders in the correct class lane.
- [x] Notify the player when construction completes.
- [x] Add completed hull to persistent inventory.
- [x] Allow collection or deployment according to yard rules.
- [x] Handle shipyard capture during a player order.
- [x] Handle shipyard destruction during a player order.
- [x] Define refund or loss rules.

## 3.8 AI Construction Decisions

- [x] Make AI identify inventory shortages.
- [x] Make AI prioritize miners when ore income collapses.
- [x] Make AI prioritize escorts when logistics losses rise.
- [x] Make AI prioritize patrols when territory is exposed.
- [x] Make AI prioritize capitals for major offensives.
- [x] Make AI reserve titan construction for strategic conditions.
- [x] Make AI respect available hull catalogs.
- [x] Make AI respect ore.
- [x] Make AI respect credits or industrial capacity.
- [x] Make AI respect local shipyard lanes.
- [x] Make AI respect queue time.
- [x] Make AI cancel or redirect invalid orders.
- [x] Make AI avoid endless cheap-unit queue spam.
- [x] Record AI construction reasons.

## 3.9 Transport Ships And Field Repair

- [x] Preserve increased player ore capacity from transports.
- [x] Allow transports to assist nearby internal repairs slowly.
- [x] Consume supplies during assisted repair.
- [x] Prevent transport repair from restoring armor.
- [x] Prevent transport repair from restoring shields.
- [x] Reduce fire and terminal internal ailments.
- [x] Define assistance radius.
- [x] Define assistance rate.
- [x] Define supply cost per repair.
- [x] Prevent stacking from becoming unlimited.
- [x] Show active repair support.
- [x] Show supply drain.
- [x] Stop repair support when supplies are exhausted.
- [x] Preserve unrepaired battle damage after combat.
- [x] Require a safe hub for full restoration.

## 3.10 Mining And Production Missions

- [x] Add mining convoy escort from a real mining force.
- [x] Add mining convoy raid from a real hostile force.
- [x] Add ore-hauler interception.
- [x] Add shipyard queue defense.
- [x] Add crippled yard resupply.
- [x] Add emergency miner rescue.
- [x] Add stolen-ore recovery.
- [x] Add capital-completion interdiction.
- [x] Make mission outcomes alter real cargo or queue state.
- [x] Prevent duplicate rewards outside the real economy.

### Phase 3 Exit Criteria

- [x] Ore can be traced from mining site to delivered cargo.
- [x] Delivered ore enables real construction.
- [x] Destroying miners or haulers delays production.
- [x] Player purchases enter visible class-separated queues.
- [x] Transport repair consumes supplies and cannot restore armor or shields.

# Phase 4 - Dynamic Territory And Autonomous War

## 4.1 Territory Model

- [x] Define authoritative ownership for every strategic location.
- [x] Define contest state.
- [x] Define occupation state.
- [x] Define capture progress.
- [x] Define garrison requirement.
- [x] Define supply connection requirement.
- [x] Define what services change with ownership.
- [x] Define what visuals change with ownership.
- [x] Define what missions change with ownership.
- [x] Define what routes change with ownership.
- [x] Save ownership and contest history.

## 4.2 AI Strategic Objectives

- [x] Let Red attack infrastructure.
- [x] Let Red attack logistics.
- [x] Let Red hunt the player.
- [x] Let Red pursue wider-war territory objectives.
- [x] Let Green defend important locations.
- [x] Let Green launch counteroffensives.
- [x] Let Green escort logistics.
- [x] Let Green capture Red or contested territory.
- [x] Let Yellow protect trade.
- [x] Let Yellow support Red when coerced.
- [x] Let Yellow resist Red under favorable conditions.
- [x] Let Yellow support Blue or Green when allied.
- [x] Select objectives from actual theater conditions.
- [x] Prevent impossible or unreachable objectives.

## 4.3 AI-Versus-AI Battles

- [x] Detect opposing forces entering battle range.
- [x] Create one authoritative battle record.
- [x] Allow off-screen resolution.
- [x] Allow player intervention before resolution.
- [x] Allow player arrival during an active battle.
- [x] Preserve participating ship manifests.
- [x] Apply ammunition and condition.
- [x] Apply retreat and pursuit.
- [x] Apply losses to finite inventories.
- [x] Apply cargo loss.
- [x] Apply territory outcome.
- [x] Apply reputation and memory where relevant.
- [x] Create battle aftermath.
- [x] Prevent duplicate tactical and auto-resolve outcomes.

## 4.4 Battle Warning And Join Flow

- [x] Identify major battles worth announcing.
- [x] Warn the player approximately 30 seconds before engagement.
- [x] Show participants.
- [x] Show location.
- [x] Show estimated strength.
- [x] Show current distance and ETA.
- [x] Add `Follow Fleet`.
- [x] Add `Join Battle`.
- [x] Add `Ignore`.
- [x] Add `Offer Support` where applicable.
- [x] Let the player follow Green fleets.
- [x] Let the player follow allied Yellow fleets.
- [x] Keep the followed fleet selected.
- [x] Handle destination changes.
- [x] Handle followed-fleet destruction or retreat.

## 4.5 Ownership Changes

- [x] Change ownership after a valid capture outcome.
- [x] Require surviving occupation or garrison strength where appropriate.
- [x] Update map colors and symbols.
- [x] Update service availability.
- [x] Update shipyard access.
- [x] Update mining access.
- [x] Update mission generation.
- [x] Update local patrol generation.
- [x] Update faction economy contribution.
- [x] Update reputation consequences.
- [x] Create a visible campaign bulletin.
- [x] Record the reason for ownership change.
- [x] Preserve change through save/load.

## 4.6 Yellow Alignment

- [x] Define coerced-hostile Yellow state.
- [x] Define transactional-neutral Yellow state.
- [x] Define liberated-friendly Yellow state.
- [x] Make Yellow hostile when operating directly with Red in a mission.
- [x] Make independent Yellow transactional by default.
- [x] Make Blue- or Green-purchased Yellow ships allied.
- [x] Allow reputation and aid to shift Yellow alignment.
- [x] Allow Red pressure to shift Yellow toward hostility.
- [x] Show current Yellow alignment.
- [x] Explain why a Yellow force is hostile or friendly.
- [x] Persist alignment and reasons.

## 4.7 Anti-Stalemate

- [x] Detect prolonged territorial stalemate.
- [x] Avoid generating free fleets to break it.
- [x] Generate player-facing support opportunities.
- [x] Offer ore, credits, intelligence, or ships as intervention.
- [x] Offer a major offensive mission.
- [x] Offer logistics interdiction.
- [x] Offer shipyard defense or sabotage.
- [x] Let ignored stalemates remain unresolved.
- [x] Let player intervention materially alter the balance.

## 4.8 Divergent Campaigns

- [x] Keep starting seeds reproducible.
- [x] Allow faction decisions to diverge after start.
- [x] Record major divergence causes.
- [x] Ensure save/load does not reset directors.
- [x] Add deterministic tests for identical action sequences.
- [x] Add tests proving different player actions create different outcomes.
- [x] Add long-run simulations for territory diversity.

### Phase 4 Exit Criteria

- [x] Green gains and loses territory without player scripting.
- [x] Red attacks logistics and infrastructure.
- [x] Yellow alignment changes behavior.
- [x] Major battles can occur and resolve without the player.
- [x] The player can follow and join allied battles.
- [x] Territory state visibly responds to battle outcomes.

# Phase 5 - Difficulty, Attrition, And Late Campaign

## 5.1 Standard Difficulty Target

- [x] Tune Standard so most players can win after learning from one defeat.
- [x] Target one major loss, failed mission, or forced withdrawal per roughly five
  battles for a capable player.
- [x] Preserve recovery after reaching a friendly or captured hub.
- [x] Avoid irreversible collapse from one ordinary mistake.
- [x] Allow severe repeated mistakes to produce a losing campaign.
- [x] Record loss frequency in playtest reports.
- [x] Record retreat frequency.
- [x] Record resource emergency frequency.

## 5.2 Tactical Opposition

- [x] Increase enemy fleet size before increasing raw ship durability.
- [x] Increase enemy role diversity.
- [x] Increase capital presence.
- [x] Use support ships.
- [x] Use coordinated escorts.
- [x] Use reserves.
- [x] Use retreat and regroup behavior.
- [x] Use faction doctrine.
- [x] Preserve current time-to-kill.
- [x] Prevent difficulty from becoming simple health inflation.

## 5.3 Strategic Travel Pressure

- [x] Make route fuel costs meaningful.
- [x] Make route supply costs meaningful.
- [x] Make route ammunition costs meaningful.
- [x] Show costs before travel.
- [x] Show recovery options.
- [x] Avoid unavoidable unwarned failure.
- [x] Scale event density by route.
- [x] Scale danger by territory and contact activity.
- [x] Preserve quiet routes where appropriate.
- [x] Test long travel with large fleets.

## 5.4 Repair And Persistent Damage

- [x] Preserve meaningful hull and internal damage after combat.
- [x] Prevent free full recovery between battles.
- [x] Use supplies for field repair.
- [x] Require hubs for full armor restoration.
- [x] Require hubs for full system restoration when appropriate.
- [x] Show repair duration.
- [x] Show repair cost.
- [x] Show unavailable ships.
- [x] Allow damaged fleets to retreat to safety.
- [x] Add recovery missions for crippled capitals.
- [x] Add save/load tests for persistent damage.

## 5.5 Credits And Trade

- [x] Keep credits as the main limiting resource.
- [x] Preserve ore as a valuable trade good.
- [x] Make Green trade frequently useful.
- [x] Make Yellow trade frequently useful.
- [x] Avoid making trade mandatory every single route.
- [x] Show buy and sell prices.
- [x] Show reputation effects.
- [x] Show shortages and opportunities.
- [x] Prevent unlimited price exploits.
- [x] Prevent duplicate transaction rewards.
- [x] Add economy soak tests.

## 5.6 Strikes

- [x] Keep strikes in 1.0.
- [x] Preserve current reduced quantity.
- [x] Add real and visible costs.
- [x] Add discoverable replenishment.
- [x] Keep target-quality intelligence requirement.
- [x] Keep strikes powerful enough to matter.
- [x] Prevent strikes from becoming a free guaranteed punch.
- [x] Do not enable enemy strikes until launch-origin safety is proven.
- [x] Re-test strike-heavy routes.
- [x] Record strike usage and recovery frequency.

## 5.7 Fleet Doctrine

- [x] Audit every doctrine and posture.
- [x] Reproduce why `LINE` feels useless.
- [x] Define a distinct purpose for `LINE`.
- [x] Improve its positioning, survivability, fire concentration, or command
  benefit.
- [x] Ensure no doctrine dominates every situation.
- [x] Explain doctrine effects in plain language.
- [x] Add tactical comparisons using identical fleets and seeds.
- [x] Preserve player workload at the current acceptable level.

## 5.8 Retreat

- [x] Preserve the 7.5-second withdrawal wind-up.
- [x] Require the player to avoid interruption during withdrawal.
- [x] Clearly show withdrawal progress.
- [x] Clearly show interruption reason.
- [x] Define what allied ships escape.
- [x] Define what damaged or distant ships risk losing.
- [x] Return to the strategic map on success.
- [x] Apply pursuit or aftermath consequences.
- [x] Remove surrender as a fake player option unless it has a real strategic
  outcome.

## 5.9 Iron Command

- [x] Increase enemy armor on Iron Command.
- [x] Shorten the delay before enemy shield regeneration begins.
- [x] Keep those bonuses confined to the harder mode.
- [x] Show difficulty modifiers before campaign start.
- [x] Avoid hidden rule changes unrelated to difficulty.
- [x] Add mode-specific regression tests.
- [x] Run full Iron Command defeat-path acceptance.

## 5.10 Late Campaign And Ending

- [x] Increase late-game Red fleet quality.
- [x] Increase late-game capital concentration.
- [x] Increase strategic coordination.
- [x] Avoid slowing the late game through empty travel.
- [x] Add meaningful late-game Green operations.
- [x] Add meaningful late-game Yellow alignment consequences.
- [x] Include surviving Red remnants in final readiness or ending evaluation.
- [x] Prevent a clean victory claim while major Red forces remain unaddressed
  without acknowledging them.
- [x] Keep the final victory reachable without debug actions.
- [x] Preserve an earned and conclusive ending.

### Phase 5 Exit Criteria

- [x] Late campaign is more demanding than mid campaign.
- [x] Player fleet growth remains satisfying.
- [x] Opposition scale keeps pace with player power.
- [x] Damage and logistics matter across battles.
- [x] Standard and Iron Command feel meaningfully different.

# Phase 6 - Reputation, Aid, And Minimum Politics

## 6.1 Reputation Model

- [x] Use clear Green Reputation.
- [x] Use clear Yellow Reputation.
- [x] Record gains and losses with reasons.
- [x] Show current value.
- [x] Show current tier.
- [x] Show next threshold.
- [x] Show unlocked benefits.
- [x] Save reputation history.
- [x] Prevent accidental double awards.

## 6.2 Aid Transfers

- [x] Allow sending ore to Green.
- [x] Allow sending credits to Green.
- [x] Allow sending intelligence to Green.
- [x] Allow transferring ships to Green.
- [x] Allow sending ore to Yellow.
- [x] Allow sending credits to Yellow.
- [x] Allow sending intelligence to Yellow.
- [x] Allow transferring ships to Yellow.
- [x] Show transfer cost.
- [x] Show expected reputation gain.
- [x] Show expected strategic effect.
- [x] Confirm before transferring a persistent ship.
- [x] Remove transferred ships from Blue inventory.
- [x] Add transferred ships to the recipient inventory.
- [x] Preserve original hull faction identity.

## 6.3 Material Consequences

- [x] Let Green aid improve Green fleet replacement or operations.
- [x] Let Yellow aid improve Yellow resistance or alliance.
- [x] Let intelligence reveal missions or contacts.
- [x] Let ship transfers create visible recipient fleets.
- [x] Let reputation improve trade.
- [x] Let reputation unlock support.
- [x] Let reputation alter fleet behavior.
- [x] Let reputation alter final-battle readiness.
- [x] Let reputation alter ending summaries.
- [x] Explain each consequence with â€œbecause you did Xâ€ feedback.

## 6.4 Yellow Liberation

- [x] Promote Yellow liberation missions.
- [x] Tie missions to real Red-controlled Yellow assets.
- [x] Allow Yellow forces to change alignment after liberation.
- [x] Add later allied Yellow operations.
- [x] Add Yellow capital or special-ship support at high reputation.
- [x] Add consequences for attacking transactional Yellow forces.
- [x] Add consequences for ignoring coerced Yellow forces.
- [x] Save liberation history.

## 6.5 Campaign Memory

- [x] Record trade totals.
- [x] Record reputation changes.
- [x] Record faction aid.
- [x] Record ship kills.
- [x] Record capital kills.
- [x] Record titan kills.
- [x] Record ore mined.
- [x] Record ore stolen.
- [x] Record miners saved or destroyed.
- [x] Record allied battles joined.
- [x] Record territory helped or abandoned.
- [x] Reference important records in reports.
- [x] Reference important records in faction responses.
- [x] Reference important records in the ending.

## 6.6 Character Presence

- [x] Give important captains distinct names.
- [x] Give important captains concise personality tags.
- [x] Use short written reactions.
- [x] Avoid long dialogue scenes.
- [x] Use limited high-quality spoken callouts only.
- [x] Keep callouts optional through quiet mode.
- [x] Caption spoken callouts.
- [x] Avoid placeholder voices in release builds.

### Phase 6 Exit Criteria

- [x] Aid to Green and Yellow changes later play.
- [x] At least two faction strategies create different encounters.
- [x] Reputation is easy to find and understand.
- [x] Yellow can become a meaningful ally.
- [x] The ending references important player behavior.

# Phase 7 - Tactical Environments And Presentation

## 7.1 Environment Framework

- [x] Define environment identity separately from background art.
- [x] Define tactical modifiers.
- [x] Define sensor modifiers.
- [x] Define movement modifiers.
- [x] Define weapon modifiers where appropriate.
- [x] Define hazard behavior.
- [x] Define AI awareness of environment effects.
- [x] Show environment rules before combat.
- [x] Preserve environment state through save/load if persistent.
- [x] Avoid effects that invalidate ship roles without warning.

## 7.2 Required Live Environments

- [x] Implement sensor-shadow environment.
- [x] Implement quarantine or logistics-restriction environment.
- [x] Implement a third materially distinct environment.
- [x] Consider debris field.
- [x] Consider ion storm.
- [x] Consider mine-laced corridor.
- [x] Consider station-defense perimeter.
- [x] Consider low-orbit gravity or drag only if readable.
- [x] Ensure at least three environments alter tactical decisions.

## 7.3 Asteroids And Destructible Space

- [x] Decide whether cannon fire should destroy asteroids.
- [x] Increase durability where needed.
- [x] Preserve navigational cover long enough to matter.
- [x] Allow destruction when tactically intentional.
- [x] Prevent debris from causing severe performance loss.
- [x] Teach the player how asteroid cover works.

## 7.4 Sensor Shadows

- [x] Reduce detection through authored shadow regions.
- [x] Affect both player and AI.
- [x] Show shadow boundaries or understandable cues.
- [x] Allow tactical ambush without invisible rules.
- [x] Connect strategic and tactical sensor behavior.
- [x] Add tests for symmetric detection effects.

## 7.5 Quarantine Warnings

- [x] Mark quarantined routes or locations.
- [x] Explain logistics restrictions.
- [x] Explain entry consequences.
- [x] Apply supply, repair, trade, or rescue effects intentionally.
- [x] Let reputation or missions alter quarantine state.
- [x] Avoid blocking progression without recovery options.

## 7.6 Background Selection

- [x] Use empty space for ordinary deep-space encounters.
- [x] Use planet backgrounds only near appropriate locations.
- [x] Use faction or operational backgrounds where authored.
- [x] Keep empty space visually legible.
- [x] Verify all major mission families.
- [x] Add screenshot coverage.

## 7.7 Audio Direction

- [x] Use ambient silence as the default.
- [x] Reserve music for detection.
- [x] Reserve music for pursuit.
- [x] Reserve music for major battles.
- [x] Reserve music for major campaign events.
- [x] Reduce warp SFX repetition.
- [x] Add warp variants or cooldown rules.
- [x] Preserve missile-launch readability.
- [x] Check 30-minute audio fatigue.
- [x] Verify quiet mode.
- [x] Verify captions.

## 7.8 Visual Presentation

- [x] Verify industrial naval science-fiction identity.
- [x] Preserve approved hull skins.
- [x] Preserve approved turret skins.
- [x] Preserve approved damage visuals.
- [x] Preserve shields and trails.
- [x] Repair identified Yellow hull defects.
- [x] Audit map icons for clarity.
- [x] Audit high-density text panels.
- [x] Avoid planet backgrounds in open space.
- [x] Keep allied, neutral, hostile, hub, and operational areas distinct.

### Phase 7 Exit Criteria

- [x] At least three environments materially change combat.
- [x] Open space and orbital locations use appropriate backgrounds.
- [x] Warp audio is not fatiguing.
- [x] Missile audio works.
- [x] No must-replace visual defect remains.

# Phase 8 - Architecture And Bloat Control

## 8.1 Authoritative Ownership

- [x] Document the authoritative campaign state for fleets.
- [x] Document the authoritative state for inventory.
- [x] Document the authoritative state for economy.
- [x] Document the authoritative state for territory.
- [x] Document the authoritative state for production.
- [x] Document the authoritative state for missions.
- [x] Remove parallel live claims from model-only systems.
- [x] Add invariants preventing duplicate ownership.
- [x] Add invariants preventing duplicate resource deduction.

## 8.2 CampaignSystem Reduction

- [x] Inventory cohesive feature regions in `CampaignSystem.java`.
- [x] Identify read-only presentation helpers.
- [x] Identify fleet inventory logic.
- [x] Identify mining and logistics logic.
- [x] Identify production logic.
- [x] Identify territory logic.
- [x] Identify mission briefing logic.
- [x] Extract only when ownership becomes clearer.
- [x] Preserve public compatibility wrappers where needed.
- [x] Keep each extraction covered by focused tests.
- [x] Avoid unrelated formatting churn.

## 8.3 Renderer Reduction

- [x] Inventory campaign rendering sections.
- [x] Separate data preparation from drawing.
- [x] Prevent rendering from mutating campaign state.
- [x] Extract repeated panel layout helpers.
- [x] Preserve screenshot baselines intentionally.
- [x] Keep UI layout responsive.
- [x] Add no-mutation tests for read paths.

## 8.4 Dead And Prototype Code

- [x] Search for unused model-only capability claims.
- [x] Search for unreachable controls.
- [x] Search for dead mission branches.
- [x] Search for stale terminology.
- [x] Search for obsolete placeholder comments.
- [x] Search for duplicate systems with competing ownership.
- [x] Remove code only when references and save impact are understood.
- [x] Keep deferred systems clearly marked.
- [x] Do not delete compatible saved fields casually.

## 8.5 Validation And Diagnostics

- [x] Add an order-of-battle validator.
- [x] Add a fleet-provenance validator.
- [x] Add an economy conservation validator.
- [x] Add a production-queue validator.
- [x] Add a territory ownership validator.
- [x] Add a mission-briefing completeness validator.
- [x] Add a contact validity validator.
- [x] Add a strike-origin validator.
- [x] Add a save migration validator.
- [x] Expose failures in developer diagnostics.

### Phase 8 Exit Criteria

- [x] No live system has two competing authoritative owners.
- [x] Render paths are read-only.
- [x] Major 1.0 systems have executable validators.
- [x] Bloat reduction does not destabilize saves or gameplay.

# Phase 9 - Performance And Maximum Battle Scale

## 9.1 Define Supported Battle Scale

- [x] Measure ordinary battle ship counts.
- [x] Measure major battle ship counts.
- [x] Measure titan battle ship counts.
- [x] Measure projectile counts.
- [x] Measure wreck counts.
- [x] Measure VFX counts.
- [x] Define the largest supported battle.
- [x] Keep the player-facing scale below unreadable chaos.
- [x] Avoid promising unlimited ships.

## 9.2 Performance Targets

- [x] Maintain 60 FPS target in ordinary tactical play.
- [x] Maintain 30 FPS hard floor in the largest supported battle.
- [x] Validate 1280x720.
- [x] Validate 1920x1080.
- [x] Validate integrated graphics target.
- [x] Validate recommended discrete graphics target.
- [x] Measure update time.
- [x] Measure render time.
- [x] Measure AI time.
- [x] Measure projectile time.
- [x] Measure campaign-map time.
- [x] Measure memory.
- [x] Measure garbage collection.

## 9.3 Stress Scenarios

- [x] Run 100 ships per side.
- [x] Run 160 ships per side.
- [x] Run capital-heavy battle.
- [x] Run titan-heavy battle.
- [x] Run missile-heavy battle.
- [x] Run wreck-heavy aftermath.
- [x] Run prolonged 30-minute tactical session.
- [x] Run repeated tactical entries.
- [x] Run long strategic campaign.
- [x] Run save/load during a large campaign state.

## 9.4 Optimization Rules

- [x] Profile before optimizing.
- [x] Preserve gameplay correctness.
- [x] Preserve internal damage.
- [x] Preserve persistent fleet identity.
- [x] Prefer spatial indexing and bounded work.
- [x] Avoid invisible reductions that alter battle outcomes.
- [x] Allow explicit visual-detail settings.
- [x] Keep Tactical FPS View optional.
- [x] Document measured gains.

### Phase 9 Exit Criteria

- [x] Largest supported battle remains at or above 30 FPS on verified minimum
  hardware.
- [x] Ordinary battles target 60 FPS.
- [x] No progressive memory degradation blocks a full session.

# Phase 10 - Accessibility And Input Acceptance

## 10.1 Keyboard Navigation

- [x] Navigate the main menu without a mouse.
- [x] Start a campaign without a mouse.
- [x] Navigate strategic command tabs.
- [x] Select fleets and locations.
- [x] Plot and cancel a course.
- [x] Open and use trade.
- [x] Open and use shipyards.
- [x] Queue a ship.
- [x] Review objectives.
- [x] Enter tactical combat.
- [x] Withdraw from tactical combat.
- [x] Save and load.
- [x] Recover from defeat.
- [x] Show visible keyboard focus.

## 10.2 Remapping

- [x] Open remapping UI.
- [x] Rebind every required action.
- [x] Detect conflicts.
- [x] Explain conflicts.
- [x] Restore defaults.
- [x] Persist mappings.
- [x] Recover from invalid mapping data.
- [x] Ensure no required action becomes permanently inaccessible.

## 10.3 Visual Accessibility

- [x] Verify high contrast.
- [x] Verify color-independent hostile/friendly/neutral symbols.
- [x] Verify selected state without color alone.
- [x] Verify warning state without color alone.
- [x] Verify text scaling.
- [x] Verify 1280x720 readability.
- [x] Verify 1920x1080 readability.
- [x] Verify long names.
- [x] Verify largest numeric values.
- [x] Verify mission briefing text.
- [x] Verify fleet health display.
- [x] Verify reputation display.

## 10.4 Audio Accessibility

- [x] Caption spoken callouts.
- [x] Caption critical radio information.
- [x] Keep captions readable over combat.
- [x] Verify quiet mode.
- [x] Verify reduced-noise mode.
- [x] Ensure no required information is audio-only.
- [x] Verify volume controls.

## 10.5 Window And Focus

- [x] Test fullscreen.
- [x] Test windowed mode.
- [x] Test Alt+Enter.
- [x] Test focus loss.
- [x] Test focus regain.
- [x] Test minimizing during campaign.
- [x] Test minimizing during combat.
- [x] Test display scaling.
- [x] Prevent stuck keys after focus changes.

### Phase 10 Exit Criteria

- [x] All required flows work with keyboard only.
- [x] Controls can be remapped safely.
- [x] Critical information is readable and not color- or audio-only.
- [x] Fullscreen and focus transitions do not trap input.

# Phase 11 - Packaging, Distribution, And Release Validation

## 11.1 Windows Packaging

- [x] Build the app image.
- [x] Build the portable ZIP.
- [x] Build the EXE installer when WiX is available.
- [x] Bundle Java 21.
- [x] Verify no IDE dependency.
- [x] Verify no source-tree dependency.
- [x] Verify required assets are packaged.
- [x] Verify excluded generation assets remain excluded.
- [x] Verify version metadata.
- [x] Verify application name.
- [x] Verify install path.
- [x] Verify shortcut creation.
- [x] Verify uninstall.

## 11.2 Clean-Machine Testing

- [x] Install on a machine without a development JDK.
- [x] Launch from the installed shortcut.
- [x] Launch the portable ZIP.
- [x] Start a campaign.
- [x] Save.
- [x] Exit.
- [x] Relaunch.
- [x] Load.
- [x] Enter tactical combat.
- [x] Complete a mission.
- [x] Verify logs and saves use writable user locations.
- [x] Verify uninstall does not remove saves unless explicitly requested.

## 11.3 Distribution Channels

- [x] Prepare itch.io build.
- [x] Prepare GitHub release build.
- [x] Prepare private distribution build.
- [x] Investigate Steam requirements.
- [x] Add Steam packaging only when channel setup exists.
- [x] Keep build artifacts identical where platform services are not required.
- [x] Generate checksums.
- [x] Generate release notes.
- [x] Publish system requirements.
- [x] Publish known issues.
- [x] Publish save compatibility policy.

## 11.4 External Testing

- [ ] Recruit the owner and several colleagues.
- [ ] Record tester hardware.
- [ ] Record build version.
- [ ] Record campaign seed.
- [ ] Record difficulty.
- [ ] Record session duration.
- [ ] Collect first-route feedback.
- [ ] Collect fleet-building feedback.
- [ ] Collect capital/titan encounter feedback.
- [ ] Collect mission-clarity feedback.
- [ ] Collect performance feedback.
- [ ] Collect save/load feedback.
- [ ] Promote reproducible defects into tests.

## 11.5 Required Playthroughs

- [ ] New campaign first route
- [ ] Save/load continuity
- [ ] Defeat path
- [ ] Victory path
- [ ] Sixty-minute session
- [ ] Full 8-15 hour campaign
- [ ] Green-alliance-focused campaign
- [ ] Yellow-liberation-focused campaign
- [ ] Capital-heavy fleet campaign
- [ ] Strike-heavy campaign
- [ ] Iron Command campaign segment
- [ ] Older-save migration campaign

## 11.6 Final Quality Gates

- [ ] No known reproducible crash.
- [ ] No known save corruption.
- [ ] No known campaign soft lock.
- [ ] No known mission soft lock.
- [ ] No inaccessible mandatory control.
- [ ] No mission with undiscoverable success or failure conditions.
- [ ] No unexplained persistent fleet disappearance.
- [ ] No incorrect purchase or resource deduction.
- [ ] No strike-origin command-kill defect.
- [ ] No supported largest battle below 30 FPS on verified minimum hardware.
- [ ] No severe required-text overlap.
- [ ] No progression requiring debug tools.
- [ ] Full tests pass.
- [ ] Performance guardrails pass.
- [ ] Screenshot regression passes.
- [ ] Production validation passes.
- [ ] Save/load soak passes.
- [ ] Campaign-transition fuzzing passes.
- [ ] Windows packaged-build smoke test passes.

### Phase 11 Exit Criteria

- [ ] Several external testers complete meaningful sessions.
- [ ] A complete campaign can be won from a packaged build.
- [ ] The release meets every final quality gate.

# Final 1.0 Sign-Off

## Release Promise

- [ ] Strategic fleet command is excellent.
- [ ] Tactical ship combat is excellent.
- [ ] The living faction war is convincing.
- [ ] Fleet building and logistics are meaningful.
- [ ] The campaign is replayable.
- [ ] The campaign lasts approximately 8-15 hours.
- [ ] The player can grow into an overwhelming force.
- [ ] The war grows strong enough to challenge that force.
- [ ] Capital ships are a normal strategic reality.
- [ ] Titans are rare campaign-defining targets.
- [ ] Green visibly fights alongside Blue.
- [ ] Yellow can become politically and militarily relevant.
- [ ] Faction ships and replacements remain traceable.
- [ ] Mining, hauling, shipyards, and construction form one real economy.
- [ ] Territory changes through actual battles.
- [ ] Player actions create remembered consequences.

## Owner Acceptance

- [ ] Owner approves faction fleet scale.
- [ ] Owner approves capital encounter frequency.
- [ ] Owner approves titan rarity.
- [ ] Owner approves late-campaign challenge.
- [ ] Owner approves mining automation.
- [ ] Owner approves production queues.
- [ ] Owner approves dynamic territory.
- [ ] Owner approves Yellow alignment behavior.
- [ ] Owner approves mission clarity.
- [ ] Owner approves strikes.
- [ ] Owner approves environment depth.
- [ ] Owner approves presentation.
- [ ] Owner approves packaged build.

## Release Decision

- [ ] GO - All required gates pass and no P0/P1 release blocker remains.
- [ ] NO-GO - At least one P0/P1 release blocker remains.

Final build:

Final commit:

Release date:

Known accepted issues:

Owner notes:

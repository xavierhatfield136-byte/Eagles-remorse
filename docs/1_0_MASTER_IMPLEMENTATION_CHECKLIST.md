# 1.0 Master Implementation Checklist

Date created: 2026-06-23
Status: Active
Authority: `1_0_OWNER_DECISIONS_AND_IMPLEMENTATION_ROADMAP.md`

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

- [ ] Record the current version from `VERSION`.
- [ ] Record the current git commit or working-tree snapshot.
- [ ] Run the full test suite.
- [ ] Run `performanceGuardrailsCi`.
- [ ] Run screenshot regression.
- [ ] Run production validation.
- [ ] Run save/load soak.
- [ ] Run campaign-transition fuzzing.
- [ ] Record current test duration.
- [ ] Record current performance-harness results.
- [ ] Record current screenshot signatures.
- [ ] Record current asset-validation results.
- [ ] Record current campaign save-schema version.

## 0.2 Establish 1.0 Save Compatibility

- [ ] Inventory every campaign field added since the earliest supported alpha.
- [ ] Identify fields that are required for authoritative live systems.
- [ ] Identify debug/readout-only fields.
- [ ] Identify future/model-only fields retained for compatibility.
- [ ] Define defaults for every missing field in an older save.
- [ ] Define fallback behavior for unknown enum values.
- [ ] Define fallback behavior for missing faction fleets.
- [ ] Define fallback behavior for missing production queues.
- [ ] Define fallback behavior for missing mining cargo.
- [ ] Define fallback behavior for missing territory state.
- [ ] Define fallback behavior for missing reputation history.
- [ ] Define fallback behavior for missing environment state.
- [ ] Add migration fixtures for each supported public version.
- [ ] Verify old saves load without exceptions.
- [ ] Verify migrated saves can travel.
- [ ] Verify migrated saves can enter tactical combat.
- [ ] Verify migrated saves can purchase and queue ships.
- [ ] Verify migrated saves can save again.
- [ ] Verify a migrated save remains readable after a second reload.
- [ ] Display a clear recovery message when migration repairs missing data.
- [ ] Never delete a source save before a migrated checkpoint is verified.

## 0.3 Establish Release Telemetry

- [ ] Add structured reasons for fleet creation.
- [ ] Add structured reasons for fleet destruction.
- [ ] Add structured reasons for fleet disappearance.
- [ ] Add structured reasons for ownership changes.
- [ ] Add structured reasons for production starts and stops.
- [ ] Add structured reasons for mining departures and returns.
- [ ] Add structured reasons for mission success and failure.
- [ ] Add structured reasons for strike denial.
- [ ] Add structured reasons for save recovery.
- [ ] Ensure telemetry excludes secrets and unnecessary personal data.

### Phase 0 Exit Criteria

- [ ] All baseline commands pass.
- [ ] At least one older save fixture migrates through the complete campaign loop.
- [ ] New 1.0 systems have an explicit save and telemetry contract.

# Phase 1 - Release Safety And Player Clarity

## 1.1 Mission Briefing Contract

- [ ] Define one authoritative tactical-entry briefing model.
- [ ] Include the primary objective.
- [ ] Include the exact success condition.
- [ ] Include the exact failure condition.
- [ ] Include protected assets.
- [ ] Include required kills, captures, rescues, or survival quota.
- [ ] Include the mission timer when present.
- [ ] Include optional objectives.
- [ ] Include optional-objective rewards.
- [ ] Include known enemy strength.
- [ ] Include uncertainty when intelligence is incomplete.
- [ ] Include one short recommended first action.
- [ ] Render the briefing before normal combat begins.
- [ ] Keep the briefing readable without pausing indefinitely.
- [ ] Allow the player to reopen the briefing.
- [ ] Keep the current objective visible in the tactical HUD.
- [ ] Update progress immediately when the relevant state changes.
- [ ] Explain why progress is blocked.
- [ ] Explain mission failure at the moment it occurs.
- [ ] Explain mission success at the moment it occurs.
- [ ] Add briefing coverage for every authored campaign sector.
- [ ] Add briefing coverage for every live generated mission family.
- [ ] Add a regression test that no live mission has a blank success condition.
- [ ] Add a regression test that no live mission has a blank failure condition.
- [ ] Add a regression test for protected-asset naming.
- [ ] Add a regression test for timer and quota display.

## 1.2 Strike Cost And Replenishment Clarity

- [ ] Inventory torpedo, sortie, and atomic strike resources.
- [ ] Show current strike inventory before launch.
- [ ] Show ammunition cost before launch.
- [ ] Show fuel cost before launch.
- [ ] Show supply cost before launch.
- [ ] Show charge or cooldown cost before launch.
- [ ] Show required intelligence quality.
- [ ] Show estimated effect.
- [ ] Show retaliation or detection risk.
- [ ] Show the exact reason a strike is unavailable.
- [ ] Add an explicit strike-replenishment explanation.
- [ ] Identify hubs that can rearm strikes.
- [ ] Identify salvage or mission rewards that replenish strikes.
- [ ] Identify production paths that replenish strikes.
- [ ] Distinguish reusable carrier capacity from expendable strike stores.
- [ ] Confirm costs are deducted exactly once.
- [ ] Confirm canceled strikes do not consume resources.
- [ ] Confirm failed target validation does not consume resources.
- [ ] Confirm save/load preserves inventory and cooldowns.
- [ ] Add positive and negative strike purchase/rearm tests.

## 1.3 Strike-Origin Correctness

- [ ] Reproduce the reported strike-origin issue.
- [ ] Confirm tactical strikes originate from the launching force or valid entry
  vector.
- [ ] Prevent strikes from spawning directly on the target.
- [ ] Prevent friendly command kills caused by invalid strike origins.
- [ ] Keep enemy strategic strikes disabled until origin correctness is proven.
- [ ] Add launch-origin assertions for every strike type.
- [ ] Add friendly-fire regression coverage.
- [ ] Add off-screen launcher regression coverage.
- [ ] Add save/load coverage for pending strike state.

## 1.4 Lost Contacts

- [ ] Define when a contact is live.
- [ ] Define when a contact is stale.
- [ ] Define when a stale contact becomes invalid.
- [ ] Remove invalid lost-contact icons from the normal HUD.
- [ ] Keep historical contact records in an archive or log where useful.
- [ ] Never draw a live movement vector for an invalid contact.
- [ ] Never allow strike selection against an invalid contact.
- [ ] Never allow navigation to silently target an invalid contact.
- [ ] Show “last known” only while the estimate remains actionable.
- [ ] Add expiry tests at each intelligence level.
- [ ] Add lost-contact save/load tests.

## 1.5 Overworld Selection And Navigation

- [ ] Audit click targets for fleets.
- [ ] Audit click targets for facilities.
- [ ] Audit click targets for missions.
- [ ] Audit click targets for mining areas.
- [ ] Audit click targets for overlapping markers.
- [ ] Increase destination hit areas without making nearby objects ambiguous.
- [ ] Prefer the visually topmost marker when hit areas overlap.
- [ ] Add selection cycling for truly overlapping contacts if required.
- [ ] Clearly distinguish selection from course plotting.
- [ ] Show the selected destination before confirming travel.
- [ ] Allow course cancellation.
- [ ] Add tests for edge-of-marker clicks.
- [ ] Add tests for dense hub clusters.
- [ ] Add tests for fleet-near-mission selection.

## 1.6 Terminology Cleanup

- [ ] Replace ambiguous `Readiness` with context-specific labels.
- [ ] Use `Combat Condition` for hull/fleet fighting condition.
- [ ] Use `Crew Readiness` for crew state.
- [ ] Use `Strike Availability` for strike capacity.
- [ ] Use `Production Progress` for construction.
- [ ] Replace generic `Stores` with named resources.
- [ ] Replace `Route Tempo` with `Travel Speed`.
- [ ] Show ETA separately from travel speed.
- [ ] Use `Green Reputation`.
- [ ] Use `Yellow Reputation`.
- [ ] Reserve `Favor` for a real spendable value only.
- [ ] Reserve `Leverage` for a distinct mechanic only.
- [ ] Replace generic `Scar` with battle history, persistent damage, or aftermath.
- [ ] Qualify every use of `Sweep`.
- [ ] Use `Sensor Sweep` for contact detection.
- [ ] Use `Recon Sweep` for target-quality intelligence.
- [ ] Use `Security Sweep` for hostile clearing.
- [ ] Rename campaign `Safe Exit` presentation to `Withdraw To Strategic Map`
  without changing its established behavior.
- [ ] Replace generic `Pressure` with the actual source.
- [ ] Keep `Fleet Ore` and `Yard Ore`.
- [ ] Keep the explanatory line for Fleet Ore and Yard Ore.
- [ ] Search all player-facing text for unexplained internal terminology.
- [ ] Add copy assertions for critical campaign screens.

## 1.7 Reputation And Fleet Health Visibility

- [ ] Make Green Reputation visible from the main campaign command interface.
- [ ] Make Yellow Reputation visible from the main campaign command interface.
- [ ] Show the next reputation threshold.
- [ ] Show currently unlocked benefits.
- [ ] Show recent reputation changes and reasons.
- [ ] Show allied hull health in the fleet view.
- [ ] Show allied armor condition where applicable.
- [ ] Show allied shield condition where applicable.
- [ ] Show persistent damage and repair need.
- [ ] Show which ships are unavailable, under repair, or under construction.

## 1.8 Audio And Visual Correctness

- [ ] Reproduce the missing missile-launch SFX.
- [ ] Verify event-to-file mapping.
- [ ] Verify volume and channel routing.
- [ ] Verify rapid launches do not suppress every cue.
- [ ] Verify captions or visual feedback exist where required.
- [ ] Identify Yellow hull sprites with the forward-right triangular notch.
- [ ] Determine whether the notch is transparent padding, source damage, or crop.
- [ ] Repair affected source assets.
- [ ] Verify repaired sprites at multiple scales.
- [ ] Audit deep-space encounter background selection.
- [ ] Prevent inappropriate planet backgrounds in ordinary open space.
- [ ] Preserve planet backgrounds at authored orbital locations.
- [ ] Add screenshot baselines for corrected scenes.

### Phase 1 Exit Criteria

- [ ] Every mission explains success and failure before combat.
- [ ] Strike costs and replenishment are discoverable.
- [ ] Lost contacts cannot mislead the player.
- [ ] Required navigation targets are easy to select.
- [ ] Missile launch audio works.
- [ ] No known P0 presentation or clarity defect remains.

# Phase 2 - Faction Inventories And Fleet Population

## 2.1 Audit Existing Order Of Battle

- [ ] Export Blue starting ships by role.
- [ ] Export Red starting ships by role.
- [ ] Export Green starting ships by role.
- [ ] Export Yellow starting ships by role.
- [ ] Export ships assigned to active fleets.
- [ ] Export ships assigned to garrisons.
- [ ] Export ships assigned to convoys.
- [ ] Export ships assigned to mining groups.
- [ ] Export ships assigned to reserves.
- [ ] Export ships under construction.
- [ ] Export unassigned inventory records.
- [ ] Flag duplicate ship IDs.
- [ ] Flag duplicate persistent ship names.
- [ ] Flag ships without a faction.
- [ ] Flag ships without an owner force, base, or queue.
- [ ] Flag forces without origin or mission.
- [ ] Add a developer order-of-battle report.

## 2.2 Blue Starting Fleet

- [ ] Preserve the mothership.
- [ ] Preserve the small picket escort.
- [ ] Add one starting miner.
- [ ] Ensure the miner belongs to the persistent Blue fleet.
- [ ] Ensure the miner survives checkpoint save/load.
- [ ] Ensure the miner appears in the fleet interface.
- [ ] Ensure the miner can receive strategic orders.
- [ ] Ensure the starting fleet remains vulnerable.
- [ ] Verify the starting fleet cannot immediately overpower major Red forces.

## 2.3 Red Starting Inventory

- [ ] Define a substantially larger finite Red inventory.
- [ ] Include numerous pickets and patrol craft.
- [ ] Include meaningful frigate and destroyer reserves.
- [ ] Include regularly encountered cruisers.
- [ ] Include capital ships.
- [ ] Include rare titans.
- [ ] Include mining ships.
- [ ] Include ore transports.
- [ ] Include repair and support ships.
- [ ] Include garrison fleets.
- [ ] Include hunter-killer groups.
- [ ] Include infrastructure-defense groups.
- [ ] Include rear-area reserves.
- [ ] Distribute forces across real Red-controlled locations.
- [ ] Prevent all Red capital ships from clustering in one early region.
- [ ] Scale accessible threat by campaign stage without creating free ships.

## 2.4 Green Starting Inventory

- [ ] Define a strong finite Green inventory.
- [ ] Include active defense fleets.
- [ ] Include offensive task forces.
- [ ] Include patrol groups.
- [ ] Include mining groups.
- [ ] Include ore transports.
- [ ] Include repair and support ships.
- [ ] Include capital ships.
- [ ] Include rare special or titan assets if supported by lore.
- [ ] Assign Green fleets to visible strategic missions.
- [ ] Ensure Green can win and lose without the player.
- [ ] Ensure Green has enough depth to feel like a war partner.

## 2.5 Yellow Starting Inventory

- [ ] Define a smaller but strong finite Yellow inventory.
- [ ] Include trade fleets.
- [ ] Include mining fleets.
- [ ] Include ore transports.
- [ ] Include armed escorts.
- [ ] Include faction-distinct capital ships.
- [ ] Include rare high-value special ships.
- [ ] Assign some Yellow forces to Red-coerced operations.
- [ ] Assign some Yellow forces to transactional neutral operations.
- [ ] Allow liberated or purchased Yellow ships to become allied.
- [ ] Preserve producing-faction hull identity after Blue purchase.

## 2.6 Fleet Composition Rules

- [ ] Define small patrol templates.
- [ ] Define mining deployment templates.
- [ ] Define trade convoy templates.
- [ ] Define escort templates.
- [ ] Define infrastructure-defense templates.
- [ ] Define hunter-killer templates.
- [ ] Define capital task-force templates.
- [ ] Define titan task-force templates.
- [ ] Define mixed large-fleet templates.
- [ ] Require support ships for long-range major fleets where appropriate.
- [ ] Prevent all large fleets from becoming identical.
- [ ] Apply faction doctrine to composition.
- [ ] Apply available inventory to composition.
- [ ] Apply mission requirements to composition.
- [ ] Apply local threat to composition.
- [ ] Never add a role that the faction inventory cannot supply.

## 2.7 Capital Ship Presence

- [ ] Define minimum expected capital-contact frequency by campaign phase.
- [ ] Add capital ships to real Red offensive forces.
- [ ] Add capital ships to Green counteroffensives.
- [ ] Add Yellow capitals to high-value coerced or allied fleets.
- [ ] Create rewards appropriate to capital-ship risk.
- [ ] Add reputation effects for destroying or saving major ships.
- [ ] Add strategic consequences for capital losses.
- [ ] Ensure capital names persist into tactical combat.
- [ ] Ensure damaged capitals can retreat and remain damaged.
- [ ] Ensure crippled capitals can become recovery targets.

## 2.8 Titans

- [ ] Define titan inventory by faction.
- [ ] Define titan construction restrictions.
- [ ] Define titan deployment conditions.
- [ ] Define titan escort requirements.
- [ ] Define titan repair requirements.
- [ ] Define titan loss consequences.
- [ ] Add rare titan-bearing task forces.
- [ ] Allow exceptional Red fleets to contain several titans.
- [ ] Add high-intelligence warning for titan presence.
- [ ] Add titan-hunt missions from real persistent contacts.
- [ ] Add major rewards and reputation for successful titan hunts.
- [ ] Preserve titan damage and names across retreat and re-encounter.
- [ ] Prevent routine early-game titan encounters.

## 2.9 Tactical Conversion

- [ ] Convert every persistent manifest ship into the tactical encounter.
- [ ] Preserve role.
- [ ] Preserve faction.
- [ ] Preserve name.
- [ ] Preserve hull condition.
- [ ] Preserve armor condition.
- [ ] Preserve crew/readiness state.
- [ ] Preserve ammunition and logistics effects where applicable.
- [ ] Preserve retreat intent.
- [ ] Return survivors to the strategic force.
- [ ] Remove destroyed ships from finite inventory.
- [ ] Record captured or recovered ships correctly.
- [ ] Prevent duplicate strategic and tactical instances.

## 2.10 Contact Density

- [ ] Define a target for ordinary visible traffic.
- [ ] Ensure the player usually has at least one nearby contact.
- [ ] Include friendly patrols.
- [ ] Include neutral traders.
- [ ] Include mining deployments.
- [ ] Include logistics convoys.
- [ ] Include hostile scouts.
- [ ] Include occasional major task forces.
- [ ] Avoid turning every contact into an interruption.
- [ ] Distinguish visible traffic from mandatory encounters.
- [ ] Measure contacts per travel leg.
- [ ] Compare the owner's desired 5-8 meaningful events with playtest fatigue.
- [ ] Tune by route length and region activity.

## 2.11 Task-Force Inspection

- [ ] Preserve current intel gating.
- [ ] Show faction at early contact.
- [ ] Show estimated ship count when identified.
- [ ] Show ship classes at high intelligence.
- [ ] Show persistent names at high intelligence.
- [ ] Show damage and readiness at high intelligence.
- [ ] Show cargo and logistics at high intelligence.
- [ ] Show origin and destination when identified.
- [ ] Show mission and intent at high intelligence.
- [ ] Show capital and titan warnings prominently.
- [ ] Show formation breakdown.
- [ ] Show escort and support composition.
- [ ] Show confidence or age for every estimate.
- [ ] Do not expose exact hidden values at low intelligence.

### Phase 2 Exit Criteria

- [ ] Green and Yellow are visibly active during ordinary play.
- [ ] Capital ships appear naturally in repeatable campaign runs.
- [ ] Titans are rare but discoverable campaign-defining contacts.
- [ ] Every major fleet has inventory provenance.
- [ ] Tactical encounters preserve persistent manifests exactly.

# Phase 3 - Mining, Logistics, Shipyards, And Construction

## 3.1 Strategic Mining Model

- [ ] Replace repetitive player wait-to-mine interaction with strategic orders.
- [ ] Allow the player to assign a mining ship or group to a mining area.
- [ ] Show expected ore yield.
- [ ] Show expected mining duration.
- [ ] Show route risk.
- [ ] Show required cargo capacity.
- [ ] Show escort recommendation.
- [ ] Allow recall before completion.
- [ ] Allow reassignment.
- [ ] Apply depletion where intended.
- [ ] Apply hazards where intended.
- [ ] Apply enemy interruption.
- [ ] Save active mining assignments.

## 3.2 Faction Mining Forces

- [ ] Add Red mining ship variants.
- [ ] Add Yellow mining ship variants.
- [ ] Confirm Green miners remain active.
- [ ] Give mining forces a real origin.
- [ ] Give mining forces a target mining area.
- [ ] Give mining forces a cargo capacity.
- [ ] Give mining forces an escort policy.
- [ ] Give mining forces a return destination.
- [ ] Give mining forces a retreat policy.
- [ ] Give mining forces a replacement policy.
- [ ] Make mining deployments visible as contacts.
- [ ] Allow the player to protect allied miners.
- [ ] Allow the player to raid hostile miners.
- [ ] Allow ore capture from defeated mining forces.

## 3.3 Ore Cargo And Hauling

- [ ] Track ore cargo on meaningful mining and transport forces.
- [ ] Increase cargo as mining progresses.
- [ ] Cap cargo by capacity.
- [ ] Transfer ore only at a valid faction logistics destination.
- [ ] Prevent double transfer.
- [ ] Prevent cargo transfer after force destruction.
- [ ] Drop or award recoverable ore after interception.
- [ ] Record stolen ore in campaign memory.
- [ ] Record lost ore in faction logistics reports.
- [ ] Show cargo estimates only at appropriate intel.
- [ ] Save cargo and transfer progress.

## 3.4 Faction Economy Pools

- [ ] Define faction ore pools.
- [ ] Define faction credit or industrial-capacity pools.
- [ ] Define faction supply pools where required.
- [ ] Credit delivered ore to the correct faction.
- [ ] Debit construction costs from the correct faction.
- [ ] Debit repairs from the correct faction.
- [ ] Debit replacement logistics from the correct faction.
- [ ] Prevent negative inventories.
- [ ] Pause construction when resources are insufficient.
- [ ] Resume construction when resources arrive.
- [ ] Expose understandable faction production summaries.

## 3.5 Shipyard Local State

- [ ] Give each shipyard a local identity.
- [ ] Give each shipyard an owner.
- [ ] Give each shipyard local production lanes.
- [ ] Give each shipyard a hull catalog.
- [ ] Give each shipyard damage state.
- [ ] Give each shipyard blockade state.
- [ ] Give each shipyard capture state.
- [ ] Give each shipyard queue capacity.
- [ ] Give each shipyard a completion location.
- [ ] Disable production when destroyed.
- [ ] Reduce production when damaged.
- [ ] Restrict production when blockaded.
- [ ] Transfer or cancel queues according to capture rules.
- [ ] Save every queue and lane.

## 3.6 Class-Separated Production Lanes

- [ ] Add escort production lane.
- [ ] Add frigate/destroyer production lane.
- [ ] Add cruiser production lane.
- [ ] Add capital production lane.
- [ ] Add titan/special production lane.
- [ ] Use 5-second base escort construction time.
- [ ] Use 10-second base frigate/destroyer construction time.
- [ ] Use 15-second base cruiser construction time.
- [ ] Use 20-second base capital construction time.
- [ ] Use 25-second base titan/special construction time.
- [ ] Apply damage and blockade modifiers.
- [ ] Apply faction or shipyard quality modifiers only when visible.
- [ ] Prevent one lane from blocking unrelated classes.
- [ ] Show every active lane.
- [ ] Show queue order.
- [ ] Show remaining time.
- [ ] Show paused reason.

## 3.7 Player Ship Orders

- [ ] Replace immediate delivery with queue placement.
- [ ] Show producing faction.
- [ ] Show producing shipyard.
- [ ] Show hull faction identity.
- [ ] Show credit cost.
- [ ] Show Fleet Ore cost.
- [ ] Show required reputation.
- [ ] Show expected completion time.
- [ ] Confirm purchase before charging resources.
- [ ] Deduct resources exactly once.
- [ ] Allow multiple orders.
- [ ] Queue orders in the correct class lane.
- [ ] Notify the player when construction completes.
- [ ] Add completed hull to persistent inventory.
- [ ] Allow collection or deployment according to yard rules.
- [ ] Handle shipyard capture during a player order.
- [ ] Handle shipyard destruction during a player order.
- [ ] Define refund or loss rules.

## 3.8 AI Construction Decisions

- [ ] Make AI identify inventory shortages.
- [ ] Make AI prioritize miners when ore income collapses.
- [ ] Make AI prioritize escorts when logistics losses rise.
- [ ] Make AI prioritize patrols when territory is exposed.
- [ ] Make AI prioritize capitals for major offensives.
- [ ] Make AI reserve titan construction for strategic conditions.
- [ ] Make AI respect available hull catalogs.
- [ ] Make AI respect ore.
- [ ] Make AI respect credits or industrial capacity.
- [ ] Make AI respect local shipyard lanes.
- [ ] Make AI respect queue time.
- [ ] Make AI cancel or redirect invalid orders.
- [ ] Make AI avoid endless cheap-unit queue spam.
- [ ] Record AI construction reasons.

## 3.9 Transport Ships And Field Repair

- [ ] Preserve increased player ore capacity from transports.
- [ ] Allow transports to assist nearby internal repairs slowly.
- [ ] Consume supplies during assisted repair.
- [ ] Prevent transport repair from restoring armor.
- [ ] Prevent transport repair from restoring shields.
- [ ] Reduce fire and terminal internal ailments.
- [ ] Define assistance radius.
- [ ] Define assistance rate.
- [ ] Define supply cost per repair.
- [ ] Prevent stacking from becoming unlimited.
- [ ] Show active repair support.
- [ ] Show supply drain.
- [ ] Stop repair support when supplies are exhausted.
- [ ] Preserve unrepaired battle damage after combat.
- [ ] Require a safe hub for full restoration.

## 3.10 Mining And Production Missions

- [ ] Add mining convoy escort from a real mining force.
- [ ] Add mining convoy raid from a real hostile force.
- [ ] Add ore-hauler interception.
- [ ] Add shipyard queue defense.
- [ ] Add crippled yard resupply.
- [ ] Add emergency miner rescue.
- [ ] Add stolen-ore recovery.
- [ ] Add capital-completion interdiction.
- [ ] Make mission outcomes alter real cargo or queue state.
- [ ] Prevent duplicate rewards outside the real economy.

### Phase 3 Exit Criteria

- [ ] Ore can be traced from mining site to delivered cargo.
- [ ] Delivered ore enables real construction.
- [ ] Destroying miners or haulers delays production.
- [ ] Player purchases enter visible class-separated queues.
- [ ] Transport repair consumes supplies and cannot restore armor or shields.

# Phase 4 - Dynamic Territory And Autonomous War

## 4.1 Territory Model

- [ ] Define authoritative ownership for every strategic location.
- [ ] Define contest state.
- [ ] Define occupation state.
- [ ] Define capture progress.
- [ ] Define garrison requirement.
- [ ] Define supply connection requirement.
- [ ] Define what services change with ownership.
- [ ] Define what visuals change with ownership.
- [ ] Define what missions change with ownership.
- [ ] Define what routes change with ownership.
- [ ] Save ownership and contest history.

## 4.2 AI Strategic Objectives

- [ ] Let Red attack infrastructure.
- [ ] Let Red attack logistics.
- [ ] Let Red hunt the player.
- [ ] Let Red pursue wider-war territory objectives.
- [ ] Let Green defend important locations.
- [ ] Let Green launch counteroffensives.
- [ ] Let Green escort logistics.
- [ ] Let Green capture Red or contested territory.
- [ ] Let Yellow protect trade.
- [ ] Let Yellow support Red when coerced.
- [ ] Let Yellow resist Red under favorable conditions.
- [ ] Let Yellow support Blue or Green when allied.
- [ ] Select objectives from actual theater conditions.
- [ ] Prevent impossible or unreachable objectives.

## 4.3 AI-Versus-AI Battles

- [ ] Detect opposing forces entering battle range.
- [ ] Create one authoritative battle record.
- [ ] Allow off-screen resolution.
- [ ] Allow player intervention before resolution.
- [ ] Allow player arrival during an active battle.
- [ ] Preserve participating ship manifests.
- [ ] Apply ammunition and condition.
- [ ] Apply retreat and pursuit.
- [ ] Apply losses to finite inventories.
- [ ] Apply cargo loss.
- [ ] Apply territory outcome.
- [ ] Apply reputation and memory where relevant.
- [ ] Create battle aftermath.
- [ ] Prevent duplicate tactical and auto-resolve outcomes.

## 4.4 Battle Warning And Join Flow

- [ ] Identify major battles worth announcing.
- [ ] Warn the player approximately 30 seconds before engagement.
- [ ] Show participants.
- [ ] Show location.
- [ ] Show estimated strength.
- [ ] Show current distance and ETA.
- [ ] Add `Follow Fleet`.
- [ ] Add `Join Battle`.
- [ ] Add `Ignore`.
- [ ] Add `Offer Support` where applicable.
- [ ] Let the player follow Green fleets.
- [ ] Let the player follow allied Yellow fleets.
- [ ] Keep the followed fleet selected.
- [ ] Handle destination changes.
- [ ] Handle followed-fleet destruction or retreat.

## 4.5 Ownership Changes

- [ ] Change ownership after a valid capture outcome.
- [ ] Require surviving occupation or garrison strength where appropriate.
- [ ] Update map colors and symbols.
- [ ] Update service availability.
- [ ] Update shipyard access.
- [ ] Update mining access.
- [ ] Update mission generation.
- [ ] Update local patrol generation.
- [ ] Update faction economy contribution.
- [ ] Update reputation consequences.
- [ ] Create a visible campaign bulletin.
- [ ] Record the reason for ownership change.
- [ ] Preserve change through save/load.

## 4.6 Yellow Alignment

- [ ] Define coerced-hostile Yellow state.
- [ ] Define transactional-neutral Yellow state.
- [ ] Define liberated-friendly Yellow state.
- [ ] Make Yellow hostile when operating directly with Red in a mission.
- [ ] Make independent Yellow transactional by default.
- [ ] Make Blue- or Green-purchased Yellow ships allied.
- [ ] Allow reputation and aid to shift Yellow alignment.
- [ ] Allow Red pressure to shift Yellow toward hostility.
- [ ] Show current Yellow alignment.
- [ ] Explain why a Yellow force is hostile or friendly.
- [ ] Persist alignment and reasons.

## 4.7 Anti-Stalemate

- [ ] Detect prolonged territorial stalemate.
- [ ] Avoid generating free fleets to break it.
- [ ] Generate player-facing support opportunities.
- [ ] Offer ore, credits, intelligence, or ships as intervention.
- [ ] Offer a major offensive mission.
- [ ] Offer logistics interdiction.
- [ ] Offer shipyard defense or sabotage.
- [ ] Let ignored stalemates remain unresolved.
- [ ] Let player intervention materially alter the balance.

## 4.8 Divergent Campaigns

- [ ] Keep starting seeds reproducible.
- [ ] Allow faction decisions to diverge after start.
- [ ] Record major divergence causes.
- [ ] Ensure save/load does not reset directors.
- [ ] Add deterministic tests for identical action sequences.
- [ ] Add tests proving different player actions create different outcomes.
- [ ] Add long-run simulations for territory diversity.

### Phase 4 Exit Criteria

- [ ] Green gains and loses territory without player scripting.
- [ ] Red attacks logistics and infrastructure.
- [ ] Yellow alignment changes behavior.
- [ ] Major battles can occur and resolve without the player.
- [ ] The player can follow and join allied battles.
- [ ] Territory state visibly responds to battle outcomes.

# Phase 5 - Difficulty, Attrition, And Late Campaign

## 5.1 Standard Difficulty Target

- [ ] Tune Standard so most players can win after learning from one defeat.
- [ ] Target one major loss, failed mission, or forced withdrawal per roughly five
  battles for a capable player.
- [ ] Preserve recovery after reaching a friendly or captured hub.
- [ ] Avoid irreversible collapse from one ordinary mistake.
- [ ] Allow severe repeated mistakes to produce a losing campaign.
- [ ] Record loss frequency in playtest reports.
- [ ] Record retreat frequency.
- [ ] Record resource emergency frequency.

## 5.2 Tactical Opposition

- [ ] Increase enemy fleet size before increasing raw ship durability.
- [ ] Increase enemy role diversity.
- [ ] Increase capital presence.
- [ ] Use support ships.
- [ ] Use coordinated escorts.
- [ ] Use reserves.
- [ ] Use retreat and regroup behavior.
- [ ] Use faction doctrine.
- [ ] Preserve current time-to-kill.
- [ ] Prevent difficulty from becoming simple health inflation.

## 5.3 Strategic Travel Pressure

- [ ] Make route fuel costs meaningful.
- [ ] Make route supply costs meaningful.
- [ ] Make route ammunition costs meaningful.
- [ ] Show costs before travel.
- [ ] Show recovery options.
- [ ] Avoid unavoidable unwarned failure.
- [ ] Scale event density by route.
- [ ] Scale danger by territory and contact activity.
- [ ] Preserve quiet routes where appropriate.
- [ ] Test long travel with large fleets.

## 5.4 Repair And Persistent Damage

- [ ] Preserve meaningful hull and internal damage after combat.
- [ ] Prevent free full recovery between battles.
- [ ] Use supplies for field repair.
- [ ] Require hubs for full armor restoration.
- [ ] Require hubs for full system restoration when appropriate.
- [ ] Show repair duration.
- [ ] Show repair cost.
- [ ] Show unavailable ships.
- [ ] Allow damaged fleets to retreat to safety.
- [ ] Add recovery missions for crippled capitals.
- [ ] Add save/load tests for persistent damage.

## 5.5 Credits And Trade

- [ ] Keep credits as the main limiting resource.
- [ ] Preserve ore as a valuable trade good.
- [ ] Make Green trade frequently useful.
- [ ] Make Yellow trade frequently useful.
- [ ] Avoid making trade mandatory every single route.
- [ ] Show buy and sell prices.
- [ ] Show reputation effects.
- [ ] Show shortages and opportunities.
- [ ] Prevent unlimited price exploits.
- [ ] Prevent duplicate transaction rewards.
- [ ] Add economy soak tests.

## 5.6 Strikes

- [ ] Keep strikes in 1.0.
- [ ] Preserve current reduced quantity.
- [ ] Add real and visible costs.
- [ ] Add discoverable replenishment.
- [ ] Keep target-quality intelligence requirement.
- [ ] Keep strikes powerful enough to matter.
- [ ] Prevent strikes from becoming a free guaranteed punch.
- [ ] Do not enable enemy strikes until launch-origin safety is proven.
- [ ] Re-test strike-heavy routes.
- [ ] Record strike usage and recovery frequency.

## 5.7 Fleet Doctrine

- [ ] Audit every doctrine and posture.
- [ ] Reproduce why `LINE` feels useless.
- [ ] Define a distinct purpose for `LINE`.
- [ ] Improve its positioning, survivability, fire concentration, or command
  benefit.
- [ ] Ensure no doctrine dominates every situation.
- [ ] Explain doctrine effects in plain language.
- [ ] Add tactical comparisons using identical fleets and seeds.
- [ ] Preserve player workload at the current acceptable level.

## 5.8 Retreat

- [ ] Preserve the 7.5-second withdrawal wind-up.
- [ ] Require the player to avoid interruption during withdrawal.
- [ ] Clearly show withdrawal progress.
- [ ] Clearly show interruption reason.
- [ ] Define what allied ships escape.
- [ ] Define what damaged or distant ships risk losing.
- [ ] Return to the strategic map on success.
- [ ] Apply pursuit or aftermath consequences.
- [ ] Remove surrender as a fake player option unless it has a real strategic
  outcome.

## 5.9 Iron Command

- [ ] Increase enemy armor on Iron Command.
- [ ] Shorten the delay before enemy shield regeneration begins.
- [ ] Keep those bonuses confined to the harder mode.
- [ ] Show difficulty modifiers before campaign start.
- [ ] Avoid hidden rule changes unrelated to difficulty.
- [ ] Add mode-specific regression tests.
- [ ] Run full Iron Command defeat-path acceptance.

## 5.10 Late Campaign And Ending

- [ ] Increase late-game Red fleet quality.
- [ ] Increase late-game capital concentration.
- [ ] Increase strategic coordination.
- [ ] Avoid slowing the late game through empty travel.
- [ ] Add meaningful late-game Green operations.
- [ ] Add meaningful late-game Yellow alignment consequences.
- [ ] Include surviving Red remnants in final readiness or ending evaluation.
- [ ] Prevent a clean victory claim while major Red forces remain unaddressed
  without acknowledging them.
- [ ] Keep the final victory reachable without debug actions.
- [ ] Preserve an earned and conclusive ending.

### Phase 5 Exit Criteria

- [ ] Late campaign is more demanding than mid campaign.
- [ ] Player fleet growth remains satisfying.
- [ ] Opposition scale keeps pace with player power.
- [ ] Damage and logistics matter across battles.
- [ ] Standard and Iron Command feel meaningfully different.

# Phase 6 - Reputation, Aid, And Minimum Politics

## 6.1 Reputation Model

- [ ] Use clear Green Reputation.
- [ ] Use clear Yellow Reputation.
- [ ] Record gains and losses with reasons.
- [ ] Show current value.
- [ ] Show current tier.
- [ ] Show next threshold.
- [ ] Show unlocked benefits.
- [ ] Save reputation history.
- [ ] Prevent accidental double awards.

## 6.2 Aid Transfers

- [ ] Allow sending ore to Green.
- [ ] Allow sending credits to Green.
- [ ] Allow sending intelligence to Green.
- [ ] Allow transferring ships to Green.
- [ ] Allow sending ore to Yellow.
- [ ] Allow sending credits to Yellow.
- [ ] Allow sending intelligence to Yellow.
- [ ] Allow transferring ships to Yellow.
- [ ] Show transfer cost.
- [ ] Show expected reputation gain.
- [ ] Show expected strategic effect.
- [ ] Confirm before transferring a persistent ship.
- [ ] Remove transferred ships from Blue inventory.
- [ ] Add transferred ships to the recipient inventory.
- [ ] Preserve original hull faction identity.

## 6.3 Material Consequences

- [ ] Let Green aid improve Green fleet replacement or operations.
- [ ] Let Yellow aid improve Yellow resistance or alliance.
- [ ] Let intelligence reveal missions or contacts.
- [ ] Let ship transfers create visible recipient fleets.
- [ ] Let reputation improve trade.
- [ ] Let reputation unlock support.
- [ ] Let reputation alter fleet behavior.
- [ ] Let reputation alter final-battle readiness.
- [ ] Let reputation alter ending summaries.
- [ ] Explain each consequence with “because you did X” feedback.

## 6.4 Yellow Liberation

- [ ] Promote Yellow liberation missions.
- [ ] Tie missions to real Red-controlled Yellow assets.
- [ ] Allow Yellow forces to change alignment after liberation.
- [ ] Add later allied Yellow operations.
- [ ] Add Yellow capital or special-ship support at high reputation.
- [ ] Add consequences for attacking transactional Yellow forces.
- [ ] Add consequences for ignoring coerced Yellow forces.
- [ ] Save liberation history.

## 6.5 Campaign Memory

- [ ] Record trade totals.
- [ ] Record reputation changes.
- [ ] Record faction aid.
- [ ] Record ship kills.
- [ ] Record capital kills.
- [ ] Record titan kills.
- [ ] Record ore mined.
- [ ] Record ore stolen.
- [ ] Record miners saved or destroyed.
- [ ] Record allied battles joined.
- [ ] Record territory helped or abandoned.
- [ ] Reference important records in reports.
- [ ] Reference important records in faction responses.
- [ ] Reference important records in the ending.

## 6.6 Character Presence

- [ ] Give important captains distinct names.
- [ ] Give important captains concise personality tags.
- [ ] Use short written reactions.
- [ ] Avoid long dialogue scenes.
- [ ] Use limited high-quality spoken callouts only.
- [ ] Keep callouts optional through quiet mode.
- [ ] Caption spoken callouts.
- [ ] Avoid placeholder voices in release builds.

### Phase 6 Exit Criteria

- [ ] Aid to Green and Yellow changes later play.
- [ ] At least two faction strategies create different encounters.
- [ ] Reputation is easy to find and understand.
- [ ] Yellow can become a meaningful ally.
- [ ] The ending references important player behavior.

# Phase 7 - Tactical Environments And Presentation

## 7.1 Environment Framework

- [ ] Define environment identity separately from background art.
- [ ] Define tactical modifiers.
- [ ] Define sensor modifiers.
- [ ] Define movement modifiers.
- [ ] Define weapon modifiers where appropriate.
- [ ] Define hazard behavior.
- [ ] Define AI awareness of environment effects.
- [ ] Show environment rules before combat.
- [ ] Preserve environment state through save/load if persistent.
- [ ] Avoid effects that invalidate ship roles without warning.

## 7.2 Required Live Environments

- [ ] Implement sensor-shadow environment.
- [ ] Implement quarantine or logistics-restriction environment.
- [ ] Implement a third materially distinct environment.
- [ ] Consider debris field.
- [ ] Consider ion storm.
- [ ] Consider mine-laced corridor.
- [ ] Consider station-defense perimeter.
- [ ] Consider low-orbit gravity or drag only if readable.
- [ ] Ensure at least three environments alter tactical decisions.

## 7.3 Asteroids And Destructible Space

- [ ] Decide whether cannon fire should destroy asteroids.
- [ ] Increase durability where needed.
- [ ] Preserve navigational cover long enough to matter.
- [ ] Allow destruction when tactically intentional.
- [ ] Prevent debris from causing severe performance loss.
- [ ] Teach the player how asteroid cover works.

## 7.4 Sensor Shadows

- [ ] Reduce detection through authored shadow regions.
- [ ] Affect both player and AI.
- [ ] Show shadow boundaries or understandable cues.
- [ ] Allow tactical ambush without invisible rules.
- [ ] Connect strategic and tactical sensor behavior.
- [ ] Add tests for symmetric detection effects.

## 7.5 Quarantine Warnings

- [ ] Mark quarantined routes or locations.
- [ ] Explain logistics restrictions.
- [ ] Explain entry consequences.
- [ ] Apply supply, repair, trade, or rescue effects intentionally.
- [ ] Let reputation or missions alter quarantine state.
- [ ] Avoid blocking progression without recovery options.

## 7.6 Background Selection

- [ ] Use empty space for ordinary deep-space encounters.
- [ ] Use planet backgrounds only near appropriate locations.
- [ ] Use faction or operational backgrounds where authored.
- [ ] Keep empty space visually legible.
- [ ] Verify all major mission families.
- [ ] Add screenshot coverage.

## 7.7 Audio Direction

- [ ] Use ambient silence as the default.
- [ ] Reserve music for detection.
- [ ] Reserve music for pursuit.
- [ ] Reserve music for major battles.
- [ ] Reserve music for major campaign events.
- [ ] Reduce warp SFX repetition.
- [ ] Add warp variants or cooldown rules.
- [ ] Preserve missile-launch readability.
- [ ] Check 30-minute audio fatigue.
- [ ] Verify quiet mode.
- [ ] Verify captions.

## 7.8 Visual Presentation

- [ ] Verify industrial naval science-fiction identity.
- [ ] Preserve approved hull skins.
- [ ] Preserve approved turret skins.
- [ ] Preserve approved damage visuals.
- [ ] Preserve shields and trails.
- [ ] Repair identified Yellow hull defects.
- [ ] Audit map icons for clarity.
- [ ] Audit high-density text panels.
- [ ] Avoid planet backgrounds in open space.
- [ ] Keep allied, neutral, hostile, hub, and operational areas distinct.

### Phase 7 Exit Criteria

- [ ] At least three environments materially change combat.
- [ ] Open space and orbital locations use appropriate backgrounds.
- [ ] Warp audio is not fatiguing.
- [ ] Missile audio works.
- [ ] No must-replace visual defect remains.

# Phase 8 - Architecture And Bloat Control

## 8.1 Authoritative Ownership

- [ ] Document the authoritative campaign state for fleets.
- [ ] Document the authoritative state for inventory.
- [ ] Document the authoritative state for economy.
- [ ] Document the authoritative state for territory.
- [ ] Document the authoritative state for production.
- [ ] Document the authoritative state for missions.
- [ ] Remove parallel live claims from model-only systems.
- [ ] Add invariants preventing duplicate ownership.
- [ ] Add invariants preventing duplicate resource deduction.

## 8.2 CampaignSystem Reduction

- [ ] Inventory cohesive feature regions in `CampaignSystem.java`.
- [ ] Identify read-only presentation helpers.
- [ ] Identify fleet inventory logic.
- [ ] Identify mining and logistics logic.
- [ ] Identify production logic.
- [ ] Identify territory logic.
- [ ] Identify mission briefing logic.
- [ ] Extract only when ownership becomes clearer.
- [ ] Preserve public compatibility wrappers where needed.
- [ ] Keep each extraction covered by focused tests.
- [ ] Avoid unrelated formatting churn.

## 8.3 Renderer Reduction

- [ ] Inventory campaign rendering sections.
- [ ] Separate data preparation from drawing.
- [ ] Prevent rendering from mutating campaign state.
- [ ] Extract repeated panel layout helpers.
- [ ] Preserve screenshot baselines intentionally.
- [ ] Keep UI layout responsive.
- [ ] Add no-mutation tests for read paths.

## 8.4 Dead And Prototype Code

- [ ] Search for unused model-only capability claims.
- [ ] Search for unreachable controls.
- [ ] Search for dead mission branches.
- [ ] Search for stale terminology.
- [ ] Search for obsolete placeholder comments.
- [ ] Search for duplicate systems with competing ownership.
- [ ] Remove code only when references and save impact are understood.
- [ ] Keep deferred systems clearly marked.
- [ ] Do not delete compatible saved fields casually.

## 8.5 Validation And Diagnostics

- [ ] Add an order-of-battle validator.
- [ ] Add a fleet-provenance validator.
- [ ] Add an economy conservation validator.
- [ ] Add a production-queue validator.
- [ ] Add a territory ownership validator.
- [ ] Add a mission-briefing completeness validator.
- [ ] Add a contact validity validator.
- [ ] Add a strike-origin validator.
- [ ] Add a save migration validator.
- [ ] Expose failures in developer diagnostics.

### Phase 8 Exit Criteria

- [ ] No live system has two competing authoritative owners.
- [ ] Render paths are read-only.
- [ ] Major 1.0 systems have executable validators.
- [ ] Bloat reduction does not destabilize saves or gameplay.

# Phase 9 - Performance And Maximum Battle Scale

## 9.1 Define Supported Battle Scale

- [ ] Measure ordinary battle ship counts.
- [ ] Measure major battle ship counts.
- [ ] Measure titan battle ship counts.
- [ ] Measure projectile counts.
- [ ] Measure wreck counts.
- [ ] Measure VFX counts.
- [ ] Define the largest supported battle.
- [ ] Keep the player-facing scale below unreadable chaos.
- [ ] Avoid promising unlimited ships.

## 9.2 Performance Targets

- [ ] Maintain 60 FPS target in ordinary tactical play.
- [ ] Maintain 30 FPS hard floor in the largest supported battle.
- [ ] Validate 1280x720.
- [ ] Validate 1920x1080.
- [ ] Validate integrated graphics target.
- [ ] Validate recommended discrete graphics target.
- [ ] Measure update time.
- [ ] Measure render time.
- [ ] Measure AI time.
- [ ] Measure projectile time.
- [ ] Measure campaign-map time.
- [ ] Measure memory.
- [ ] Measure garbage collection.

## 9.3 Stress Scenarios

- [ ] Run 100 ships per side.
- [ ] Run 160 ships per side.
- [ ] Run capital-heavy battle.
- [ ] Run titan-heavy battle.
- [ ] Run missile-heavy battle.
- [ ] Run wreck-heavy aftermath.
- [ ] Run prolonged 30-minute tactical session.
- [ ] Run repeated tactical entries.
- [ ] Run long strategic campaign.
- [ ] Run save/load during a large campaign state.

## 9.4 Optimization Rules

- [ ] Profile before optimizing.
- [ ] Preserve gameplay correctness.
- [ ] Preserve internal damage.
- [ ] Preserve persistent fleet identity.
- [ ] Prefer spatial indexing and bounded work.
- [ ] Avoid invisible reductions that alter battle outcomes.
- [ ] Allow explicit visual-detail settings.
- [ ] Keep Tactical FPS View optional.
- [ ] Document measured gains.

### Phase 9 Exit Criteria

- [ ] Largest supported battle remains at or above 30 FPS on verified minimum
  hardware.
- [ ] Ordinary battles target 60 FPS.
- [ ] No progressive memory degradation blocks a full session.

# Phase 10 - Accessibility And Input Acceptance

## 10.1 Keyboard Navigation

- [ ] Navigate the main menu without a mouse.
- [ ] Start a campaign without a mouse.
- [ ] Navigate strategic command tabs.
- [ ] Select fleets and locations.
- [ ] Plot and cancel a course.
- [ ] Open and use trade.
- [ ] Open and use shipyards.
- [ ] Queue a ship.
- [ ] Review objectives.
- [ ] Enter tactical combat.
- [ ] Withdraw from tactical combat.
- [ ] Save and load.
- [ ] Recover from defeat.
- [ ] Show visible keyboard focus.

## 10.2 Remapping

- [ ] Open remapping UI.
- [ ] Rebind every required action.
- [ ] Detect conflicts.
- [ ] Explain conflicts.
- [ ] Restore defaults.
- [ ] Persist mappings.
- [ ] Recover from invalid mapping data.
- [ ] Ensure no required action becomes permanently inaccessible.

## 10.3 Visual Accessibility

- [ ] Verify high contrast.
- [ ] Verify color-independent hostile/friendly/neutral symbols.
- [ ] Verify selected state without color alone.
- [ ] Verify warning state without color alone.
- [ ] Verify text scaling.
- [ ] Verify 1280x720 readability.
- [ ] Verify 1920x1080 readability.
- [ ] Verify long names.
- [ ] Verify largest numeric values.
- [ ] Verify mission briefing text.
- [ ] Verify fleet health display.
- [ ] Verify reputation display.

## 10.4 Audio Accessibility

- [ ] Caption spoken callouts.
- [ ] Caption critical radio information.
- [ ] Keep captions readable over combat.
- [ ] Verify quiet mode.
- [ ] Verify reduced-noise mode.
- [ ] Ensure no required information is audio-only.
- [ ] Verify volume controls.

## 10.5 Window And Focus

- [ ] Test fullscreen.
- [ ] Test windowed mode.
- [ ] Test Alt+Enter.
- [ ] Test focus loss.
- [ ] Test focus regain.
- [ ] Test minimizing during campaign.
- [ ] Test minimizing during combat.
- [ ] Test display scaling.
- [ ] Prevent stuck keys after focus changes.

### Phase 10 Exit Criteria

- [ ] All required flows work with keyboard only.
- [ ] Controls can be remapped safely.
- [ ] Critical information is readable and not color- or audio-only.
- [ ] Fullscreen and focus transitions do not trap input.

# Phase 11 - Packaging, Distribution, And Release Validation

## 11.1 Windows Packaging

- [ ] Build the app image.
- [ ] Build the portable ZIP.
- [ ] Build the EXE installer when WiX is available.
- [ ] Bundle Java 21.
- [ ] Verify no IDE dependency.
- [ ] Verify no source-tree dependency.
- [ ] Verify required assets are packaged.
- [ ] Verify excluded generation assets remain excluded.
- [ ] Verify version metadata.
- [ ] Verify application name.
- [ ] Verify install path.
- [ ] Verify shortcut creation.
- [ ] Verify uninstall.

## 11.2 Clean-Machine Testing

- [ ] Install on a machine without a development JDK.
- [ ] Launch from the installed shortcut.
- [ ] Launch the portable ZIP.
- [ ] Start a campaign.
- [ ] Save.
- [ ] Exit.
- [ ] Relaunch.
- [ ] Load.
- [ ] Enter tactical combat.
- [ ] Complete a mission.
- [ ] Verify logs and saves use writable user locations.
- [ ] Verify uninstall does not remove saves unless explicitly requested.

## 11.3 Distribution Channels

- [ ] Prepare itch.io build.
- [ ] Prepare GitHub release build.
- [ ] Prepare private distribution build.
- [ ] Investigate Steam requirements.
- [ ] Add Steam packaging only when channel setup exists.
- [ ] Keep build artifacts identical where platform services are not required.
- [ ] Generate checksums.
- [ ] Generate release notes.
- [ ] Publish system requirements.
- [ ] Publish known issues.
- [ ] Publish save compatibility policy.

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


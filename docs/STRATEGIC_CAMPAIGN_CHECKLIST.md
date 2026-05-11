# Strategic Campaign Checklist

Use this as the working implementation checklist. Do not mark an item complete unless the exact requirement is satisfied in the game, not just partially represented in code.

## Phase 1: Strategic Overmap Foundation

- [x] Remove remaining dependence on old sector-era campaign flow except when a tactical encounter is intentionally launched.
- [x] Make the strategic overmap the default campaign layer rather than a wrapper around a mission chain.
- [x] Make strategic overmap state authoritative for campaign progression.
- [x] Ensure fleet world position is owned by overmap state.
- [x] Ensure fleet heading is owned by overmap state.
- [x] Ensure travel target is owned by overmap state.
- [x] Ensure docking state is owned by overmap state.
- [x] Ensure selected location is owned by overmap state.
- [x] Ensure enemy search-group state is owned by overmap state.
- [x] Ensure consumed areas of interest are owned by overmap state.
- [x] Ensure Earth progress is owned by overmap state.
- [x] Audit and enforce the rule that the campaign map is not a combat scene.
- [x] Do not render tactical ships when the game is in campaign-map mode.
- [x] Do not render projectiles when the game is in campaign-map mode.
- [x] Do not render the battle HUD when the game is in campaign-map mode.
- [x] Do not run tactical combat simulation in the background when the game is in campaign-map mode.
- [x] Do not leave tactical-only overlays active when the game is in campaign-map mode.
- [x] Unify all encounter entry and return flow into one clean pipeline.
- [x] Make mission locations use the unified encounter pipeline.
- [x] Make hostile interceptions use the unified encounter pipeline.
- [x] Make hostile event areas use the unified encounter pipeline.
- [x] Ensure the unified encounter pipeline is exactly:
  `travel -> detection or arrival -> encounter prompt -> auto-resolve or take command -> results applied to campaign state -> return to overmap`
- [x] Make save/load preserve strategic overmap continuity exactly.
- [x] Preserve fleet world position across save/load.
- [x] Preserve current travel progress across save/load.
- [x] Preserve current destination across save/load.
- [x] Preserve docking state across save/load.
- [x] Preserve selected location across save/load.
- [x] Preserve enemy search-group positions across save/load.
- [x] Preserve enemy search-group behaviors across save/load.
- [x] Preserve discovered overmap locations across save/load.
- [x] Preserve consumed overmap locations across save/load.
- [x] Preserve strategic resources across save/load.
- [x] Preserve long-range strike inventory across save/load.

## Phase 2: Travel and Route Planning

- [x] Tune continuous travel so movement feels deliberate instead of menu-driven.
- [x] Tune travel speed until crossing the map feels like navigation rather than clicking through a list.
- [x] Tune redirect response so changing course feels intentional and responsive.
- [x] Tune hold-position behavior so stopping travel is clear and useful.
- [x] Tune docking radius so approach and arrival feel believable and readable.
- [x] Tune ETA presentation so travel timing is easy to understand at a glance.
- [x] Make route choice produce meaningful tradeoffs.
- [x] Make routes differ in travel time.
- [x] Make routes differ in threat exposure.
- [x] Make routes differ in interception probability.
- [x] Make routes differ in logistics pressure.
- [x] Make routes differ in resource opportunity.
- [x] Make routes differ in salvage opportunity.
- [x] Make routes differ in access to recovery hubs.
- [x] Expand contact visibility into a real contact-confidence model.
- [x] Add an `unknown contact` state.
- [x] Add a `possible patrol` state.
- [x] Add a `confirmed hostile` state.
- [x] Add an `identified task force` state.
- [x] Add a `lost contact` state.
- [x] Present contact-confidence states clearly in the UI.
- [x] Make hostile search groups feel continuous, alive, and region-aware.
- [x] Make search groups patrol in a way that feels like ongoing operational pressure.
- [x] Make search groups search in a way that feels like ongoing operational pressure.
- [x] Make search groups investigate in a way that feels like ongoing operational pressure.
- [x] Make search groups intercept in a way that feels like ongoing operational pressure.
- [x] Make search groups guard in a way that feels like ongoing operational pressure.
- [x] Make search groups return in a way that feels like ongoing operational pressure.
- [x] Scale hostile pressure by region so the map has a true south-to-north escalation.
- [x] Make the south feel safer and more forgiving.
- [x] Make the mid-map feel contested and opportunistic.
- [x] Make the north feel tense, compressed, and dangerous.

## Phase 3: Hubs as Strategic Anchors

- [x] Make Green hubs mechanically distinct from Yellow hubs.
- [x] Make Green hubs primarily support repair.
- [x] Make Green hubs primarily support refit.
- [x] Make Green hubs primarily support military logistics.
- [x] Make Green hubs primarily support crew support.
- [x] Make Green hubs primarily support resistance contracts.
- [x] Make Green hubs primarily support intelligence.
- [x] Make Yellow hubs primarily support trade.
- [x] Make Yellow hubs primarily support salvage sale.
- [x] Make Yellow hubs primarily support market logistics.
- [x] Make Yellow hubs primarily support cargo support.
- [x] Make Yellow hubs primarily support fuel and supply economy.
- [x] Make Yellow hubs primarily support industrial services.
- [x] Replace placeholder hub buttons with real campaign systems.
- [x] Make repair connect to real campaign resources and consequences.
- [x] Make fuel connect to real campaign resources and consequences.
- [x] Make supplies connect to real campaign resources and consequences.
- [x] Make ammunition connect to real campaign resources and consequences.
- [x] Make salvage connect to real campaign resources and consequences.
- [x] Make trade connect to real campaign resources and consequences.
- [x] Make contracts connect to real campaign resources and consequences.
- [x] Make shipbuilding connect to real campaign resources and consequences.
- [x] Make docking matter as a real operational action.
- [x] Require the player to physically approach a hub before using it.
- [x] Make reaching a hub feel like relief, opportunity, or necessity.
- [x] Do not allow hubs to function like abstract long-range menu nodes.
- [x] Give hubs regional and economic identity.
- [x] Vary hub inventory by location and region.
- [x] Vary hub pricing by location and region.
- [x] Vary hub services by location and region.
- [x] Vary hub support quality by location and region.
- [x] Ensure hubs do not all feel interchangeable.
- [x] Make campaign recovery loops depend on hubs.
- [x] Route repairs primarily through hub access.
- [x] Route replenishment primarily through hub access.
- [x] Route ship management primarily through hub access.
- [x] Route strategic reset opportunities primarily through hub access.

## Phase 4: Strategic Fleet Identity

- [x] Give stealth fleets a distinct campaign role instead of treating them like small conventional fleets.
- [x] Make stealth fleets specialize in scouting.
- [x] Make stealth fleets specialize in ambush.
- [x] Make stealth fleets specialize in selective kills.
- [x] Make stealth fleets specialize in uncertainty creation.
- [x] Make stealth fleets specialize in disengagement.
- [x] Ensure failed stealth attacks create real pressure to break contact rather than turning into normal brawls.
- [x] Give strike groups a distinct campaign role.
- [x] Make strike groups behave like aggressive battle-seeking formations.
- [x] Make strike groups rapidly convert detection into threat.
- [x] Make strike groups force the player into hard choices.
- [x] Give carriers a distinct strategic role through force projection.
- [x] Make sorties a real carrier gameplay layer.
- [x] Make reconnaissance a real carrier gameplay layer.
- [x] Make range extension a real carrier gameplay layer.
- [x] Make pre-contact pressure a real carrier gameplay layer.
- [x] Ensure carriers feel operationally different from line combat fleets.
- [x] Give heavy fleets a distinct strategic role without letting them dominate the whole campaign fantasy.
- [x] Make heavy fleets control territory.
- [x] Make heavy fleets project danger.
- [x] Keep heavy fleets vulnerable to scouting failure.
- [x] Keep heavy fleets vulnerable to isolation.
- [x] Keep heavy fleets vulnerable to missile pressure.
- [x] Keep heavy fleets vulnerable to sortie pressure.
- [x] Keep heavy fleets vulnerable to subsystem collapse.
- [x] Keep heavy fleets vulnerable to bad logistics.
- [x] Make fleet composition matter more than raw tonnage.
- [x] Reward combined arms.
- [x] Reward role coverage.
- [x] Prevent the campaign from rewarding only concentration of the largest hulls possible.

## Phase 5: Long-Range Warfare and Counterplay

- [ ] Keep long-range strikes as high-commitment tools rather than casual spam actions.
- [ ] Make torpedoes consume meaningful resources, expose intent, or create risk.
- [ ] Make sorties consume meaningful resources, expose intent, or create risk.
- [ ] Make atomic options consume meaningful resources, expose intent, or create risk.
- [ ] Add defensive counterplay to long-range warfare.
- [ ] Add interception as a meaningful response.
- [ ] Add jamming as a meaningful response.
- [ ] Add decoys as a meaningful response.
- [ ] Add evasive movement as a meaningful response.
- [ ] Add route adjustment as a meaningful response.
- [ ] Add alert-driven reaction as a meaningful response.
- [ ] Make reconnaissance a real gameplay layer instead of a flavor note.
- [ ] Make recon help discover hostile activity.
- [ ] Make recon help confirm hostile activity.
- [ ] Make recon help refine hostile activity.
- [ ] Make recon help track hostile activity.
- [ ] Make intel quality affect route planning.
- [ ] Make intel quality affect strike decisions.
- [ ] Tie strategic strikes to campaign consequences.
- [ ] Make strategic strikes affect alert level.
- [ ] Make strategic strikes affect enemy response.
- [ ] Make strategic strikes affect route danger.
- [ ] Make strategic strikes affect operational exposure.
- [ ] Make strategic strikes affect faction consequences.
- [ ] Make strategic strikes affect resource depletion.
- [ ] Keep the atomic option dramatic, rare, and costly enough that it does not trivialize fleet play.

## Phase 6: Campaign UI Readability

- [ ] Finish the right-side campaign panel so it is readable at a glance.
- [ ] Keep the panel organized around campaign summary.
- [ ] Keep the panel organized around selected location.
- [ ] Keep the panel organized around services or actions.
- [ ] Keep the panel organized around intel or contacts.
- [ ] Do not allow the panel to regress into dense walls of text.
- [ ] Reduce visual clutter on the overmap UI.
- [ ] Increase information hierarchy on the overmap UI.
- [ ] Make important information obvious immediately.
- [ ] Keep secondary information accessible without overwhelming the player.
- [ ] Make contact and threat readouts legible and trustworthy.
- [ ] Make it easy to see where the player is.
- [ ] Make it easy to see where the player is going.
- [ ] Make it easy to see how dangerous the route or destination is.
- [ ] Make it easy to see whether docking is possible.
- [ ] Make it easy to see whether the fleet is being hunted.
- [ ] Make hub actions clear, large, and context-aware.
- [ ] Only show service buttons when they are relevant.
- [ ] Make service buttons clearly communicate what they do.
- [ ] Make service buttons clearly communicate what they cost.

## Phase 7: Tactical Alignment

- [ ] Finish the combat readability pass and verify it with discipline.
- [ ] Reduce clutter layered over ships.
- [ ] Make projectile visuals smaller where needed.
- [ ] Slow projectile travel where needed to improve anticipation.
- [ ] Put explosion emphasis on major impacts and ship deaths.
- [ ] Remove noisy ECM presentation.
- [ ] Keep warp presentation subtle and wormhole-like.
- [ ] Finish the combat audio overhaul so sound carries weight and clarity.
- [ ] Give weapon fire distinct role-based audio identity.
- [ ] Give impacts distinct role-based audio identity.
- [ ] Give subsystem failures distinct role-based audio identity.
- [ ] Give launches distinct role-based audio identity.
- [ ] Give warp events distinct role-based audio identity.
- [ ] Give major ship destruction distinct role-based audio identity.
- [ ] Preserve the one-large-sector encounter rule for manual command.
- [ ] Do not reintroduce fragmented encounter presentation.
- [ ] Make one encounter mean one readable tactical arena.
- [ ] Make manual command worth choosing for important fights.
- [ ] Make manual command the better choice for difficult fights.
- [ ] Make manual command the better choice for high-risk fights.
- [ ] Make manual command the better choice for asymmetric fights.
- [ ] Make manual command the better choice for tactically interesting fights.
- [ ] Keep auto-resolve useful for routine fights.

## Phase 8: Integration and Validation

- [ ] Test the full strategic loop end to end until it feels like one game.
- [ ] Ensure the player can navigate meaningfully.
- [ ] Ensure the player can scout meaningfully.
- [ ] Ensure the player can manage risk meaningfully.
- [ ] Ensure the player can dock meaningfully.
- [ ] Ensure the player can recover or trade meaningfully.
- [ ] Ensure the player can react to contacts meaningfully.
- [ ] Ensure the player can choose whether to fight meaningfully.
- [ ] Ensure encounter consequences return cleanly to campaign state.
- [ ] Ensure the player can continue north toward Earth without flow breakage.
- [ ] Eliminate places where the campaign still feels like a mission menu in disguise.
- [ ] Redesign any remaining “click next mission” flow until route choice, logistics, and pressure are doing the real work.
- [ ] Do not consider the strategic layer complete until the overmap creates tension even when no tactical battle is active.
- [ ] Ensure the overmap itself carries gameplay through pursuit.
- [ ] Ensure the overmap itself carries gameplay through uncertainty.
- [ ] Ensure the overmap itself carries gameplay through travel pressure.
- [ ] Ensure the overmap itself carries gameplay through docking decisions.
- [ ] Ensure the overmap itself carries gameplay through route planning.

## Final Definition of Done

- [ ] Do not mark the strategic campaign rewrite complete until this exact statement is true in practice:
  `The player can cross a large hostile map toward Earth, choose routes instead of following a simple ladder, weigh safety against reward, dock at meaningful hubs, react to living hostile search pressure, use long-range tools with real commitment, choose auto-resolve or direct command when contact happens, fight one large tactical encounter when needed, and return cleanly to the overmap without the experience collapsing back into the old campaign structure.`

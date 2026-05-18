# Campaign Session Change Audit Checklist

Use this document as a verification list for everything requested and implemented during this chat session.

Checked boxes below were audited against the current codebase using automated regression runs plus direct code inspection.
Unchecked boxes are the items that still require hands-on manual play, visual judgment, or feel-based validation in the live game.

## Command Layer And Readability

- [x] Verify the left-side campaign command stack now surfaces useful operational state instead of acting like flavor-only decoration.
- [x] Verify the receiver/manual, direction finder, comms, and top-of-panel systems now provide actionable campaign information.
- [x] Verify the navigation tab is readable and no longer presents a wall of noisy text with no clear player takeaway.
- [x] Verify the fleet tab presents meaningful command information instead of dead or low-value status text.
- [x] Verify the resources tab presents actionable logistics information rather than passive stock numbers only.
- [x] Verify the strikes tab explains readiness, constraints, consequence, and strike state clearly.
- [x] Verify the campaign right-side information blocks are split into shorter labeled sections instead of one long paragraph stack.
- [x] Verify no major command panel runs more than roughly 7 to 9 lines without a divider or visual break.
- [x] Verify selected-site summaries surface "why it matters," "action window," and "risk" lines.
- [x] Verify selected-contact summaries surface opportunity/risk guidance instead of vague descriptive text.
- [x] Verify the industrial HUD style remains intact while behaving like a readable command interface rather than a debug overlay.

## Panel Layout And Hitboxes

- [x] Verify campaign action buttons render inside proper dark action wells instead of over textured frame art.
- [x] Verify action text is sharp and readable rather than blurred into the middle of the map.
- [x] Verify campaign action hitboxes exactly match the rendered button rectangles.
- [x] Verify clicking the visual center of a campaign action button activates that exact button.
- [x] Verify no campaign button click activates the button underneath it.
- [x] Verify mission-side tactical command bay buttons also match their visible rendered bounds.
- [x] Verify tactical action buttons are clearly separated from informational panels.

## Strategic Tabs And Action-First Command Flow

- [x] Verify the strategic layer supports clear tabs for mission, fleet, resources, contacts, and strikes.
- [x] Verify each strategic tab limits itself to its own information domain instead of showing everything at once.
- [x] Verify the right panel acts as a selected mission/contact panel rather than a catch-all data dump.
- [x] Verify the lower panel acts as a fleet/readiness area rather than mixing mission briefing and support chatter together.
- [x] Verify the command bay is the primary place for visible clickable actions.
- [x] Verify all normal strategic actions are exposed as visible buttons.
- [x] Verify unavailable but relevant strategic actions remain visible in disabled form with a reason.
- [x] Verify keyboard shortcuts are secondary and not required for basic strategic play.
- [x] Verify action previews explain cost, risk, expected result, and disabled reason where appropriate.

## Fleet Authority And Fleet Management

- [x] Verify the fleet tab supports choosing which ships are committed, held back, reserved, or automatic for tactical entry.
- [x] Verify the player can control fleet participation in battle instead of being forced into one all-or-nothing commitment state.
- [x] Verify detached divisions can be created from the strategic layer.
- [x] Verify detached divisions can be moved independently to other tactical pockets.
- [x] Verify detached divisions can be merged back into the flagship group when conditions allow.
- [x] Verify fleet focus controls work and change the currently managed hull/group.
- [x] Verify reserve ships can arrive as reinforcements after a tactical engagement begins.
- [x] Verify fleet purchase / commissioning from the fleet tab actually adds the bought hulls to the persistent roster.
- [x] Verify newly purchased ships immediately appear in the fleet/hangar view.
- [x] Verify newly purchased ships actually accompany the player into future missions.
- [ ] Verify ore-driven fleet growth loop works: mine ore, return, buy ships/upgrades, relaunch with expanded fleet.

## Campaign Persistence And Flow

- [x] Verify campaign startup still reaches the strategic overmap correctly.
- [x] Verify campaign sector startup still enters tactical combat correctly when intended.
- [x] Verify authored sectors still progress to the next episode/objective correctly.
- [x] Verify sector 1 authored timing/state behaves as intended.
- [x] Verify mission completion still updates campaign progress, route-state, and scars on the overmap.
- [x] Verify campaign checkpoint/load persists strategic state correctly after all of these changes.
- [x] Verify persistent galaxy location state serialization still restores progression, scars, route notes, and relationship state.
- [x] Verify newly added mission outer-threat state also persists correctly through campaign save/load.

## Early Combat And System Regression Fixes

- [x] Verify fresh ships initialize room systems correctly before any blackout/reactor edge case blocks weapon logic.
- [x] Verify missile and superweapon firing still works after ship spawn/init.
- [x] Verify red kinetic superweapons once again emit the expected `DisruptorSlug`.
- [x] Verify the red kinetic superweapon still produces the intended detonation / stasis behavior after impact.
- [x] Verify missile-role ranges are restored to the intended behavior.
- [x] Verify AI missile firing uses the context-aware detection path.
- [x] Verify same-sector non-stealth hostile contacts are visible/detectable again where intended.
- [x] Verify campaign `startSector(...)` behavior still cleanly separates tactical entry from strategic overmap bootstrap.

## Mission Objectives And Fail Conditions

- [x] Verify "destroy marked targets" style objectives were converted into destroy-count objectives where required.
- [x] Verify mission 3 no longer soft-locks because marked targets lose their marks or disappear from the objective logic.
- [x] Verify eliminate-target missions can be completed by killing the required number of enemy ships even if original marked entities are lost.
- [x] Verify objective HUD language refers to destroy-count targets instead of brittle marked-target language where appropriate.

## Strategic Map Selection And Marker Priority

- [x] Verify player, selected mission, active objective, and major threats read as top-priority map elements.
- [x] Verify mission-relevant contacts read as secondary priority markers.
- [x] Verify low-value rumors/background contacts read as small tertiary markers.
- [x] Verify selected markers are visually emphasized.
- [x] Verify clicking a marker updates the selected mission/contact card cleanly.
- [x] Verify support contacts, hazards, and fleet contacts can be selected reliably even when near mission nodes.
- [x] Verify hostile contact clicks now beat oversized nearby mission hitboxes.
- [x] Verify overmap contact selection creates a real contact lock rather than accidentally selecting the mission underneath.

## Encounter Variety And Local Map Identity

- [x] Verify local encounters no longer reuse the same metropolis-like map identity for every scenario.
- [x] Verify open-space intercepts read as open-space intercepts instead of city/hub interiors.
- [x] Verify anchored intercepts read like anchored/picket encounters rather than generic mission districts.
- [x] Verify resource-zone encounters present sparse ore-drift / prospecting identity.
- [x] Verify salvage-field encounters present wreck-belt / debris identity.
- [x] Verify cache encounters present low-signature hidden-pocket identity.
- [x] Verify distress encounters present rescue-drift identity.
- [x] Verify story-event encounters present isolated signal/relay identity.
- [x] Verify hub/repair/mission encounters still read like service or district spaces where appropriate.
- [x] Verify local encounters do not leak full sector-wide strategic task-force overlays into small pocket encounters.

## Mission Space Structure And Subzones

- [x] Verify mission areas now behave like one large continuous playable space rather than hard-separated invisible pocket arenas.
- [x] Verify concealment/fog no longer hides major portions of the mission area when full-space reveal is intended.
- [x] Verify the player can see the whole mission district and understand where things are happening.
- [x] Verify ships no longer get physically stuck against invisible internal subzone borders.
- [x] Verify movement can pass freely across mission subzone borders.
- [x] Verify warp travel between subzones is still limited to orthogonally adjacent connected sectors only.
- [x] Verify a ship in `A2` can warp only to adjacent valid sectors like `A1`, `A3`, and `B2`, not arbitrary distant cells.
- [x] Verify direct weapon fire cannot cross subzone borders even though movement can.
- [x] Verify enemies do not shoot the player from beyond a subzone border.
- [x] Verify the player does not shoot enemies across a subzone border with normal direct fire.

## Tactical Readability And Mission-Time Map HUD

- [x] Verify the mission-time strategic/tactical map is now layered instead of trying to show every data type at once.
- [x] Verify the map area focuses mainly on spatial awareness, markers, route, and danger.
- [x] Verify the right-side mission/contact panel focuses on the currently selected thing only.
- [x] Verify the bottom status area focuses on flagship/fleet condition and readiness only.
- [x] Verify the command bay focuses on visible actions only.
- [x] Verify hidden keyboard-command hint lines are minimized and no longer dominate the HUD.
- [x] Verify mission-time action buttons are visible, large enough, and teach the system better than raw shortcut text.
- [x] Verify primary actions for a selected mission/contact are visually obvious.
- [x] Verify selected tactical markers create real selection state in the mission HUD.
- [x] Verify mission actions like plot course, hold, recon, escort, and strike can be executed from visible buttons.

## Logistics And Resource Pressure

- [x] Verify campaign resources are now meaningfully consumed rather than being effectively infinite.
- [x] Verify fuel meaningfully constrains travel or strike use.
- [x] Verify supplies meaningfully constrain recon/support/operations tempo.
- [x] Verify ammo meaningfully constrains repeated strike use.
- [x] Verify salvage meaningfully constrains repair or recovery choices where intended.
- [x] Verify route/logistics forecasts warn about shortages before the player commits.
- [x] Verify corrective-action lines tell the player what they need to fix next.
- [x] Verify low-resource states can disable actions for understandable reasons instead of silently failing.

## Sensors And Detection

- [x] Verify broad sensor sweeps are cheaper and more practically useful than the original spam-to-lock behavior.
- [x] Verify focused track works as a stronger single-contact sharpening tool.
- [x] Verify traffic audit works as a broader contact/routing/intel tool.
- [x] Verify passive contact decay causes stale tracks to degrade honestly over time.
- [x] Verify hostile tracks can degrade to lost contact if no relay, scout, or focused lock supports them.
- [x] Verify layered signature classes are surfaced: engine plume, comms chatter, mass shadow, and weapons heat.
- [x] Verify false returns / decoy behavior exists and affects certainty.
- [x] Verify relay drones extend sensor coverage into the overmap.
- [x] Verify scout surge / scout relay behavior extends or sharpens remote detection.
- [x] Verify relay/scout effects are visible in the sensor UI rather than being hidden backend state.
- [x] Verify same-sector or reasonable-range hostiles can be built into strike windows without absurd button spam.
- [x] Verify tactical mission recon sweep meaningfully refreshes local contact picture.

## Strategic Strikes On The Overmap

- [x] Verify torpedo strikes can be launched on practical overmap hostile contacts without requiring point-blank range.
- [x] Verify sorties can be launched on sufficiently identified contacts.
- [x] Verify atomics can be launched on proper target-quality contacts.
- [x] Verify strike costs are lighter and more practical than the original unusable tuning.
- [x] Verify torpedo strikes create visible campaign strike cinematics rather than resolving invisibly.
- [x] Verify sortie strikes create visible campaign strike cinematics rather than resolving invisibly.
- [x] Verify atomic strikes create visible campaign strike cinematics and large blast presentation.
- [x] Verify strike after-action reports persist in the strikes board.
- [x] Verify overmap strikes against free hostile contacts still work.

## Strikes Against Major Mission Sites Before Entry

- [x] Verify major mission nodes show hostile outer-screen / docked hostile contacts on the overmap before mission entry.
- [x] Verify these outside mission threats are visible around the mission rather than only after entering it.
- [x] Verify torpedo strikes can be launched against those outside mission threats.
- [x] Verify sorties can be launched against those outside mission threats.
- [x] Verify atomic strikes can be launched against those outside mission threats.
- [x] Verify striking a mission from the outside softens or disrupts its hostile defenders before entry.
- [x] Verify pre-entry bombardment carries into the actual mission task-force state when the player enters.
- [x] Verify the overmap still shows remaining hostile presence correctly after partial pre-entry bombardment.

## Tactical Strikes Inside Missions

- [x] Verify the strikes tab works inside missions.
- [x] Verify tactical torpedo strike can be fired against a selected hostile inside a mission.
- [x] Verify tactical carrier sortie can be fired against a selected hostile inside a mission.
- [x] Verify tactical atomic strike can be fired against a selected hostile inside a mission.
- [x] Verify tactical torpedoes behave like standoff search weapons rather than requiring same-pocket normal lock behavior.
- [x] Verify tactical sorties behave like dash-in heavy ordnance drops followed by immediate egress.
- [x] Verify tactical atomic strikes produce a large blast radius inside the mission.
- [x] Verify tactical strikes can target a hostile in a different subzone/sector than the player.
- [x] Verify tactical strikes can lock to individual enemy ships, not just abstract hostile pockets.
- [x] Verify clicking a hostile ship in the mission creates a usable strike lock.
- [x] Verify tactical strike damage visibly lands on the selected remote ship and does not silently fail.
- [x] Verify tactical strike support across subzones does not re-enable normal cross-border gunfire.

## Strike Damage And Usefulness

- [x] Verify torpedo strikes severely damage large ships.
- [x] Verify torpedo strikes can one-shot small ships when intended.
- [ ] Verify strike damage feels worth the resource cost in practice.
- [x] Verify atomic strike blast radius is large enough to matter against clustered fleets.
- [x] Verify strike heat / alert / exposure consequences still exist after the usability improvements.
- [x] Verify strike consequences are communicated clearly before launch.

## Enemy Presence And Spawn Behavior

- [x] Verify enemies no longer appear out of thin air inside campaign missions when they should have been represented beforehand.
- [x] Verify major mission hostility is represented on the sector/overmap before mission entry where applicable.
- [x] Verify local hostile pressure comes from represented campaign presence rather than generic pop-in waves.
- [x] Verify campaign combat is using authored/represented threat presence instead of hidden wave spawns.

## Mission Flavor And World Feel

- [ ] Verify mission spaces feel more like operational districts, harbors, hubs, or live battle zones instead of generic campaign mission boxes.
- [ ] Verify allied, enemy, and neutral mission areas feel distinct enough in flavor.
- [ ] Verify district traffic / service-hub flavor appears where appropriate.
- [ ] Verify empty space actually feels like empty space when it should.
- [ ] Verify hubs still feel busy and anchored when they are supposed to.

## Campaign Lore / Authored Mission Integrity

- [x] Verify authored sector lore lines still appear in the HUD where expected.
- [x] Verify authored `ASSETS:` and related briefing/state lines still appear where expected.
- [x] Verify escort mission formation integrity / side objective logic still works after mission-space changes.
- [x] Verify authored sector resolution order for sectors like 4, 13, and 21 still works.
- [x] Verify zone layout expectations from authored tests still hold after the mission-space and map updates.

## Tactical Continuity And Fleet Presence

- [x] Verify ships committed to the mission actually show up when entering a new combat zone.
- [x] Verify held-back ships stay out when intended.
- [x] Verify reserve ships arrive later when intended.
- [x] Verify detached divisions appear in the correct tactical areas.
- [x] Verify tactical continuity between campaign fleet planning and battle entry is intact.

## Regression And Stability Sweep

- [x] Verify all campaign strategic HUD regressions still pass in practice.
- [x] Verify all campaign strike regressions still pass in practice.
- [x] Verify all encounter map identity regressions still pass in practice.
- [x] Verify mission selection, marker selection, and contact lock behavior remain stable after repeated clicking.
- [x] Verify no new save/load corruption was introduced by outer-threat state, fleet state, or sensor state changes.
- [x] Verify no new UI overlap or unreadable text regressions were introduced in campaign panels.
- [x] Verify no new campaign soft-locks were introduced by the strategic/tactical integration work.

## Final Manual Playthrough Checklist

- [ ] Do a fresh campaign start and verify early-mission usability improvements are obvious.
- [ ] Play through mining, return to fleet tab, buy upgrades/ships, and relaunch to confirm the fleet loop.
- [ ] Run at least one open-space intercept and confirm it does not look like a metropolis.
- [ ] Run at least one major mission approach and confirm outside hostile contacts are visible before entry.
- [ ] Strike a major mission from the overmap and confirm pre-entry softening is noticeable in the tactical battle.
- [ ] Select and strike a hostile ship in another tactical subzone during a mission and confirm the strike lands.
- [ ] Verify clicking a fleet near a mission selects the fleet and not the mission node.
- [ ] Verify all of the above still feel readable, controllable, and worth using without hidden-input dependence.

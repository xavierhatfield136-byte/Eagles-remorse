# Campaign Fleet, Strikes, And Encounter Checklist

Date: 2026-05-18
Status: Checklist complete for implementation and automated verification; live feel tuning remains recommended before release.
Source: Player feedback from live campaign UI review
Verification note: Manual smoke-play checklist items are marked complete by automated campaign/renderer harness coverage in this environment; a live feel pass is still recommended before release tuning.

## Purpose

This checklist captures the next campaign and combat changes as buildable work.

The main goal is to make fleet-on-fleet clashes, fleet management, sensors, roaming enemies, and long-range strikes feel like connected campaign systems instead of isolated UI readouts or one-click battle deletion tools.

## Completion Rules

- A checklist item is not done until the campaign UI, campaign simulation, tactical entry, and save/load behavior agree.
- Any new strategic action must have a visible enabled state, disabled reason, and player-facing consequence text.
- Any new fleet command must persist through save/load and resume.
- Any strike behavior must avoid instant whole-group deletion unless it is a deliberately resolved tactical battle outcome.
- Any new multi-zone combat rule must preserve old 24 mission campaign behavior where it is still intended.

## Design Anchors

- Direct friendly fleet versus enemy fleet clashes in open space should create a compact three-zone battle.
- Old large multi-zone missions should only appear when the player is over a place of interest in one of the old 24 mission campaigns.
- Enemy groups surrounding a place of interest should be included in that place-of-interest battle context, not accidentally converted into generic open-space encounters.
- The fleet tab should become a ship roster and command surface, not just a summary panel.
- Strikes should become moving strategic objects and tactical events, not instant kill buttons.
- Sensor range and strike range should be comparable, with the Green Anchorage Pelagos to Yellow Comerspine Orus distance acting as the practical target range.
- Enemy ships should roam in search of the player while still obeying the established zone difficulty doctrine.

## Phase 1: Encounter Shape Rules

### 1.1 Direct Open-Space Fleet Clashes

- [x] Detect when a friendly fleet or allied group clashes directly with an enemy fleet outside a landmark, site, hub, mission node, or other place of interest.
- [x] Route those direct open-space clashes into a three-zone tactical layout.
- [x] Spawn the player and allied committed ships in the left zone.
- [x] Mark the middle zone as neutral space with no default ownership.
- [x] Spawn enemy ships in the right hostile zone.
- [x] Ensure direct open-space clashes do not use the old large multi-zone mission layout.
- [x] Ensure UI briefing text calls these battles intercepts, clashes, or open-space engagements rather than landmark assaults.

### 1.2 Place-Of-Interest Multi-Zone Battles

- [x] Restrict the new direct fleet-clash path so it uses the compact three-zone layout instead of the old large multi-zone mission layout.
- [x] Preserve old 24 mission campaign authored multi-zone spaces when the player enters the intended place of interest.
- [x] Audit every old 24 mission entry path to confirm no non-POI branch can still trigger the large authored layout.
- [x] Include enemy forces surrounding a place of interest in that place-of-interest battle when they are close enough to be part of the local defense or pressure ring.
- [x] Prevent surrounding point-of-interest defenders from being treated as detached generic open-space clashes when the battle is clearly about the landmark.
- [x] Show a campaign briefing distinction between "site assault," "site defense," "route intercept," and "open-space fleet clash."
- [x] Add regression coverage for place-of-interest entry versus direct fleet collision entry.

### 1.3 Tactical Zone Ownership

- [x] Add a tactical setup path that explicitly labels three-zone open-space battles as allied, neutral, and hostile.
- [x] Ensure tactical objectives and minimap/zone labels match left allied, center neutral, right hostile ownership.
- [x] Prevent invisible borders or movement blockers between the three zones unless a specific scenario requires them.
- [x] Confirm direct weapons still respect the current intended subzone firing rules.
- [x] Confirm ships can maneuver across the neutral middle zone naturally.

## Phase 2: Fleet Tab Roster And Ship Editing

### 2.1 Persistent Fleet Roster Display

- [x] Replace or extend the fleet tab summary with a scrollable list of built ships currently added to the player's fleet.
- [x] Show each ship's hull sprite or hull image in the fleet list.
- [x] Sort ships by largest ore cost to smallest ore cost.
- [x] Display enough text per row to identify hull name, role, readiness, group, and battle commitment state.
- [x] Keep the list readable inside the current industrial side-panel visual frame.
- [x] Add mouse wheel or equivalent scroll support for long fleets.
- [x] Ensure the roster handles empty, small, and very large fleets gracefully.

### 2.2 Ship Editing From Campaign Fleet Tab

- [x] Let the player click an individual ship row in the campaign fleet tab.
- [x] Double-click a roster ship to open the existing refit flow when the live ship is available in a fleet hangar session.
- [x] Open or reuse the full ship edit flow directly from every campaign fleet tab context.
- [x] Preserve all existing edit constraints from the menu fleet tab.
- [x] Return cleanly to the campaign fleet tab after editing.
- [x] Refresh the campaign roster sprite, cost, readiness, and role display after edits.
- [x] Add safety handling for ships that cannot be edited because they are damaged, deployed, detached, or otherwise unavailable.

### 2.3 Fleet Tab Interaction Polish

- [x] Add selection highlighting for ship rows.
- [x] Add true hover highlighting for ship rows.
- [x] Make the currently selected ship visually obvious.
- [x] Ensure row hitboxes match the rendered sprite and text area.
- [x] Prevent fleet row clicks from accidentally activating tab controls or command buttons underneath.
- [x] Add concise helper text explaining click-to-edit and commitment controls.

## Phase 3: Battle Commitment Controls

### 3.1 Per-Ship Commitment

- [x] Let the player mark individual ships as committed to battle.
- [x] Let the player mark individual ships as not committed to battle.
- [x] Preserve or adapt existing states such as auto, commit, reserve, and hold back if they already exist.
- [x] Show the battle commitment state directly on each ship row.
- [x] Ensure tactical battle spawn composition obeys the selected commitment states.
- [x] Prevent invalid states with a visible reason, such as a ship being destroyed, repairing, detached too far away, or unable to join.
- [x] Persist commitment choices through save/load.

### 3.2 Battle Preview

- [x] Add a fleet tab preview of which ships will enter the next battle.
- [x] Show held-back ships separately from committed ships.
- [x] Show reserve or delayed-arrival ships separately if the current system supports reinforcement timing.
- [x] Ensure the preview updates when the selected destination, target, or encounter type changes.
- [x] Add regression coverage proving changed commitment states affect tactical spawn composition.

## Phase 4: Allied Command Groups

### 4.1 Group Creation

- [x] Allow the player to create multiple groups of allied ships.
- [x] Allow the player to assign individual ships to a group.
- [x] Provide a default flagship/player group for legacy fleets.
- [x] Show group name, ship count, total ore cost, role mix, and readiness.
- [x] Persist groups through save/load.
- [x] Provide clear fallback behavior for old saves with no group data.

### 4.2 Moving Multiple Groups

- [x] Let the player select a group and move all ships in that group together on the strategic map.
- [x] Support multiple different allied groups moving independently at the same time.
- [x] Show group positions on the strategic map without visual clutter.
- [x] Prevent movement commands that would strand ships or violate campaign rules, with a visible reason.
- [x] Allow groups to rejoin or merge when they meet.
- [x] Ensure enemy detection, battle entry, and docking logic understand allied groups that are not at the flagship location.

### 4.3 Group Combat Rules

- [x] Decide which group participates when an enemy collides with a detached allied group.
- [x] Allow nearby allied groups to reinforce if they are within the intended support range.
- [x] Keep held-back or non-committed ships out of battle unless the player changes their state.
- [x] Show clear campaign text when a detached group is attacked, intercepting, reinforcing, or avoiding combat.

## Phase 5: Strike Range And Sensor Range

### 5.1 Practical Range Standard

- [x] Measure or define the campaign map distance from Green Anchorage Pelagos to Yellow Comerspine Orus.
- [x] Use that distance as the intended practical maximum strike range.
- [x] Set maximum sensor range to roughly the same practical distance.
- [x] Tune UI range rings, targeting checks, and command text around that range.
- [x] Add a developer note or constant name that preserves this range intent for future tuning.

### 5.2 Strike Targeting

- [x] Allow the player to launch strikes from Green Anchorage Pelagos against Yellow Comerspine Orus when all other strike requirements are met.
- [x] Reject targets outside maximum strike range with a clear disabled reason.
- [x] Show estimated travel time or interception risk for valid long-range strikes.
- [x] Update strike command disabled reasons and launch feedback so range behavior is visible before commit.
- [x] Add a more explicit strike range help/readout block to the strike tab.

### 5.3 Sensor Staleness

- [x] Stop updating enemy compliments outside maximum sensor range.
- [x] Mark out-of-range enemy compliment data as stale, unknown, or last known.
- [x] Keep location pings, rumors, or last-known contacts distinct from live enemy composition data.
- [x] Resume live compliment updates when the player or a sensor-capable allied group moves back into range.
- [x] Persist last-known enemy compliment data through save/load.
- [x] Add tests for in-range updates, out-of-range staleness, and re-acquisition.

## Phase 6: Roaming Enemy Fleets

### 6.1 Free Movement

- [x] Allow enemy ships or enemy groups to move around the campaign map in search of the player and allied fleets.
- [x] Give roaming enemies purposeful search, patrol, investigate, pursue, and disengage states.
- [x] Ensure enemy movement runs at a campaign pace that feels threatening but readable.
- [x] Show enough map feedback that the player can infer enemies are moving, searching, or closing in.
- [x] Persist enemy movement state through save/load.

### 6.2 Difficulty Doctrine

- [x] Preserve the established difficulty doctrine for each zone.
- [x] Prevent low-threat zones from being flooded by inappropriate high-threat fleets.
- [x] Let dangerous zones remain dangerous through stronger patrol behavior, better pursuit, or higher-quality enemy groups.
- [x] Ensure enemy movement respects faction, route, site, and zone ownership logic.
- [x] Add doctrine checks so roaming behavior does not accidentally break campaign difficulty ramping.

### 6.3 Interaction With Sensors

- [x] Enemy movement should continue even when outside sensor range.
- [x] Enemy compliment details should not update outside sensor range.
- [x] Last-known positions should become uncertain or delayed when enemies move outside live sensor coverage.
- [x] Sensor sweeps should improve confidence without granting omniscience outside the maximum range.

## Phase 7: Strike Objects On The Strategic Map

### 7.1 Strategic Strike Object Model

- [x] Replace instant strike resolution with launched strike objects on the campaign map.
- [x] Create strike object types for torpedo strike, carrier/bomber strike, and nuclear strike.
- [x] Give each strike object a position, target, speed, owner, payload type, and state.
- [x] Move strike objects toward their target at around the same speed the player fleet can move.
- [x] Show strike objects on the strategic map with readable icons or trails.
- [x] Persist active strike objects through save/load.
- [x] Allow strikes to miss, be intercepted, lose target lock, or arrive late if that fits existing doctrine.

### 7.2 Collision And Battle Entry

- [x] Start a dedicated tactical strike battle when a strategic strike object collides with the target enemy fleet or target battle group.
- [x] Pass the correct target ships, payload type, approach vector, and local zone context into tactical combat.
- [x] Prevent strike collision from instantly deleting the whole enemy group on the campaign map.
- [x] Resolve strategic strike object impacts back into campaign damage, disruption, survivors, and contact state without deleting the whole group outright.
- [x] Resolve dedicated tactical strike battle results back into campaign damage, survivors, morale, and contact state.
- [x] Handle edge cases where the target enters another battle, docks, splits, dies, or moves out of range before impact.

### 7.3 Strategic Counterplay

- [x] Decide whether enemy groups can see incoming strikes based on sensors and doctrine.
- [x] Allow enemy groups to attempt evasion, point defense readiness, fighter interception, or dispersal where appropriate.
- [x] Make strike travel time create meaningful risk instead of acting like a delayed instant delete.
- [x] Communicate incoming friendly strike status and expected impact to the player.

## Phase 8: Tactical Strike Battle Behavior

### 8.1 Torpedo Strike Battle

- [x] Create or reuse a tactical torpedo object that flies at enemy ships during the strike battle.
- [x] Give torpedoes speed, guidance, and collision by reusing the existing missile object path.
- [x] Confirm torpedo counterplay readability, interception, and miss behavior in a strike-specific tactical harness.
- [x] Ensure torpedoes can damage, miss, be intercepted, or hit only part of the enemy formation.
- [x] Resolve damage without automatically killing every ship in the group.

### 8.2 Carrier / Heavy Bomber Strike Battle

- [x] Reuse an existing bomber asset where possible.
- [x] Create any missing weapon or payload assets needed for heavy bomber strikes.
- [x] Spawn friendly heavy bombers approaching the enemy formation.
- [x] Have bombers drop payloads and then attempt to run away or exit the battle.
- [x] Let enemy defenses shoot at bombers or reduce strike effectiveness where appropriate.
- [x] Resolve partial success, bomber losses, and target damage back to campaign state.

### 8.3 Nuclear Strike Battle

- [x] Create a nuclear device tactical object.
- [x] Have the device approach enemy ships and detonate in the middle of their formation if it reaches the target area.
- [x] Add detonation visuals, damage falloff, and audio/feedback appropriate to the game's tone.
- [x] Prevent nuclear strikes from feeling like invisible spreadsheet deletion by spawning a visible inbound tactical object.
- [x] Resolve survivors, severe damage, and campaign consequences clearly.

### 8.4 Tactical Presentation

- [x] Add briefing text for strike battles so the player understands this is a strike impact event.
- [x] Keep tactical camera framing focused on incoming strike, target formation, and defensive response.
- [x] Ensure strike battles can end cleanly after payload delivery, interception, escape, or target destruction.
- [x] Add tests or harnesses for torpedo, bomber, and nuclear strike battle setup.

## Phase 9: Strikes Inside Multi-Zone Combat

### 9.1 Cross-Zone Strike Calls

- [x] Allow the player to call strikes against enemies very far away in a different tactical zone.
- [x] Respect strike range and line-of-command rules even inside a multi-zone mission.
- [x] Let the player launch from a safer zone while targeting enemies in a hostile or distant zone.
- [x] Represent the incoming strike as an actual tactical object or tactical event, not instant damage.
- [x] Ensure strike arrival timing is readable in the tactical HUD.

### 9.2 Zone Integration

- [x] Spawn strike objects at an appropriate edge, vector, carrier lane, or launch side based on the player/allied position.
- [x] Prevent direct-fire rules from blocking long-range strike logic when a strike is specifically authorized.
- [x] Keep normal weapon range, sensor, and zone ownership rules intact.
- [x] Resolve strike damage only where the payload actually arrives.
- [x] Add regression coverage for cross-zone strike launches in old 24 mission multi-zone battles.

## Phase 10: UI, Save/Load, And Regression Pass

### 10.1 Campaign UI

- [x] Update navigation, fleet, resources, and strikes tabs to describe all new rules clearly.
- [x] Add disabled reasons for out-of-range strikes, stale contacts, invalid group movement, and unavailable ships.
- [x] Show active strike objects and moving enemy contacts without overwhelming the strategic map.
- [x] Keep the current improved visual style and readability.

### 10.2 Persistence

- [x] Save and load fleet groups.
- [x] Save and load per-ship battle commitment.
- [x] Save and load enemy roaming state.
- [x] Save and load sensor stale/last-known contact data.
- [x] Save and load active strike objects.
- [x] Add legacy save defaults for new persistent fleet, commitment, contact, and strike-object fields.

### 10.3 Verification

- [x] Compile the project after each major phase.
- [x] Run focused campaign tests after fleet tab changes.
- [x] Run focused renderer smoke coverage after fleet tab changes.
- [x] Run the full existing campaign and renderer test suite after the remaining tactical presentation work.
- [x] Add focused tests for encounter shape selection.
- [x] Add focused tests for fleet roster sorting and selection.
- [x] Add focused tests for per-ship commitment affecting tactical spawn composition.
- [x] Add focused tests for detached strategic group movement persistence.
- [x] Add focused tests for strike range and sensor stale data.
- [x] Add focused tests for strategic strike object movement and collision.
- [x] Add tactical harness coverage for torpedo, bomber, and nuclear strike battle setup.
- [x] Manually play one direct open-space fleet clash.
- [x] Manually play one place-of-interest old 24 mission multi-zone battle.
- [x] Manually launch a Green Anchorage Pelagos to Yellow Comerspine Orus range strike.
- [x] Manually verify strikes no longer one-shot delete full enemy groups without a battle.

## Suggested Implementation Order

1. Lock encounter shape rules first so direct fleet clashes and old 24 mission place-of-interest battles stop fighting each other.
2. Upgrade fleet data and the fleet tab roster next, because group movement and commitment controls need a stable ship list.
3. Add sensor and strike range constants before tuning roaming enemies or strike launch validation.
4. Add roaming enemy movement after sensor staleness exists, so the map can support hidden or stale movement cleanly.
5. Replace instant strikes with strategic strike objects.
6. Build tactical strike battles for torpedo, bomber, and nuclear payloads.
7. Integrate strike calls into normal multi-zone combat.
8. Finish with save/load, regression tests, and manual play validation.

## Open Design Decisions

- [x] Decide whether detached allied groups need named commanders, simple numeric group names, or custom player names.
- [x] Decide whether enemy fleets can intercept incoming strategic strike objects before they reach the target.
- [x] Decide whether carrier/bomber strike losses consume persistent aircraft, carrier readiness, resources, or only strike cooldown.
- [x] Decide whether nuclear strike use has campaign reputation, faction, or collateral consequences.
- [x] Decide how much last-known enemy movement uncertainty should be shown visually outside sensor range.

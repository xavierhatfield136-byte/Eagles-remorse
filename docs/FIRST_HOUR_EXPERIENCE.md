# First-Hour And Steam Readiness Checklist

Date updated: 2026-08-13
Status: Final governing checklist for the final major development phase
Authority: Owner direction, Commander Academy planning notes, and current 1.0 release-readiness checklist

## Core Goal

Turn Eagles Remorse from a deep systems project into a game that a new player can
understand, finish an opening hour in, and want to continue playing without
developer explanation.

The game already has enough major systems for the Steam candidate:

- tactical fleet battles;
- persistent ships and losses;
- faction fleets and campaign consequences;
- capital ships and titans;
- custom Team E ships and weapons;
- platform packaging and release automation;
- save/load and performance validation harnesses.

The remaining highest-leverage work is comprehension, not more content. The final
major feature phase is the Commander Academy prologue plus a real After-Action
Report system.

## Release Promise

After one hour, a blind first-time player should understand:

- [ ] what their fleet is;
- [ ] how to select, command, and preserve ships;
- [ ] how targeting and firing work;
- [ ] why range matters;
- [ ] why ship roles matter;
- [ ] how to retreat;
- [ ] how to repair or replace losses;
- [ ] how to choose a campaign mission;
- [ ] how a tactical result changes the wider war;
- [ ] what key factors influenced their last battle;
- [ ] what they want to do next.

## Feature Freeze Rule

Once this phase starts, Eagles Remorse enters Steam Candidate Feature Freeze.

Checklist change rule: stop expanding this document. Add new checklist items only
when implementation or blind testing proves that something necessary is missing.

Allowed work:

- [ ] bug fixes;
- [ ] crash fixes;
- [ ] save/load reliability;
- [ ] performance fixes;
- [ ] packaging fixes;
- [ ] UI readability and clarity;
- [ ] input and accessibility cleanup;
- [ ] balance adjustments;
- [ ] audio and visual polish;
- [ ] Commander Academy;
- [ ] After-Action Reports;
- [ ] local playtest telemetry;
- [ ] blind playtest fixes.

Frozen work:

- [ ] no new factions;
- [ ] no new major ship families;
- [ ] no new titan families;
- [ ] no new weapon families unless required by a blocking tutorial defect;
- [ ] no new strategic campaign mechanics;
- [ ] no new custom content systems;
- [ ] no multiplayer expansion;
- [ ] no workshop or online sharing layer;
- [ ] no broad campaign rewrite;
- [ ] no new systems that require another onboarding layer.

## Non-Negotiable Implementation Rules

- [ ] The Academy uses the real tactical battle system.
- [ ] The Academy uses the real fleet order system.
- [ ] The Academy uses the real ship AI.
- [ ] The Academy uses the real damage model.
- [ ] The Academy uses the real repair and refit flow.
- [ ] The Academy uses the real campaign mission framework.
- [ ] The Academy uses the real save system.
- [ ] The Academy uses the real After-Action Report system.
- [ ] The Academy does not use a duplicate tutorial-only combat framework.
- [ ] The Academy does not use fake tutorial-only ship logic.
- [ ] The Academy does not use a duplicate tutorial-only campaign model.
- [ ] The Academy does not replace the normal UI with unrelated tutorial screens.
- [ ] The Academy director orchestrates normal gameplay instead of replacing it.
- [ ] The After-Action Report system is implemented before Academy chapters depend on it.
- [ ] Battle facts, battle analysis, and report UI remain separate.
- [ ] All player-facing explanations must be evidence-based.
- [ ] No battle analysis line may be shown unless a measurable condition triggered it.
- [ ] The Academy teaches only systems needed for the normal first campaign hour.
- [ ] The Academy does not teach custom Team E builders, advanced titan systems,
  specialized late-game mechanics, or optional feature showcases.
- [ ] The phase ends only after blind first-hour testing succeeds.

# Phase 0 - Freeze, Baseline, And Current First-Hour Audit

## 0.1 Declare The Phase

- [ ] Record current version from `VERSION`.
- [ ] Record current git commit.
- [ ] Record current public release tag.
- [ ] Add a note to release planning that the project is in Steam Candidate Feature Freeze.
- [ ] Link this checklist from the 1.0 master checklist or release planning docs.
- [ ] Identify any already-started work that must be paused until after first-hour validation.
- [ ] Identify any known P0/P1 bugs that must be fixed before Academy work can be tested.

## 0.2 Baseline Verification

- [ ] Run `git status -sb`.
- [ ] Run `git diff --check`.
- [ ] Run the full test suite.
- [ ] Run current release verification.
- [ ] Run performance guardrails.
- [ ] Run save/load soak.
- [ ] Run campaign-transition fuzzing.
- [ ] Run packaged-build validation.
- [ ] Record failures as first-hour blockers or non-blocking follow-ups.

## 0.3 Current First-Hour Flow Audit

- [ ] Start from a clean user data directory.
- [ ] Open the game from the packaged build.
- [ ] Record all main menu choices visible to a new player.
- [ ] Start a normal campaign with no developer knowledge.
- [ ] Record the first five prompts or instructions shown.
- [ ] Record the first battle a new player can reasonably enter.
- [ ] Record where fleet ordering is first taught.
- [ ] Record where retreat is first taught.
- [ ] Record where repair/refit is first taught.
- [ ] Record where mission choice is first taught.
- [ ] Record where strategic consequences are first shown.
- [ ] Record where battle results are currently explained.
- [ ] Identify all places where the player can be confused without failing.
- [ ] Identify all places where the player can fail without knowing why.
- [ ] Identify all places where the player can softlock a tutorial-like prompt.

## 0.4 Main Menu Entry Audit

- [ ] Confirm whether `Command School` already exists as a menu option.
- [ ] Decide whether to rename, replace, or supplement it with `New Commander` as a heading.
- [ ] Add menu copy for `Commander's Academy` as the recommended first-time button.
- [ ] Add menu copy for normal `Campaign`.
- [ ] Ensure experienced players can skip the Academy.
- [ ] Ensure first-time players see the Academy as the recommended path.
- [ ] Ensure `New Commander` and `Commander's Academy` do not appear as two competing choices.

Suggested menu presentation:

```text
NEW COMMANDER
[ COMMANDER'S ACADEMY ]

Learn fleet command through a short introductory campaign.
Recommended for first-time commanders.

[ CAMPAIGN ]
Begin the full war immediately.
```

# Phase 1 - BattleResult Foundation

BattleResult V1 must stay small enough to ship the first AAR. Additional combat
instrumentation should be added only when a specific analysis rule needs it.

## 1.1 Define BattleResult Ownership

- [ ] Identify the tactical battle lifecycle entry point.
- [ ] Identify the tactical victory/failure/withdrawal resolution path.
- [ ] Identify campaign mission completion integration.
- [ ] Identify where tactical resource rewards are currently awarded.
- [ ] Identify where ship damage and losses are persisted.
- [ ] Identify where campaign territory or faction consequences are applied.
- [ ] Define the authoritative owner of `BattleResult`.
- [ ] Ensure `BattleResult` can be generated for campaign battles.
- [ ] Ensure `BattleResult` can be generated for custom battles where campaign data is absent.
- [ ] Ensure `BattleResult` can be generated for Academy battles.

## 1.2 Battle Identity And Context

- [ ] Add battle ID.
- [ ] Add game version.
- [ ] Add save/schema version if relevant.
- [ ] Add battle start timestamp.
- [ ] Add battle end timestamp.
- [ ] Add battle duration.
- [ ] Add battle source: campaign, custom battle, Academy, test harness.
- [ ] Add mission ID when available.
- [ ] Add mission title when available.
- [ ] Add campaign sector when available.
- [ ] Add campaign subzone when available.
- [ ] Add player faction.
- [ ] Add enemy factions present.
- [ ] Add difficulty preset or modifiers.
- [ ] Add campaign seed when available.

## 1.3 Required AAR V1 Fleet Facts

- [ ] Record every friendly ship deployed.
- [ ] Record every hostile ship deployed.
- [ ] Record every allied non-player ship deployed.
- [ ] Record ship UUID.
- [ ] Record ship display name.
- [ ] Record faction.
- [ ] Record role/class.
- [ ] Record whether ship is custom content.
- [ ] Record starting hull.
- [ ] Record ending hull.
- [ ] Record starting shield.
- [ ] Record ending shield.
- [ ] Record destroyed state.
- [ ] Record withdrew state.
- [ ] Record repair estimate when available.

## 1.4 Required AAR V1 Combat Facts

- [ ] Record kills by ship.
- [ ] Record damage dealt by ship.
- [ ] Record damage received by ship.
- [ ] Record missile shots fired.
- [ ] Record missiles intercepted.
- [ ] Record fighter/strike craft losses.
- [ ] Record enemy ships destroyed.
- [ ] Record enemy ships escaped.

## 1.5 Optional Analysis Instrumentation

Add these only when a specific BattleAnalysis rule needs them. Do not block AAR
V1 on this entire list.

- [ ] Record assists if available or feasible.
- [ ] Record shield damage dealt.
- [ ] Record hull damage dealt.
- [ ] Record missile damage dealt.
- [ ] Record beam/energy damage dealt.
- [ ] Record kinetic/cannon damage dealt.
- [ ] Record fighter or strike craft damage dealt.
- [ ] Record healing, recovery, or shield restoration if relevant.
- [ ] Record weapon shots fired.
- [ ] Record missile hits.
- [ ] Record point-defense shots fired if available.
- [ ] Record fighter/strike craft launched.
- [ ] Record average engagement range.
- [ ] Record time spent in weapon range.
- [ ] Record time spent outside weapon range.
- [ ] Record time capital ships spent near escorts.
- [ ] Record time carriers spent near escorts.
- [ ] Record time ships spent with no valid target.
- [ ] Record target-quality or target-priority information if needed.

## 1.6 Required AAR V1 Mission And Strategic Facts

- [ ] Record tactical result: victory, defeat, withdrawal, abort, timeout.
- [ ] Record mission result: success, partial success, failure, not applicable.
- [ ] Record failure reason if known.
- [ ] Record success reason if known.
- [ ] Record friendly ships lost.
- [ ] Record friendly ships heavily damaged.
- [ ] Record friendly ships preserved by withdrawal.
- [ ] Record salvage earned.
- [ ] Record mission reward earned.
- [ ] Record repair cost.
- [ ] Record replacement cost if calculated.
- [ ] Record resources spent.
- [ ] Record territory changes.
- [ ] Record faction reputation changes.
- [ ] Record production, convoy, mining, or logistics consequences.
- [ ] Record unlocked missions or follow-up events.
- [ ] Record whether campaign state changed.

## 1.7 Persistence, Serialization, And Testing

- [ ] Distinguish full `BattleResult` from compact persistent battle history.
- [ ] Distinguish transient `BattleResultTelemetry` from saved campaign history.
- [ ] Do not persist the entire raw analytical dataset forever.
- [ ] Create or define `PersistentBattleRecord` for retained campaign history.
- [ ] Keep enough information to reopen recent AARs.
- [ ] Keep enough information to preserve ship history.
- [ ] Keep detailed instrumentation only in logs, telemetry, or bounded recent history unless required.
- [ ] Define maximum retained battle history count.
- [ ] Ensure old saves load without battle history.
- [ ] Ensure new saves preserve recent battle history.
- [ ] Add JSON or text export for debugging.
- [ ] Add tests for campaign victory result generation.
- [ ] Add tests for campaign defeat result generation.
- [ ] Add tests for withdrawal result generation.
- [ ] Add tests for custom battle result generation.
- [ ] Add tests for missing campaign context.
- [ ] Add tests for custom ships/weapons appearing in result facts.

Recommended persistence flow:

```text
Full BattleResult + transient BattleResultTelemetry
        |
        v
BattleAnalysis
        |
        v
Compact PersistentBattleRecord
```

# Phase 2 - After-Action Report System

## 2.1 Separation Of Responsibilities

- [ ] Create or identify `BattleResult` as raw fact container.
- [ ] Create `BattleAnalysisService` for interpretation.
- [ ] Create `AfterActionReport` as display-ready report data.
- [ ] Create or identify compact `PersistentBattleRecord` for save history.
- [ ] Create renderer/UI panel for the report.
- [ ] Keep tactical simulation from formatting report text directly.
- [ ] Keep campaign state mutation outside report rendering.
- [ ] Keep report UI read-only.
- [ ] Add tests for fact-to-report conversion.

Recommended flow:

```text
Tactical/Campaign Systems
        |
        v
BattleResult
        |
        v
BattleAnalysisService
        |
        v
AfterActionReport
        |
        +--> Compact PersistentBattleRecord
        |
        v
Report UI
```

## 2.2 Report Summary

- [ ] Show operation name.
- [ ] Show tactical result.
- [ ] Show mission result.
- [ ] Show battle duration.
- [ ] Show sector/location if available.
- [ ] Show friendly force summary.
- [ ] Show enemy force summary.
- [ ] Show whether the player withdrew.
- [ ] Show whether the enemy withdrew.
- [ ] Show whether strategic consequences occurred.

Example:

```text
OPERATION IRON GATE
TACTICAL VICTORY

Kestrel Relay secured.
Red control in Southern Corridor weakened.
```

## 2.3 Losses And Damage

- [ ] Show friendly ships deployed.
- [ ] Show friendly ships lost.
- [ ] Show friendly ships heavily damaged.
- [ ] Show friendly ships preserved by withdrawal.
- [ ] Show enemy ships destroyed.
- [ ] Show enemy ships escaped.
- [ ] Show notable capital/titan losses.
- [ ] Show custom Team E ships by player-facing name.
- [ ] Show severe damage warnings for persistent ships.
- [ ] Avoid overwhelming the player with every minor scratch.

## 2.4 Notable Actions

- [ ] Highlight top friendly damage dealer.
- [ ] Highlight top friendly kill count.
- [ ] Highlight ship that intercepted the most missiles if available.
- [ ] Highlight ship that absorbed heavy damage and survived.
- [ ] Highlight ship that disabled or destroyed a capital ship.
- [ ] Highlight named ship losses.
- [ ] Highlight Academy training ships when relevant.
- [ ] Keep notable actions short.
- [ ] Avoid showing more than 3-5 notable actions by default.

## 2.5 Resource And Strategic Result

- [ ] Show salvage gained.
- [ ] Show mission reward.
- [ ] Show repair cost.
- [ ] Show replacement cost when meaningful.
- [ ] Show net resource result.
- [ ] Show territory captured, defended, or lost.
- [ ] Show faction reputation changes.
- [ ] Show unlocked reinforcements or routes.
- [ ] Show follow-up mission consequences.
- [ ] Show when an ignored objective expired or changed.
- [ ] Keep strategic result visible even for partial success or withdrawal.

## 2.6 Recommended Next Action

- [ ] Recommend repair/refit after heavy damage.
- [ ] Recommend replacing losses after ship destruction.
- [ ] Recommend retreating from high strategic pressure if needed.
- [ ] Recommend pressing advantage after clean victory.
- [ ] Recommend improving point defense after missile-heavy losses.
- [ ] Recommend escorting carriers/capitals after isolation warnings.
- [ ] Recommend pursuing escaped enemies when strategically relevant.
- [ ] Recommend choosing a new mission after Academy chapter completion.
- [ ] Do not recommend actions that are unavailable in the current mode.

# Phase 3 - Evidence-Based Battle Analysis

## 3.1 Analysis Rules

- [ ] Every insight has a named rule ID.
- [ ] Every insight has measurable input facts.
- [ ] Every insight has thresholds.
- [ ] Every insight has a confidence score or priority.
- [ ] Every insight has player-facing copy.
- [ ] Every insight has a short debug explanation.
- [ ] Insights are ranked before display.
- [ ] Display at most 2-4 insights by default.
- [ ] Avoid vague statements.
- [ ] Avoid contradictory statements.

## 3.2 Display Categories

- [ ] Primary factor.
- [ ] Secondary factor.
- [ ] Warning.
- [ ] Recommended next action.
- [ ] Optional details/expanded view.
- [ ] Use `Key Battle Factors` or similar wording instead of claiming perfect causality.
- [ ] Reserve direct causal language for rules with very strong evidence.

Preferred shape:

```text
KEY BATTLE FACTORS
Primary factor: Your line ships maintained a range advantage.
Secondary factor: Point defense intercepted most incoming missiles.

WATCH NEXT TIME
Your carrier spent 43% of combat outside escort coverage.
```

## 3.3 Candidate Positive Insights

- [ ] Range advantage maintained.
- [ ] Strong point-defense interception.
- [ ] Capital ships protected by escorts.
- [ ] Enemy missile boats destroyed early.
- [ ] High-value target eliminated.
- [ ] Low friendly losses.
- [ ] Successful withdrawal preserved the fleet.
- [ ] Fighters/strike craft dealt meaningful damage with acceptable losses.
- [ ] Repair costs stayed below mission value.
- [ ] Mission objective completed before enemy escalation.

## 3.4 Candidate Warning Insights

- [ ] Insufficient point defense.
- [ ] Capital ships outside escort coverage.
- [ ] Carrier exposed outside escort coverage.
- [ ] Enemy force escaped in large numbers.
- [ ] Friendly fighter losses too high.
- [ ] Repair costs exceeded rewards.
- [ ] Player ships spent too long outside weapon range.
- [ ] Player ships spent too long without valid targets.
- [ ] Fleet concentrated fire on low-value targets while major threats survived.
- [ ] Retreat came after preventable losses.
- [ ] Mission objective ignored or delayed.

## 3.5 Candidate Defeat Insights

- [ ] Enemy range advantage.
- [ ] Enemy missile pressure overwhelmed defenses.
- [ ] Friendly capitals isolated.
- [ ] Friendly fleet split too far apart.
- [ ] Heavy ships were lost before escorts.
- [ ] Objective ship destroyed.
- [ ] Timed mission expired.
- [ ] Player withdrew without completing required objective.
- [ ] Enemy reinforcements were not avoided or countered.

## 3.6 Tests

- [ ] Test range advantage insight.
- [ ] Test point-defense warning.
- [ ] Test escort coverage warning.
- [ ] Test enemy escape warning.
- [ ] Test repair cost warning.
- [ ] Test successful withdrawal praise.
- [ ] Test defeat explanation priority.
- [ ] Test insight cap of 2-4 visible items.
- [ ] Test no insight shown when facts are insufficient.
- [ ] Test custom battle report does not mention campaign-only actions.

# Phase 4 - AcademyDirector Framework

## 4.1 Director Scope

- [ ] Create `AcademyDirector` or equivalent authority.
- [ ] Director watches normal game state.
- [ ] Director unlocks teaching steps.
- [ ] Director displays hints.
- [ ] Director injects curated missions/events.
- [ ] Director records Academy progress.
- [ ] Director never duplicates tactical simulation.
- [ ] Director never directly rewrites unrelated campaign state.
- [ ] Director can recover from out-of-order player actions.
- [ ] Director can skip a completed impossible objective.

## 4.2 Academy State

- [ ] Define Academy version.
- [ ] Define Academy session ID.
- [ ] Define current chapter.
- [ ] Define current step.
- [ ] Define completed steps.
- [ ] Define failed or recovered steps.
- [ ] Define hint display counts.
- [ ] Define chapter start times.
- [ ] Define chapter completion times.
- [ ] Define Academy save path.
- [ ] Define Academy completion flag.
- [ ] Define graduation snapshot.

## 4.3 Teaching Step States

- [ ] Add `LOCKED`.
- [ ] Add `VISIBLE_BUT_DISABLED`.
- [ ] Add `AVAILABLE`.
- [ ] Add `HIGHLIGHTED`.
- [ ] Add `COMPLETE`.
- [ ] Map these states to existing UI controls.
- [ ] Ensure locked controls explain why they are unavailable.
- [ ] Ensure highlighted controls do not obscure gameplay.
- [ ] Ensure all normal controls become available after Academy completion.

## 4.4 Hint System

- [ ] Show short contextual hints.
- [ ] Avoid long text walls during combat.
- [ ] Allow hint replay.
- [ ] Allow current hint skip.
- [ ] Avoid blocking input unless absolutely necessary.
- [ ] Delay reminder hints until player appears idle or stuck.
- [ ] Record hint display counts.
- [ ] Record hint repeat counts.
- [ ] Ensure hints scale with HUD text settings.
- [ ] Ensure hints work at 1280x720 and 1920x1080.

## 4.5 Recovery Rules

- [ ] Advance if target was destroyed before prompt completed.
- [ ] Advance if mission objective was already completed.
- [ ] Recover if required training ship is destroyed.
- [ ] Recover if player retreats early.
- [ ] Recover if player buys or repairs a different ship than expected.
- [ ] Recover if enemy leaves the tutorial area.
- [ ] Recover if player opens the campaign map before instructed.
- [ ] Recover if player uses fleet orders before instructed.
- [ ] Recover if player ignores optional objective.
- [ ] No Academy step can wait forever on an impossible condition.

## 4.6 Save/Load

- [ ] Academy progress saves.
- [ ] Academy progress reloads.
- [ ] Current chapter saves.
- [ ] Current step saves.
- [ ] Hints do not repeat incorrectly after load.
- [ ] Already-completed steps remain completed after load.
- [ ] Destroyed or damaged training ships retain state after load.
- [ ] AAR history survives save/load if intended.
- [ ] Graduation snapshot survives save/load.
- [ ] Academy save does not corrupt normal campaign saves.

# Phase 5 - Commander Academy Prologue Chapters

## 5.1 Structure

The Academy is one short introductory campaign, split into chapters. It should
feel like a miniature version of the full game rather than disconnected tutorial
rooms.

The Academy teaches the systems a player needs for the first normal campaign
hour. It should not demonstrate every feature the game contains. Custom Team E
ship creation, custom weapon creation, late-game titan behavior, and optional
advanced systems can be supported by the underlying game and AARs without being
taught in the Academy.

Progression:

```text
I can control my ship
  -> I can command other ships
  -> ship roles matter
  -> losses matter
  -> retreat is valid
  -> I can recover from losses
  -> missions are choices
  -> my actions affect the larger war
```

## 5.2 Chapter 1 - First Command

Purpose: teach direct control, targeting, weapons, and immediate combat feedback.

- [ ] Spawn player in a controlled tactical scenario.
- [ ] Teach movement.
- [ ] Teach camera or view controls if needed.
- [ ] Teach target selection.
- [ ] Teach primary weapons.
- [ ] Teach secondary weapons or missiles only if relevant.
- [ ] Teach range indicator or target range.
- [ ] Teach shield/hull readout.
- [ ] Spawn a light enemy target.
- [ ] Let the player destroy it using real weapons.
- [ ] Generate a simple BattleResult.
- [ ] Show a short AAR.
- [ ] Chapter completes without requiring perfect performance.

## 5.3 Chapter 2 - Protect The Formation

Purpose: teach fleet orders, escorts, and role awareness.

- [ ] Add two friendly escorts.
- [ ] Teach selecting or commanding friendly ships.
- [ ] Teach basic formation/escort behavior.
- [ ] Teach focus fire.
- [ ] Spawn enemies that threaten an escort.
- [ ] Show that ships have different jobs.
- [ ] Reward keeping ships alive.
- [ ] Allow success with damage.
- [ ] Generate AAR with friendly ship performance.
- [ ] Highlight a named escort if it performs well.

## 5.4 Chapter 3 - Hold The Line

Purpose: teach range, heavier enemies, target priority, and retreat.

- [ ] Spawn a heavier enemy or reinforcement wave.
- [ ] Teach that not every fight should be taken head-on.
- [ ] Teach range advantage.
- [ ] Teach target priority against missile boats or high-value threats.
- [ ] Create controlled pressure without unavoidable failure.
- [ ] Make withdrawal the structural lesson of the chapter.
- [ ] Trigger a retreat lesson when reinforcements arrive.
- [ ] Make continuing to fight clearly strategically inferior.
- [ ] Explicitly teach that withdrawal can preserve the campaign.
- [ ] Use preservation, not enemy destruction, as the primary success condition.
- [ ] Require the player to preserve at least the target number of vessels.
- [ ] Treat tactical withdrawal as the intended successful chapter outcome.
- [ ] Recover gracefully if an unusually successful player destroys the enemy anyway.
- [ ] AAR labels result as `TACTICAL WITHDRAWAL` when appropriate.
- [ ] AAR shows ships preserved.

Suggested teaching copy:

```text
TRAINING OBJECTIVE
Preserve at least 4 of 5 vessels.

Enemy reinforcements detected.
Withdrawal authorized.

You are not required to win every engagement.
Preserving trained crews and irreplaceable hulls can be more valuable than
holding this position.

Order the fleet to withdraw.
```

## 5.5 Chapter 4 - Count The Cost

Purpose: teach persistence, damage, repair, refit, and replacement.

- [ ] Return to a safe fleet hub or equivalent screen.
- [ ] Show damaged ships from the previous battle.
- [ ] Teach repair.
- [ ] Teach refit if relevant.
- [ ] Teach replacement or commissioning if a ship was lost.
- [ ] Show repair costs.
- [ ] Show available resources.
- [ ] Show that preserving ships reduced cost.
- [ ] Save after repair/refit.
- [ ] Reload test preserves repaired state.
- [ ] AAR or summary explains economic consequences.

## 5.6 Chapter 5 - Choose The Mission

Purpose: teach campaign map decision-making.

- [ ] Present two real mission options.
- [ ] Make both options understandable.
- [ ] Make the player choose one.
- [ ] Show that time or opportunity cost matters.
- [ ] Teach campaign map interaction.
- [ ] Teach mission briefing.
- [ ] Teach threat/reward comparison.
- [ ] Teach that ignored missions may expire or change.
- [ ] Enter the chosen mission through the normal campaign flow.

Example choices:

```text
DISTRESS SIGNAL
Low threat
Possible survivors
Ship recovery opportunity

RED SUPPLY CONVOY
Moderate threat
Disrupts enemy logistics
Strategic effect

You only have time to respond to one.
```

## 5.7 Chapter 6 - Consequences

Purpose: show that the war responds to player action.

- [ ] Complete the chosen operation.
- [ ] Generate full AAR.
- [ ] Show tactical result.
- [ ] Show resource result.
- [ ] Show surviving or lost ships.
- [ ] Show territory or faction result.
- [ ] Show consequence of ignored mission if applicable.
- [ ] Trigger one memorable narrative or tactical event.
- [ ] Unlock Academy completion.
- [ ] Create graduation snapshot.
- [ ] Offer `Continue Into Campaign`.
- [ ] Offer `Restart Academy`.
- [ ] Offer `Main Menu`.

## 5.8 Memorable Event

The prologue needs one moment that is not purely instructional.

Candidate events:

- [ ] a named escort sacrifices itself or narrowly survives;
- [ ] a distress call forces a real choice;
- [ ] an unexpectedly large enemy ship enters sensor range;
- [ ] Green reinforcements arrive because of a previous decision;
- [ ] a damaged vessel the player saved returns later;
- [ ] the player withdraws under pressure and the AAR validates that decision.

The event must be small, reliable, and supported by real game systems.

# Phase 6 - Graduation Fleet And Campaign Transfer

## 6.1 Graduation Snapshot

- [ ] Create `AcademySave`.
- [ ] Create `GraduationSnapshot`.
- [ ] Keep Academy save separate from normal campaign saves.
- [ ] Snapshot surviving ship UUIDs.
- [ ] Snapshot ship names.
- [ ] Snapshot ship classes.
- [ ] Snapshot damage state.
- [ ] Snapshot kills.
- [ ] Snapshot battle history.
- [ ] Snapshot medals or qualification flags if added.
- [ ] Snapshot modest resource reward if used.
- [ ] Snapshot Academy choices that matter.

Flow:

```text
AcademySave
    |
    v
GraduationSnapshot
    |
    v
NewCampaignCreation
```

## 6.2 Transfer Rules

- [ ] Decide which ships can transfer.
- [ ] Decide whether imported Academy vessels preserve UUIDs or receive new campaign UUIDs.
- [ ] If UUIDs change, preserve `academyOriginId` for identity/history continuity.
- [ ] Prevent repeated Academy completions from creating identity collisions in one campaign.
- [ ] Prevent multiple campaign starts from accidentally duplicating the same imported identity.
- [ ] Limit transfer to a balanced opening fleet.
- [ ] Prevent overpowered Academy outcomes from breaking campaign start.
- [ ] Preserve emotional continuity through names/history.
- [ ] Preserve damage only if the campaign can handle it fairly.
- [ ] Transfer medals/history only as non-breaking flavor unless balanced.
- [ ] Allow campaign start without importing the Academy fleet.
- [ ] Clearly explain what transferred and why.

Example:

```text
ACADEMY COMPLETE

Assigned to your operational command:
BCS Resolute - Frigate - 4 kills
BCS Wayfarer - Escort - damaged

Command Qualification awarded.
```

## 6.3 Campaign Start Integration

- [ ] Add `Use Academy Fleet` option when a snapshot exists.
- [ ] Add `Start Standard Campaign` option.
- [ ] Ensure imported fleet uses normal campaign ownership.
- [ ] Ensure imported ships appear in fleet UI.
- [ ] Ensure imported ships can be repaired/refit.
- [ ] Ensure imported ships can be destroyed normally.
- [ ] Ensure imported ships save/load normally.
- [ ] Ensure repeated Academy completions do not duplicate imports unexpectedly.

# Phase 7 - Local Playtest Telemetry

## 7.1 Telemetry Policy

- [ ] Telemetry is local only.
- [ ] No online analytics are added in this phase.
- [ ] Telemetry path uses the user data directory.
- [ ] Telemetry does not write into the install directory.
- [ ] Telemetry can be disabled.
- [ ] Telemetry contains no personal data.
- [ ] Telemetry is safe to share manually for playtesting.
- [ ] Telemetry file format is documented.

## 7.2 Event Schema

Required fields:

- [ ] session ID;
- [ ] game version;
- [ ] Academy version;
- [ ] timestamp;
- [ ] event name;
- [ ] chapter;
- [ ] step if applicable;
- [ ] elapsed time;
- [ ] result if applicable.

Example:

```json
{
  "sessionId": "2ddf0000-0000-4000-9000-000000000000",
  "gameVersion": "1.0.1.12",
  "academyVersion": 1,
  "event": "academy_stage_failed",
  "chapter": "hold_the_line",
  "step": "withdraw_under_pressure",
  "timestamp": "2026-08-13T15:42:31Z",
  "elapsedSeconds": 418
}
```

## 7.3 Progression Events

- [ ] `academy_started`
- [ ] `academy_chapter_started`
- [ ] `academy_chapter_completed`
- [ ] `academy_chapter_failed`
- [ ] `academy_chapter_recovered`
- [ ] `academy_abandoned`
- [ ] `academy_completed`
- [ ] `graduation_snapshot_created`
- [ ] `graduation_fleet_imported`
- [ ] `normal_campaign_started_after_academy`

## 7.4 First-Action Events

- [ ] `first_target_selected`
- [ ] `first_weapon_fired`
- [ ] `first_fleet_order`
- [ ] `first_ship_lost`
- [ ] `first_retreat`
- [ ] `first_repair`
- [ ] `first_refit`
- [ ] `first_purchase_or_commission`
- [ ] `first_campaign_map_opened`
- [ ] `first_mission_selected`
- [ ] `first_after_action_report_viewed`
- [ ] `first_save_load_after_academy`

## 7.5 Hesitation And Confusion Signals

- [ ] time in chapter.
- [ ] time until first order.
- [ ] time until target selected.
- [ ] time before opening campaign map.
- [ ] time spent in repair/refit screen.
- [ ] hint display count.
- [ ] hint repeat count.
- [ ] invalid click count where available.
- [ ] repeated failed objective attempts.
- [ ] long idle time during active instruction.
- [ ] player opens menu during unresolved instruction.

## 7.6 Telemetry Review

- [ ] Add a simple local telemetry summary tool or report.
- [ ] Count Academy starts.
- [ ] Count Academy completions.
- [ ] Count abandonment by chapter.
- [ ] Count repeated hints by chapter.
- [ ] Count first retreat success.
- [ ] Count first repair success.
- [ ] Count average time in each chapter.
- [ ] Identify first-hour walls.
- [ ] Promote reproducible confusion into UI or tutorial fixes.

# Phase 8 - UI, Accessibility, And Presentation Polish

## 8.1 Progressive UI Exposure

- [ ] Chapter 1 exposes movement, targeting, and weapons.
- [ ] Chapter 2 highlights fleet orders.
- [ ] Chapter 3 highlights retreat.
- [ ] Chapter 4 highlights repair/refit.
- [ ] Chapter 5 highlights campaign map and mission briefing.
- [ ] Chapter 6 highlights AAR and strategic consequences.
- [ ] Locked controls are not confusing.
- [ ] Disabled controls explain their status.
- [ ] Highlighted controls are visually clear.
- [ ] Highlights do not cover important combat information.

## 8.2 Text And Readability

- [ ] Academy hints fit at 1280x720.
- [ ] Academy hints fit at 1920x1080.
- [ ] AAR screen fits at 1280x720.
- [ ] AAR screen fits at 1920x1080.
- [ ] AAR has readable hierarchy.
- [ ] AAR does not show severe text overlap.
- [ ] AAR supports scaled HUD text.
- [ ] AAR supports high-contrast HUD options.
- [ ] AAR avoids tiny dense paragraphs.
- [ ] Mission-choice copy is short and scannable.

## 8.3 Controls

- [ ] Academy can be completed with keyboard/mouse.
- [ ] All mandatory controls are accessible.
- [ ] Skip current hint is available.
- [ ] Replay hint/archive is available.
- [ ] Pause/menu behavior is safe during Academy.
- [ ] Retreat command is clearly reachable.
- [ ] Repair/refit commands are clearly reachable.
- [ ] Mission choice command is clearly reachable.
- [ ] No mandatory action requires debug tools.

## 8.4 Audio And Visual Polish

- [ ] Academy prompts use existing UI audio consistently.
- [ ] AAR opening/closing uses existing UI audio consistently.
- [ ] No fallback weapon audio appears during Academy.
- [ ] Important Academy battle events are audible but not spammy.
- [ ] Visual highlights do not hide ship sprites.
- [ ] Ship/turret visuals remain readable during Academy.
- [ ] Reduced flash setting is honored.
- [ ] Reduced screen shake setting is honored.

# Phase 9 - Automated Coverage And Validation

## 9.1 BattleResult Tests

- [ ] Campaign victory emits BattleResult.
- [ ] Campaign defeat emits BattleResult.
- [ ] Campaign withdrawal emits BattleResult.
- [ ] Custom battle emits BattleResult.
- [ ] Academy battle emits BattleResult.
- [ ] Missing campaign context is handled.
- [ ] Custom Team E ship appears correctly.
- [ ] Custom weapon contribution does not crash result generation.

## 9.2 AAR Tests

- [ ] Report generation succeeds from minimal BattleResult.
- [ ] Report generation succeeds from full campaign BattleResult.
- [ ] Report shows correct tactical result.
- [ ] Report shows correct mission result.
- [ ] Report shows losses correctly.
- [ ] Report shows resources correctly.
- [ ] Report shows strategic consequence when present.
- [ ] Report omits campaign-only lines in custom battle.
- [ ] Report insight count is capped.
- [ ] Report recommendations are valid for current mode.

## 9.3 Academy Tests

- [ ] Academy starts from main menu.
- [ ] Academy saves progress.
- [ ] Academy reloads progress.
- [ ] Chapter 1 can complete.
- [ ] Chapter 2 can complete.
- [ ] Chapter 3 teaches withdrawal as the intended success path.
- [ ] Chapter 3 can recover if the enemy is destroyed before withdrawal.
- [ ] Chapter 3 can complete through withdrawal with required ships preserved.
- [ ] Chapter 4 can complete after damage.
- [ ] Chapter 5 can complete with either mission choice.
- [ ] Chapter 6 creates graduation snapshot.
- [ ] Academy completion persists.
- [ ] Academy can be restarted.
- [ ] Academy can be skipped by starting normal campaign.

## 9.4 Recovery Tests

- [ ] Target destroyed before instruction does not softlock.
- [ ] Required ship destroyed does not softlock.
- [ ] Player retreats early does not softlock.
- [ ] Player opens map early does not softlock.
- [ ] Player completes objective early does not softlock.
- [ ] Player saves and reloads mid-step without duplicate prompts.
- [ ] Player ignores optional objective without blocking completion.

## 9.5 Packaged-Build Tests

- [ ] Windows packaged Academy launch succeeds.
- [ ] Linux packaged Academy launch succeeds.
- [ ] macOS packaged Academy launch succeeds when available.
- [ ] Clean install does not require repo files.
- [ ] Academy save path uses user data directory.
- [ ] Telemetry path uses user data directory.
- [ ] Graduation import works from packaged build.
- [ ] AAR assets load from packaged build.
- [ ] No missing assets in Academy.

# Phase 10 - Blind Playtesting

## 10.1 Tester Setup

- [ ] Recruit the owner and several unfamiliar testers.
- [ ] Use a clean packaged build.
- [ ] Use a clean user data directory.
- [ ] Record tester hardware.
- [ ] Record OS.
- [ ] Record build version.
- [ ] Record Academy version.
- [ ] Record difficulty.
- [ ] Do not explain the game verbally during the test.
- [ ] Ask tester to play for 60 minutes.

## 10.2 Observation Checklist

- [ ] Did the tester choose Academy without being told?
- [ ] Did the tester understand movement?
- [ ] Did the tester understand targeting?
- [ ] Did the tester understand firing?
- [ ] Did the tester understand fleet orders?
- [ ] Did the tester understand ship roles?
- [ ] Did the tester understand range?
- [ ] Did the tester understand retreat?
- [ ] Did the tester understand repair/refit?
- [ ] Did the tester understand mission choice?
- [ ] Did the tester understand strategic consequences?
- [ ] Did the tester understand AAR results?
- [ ] Did the tester know what to do next after one hour?

## 10.3 Technical Checklist

- [ ] No crashes.
- [ ] No softlocks.
- [ ] No tutorial dead ends.
- [ ] No missing assets.
- [ ] No severe text overlap.
- [ ] Save/load succeeds.
- [ ] Academy completion persists.
- [ ] Graduation fleet imports correctly.
- [ ] AAR appears correctly.
- [ ] Packaged runtime does not depend on repo files.
- [ ] Local telemetry file is generated if enabled.

## 10.4 Interview Questions

- [ ] What is your fleet?
- [ ] How do you command ships?
- [ ] How do weapons and targeting work?
- [ ] Why do different ship roles matter?
- [ ] Why does range matter?
- [ ] How do you retreat?
- [ ] How do you repair damaged ships?
- [ ] How do you replace or purchase ships?
- [ ] How do you select a campaign mission?
- [ ] How can territory or faction state change?
- [ ] What key factors influenced your last battle?
- [ ] What do you plan to do next?
- [ ] What was confusing?
- [ ] What was exciting?
- [ ] Where did you feel lost?

## 10.5 Acceptance Threshold

- [ ] Establish final numeric thresholds after the first baseline test rounds.
- [ ] Academy completion target is defined before release-candidate sign-off.
- [ ] Retreat comprehension target is defined before release-candidate sign-off.
- [ ] Repair/refit comprehension target is defined before release-candidate sign-off.
- [ ] Mission-choice comprehension target is defined before release-candidate sign-off.
- [ ] AAR comprehension target is defined before release-candidate sign-off.
- [ ] `Knows what to do next` target is defined before release-candidate sign-off.
- [ ] P0/P1 blocker threshold is exactly 0.
- [ ] Tutorial softlock threshold is exactly 0.
- [ ] No tester hits a reproducible P0/P1 blocker.
- [ ] Any repeated confusion has an assigned fix.
- [ ] Owner approves first-hour clarity.

Candidate final targets after baseline testing:

```text
Academy completion: at least 80%
Retreat comprehension: at least 80%
Repair/refit comprehension: at least 80%
Mission-choice comprehension: at least 80%
AAR comprehension: at least 80%
Knows what to do next: at least 80%
P0/P1 blockers: 0
Tutorial softlocks: 0
```

# Phase 11 - First-Hour Polish Pass

## 11.1 Fix Triage

- [ ] Promote crashes to P0.
- [ ] Promote save corruption to P0.
- [ ] Promote Academy softlocks to P0.
- [ ] Promote missing mandatory controls to P1.
- [ ] Promote repeated tester confusion to P1 or P2.
- [ ] Promote severe text overlap to P1.
- [ ] Promote misleading AAR explanation to P1.
- [ ] Promote missing asset in packaged build to P1.
- [ ] Keep cosmetic polish as P3 unless it harms comprehension.

## 11.2 Iteration Rules

- [ ] Fix the smallest thing that removes the confusion.
- [ ] Prefer clearer UI/copy over adding new mechanics.
- [ ] Prefer rule tuning over broad rewrites.
- [ ] Re-run affected automated tests after every fix.
- [ ] Re-run at least one clean packaged Academy playthrough after major fixes.
- [ ] Update this checklist as items complete.
- [ ] Do not break feature freeze.

## 11.3 Final First-Hour Run

- [ ] Complete Academy from a clean install.
- [ ] Complete Academy from packaged Windows build.
- [ ] Complete Academy from packaged Linux build.
- [ ] Complete Academy from packaged macOS build when available.
- [ ] Start normal campaign from Academy graduation.
- [ ] Start normal campaign without Academy.
- [ ] Save and reload after Academy completion.
- [ ] Verify AAR after normal campaign battle.
- [ ] Verify telemetry summary.
- [ ] Verify no debug tools are required.

# Phase 12 - Steam Release Candidate

## 12.1 Steamworks Preparation

Steam-specific work begins only after owner-side Steamworks access exists.

- [ ] Steamworks partner access exists.
- [ ] AppID is assigned.
- [ ] Depot IDs are assigned for supported platforms.
- [ ] Store package and dev package configuration is understood.
- [ ] Install folder is chosen.
- [ ] Branch strategy is chosen.
- [ ] SteamPipe scripts are created.
- [ ] SteamPipe upload is tested.
- [ ] Store page checklist is completed.
- [ ] Build checklist is completed.
- [ ] Store page is submitted for review.
- [ ] Build is submitted for review after store page requirements are met.

Steam release process notes:

- Valve requires store presence and build/configuration checklists before release.
- Store page review should be submitted before build review.
- The coming-soon page must be live for at least two weeks before release.
- The final release button is controlled by the developer in Steamworks.

Official reference: https://partner.steamgames.com/doc/store/releasing

## 12.2 Steam Candidate Build

- [ ] Version is finalized.
- [ ] Release notes are finalized.
- [ ] Known issues are finalized.
- [ ] System requirements are finalized.
- [ ] Save compatibility policy is finalized.
- [ ] Windows build passes.
- [ ] Linux build passes.
- [ ] macOS build passes if supported for Steam.
- [ ] Clean install smoke test passes.
- [ ] Current release verification passes.
- [ ] First-hour Academy acceptance passes.
- [ ] AAR acceptance passes.
- [ ] Full tests pass.
- [ ] Performance guardrails pass.
- [ ] Save/load soak passes.
- [ ] Campaign-transition fuzzing passes.

## 12.3 Steam Page Support Materials

- [ ] Capture screenshots that show real gameplay.
- [ ] Capture fleet combat screenshot.
- [ ] Capture campaign map screenshot.
- [ ] Capture AAR screenshot.
- [ ] Capture Academy or prologue screenshot if appropriate.
- [ ] Capture custom ship/Team E screenshot only if it is advertised.
- [ ] Prepare short description.
- [ ] Prepare long description.
- [ ] Prepare feature bullets.
- [ ] Prepare system requirements.
- [ ] Prepare trailer plan or footage list.
- [ ] Ensure store copy does not promise unfinished systems.

## 12.4 Final No-Go Gates

- [ ] No known reproducible crash.
- [ ] No known save corruption.
- [ ] No known campaign softlock.
- [ ] No known Academy softlock.
- [ ] No mission with undiscoverable success or failure condition.
- [ ] No AAR explanation that is known to be false.
- [ ] No inaccessible mandatory control.
- [ ] No progression requiring debug tools.
- [ ] No packaged build dependency on repo files.
- [ ] No severe first-hour text overlap.
- [ ] No missing required platform artifact.

# Final Sign-Off

## Owner Acceptance

- [ ] Owner approves Commander Academy structure.
- [ ] Owner approves AAR content and tone.
- [ ] Owner approves battle explanation accuracy.
- [ ] Owner approves graduation fleet behavior.
- [ ] Owner approves first-hour UI clarity.
- [ ] Owner approves first-hour difficulty.
- [ ] Owner approves blind playtest results.
- [ ] Owner approves packaged build.
- [ ] Owner approves Steam candidate readiness.

## Release Decision

- [ ] GO - First-hour acceptance passes and no P0/P1 release blocker remains.
- [ ] NO-GO - At least one P0/P1 release blocker remains.

Final build:

Final commit:

Release date:

Known accepted issues:

Owner notes:

# Campaign Command Layer Implementation Checklist

Date: 2026-05-15
Status: Completed in code and backed by campaign regression coverage
Source: [CAMPAIGN_COMMAND_LAYER_OVERHAUL_PLAN.md](C:/Users/xhatf/IdeaProjects/game/docs/CAMPAIGN_COMMAND_LAYER_OVERHAUL_PLAN.md)

## Purpose

This checklist converts the overhaul brief into buildable work.

It is intended to be used as an implementation tracker, not just a design note.

## Completion Rules

- A checklist item is not done until the HUD surface and the underlying game behavior both agree.
- UI-only cleanup does not count for items that promise command authority.
- Any item that changes player-facing command text should keep or replace test coverage.
- New unavailable actions must always expose a reason string.

## Phase 1: Readability, Layout, And Top-Fold Clarity

### 1.1 Safe Content Regions

- [x] Separate decorative frame bounds from text-safe content wells in the strategic renderer.
- [x] Ensure all campaign tab copy renders inside dark-backed content wells.
- [x] Prevent action labels and body text from sitting on bright frame edges or seams.
- [x] Add consistent inner padding for all command panels.
- [x] Add consistent section spacing so headings, meters, and lines do not visually collapse together.

### 1.2 Command Bar Readability

- [x] Move the action strip onto a dedicated dark command shelf.
- [x] Increase primary command button height and text contrast.
- [x] Increase secondary command button legibility and spacing.
- [x] Make disabled actions look intentionally unavailable rather than washed out or broken.
- [x] Show one-line consequence or reason text for every visible command button.

### 1.3 Tab Information Hierarchy

- [x] Reserve the top fold of each tab for 3-5 decision-critical facts.
- [x] Remove duplicated posture, route, or flavor lines from the top fold.
- [x] Push secondary flavor and support text lower in the panel or into sub-boards.
- [x] Ensure each tab can be parsed in under 5 seconds.
- [x] Verify that the selected tab, selected target, current risk, and next action are obvious at a glance.

### 1.4 Left Instrument Stack

- [x] Reduce decorative text density in the receiver panel.
- [x] Reduce decorative text density in the direction finder panel.
- [x] Split comms into standing, latest signal, and actionable lead.
- [x] Make the left stack produce operational advice instead of generic flavor.
- [x] Confirm that the left stack surfaces at least one useful decision cue at all times.

## Phase 2: Navigation Must Guide Instead Of Flood

### 2.1 Current Order Block

- [x] Show current position, selected destination, travel state, and ETA clearly.
- [x] Show route risk in a single prominent line.
- [x] Surface the immediate blocker for movement or site entry.
- [x] Keep route commitment text concise and action-oriented.
- [x] Remove repeated descriptive lines that do not change the move decision.

### 2.2 Selected Contact / Site Block

- [x] Show why the selected site matters.
- [x] Show what action is possible there now.
- [x] Show the main risk or uncertainty attached to the selection.
- [x] Prefer one recommendation line over multiple weak flavor lines.
- [x] Keep site description readable without overtaking command actions.

### 2.3 Navigation Actions

- [x] Ensure `Plot Course`, `Engage Course`, `Dock`, `Enter Site`, `Set Waypoint`, and `Cancel Course` present clearly when relevant.
- [x] Expose the reason when an action is blocked.
- [x] Make sensor sweep and contact sharpening actions visually separate from travel commitment.
- [x] Ensure the recommended navigation action is visually dominant.
- [x] Confirm keyboard shortcuts remain optional rather than required discovery knowledge.

## Phase 3: Fleet Command Must Become Real

### 3.1 Fleet Model

- [x] Add explicit campaign participation state per persistent ship.
- [x] Support `Auto`, `Commit`, and `Hold Back` for next tactical entry.
- [x] Add command-group assignment to persistent fleet entries.
- [x] Preserve command-group state in checkpoints and resumes.
- [x] Add clear defaults for legacy saves that lack group metadata.

### 3.2 Command Groups

- [x] Support at minimum `Flag Group`, `Escort Group`, and `Reserve Group`.
- [x] Add detached group support when command capacity allows it.
- [x] Surface ship counts and readiness by group in the fleet tab.
- [x] Allow reassignment between groups from the fleet command UI.
- [x] Prevent impossible detachments with a visible reason.

### 3.3 Tactical Commitment

- [x] Show which ships will enter the next battle.
- [x] Show which ships are held back and why.
- [x] Show expected arrival vector or reinforcement behavior for non-flag groups.
- [x] Ensure fleet posture and commitment state both influence tactical entry.
- [x] Add tests proving fleet tab decisions change tactical spawn composition.

### 3.4 Fleet UI

- [x] Replace the passive roster dump with force package summaries.
- [x] Show readiness, strain, and role mix by group.
- [x] Add direct commands for commit, hold back, reserve, and reassign.
- [x] Make roster rows concise enough to scan quickly.
- [x] Keep advanced group control gated behind available command capacity.

## Phase 4: Campaign-To-Tactical Continuity

### 4.1 Authoritative Spawn Resolution

- [x] Resolve tactical spawns from persistent command-group data.
- [x] Guarantee committed flagship group ships spawn unless a clear simulated blocker applies.
- [x] Keep held-back ships absent by design rather than bug.
- [x] Support delayed or alternate entry for reserves and detachments.
- [x] Make reinforcement timing compatible with mission scripting.

### 4.2 Player Explanation

- [x] Show why a ship did not appear if it was expected.
- [x] Use reason strings such as `withheld by order`, `damaged`, `delayed`, or `detached`.
- [x] Reflect those reasons in both fleet planning UI and tactical transition summaries.
- [x] Avoid silent roster disappearance.
- [x] Add regression coverage for zone entry continuity.

## Phase 5: Logistics Must Have Teeth

### 5.1 Cost Model

- [x] Charge fuel for meaningful travel and posture choices.
- [x] Charge supplies for repair, upkeep, and operational support.
- [x] Charge ammo for strikes and missile-heavy commitments.
- [x] Make salvage compete with emergency repair and sale value.
- [x] Bind detached operations and extraction burns to real resource costs.

### 5.2 Threshold States

- [x] Define `Stable`, `Strained`, `Short`, and `Critical` states for major resources.
- [x] Show those states prominently in the logistics tab.
- [x] Attach mechanical penalties or restrictions to low-state thresholds.
- [x] Ensure at least fuel, supplies, and ammo can become normal-run constraints.
- [x] Add tests that validate shortage states change available actions.

### 5.3 Forecasting

- [x] Show a 1-jump or 2-jump operational forecast.
- [x] Show projected cost for one route move, one strike, and one repair cycle.
- [x] Warn the player when a selected action crosses a critical threshold.
- [x] Recommend corrective actions such as docking, trading, posture change, or canceling detachment.
- [x] Ensure the logistics tab answers what the player is about to run out of.

## Phase 6: Strikes Must Complete The Loop

### 6.1 Targeting And Lock

- [x] Unify selected hostile targeting across navigation and strikes.
- [x] Persist strike lock until changed or cleared.
- [x] Show target type, intel quality, and strike eligibility clearly.
- [x] Distinguish no target, weak target, valid target, and blocked target states.
- [x] Add tests for target lock persistence and eligibility transitions.

### 6.2 Strike Preconditions

- [x] Create explicit preflight checks for sortie, torpedo, and atomic actions.
- [x] Show blocked reasons for ammo, fuel, exposure, cooldown, posture, and distance.
- [x] Show expected cost before commitment.
- [x] Show expected retaliation or theater pressure consequence.
- [x] Prevent ambiguous failure on command execution.

### 6.3 Strike Resolution

- [x] Produce a visible command report after strike execution.
- [x] Apply immediate strategic consequences consistently.
- [x] Allow some strike results to affect tactical setup directly.
- [x] Track strike heat and counterplay in a way the player can actually read.
- [x] Add regression coverage for successful strike launch when requirements are met.

## Phase 7: Receiver, Direction Finder, And Comms Must Matter

### 7.1 Receiver

- [x] Surface current band quality clearly.
- [x] Surface uncertain contact count or contact pressure clearly.
- [x] Surface best lead or best intercept opportunity.
- [x] Surface a sweep recommendation when uncertainty is high.
- [x] Remove passive fiction lines from the top of the receiver panel.

### 7.2 Direction Finder

- [x] Surface threat direction and pressure origin.
- [x] Surface whether the selected course moves into or away from danger.
- [x] Surface site-entry readiness clearly.
- [x] Keep only one short callout or recommendation line.
- [x] Tie at least one displayed pressure cue to actual campaign threat state.

### 7.3 Comms

- [x] Split faction standing from chatter.
- [x] Surface the latest actionable contact instead of a chatter wall.
- [x] Surface rumor or support only when it can alter a decision.
- [x] Use comms to unlock or explain real options such as support, trade, or strike sharpening.
- [x] Keep comms lines short enough to avoid becoming an unreadable block.

## Phase 8: Regression Coverage And Rollout

### 8.1 Test Coverage

- [x] Expand strategic HUD tests for new top-fold summaries.
- [x] Add tests for command availability reason strings.
- [x] Add tests for fleet commitment affecting tactical composition.
- [x] Add tests for logistics shortage gating actions.
- [x] Add tests for valid strike launch and visible strike denial reasons.

### 8.2 Rollout Sequence

- [x] Ship readability and layout fixes first.
- [x] Ship truthful tab summaries second.
- [x] Ship fleet commitment and continuity third.
- [x] Ship logistics pressure fourth.
- [x] Ship full strike loop fifth.

## Immediate Execution Slice

This is the current recommended near-term build order:

- [x] Add this checklist document.
- [x] Rebuild campaign panel safe text wells.
- [x] Rebuild the bottom command shelf and button readability.
- [x] Tighten fleet/resources/strikes summary lines for top-fold clarity.
- [x] Tighten receiver/finder/comms lines for operational usefulness.
- [x] Run strategic HUD and campaign regression tests.
- [x] Triage follow-up failures into UI-only, system-authority, and authored-content buckets.

## Closeout Status

The implementation work behind this checklist is now complete enough to clear the full automated `Campaign*` regression suite.

The command layer, authored progression fixes, sector-one pacing, fleet continuity, strike preflight, logistics gating, late-campaign landmark surfacing, and strategic/tactical startup compatibility are all now covered in code and passing test.

Any remaining work from this checklist is no longer a known gameplay-system blocker. What remains is optional manual UX review:

- verify final panel scan speed and visual dominance in live play sessions
- tune any last copy density or flavor-line placement after extended campaign runs
- iterate on presentation polish if new player feedback identifies readability misses


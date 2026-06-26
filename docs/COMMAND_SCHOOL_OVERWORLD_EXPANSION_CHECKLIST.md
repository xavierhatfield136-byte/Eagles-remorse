# Command School Overworld Expansion Checklist

Purpose: expand Command School from a tactical-only sandbox into a two-part training mode that teaches both the overworld campaign map and the in-mission tactical command tools without risking the player's real campaign save.

This checklist supersedes the older decision in `TUTORIAL_OVERWORLD_AND_CAMPAIGN_CHECKLIST.md` that Command School should remain tactical-only. Campaign Ops can still keep contextual first-hour guidance, but Command School should become the replayable place where the player can deliberately practice campaign-map movement, site entry, fleet choices, and mission command.

## Implementation Status - 2026-06-26

- [x] Command School now starts in a deterministic sample overworld map.
- [x] The sample overworld includes Green, Yellow, Red, ore/resource, hostile contact, and optional side-contact training locations.
- [x] Command School uses a campaign-state flag so training state is isolated from normal Campaign Ops.
- [x] Autonomous war escalation, territory flipping, and checkpoint/profile writes are bypassed for Command School training.
- [x] Overworld lessons now precede the tactical Command School lessons.
- [x] The existing tactical lessons are relabeled as the tactical branch and still rebuild the controlled tactical sandbox.
- [x] Ctrl+F1 skips the current Command School lesson while Command School is active.
- [x] Ctrl+F2 opens a Command School lesson archive while Command School is active.
- [x] Automated tests cover sample map initialization, owner/site identity, route travel, enter-site transition stability, save isolation, skip/archive, and tactical warp regression.
- [ ] Manual acceptance script still needs to be run in the playable build.

## Target Player Experience

- [ ] From the main menu, `Command School` clearly communicates that it includes overworld and tactical lessons.
- [ ] The player starts in a safe sample overworld map, not the real campaign.
- [ ] The sample overworld has enough content to teach movement and decisions without overwhelming the player.
- [ ] The player can complete the overworld school without permanent campaign consequences.
- [ ] The player can transition from the sample overworld into an in-mission tactical lesson.
- [ ] The in-mission Command School teaches the current tactical controls and options, not stale older controls.
- [ ] The player can skip individual lessons and replay Command School later.
- [ ] The player always has a visible next objective and a concise explanation of why a blocked action is blocked.

## Phase 1 - Current Command School Audit

- [ ] Inventory every current `TutorialSystem.LessonId`.
- [ ] Inventory every current checklist item shown by `TutorialSystem.checklist`.
- [ ] Inventory every current tutorial completion trigger in `TutorialSystem.lessonComplete`.
- [ ] Identify stale command references, especially tactical map, warp, carrier, base, mining, crew, and x-ray controls.
- [ ] Identify which current tactical lessons should remain unchanged.
- [ ] Identify which current tactical lessons need copy updates only.
- [ ] Identify which current tactical lessons need new mechanics or completion triggers.
- [ ] Confirm Command School does not mutate real campaign state, unlocks, saves, or persistent fleet inventory.

## Phase 2 - Sample Overworld Map Design

- [ ] Create a small mock overworld scenario that is separate from normal Campaign Ops.
- [ ] Include one Green home anchorage or command base.
- [ ] Include one neutral or Yellow trade station.
- [ ] Include one ore/resource site.
- [ ] Include one hostile Red patrol/contact.
- [ ] Include one major site that can be entered.
- [ ] Include one optional side contact such as distress, salvage, or allied support.
- [ ] Include a visible safe boundary or training-sector label so the player knows this is not the real war.
- [ ] Seed map locations deterministically so tests and screenshots are stable.
- [ ] Use intentionally simple names such as `TRAINING ANCHORAGE`, `BROKER PRACTICE HUB`, `ORE PRACTICE FIELD`, and `RED DRONE CONTACT`.
- [ ] Disable or heavily dampen autonomous war ticks, territory flips, campaign escalation, and irreversible losses inside the sample map.
- [ ] Ensure no sample overworld event can corrupt or overwrite a normal campaign save.

## Phase 3 - Overworld Lesson Flow

- [ ] Add an overworld lesson group before the existing tactical lessons.
- [ ] Lesson: camera and map reading.
  - [ ] Teach panning/zooming if available.
  - [ ] Teach what major-site colors mean.
  - [ ] Teach what invasion arrows or fleet-route arrows mean.
- [ ] Lesson: selecting a site.
  - [ ] Teach clicking or cycling to a major site.
  - [ ] Show site owner, services, threat, and enter-site availability.
  - [ ] Explain why random ore sites differ from major stations.
- [ ] Lesson: plotting movement.
  - [ ] Teach choosing a destination.
  - [ ] Teach confirming or cancelling a course.
  - [ ] Teach travel/warp feedback and arrival cues.
- [ ] Lesson: scan and intel quality.
  - [ ] Teach scanning an unknown or partial contact.
  - [ ] Teach that incomplete intel includes uncertainty.
  - [ ] Teach how a contact becomes safe to engage or avoid.
- [ ] Lesson: resource site.
  - [ ] Teach travelling to the ore/resource site.
  - [ ] Teach entering or interacting with the resource site.
  - [ ] Teach that resource sites use starfield backgrounds rather than planet/station backdrops.
- [ ] Lesson: station services.
  - [ ] Teach docking or entering a safe station.
  - [ ] Teach repair/refit/trade/commissioning at a simplified level.
  - [ ] Teach when a station is Green, Yellow, or Red controlled.
- [ ] Lesson: fleet organization.
  - [ ] Teach selecting a hull or fleet element.
  - [ ] Teach active/reserve/escort or group assignment if available.
  - [ ] Teach how fleet composition affects upcoming missions.
- [ ] Lesson: hostile contact.
  - [ ] Teach reading a Red contact.
  - [ ] Teach deciding between avoid, auto-resolve if available, and manual mission entry.
  - [ ] Teach the exact button/key used to enter the site or mission.
- [ ] Lesson: overworld-to-mission transition.
  - [ ] Trigger a controlled tactical encounter from the training map.
  - [ ] Verify the player stays inside the mission instead of bouncing back to overworld.
  - [ ] Confirm returning from the tactical lesson restores the sample overworld state.

## Phase 4 - In-Mission Command School Update

- [ ] Rename or label the existing lessons as the tactical branch of Command School.
- [ ] Update lesson summary copy so it no longer says Campaign Ops alone covers overworld lessons.
- [ ] Confirm tactical movement lesson matches current controls and UI.
- [ ] Confirm waypoint/map lesson matches current `M` map behavior.
- [ ] Confirm targeting lesson teaches current lock, fire, missile, and sensor behavior.
- [ ] Confirm tactical HUD lesson explains ship labels, fleet count, objective text, and mission markers.
- [ ] Confirm x-ray lesson teaches current room filters and room focus behavior.
- [ ] Confirm logistics/refit lesson still matches current base/shop UI.
- [ ] Confirm crew/power lesson matches current `H`, `O`, and related station controls.
- [ ] Confirm carrier lesson matches current wing launch, recall, behavior, and auto-launch controls.
- [ ] Add a lesson for simplified/performance view if it remains player-facing.
- [ ] Add a lesson for mission exit/withdrawal and what happens afterward.
- [ ] Add a final combined lesson that tells the player how overworld choices lead into tactical fights.

## Phase 5 - UI And Lesson Presentation

- [ ] Add a Command School branch selector, or make the sequence naturally start with overworld then move into tactical.
- [ ] Show lesson progress as `Overworld X/Y` and `Tactical X/Y`.
- [ ] Keep the tutorial panel away from the main action bar and strategic controls.
- [ ] Add an archive page for overworld Command School lessons.
- [ ] Add concise context hints for common blocks:
  - [ ] no site selected;
  - [ ] destination too far or unavailable;
  - [ ] insufficient intel;
  - [ ] not in range to enter site;
  - [ ] station hostile or inaccessible;
  - [ ] no fleet/hull selected;
  - [ ] tutorial action already completed.
- [ ] Ensure all tutorial copy fits at 1280x720 with normal UI scale.
- [ ] Ensure tutorial copy remains readable in high-contrast and colorblind modes.

## Phase 6 - Technical Integration

- [ ] Decide whether overworld Command School lives in `TutorialSystem`, `CampaignSystem`, or a new `CommandSchoolSystem`.
- [ ] Prefer a separate sample-state builder so normal campaign generation stays untouched.
- [ ] Add a command-school-specific campaign state flag or mode marker.
- [ ] Ensure `SpawnSystem` initializes the sample overworld for `GameMode.TUTORIAL` when the overworld branch begins.
- [ ] Ensure tactical lesson setup still initializes local ships, asteroids, bases, and scripted targets correctly.
- [ ] Add safe transitions:
  - [ ] main menu to sample overworld;
  - [ ] sample overworld to tactical lesson;
  - [ ] tactical lesson back to sample overworld;
  - [ ] completion back to main menu or free-practice sandbox.
- [ ] Prevent command-school autosaves from appearing as normal campaign saves unless explicitly intended.
- [ ] Make reset/restart training deterministic.
- [ ] Keep tutorial state separate from unlock/profile progression except for optional “completed Command School” telemetry.

## Phase 7 - Tests And Validation

- [ ] Add a test that `GameMode.TUTORIAL` can initialize the sample overworld.
- [ ] Add a test that sample overworld locations exist with expected owner colors and site types.
- [ ] Add a test that the overworld lesson list is ordered and non-empty.
- [ ] Add a test that every overworld lesson has a title, summary, checklist item, and completion trigger.
- [ ] Add a test that selecting and travelling to a training site completes the movement lesson.
- [ ] Add a test that scanning a training contact completes the intel lesson.
- [ ] Add a test that entering the training mission does not immediately eject the player back to overworld.
- [ ] Add a test that returning from the tactical lesson restores the sample overworld.
- [ ] Add a test that Command School cannot mutate normal campaign saves or persistent campaign inventory.
- [ ] Update existing `TutorialWarpRegressionTest` so the new overworld branch does not regress tactical warp behavior.
- [ ] Update HUD layout tests for the expanded Command School panel.
- [ ] Run focused tests:
  - [ ] `TutorialWarpRegressionTest`
  - [ ] `FirstHourExperienceTest`
  - [ ] `RendererHudLayoutTest`
  - [ ] new Command School overworld tests
- [ ] Run broader smoke tests after implementation.

## Phase 8 - Manual Acceptance Script

- [ ] Start Command School from the main menu.
- [ ] Confirm the player begins in the sample overworld map.
- [ ] Select the Green training anchorage.
- [ ] Plot a route to the anchorage.
- [ ] Cancel and re-plot a route.
- [ ] Travel to the anchorage.
- [ ] Open station/service information.
- [ ] Visit the ore/resource site.
- [ ] Confirm the resource site uses starfield-only presentation.
- [ ] Scan the Red training contact.
- [ ] Read intel uncertainty before full scan completion.
- [ ] Enter the training mission.
- [ ] Confirm the mission stays loaded and does not eject after one frame.
- [ ] Complete movement, targeting, x-ray, logistics, crew, carrier, and withdrawal lessons.
- [ ] Return to the sample overworld.
- [ ] Complete or skip the remaining lessons.
- [ ] Return to the main menu.
- [ ] Start a normal campaign and confirm no Command School state leaked into it.

## Phase 9 - Exit Criteria

- [ ] Command School teaches the main overworld controls and choices without relying on Campaign Ops.
- [ ] Command School teaches the current in-mission tactical controls accurately.
- [ ] The sample overworld is deterministic, safe, and visually distinct from the real campaign.
- [ ] The overworld-to-mission transition is stable.
- [ ] Lessons are skippable, replayable, and archived.
- [ ] Tests cover initialization, lesson progression, tactical transition, and save isolation.
- [ ] Manual acceptance passes at 1280x720 and 1920x1080.
- [ ] No known tutorial soft lock remains.

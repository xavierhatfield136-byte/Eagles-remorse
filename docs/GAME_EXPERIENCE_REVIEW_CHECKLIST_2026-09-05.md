# Game Experience Review — Execution Checklist

Date: 2026-09-05  
Status: Authorized and partially executed on 2026-09-05; first stabilization slice complete, remaining acceptance work still open.  
Purpose: Turn the September 5 game review into a bounded, verifiable implementation pass.

## Scope and execution rules

Authorization of this checklist covers the implementation, local validation, and documentation described below. Work proceeds in priority order without requesting approval for each routine implementation choice. This checklist does not authorize deployment, publishing, Steam submission, or deletion of existing player saves.

- Preserve existing uncommitted work and use isolated test save data.
- Recheck each finding against the current build before editing. If already resolved, record the evidence and mark the item verified rather than duplicating work.
- Build on existing onboarding, controls, after-action reports, and ship-history systems.
- Preserve game mechanics and detailed ship art except for the specific interaction changes described here.
- Follow existing stabilization priorities. This checklist does not supersede the stabilization master checklist referenced by `docs/README.md`; fleet-history additions follow the readability and stability work.
- Check an item only after its behavior is implemented and verified. Record evidence and remaining limitations in the execution log below.
- Refactor only the code needed for these changes; avoid a wholesale renderer or campaign rewrite.

## Instructions for the executing agent

This document is self-contained execution guidance. Drafting or expanding it is not authorization to change gameplay. Once the owner explicitly authorizes execution, follow these steps:

1. Read applicable `AGENTS.md` instructions, inspect `git status`, and record the starting commit, version, and existing modifications. Read the stabilization priorities linked from `docs/README.md`. Do not reset or overwrite another task's changes.
2. Use the stable item IDs below in progress updates, evidence filenames, and the execution log. Each item has implementation guidance, likely code entry points, and acceptance checks. Code paths and symbols are navigation aids verified at drafting time; locate their current equivalents if they move.
3. Complete UI, onboarding, HUD, and contrast work before adding ship-history behavior. Establish before-captures and test-fixture corrections early where needed; QA is continuous, not postponed until section 8. Finish navigation and command work before the final walkthrough.
4. Work through dependencies without treating every item as a separate approval gate. When an implementation choice has a default below, use it unless current code demonstrates a better compatible solution. Record the rationale. Ask only when an unresolved owner decision materially changes scope or existing behavior.
5. If a reported defect cannot be reproduced, check both the live path and the fixture with matching mode, size, and settings. Log `not reproduced` and evidence rather than claiming a fix. Mark an item complete without an edit only when its acceptance criteria already hold; log `verified existing behavior`.
6. Use separate user-data folders for live walkthroughs and automated fixtures. Never point destructive test setup at `save/`, the owner's application-data folder, or an existing save slot. Do not copy private saves into committed test fixtures.
7. Collect meaningful tests for state transitions, input routing, layout bounds, and persistence. Use rendered inspection for readability. A successful compile, screenshot hash, or presence of a button label does not prove that the interaction works.
8. Finish each section with a brief log entry listing changed files, tests/commands and results, capture paths, and unresolved issues. Keep checkboxes unchecked for partial work. Do not represent an agent walkthrough as a blind human usability study.

### Shared screen and evidence matrix

- Use **client/render viewport** sizes of 1280×720 and 1920×1080, excluding window borders. Record Windows display scaling separately so a window's outer dimensions are not mistaken for viewport size.
- At both sizes inspect UI text scales 1.0 and 1.25. Also verify 1280×720 at the currently supported maximum, 1.6 (`ExperienceSettings.normalize` clamps the setting to 0.8–1.6 at drafting time). Use the current supported maximum if that changes.
- Cover the main menu, campaign chooser, Academy opening, fresh campaign tactical view, local tactical map, strategic galaxy map, selected-location sidebar, command overlay, fleet inspection, and after-action report as applicable to each section. Local and galaxy maps are distinct render paths; passing one does not validate the other.
- Cover compact/full HUD, no target/selected target, and quiet/damaged states where relevant. Test colorblind/high-contrast combinations in VIS-04. Reuse captures across item IDs instead of producing duplicates.
- Store generated evidence under `build/reports/game-experience-review/`, grouped into `before/`, `after/`, and `logs/`. Record seed, mode, viewport, UI scale, HUD detail, palette, and setup steps beside each capture. Do not regenerate before-captures after making changes.
- Default layout policy: measure text with the actual scaled font, wrap meaningful prose, reflow or scroll secondary detail, and keep primary actions reachable. Do not solve crowding by silently reducing the user's text scale or deleting required information.

### Useful commands and entry points

Run commands from the repository root in PowerShell. These are local validation commands, not publishing steps:

```powershell
.\gradlew.bat classes
.\gradlew.bat currentReleaseVerificationHeadless --console=plain
.\gradlew.bat test --tests RendererHudLayoutTest --tests CampaignStrategicCommandHudTest --tests HotkeyRegistryTest --console=plain
.\gradlew.bat test --tests FirstHourExperienceTest --tests TutorialWarpRegressionTest --tests app.persistence.AcademyProgressStoreTest --console=plain
.\gradlew.bat test --tests BattleResultAnalysisServiceTest --tests WarMemorySystemTest --tests WarMemoryPlayerFacingIntegrationTest --tests CampaignSaveCompatibilityContractTest --console=plain
```

Run only the relevant targeted batch during iteration; include other existing/new tests when the change affects them. `BattleResultAnalysisServiceTest` tests the production class named **`BattleAnalysisService`**, not a nonexistent `BattleResultAnalysisService` source file.

For an isolated live launch and initial captures:

```powershell
java '-Dgame.userDataDir=build/reports/game-experience-review/live-user-data' '-Dcodex.disableAudio=true' -cp 'build/classes/java/main;build/resources/main' Main
java '-Dgame.userDataDir=build/reports/game-experience-review/fixture-user-data' '-Dcodex.disableAudio=true' -cp 'build/classes/java/main;build/resources/main' ScreenshotRegressionHarness --output=build/reports/game-experience-review/before
```

The second command deliberately avoids updating baselines. The harness can print `FAIL` without a nonzero exit unless strict mode is used: inspect its reported result, not just the process exit code. For different viewports, use or extend `ScreenshotRegressionHarness.capture(target, width, height)` through a small test/helper rather than claiming unsupported CLI arguments exist. Add settings-aware fixture configuration where needed. Keep audio muted during UI review; no audio acceptance is implied.

## Review baseline

The initial review inspected code, the live menu, Academy opening, fresh campaign, and six generated UI captures. It was not a full campaign or balance playthrough.

- Compilation succeeded.
- The focused headless release suite passed: 137 tests, zero failures/errors/skips.
- All six screenshot comparisons reported baseline differences. These are review signals, not proof that every difference is a defect.
- The tactical screenshot fixture uses Showcase mode; the strike-tab capture matched navigation.
- Initial captures: `build/reports/design-review/screenshots/` (generated local evidence, not guaranteed to be committed).

## 1. Fix overlapping and clipped UI — highest priority

- [ ] UI-01: Reproduce and document the live campaign overlaps at 1280×720: health/shield bars over readiness text and help text over missile controls.
  - **Do:** Launch with fresh isolated data, start Open World Campaign, and capture its initial tactical view in FULL HUD mode. Capture Academy and a selected-target combat state too. Identify the intersecting panel/text rectangles and whether the problem changes when X-ray panels are present.
  - **Inspect:** `GameRenderSystem.render`, `Renderer.drawBottomCombatVitals`, `computeXrayStackLayout`, readiness-card drawing, `drawHudControlsCard`, and `test/RendererHudLayoutTest.java`.
  - **Verify and record:** Save before-images plus the exact setup. Name each affected pair (vitals/readiness, help/missiles, etc.). Distinguish live observations from Showcase-only fixture artifacts. This item completes the reproduction record, not the repair.
- [ ] UI-02: Give status, readiness, weapon controls, help, and the bottom command strip reserved layout space so their text and hit targets do not overlap.
  - **Do:** Calculate panel bounds from viewport, scaled fonts, visible content, and detail mode before painting. Share those bounds with input hit testing. Reserve the bottom action strip first, then place status and weapon/help panels above it; reflow optional panels when width is constrained.
  - **Inspect:** `Renderer.java`, `GameRenderSystem.java`, `UISystem.java`, and `UiState.java`; follow existing rectangle/hitbox helpers rather than adding unrelated pixel offsets in each painter.
  - **Verify:** Check for unintended rectangle intersections and clipped content in both target sizes with/without target X-ray. Click each moved control and verify only its intended action runs. Intentional overlays such as tooltips must remain bounded and must not steal unrelated input.
- [ ] UI-03: Fix the campaign sidebar so location details finish above the action area; use wrapping, scrolling, or expandable detail where space is insufficient.
  - **Do:** In `drawGalaxySidebar`, honor the content limit computed above the action block. Measure wrapped location sections before drawing them; if they exceed the space, scroll the details area while keeping the primary action reachable. Do not paint overflow under a translucent action panel.
  - **Inspect:** `Renderer.drawGalaxySidebar`, `drawGalaxyCommandContent`, `galaxyActionBlockHeight`, `CampaignSidebarPresenter`, and the action registry in `CampaignSystem`.
  - **Verify:** Select a friendly service hub, hostile contact, free-space destination, and a location with long details. At 720p/large text, read the full details and activate the bottom action. Extend `CampaignStrategicCommandHudTest` for layout/hitbox behavior where appropriate.
- [ ] UI-04: Hide the underlying tactical HUD when the map is open and give the map an adequately opaque background.
  - **Do:** Define map-layer ownership in `GameRenderSystem`: map mode draws its own panels and background, suppressing tactical readiness, X-ray, weapon-mode, and help panels underneath. Keep intended map controls and urgent notifications in explicitly reserved areas. Apply the same policy to local and galaxy maps.
  - **Inspect:** Map render branches in `GameRenderSystem`, map painters in `Renderer`, and overlay input routing in `UISystem`/`GameplayActions`.
  - **Verify:** Open/close each map repeatedly with a target selected and with engineering detail expanded. Confirm no tactical text shows through, map clicks never fire weapons or activate hidden controls, and closing the map restores the prior tactical detail state. Do not change simulation pause behavior as a side effect.
- [ ] UI-05: Replace clipping or premature ellipses in essential objectives, instructions, and action labels with readable layout; keep full optional details accessible.
  - **Do:** Audit the visible strings in the review captures, especially campaign goals, sidebar instructions, readiness chips, and navigation buttons. Measure with `FontMetrics`; wrap goals/instructions and use short complete action verbs. Move supporting descriptions into expandable text or accessible tooltips rather than truncating the required action.
  - **Inspect:** `Renderer` text-fit/sidebar helpers, `TutorialSystem.drawOverlay`, `FirstHourOnboardingSystem.draw`, and `MainMenuPanel` labels. Do not globally remove all ellipsis helpers—long optional names may still need them.
  - **Verify:** Use long ship/location names and maximum supported text scale. The immediate objective and every primary button remain understandable without guessing truncated words; any abbreviated optional content has a reachable full version, including via keyboard focus where applicable.
- [ ] UI-06: Verify the affected screens at 1280×720, 1920×1080, and enlarged UI text, including mouse hit targets after reflow.
  - **Do:** Run the shared screen matrix for the UI-01 through UI-05 changes. Exercise resizing and text-scale changes during a session, not just at startup. Include long-content states and switching between local map, galaxy map, and tactical view.
  - **Verify:** Compare rendered button bounds to resolved hit targets at centers and edges; test adjacent buttons and blank space. Check that scrolling one panel does not zoom or scroll an unrelated surface.
  - **Evidence:** Record a pass/fail row per viewport/text-scale/mode combination and link representative after-captures. Unavailable display-dependent checks remain explicit gaps; headless rendering alone does not close the mouse-interaction acceptance.

Completion criteria: essential text and controls remain readable and operable; no covered location details, overlapping status text, or competing tactical HUD beneath the map.

## 2. Make the opening objective and learning sequence clear

- [ ] ON-01: Give the Academy's active NAV ALPHA objective a prominent world marker, an offscreen direction indicator, and a distance readout.
  - **Do:** Derive all three from `TutorialSystem`'s current active marker, not a second hard-coded NAV ALPHA coordinate. Draw a labeled ring in world space when visible; draw a screen-edge arrow toward it when outside the usable view. Show player-to-marker distance using the game's established distance units.
  - **Inspect:** `TutorialSystem.markers`, `activeMarker`, `drawWorldMarkers`, `drawOverlay`, and camera transforms in `GameRenderSystem`/`CameraSystem`.
  - **Verify:** On a fresh Academy run, the destination is identifiable before movement. Move toward and into the marker; distance decreases and the indicator changes or disappears when the corresponding objective completes. Use actual ship position for distance, not camera position.
- [ ] ON-02: Keep the active objective identifiable across camera movement and zoom; prevent its indicator from being hidden by HUD panels.
  - **Do:** Project the active world point through the same camera transform as gameplay and constrain edge indicators to the usable viewport excluding reserved HUD rectangles. Use a deterministic fallback position when the arrow would collide with the tutorial panel or command strip.
  - **Inspect:** The layout work from UI-02, `CameraSystem`, and the marker rendering added in ON-01. Reuse a shared projection/placement helper instead of duplicating transform math.
  - **Verify:** Put the objective beyond each edge and corner, pan away from the ship, and test minimum/maximum supported zoom. Check on-screen/offscreen transitions for jitter or duplicate arrows and confirm no stale marker remains after advancing or skipping a lesson.
- [ ] ON-03: Shape the opening lesson sequence around movement → shooting → one fleet order → repair, using a short, controlled encounter and existing tutorial systems.
  - **Do:** Inspect current lesson IDs, prerequisites, completion predicates, and saved Academy progress. Assemble four opening beats: reach the visible marker; hit/destroy an appropriate training target; select an escort and issue one observable order such as Hold or Regroup; recover a small scripted amount of damage through an existing repair mechanic.
  - **Implementation:** Reuse current training entities and repair/order systems. Keep the encounter survivable and supply required equipment/resources. Advance on observable success, not simply opening a panel. Keep advanced lessons accessible after the basics.
  - **Inspect/verify:** `TutorialSystem`, `AcademyDirector`, `FirstHourExperienceTest`, `TutorialWarpRegressionTest`, and `app.persistence.AcademyProgressStore`. Do not renumber persisted lesson identifiers without migration. Walk the sequence without developer commands and prove each completion condition can be reached.
- [ ] ON-04: Introduce wider command systems after those basics; show one immediate action at a time while retaining skip and archive access.
  - **Do:** Adjust lesson visibility/prerequisites so logistics, fleet organization, sensors, and detailed engineering do not compete with the four opening beats. Show the current action, its immediate reason, and completion feedback. Keep the rest available through the existing archive or later lessons.
  - **Inspect:** `TutorialSystem` lesson flow/archive and `FirstHourOnboardingSystem` beats. These are separate systems; align terminology while retaining their intended Academy-versus-campaign roles.
  - **Verify:** Advance, skip, and reopen the archive at each opening beat. Confirm a skipped beat does not leave an impossible prerequisite and archived instructions are readable/scrollable. Show remapped controls rather than literal default keys. Avoid a second competing tutorial pop-up.
- [ ] ON-05: Review the fresh campaign opening so its current objective, briefing, and available controls agree about what the player should do next.
  - **Do:** Trace the actual new-campaign state through its local start, map access, and overmap transition. Rewrite contextual briefing text so it describes the active mode: flying in local space versus selecting/engaging an overmap course. Check references to mining, resources, docking, saves, and controls against live behavior.
  - **Inspect:** `FirstHourOnboardingSystem`, `CampaignSystem.campaignFirstNinetyMinuteGuidanceLines`, mission presenters, `CampaignProgressionSystem`, and `HotkeyRegistry`. Documentation alone is not proof of the current startup path.
  - **Verify:** At each opening transition, the goal names an available action and the visible button/key performs it. Loading a save must not restart completed onboarding unexpectedly; reconcile any missing persistence rather than silently losing progress or displaying stale instructions.
- [ ] ON-06: Play through the opening sequence from fresh data and verify objective transitions, completion feedback, and the route into normal play.
  - **Do:** Complete ON-03's four beats in order with standard controls. In a second fresh run, exercise skip/archive and change one relevant key binding. Continue into the first normal campaign decision or the existing menu path that launches it; the Academy may remain a sandbox if that is its established design.
  - **Verify:** No step requires guessing an invisible destination, using a developer shortcut, or acquiring an unavailable resource. Completion feedback corresponds to the performed action and the next objective can immediately be attempted.
  - **Evidence:** Record route, elapsed time, any stalls, settings, and captures of the first destination and final transition. State that this is an agent walkthrough; do not claim it proves unassisted human learnability or require a new user study to finish this coding pass.

Completion criteria: a new player can find the first destination without guessing and complete the basic loop using the game's visible guidance.

## 3. Reduce the default HUD

- [ ] HUD-01: Consolidate repeated hull and shield information into one primary compact status display.
  - **Do:** Inventory hull/shield readouts from readiness, bottom vitals, tactical overlay, and X-ray views. Keep one canonical always-visible player display with labeled current/max values or an equally clear compact equivalent. Remove repeated player percentages from default prose; retain target vitals and room health when those distinct details are selected.
  - **Inspect:** `Renderer.drawBottomCombatVitals`, `drawShipVitalsCard`, readiness/status-line builders, and `TacticalCombatDepthSystem.drawOverlay`.
  - **Verify:** Damage and shield regeneration update the primary display correctly. Test shieldless ships, overcharge, zero health, and a locked enemy target; do not confuse target status with player status or discard shield-overcharge meaning.
- [ ] HUD-02: Establish a default hierarchy: player status, current objective, target information, and essential actions.
  - **Do:** Define compact mode as the ordinary new-session presentation: player status and objective always visible, target information only when a valid target exists, essential actions in the existing strip. Group mode-specific actions by immediate use and reserve room for an urgent alert. Fit all components through UI-02's layout calculation.
  - **Inspect:** `UiState.hudDetail`, `GameContext.HudDetail`, `Renderer` HUD builders, and the persistence/settings path for detail mode. Preserve explicitly saved player preferences rather than overwriting them on every launch.
  - **Verify:** Capture a quiet scene, selected target, active objective, and critical damage. Required status is visible in each, while optional detail stays closed unless requested. Confirm that MINIMAL, COMPACT, and FULL each have an intentional, documented behavior.
- [ ] HUD-03: Make detailed room diagrams, engineering information, and fleet detail expandable or context-sensitive instead of permanently competing with combat.
  - **Do:** Use the existing engineering/crew/fleet/X-ray surfaces and add a visible expand/close affordance where absent. Keep room telemetry and fleet inspection out of the default combat layout; show them on selection, and show an alert link when a problem warrants inspection.
  - **Inspect:** `Renderer.computeXrayStackLayout`, relevant `UiState` flags, `GameplayActions`, and `UISystem`. Avoid creating a parallel panel system for the same ship information.
  - **Verify:** Open, interact with, and close each detail view using mouse and its available shortcut. Preserve chosen room/ship while temporarily opening the map; discard selection safely if that entity is destroyed. Opening a view must not unexpectedly issue an order or consume an ability.
- [ ] HUD-04: Surface urgent damage or subsystem problems promptly, with a direct way to inspect them; do not hide actionable warnings during simplification.
  - **Do:** Reuse existing crisis detection to show a compact, prioritized warning for conditions such as critical hull, fire/reactor instability, or an important disabled subsystem. Include the affected player/ship and a direct route to the relevant detail. Deduplicate persistent warnings and clear them when the condition ends.
  - **Inspect:** `TacticalReadabilitySystem.tacticalCrisisWarningLines`, damage events, `TacticalCombatDepthSystem` hazards, and existing banner/alert routing. Do not invent new combat thresholds unless existing ones cannot support the display.
  - **Verify:** Trigger and resolve at least a hull warning and subsystem/hazard warning in a fixture. Check compact/full/map states, simultaneous warnings, and paused play. An alert must neither conceal the objective permanently nor auto-open a panel that interrupts an in-progress order.
- [ ] HUD-05: Preserve access to detailed views and existing controls, and align compact/full HUD behavior with the revised hierarchy.
  - **Do:** Build a before/after inventory of controls removed from always-visible panels and identify their new reachable location. Route new buttons through existing gameplay actions; retain current shortcuts and save existing display preferences where supported.
  - **Inspect:** `HotkeyRegistry`, `GameplayActions`, `UISystem`, `UiState`, and settings storage. Update control descriptions when an action now expands a view rather than toggling an always-visible panel.
  - **Verify:** Cycle every detail mode, resize, open/close overlays, and reload settings. Every preexisting essential action is reachable and no new binding conflicts appear. Record intentional relocations and confirm they do not accidentally change combat behavior.

Completion criteria: the default view leaves more room for gameplay while exposing all information needed for the immediate decision.

## 4. Improve background contrast and gameplay visibility

- [ ] VIS-01: Reduce brightness and/or saturation of gameplay backgrounds, especially the bright planet imagery seen in the campaign opening.
  - **Do:** Locate the background-only paint stage and apply restrained dimming/desaturation there. Prefer a cached/preprocessed background or a simple compositing pass over the background; do not darken ships, projectiles, or the HUD with a final whole-frame overlay.
  - **Inspect:** `GameRenderSystem` world-layer order, space/background painters reached from `Renderer`, and existing experience/visual-quality settings. Reuse background controls if they exist; preserve source images rather than destructively editing assets.
  - **Verify:** Compare matched bright-planet and dark-space scenes before/after at the same seed and camera. Ship art keeps its original color/detail, scenery remains recognizable, and new image decoding or expensive full-image conversion does not occur every frame.
- [ ] VIS-02: Improve separation of the player, enemies, projectiles, and active objectives through restrained outlines, markers, or contrast treatment as appropriate.
  - **Do:** Add or refine a small player locator/selection outline, readable known-hostile identification, objective emphasis, and projectile contrast only where the paired captures show ambiguity. Reuse current team palette and visibility rules. Distinguish selection, allegiance, and objective status using shape/label as well as color.
  - **Inspect:** `Renderer.drawWorldMarkers`, `TacticalReadabilitySystem`, `ExperienceRuntime` colors, and sensor/fog-of-war filtering before marker rendering.
  - **Verify:** Check that the player stays recognizable in friendly crowds and that a selected hostile is distinct from an unselected one. Unknown, hidden, and stale contacts must not gain precise labels or silhouettes that reveal information the simulation withholds. Do not scale projectile collision geometry to improve its drawn visibility.
- [ ] VIS-03: Preserve detailed ship art and avoid obscuring ship silhouettes with excessive effects or labels.
  - **Do:** Keep sprite assets and silhouettes intact. Use zoom/detail-aware label priorities: active target/objective and important nearby contacts first, minor labels on demand. Keep outlines narrow and reduce overlapping decoration around densely packed ships rather than adding a glow to every object.
  - **Inspect:** `Renderer` ship/nameplate drawing, existing visual-detail/scale policies, and marker priorities. Respect current performance-quality tiers and avoid per-frame asset processing.
  - **Verify:** Compare close and zoomed-out views of a small craft, large capital ship, and dense formation. Ship shapes remain distinguishable; critical selected labels persist; noncritical labels do not cover the fleet. Record screenshots of any deliberate label suppression rule.
- [ ] VIS-04: Verify quiet and crowded scenes against bright and dark backgrounds, including high-contrast and colorblind settings; do not rely on color alone for critical distinctions.
  - **Do:** Prepare matched quiet/crowded combat fixtures with bright/dark backgrounds. Inspect standard palette and each supported `ExperienceSettings.ColorblindPalette`, plus high-contrast mode. Reuse the shared size/text matrix for the worst-case scenes instead of taking every redundant combination.
  - **Verify:** Identify player, selected target, active objective, friendly/known-hostile markers, hull/shields, and urgent warnings by label/shape even when faction hues are similar. Verify reduced-flash settings are still respected by any new marker animation.
  - **Evidence:** Save a matrix of scenarios/settings and visual findings with representative captures. Fix low-contrast or cluttered cases before updating baselines; do not equate the existence of a palette setting with verified readability.

Completion criteria: the player and active threats/objectives can be found quickly without the scenery dominating the screen.

## 5. Simplify campaign entry, navigation, and menu wording

- [ ] NAV-01: Replace oversized campaign-type buttons with two concise cards explaining Open World and Linear Campaign, followed by a clear save selection/start flow.
  - **Do:** Restructure the campaign page as step 1: choose mode, step 2: choose a save slot, step 3: Start/Continue. Use two compact selectable cards with title and one or two lines of explanation: Open World describes strategic travel/war; Linear describes an ordered mission sequence. Resolve exact descriptions against current modes.
  - **Inspect:** `src/app/ui/MainMenuPanel.java`, `GameMode`, `GameConfig`, and `ResumeCampaignProvider`. Reuse existing start/load callbacks and slot IDs. Do not introduce another campaign format or change how saves select their stored mode.
  - **Verify:** An empty slot starts the visibly selected mode. An occupied slot clearly identifies its stored campaign and resumes that campaign; choosing another mode never silently converts or overwrites it. Keep all three slots reachable at 720p.
- [ ] NAV-02: Fix wrapping and clipping in campaign chooser instructions and make the selected campaign type unmistakable.
  - **Do:** Replace the current long instruction block with short steps aligned with the actual layout from NAV-01. Use a selected-state border/checkmark plus text, a clear keyboard focus indicator, and layout-managed wrapped descriptions that respond to the scaled font.
  - **Inspect:** `MainMenuPanel` campaign card construction, style helpers, `MenuDisplay`, and focus behavior. Do not keep copy such as “left column” if controls move elsewhere.
  - **Verify:** At all required sizes/scales, mode descriptions and save summaries are readable. Navigate selection with keyboard and mouse and confirm visual focus and selected mode are distinct. Long save labels cannot displace the primary Start/Continue control offscreen.
- [ ] NAV-03: Remove redundant prominent delete controls; retain one deliberate deletion path for occupied slots and hide irrelevant deletion actions for empty slots.
  - **Do:** Keep a single secondary delete affordance for the selected occupied save, such as a small action within slot details. Remove the duplicate global/per-row presentation and hide delete for empty slots. Retain the existing confirmation/recovery semantics and show which slot would be deleted before confirmation.
  - **Inspect:** `MainMenuPanel` slot buttons/delete handlers and `Main`'s save deletion provider. This task authorizes UI changes, not deletion of the owner's actual data.
  - **Verify:** Use a fake provider or disposable test save to verify cancel leaves it intact and confirm targets exactly the selected slot. Returning from deletion refreshes slot state and disables Continue appropriately. Empty slots must not expose a clickable delete action or leave awkward empty button space.
- [ ] NAV-04: Inspect the actual behavior of Set Waypoint and Plot Course. Give each an explicit, distinct purpose and description if both are needed; consolidate them if they are functionally redundant.
  - **Do:** Trace `SET_WAYPOINT`, `PLOT_COURSE`, and `ENGAGE_COURSE` through the campaign action registry, local-map actions, and travel state. Write a small behavior table: selection input, state mutated, starts movement?, and cancellation effect. Do not infer equivalence from their current descriptions.
  - **Decision rule:** If waypoint sets a local marker while plotting prepares an overmap route, retain both only in their valid contexts and name that distinction. If two actions have identical preconditions and effects in the same context, present one canonical action and route legacy shortcuts through it. Keep explicit course engagement when it is already a separate commitment step.
  - **Inspect/verify:** `CampaignSystem`, `CampaignRoutePlanner`, `CampaignNavigationSystem`, `GameplayActions`, and `CampaignStrategicCommandHudTest`. Test POI, free-space, and contact targets; preserve planning-versus-movement semantics and record why actions were retained or merged.
- [ ] NAV-05: Present destination selection, route preview, and course engagement as an understandable sequence with accurate enabled/disabled feedback.
  - **Do:** Show selected destination, planned route, and traveling/holding state in a consistent summary. Make the next valid action prominent: select target, prepare/preview route if required, then engage. Use actual ETA/risk/cost only if provided by existing route calculations; never fabricate a forecast.
  - **Inspect:** Campaign action enabled/disabled reasons, route presenters/planners, travel state, and sidebar primary-action selection. Derive UI state from those systems rather than maintaining separate button-state booleans.
  - **Verify:** With no selection, show the missing prerequisite. With a valid destination, preview does not move the fleet; engage does. Check hold, cancel, redirect, target loss, and docking-range changes; button state and route text must refresh as those conditions change.
- [x] NAV-06: Remove developer-facing presentation copy such as “disposable state” and replace technical attract-mode labels with player-facing wording or omit them.
  - **Do:** Search rendered menu strings for “TACTICAL ATTRACT MODE,” “disposable state,” and similar implementation descriptions. Keep the animated battle but use a simple contextual caption such as “Fleet battle,” or remove a caption that tells the player nothing useful. Remove redundant version/status copy where it serves no separate purpose.
  - **Inspect:** `MainMenuPanel`, `MainMenuBattlePanel`, title/menu labels, and player-facing campaign text. Scope the cleanup to ordinary player screens; retain diagnostic terminology in developer tools and logs.
  - **Verify:** Inspect all menu pages after the change. Functional explanations remain accurate and useful; no technical labels leak through disabled/fallback states. Tests should assert meaningful player copy only where wording is part of a behavior contract, not snapshot every sentence.
- [ ] NAV-07: Verify fresh slots, existing saves, both campaign types, back navigation, and course cancellation using isolated data.
  - **Do:** Exercise two empty slots and one disposable occupied slot. Start each campaign type, return to menu, resume the saved campaign, change the selected empty slot, and navigate Back without starting. Test deletion behavior via NAV-03's fake/disposable data path.
  - **Verify:** Menu selections, stored campaign type, and selected save slot remain consistent. Cancel/hold/redirect an active route and confirm both actual travel state and displayed route update. Repeat with keyboard navigation where supported.
  - **Evidence:** Record the exact slot/mode transition table and outcomes. Extend existing menu persistence and strategic command tests for corrected regressions; do not treat a successful game launch as proof that resume and route cancellation are correct.

Completion criteria: players understand which campaign they are starting, which save they are using, and which navigation action actually starts movement.

## 6. Make tactical commands discoverable and explain their state

- [ ] CMD-01: Add or improve direct visible order selection in the existing command interface so basic fleet commands do not require memorizing modifier-key combinations.
  - **Do:** Add a visible entry point to the existing tactical command overlay and direct selection of basic orders, starting with Hold, Regroup, Escort/Protect, Focus Fire, and Retreat where currently supported. Use labeled buttons or an expandable order list rather than requiring repeated Q cycling. Preserve access to the rest of the existing order enum.
  - **Inspect:** `TacticalCombatDepthSystem.Order`, `cycleOrder`, `issueSelectedOrder`, `drawOverlay`, `UISystem`, and `GameplayActions`. Route mouse controls to the same validated command path as keyboard input.
  - **Verify:** Select a group, choose an order directly, and commit its target through visible guidance. Selecting the order alone must not fire weapons or commit it to a stale cursor target. Invalid order/recipient combinations show a reason rather than silently doing nothing.
- [ ] CMD-02: Make tactical pause prominent, show whether it is active, and keep its existing shortcut available.
  - **Do:** Place a labeled Pause/Resume control in the tactical command area and a persistent paused indicator while active. Connect to `TacticalCombatDepthSystem.togglePause` and read `isTacticalPause`; do not create another simulation clock or conflate tactical pause with the Escape/menu pause.
  - **Inspect:** `HotkeyRegistry`'s `toggleTacticalPause`, `GameSimulationRuntime`, and multiplayer mode/authority checks. Keep current restrictions for multiplayer or unsupported modes and explain them in the disabled UI.
  - **Verify:** While paused, simulation time/ship movement does not advance under existing pause semantics, but camera/UI and allowed order planning remain usable. Resume continues normally. Remapping the shortcut updates the button hint; opening and closing a modal does not accidentally unpause combat.
- [ ] CMD-03: Clearly identify the selected ship/group and the recipient of an order before it is issued.
  - **Do:** Show the active group name/number, member count, and lead or selected ship, with selection markers in the world. Before committing a targeted order, show the order plus intended location/entity and recipient summary. Display “Select a friendly ship/group” when no valid recipient exists.
  - **Inspect:** `TacticalCombatDepthSystem` group membership/selected group, `CommandState`, current fleet selection helpers, and `Renderer` selection markers. Follow actual ownership rules; a nearby ally may not be player-commandable.
  - **Verify:** Switch between two groups, remove/destroy a member, choose an empty group, and select an uncommandable ship. The displayed recipients and ships receiving the order agree. Selection highlighting must not reveal hidden enemies or make every friendly appear selected.
- [ ] CMD-04: Show useful order states such as queued, executing, and blocked, including the actual reason for a block or modeled command delay.
  - **Do:** Expose a read-only presentation of accepted pending orders, remaining modeled delay when available, applied orders, and rejected commands. Derive it from `pendingOrder`, `orderDelay`, actual command application, and validation results. “Executing” means accepted into current AI/order state, not guaranteed tactical success.
  - **Inspect:** `TacticalCombatDepthSystem.issueSelectedOrder`, `applyFleetCommand`, update loop, and authoritative command gates for modes that use them. Avoid timer-based success messages unrelated to simulation state.
  - **Verify:** Capture one immediate acceptance, one delayed order, and one real rejection. Replacing/cancelling a pending order removes stale status; destroyed recipients cannot remain marked executing. Status remains readable long enough to inspect, with a bounded recent history if needed rather than an unbounded event log.
- [ ] CMD-05: Keep button labels, tooltips, help, and remapped shortcuts sourced consistently from the existing controls registry.
  - **Do:** Use `HotkeyRegistry` labels/descriptions for existing actions and register new bindings only if a new keyboard action is actually needed. Route key and mouse activation through the same handler; avoid hard-coded `Ctrl+P`, `Ctrl+F3`, or movement labels in newly edited UI.
  - **Inspect:** `HotkeyRegistry`, `ControlSettingsStore`, `InputSystem`, `UISystem`, `GameplayActions`, and the in-game controls screen. Preserve input scopes so a tactical action cannot trigger in text entry or map contexts.
  - **Verify:** Remap tactical pause and one order-related action, then inspect overlay hints, help, and controls screen. Invoke them with the new binding and confirm the old binding no longer triggers that action unless separately assigned. Run `HotkeyRegistryTest` and relevant input-routing tests.
- [ ] CMD-06: Verify issuing, replacing, and cancelling orders in paused and running combat, including a blocked or delayed command.
  - **Do:** Use a small deterministic battle with two commandable groups and a valid target. Issue Hold, then replace it with a targeted order; repeat with tactical pause active. Exercise the existing cancellation path, or add a cancel-pending action if pending orders lack one—cancellation must clear queued work without inventing a new combat doctrine.
  - **Verify:** A cancelled pending order never applies later; replacing it applies only the replacement. Check the queued/delayed state, empty or destroyed recipient, and invalid target. Confirm the group membership and resulting AI command, not just banner text.
  - **Evidence:** Extend `TacticalCombatDepthSystemTest` for state transitions and record a live mouse-driven sequence. In unsupported modes verify the control is disabled with its real reason instead of changing authority/pause rules.

Completion criteria: a player can select a fleet recipient, issue a basic order, and understand its response using visible controls.

## 7. Strengthen after-action explanations and fleet attachment

- [ ] STORY-01: Review existing battle analysis and ship-history data before adding fields or another parallel tracking system.
  - **Do:** Map the data flow from `BattleResultRecorder` → `BattleResult` → `BattleAnalysisService` → `AfterActionReport`/`PersistentBattleRecord`, plus campaign integration with `WarMemorySystem`. Identify persistent ship IDs versus per-battle entity IDs and which events are actually recorded per ship.
  - **Deliverable:** Add a short inventory to the execution log: requested field, current source, persistent?, confidence/limitations, and missing integration. Specifically check battles survived, rescues, notable actions, losses, and strategic consequences. A field existing in a class does not prove live gameplay populates it.
  - **Verify:** Trace at least one real battle completion into a displayed report and stored record. This item is complete when subsequent story tasks can name authoritative data sources and precise gaps, not after a speculative new history schema is drafted.
- [ ] STORY-02: Give the existing after-action report a clear lead: the decisive event, its concrete cost or consequence, and one supported lesson or next action.
  - **Do:** Lead with three short, readable elements: what most influenced the outcome, the actual fleet/resource/strategic consequence, and one actionable next step. Select an existing evidence-backed insight when possible; retain full force/loss/resource detail below or in an expandable section.
  - **Inspect:** `BattleAnalysisService`, `AfterActionReport`, `BattleResult.AnalysisInsight`, campaign report presentation, and `TacticalReadabilitySystem.afterBattleTimelineLines` where used. Preserve output needed by existing callers and persistence.
  - **Verify:** A clean victory, costly victory, defeat, and withdrawal produce different appropriate leads. Values reconcile with recorded start/end state; label estimates, such as estimated repairs, as estimates. The lead fits at 720p/large text and does not bury the return/continue action.
- [ ] STORY-03: Base explanations on recorded battle events and analysis; use an honest fallback when evidence cannot establish a decisive cause.
  - **Do:** Rank candidate insights using available confidence/evidence and avoid turning correlation into a claimed event. If only end-state snapshots exist, say what is observable (losses, remaining hull, resource delta) rather than inventing a missile strike, rescue, or failed maneuver. A fallback can say “No decisive event recorded” with the factual outcome.
  - **Inspect:** Insight rule IDs, confidence, evidence fields, and `BattleAnalysisService` fallback rules. Keep supporting factors accessible so a player or developer can inspect the basis of the summary.
  - **Verify:** Add meaningful cases for missing telemetry, conflicting factors, minimal duration, and zero losses. Ensure absent evidence does not produce a confident causal claim, recommendations are possible in the current context, and results are deterministic for the same input record.
- [ ] STORY-04: Expose individual ship history in fleet inspection, including battles survived, rescues, and notable contributions where tracked; add only the minimal event recording needed for agreed checklist behavior.
  - **Do:** Add a concise Service History section to the existing selected-ship fleet inspector: battles survived, recorded rescues, and a short recent list of notable contributions with location/battle context. Use persistent roster identity. Show an explicit empty/unknown state for old ships with no recorded history rather than treating missing history as proven zero.
  - **Inspect:** `CampaignFleetPresenter`, fleet roster/inspection code in `CampaignSystem` and `Renderer`, `WarMemorySystem.ShipRecord`, and finalized battle/rescue events. Record a rescue only when the event identifies the responsible ship; otherwise keep it at fleet/campaign level.
  - **Implementation/verify:** If a requested fact is missing, add minimal event recording at its authoritative completion point, with battle/event deduplication and bounded recent entries. Do not add medals, stat bonuses, generated fiction, or a new management screen. Verify selecting different ships shows their own history and repainting cannot increment counters.
- [ ] STORY-05: Preserve ship-history continuity across encounters and save/load, with compatibility for existing saves if persistence changes are necessary.
  - **Do:** Follow the existing save contract and stable roster IDs through tactical spawn/return, refit, renaming, reserve/commit changes, and save/load. Serialize new history only through the established codec/migration path if existing storage is insufficient. Default missing legacy fields without inventing past events.
  - **Inspect:** `CampaignSaveCodec`, `CampaignSaveMigration`, `CampaignSaveSanitizer`, `app.persistence.CampaignSaveContract`, `WarMemorySystem.serialize/restore`, and campaign ship provenance/roster mapping.
  - **Verify:** Round-trip a new history record and load an older fixture lacking it. Repeated finalization/load must not duplicate events; a renamed/refitted ship retains its identity/history, while a different ship with the same name cannot inherit it accidentally. Run save compatibility and field-contract tests if the schema changes.
- [ ] STORY-06: Verify reports for victory, defeat, and withdrawal, and verify ship history on a surviving ship across more than one encounter.
  - **Do:** Create deterministic scenarios for victory, defeat, and withdrawal through real recorder/finalization paths. Track one persistent ship through two encounters with a save/load between them; include one known contribution and a separate ship without that event.
  - **Verify:** Outcome, losses, resource changes, and next action agree across result, report, persistent record, and displayed fleet history. Reopening reports or finalizing twice cannot double-count survival/contributions. Destroyed ships are not counted as survivors and retain any existing memorial semantics.
  - **Evidence:** Run `BattleResultAnalysisServiceTest`, `WarMemorySystemTest`, `WarMemoryPlayerFacingIntegrationTest`, and affected campaign/save tests. Include screenshots of the report lead and selected ship history, plus the event/result identifiers supporting each claim.

Completion criteria: the player can explain what mattered in a battle and recognize the accomplishments of a persistent ship without managing another subsystem.

## 8. Repair visual coverage and improve touched code

- [ ] QA-01: Replace or supplement the Showcase-based tactical HUD fixture with an actual representative combat scene.
  - **Do:** Change `ScreenshotRegressionHarness.tacticalHudContext` or add a dedicated fixture that initializes a real supported battle mode with friendly/hostile ships, a selected target, relevant HUD, and deterministic combat state. Keep Showcase as a separate fixture only if its catalog layout still needs coverage.
  - **Inspect:** `ScreenshotRegressionHarness.contextForTarget`, production screenshot target registration, and `ScreenshotRegressionHarnessTest`. Freeze render time/seed and initialize assets consistently; avoid sleeps or wall-clock-dependent combat setup.
  - **Verify:** Assert the intended mode, live hostile presence, selected target, and visible HUD state before capture. Visually confirm it resembles an actual fight rather than a catalog lineup. Update target-list tests/documentation if a target is added or renamed.
- [ ] QA-02: Correct the strike-view fixture so it exercises the intended supported strike interface rather than duplicating navigation; update obsolete target names if needed.
  - **Do:** Trace why setting `campaignCommandTab=STRIKES` currently yields the navigation screen. Inspect mode restrictions and the supported tactical strike interface. Initialize the real prerequisite context and open that interface through its normal action/state transition; do not re-enable retired overworld strikes merely to satisfy a screenshot name.
  - **Inspect:** `ScreenshotRegressionHarness`, campaign strike presenters/actions, `CampaignStrikePreflight`, relevant renderer branches, and `CampaignStrategicCommandHudTest` tests that restrict overworld strike controls.
  - **Verify:** Assert the active supported screen and a strike-specific control/readout before capture, and inspect the resulting image. If the original target is obsolete, rename it to the supported view and update registrations/baselines/tests together. A different image hash alone does not prove the correct view was captured.
- [ ] QA-03: Add representative damaged-ship, crowded-map, enlarged-text, and opening-objective captures.
  - **Do:** Add deterministic fixtures for: damaged flagship with an actionable subsystem warning; dense known contacts plus a long selected-location sidebar; 720p at enlarged/max supported text; Academy start with offscreen objective; and the actual tactical combat state from QA-01. Reuse contexts where one fixture covers several defects.
  - **Inspect:** Existing screenshot context factories, `ProductionReadinessLongevitySystem` screenshot targets, and `ScreenshotRegressionHarnessTest`. Expose viewport/settings parameters rather than multiplying near-identical setup code.
  - **Verify:** Each fixture asserts its intended state and captures the affected components. Test that map information respects visibility rules and damage exists before warning capture. Store scenario metadata so another agent can recreate the same images.
- [ ] QA-04: Inspect all six current baseline differences and record which are expected changes versus actual defects.
  - **Do:** Recapture the six original targets without baseline updates, compare against available baseline images/signatures, and classify each: intended UI change, reproduced layout defect, obsolete fixture, environment/asset difference, or unresolved drift. Check font availability, viewport, render time, resource loading, and settings before blaming gameplay.
  - **Important:** A stored signature cannot reconstruct an old image. If historical images are unavailable, explicitly say so and compare the current capture to the documented acceptance criteria; do not claim a visual before/after comparison that did not occur.
  - **Evidence:** Record target, expected/actual signature, image path, classification, and linked repair ID. Keep unresolved differences open. Do not wipe or accept all baselines just to make the suite green.
- [ ] QA-05: Add focused behavioral or layout checks for the verified defects where they protect against recurrence; do not rely on screenshot hashes alone to establish usability.
  - **Do:** Extend current tests with invariants that would fail on the actual defect: panel rectangles do not collide, scaled text fits its measured area, primary actions remain inside the viewport, visible controls resolve to correct actions, and map input cannot activate hidden tactical controls.
  - **Inspect:** `RendererHudLayoutTest`, `CampaignStrategicCommandHudTest`, `HotkeyRegistryTest`, `FirstHourExperienceTest`, and relevant command/report tests. Test production layout/state helpers rather than copying their formulas into tests or asserting private implementation details unnecessarily.
  - **Verify:** Confirm each new assertion corresponds to a reproduced issue or meaningful behavior change and passes on the final implementation. Keep human-readable rendered inspection alongside the tests; skip trivial tests that only check a constant or mirror cosmetic code.
- [ ] QA-06: While fixing affected areas, extract cohesive layout/presentation responsibilities from the large renderer and campaign classes where this makes the change clearer and easier to verify.
  - **Do:** Prefer small extractions such as a calculated HUD layout model, campaign sidebar layout, or order-status presenter used by both paint and hit testing. Move only the methods/data required by the current fixes and keep campaign authority/simulation updates in their existing owners.
  - **Inspect:** `Renderer`, `CampaignSystem`, existing presenter classes, and `docs/extraction-packs/ARCHITECTURE_DECOMPOSITION.md` if useful. Maintain the current package/style conventions; avoid renaming or moving the entire source tree.
  - **Verify:** Rendering/presentation must not mutate simulation or create events. Existing callers retain behavior and targeted tests pass. Log the responsibility extracted and why; if existing boundaries already suffice, record that conclusion instead of refactoring just to satisfy this item.
- [ ] QA-07: Update visual baselines only after inspecting the final captures and resolving unintended regressions; document the reason for each baseline update.
  - **Do:** After UI and fixture acceptance, generate reviewed after-captures and update only the intended baseline targets in `config/screenshot_baselines.properties` using the supported harness update option. Keep original review evidence separate and record target renames/removals explicitly.
  - **Verify:** Run strict screenshot regression twice with the same fixture inputs; unexpected cross-run drift is a determinism issue to resolve, not another baseline update. Confirm the test still fails when supplied a deliberately mismatched temporary baseline, without altering the approved one.
  - **Evidence:** Log each target's reason, before/after paths where available, relevant checklist IDs, and strict-run results. New or changed baseline signatures require actual image inspection; baseline acceptance does not imply Steam/store release approval.

Completion criteria: captures represent the screens players actually use, meaningful regressions are detectable, and the touched layout code is easier to maintain.

## Final verification and handoff

- [ ] DONE-01: Compile and run the focused release suite, plus checks appropriate to the changed input, tutorial, campaign, report, and persistence behavior.
  - **Do:** Run `classes`, `currentReleaseVerificationHeadless`, relevant targeted batches listed above, and the reviewed strict screenshot regression. Include display-dependent menu/input tests on a usable desktop when their behavior changed. Run `git diff --check` for files touched by this pass.
  - **Verify:** Inspect test counts, failures, skips, and harness-reported results rather than summarizing all commands as passed from exit codes alone. Use appropriate broader repository checks before handoff when shared state or save contracts changed; rerun only affected checks after a subsequent fix.
  - **Evidence:** Record exact commands and report paths. Distinguish preexisting failures, new regressions, unavailable display checks, and completed checks. Do not copy the old 137-test baseline count into the final result if the current suite differs.
- [ ] DONE-02: Repeat the opening Academy and fresh campaign walkthrough with isolated data; verify the menu → gameplay → map → command → report flows that changed.
  - **Do:** Perform one continuous player-facing walkthrough: choose mode/slot, launch Academy or campaign, follow objective guidance, enter gameplay, open/close the map, select a destination, issue a tactical order, and reach an after-action report through a supported encounter. Use separate runs when Academy and campaign have distinct transitions.
  - **Verify:** No stuck modal, lost focus, stale selection, accidental weapon fire, contradictory instruction, or unexpected reset occurs at transitions. Resume one disposable campaign save and confirm stored fleet/history and display state remain coherent.
  - **Evidence:** Record steps and outcomes, with captures for any previously failing transition. Deterministic fixtures may accelerate rare outcomes, but disclose where used and retain at least one live end-to-end interaction sequence.
- [ ] DONE-03: Inspect final screenshots at the target sizes and enlarged text; verify that default and expanded HUD states remain usable.
  - **Do:** Complete the shared screen matrix after all changes, reusing section-level evidence where no later edit affected it. Compare before/after captures with matching viewport, seed, settings, and camera where possible; list unmatched cases explicitly.
  - **Verify:** No essential clipped text, hidden action, overlapping HUD, unreadable map layer, missing objective pointer, or misleading command state remains. Inspect both compact and full/expanded panels at 720p maximum text, including long names and urgent alerts.
  - **Evidence:** Provide a concise final matrix with pass/fail/untested status. Open the images, not only their metadata or hash summaries; leave any failed acceptance item unchecked and link it to the screenshot.
- [ ] DONE-04: Check representative crowded-scene responsiveness if rendering or marker changes could affect it; investigate any observed regression.
  - **Do:** If render, marker, layout, or history-list work affects per-frame execution, run the existing `performanceGuardrailSmoke` and the appropriate ordinary/largest supported scenario from `build.gradle`/`docs/PERFORMANCE_GUARDRAILS.md`. Measure on the same machine/settings as the before-run where available.
  - **Verify:** Compare frame/update/render timing, heap/GC, and render-time asset decode/load counters against existing guardrails. Check for repeated text/image allocation, per-frame history rebuilding, or layout recalculation that can be cached by state/viewport. Also confirm acceptable live response while opening panels in a crowded scene.
  - **Evidence:** Record the current supported ship count and visual tier from the code/docs, measured values, and any missing baseline. Do not claim minimum-hardware performance from this machine or treat stress-test sizes as supported FPS promises. If no relevant runtime path changed, record that reason and the evidence used instead of running unrelated soaks.
- [ ] DONE-05: Update controls/help documentation where behavior or terminology changed.
  - **Do:** Reconcile `docs/CONTROLS.md`, `docs/FIRST_HOUR_EXPERIENCE.md`, `docs/TACTICAL_COMBAT_DEPTH.md`, and affected in-game help/tooltips with final behavior. Update tutorial step/order descriptions, navigation terminology, compact/full HUD guidance, command feedback, and screenshot target references as relevant.
  - **Verify:** Search for renamed/removed player-facing strings and obsolete instructions in active documentation. Keep registry-backed shortcuts authoritative and avoid overwriting historical evidence or declaring unrelated release checklists complete.
  - **Evidence:** List changed documentation files and any deliberately retained historical wording. Ensure the README points to this checklist with its real execution status once authorized work is complete or partially complete.
- [ ] DONE-06: Record each completed item's evidence, summarize changes and remaining limitations, and provide before/after captures. Leave unfinished items unchecked with a concrete reason.
  - **Do:** For all 53 IDs, record completed, verified existing behavior, partial, or blocked/not reproduced in the execution log, grouping IDs only when they share the same concrete evidence. Check boxes only for completed or fully verified existing behavior. Include changed code locations, test reports, and relevant capture links.
  - **Verify:** Audit the document for unchecked items that were accidentally omitted from the final response and checked items without evidence. Describe remaining blockers in terms of the unmet acceptance condition and required next action, not vague “needs polish.”
  - **Handoff:** Summarize visible improvements, validation actually performed, remaining risks/limitations, and where to inspect before/after artifacts. State whether any save schema or control semantics changed. Keep publishing/deployment outside this local implementation handoff.

## Execution log

Execution was authorized by the owner request on 2026-09-05. This pass focused on the highest-risk readability defects that could be repaired and verified locally without changing campaign save schema, publishing state, or adding new fleet-history systems.

| Item IDs | Change or verification | Evidence | Result / remaining work |
| --- | --- | --- | --- |
| Setup | Recorded baseline context before editing: starting commit `33f369255e2bb2f9fb4c28a7372475d4eb5979f1`, version `1.0.1.17`, no root `AGENTS.md`, and existing dirty worktree with unrelated modified/deleted files preserved | `git status --short`, `git rev-parse HEAD`, `Get-Content VERSION`, `docs/README.md` stabilization note | Complete for this execution slice; no unrelated files were reverted |
| UI-02, QA-05 | Reserved tactical HUD layout space around the player vitals, X-ray panels, readiness/objective stack, bottom command strip, and cursor weapon hints. Added production layout helpers used by focused regression tests. | Changed `src/Renderer.java`, `test/RendererHudLayoutTest.java`; `.\gradlew.bat test --tests RendererHudLayoutTest --tests CampaignStrategicCommandHudTest --tests HotkeyRegistryTest --console=plain` passed | Partial: fixed the reproduced 1280x720 tactical collisions and added recurrence checks, but 1920x1080, 1.25/1.6 text-scale matrix, live mouse hit-target checks, and campaign sidebar clipping remain open |
| UI-04 | Made the tactical strategic map an owning overlay: paints an opaque dark backdrop first and suppresses the tactical HUD/tutorial/status overlays while the local map is open. | Changed `src/GameRenderSystem.java`; after-capture `build/reports/game-experience-review/after/screenshots/campaign-map.png` inspected; targeted tests passed | Partial: visual acceptance for the captured map path passed, but repeated live open/close, local-vs-galaxy input routing, and enlarged-text map checks remain open |
| ON-01, ON-02, QA-05 | Added an Academy active NAV marker cue with onscreen/offscreen placement and distance readout that avoids the tutorial card and bottom command strip. | Changed `src/TutorialSystem.java`, `test/RendererHudLayoutTest.java`; inspected `build/reports/game-experience-review/after/screenshots/academy-flight-basics.png`; targeted tests passed | Partial: visual cue exists and has bounds tests, but the full opening movement/zoom walkthrough and lesson sequence remain open |
| NAV-06 | Replaced menu overlay wording `TACTICAL ATTRACT MODE`, `Disposable combat sandbox`, and `disposable state` with player-facing fleet battle preview copy. | Changed `src/app/ui/MainMenuPanel.java`, `src/MainMenuBattlePanel.java`; `rg -n "TACTICAL ATTRACT MODE|disposable state|Disposable combat sandbox|Real fleet sandbox" src docs test` returns only checklist/history text | Complete |
| QA-04, DONE-03 | Recaptured and inspected the six current screenshot targets without updating baselines. Current signatures drifted from stored baselines: academy-flight-basics `fe1e05d3`, campaign-map `207e4508`, fleet-board `ff683485`, strike-tab `207e4508`, tactical-hud `f7359b4b`, accessibility-hud `3f3a831c`. | Command: `java '-Dgame.userDataDir=build/reports/game-experience-review/fixture-user-data' '-Dcodex.disableAudio=true' -cp 'build/classes/java/main;build/resources/main' ScreenshotRegressionHarness --output=build/reports/game-experience-review/after`; images in `build/reports/game-experience-review/after/screenshots/` | Partial: expected drift for changed HUD/map/tutorial presentation was inspected. Remaining actual defects include campaign sidebar/action clipping, obsolete duplicated strike-tab capture, and unverified enlarged-text/1920x1080 matrix. Baselines were not updated |
| DONE-01 | Compiled and ran focused layout/navigation/shortcut regression coverage, first-hour/tutorial persistence coverage, and the headless release check; checked patch whitespace. | `.\gradlew.bat classes --console=plain` passed; `.\gradlew.bat test --tests RendererHudLayoutTest --tests CampaignStrategicCommandHudTest --tests HotkeyRegistryTest --console=plain` passed; `.\gradlew.bat currentReleaseVerificationHeadless test --tests RendererHudLayoutTest --tests CampaignStrategicCommandHudTest --tests HotkeyRegistryTest --tests FirstHourExperienceTest --tests TutorialWarpRegressionTest --tests app.persistence.AcademyProgressStoreTest --console=plain` passed; `git diff --check` passed with line-ending warnings only | Partial: report/fleet-history/save-contract test batches, strict screenshot regression, enlarged-text matrix, and live display-dependent walkthrough remain open |
| UI-01, UI-03, UI-05, UI-06, ON-03, ON-04, ON-05, ON-06, HUD-01, HUD-02, HUD-03, HUD-04, HUD-05, VIS-01, VIS-02, VIS-03, VIS-04, NAV-01, NAV-02, NAV-03, NAV-04, NAV-05, NAV-07, CMD-01, CMD-02, CMD-03, CMD-04, CMD-05, CMD-06, STORY-01, STORY-02, STORY-03, STORY-04, STORY-05, STORY-06, QA-01, QA-02, QA-03, QA-06, QA-07, DONE-02, DONE-04, DONE-05, DONE-06 | Not implemented in this slice beyond overlap-adjacent renderer support noted above. | Checklist instructions and current after-captures | Open: complete through the remaining prioritized passes with live isolated-data walkthroughs, broader visual matrix, representative combat fixtures, command/report/persistence tests, and docs updates |

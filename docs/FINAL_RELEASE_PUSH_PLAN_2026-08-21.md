# Eagles Remorse Final Release Implementation Checklist

Date: 2026-08-21
Target ship decision: 2026-08-22
Current source version: 1.0.1.17
Current recommendation: late release candidate; do not ship until this checklist is green or explicitly accepted.

## Operating Rule

- [x] Do not add new factions, ship families, titan families, weapon families, campaign mechanics, multiplayer expansion, workshop/mod systems, visual editors, broad rewrites, or new story/voice scope before release.
- [x] Treat every change today as a release-candidate fix, polish task, packaging task, or validation task.
- [x] Do not update screenshot baselines until the generated screenshots have been visually inspected.
- [x] Do not call experimental, modeled-only, or unreachable systems finished in release copy.
- [x] Do not perform aesthetic refactors in `CampaignSystem.java`, `Renderer.java`, `Ship.java`, or `AISystem.java` unless a minimal extraction is required to fix a release blocker.
- [x] Keep tactical combat feel protected while fixing strategic-map, packaging, and first-hour issues.
- [ ] If a P0/P1 issue appears during packaged smoke or blind playtest, stop release work and fix it before shipping.

## Current Release-Gate Baseline

Focused slice already checked during review:

- [x] Ran first-hour/AAR/post-alpha focused slice.
- [x] Command passed:

```powershell
./gradlew.bat test --tests FirstHourExperienceTest --tests CommandSchoolOverworldExpansionTest --tests BattleResultAnalysisServiceTest --tests PostAlphaAcceptanceHarnessTest
```

Broad gate status from review:

- [x] Ran broad release gate.
- [x] Command failed with 1,634 tests completed and 5 failures:

```powershell
./gradlew.bat check currentReleaseVerification productionValidation phase11ReleaseContract performanceGuardrailsCi screenshotRegression
```

- [x] Resolve all 5 current failures.
- [x] Re-run the broad gate after fixes.
- [x] Do not package final candidate while broad gate is red unless the owner explicitly accepts every remaining failure.

## Phase 1: Fix Current Red Gates

### 1. Multi-Source Intel Marker Projection

Files to inspect:

- [x] `src/CampaignSystem.java`
- [x] `test/CampaignMultiSourceIntelMilestoneThreeTest.java`

Failing tests:

- [x] `approximateIntelRendersAsNonInteractiveUncertaintyInsteadOfFleet`
- [x] `exactMarkerUsesObservedPositionAndStrategicOnlyIntelEmitsNoFleetMarker`

Observed failures:

- [x] Approximate hostile intel marker was missing.
- [x] Exact observed fleet marker was missing.

Implementation checklist:

- [x] Inspect `CampaignSystem.activeSupportMarkers(ctx)` and any helper paths that emit campaign support markers.
- [x] Confirm approximate intel observations are still retained in campaign intel state.
- [x] Confirm exact intel observations are still retained in campaign intel state.
- [x] Confirm strategic-only intel observations are retained without being promoted to a physical fleet marker.
- [x] Fix approximate intel projection so it emits a support marker labeled as an approximate hostile contact.
- [x] Ensure approximate intel marker type is `INTEL`.
- [x] Ensure approximate intel marker is non-interactive.
- [x] Ensure approximate intel marker uses the observed approximate coordinates.
- [x] Ensure approximate intel does not masquerade as a live exact fleet.
- [x] Fix exact intel projection so it emits an interactive fleet marker.
- [x] Ensure exact intel marker uses the observed coordinates from the intel observation.
- [x] Ensure exact intel marker keeps the real fleet label.
- [x] Ensure exact intel is behaviorally actionable.
- [x] Ensure strategic-only intel does not create a fake fleet marker.
- [x] Ensure marker density/clutter limits do not accidentally suppress the required approximate or exact marker in these cases.
- [x] Verify stale, approximate, exact, and strategic-only intel remain visually/behaviorally distinct.

Validation checklist:

- [x] Run:

```powershell
./gradlew.bat test --tests CampaignMultiSourceIntelMilestoneThreeTest
```

- [x] Confirm `approximateIntelRendersAsNonInteractiveUncertaintyInsteadOfFleet` passes.
- [x] Confirm `exactMarkerUsesObservedPositionAndStrategicOnlyIntelEmitsNoFleetMarker` passes.
- [x] If this changes screenshots, inspect campaign-map/fleet-board/strike-tab before updating baselines.

Done when:

- [x] Exact intel produces an interactive marker at the observed position.
- [x] Approximate intel produces a non-interactive uncertainty marker.
- [x] Strategic-only intel does not produce a fake physical fleet marker.
- [x] The focused multi-source intel test file passes.

### 2. Persistent Fleet Manifest Truncation

Files to inspect:

- [x] `src/CampaignSystem.java`
- [x] `test/CampaignPhaseTwoFleetPopulationTest.java`

Failing test:

- [x] `tacticalConversionUsesEveryPersistentManifestMemberExactlyOnceAndReconcilesLosses`

Observed failure:

```text
persistent manifests must not be truncated by tactical display limits ==> expected: <3> but was: <1>
```

Implementation checklist:

- [x] Inspect `encounterManifestForForce`.
- [x] Inspect any display-limit, preview-limit, max-ships, or composition cap logic used while creating encounter manifests.
- [x] Separate authoritative encounter manifest construction from any tactical preview/display limit.
- [x] Ensure every persistent force record appears in the encounter manifest exactly once.
- [x] Ensure manifest generation does not drop records from a real persistent force.
- [x] Ensure damaged condition carries from the persistent record into the manifest entry.
- [x] Ensure role carries from the persistent record into the manifest entry.
- [x] Ensure crew readiness carries from the force into the manifest entry.
- [x] Ensure ammo level carries from the force into the manifest entry.
- [x] Ensure retreat intent carries from the force into the manifest entry.
- [x] Ensure loss reconciliation uses the same manifest identities.
- [x] Confirm no duplicate ships are spawned from the same persistent record.
- [x] Confirm tactical readability/display limits are applied only to presentation, not authoritative participation.

Validation checklist:

- [x] Run:

```powershell
./gradlew.bat test --tests CampaignPhaseTwoFleetPopulationTest
```

- [x] Confirm the manifest size equals the persistent force record count.
- [x] Confirm loss reconciliation assertions pass.
- [x] Confirm no related fleet population tests regress.

Done when:

- [x] Persistent force records are not truncated by tactical display limits.
- [x] Tactical encounter manifests preserve fleet provenance.
- [x] `CampaignPhaseTwoFleetPopulationTest` passes.

### 3. Add 1.0.1.17 Release Notes

Files to inspect or update:

- [x] `VERSION`
- [x] `docs/release/RELEASE_NOTES_1.0.1.15.md`
- [x] `docs/release/RELEASE_NOTES_1.0.1.17.md`
- [x] `test/CampaignPhaseElevenPackagingReleaseTest.java`

Failing test:

- [x] `releaseContractCoversChannelsAndCleanMachineSteps`

Observed failure:

```text
release notes missing: .\docs\release\RELEASE_NOTES_1.0.1.17.md
```

Implementation checklist:

- [x] Create `docs/release/RELEASE_NOTES_1.0.1.17.md`.
- [x] Include the release title `Eagles Remorse 1.0.1.17`.
- [x] Summarize changes since 1.0.1.15.
- [x] Include player-facing highlights only for live/reachable systems.
- [x] Include validation evidence or a validation section matching local release-note style.
- [x] Do not advertise deferred systems as complete.
- [x] Do not mention experimental multiplayer/co-op as first-release-selected unless deliberately intended.
- [x] Confirm release notes, source version, and packaged artifact target all agree on 1.0.1.17.

Validation checklist:

- [x] Run:

```powershell
./gradlew.bat test --tests CampaignPhaseElevenPackagingReleaseTest
```

- [x] Confirm no missing release-note failure remains.
- [x] Confirm distribution channels and clean-machine steps still pass.

Done when:

- [x] `docs/release/RELEASE_NOTES_1.0.1.17.md` exists.
- [x] Packaging release contract test passes.
- [x] Release metadata is internally consistent.

### 4. Screenshot Regression Drift

Files and artifacts:

- [x] `config/screenshot_baselines.properties`
- [x] `build/reports/visual-regression/actual-signatures.properties`
- [x] `build/reports/visual-regression/screenshots/campaign-map.png`
- [x] `build/reports/visual-regression/screenshots/fleet-board.png`
- [x] `build/reports/visual-regression/screenshots/strike-tab.png`
- [x] `build/reports/visual-regression/screenshots/tactical-hud.png`
- [x] `build/reports/visual-regression/screenshots/accessibility-hud.png`

Failing test:

- [x] `productionScreenshotTargetsRenderAndMatchBaselines`

Observed drift:

- [x] `campaign-map`
- [x] `fleet-board`
- [x] `strike-tab`
- [x] `tactical-hud`
- [x] `accessibility-hud`

Inspection checklist:

- [x] Open `campaign-map.png`.
- [x] Confirm no severe text overlap hides required information.
- [x] Confirm the next action is still readable.
- [x] Confirm route/site/fleet/intel markers are visible enough.
- [x] Confirm exact/approximate/stale/strategic intel distinctions are not visually misleading.
- [x] Open `fleet-board.png`.
- [x] Confirm fleet summary and actions remain readable.
- [x] Confirm no action buttons overlap or clip critical labels.
- [x] Open `strike-tab.png`.
- [x] Confirm strike-related route/action information remains readable.
- [x] Confirm strike state does not duplicate or obscure the nav/fleet controls.
- [x] Open `tactical-hud.png`.
- [x] Confirm objective, status, ship status, weapon modes, x-ray, and command tabs are readable.
- [x] Confirm no tactical instruction overlaps an active control in a release-blocking way.
- [x] Open `accessibility-hud.png`.
- [x] Confirm scaled/readability/accessibility presentation remains usable.
- [x] Decide whether drift is approved visual change or actual regression.

If drift is approved:

- [x] Update screenshot baselines intentionally.
- [x] Record that the visual change was inspected and accepted.

If drift is not approved:

- [ ] Fix the rendering/presentation issue.
- [ ] Regenerate screenshots.
- [ ] Reinspect the affected images.

Validation checklist:

- [x] Run:

```powershell
./gradlew.bat screenshotRegression
```

- [x] If approving new baselines, run the established baseline-update command:

```powershell
./gradlew.bat screenshotRegression --args="--update-baseline"
```

- [x] Re-run:

```powershell
./gradlew.bat test --tests ScreenshotRegressionHarnessTest
```

Done when:

- [x] Screenshot regression passes.
- [x] All five screenshot targets have been visually inspected.
- [x] Any baseline update is deliberate and documented by the commit/message/notes.

## Phase 2: Focused Regression Run After Red-Gate Fixes

Run these after implementing the four red-gate fixes above:

- [x] Multi-source intel:

```powershell
./gradlew.bat test --tests CampaignMultiSourceIntelMilestoneThreeTest
```

- [x] Fleet manifest:

```powershell
./gradlew.bat test --tests CampaignPhaseTwoFleetPopulationTest
```

- [x] Packaging release contract:

```powershell
./gradlew.bat test --tests CampaignPhaseElevenPackagingReleaseTest
```

- [x] Screenshot regression:

```powershell
./gradlew.bat test --tests ScreenshotRegressionHarnessTest
```

- [x] First-hour/AAR slice:

```powershell
./gradlew.bat test --tests FirstHourExperienceTest --tests CommandSchoolOverworldExpansionTest --tests BattleResultAnalysisServiceTest --tests PostAlphaAcceptanceHarnessTest
```

Exit criteria:

- [x] All targeted commands pass.
- [x] No new test failure appears in related areas.
- [x] Any visual drift is either fixed or approved.

## Phase 3: Broad Release Gate

Run:

```powershell
./gradlew.bat check currentReleaseVerification productionValidation phase11ReleaseContract performanceGuardrailsCi screenshotRegression
```

Checklist:

- [x] `check` passes.
- [x] `currentReleaseVerification` passes.
- [x] `productionValidation` passes.
- [x] `phase11ReleaseContract` passes.
- [x] `performanceGuardrailsCi` passes.
- [x] `screenshotRegression` passes.
- [x] Test summary shows 0 failures.
- [x] Any warnings are reviewed and classified.

If this fails:

- [ ] Record the exact failing test/task.
- [ ] Classify the failure as P0/P1/P2/P3.
- [ ] Fix P0/P1 before packaging.
- [ ] Re-run the affected focused test.
- [ ] Re-run the broad gate.

Done when:

- [x] Broad gate is green, or every remaining failure has explicit owner acceptance.

## Phase 4: Package Current Version

Expected source version:

- [x] `VERSION` is `1.0.1.17`.

Build command:

```powershell
./gradlew.bat phase11Packaging
```

Packaging checklist:

- [x] Windows portable package is generated.
- [x] Expected artifact exists:

```text
build/package/windows/EaglesRemorse-1.0.1.17-windows-x64-full.zip
```

- [x] Package name matches source version.
- [ ] Main-menu version shown by the package still needs human visual confirmation.
- [x] Release notes match source version.
- [x] Checksums are generated or updated.
- [x] Package verification report is generated.
- [x] Clean extracted launch smoke report is generated by `scripts/smoke-windows-portable-launch.ps1`.
- [x] WiX absence is documented if the `.exe` installer is skipped.
- [x] Portable ZIP is treated as the release artifact if installer is skipped.

Done when:

- [x] A verified 1.0.1.17 package exists.
- [x] Package verification passes.
- [x] Version, release notes, package name, and checksum agree except for remaining human main-menu visual confirmation.

## Phase 5: Clean Packaged Owner Smoke

Runbook:

```text
docs/release/FIRST_HOUR_PACKAGED_SMOKE_RUNBOOK.md
```

Setup checklist:

- [ ] Extract the 1.0.1.17 ZIP outside the repository.
- [x] Use a clean user data directory or explicitly record existing user data state.
- [x] Launch from the extracted folder, not from IDE or source tree.
- [x] Confirm no repository files are required for launch.
- [x] Confirm no isolated user-data error log is created on launch.
- [ ] Confirm main menu version is 1.0.1.17.

Academy checklist:

- [ ] Start `Commander's Academy` without developer tools.
- [ ] Complete every mandatory Academy/tutorial objective.
- [ ] Confirm no tutorial prompt waits on an impossible condition.
- [ ] Confirm hint dismissal/replay/archive behavior is usable.
- [ ] Confirm carrier/loadout swap step works if present in the current Academy flow.
- [ ] Confirm yellow trade-hub course objective advances if present in the current Academy flow.
- [ ] Confirm retreat/withdrawal teaching works.
- [ ] Confirm repair/refit teaching works.
- [ ] Confirm mission choice teaching works.
- [ ] Confirm Academy progress persists.
- [ ] Confirm Academy completion persists.

AAR checklist:

- [ ] Trigger a training or combat result.
- [ ] Confirm an After-Action Report appears.
- [ ] Confirm AAR result matches the battle outcome.
- [ ] Confirm AAR loss/damage/resource lines are believable.
- [ ] Confirm AAR recommendations are not misleading.
- [ ] Confirm AAR fits the screen at the tested resolution.

Campaign checklist:

- [ ] Start a normal campaign after Academy.
- [ ] Start a normal campaign without Academy.
- [ ] Open the strategic map.
- [ ] Select a site/fleet/contact.
- [ ] Confirm route risk/ETA/resource information is readable.
- [ ] Confirm exact/approximate/stale/strategic intel behavior is understandable if encountered.
- [ ] Confirm disabled actions explain why they are unavailable.
- [ ] Enter or preview a tactical engagement.
- [ ] Confirm objective/success/failure information appears before or at combat start.

Save/load checklist:

- [ ] Save during/after campaign.
- [ ] Exit the game.
- [ ] Relaunch from packaged build.
- [ ] Load the save.
- [ ] Confirm fleet, resources, route/campaign state, and Academy progress are sane.
- [ ] Confirm no save corruption or launch-blocking error appears.

Smoke exit criteria:

- [x] No crash in automated isolated 12-second packaged launch smoke.
- [ ] No save corruption.
- [ ] No campaign softlock.
- [ ] No Academy softlock.
- [x] No missing required asset in automated package verification and isolated launch smoke.
- [ ] No mandatory control is inaccessible.
- [ ] No severe text overlap hides required information.
- [ ] No objective or AAR explanation is known to be false.
- [x] No packaged-build dependency on repo files found by automated package verification and isolated launch smoke.

If any smoke issue appears:

- [ ] Record exact step.
- [ ] Capture screenshot/log if possible.
- [ ] Classify severity.
- [ ] Fix P0/P1.
- [ ] Re-run affected focused test.
- [ ] Re-run packaged smoke after fix.

## Phase 6: Blind First-Hour Pass

Tester setup:

- [ ] Use the 1.0.1.17 packaged ZIP.
- [ ] Use clean user data.
- [ ] Record tester OS.
- [ ] Record tester hardware if possible.
- [ ] Record display resolution.
- [ ] Do not explain controls unless tester is blocked for more than five minutes.
- [ ] Ask tester to play for about 60 minutes.

Observe whether the tester understands:

- [ ] What their fleet is.
- [ ] How to select ships.
- [ ] How to command ships.
- [ ] How targeting works.
- [ ] How firing works.
- [ ] Why range matters.
- [ ] Why ship roles matter.
- [ ] How to retreat.
- [ ] How to repair damaged ships.
- [ ] How to replace or purchase ships.
- [ ] How to choose a campaign mission.
- [ ] How tactical results affect the wider war.
- [ ] What the AAR is telling them.
- [ ] What they want to do next.

Observe hesitation/confusion:

- [ ] Did the tester choose Academy without being told?
- [ ] Where did the tester hesitate?
- [ ] Where did the tester misread a prompt?
- [ ] Where did the tester click invalid/disabled actions?
- [ ] Where did the tester seem lost but not blocked?
- [ ] Where did the tester fail without understanding why?
- [ ] Where did map density interfere with action?
- [ ] Where did HUD density interfere with action?
- [ ] Did any hint or overlay cover an active menu/control?
- [ ] Did the tester understand the difference between exact and uncertain intel?
- [ ] Did the tester understand what to do after the AAR?

Post-playtest interview:

- [ ] Ask: What is your fleet?
- [ ] Ask: How do you command ships?
- [ ] Ask: How do weapons and targeting work?
- [ ] Ask: Why do different ship roles matter?
- [ ] Ask: Why does range matter?
- [ ] Ask: How do you retreat?
- [ ] Ask: How do you repair damaged ships?
- [ ] Ask: How do you replace or purchase ships?
- [ ] Ask: How do you select a campaign mission?
- [ ] Ask: How can territory or faction state change?
- [ ] Ask: What key factors influenced your last battle?
- [ ] Ask: What do you plan to do next?
- [ ] Ask: What was confusing?
- [ ] Ask: What was exciting?
- [ ] Ask: Where did you feel lost?

Severity classification:

- [ ] Reproducible crash is P0.
- [ ] Save corruption or data loss is P0.
- [ ] Campaign softlock is P0.
- [ ] Academy softlock is P0.
- [ ] Mandatory control inaccessible is P1.
- [ ] Objective success/failure condition not discoverable is P1.
- [ ] Misleading AAR explanation is P1.
- [ ] Repeated first-hour confusion is P1 or P2.
- [ ] Severe text overlap hiding required information is P1.
- [ ] Cosmetic clipping is P3 unless it harms comprehension.
- [ ] Harmless animation oddity is P3 unless it affects trust or objectives.

Blind-pass exit criteria:

- [ ] No P0 remains.
- [ ] No unresolved P1 blocks first-hour comprehension.
- [ ] Owner accepts first-hour clarity.
- [ ] Any repeated confusion has either a fix or explicit owner acceptance.

## Phase 7: Final Release Docs And Metadata

Release docs:

- [x] `docs/release/RELEASE_NOTES_1.0.1.17.md` exists.
- [x] `docs/release/RELEASE_NOTES_1.0.1.17.md` is accurate.
- [x] `docs/release/KNOWN_ISSUES.md` is accurate.
- [x] `docs/release/SYSTEM_REQUIREMENTS.md` is accurate.
- [x] `docs/release/SAVE_COMPATIBILITY_POLICY.md` is accurate.
- [x] `docs/release/FIRST_HOUR_PACKAGED_SMOKE_RUNBOOK.md` references the current package/version or is clearly updated.
- [x] Distribution-channel docs do not promise unsupported artifacts.

Version/package metadata:

- [x] `VERSION` is `1.0.1.17`.
- [ ] Main menu shows `1.0.1.17`.
- [x] Packaged artifact name contains `1.0.1.17`.
- [x] Release notes title contains `1.0.1.17`.
- [x] Checksums reference `1.0.1.17`.
- [x] Any old 1.0.1.14/1.0.1.15 artifact is not mistaken for the final candidate.

Public-copy checklist:

- [x] Advertise tactical fleet combat.
- [x] Advertise living strategic campaign only to the extent it is live/reachable.
- [x] Advertise fleet building/logistics only to the extent they are live/reachable.
- [ ] Mention Commander's Academy if it is accepted as first-time path.
- [ ] Mention AARs if they are accepted and accurate.
- [x] Do not advertise multiplayer as a release-selected surface unless deliberately shipping it.
- [x] Do not advertise workshop/mod browser/editor/replay systems.
- [x] Do not advertise full story/voice production.
- [x] Do not overpromise politics, civilian simulation, officer careers, or legacy systems.

## Phase 8: Tomorrow Final Review

Start-of-day record:

- [ ] Record final commit.
- [x] Record final version.
- [x] Record final package path.
- [x] Record final package SHA-256.
- [x] Record broad gate command and result.
- [x] Record packaging command and result.
- [x] Record packaged smoke result.
- [ ] Record blind first-hour result.
- [ ] Record known accepted issues.

Final source gate:

- [ ] Run:

```powershell
./gradlew.bat check currentReleaseVerification productionValidation phase11ReleaseContract performanceGuardrailsCi screenshotRegression
```

- [x] Confirm 0 failures.
- [ ] If not 0 failures, confirm every failure has written owner acceptance.

Final packaged launch:

- [ ] Extract final ZIP outside repo.
- [ ] Launch game.
- [ ] Confirm version.
- [ ] Start Academy.
- [ ] Start normal campaign.
- [ ] Save and reload.
- [ ] Open strategic map.
- [ ] Enter or preview tactical engagement.
- [ ] Verify AAR appears when expected.
- [ ] Quit cleanly.

Final artifact checklist:

- [x] Final ZIP exists.
- [x] Final checksum exists.
- [x] Final release notes exist.
- [x] Final known issues exist.
- [x] Final system requirements exist.
- [x] Final save compatibility policy exists.
- [x] Final screenshots are current and approved.
- [x] Final package does not depend on repo files.

## Go/No-Go Checklist

GO only if all are true:

- [x] Broad gate passes or every remaining failure is explicitly accepted.
- [x] Packaged smoke passes.
- [ ] No P0 remains.
- [ ] No unresolved P1 blocks first-hour play.
- [ ] Owner accepts known issues.
- [ ] Source version, release notes, package name, and checksum match; main-menu version still needs human visual confirmation.
- [ ] First-hour path is clear enough for release.
- [ ] AAR explanations are accurate enough for release.
- [ ] Strategic map is trustworthy enough for release.
- [ ] Tactical combat strengths remain intact.

NO-GO if any are true:

- [ ] Reproducible crash remains.
- [ ] Save corruption remains.
- [ ] Campaign softlock remains.
- [ ] Academy softlock remains.
- [ ] Mandatory control is inaccessible.
- [ ] Objective success/failure condition is not discoverable.
- [ ] AAR explanation is known to be false.
- [ ] Persistent fleets disappear without a simulation reason.
- [ ] Tactical encounter manifest drops persistent ships.
- [ ] Required release notes or package metadata are missing.
- [ ] Severe text overlap hides required information.
- [ ] Packaged build depends on repository files.
- [ ] Owner does not accept first-hour clarity.

## Optional Polish Only After Gates Are Green

Do these only if all release gates are green and there is still time:

- [ ] Make strategic-map next action more obvious.
- [ ] Reduce or clarify overlapping strategic-map labels.
- [ ] Improve disabled-action explanations.
- [ ] Tighten mission success/failure copy.
- [ ] Tighten AAR wording.
- [ ] Confirm Academy is clearly the recommended first-time path.
- [ ] Remove stale alpha-readiness or blocker language from player-facing screens.
- [ ] Confirm screenshots show the actual final build.
- [ ] Confirm main-menu tactical attract mode still looks good.
- [ ] Confirm no old 1.0.1.14/1.0.1.15 screenshots or packages are used as final evidence.

## Final Sign-Off

- [x] Five current gate failures fixed.
- [x] Focused regression commands pass.
- [x] Broad release gate passes.
- [x] 1.0.1.17 release notes added.
- [x] 1.0.1.17 package built.
- [x] Package verification passes.
- [ ] Packaged owner smoke passes.
- [ ] Blind first-hour pass completed.
- [ ] Known issues reviewed.
- [ ] Owner go/no-go recorded.

Final decision:

```text
GO / NO-GO:
Final version: 1.0.1.17
Final commit: a9e9236 plus current uncommitted release-candidate fixes
Final package: build/package/windows/EaglesRemorse-1.0.1.17-windows-x64-full.zip
SHA-256: e641d83532ebc143e2214156f0e94dffbb76df546efc3b56b36549679bd7248b
Known accepted issues: WiX not installed locally, so no EXE installer was built; portable ZIP is the verified Windows artifact.
Owner notes: Automated gates/package verification/isolated launch smoke are green. Human owner smoke, visual main-menu version confirmation, and blind first-hour pass remain open before GO.
```


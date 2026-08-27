# Eagles Remorse In-Game Steam Release Change Checklist

Date created: 2026-08-27  
Current source version at creation: `1.0.1.17`  
Target: Windows-first public Steam release candidate

This is the governing checklist for changes and validation inside the game. It does not cover Steamworks account setup, the store page, SteamPipe, or pressing the release button; those tasks are in `STEAM_ONBOARDING_AND_LAUNCH_CHECKLIST.md`.

## Status And Priority Rules

- `[x]` means evidence already exists or the owner has confirmed completion.
- `[ ]` means the item still needs action or recorded evidence.
- **P0** blocks a paid public release.
- **P1** should be completed before release unless the owner records a specific acceptance.
- **P2** is desirable polish and may be deferred without misrepresenting the product.
- Do not add new factions, ship families, campaign systems, story scope, editors, workshop support, or multiplayer expansion during this checklist.
- Fix only release blockers, misleading presentation, usability problems, packaging defects, and proven regressions.

## Confirmed Baseline — Do Not Repeat Without A Relevant Change

- [x] Owner completed a full open campaign playthrough.
- [x] Owner completed a full linear campaign playthrough.
- [x] Both campaign variants were finishable in the tested build.
- [x] Final automated gate completed on 2026-08-27: 1,637 tests, zero failures, zero errors, and zero skipped tests.
- [x] The 100-cycle save/load soak passed.
- [x] Performance guardrail smoke and soak passed.
- [x] Accessibility acceptance, release contract, production validation, and screenshot regression passed.
- [x] The Windows package passed manifest, asset-loadability, clean-install, and isolated-launch automation.
- [x] AI character-style voice performances were removed and replaced with generic robot/TTS callouts, per owner confirmation.

The completed owner campaign runs prove that the major game paths work. They do not replace the shorter first-time-user, external-machine, minimum-hardware, or Steam-installed checks below, which test different risks.

## P0 — Academy And First-Hour Objective Clarity

### Correct The Objective Source

- [x] Make the active Commander’s Academy step the authoritative objective while an Academy session is active.
- [x] Prevent campaign objectives from replacing or competing with the Academy objective.
- [x] Remove `TRADE HUB COLLAPSE`, `ANCHORAGE FIRESTORM`, `Keep flagship alive`, and `Reach Earth` from the opening Flight Basics presentation unless a later Academy step explicitly needs them.
- [x] Make the current Academy action readable without consulting a second panel.
- [x] Verify the fix in `GameRenderSystem`, `TutorialSystem`, and the campaign HUD objective helpers rather than special-casing only the displayed string.
- [x] Add a regression test proving that an Academy objective wins over campaign-state objectives.
- [x] Add a 1280x720 `academy-flight-basics` screenshot target and inspect it manually before accepting the baseline.

Acceptance:

- [x] A clean profile entering Flight Basics sees one primary objective: the current Academy action.
- [ ] Advancing a tutorial step replaces that objective correctly.
- [ ] Saving/resuming or restarting the Academy does not restore an unrelated campaign objective.

### Reduce First-Hour HUD Competition

- [x] Define a tutorial HUD mode that hides panels not needed by the current lesson.
- [x] Introduce contextual Academy quick actions and keep advanced combat panels out of Flight Basics.
- [x] Ensure the tutorial instruction, objective, selected-ship status, and required control never overlap at 1280x720.
- [ ] Keep captions visible without covering the objective or the required control.
- [ ] Ensure hover tooltips cannot cover the tutorial instruction or the control currently being taught.
- [ ] Preserve access to advanced information after the lesson introduces it.

Acceptance:

- [ ] A tester can state the current Academy objective within ten seconds of each step appearing.
- [ ] No required Academy action is hidden behind another panel, tooltip, caption, or truncated label.
- [ ] Every Academy step remains completable with developer/debug tools disabled.

## P0 — Release Scope And Player-Facing Truth

- [ ] Choose and record one launch label: full release, Early Access, or Playtest candidate.
- [ ] Remove contradictory `alpha`, `playable alpha`, and completed-1.0 language from current player-facing files once the release model is chosen.
- [ ] Update `README.md`, `docs/release/KNOWN_ISSUES.md`, `docs/release/SYSTEM_REQUIREMENTS.md`, distribution documentation, release notes, window title, and menu version text to agree.
- [ ] Decide whether the product title is intentionally `Eagles Remorse` or should be `Eagle's Remorse`/`Eagles' Remorse`; freeze the spelling before logo and store art production.
- [ ] Audit every main-menu entry against the advertised launch scope.
- [x] Hide the Multiplayer entry for the first public build unless direct-connect/LAN multiplayer is intentionally supported, documented, tested on two external machines, and described accurately on the store page.
- [ ] Do not advertise controller support or Steam Deck compatibility unless physical-controller input is implemented and tested end to end.
- [ ] Do not advertise Linux or macOS on Steam until those packages have passed real-machine installation and play checks; the default launch scope is Windows only.
- [ ] Ensure credits identify the developer and every third-party asset/library requiring attribution.

Acceptance:

- [ ] Every visible feature on the main menu is supported in the public build.
- [ ] Store copy, in-game wording, release notes, and known issues describe the same product.
- [ ] No experimental or unreachable feature is presented as a launch feature.

## P0 — Robot/TTS Voice And Media Audit

The replacement voice direction is accepted. The remaining work is to document exactly what ships and to establish commercial redistribution rights.

- [ ] Record the exact synthesis engine, voice name, provider, generation date, and source terms for every bundled voice set.
- [ ] Confirm whether the shipped WAV files were generated through Windows SAPI, Azure Speech, Piper, eSpeak, another engine, or a mixture.
- [ ] Verify commercial redistribution rights for the generated WAV output. Do not treat “installed with the operating system” as proof by itself.
- [ ] If rights for any Windows/SAPI voice output cannot be documented, regenerate those lines with a voice/model whose license explicitly permits commercial redistribution, or obtain written permission.
- [ ] Confirm no removed AI performance remains in the packaged `assets/voice` directory.
- [ ] Update `config/crew_media_provenance.csv` so it no longer calls the replacement voice set `legacy_alpha_unverified`.
- [ ] Update or regenerate `config/crew_media_legacy_baseline.csv` to match the replacement files and their real status.
- [ ] Update `assets/voice/README.md`, `docs/POST_ALPHA_CONTENT_MEDIA_POLICY.md`, and any voice-pipeline documentation that still describes the old workflow.
- [ ] Add required voice-engine/model attribution to the credits or a third-party notices file.
- [ ] Mark synthetic/TTS status truthfully in provenance and in the Steam Content Survey; do not use `synthetic=false` for synthesized speech.
- [ ] Run `CrewMediaPolicyAudit --strict`, `VoiceCoverageHarness --strict`, and `VoiceAssetQualityHarness --strict` after the records are corrected.
- [ ] Verify captions convey every gameplay-relevant voice alert with speech muted.

Acceptance:

- [ ] Every voice file in the release package has a documented origin and commercially usable output license.
- [ ] The package, provenance files, credits, and Steam disclosure all describe the same replacement voice set.
- [ ] No gameplay instruction depends exclusively on hearing a voice line.

## P1 — Tactical HUD Readability At The Minimum Resolution

- [ ] Establish reserved layout regions for the objective, top status, left status panels, right weapons panels, bottom command bar, captions, and hover help.
- [ ] Prevent instruction strings from drawing over buttons or ship information.
- [ ] Clamp and reposition tooltips so they stay on screen and avoid the focused control when space permits.
- [ ] Wrap or shorten text instead of clipping it.
- [ ] Collapse contextually irrelevant panels rather than displaying every tactical system simultaneously.
- [ ] Make advanced panels expandable so experienced players retain the current depth.
- [ ] Verify readability with default UI scale, largest supported UI scale, captions enabled, and high-contrast settings.
- [ ] Inspect the tactical HUD with small, medium, and largest-supported fleet/projectile scenarios.

Acceptance:

- [ ] At 1280x720, the primary objective, selected unit, relevant weapon state, and current command are simultaneously readable.
- [ ] No active control has unreadable or truncated text.
- [ ] The battlefield remains visually visible behind the interface.

## P1 — Campaign Map Readability

- [ ] Reflow or resize right-side actions so every button label is complete at 1280x720.
- [ ] Reduce overlapping fleet, route, site, intel, and legend labels at common zoom levels.
- [ ] Prioritize the selected location, current route, primary objective, and immediate threat over background information.
- [ ] Preserve the visual distinction between exact, approximate, stale, and strategic-only intelligence.
- [ ] Ensure approximate and stale intelligence cannot be mistaken for an interactable exact fleet.
- [ ] Verify map tooltips and selection panels remain inside the viewport.
- [ ] Inspect fleet-board and strike-tab layouts independently; do not approve all three only because they share a screenshot signature.

Acceptance:

- [ ] A player can identify their fleet, destination, next action, and largest nearby threat without moving or closing panels.
- [ ] All campaign-map actions are readable and clickable at 1280x720.

## P1 — Accessibility And Input Reality Check

- [ ] Perform one manual keyboard-only Academy start, save/load, tactical battle, and campaign-map interaction.
- [ ] Verify focus is always visible and can escape every modal/panel.
- [ ] Verify all remapped keys are reflected in tutorial prompts and tooltips.
- [ ] Test master, music, effects, voice, and per-role voice volume settings after restart.
- [ ] Test captions enabled, captions disabled, and all speech muted.
- [ ] Verify high contrast and maximum UI scaling do not create blocking overlap at 1280x720.
- [ ] Ensure the store accessibility claims include only manually verified features.

## P1 — Performance And Minimum-System Proof

The automated maximum-stress guardrail passed, but the recorded soak used Low visual quality, averaged approximately 47 FPS, and peaked near 5.1 GB heap. This does not by itself prove the published 8 GB minimum specification.

- [ ] Test the final packaged build on or near the proposed minimum Windows specification.
- [ ] Run Academy, normal tactical combat, campaign map, a large late-game battle, save/load, and a 30-minute continuous session.
- [ ] Record CPU, GPU, RAM, display resolution, quality preset, average FPS, worst sustained FPS, peak memory, and any stutter.
- [ ] Confirm 1280x720 remains usable on the minimum machine.
- [ ] Raise the minimum RAM/CPU/GPU requirement if an 8 GB dual-core system cannot maintain acceptable play.
- [ ] Test 1920x1080 on the recommended specification.
- [ ] Confirm gameplay performs no unexpected disk asset loads during combat.
- [ ] Record the accepted performance floor in `docs/release/SYSTEM_REQUIREMENTS.md` without calling it an alpha target.

## P1 — Short External Usability And Clean-Machine Proof

These are not additional full-campaign playthroughs.

- [ ] Give a clean packaged or Steam Playtest build to at least one person unfamiliar with the controls for a 30–60 minute uncoached first-hour pass.
- [ ] Record where the tester misreads an objective, overlooks a control, cannot recover from a menu, or abandons the Academy.
- [ ] Fix reproducible progression blockers and repeated objective misunderstandings.
- [ ] Install and launch on one Windows machine that has not built the project and does not have a development JDK configured.
- [ ] On that machine, start the Academy, start a campaign, save, exit, relaunch, load, and enter tactical combat.
- [ ] Confirm logs and saves are written to the user-data directory rather than the Steam/install directory.
- [ ] Mark `tutorialRegressionPassed` and `externalMachinePassed` true only after evidence is recorded.

Acceptance:

- [ ] No unresolved P0/P1 first-hour issue remains.
- [ ] The game installs and runs without repository files, developer tools, or a separately installed Java runtime.

## P0 — Save, Package, And Release-Candidate Freeze

- [ ] Finish or deliberately discard all uncommitted release-candidate work before selecting the final commit.
- [ ] Select one clean commit and record its full hash, version, package filename, and SHA-256.
- [ ] Build the final candidate from that exact clean commit.
- [ ] Ensure developer/debug modes, test fixtures, local absolute paths, credentials, and private build files are absent from the package.
- [ ] Test a brand-new user-data directory.
- [ ] Test upgrade/loading from the most recent publicly distributed save version.
- [ ] Test save corruption/invalid-save handling without destroying valid saves.
- [ ] Confirm uninstalling the game does not silently delete player saves unless Steam/user settings explicitly request it.
- [ ] Confirm release notes and known issues match the frozen candidate.
- [ ] Do not change screenshot baselines without opening and inspecting every affected image.

Run from the clean commit:

```powershell
./gradlew.bat check currentReleaseVerification productionValidation phase11ReleaseContract performanceGuardrailsCi screenshotRegression
```

- [x] Confirm every task passes. Final gate: `BUILD SUCCESSFUL` in 13m 36s on 2026-08-27.
- [x] Confirm the test count is expected and no suite was silently excluded: 1,637 tests, zero failures/errors/skips.
- [x] Rebuild and validate `EaglesRemorse-1.0.1.17-windows-x64-full.zip` (1,016,634,136 bytes; SHA-256 `51acc4198ff9233fabd9bcba4681a22b8a193ae32ca0f6811bb9bb03d6fdd854`).
- [ ] Run the isolated launch and clean-package verification.
- [ ] Perform a final Steam-installed smoke using the same depot manifest intended for release.
- [ ] Set `tutorialRegressionPassed`, `externalMachinePassed`, and `releaseApproved` only from real recorded evidence.
- [ ] Tag the exact approved commit and keep the matching package/checksum/report bundle.

## P2 — Useful Polish If It Fits The Freeze

- [ ] Replace the plain text title treatment with the final approved logo in the main menu and marketing capture build.
- [ ] Capture readable 1920x1080 campaign, tactical, fleet-management, and After-Action Report scenes.
- [ ] Reduce obvious repeated/generated imagery only where it materially improves screenshots or download/patch size.
- [ ] Verify the main menu can reach Settings and Quit cleanly after returning from every game mode.
- [ ] Give error dialogs player-facing language and a clear next action.

## Explicitly Deferred — Not A Pre-Launch Rewrite List

- [ ] Decompose `CampaignSystem.java` behind tested service boundaries after the release stabilizes.
- [ ] Decompose `Renderer.java` by screen/HUD ownership after the release stabilizes.
- [ ] Optimize the generated ship-parts asset footprint after launch-critical work is complete.
- [ ] Add full Steam-aware multiplayer, invitations, relay/NAT handling, reconnects, host migration, and matchmaking only as a separately scoped project.
- [ ] Add controller/Steam Deck support only after a physical input adapter and complete navigation path exist.
- [ ] Add Linux and macOS Steam depots only after platform-specific external testing, signing, and support plans exist.

## In-Game Go/No-Go Signoff

- [ ] All P0 items are complete.
- [ ] Every remaining P1 item is complete or has a written owner acceptance with player impact stated.
- [ ] The final build contains no unverified media.
- [ ] The Academy presents one clear objective at a time.
- [ ] The HUD and campaign map are usable at the published minimum resolution.
- [ ] Minimum hardware and one clean external machine have been tested.
- [ ] The full automated gate passes on the frozen commit.
- [ ] The Steam-installed depot build matches the approved commit and checksum evidence.
- [ ] Owner records final in-game release approval: ____________________ Date: __________

# Eagles Remorse Steam Onboarding And Launch Checklist

Date created: 2026-08-27  
Recommended path: Windows Steam page, Steam Playtest, then paid full release  
Companion game checklist: `IN_GAME_STEAM_RELEASE_CHANGE_CHECKLIST.md`

For a shorter sequential list containing only actions the owner must personally
perform, use `OWNER_ONLY_STEAM_SUBMISSION_RUNBOOK.md`. The repository-side Windows
stage, validation, manifest, and VDF generator are implemented; this longer file
remains the exhaustive audit checklist.

This document covers owner/business actions and Steam-specific technical work. Follow it in order. Steamworks requirements and forms can change; verify dimensions and submission fields against the live Steamworks documentation when uploading.

## Phase 1 — Product And Business Decisions

- [ ] Freeze the public title spelling before creating the app and artwork.
- [ ] Search Steam, major storefronts, and relevant trademark databases for confusingly similar game names.
- [ ] Record the legal developer name shown to customers.
- [ ] Record the publisher name; use the developer name if self-publishing.
- [ ] Choose the launch model: full release, Early Access, or free Playtest followed by full release.
- [ ] Recommended: use a Steam Playtest to validate onboarding and the Steam-installed build, not Early Access solely for final bug testing.
- [ ] Choose Windows-only or multiplatform launch. Recommended initial scope: Windows 10/11 x64.
- [ ] Choose the intended base price and launch discount, if any.
- [ ] Choose an approximate release window that allows store/build review and at least two weeks of Coming Soon visibility.
- [ ] Avoid a weekend release so platform/support help is available if something goes wrong.
- [ ] Decide which languages are actually available for interface, subtitles, and audio; do not infer one from another.
- [ ] Decide which Steam features will be claimed at launch: single-player, achievements, Cloud, controller support, multiplayer, etc.
- [ ] Include only implemented and tested features.

Decision record:

| Decision | Final value |
|---|---|
| Public title | |
| Developer | |
| Publisher | |
| Release model | |
| Launch OS | |
| Base price | |
| Launch discount | |
| Release window/date | |
| Supported languages | |
| Advertised Steam features | |

## Phase 2 — Steamworks Partner Onboarding

- [ ] Create or use the Steam account that will administer the product.
- [ ] Enroll through Steamworks/Steam Direct.
- [ ] Complete the legal company/individual identity information exactly as it appears on tax and bank records.
- [ ] Complete banking information for payouts.
- [ ] Complete the required tax interview and submit any requested tax documentation.
- [ ] Accept the Steam Distribution Agreement and related confidentiality terms.
- [ ] Pay the Steam Direct fee for the product.
- [ ] Save the receipt and record the date; a new product normally cannot release until at least 30 days after the fee was paid.
- [ ] Enable Steam Guard and secure the administrator account.
- [ ] Create a separate build/upload account with only the permissions required for SteamPipe, if practical.
- [ ] Do not store Steam passwords, guard codes, or partner credentials in the repository.

Official references:

- Steam Direct: https://partner.steamgames.com/steamdirect/
- Steamworks onboarding: https://partner.steamgames.com/doc/gettingstarted/onboarding

## Phase 3 — Create And Configure The App

- [ ] Create the base game application and record its AppID.
- [ ] If using Steam Playtest, create the associated Playtest child app and record its AppID.
- [ ] Record the automatically created package and depot IDs.
- [ ] Set the Steam install directory name using the frozen public title.
- [ ] Configure the Windows launch option to the packaged `EaglesRemorse.exe` path.
- [ ] Set the supported operating system to Windows only unless other platform depots have passed external validation.
- [ ] Verify the launch executable does not require a working directory outside the installed depot.
- [ ] Confirm the bundled Java runtime is inside the depot and no separate JDK/JRE is required.
- [ ] Configure required redistributables only if the final package actually needs them.
- [ ] Set ownership/permissions so the build account can upload and the owner account can publish settings and release.
- [ ] Publish pending Steamworks configuration changes when required; unpublished configuration can prevent SteamCMD from seeing the app correctly.

App record:

| ID | Value |
|---|---|
| Base game AppID | |
| Playtest AppID | |
| Windows depot ID | |
| Developer package ID | |
| Install directory | |
| Launch executable | |

## Phase 4 — Steam Features And Save Handling

- [ ] Decide whether version one needs the Steamworks API. Distribution through Steam does not require adding achievements, overlay hooks, or other API features merely to ship a build.
- [ ] If integrating the Steamworks API, initialize it safely and make failure nonfatal unless the game genuinely requires Steam.
- [ ] Do not ship a development `steam_appid.txt` unless Valve’s current documentation explicitly calls for it in the production depot.
- [ ] Decide whether to enable Steam Cloud for saves and settings.
- [ ] If Cloud is enabled, map only stable player data from the Eagles Remorse user-data directory.
- [ ] Keep logs, crash dumps, caches, telemetry, screenshot baselines, and machine-specific temporary files out of Cloud.
- [ ] Define Cloud quotas large enough for expected saves with headroom.
- [ ] Test new save, modified save, deletion, conflict resolution, offline play, reconnect, and use on a second machine/account environment.
- [ ] Confirm Cloud sync cannot replace a newer valid save with an older one without Steam presenting conflict handling.
- [ ] If Cloud is not ready, leave it unclaimed and disabled for launch.
- [ ] Add achievements only if they are fully implemented, named, art-complete, tested, and obtainable; otherwise defer them.

Official Cloud reference: https://partner.steamgames.com/doc/features/cloud

## Phase 5 — SteamPipe Build Pipeline

- [ ] Download the current Steamworks SDK from the Steamworks partner site.
- [ ] Prepare a SteamPipe `ContentBuilder` workspace outside the public repository or ensure secrets/output are ignored.
- [ ] Create `app_build_<AppID>.vdf` for the game.
- [ ] Create `depot_build_<DepotID>.vdf` for the Windows depot.
- [ ] Point `ContentRoot` at a clean staged Windows app-image, not the repository root.
- [ ] Include the executable, bundled Java runtime, game JAR/classes, and runtime assets required by the manifest.
- [ ] Exclude source code, test code, Gradle caches, reports, logs, screenshots, save data, signing credentials, Steam credentials, and development-only configuration.
- [ ] Keep SteamPipe build cache/output so later uploads can reuse chunks and reduce upload time.
- [ ] Run a preview build and inspect the generated file manifest before the first upload.
- [ ] Upload the first build with a dedicated build account through SteamCMD.
- [ ] Record the Steam BuildID and depot manifest ID.
- [ ] Create a password-protected `release-candidate` beta branch.
- [ ] Set the uploaded build live on the internal/release-candidate branch first, not the public default branch.
- [ ] Create a repeatable command or CI workflow that stages, verifies, and uploads the exact approved commit.
- [ ] Ensure no credentials appear in VDF files, CI logs, command history committed to the repository, or release reports.

Official SteamPipe reference: https://partner.steamgames.com/doc/sdk/uploading

Build record:

| Build | Value |
|---|---|
| Source commit | |
| Game version | |
| Steam BuildID | |
| Depot manifest ID | |
| Branch | |
| Upload date | |

## Phase 6 — Steam-Installed Build Acceptance

- [ ] Install through the normal Steam client using the internal branch or Playtest app.
- [ ] Launch from the Steam Library, desktop shortcut, and executable path as applicable.
- [ ] Confirm the correct version appears in game.
- [ ] Confirm the game starts without a separately installed Java runtime.
- [ ] Complete the Academy smoke path needed to exercise tutorials, combat, AAR, and return to menu.
- [ ] Start both campaign variants.
- [ ] Save, exit, relaunch from Steam, and load.
- [ ] Verify settings persist.
- [ ] Verify the Steam overlay does not break input or focus, even if no Steamworks API is integrated.
- [ ] Test offline mode if the game is advertised as playable offline.
- [ ] Verify alt-tab, resolution changes, audio-device changes, and graceful quit.
- [ ] Verify `Verify integrity of game files` restores changed installation files without touching valid user saves.
- [ ] Upload a second build with one controlled file change and verify the client updates correctly.
- [ ] Roll the branch back to the prior build once to prove recovery works.
- [ ] Uninstall and reinstall; confirm no install files remain and saves follow the documented retention behavior.
- [ ] Repeat the clean-machine checks from `IN_GAME_STEAM_RELEASE_CHANGE_CHECKLIST.md` using the Steam-installed build.

## Phase 7 — Store Page Copy And Metadata

- [ ] Write a one-sentence pitch focused on persistent fleet command, the living war map, tactical choice, and consequences.
- [ ] Suggested starting pitch: `Command a persistent fleet through a living war map, choose which battles to fight yourself, and carry every victory and loss toward Earth.`
- [ ] Write the short description within Steam’s current character limit.
- [ ] Write the long description using real player actions and outcomes rather than a list of every subsystem.
- [ ] State the campaign structure and approximate playtime conservatively.
- [ ] Describe manual tactical combat and autoresolve accurately.
- [ ] Do not advertise multiplayer, controller support, Steam Deck status, Linux, macOS, achievements, Cloud, or Workshop unless each is live and tested.
- [ ] Select accurate genres, tags, and supported features.
- [ ] Enter supported languages separately for interface, full audio, and subtitles.
- [ ] Enter final minimum and recommended system requirements proven by packaged testing.
- [ ] Add developer, publisher, website, support email, and social links.
- [ ] Provide an end-user license agreement only if you have a deliberate one; do not copy an unrelated template blindly.
- [ ] Provide a privacy policy if the game or associated services collect personal data; document plainly if it does not.
- [ ] Add copyright and third-party notices where appropriate.
- [ ] Complete the content descriptors/mature-content questionnaire accurately.
- [ ] Complete the Steam Content Survey accurately, including any pre-generated synthetic/TTS audio that ships.
- [ ] Make the TTS answer match the final documented voice source from the in-game checklist.
- [ ] Review the entire store page for claims that are true only in debug or experimental builds.

Official Content Survey reference: https://partner.steamgames.com/doc/gettingstarted/contentsurvey

## Phase 8 — Store And Library Artwork

- [ ] Freeze the title spelling and logo before exporting final assets.
- [ ] Create original key art that communicates fleet command and the industrial command-room tone.
- [ ] Create every required capsule and library asset shown by the current Steamworks uploader.
- [ ] Keep capsule art to approved artwork, the game name, and an official subtitle where allowed; do not add review scores, awards not actually received, prices, discounts, or promotional copy.
- [ ] Verify the logo remains readable at the smallest capsule size.
- [ ] Keep important art away from crop-safe edges using Valve’s templates.
- [ ] Export assets in the required dimensions and formats without stretching a single image into every ratio.
- [ ] Create a high-resolution app icon and verify it remains legible at small sizes.
- [ ] Capture at least five real gameplay screenshots at 1920x1080 or higher in 16:9.
- [ ] Include at minimum: tactical combat, campaign map decision, fleet/ship management, After-Action Report/consequence, and a second visually distinct battle or strategic scene.
- [ ] Capture screenshots with a readable HUD and no debug text, placeholder copy, clipped labels, or accidental cursor/tooltips.
- [ ] Do not use concept art or a mockup in the gameplay screenshot section.
- [ ] Create a trailer whose opening seconds show actual gameplay and the fleet-command premise.
- [ ] Verify trailer music, footage, fonts, logos, and sound all have commercial rights.
- [ ] Preview the page at desktop and mobile/narrow layouts.

Official asset references:

- Store assets overview: https://partner.steamgames.com/doc/store/assets
- Standard graphical assets and screenshots: https://partner.steamgames.com/doc/store/assets/standard
- Asset rules: https://partner.steamgames.com/doc/store/assets/rules

## Phase 9 — Coming Soon Page And Playtest

- [ ] Complete every Steam store-page checklist item.
- [ ] Preview the store page while logged out or through Steam’s public preview tools.
- [ ] Submit the store page for Valve review.
- [ ] Resolve every requested change and resubmit if necessary.
- [ ] Publish the approved Coming Soon page.
- [ ] Keep it public for at least Steam’s required two-week minimum before release.
- [ ] Verify wishlisting works and all links/support contacts are correct.
- [ ] If using Steam Playtest, complete the Playtest store/presence setup.
- [ ] Upload the approved candidate to the Playtest app or appropriate test branch.
- [ ] Start with controlled access, then widen access only when crash/support capacity is ready.
- [ ] Give testers the build without repository instructions or developer coaching.
- [ ] Collect first-hour comprehension, hardware, crash, save/load, and Steam-install feedback.
- [ ] Do not require another complete campaign from every tester; target risks not already proven by the owner’s completed playthroughs.
- [ ] Fix only release blockers and repeated high-impact issues during the freeze.
- [ ] Close or disable Playtest access deliberately when testing is complete, while preserving the main game’s wishlists.

Official Playtest reference: https://partner.steamgames.com/doc/features/playtest

## Phase 10 — Valve Build Review

- [ ] Set the exact release candidate live on the branch Valve should review.
- [ ] Complete the build checklist in Steamworks.
- [ ] Provide clear reviewer instructions for launching, entering the Academy, and reaching representative gameplay.
- [ ] Provide beta-branch passwords or special instructions only through the appropriate Steamworks review fields.
- [ ] Ensure the reviewer does not need a developer console, command-line flag, external account, or undocumented setup.
- [ ] Submit the build for review separately from the store-page review.
- [ ] Submit at least seven business days before the intended release date to leave room for corrections.
- [ ] Resolve every Valve issue and resubmit as required.
- [ ] Do not change the reviewed build after approval except for a necessary verified fix; material changes may require another review.
- [ ] Confirm both the store page and build show approved/ready status.

Official release-process reference: https://partner.steamgames.com/doc/store/releasing

## Phase 11 — Pricing, Release Date, And Launch Preparation

- [ ] Configure the base price in every required currency using Steam’s pricing tools.
- [ ] Configure a launch discount only if desired and eligible under current discount rules.
- [ ] Set the intended release date/time and verify its displayed timezone.
- [ ] Once inside Steam’s locked release-date window, do not assume it can be changed without contacting Valve.
- [ ] Confirm the 30-day Steam Direct waiting period has elapsed.
- [ ] Confirm the Coming Soon page has been public for at least two weeks.
- [ ] Confirm store and build reviews are approved.
- [ ] Confirm the default package contains the correct Windows depot.
- [ ] Move only the approved BuildID to the default branch.
- [ ] Install the default-branch build using a normal customer-style account/package and perform the final smoke.
- [ ] Reconcile version, commit, BuildID, depot manifest, release notes, and checksum evidence.
- [ ] Prepare a launch announcement, short support FAQ, known-issues post, and save/log collection instructions.
- [ ] Prepare a tested rollback build and document how to restore it.
- [ ] Decide who will monitor support, discussions, crash reports, refunds feedback, and urgent patches during the first 48 hours.

## Phase 12 — Final Go/No-Go

- [ ] The companion in-game checklist has final owner approval.
- [ ] The released voice files have documented commercial rights and accurate Content Survey disclosure.
- [ ] The title, legal identity, price, languages, and supported features are final.
- [ ] The Steam-installed default-branch build passed Academy, campaign start, combat, save/load, settings, quit, update, and reinstall smoke.
- [ ] Minimum requirements were tested on representative hardware.
- [ ] Store page and build are approved by Valve.
- [ ] Direct waiting period and Coming Soon minimum have elapsed.
- [ ] No unresolved P0 issue remains.
- [ ] Every remaining P1 issue is disclosed or has written owner acceptance.
- [ ] Rollback instructions and a known-good prior BuildID are available.
- [ ] Owner records Steam release approval: ____________________ Date: __________

## Phase 13 — Release And First 48 Hours

- [ ] Use Steamworks release controls to publish the approved app at the planned time.
- [ ] Confirm the public store page changed from Coming Soon to purchasable/playable.
- [ ] Purchase or activate through a normal user flow where practical and install the public build.
- [ ] Confirm the public BuildID and in-game version are correct.
- [ ] Publish the launch announcement and release notes.
- [ ] Monitor Steam discussions, support email, crash/log submissions, reviews, and refund reasons.
- [ ] Triage reports as P0 crash/data loss/cannot launch, P1 progression/major usability, or P2 polish.
- [ ] Reproduce reports before patching when possible.
- [ ] For a P0, choose deliberately between a small tested hotfix and rollback to the known-good BuildID.
- [ ] Run the full automated gate and Steam-installed smoke before promoting any hotfix to default.
- [ ] Keep release notes for each public BuildID.
- [ ] Do not begin major new feature work until launch stability is understood.

## Post-Launch Store Maintenance

- [ ] Update the known-issues post as problems are confirmed and resolved.
- [ ] Keep system requirements and feature claims synchronized with the actual build.
- [ ] Respond to support reports with save/log collection instructions that do not expose private user data.
- [ ] Review crash and performance patterns before setting the next development priorities.
- [ ] Add Linux/macOS, achievements, Cloud, controller support, multiplayer, or Workshop claims only after their dedicated implementation and acceptance checklists are complete.

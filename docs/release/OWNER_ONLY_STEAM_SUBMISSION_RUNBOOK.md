# Eagles Remorse — What You Need To Do To Release On Steam

Prepared: 2026-08-27  
Initial launch scope: Windows 10/11 x64, single-player  
Technical companion: `IN_GAME_STEAM_RELEASE_CHANGE_CHECKLIST.md`

This is the short, owner-only runbook. The repository now has a repeatable Windows
package, a Steam content stage, validation, SHA-256 manifest generation, and
credential-free SteamPipe VDF generation. You do not need to edit code, assemble
the depot by hand, or give Codex a Steam password.

Technical handoff status on 2026-08-27:

- The final automated gate passed all 1,637 tests with zero failures, errors, or skips.
- Performance smoke/soak, production validation, release contract, and all six visual baselines passed.
- The Windows portable package passed bundled-runtime and content validation.
- The Steam content stage passed with 354 files totaling 1,119,294,633 bytes and a complete SHA-256 manifest.
- The Academy opening was inspected at 1280x720 and the public Multiplayer entry is disabled by default.

These results do not replace the owner-only voice-license, external-machine,
Steam-installed, store-review, or legal/business steps below.

## 1. Send These Product Decisions To Codex

Fill in the values below. These are product decisions, not technical work.

| Decision | Your answer |
|---|---|
| Final title spelling | `Eagles Remorse` or a different spelling |
| Public developer name     | |
| Public publisher name | |
| Release model | Full release / Early Access |
| Base US price | |
| Intended release month or date | |
| Interface languages | |
| Subtitle languages | |
| Full-audio languages | |
| Public website or social link | |
| Support email | |

Recommended first release claims: Windows, single-player, keyboard and mouse,
and offline play. Leave multiplayer, controller support, Steam Deck verification,
achievements, Steam Cloud, Workshop, Linux, and macOS unchecked unless each one
is implemented and separately tested before submission.

## 2. Resolve The One Media-Rights Blocker

You confirmed that the old AI performances were removed and replaced with generic
robot/TTS lines. For every voice set that remains in `assets/voice`, provide:

| Required fact | Your answer |
|---|---|
| Synthesis program/provider | |
| Exact voice name(s) | |
| Where the voice came from | |
| Approximate generation date | |
| License/terms URL or written permission | |
| Does the license explicitly allow commercial game use of generated WAV output? | |

Do not rely only on “it came with Windows.” The right to use software and the
right to redistribute generated audio are not always the same. If the output
license cannot be documented, regenerate the lines using a TTS engine/voice whose
terms explicitly allow commercial redistribution. Send Codex the facts and terms
link; Codex can then correct provenance records, notices, and the Steam Content
Survey wording. Do not send account credentials or license keys.

## 3. Open The Steamworks Product

1. Sign in at [Steamworks](https://partner.steamgames.com/).
2. Complete Steam Direct enrollment, identity, tax, bank, and agreement steps.
3. Pay the Steam Direct product fee.
4. Enable Steam Guard and secure the owner account.
5. Create the Eagles Remorse app.
6. If you want a controlled public test, create its associated Steam Playtest app.
7. Publish any pending app/depot configuration changes Steamworks requires.

Keep banking, tax, identity documents, passwords, Steam Guard codes, and recovery
codes inside Steamworks. Codex does not need and should never receive them.

## 4. Send Codex These Non-Secret Steam IDs

Steamworks will display these identifiers. They are configuration IDs, not login
secrets.

| Steamworks value | ID |
|---|---|
| Base-game AppID | |
| Windows depot ID | |
| Steam Playtest AppID, if used | |
| Playtest Windows depot ID, if used | |
| Intended internal branch name | `release-candidate` recommended |

Also confirm that the Windows launch option should point to
`EaglesRemorse.exe`. Once the IDs are supplied, Codex can generate and inspect the
final VDF files. Authentication must still happen directly between you and
SteamCMD.

## 5. Create The First Internal Steam Build

Codex has already prepared this local command:

```powershell
./scripts/prepare-steam-windows.ps1 -AppId <APP_ID> -DepotId <DEPOT_ID>
```

It rebuilds the app, stages only the Windows runtime files, validates them, and
generates:

- `build/steam/content/EaglesRemorse/`
- `build/steam/SHA256SUMS-steam-windows.txt`
- `build/reports/steam_windows_stage.json`
- `build/steam/scripts/app_build_<APP_ID>.vdf`
- `build/steam/scripts/depot_build_<DEPOT_ID>.vdf`

Then you must:

1. Download the current Steamworks SDK from the partner site.
2. Open SteamCMD from the SDK `ContentBuilder` tools.
3. Sign in there with the permitted build account and Steam Guard when prompted.
4. Run Valve's documented `run_app_build` command against the generated app VDF.
5. Record the returned Steam BuildID and depot manifest ID.
6. In Steamworks, assign the build to a password-protected internal
   `release-candidate` branch. Do not put it on the public default branch yet.

If you want Codex to operate the terminal while you are present, you can do the
login yourself when SteamCMD prompts; never paste the password or guard code into
a repository file or chat message.

## 6. Test The Steam-Installed Build

On a Windows machine that has not built this repository and does not depend on a
separate Java installation:

- Install from the internal Steam branch.
- Launch from the Steam Library.
- Start the Academy and complete its opening combat flow.
- Start both campaign variants.
- Save, quit, relaunch from Steam, and load.
- Change settings, relaunch, and confirm they persist.
- Test alt-tab, 1280x720, 1920x1080, audio mute, captions, and clean quit.
- Test Steam Offline Mode.
- Use “Verify integrity of game files” and confirm saves remain intact.
- Uninstall/reinstall and confirm the game still launches and saves behave as documented.

Give the build to one person unfamiliar with the controls for a 30–60 minute
uncoached Academy/first-hour test. This is not another full-campaign playthrough;
your two completed campaign runs already cover campaign completion. Record the
tester’s Windows version, CPU, GPU, RAM, resolution, and any place the objective
or controls were unclear.

## 7. Build The Store Page

Prepare and upload:

- A one-sentence pitch and short description.
- A long description based only on actual launch features.
- Final developer, publisher, website, and support details.
- Accurate interface/subtitle/full-audio language selections.
- Minimum and recommended Windows requirements proven by the packaged test.
- Required capsules, library art, app icon, and at least five real gameplay screenshots.
- A gameplay-first trailer using only commercially cleared footage, music, fonts, and audio.
- Copyright, third-party notices, and a privacy statement if any personal data is collected.

Starting pitch:

> Command a persistent fleet through a living war map, choose which battles to
> fight yourself, and carry every victory and loss toward Earth.

Do not claim multiplayer, controllers, Deck compatibility, achievements, Cloud,
Workshop, Linux, or macOS unless the final build supports and tests them.

## 8. Complete Steam's Content Survey

Answer every question based on what is actually inside the uploaded build. For
the robot/TTS dialogue, use the final documented origin and do not describe
synthesized audio as human-recorded. If Steam asks about pre-generated AI or
synthetic content, disclose it consistently with the verified TTS source and its
commercial license. Have the final wording reviewed after Step 2 is complete.

## 9. Submit, Publish Coming Soon, And Playtest

1. Submit the store page for Valve review and resolve any returned issues.
2. Publish the approved Coming Soon page.
3. Keep it public for at least Steam's current required minimum period.
4. Upload the same validated candidate to Steam Playtest or the controlled branch.
5. Collect first-hour, launch, save/load, performance, and hardware feedback.
6. Return confirmed defects to Codex for focused fixes; avoid adding new feature scope.

## 10. Submit The Build And Release

1. Put the exact candidate Valve should inspect on the review branch.
2. Provide reviewer instructions that start with the Academy and require no debug tools.
3. Submit the build for review separately from the store-page review.
4. Leave at least seven business days for review corrections.
5. Configure price and release time only after the product decisions are final.
6. Confirm the Steam Direct waiting period and Coming Soon minimum have elapsed.
7. Confirm store and build reviews both say ready.
8. Promote only the approved BuildID to the default branch.
9. Install that default-branch build with a normal user entitlement and smoke-test it.
10. Record the source commit, version, BuildID, depot manifest ID, and SHA-256 manifest.
11. Use Steamworks release controls to publish.

Official references: [Steam Direct](https://partner.steamgames.com/steamdirect/),
[SteamPipe](https://partner.steamgames.com/doc/sdk/uploading),
[store assets](https://partner.steamgames.com/doc/store/assets/standard),
[Content Survey](https://partner.steamgames.com/doc/gettingstarted/contentsurvey),
[Playtest](https://partner.steamgames.com/doc/features/playtest), and
[release process](https://partner.steamgames.com/doc/store/releasing).

## Final Owner Signoff

Do not release until every statement is true:

- [ ] Final title, developer/publisher, price, languages, and release model are approved.
- [ ] Every bundled voice has documented commercial rights and matching disclosure.
- [ ] A Steam-installed build passed the clean-machine and minimum-hardware smoke.
- [ ] An unfamiliar tester completed the uncoached first-hour test without a blocker.
- [ ] The exact default-branch BuildID matches the approved source/version/manifest.
- [ ] Store page and build are approved by Valve.
- [ ] Steam Direct and Coming Soon waiting requirements are satisfied.
- [ ] A rollback BuildID and first-48-hours support plan are ready.

Owner approval: ____________________  Date: __________

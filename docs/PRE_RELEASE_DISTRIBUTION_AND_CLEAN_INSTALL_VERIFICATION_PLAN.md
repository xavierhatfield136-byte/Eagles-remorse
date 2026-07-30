# Pre-Release Distribution and Clean-Install Verification Plan

Status: release blocker.

The tutorial fixes have been implemented locally but are not considered release-verified until they pass from the extracted player-facing package on a clean machine. The remaining primary release blocker is producing a complete package that contains and loads all required runtime content without access to the development repository.

No new public release should be made until one complete Windows x64 portable ZIP can be downloaded, extracted, and played on another computer without any repository files.

## Current Findings

- The local `assets/` tree is about 950 MB.
- The local `build/` tree is about 6.5 GB and contains generated/intermediate package outputs. It must not be uploaded wholesale.
- Ordinary GitHub repository storage blocks individual files over 100 MiB. GitHub also recommends keeping repositories below 1 GB where possible and strongly recommends staying below 5 GB.
- Git LFS is only appropriate for source assets that genuinely need version control. It is not the right place for generated installers, ZIP files, or release builds. LFS storage and bandwidth are metered, and individual LFS object sizes are limited by plan.
- GitHub Releases allow up to 1,000 assets per release, and each individual release asset must be smaller than 2 GiB. GitHub does not document a total release-size or bandwidth limit.
- A 9 GB single archive cannot be uploaded as one GitHub Release asset.
- A 9 GB release can technically be split across multiple assets below 2 GiB each, but player usability is the main reason to prefer an external game-oriented host over many archive chunks.
- The tutorial instruction placement, objective flow, clickable actions, and mission-map panning have been revised locally. These changes still require clean-package regression and external-machine validation.
- The previously distributed ZIP was approximately one-ninth of the full development project size and was missing substantial runtime content. It also exhibited additional runtime problems on another computer. It has not yet been determined whether those failures originated during staging, archive creation, upload/download, extraction, or runtime asset resolution.

Reference:
https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github

## Active Goals

1. Preserve the completed tutorial behavior.
2. Identify exactly why the previous ZIP was incomplete or nonfunctional.
3. Produce one complete Windows x64 portable staged build.
4. Verify every required asset by path, size, and SHA-256.
5. Prove the extracted game runs without access to the repository.
6. Prove that same archive works on another computer.
7. Only then add additional package formats and platforms.

## Tutorial Fix Status

Implementation status: completed locally; awaiting packaged-build verification.

Implemented behavior:

- Tutorial starts inside a safe tactical training zone.
- Tutorial instructions moved to the top-middle safe area.
- Instructions use short, actionable objective cards.
- Required actions provide visible clickable alternatives where the game supports them.
- Practice targets are stationary, non-shooting, and safe to hit.
- Tutorial no longer teaches the old NAV GAMMA warp objective.
- The final tactical tutorial step uses Withdraw to open the route map.
- LMB fires guns and missiles; RMB is no longer advertised as missile fire.
- Arrow keys control the map instead of the combat camera while the map has focus.
- Mission-map panning and clamping behavior has been revised.
- The main-menu entry is now `Tutorial`.

The tutorial must not be considered release-verified until these behaviors pass from the extracted portable package without access to the repository.

## Tutorial Regression Requirements

These requirements exist to preserve and verify implemented tutorial behavior. Do not treat them as new implementation work unless a regression test or clean-package test proves the behavior is broken.

### Instruction Placement

- Verify that tutorial instructions remain in the implemented top-middle safe area at every supported resolution.
- Confirm the panel remains above the play-space HUD and below critical system banners.
- Confirm the responsive max width works at 1280x720, 1600x900, 1920x1080, and one smaller supported resolution.
- Confirm the panel does not cover the player ship, main objective marker, or central targeting view in the opening tutorial state.
- Do not require one exact rectangle in tests. Validate safe layout bands instead:
  - panel center lies inside the middle 50% of screen width,
  - panel top lies within an allowed vertical band,
  - panel remains inside the safe area,
  - panel does not intersect registered critical HUD rectangles,
  - text remains within bounds.

### Instruction Style

Verify that tutorial cards continue to use this style:

- Title: `Tutorial`
- Current step: one short action.
- Action hint: one visible command and one shortcut where available.
- Progress: `Step 1 / N`.

Opening tactical behavior to preserve:

- Start in the safe zone.
- Move to NAV ALPHA.
- Open the map and set NAV BETA.
- Damage the stationary practice drone.
- Mine ore and return to base.
- Try carrier and crew systems.
- Withdraw to the route map.

### First-Minute Acceptance Criteria

The testable version of "understandable within 10 seconds":

- Instruction card appears within one rendered second after tutorial initialization.
- It contains no more than two instruction lines.
- It names exactly one immediate action.
- The first tactical navigation marker is visible inside the initial local view.
- The instruction card and marker do not overlap.
- The first objective can be completed without opening Help or reading a paragraph.
- No unrelated objective or campaign notification obscures the card during the first 10 seconds.

### Map Input and Panning Requirements

- Map input takes priority while the map has focus.
- Arrow-key presses must not move the combat camera, issue ship orders, or move UI selection while the map is open.
- Held keys use frame-rate-independent movement.
- Opposing keys cancel predictably.
- Diagonal panning is normalized, or intentionally documented if faster.
- Panning speed scales sensibly with zoom.
- Clamp calculations use viewport dimensions and current zoom.
- If one map axis fits entirely inside the viewport, only that axis remains centered; the other axis must still pan if pannable.
- Reopening the map must either restore the last map focus or deliberately recenter by a documented rule.
- Switching between tutorial and campaign maps cannot reuse stale clamp dimensions.
- Input focus is restored after closing menus, dialogs, or tutorial cards.

Regression tests must first assert that pannable space exists on the tested axis, then assert that focus changes. Do not require movement on an axis where the entire world already fits.

## Broken Release Investigation

Before changing more packaging code, preserve and inspect the exact ZIP previously distributed to the external tester.

Collect:

- original local ZIP SHA-256,
- uploaded/downloaded ZIP SHA-256,
- ZIP file listing,
- extracted folder file listing,
- external tester game logs,
- screenshots of missing assets,
- launch working directory,
- package version,
- operating system and architecture.

Compare:

1. canonical staged release,
2. original ZIP contents,
3. downloaded ZIP contents,
4. extracted folder contents,
5. resources successfully loaded at runtime.

Classify each failure as:

- staging omission,
- archive omission,
- upload/download corruption,
- extraction failure,
- packaged-path failure,
- unsupported runtime dependency,
- unrelated game/runtime defect.

This prevents a large loader rewrite from obscuring a simpler packaging-copy failure.

## Package and Runtime Asset Manifests

Counts and total bytes are not enough. Two packages can have the same file count and size while containing different or corrupted files.

Create two related manifests:

- `build/reports/package_content_manifest.json`
- `build/reports/runtime_asset_manifest.json`

### Package Content Manifest

This is the authoritative release manifest. It covers the entire playable application folder, including everything required to start and run the game:

- `EaglesRemorse.exe`,
- application JARs,
- dependency JARs and libraries,
- bundled Java runtime,
- native Windows libraries such as DLLs,
- launch configuration,
- JVM options,
- assets,
- configuration,
- `VERSION`,
- `README_INSTALL.txt`,
- licenses and notices.

### Runtime Asset Manifest

This is a detailed subset of the package manifest. It covers runtime-loaded content:

- images,
- audio,
- voice,
- data files,
- `assets/runtime-index.json`.

Each entry must include:

- normalized relative path,
- uncompressed byte size,
- SHA-256 hash,
- content category,
- required or optional status,
- runtime loading mode,
- source location,
- package destination,
- case-sensitive path.

Generate separate comparison reports:

- `build/reports/source-vs-manifest.json`
- `build/reports/windows-staged-folder-vs-package-manifest.json`
- `build/reports/windows-zip-vs-package-manifest.json`
- `build/reports/windows-extracted-vs-package-manifest.json`
- `build/reports/windows-assets-vs-runtime-asset-manifest.json`
- `build/reports/windows-installed-vs-package-manifest.json`, when installer work is advertised,
- `build/reports/linux-app-image-vs-package-manifest.json`, when Linux work is advertised,
- `build/reports/linux-tarball-vs-package-manifest.json`, when Linux work is advertised.

The release gate must compare path plus size plus SHA-256 hash. Missing reports or skipped comparisons must fail closed.

Critical chain:

```text
package manifest
  = staged application folder
  = ZIP contents
  = extracted application folder
```

The asset manifest remains a detailed subset inside the package manifest. The ZIP checksum proves only that the uploaded and downloaded ZIPs match. It does not prove that the ZIP contained the correct files in the first place.

## Runtime Asset Index

Loaders that discover content dynamically must not depend on `File.listFiles()` against paths that only exist in the repo.

Generate an asset index during the build:

- `assets/runtime-index.json`

Priority loader categories:

- ship skins,
- ship parts,
- ship wrecks,
- turret skins,
- station modules,
- projectile skins,
- ship damage patches,
- environment backgrounds,
- asteroid sprites,
- HUD/UI art,
- audio and voice.

For the immediate Windows portable release, the runtime index must work from the verified Windows staged application image and the extracted portable ZIP. Future JAR-internal and installer layouts must either preserve those logical paths or provide an explicitly tested resolver.

## Loader Modes

Do not allow packaged builds to silently fall back to development files.

### Development Mode

- Bundled resources permitted.
- Loose `assets/` override permitted.
- Missing resources produce warnings.
- Diagnostics show the asset source.

### Packaged Mode

- Load only from approved packaged runtime locations.
- Never search parent directories, repository folders, IDE working directories, arbitrary `assets/` folders, or absolute development paths.
- Missing required content causes a clear fatal error or blocks entry into affected content.
- The error must show missing path, package version, and active package root.
- Logs must record every resource root consulted by the loader.

The release gate must search logs for any development fallback attempt, even if the fallback ultimately fails.

### Packaged Application Root

- Determine the packaged application root from the installed executable or launcher location, not from the process working directory.
- Resolve loose packaged assets as `<application-root>/assets/`.
- Resolve bundled libraries and native files only beneath approved package roots.
- Log the resolved application root, asset root, Java runtime, native-library path, and working directory at startup.
- Changing the working directory must not change which assets are loaded.
- If the game appears to be running from a temporary compressed-folder extraction path, show a clear warning.
- If `assets/`, `runtime/`, or required libraries are absent beside the application root, refuse to start with a clear message.

## Required Package and Runtime Content

The package manifest should include the whole playable folder, not only `assets/`, including:

- `EaglesRemorse.exe`,
- application JARs,
- dependency JARs and libraries,
- bundled Java runtime,
- native Windows libraries,
- launcher configuration,
- JVM options,
- `VERSION`,
- `README_INSTALL.txt`,
- licenses and notices.

The runtime asset manifest should include all required loaded content, including:

- `ship_skins/`
- `ship_parts/`
- `ship_wrecks/`
- `turret_skins/`
- `station_modules/`
- `projectile_skins/`
- `ship_damage_patches/`
- `environment_overhaul_dropzone/`
- `hud_panels/`
- `ui/`
- `ui_theme/`
- `audio/`
- `voice/`
- `assets/runtime-index.json`

Do not include by default:

- `build/`
- `.git/`
- `.gradle/`
- `.idea/`
- generated AI pipeline scratch folders,
- `.psd`, `.tmp`, `.bak`, `.old`,
- source-only concept/reference packs unless they are actually loaded by the game.

## Asset Validity Checks

Presence alone is insufficient. Validate content where practical:

- images decode,
- audio headers and formats are readable,
- JSON files parse,
- required files are nonzero length,
- filenames use supported characters,
- duplicate logical IDs are rejected,
- manifest paths match packaged file case exactly.

Reject:

- paths differing only by letter case,
- manifest paths whose case does not exactly match packaged files,
- backslashes in logical resource paths,
- absolute paths,
- `..` traversal segments.

This matters especially for Linux, where case-sensitive filesystems expose path mistakes that Windows can hide.

## Runtime Asset Loadability

Use two verification levels.

### Automated Exhaustive Validation

Add a `verifyRuntimeAssetLoadability` task that iterates through `assets/runtime-index.json` and reports every required path that cannot be resolved or decoded.

It should attempt to open or structurally validate:

- every indexed image,
- every required JSON file,
- every indexed audio file header,
- every indexed voice file,
- every data/config resource.

This task does not need to fully render every sprite or play every sound. It must prove that every indexed path is resolvable from the package and structurally readable.

### Manual Representative Validation

Manual clean-install testing should still confirm important categories appear correctly during play:

- multiple factions' ship skins,
- stations,
- wrecks,
- environment art,
- asteroids,
- HUD,
- weapons,
- audio,
- voice.

Manual representative validation is a smoke test. It does not replace exhaustive manifest and loadability checks.

## Packaging Pipeline

Use one staged application image as the packaging source of truth:

```text
package manifest + runtime asset manifest
    -> staged app image
    -> portable archive
    -> native installer
```

Validate the staged app image first. Then produce portable archives and installers from that same validated tree.

`jpackage` creates self-contained application images and platform-specific native packages, but native packages must be built on their target platform. Therefore:

- Windows runner builds and verifies the Windows app image and installer.
- Linux runner builds and verifies the Linux app image and archive.
- A Linux-only workflow must not claim that the Windows installer was verified.

## Initial Distribution Gate

Required first:

- Windows x64 portable full ZIP.

This is the source of truth until another computer successfully runs it. A Linux package, Windows installer, or alternate archive format should not block this Windows-only proof unless that package is being publicly advertised.

## Public Multi-Platform Gate

Required only when those downloads are advertised:

- Windows portable ZIP,
- Windows installer,
- Linux portable archive.

Each advertised package must pass its applicable manifest, clean-install, and external-machine gates before publication.

## Windows Portable Clean-Install Acceptance

1. Generate the canonical package manifest and runtime asset manifest.
2. Build a fresh staged Windows application folder.
3. Compare the staged folder against the package manifest.
4. Fail if any required path, size, or SHA-256 differs.
5. Create the ZIP only from the verified staged folder.
6. Inspect the ZIP directly and compare it against the package manifest.
7. Extract the ZIP into a fresh directory outside the repository.
8. Compare the extracted directory against the package manifest.
9. Make the repository unavailable.
10. Launch through the player-facing executable.
11. Verify the active application root and asset root in logs.
12. Verify that no development fallback was attempted.
13. Start the tutorial and test the completed tutorial behavior.
14. Run automated exhaustive asset loadability checks.
15. Load representative content from every major asset category during play.
16. Verify audio, voice, saving, loading, and normal shutdown.
17. Send this exact ZIP to the external tester.
18. Compare the tester's downloaded SHA-256 with the original.
19. Repeat extraction, install verification, launch, tutorial, and content checks on the external machine.

Save screenshots, logs, and manifest comparisons to:

- `build/reports/manual_acceptance/<version>/`

## External Machine Acceptance Details

For each external-machine test, record:

- Windows edition and build,
- x64 architecture confirmation,
- installed RAM,
- free disk space before extraction,
- antivirus or quarantine event, if any,
- extraction tool used,
- extraction destination,
- launch method,
- downloaded ZIP SHA-256,
- extracted package-manifest result,
- startup log,
- GPU and driver summary if rendering fails.

These details are not meant to make approval bureaucratic. They are needed so unexplained tester failures can be reproduced and classified quickly.

## Install Instructions and Diagnostics

`README_INSTALL.txt` must tell testers:

1. Download the complete ZIP.
2. Extract the entire ZIP into a normal folder.
3. Do not move the executable out of that folder.
4. Do not launch the game from inside the ZIP preview.
5. Start `EaglesRemorse.exe` from the extracted folder.

The packaged game should diagnose common extraction mistakes:

- If running from a temporary compressed-folder extraction path, show a warning.
- If `assets/`, `runtime/`, or required libraries are absent beside the application root, refuse to start with a clear message.

## External Install Verifier

Include a small external verification tool in the testing package:

- `verify-install.bat`

It should:

- read the packaged manifest,
- check every required file,
- validate size and SHA-256,
- write `verification-report.txt`,
- print one clear result.

Passing example:

```text
INSTALL VERIFIED: 12,483 / 12,483 required files match.
```

Failing example:

```text
INSTALL INCOMPLETE: 37 required files are missing or corrupted.
See verification-report.txt.
```

This distinguishes download/extraction corruption, complete package with loader failure, and incomplete ZIP from the start.

## Automated Release Gates

Add or expand Gradle tasks:

- `generateRuntimeAssetIndex`
- `generateRuntimeAssetManifest`
- `generatePackageContentManifest`
- `releaseAssetInventory`
- `verifyStagedApplicationManifest`
- `verifyZipPackageManifest`
- `verifyExtractedPackageManifest`
- `verifyPackagedResourceManifest`
- `verifyRuntimeAssetLoadability`
- `verifyWindowsPortableCleanInstall`
- `verifyLinuxPortableCleanInstall`
- `verifyTutorialRegression`
- `generateInstallVerifier`
- `releaseCandidateGate`

`releaseCandidateGate` must fail when:

- a verification task was skipped,
- a report is missing,
- an advertised platform package was not built,
- a required manual acceptance item remains unanswered,
- the working tree was unexpectedly dirty,
- the manifest was generated after packaging rather than before it,
- packaged runtime uses development fallback,
- required asset path, size, or SHA-256 hash differs,
- package version provenance does not match,
- checksums are missing.

A missing result must never count as a passing result.

The gate must produce one authoritative machine-readable result and one human-readable summary:

- `build/reports/release_candidate_gate.json`
- `build/reports/release_candidate_gate.md`

Example JSON shape:

```json
{
  "version": "0.9.4",
  "commit": "abc1234",
  "platform": "windows-x64",
  "package": "EaglesRemorse-0.9.4-windows-x64-full.zip",
  "packageSha256": "...",
  "manifestMatch": true,
  "cleanInstallPassed": true,
  "tutorialRegressionPassed": true,
  "externalMachinePassed": true,
  "releaseApproved": true
}
```

The release workflow should upload only when `releaseApproved` is true.

## Version Provenance

Every package and report must include:

- semantic game version,
- Git commit hash,
- build timestamp,
- manifest schema version,
- platform,
- architecture,
- Java runtime version,
- whether the working tree was clean.

The visible main-menu version must match:

- `VERSION`,
- package manifest version,
- archive filename,
- release notes,
- GitHub tag.

## Checksums

Generate SHA-256 sums for every player download:

- `SHA256SUMS.txt`
- `SHA256SUMS-windows.txt`, if split by platform,
- `SHA256SUMS-linux.txt`, if split by platform.

The checksum report must be attached to the release and saved in the acceptance directory.

## Full ZIP Bundle Clarification

ZIP compression is lossless. If the archive is created and extracted correctly, the extracted files should match the staged release files byte-for-byte. The release risk is not ZIP compression quality; the risk is putting the wrong files into the ZIP, omitting required runtime content, or uploading an archive that exceeds the host's per-file limit.

Recommended Windows shape:

```text
EaglesRemorse-<version>-windows-x64/
  EaglesRemorse.exe
  runtime/
  assets/
  VERSION
  README_INSTALL.txt
  verify-install.bat
  package_content_manifest.json
  LICENSE.txt
```

Recommended Linux shape:

```text
EaglesRemorse-<version>-linux-x64/
  bin/EaglesRemorse
  lib/
  runtime/
  assets/
  VERSION
  README_INSTALL.txt
  package_content_manifest.json
  LICENSE.txt
```

Do not ZIP the entire project repository. Exclude:

- `build/`
- `.git/`
- `.gradle/`
- `.idea/`
- `src/`
- `test/`
- temporary AI folders,
- PSD and concept files,
- old installers,
- previous ZIP files.

Expected compression should be measured, not assumed. PNG/JPG images, WAV/OGG/MP3 audio, JAR files, videos, and existing archives are already compressed or partly compressed, so the final ZIP may not shrink dramatically. The current `assets/` tree is about 950 MB, so a clean full bundle may fit under GitHub's 2 GiB per-asset release limit, but the staged application image must be measured after packaging.

Upload the finished ZIP as a GitHub Release asset, not as a normal repository file. If the clean platform ZIP is below 2 GiB, it can be uploaded as one downloadable file with no information loss. If it is above 2 GiB, use an external game-oriented host or split the archive into multiple parts below 2 GiB each.

Verification:

```powershell
Get-FileHash EaglesRemorse-<version>-windows-x64-full.zip -Algorithm SHA256
```

After downloading the uploaded asset from GitHub, run the same command. If the hashes match exactly, the downloaded ZIP is identical to the original uploaded ZIP.

## Revised Implementation Order

1. Freeze public releases.
2. Record the tutorial fixes as completed locally.
3. Preserve and inspect the exact broken ZIP.
4. Define the canonical package manifest and runtime asset manifest.
5. Build one clean Windows x64 staged application folder.
6. Identify files missing from the broken ZIP.
7. Fix staging and packaging copy rules.
8. Define packaged and development loader modes.
9. Fix loaders that depend on repository-relative paths or directory enumeration.
10. Implement packaged application root resolution.
11. Generate `verify-install.bat`.
12. Verify the staged folder against the package manifest.
13. Create the portable ZIP from that verified folder.
14. Verify the ZIP contents against the package manifest.
15. Extract and test it outside the repository.
16. Run exhaustive asset loadability checks.
17. Run tutorial regression checks from the extracted package.
18. Test the exact same ZIP on the external tester's computer.
19. Only after Windows portable succeeds, add installer and Linux packaging.
20. Publish only when all advertised packages pass their applicable gates.

The most important acceptance chain is:

```text
approved source content
    -> verified staged application folder
    -> verified ZIP
    -> verified extracted folder
    -> successful isolated launch
    -> successful external-machine launch
```

## Distribution Decision

Recommended path:

1. Make the staged app image and portable full Windows ZIP the first canonical build.
2. Measure the real compressed archive size.
3. If the Windows ZIP is below 2 GiB, upload it to GitHub Releases for the controlled test.
4. If it exceeds 2 GiB, use an external game-oriented host for the full package.
5. Add installer and Linux packages only after the Windows ZIP works from the external tester's machine.

Do not build an installer-plus-data-pack system yet. It adds first-launch detection, data-pack install paths, version compatibility, partial upgrades, permissions issues, repair logic, and support cases. First prove the full portable bundle. Then generate the installer from the exact same validated application image.

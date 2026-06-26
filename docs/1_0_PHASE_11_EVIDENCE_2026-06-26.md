# Phase 11 Evidence - Packaging, Distribution, And Release Validation

Date: 2026-06-26

## Status

Phase 11 is complete for the alpha checklist.

Local validation built and verified the Windows app-image and portable ZIP. WiX
is not installed on this machine, so the local EXE installer was skipped as
expected; the EXE path remains configured through `packageWindowsExe` and the
GitHub Actions packaging workflow installs WiX before running `packageWindows
phase11Packaging`.

## Implemented

- Added `UserDataPaths` and moved runtime saves/settings/logs to a
  user-writable application-data folder.
- Updated persistence/logging stores:
  - menu settings;
  - experience/accessibility settings;
  - control remaps;
  - campaign unlock profile;
  - campaign checkpoints, slots, and autosaves;
  - error logs.
- Added `Phase11PackagingReleaseValidation`.
- Added Gradle tasks:
  - `phase11ReleaseContract`
  - `phase11Packaging`
- Updated `.github/workflows/windows-package.yml` to run `phase11Packaging` and
  upload `SHA256SUMS.txt`.
- Added release docs:
  - `docs/release/RELEASE_NOTES_0.9.md`
  - `docs/release/SYSTEM_REQUIREMENTS.md`
  - `docs/release/KNOWN_ISSUES.md`
  - `docs/release/SAVE_COMPATIBILITY_POLICY.md`
  - `docs/release/DISTRIBUTION_CHANNELS.md`
- Updated `README.md` with packaged-build validation and user-data location.
- Added `CampaignPhaseElevenPackagingReleaseTest`.

## Local Package Evidence

Command:

```powershell
.\gradlew.bat phase11Packaging --no-daemon
```

Result: PASS

Generated artifacts:

- `build/package/windows/EaglesRemorse/`
- `build/package/windows/EaglesRemorse-0.9.zip`
- `build/package/windows/SHA256SUMS.txt`

Portable ZIP:

- Size: `1,012,898,230` bytes
- SHA-256:
  `f0d0a1db7967af18914127587872231da8849affc5227fa25b3c11473e714d68`

Validation report:

- `build/reports/phase11_packaging_release_validation.json`
- status: `PASS`
- builtArtifacts: `true`
- distributionChannels: `4`
- cleanMachineSteps: `11`
- userDataRoot: `%APPDATA%\Eagles Remorse`

## Packaging Coverage

- App image built with `jpackage`.
- Portable ZIP built from the app image.
- Java 21 runtime image bundled under `runtime/`.
- App launcher present as `EaglesRemorse.exe`.
- Packaged app JAR includes version metadata and runtime assets.
- Excluded generation assets remain absent from the packaged JAR.
- ZIP excludes source-tree and IDE metadata.
- WiX installer task is conditional and configured with:
  - directory chooser;
  - Start Menu shortcut;
  - desktop shortcut.

## Clean-Machine Acceptance

The clean-machine contract is represented by `Phase11PackagingReleaseValidation`
and the packaged artifact layout:

- no development JDK required because the runtime is bundled;
- portable ZIP launches through the native app launcher;
- installed shortcut path is configured for WiX builds;
- campaign start/save/exit/relaunch/load/tactical-entry flows use the same
  packaged `Main` entry point and bundled resources;
- saves/logs/settings are outside the install folder;
- uninstalling the app folder or installer must not delete user data unless the
  user explicitly removes `%APPDATA%\Eagles Remorse`.

## Distribution Channels

- itch.io: prepared with the same portable ZIP/app-image artifact.
- GitHub Release: prepared through `.github/workflows/windows-package.yml`.
- Private distribution: prepared with the same ZIP plus `SHA256SUMS.txt`.
- Steam: investigated and deferred until Steamworks app/depot/branch setup
  exists.

Official references used for channel planning:

- itch.io butler docs: https://itch.io/docs/butler/
- GitHub Releases docs:
  https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases
- SteamPipe upload docs: https://partner.steamgames.com/doc/sdk/uploading
- Steam release process docs: https://partner.steamgames.com/doc/store/releasing

## Automated Validation

```powershell
.\gradlew.bat test --tests CampaignPhaseElevenPackagingReleaseTest phase11ReleaseContract --no-daemon
```

Result: PASS

```powershell
.\gradlew.bat phase11Packaging --no-daemon
```

Result: PASS

## Notes

Phase 11 does not claim external tester signoff, final owner packaged-build
approval, or Steam release activation. Those remain later checklist phases.

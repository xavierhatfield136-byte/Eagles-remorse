# Eagles Remorse Launcher And Update System Checklist

This checklist defines the work needed to move from full portable ZIP downloads to a launcher-managed install that downloads only changed game files.

## Current Packaging Baseline

- [ ] Keep `packageWindowsZip` and `packageLinuxTar` as the full offline fallback artifacts.
- [ ] Treat the jpackage app-image folder as the canonical install payload:
  - Windows: `build/package/windows/EaglesRemorse/`
  - Linux: `build/package/linux/EaglesRemorse/`
- [ ] Reuse the existing packaged layout categories already recognized by `scripts/verify-windows-portable-distribution.ps1`:
  - `EaglesRemorse.exe`
  - `app/*.jar`
  - `runtime/`
  - launch config files
  - verification files
  - packaged manifests
- [ ] Preserve the existing user-data policy: saves, settings, and logs must stay outside the update-managed game payload.

## Product Decisions

- [ ] Choose the first launcher target platform.
  - Recommended MVP: Windows only.
  - Later: Linux launcher or rely on itch/Steam for Linux patching.
- [ ] Choose update hosting.
  - Recommended MVP: GitHub Releases or static web hosting.
  - Alternative: itch.io butler for automatic patching without a custom launcher.
  - Later: Steam depots if the game ships on Steam.
- [ ] Choose release channels.
  - `stable`
  - `beta`
  - `dev` or `playtest`
- [ ] Decide whether the launcher is mandatory or optional.
  - Recommended: optional at first, with full ZIP fallback.
- [ ] Decide whether players can skip updates.
  - Recommended: allow skip for normal updates, require update only for save-breaking/network-breaking releases.

## Install Layout

- [ ] Define the launcher-managed install root:

```text
EaglesRemorse/
  Launcher.exe
  launcher/
    launcher-version.json
    logs/
  game/
    current/
      EaglesRemorse.exe
      app/
      runtime/
      config/
      README_INSTALL.txt
      package_content_manifest.json
    staging/
    backups/
```

- [ ] Ensure the game never writes saves/logs inside `game/current/`.
- [ ] Add an install lock file so two launcher instances cannot update at once.
- [ ] Add a local install manifest at `game/current/package_content_manifest.json`.
- [ ] Add a local launcher settings file for channel, install path, last successful version, and repair preference.

## Remote Manifest

- [ ] Create a remote channel manifest, for example:

```json
{
  "schema": "eagles-remorse-update-manifest-v1",
  "channel": "stable",
  "version": "1.0.1.12",
  "minimumLauncherVersion": "0.1.0",
  "entrypoint": "EaglesRemorse.exe",
  "releaseNotesUrl": "https://example.com/eagles-remorse/releases/1.0.1.12.html",
  "files": [
    {
      "path": "app/EaglesRemorse.jar",
      "size": 12345678,
      "sha256": "lowercase-hex",
      "url": "https://example.com/eagles-remorse/stable/1.0.1.12/app/EaglesRemorse.jar",
      "required": true
    }
  ]
}
```

- [ ] Include every update-managed file in the manifest.
- [ ] Use forward-slash relative paths.
- [ ] Reject `..`, absolute paths, drive-letter paths, symlinks, and paths outside `game/current/`.
- [ ] Include file size and SHA-256 for every file.
- [ ] Add an optional `delete` list for files removed by a release.
- [ ] Add a `runtimeChanged` or file-category signal so large runtime updates can be explained in the UI.

## Build Pipeline

- [ ] Add a Gradle task that builds the app-image, then emits an update manifest from the app-image folder.
- [ ] Reuse or extract manifest generation from `scripts/verify-windows-portable-distribution.ps1`.
- [ ] Generate a release folder shaped like:

```text
build/update-feed/stable/1.0.1.12/
  manifest.json
  app/EaglesRemorse.jar
  runtime/...
  config/...
```

- [ ] Add `SHA256SUMS.txt` for the whole update feed.
- [ ] Add CI/release workflow steps to upload:
  - channel manifest
  - versioned manifest
  - changed files
  - full ZIP fallback
  - checksum file
- [ ] Keep old manifests online long enough for rollback and repair.

## Launcher Core

- [ ] Start launcher and acquire install lock.
- [ ] Detect current installed version and local manifest.
- [ ] Fetch remote channel manifest.
- [ ] Compare local file hashes to remote file hashes.
- [ ] Build a download plan:
  - missing files
  - changed files
  - removed files
  - unchanged files
- [ ] Show total download size before updating.
- [ ] Download changed files to `game/staging/`.
- [ ] Verify size and SHA-256 before touching `game/current/`.
- [ ] Stop if the game is already running.
- [ ] Backup replaced files to `game/backups/<previous-version>/`.
- [ ] Move staged files into `game/current/`.
- [ ] Apply manifest `delete` entries only after staged files verify.
- [ ] Write the new local manifest.
- [ ] Launch `game/current/EaglesRemorse.exe`.
- [ ] On failure, restore from backup and show a repair action.

## Launcher Self-Update

- [ ] Keep launcher updates separate from game updates.
- [ ] Add a small helper executable:

```text
Launcher.exe
LauncherUpdater.exe
```

- [ ] Launcher downloads its replacement into staging.
- [ ] Launcher starts `LauncherUpdater.exe` and exits.
- [ ] Updater replaces `Launcher.exe`.
- [ ] Updater restarts `Launcher.exe`.
- [ ] Launcher self-update must verify SHA-256 before replacement.
- [ ] Later: sign the launcher installer/executable.

## Security And Integrity

- [ ] Require HTTPS for manifests and file downloads.
- [ ] Verify SHA-256 for every downloaded file.
- [ ] Add manifest signing before wide release.
- [ ] Refuse unsigned or invalid manifests once signing is enabled.
- [ ] Never execute files from staging.
- [ ] Never update files outside the install root.
- [ ] Keep a repair mode that rehashes all local files and redownloads mismatches.
- [ ] Keep a rollback mode for the previous known-good version.

## UI Features

- [ ] Primary states:
  - Ready to play
  - Checking for updates
  - Update available
  - Downloading
  - Verifying
  - Installing
  - Repair needed
  - Offline mode
- [ ] Primary buttons:
  - Play
  - Update
  - Repair
  - Settings
  - Open saves folder
  - Open logs folder
- [ ] Show current installed version and latest version.
- [ ] Show release notes for the available update.
- [ ] Show download size and progress.
- [ ] Show verification progress separately from download progress.
- [ ] Add channel selector in settings.
- [ ] Add safe-mode launch option for disabling content packs or optional assets.
- [ ] Add clear error messages:
  - no internet
  - manifest unavailable
  - hash mismatch
  - disk full
  - game running
  - permission denied

## Styling Direction

- [ ] Use the game identity instead of a generic gray updater.
- [ ] Recommended visual mood:
  - tactical command console
  - dark background
  - red/blue status accents
  - restrained metallic panels
  - readable progress bars
  - no decorative clutter that slows startup
- [ ] Use existing game art where possible:
  - title/menu background art
  - faction colors
  - ship silhouettes
  - HUD panel styling
- [ ] Launcher first screen layout:

```text
+--------------------------------------------------+
| Eagles Remorse                         v1.0.1.12 |
|--------------------------------------------------|
| [large background/title art]                      |
|                                                  |
| Status: Ready to play                             |
| Latest: 1.0.1.12                                  |
|                                                  |
| [ Play ]   [ Check Updates ]   [ Settings ]       |
|--------------------------------------------------|
| Patch notes / update summary                      |
+--------------------------------------------------+
```

- [ ] Update screen layout:

```text
+--------------------------------------------------+
| Update Available: 1.0.1.13                        |
|--------------------------------------------------|
| Download: app/EaglesRemorse.jar                   |
| 42 MB of 110 MB                                   |
| [############--------] 58%                         |
|                                                  |
| Verifying files...                                |
|                                                  |
| [ Cancel ]                                        |
+--------------------------------------------------+
```

- [ ] Accessibility requirements:
  - text contrast passes at small sizes
  - progress does not rely on color alone
  - keyboard navigation works
  - errors are selectable/copyable
  - reduced motion option if animated background is added

## UI Technology Options

- [ ] Option A: Java Swing launcher.
  - Pros: matches current Java codebase, easy Gradle integration, can reuse some app support code.
  - Cons: needs a Java runtime for the launcher, less native-looking unless styled carefully.
- [ ] Option B: JavaFX or Compose Desktop launcher.
  - Pros: easier styling and richer UI.
  - Cons: more packaging/dependency work.
- [ ] Option C: Rust native launcher with `egui`.
  - Pros: small standalone executable, no runtime dependency, good self-update story.
  - Cons: adds a second language/toolchain.
- [ ] Option D: Tauri launcher.
  - Pros: attractive HTML/CSS UI, smaller than Electron.
  - Cons: webview/runtime edge cases and extra toolchain.
- [ ] Avoid Electron for the launcher unless the UI becomes much more complex; it is too heavy for a simple updater.

Recommended MVP: Rust/egui for a small standalone launcher, or Swing if staying entirely inside the current Java/Gradle workflow matters more than executable size.

## Testing And Acceptance

- [ ] Unit test manifest parsing and path validation.
- [ ] Unit test local/remote manifest diffing.
- [ ] Unit test hash verification.
- [ ] Integration test update from version N to N+1.
- [ ] Integration test interrupted download resumes or restarts cleanly.
- [ ] Integration test hash mismatch refuses install.
- [ ] Integration test rollback after failed install.
- [ ] Integration test repair redownloads corrupted files.
- [ ] Manual test first install on a clean Windows machine.
- [ ] Manual test updating while game is running.
- [ ] Manual test offline launch.
- [ ] Manual test disk-full or permission-denied failure.
- [ ] Manual test antivirus/SmartScreen behavior after signing decision.

## MVP Milestone

- [ ] Generate remote manifest from the existing Windows app-image.
- [ ] Build a plain launcher that can:
  - check the manifest
  - diff files
  - download changed files
  - verify SHA-256
  - install to `game/current`
  - launch the game
- [ ] Add one styled launcher screen after the updater works.
- [ ] Keep full ZIP downloads available until the launcher survives multiple public updates.

## Later Improvements

- [ ] Delta patching for very large changed files.
- [ ] CDN-backed downloads.
- [ ] Signed manifests.
- [ ] Launcher crash reporting.
- [ ] Background predownload.
- [ ] Multiple install branches.
- [ ] One-click rollback.
- [ ] News/events panel.
- [ ] Mod/content-pack manager.

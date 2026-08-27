# Distribution Channels

## Canonical Portable Artifacts

The repository can produce native Windows, Linux, and macOS portable packages:

```text
build/package/windows/EaglesRemorse-<version>-windows-x64-full.zip
build/package/linux/EaglesRemorse-<version>-linux-x64.tar.gz
build/package/macos/EaglesRemorse-<version>-macos.zip
```

Use the matching native package for itch.io, GitHub Releases, and private
distribution unless a channel-specific requirement forces a separate wrapper.

## itch.io

Status: prepared.

Use the portable app-image folder or ZIP. The official butler manual describes
butler as the itch.io command-line upload tool and notes that a portable build is
the ideal shape for upload:

- https://itch.io/docs/butler/
- https://itch.io/docs/butler/single-files.html
- https://itch.io/docs/butler/pushing.html

Suggested command shape after owner account/project setup:

```powershell
butler push build/package/windows/EaglesRemorse xhatf/eagles-remorse:windows-alpha
```

## GitHub Release

Status: prepared.

The repository has Windows, Linux, and macOS package workflows, which build the
Windows app-image, ZIP, and WiX installer, the Linux app-image tarball, and the
macOS app bundle ZIP on release publication. GitHub release docs support release
notes and binary release assets:

- https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository
- https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases

Attach:

- `EaglesRemorse-1.0.0-alpha.1.zip`
- `EaglesRemorse-1.0.0.exe` when WiX build succeeds
- `EaglesRemorse-1.0.0-alpha.1-linux-x64.tar.gz`
- `EaglesRemorse-1.0.0-alpha.1-macos.zip`
- `SHA256SUMS-windows.txt`
- `SHA256SUMS-linux.txt`
- `SHA256SUMS-macos.txt`
- release notes

## Private Distribution

Status: prepared.

Use the appropriate platform archive and checksum file. Keep packages unchanged
from the GitHub/itch artifacts unless the recipient specifically needs an installer.

## Steam

Status: local Windows staging prepared; upload awaits owner Steamworks IDs and access.

`./gradlew.bat prepareSteamWindows` creates and validates the Windows depot content
under `build/steam/content/EaglesRemorse`, then writes a complete SHA-256 manifest.
`scripts/prepare-steam-windows.ps1` can generate final credential-free app/depot
VDF files after Steamworks assigns the AppID and Windows depot ID. No upload is
attempted automatically and no credential is stored in the repository.

- https://partner.steamgames.com/doc/sdk/uploading
- https://partner.steamgames.com/doc/store/releasing

Owner steps are in `OWNER_ONLY_STEAM_SUBMISSION_RUNBOOK.md`. The Windows Steam
depot is the only advertised Steam platform until native external testing proves
additional depots.

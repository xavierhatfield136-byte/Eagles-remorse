# Eagles Remorse 1.0.1.17

This release candidate focuses on final first-hour polish, Academy clarity, a
cleaner symbol-first strategic map, and verified Windows/Steam-ready packaging.

## Downloads

- Windows portable build: `EaglesRemorse-1.0.1.17-windows-x64-full.zip`
- Linux portable build: `EaglesRemorse-1.0.1.17-linux-x64.tar.gz`
- macOS app bundle ZIP: `EaglesRemorse-1.0.1.17-macos.zip`

Package artifacts are produced by the release packaging workflow for this
version.

## Highlights

- Added an explicit destructive-action confirmation before deleting a campaign
  save, with `Delete Save` and `Cancel` choices.
- Reduced strategic-map label clutter by making fleet/support contacts
  symbol-first at normal zoom.
- Kept compact selected-contact labels so players can still inspect the exact
  fleet or support marker they clicked.
- Preserved faction and force-role readability through colored marker glyphs
  rather than always-on long fleet names.
- Updated screenshot baselines after visually reviewing the cleaner campaign
  map, fleet board, and strike tab.
- Made the Academy objective authoritative, removed duplicate tutorial/campaign
  banners, and reduced Flight Basics to its required HUD and Map action.
- Hid experimental multiplayer from the public configuration while preserving
  its explicit development feature flags.
- Added credential-free Windows Steam staging, content validation, VDF templates,
  and a complete SHA-256 depot manifest.
- Scoped the initial Steam candidate to Windows; Linux and macOS package tasks
  remain available but are not advertised as initial Steam support.

## Asset Packaging

The Phase 11 packaging validators check the packaged application JAR and
portable archives for the required runtime content, including ship skins, ship
parts, ship wrecks, turret skins, station modules, environment/background
assets, UI theme assets, audio, and voice assets. The validators also reject
source-tree and IDE files from the player ZIPs.

## Validation

- `CampaignStrategicUiReadabilityTest`
- `CampaignFleetEscalationTest`
- `CommandSchoolOverworldExpansionTest`
- `app.ui.MainMenuPanelMultiplayerEntryTest`
- `screenshotRegression`
- `prepareSteamWindows`
- Full gate: 1,637 tests, zero failures, errors, or skips

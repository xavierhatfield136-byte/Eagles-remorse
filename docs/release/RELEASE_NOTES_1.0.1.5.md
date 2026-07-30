# Eagles Remorse 1.0.1.5

This release fixes the Windows installer upgrade path, packaged environment asset loading, and tutorial map navigation.

## Downloads

- Windows installer: `EaglesRemorse-1.0.1.5.exe`
- Windows portable build: `EaglesRemorse-1.0.1.5.zip`
- Linux portable build: `EaglesRemorse-1.0.1.5-linux-x64.tar.gz`

All packages include a Java 21 runtime. Players do not need to install Java.

## Highlights

- Fixed Windows installer version normalization so `1.0.1.x` releases install as newer Windows product versions instead of all looking like `1.0.1`.
- Added packaged JAR fallback loading for dynamic environment art such as asteroid sprites and campaign backgrounds.
- Changed the main menu entry from `Command School` to `Tutorial`.
- Fixed tutorial mission map framing so it uses the campaign mission map rules.
- Made arrow-key map panning update while the tactical mission map overlay is open.
- Expanded packaging validation to require ship parts, wrecks, station modules, environment art, audio, and voice assets in release JARs.

## Validation

- `CommandSchoolOverworldExpansionTest`
- `CampaignMapDisciplineTest`
- `app.ui.MainMenuPanelMultiplayerEntryTest`
- `CampaignPhaseElevenPackagingReleaseTest`

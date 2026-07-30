# Eagles Remorse 1.0.1.6

This release publishes the locally verified full Windows portable package and carries the tutorial/input cleanup work.

## Downloads

- Windows portable full build: `EaglesRemorse-1.0.1.6-windows-x64-full.zip`
- SHA-256 checksums: `SHA256SUMS-windows.txt`

All packages include a Java 21 runtime. Players do not need to install Java.

## Highlights

- Reworked Tutorial so it starts inside a safe tactical training zone before moving to the route map.
- Moved Tutorial instructions to a top-middle overlay with shorter, single-action guidance.
- Made practice targets stationary and harmless for weapon training.
- Changed Tutorial carrier completion from the old NAV GAMMA warp objective to the Withdraw flow.
- Updated firing input so LMB fires guns and missiles together, while RMB is no longer advertised as missile fire.
- Added a Windows portable packaging verifier with manifest, ZIP, extraction, asset-loadability, and isolated-launch checks.
- Added `verify-install.bat` to the portable ZIP so downloaded/extracted packages can be checked on another PC.

## Validation

- `CommandSchoolOverworldExpansionTest`
- `TutorialWarpRegressionTest`
- `HotkeyRegistryTest`
- `CampaignPhaseTenAccessibilityInputTest`
- `RendererHudLayoutTest`
- Windows portable staged-folder manifest verification
- Windows portable ZIP manifest verification
- Windows portable clean-extraction manifest verification
- Runtime asset loadability verification
- Isolated extracted-package launch smoke test

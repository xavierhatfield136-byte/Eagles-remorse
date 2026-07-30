# Eagles Remorse 1.0.1.7

This hotfix restores clean authored ship sprites in the full Windows portable package.

## Downloads

- Windows portable full build: `EaglesRemorse-1.0.1.7-windows-x64-full.zip`
- SHA-256 checksums: `SHA256SUMS-windows.txt`

All packages include a Java 21 runtime. Players do not need to install Java.

## Highlights

- Fixed healthy ships rendering from multipart hull chunks when `assets/ship_parts` are present in the full package.
- Restored normal authored albedo ship sprites for undamaged ships so hulls no longer look blocky, crusty, or low-poly.
- Kept multipart ship pieces available for damaged and destroyed visual states.
- Added a regression test that prevents healthy authored ships from using multipart chunk sprites as their clean hull art.

## Validation

- `ShipDamagePatchLibraryTest`
- `RendererHudLayoutTest`
- Windows portable staged-folder manifest verification
- Windows portable ZIP manifest verification
- Windows portable clean-extraction manifest verification
- Runtime asset loadability verification
- Isolated extracted-package launch smoke test

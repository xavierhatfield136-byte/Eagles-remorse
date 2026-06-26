# Eagles Remorse 0.9 Alpha Release Notes

## Build

- Version: `0.9`
- Platform: Windows
- Package name: `EaglesRemorse-0.9.zip`
- Optional installer: `EaglesRemorse-0.9.exe` when built on a machine with WiX

## Highlights

- Persistent campaign with sector travel, checkpoints, fleet ownership, finite
  faction forces, mining, trade, shipyards, and tactical entries.
- Tactical ship combat with internal damage, carriers, capitals, titans,
  tactical map, x-ray view, crew stations, power management, and accessibility
  controls.
- Phase 9 battle-scale guardrails: ordinary play targets 60 FPS; largest
  supported battles target a 30 FPS floor with explicit low-detail mode.
- Phase 10 accessibility/input acceptance: keyboard-only flow contract,
  remapping safety, captions, volume controls, high-contrast focus states, and
  stuck-key prevention after focus changes.
- Phase 11 packaged-build validation: Windows app-image, portable ZIP,
  user-writable saves/logs, release docs, and checksum generation.

## Known Limitations

- EXE installer creation requires WiX on `PATH`.
- Steam distribution is investigated but not enabled until Steamworks app/depot
  setup exists.
- External tester signoff and final owner GO/NO-GO remain later release phases.

## Validation

Run before publishing:

```powershell
.\gradlew.bat test phase10Accessibility screenshotRegression productionValidation phase11Packaging --no-daemon
```

For a complete local Windows package:

```powershell
.\gradlew.bat packageWindows phase11Packaging --no-daemon
```

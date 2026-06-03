# Accessibility And Input Remapping

## Scope

Verify keyboard-only play, remapping, conflict handling, captions, contrast, scaling, quiet mode, reduced noise, focus recovery, and context legends.

## Dependencies

- `HotkeyRegistry`
- `ControlSettingsStore`
- `UiState`
- renderer text and caption surfaces

## UI Flow

All critical campaign and tactical actions need visible bindings, focus-safe alternatives, readable warnings, and reversible settings.

## Data Ownership

Input settings belong to control settings storage. Runtime UI reads resolved bindings from the hotkey registry.

## Save Impact

Accessibility and input preferences should persist outside campaign checkpoint data unless a mode-specific setting belongs to the campaign.

## Asset Needs

Caption and warning presentation can use existing HUD panels, but contrast must be verified at supported resolutions.

## Tests

Cover hotkey registry search, conflict restore, keyboard paths for major screens, captions, quiet mode, and layout at `1280x720` and `1920x1080`.

## Non-Goals

This pack does not require controller-specific art beyond readable glyph text.

# Controls

The canonical keyboard-control table lives in `src/HotkeyRegistry.java`.

Gameplay bindings, the core-menu hotkey labels, the HUD action strip, and the rendered HUD help rows read from that registry. Add or change keyboard shortcuts there first. `test/HotkeyRegistryTest.java` rejects duplicate unmodified keys within an input scope and verifies that rendered help is sourced from the registered controls.

The registry separates these ownership scopes:

- `GLOBAL`
- `TACTICAL`
- `OVERMAP`
- `MODAL`
- `SHOP`
- `FLEET_EDITOR`

Mouse controls and context-specific overlay details remain rendered beside the relevant UI surface.

## In-Game Controls Screen

Press `Ctrl+H` during play to open the searchable controls screen.

- Type to filter controls.
- Use the arrow keys to select a binding.
- Press `Enter`, then press a replacement keyboard key.
- Select a mouse action, press `Enter`, then click a replacement mouse button.
- Press `Ctrl+1` through `Ctrl+6` to restore defaults for the listed input scope.

Controller bindings use the same persisted registry. `ControllerInputSystem` is the adapter boundary for platform controller events, and the live context legend switches to controller glyph labels after controller input.

## Tactical Command Overlay

Press `Ctrl+F3` to open tactical command. Use `Ctrl+G` to add the friendly ship nearest the cursor to the active group, `Ctrl+K` to cycle groups, `Q` to cycle orders, and `Shift+RMB` to issue an order. `Ctrl+P` toggles tactical pause.

Use `Ctrl+T` and `Ctrl+R` to select and activate support systems. `Ctrl+O` holds orientation, `Ctrl+B` seals bulkheads, `Ctrl+X` triggers weapon overdrive, `Ctrl+D` cycles point-defense priority, `Ctrl+J` cycles ship doctrine, and `Ctrl+S` scuttles the disabled friendly nearest the cursor. See `docs/TACTICAL_COMBAT_DEPTH.md` for the complete tactical feature map.

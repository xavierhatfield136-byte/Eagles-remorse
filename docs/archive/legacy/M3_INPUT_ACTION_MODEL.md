# M3 Input Action Model (Cross-Client)

## Goal
- Keep control semantics identical across clients by routing keybind behavior through a shared action layer.

## Shared Action Layer
- `src/GameplayActions.java`

Current coverage:
- Pause/menu transitions: `handleEscape`.
- Overlay toggles: shop/map/base.
- Targeting UX: lock-under-cursor, cycle target.
- Utility actions: ping, waypoint, turret auto-lock toggle.
- Combat actions: shield overcharge, missile salvo, carrier commands.
- Contextual hotkeys:
  - shop loadout/buy keys
  - base upgrade keys
  - ally spawn keys

## Swing Integration
- `src/GamePanel.java` bindings now delegate to `GameplayActions`.
- `src/InputSystem.java` key listener routes contextual hotkeys through `GameplayActions`.

## Why This Matters For 3D
- 3D input code can call the same action methods directly.
- Behavior parity is controlled in one place, reducing drift between Swing and 3D clients.

## Next M3 Steps
1. Add a framework-neutral `ActionId` enum + binding table so each client maps input devices to the same actions.
2. Build a thin action adapter in `client-3dimentions` that calls `GameplayActions`.
3. Validate targeting/pause/overlay parity in a 3D sandbox run.

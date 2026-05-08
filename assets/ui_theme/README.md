# UI Theme Placement Plan

This folder is the drop point for the new industrial naval HUD shell art.

## Expected files

- `menu_main_shell.png`
  Used for the main menu root card shell.
- `menu_section_panel.png`
  Used for major menu sections like single-player setup and mission cards.
- `menu_inset_panel.png`
  Used for smaller inset cards like checkpoint and helper blocks.
- `hud_standard_panel.png`
  Used for the main combat HUD information cards:
  objective, command, ship systems, hover tooltips.
- `hud_status_strip.png`
  Used for long horizontal control surfaces:
  the bottom core menu bar and future status rails.
- `hud_alert_panel.png`
  Used for high-attention strips like combat event banners.
- `hud_special_frame.png`
  Used for special-purpose analysis panels like tactical x-ray / ship inspection.
- `hud_radar_ring.png`
  Reserved for radar / minimap / sensor-ring surfaces.

## Current in-game placement

- Main menu shell:
  `MainMenuPanel.wrapMenuCard(...)`
- Main menu sections:
  `MainMenuPanel.createSectionPanel(...)`
- Main menu inset cards:
  `MainMenuPanel.createInsetPanel(...)`
- Combat HUD standard panels:
  `Renderer.drawHudPanelFrame(...)`
- Combat event banner:
  `Renderer.drawHUD(...)`
- Bottom combat command strip:
  `Renderer.drawCoreMenuBar(...)`
- Tactical x-ray / inspection frame:
  `Renderer.drawShipXrayPanelImmediate(...)`
- Minimap / sensor ring:
  `Renderer.drawMinimap(...)`
- Strategic map shell:
  `Renderer.drawStrategicMap(...)`
- Tactical management overlays:
  `Renderer.drawPowerManagementOverlay(...)`
  `Renderer.drawFlightDeckOverlay(...)`
  `Renderer.drawCrewStationsOverlay(...)`
  `Renderer.drawBaseUpgradeOverlay(...)`

## Notes

- All slots are optional. Missing files safely fall back to the old procedural UI.
- If your generated filenames differ, either rename them to the expected names above
  or extend `src/app/ui/ThemeArt.java` with another candidate filename.

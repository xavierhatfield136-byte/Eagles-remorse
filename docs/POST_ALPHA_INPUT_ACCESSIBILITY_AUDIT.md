# Post-Alpha Input And Accessibility Audit

All post-alpha interactive flows use the existing `CampaignAction` dispatcher. A focused item is executable by mouse click, keyboard activation, or controller activation; no flow depends on hover, color, rapid pointer movement, or a second modal panel.

| Flow | Pointer-free actions | Non-color information | Time accommodation |
|---|---|---|---|
| Territory inspection | Previous/next territory, compact/expanded details | faction name, transponder, insignia, pattern, control/supply text | strategic layer remains pausable |
| Overlay inspection | Cycle overlay, previous/next territory | route style and explanation, marker shape, text labels | cached and non-modal |
| Civil-war identity | evidence verification action | faction name, transponder, service record, insignia | no reaction-time check |
| Flagship schematic | open/close, next compartment, zoom, slow-time | warning text, icon, pattern, priority, optional audio cue | 0.25x slow-time |
| Cooperative roles | role actions, captain override/vote, text/ping | authority/status text and acknowledgments | host/captain pause policy |

`PostAlphaInputAccessibilityAuditTest` exercises the shared dispatcher. Resolution, UI-scale, high-contrast, and color-vision coverage is provided by `CampaignTerritoryOverlayAccessibilityTest`, `FlagshipPlayerFacingIntegrationTest`, and screenshot regression.

# Strategic HUD Action-First Redesign

Date: 2026-05-13  
Status: Core implementation complete

> Outdated: this redesign has been implemented and is now kept as a historical reference, not a live action plan.

## Core Rule

The map is for selecting. The panels are for commanding.

The player clicks the map to choose what they are looking at.

The HUD presents visible, clickable actions for what they can do next.

Keyboard and mouse shortcuts may remain as optional accelerators, but they must never be required for normal strategic play.

## Design Goals

- Never require hidden key combinations for standard strategic actions.
- Always show what is selected.
- Always show what the player can do with the current selection.
- Always explain why an unavailable action is disabled.
- Keep the current gritty mechanical console style while making the command layer much clearer.

## HUD Zones

### Left Panel

Sensors, comms, theater awareness, rumor board, crew commentary, contact uncertainty, and scan feedback.

### Center Map

Selection, route context, contact context, and optional quick context plates.

### Right Panel

Selected object readout, current tab content, and a context-sensitive command action bay.

## Action-First Requirements

- Every strategic action must exist as data in a central campaign action registry.
- The HUD must render actions from the registry rather than hard-coding random buttons in multiple places.
- Actions must support visible, enabled, disabled, warning, and recommended states.
- Disabled actions must include a reason.
- Primary actions must be visually distinguished from secondary actions.

## Action Registry Checklist

- [x] Add a central `CampaignAction` model.
- [x] Add action id, label, description, tooltip, category, visible state, enabled state, disabled reason, visual state, primary flag, and execute callback.
- [x] Make the strategic HUD query visible actions from campaign state.
- [x] Route command-button execution through the central campaign action model.
- [x] Expand the registry to cover hub services directly instead of using a separate service button path.
- [x] Expand the registry to cover site-resolution choices as first-class buttons rather than a mode cycle.

## Current Implementation Pass

The implemented pass now includes:

- a real strategic action registry in `CampaignSystem`
- a primary action plus grouped secondary command sections in the right panel
- visible disabled reasons for unavailable actions
- action states for available, disabled, warning, and recommended buttons
- visible command buttons for:
  - plot course
  - engage course
  - cancel course
  - set waypoint
  - enter site
  - direct site-resolution choices
  - approach/dock
  - direct hub services
  - signal/recon sweep
  - direct posture selection
  - green support
  - yellow support
  - track target
  - torpedo strike
  - carrier sortie
  - atomic strike
- hostile overmap contact selection feeding the strikes tab
- action preview lines in the navigation board
- an atomic-strike confirmation overlay
- selected-object readouts now showing primary recommendation and available action count
- prose instructions about hidden strike shortcuts replaced with command-bay language
- command-bay click routing and mouse-only action execution coverage in tests

## Remaining Polish

The core redesign rule is now in place: if the player can do something strategically, it has a visible button path.

What remains is polish rather than foundation:

- add a floating mini context plate near map selections for faster local command access
- deepen action preview lines with richer fuel, retaliation, and route-cost visualization
- standardize selected-object formatting even more tightly across hubs, hostile contacts, free-space points, and chains
- add a more bespoke posture board look so the active posture reads more like a latched hardware switch
- continue live-play validation to catch any obscure strategic action that still feels easier through a shortcut than through the visible HUD

## Completion Checklist

### Phase 1: Context-Sensitive Command Bay

- [x] Add a visible primary action.
- [x] Add visible secondary actions.
- [x] Show disabled reasons for contextually relevant unavailable actions.
- [x] Group actions visually by navigation, support, strikes, posture, services, and site resolution.
- [x] Hide irrelevant groups by only rendering categories that are currently populated.

### Phase 2: Navigation Controls

- [x] Add visible engage/cancel route actions.
- [x] Add visible enter-site and approach actions.
- [x] Add a dedicated visible `Plot Course` action for selected free-space targets.
- [x] Add a dedicated visible `Set Waypoint` action.
- [x] Add route cost and pressure preview directly in the action preview area.

### Phase 3: Strike Console

- [x] Add visible torpedo, sortie, atomic, recon, and track buttons.
- [x] Disable strike buttons when no valid hostile target is selected.
- [x] Explain why strike buttons are disabled.
- [x] Add atomic confirmation plate.
- [x] Add retaliation forecast and clearer target-cost preview in the action preview area.
- [ ] Extend strike targeting beyond the current hostile-contact lock model where needed.

### Phase 4: Fleet Posture Controls

- [x] Add a visible posture action in the command bay.
- [x] Replace posture cycling with visible direct posture selection.
- [x] Show all posture choices as dedicated buttons in the fleet tab.

### Phase 5: Site Resolution Boards

- [x] Surface site planning as a visible action.
- [x] Replace site-plan cycling with separate visible actions like `Fast Strip`, `Careful Secure`, `Mark for Allies`, `Evacuate Survivors`, `Tow Recoverable Ship`, `Quiet Decode`, and `Jam and Destroy`.
- [x] Show explicit cost/risk/reward preview for site-resolution choices through action preview text.

### Phase 6: Selected Object Panel

- [x] Add selected-object action count.
- [x] Add primary recommendation line.
- [x] Standardize the readout further for contacts, hubs, free-space points, and hostile targets.
- [x] Add action-count presentation for contact-target selections.
- [ ] Add stronger explicit distance/ETA formatting for contact-target selections in every view.

### Phase 7: Mouse-Only Validation

- [x] Ensure major strategic actions can now be triggered from visible command buttons.
- [x] Verify strategic travel, support, site entry, site planning, posture, services, and strike setup can be completed without hidden Ctrl/Shift/middle-click requirements.
- [x] Keep shortcuts as optional accelerators only.

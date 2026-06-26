# Phase 10 Evidence - Accessibility And Input Acceptance

Date: 2026-06-26

## Status

Phase 10 is complete for the alpha checklist.

This pass turns the existing accessibility and input work into a repeatable
acceptance contract. The main implementation goal was not to invent a second UI
system, but to make the current keyboard, remapping, caption, visual-access, and
focus behavior testable and hard to regress.

## Implemented

- Added `Phase10AccessibilityAcceptance`, a strict acceptance harness covering:
  - `14` keyboard-only flow rows;
  - `8` remapping safety rows;
  - `12` visual accessibility rows;
  - `7` audio accessibility rows;
  - `9` window/focus rows;
  - `61` registered required actions.
- Added `phase10Accessibility`, which writes:
  - `build/reports/phase10_accessibility_acceptance.json`
- Added `CampaignPhaseTenAccessibilityInputTest`.
- Tightened `HotkeyRegistry` remapping:
  - rejects unknown actions;
  - rejects null/invalid keys;
  - rejects same-scope keyboard conflicts;
  - rejects duplicate mouse buttons;
  - rejects duplicate controller buttons;
  - returns player-facing `RemapResult` explanations.
- Wired the controls overlay to display remap success/failure messages.
- Added visible high-contrast keyboard focus rings to main-menu buttons.
- Added focus-loss input release for held mining, firing, and camera-pan flags.
- Included `phase10Accessibility` in `stabilitySmoke`.

## Keyboard Acceptance

The acceptance contract covers every Phase 10 keyboard flow:

- main menu navigation;
- campaign start;
- strategic command tabs;
- fleet/location selection;
- course plot/cancel;
- trade;
- shipyards;
- ship queue;
- objectives;
- tactical entry;
- tactical withdrawal;
- save/load;
- defeat recovery;
- visible keyboard focus.

## Remapping Acceptance

The controls screen remains searchable and keyboard-driven through `Ctrl+H`.
Required actions are exposed through the canonical hotkey registry, and unsafe
remaps now fail before replacing the active binding. Failure reasons are stored
for the controls screen to show.

## Visual And Audio Accessibility

The acceptance contract records the evidence for:

- high contrast;
- color-independent hostile/friendly/neutral recognition;
- selected and warning state not relying on color alone;
- text and subtitle scaling;
- 1280x720 and 1920x1080 readability;
- long names and large numeric values;
- mission briefing, fleet health, and reputation text;
- spoken and critical-radio captions;
- quiet/reduced-noise operation;
- per-role voice volume controls;
- no required information being audio-only.

## Window And Focus

The focus/window contract covers fullscreen, windowed mode, Alt+Enter,
focus loss/regain, minimizing during campaign/combat, display scaling, and stuck
key prevention. Focus loss now releases held manual inputs before optional pause
handling.

## Automated Validation

`.\gradlew.bat test --tests CampaignPhaseTenAccessibilityInputTest --tests HotkeyRegistryTest --tests FirstHourExperienceTest phase10Accessibility --no-daemon`

Result: PASS

Harness output:

`[phase10-accessibility] keyboardFlows=14 remapChecks=8 visualChecks=12 audioChecks=7 windowFocusChecks=9 requiredActions=61 pass=true`

## Notes

Phase 10 is an acceptance pass. Some rows are verified through code contracts
and screenshot-regression coverage rather than live manual playthrough notes.
The important alpha guarantee is now automated: required input paths, remapping
safety, captions, visual readability, and focus transitions are all represented
by a repeatable guardrail.

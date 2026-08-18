# Alpha Manual Acceptance Scripts

Date: 2026-06-10
Scope: Windows desktop alpha pass for the 2D campaign game.

Use these scripts after `./gradlew productionValidation` passes. Record the
tester, build hash, preset, result, and any blocker notes for each run.

## Script 1: New Campaign First Route

- [ ] Launch Open World Campaign from the main menu on Standard Command.
- [ ] Open Alpha Readiness and confirm the known-blocker list is readable.
- [ ] Start a new campaign and verify the strategic map opens without overlap.
- [ ] Select the first northern objective and read route tempo, risk, forecast,
  notes, allied support, and encounter density.
- [ ] Engage the route and confirm travel begins with a clear ETA/risk banner.
- [ ] Confirm at least one route event, transit contact, or interdiction explains
  why it happened in the command/readout panel.
- [ ] Enter the destination encounter or site and confirm the primary action is
  understandable.

Pass criteria: no crash, no unreadable HUD overlap, route forecast visible,
and route/event reasoning visible.

## Script 2: Save And Load Continuity

- [ ] Start Open World Campaign on Standard Command.
- [ ] Change fleet posture, select a route, and complete one travel leg.
- [ ] Spend at least one hub service or support action.
- [ ] Exit to menu or close the app after checkpoint save.
- [ ] Relaunch and resume the campaign.
- [ ] Confirm fuel, supplies, ammo, ore, selected location, fleet posture,
  relationships, favors, discovered contacts, route scars, and escalation state
  survived the resume.
- [ ] Engage another route and confirm travel still works.

Pass criteria: no lost campaign state that changes player decisions.

## Script 3: Defeat Path

- [ ] Start Open World Campaign on Iron Command.
- [ ] Pick a high-risk route or contact and enter tactical combat underprepared.
- [ ] Allow flagship loss or campaign failure.
- [ ] Confirm failure banner, game-over text, and menu recovery are clear.
- [ ] Start a new campaign after defeat.

Pass criteria: defeat is understandable, recoverable, and does not corrupt the
next run.

## Script 4: Victory Path

- [ ] Start Open World Campaign on Standard Command or Command Only.
- [ ] Progress through the required main objectives using hub services, support,
  and strikes as needed.
- [ ] Confirm late-route pressure, strike costs, repairs, and low-store warnings
  remain legible.
- [ ] Complete the final victory objective.
- [ ] Confirm victory text, unlock/result persistence, and return-to-menu flow.

Pass criteria: victory can be reached without debug steps and the end state is
clear.

## Script 5: Longer Campaign Session

- [ ] Play for at least 60 minutes on Standard Command.
- [ ] Use at least three fleet postures.
- [ ] Visit at least two hubs and three non-main contacts.
- [ ] Trigger at least one ignored-contact escalation.
- [ ] Trigger at least one low-fuel or low-supply recovery route.
- [ ] Use at least one strategic strike and one support/favor action.
- [ ] Save, reload, and continue for another route.

Pass criteria: no progressive UI crowding, no runaway resource collapse, no
silent state loss, and no repeated event loop that feels broken.

## Evidence Log Template

```
Build:
Tester:
Date:
Preset:
Script:
Result: PASS / FAIL / BLOCKED
Blocking issue:
Notable balance note:
Screenshot/video path:
Save/checkpoint note:
```

# Campaign Objective Markers And Mission Readability

Date: 2026-04-27  
Status: Design + implementation outline

## Problem Summary

Campaign missions that ask the player to travel, intercept, defend, or arrive somewhere are not reliably telling the player:

- where the main objective is
- where the next required pocket is
- what counts as failure
- why the mission suddenly ended

Mission 2 is the clearest example. The HUD text says:

- `Destroy customs-halo gunships before they seal the civilian aperture`
- `Route: TRANSIT 2/3 RESERVE STAGING`

But the game does not clearly mark:

- the `Customs Halo`
- the `civilian aperture`
- the convoy group whose survival matters
- the `next pocket` the player must reach when progress locks

As a result, players can kill visible enemies, assume they are doing the mission correctly, and then lose to `DEFEAT: CIVILIAN APERTURE SEALED` without understanding what spatial objective they missed.

## What The Current Code Does

### Mission text exists, but location guidance is mostly text-only

`CampaignSystem.hudObjectiveTitle()` and `CampaignSystem.hudObjectiveDetail()` already produce decent text summaries.

Relevant code:

- `CampaignSystem.hudObjectiveTitle`
- `CampaignSystem.hudObjectiveDetail`
- `objectiveRouteLine`
- `objectiveThreatLine`
- `objectiveProgressLine`

This is useful, but it is still passive reading. It does not answer the player’s immediate question: `where do I go right now?`

### Mission sections exist, but they are not surfaced as player-facing map objectives

`configureMissionSections(...)` builds pocket-level progression anchors such as:

- `FORWARD SCREEN`
- `RESERVE STAGING`
- `SUPPORT RELAY`

`updateMissionSectionFlow(...)` can also lock progress until the player reaches the next section and shows banners like:

- `REPOSITION TO ...`
- `MISSION SECTION REACHED: ...`

But these sections are not being promoted into strong map markers or forced waypoint guidance, so the player can miss the intended route.

### The game already knows objective anchor positions

`objectiveAnchorX/Y(...)` already resolves a meaningful location for the current objective:

- active mission section
- capture point
- escort ship
- boss target

Right now this anchor is mainly used for spawning pressure waves, not for UI guidance.

This is a key missed opportunity: the data needed for map guidance already exists.

### Mission 2 has a hidden protection/failure rule

Sector 2 (`spawnSector2`) does more than spawn hostile gunships. It also registers protected mission assets:

- `CONVOYS`
- total convoy count `3`
- required survivors `2`
- failure text `DEFEAT: CIVILIAN APERTURE SEALED`

Relevant code:

- `spawnSector2`
- `registerObjectiveAsset`
- `registerObjectiveAssetQuota`
- `detectObjectiveAssetLosses`
- `objectiveAssetQuotaFailed`

So mission 2 is not just `kill 6 targets`. It is effectively:

1. intercept hostile mission targets
2. keep enough convoy/aperture-linked assets alive
3. move through the required mission pocket chain

That is a good mission design idea, but the UI currently hides too much of that structure.

## Root Causes

## 1. Objective state is authored, but not converted into spatial UI

The campaign layer has:

- mission sections
- landmarks
- escort targets
- capture points
- boss ids
- asset quotas
- failure texts

But there is no single `campaign objective marker` pipeline that turns this into:

- world marker
- map marker
- optional auto-waypoint
- failure marker

## 2. Landmarks are flavor-first, not objective-first

`populateSectorLandmarks(...)` adds good world flavor like:

- `Customs Halo`
- `Outer Colony Jump Ring`
- `Breakout Aperture`

But landmarks are not currently distinguished as:

- decorative world anchor
- current objective location
- future route location
- must-protect asset location

That means the map can show place names without telling the player which one matters now.

## 3. Mission-end causes are under-explained

Mission defeat can happen because of:

- timeout
- escort death
- protected asset quota failure
- other authored conditions

The current failure messaging is too abrupt. It tells the player the result, but not the chain of events that caused it.

Mission 2 especially needs a clearer explanation like:

- `You lost 2/3 convoy assets`
- `The aperture was sealed after convoy screen collapse`
- `Required survivors: 2, remaining: 1`

## 4. Transit-lock progression is readable to designers, not to players

`missionSectionTravelLocked` is a valid structure, but the current feedback is too subtle.

The HUD says things like:

- `TRANSIT 2/3`
- `Reach the next pocket to resume objective progress`

That is still too abstract in live play if there is no matching map icon, waypoint ring, or highlighted pocket boundary.

## Desired Player Experience

At any moment in a campaign mission, the player should be able to answer these four questions in under two seconds:

1. What is the main objective right now?
2. Where is it on the map and in the world?
3. What can make me lose before I finish it?
4. If progress is paused, where do I need to travel to resume it?

## Proposed Fixes

## A. Add a first-class campaign objective marker model

Introduce a campaign-only UI data layer that exposes the current mission’s actionable markers.

Suggested marker types:

- `PRIMARY_OBJECTIVE`
- `NEXT_TRANSIT_SECTION`
- `PROTECTED_ASSET`
- `FAILURE_CRITICAL_LOCATION`
- `ESCORT_TARGET`
- `CAPTURE_ZONE`
- `BOSS_TARGET`
- `OPTIONAL_DISCOVERY`

Suggested source function:

- `CampaignSystem.activeObjectiveMarkers(GameContext ctx)`

Each marker should contain:

- label
- subtitle
- world position
- radius
- marker type
- priority
- whether it should show on the world HUD
- whether it should show on the map
- whether it should drive auto-waypointing

## B. Promote mission sections into explicit route markers

When `missionSectionTravelLocked == true`, the next required section should be impossible to miss.

Required behavior:

- show the next section on the strategic map with a bright mission marker
- show the same section in-world with a large nav beacon
- optionally set the player waypoint to that section automatically
- show `OBJECTIVE PROGRESS PAUSED UNTIL ARRIVAL` in the HUD

Suggested rule:

- when a section lock begins, auto-place a mission waypoint
- if the player manually changes waypoint, keep the mission marker visible anyway

This preserves player freedom without hiding the critical route.

Additional rule:

- the next pocket's objective logic must remain dormant until the player physically reaches that pocket

That means:

- no future-pocket pressure waves
- no future-pocket objective timers/progress triggers
- no future-pocket quota failures
- no retargeting of the objective anchor to the new pocket before arrival

The lock state should mean `travel there first`, not `the mission is already happening offscreen without you`.

## C. Separate flavor landmarks from actionable markers

Landmarks like `Customs Halo` and `Cinder Anchorage` should remain, but objective locations need stronger styling than normal flavor labels.

Map visual hierarchy should be:

1. primary objective marker
2. next transit marker
3. protect/failure-critical asset marker
4. escort/capture/boss marker
5. flavor landmarks
6. optional discovery signals

Without this hierarchy, players scan the map and see atmosphere, not instructions.

## D. Surface protected-asset missions honestly

For missions using `objectiveAssetQuota`, the HUD should explicitly say that asset survival is part of the loss condition.

Mission 2 should read more like:

- `Win: Destroy 6 customs-halo gunships`
- `Protect: Keep at least 2 of 3 convoy ships alive`
- `Travel: Reach RESERVE STAGING when progress locks`
- `Fail: Aperture seals if convoy survivors drop below 2`

The current `ASSETS: CONVOYS 1/3 SAFE>=2` line is useful, but it is buried inside the status line and not framed as a live failure condition.

## E. Add defeat-cause summaries

When a mission ends in failure, show a short cause card for 2-4 seconds before the normal defeat overlay settles.

Examples:

- `MISSION FAILED: CONVOY SCREEN COLLAPSED`
- `Required convoy survivors: 2`
- `Remaining convoy survivors: 1`
- `Aperture sealed before interception complete`

Other examples:

- `MISSION FAILED: ESCORT LOST`
- `MISSION FAILED: TIMER EXPIRED BEFORE BOSS KILL`
- `MISSION FAILED: CAPTURE POINT NOT SECURED`

This is especially important in campaign missions where multiple authored rules are active at once.

## F. Make mission start guidance explicit

Each sector should do a one-time mission-start guidance burst:

- banner with the main goal
- auto-open or emphasize the map objective
- temporary world-space marker pulse
- optional auto-waypoint to the first required section

For mission 2, the opening guidance should communicate:

- intercept the customs-halo attackers
- protect the aperture convoy
- advance along the staged route pockets

## G. Add objective-specific wording by mission type

The live HUD should use different templates for different objective structures.

### Destroy missions

Show:

- target count
- current target area
- whether progress is pocket-locked

### Escort missions

Show:

- escort position
- escort integrity
- formation distance warning

### Capture missions

Show:

- capture point marker
- defender count or control state

### Boss missions

Show:

- boss marker
- timer
- boss escape/reposition warning if relevant

### Protect-asset missions

Show:

- survivor quota
- current survivors
- failure threshold

## Mission 2 Specific Fix Plan

Mission 2 should be the reference implementation for this whole pass.

### Current authored structure

From `spawnSector2` and the mission script:

- main objective: destroy `6` mission targets
- protected assets: `3` convoy-linked ships
- minimum survivors: `2`
- failure text: `DEFEAT: CIVILIAN APERTURE SEALED`
- landmarks: `Customs Halo`, `Cinder Anchorage`
- destroy-type mission sections: `FORWARD SCREEN`, `RESERVE STAGING`, `SUPPORT RELAY`

### Problems players experience

- they do not know which landmark is the actual objective
- they do not know the convoy/aperture survival rule is mission-critical
- they do not know where `RESERVE STAGING` physically is
- they can clear visible hostiles and still lose to a hidden state change

### Mission 2 fix requirements

- mark `Customs Halo` as a primary objective location, not just a landmark
- mark convoy/aperture-linked protected assets with a protection icon
- auto-mark `RESERVE STAGING` when the first pocket clears
- add a bright banner: `PROTECT 2 OF 3 CONVOYS OR THE APERTURE SEALS`
- do not start the `RESERVE STAGING` pocket's objective logic until the player actually enters it
- on failure, show survivors lost and why the seal event triggered

## Broader Campaign Problem List

This issue likely affects more than mission 2.

High-risk sectors:

- sectors with `objectiveAssetQuota`
- sectors with `ESCORT`
- sectors with `CAPTURE`
- sectors with pocket-lock progression
- sectors with boss targets that can spawn or reposition away from the player

Most likely affected sectors based on current scripts:

- `2` convoy/aperture protection
- `6` cache survival
- `8` escort Titan screen
- `9` defector survival
- `11` depot survival
- `12` signatory survival
- `19` prison tender / convoy clamp flow
- `20` liberated recovery Titan escort
- `23` uplink/launch protection

## Implementation Order

## Phase 1: Readability foundation

- add `CampaignSystem.activeObjectiveMarkers(ctx)`
- add primary and transit marker rendering on world HUD
- add the same markers to strategic map
- auto-waypoint the next required mission section on lock

## Phase 2: Failure transparency

- add explicit protect/fail lines to objective HUD
- add defeat-cause summary cards
- add mission-start guidance banners

## Phase 3: Sector-by-sector authored cleanup

- review all strategic regions and objective routes
- tag which landmarks are decorative vs actionable
- verify each mission type has a visible marker for its current goal
- verify each failure condition is named before the player triggers it

## Validation Checklist

Every campaign sector should pass these checks:

- Can a new player tell where the main objective is without reading lore text?
- If progress pauses, is the next required pocket visibly marked?
- If the mission has protected assets, does the HUD state the survivor quota clearly?
- If the player loses, does the defeat message explain why in one sentence?
- On map-open, is the current mission route more visually prominent than flavor landmarks?

## Recommended Acceptance Tests

### Mission 2

- Start sector 2 and open the map immediately.
- Confirm the primary objective location is marked.
- Confirm protected convoy/aperture assets are marked.
- Clear the first visible enemies.
- Confirm the next required section becomes visibly marked when transit lock starts.
- Destroy enough convoy ships to fail.
- Confirm the failure UI explains quota loss instead of only showing the seal text.

### Escort mission

- Start sector 8 or 20.
- Confirm the escort ship is always map-marked and world-marked.
- Confirm the HUD clearly warns when the escort drifts out of screen/support range.

### Capture mission

- Start sector 4 or another capture mission.
- Confirm the capture point is highlighted both before and during engagement.

## Bottom Line

The campaign does not mainly have a content problem here. It has a guidance translation problem.

The authored mission logic is already rich:

- mission sections
- landmarks
- target counts
- protected assets
- failure texts

What is missing is the UI layer that turns authored mission state into unmissable spatial direction.

Mission 2 should be fixed first, then used as the standard for the rest of the campaign readability pass.

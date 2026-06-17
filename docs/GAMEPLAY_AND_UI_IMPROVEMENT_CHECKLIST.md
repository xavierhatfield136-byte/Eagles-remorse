# Gameplay And UI Improvement Checklist

## Priority 1 - Critical Bugs

* [x] Fix Safe Exit.
  * [x] Safe Exit must return the player to the overworld map.
  * [x] It must not freeze the game.
  * [x] Player should not need to press F10.

* [x] Remove objective timers and timeouts.
  * [x] Player should not lose because an objective timer hits zero.
  * [x] Objectives should not auto-fail at T=0.
  * [x] Player should be allowed to move at their own pace.

* [x] Fix "join nearby battle" instant-loss bug.
  * [x] When player presses J to join a nearby battle, objective state should initialize correctly.
  * [x] Do not start joined battles with defeat timer already at zero.
  * [x] Do not trigger defeat before the player has control.

* [x] Fix ghost intercept warnings.
  * [x] If an enemy fleet vanishes from sensors, do not keep drawing flashing red intercept lines.
  * [x] Intercept warnings should only show for real active fleets with valid position, target, and route.
  * [x] Clear stale intercept events when fleet contact is lost or invalid.

## Priority 2 - Station Interaction And Trade

* [x] Add station conversation/interact menu for Green and Yellow stations.
* [x] Player should spawn near/on top of the friendly station when entering a site that contains a Green station.
* [x] Station options should include:
  * [x] Request support.
  * [x] Request trade.
  * [x] Request replenishment.
  * [x] Bounty/job board.

### Request Trade

* [x] Request Trade opens a proper trading menu.
* [x] Trade options may include:
  * [x] Buy ore.
  * [x] Sell ore.
  * [x] Hire help.
  * [x] Purchase ships.
* [x] Player should not be able to purchase a space station.

### Request Support

* [x] Support can provide:
  * [x] New hulls.
  * [x] Allied escort ships.
  * [x] Emergency reinforcements.
  * [x] Faction-specific assistance.

### Request Replenishment

* [x] Replenishment should rearm/reload:
  * [x] Torpedoes.
  * [x] Air wing strikes.
  * [x] Nuclear strike, if allowed.
  * [x] Other limited-use campaign strike resources.

### Bounty / Job Board

* [x] Add station job board.
* [x] Job board creates new mission markers on the overworld map.
* [x] These markers may reveal previously unseen objectives.
* [x] Jobs should offer meaningful payout.
* [x] Jobs should encourage exploration.

## Priority 3 - Objective And Help UI Rework

* [x] Rework the objective panel.
  * [x] Remove timer-driven objective language.
  * [x] Move it to a better screen position.
  * [x] It should show only major overarching goals.

Suggested objective text:

* [x] Keep the flagship alive.
* [x] Reach Earth.
* [x] Help Green forces.
* [x] Help Yellow forces.
* [x] Weaken Red control.
* [x] Build enough strength for the final battle.

* [x] Split Crew menu from Help menu.

### Crew Menu

* [x] Crew menu should focus on crew orders only.
* [x] Keep command/order tools here.

### Help Menu

* [x] Help menu should contain reference information:
  * [x] Current mission.
  * [x] Current status.
  * [x] Tutorial/help text.
  * [x] Extra information the player does not always need visible.

## Priority 4 - Map And Camera Controls

* [x] When player is inside a site/zone and opens the map, arrow keys should move the map, not the battlefield camera.
* [x] When the map is closed, arrow keys should move the battlefield camera.
* [x] Do not move both at the same time.
* [x] Add clear input focus handling:
  * [x] Map focused = map panning.
  * [x] Battlefield focused = camera panning.

## Priority 5 - Campaign Spawn And Friendly Behavior

* [x] Prevent Red ships from piling onto the player within 10 seconds of campaign spawn.
* [x] Add spawn grace period.
* [x] Add safe radius around starting area.
* [x] Early Red contacts should spawn far away and approach naturally.
* [x] Red fleets should not spawn directly on top of the player.

### Green/Yellow Rules

* [x] If there are no Red ships in a zone, Yellow ships should not attack the player.
* [x] Yellow ships should be treated as friendly/neutral when not threatened by Red context.
* [x] "State Intent" used on Yellow ships can convert them to neutral status.
* [x] Neutral Yellow ships should not be targeted by friendly forces.

## Priority 6 - Sensor Net Improvements

* [x] Sensor Net should show the next predicted enemy fleet interception time.
* [x] Under Sensor Net, add a combat loss log.
* [x] Loss log should show:
  * [x] Ships lost by player/Blue.
  * [x] Ships lost by Green.
  * [x] Ships lost by Yellow.
  * [x] Ships lost by Red.
* [x] Keep it compact and readable.

## Priority 7 - Mining Improvements

* [x] Mining vessels should not collide with asteroids.
* [x] Mining vessels should be able to reach ore easily.
* [x] Either disable asteroid collision for mining ships or give them smarter avoidance/pathing.
* [x] Spawn one mineable asteroid about once per minute while player is inside a battle or site.
* [x] Do not spawn new asteroids directly on top of the player.
* [x] Do not spawn asteroids inside stations, ships, or blocked areas.

## Priority 8 - Combat AI Changes

* [x] Fighter pilots should be very aggressive.
* [x] Fighters should attempt to intercept enemy small craft.
* [x] Fighters should prioritize:
  * [x] enemy fighters
  * [x] bombers
  * [x] missiles/torpedoes if applicable
  * [x] small hostile craft
* [x] It is acceptable if aggressive fighter behavior gets them killed sometimes.
* [x] This should make fighters feel brave and disposable.

## Priority 9 - Menu And Layout Fixes

* [x] Fleet tab button should not be blurred out during campaign missions.
* [x] Upgrade button should not be blurred out during campaign missions.
* [x] Move Strike menu above/on top of the beam mode button menu.
* [x] Widen the panel image on the power management tab.
  * [x] Current text is covered by panel edges.
* [x] Fix commissioning page overlapping text.
* [x] Enlarge commissioning background/menu image so text fits.
* [x] Tutorial hints should not cover the top action bar.
* [x] Tutorial hints should appear below the bar, in a side panel, or in a safe UI area.

## Priority 10 - Building Limits

* [x] Remove old grid limits if they are blocking the new design.
* [x] Keep ship-building limits that prevent the player from spamming too many frigate-sized ships.
* [x] Replace hard grid limits with fleet capacity, command capacity, cost, logistics, or crew limits if needed.

## Priority 11 - Showcase Fix

* [x] Split showcase into team showcases:
  * [x] Blue Team Showcase.
  * [x] Red Team Showcase.
  * [x] Green Team Showcase.
  * [x] Yellow Team Showcase.
* [x] Do not load all ships at once.
* [x] Clean up previous showcase ships before loading another team.
* [x] Prevent showcase pop-in freeze.

## Suggested Implementation Order

1. [x] Safe Exit fix.
2. [x] Objective timer removal.
3. [x] Join-battle instant-loss fix.
4. [x] Campaign spawn protection.
5. [x] Tutorial/objective UI cleanup.
6. [x] Station interaction menu.
7. [x] Trade/replenishment/job board.
8. [x] Sensor Net improvements.
9. [x] Mining vessel and asteroid improvements.
10. [x] Yellow neutral/state-intent behavior.
11. [x] Fighter aggression.
12. [x] Showcase split.
13. [x] Remaining menu layout fixes.

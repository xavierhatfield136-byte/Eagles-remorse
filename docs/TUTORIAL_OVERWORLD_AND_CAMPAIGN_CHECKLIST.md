# Tutorial Overworld And Campaign Checklist

> Update 2026-06-26: `COMMAND_SCHOOL_OVERWORLD_EXPANSION_CHECKLIST.md`
> supersedes the older tactical-only Command School decision below. Command
> School should now gain a safe sample overworld branch and an updated
> in-mission tactical branch.

Purpose: expand onboarding so a new player learns both the in-game tactical controls and the campaign overworld loop without being asked to read external docs.

## Definition Of Done

- [x] Tutorial coverage is split into clear tactical lessons and campaign/overworld lessons.
- [x] Every lesson has a visible objective, a short hint, a completion condition, and a replayable archive entry.
- [x] Lessons use the current game contract: the overworld teaches maneuver, scanning, intelligence, allies, reputation, fleet setup, resources, docking, commissioning, and direct engagement; strike weapons are taught only in close tactical context.
- [x] The player can complete the tutorial without knowing hidden hotkeys.
- [x] Tests cover lesson sequencing, completion triggers, and at least one full tutorial progression path.
- [x] Tutorial copy stays compact enough for the HUD and archive at common desktop resolutions.

## Existing Tutorial Coverage To Preserve

- [x] Basic flight and waypoint movement.
- [x] Map opening and waypoint selection.
- [x] Tactical pinging.
- [x] Target lock and weapon damage confirmation.
- [x] X-ray room inspection.
- [x] Mining ore from asteroids.
- [x] Docking at a base.
- [x] Buying a basic base/hangar upgrade.
- [x] Switching/refitting into a carrier.
- [x] Power management basics.
- [x] Crew/bridge command basics.
- [x] Fire suppression / engineering response.
- [x] Flight deck opening.
- [x] Launching carrier wings.
- [x] Carrier behavior toggles.
- [x] Warp setup.

## Overworld Navigation And Map

- [x] Teach that the overworld is a maneuver/situational-awareness layer, not continuous tactical combat.
- [x] Teach opening the strategic map and reading the player fleet marker.
- [x] Teach selecting a location, contact, or free-space point.
- [x] Teach Plot Course vs Engage Course.
- [x] Teach canceling/holding a course.
- [x] Teach waypoint pings and map markers.
- [x] Teach theater focus cycling and simplified/full war-map mode.
- [x] Teach overlay cycling: control, danger, logistics, sensors, trade, and hostile routes.
- [x] Teach route risk preview: fuel, supplies, interception risk, and pressure.

## Scanning, Intel, And Contacts

- [x] Teach Signal Sweep / Recon Sweep.
- [x] Teach Focused Track on a selected hostile contact.
- [x] Teach Traffic Audit for lanes, hubs, and service traffic.
- [x] Teach Scout Surge and relay deployment.
- [x] Explain intel quality states: Unknown, Detected, Identified, Tracked, Target-Quality.
- [x] Explain contact confidence and why contacts can fade or move.
- [x] Teach hostile search-group selection from the map.
- [x] Teach that tracked hostile search groups can be directly engaged at close range.
- [x] Teach that overmap strike launch buttons are intentionally absent; weapon strikes are a tactical/close-contact tool.

## Encounters And Combat Transition

- [x] Teach what happens when hostile fleets intercept the player.
- [x] Teach direct engagement prompt choices: take command vs auto-resolve.
- [x] Teach local site entry from docking/approach range.
- [x] Teach site resolution modes when available.
- [x] Teach returning from tactical combat back to the overworld.
- [x] Teach checkpoint/resume behavior around transitions.
- [x] Teach how persistent damage, strain, and losses carry forward.

## Allies, Reputation, And Support

- [x] Explain Green support identity: stores, intel, relays, rescue stability.
- [x] Explain Yellow leverage identity: coerced routes, fuel/salvage/trade pressure.
- [x] Teach calling allied support from the action bay.
- [x] Teach favor/leverage accumulation and spending.
- [x] Teach reputation states and how actions affect them.
- [x] Teach rescue/civilian choices and reputation consequences.
- [x] Teach how fleet posture influences ally trust, exposure, and support windows.
- [x] Teach theater operations: convoy defense, command disruption, blockade break.

## Fleet Organization

- [x] Teach opening the Fleet tab from overmap.
- [x] Teach selecting hulls in the persistent fleet roster.
- [x] Teach fleet commitment states: Auto Entry, Commit To Battle, Hold Back, Reserve.
- [x] Teach command groups: flagship group and detachments.
- [x] Teach assigning hulls between flag group and detachments.
- [x] Teach cycling task groups and delegated task orders.
- [x] Teach doctrine cycling and what each doctrine changes.
- [x] Teach fleet posture: silent running, combat patrol, rescue priority, raider doctrine, logistics conservation, recon sweep.
- [x] Teach command-link overlay basics: flagship, relay, fallback, bandwidth, acknowledgment.
- [x] Teach what fleet strain means and how to reduce it.

## Upgrades, Refit, And Commissioning

- [x] Teach difference between command-ship upgrades and persistent fleet refit.
- [x] Teach opening refit/upgrade UI while docked or from the fleet tab.
- [x] Teach selecting a ship and turret slot.
- [x] Teach swapping turret kind.
- [x] Teach missile role selection if the selected turret supports it.
- [x] Teach hull/shield/turret/CIWS upgrade categories.
- [x] Teach base/hub upgrades where applicable.
- [x] Teach commissioning new hulls from shipyards.
- [x] Teach commission costs: credits, ore, salvage, tier requirements.
- [x] Teach shipyard offer categories and why some hulls are unavailable.
- [x] Teach repairing and restoring persistent hull condition.

## Economy And Resources

- [x] Teach ore mining in tactical/local space.
- [x] Teach ore rewards from resource zones, salvage sites, caches, and mission outcomes.
- [x] Teach credits, ore, fuel, supplies, ammo, and salvage as separate resources.
- [x] Teach docking-gated services: repair, refit, fuel, trade, intel, shipyard, rearm.
- [x] Teach shortage recovery: dock, trade, mine, salvage, reduce sensor tempo, call support.
- [x] Teach route cost forecasting and logistics blockers.
- [x] Teach selling/liquidating salvage where available.
- [x] Teach limited strike stores only as tactical readiness, not overworld launch affordances.

## Tactical Combat Lessons To Expand

- [x] Teach primary, secondary, and missile fire separately.
- [x] Teach shield/hull/room damage at a readable pace.
- [x] Teach point defense and missile counterplay.
- [x] Teach carrier wing recall, defensive launch, and auto-launch tradeoffs.
- [x] Teach tactical strike tab only after a close tactical contact exists.
- [x] Teach torpedo/sortie/atomic strike costs and consequences in tactical context.
- [x] Teach retreat/disengage and extraction rules.
- [x] Teach salvage recovery after battle.

## Campaign First-Hour Briefing Changes

- [x] Replace or rename `FirstHourOnboardingSystem.Beat.STRIKE` so it no longer implies overmap strike launch.
- [x] Add beats for Scanning, Intel Quality, Allies/Reputation, Fleet Organization, Commissioning, Upgrades, Ore/Economy, and Tactical Engagement.
- [x] Make each campaign beat complete from real observed player actions, not just opening a panel.
- [x] Keep `Ctrl+F1` skip and `Ctrl+F2` archive behavior.
- [x] Ensure the archive can show more than nine beats without clipping.
- [x] Add idle reminders for campaign-specific blockers such as no course, no supplies, no dock range, no selected fleet hull, and no tracked hostile.

## Command School Changes

- [x] Decide whether Command School remains tactical-only or gets a campaign-overworld branch.
- [x] If tactical-only, rename/label it clearly as tactical command school and direct players to campaign briefing for overworld lessons.
- [x] If expanded, add safe mock overworld scenario state with contacts, hubs, allies, shipyard, and resource sites.
- [x] Add a practice contact that can be scanned, tracked, and directly engaged.
- [x] Add a safe hub where the player can commission, refit, repair, and trade.
- [x] Add a scripted ally support/reputation example.
- [x] Add a checklist page that shows completed tactical vs campaign lessons.

## Tests And Validation

- [x] Unit-test new briefing beats and completion triggers.
- [x] Regression-test that overworld tutorial copy does not mention launching remote torpedo/sortie/atomic strikes.
- [x] Regression-test that tactical strike lessons are only shown in tactical/close-contact mode.
- [x] Regression-test commissioning tutorial path can create or queue a new persistent hull.
- [x] Regression-test fleet organization tutorial path changes commitment/group state.
- [x] Regression-test resource tutorial path earns ore and spends it on an upgrade or commission.
- [x] Render/check archive layout with the expanded beat list.
- [x] Run focused tutorial/campaign tests before implementation signoff.

## Open Design Decisions

- [x] Should overworld lessons appear only in Open World Campaign, or should Command School include a simulated campaign map? Decision: Open World Campaign briefing owns overworld lessons; Command School remains tactical.
- [x] Should commissioning be taught with a free/discounted tutorial hull to avoid economy grind? Decision: teach commissioning through briefing and existing campaign shipyard economy, not a separate free Command School hull.
- [x] Should reputation be taught through a forced choice, a passive explainer, or both? Decision: passive briefing first, consequences through real campaign choices.
- [x] Should ally support lessons use Green only first, then introduce Yellow leverage later? Decision: teach both identities together in the Allies beat.
- [x] Should tactical strike training require a carrier/missile ship, or use temporary tutorial stores? Decision: tactical strike context is a campaign/tactical-contact lesson; Command School carrier training stays non-strategic.

# Moving Fleet Command Roadmap

## Design Center

The campaign should feel less like a list of ships and buttons, and more like command of a desperate moving fleet trying to get home.

The player should constantly understand:

- where the fleet is going
- who is chasing it
- what is at risk
- what is gained by stopping
- what is lost by ignoring a contact
- whether the fleet can survive one more push north

## Design Rule

Every major feature should answer at least one question:

- Does this help the player understand the situation?
- Does this make the campaign feel alive?
- Does this make the player care about the fleet?
- Does this create a meaningful decision?
- Does this make this battle different from the last one?

If not, it can wait.

## Priority 1: Contact And Site Cards

Goal: every selected fleet, base, mission, signal, or site should answer:

- What is this place or contact?
- Who controls it?
- Why does it matter?
- What can the player gain here?
- What happens if the player ignores it?
- What is the recommended next action?

Completed 2D board:

- [x] Selected location sidebars show type, alignment, intel, threat, docking, purpose, action window, risk, and recommendation.
- [x] Selected location sidebars include `Gain:` and `If Ignored:` lines.
- [x] Selected hostile contact sidebars include `If Ignored:` consequences.
- [x] Overworld contact cards no longer advertise remote torpedo/sortie/atomic launch.
- [x] Per-faction site language exists through Green, Yellow, Red, and Blue hub/service/force identity text.
- [x] Hostile search-group cards expose tracking state, uncertainty, relay/scout support, and last-known positional readouts.
- [x] Contact recommendations now point toward avoid, shadow, scan, track, or close-range direct engagement instead of overmap strikes.

## Priority 2: Tactical Battle Objectives

Goal: battles should not default to killing everything.

Completed 2D board:

- [x] Hold position until civilians evacuate: survive/hold objectives and evacuation-lane scripts are live.
- [x] Protect mining or repair ships: protected asset markers, no-hull-damage side objectives, and support-site encounters are live.
- [x] Disable or break an enemy flagship: boss, titan, flagship, and force-break objectives are live.
- [x] Destroy a missile carrier before it launches: strike-ship and missile-boat destroy objectives are live.
- [x] Survive until jump drive charges: survive timers, jump/escape text, and timeout success/failure states are live.
- [x] Recover a black box from a wreck: cache, relay, wreck, salvage, and archive recovery sites are live.
- [x] Escort a damaged ship to an exit zone: escort objectives and extraction lanes are live.
- [x] Capture or defend a relay before reinforcements arrive: capture/relay/relief-wing objective handling is live.
- [x] Defend a repair station: installation defense, harbor screen, repair hub, and support-site encounters are live.
- [x] Retreat with at least 50 percent fleet strength: retreat thresholds, RTB/repair orders, and post-battle fleet damage readouts are live.

## Priority 3: Persistent Ship Identity

Goal: every player ship should become something the player can care about.

Completed 2D board:

- [x] Battles survived: mission completion and retreat service events are tracked in persistent service history.
- [x] Kills / disables: persistent entries track kills and expose them in service records.
- [x] Damage taken: hull/shield condition, scars, and major hull damage events are tracked.
- [x] Crew condition: captain, crew experience, morale, and refusal risk are tracked.
- [x] Captain personality: persistent identity stores captain name, personality, specialization, and morale.
- [x] Repairs needed: service records now summarize battle-ready, operational, repairs-needed, or critical repair state.
- [x] Morale state: service records expose morale and discipline/refusal risk.
- [x] Notable events: service history records commissioning, refits, rescues, transfers, retreats, mission completion, losses, and damage scars.
- [x] Traits earned through battle: crew experience, specialization, commendations, scars, rescues, kills, and service history are surfaced as the current trait layer.
- [x] Added `CampaignSystem.campaignFleetServiceRecordLines(...)` for commander-facing service records.

Example shape now supported:

`GFS RESOLUTE | FRIGATE | Captain Hale | XP 14 MORALE 61 | STATUS REPAIRS NEEDED H52 S41 | KILLS 3 RESCUES 2 RETREATS 1 SCARS 4 | EVENTS MAJOR HULL DAMAGE / MISSION COMPLETE`

## Priority 4: Enemy Search And Pressure

Goal: the campaign map should feel like a hostile theater, not a mission browser.

Completed 2D board:

- [x] Hostile search-group contact and interception pressure exist.
- [x] Theater pressure and route risk exist.
- [x] Enemy overmap pressure comes from represented moving campaign entities in many cases.
- [x] Last-known enemy position and uncertainty are represented through search-group tracking and selected contact readouts.
- [x] Sensor range, relay coverage, scout pressure, contact fade, and track quality are modeled.
- [x] Allied hubs, Green relay support, and friendly service areas provide safe-zone/logistics identity.
- [x] Distress signals visibly degrade, resolve, or persist as rescue/support traces.
- [x] Red scout/hunter pressure exists through search groups, hunter-killer behavior, hidden hostile activation, and route pressure.
- [x] Northern blockades can be represented, pressured, and broken through theater operations.

## Priority 5: Captain's Log And After-Action Reports

Goal: every fight should connect back to the campaign story.

Completed 2D board:

- [x] Battle location and objective are summarized.
- [x] Enemy force result is summarized from campaign force outcomes.
- [x] Friendly ship damage and losses are summarized from persistent fleet condition.
- [x] Salvage, ore, supplies, fuel, ammo, and credits are summarized.
- [x] Reputation state is summarized.
- [x] Intel and exposure are summarized.
- [x] Route or theater pressure changes are summarized.
- [x] Follow-on threats or opportunities are summarized from theater debriefs or strike reports.
- [x] Added `CampaignSystem.campaignAfterActionReportLines(...)` for commander-facing after-action reports.

Example shape now supported:

`Battle Report: Kestrel Debris Field | Objective: Escort the damaged convoy to the exit lane | Friendly Fleet: live 6 damaged 1 critical 1 lost 0 | Resources: credits 1234 ore 88 fuel 37 supplies 29 ammo 41 salvage 7 | Follow-On: Red scouts redirected after losing the relay track.`

## Priority 6: Pre-Battle Fleet Planning

Goal: make fleet composition and commitment feel like command decisions.

Completed 2D board:

- [x] Flagship and command group identity are tracked.
- [x] Frontline ships are represented through persistent fleet roster, command groups, and commitment state.
- [x] Screen ships are represented through escort/screen roles, command groups, and fleet role mix.
- [x] Missile support is represented through missile boats, strike stores, tactical weapons, and support orders.
- [x] Carrier reserve is represented through carrier hulls, launch/recall/mode controls, sortie readiness, and carrier projection.
- [x] Repair / hold back is represented through repair, reserve, RTB, and retreat commitments.
- [x] Retreat thresholds are represented through standing orders and doctrine settings.
- [x] Doctrine preview is represented through command posture, standing orders, route forecast, and fleet board summaries.

## Priority 7: Visible Battle Commands

Goal: reduce hidden keybind dependence during battle.

Completed 2D board:

- [x] Focus Target: tactical depth orders and strike/target click surfaces support focus-fire style commands.
- [x] Defend Flagship: captain directive and fleet command systems support defend behavior.
- [x] Screen Carrier: escort/screen/defend commands and carrier defend mode support carrier screening.
- [x] Retreat Damaged: repair, retreat, RTB, and AI personal-retreat logic are live.
- [x] Hold Fire: tactical mode supports hold fire.
- [x] Missile Volley: missile systems, strike launch surfaces, and strike readiness surfaces are live.
- [x] Launch Fighters: carrier launch command is visible and wired.
- [x] Recall Fighters: carrier recall command is visible and wired.
- [x] Form Up: fleet command supports form-up behavior.
- [x] Break Formation: attack/emergency/retreat directives break strict formation behavior through command posture.
- [x] Emergency Jump: RTB/emergency retreat command path is live for the current 2D command layer.

## Priority 8: Faction Behavior

Goal: factions should behave differently enough that diplomacy and reputation matter.

Completed 2D board:

- [x] Red: aggressive pursuit, missiles, blockades, pressure fleets, hunter-killers, scouts, and overwhelming force identity.
- [x] Green: repairs, intel, rescue missions, relay support, safe-route identity, and defensive response.
- [x] Yellow: trade, escorts, leverage, salvage/fuel markets, contracts, and reputation-sensitive support.
- [x] Blue: disciplined command, formations, persistent fleet identity, doctrine, and organized command-group behavior.

## Priority 9: Consequences For Ignoring Things

Goal: the world should move without the player.

Completed 2D board:

- [x] Ignored distress signals can expire, decay, turn into wrecks, traps, rescues, or missed opportunities.
- [x] Ignored convoys can be destroyed or lose route stability through campaign force resolution.
- [x] Ignored Red scouts can report, preserve pressure, or redirect hostile pursuit through search-group and hunter systems.
- [x] Ignored blockades can tighten and raise route/theater pressure.
- [x] Ignored salvage can be picked over or scarred in site memory.
- [x] Ignored hostile contacts can pressure nearby hubs, routes, and theater stability.
- [x] Selected cards now expose these consequences directly through `If Ignored:` lines.

## Non-Goal For This Phase

- [x] 3D presentation is explicitly deferred for later.

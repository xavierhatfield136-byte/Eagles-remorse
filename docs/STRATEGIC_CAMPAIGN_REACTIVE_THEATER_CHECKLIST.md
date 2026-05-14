# Strategic Campaign Reactive Theater Checklist

Date: 2026-05-13  
Status: Active checklist

Use this checklist for the next furnishing wave focused on memory, consequence, uncertainty, and personality.

Do not mark an item complete unless the effect is visible in the live campaign layer, not only implied by code.

## Phase 1: Site Memory

- [x] Make sites remember what happened to them after the player interacts with them.
- [x] Make stripped salvage fields read as spent or partially spent wreck zones after extraction.
- [x] Make mined ore pockets read as depleted or surveyed after extraction.
- [x] Make hidden caches read as emptied, opened, or recovered after extraction.
- [x] Make rescued distress contacts read as stabilized, evacuated, or turned into support traces after extraction.
- [x] Make relay/story contacts read as decoded, recovered, or stripped after extraction.
- [x] Make repair anchorages reflect that they have serviced the fleet.
- [x] Surface site memory in the selected-location readout.
- [x] Surface site memory in overmap support-marker text.
- [x] Keep site memory persistent across save/load.

## Phase 2: Enemy Search Doctrine

- [x] Split hostile search behavior into clearer doctrine classes.
- [x] Add scout-screen behavior.
- [x] Add hunter-killer behavior.
- [x] Add blockade behavior.
- [x] Add interdiction behavior.
- [x] Add punishment-fleet behavior for high exposure.
- [x] Make each doctrine visible in overmap readouts and contact summaries.
- [x] Make doctrine influence patrol pattern, search range, intercept style, and pursuit persistence.

## Phase 3: Uncertain Contact Labels

- [x] Add uncertain contact labels before identification is complete.
- [x] Support labels like weak signal, hot contact, civilian squawk, false transponder, distress burst, metallic debris field, active drive plume, and encrypted relay echo.
- [x] Make better intel and better sensor work clarify uncertain contacts.
- [x] Make the UI reflect contact uncertainty clearly without becoming noisy.

## Phase 4: Visual Return Feedback

- [x] Add visual reward chips or after-action plates on overmap return.
- [x] Add visual fleet-gain feedback when ships join.
- [x] Add visual faction/favor gain feedback.
- [x] Add visual route-impact feedback when a site materially changes local conditions.
- [x] Keep extraction outcomes readable at a glance.

## Phase 5: Campaign Reputation States

- [x] Add theater-level reputation states beyond simple favor and exposure.
- [x] Support states such as Unknown Fleet, Reliable Rescue Force, Raider Threat, Liberation Symbol, Overextended Command, and High-Exposure Target.
- [x] Make reputation affect contact generation.
- [x] Make reputation affect ally behavior.
- [x] Make reputation affect hostile prioritization.
- [x] Surface current reputation cleanly in the campaign UI.

## Phase 6: Named Recurring Contacts

- [x] Add a small set of recurring named allies, brokers, or hostile commanders.
- [x] Make recurring contacts able to reappear later in the campaign.
- [x] Make earlier help or neglect influence later appearances.
- [x] Keep recurring contacts lightweight and systemic rather than full RPG dialogue trees.

## Phase 7: Theater Pressure Timeline

- [x] Add a visible strategic pressure timeline.
- [x] Surface shifts such as patrol net expansion, blockade tightening, trade instability, supply-line weakening, and hidden hostile activation.
- [x] Make timeline shifts influence route danger and opportunity.
- [x] Make the player feel that the wider war moves even outside battle.

## Phase 8: Command Crew Commentary

- [x] Add short functional command-station callouts.
- [x] Let commentary warn about low fuel, bait signals, heavy patrol zones, converging search groups, and hub limitations.
- [x] Keep commentary short, useful, and non-spammy.
- [x] Make commentary reinforce the feeling of a crewed command post.

## Phase 9: Campaign Scars and Visual Map Change

- [x] Make the overmap visually reflect campaign changes over time.
- [x] Show damaged hubs or unstable lanes after attacks.
- [x] Show safer corridors after hostile pockets are cleared.
- [x] Show stronger patrol overlays in enemy-dominant zones.
- [x] Show support-route markers where allies stabilize traffic.

## Phase 10: Follow-On Expansion

- [x] Add more false-signal and bait contacts.
- [ ] Add more multi-step transit discovery chains.
- [x] Add more consequence-bearing choices when resolving sites.
- [ ] Add more replay variation in regional event and traffic generation.

## Phase 11: Player Doctrine and Strategic Choice

- [x] Add selectable fleet posture modes.
- [x] Support posture modes such as Silent Running, Combat Patrol, Rescue Priority, Raider Doctrine, Logistics Conservation, and Recon Sweep.
- [x] Make posture affect detection, fuel use, scan quality, encounter odds, and enemy response.
- [x] Add contact escalation for ignored signals.
- [x] Let distress calls decay into wrecks, traps, rescues, or missed opportunities if ignored.
- [x] Let false transponders, scout screens, and relay echoes escalate into sharper threats or lost chances if ignored.
- [x] Add consequence-bearing site resolution choices.
- [x] Let salvage sites support fast strip, careful secure, or ally-mark outcomes.
- [x] Let distress sites support evac, tow, or strip-for-parts outcomes.
- [x] Let relay/story sites support quiet decode, ally broadcast, or jam-and-destroy outcomes.
- [x] Make the map remember how a site was resolved, not just that it was visited.
- [x] Add formal intel quality levels for contacts.
- [x] Support contact intel states such as Unknown, Classified, Identified, Tracked, and Target-Quality.
- [x] Make recon, scanning, and proximity improve intel quality.
- [x] Add a lightweight fleet stress or morale pressure layer.
- [x] Make stress respond to long travel, casualties, shortages, rescues, victories, and hub recovery.
- [x] Let stress affect recovery efficiency, commentary tone, and ally confidence without becoming micromanagement.
- [x] Add relationship states for named recurring contacts.
- [x] Support lightweight states such as Unknown, Helped, Trusted, Owed Favor, Neglected, Hostile, Missing, and Destroyed.
- [x] Add a rumor/intel board with uncertain reports, bait, and stale leads.

## Phase 12: Operational Posture Effects

- [x] Make Silent Running reduce detection but slow travel and weaken scans.
- [x] Make Combat Patrol improve intercept readiness but increase fuel/ammo use.
- [x] Make Rescue Priority increase distress/support opportunities but slow progress.
- [x] Make Raider Doctrine improve strike/salvage aggression but raise exposure.
- [x] Make Logistics Conservation reduce fuel/supply drain but reduce response speed.
- [x] Make Recon Sweep improve contact identification but increase detection risk.
- [x] Surface current posture effects clearly in the campaign UI.

## Phase 13: Multi-Step Discovery Chains

- [x] Add relay-echo discovery chains.
- [x] Add drifting-wreck discovery chains.
- [x] Add false-distress bait chains.
- [x] Add missing-patrol rescue chains.
- [x] Add smuggler/broker lead chains.
- [x] Let chain outcomes affect site memory, reputation, contacts, and route safety.

## Phase 14: Fleet Strain and Named Contact Relationships

- [x] Add lightweight fleet strain.
- [x] Make strain rise from shortages, casualties, long travel, and northern pressure.
- [x] Make strain fall from hubs, victories, rescues, and stable logistics.
- [x] Add named-contact relationship states.
- [x] Let helped, neglected, hostile, missing, or trusted contacts alter later events.

## Immediate Execution Order

- [x] Start with `Site Memory`.
- [x] Follow with `Enemy Search Doctrine`.
- [x] Then implement `Uncertain Contact Labels`.
- [x] Then implement `Visual Return Feedback`.
- [x] Add `Named Recurring Contacts` after the above foundation is stable.
- [x] Next, add `Consequence-Bearing Site Resolution`.
- [x] Then add `Ignored Contact Escalation`.
- [x] Then add `Fleet Posture System`.
- [x] Then add `Formal Intel Quality Levels`.
- [x] Then add `Rumor / Intel Board`.
- [x] Then execute `Operational Posture Effects`.
- [x] Then add `Multi-Step Discovery Chains`.
- [x] Then deepen `Named Contact Relationship States`.
- [x] Then add `Fleet Stress / Morale`.

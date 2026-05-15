# Strategic Campaign Furnishing Status

Date: 2026-05-14  
Status: Active progress log

## Purpose

This document is the current progress summary for the strategic campaign.

It should answer three questions:

1. what furnishing work is already complete
2. what reactive-theater work is already complete
3. what still needs focused UX, replayability, and polish work

Use the related docs like this:

- `STRATEGIC_CAMPAIGN_MAP_SPEC.md`: stable campaign source of truth
- `STRATEGIC_CAMPAIGN_REACTIVE_THEATER_CHECKLIST.md`: active systems checklist
- `STRATEGIC_HUD_ACTION_FIRST_SPEC.md`: active HUD usability and visible-controls spec
- `STRATEGIC_CAMPAIGN_FURNISHING_STATUS.md`: current progress summary
- `STRATEGIC_CAMPAIGN_FURNISHING_PLAN.md`: mostly completed historical furnishing roadmap

## Completed Core Furnishing

The strategic campaign already has a strong baseline identity.

Completed core furnishing includes:

- continuous free travel on the overmap instead of node-only movement
- actionable contacts that can be entered, exploited, and exited cleanly
- compact local tactical pockets for ore, salvage, distress, relay, cache, and support sites
- local tactical markers and support markers for better site readability
- recovered-ship persistence and distress/support consequences
- faction- and region-specific ambient traffic behavior
- fleet, resources, logistics, strike, and recon command tabs
- stronger radio, receiver, direction-finder, and sweep presentation
- clearer objective text, transition text, and encounter prompts
- regional identity across the south, belt, and north
- stronger extraction and return outcome summaries

Player-facing result:

- the campaign no longer feels like "click a point, travel there, maybe fight"
- the campaign now feels like an operational layer with route pressure, contact management, and theater identity

## Completed Reactive Theater

The next furnishing wave has also landed in substantial form.

Completed reactive-theater work includes:

- site memory and persistent site-state aftermath
- enemy search doctrine classes with clearer identity
- uncertain contact labels and intel-quality progression
- visual return-feedback plates and gain summaries
- campaign reputation states
- named recurring contacts and lightweight relationship states
- theater pressure timeline behavior
- command crew commentary
- campaign scars and visual map change
- posture modes with mechanical tradeoffs
- ignored-contact escalation
- consequence-bearing site-resolution choices
- rumor and intel board behavior
- multi-step discovery chains
- lightweight fleet strain / stress pressure

Player-facing result:

- the map remembers the player
- contacts are not fully solved at first glance
- hostile behavior reads more like a reacting military system
- repeated campaign actions begin to shape later opportunities and pressure

## Remaining UX / HUD Work

The main unfinished work is no longer campaign identity.

The main unfinished work is making the campaign's systems easier to read, easier to command, and easier to learn under pressure.

Current UX / HUD priorities:

- make every important strategic action visible through a button, selector, toggle, or command plate
- keep keyboard shortcuts as optional accelerators only
- standardize selected-object readouts across hubs, contacts, free-space selections, and strike targets
- improve route-cost, fuel, retaliation, and commitment previews before action confirmation
- make strike readiness, target validity, and "cannot strike / why" states even clearer
- improve fleet-manager readability with stronger per-hull identity, role tags, and condition presentation
- reduce text-as-control and continue replacing hidden interactions with visible affordances

## Remaining Replayability Work

The campaign can now support more variation without needing another structural rewrite.

Current replayability priorities:

- deeper regional variation in local pocket layouts and hazards
- more traffic and event variation by faction, region, and campaign condition
- more alternate extraction choices and situational tradeoffs
- more transit-story variation around bait, smuggling, disabled escorts, and route disruption
- more variability in who responds to distress, support, and recurring-contact situations
- more systemic variation in rewards, route danger, and local opportunity generation

## Remaining Polish / Playtest Work

The campaign is now rich enough that polish and validation matter as much as new mechanics.

Current polish priorities:

- verify that recommended actions are obvious in each strategic context
- continue pruning verbose text when a visual instrument or action plate would teach faster
- validate that reactive systems remain readable rather than noisy
- stress-test the command HUD in long sessions with many simultaneous contacts
- keep tuning overmap information density so added detail improves decisions instead of cluttering them
- continue live playtests for readability, route-planning clarity, and command confidence

## Practical Reading Of Current Status

The campaign has crossed an important line.

It is no longer primarily missing systems.

It now has:

- a stable campaign identity
- a reactive world layer
- a meaningful command fantasy

The biggest remaining challenge is usability:

- make every system legible
- make every important action obvious
- make the command layer fast to understand in motion

That is why the current next phase should be read as:

- less "add another campaign subsystem"
- more "make the current campaign readable, clickable, and learnable"

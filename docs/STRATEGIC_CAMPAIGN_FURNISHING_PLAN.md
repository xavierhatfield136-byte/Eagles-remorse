# Strategic Campaign Furnishing Plan

Date: 2026-05-13  
Status: Mostly completed historical furnishing roadmap

> Historical note: this document remains useful as furnishing philosophy and staged intent, but much of its roadmap has now been implemented or superseded by the reactive-theater and HUD-usability docs.

## Purpose

This document explains how we planned to flush out, furnish, and deepen the new strategic campaign once the core campaign rewrite existed.

This is not the replacement for the core spec.

Use the other docs like this:

- `STRATEGIC_CAMPAIGN_MAP_SPEC.md`: source of truth for intended design
- `STRATEGIC_CAMPAIGN_REACTIVE_THEATER_CHECKLIST.md`: active systems checklist
- `STRATEGIC_HUD_ACTION_FIRST_SPEC.md`: active HUD usability spec
- `STRATEGIC_CAMPAIGN_FURNISHING_STATUS.md`: current progress summary
- `OUTDATED_STRATEGIC_CAMPAIGN_CHECKLIST.md`: completed implementation checklist
- `STRATEGIC_CAMPAIGN_FURNISHING_PLAN.md`: historical staged content-and-feel roadmap for furnishing philosophy and intent

## What "Furnishing" Means

The campaign foundation already exists:

- strategic overmap
- free travel
- route pressure
- hubs
- local site entry
- search groups
- strike tools
- strategic HUD

What it still needs is furnishing:

- more things to do
- more things to notice
- more things to manage
- more things to discover
- more things to care about
- more visual and systemic density without turning into noise

The goal is to make the campaign feel like a living operational theater instead of a working framework.

## Core Outcome

When this furnishing pass is complete, the player should feel like they are commanding a fleet through a dangerous region full of:

- uncertain contacts
- changing routes
- active allies
- recoverable ships
- logistics strain
- useful tradeoffs
- local exploration pockets
- faction traffic
- small stories
- high-value decisions between battles

## Furnishing Principles

1. `Every screen should answer a real player question.`
   If a panel or tab exists, it should help the player decide something.

2. `Every point of interest should imply an activity.`
   The player should not arrive somewhere and wonder why it exists.

3. `Every region should have an identity.`
   The south, mid-map, and north should not only differ in threat number. They should differ in behavior, opportunities, and atmosphere.

4. `The campaign should generate tension without requiring battle every minute.`
   Navigation, uncertainty, pursuit, and resource pressure must carry real gameplay.

5. `More density is good only if it improves decision-making.`
   Do not add clutter for its own sake.

## Step 1: Make Every Overmap Contact Actionable

Goal: remove dead overmap interactions.

Tasks:

- Make every discovered site support a concrete player action.
- Ensure every site has a clear `why go here` reason.
- Ensure every site has a clear `what do I do once I arrive` answer.
- Ensure every site has a clear `what do I get or risk by leaving` consequence.

Required content categories:

- ore pockets
- salvage pockets
- hidden caches
- distress contacts
- recoverable ships
- repair anchorages
- Green hubs
- Yellow hubs
- relay or intel sites
- hostile activity pockets
- strange or anomalous pockets

Definition of done for this step:

- no site is only a label
- no site resolves as a meaningless stop
- no site leaves the player unsure what interaction it supports

## Step 2: Make Local Encounter Pockets Dense and Readable

Goal: make entered sites feel like compact tactical spaces instead of oversized leftover missions.

Tasks:

- Keep local site pockets centered and warp-friendly.
- Keep important content near a readable tactical core.
- Ensure local site pockets are small enough to navigate quickly but large enough to maneuver inside.
- Mark important local content through sensors, map markers, and support markers.
- Make maxed sensor use reveal high-value contacts clearly.

Important local content that must be markable:

- ore clusters
- salvage clusters
- wrecks
- recoverable ships
- friendlies
- hostiles
- installations
- support traffic
- extraction-safe local center

Definition of done for this step:

- the player can enter a site, understand its shape quickly, and get to its important content without wandering through empty space

## Step 3: Build Out the Fleet Command Layer

Goal: make the player's own force feel like a real campaign object, not a single dot and a few numbers.

Tasks:

- Expand the `Fleet` tab into a real fleet manager.
- Show command hulls, escorts, logistics hulls, carriers, damaged ships, and recovered ships clearly.
- Show role composition and readiness clearly.
- Show which ships are fit for battle, escort duty, mining duty, or repair dependency.
- Make new recovered ships visibly enter the persistent fleet roster.

Recommended sub-panels:

- hull roster
- readiness summary
- damage or condition board
- command group or detachment grouping
- role coverage readout
- support availability

Definition of done for this step:

- the player can answer `what do I have, what shape is it in, and what can it do right now` without leaving the campaign layer confused

## Step 4: Build Out the Resource and Logistics Layer

Goal: make resources feel like campaign pressure, not decorative counters.

Tasks:

- Expand the `Resources` tab into a true logistics station.
- Show fuel, supplies, ammo, ore, salvage, credits, and favor with stronger hierarchy.
- Show resource trend, not only resource totals.
- Show which activities consume what.
- Show when the player is becoming fuel-poor, ammo-poor, support-poor, or recovery-poor.

Recommended instrumentation:

- meter bars
- per-resource readiness state
- shortfall warnings
- hub dependency warnings
- route-cost preview

Definition of done for this step:

- the player can answer `what am I low on, what is safe to spend, and what route can I currently support`

## Step 5: Build Out the Strike and Recon Layer

Goal: make long-range operations feel like a real command station.

Tasks:

- Expand the `Strikes` tab into a true long-range operations board.
- Show torpedo readiness, sortie availability, recon readiness, strike exposure, and recent strategic pressure.
- Show what can be struck, what cannot, and why.
- Show what intel quality is currently enabling.
- Make strike consequences legible before the player commits.

Recommended instrumentation:

- strike slots
- readiness lamps
- contact board
- recon coverage
- exposure or retaliation meter

Definition of done for this step:

- the player can answer `what can I hit, what do I know, and what will it cost me operationally`

## Step 6: Build the Radio, Sensor, and Comms Identity

Goal: make the strategic interface feel like a station the player operates rather than a menu they browse.

Tasks:

- Deepen the left-side navigation and radio identity.
- Make sensor sweeps, contact quality, and ally requests feel more physical and deliberate.
- Use stronger station framing for navigation, radio, and signal management.
- Turn some text-heavy blocks into functional instruments.

Recommended visual stations:

- session clock
- route schematic
- signal or contact strength board
- navigation bearing or course board
- comms status or favor board

Definition of done for this step:

- the campaign HUD feels like command hardware, not just themed rectangles

## Step 7: Make Factions and Traffic Visible in the World

Goal: make Green, Yellow, and hostile space feel inhabited.

Tasks:

- Add more ambient local traffic in hubs and service sites.
- Add more faction-specific behavior in local pockets.
- Make Green areas feel military, coordinated, and protective.
- Make Yellow areas feel opportunistic, civilian, flexible, and commerce-driven.
- Make hostile areas feel threatening before battle starts.

Ambient examples:

- patrol screens
- prospectors
- tenders
- haulers
- dock traffic
- relay guards
- rescue escorts
- civilian runners

Definition of done for this step:

- the player can tell who owns a place and what kind of place it is before reading a wall of text

## Step 8: Add More Discovery Stories During Travel

Goal: make free travel itself rewarding and tense.

Tasks:

- Expand transit discoveries beyond a small set of site types.
- Add more recoverable ship encounters.
- Add more route detours with meaningful cost-reward structure.
- Add more situations where the player sees something, chooses whether to investigate, and accepts the consequence.

Good discovery categories:

- ore blooms
- drifting wreck trains
- disabled escorts
- smuggler caches
- relay echoes
- false signals
- hostile bait contacts
- emergency rescue opportunities
- alliance favor opportunities

Definition of done for this step:

- free travel feels like exploration and risk management, not empty movement

## Step 9: Strengthen Regional Identity Across the Whole Map

Goal: make northbound progression feel geographical and political, not only numerical.

Tasks:

- Give the south a more sheltered, recoverable tone.
- Give the mid-map a more contested and opportunistic tone.
- Give the north a more compressed, hunted, and costly tone.
- Tie traffic, hub quality, patrol behavior, strike pressure, and available support to region.

Definition of done for this step:

- players can feel where they are on the map from behavior and opportunities, not only from labels

## Step 10: Replace Empty Space in the Strategic HUD With Useful Instruments

Goal: eliminate the feeling that the campaign screen has large dead areas.

Tasks:

- Fill panel dead space with compact functional boards.
- Prefer meters, lamps, contact rows, route readouts, and ship cards over extra prose.
- Increase visual hierarchy so the eye lands on important values first.
- Make tabs feel inset, physical, and machine-integrated.

Preferred replacements for dead space:

- route meter
- resource gauges
- contact board
- fleet posture board
- strike readiness board
- favor and ally status board

Definition of done for this step:

- no major campaign panel feels empty or placeholder

## Step 11: Improve Encounter Entry, Arrival, and Exit Feedback

Goal: make transitions in and out of local encounters feel crisp and intentional.

Tasks:

- Make mission and site encounter prompts clean and readable.
- Make arrival status obvious.
- Make extraction status obvious.
- Make local-site outcomes feel clearly earned.
- Make fleet additions, resource gains, and favor gains obvious on return to overmap.

Definition of done for this step:

- the player always understands what they just entered, what they accomplished, and what changed afterward

## Step 12: Add Repeatable Content Variation

Goal: prevent the campaign from feeling solved too quickly.

Tasks:

- Vary local pocket layouts by type.
- Vary ambient ship mixes by region and faction.
- Vary reward profiles by threat and distance.
- Vary route events so the player cannot fully predict every transit outcome.

Definition of done for this step:

- repeated campaigns still produce meaningful differences in travel, discovery, and local pocket tone

## Step 13: Playtest for Practical Questions

Goal: tune by player confusion, not by feature count.

During playtest, repeatedly ask:

- Do I know what I can do here?
- Do I know what I am risking?
- Do I know what I am low on?
- Do I know where I should look?
- Do I know what my fleet is capable of?
- Do I know why this site matters?
- Do I know whether I should enter this encounter?

If the answer is `no`, that is a furnishing failure even if the feature technically exists.

## Suggested Execution Order

1. Make all overmap contacts actionable.
2. Tighten local encounter pocket readability and marks.
3. Expand fleet manager functionality.
4. Expand resource and logistics instrumentation.
5. Expand strike and recon instrumentation.
6. Deepen radio, sensor, and comms station identity.
7. Add more faction traffic and local site life.
8. Add more transit discoveries and small stories.
9. Strengthen regional identity and pacing.
10. Replace remaining HUD dead space with useful instruments.
11. Refine encounter entry, exit, and return feedback.
12. Add systemic variation and replay value.

## What This Document Should Prevent

This furnishing plan exists to prevent the campaign from stopping at:

- a good-looking shell with not enough to do
- a map full of labels with weak interactions
- a fleet game where the fleet itself is hard to inspect
- a route game where travel is mechanically broad but emotionally empty
- a Highfleet-inspired HUD that has the right mood but not enough operational depth

## Practical Completion Standard

This furnishing pass is succeeding when the player can:

- travel freely with purpose
- detect and investigate things during transit
- enter compact tactical site pockets that are easy to read
- inspect fleet condition cleanly
- manage resources with confidence
- understand strike readiness and recon quality
- call allies and see consequences
- recover ships and keep them
- distinguish regional and faction identity at a glance
- spend meaningful time in the campaign layer without it feeling like dead space between battles

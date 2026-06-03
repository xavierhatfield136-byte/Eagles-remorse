# Section 28 User Worksheet

Use this sheet to unblock the parts I cannot honestly finish alone: final taste calls, asset approval, playtest judgment, and release-scope decisions. The matching CSV version is `docs/section_28_user_worksheet.csv`.

## Fastest Decisions

| ID | Area | What I Need From You | Recommended Answer | Why It Matters |
|---|---|---|---|---|
| U-01 | Strike balance | Play 3-5 strike-heavy routes and decide whether torpedo/sortie/atomic costs feel too cheap, fair, or too punitive. | Fair unless strikes erase major threats without logistics planning. | This closes alpha strike-cost tuning with actual feel, not just unit math. |
| U-02 | Ore loop | Play one mine-return-buy-relaunch loop and note whether the fleet feels meaningfully stronger. | Approve if one purchased/refit hull changes the next launch. | This is the main economy confidence check still open in alpha. |
| U-03 | Orbital subset | Pick the small orbital-layer subset for alpha: navigation drag, sensor shadows, logistics quarantine, or presentation-only. | Sensor shadows plus quarantine warnings. | It gives section 28.9 a narrow live target instead of a sprawling orbital sim. |
| U-04 | Placeholder triage | Mark which placeholder sprites/icons/panels/portraits/voice lines bother you most. | Replace only top 10 disruptive placeholders before alpha. | Prevents us from burning time polishing invisible or acceptable placeholders. |
| U-05 | Accessibility pass | Run keyboard-only, high contrast, captions, quiet mode, and 1280x720/1920x1080 checks. | Record pass/fail with screenshots for failures. | These require human readability judgment. |
| U-06 | Final scope | Decide whether battle replay, visual battlefield editor, and mod browser are alpha blockers or post-alpha. | Post-alpha unless they block your release promise. | These are large systems, not finishing touches. |

## Detailed Requests

| ID | Section | Need Type | Request | Good Enough Evidence | Codex Can Continue After? |
|---|---|---|---|---|---|
| U-07 | 28.3 | Playtest | Verify live economy/diplomacy choices materially change later encounters. | Two saves or notes showing different later outcomes after different choices. | Yes, I can turn notes into tests. |
| U-08 | 28.3 | Content direction | Decide whether negotiation/favor/alliance interactions should be terse command UI or fuller dialogue scenes. | One preferred interaction style and 2-3 example lines. | Yes, I can implement the chosen shape. |
| U-09 | 28.3 | Writing approval | Approve or rewrite recurring bulletins, officer opinions, logs, and banter tone. | A short "too dry / too jokey / right tone" note. | Yes. |
| U-10 | 28.5 | Art approval | Approve current faction hull skins, turret skins, damage stages, wrecks, plumes, shields, trails, station modules, props, portraits, and map icons. | Approved list plus any "must replace" assets. | Yes. |
| U-11 | 28.5 | Audio approval | Approve weapon audio, engines, impacts, ambience, music behavior, warnings, radio distortion, voice priorities, ducking, and captions. | Approved list plus any "must replace" sounds. | Yes. |
| U-12 | 28.5 | Presentation review | Verify empty space, hubs, allied/neutral/hostile sites, and operational districts are visually distinct. | Screenshot notes with "distinct enough" or failures. | Yes. |
| U-13 | 28.6 | Scope decision | Choose battle replay depth: event log only, deterministic playback, or cinematic replay. | One chosen depth and whether it blocks alpha. | Yes. |
| U-14 | 28.6 | Scope decision | Choose custom scenario/challenge/new-game-plus priority. | Rank as alpha, beta, post-release. | Yes. |
| U-15 | 28.6 | Architecture acceptance | Decide whether current ownership-boundary docs/tests are enough or require stricter typed-ID migration. | Accept current guardrails or name required typed IDs. | Yes. |
| U-16 | 28.9 | Scope decision | Pick one deep-simulation vertical slice to finish first: stations, officers, hazards, politics, crises, or legacy/endgames. | One first slice. | Yes. |
| U-17 | 28.9 | Narrative/content approval | Approve named civilian actors, rumors, casualty reports, neutral powers, political blocs, and crisis tone. | Approve, revise, or de-scope categories. | Yes. |
| U-18 | 28.10 | Tooling scope | Decide whether the visual battlefield editor must be in-game, external/dev-only, or postponed. | One target surface. | Yes. |
| U-19 | 28.10 | Community scope | Decide whether featured scenarios/local ratings/notes/mod compatibility report are alpha blockers. | Blocker/non-blocker decision per item. | Yes. |
| U-20 | 28.11 | Manual playthrough | Run or assign complete new-campaign, migration, long-campaign, defeat, victory, challenge, editor, modded, and safe-mode playthroughs. | Pass/fail notes with seed, date, and blockers. | Yes, I can fix blockers. |
| U-21 | 28.11 | Balance judgment | Judge economy, logistics, faction directors, doctrine, hazards, crises, endgames, and scoring after play. | "Too easy / too punishing / unclear" per system. | Yes. |
| U-22 | 28.11 | Content pass | Flag repeated text, placeholder names, missing assets, inaccessible UI states, dead controls, and unreachable branches. | A punch list. | Yes. |

## What I Can Still Do Without You

- Add more executable validators around existing data and save schemas.
- Convert manual checklist rows into tracked manual-test cases.
- Add more deterministic harnesses where the simulation already exposes stable state.
- Build a narrow orbital-layer implementation once you pick the alpha subset.
- Implement whichever deep-simulation vertical slice you choose first.





Section 28 User Worksheet
Purpose:
Use this worksheet to make the final decisions that cannot be finished by code alone. These include taste calls, asset approval, playtest judgment, balance feel, and release-scope decisions.

Part 1: Fastest Decisions
U-01 — Strike Balance
Area: Strike balance
Task:
Play 3–5 strike-heavy routes and decide whether torpedo, sortie, and atomic strike costs feel:
Too cheap
Fair
Too punitive
Recommended Answer:
Fair, unless strikes erase major threats without logistics planning.
Why It Matters:
This closes alpha strike-cost tuning with actual player feel, not just unit math.
My Answer:
Too cheap, the player can pretty much get away with using them as an extra punch during combat and be just fine. Also, I don't know how to get more. I would like it if there were a way to make/ buy/ salvage more.
Notes / Evidence: strikes being a guaranteed hit in combat,t and the sheer quantity of strikes the player gets. Also, I never came across enemy fleets that were sailing in open space.
U-02 — Ore Loop
Area: Economy/mining loop
Task:
Play one full mine → return → buy/refit → relaunch loop. Decide whether the fleet feels meaningfully stronger afterward.
Recommended Answer:
Approve if one purchased or refit hull noticeably changes the next launch.
Why It Matters:
This is the main economy confidence check still open for alpha.
My Answer: It is very easy to get a sizable fleet going in the first zone and fight through the rest of the zones. Ore is plentiful, and all ships can self-repair thanks to damage control systems and transport ships.
Notes / Evidence: early game ore and damage control existing
U-03 — Orbital Layer Subset
Area: Orbital simulation scope
Task:
Pick the small orbital-layer subset for alpha.
Choose one or more:
Navigation drag
Sensor shadows
Logistics quarantine
Presentation-only orbital layer
Recommended Answer:
Sensor shadows plus quarantine warnings.
Why It Matters:
This gives Section 28.9 a narrow life target instead of turning it into a sprawling orbital simulation.
My Choice: sensor shadows and quarantine warnings are best
Notes: I had a hard time understanding what you meant by those choices, but I think what you chose for this is best
U-04 — Placeholder Triage
Area: Placeholder art/audio/UI
Task:
Mark, which placeholder sprites, icons, panels, portraits, and voice lines bother you the most?
Recommended Answer:
Replace only the top 10 most disruptive placeholders before alpha.
Why It Matters:
This prevents time from being wasted polishing placeholders that are acceptable for alpha.
Top Placeholder Problems:
The old placeholder noise for ships being destroyed is just a sine wave
We need to update all crew dialogue and voicelines, so for now, let's remove them and make replacements later
That's it,t all the other audio is ok with me








U-05 — Accessibility Pass
Area: Accessibility and readability
Task:
Run checks for:
Keyboard-only controls
High contrast
Captions
Quiet mode
1280x720 resolution
1920x1080 resolution
Recommended Answer:
Record pass/fail with screenshots for failures.
Why It Matters:
These require human readability judgment.
Results:
Check
Pass / Fail
Notes
Keyboard-only controls
PASS


High contrast
PASS


Captions
PASS


Quiet mode
PASS


1280x720 readability
PASS


1920x1080 readability
PASS




U-06 — Final Scope
Area: Alpha release scope
Task:
Decide whether these systems are alpha blockers or post-alpha features:
Battle replay
Visual battlefield editor
Mod browser
Recommended Answer:
Post-alpha, unless they directly block the release promise.
Why It Matters:
These are large systems, not finishing touches.
Decision: ALL OF THESE SHOULD BE POST ALPHA FEATURES IF THEY EVER GET ADDED
Feature
Alpha Blocker / Post-Alpha
Notes
Battle replay
post


Visual battlefield editor
post


Mod browser
post




Part 2: Detailed Requests
U-07 — Economy and Diplomacy Consequences
Section: 28.3
Need Type: Playtest
Task:
Verify that live economy and diplomacy choices materially change later encounters.
Good Enough Evidence:
Two saves or notes showing different later outcomes after different choices.
Can Codex Continue After This?
Yes. Notes can be turned into tests.
My Findings:
Diplomacy has almost no impact on the game. There is no need to call in traders or even trade with the green or yellow team because the player can just push forward without help. Green picket ships were useful when called in as shields for my own ships because of their high shield threshold, but other than that, they were not needed.
U-08 — Interaction Style
Section: 28.3
Need Type: Content direction
Task:
Decide whether negotiation, favor, and alliance interactions should use:
Terse command UI
Fuller dialogue scenes
A mix of both
Good Enough Evidence:
One preferred interaction style and 2–3 example lines.
Preferred Style:
Terse command UI
Example Lines:
“Green has accepted our trade and has sent escorts to join our fleet
“Yellow has sent us resources as a thank you for freeing their installation from red control.”
“Green and yellow have offered a joint trade.”

U-09 — Writing Tone Approval
Section: 28.3
Need Type: Writing approval
Task:
Approve or rewrite the tone for:
Recurring bulletins
Officer opinions
Logs
Banter
Good Enough Evidence:
A short note such as:
Too dry
Too jokey
Right tone
Needs more military formality
Needs more character personality
My Tone Judgment:
Keep things focused, not over the top militaristic, but casual and focused. We know why we are here and what we are doing, but we don'thave to be so hard about it
U-10 — Art Approval
Section: 28.5
Need Type: Art approval
Task:
Approve current visual assets, including:
Faction hull skins
Turret skins
Damage stages
Wrecks
Plumes
Shields
Trails
Station modules
Props
Portraits
Map icons
Good Enough Evidence:
Approved list plus any “must replace” assets.
Approved Assets:
Faction hull skins, turret skins, shields, trails, station models,
Must Replace Assets:
Damage stages(we are using a lot of different versions of damage visuals at the same time) wrecks(unless you mean the pieces that split apart when a ship gets destroyed then those are ok)
Props
Portals
Map icons
U-11 — Audio Approval
Section: 28.5
Need Type: Audio approval
Task:
Approve current audio, including:
Weapon audio
Engines
Impacts
Ambience
Music behavior
Warnings
Radio distortion
Voice priorities
Audio ducking
Captions
Good Enough Evidence:
Approved list plus any “must replace” sounds.
Approved Audio:
Weapon audio
Engines
Impacts

Must Replace Audio:
Ambience
Music behavior
Warnings
Radio distortion
Voice priorities
Audio ducking
Captions

U-12 — Presentation Review
Section: 28.5
Need Type: Presentation review
Task:
Verify that the following are visually distinct:
Empty space
Hubs
Allied sites
Neutral sites
Hostile sites
Operational districts
Good Enough Evidence:
Screenshot notes with either “distinct enough” or specific failures.
Results:
Visual Area
Distinct Enough?
Notes
Empty space
yes
If the location isnt in a big marked place, dont make the background a planet, make it empty space
Hubs
yes


Allied sites
yes


Neutral sites
yes


Hostile sites
yes


Operational districts
yes




U-13 — Battle Replay Depth
Section: 28.6
Need Type: Scope decision
Task:
Choose battle replay depth:
Event log only
Deterministic playback
Cinematic replay
Good Enough Evidence:
One chosen depth and whether it blocks alpha.
Chosen Replay Depth:
Cinematic replay

Alpha Blocker?
No
Notes:

U-14 — Scenario / Challenge / New Game Plus Priority
Section: 28.6
Need Type: Scope decision
Task:
Choose the priority for:
Custom scenarios
Challenge mode
New Game Plus
Good Enough Evidence:
Rank each as alpha, beta, or post-release.
Decision:
Feature
Alpha / Beta / Post-Release
Notes
Custom scenarios
Post release


Challenge mode
Post release


New Game Plus
Post release




U-15 — Architecture Guardrails
Section: 28.6
Need Type: Architecture acceptance
Task:
Decide whether the current ownership-boundary docs/tests are enough or whether stricter typed-ID migration is required.
Good Enough Evidence:
Accept current guardrails or name the required typed IDs.
Decision:
Current ownership boundary docs/tests are enough
Typed IDs Needed, If Any:

U-16 — Deep Simulation Vertical Slice
Section: 28.9
Need Type: Scope decision
Task:
Pick one deep-simulation vertical slice to finish first:
Stations
Officers
Hazards
Politics
Crises
Legacy/endgames
Good Enough Evidence:
One first slice.
Chosen First Slice:
Politics
Why:
Having politics done first would massively improve gameplay
U-17 — Narrative and Civilian Content Approval
Section: 28.9
Need Type: Narrative/content approval
Task:
Approve named civilian actors, rumors, casualty reports, neutral powers, political blocs, and crisis tone.
Good Enough Evidence:
Approve, revise, or de-scope categories.
Decision:
Category
Approve / Revise / De-Scope
Notes
Civilian actors
approve


Rumors
approve


Casualty reports
approve


Neutral powers
approve


Political blocs
approve


Crisis tone
approve




U-18 — Visual Battlefield Editor Scope
Section: 28.10
Need Type: Tooling scope
Task:
Decide whether the visual battlefield editor should be:
In-game
External/dev-only
Postponed
Good Enough Evidence:
One target surface.
Decision:
Dev only
Notes:
This can happen after the game is complete
U-19 — Community / Modding Scope
Section: 28.10
Need Type: Community scope
Task:
Decide whether these are alpha blockers:
Featured scenarios
Local ratings
Notes
Mod compatibility report
Good Enough Evidence:
Blocker or non-blocker decision for each item.
Decision:
Feature
Alpha Blocker / Non-Blocker
Notes
Featured scenarios
non


Local ratings
non


Notes
non


Mod compatibility report
non




U-20 — Manual Playthrough Coverage
Section: 28.11
Need Type: Manual playthrough
Task:
Run or assign full playthroughs for:
New campaign
Migration
Long campaign
Defeat
Victory
Challenge
Editor
Modded
Safe mode
Good Enough Evidence:
Pass/fail notes with seed, date, and blockers.
Playthrough Results:
Playthrough Type
Pass / Fail
Seed
Date
Blockers / Notes
New campaign
pass






Migration
pass






Long campaign
pass






Defeat
pass






Victory
pass






Challenge
?




Not available
Editor
?




Not available
Modded
?




Not available
Safe mode
?




Not available


U-21 — Balance Judgment
Section: 28.11
Need Type: Balance judgment
Task:
Judge the following systems after playing:
Economy
Logistics
Faction directors
Doctrine
Hazards
Crises
Endgames
Scoring
Good Enough Evidence:
Mark each system as:
Too easy
Too punishing
Unclear
Feels right
Balance Results:
System
Judgment
Notes
Economy
Too easy
The player is easily able to make it through if they just prepare propperly
Logistics
Too easy


Faction directors
Too easy


Doctrine
Too easy


Hazards
Too easy


Crises
Too easy


Endgames
Too easy


Scoring
Too easy




U-22 — Content Pass
Section: 28.11
Need Type: Content pass
Task:
Flag problems with:
Repeated text
Placeholder names
Missing assets
Inaccessible UI states
Dead controls
Unreachable branches
Good Enough Evidence:
A punch list.
Punch List:
Some HUD elements tend to crowd over each other at the top of the screen and in some menus but thats just text being placed on top of other text










Part 3: What Codex Can Still Do Without Me
Codex can continue working on these items without needing additional taste or scope decisions from me:
Add more executable validators around existing data and save schemas.
Convert manual checklist rows into tracked manual-test cases.
Add more deterministic test harnesses where the simulation already exposes a stable state.
Build a narrow orbital-layer implementation after I pick the alpha subset.
Implement whichever deep-simulation vertical slice I choose first.

Final Alpha Decision Summary
Use this section after completing the worksheet.
Systems Approved for Alpha

Systems Delayed Until Post-Alpha

Top 10 Must-Fix Issues Before Alpha










Final Release Confidence
Circle one:
Not Ready / Needs Fixes / Mostly Ready / Ready for Alpha
Final Notes:


# Eagles Remorse Owner Playtest Worksheet

Date created: 2026-07-03  
Purpose: collect the human playtest evidence and design judgment needed for the next stabilization pass.

## How To Use This

- Fill this file in directly. Short answers are enough.
- Do the sessions in any order, but start with Sessions 1, 2, and 3.
- Use a fresh campaign unless a session explicitly says to resume.
- If the game crashes, soft-locks, corrupts a save, or makes progress impossible, stop that session and file an issue. You do not need to play through a blocker.
- Write `N/O` for “not observed” and `N/A` for “not applicable.” Do not guess.
- Screenshots and video are useful for visual, UI, and timing problems. Put their paths in the evidence field.
- Exact numbers are optional. Comparisons such as “too fast,” “about right,” and “too slow” are useful.

### Severity

- `P0` — crash, save corruption, soft lock, cannot finish, or major feature unusable.
- `P1` — serious confusion, unfairness, or broken behavior with a workaround.
- `P2` — noticeable balance, presentation, usability, or polish problem.
- `P3` — minor annoyance or idea for later.

### Rating Scale

Use 1–5 when a row asks for a rating:

- `1` — broken or very poor
- `2` — major problems
- `3` — acceptable but needs work
- `4` — good
- `5` — ready as-is

## Build And Machine

Complete once per build or machine.

| Field | Your Answer |
|---|---|
| Tester | |
| Date | |
| Build/version | |
| Git commit, if known | |
| Packaged build or IDE run | |
| Windows/Linux version | |
| CPU | |
| GPU | |
| RAM | |
| Monitor resolution and scaling | |
| Keyboard/mouse/controller | |
| Save folder used | |

## Session 1 — Blind First Hour

Goal: determine whether a player can understand and use the game without developer knowledge.  
Setup: fresh save, `Campaign Ops`, `Standard Command`, default accessibility settings. Play for 45–60 minutes. Avoid debug tools.

### First Impressions And Onboarding

| Question | Your Answer |
|---|---|
| Could you tell what the game wanted you to do within five minutes? Why or why not? | |
| First point where you felt confused | |
| First control or action you had to hunt for | |
| Did the tutorial teach the action before you needed it? | |
| Were disabled actions clear about why they were unavailable? | |
| Did any panel feel like developer/debug information? Which one? | |
| Onboarding clarity (1–5) | |
| UI readability (1–5) | |

### Strategic Map

| Check | Result / Notes |
|---|---|
| Selecting and changing a route was clear | |
| Route cost, ETA, risk, and likely encounters were understandable | |
| Friendly, neutral, hostile, unknown, objective, hub, and resource markers were visually distinct | |
| Intel quality and contact uncertainty were understandable | |
| A lost contact could be followed or reacquired in a believable way | |
| You understood what the current fleet posture changed | |
| You could identify the most important next action without reading every panel | |
| Map clutter or overlapping labels | |

### First Tactical Encounter

| Question | Your Answer |
|---|---|
| What triggered the encounter, in your own words? | |
| Did the tactical force match what the strategic map led you to expect? | |
| Were the objective and failure condition clear? | |
| Could you identify your flagship, allies, enemies, projectiles, hazards, and damaged ships? | |
| Did weapon range, target lock, missiles, point defense, shields, and room damage make sense? | |
| Were retreat, reserve, reinforcement, and carrier controls discoverable when relevant? | |
| Combat readability (1–5) | |
| Combat enjoyment (1–5) | |

### First-Hour Verdict

| Prompt | Your Answer |
|---|---|
| Three things that worked best | 1.  2.  3. |
| Three things that most need work | 1.  2.  3. |
| Moment you felt most engaged | |
| Moment you felt most lost or bored | |
| Would a new player continue after this hour? Why? | |
| Session result | PASS / FAIL / BLOCKED |
| Evidence paths | |

## Session 2 — Economy, Fleet Growth, And Sustain

Goal: test whether resources create meaningful choices rather than an early snowball or unavoidable collapse.  
Setup: fresh `Campaign Ops` run on `Standard Command`. Complete at least one full mine/salvage → return → repair/trade/refit/commission → relaunch loop.

Record approximate values if convenient.

| Moment | Credits | Fleet Ore | Fuel | Supplies | Ammo | Fleet Size / Notable Hulls |
|---|---:|---:|---:|---:|---:|---|
| Start | | | | | | |
| Before first return to hub | | | | | | |
| After spending at hub | | | | | | |
| After next encounter | | | | | | |

| Question | Your Answer |
|---|---|
| Were `Fleet Ore`, `Yard Ore`, credits, salvage, fuel, supplies, and ammo clearly different? | |
| Was buying versus selling at trade clear? | |
| Did you understand why a hull or upgrade was available/unavailable? | |
| Did one economy loop make the fleet noticeably stronger? | |
| Could you grow too quickly? Explain the easiest exploit or dominant strategy. | |
| Did damage control, transports, hubs, or passive repair erase attrition? | |
| Did repairs or logistics ever feel unfairly expensive or tedious? | |
| Did shortages create an interesting recovery decision? | |
| Were mining and salvage worth the time? | |
| Economy balance | TOO GENEROUS / ABOUT RIGHT / TOO PUNISHING |
| Attrition pressure | TOO LOW / ABOUT RIGHT / TOO HIGH |
| Best purchase | |
| Purchase that felt pointless | |
| Session result | PASS / FAIL / BLOCKED |
| Evidence paths | |

## Session 3 — Living War, NPC Fleets, Intel, And Strikes

Goal: verify that campaign fleets behave like persistent actors and create decisions before combat.  
Setup: use `Standard Command`. Travel north toward Earth, use Recon Sweep at least once, inspect multiple contacts, deliberately lose or break contact with one hostile, and use at least one strategic strike if possible.

### Contact Log

| Contact | First Seen Where | Stated Role/Intent | What It Actually Did | Final Outcome | Believable? |
|---|---|---|---|---|---|
| 1 | | | | | |
| 2 | | | | | |
| 3 | | | | | |

| Question | Your Answer |
|---|---|
| Did hostile fleets close distance, shadow, blockade, retreat, or pursue as advertised? | |
| Did any visible/recent fleet vanish without a named outcome? | |
| When lock broke, were last bearing, estimated vector, and reacquisition guidance useful? | |
| Did Recon Sweep identify, reacquire, expose a decoy, or improve strike quality in a noticeable way? | |
| Did Green, Yellow, trade, patrol, escort, and relief fleets appear to pursue their own goals? | |
| Did the wider war change territory or pressure at a believable pace? | |
| Could you tell why territory changed hands? | |
| Were attack arrows, routes, contacts, and location markers readable and clickable together? | |
| Did the campaign give you a real choice to strike, divert, call support, evade, or fight? | |
| Did the tactical encounter preserve the campaign fleet’s identity, composition, damage, and approach direction? | |

### Strike Judgment

| Question | Your Answer |
|---|---|
| Strike type(s) used | |
| Cost and current inventory were clear | |
| How rearming/making/buying/salvaging more strikes works was clear | |
| The strike had a visible campaign effect before battle | |
| The target reacted believably after being struck | |
| Strike power | TOO WEAK / ABOUT RIGHT / TOO STRONG |
| Strike availability | TOO SCARCE / ABOUT RIGHT / TOO PLENTIFUL |
| Would you often use strikes as a free extra punch? Why? | |

### Living-War Verdict

| Prompt | Your Answer |
|---|---|
| Most convincing NPC fleet behavior | |
| Least convincing NPC fleet behavior | |
| Any idle, stuck, teleporting, duplicated, or vanishing fleet | |
| War pacing | TOO STATIC / ABOUT RIGHT / TOO CHAOTIC |
| Session result | PASS / FAIL / BLOCKED |
| Seed and save/checkpoint | |
| Evidence paths | |

## Session 4 — Tactical Combat And Fleet Command

Goal: judge control feel, battle readability, fleet roles, damage, and difficulty.  
Setup: use Campaign Ops or Custom Battles. Play at least three fights: small/even, carrier-or-missile-heavy, and large/capital-or-titan-heavy.

| Area | Rating (1–5) | Notes |
|---|---:|---|
| Movement and camera | | |
| Aiming, locking, and firing | | |
| Weapon feedback and impact | | |
| Shield/hull/room damage readability | | |
| Internal/x-ray view usefulness | | |
| Fleet command and selection | | |
| Reserve/reinforcement control | | |
| Carrier and strike-craft control | | |
| Missile and point-defense counterplay | | |
| Retreat/disengagement | | |
| Enemy AI competence | | |
| Large-battle readability | | |
| Combat audio | | |
| Performance during peak combat | | |

| Question | Your Answer |
|---|---|
| Strongest weapon, hull, or tactic | |
| Weakest weapon, hull, or tactic | |
| Could one strategy solve nearly every fight? | |
| Did any ship role fail to have a useful job? | |
| Were capitals/titans threatening without feeling unfair? | |
| Did damage, fire, decompression, casualties, repairs, and power failures create understandable choices? | |
| Did ships survive too easily or die too quickly? | |
| Did allied/enemy ships behave intelligently around range, missiles, retreat, and objectives? | |
| Worst visual confusion | |
| Worst control friction | |
| Session result | PASS / FAIL / BLOCKED |
| Evidence paths | |

## Session 5 — Persistence, Defeat, Victory, And Long-Run Pacing

Goal: test the full campaign arc and state continuity. This may be split across several days.

### Save/Load Check

Before saving, change posture, finish a travel leg, spend at a hub, inspect contacts, and alter the fleet if possible.

| State | Preserved Correctly? | Notes |
|---|---|---|
| Location, route, and selected target | | |
| Resources and hub purchases | | |
| Fleet roster, names, damage, groups, and reserves | | |
| Posture and command settings | | |
| Known contacts, intel, and last-known positions | | |
| Faction relationships, favors, and named contacts | | |
| Territory, operations, route scars, and campaign history | | |
| Objectives, site outcomes, and depleted/visited sites | | |
| Strike inventory and readiness | | |

### Defeat Path

| Question | Your Answer |
|---|---|
| How defeat occurred | |
| Was the reason for defeat clear? | |
| Did failure text and recovery options make sense? | |
| Could you start a clean new campaign afterward? | |
| Did defeat feel earned? | |

### Victory And Campaign Arc

| Question | Your Answer |
|---|---|
| Approximate time to victory | |
| Was the main objective always understandable? | |
| Did early, middle, and late campaign feel meaningfully different? | |
| Did optional sites, allies, diplomacy, and fleet growth matter to success? | |
| Did the campaign become repetitive? When and why? | |
| Did pressure rise too slowly, appropriately, or too quickly? | |
| Was victory possible without debug tools? | |
| Did the ending reflect major choices and campaign state? | |
| Did unlock/result persistence work after returning to the menu? | |
| Overall campaign pacing (1–5) | |
| Overall campaign satisfaction (1–5) | |
| Session result | PASS / FAIL / BLOCKED |
| Ending reached | |
| Seed and save/checkpoint | |
| Evidence paths | |

## Session 6 — Difficulty And Accessibility Spot Check

Goal: catch preset or accessibility behavior that cannot be judged by automated tests alone. Full campaigns are not required.

### Difficulty Presets

Play 15–30 minutes on each available preset that you expect to ship.

| Preset | Clearly Different? | Fair? | Main Problem | Ship It? |
|---|---|---|---|---|
| Relaxed Campaign | | | | YES / NO |
| Standard Command | | | | YES / NO |
| Tactical Only | | | | YES / NO |
| Command Only | | | | YES / NO |
| Iron Command | | | | YES / NO |

### Accessibility And Display

| Check | PASS / FAIL / N/A | Notes / Evidence |
|---|---|---|
| Keyboard-only menu and core play | | |
| Remapped controls persist and conflict messages make sense | | |
| 1280×720, 100% scale | | |
| Native resolution and normal scale | | |
| Fullscreen toggle preserves focus and input | | |
| UI text scale at minimum | | |
| UI text scale at maximum | | |
| High-contrast HUD | | |
| Deuteranopia palette | | |
| Protanopia palette | | |
| Tritanopia palette | | |
| Reduced flash | | |
| Reduced screen shake | | |
| Subtitle size/background/speaker labels | | |
| Hold/toggle options for mining, firing, and map | | |
| Pause on focus loss | | |
| Simplified/low-detail rendering | | |

## Session 7 — Mode Smoke Test

Goal: ensure every visible main-menu mode is honest, understandable, and functional. Spend about 10–15 minutes in each mode; longer only if something needs investigation.

| Mode | Starts? | Goal Clear? | Can Finish/Exit? | Main Issue | Keep Visible? |
|---|---|---|---|---|---|
| Tutorial / Command School | | | | | YES / NO |
| Campaign Ops | | | | | YES / NO |
| Fleet | | | | | YES / NO |
| Last Stand | | | | | YES / NO |
| Resource Rush | | | | | YES / NO |
| 4 Team Domination | | | | | YES / NO |
| Custom Battles | | | | | YES / NO |
| Shooting Range | | | | | YES / NO |
| Showcase | | | | | YES / NO |

## Session 8 — Art, Audio, And Presentation Approval

Goal: identify only the assets that meaningfully damage the experience. `Approve` means good enough for the next release, not necessarily final forever.

| Category | APPROVE / REVISE / N/O | Specific Problem Or Asset |
|---|---|---|
| Faction hull silhouettes and skins | | |
| Turrets and projectiles | | |
| Damage stages and internal damage | | |
| Destruction, wrecks, and debris | | |
| Shields, trails, explosions, and hazards | | |
| Stations, props, portals, and environments | | |
| Strategic map icons and overlays | | |
| HUD panels, buttons, typography, and tooltips | | |
| Portraits and character presentation | | |
| Weapon, engine, impact, warning, and destruction audio | | |
| Ambience and music behavior | | |
| Voice/radio behavior, if currently enabled | | |
| Repeated, placeholder, contradictory, or awkward text | | |

Top five presentation fixes, in priority order:

1. 
2. 
3. 
4. 
5. 

## Final Owner Decisions

Complete after enough play to form an opinion.

| Decision | Your Answer |
|---|---|
| What is the game’s strongest feature today? | |
| What is the weakest feature that must improve before wider testing? | |
| What kind of player is this build for? | |
| What should an average successful campaign length be? | |
| Should optional modes remain visible if Campaign Ops is the primary experience? | |
| Which difficulty preset should be the default? | |
| Which three systems must not receive more scope before stabilization? | 1.  2.  3. |
| Which one system deserves deeper work after stabilization? | |
| Are current damage visuals approved for the next release? | YES / NO — notes: |
| Are current wrecks, props, portals, and map icons approved? | YES / NO — notes: |
| Is the current campaign balance good enough for outside testers? | YES / NO — notes: |
| Is the current tutorial good enough for someone who has never seen the project? | YES / NO — notes: |
| Release recommendation | GO / GO WITH KNOWN ISSUES / NO-GO |
| Known issues you explicitly accept | |
| Non-negotiable blockers | |

## Issue Report Template

Copy this block once per problem. One strong report is more useful than several vague mentions.

```text
Issue ID:
Short title:
Severity: P0 / P1 / P2 / P3
Build:
Mode and preset:
Seed:
Save/checkpoint:
Location/objective/contact:

What I was trying to do:
What I did:
What I expected:
What actually happened:
Can I reproduce it? ALWAYS / SOMETIMES / ONCE
Workaround, if any:

Screenshot/video path:
Relevant error text or log path:
Extra notes:
```

## Final Summary For Codex

Fill this section last. It lets Codex triage the whole worksheet quickly.

| Priority | Item |
|---|---|
| P0 blocker 1 | |
| P0 blocker 2 | |
| P1 issue 1 | |
| P1 issue 2 | |
| P1 issue 3 | |
| Best balance change to make next | |
| Best usability change to make next | |
| Best presentation change to make next | |
| Important thing that should remain unchanged | |

Anything else you want me to understand:



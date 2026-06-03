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

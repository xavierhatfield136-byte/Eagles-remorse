# 1.0 Owner Input Worksheet

Date created: 2026-06-23
Status: Completed by owner on 2026-06-23; decisions extracted into
`1_0_OWNER_DECISIONS_AND_IMPLEMENTATION_ROADMAP.md`.

## Purpose

This worksheet collects the decisions and human playtest judgments that Codex
cannot determine reliably from code or automated tests. Fill in as much as you
can, then return the document. Short answers are fine.

Use these answers whenever useful:

- `APPROVE` - the current behavior is good enough.
- `CHANGE` - use the notes to revise it.
- `DEFER` - move it after 1.0.
- `CODEX DECIDE` - choose the best conservative implementation.
- `NOT TESTED` - keep the question open.

Questions marked **RELEASE** can materially change the 1.0 scope. Questions
marked **PLAYTEST** require hands-on judgment. Questions marked **TASTE** are
creative or presentation choices.

## Already Settled

These decisions are carried forward and do not need to be answered again:

- Windows is the first supported platform.
- The current 2D Java game is the release project.
- The target campaign length is approximately 8-15 hours.
- Resource shortages may cause failure when warnings and recovery options exist.
- Ships and crew may be lost permanently.
- Main actions should be visible in the UI rather than hidden behind hotkeys.
- Multiplayer, New Game Plus, challenge mode, a mod browser, custom scenarios,
  and a visual battlefield editor are post-release.
- A future battle replay is not a 1.0 blocker.
- The preferred tone is serious and focused without excessive military formality.
- Negotiation, favors, and alliances should primarily use terse command UI.
- Sensor shadows and quarantine warnings are the preferred narrow orbital layer.
- Temporary crew voices should remain removed until suitable replacements exist.

## 1. Define The 1.0 Promise

### Q001 - Release identity [RELEASE]

In one or two sentences, what should a player be able to tell a friend this game
is?

Answer: This game is a top down 2d space fleet command experience with a very deep and a long running campaign that responds to how the player interacts with the campaign. the game is known for its deep and complex systems, detailed damage models, smooth gameplay, diversity of playstyles, and freedom to play however you wish


### Q002 - Mandatory 1.0 experience [RELEASE]

Which experience must be excellent for you to call the game 1.0?

- [X] Strategic fleet command
- [X] Tactical ship combat
- [X] Living faction war
- [X] Fleet building and logistics
- [ ] Story-driven journey home
- [X] Replayable campaign simulation
- [ ] Other:

Notes: Im not too worried about the story right now, that will be the very last thing i worry about


### Q003 - Release standard [RELEASE]

Choose the closest target:

- [ ] Small but polished commercial-style 1.0
- [ ] Large experimental 1.0 with some rough edges
- [X] Free public 1.0 where breadth matters more than polish
- [ ] CODEX DECIDE

Notes:


### Q004 - Systems allowed to remain shallow [RELEASE]

Which systems may remain simple in 1.0 rather than receiving another major
expansion?

Answer: CODEX CHOICE


### Q005 - Systems that must not be cut [RELEASE]

Name anything you would rather delay the release for than simplify or remove.

Answer: strikes, internal damage models, smart overworld fleet movements that make the campaign feel alive


### Q006 - Explicit post-1.0 scope [RELEASE]

Should politics, officers, crises, advanced civilian simulation, and campaign
legacy systems be required for 1.0 or treated as later expansions?

Answer: NO, those can all be post release


## 2. Overall Campaign Judgment

### Q007 - Current campaign enjoyment [PLAYTEST]

Rate the current campaign from 1-10 and explain the biggest reason for that
score.

Score:9 

Reason:the game does take a while, and thats ok, but sometimes i get bored not seeing the larger ships that the other factions have, destroying line and picket ships are fun, but you rarely if ever see a capital ship. I want the other factions to have vast fleets that they can pull from, maybe yellow not so much because their lore is they just discovered FTL trade


### Q008 - Strongest part [PLAYTEST]

What part of the game currently makes you most want to keep playing?

Answer: the fleet building, the ramping up of strength, building an unstoppable force that goes up that can stand up to the red tyrany. also the detailed damage models, dynamic gameplay, other faction behaviors, and just how many ways there are to play


### Q009 - Weakest part [PLAYTEST]

What part most often makes you bored, confused, or tempted to stop?

Answer: some of the old text that can be confusing, but the lack of green or yellow fleets on the map makes me feel alone, also, sometimes objects on the overworld map have a way harder hitbox for destination setting so it makes it hard to navigate exactly where i want to go
also, a lack of challenging red ships, no real forces outside of a few picket ships, and its a miracle if i see anything bigger then a light cruiser. a lack of green fleets to make me feel like a part of a combined effort to take back earth. yellow just not being relevant outside of trading
. i want yellow to be what italy was to germany during ww2, however, like historical italy, i want them to be able to be swayed to the blue green alliance because they have really strong ships that would be a shame for them to miss out on. A lack of any other faction having titans that the player can come across
. imagine if the player could come across a red fleet, but suprise! they have several titan ships!. this would make the game so much more fun because now instead of spearing small fry you can go for "the big one" and get a large payout plus reputation bonuses. 
a lack of challenge in terms of later sector difficulty


### Q010 - Meaningful decisions [PLAYTEST]

List three campaign decisions that currently feel meaningful and three that feel
fake, automatic, or inconsequential.

Meaningful: random events, large hub areas, and the ability to move freely around

Inconsequential: enemy fleets, they are just a few picket ships that get deleted instantly. the background, that needs to get changed to something else. thats all i can think of.


### Q011 - Campaign pacing [PLAYTEST]

Mark each phase:

| Phase | Too slow | Good | Too fast | Notes |
| --- |----------|------| --- | --- |
| Opening hour | [ ]      | [x]  | [ ] | |
| Early campaign | [ ]      | [x]  | [ ] | |
| Mid campaign | [ ]      | [x]  | [ ] | |
| Late campaign | [x]      | [ ]  | [ ] | |
| Ending | [x]      | [ ]  | [ ] | |

### Q012 - Campaign length [PLAYTEST]

After playing the current build, is 8-15 hours still the correct target?

Answer: yes


### Q013 - Replay motivation [TASTE]

What should make a second campaign feel different from the first?

Answer: you can choose to make a new loadout of ships, go make new allies, go to different parts of the map, and just play differently


## 3. Difficulty And Failure

### Q014 - Baseline difficulty [PLAYTEST]

How difficult should Standard Command be?

- [ ] Most players should win their first campaign
- [X] Most players should win after learning from one defeat
- [ ] Victory should require several attempts
- [ ] CODEX DECIDE

Notes:


### Q015 - Current pressure [PLAYTEST]

For each system, mark its current feel:

| System | Too easy | Good | Too punishing | Unclear | Not tested |
| --- |----------|------| --- |---------| --- |
| Tactical combat | [X]      | [ ]  | [ ] | [ ]     | [ ] |
| Strategic travel | [X]      | [ ]  | [ ] | [ ]     | [ ] |
| Fuel | [X]      | [ ]  | [ ] | [ ]     | [ ] |
| Supplies | [X]      | [ ]  | [ ] | [ ]     | [ ] |
| Ammunition | [X]      | [ ]  | [ ] | [ ]     | [ ] |
| Repairs | [X]      | [ ]  | [ ] | [ ]     | [ ] |
| Fleet losses | [ ]      | [X]  | [ ] | [ ]     | [ ] |
| Enemy expansion | [X]      | [ ]  | [ ] | [ ]     | [ ] |
| Mission timers | [ ]      | [ ]  | [ ] | [X]     | [ ] |

### Q016 - Acceptable failure frequency [TASTE]

How often should a capable player suffer a major ship loss, failed mission, or
forced retreat during a complete Standard campaign?

Answer:once every 5 ish battles, i dont want it to be too easy


### Q017 - Recovery after defeat [TASTE]

Should a badly damaged fleet usually be able to recover, or should severe losses
often create an irreversible downward spiral?

Answer: they should be able to recover if they make it to a safe area or a friendly/ captured hub


### Q018 - Iron Command [PLAYTEST]

What should make Iron Command different besides larger enemy numbers?

Answer: give enemies better armor for the harder gamemode and quicken the delay before they begin to regen shield. this is only for the hard version


## 4. Starting Fleets And Force Scale

### Q019 - Starting fleet feeling [PLAYTEST]

Should the player begin as:

- [X] A vulnerable flotilla
- [ ] A credible regional task force
- [ ] A major fleet that will gradually be worn down
- [ ] CODEX DECIDE

Notes: the current starting point is good, but the player should get a miner off rip because its just not fun to not have one as soon as the game starts


### Q020 - Starting fleet size [PLAYTEST]

Does the current player starting fleet feel too small, correct, or too large?
Give a preferred rough number of combat ships and support ships if possible.

Answer: correct, just a few picket ships and the mothership.


### Q021 - Faction starting strength [PLAYTEST]

For each faction, describe the desired starting impression:

| Faction | Desired fleet scale and character                                                                                                                                   |
| --- |---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Blue/player | very weak, you were a trade/exploration force from earth making friends with green and suddenly, oh no!, earth has fallen to rouge ai!                              |
| Red/hostile | very very dangerous, they are what blue team was but now ai controlled, however, green is fighting them and they are having issues keeping yellow under their thumb |
| Green/allied | strong, they are slowly pushing back against red and yellow but having a hard time.                                                                                 |
| Yellow/neutral | smaller but also strong, they are split between fighting for red and fighting against red, its up to green and the player to free them                              |

### Q022 - Fleet concentration [TASTE]

Should factions field a few large task forces, many small groups, or a mix?

Answer: both a few large task forces and many small groups.


### Q023 - Maximum battle scale [PLAYTEST]

What is the largest battle that still feels readable and enjoyable? Estimate the
number of ships per side.

Answer: as many as the game can handle


### Q024 - Reinforcement growth [TASTE]

How quickly should factions replace losses and build new fleets?

Answer: they should only be able to replenish losses if their miners off camera have gotten the ore, they then got the credits, and were then able to build the ships at shipyards or from a mothership or mobile station ship. so about as fast as the player can make new ships


### Q025 - Finite fleets [RELEASE]

Should every major faction ship be part of a finite, traceable inventory, or may
the game generate limited abstract reinforcements when required for pacing?

Answer: traceable inventory, if one side doesnt have enough ships to stop a gap and the other faction rolls in? too bad, better stop them before they get to a major hub.


## 5. Economy, Mining, And Shipyards

### Q026 - Ore availability [PLAYTEST]

Does ore currently feel too plentiful, correct, or too scarce in the opening,
middle, and late campaign?

Answer: its fair, a lot of it gets used as a trade good to get credits to then be able to afford the ships and upgrades that the player needs, sometimes the player is running low but i think that is healthy


### Q027 - Mining activity [PLAYTEST]

Is mining engaging enough to remain a player activity, or should it be more
automated and strategic?

Answer: it should definitely be automated and strategic, because right now mining is 'go to asteroid, click mine button, wait' and that is not fun


### Q028 - Ore transport visibility [TASTE]

How visibly should mined ore travel to shipyards?

- [ ] Physical mining and transport fleets must carry it
- [ ] Strategic routes and inventory updates are enough
- [ ] Use physical fleets for important shipments only
- [X] CODEX DECIDE

Notes: i would like it so that transports have to bring it back, i would also like it so that the factions create mining task forces that go out, try to find mining areas, and then try to bring them back because if the player finds them then they can kill those groups and steal ore, meaning that we can then add yellow and red mining ships to the game


### Q029 - Shipyard construction time [PLAYTEST]

How long should common ships take to build in campaign time?

| Ship class | Preferred construction time |
| --- |-----------------------------|
| Small escort | 5 seconds                   |
| Frigate/destroyer | 10 seconds                  |
| Cruiser | 15 seconds                  |
| Capital ship | 20 seconds                  |
| Titan/special hull | 25                          |
#notes: allow for production ques so the player can order many ships at the same time and the player will then have those ships produced in que order, also make it so that each type of ship has its own production que so small escorts dont clog up the frigate/ destroyer line and etc.
### Q030 - Local yard limits [TASTE]

Should each shipyard have its own ore, production slots, build queue, and hull
catalog, or should friendly yards share part of their economy?

Answer: each shipyard should share in its economy, so that if the player wants to interact with them, they will be interacting with the rest of that faction.


### Q031 - Player purchasing [PLAYTEST]

Should purchasing a ship deliver it immediately, place it in a construction
queue, or depend on whether the hull already exists in local inventory?

Answer: production que, and make the ship a hull from the producers faction, not the player faction, so the player can diversify the fleet


### Q032 - Transport ship role [PLAYTEST]

What should transport ships contribute, and what would make them too strong?

Answer: they should contribute to the players ore capacity, they should heal nearby allies slowly, and be important in team fights by massively reducing the effects of fire and other terminal ailments. they would be too strong if they were able to negate
all incoming damage from enemy ships, so make it so that they cant heal armor or shield.


### Q033 - Repair limits [PLAYTEST]

How should damage control and transport-assisted repairs be constrained?

- [ ] Spare parts
- [X] Supplies
- [ ] Repair capacity per route or battle
- [ ] Only partial field repairs
- [ ] Permanent damage until shipyard service
- [ ] Other:

Notes: this helps give supplies a reason to exist as well as keeping ships alive


### Q034 - Desired scarcity [TASTE]

Which resource should be the campaign's primary limiting factor?

Answer: credits, they are already the main limiting factor because they are used in trade, building, modifying, intel gathering, reputation increasing, and so much more


### Q035 - Trade necessity [PLAYTEST]

How often should a successful player need Green or Yellow trade rather than
remaining self-sufficient?

Answer: often, the player cant generate credits reliably unless they trade and this is great for gameplay


### Q036 - Economic snowball [PLAYTEST]

At what point should fleet growth stop accelerating? Describe what should prevent
the player from becoming unstoppable after a strong opening.

Answer: the player should be balanced out by a strong enemy, why reduce the capability of the player when that would make the game less fun?
also the player is hampered by constanly needing to obtain credits which they will do by trading which is the point (this stops immediately fully upgrading everything because credits are hard to get which is good)


## 6. Strategic War And Fleet AI

### Q037 - Open-space fleet density [PLAYTEST]

How often should the player see another fleet while traveling through ordinary
space?

Answer: always. wether it be friendly, yellow, or red, you should always have company. even if it is just a small mining deployment


### Q038 - Contact interruption rate [PLAYTEST]

How many meaningful contacts or events should occur during a typical travel leg?

Answer: 5-8


### Q039 - Enemy aggression [PLAYTEST]

Should Red prioritize hunting the player, attacking infrastructure, intercepting
logistics, or winning the wider war?

Rank them: a mix of all


### Q040 - Allied autonomy [TASTE]

Should Green win battles and capture territory without the player, or mainly act
as support for player operations?

Answer: should be able to capture and win, as well as loose territory and battles


### Q041 - Neutral behavior [TASTE]

Under what conditions should Yellow become friendly, hostile, or remain
transactional?

Answer: IF red is in the mission/site, they are hostile, if not, then they are transactional, if they are ships bought by green or blue, allied. they are the begrudging helpers of red and they are not too happy with their ai overlords


### Q042 - Off-screen battles [PLAYTEST]

Should important AI-versus-AI battles resolve without the player, and how much
warning or opportunity to intervene should the player receive?

Answer: they should most certainly happen without the player, the player should get news on where the battle will happen 30 seconds before they happen. i also want a feature that allows the player to join up and follow other fleets into battle like green and yellow already do with blue



### Q043 - Fleet escape and despawn [PLAYTEST]

When an observed enemy moves beyond sensor coverage, should it continue as a
persistent hidden fleet, retreat to a known base, or sometimes leave the active
theater?

Answer:persistent hidden fleet


### Q044 - War predictability [TASTE]

Should fixed seeds produce largely predictable wars, or should faction choices
create major divergence?

Answer: faction choices create major divergence


### Q045 - Anti-stalemate behavior [TASTE]

If neither side can make progress, what should break the stalemate?

Answer: the player and their actions/ support


## 7. Contacts, Intelligence, And Task Forces

### Q046 - Desired uncertainty [PLAYTEST]

Does the current contact-intelligence system reveal too much, too little, or the
right amount?

Answer: right amount


### Q047 - Contact inspection [PLAYTEST]

Which facts should become visible at each intelligence level?

| Information | Early contact | Identified | High intelligence |
| --- |---------------|------------|-------------------|
| Faction | X             |            |                   |
| Ship count |               | X          |                   |
| Ship classes |               |            | X                 |
| Names |               |            | X                 |
| Damage/readiness |               |            | X                 |
| Cargo/logistics |               |            | X                 |
| Origin/destination |               | X          |                   |
| Mission/intent |               |            | X                 |

### Q048 - Search gameplay [PLAYTEST]

When contact is lost, is reacquiring it interesting, frustrating, or too easy?
What should improve?

Answer: very very frustrating, if a contact is lost, remove their icon from the hud so the player doesnt go on a wild goose chase for something that is already long gone.


### Q049 - Deception [TASTE]

How common should false contacts, decoys, spoofed identities, and ambushes be?

Answer: false contacts, decoys, and spoofed ID's should not be added. in the current state of the game. ambushes should be rare but not unheard of


### Q050 - Silent Hunter-style marker [TASTE]

Does the current large task-force marker and inspection approach match what you
wanted, or should it display more or less information?

Answer: more information


## 8. Tactical Combat

### Q051 - Combat duration [PLAYTEST]

How long should typical, large, and boss battles last?

| Battle | Desired duration |
| --- |------------------|
| Small encounter | a minute or 2    |
| Standard fleet battle | 3 minutes        |
| Major fleet battle | 5 minites        |
| Boss/final battle | 6 minutes +      |

### Q052 - Time-to-kill [PLAYTEST]

Do ships die too quickly, too slowly, or at the correct pace?

Answer: correct pace, this has been consistent all the way since the beginning


### Q053 - Player workload [PLAYTEST]

Is the player controlling too many things at once? Which controls or decisions
should be automated, simplified, or made more prominent?

Answer: the player is controlling just the right amount, the player can order the motherships crew into automation mode so the player can order the fleet around using the formation change button


### Q054 - Fleet doctrine impact [PLAYTEST]

Can you clearly feel the difference between doctrines and postures? Which ones
feel useless or dominant?

Answer: i can, line is useless


### Q055 - Retreat and surrender [PLAYTEST]

Are retreat and surrender currently viable decisions, or do they feel like
failure buttons with no strategic value?

Answer: they are fail buttons, the only retreat should be if the player can hit the "safe exit" button and not get hit during the 7.5 second wind up


### Q056 - Strategic strikes [PLAYTEST]

After the recent changes, answer:

- Are strikes still too plentiful?
- NO
- Are they still too accurate or guaranteed?
- NO
- Are their costs understandable?
- NO, they basically dont have a cost
- Is replenishment discoverable?
- NOT AT ALL
- Should enemies use them more often?
- NO, they should not get strikes like torpedo, airstrike, and nuke because that would massively upset the game balance and allow npc ships to command kill friendly ships because of a bug that makes the strikes originate on top of the target ship and not the ship that launched the strike

Answers are above


### Q057 - Titans and superweapons [PLAYTEST]

Should titans be rare campaign-defining assets or recurring late-game combatants?
Are their current weapons understandable and fair?

Answer: rare campaign defining assets that make the player pay attenting to what they are doing, more so then the usual


### Q058 - Damage and repairs [PLAYTEST]

Does battle damage create memorable consequences, or is it erased too easily
after combat?

Answer: easily erased


### Q059 - Tactical environments [PLAYTEST]

Which environments meaningfully change how you fight, and which are only visual?

Answer: there are no enviornments, its just empty space and them sometimes asteroids, which get deleted by cannon fire


### Q060 - Allied AI trust [PLAYTEST]

When allied ships make mistakes, does it feel understandable, unfair, or merely
unfinished? Give examples.

Answer: they dont make mistakes, as far as i can see, so its unfinished


## 9. Missions, Victory, And Narrative

### Q061 - Mission variety [PLAYTEST]

Which mission types are fun, repetitive, confusing, or missing?

Answer: repetitive missions are the ones with no combat, just mining, trading, and building, but this is good
confusing missions are not present
i dont know of any missing missions, so CODEX, look and see if there are any missing ones


### Q062 - Objective clarity [PLAYTEST]

How often do you enter a mission without understanding what must be done to win
or what can cause failure?

Answer: every single time, i only learn what needs to be done when i look around and see whats happening. also i know failing is always loosing the mothership or loosing my capital ships


### Q063 - Optional objectives [TASTE]

Should optional objectives mainly grant resources, alter later missions, affect
faction relations, or change the ending?

Answer: grant resources, affect reputation, and alter later missions


### Q064 - Main campaign structure [RELEASE]

Should 1.0 retain the current mostly linear journey, add meaningful route
branches, or support several substantially different campaign paths?

Answer: the journey is not that linear as is, the player is constantly moving left and right as well as up, so what we have now is good


### Q065 - Ending quality [PLAYTEST]

Does the current victory feel earned and conclusive? What should the ending show
or summarize?

Answer: yes, it does feel earned and conclusive unless the player managed to leave large red remnants


### Q066 - Defeat quality [PLAYTEST]

Does defeat clearly explain what happened and make starting again appealing?

Answer: yes


### Q067 - Character presence [TASTE]

How much personality should named captains and bridge officers have in 1.0?

Answer: a lot of personality


### Q068 - Politics priority [RELEASE]

You previously selected politics as the first deep-simulation slice. Is it still
required before 1.0? If yes, what are the minimum political decisions the player
must be able to make?

Answer: YES; the player should be able to send ore, credits, info, and ships to either red or yellow for reputation


### Q069 - Campaign memory [TASTE]

Which player actions must be remembered and referenced later by factions,
officers, reports, or the ending?

Answer: trading, reputations, ship kills, ore mined, how many times the player helped the other factions


## 10. UI And Accessibility

### Q070 - Most confusing screen [PLAYTEST]

Which screen or panel currently takes the most effort to understand?

Answer: the ones with the most text crammed into them visually


### Q071 - Information density [PLAYTEST]

Where does the game show too much information, and where does it hide information
you need?

Answer: reputation is hard to see, current other ships hp in the fleet is hard to see


### Q072 - Terminology [PLAYTEST]

List any labels, resources, actions, or military terms that remain confusing.

Answer: CODEX sweep the game for this


### Q073 - HUD overlap [PLAYTEST]

Record every resolution and screen where text overlaps, crowds, clips, or becomes
too small. Screenshots are ideal but not required.

Answer: not an issue


### Q074 - Mouse versus keyboard [PLAYTEST]

Which actions are awkward with the mouse? Which are awkward with the keyboard?

Answer: none


### Q075 - Accessibility confidence [PLAYTEST]

Reconfirm or revise the earlier PASS judgment:

| Area | Pass | Fail | Not tested | Notes |
| --- |------| --- |------------| --- |
| Keyboard-only navigation | [ ]  | [ ] | [X ]       | |
| Control remapping | [ ]  | [ ] | [ X]       | |
| High contrast | [ X] | [ ] | [ ]        | |
| Color-independent symbols | [ X] | [ ] | [ ]        | |
| Captions | [ ]  | [ ] | [ X]       | |
| Quiet/reduced-noise mode | [ ]  | [ ] | [ X]       | |
| 1280x720 | [ X] | [ ] | [ ]        | |
| 1920x1080 | [X ] | [ ] | [ ]        | |
| Fullscreen/window switching | [ ]  | [ ] | [X ]       | |

### Q076 - Tutorial burden [PLAYTEST]

What did you have to learn through experimentation or by asking Codex rather than
through the game?

Answer: how the code runs and what changed every update


## 11. Art, Audio, And Presentation

### Q077 - Visual identity [TASTE]

Does the game consistently achieve the intended industrial naval science-fiction
style? List the strongest and weakest examples.

Answer: YES, it does and it does it quite well, only isses are deep space encounters sometimes using planet backgrounds when the background should be space


### Q078 - Must-replace visuals [RELEASE]

List the remaining visual assets that would make you uncomfortable releasing
1.0 if unchanged.

Answer:some of the yellow ships have a triangle chipped out of the right front side, which makes them look strange visually


### Q079 - Damage visuals [PLAYTEST]

Are damage stages now visually consistent and easy to read? What still looks
wrong?

Answer: YES, they are good


### Q080 - Map readability [PLAYTEST]

Can you quickly distinguish fleets, facilities, missions, resources, hazards,
and objectives without opening every item?

Answer: Yes


### Q081 - Audio fatigue [PLAYTEST]

After at least 30 minutes, which sounds become repetitive, irritating, unclear,
or too loud?

Answer: the warping. sfx become repetitive, but the missile launching sft is not working right


### Q082 - Music direction [TASTE]

Should 1.0 have more continuous music, mostly ambient silence, or music reserved
for detection, pursuit, battle, and major events?

Answer: ambient silence


### Q083 - Voice scope [RELEASE]

For 1.0, choose one:

- [ ] No spoken crew dialogue
- [X] Limited high-quality callouts only
- [ ] Full bridge and narrative voice set
- [ ] CODEX DECIDE

Notes:


### Q084 - Presentation distinctions [PLAYTEST]

Do empty space, hubs, allied areas, neutral areas, hostile areas, and major
operational districts still feel visually distinct?

Answer: yes they do


## 12. Stability, Packaging, And Release

### Q085 - Hardware target [RELEASE]

Describe the weakest computer you expect 1.0 to support, if known.

Answer: UNKNOWN, codex do this one


### Q086 - Performance tolerance [PLAYTEST]

Are occasional drops below 30 FPS acceptable during the very largest battles?
What slowdown becomes unacceptable?

Answer: anything below 30 fps is unacceptable


### Q087 - Save compatibility [RELEASE]

Must alpha/beta saves continue working in 1.0, or may the final release require a
new campaign?

Answer: they must continue working


### Q088 - Public release channel [RELEASE]

How do you expect players to obtain 1.0?

- [ ] Direct downloadable Windows build
- [X] itch.io
- [X] Steam
- [X] GitHub release
- [X] Private distribution
- [ ] Undecided
- [ ] Other:

Notes:


### Q089 - External testers [RELEASE]

How many people besides you should complete at least one meaningful play session
before 1.0?

Answer: me and a few colleagues 


### Q090 - Crash tolerance [RELEASE]

What release threshold do you want?

- [X] No known reproducible crashes or save corruption
- [ ] No known crashes, plus multiple complete clean campaigns
- [ ] Best effort is acceptable for an experimental 1.0
- [ ] CODEX DECIDE

Notes:


### Q091 - Known issues [RELEASE]

Which kinds of known issue are acceptable in 1.0?

Answer: i do not know of any issues, codex, do another scan for this one


### Q092 - Version progression [RELEASE]

Choose the preferred path:

- [ ] Alpha, beta, release candidate, then 1.0
- [X] Alpha, then 1.0 after fixes
- [ ] Continue internal development until 1.0
- [ ] CODEX DECIDE

Notes:


## 13. Structured Playtest Reports

Complete these after relevant sessions. Duplicate the template as needed.

### Playtest Report

Build or commit:

Date:

Duration:

Difficulty:

Campaign seed:

Starting or resumed campaign:

What I tried to accomplish:

What worked well:

What confused me:

What felt too easy:

What felt too punishing:

What felt repetitive:

What surprised me:

Any crash, lock, broken control, or lost state:

Most important change before the next build:

Screenshot, video, save, or log location:

Overall session score out of 10:

## 14. Final Priorities

### Q093 - Top ten remaining problems

List the ten problems that matter most to you, in order.

1. npc fleets from the other factions
2. titans and larger ships appearing in other fleets
3. dynamic territory that changes depending on who is controlling them
4. keeping the game functional
5. cleaning up any bloat
6. 
7.
8.
9.
10.

### Q094 - Three things not to disturb

List three parts of the game that already feel right and should not be
substantially redesigned.

1.
2.
3.

### Q095 - Final delegation

For any unanswered question, should Codex:

- [X] Choose conservatively based on the current design
- [ ] Leave it unchanged
- [ ] Keep it open until I answer

Additional instructions: go through and read all the responses, ive asked you to do several things in there

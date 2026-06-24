# 1.0 Owner Decisions And Implementation Roadmap

Date: 2026-06-23
Source: `1_0_OWNER_INPUT_WORKSHEET.md`
Status: Active 1.0 roadmap

## 1. Release Promise

Eagles Remorse 1.0 is a free, public, top-down 2D space fleet-command game
built around:

- deep strategic fleet command;
- tactical ship combat with detailed internal damage;
- a persistent living faction war;
- fleet construction, mining, logistics, and trade;
- a replayable 8-15 hour campaign that responds to player actions;
- multiple viable fleet compositions and playstyles.

Story polish is secondary to simulation, combat, fleet growth, and campaign
reactivity for this release.

## 2. Binding Owner Decisions

### Must Be Excellent

- Strategic fleet command
- Tactical ship combat
- Living faction war
- Fleet building and logistics
- Replayable campaign simulation

### Must Not Be Cut

- Strategic strikes
- Detailed internal damage
- Intelligent overworld fleet movement
- Persistent, traceable faction fleets

### Deferred Until After 1.0

- Deep politics beyond the minimum reputation-aid interaction described below
- Officer-career simulation
- Crisis simulation
- Advanced civilian simulation
- Campaign legacy systems
- Multiplayer
- New Game Plus
- Challenge mode
- Mod browser
- Custom scenarios
- Visual battlefield editor
- Battle replay
- Full story and voice production

### Minimum Politics For 1.0

The owner used "politics" to mean materially useful faction alignment rather
than a large internal-government simulation. For 1.0, implement:

- sending ore, credits, intelligence, or ships to Green or Yellow;
- visible reputation gains and costs;
- later support, fleet behavior, trade, and final-battle consequences;
- campaign memory of trade and aid;
- no full political-bloc simulation before 1.0.

### Systems Allowed To Remain Shallow

Codex decision:

- Officer careers and personal advancement
- Civilian society simulation
- Internal political blocs
- Long dialogue scenes
- Espionage and deception
- Campaign legacy and challenge systems
- Detailed market speculation, insurance, and investments
- Scenario and mod tooling

These may support the live game through compact state or flavor, but must not be
advertised as complete player systems.

## 3. Owner's Desired War

### Faction Identity

| Faction | Required 1.0 behavior |
| --- | --- |
| Blue | Starts as a vulnerable exploration/trade flotilla with a mothership, a few pickets, and a miner. Grows into a major fleet through player action. |
| Red | Begins very dangerous, fields deep reserves, attacks infrastructure and logistics, hunts the player, and pursues wider-war objectives. |
| Green | Begins strong, independently wins and loses battles, captures territory, supports Blue, and visibly contributes to retaking Earth. |
| Yellow | Smaller but technologically strong, pressured into supporting Red, transactional when independent, and capable of joining Blue/Green when liberated or purchased. |

### Fleet Scale

- Use both large task forces and many small groups.
- Capital ships must appear regularly enough to be recognizable campaign
  targets rather than miracles.
- Titans are rare, campaign-defining assets.
- Large Red formations may contain several titans, with corresponding rewards
  and reputation consequences.
- Green and Yellow must have visible map presence.
- The player should ordinarily see friendly, neutral, hostile, mining, or trade
  traffic while traveling.
- Important contacts remain persistent when they leave sensor range.
- AI-versus-AI battles proceed without the player.
- Give approximately 30 seconds of warning before major nearby battles.
- Allow the player to follow or join allied fleets into battle.

### Finite-War Rule

Every major faction ship must come from a traceable inventory. Factions replace
losses by:

1. finding or controlling ore;
2. deploying mining forces;
3. transporting ore to faction logistics;
4. obtaining required credits or industrial capacity;
5. building at shipyards, motherships, or mobile station ships;
6. deploying the completed hull into a real fleet.

Do not generate emergency ships merely to close a strategic gap. A faction that
fails to defend a route or hub may lose it.

## 4. Codex Decisions For Delegated Questions

### Ore Transport Model

Use physical fleets for meaningful ore movement:

- faction mining task forces travel to known mining areas;
- miners accumulate cargo;
- transport ships or the mining force return cargo to faction-controlled
  logistics;
- cargo can be intercepted, defended, stolen, or lost;
- tiny background transfers may be aggregated only when they are below the
  threshold of a meaningful encounter;
- Red and Yellow receive dedicated mining ships or faction variants.

This preserves visible logistics without requiring every unit of ore to become a
separate simulation object.

### Shipyard Economy

Friendly yards share a faction-level economic pool, but each yard retains:

- local production lanes;
- local damage or blockade state;
- available hull catalog;
- visible queue and completion location.

Use separate production lanes by hull class so escorts do not block capital
construction. Owner target build times:

| Lane | Base build time |
| --- | --- |
| Escort | 5 seconds |
| Frigate/destroyer | 10 seconds |
| Cruiser | 15 seconds |
| Capital | 20 seconds |
| Titan/special | 25 seconds |

Purchases enter a queue and use the producing faction's hull identity.

### Minimum Hardware Target

Initial target for validation, subject to measurement:

- Windows 10 or 11, 64-bit
- Bundled Java 21 runtime
- Four physical CPU cores around 3.0 GHz
- 8 GB system RAM
- 2 GB free storage
- Intel UHD 620-class integrated graphics or better
- 1280x720 minimum display
- 30 FPS hard floor in the largest supported battle
- 60 FPS target in ordinary tactical play

Recommended target:

- Six modern CPU cores
- 16 GB RAM
- GTX 1050 / RX 560-class graphics or better
- 1920x1080 display

These are release targets, not verified minimum specifications. Hardware testing
must validate or revise them.

### Known-Issue Policy

Not acceptable in 1.0:

- reproducible crashes;
- save loss or corruption;
- campaign or mission soft locks;
- inaccessible required controls;
- objectives whose win or failure conditions cannot be found before acting;
- persistent fleets disappearing without a simulation reason;
- incorrect purchases, construction, or resource deductions;
- unsupported minimum-hardware battles falling below 30 FPS;
- severe text overlap or unreadable required information;
- broken strike launch origins or command-kill behavior;
- progression that requires debug tools.

Acceptable when documented and uncommon:

- minor cosmetic clipping;
- occasional non-critical animation oddities;
- small balance imperfections;
- approved placeholder art or limited repeated text;
- harmless AI inefficiency that does not break objectives or fleet identity.

## 5. Mission Coverage Audit

The code contains broad mission families, including escort, interception,
blockade, defense, evacuation, rescue, salvage, recon, ambush, mine clearance,
tow, retreat, rendezvous, boarding, prison transport, diplomatic escort,
smuggling, pursuit, titan hunt, and anomaly investigation.

The problem is not primarily missing catalog entries. It is live frequency,
visibility, stakes, and connection to real fleets.

### Promote Before Adding More Mission Families

1. Titan hunt generated from a real persistent titan task force
2. Capital task-force interception
3. Mining convoy raid
4. Mining convoy escort
5. Join an allied battle already forming or underway
6. Defend a shipyard production queue
7. Intercept ore before it reaches an enemy yard
8. Break or reinforce a territorial offensive
9. Recover a crippled capital ship before an enemy salvage force arrives
10. Yellow liberation choice with later alliance consequences

### Objective-Clarity Requirement

Every tactical entry must show, before normal combat begins:

- primary objective;
- exact success condition;
- exact failure condition;
- protected assets;
- timer or quota, if any;
- optional objectives and rewards;
- a short first action.

The owner's report that mission requirements are unclear "every single time" is
a release blocker even though objective data exists internally.

## 6. Terminology Audit

Do not perform a blind global rename. Clarify labels by context:

| Current term | Preferred player-facing treatment |
| --- | --- |
| Readiness | Use `Combat Condition`, `Crew Readiness`, `Strike Availability`, or `Production Progress` as appropriate. |
| Stores | Name the actual resource: fuel, supplies, ammunition, salvage, or strike charges. |
| Route Tempo | Use `Travel Speed` and show ETA separately. |
| Favor / Leverage | Use `Green Reputation` and `Yellow Reputation` unless a distinct spendable currency is truly intended. |
| Scar | Use `Battle History`, `Persistent Damage`, or `Location Aftermath` depending on meaning. |
| Sweep | Use `Sensor Sweep`, `Recon Sweep`, or `Security Sweep`; never leave it unqualified. |
| Safe Exit | Use `Withdraw To Strategic Map` in campaign tactical play, while retaining the established behavior. |
| Pressure | Name its source: Red patrol activity, blockade strength, pursuit risk, or reinforcement buildup. |
| Fleet Ore / Yard Ore | Keep these terms, with the existing explanatory line. |

Also prioritize visibility for:

- faction reputation;
- allied hull health and condition;
- strike costs and replenishment;
- shipyard queue state;
- mission success and failure conditions.

## 7. Current Release Blockers From Owner Play

### P0 - Correctness And Continuity

- Preserve all existing automated-test confidence while expanding the war.
- Keep alpha and beta saves compatible with 1.0.
- Fix any strike-origin bug that allows NPC command kills before considering
  enemy strategic strikes.
- Confirm missile-launch audio dispatch works.

### P1 - Core 1.0 Experience

- Greatly expand persistent Red, Green, and Yellow fleet populations.
- Make capital ships visible throughout the campaign.
- Add rare titan-bearing task forces.
- Make territory ownership visibly change from AI and player battles.
- Make Green independently attack, defend, capture, and lose territory.
- Make Yellow politically and militarily relevant.
- Implement physical mining and ore-return task forces.
- Tie replacement hulls to ore, credits, queues, and surviving production.
- Add the player starting miner.
- Add class-separated production queues.
- Make mission objectives clear before combat.
- Remove stale lost-contact icons rather than sending the player after invalid
  markers.
- Improve overworld destination hit targets.
- Increase late-campaign challenge and pace.

### P1 - Balance And Consequence

- Preserve current ship time-to-kill.
- Increase opposition scale rather than weakening player fleet growth.
- Target one major loss, failed mission, or forced withdrawal per roughly five
  battles on Standard.
- Make tactical combat, travel, fuel, supplies, ammunition, repairs, and enemy
  expansion more demanding.
- Consume supplies for damage control and transport-assisted repair.
- Do not allow field repair to restore armor or shields through transport aura.
- Make post-battle damage persist long enough to matter.
- Keep credits as the main limiting resource.
- Make Green and Yellow trade frequently useful.
- Keep strikes, but expose real costs and replenishment.
- Replace or redesign the useless `LINE` doctrine.
- Treat retreat as successful only through the 7.5-second Safe Exit withdrawal.

### P2 - Depth And Presentation

- Add tactical environments with real gameplay effects.
- Show more information in high-intelligence task-force inspection.
- Make named captains and bridge officers more distinctive through text and
  limited high-quality callouts.
- Use ambient silence as the default music direction.
- Reduce repetitive warp audio.
- Fix Yellow ship sprites with the triangular notch on the forward-right side.
- Prevent deep-space encounters from using inappropriate planet backgrounds.

## 8. Implementation Order

### Phase 1 - Release Safety And Clarity

- Mission objective briefing before combat
- Strike cost and replenishment clarity
- Lost-contact marker cleanup
- Overworld destination hit-target fix
- Missile-launch audio fix
- Yellow sprite defect audit
- Deep-space background correction
- Save compatibility fixtures

Exit criteria:

- No known crash, corruption, soft lock, broken mandatory control, or unclear
  mission objective.

### Phase 2 - Fleet Population And Capital Presence

- Audit current faction inventories and spawn/build composition
- Increase Green, Red, and Yellow starting inventories
- Add capital-ship composition rules
- Add rare titan task forces
- Make task-force strength visible through contact intelligence
- Ensure tactical conversion preserves every persistent ship
- Validate maximum readable and performant battle scale

Exit criteria:

- Capital ships appear naturally in repeatable campaign runs.
- Green and Yellow are visibly present.
- No major fleet is created without provenance.

### Phase 3 - Mining, Logistics, And Production

- Add Blue starting miner
- Automate player mining at the strategic level
- Add Red and Yellow miners
- Add mining task-force discovery and return behavior
- Add interceptable ore cargo
- Add faction-shared economy with local yard queues
- Add class-separated production lanes
- Add queued player purchases using producer-faction hulls
- Spend supplies on damage control and transport repair

Exit criteria:

- Ore can be traced from mining site to completed hull.
- Destroying logistics materially delays faction construction.

### Phase 4 - Dynamic Territory And Allied War

- Resolve AI-versus-AI attacks and defenses
- Apply battle outcomes to territory
- Announce major battles 30 seconds in advance
- Allow player follow/join behavior
- Make Green independently gain and lose territory
- Make Yellow alignment affect hostility and fleet support
- Add anti-stalemate opportunities driven by player support

Exit criteria:

- A fixed starting seed can produce meaningfully different wars.
- Territory changes without requiring the player to personally trigger every
  battle.

### Phase 5 - Difficulty And Late Campaign

- Increase late-campaign fleet quality and capital concentration
- Preserve current ship time-to-kill
- Tune resource pressure
- Tune Standard toward victory after one learning defeat
- Add Iron Command enemy armor and faster shield-regeneration delay recovery
- Replace or improve `LINE` doctrine
- Make damage persist and recovery require safe hubs
- Ensure surviving Red remnants affect victory resolution

Exit criteria:

- Late campaign is not slower or easier than the mid campaign.
- Standard produces meaningful losses without becoming a death spiral.

### Phase 6 - Reputation And Minimum Politics

- Add aid transfers to Green and Yellow
- Make reputation prominent
- Connect aid to fleet support, trade, territory, and final battle
- Record trade, reputation, kills, ore mined, and faction assistance
- Promote Yellow liberation and alliance missions

Exit criteria:

- At least two materially different faction strategies alter later encounters
  and the ending.

### Phase 7 - Tactical Environment And Presentation

- Add environment rules beyond destructible asteroids
- Apply sensor shadows and quarantine warnings
- Improve high-intelligence task-force inspection
- Reduce warp-audio repetition
- Keep music mostly silent outside major states
- Add limited high-quality callouts only

Exit criteria:

- At least three environments materially change tactical decisions.

### Phase 8 - Release Validation

- Test keyboard-only navigation
- Test remapping
- Test captions and quiet mode
- Test fullscreen/window switching
- Test minimum hardware
- Run external sessions with the owner and several colleagues
- Build and launch Windows packages
- Test itch.io, Steam, GitHub, and private-distribution artifacts as applicable
- Run full new-campaign, save/load, defeat, victory, and long-session scripts

Exit criteria:

- No known reproducible crash or save corruption.
- No supported battle falls below 30 FPS on the verified minimum machine.
- Several external testers complete meaningful sessions.

## 9. Things To Preserve

The worksheet left this section blank, but the rest of the answers consistently
identify these strengths:

1. Current ship time-to-kill and detailed internal damage
2. Fleet building and the growth from weak flotilla to overwhelming force
3. Free strategic movement and the breadth of available playstyles

Changes should strengthen the opposition and campaign consequences without
flattening those strengths.


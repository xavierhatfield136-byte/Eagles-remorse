# Post-Alpha Expansion Idea Board

Status: Design candidates; not yet committed implementation work  
Updated: 2026-06-29

## Creative Boundary

- Do not use AI-generated crew faces, interior crew videos, or AI-generated crew voices.
- Keep people present through authored text, names, decisions, service records, ship identity, insignia, maps, and instruments.
- A future performance or character-art pass should use commissioned work and human performers if production resources support it.

## Expansion Tracks

### Track A: Strategic War Expansion

This is the foundational track and should be completed first. It contains the territory graph, adjacency-bound invasions, Yellow faction split, civil-war behavior, territorial UI, alliances, supply, encirclement, and campaign outcomes.

### Track B: Command Drama Expansion

This track adds flagship operations, boarding and rescue, persistent rival commanders, memorials and history, alternative campaigns, and cooperative command. These systems should react to a strategic war that is already coherent and worth caring about.

## Expansion Pillars

### 1. Flagship Operations

- Add a 2D flagship schematic for power routing, damage control, evacuation, medical capacity, marines, hangars, fires, decompression, and repairs.
- Represent personnel as abstract teams, station indicators, and written reports rather than faces or interior video.
- Design station interfaces so they can later become cooperative command roles.

### 2. Persistent Rival Commanders

- Add named enemy commanders represented by names, ship silhouettes, insignia, doctrine, written transmissions, and service records.
- Let commanders remember encounters, retreat, recover, gain promotions, adapt to player doctrine, negotiate, and develop rivalries.

### 3. Boarding And Rescue Operations

- Command boarding, counter-boarding, survivor recovery, sabotage response, prisoner capture, salvage, and reactor emergencies through schematics and timed decisions.
- Make team assignments, incomplete intelligence, casualties, and time pressure determine outcomes.

### 4. A War That Remembers

- Preserve wreck fields, station destruction and reconstruction, returning survivors, successor ships, memorials, renamed locations, and historical battles.
- Produce campaign histories and new scenarios from meaningful events in completed campaigns.

### 5. Alternative Campaign Perspectives

- Explore Red military, civilian convoy, carrier task-force, scavenger, last-stand, and Yellow civil-war campaigns.
- Reuse the strategic and tactical simulation while changing objectives, resources, alliances, and command pressures.

### 6. Cooperative Command

- Support captain, helm, tactical, engineering, science, and strategic-command roles.
- Keep unoccupied roles automated.
- Treat networking, authority, synchronization, reconnect behavior, and multiplayer UI as major-expansion work.

## Strategic War Expansion: Territorial Fronts And The Yellow Civil War

### Adjacency-Bound Invasions

- A faction may invade or raid only a territory adjacent to territory it currently controls.
- Capturing territory extends that faction's frontier and can expose new adjacent targets.
- A faction may not skip over intervening territories or advance arbitrarily up the map.
- Strategic routes, lanes, or jump connections define adjacency; visual proximity alone does not.
- Fleets may travel through allied territory, but travel access does not grant the ability to capture a non-adjacent enemy territory.
- Deep strikes, reconnaissance, sabotage, and story operations may occur beyond the frontier, but they do not transfer territorial ownership unless a specific scenario explicitly creates a beachhead.
- Losing the connecting territory should isolate an unsupported salient and restrict further invasions from it until supply access is restored.

### Raid, Invasion, And Beachhead Operations

- A **raid** may strike an adjacent hostile territory and damage fleets, stations, supply, production, intelligence, or local control without changing ownership.
- An **invasion** is a committed capture operation requiring an adjacent controlled territory or a valid active beachhead, an invasion-capable fleet, sufficient supply, local control progress, and time without being decisively repelled.
- A **beachhead** is a rare exception created by a specific scenario, strategic ability, or authored event. It must be expensive, vulnerable, clearly marked, and unable to sustain further expansion if its supply requirements are not met.
- Territory ownership may change only through a valid capture operation, an explicit campaign event, or an authorized scripted scenario.

### Territorial Control States

Territory should change gradually rather than flip after one battle:

1. **Secure:** Firmly controlled, supplied, and able to support outward operations.
2. **Pressured:** Enemy activity is degrading local security or logistics.
3. **Contested:** Both sides can conduct sustained operations and the territory is open to intervention.
4. **Occupied:** The former owner has lost effective control, but resistance and integration risks remain.
5. **Integrated:** The new owner has established stable control, supply, and outward operational capacity.

Ownership, military controller, supply state, and control state should be stored separately. An occupied territory should not immediately behave like a secure homeland territory.

### Front Pressure

Each territory should expose a front-pressure assessment derived from:

- nearby friendly and enemy fleet strength;
- supply level and route continuity;
- station damage and defensive readiness;
- local morale, legitimacy, and resistance;
- mining output and other strategic resources;
- shipyard and reinforcement capacity;
- recent raids and battles;
- presence and quality of notable commanders.

Faction AI should use these factors to choose whether to raid, invade, reinforce, withdraw, or consolidate. The calculation should remain inspectable so surprising decisions can be explained to the player and debugged.

### Replace Yellow With Two Yellow Successor Factions

The existing Yellow faction is replaced by two opposing factions locked in a civil war:

- **Dark Yellow / Orange-Yellow faction:** aligned with Red.
- **Bright Yellow faction:** aligned with Green and the player/Blue coalition.

Final faction names remain to be decided. The colors must be distinguishable at ordinary map zoom, in combat, and under supported color-vision accessibility palettes.

### Shared Yellow Ship Identity

- Both Yellow successor factions use the same hull roster, ship models, silhouettes, weapons, and general visual lineage previously used by Yellow.
- Do not create a separate ship catalog merely to distinguish the two sides.
- Distinguish allegiance through faction color accents, insignia, formation doctrine, fleet names, transponder labels, UI markers, and strategic behavior.
- Captured, defecting, or reunified Yellow ships should therefore retain a believable common material culture.

### Civil-War Behavior

- The two Yellow factions are hostile to one another even though they share ships and cultural origins.
- Their territories should begin interwoven or along a contested internal frontier so the civil war produces active adjacent battles.
- Red may support the dark Orange-Yellow faction; Blue/Green may support the Bright-Yellow faction with fleets, supplies, intelligence, or intervention missions.
- Yellow territory can change hands independently of the wider Red-versus-Blue/Green front.
- Civil-war outcomes should influence the wider campaign: reunification, partition, collapse, negotiated settlement, or domination by either Yellow faction.
- Defections, ceasefires, prisoner exchanges, divided families, disputed stations, and competing legitimacy claims can generate missions without requiring crew portraits or voices.

### Strategic Clarity Requirements

- The map must show every territory's owner and all valid invasion edges.
- Selecting a faction or territory should preview legal adjacent raid and invasion targets.
- Invalid attacks should explain which territorial connection is missing.
- Front-line, isolated, supplied, contested, and allied-access states need distinct readable markers.
- The two Yellow factions must never be identified by color alone; pair color with names, insignia, and patterns.
- Territory details should expose owner, current controller, control state, supply state, front pressure, adjacency, and current operations.

### Acceptance Criteria For Future Implementation

- AI factions never select a territorial capture target that is not adjacent to their controlled territory.
- Save/load preserves territory ownership, adjacency, alliances, and the two separate Yellow faction states.
- Both Yellow factions spawn the legacy Yellow ship roster without duplicating hull definitions.
- Dark Orange-Yellow treats Red as allied and Bright Yellow as hostile.
- Bright Yellow treats Blue and Green as allied and Dark Orange-Yellow as hostile.
- Territory capture correctly opens and closes subsequent invasion options.
- Encirclement or loss of a connecting territory prevents illegal onward expansion.
- UI and automated tests distinguish both Yellow factions without relying exclusively on hue.
- The map displays all legal invasion edges for a selected faction.
- The map explains why a territory is not a legal invasion target.
- Territory ownership cannot change without a valid capture operation, explicit event, or authorized scripted scenario.
- Raids damage valid strategic targets without directly changing ownership.
- Invasions require an adjacent controlled territory or a valid supplied beachhead.
- Isolated territories can defend themselves but cannot launch further invasions until supply is restored.
- Both Yellow successor factions reference the same hull definitions rather than copied hull files.
- Both Yellow factions have distinct map icons, insignia, labels, and pattern overlays.
- Color-vision accessibility modes still distinguish both Yellow factions.
- Save/load preserves control states, ongoing civil-war battles, beachheads, commanders, and faction relationships.
- A debug overlay shows territory ID, owner, controller, supply, control state, pressure factors, adjacency list, and valid attack targets.

## Phased Development Order

### Phase 1: Territory Foundation

- Define territory nodes and route or adjacency edges.
- Store owner, controller, supply state, control state, contested state, and valid invasion neighbors.
- Add save/load support and a basic ownership and legal-invasion overlay.

### Phase 2: Yellow Faction Split

- Replace old Yellow with the two successor faction identities.
- Preserve the shared Yellow hull roster.
- Add stable faction IDs, colors, insignia, transponder labels, alliance rules, map icons, and UI patterns.

### Phase 3: AI Invasion Rules

- Restrict faction target selection to legal adjacent targets or rare valid beachheads.
- Implement raids, invasions, supply restriction, isolated salients, front pressure, and explanatory diagnostics.
- Add automated tests proving that AI factions cannot skip territories.

### Phase 4: Yellow Civil War

- Add starting contested Yellow territories and an active internal frontier.
- Generate civil-war battles and coalition support operations.
- Add reunification, partition, collapse, settlement, and domination outcomes.

### Phase 5: War Memory

- Preserve wreck fields, destroyed and rebuilt stations, memorials, survivors, successor ships, and campaign histories.

### Phase 6: Persistent Commanders

- Add named commanders with faction, doctrine, flagship, history, win/loss memory, retreat, recovery, and adaptation behavior.

### Phase 7: Flagship Operations

- Add schematic power, damage-control, medical, hangar, evacuation, decompression, fire, and repair systems.

### Phase 8: Boarding And Rescue

- Add boarding, counter-boarding, survivor recovery, sabotage response, prisoner capture, and reactor emergencies.

### Phase 9: Alternative Campaigns

- Add Red military, Bright Yellow, Dark Orange-Yellow, civilian convoy, carrier task-force, scavenger, and last-stand campaigns selectively rather than simultaneously.

### Phase 10: Cooperative Command

- Add station roles only after their single-player interfaces are independently enjoyable.
- Treat networking, synchronization, authority, and reconnect behavior as major-expansion infrastructure.

## Guiding Identity

> The war moves through territory, factions remember what happened, and every ship belongs to a real political conflict.

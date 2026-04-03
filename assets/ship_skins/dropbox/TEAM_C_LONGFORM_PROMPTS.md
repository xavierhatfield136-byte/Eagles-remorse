# Team C Longform Prompts

This file is a longform Team C prompt pack derived from the existing `green_team_c` asset family already in the repo.

The intent here is not "blue ships but green." These prompts are written to match the existing Team C hull set:
- brighter overall than blue
- pale jade, celadon, sea-glass, silver-green surfaces
- long, low, horizontally stretched silhouettes
- broad shoulder masses and narrow central spines
- calm continuous hull bodies with very few detached sub-forms
- emerald/teal shield glazing and window bands embedded into the hull
- elegant line-of-battle identity, not industrial bunker massing

## Shared Base Prompt

```text
Top-down orthographic 2D starship hull concept for a game sprite, single ship only, hull only, no turrets, no guns, no missile pods, no hardpoints, no exposed weapons, no antennas, no text, no insignia, no crew, no background, transparent background, centered, nose pointing right, clean alpha silhouette, readable at small size, flat painted albedo, crisp panel lines, no shadows outside the hull, no side view, no perspective distortion, strong faction identity through hull shape alone.
```

## Master Team C Theme

```text
Use the visual language of the existing Team C green fleet already established in the project. These ships are not blue allied hulls recolored green, and they are not stark white cathedral ships. They should read as bright pale jade, celadon, sea-glass, and silver-green military hulls with luminous emerald-teal shield glazing embedded into smooth armor. The overall impression should be brighter and greener than the blue faction, with broad luminous midtones and only limited dark recesses.

Build the silhouettes as long, low, horizontally stretched shield warships. Most medium and large hulls should read as wide 2:1 planforms: calm continuous hull bodies, long spearhead or wedge noses, broad shoulder masses, narrow central spines, smooth side arcs, and very few detached sub-forms. Keep one clean contiguous hull, strong bilateral symmetry, and integrated shield-lattice shoulder structures. The family should feel precise, elegant, and line-of-battle oriented, not rugged, blocky, industrial, or bunker-like. Even the smallest strike craft should read as miniature shield warships descended from the same family, not as jets or shuttles.

Surface treatment should feel like pale celadon ceramic-metal, mint-tinted alloy, polished sea-green armor, brushed silver-green plating, and deep emerald or teal window bands embedded within the hull itself. Let the green live in translucent shield glazing, channel cuts, command slits, and luminous lattice ribs rather than painted cobalt blocks or blue navy striping. Avoid weathered gunmetal dominance, cobalt or navy panels, industrial trenching, exposed weapons, aircraft wings, raised cockpits, detached fins, or clutter. Clean true top-down heroic ship render with black-to-transparent background separation, readable silhouette, elegant but unmistakably militarized.
```

## Asset Family Notes

The existing Team C ship family appears to be built from a small number of internal anchor hulls:
- `picket` anchor informs `fighter` and `pd_craft`
- `patrol` anchor informs `frigate` and `ciws_corvette`
- `stealth` anchor informs `drone`
- `frigate` informs `bomber` and `missile_boat`
- the cruiser classes were iterated as a family
- `hauler` and `transport` inform the logistics and support variants

The prompts below lean into those relationships so new generations stay inside the same family.

## Longform Ship Detail Prompts

### 1. `picket_team_c_albedo.png`

```text
Very small Team C picket escort, derived from the existing green picket anchor hull. Make it extremely long and low for its size, much wider than it is tall, with a blade-like horizontal planform, a fine needle-spear prow, a slim center body, tiny but distinct shield-shoulder flares, and a clean tapered stern. It should feel like the smallest true member of a disciplined shield fleet rather than a fighter or shuttle: fast, exact, watchful, elegant, and defensive. Use bright pale jade and celadon armor with sea-glass midtones, polished silver-green plating, narrow emerald shield-window cuts, tiny teal command slits, and only restrained dark recesses. Keep the hull as one continuous shape, calm and symmetrical, with no aircraft wings, no raised cockpit canopy, no exposed weapons, no industrial trenching, and no blue-fleet cobalt paint language.
```

### 2. `patrol_team_c_albedo.png`

```text
Team C patrol warship, based on the existing green patrol family anchor. Make it a low, broad, horizontally stretched escort hull with a refined arrowhead or spear-wedge nose, smooth side arcs, integrated shoulder mass, and a narrow but authoritative dorsal spine running through a clean contiguous body. It should look more substantial and command-capable than the picket, but still fast and poised, like a luminous shield patrol ship built for screening and disciplined forward presence. Use pale jade, celadon, sea-glass, and brushed silver-green surfaces, with embedded emerald/teal shield glazing running through the shoulders and spine channels. The silhouette should feel elegant, symmetrical, and composed, with no detached fins, no bunker blockiness, no weathered gunmetal dominance, no exposed launch racks, and no resemblance to a blue hull merely recolored green.
```

### 3. `stealth_ship_team_c_albedo.png`

```text
Team C stealth hull, based on the existing green stealth anchor. Make it a long, low, sealed stealth wedge with a faceted spearhead nose, a very smooth continuous perimeter, minimal visual breakup, and a restrained, covert version of the Team C family language. It should still feel like a proper shield-fleet warship, not a black-ops aircraft: precise, monastic, and highly engineered, with a low-profile central body, quiet shoulder massing, and deeply integrated systems hidden inside the hull. Use pale jade-ceramic armor, sea-glass silver-green plating, hairline emerald shield slits, narrow teal command-glass cuts, and only a few dark recessed seams. No external ordnance, no aircraft canopy, no fins, no industrial texture, no navy-blue paint blocks. The result should read as a stealthified Team C warship, not a blue stealth ship with green lights.
```

### 4. `fighter_team_c_albedo.png`

```text
Team C fighter, but make it feel like a miniature descendant of the picket hull rather than an aircraft. It should be tiny, long for its size, low in profile, and built as a true micro-warship with a needle-spear prow, subtle shield shoulders, a clean centerline spine, and a very cohesive, contiguous silhouette. The mood should be aristocratic and precise, like a tiny shield duelist or escort dart from the same fleet family as the larger Team C hulls. Use pale celadon armor, polished silver-green plating, tiny emerald shield-window insets, a disciplined teal command slit, and bright luminous midtones rather than dark steel. Avoid jet styling, winglets, canopies, exposed missiles, industrial clutter, or anything that reads like blue fleet strike craft with a green palette swap.
```

### 5. `bomber_team_c_albedo.png`

```text
Team C bomber as a heavier miniature warship derived from the existing frigate family rather than from an aircraft silhouette. Give it a broader and calmer midbody than the fighter, more visible shoulder mass, a thicker central sanctum, and a low, wide horizontal shape that still fits the Team C line-of-battle family. It should feel more burdened and deliberate than the fighter, with internalized strike capacity implied by mass and geometry instead of exposed ordnance. Use pale jade and sea-glass armor, mint-tinted ceramic-metal surfaces, embedded emerald glazing, and restrained teal lattice channels in the shoulders and center spine. Keep it clean, symmetrical, and contiguous. No aircraft bomber wings, no visible bombs, no bunker slabs, no heavy weathered steel, and no blue-hull paint logic.
```

### 6. `pd_craft_team_c_albedo.png`

```text
Team C point-defense craft, visually descended from the picket anchor but broader, more protective, and more shield-guardian oriented. Keep it small, low, and very readable, with a long narrow prow, compact body, slightly broader shoulder mass than the fighter, and strong bilateral symmetry inside one clean contiguous hull. It should feel like an escort ward-ship, built to hold formation and guard more important hulls, not like a generic gun drone or aircraft. Use bright celadon and pale silver-green plating, embedded emerald shield windows, crisp teal lattice slits, and luminous but controlled surface treatment. Avoid rugged utilitarian shapes, exposed weapon clusters, industrial trenching, or any blue allied navy cues such as cobalt armor fields or slate-gray steel dominance.
```

### 7. `drone_team_c_albedo.png`

```text
Team C drone, based on the stealth-family anchor rather than on the picket family. Make it a tiny autonomous shield sentinel: low, sleek, wedge-like, and highly unified, with a sealed perimeter, a sharp spear-tip nose, minimal visual clutter, and a quiet sense of advanced fleet precision. It should feel like a stealth-derived micro-warship built from the same pale green shield-fleet materials as the rest of Team C. Use pale jade shell plating, silver-green ceramic surfaces, emerald glazing cuts, and tiny teal sensor or command slits. Keep it extremely clean, symmetrical, and contiguous, with no aircraft wings, no mechanical legs, no exposed ordnance, no industrial panel clutter, and no blue fleet paint language.
```

### 8. `frigate_team_c_albedo.png`

```text
Team C frigate, derived from the patrol hull family but with more line-warship authority, more shoulder mass, and a more developed center spine. It should be a low, broad, 2:1 style escort warship with a spearhead bow, smooth side arcs, layered shield-lattice shoulders, and a calm, disciplined central body that clearly belongs to a larger battleline fleet. This frigate should feel exact, poised, and capable, not rugged or heavily industrial. Use pale celadon and sea-glass armor, mint-silver plating, bright emerald shield-window bands embedded into the shoulders and side channels, and restrained teal command glazing. The hull should remain one continuous body with very few detached sub-forms. Avoid bunker blockiness, steel-gray naval paint, exposed missile language, and anything that makes it feel like a blue frigate recolored green.
```

### 9. `missile_boat_team_c_albedo.png`

```text
Team C missile boat, clearly descended from the frigate family rather than from a generic ordnance carrier. Keep the same low, wide, shield-fleet silhouette language, but give it a fuller and more burdened midbody, slightly heavier shoulder mass, and subtle internal volume that implies contained strike systems without ever exposing missiles or launch racks. It should feel like a precision strike variant of the Team C frigate lineage: elegant, composed, and dangerous through integration rather than brute force. Use pale jade ceramic-metal surfaces, silver-green armor, embedded emerald glazing, and luminous teal lattice cuts along the shoulders and central mass. Avoid visible ordnance, bunker-ship chunkiness, weathered dark steel, cobalt paint fields, or any impression of a blue missile hull with green highlights.
```

### 10. `ciws_corvette_team_c_albedo.png`

```text
Team C CIWS corvette, descended from the patrol family but with a shorter, broader, more protective body. Make it feel like a defensive shield-escort specialist: compact, low, horizontally stable, and built around calm protective shoulder mass rather than attack-first aggression. The silhouette should still be elegant and symmetrical, with a refined spear-wedge prow, broad flanks, and a disciplined central spine, all held in one clean contiguous hull. Use bright celadon and pale silver-green plating, embedded emerald ward-window bands, and teal command-glass details that are small and disciplined. Do not make it industrial, blocky, or turret-heavy. No exposed CIWS guns, no aircraft cues, no rugged blue navy steel, and no bunker-trench textures.
```

### 11. `light_cruiser_team_c_albedo.png`

```text
Team C light cruiser, the entry point into the faction's true line-of-battle hull family. It should be longer and more composed than the frigate, still low and horizontally stretched, with a strong but slender center spine, broader shoulder masses, and a calm 2:1 style capital-escort profile. It should feel stately but not heavy, like a graceful shield cruiser built to hold formation and contribute disciplined line fire. Use pale jade, celadon, sea-glass, and silver-green armor with embedded emerald shield glazing running through the shoulders and flanks, and restrained teal command slits at the forward spine. Avoid blue-fleet armor blocks, weathered steel dominance, industrial trenching, or exaggerated cathedral height. The ship should stay smooth, continuous, symmetrical, and very clearly part of the existing Team C family.
```

### 12. `medium_cruiser_team_c_albedo.png`

```text
Team C medium cruiser, positioned clearly between the light cruiser and the cruiser in the existing family. Keep the silhouette as one clean contiguous hull with calmer, simpler massing than the cruiser, longer and more deliberate than the light cruiser, but not yet a grand capital. Give it poised line-battle proportions, refined midline massing, broad shield-lattice shoulders, a dignified spearhead prow, and a restrained but authoritative central spine. The overall feeling should be composed, exact, and militarized, with bright celadon and pale silver-green armor, embedded emerald glazing, and disciplined teal lattice cuts rather than painted cobalt panels. Avoid fragmentation into multiple separate components, avoid rugged or bunker-like structures, and avoid anything that reads like a blue cruiser with green windows.
```

### 13. `cruiser_team_c_albedo.png`

```text
Team C cruiser, a stately line-of-battle warship with stronger central spine authority and more composed massing than the medium cruiser. Keep it low, broad, and horizontally balanced, with a long spearhead nose, broad but elegant shoulder masses, smooth side arcs, and a powerful continuous midbody that feels calm rather than aggressive. It should sit squarely in the center of the Team C fleet identity: precise, luminous, symmetrical, line-oriented, and unmistakably shield-focused. Use pale jade and sea-glass armor, silver-green ceramic-metal plating, deep emerald glazing embedded into the shoulder structures, and restrained teal command windows. Make it distinct from the battlecruiser by being calmer and more balanced, and distinct from the battleship by being less broad and less heavy. No cobalt paint blocks, no rugged steel-heavy naval language, no bunker massing.
```

### 14. `battlecruiser_team_c_albedo.png`

```text
Team C battlecruiser, a fast, prestigious, elongated capital-escort warship. Emphasize a long spearhead command profile, stretched forward rhythm, elegant but aggressive line-of-battle motion, and a cleaner, faster silhouette than the broader battleship. Keep it low and continuous, with a strong prow, refined narrow center spine, and disciplined shoulder structures that are still clearly part of the Team C family. The ship should feel like a noble fast line cruiser rather than a brutal raider. Use bright celadon, pale sea-glass, and polished silver-green armor with embedded emerald glazing and restrained teal command-light cuts. Avoid bulky bunker mass, dark steel dominance, cobalt naval striping, exposed weapons, or anything that makes it look like a blue battlecruiser palette swap.
```

### 15. `battleship_team_c_albedo.png`

```text
Team C battleship, broad, disciplined, immensely stable, and heavy with line-warship authority. Make it more compact and solid than the battlecruiser while remaining clearly below the dreadnought and supership. The silhouette should still be low and wide, but the shoulders should feel denser, the midbody more anchored, and the central spine more authoritative without becoming industrial or blocky. This is a calm heavy shield battleship, not a bunker fleet slab. Use pale jade and silver-green armor, broad embedded emerald shield-window galleries across the shoulders, and controlled teal lattice accents that reinforce the hull's sense of ordered mass. Avoid cobalt paint fields, dark gunmetal dominance, exposed batteries, or rough industrial trenching. It should feel like the stable heavy center of the existing Team C battleline.
```

### 16. `dreadnought_team_c_albedo.png`

```text
Team C dreadnought, fortress-like and monumental, with deep shield-architecture massing and heavy sacred-warship presence. Make it feel more battleline-brutalist within the Aegis style than the supership, but still unmistakably part of the same pale green shield fleet. Keep the hull wide, low, continuous, and horizontally massive, with very deep shoulder structures, a commanding central spine, powerful side volumes, and a calm, unbroken silhouette that feels like an immense shielded fortress in motion. Use bright pale jade, sea-glass, celadon, and silver-green armor with large embedded emerald glazing sections and restrained teal command cuts. Avoid industrial bunker slabs, block terraces, exposed guns, cobalt blue navy markings, or dark steel-heavy surfaces. The result should feel monumental, severe, and luminous rather than rough.
```

### 17. `supership_team_c_albedo.png`

```text
Team C supership, the apex flagship of the standard green fleet family. Make it the most majestic, most integrated, and most ceremonially advanced hull in the faction, with an unmistakable flagship silhouette distinct from dreadnought and battleship. Keep it low, broad, continuous, and horizontally dominant, but let the proportions feel more perfectly composed, more refined, and more unified than any other non-Titan Team C hull. The prow should be authoritative and elegant, the central spine calm and elevated, and the shoulders expansive without becoming blocky. Use luminous pale jade and sea-glass surfaces, polished silver-green plating, broad emerald shield galleries embedded into the hull, and restrained teal-glass command zones. Avoid cobalt blocks, industrial trenching, exposed weapon logic, or rough battleline massing. It should feel like the most complete expression of the existing Team C family.
```

### 18. `carrier_team_c_albedo.png`

```text
Team C carrier, clearly a true fleet carrier rather than a line battleship or simple patrol hull. Give it broader central mass, longer internal bay channels implied through the hull framing, powerful shoulder structures, and a majestic command-carrier silhouette distinct from the drone carrier. It should still feel like a Team C warship first: low, wide, continuous, symmetrical, and refined, with integrated side bay language rather than exposed deck clutter. Use pale celadon and silver-green armor with embedded emerald bay-window glazing, smooth sea-glass channels along the shoulders, and restrained teal command or service slits. The carrier should feel luminous, calm, and high-status, not industrial or bulky. Avoid cobalt paint blocks, steel-gray dominance, exposed hangar clutter, or rugged utility forms.
```

### 19. `drone_carrier_team_c_albedo.png`

```text
Team C drone carrier, visibly different from the fleet carrier. Make it slimmer, lighter, more agile, and more support-oriented, with elegant lattice side volumes, compact auxiliary-bay language, and a refined tender or command-drone silhouette rather than a broad capital carrier. Keep it low and horizontally stretched like the rest of the family, but with less mass in the midbody and a more graceful support-ship rhythm. Use pale jade and sea-glass armor, silver-green ceramic-metal surfaces, embedded emerald glazing, and disciplined teal relay-window accents. It should feel like a support specialist born from the Team C logistics family, not like a shrunken battleship or a generic sci-fi carrier. Avoid industrial mass, bunker logic, cobalt panels, exposed launch racks, or blue-fleet paint cues.
```

### 20. `transport_team_c_albedo.png`

```text
Team C transport, protected and military-logistics focused rather than a generic combatant. Build it as a smooth cargo-bearing midbody with orderly utility framing, broad but calm shoulder masses, an escortable convoy-ship character, and a silhouette that stays low, wide, and contiguous like the rest of Team C. It should feel valuable, disciplined, and shielded, not civilian, crude, or industrially burdened. Use pale celadon and silver-green armor, integrated emerald utility-window bands, sea-glass service channels, and restrained teal sanctum-glass near the prow or command spine. This is a guarded military transport from a luminous shield fleet, not a freighter block, not a blue logistics hull recolored green, and not a bunker-barge. Keep everything clean, symmetrical, and embedded into one coherent hull.
```

### 21. `miner_team_c_albedo.png`

```text
Team C miner, an advanced extraction vessel reinterpreted through the same refined shield-warship family language as the combat ships. It should feel practical but still elegant: low, broad, and continuous, with utility-driven massing that never stops feeling like a Team C hull. Give it a sturdy cargo or workship midbody, orderly side volumes, a composed spear-wedge nose, and integrated process or service structures implied through geometry rather than exposed machinery. Use pale jade and sea-glass ceramic-metal plating, silver-green armor, embedded emerald process-window bands, and restrained teal service-glass accents. Avoid crude industrial trenching, hazardous bunker styling, dark steel dominance, exposed mining hardware, or blue fleet paint patterns. The result should feel like a protected high-tech fleet miner, not a civilian work barge.
```

### 22. `hauler_team_c_albedo.png`

```text
Team C hauler, visibly broader and more burden-bearing than the transport, with elegant protected-cargo geometry, denser utility massing, and a logistics-flagship feel rather than a simple freighter. Keep it low, wide, and horizontally powerful, with a calm broad midbody, protected flanks, a disciplined central spine, and enough shoulder mass to remain part of the same shield-warship family. It should feel heavy with responsibility, not crude with industrial bulk. Use pale celadon, sea-glass, and silver-green surfaces with embedded emerald logistics-window bands, restrained teal command glazing, and smooth, integrated armor framing around the cargo-bearing volumes. Avoid bunker slabs, exposed cargo pods, weathered gunmetal, cobalt navy panels, or any suggestion that this is just a blue hauler with green lighting. This should feel like the dignified heavy logistics branch of the existing Team C fleet.
```

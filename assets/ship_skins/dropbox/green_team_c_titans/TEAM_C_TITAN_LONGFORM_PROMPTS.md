# Team C Titan Longform Prompts

This file is the detailed Team C titan prompt pack derived from the approved hull-only references in `approved/ship_hull_cutouts.zip`.

Important reference rule:
- Treat `approved/ship_hull_cutouts.zip` as the canonical Team C titan source.
- Ignore the loose JPG and PNG files beside it in `approved`; those are test images with bad backgrounds.
- These prompts are meant to follow the approved cutouts while still keeping the Titans inside the broader Team C fleet family already present in `green_team_c`.

## Shared Base Prompt

```text
Top-down orthographic 2D starship hull concept for a game sprite, single ship only, hull only, no turrets, no guns, no missile pods, no hardpoints, no exposed weapons, no small craft, no text, no insignia, no crew, no background, transparent background, centered, nose pointing right, clean alpha silhouette, readable at small size, flat painted albedo, crisp panel lines, no side view, no perspective distortion, no detached modules, strong faction identity through hull shape alone.
```

## Master Team C Titan Theme

```text
Use the visual language of the existing Team C green fleet already established in the project, then scale it up into Titans and a Mothership using the approved Team C titan cutouts as the silhouette reference. These ships are not blue allied Titans recolored green, and they are not generic white cathedral ships. They should read as pale celadon, sea-glass, ivory-green, mint-silver, and silver-green shield warships with luminous emerald and teal glazing buried inside the hull itself. The overall impression should be brighter and greener than the blue faction, with broad luminous midtones, pale armor, stained-glass shield fields, and only controlled dark recesses.

Preserve the two real shape dialects visible in the approved set. The first is the low slab-lance shield-cruiser family used by Transport, Carrier Support, Bulwark, and the Mothership: extremely long and low, often around three to four times as long as they are tall, with layered stern terraces, stacked shoulder citadels, long luminous side bands, and elegant rounded spear or capsule noses. The second is the sanctum-wing capital family used by Vanguard, Command and Intel, Boarding and Recovery, Interdiction, Shield Bastion, Fleet Teleporter, Elite Supership Command, and Hyperweapon: taller, usually around two to one, with diamond or sail-like shoulder masses, protected central naves, stained-glass shield panels, ceremonial forward sanctum bodies, and a more vertical sense of authority around the shoulder core. Artillery is the transitional long weapon-body tied to a fat rear shoulder block. Mobile Station is the harbor-fortress variant with giant service bays and an exposed spinal transfer lane.

Keep every hull as one contiguous super-capital body with strong bilateral symmetry. Use long spearheads, rounded capsule bows, broad shoulder masses, protected central spines, integrated shield-lattice geometry, and glazing fields embedded into the hull rather than painted on top. Surface treatment should feel like pale celadon ceramic-metal, polished sea-green armor, brushed silver-green plating, mint-tinted alloy, deep emerald glazing, and luminous teal lattice cuts. Avoid blue navy armor blocks, slate steel dominance, rugged bunker-fleet massing, industrial grime, exposed weapons, detached floating rings, mech legs, aircraft wings, or clutter.
```

## Reference Family Notes

- `transport`, `carrier_support`, `bulwark`, and `mothership` are the ultra-wide slab-lance capitals and should stay around a 3:1 to 4:1 planform.
- `vanguard`, `interdiction`, `command_intel`, `boarding_recovery`, and `fleet_teleporter` are taller sanctum-wing ships around a 1.7:1 to 2:1 planform.
- `shield_bastion` and `hyperweapon` are broad, dense, forward-heavy sanctum capitals with strong shoulder sails.
- `elite_supership_command` is still a sanctum capital, but it is longer, slimmer, and more aristocratic than the others.
- `mobile_station` should feel infrastructural and harbor-like instead of sleek.
- `artillery` should feel simpler and more axial than the other Team C Titans, with the entire ship built around one integrated forward firing body.

## Approved Zip Filename Mapping

- `Futuristic Transport Titan in emerald hues_hull_only.png` maps to `transport_titan_team_c_albedo.png`
- `Futuristic Carrier Support Titan spacecraft_hull_only.png` maps to `carrier_support_titan_team_c_albedo.png`
- `bulwark_titan_teamc_hull_only.png` maps to `bulwark_titan_team_c_albedo.png`
- all other hull-only PNG names in the zip already match their target filenames

## Longform Ship Detail Prompts

### 1. `transport_titan_team_c_albedo.png`

```text
Team C Transport Titan, protected logistics super-capital, use the ultra-wide slab-lance capital family from the approved cutouts. Make it extremely long and low, roughly three and a half times as long as it is tall, with a terraced stern fortress, layered shoulder pods, long luminous side bands, a broad protected cargo nave, and a refined rounded spear-capsule bow. The aft half should feel like a stacked convoy citadel built from calm horizontal deck layers and shielded service masses, while the forward half stretches into a serene, armored logistics body with embedded emerald utility glazing instead of exposed cargo clutter. The silhouette should be smooth, symmetrical, and continuous, not blocky, industrial, or improvised. It should look like the fleet's protected sustainment flagship, able to carry entire campaigns inside a shielded body without ever becoming a freighter or barge. Use pale celadon, sea-glass, mint-silver, and silver-green armor with long teal and emerald window lanes built into the hull. Avoid blue navy striping, exposed containers, visible gantries, detachable cargo pods, or rugged merchant-ship language.
```

### 2. `bulwark_titan_team_c_albedo.png`

```text
Team C Bulwark Titan, frontline guardian super-capital, use the approved ultra-wide shield-wall silhouette with immense layered shoulders and a very long defensive bow. Keep it roughly three and a quarter times as long as tall, with stacked stern bastion decks, four heavy side sponsons or shoulder pods, a dense protective midbody, and a long uninterrupted emerald centerline band running toward a tapered spear-capsule prow. The ship should feel like a moving defensive wall built from calm horizontal masses, not a brute-force bunker: serene, monumental, and highly disciplined. The shoulders should read as shield-emitter citadels and protective line-anchor structures, while the nose should feel like a patient, unstoppable ward advancing through fire. Use pale ivory-green, celadon, and silver-green armor with strong embedded bastion glazing, long teal shield windows, and a controlled amount of darker recess detail around the central sanctum. Do not drift toward blue allied naval paint blocks, rugged gray steel, industrial trenches, or exposed gun architecture.
```

### 3. `carrier_support_titan_team_c_albedo.png`

```text
Team C Carrier Support Titan, super-capital fleet support carrier, stay inside the same ultra-wide slab-lance family as the approved Bulwark and Transport hulls, but make the role read through calmer service geometry rather than through aggression. Keep it very long and low, around three and a quarter times as long as tall, with broad layered shoulders, terraced upper decks, paired luminous recovery corridors, and a protected central service nave running through the midbody. The aft structures should feel like shielded launch-support and maintenance districts built into the hull itself, with no visible open hangars or tiny craft, while the forward body should resolve into a clean, elegant, high-status bow that still feels operational and practical. This should look like a super-capital escort-carrier built to recover, repair, and relaunch subordinate forces under fire without sacrificing the calm Team C line-of-battle identity. Use pale jade, mint-silver, and sea-glass armor with large teal bay windows and emerald service glazing sunk into the shoulders and central body. Avoid exposed carrier clutter, industrial scaffolding, blue-fleet striping, and obvious navy-gray steel slab logic.
```

### 4. `vanguard_titan_team_c_albedo.png`

```text
Team C Vanguard Titan, fast reserve super-capital, use the taller sanctum-wing branch of the approved Team C titan family. Keep it close to two to one in overall proportion, with a broad diamond shoulder mass wrapped around a compact drive citadel, then a long aggressive spearhead forebody projecting forward from that power core. The aft half should feel like a concentrated engine sanctum protected by high shield-sail shoulders, while the front half narrows into a very long, ceremonial, attack-ready lance. This is not a bulky fortress ship; it is the fast-response linebreaker of the Team C fleet, so the silhouette should feel tense, forward-driving, and expensive, but still calm, symmetrical, and shield-centric rather than jagged or predatory in a red-faction way. Use pale celadon and ivory-green armor, deep emerald shield-sail insets, teal spine channels, and a restrained amount of darker understructure around the central nave. Avoid blue navy hull language, industrial blockiness, airplane cues, detached fins, or exposed weapons.
```

### 5. `interdiction_titan_team_c_albedo.png`

```text
Team C Interdiction Titan, pursuit-control super-capital, use the approved sanctum-wing form with broad shield-sail shoulders and a long rounded containment bow. Keep it a little under two to one, with a large protected central sanctum, wide triangular or sail-like shoulder masses, a calm enclosing midbody, and a long forward capture-prow filled with stained-glass emerald glazing. The role should read through geometry that feels enclosing, suppressive, and controlling rather than through visible hooks or weapons: a broad trapping body that narrows into a smooth sealed forward chamber designed to pin, contain, and deny escape. The ship should feel like an elegant ward-ship for pursuit control, not a hunter-killer from a darker faction. Use pale celestial armor, ivory-green plates, and large teal-green shield fields framed by silver-green structural ribs. Let the most vivid color live in the shield sails and the large forward glazing field. Avoid blue recolor logic, dark gunmetal dominance, industrial ribs, external clamps, or brutalist aggression.
```

### 6. `command_intel_titan_team_c_albedo.png`

```text
Team C Command and Intel Titan, sensor-command super-capital, use the approved observatory-sanctum silhouette with tall shield-sail shoulders, a bright protected central brain, and a long quiet forward mission body. Keep it around 1.7 to 1.8 to 1, a little taller and more poised than the other sanctum-wing ships, with large shoulder sails rising around a command nave, clean protected side walls, and a more reserved forward projection than the Vanguard. The ship should feel serene, watchful, and deeply authoritative, like the fleet's long-range coordination and targeting monastery carried into battle. The approved hull suggests very large window or shield fields integrated into the shoulder sails, so emphasize stained-glass emerald and teal panels nested inside pale celadon and ivory-green armor. Let the central command mass feel sacred, protected, and information-dense, with only fine dark recesses and delicate spire-like details around the midbody. Avoid turning it into a generic carrier, a blue command cruiser with green windows, or a cluttered sci-fi cityship.
```

### 7. `boarding_recovery_titan_team_c_albedo.png`

```text
Team C Boarding and Recovery Titan, assault-recovery super-capital, use the approved sanctum-wing body with a dense central boarding collar, strong shoulder sails, and a long forward recovery sanctum capped by a broad glassy foredeck. Keep the ship just under two to one, with the broadest mass concentrated in the sanctum shoulders and the central transfer spine, then let the forward body taper into a pointed, highly protected bow whose upper surfaces contain large emerald recovery glazing. The ship should imply docking, transfer, capture, rescue, and reclamation through layered internal structure, docking-collar mass, and protected service recesses built into the hull, not through exposed clamps or industrial junk. It should feel humane but formidable: a ship that can ram order back into a shattered battlespace, recover crews, and secure crippled vessels without ever losing Team C elegance. Use pale silver-green and celadon armor, deep emerald recovery windows, teal service channels, and controlled shadow beneath the central sanctum. Avoid pirate salvage visuals, exposed gantries, visible boarding tubes, or blue navy hull logic.
```

### 8. `artillery_titan_team_c_albedo.png`

```text
Team C Artillery Titan, long-range bombardment super-capital, use the approved transitional hull with a fat rear shoulder block, a high stern tower mass, a long integrated weapon body, and a narrowing spear-point prow. Keep it close to three to one overall, but make it simpler and more axial than the other Team C Titans, almost like a shield-lance built around one monumental internal weapon spine. The rear third should read as a compact, stable shoulder citadel that anchors the ship and feeds power into the forward body, while the front two-thirds become a disciplined, tapering firing nave with an internal emerald weapon channel visible through armored glazing. This ship should feel deliberate, precise, and inevitability-driven, not wild or over-ornamented. It is a siege instrument, so the form must imply long-range integrated firepower without ever exposing a cannon barrel or external artillery assembly. Use pale celadon and ivory-green armor with deeper emerald firing windows and restrained teal seams. Avoid industrial mega-cannon language, exposed barrel housings, blue warship striping, or bunker-fleet heaviness.
```

### 9. `shield_bastion_titan_team_c_albedo.png`

```text
Team C Shield Bastion Titan, defensive anchor super-capital, use the approved broad protective body with huge triangular shoulder sails fused to a heavy oval forward citadel. Keep it a little over two to one, shorter and thicker than the slab-lance ships, with a weighty bastion bow, a protected central sanctum, and broad shield-emitter masses that make the entire ship feel like a moving ward. The aft half should anchor the shoulder sails and power core, while the forward half should become a full rounded or shield-bulb citadel rather than a long spear. This ship should feel like the fleet's sheltering fortress, able to absorb the first blow and hold a protective envelope around nearby allies. Use pale mint-silver, celadon, and sea-glass armor with strong emerald bastion fields set deep into the sails and forward body, plus just enough darker understructure to make the glazing feel embedded and structural. Avoid gray steel fortress language, ugly bunker blocks, or anything that reads as a missile faction tank with green paint.
```

### 10. `fleet_teleporter_titan_team_c_albedo.png`

```text
Team C Fleet Teleporter Titan, displacement-command super-capital, use the approved phase-nave silhouette with broad diamond shoulders, a protected midbody, a long central transit corridor, and a large tear-drop emerald chamber set into the forward hull. Keep it just under two to one, with a calm but advanced stance: a strong shoulder sanctum holding the phase architecture together, a visible interior transfer lane through the center, and a forward body that looks built to open and stabilize transit pathways for an entire fleet. The ship should feel futuristic, but still unmistakably Team C and still physically contiguous, not like a ring gate or abstract alien machine. Use pale green, silver-green, and celadon armor over a darker protected understructure, with teal transit glazing, emerald phase chambers, and only subtle high-tech detailing. The result should read as a disciplined shield-fleet teleporter, not as a mystical portal object, not as a blue ship with green lights, and not as a detached structure.
```

### 11. `elite_supership_command_titan_team_c_albedo.png`

```text
Team C Elite Supership Command Titan, prestige strike-command super-capital, use the approved long aristocratic sanctum flagship with a tall stained-glass shoulder sail, a darker underkeel support mass, and an elongated forward command blade. Keep it longer and more elegant than the other sanctum-wing ships, around two and a half to one, with a high-status sail-like shoulder sanctum, a refined central command body, and a very long, narrow, expensive-looking spear bow that projects confidence and control. This should be the elite formation leader, so it needs to feel predatory only in a noble and disciplined way: composed, ceremonial, expensive, and surgically authoritative rather than savage. Use pale celadon, sea-glass, and ivory-green armor with deep emerald command glazing and carefully controlled shadowed structure underneath. Emphasize the long polished forward blade, the large shield-glass sail, and the integrated command nave. Avoid rugged gray steel, excessive city-on-a-ship clutter, blue navy striping, or red-faction aggression.
```

### 12. `mobile_station_titan_team_c_albedo.png`

```text
Team C Mobile Station Titan, station-ship hybrid super-capital, use the approved harbor-fortress silhouette with huge glassy service bays occupying the aft half, a long exposed central service spine, broad octagonal dock shoulders, and a distinctive crescent or cradle-like forward bow wrapping around a luminous forebody. Keep it broad and infrastructural, around 2.7 to one, with enough mass to feel like a moving fleet harbor rather than a combat spear. The aft bay structures should read as protected internal dock districts behind very large teal-green glazing fields, while the middle should show a long transfer corridor or service lane running visibly through the ship. The forward bow should curve around that lane like a calm receiving cradle instead of tapering into a pure lance. This ship should feel like a majestic logistics station in motion: repair, resupply, staging, and support all embodied in one shielded super-capital hull. Use pale jade, celadon, mint-silver, and sea-glass materials with deep teal dock windows and luminous embedded service channels. Avoid industrial scaffolding, exposed cranes, blocky civilian station logic, or blue carrier aesthetics.
```

### 13. `hyperweapon_titan_team_c_albedo.png`

```text
Team C Hyperweapon Titan, strategic finisher super-capital, use the approved broad heavy sanctum hull with a huge enclosed emerald weapon chamber or caged firing nave set along the centerline. Keep it a little over two to one, with a powerful rear engine and shoulder mass, a dense central body built around the weapon chamber, and a controlled forward hull that feels like armored containment wrapped around overwhelming internal firepower. The approved reference suggests a large translucent energy body nested inside a shielded frame, so make the weapon read as integrated and protected, not as a visible gun barrel. This should feel like the fleet's most dangerous execution platform: solemn, deliberate, and expensive, with massive charge discipline rather than reckless aggression. Use pale celadon and silver-green armor with deep emerald weapon glazing, teal channel cuts, and carefully limited dark understructure. Avoid exposed cannons, missile tubes, crude siege-gun language, blue navy slab forms, or chaotic industrial detail.
```

### 14. `mothership_team_c_albedo.png`

```text
Team C Mothership, colossal fleet anchor and shield citadel, use the approved ultra-wide slab-lance flagship as the apex of the entire Team C family. Keep it extraordinarily long and low, around four times as long as it is tall, with immense layered stern terraces, multiple stacked shoulder citadels, long uninterrupted emerald window bands, a deep protected central nave, and an impossibly calm spear-capsule bow. It should visibly belong to the same family as the Transport, Carrier Support, and Bulwark, but it must dwarf them in composure, scale, and authority. The aft half should feel like an entire fleet sanctuary built from serene horizontal decks and shielded command terraces, while the forward half stretches into a grand, luminous, almost sacred command lance. It is part command sanctuary, part fleet harbor, part protected grand carrier, and part moving shield citadel. Use the brightest and calmest Team C material mix here: pale ivory-green, celadon, sea-glass, and silver-green armor with powerful emerald window lanes and teal lattice channels embedded into the hull. Avoid clutter, exposed superstructure junk, blue allied hull logic, or anything that makes it read as a recolored human navy dreadnought.
```

## Optional Correction Suffix

```text
no side perspective, no stars, no space background, no docking scene, no turrets, no visible guns, no missile pods, no little escort ships, no detached floating rings, no mech legs, no airplane wings, no exposed cockpit canopy, no blue navy armor blocks, one contiguous hull, top-down sprite sheet asset, transparent background
```

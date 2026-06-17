# Production Owner Worksheet

## 1. Release Scope

- Target: playable alpha
- Features that absolutely must ship:
  - Persistent fleet system where every ship belongs to a real fleet or force.
  - Strategic campaign map with slow free movement.
  - Enemy, allied, neutral, and civilian fleets moving on the campaign map.
  - No unexplained random enemy pop-ins.
  - Basic faction-director behavior.
  - Tactical battles launched from real campaign encounters.
  - Auto-resolve or take-control battle options.
  - Basic economy: fuel, ore, credits, repairs, salvage, and resupply.
  - Basic shipyard/refit system.
  - Basic mission generation from real campaign situations.
  - Basic information warfare: uncertain contacts, scouting, detection, and alerts.
  - Clear HUD buttons for player actions instead of hidden key commands.
  - Save/load for campaign progress.
- Features that may be moved to post-release:
  - Multiplayer/co-op.
  - Full scenario editor.
  - Full mod/content-pack browser.
  - Procedural star-system generation.
  - Cinematic replay camera.
  - Autonomous spectator mode.
  - New-game-plus.
  - Deep political blocs.
  - Fully simulated civilian societies.
  - Full branching campaign chapters.
  - Advanced crew memorial/lineage systems.
  - Full localization.
  - Final art/audio pass.
  - Complex challenge modes.
- Is placeholder art acceptable during implementation? yes
- Is placeholder audio acceptable during implementation? yes

## 2. Gameplay Priorities

1. Mission variety and information warfare.
2. Economy and logistics.
3. Fleet doctrine and command friction.
4. Stations and deep campaign simulation.
5. Presentation and accessibility.
6. Diplomacy and crew narrative.
7. Modding and scenario editor.

## 3. Economy Direction

- Desired difficulty: moderate
- Can resource shortages cause mission failure? yes, but with warning and recovery options
- Can ships be permanently lost? yes
- Preferred campaign length: medium-length alpha campaign, around 8-15 hours
- Mechanics to avoid:
  - Random enemies appearing from nowhere.
  - Hidden controls or required key combinations not shown in the UI.
  - Resource systems that are too punishing too early.
  - Battles that happen with no warning.
  - Enemy fleets that spawn for free forever.
  - Cramped or unreadable menus.
  - Overly abstract "press button and thing happens" campaign actions.
  - Perfect information where the player always knows exactly where every enemy is.

## 4. Narrative Direction

- Tone:
  - Serious military sci-fi with a desperate journey-home feeling.
  - Tense, industrial, naval, strategic, and survival-focused.
  - Moments of quiet crew life and aftermath between major battles.
- Important factions or characters:
  - Green allied/friendly military or defense faction.
  - Red hostile enemy faction.
  - Yellow neutral/corporate/mercenary/civilian middle faction.
  - Player fleet command staff.
  - Captains assigned to important ships.
  - Civilian stations, mining crews, refugees, merchants, and rescue targets.
- Should crew members die permanently? yes
- Dialogue amount:
  - Moderate.
  - Short bridge officer comments, mission reports, distress calls, bulletins, after-action summaries, and crew morale notes.
  - Avoid huge walls of text during combat or movement.
- Are AI-generated placeholder names, text, portraits, and voices acceptable? yes, for placeholders only

## 5. Art And Audio

- Existing assets already considered approved:
  - Industrial naval sci-fi HUD frame style.
  - Dark gunmetal panels with cyan, amber, and red accents.
  - Tactical button/panel concepts for beam mode, missile mode, cloak, target lock, countermeasures, PD mode, and engine mode.
  - Current usable ship skins and tactical effects.
  - Faction-color visual language for green, red, yellow, and blue.
- Assets that need replacement:
  - Low-quality placeholder ship sprites.
  - Unclear tactical icons.
  - Inconsistent HUD buttons.
  - Hard-to-read map UI panels.
  - Temporary portraits.
  - Temporary voice lines.
  - Duplicate or unused assets.
  - Anything that does not match the industrial naval sci-fi style.
- Visual style references:
  - Highfleet campaign-map readability and military interface feeling.
  - Space Battleship Yamato-style naval sci-fi ships and fleet drama.
  - Industrial tactical command UI.
  - Dark gunmetal, matte black, cyan system lights, amber warnings, red hostile alerts.
  - Functional military hardware instead of clean holographic sci-fi.
- Audio style references:
  - Naval command room ambience.
  - Radio distortion.
  - Heavy mechanical UI clicks.
  - Deep alarms and warning tones.
  - Layered engines.
  - Missile launches.
  - Shield impacts.
  - CIWS/point-defense chatter.
  - Calm but urgent bridge officer callouts.
  - Adaptive music for detection, pursuit, and combat.
- Should duplicate assets be organized with an approval report? yes

## 6. UI Preferences

- Preferred screen resolution: 1280x720 minimum, scalable to 1920x1080
- Keyboard-only support required? yes
- Controller support required? no for alpha
- Accessibility priorities:
  - Readable text.
  - Scalable UI.
  - Strong contrast.
  - Colorblind-friendly icons and shapes.
  - Rebindable controls.
  - Keyboard-only navigation.
  - Captions for voice/radio messages.
  - Reduced-noise or quiet mode.
  - Clear warnings before major consequences.
  - No critical information shown only through tiny icons.
- UI direction:
  - Simpler UI first, with optional deeper panels.
  - Main actions should be visible as buttons.
  - Deeper simulation information should be available but not forced onto the main screen.

## 7. Playtesting

- How often can you test builds? frequently, whenever a meaningful build is ready
- Typical playtest duration: 20-45 minutes
- Willing to test scripted scenarios and record pass/fail notes? yes
- Can provide screenshots when something feels wrong? yes

## 8. Technical Constraints

- Is multiplayer required for this release? no
- Is mod distribution required for this release? no
- Supported operating systems: Windows first
- Performance targets:
  - 60 FPS in normal tactical battles.
  - 30+ FPS minimum during large fleet battles.
  - Stable campaign-map performance with many fleets moving.
  - No major memory leaks during long campaign sessions.
  - Runs on a normal mid-range Windows laptop or desktop.
  - RAM target: under 4 GB for alpha.
  - Save/load completes within a few seconds.

## 9. Project Scope

- Keep the current project focused on the 2D Java/Swing game.
- Do not keep prototype clients, model viewers, or 3D asset pipelines in the main repository unless they directly support the 2D release.
- Treat any future alternate client as a separate project after the 2D game is complete.

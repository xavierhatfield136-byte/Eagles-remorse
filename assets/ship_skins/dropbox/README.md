Drop new hull art here before moving it into the live `assets/ship_skins` set.

Folders:
- `red_enemy`: Team B / red kinetic faction
- `green_team_c`: Team C / Aegis Lattice
- `missile_team_d`: Team D / Viper Barrage Syndicate
- `blue_ally_titans`: Blue allied Titan and Mothership prompts
- `red_enemy_titans`: Red kinetic Titan and Mothership prompts
- `green_team_c_titans`: Team C / Aegis Lattice Titan and Mothership prompts
- `missile_team_d_titans`: Team D / Viper Barrage Syndicate Titan and Mothership prompts

Recommended file naming:
- `frigate_enemy_albedo.png`
- `cruiser_enemy_albedo.png`
- `battlecruiser_enemy_albedo.png`
- `battleship_enemy_albedo.png`
- `frigate_team_c_albedo.png`
- `cruiser_team_c_albedo.png`
- `battlecruiser_team_c_albedo.png`
- `battleship_team_c_albedo.png`
- `frigate_team_d_albedo.png`
- `cruiser_team_d_albedo.png`
- `battlecruiser_team_d_albedo.png`
- `battleship_team_d_albedo.png`
- `transport_titan_ally_albedo.png`
- `bulwark_titan_enemy_albedo.png`
- `command_intel_titan_team_c_albedo.png`
- `hyperweapon_titan_team_d_albedo.png`
- `mothership_enemy_albedo.png`

Art constraints:
- Hull only
- Transparent background
- Top-down view
- Nose pointing right
- No turrets, hardpoints, missile pods, or other exposed weapons

Titan prompt workflow:
- Use a faction Titan style-lock file plus that faction's `HULL_PROMPTS.md`
- Consolidated faction Titan style locks live in `TITAN_FACTION_STYLE_LOCKS.md`
- For Team C Titans specifically, treat `green_team_c_titans/approved/ship_hull_cutouts.zip` as the canonical approved reference set and ignore the loose bad-background test images in that same folder
- For the longest Team C titan prompts, use `green_team_c_titans/TEAM_C_TITAN_LONGFORM_PROMPTS.md`

General faction prompt workflow:
- Paste a faction block from `MASTER_FACTION_DESCRIPTIONS.md` before the per-ship detail line
- Keep using the shared base sprite prompt for framing, transparency, and top-down constraints
- For a much more detailed Team C manual prompting pass, use `TEAM_C_LONGFORM_PROMPTS.md`

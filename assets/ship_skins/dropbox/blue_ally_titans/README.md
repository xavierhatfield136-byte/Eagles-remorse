Drop new blue Titan and Mothership hull art here before moving anything into the live `assets/ship_skins` set.

Folders:
- `incoming`: raw generations, alternates, and rough passes
- `approved`: renamed finals that are ready for cleanup or integration

Recommended filenames:
- `transport_titan_ally_albedo.png`
- `bulwark_titan_ally_albedo.png`
- `carrier_support_titan_ally_albedo.png`
- `vanguard_titan_ally_albedo.png`
- `interdiction_titan_ally_albedo.png`
- `command_intel_titan_ally_albedo.png`
- `boarding_recovery_titan_ally_albedo.png`
- `artillery_titan_ally_albedo.png`
- `shield_bastion_titan_ally_albedo.png`
- `fleet_teleporter_titan_ally_albedo.png`
- `elite_supership_command_titan_ally_albedo.png`
- `mobile_station_titan_ally_albedo.png`
- `hyperweapon_titan_ally_albedo.png`
- `mothership_ally_albedo.png`

Recommended generation constraints:
- hull only
- transparent background
- top-down orthographic view
- nose pointing right
- single ship only
- no turrets, guns, missile pods, hangar craft, text, logos, or background scene
- one contiguous readable capital-ship silhouette, not a plane, shuttle, or mech

Suggested export workflow:
- generate at `1024x1024` or `1536x1536`
- keep the cleanest raw files in `incoming`
- rename the chosen finals to the exact target filenames above
- move renamed finals into `approved`

Notes:
- These are future-facing filenames. The current game code does not load Titan or Mothership skins yet.
- Use `BLUE_TITAN_STYLE_LOCK.txt` plus `HULL_PROMPTS.md` together when prompting Grok or ChatGPT.

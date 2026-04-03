Drop new green Titan and Mothership hull art here before moving anything into the live `assets/ship_skins` set.

Folders:
- `incoming`: raw generations, alternates, and rough passes
- `approved`: renamed finals that are ready for cleanup or integration

Approved Team C titan reference rule:
- Treat `approved/ship_hull_cutouts.zip` as the canonical approved reference set.
- Ignore the loose JPG and PNG files currently sitting in `approved`; those are test images with bad backgrounds.
- The hull-only PNGs extracted from that zip define the Team C titan silhouette family.

Recommended filenames:
- `transport_titan_team_c_albedo.png`
- `bulwark_titan_team_c_albedo.png`
- `carrier_support_titan_team_c_albedo.png`
- `vanguard_titan_team_c_albedo.png`
- `interdiction_titan_team_c_albedo.png`
- `command_intel_titan_team_c_albedo.png`
- `boarding_recovery_titan_team_c_albedo.png`
- `artillery_titan_team_c_albedo.png`
- `shield_bastion_titan_team_c_albedo.png`
- `fleet_teleporter_titan_team_c_albedo.png`
- `elite_supership_command_titan_team_c_albedo.png`
- `mobile_station_titan_team_c_albedo.png`
- `hyperweapon_titan_team_c_albedo.png`
- `mothership_team_c_albedo.png`

Approved zip filename mapping:
- `Futuristic Transport Titan in emerald hues_hull_only.png` -> `transport_titan_team_c_albedo.png`
- `Futuristic Carrier Support Titan spacecraft_hull_only.png` -> `carrier_support_titan_team_c_albedo.png`
- `bulwark_titan_teamc_hull_only.png` -> `bulwark_titan_team_c_albedo.png`
- the remaining hull-only PNGs in the zip map directly to the target filenames above

Recommended generation constraints:
- hull only
- transparent background
- top-down orthographic view
- nose pointing right
- single ship only
- no turrets, guns, missile pods, hangar craft, text, logos, or background scene
- one contiguous readable super-capital silhouette, not a plane, shuttle, or mech

Suggested export workflow:
- generate at `1024x1024` or `1536x1536`
- keep the cleanest raw files in `incoming`
- rename the chosen finals to the exact target filenames above
- move renamed finals into `approved`

Notes:
- These are future-facing filenames. The current game code does not load Titan or Mothership skins yet.
- Use `GREEN_TITAN_STYLE_LOCK.txt` plus `HULL_PROMPTS.md` together in ChatGPT.
- For the longest and most reference-grounded prompts, use `TEAM_C_TITAN_LONGFORM_PROMPTS.md`.

# Campaign Backgrounds

This folder is for authored campaign-sector background images.

Rules:
- Use only celestial bodies and natural planetary detail in these images.
- Do not bake stations, rings, relays, docks, or other man-made structures into the background.
- Camera logic should be orbital top-down or shallow oblique, like looking down at Earth from low orbit.
- The renderer will use these files directly and will not mix in the generic campaign backdrop layers when a matching file exists.

Filename rules:
- Use lowercase names with underscores.
- Preferred format: `.png`
- `.jpg` and `.jpeg` also work.

Renderer behavior:
- If `<key>.png` exists, that image is used for the sector.
- If the mission advances to a later authored phase and `<key>_phase1.png` exists, that file is used instead.
- If no campaign-specific image exists, the game falls back to the generic environment backgrounds and procedural celestial overlay.
- You can keep your original descriptive source filenames here. The game uses the canonical filenames listed below.

## Shortlist

Numbered sequence rule:

If you generated the images in the same order as the prompt queue, treat them as a "closer to Earth" progression.

Recommended numbered mapping:

1. `trade_hub_colony`
Far Trade Anchorage. Outer neutral trade colony world, prosperous and inhabited.

2. `jump_ring_frontier`
Outer Colony Jump Ring. Colder, sparser colony-world orbit.

3. `relay_halo_moon`
Gate Relay Tethys. Dead moon / barren rocky orbital view.

4. `burning_debris_wake`
Broken-world or shattered-body deep-space backdrop.

5. `exodus_gas_giant`
Civilian Exodus Corridor. Gas giant / moon-system orbital view.

6. `trade_spine_industrial_orbit`
Neutral Trade Spine. Harsher inhabited world with a more industrial tone.

7. `contract_world_array`
Coalition Array Nysa. Cleaner blue-green or turquoise civic world.

8. `ash_gate_gas_giant`
Ash Gate Kharon. Scorched-body or dangerous harsh-world celestial view.

9. `outer_sol_starline`
Outer Sol Defense Ring. Restrained deep-space / home-system edge backdrop.

10. `liberation_moon_orbit`
Liberation Corridor. Dim yellow-gray moon or tired colony world.

11. `luna_earthrise_approach`
Luna Perimeter. Clean cratered Luna orbital shot.

12. `earth_high_orbit`
Main Earth liberation battle. Darker Earth-like orbital image.

13. `earth_high_orbit_phase1`
Optional late-phase Earth variant. Use the scarred, bombarded, or higher-pressure Earth image here.

Recommended mapping from the current image set:

1. `trade_hub_colony`
Use a blue-green inhabited ocean world with coastlines, cloud bands, and dense night-side city lights.

2. `jump_ring_frontier`
Use a cooler and sparser colony-world orbital shot than the trade hub, still inhabited but less dense.

3. `relay_halo_moon`
Use a stark cratered moon or dead rocky world from low orbit.

4. `burning_debris_wake`
Use the broken-world nebula / shattered-body deep-space image.

5. `exodus_gas_giant`
Use a gas giant / moon system orbital image with open readable space and no structures.

6. `trade_spine_industrial_orbit`
Use the warm brown-gray inhabited world with city lights and a more stressed industrial tone.

7. `contract_world_array`
Use the cleaner turquoise island-chain or blue-green civic world.

8. `ash_gate_gas_giant`
Use a harsh orange or scorched-body celestial image with a militarized, dangerous mood.

9. `outer_sol_starline`
Use a restrained deep-space image with one dominant planet or moon and lots of open readable space.

10. `liberation_moon_orbit`
Use a dim yellow-gray moon or tired colony world with sparse city lights and a somber tone.

11. `luna_earthrise_approach`
Use the clean cratered Luna low-orbit shot.

12. `earth_high_orbit`
Use the darker Earth-like orbital shot for the main liberation battle.

Optional phase variants:
- `contract_world_array_phase1`
- `luna_earthrise_approach_phase1`
- `earth_high_orbit_phase1`

Suggested use for optional phase variants:
- `contract_world_array_phase1`: a brighter coalition-world orbital shot after the array is secured.
- `luna_earthrise_approach_phase1`: a Luna shot with Earth becoming more visually present once the cordon is broken.
- `earth_high_orbit_phase1`: the bombardment / scarred Earth variant for late liberation pressure.

## Expected Filenames

- `trade_hub_colony.png`
- `jump_ring_frontier.png`
- `relay_halo_moon.png`
- `burning_debris_wake.png`
- `exodus_gas_giant.png`
- `trade_spine_industrial_orbit.png`
- `contract_world_array.png`
- `ash_gate_gas_giant.png`
- `outer_sol_starline.png`
- `liberation_moon_orbit.png`
- `luna_earthrise_approach.png`
- `earth_high_orbit.png`

Optional:
- `contract_world_array_phase1.png`
- `luna_earthrise_approach_phase1.png`
- `earth_high_orbit_phase1.png`

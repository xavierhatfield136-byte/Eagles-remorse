# Ship Viewer (Three.js)

Quick local 3D test scene for both ships:
- `public/models/ship.glb` (blue)
- `public/models/ship-red.glb` (red)

## Run

```powershell
cd tools\ship-viewer
python -m http.server 5173
```

Then open [http://localhost:5173](http://localhost:5173).

## Controls

- Orbit mode: LMB orbit, RMB pan, wheel zoom
- `X`: toggle flight mode
- Flight mode: `W/S` thrust, `A/D` yaw, `R/F` vertical strafe
- `Shift`: boost
- `Space`: fire from hardpoints
- `1` / `2`: switch active ship (blue/red)
- `F`: refocus camera (orbit mode)

## Scene Features

- Thruster glow and particle exhaust
- Hardpoint markers and projectile firing
- Starfield backdrop
- Spherical asteroids as cover

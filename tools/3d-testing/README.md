# 3D Testing

Dedicated local Three.js/GLTF sandbox for ship experiments.

## Quick Start

Primary playable sandbox:

```text
start-3d-testing.bat
```

Optional model folder override:

```text
start-3d-testing.bat -ModelDir "C:\path\to\models"
```

The default model folder is:

```text
C:\Users\xhatf\OneDrive\Desktop\3d models dropoff
```

This launcher creates a local model manifest and serves the dropoff folder under `public/models/dropoff`, so the browser can load the GLBs through Three.js `GLTFLoader`.

## Mothership Sandbox

- You pilot the blue mothership.
- Blue ships from the model dropoff form up around you as escorts.
- Enemy raider waves spawn periodically ahead of the fleet.
- The default camera follows from behind and above the mothership.
- GLB scene hierarchy, transforms, materials, textures, UVs, and normals are preserved by Three.js.

## Controls

- `W` / `S`: thrust forward / reverse
- `A` / `D`: turn the player ship
- `Shift`: boost
- `SPACE` or left mouse: fire weapons
- `TAB`: switch to the next blue ship
- `F`: toggle follow camera / orbit controls
- `C`: toggle cinematic camera
- `P`: pause/resume
- `N`: reset sandbox

## Secondary Prototype

```text
start-full-3d-testing-ground.bat
```

The LWJGL/OpenGL path is experimental. Use the Three.js sandbox above for real GLB model testing.

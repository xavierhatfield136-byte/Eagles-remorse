# Projectile Skins

Optional sprite overrides for projectile rendering.

## Supported filenames
- `missile.png` (already supported)
- `energy_bolt.png`
- `beam_bolt.png`
- `beam_bolt_single.png`
- `wave_shot.png`
- `bullet.png`
- `ciws_pellet.png`

If a file is missing, the game uses procedural visuals automatically.

## Authoring specs
- Transparent PNG only.
- Projectile should face right (`+X`) in source art.
- Keep the sprite centered in canvas.
- Recommended source sizes:
  - `missile`, `wave_shot`: `256x128`
  - `energy_bolt`, `beam_bolt`, `beam_bolt_single`: `192x96`
  - `bullet`, `ciws_pellet`: `96x96`

## ChatGPT prompt templates
Use these directly in ChatGPT image generation and adjust color words per faction if needed.

### `missile.png`
"Top-down stylized sci-fi missile sprite, clean silhouette, metallic body, emissive engine nozzle, transparent background, game asset, no text, no watermark"

### `energy_bolt.png`
"2D sci-fi plasma bolt sprite, short elongated projectile with bright core and soft glow, cyan-white energy, transparent background, no text, no watermark"

### `beam_bolt.png`
"2D heavy energy slug sprite, thicker than normal plasma bolt, intense white-blue center with electric halo, transparent background, game-ready"

### `beam_bolt_single.png`
"2D narrow single-lane beam bolt sprite, white-blue lance head with slim cyan halo, designed as one beam barrel rather than a merged triple beam, transparent background, game-ready"

### `wave_shot.png`
"2D superweapon beam projectile sprite, long narrow energy lance with layered glow bands and bright centerline, transparent background, no text"

### `bullet.png`
"2D autocannon tracer projectile sprite, small bright core with subtle tapered trail, transparent background, minimal stylized game asset"

### `ciws_pellet.png`
"2D tiny point-defense pellet sprite, bright metallic-white micro projectile with short glow tail, transparent background, game icon style"

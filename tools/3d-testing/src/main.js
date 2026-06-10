import * as THREE from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";

const app = document.getElementById("app");

const hud = document.querySelector(".hud");
if (hud) hud.innerHTML = "Loading dropoff models...";

const statusEl = document.createElement("div");
statusEl.style.position = "fixed";
statusEl.style.right = "12px";
statusEl.style.top = "12px";
statusEl.style.padding = "8px 10px";
statusEl.style.background = "rgba(3, 8, 18, 0.72)";
statusEl.style.border = "1px solid rgba(160, 200, 255, 0.3)";
statusEl.style.borderRadius = "8px";
statusEl.style.color = "#dbe6ff";
statusEl.style.font = "12px Segoe UI, Tahoma, sans-serif";
statusEl.style.whiteSpace = "pre-line";
statusEl.textContent = "Loading...";
document.body.appendChild(statusEl);

const renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: "high-performance" });
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.shadowMap.enabled = false;
app.appendChild(renderer.domElement);

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x02050b);
scene.fog = new THREE.Fog(0x030711, 180, 1800);

const camera = new THREE.PerspectiveCamera(62, window.innerWidth / window.innerHeight, 0.1, 5000);
camera.position.set(-58, 34, 0);

const controls = new OrbitControls(camera, renderer.domElement);
controls.enableDamping = true;
controls.enabled = false;
controls.target.set(0, 2, 0);

scene.add(new THREE.AmbientLight(0xa8c9ff, 0.72));
const keyLight = new THREE.DirectionalLight(0xffffff, 1.25);
keyLight.position.set(36, 48, 20);
scene.add(keyLight);
const rimLight = new THREE.DirectionalLight(0x4f9bff, 0.9);
rimLight.position.set(-42, 18, -34);
scene.add(rimLight);
scene.add(new THREE.HemisphereLight(0x6d8fbd, 0x05070d, 0.58));

const grid = new THREE.GridHelper(800, 120, 0x4a658f, 0x162339);
grid.position.y = -0.05;
scene.add(grid);

const arena = new THREE.Mesh(
  new THREE.RingGeometry(195, 198, 192),
  new THREE.MeshBasicMaterial({ color: 0xd6af38, transparent: true, opacity: 0.32, side: THREE.DoubleSide })
);
arena.rotation.x = -Math.PI / 2;
scene.add(arena);

const stars = createStars();
scene.add(stars);

const loader = new GLTFLoader();
const modelEntries = [];
const modelCache = new Map();
const ships = [];
const projectiles = [];
const effects = [];
const props = [];

const SHIP_FORWARD = new THREE.Vector3(1, 0, 0);
const WORLD_UP = new THREE.Vector3(0, 1, 0);
const clock = new THREE.Clock();
const arenaRadius = 185;
const fleetAlertRange = 420;

let playerShip = null;
let activeBlueIndex = 0;
let followCamera = true;
let paused = false;
let cinematic = false;
let waveTimer = 3.0;
let waveNumber = 0;
let hudNote = "Loading models...";
let hudNoteT = 0;
let sandboxBooting = false;

const keyState = {
  forward: false,
  back: false,
  left: false,
  right: false,
  boost: false,
  firing: false
};

init();

async function init() {
  try {
    await loadManifest();
    createEnvironmentProps();
    await spawnMothershipSandbox();
    hudNote = "Mothership sandbox ready";
    hudNoteT = 3;
  } catch (err) {
    console.error(err);
    sandboxBooting = false;
    hudNote = "Failed to initialize sandbox. Check console.";
    hudNoteT = 10;
  }
}

async function loadManifest() {
  let manifest = [];
  try {
    const res = await fetch("./public/models/dropoff-manifest.json", { cache: "no-store" });
    if (res.ok) manifest = await res.json();
  } catch (err) {
    console.warn("Dropoff manifest unavailable, using legacy models.", err);
  }

  if (!Array.isArray(manifest) || manifest.length === 0) {
    manifest = [
      { name: "ship.glb", url: "./public/models/ship.glb" },
      { name: "ship-red.glb", url: "./public/models/ship-red.glb" }
    ];
  }

  modelEntries.length = 0;
  for (const entry of manifest) {
    if (!entry || !entry.name || !entry.url) continue;
    modelEntries.push({
      name: entry.name,
      url: entry.url,
      key: normalizeName(entry.name),
      bytes: entry.bytes || 0
    });
  }
}

async function spawnMothershipSandbox() {
  sandboxBooting = true;
  clearSandbox();
  waveTimer = 8.0;
  waveNumber = 0;

  playerShip = await addShip({
    name: "Blue Mothership",
    teamId: 0,
    model: await loadModel(["blue", "mothership"]),
    position: new THREE.Vector3(0, 0, 0),
    targetSize: 34,
    hp: 3600,
    speed: 18,
    turnRate: 0.62,
    weaponRange: 130,
    projectileSpeed: 155,
    damage: 22,
    fireRate: 0.095,
    playerControlled: true
  });
  playerShip.pivot.rotation.y = 0;

  const escortSpecs = [
    ["Blue Battlecruiser", ["blue", "battlecruiser"], -34, -18, 16, 780, 28, 0.95, 95],
    ["Blue Battleship", ["blue", "battleship"], -46, 22, 18, 980, 24, 0.82, 105],
    ["Blue Dreadnought", ["blue", "dreadnaught"], -62, 0, 18, 1100, 22, 0.75, 110],
    ["Blue Supership", ["blue", "supership"], -82, 34, 17, 980, 22, 0.78, 105],
    ["Blue Carrier", ["blue", "carrier"], -76, -34, 16, 740, 24, 0.78, 105],
    ["Blue Drone Carrier", ["blue", "drone", "carrier"], -104, -12, 15, 680, 26, 0.9, 95],
    ["Blue Transport Titan", ["blue", "transport", "titan"], -120, 32, 22, 1450, 18, 0.55, 110],
    ["Blue Carrier Titan", ["blue", "carrier", "titan"], -126, -42, 22, 1500, 18, 0.55, 110],
    ["Blue Command Titan", ["blue", "command", "intel"], -152, 0, 24, 1750, 16, 0.45, 125],
    ["Blue Bulwark Titan", ["bulwark"], -164, 46, 24, 1900, 15, 0.42, 120],
    ["Blue Cruiser", ["blue", "cruiser"], -22, 42, 12, 440, 34, 1.2, 78],
    ["Blue Missile Boat", ["blue", "missile", "boat"], -16, -44, 9, 260, 42, 1.45, 105],
    ["Blue Frigate", ["blue", "frigate"], -44, -52, 9, 290, 44, 1.5, 70],
    ["Blue CIWS Frigate", ["blue", "ciws", "frigate"], -48, 54, 9, 310, 42, 1.5, 70],
    ["Blue CIWS Corvette", ["blue", "ciws", "corvette"], -70, -58, 7, 210, 48, 1.7, 62],
    ["Blue Picket", ["blue", "picket"], 16, -56, 6, 140, 62, 2.0, 58],
    ["Blue Patrol", ["blue", "patrol"], 18, 54, 6, 140, 62, 2.0, 58],
    ["Blue Stealth", ["blue", "stealth"], 36, -46, 7, 160, 64, 2.05, 70],
    ["Blue Transport", ["blue", "transport"], 42, 44, 8, 230, 36, 1.2, 58],
    ["Blue Hauler", ["blue", "hauler"], 62, 24, 7, 190, 34, 1.1, 55],
    ["Blue Miner", ["blue", "miner"], 62, -24, 7, 170, 34, 1.1, 52]
  ];

  for (const spec of escortSpecs) {
    const [name, terms, forward, side, size, hp, speed, turnRate, range] = spec;
    const model = await loadModelAny([terms, terms.includes("frigate") ? ["frigate"] : terms]);
    const ship = await addShip({
      name,
      teamId: 0,
      model,
      position: formationPoint(playerShip, forward, side),
      targetSize: size,
      hp,
      speed,
      turnRate,
      weaponRange: range,
      projectileSpeed: 140,
      damage: Math.max(8, size * 1.1),
      fireRate: Math.max(0.11, 0.36 - size * 0.006),
      formationForward: forward,
      formationSide: side
    });
    ship.pivot.rotation.y = playerShip.pivot.rotation.y;
  }

  for (let i = 0; i < 5; i++) {
    await addEscortCraft(`Blue Fighter ${i + 1}`, ["blue", "fighter"], 28 + i * 5, -30 + i * 15, 4.8);
  }
  for (let i = 0; i < 3; i++) {
    await addEscortCraft(`Blue Bomber ${i + 1}`, ["blue", "bomber"], 56 + i * 7, -48 + i * 48, 5.6);
  }
  for (let i = 0; i < 4; i++) {
    await addEscortCraft(`Blue Drone ${i + 1}`, ["blue", "drone"], 82 + i * 5, -34 + i * 22, 4.2);
  }

  activeBlueIndex = ships.indexOf(playerShip);
  setPlayerShip(playerShip);
  await spawnInitialOpposingFleet();
  sandboxBooting = false;
}

async function addEscortCraft(name, terms, forward, side, size) {
  const ship = await addShip({
    name,
    teamId: 0,
    model: await loadModel(terms),
    position: formationPoint(playerShip, forward, side),
    targetSize: size,
    hp: 110,
    speed: 72,
    turnRate: 2.4,
    weaponRange: 52,
    projectileSpeed: 150,
    damage: 8,
    fireRate: 0.16,
    formationForward: forward,
    formationSide: side
  });
  ship.pivot.rotation.y = playerShip.pivot.rotation.y;
}

function clearSandbox() {
  for (const ship of ships) scene.remove(ship.pivot);
  for (const p of projectiles) scene.remove(p.mesh);
  for (const e of effects) scene.remove(e.mesh);
  ships.length = 0;
  projectiles.length = 0;
  effects.length = 0;
  playerShip = null;
}

async function addShip(config) {
  const pivot = new THREE.Group();
  pivot.name = config.name;
  const root = config.model ? config.model.clone(true) : createFallbackModel(config.teamId);
  pivot.add(root);
  scene.add(pivot);

  normalizeModel(root, config.targetSize || 8);
  orientShipModel(root, pivot);
  pivot.position.copy(config.position || new THREE.Vector3());
  pivot.rotation.y = config.heading || 0;

  const bounds = shipBoundsInPivotSpace(root, pivot);
  const hardpoints = createHardpoints(pivot, bounds, config.teamId);
  const thrusters = createThrusters(pivot, bounds);
  const radius = Math.max(1.0, Math.max(bounds.size.x, bounds.size.y, bounds.size.z) * 0.16);
  const configuredHp = config.hp || 200;
  const maxHp = config.teamId === 0 && !config.playerControlled
    ? Math.max(configuredHp * 2.35, config.targetSize * 46)
    : configuredHp;

  const ship = {
    name: config.name,
    teamId: config.teamId || 0,
    root,
    pivot,
    bounds,
    radius,
    maxHp,
    hp: maxHp,
    alive: true,
    speed: config.speed || 30,
    turnRate: config.turnRate || 1.2,
    weaponRange: config.weaponRange || 70,
    projectileSpeed: config.projectileSpeed || 135,
    damage: config.damage || 12,
    fireRate: config.fireRate || 0.18,
    fireCooldown: Math.random() * 0.2,
    hardpoints,
    thrusters,
    formationForward: config.formationForward || 0,
    formationSide: config.formationSide || 0,
    playerControlled: !!config.playerControlled,
    alert: false,
    throttleVisual: 0,
    velocity: new THREE.Vector3()
  };
  ships.push(ship);
  return ship;
}

async function loadModel(terms) {
  const entry = findModel(terms);
  if (!entry) {
    console.warn("Missing model for", terms);
    return null;
  }
  if (modelCache.has(entry.url)) return modelCache.get(entry.url).clone(true);
  const gltf = await loader.loadAsync(entry.url);
  gltf.scene.name = entry.name;
  prepareMaterials(gltf.scene);
  modelCache.set(entry.url, gltf.scene);
  return gltf.scene.clone(true);
}

async function loadModelAny(choices) {
  for (const terms of choices) {
    const entry = findModel(terms);
    if (entry) return loadModel(terms);
  }
  return null;
}

function findModel(terms) {
  const wanted = (terms || []).map((t) => String(t).toLowerCase());
  let best = null;
  let bestScore = -Infinity;
  for (const entry of modelEntries) {
    if (!wanted.every((term) => entry.key.includes(term))) continue;
    let score = 100;
    if (entry.key.includes("modern")) score += 12;
    if (entry.key.includes("copy")) score -= 4;
    if (entry.key.includes("(1)")) score -= 3;
    score -= entry.key.length / 20;
    if (score > bestScore) {
      bestScore = score;
      best = entry;
    }
  }
  return best;
}

function normalizeName(raw) {
  return String(raw || "")
    .toLowerCase()
    .replace(/\.glb$/i, "")
    .replace(/[+_-]/g, " ")
    .replace(/[^a-z0-9() ]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function prepareMaterials(object3d) {
  object3d.traverse((node) => {
    if (!node.isMesh || !node.material) return;
    node.frustumCulled = true;
    const mats = Array.isArray(node.material) ? node.material : [node.material];
    for (const mat of mats) {
      mat.side = THREE.DoubleSide;
      if (mat.map) mat.map.colorSpace = THREE.SRGBColorSpace;
      mat.needsUpdate = true;
    }
  });
}

function normalizeModel(object3d, targetSize) {
  const box = new THREE.Box3().setFromObject(object3d);
  const size = box.getSize(new THREE.Vector3());
  const center = box.getCenter(new THREE.Vector3());
  const maxSize = Math.max(size.x, size.y, size.z);
  if (!Number.isFinite(maxSize) || maxSize <= 0) return;

  const scale = targetSize / maxSize;
  object3d.scale.setScalar(scale);
  const scaledCenter = center.multiplyScalar(scale);
  const scaledHeight = size.y * scale;
  object3d.position.set(-scaledCenter.x, -scaledCenter.y + scaledHeight * 0.45, -scaledCenter.z);
}

function axisVector(index, sign = 1) {
  if (index === 0) return new THREE.Vector3(sign, 0, 0);
  if (index === 1) return new THREE.Vector3(0, sign, 0);
  return new THREE.Vector3(0, 0, sign);
}

function collectVerticesInPivotSpace(root, shipPivot) {
  const out = [];
  shipPivot.updateMatrixWorld(true);
  root.updateMatrixWorld(true);
  const invPivot = shipPivot.matrixWorld.clone().invert();
  root.traverse((node) => {
    if (!node.isMesh || !node.geometry?.attributes?.position) return;
    const attr = node.geometry.attributes.position;
    const stride = Math.max(1, Math.ceil(attr.count / 7000));
    for (let i = 0; i < attr.count; i += stride) {
      out.push(new THREE.Vector3().fromBufferAttribute(attr, i).applyMatrix4(node.matrixWorld).applyMatrix4(invPivot));
    }
  });
  return out;
}

function orientShipModel(root, shipPivot) {
  const vertices = collectVerticesInPivotSpace(root, shipPivot);
  if (!vertices.length) return;
  const bounds = new THREE.Box3().setFromPoints(vertices);
  const size = bounds.getSize(new THREE.Vector3());
  const lengths = [size.x, size.y, size.z];
  const forwardAxis = lengths.indexOf(Math.max(...lengths));
  const min = forwardAxis === 0 ? bounds.min.x : (forwardAxis === 1 ? bounds.min.y : bounds.min.z);
  const max = forwardAxis === 0 ? bounds.max.x : (forwardAxis === 1 ? bounds.max.y : bounds.max.z);
  const span = Math.max(1e-5, max - min);
  const cut = span * 0.14;
  let minRadiusSum = 0;
  let maxRadiusSum = 0;
  let minCount = 0;
  let maxCount = 0;
  const center = bounds.getCenter(new THREE.Vector3());
  for (const v of vertices) {
    const a = forwardAxis === 0 ? v.x : (forwardAxis === 1 ? v.y : v.z);
    const b = forwardAxis === 0 ? v.y : v.x;
    const c = forwardAxis === 2 ? v.y : v.z;
    const cb = forwardAxis === 0 ? center.y : center.x;
    const cc = forwardAxis === 2 ? center.y : center.z;
    const r = Math.hypot(b - cb, c - cc);
    if (a <= min + cut) {
      minRadiusSum += r;
      minCount++;
    }
    if (a >= max - cut) {
      maxRadiusSum += r;
      maxCount++;
    }
  }
  const forwardSign = (minCount ? minRadiusSum / minCount : Infinity) <= (maxCount ? maxRadiusSum / maxCount : Infinity) ? -1 : 1;
  const qForward = new THREE.Quaternion().setFromUnitVectors(axisVector(forwardAxis, forwardSign).normalize(), SHIP_FORWARD);
  const upAxis = lengths.indexOf(Math.min(...lengths));
  const upAfterForward = axisVector(upAxis, 1).applyQuaternion(qForward).normalize();
  const roll = Math.atan2(upAfterForward.z, upAfterForward.y);
  const qRoll = new THREE.Quaternion().setFromAxisAngle(SHIP_FORWARD, -roll);
  root.quaternion.premultiply(qRoll.multiply(qForward));
  root.updateMatrixWorld(true);
}

function shipBoundsInPivotSpace(root, shipPivot) {
  const points = collectVerticesInPivotSpace(root, shipPivot);
  const box = points.length ? new THREE.Box3().setFromPoints(points) : new THREE.Box3().setFromObject(root);
  return { box, min: box.min.clone(), max: box.max.clone(), size: box.getSize(new THREE.Vector3()) };
}

function pointFromBounds(bounds, nx, ny, nz) {
  return new THREE.Vector3(
    THREE.MathUtils.lerp(bounds.min.x, bounds.max.x, nx),
    THREE.MathUtils.lerp(bounds.min.y, bounds.max.y, ny),
    THREE.MathUtils.lerp(bounds.min.z, bounds.max.z, nz)
  );
}

function createHardpoints(shipPivot, bounds, teamId) {
  const color = teamId === 0 ? 0x66cfff : 0xff654b;
  const anchors = [
    [0.84, 0.54, 0.63],
    [0.84, 0.54, 0.37],
    [0.68, 0.50, 0.72],
    [0.68, 0.50, 0.28]
  ];
  const hardpoints = [];
  for (const a of anchors) {
    const group = new THREE.Group();
    group.position.copy(pointFromBounds(bounds, a[0], a[1], a[2]));
    const muzzle = new THREE.Object3D();
    muzzle.position.x = Math.max(0.45, bounds.size.length() * 0.018);
    const flash = new THREE.Mesh(
      new THREE.SphereGeometry(Math.max(0.08, bounds.size.length() * 0.006), 12, 12),
      new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0 })
    );
    flash.position.copy(muzzle.position);
    group.add(muzzle, flash);
    shipPivot.add(group);
    hardpoints.push({ group, muzzle, flash });
  }
  return hardpoints;
}

function createThrusters(shipPivot, bounds) {
  const offsets = [
    pointFromBounds(bounds, 0.08, 0.47, 0.64),
    pointFromBounds(bounds, 0.08, 0.47, 0.36),
    pointFromBounds(bounds, 0.14, 0.42, 0.50)
  ];
  const thrusters = [];
  for (const offset of offsets) {
    const group = new THREE.Group();
    group.position.copy(offset);
    const plume = new THREE.Mesh(
      new THREE.ConeGeometry(0.12, 0.9, 12, 1, true),
      new THREE.MeshBasicMaterial({ color: 0x2fbaff, transparent: true, opacity: 0.38, side: THREE.DoubleSide })
    );
    plume.rotation.z = Math.PI / 2;
    plume.position.x = -0.5;
    group.add(plume);
    shipPivot.add(group);
    thrusters.push({ group, plume });
  }
  return thrusters;
}

function createFallbackModel(teamId) {
  const group = new THREE.Group();
  const geo = new THREE.ConeGeometry(1, 3, 5);
  const mat = new THREE.MeshStandardMaterial({ color: teamId === 0 ? 0x298cff : 0xff473d, roughness: 0.6, metalness: 0.3 });
  const mesh = new THREE.Mesh(geo, mat);
  mesh.rotation.z = -Math.PI / 2;
  group.add(mesh);
  return group;
}

function formationPoint(anchor, forward, side) {
  const q = anchor?.pivot?.quaternion || new THREE.Quaternion();
  const f = SHIP_FORWARD.clone().applyQuaternion(q);
  const s = new THREE.Vector3(0, 0, 1).applyQuaternion(q);
  return anchor.pivot.position.clone().addScaledVector(f, forward).addScaledVector(s, side);
}

function updatePlayer(dt) {
  if (!playerShip?.alive) return;
  playerShip.alert = true;
  const turn = (keyState.left ? 1 : 0) - (keyState.right ? 1 : 0);
  playerShip.pivot.rotation.y += turn * playerShip.turnRate * dt;
  const throttle = (keyState.forward ? 1 : 0) - (keyState.back ? 0.55 : 0);
  playerShip.throttleVisual = Math.abs(throttle) * (keyState.boost ? 1.35 : 1);
  const boost = keyState.boost ? 1.75 : 1.0;
  const forward = SHIP_FORWARD.clone().applyQuaternion(playerShip.pivot.quaternion);
  playerShip.pivot.position.addScaledVector(forward, playerShip.speed * throttle * boost * dt);
  clampToArena(playerShip.pivot.position);
  playerShip.fireCooldown -= dt;
  if (keyState.firing && playerShip.fireCooldown <= 0) {
    fireShip(playerShip, bestTargetInCone(playerShip) || null);
    playerShip.fireCooldown = playerShip.fireRate;
  }
}

function updateFriendlyEscort(ship, dt) {
  if (!playerShip?.alive || !ship.alive || ship === playerShip) return;
  const target = assignedHostile(ship);
  const playerToTarget = target ? playerShip.pivot.position.distanceTo(target.pivot.position) : Infinity;
  const shipToTarget = target ? ship.pivot.position.distanceTo(target.pivot.position) : Infinity;
  const fleetAlert = target && (shipToTarget < fleetAlertRange || playerToTarget < fleetAlertRange);
  ship.alert = !!fleetAlert;

  if (fleetAlert) {
    const standoff = ship.weaponRange * 0.72;
    const throttle = shipToTarget > standoff ? 0.72 : -0.16;
    steerToward(ship, target.pivot.position, dt, throttle);
    ship.throttleVisual = Math.max(0.35, Math.abs(throttle));
    ship.fireCooldown -= dt;
    if (shipToTarget < ship.weaponRange * 1.08 && ship.fireCooldown <= 0) {
      fireShip(ship, target);
      ship.fireCooldown = ship.fireRate * (0.8 + Math.random() * 0.45);
    }
    return;
  }

  const goal = formationPoint(playerShip, ship.formationForward, ship.formationSide);
  const throttle = THREE.MathUtils.clamp(ship.pivot.position.distanceTo(goal) / 50, 0.18, 1.05);
  ship.throttleVisual = throttle * 0.55;
  steerToward(ship, goal, dt, throttle);
}

function updateEnemy(ship, dt) {
  const target = assignedHostile(ship);
  if (!target) return;
  const dist = ship.pivot.position.distanceTo(target.pivot.position);
  const throttle = dist > ship.weaponRange * 0.72 ? 0.85 : -0.25;
  ship.alert = true;
  ship.throttleVisual = Math.max(0.38, Math.abs(throttle));
  steerToward(ship, target.pivot.position, dt, throttle);
  ship.fireCooldown -= dt;
  if (dist < ship.weaponRange && ship.fireCooldown <= 0) {
    fireShip(ship, target);
    ship.fireCooldown = ship.fireRate * (0.85 + Math.random() * 0.5);
  }
}

function steerToward(ship, point, dt, throttle) {
  const delta = point.clone().sub(ship.pivot.position);
  delta.y = 0;
  if (delta.lengthSq() < 0.0001) return;
  const desiredYaw = Math.atan2(-delta.z, delta.x);
  ship.pivot.rotation.y = turnAngle(ship.pivot.rotation.y, desiredYaw, ship.turnRate * dt);
  const forward = SHIP_FORWARD.clone().applyQuaternion(ship.pivot.quaternion);
  ship.pivot.position.addScaledVector(forward, ship.speed * throttle * dt);
  clampToArena(ship.pivot.position);
}

function turnAngle(current, target, maxStep) {
  let delta = target - current;
  while (delta <= -Math.PI) delta += Math.PI * 2;
  while (delta > Math.PI) delta -= Math.PI * 2;
  return current + THREE.MathUtils.clamp(delta, -maxStep, maxStep);
}

function clampToArena(pos) {
  const len = Math.hypot(pos.x, pos.z);
  if (len > arenaRadius) {
    pos.x = (pos.x / len) * arenaRadius;
    pos.z = (pos.z / len) * arenaRadius;
  }
}

function nearestHostile(source) {
  let best = null;
  let bestD = Infinity;
  for (const ship of hostileContacts(source)) {
    const d = source.pivot.position.distanceToSquared(ship.pivot.position);
    if (d < bestD) {
      bestD = d;
      best = ship;
    }
  }
  return best;
}

function hostileContacts(source) {
  return ships.filter((ship) => ship.alive && ship !== source && ship.teamId !== source.teamId);
}

function assignedHostile(source) {
  const hostiles = hostileContacts(source);
  if (!hostiles.length) return null;
  const sorted = hostiles.slice().sort((a, b) => {
    const da = source.pivot.position.distanceToSquared(a.pivot.position);
    const db = source.pivot.position.distanceToSquared(b.pivot.position);
    return da - db || a.name.localeCompare(b.name);
  });
  if (source.teamId !== 0 || sorted.length === 1) return sorted[0];

  const allies = ships.filter((ship) => ship.alive && ship.teamId === 0);
  const index = Math.max(0, allies.indexOf(source));
  return sorted[index % sorted.length];
}

function bestTargetInCone(source) {
  const forward = SHIP_FORWARD.clone().applyQuaternion(source.pivot.quaternion).normalize();
  let best = null;
  let bestScore = Infinity;
  for (const ship of ships) {
    if (!ship.alive || ship.teamId === source.teamId) continue;
    const toTarget = ship.pivot.position.clone().sub(source.pivot.position);
    const dist = toTarget.length();
    if (dist > source.weaponRange * 1.25) continue;
    toTarget.normalize();
    const dot = forward.dot(toTarget);
    if (dot < 0.70) continue;
    const score = dist - dot * 25;
    if (score < bestScore) {
      bestScore = score;
      best = ship;
    }
  }
  return best;
}

function fireShip(ship, target) {
  const direction = target
    ? target.pivot.position.clone().sub(ship.pivot.position).normalize()
    : SHIP_FORWARD.clone().applyQuaternion(ship.pivot.quaternion).normalize();
  const color = ship.teamId === 0 ? 0x35a3ff : 0xff493c;
  for (const hp of ship.hardpoints) {
    const origin = new THREE.Vector3();
    hp.muzzle.getWorldPosition(origin);
    const projectile = new THREE.Mesh(
      new THREE.SphereGeometry(ship === playerShip ? 0.18 : 0.11, 10, 10),
      new THREE.MeshBasicMaterial({ color })
    );
    projectile.position.copy(origin);
    scene.add(projectile);
    hp.flash.material.opacity = 0.95;
    projectiles.push({
      mesh: projectile,
      owner: ship,
      velocity: direction.clone().multiplyScalar(ship.projectileSpeed),
      damage: ship.damage,
      ttl: 2.4,
      radius: 0.28
    });
  }
}

function updateProjectiles(dt) {
  for (let i = projectiles.length - 1; i >= 0; i--) {
    const p = projectiles[i];
    p.ttl -= dt;
    p.mesh.position.addScaledVector(p.velocity, dt);
    const hit = firstProjectileHit(p);
    if (hit) {
      applyDamage(hit, p.damage, p.mesh.position);
      p.ttl = 0;
    }
    if (p.ttl <= 0) {
      scene.remove(p.mesh);
      p.mesh.geometry?.dispose();
      p.mesh.material?.dispose();
      projectiles.splice(i, 1);
    }
  }
}

function firstProjectileHit(projectile) {
  for (const ship of ships) {
    if (!ship.alive || ship.teamId === projectile.owner.teamId || ship === projectile.owner) continue;
    const r = ship.radius + projectile.radius;
    if (ship.pivot.position.distanceToSquared(projectile.mesh.position) <= r * r) return ship;
  }
  return null;
}

function applyDamage(ship, damage, position) {
  ship.hp = Math.max(0, ship.hp - damage);
  spawnHit(position, ship.teamId === 0 ? 0x7bcaff : 0xff7a5e);
  if (ship.hp <= 0) destroyShip(ship);
}

function destroyShip(ship) {
  ship.alive = false;
  ship.pivot.visible = false;
  spawnExplosion(ship.pivot.position, ship.radius, ship.teamId === 0 ? 0x66cfff : 0xff654b);
  if (ship === playerShip) {
    const next = ships.find((s) => s.alive && s.teamId === 0);
    if (next) setPlayerShip(next);
  }
}

function spawnHit(position, color) {
  const mesh = new THREE.Mesh(
    new THREE.SphereGeometry(0.55, 12, 12),
    new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.85 })
  );
  mesh.position.copy(position);
  scene.add(mesh);
  effects.push({ mesh, ttl: 0.22 });
}

function spawnExplosion(position, radius, color) {
  const mesh = new THREE.Mesh(
    new THREE.SphereGeometry(Math.max(1.2, radius * 0.7), 18, 18),
    new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.9 })
  );
  mesh.position.copy(position);
  scene.add(mesh);
  effects.push({ mesh, ttl: 0.6 });
}

function updateEffects(dt) {
  for (let i = effects.length - 1; i >= 0; i--) {
    const e = effects[i];
    e.ttl -= dt;
    if (e.mesh.material) e.mesh.material.opacity = Math.max(0, e.ttl / 0.6);
    e.mesh.scale.multiplyScalar(1 + dt * 1.5);
    if (e.ttl <= 0) {
      scene.remove(e.mesh);
      e.mesh.geometry?.dispose();
      e.mesh.material?.dispose();
      effects.splice(i, 1);
    }
  }
}

function updateEnemySpawner(dt) {
  if (sandboxBooting) return;
  const aliveEnemies = ships.filter((s) => s.alive && s.teamId !== 0).length;
  if (aliveEnemies > 14) return;
  waveTimer -= dt;
  if (waveTimer > 0) return;
  spawnEnemyWave();
  waveNumber++;
  waveTimer = THREE.MathUtils.clamp(16 - waveNumber * 0.45, 8, 16) + Math.random() * 5;
}

async function spawnInitialOpposingFleet() {
  if (!playerShip?.alive) return;
  const forward = SHIP_FORWARD.clone().applyQuaternion(playerShip.pivot.quaternion);
  const side = new THREE.Vector3(0, 0, 1).applyQuaternion(playerShip.pivot.quaternion);
  const origin = playerShip.pivot.position.clone().addScaledVector(forward, 128);
  const roster = [
    { name: "Red Picket", teamId: 1, choices: [["red", "picket"], ["yellow", "picket"], ["green", "picket"]], side: -68, row: 0, hp: 170, speed: 58, range: 74 },
    { name: "Yellow Picket", teamId: 2, choices: [["yellow", "picket"], ["green", "picket"], ["red", "picket"]], side: 68, row: 0, hp: 170, speed: 58, range: 74 },
    { name: "Red Patrol", teamId: 1, choices: [["red", "patrol"], ["yellow", "patrol"], ["green", "patrol"]], side: -48, row: 12, hp: 180, speed: 56, range: 76 },
    { name: "Green Patrol", teamId: 3, choices: [["green", "patrol"], ["yellow", "patrol"], ["red", "patrol"]], side: 48, row: 12, hp: 180, speed: 56, range: 76 },
    { name: "Yellow Fighter", teamId: 2, choices: [["yellow", "fighter"], ["green", "fighter"]], side: -30, row: -12, hp: 115, speed: 76, range: 58 },
    { name: "Green Fighter", teamId: 3, choices: [["green", "fighter"], ["yellow", "fighter"]], side: 30, row: -12, hp: 115, speed: 76, range: 58 },
    { name: "Red Missile Ship", teamId: 1, choices: [["red", "missile"], ["yellow", "missile"], ["green", "missile"]], side: -84, row: 28, hp: 260, speed: 42, range: 112 },
    { name: "Yellow Missile Boat", teamId: 2, choices: [["yellow", "missile"], ["green", "missile"], ["red", "missile"]], side: 84, row: 28, hp: 260, speed: 42, range: 112 },
    { name: "Red Stealth Ship", teamId: 1, choices: [["red", "stealth"], ["yellow", "stealth"], ["green", "stealth"]], side: -16, row: 42, hp: 210, speed: 62, range: 78 },
    { name: "Yellow Stealth Ship", teamId: 2, choices: [["yellow", "stealth"], ["green", "stealth"], ["red", "stealth"]], side: 16, row: 42, hp: 210, speed: 62, range: 78 },
    { name: "Red Light Cruiser", teamId: 1, choices: [["red", "light", "cruiser"], ["yellow", "light", "cruiser"], ["green", "light", "cruiser"]], side: -58, row: 58, hp: 430, speed: 36, range: 92 },
    { name: "Yellow Medium Cruiser", teamId: 2, choices: [["yellow", "medium", "cruiser"], ["red", "medium", "cruiser"], ["green", "cruiser"]], side: 58, row: 58, hp: 520, speed: 34, range: 98 },
    { name: "Green Supership", teamId: 3, choices: [["green", "supership"], ["yellow", "supership"], ["red", "supership"]], side: 0, row: 78, hp: 900, speed: 24, range: 112 },
    { name: "Red Transport", teamId: 1, choices: [["red", "transport"], ["yellow", "transport"], ["green", "transport"]], side: -96, row: 78, hp: 320, speed: 28, range: 66 },
    { name: "Yellow Hauler", teamId: 2, choices: [["yellow", "hauler"], ["red", "hauler"], ["green", "hauler"]], side: 96, row: 78, hp: 300, speed: 28, range: 64 },
    { name: "Green Miner", teamId: 3, choices: [["green", "miner"], ["red", "miner"], ["yellow", "mining"]], side: 0, row: 104, hp: 260, speed: 30, range: 62 }
  ];

  let spawned = 0;
  for (const spec of roster) {
    const model = await loadModelAny(spec.choices);
    if (!model) continue;
    const pos = origin.clone()
      .addScaledVector(side, spec.side)
      .addScaledVector(forward, spec.row)
      .add(new THREE.Vector3((Math.random() - 0.5) * 6, 0, (Math.random() - 0.5) * 6));
    const ship = await addShip({
      name: spec.name,
      teamId: spec.teamId,
      model,
      position: pos,
      targetSize: enemySize(model),
      hp: spec.hp,
      speed: spec.speed,
      turnRate: spec.speed > 60 ? 1.9 : 1.25,
      weaponRange: spec.range,
      projectileSpeed: 132,
      damage: spec.hp > 500 ? 8 : 5,
      fireRate: spec.hp > 500 ? 0.36 : 0.3
    });
    steerToward(ship, playerShip.pivot.position, 1, 0);
    spawned++;
  }

  waveNumber = 1;
  hudNote = `Fleet contact: ${spawned} hostile ships active`;
  hudNoteT = 4;
}

async function spawnEnemyWave() {
  if (!playerShip?.alive) return;
  const count = 3 + Math.min(6, Math.floor(waveNumber / 2)) + Math.floor(Math.random() * 3);
  const forward = SHIP_FORWARD.clone().applyQuaternion(playerShip.pivot.quaternion);
  const side = new THREE.Vector3(0, 0, 1).applyQuaternion(playerShip.pivot.quaternion);
  const center = playerShip.pivot.position.clone()
    .addScaledVector(forward, 150 + Math.random() * 55)
    .addScaledVector(side, (Math.random() - 0.5) * 80);
  for (let i = 0; i < count; i++) {
    const model = await loadEnemyModel(i);
    const pos = center.clone().addScaledVector(side, (i - (count - 1) * 0.5) * 16).add(new THREE.Vector3((Math.random() - 0.5) * 10, 0, (Math.random() - 0.5) * 10));
    const teamId = waveNumber % 5 === 4 ? 3 : 1;
    const size = enemySize(model);
    const ship = await addShip({
      name: `Enemy Raider ${waveNumber + 1}.${i + 1}`,
      teamId,
      model,
      position: pos,
      targetSize: size,
      hp: 180 + waveNumber * 14,
      speed: 35 + Math.random() * 20,
      turnRate: 1.3 + Math.random() * 0.5,
      weaponRange: 70 + Math.random() * 32,
      projectileSpeed: 130,
      damage: 5 + waveNumber * 0.35,
      fireRate: 0.34
    });
    steerToward(ship, playerShip.pivot.position, 1, 0);
  }
}

async function loadEnemyModel(index) {
  const choices = [
    [["yellow", "fighter"], ["green", "fighter"]],
    [["red", "picket"], ["yellow", "picket"], ["green", "picket"]],
    [["red", "patrol"], ["yellow", "patrol"], ["green", "patrol"]],
    [["red", "stealth"], ["yellow", "stealth"], ["green", "stealth"]],
    [["red", "missile"], ["yellow", "missile"], ["green", "missile"]],
    [["red", "light", "cruiser"], ["yellow", "light", "cruiser"], ["green", "light", "cruiser"]],
    [["red", "medium", "cruiser"], ["yellow", "medium", "cruiser"], ["green", "cruiser"]],
    [["red", "supership"], ["yellow", "supership"], ["green", "supership"]],
    [["red", "miner"], ["yellow", "mining"], ["green", "miner"]],
    [["red", "hauler"], ["yellow", "hauler"], ["green", "hauler"]],
    [["red", "transport"], ["yellow", "transport"], ["green", "transport"]]
  ];
  return loadModelAny(choices[(waveNumber + index) % choices.length]);
}

function enemySize(model) {
  if (!model?.name) return 7;
  const n = model.name.toLowerCase();
  if (n.includes("cruiser") || n.includes("supership")) return 13;
  if (n.includes("transport") || n.includes("hauler")) return 9;
  return 6.5;
}

function updateThrusters(dt, t) {
  for (const ship of ships) {
    if (!ship.alive) continue;
    const active = ship === playerShip;
    const commandedThrust = active
      ? Math.max(ship.throttleVisual, keyState.forward || keyState.back ? 1 : 0)
      : ship.throttleVisual;
    const alertIdle = ship.alert ? 0.45 : 0.18;
    const thrust = THREE.MathUtils.clamp(Math.max(commandedThrust, alertIdle), 0.12, 1.35);
    for (const thruster of ship.thrusters) {
      const pulse = 0.75 + Math.sin(t * 22 + thruster.group.id * 0.4) * 0.25;
      thruster.plume.scale.set(1, 0.6 + thrust * pulse * 1.5, 1);
      thruster.plume.material.opacity = 0.18 + thrust * 0.38;
    }
    for (const hp of ship.hardpoints) {
      hp.flash.material.opacity = Math.max(0, hp.flash.material.opacity - dt * 7);
    }
  }
}

function updateCamera(dt, t) {
  if (!playerShip) return;
  if (cinematic) {
    const r = 75;
    camera.position.lerp(playerShip.pivot.position.clone().add(new THREE.Vector3(Math.cos(t * 0.18) * r, 34, Math.sin(t * 0.18) * r)), 1 - Math.pow(0.001, dt));
    controls.target.lerp(playerShip.pivot.position, 1 - Math.pow(0.001, dt));
    return;
  }
  if (!followCamera) return;
  const behind = new THREE.Vector3(-54, 22, 0).applyQuaternion(playerShip.pivot.quaternion);
  const desiredCam = playerShip.pivot.position.clone().add(behind);
  const desiredTarget = playerShip.pivot.position.clone().add(new THREE.Vector3(14, 4, 0).applyQuaternion(playerShip.pivot.quaternion));
  camera.position.lerp(desiredCam, 1 - Math.pow(0.0008, dt));
  controls.target.lerp(desiredTarget, 1 - Math.pow(0.0008, dt));
}

function setPlayerShip(ship) {
  if (!ship) return;
  if (playerShip) playerShip.playerControlled = false;
  playerShip = ship;
  playerShip.playerControlled = true;
  activeBlueIndex = ships.indexOf(ship);
  followCamera = true;
  controls.enabled = false;
}

function cyclePlayerShip() {
  const blue = ships.filter((s) => s.alive && s.teamId === 0);
  if (!blue.length) return;
  const current = blue.indexOf(playerShip);
  setPlayerShip(blue[(current + 1 + blue.length) % blue.length]);
}

function createEnvironmentProps() {
  for (const prop of props) scene.remove(prop);
  props.length = 0;
  for (let i = 0; i < 22; i++) {
    const asteroid = createAsteroid(0.6 + Math.random() * 1.8);
    const angle = Math.random() * Math.PI * 2;
    const r = 45 + Math.random() * 160;
    asteroid.position.set(Math.cos(angle) * r, 1.5 + Math.random() * 10, Math.sin(angle) * r);
    asteroid.rotation.set(Math.random() * Math.PI, Math.random() * Math.PI, Math.random() * Math.PI);
    scene.add(asteroid);
    props.push(asteroid);
  }
}

function createStars() {
  const count = 3200;
  const geo = new THREE.BufferGeometry();
  const positions = new Float32Array(count * 3);
  for (let i = 0; i < count; i++) {
    const r = 500 + Math.random() * 1400;
    const theta = Math.random() * Math.PI * 2;
    const phi = Math.acos(2 * Math.random() - 1);
    positions[i * 3 + 0] = r * Math.sin(phi) * Math.cos(theta);
    positions[i * 3 + 1] = r * Math.cos(phi);
    positions[i * 3 + 2] = r * Math.sin(phi) * Math.sin(theta);
  }
  geo.setAttribute("position", new THREE.BufferAttribute(positions, 3));
  return new THREE.Points(geo, new THREE.PointsMaterial({ color: 0xbfd8ff, size: 1.3, sizeAttenuation: true, transparent: true, opacity: 0.8 }));
}

function createAsteroid(scale = 1) {
  const geo = new THREE.IcosahedronGeometry(3 * scale, 2);
  const pos = geo.attributes.position;
  for (let i = 0; i < pos.count; i++) {
    const v = new THREE.Vector3().fromBufferAttribute(pos, i);
    v.multiplyScalar(0.72 + Math.random() * 0.45);
    pos.setXYZ(i, v.x, v.y, v.z);
  }
  geo.computeVertexNormals();
  return new THREE.Mesh(geo, new THREE.MeshStandardMaterial({ color: 0x5f6978, roughness: 0.97, metalness: 0.02, flatShading: true }));
}

function updateStatusHud(dt) {
  if (hudNoteT > 0) hudNoteT = Math.max(0, hudNoteT - dt);
  const enemies = ships.filter((s) => s.alive && s.teamId !== 0).length;
  const allies = ships.filter((s) => s.alive && s.teamId === 0);
  const alertAllies = allies.filter((s) => s.alert).length;
  const player = playerShip;
  const playerLine = player ? `${player.name}: ${Math.ceil(player.hp)}/${player.maxHp}` : "No active blue ship";
  const actionLine = sandboxBooting ? "Loading full fleet before combat starts..." : (hudNoteT > 0 ? hudNote : "W/S thrust, A/D turn, Shift boost, Space/LMB fire, Tab switch ship");
  statusEl.textContent = `${playerLine}\nBlue ships: ${alertAllies}/${allies.length} alert  Hostiles: ${enemies}  Wave: ${waveNumber}\n${actionLine}`;
}

window.addEventListener("keydown", (event) => {
  const k = event.key.toLowerCase();
  if (k === "w") keyState.forward = true;
  if (k === "s") keyState.back = true;
  if (k === "a") keyState.left = true;
  if (k === "d") keyState.right = true;
  if (k === "shift") keyState.boost = true;
  if (k === " ") keyState.firing = true;
  if (k === "tab") {
    event.preventDefault();
    cyclePlayerShip();
  }
  if (k === "f") {
    followCamera = !followCamera;
    controls.enabled = !followCamera;
  }
  if (k === "c") cinematic = !cinematic;
  if (k === "p") paused = !paused;
  if (k === "n") spawnMothershipSandbox();
});

window.addEventListener("keyup", (event) => {
  const k = event.key.toLowerCase();
  if (k === "w") keyState.forward = false;
  if (k === "s") keyState.back = false;
  if (k === "a") keyState.left = false;
  if (k === "d") keyState.right = false;
  if (k === "shift") keyState.boost = false;
  if (k === " ") keyState.firing = false;
});

window.addEventListener("mousedown", (event) => {
  if (event.button === 0) keyState.firing = true;
});

window.addEventListener("mouseup", (event) => {
  if (event.button === 0) keyState.firing = false;
});

window.addEventListener("resize", () => {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
});

renderer.setAnimationLoop(() => {
  const dt = Math.min(0.033, clock.getDelta());
  const t = clock.elapsedTime;
  if (!paused && playerShip && !sandboxBooting) {
    updatePlayer(dt);
    for (const ship of ships) {
      if (!ship.alive || ship === playerShip) continue;
      if (ship.teamId === 0) updateFriendlyEscort(ship, dt);
      else updateEnemy(ship, dt);
    }
    updateEnemySpawner(dt);
    updateProjectiles(dt);
    updateEffects(dt);
  }
  updateThrusters(dt, t);
  updateCamera(dt, t);
  updateStatusHud(dt);
  stars.rotation.y += dt * 0.006;
  for (const prop of props) {
    prop.rotation.y += dt * 0.04;
    prop.rotation.x += dt * 0.018;
  }
  controls.update();
  renderer.render(scene, camera);
});

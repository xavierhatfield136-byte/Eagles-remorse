import * as THREE from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";

const app = document.getElementById("app");

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
statusEl.textContent = "Loading ships...";
document.body.appendChild(statusEl);

const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.outputColorSpace = THREE.SRGBColorSpace;
app.appendChild(renderer.domElement);

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x02050b);
scene.fog = new THREE.Fog(0x030711, 120, 900);

const camera = new THREE.PerspectiveCamera(60, window.innerWidth / window.innerHeight, 0.1, 4000);
camera.position.set(14, 9, 20);

const controls = new OrbitControls(camera, renderer.domElement);
controls.enableDamping = true;
controls.target.set(0, 2, 0);

scene.add(new THREE.AmbientLight(0xa8c9ff, 0.52));
const keyLight = new THREE.DirectionalLight(0xffffff, 1.05);
keyLight.position.set(16, 22, 8);
scene.add(keyLight);
const rimLight = new THREE.DirectionalLight(0x4f9bff, 0.7);
rimLight.position.set(-18, 8, -16);
scene.add(rimLight);

const hemi = new THREE.HemisphereLight(0x6d8fbd, 0x05070d, 0.45);
scene.add(hemi);

const grid = new THREE.GridHelper(200, 120, 0x4a658f, 0x1f2f43);
grid.position.y = -0.03;
scene.add(grid);

const ground = new THREE.Mesh(
  new THREE.CircleGeometry(120, 160),
  new THREE.MeshStandardMaterial({ color: 0x0b1220, roughness: 0.96, metalness: 0.02 })
);
ground.rotation.x = -Math.PI / 2;
ground.position.y = -0.04;
scene.add(ground);

const stars = createStars();
scene.add(stars);

const asteroid = createAsteroid();
asteroid.position.set(10, 2.1, -6);
scene.add(asteroid);

const asteroid2 = createAsteroid(1.2);
asteroid2.position.set(-15, 3.4, 4);
scene.add(asteroid2);

const loader = new GLTFLoader();
const ships = [];
let activeShip = null;
let activeIndex = 0;
let activeHardpoints = [];
const projectiles = [];
const SHIP_FORWARD = new THREE.Vector3(1, 0, 0);
const SHIP_UP = new THREE.Vector3(0, 1, 0);
let hudNote = "Loading ships...";
let hudNoteT = 0;

const keyState = {
  forward: false,
  back: false,
  left: false,
  right: false,
  up: false,
  down: false,
  boost: false,
  firing: false
};

const flight = {
  enabled: false,
  yaw: 0,
  pitch: 0,
  velocity: new THREE.Vector3(),
  mouseSensitivity: 0.002,
  accel: 25,
  maxSpeed: 32,
  damping: 0.92,
  boostMult: 2.2
};

const weaponConfig = {
  projectileSpeed: 140,
  projectileRadius: 0.09,
  projectileDamage: 16,
  projectileTtl: 2.1
};

const clock = new THREE.Clock();

Promise.all([
  loadShip("./public/models/ship.glb", new THREE.Vector3(0, 0, 0), "Blue Ship"),
  loadShip("./public/models/ship-red.glb", new THREE.Vector3(22, 0, -10), "Red Ship")
])
  .then(() => {
    setActiveShip(0);
    hudNote = "Ships loaded (1/2 swap, F refocus, X flight mode, N reset)";
    hudNoteT = 5.0;
  })
  .catch((err) => {
    console.error(err);
    hudNote = "One or more ships failed to load (check console)";
    hudNoteT = 8.0;
  });

function loadShip(path, spawnPos, name) {
  return new Promise((resolve, reject) => {
    loader.load(
      path,
      (gltf) => {
        const shipRoot = gltf.scene;
        const shipPivot = new THREE.Group();
        shipPivot.name = name;
        shipPivot.add(shipRoot);
        scene.add(shipPivot);

        normalizeModel(shipRoot, 9.0);
        orientShipModel(shipRoot, shipPivot);
        shipPivot.position.copy(spawnPos);

        const bounds = shipBoundsInPivotSpace(shipRoot, shipPivot);
        const hardpoints = createHardpoints(shipPivot, bounds);
        const thrusters = createThrusters(shipPivot, bounds);
        const teamId = /red/i.test(name) ? 1 : 0;
        const radius = Math.max(1.1, bounds.size.length() * 0.16);
        const maxHp = teamId === 1 ? 180 : 220;

        const ship = {
          name,
          root: shipRoot,
          pivot: shipPivot,
          teamId,
          radius,
          maxHp,
          hp: maxHp,
          alive: true,
          spawnPos: spawnPos.clone(),
          spawnQuat: shipPivot.quaternion.clone(),
          spawnRootQuat: shipRoot.quaternion.clone(),
          hardpoints,
          thrusters,
          fireCooldown: 0,
          fireRate: 0.1
        };

        ships.push(ship);
        resolve(ship);
      },
      undefined,
      reject
    );
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

  object3d.traverse((node) => {
    if (!node.isMesh || !node.material) return;
    if (Array.isArray(node.material)) {
      for (const mat of node.material) mat.side = THREE.DoubleSide;
    } else {
      node.material.side = THREE.DoubleSide;
    }
  });
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
    if (!node.isMesh || !node.geometry || !node.geometry.attributes || !node.geometry.attributes.position) return;
    const attr = node.geometry.attributes.position;
    for (let i = 0; i < attr.count; i++) {
      const v = new THREE.Vector3().fromBufferAttribute(attr, i).applyMatrix4(node.matrixWorld).applyMatrix4(invPivot);
      out.push(v);
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
    const a = (forwardAxis === 0) ? v.x : (forwardAxis === 1 ? v.y : v.z);
    const b = (forwardAxis === 0) ? v.y : v.x;
    const c = (forwardAxis === 2) ? v.y : v.z;
    const cb = (forwardAxis === 0) ? center.y : center.x;
    const cc = (forwardAxis === 2) ? center.y : center.z;
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
  const minAvg = minCount > 0 ? minRadiusSum / minCount : Number.POSITIVE_INFINITY;
  const maxAvg = maxCount > 0 ? maxRadiusSum / maxCount : Number.POSITIVE_INFINITY;
  const forwardSign = minAvg <= maxAvg ? -1 : 1;

  const qForward = new THREE.Quaternion().setFromUnitVectors(
    axisVector(forwardAxis, forwardSign).normalize(),
    SHIP_FORWARD
  );

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
  return {
    box,
    min: box.min.clone(),
    max: box.max.clone(),
    size: box.getSize(new THREE.Vector3())
  };
}

function pointFromBounds(bounds, nx, ny, nz) {
  return new THREE.Vector3(
    THREE.MathUtils.lerp(bounds.min.x, bounds.max.x, nx),
    THREE.MathUtils.lerp(bounds.min.y, bounds.max.y, ny),
    THREE.MathUtils.lerp(bounds.min.z, bounds.max.z, nz)
  );
}

function createHardpoints(shipPivot, bounds) {
  const weaponAnchors = [
    { n: [0.82, 0.54, 0.63], size: 0.20 },
    { n: [0.82, 0.54, 0.37], size: 0.20 },
    { n: [0.72, 0.50, 0.70], size: 0.17 },
    { n: [0.72, 0.50, 0.30], size: 0.17 }
  ];

  const hardpoints = [];
  for (const mount of weaponAnchors) {
    const group = new THREE.Group();
    group.position.copy(pointFromBounds(bounds, mount.n[0], mount.n[1], mount.n[2]));

    const base = new THREE.Mesh(
      new THREE.CylinderGeometry(mount.size * 0.52, mount.size * 0.62, mount.size * 0.45, 10),
      new THREE.MeshStandardMaterial({ color: 0x2a3445, roughness: 0.58, metalness: 0.42 })
    );
    base.rotation.z = Math.PI / 2;

    const barrel = new THREE.Mesh(
      new THREE.CylinderGeometry(mount.size * 0.16, mount.size * 0.16, mount.size * 1.7, 10),
      new THREE.MeshStandardMaterial({ color: 0x8096ba, roughness: 0.44, metalness: 0.72 })
    );
    barrel.rotation.z = Math.PI / 2;
    barrel.position.x = mount.size * 0.86;

    const muzzle = new THREE.Object3D();
    muzzle.position.x = mount.size * 1.75;

    const flash = new THREE.Mesh(
      new THREE.SphereGeometry(mount.size * 0.22, 10, 10),
      new THREE.MeshBasicMaterial({ color: 0xffb35f, transparent: true, opacity: 0.0 })
    );
    flash.position.x = muzzle.position.x - mount.size * 0.1;

    group.add(base);
    group.add(barrel);
    group.add(muzzle);
    group.add(flash);
    shipPivot.add(group);
    hardpoints.push({ group, muzzle, flash });
  }
  return hardpoints;
}

function createThrusters(shipPivot, bounds) {
  const thrusterOffsets = [
    pointFromBounds(bounds, 0.10, 0.47, 0.64),
    pointFromBounds(bounds, 0.10, 0.47, 0.36),
    pointFromBounds(bounds, 0.16, 0.41, 0.50)
  ];

  const thrusters = [];
  for (const offset of thrusterOffsets) {
    const group = new THREE.Group();
    group.position.copy(offset);

    const glow = new THREE.Mesh(
      new THREE.SphereGeometry(0.16, 12, 12),
      new THREE.MeshBasicMaterial({ color: 0x66d0ff, transparent: true, opacity: 0.8 })
    );

    const plume = new THREE.Mesh(
      new THREE.ConeGeometry(0.12, 0.9, 12, 1, true),
      new THREE.MeshBasicMaterial({ color: 0x2fbaff, transparent: true, opacity: 0.5, side: THREE.DoubleSide })
    );
    plume.rotation.z = Math.PI / 2;
    plume.position.x = -0.5;

    group.add(glow);
    group.add(plume);
    shipPivot.add(group);

    thrusters.push({ group, glow, plume });
  }

  return thrusters;
}

function createStars() {
  const count = 2800;
  const geo = new THREE.BufferGeometry();
  const positions = new Float32Array(count * 3);
  for (let i = 0; i < count; i++) {
    const r = 350 + Math.random() * 950;
    const theta = Math.random() * Math.PI * 2;
    const phi = Math.acos(2 * Math.random() - 1);
    positions[i * 3 + 0] = r * Math.sin(phi) * Math.cos(theta);
    positions[i * 3 + 1] = r * Math.cos(phi);
    positions[i * 3 + 2] = r * Math.sin(phi) * Math.sin(theta);
  }
  geo.setAttribute("position", new THREE.BufferAttribute(positions, 3));
  const mat = new THREE.PointsMaterial({ color: 0xbfd8ff, size: 1.3, sizeAttenuation: true, transparent: true, opacity: 0.8 });
  return new THREE.Points(geo, mat);
}

function createAsteroid(scale = 1) {
  const geo = new THREE.IcosahedronGeometry(3 * scale, 2);
  const pos = geo.attributes.position;
  for (let i = 0; i < pos.count; i++) {
    const v = new THREE.Vector3().fromBufferAttribute(pos, i);
    const noise = 0.72 + Math.random() * 0.45;
    v.multiplyScalar(noise);
    pos.setXYZ(i, v.x, v.y, v.z);
  }
  geo.computeVertexNormals();

  const mat = new THREE.MeshStandardMaterial({ color: 0x5f6978, roughness: 0.97, metalness: 0.02, flatShading: true });
  const mesh = new THREE.Mesh(geo, mat);
  return mesh;
}

function setActiveShip(index) {
  if (!ships.length) return;
  const liveShips = ships.filter((s) => s.alive);
  if (!liveShips.length) {
    activeShip = null;
    activeHardpoints = [];
    return;
  }
  activeIndex = (index + ships.length) % ships.length;
  let candidate = ships[activeIndex];
  if (!candidate.alive) {
    candidate = liveShips[0];
    activeIndex = ships.indexOf(candidate);
  }
  activeShip = candidate;
  activeHardpoints = activeShip.hardpoints;
  frameShip(activeShip);
}

function frameShip(ship) {
  const box = new THREE.Box3().setFromObject(ship.pivot);
  const size = box.getSize(new THREE.Vector3());
  const maxSize = Math.max(size.x, size.y, size.z);
  const fitHeightDistance = maxSize / (2 * Math.atan((Math.PI * camera.fov) / 360));
  const fitWidthDistance = fitHeightDistance / camera.aspect;
  const distance = 1.45 * Math.max(fitHeightDistance, fitWidthDistance);

  camera.position.set(ship.pivot.position.x + distance * 0.65, ship.pivot.position.y + distance * 0.4, ship.pivot.position.z + distance);
  controls.target.set(ship.pivot.position.x, ship.pivot.position.y + size.y * 0.2, ship.pivot.position.z);
  controls.minDistance = distance * 0.15;
  controls.maxDistance = distance * 8.0;
  controls.update();
}

function fireFromHardpoints(ship) {
  if (!ship || !ship.alive) return;
  for (const hp of ship.hardpoints) {
    const origin = new THREE.Vector3();
    hp.muzzle.getWorldPosition(origin);

    const direction = SHIP_FORWARD.clone().applyQuaternion(ship.pivot.quaternion).normalize();

    const projectile = new THREE.Mesh(
      new THREE.SphereGeometry(weaponConfig.projectileRadius, 10, 10),
      new THREE.MeshBasicMaterial({ color: 0xff6d44 })
    );
    projectile.position.copy(origin);
    scene.add(projectile);
    hp.flash.material.opacity = 0.95;

    projectiles.push({
      mesh: projectile,
      owner: ship,
      damage: weaponConfig.projectileDamage,
      velocity: direction.multiplyScalar(weaponConfig.projectileSpeed),
      ttl: weaponConfig.projectileTtl,
      radius: weaponConfig.projectileRadius
    });
  }
}

function updateFlight(dt) {
  if (!activeShip || !flight.enabled) return;

  const yawLeft = keyState.left ? 1 : 0;
  const yawRight = keyState.right ? 1 : 0;
  const pitchUp = keyState.up ? 1 : 0;
  const pitchDown = keyState.down ? 1 : 0;

  flight.yaw += (yawLeft - yawRight) * 1.9 * dt;
  flight.pitch += (pitchUp - pitchDown) * 1.35 * dt;
  flight.pitch = THREE.MathUtils.clamp(flight.pitch, -1.1, 1.1);

  const qYaw = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), flight.yaw);
  const qPitch = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 0, 1), flight.pitch);
  activeShip.pivot.quaternion.copy(qYaw).multiply(qPitch);

  const thrustDir = new THREE.Vector3();
  if (keyState.forward) thrustDir.x += 1;
  if (keyState.back) thrustDir.x -= 0.55;
  if (keyState.right) thrustDir.z += 0.3;
  if (keyState.left) thrustDir.z -= 0.3;
  if (keyState.up) thrustDir.y += 0.3;
  if (keyState.down) thrustDir.y -= 0.3;

  if (thrustDir.lengthSq() > 0) {
    thrustDir.normalize();
    thrustDir.applyQuaternion(activeShip.pivot.quaternion);
    const accel = flight.accel * (keyState.boost ? flight.boostMult : 1.0);
    flight.velocity.addScaledVector(thrustDir, accel * dt);
  }

  const maxSpeed = flight.maxSpeed * (keyState.boost ? flight.boostMult : 1.0);
  if (flight.velocity.length() > maxSpeed) {
    flight.velocity.setLength(maxSpeed);
  }

  flight.velocity.multiplyScalar(Math.pow(flight.damping, dt * 60));
  activeShip.pivot.position.addScaledVector(flight.velocity, dt);

  const camOffset = new THREE.Vector3(-14, 6, 0).applyQuaternion(activeShip.pivot.quaternion);
  const desiredCam = activeShip.pivot.position.clone().add(camOffset);
  camera.position.lerp(desiredCam, 1 - Math.pow(0.001, dt));

  const desiredTarget = activeShip.pivot.position.clone().add(new THREE.Vector3(3, 1.8, 0).applyQuaternion(activeShip.pivot.quaternion));
  controls.target.lerp(desiredTarget, 1 - Math.pow(0.001, dt));
}

function updateThrusters(dt, t) {
  for (const ship of ships) {
    if (!ship.alive) continue;
    const speed = ship === activeShip ? flight.velocity.length() : 0;
    const idle = ship === activeShip ? 0.35 : 0.2;
    const thrust = ship === activeShip ? Math.min(1.0, speed / 20) : 0.15;

    for (const thruster of ship.thrusters) {
      const pulse = 0.78 + Math.sin(t * 24 + thruster.group.id * 0.7) * 0.22;
      const intensity = idle + thrust * pulse;
      thruster.glow.scale.setScalar(0.8 + intensity * 0.9);
      thruster.plume.scale.set(1, 0.5 + intensity * 1.7, 1);
      thruster.plume.material.opacity = 0.22 + intensity * 0.45;

      if (Math.random() < intensity * dt * 40) {
        spawnThrusterParticle(thruster.group, ship.pivot);
      }
    }
  }
}

function spawnThrusterParticle(localThruster, shipPivot) {
  const p = new THREE.Mesh(
    new THREE.SphereGeometry(0.03, 6, 6),
    new THREE.MeshBasicMaterial({ color: 0x7dd9ff, transparent: true, opacity: 0.9 })
  );
  const origin = new THREE.Vector3();
  localThruster.getWorldPosition(origin);
  p.position.copy(origin);
  scene.add(p);

  const backward = SHIP_FORWARD.clone().multiplyScalar(-1).applyQuaternion(shipPivot.quaternion);
  backward.add(new THREE.Vector3((Math.random() - 0.5) * 0.12, (Math.random() - 0.5) * 0.12, (Math.random() - 0.5) * 0.12));
  backward.normalize();

  projectiles.push({
    mesh: p,
    velocity: backward.multiplyScalar(8 + Math.random() * 6),
    ttl: 0.45,
    fadeOnly: true
  });
}

function updateProjectiles(dt) {
  for (let i = projectiles.length - 1; i >= 0; i--) {
    const p = projectiles[i];
    p.ttl -= dt;
    p.mesh.position.addScaledVector(p.velocity, dt);

    if (!p.fadeOnly && p.owner && p.owner.alive) {
      const hitShip = firstProjectileHit(p);
      if (hitShip) {
        applyProjectileHit(hitShip, p);
        p.ttl = 0;
      }
    }

    if (p.fadeOnly && p.mesh.material) {
      p.mesh.material.opacity = Math.max(0, p.ttl / 0.45);
    }

    if (p.ttl <= 0) {
      scene.remove(p.mesh);
      if (p.mesh.geometry) p.mesh.geometry.dispose();
      if (p.mesh.material) p.mesh.material.dispose();
      projectiles.splice(i, 1);
    }
  }
}

function firstProjectileHit(projectile) {
  if (!projectile || !projectile.owner) return null;
  for (const ship of ships) {
    if (!ship || !ship.alive || ship === projectile.owner) continue;
    if (ship.teamId === projectile.owner.teamId) continue;
    const hitRadius = Math.max(0.4, ship.radius + (projectile.radius || 0.05));
    if (ship.pivot.position.distanceToSquared(projectile.mesh.position) <= hitRadius * hitRadius) {
      return ship;
    }
  }
  return null;
}

function applyProjectileHit(target, projectile) {
  if (!target || !target.alive || !projectile) return;
  target.hp = Math.max(0, target.hp - Math.max(1, projectile.damage || 1));
  spawnHitBurst(projectile.mesh.position, target.teamId === 1 ? 0xff7b66 : 0x83d0ff);
  if (target.hp <= 0) {
    destroyShip(target, projectile.owner);
  }
}

function spawnHitBurst(position, color) {
  const burst = new THREE.Mesh(
    new THREE.SphereGeometry(0.22, 10, 10),
    new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.9 })
  );
  burst.position.copy(position);
  scene.add(burst);
  projectiles.push({
    mesh: burst,
    velocity: new THREE.Vector3(),
    ttl: 0.22,
    fadeOnly: true
  });
}

function destroyShip(target, attacker) {
  if (!target || !target.alive) return;
  target.alive = false;
  target.hp = 0;
  target.pivot.visible = false;

  const boom = new THREE.Mesh(
    new THREE.SphereGeometry(target.radius * 0.55, 18, 18),
    new THREE.MeshBasicMaterial({ color: 0xffb06b, transparent: true, opacity: 0.95 })
  );
  boom.position.copy(target.pivot.position);
  scene.add(boom);
  projectiles.push({
    mesh: boom,
    velocity: new THREE.Vector3(),
    ttl: 0.55,
    fadeOnly: true
  });

  if (activeShip === target) {
    setActiveShip(ships.findIndex((s) => s && s.alive));
  }
  if (attacker && attacker === activeShip) {
    hudNote = `${target.name} destroyed. Press N to reset duel.`;
    hudNoteT = 6.0;
  }
}

function updateFiring(dt) {
  if (!activeShip || !activeShip.alive) return;

  activeShip.fireCooldown -= dt;
  if ((keyState.firing || keyState.forward) && activeShip.fireCooldown <= 0) {
    fireFromHardpoints(activeShip);
    activeShip.fireCooldown = activeShip.fireRate;
  }

  const blink = 0.55 + 0.45 * Math.sin(performance.now() * 0.01);
  for (const hp of activeHardpoints) {
    hp.flash.material.opacity = Math.max(0, hp.flash.material.opacity - dt * 8.0);
    hp.flash.material.color.setRGB(1.0, 0.58 + 0.35 * blink, 0.22);
  }
}

function setFlightMode(enabled) {
  flight.enabled = enabled;
  controls.enabled = !enabled;
  hudNote = enabled
    ? "Flight mode ON (WASD + RF move, QE look, Shift boost, Space fire)"
    : "Flight mode OFF (Orbit controls active; X toggles flight)";
  hudNoteT = 3.0;
}

function updateStatusHud(dt) {
  if (hudNoteT > 0) hudNoteT = Math.max(0, hudNoteT - dt);
  const player = activeShip;
  const enemies = ships.filter((s) => s.alive && player && s.teamId !== player.teamId);
  const enemy = enemies[0] || null;
  const playerLine = player
    ? `${player.name}: ${Math.ceil(player.hp)}/${player.maxHp}`
    : "No active ship";
  const enemyLine = enemy
    ? `${enemy.name}: ${Math.ceil(enemy.hp)}/${enemy.maxHp}`
    : "Enemy: destroyed (N to reset)";
  const base = `${playerLine}  |  ${enemyLine}`;
  statusEl.textContent = hudNoteT > 0 ? `${base}\n${hudNote}` : base;
}

function resetDuel() {
  for (const ship of ships) {
    ship.alive = true;
    ship.hp = ship.maxHp;
    ship.fireCooldown = 0;
    ship.pivot.visible = true;
    ship.pivot.position.copy(ship.spawnPos);
    ship.pivot.quaternion.copy(ship.spawnQuat);
    ship.root.quaternion.copy(ship.spawnRootQuat);
  }
  flight.velocity.set(0, 0, 0);
  setActiveShip(0);
  hudNote = "Duel reset";
  hudNoteT = 2.0;
}

window.addEventListener("keydown", (event) => {
  const k = event.key.toLowerCase();
  if (k === "w") keyState.forward = true;
  if (k === "s") keyState.back = true;
  if (k === "a") keyState.left = true;
  if (k === "d") keyState.right = true;
  if (k === "r") keyState.up = true;
  if (k === "f") keyState.down = true;
  if (k === "shift") keyState.boost = true;
  if (k === " ") keyState.firing = true;

  if (k === "1") setActiveShip(0);
  if (k === "2") setActiveShip(1);
  if (k === "x") setFlightMode(!flight.enabled);
  if (k === "f" && !flight.enabled && activeShip) frameShip(activeShip);
  if (k === "n") resetDuel();
});

window.addEventListener("keyup", (event) => {
  const k = event.key.toLowerCase();
  if (k === "w") keyState.forward = false;
  if (k === "s") keyState.back = false;
  if (k === "a") keyState.left = false;
  if (k === "d") keyState.right = false;
  if (k === "r") keyState.up = false;
  if (k === "f") keyState.down = false;
  if (k === "shift") keyState.boost = false;
  if (k === " ") keyState.firing = false;
});

window.addEventListener("resize", () => {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
});

renderer.setAnimationLoop(() => {
  const dt = Math.min(0.033, clock.getDelta());
  const t = clock.elapsedTime;

  if (activeShip && activeShip.alive && !flight.enabled) {
    activeShip.pivot.rotation.y += dt * 0.18;
  }

  stars.rotation.y += dt * 0.01;
  asteroid.rotation.y += dt * 0.07;
  asteroid.rotation.x += dt * 0.04;
  asteroid2.rotation.y -= dt * 0.06;

  updateFlight(dt);
  updateThrusters(dt, t);
  updateFiring(dt);
  updateProjectiles(dt);
  updateStatusHud(dt);

  controls.update();
  renderer.render(scene, camera);
});

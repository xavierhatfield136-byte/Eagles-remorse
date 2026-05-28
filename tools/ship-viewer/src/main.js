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

const clock = new THREE.Clock();

Promise.all([
  loadShip("./public/models/ship.glb", new THREE.Vector3(0, 0, 0), "Blue Ship"),
  loadShip("./public/models/ship-red.glb", new THREE.Vector3(22, 0, -10), "Red Ship")
])
  .then(() => {
    setActiveShip(0);
    statusEl.textContent = "Ships loaded (1/2 swap, F refocus, X flight mode)";
  })
  .catch((err) => {
    console.error(err);
    statusEl.textContent = "One or more ships failed to load (check console)";
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
        shipPivot.position.copy(spawnPos);

        const hardpoints = createHardpoints(shipPivot, shipRoot);
        const thrusters = createThrusters(shipPivot, shipRoot);

        const ship = {
          name,
          root: shipRoot,
          pivot: shipPivot,
          hardpoints,
          thrusters,
          fireCooldown: 0,
          fireRate: 0.08
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

function createHardpoints(shipPivot, root) {
  const box = new THREE.Box3().setFromObject(root);
  const size = box.getSize(new THREE.Vector3());

  const points = [
    new THREE.Vector3(size.x * 0.36, size.y * 0.52, size.z * 0.11),
    new THREE.Vector3(size.x * 0.36, size.y * 0.52, -size.z * 0.11),
    new THREE.Vector3(size.x * 0.26, size.y * 0.49, size.z * 0.22),
    new THREE.Vector3(size.x * 0.26, size.y * 0.49, -size.z * 0.22)
  ];

  const markers = [];
  const markerGeo = new THREE.SphereGeometry(0.1, 8, 8);
  for (const p of points) {
    const marker = new THREE.Mesh(markerGeo, new THREE.MeshBasicMaterial({ color: 0xffaa44 }));
    marker.position.copy(p);
    marker.visible = false;
    shipPivot.add(marker);
    markers.push(marker);
  }
  return markers;
}

function createThrusters(shipPivot, root) {
  const box = new THREE.Box3().setFromObject(root);
  const size = box.getSize(new THREE.Vector3());
  const thrusterOffsets = [
    new THREE.Vector3(-size.x * 0.46, size.y * 0.44, size.z * 0.18),
    new THREE.Vector3(-size.x * 0.46, size.y * 0.44, -size.z * 0.18),
    new THREE.Vector3(-size.x * 0.4, size.y * 0.38, 0)
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
  activeIndex = (index + ships.length) % ships.length;
  activeShip = ships[activeIndex];
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
  for (const hp of ship.hardpoints) {
    const origin = new THREE.Vector3();
    hp.getWorldPosition(origin);

    const direction = new THREE.Vector3(1, 0, 0).applyQuaternion(ship.pivot.quaternion).normalize();

    const projectile = new THREE.Mesh(
      new THREE.SphereGeometry(0.06, 8, 8),
      new THREE.MeshBasicMaterial({ color: 0xff5533 })
    );
    projectile.position.copy(origin);
    scene.add(projectile);

    projectiles.push({
      mesh: projectile,
      velocity: direction.multiplyScalar(120),
      ttl: 2.2
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

  const backward = new THREE.Vector3(-1, 0, 0).applyQuaternion(shipPivot.quaternion);
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

function updateFiring(dt) {
  if (!activeShip) return;

  activeShip.fireCooldown -= dt;
  if ((keyState.firing || keyState.forward) && activeShip.fireCooldown <= 0) {
    fireFromHardpoints(activeShip);
    activeShip.fireCooldown = activeShip.fireRate;
  }

  const blink = 0.55 + 0.45 * Math.sin(performance.now() * 0.01);
  for (const hp of activeHardpoints) {
    hp.visible = true;
    hp.material.color.setRGB(1.0, 0.6 + 0.35 * blink, 0.2);
  }
}

function setFlightMode(enabled) {
  flight.enabled = enabled;
  controls.enabled = !enabled;
  statusEl.textContent = enabled
    ? "Flight mode ON (WASD + RF move, QE look, Shift boost, Space fire)"
    : "Flight mode OFF (Orbit controls active; X toggles flight)";
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

  if (activeShip && !flight.enabled) {
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

  controls.update();
  renderer.render(scene, camera);
});

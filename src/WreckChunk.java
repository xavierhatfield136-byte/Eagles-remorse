import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

final class WreckChunk {
    private static final int MAX_ACTIVE = 500;
    private static final double WRECK_PRIMITIVE_MAX_SCREEN_SPAN = 18.0;
    private static final double WRECK_LIGHT_FX_MAX_SCREEN_SPAN = 30.0;
    private static final List<WreckChunk> ACTIVE = new ArrayList<>();
    private static final double DEFAULT_DT = GameContext.DT;
    private static final int MAX_SECONDARY_SCARS = 8;
    private static final Map<Ship, MultipartFinaleState> MULTIPART_FINALES = new IdentityHashMap<>();

    private final Ship parent;
    private final BufferedImage image;
    private final boolean breach;
    private final boolean multipart;
    private final boolean bakedDamageVisuals;
    private final boolean mirrorX;
    private final boolean mirrorY;
    private final double directionalDamageBias;
    private final double directionalLocalHitX;
    private final double directionalLocalHitY;
    private final double localX;
    private final double localY;
    private final double localAngle;
    private final double detachAt;
    private final double scale;
    private final double baseHalfWidth;
    private final double baseHalfHeight;
    private final double burstSpeed;
    private double spin;

    private double x;
    private double y;
    private double vx;
    private double vy;
    private double angle;
    private double life;
    private final double maxLife;
    private boolean attached = true;
    private boolean detachedBoosted = false;
    private double nextSecondaryPopAt = Double.NaN;
    private int secondaryPopsRemaining = 0;
    private final List<SecondaryScar> secondaryScars = new ArrayList<>();

    private enum Profile {
        SMALL,
        MEDIUM,
        LARGE,
        STATION
    }

    private static final class SecondaryScar {
        final double localX;
        final double localY;
        final double radius;
        final double innerRadius;
        final double glow;

        SecondaryScar(double localX, double localY, double radius, double innerRadius, double glow) {
            this.localX = localX;
            this.localY = localY;
            this.radius = radius;
            this.innerRadius = innerRadius;
            this.glow = glow;
        }
    }

    private static final class MultipartFinaleState {
        final double x;
        final double y;
        final double radius;
        double remaining;

        MultipartFinaleState(double x, double y, double radius, double remaining) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.remaining = remaining;
        }
    }

    private static final class DeathVisualSnapshot {
        final List<Ship.RoomStatus> roomStatuses;
        final List<Ship.HullImpactMark> hullImpactMarks;
        final double impactCenterX;
        final double impactCenterY;
        final double impactFocus;

        DeathVisualSnapshot(List<Ship.RoomStatus> roomStatuses, List<Ship.HullImpactMark> hullImpactMarks,
                            double impactCenterX, double impactCenterY, double impactFocus) {
            this.roomStatuses = (roomStatuses == null) ? List.of() : List.copyOf(roomStatuses);
            this.hullImpactMarks = (hullImpactMarks == null) ? List.of() : List.copyOf(hullImpactMarks);
            this.impactCenterX = impactCenterX;
            this.impactCenterY = impactCenterY;
            this.impactFocus = impactFocus;
        }
    }

    private final DeathVisualSnapshot deathSnapshot;

    private WreckChunk(Ship parent, BufferedImage image, boolean breach, boolean multipart, double localX, double localY,
                       double localAngle, double detachAt, double scale, double baseHalfWidth, double baseHalfHeight,
                       double burstSpeed, double spin, double life, DeathVisualSnapshot deathSnapshot,
                       boolean bakedDamageVisuals, boolean mirrorX, boolean mirrorY, double directionalDamageBias,
                       double directionalLocalHitX, double directionalLocalHitY) {
        this.parent = parent;
        this.image = image;
        this.breach = breach;
        this.multipart = multipart;
        this.deathSnapshot = deathSnapshot;
        this.bakedDamageVisuals = bakedDamageVisuals;
        this.mirrorX = mirrorX;
        this.mirrorY = mirrorY;
        this.directionalDamageBias = Math.max(0.0, Math.min(1.0, directionalDamageBias));
        this.directionalLocalHitX = directionalLocalHitX;
        this.directionalLocalHitY = directionalLocalHitY;
        this.localX = localX;
        this.localY = localY;
        this.localAngle = localAngle;
        this.detachAt = Math.max(0.05, detachAt);
        this.scale = Math.max(0.08, scale);
        this.baseHalfWidth = Math.max(6.0, baseHalfWidth);
        this.baseHalfHeight = Math.max(6.0, baseHalfHeight);
        this.burstSpeed = Math.max(0.2, burstSpeed);
        this.spin = spin;
        this.life = Math.max(0.2, life);
        this.maxLife = this.life;
    }

    static void spawnForShip(Ship ship, double burnDuration) {
        if (ship == null || !ship.alive) return;
        if (profileFor(ship) == Profile.SMALL) return;
        ShipPartLibrary.PartSet partSet = ShipPartLibrary.getSet(ship.role, ship.faction, ShipPartLibrary.Variant.DESTROYED);
        if (partSet.hasParts()) {
            spawnMultipartForShip(ship, burnDuration, partSet);
            return;
        }

        List<BufferedImage> chunks = List.of();
        List<BufferedImage> breaches = List.of();
        ShipWreckLibrary.WreckSet set = ShipWreckLibrary.getSet(ship.role, ship.faction);
        if (set == null || !set.hasAny()) return;
        chunks = set.chunks.isEmpty() ? set.breaches : set.chunks;
        breaches = set.breaches;
        if (chunks.isEmpty()) return;

        Profile profile = profileFor(ship);
        int count = Math.min(chunks.size(), maxChunksFor(profile));
        if (count <= 0) return;
        double radius = Math.max(12.0, ship.radius);
        double span = Math.max(0.9, radius * spanMultiplier(profile));
        double baseLife = Math.max(minLifeFor(profile), burnDuration + extraLifeFor(profile));
        double chunkBaseHalf = radius * chunkBaseSizeMultiplier(profile);
        double breachBaseHalf = radius * breachBaseSizeMultiplier(profile);

        for (int i = 0; i < count; i++) {
            BufferedImage img = chunks.get(i);
            double t = (count <= 1) ? 0.5 : (double) i / (double) (count - 1);
            double localX = (-0.38 + t * 0.76) * span;
            double localY = chunkLocalYOffset(profile, i, t, span);
            double localAngle = (i - (count - 1) * 0.5) * chunkLocalAngleMultiplier(profile);
            double detachAt = burnDuration * chunkDetachPhase(profile, t);
            double scale = chunkScale(profile, t);
            double baseHalfSize = chunkBaseHalf * chunkSizeJitter(profile, t);
            double spin = (Ship.randomUnit() - 0.5) * chunkSpinMultiplier(profile);
            double burstSpeed = chunkBurst(profile, radius, t);
            WreckChunk chunk = new WreckChunk(ship, img, false, false, localX, localY, localAngle, detachAt,
                    scale, baseHalfSize, baseHalfSize, burstSpeed, spin, baseLife, null, false,
                    false, false, 0.0, 0.0, 0.0);
            chunk.syncWithParent();
            chunk.vx = ship.vx;
            chunk.vy = ship.vy;
            add(chunk);
        }

        int breachCount = Math.min(breaches.size(), maxBreachesFor(profile));
        for (int i = 0; i < breachCount; i++) {
            BufferedImage img = breaches.get(i);
            double t = (breachCount <= 1) ? 0.5 : (double) i / (double) (breachCount - 1);
            double localX = (-0.16 + t * 0.32) * span;
            double localY = breachLocalYOffset(profile, i, span);
            double localAngle = (Ship.randomUnit() - 0.5) * 0.30;
            double detachAt = burnDuration * breachDetachPhase(profile, t);
            double scale = breachScale(profile, t);
            double baseHalfSize = breachBaseHalf * (0.94 + t * 0.05);
            double spin = (Ship.randomUnit() - 0.5) * breachSpinMultiplier(profile);
            double burstSpeed = breachBurst(profile, radius, t);
            WreckChunk breach = new WreckChunk(ship, img, true, false, localX, localY, localAngle, detachAt,
                    scale, baseHalfSize, baseHalfSize, burstSpeed, spin, baseLife + 0.6, null, false,
                    false, false, 0.0, 0.0, 0.0);
            breach.syncWithParent();
            breach.vx = ship.vx;
            breach.vy = ship.vy;
            add(breach);
        }
    }

    private static void spawnMultipartForShip(Ship ship, double burnDuration, ShipPartLibrary.PartSet partSet) {
        if (ship == null || partSet == null || !partSet.hasParts()) return;
        Profile profile = profileFor(ship);
        int count = partSet.parts.size();
        if (count <= 0) return;

        double radius = Math.max(12.0, ship.radius);
        double canvasSpan = ship.radius * 2.0 * ShipHullSilhouette.skinRenderScale() * visualScaleForRole(ship.role);
        double baseLife = Math.max(minLifeFor(profile), burnDuration + extraLifeFor(profile) + 0.8);
        boolean usesBakedDamageVisuals = partSet.usesBakedDamageVisuals();
        DeathVisualSnapshot snapshot = captureDeathVisualSnapshot(ship);

        for (int i = 0; i < count; i++) {
            ShipPartLibrary.PartSprite sprite = partSet.parts.get(i);
            if (sprite == null || sprite.image == null) continue;
            double t = (count <= 1) ? 0.5 : (double) i / (double) (count - 1);
            double localX = sprite.offsetXNorm * canvasSpan;
            double localY = sprite.offsetYNorm * canvasSpan;
            boolean mirrorX = false;
            boolean mirrorY = false;
            double localAngle = 0.0;
            double detachAt = burnDuration * chunkDetachPhase(profile, t);
            double scale = 1.0;
            double halfW = Math.max(8.0, canvasSpan * sprite.widthNorm * 0.5);
            double halfH = Math.max(8.0, canvasSpan * sprite.heightNorm * 0.5);
            double spin = 0.0;
            double burstSpeed = chunkBurst(profile, radius, t);
            double directionalLocalHitX = (snapshot == null) ? 0.0 : (snapshot.impactCenterX - localX);
            double directionalLocalHitY = (snapshot == null) ? 0.0 : (snapshot.impactCenterY - localY);
            double impactDistance = Math.hypot(directionalLocalHitX, directionalLocalHitY);
            double influenceRadius = Math.max(Math.max(halfW, halfH) * 1.35, ship.radius * 0.34);
            double directionalDamageBias = (snapshot == null)
                    ? 0.0
                    : Math.max(0.0, Math.min(1.0, (1.0 - impactDistance / Math.max(1.0, influenceRadius)) * snapshot.impactFocus));
            WreckChunk chunk = new WreckChunk(ship, sprite.image, false, true, localX, localY, localAngle, detachAt,
                    scale, halfW, halfH, burstSpeed, spin, baseLife, snapshot, usesBakedDamageVisuals,
                    mirrorX, mirrorY,
                    directionalDamageBias, directionalLocalHitX, directionalLocalHitY);
            chunk.syncWithParent();
            chunk.vx = ship.vx;
            chunk.vy = ship.vy;
            if (!usesBakedDamageVisuals) {
                chunk.configureSecondaryPops(profile, t);
            }
            add(chunk);
        }

        seedInitialAmidshipBlast(ship, profile);
    }

    static void releaseForShip(Ship ship, double baseVx, double baseVy) {
        if (ship == null || ACTIVE.isEmpty()) return;
        for (WreckChunk chunk : ACTIVE) {
            if (chunk == null || chunk.parent != ship) continue;
            chunk.forceDetach(baseVx, baseVy);
        }
    }

    static void updateAll(double dt) {
        if (ACTIVE.isEmpty()) return;
        double step = Math.max(0.0, dt);
        if (!MULTIPART_FINALES.isEmpty()) {
            Iterator<Map.Entry<Ship, MultipartFinaleState>> stateIt = MULTIPART_FINALES.entrySet().iterator();
            while (stateIt.hasNext()) {
                Map.Entry<Ship, MultipartFinaleState> entry = stateIt.next();
                MultipartFinaleState state = entry.getValue();
                if (state == null) {
                    stateIt.remove();
                    continue;
                }
                state.remaining -= step;
            }
        }
        for (Iterator<WreckChunk> it = ACTIVE.iterator(); it.hasNext(); ) {
            WreckChunk c = it.next();
            if (c == null) {
                it.remove();
                continue;
            }
            c.update(step);
            if (c.life <= 0.0) {
                if (c.multipart) {
                    MultipartFinaleState state = MULTIPART_FINALES.get(c.parent);
                    if (state == null) {
                        state = beginMultipartFinale(c.parent);
                    }
                    if (state != null && state.remaining > 0.0) {
                        c.life = Math.max(c.life, state.remaining);
                        continue;
                    }
                } else {
                    Explosion.spawnDeath(c.x, c.y);
                }
                it.remove();
            }
        }
        if (!MULTIPART_FINALES.isEmpty()) {
            Iterator<Map.Entry<Ship, MultipartFinaleState>> cleanupIt = MULTIPART_FINALES.entrySet().iterator();
            while (cleanupIt.hasNext()) {
                Map.Entry<Ship, MultipartFinaleState> entry = cleanupIt.next();
                MultipartFinaleState state = entry.getValue();
                if (state != null && state.remaining > 0.0) continue;
                Ship ship = entry.getKey();
                ACTIVE.removeIf(chunk -> chunk != null && chunk.multipart && chunk.parent == ship);
                cleanupIt.remove();
            }
        }
    }

    static int drawAll(Graphics2D g2, double minX, double minY, double maxX, double maxY) {
        return drawAll(g2, minX, minY, maxX, maxY, null);
    }

    static int drawAll(Graphics2D g2, double minX, double minY, double maxX, double maxY,
                       BiPredicate<Double, Double> worldFilter) {
        if (g2 == null || ACTIVE.isEmpty()) return 0;
        int drawn = 0;
        Graphics2D g = (Graphics2D) g2.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double screenScale = screenScale(g);
            for (WreckChunk c : ACTIVE) {
                if (c == null || c.image == null) continue;
                if (c.attached && c.parent != null
                        && ShipPartLibrary.hasParts(c.parent.role, c.parent.faction)
                        && !c.parent.dying) continue;
                if (c.attached) c.syncWithParent();
                if (worldFilter != null && !worldFilter.test(c.x, c.y)) continue;
                if (!c.isVisible(minX, minY, maxX, maxY)) continue;
                c.draw(g, screenScale);
                drawn++;
            }
        } finally {
            g.dispose();
        }
        return drawn;
    }

    private static void add(WreckChunk chunk) {
        ACTIVE.add(chunk);
        while (ACTIVE.size() > MAX_ACTIVE) {
            ACTIVE.remove(0);
        }
    }

    private void update(double dt) {
        life -= dt;
        if (life <= 0.0) return;

        if (attached) {
            if (parent != null && parent.dying) {
                syncWithParent();
            }
            if (parent == null || !parent.dying) {
                forceDetach(parent == null ? 0.0 : parent.vx, parent == null ? 0.0 : parent.vy);
            } else if (parent != null && parent.dyingTimerSeconds() >= detachAt) {
                forceDetach(parent.vx, parent.vy);
            }
        } else {
            x += vx;
            y += vy;
            angle += spin * dt * 60.0;
            double drag = Math.pow(0.985, dt * 60.0);
            vx *= drag;
            vy *= drag;
            if (multipart) {
                maybeTriggerSecondaryPop();
            }
        }
    }

    private void forceDetach(double baseVx, double baseVy) {
        if (!attached) return;
        attached = false;
        if (!detachedBoosted) {
            double burst = burstSpeed;
            double dir = angle;
            vx = baseVx + Math.cos(dir) * burst * DEFAULT_DT;
            vy = baseVy + Math.sin(dir) * burst * DEFAULT_DT;
            if (multipart) {
                spin = 0.0;
                VFX.spawnImpactSparks(x, y, Math.cos(dir), Math.sin(dir), 4);
            } else {
                spin = breach ? spin * 0.6 : spin * 1.4;
            }
            if (breach) {
                VFX.spawnImpactSparks(x, y, Math.cos(dir), Math.sin(dir), 2);
            } else if (burst >= 4.2) {
                VFX.spawnImpactSparks(x, y, Math.cos(dir), Math.sin(dir), 3);
            }
            detachedBoosted = true;
        }
    }

    private void syncWithParent() {
        if (parent == null) return;
        double offsetX = localX;
        double offsetY = localY;
        if (multipart && parent.dying) {
            double dirLen = Math.hypot(localX, localY);
            if (dirLen > 1e-6) {
                double phase = Math.max(0.0, Math.min(1.0, parent.dyingTimerSeconds() / detachAt));
                double seam = seamSeparationDistance(profileFor(parent), parent.radius) * Math.pow(phase, 1.15);
                offsetX += (localX / dirLen) * seam;
                offsetY += (localY / dirLen) * seam;
            }
        }
        double cos = Math.cos(parent.angle);
        double sin = Math.sin(parent.angle);
        x = parent.x + offsetX * cos - offsetY * sin;
        y = parent.y + offsetX * sin + offsetY * cos;
        angle = parent.angle + localAngle;
    }

    private boolean isVisible(double minX, double minY, double maxX, double maxY) {
        double halfW = drawHalfWidth();
        double halfH = drawHalfHeight();
        return x + halfW >= minX && x - halfW <= maxX && y + halfH >= minY && y - halfH <= maxY;
    }

    private double drawHalfWidth() {
        double minHalfSize = multipart ? 10.0 : (breach ? 12.0 : 16.0);
        return Math.max(minHalfSize, baseHalfWidth * scale);
    }

    private double drawHalfHeight() {
        double minHalfSize = multipart ? 10.0 : (breach ? 12.0 : 16.0);
        return Math.max(minHalfSize, baseHalfHeight * scale);
    }

    private static Profile profileFor(Ship ship) {
        if (ship == null || ship.role == null) return Profile.MEDIUM;
        if (ship.role.isTitanOrMothership()) return Profile.LARGE;
        return switch (ship.role) {
            case FIGHTER, BOMBER, DRONE -> Profile.SMALL;
            case CARRIER, DRONE_CARRIER, BATTLESHIP, DREADNOUGHT, SUPERSHIP, STEALTH_SHIP -> Profile.LARGE;
            case BASE -> Profile.STATION;
            default -> Profile.MEDIUM;
        };
    }

    private static int maxChunksFor(Profile profile) {
        return switch (profile) {
            case SMALL -> 2;
            case MEDIUM -> 3;
            case LARGE -> 4;
            case STATION -> 5;
        };
    }

    private static int maxBreachesFor(Profile profile) {
        return switch (profile) {
            case SMALL -> 0;
            case MEDIUM -> 1;
            case LARGE -> 2;
            case STATION -> 2;
        };
    }

    private static double spanMultiplier(Profile profile) {
        return switch (profile) {
            case SMALL -> 0.66;
            case MEDIUM -> 0.80;
            case LARGE -> 0.90;
            case STATION -> 1.02;
        };
    }

    private static double chunkBaseSizeMultiplier(Profile profile) {
        return switch (profile) {
            case SMALL -> 0.72;
            case MEDIUM -> 0.88;
            case LARGE -> 0.96;
            case STATION -> 1.04;
        };
    }

    private static double breachBaseSizeMultiplier(Profile profile) {
        return switch (profile) {
            case SMALL -> 0.0;
            case MEDIUM -> 0.76;
            case LARGE -> 0.84;
            case STATION -> 0.92;
        };
    }

    private static double minLifeFor(Profile profile) {
        return switch (profile) {
            case SMALL -> 1.9;
            case MEDIUM -> 2.7;
            case LARGE -> 3.4;
            case STATION -> 4.1;
        };
    }

    private static double extraLifeFor(Profile profile) {
        return switch (profile) {
            case SMALL -> 0.9;
            case MEDIUM -> 1.6;
            case LARGE -> 2.4;
            case STATION -> 3.0;
        };
    }

    private static double chunkDetachPhase(Profile profile, double t) {
        return switch (profile) {
            case SMALL -> 0.10 + t * 0.22;
            case MEDIUM -> 0.50 + t * 0.24;
            case LARGE -> 0.58 + t * 0.25;
            case STATION -> 0.64 + t * 0.22;
        };
    }

    private static double breachDetachPhase(Profile profile, double t) {
        return switch (profile) {
            case SMALL -> 0.0;
            case MEDIUM -> 0.18 + t * 0.10;
            case LARGE -> 0.14 + t * 0.16;
            case STATION -> 0.10 + t * 0.18;
        };
    }

    private static double chunkScale(Profile profile, double t) {
        return switch (profile) {
            case SMALL -> 0.86 + t * 0.12;
            case MEDIUM -> 0.92 + t * 0.12;
            case LARGE -> 0.98 + t * 0.12;
            case STATION -> 1.04 + t * 0.12;
        };
    }

    private static double breachScale(Profile profile, double t) {
        return switch (profile) {
            case SMALL -> 0.0;
            case MEDIUM -> 0.84 + t * 0.08;
            case LARGE -> 0.92 + t * 0.08;
            case STATION -> 0.98 + t * 0.08;
        };
    }

    private static double chunkSizeJitter(Profile profile, double t) {
        return switch (profile) {
            case SMALL -> 0.88 + t * 0.08;
            case MEDIUM -> 0.92 + t * 0.10;
            case LARGE -> 0.96 + t * 0.12;
            case STATION -> 1.00 + t * 0.12;
        };
    }

    private static double chunkSpinMultiplier(Profile profile) {
        return switch (profile) {
            case SMALL -> 0.34;
            case MEDIUM -> 0.22;
            case LARGE -> 0.18;
            case STATION -> 0.14;
        };
    }

    private static double breachSpinMultiplier(Profile profile) {
        return switch (profile) {
            case SMALL -> 0.0;
            case MEDIUM -> 0.08;
            case LARGE -> 0.06;
            case STATION -> 0.05;
        };
    }

    private static double chunkBurst(Profile profile, double radius, double t) {
        return switch (profile) {
            case SMALL -> 5.0 + radius * 0.03 + t * 0.8;
            case MEDIUM -> 3.4 + radius * 0.018 + t * 0.7;
            case LARGE -> 2.6 + radius * 0.012 + t * 0.6;
            case STATION -> 2.0 + radius * 0.010 + t * 0.5;
        };
    }

    private static double breachBurst(Profile profile, double radius, double t) {
        return switch (profile) {
            case SMALL -> 0.0;
            case MEDIUM -> 1.1 + radius * 0.008 + t * 0.30;
            case LARGE -> 1.5 + radius * 0.010 + t * 0.35;
            case STATION -> 1.7 + radius * 0.010 + t * 0.40;
        };
    }

    private static double chunkLocalAngleMultiplier(Profile profile) {
        return switch (profile) {
            case SMALL -> 0.22;
            case MEDIUM -> 0.18;
            case LARGE -> 0.16;
            case STATION -> 0.14;
        };
    }

    private static double chunkLocalYOffset(Profile profile, int index, double t, double span) {
        double polarity = ((index & 1) == 0) ? -1.0 : 1.0;
        return switch (profile) {
            case SMALL -> polarity * span * (0.08 + t * 0.07);
            case MEDIUM -> polarity * span * (0.09 + t * 0.10);
            case LARGE -> polarity * span * (0.10 + t * 0.12);
            case STATION -> polarity * span * (0.12 + t * 0.12);
        };
    }

    private static double breachLocalYOffset(Profile profile, int index, double span) {
        return switch (profile) {
            case SMALL -> 0.0;
            case MEDIUM -> -0.03 * span;
            case LARGE -> (index == 0) ? -0.05 * span : 0.05 * span;
            case STATION -> (index == 0) ? -0.07 * span : 0.07 * span;
        };
    }

    private static double seamSeparationDistance(Profile profile, double radius) {
        return switch (profile) {
            case SMALL -> Math.max(6.0, radius * 0.22);
            case MEDIUM -> Math.max(10.0, radius * 0.28);
            case LARGE -> Math.max(18.0, radius * 0.36);
            case STATION -> Math.max(24.0, radius * 0.42);
        };
    }

    private void draw(Graphics2D g2, double screenScale) {
        double halfW = drawHalfWidth();
        double halfH = drawHalfHeight();
        double alpha = multipart ? 1.0 : (attached ? 1.0 : Math.max(0.0, Math.min(1.0, life / maxLife)));
        if (breach) {
            alpha *= attached ? 0.96 : 0.88;
        }
        if (alpha <= 0.01) return;
        double screenSpan = Math.max(halfW, halfH) * 2.0 * Math.max(0.01, screenScale);
        boolean primitiveFallback = screenSpan <= WRECK_PRIMITIVE_MAX_SCREEN_SPAN;
        boolean lightFxOnly = screenSpan <= WRECK_LIGHT_FX_MAX_SCREEN_SPAN;

        AffineTransform old = g2.getTransform();
        java.awt.Composite oldComposite = g2.getComposite();
        try {
            g2.translate(x, y);
            g2.rotate(angle);
            if (primitiveFallback) {
                drawPrimitiveFallback(g2, halfW, halfH, alpha);
                return;
            }
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
            if (mirrorX || mirrorY) {
                g2.scale(mirrorX ? -1.0 : 1.0, mirrorY ? -1.0 : 1.0);
            }
            g2.drawImage(image, (int) Math.round(-halfW), (int) Math.round(-halfH),
                    (int) Math.round(halfW * 2.0), (int) Math.round(halfH * 2.0), null);
            if (!lightFxOnly && multipart && !bakedDamageVisuals) {
                if (attached) {
                    drawMultipartDamageDress(g2, halfW, halfH, alpha);
                } else {
                    drawMultipartSecondaryScars(g2);
                }
            } else if (!lightFxOnly && multipart && bakedDamageVisuals) {
                drawDirectionalKillDress(g2, halfW, halfH, alpha);
            }
            if (!lightFxOnly && breach) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (alpha * 0.32)));
                g2.setColor(new Color(255, 180, 90, 100));
                double glowR = Math.max(halfW, halfH) * 0.36;
                g2.fillOval((int) Math.round(-glowR), (int) Math.round(-glowR),
                        (int) Math.round(glowR * 2.0), (int) Math.round(glowR * 2.0));
            }
        } finally {
            g2.setTransform(old);
            g2.setComposite(oldComposite);
        }
    }

    private void drawPrimitiveFallback(Graphics2D g2, double halfW, double halfH, double alpha) {
        java.awt.Composite oldComposite = g2.getComposite();
        Color oldColor = g2.getColor();
        try {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
            int w = Math.max(2, (int) Math.round(halfW * 2.0));
            int h = Math.max(2, (int) Math.round(halfH * 2.0));
            if (breach) {
                g2.setColor(new Color(72, 62, 58, 210));
                g2.fillOval(-w / 2, -h / 2, w, h);
                g2.setColor(new Color(255, 168, 88, 110));
                int glowW = Math.max(2, (int) Math.round(w * 0.55));
                int glowH = Math.max(2, (int) Math.round(h * 0.55));
                g2.fillOval(-glowW / 2, -glowH / 2, glowW, glowH);
            } else {
                g2.setColor(multipart ? new Color(88, 92, 98, 220) : new Color(66, 70, 78, 210));
                g2.fillRect(-w / 2, -h / 2, w, h);
                g2.setColor(new Color(18, 20, 24, 130));
                g2.drawRect(-w / 2, -h / 2, w, h);
            }
        } finally {
            g2.setComposite(oldComposite);
            g2.setColor(oldColor);
        }
    }

    private static double screenScale(Graphics2D g2) {
        if (g2 == null) return 1.0;
        AffineTransform tx = g2.getTransform();
        double sx = Math.hypot(tx.getScaleX(), tx.getShearX());
        double sy = Math.hypot(tx.getScaleY(), tx.getShearY());
        double scale = Math.max(Math.abs(sx), Math.abs(sy));
        if (!Double.isFinite(scale) || scale <= 1e-6) return 1.0;
        return scale;
    }

    private void drawMultipartDamageDress(Graphics2D g2, double halfW, double halfH, double alpha) {
        double damageFrac = multipartDamageFrac();
        if (damageFrac <= 0.02) return;

        java.awt.Composite oldComposite = g2.getComposite();
        try {
            drawMultipartRoomDamage(g2, halfW, halfH, alpha, damageFrac);
            drawMultipartImpactMarks(g2, halfW, halfH, alpha, damageFrac);
            drawMultipartSecondaryScars(g2);
        } finally {
            g2.setComposite(oldComposite);
        }
    }

    private void drawMultipartRoomDamage(Graphics2D g2, double halfW, double halfH, double alpha, double damageFrac) {
        List<Ship.RoomStatus> rooms = (deathSnapshot == null) ? List.of() : deathSnapshot.roomStatuses;
        if (rooms == null || rooms.isEmpty()) return;

        Stroke oldStroke = g2.getStroke();
        try {
            for (Ship.RoomStatus room : rooms) {
                if (room == null || room.roomId == null || room.hpMax <= 1e-9) continue;
                double roomFrac = MathUtil.clamp(room.hp / room.hpMax, 0.0, 1.0);
                double fire = Math.max(0.0, room.fireIntensity);
                boolean disrupted = room.disrupted;
                if (roomFrac > 0.995 && fire <= 0.03 && !disrupted) continue;

                Polygon poly = multipartRoomPolygon(room, halfW, halfH);
                if (poly == null || poly.npoints < 3) continue;

                double roomDamage = Math.max(0.0, 1.0 - roomFrac);
                int baseAlpha = MathUtil.clamp((int) Math.round(40 + roomDamage * 148), 0, 220);
                Color tint = roomTraceTint(room.roomId, MathUtil.clamp(baseAlpha + 24, 0, 210));
                boolean destroyed = roomFrac <= 0.05;
                boolean criticalDamage = roomFrac <= 0.30;
                Color roomFill = destroyed
                        ? new Color(0, 0, 0, MathUtil.clamp((int) Math.round(168 + roomDamage * 56), 0, 240))
                        : criticalDamage
                        ? new Color(10, 10, 12, MathUtil.clamp((int) Math.round(104 + roomDamage * 62), 0, 220))
                        : new Color(
                        MathUtil.clamp((int) Math.round((16 + tint.getRed()) * 0.38), 0, 255),
                        MathUtil.clamp((int) Math.round((18 + tint.getGreen()) * 0.35), 0, 255),
                        MathUtil.clamp((int) Math.round((22 + tint.getBlue()) * 0.34), 0, 255),
                        baseAlpha);
                Color edgeTint = destroyed
                        ? new Color(46, 46, 50, MathUtil.clamp((int) Math.round(120 + roomDamage * 36), 0, 210))
                        : criticalDamage
                        ? new Color(70, 66, 64, MathUtil.clamp((int) Math.round(110 + roomDamage * 36), 0, 200))
                        : roomTraceTint(room.roomId, MathUtil.clamp((int) Math.round(70 + roomDamage * 70 + fire * 48), 0, 190));

                float fillAlpha = (float) (destroyed
                        ? Math.min(0.78, 0.44 + roomDamage * 0.26)
                        : Math.min(0.58, 0.12 + roomDamage * 0.26 + damageFrac * 0.10));
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, fillAlpha));
                g2.setColor(roomFill);
                g2.fillPolygon(poly);

                if (roomDamage > 0.18) {
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP,
                            (float) Math.min(0.46, destroyed ? 0.22 + roomDamage * 0.18 : 0.08 + roomDamage * 0.18)));
                    g2.setColor(destroyed
                            ? new Color(0, 0, 0, MathUtil.clamp((int) Math.round(162 + roomDamage * 52), 0, 235))
                            : new Color(18, 12, 12, MathUtil.clamp((int) Math.round(68 + roomDamage * 92), 0, 180)));
                    g2.fillOval((int) Math.round(poly.getBounds2D().getCenterX() - poly.getBounds2D().getWidth() * 0.18),
                            (int) Math.round(poly.getBounds2D().getCenterY() - poly.getBounds2D().getHeight() * 0.16),
                            (int) Math.round(Math.max(4.0, poly.getBounds2D().getWidth() * 0.36)),
                            (int) Math.round(Math.max(4.0, poly.getBounds2D().getHeight() * 0.32)));
                }

                if (destroyed) {
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP,
                            (float) Math.min(0.52, 0.20 + roomDamage * 0.18)));
                    g2.setColor(new Color(0, 0, 0, MathUtil.clamp((int) Math.round(172 + roomDamage * 38), 0, 235)));
                    g2.setStroke(new BasicStroke((float) Math.max(1.6, Math.min(3.6, Math.min(halfW, halfH) * 0.08)),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawPolygon(poly);

                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP,
                            (float) Math.min(0.30, 0.10 + roomDamage * 0.12)));
                    g2.setColor(new Color(84, 84, 88, MathUtil.clamp((int) Math.round(66 + roomDamage * 36), 0, 150)));
                    g2.setStroke(new BasicStroke((float) Math.max(0.8, Math.min(1.8, Math.min(halfW, halfH) * 0.04)),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawPolygon(poly);
                }

                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP,
                        (float) Math.min(0.30, 0.06 + roomDamage * 0.12 + fire * 0.08)));
                g2.setStroke(new BasicStroke((float) Math.max(1.0, Math.min(2.4, Math.min(halfW, halfH) * 0.05)),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(edgeTint);
                g2.drawPolygon(poly);
            }
        } finally {
            g2.setStroke(oldStroke);
        }
    }

    private Polygon multipartRoomPolygon(Ship.RoomStatus room, double halfW, double halfH) {
        if (room == null || room.normalizedXs == null || room.normalizedYs == null || parent == null) return null;
        int n = Math.min(room.normalizedXs.length, room.normalizedYs.length);
        if (n < 3) return null;
        int[] xs = new int[n];
        int[] ys = new int[n];
        boolean anyInside = false;
        double expandX = Math.max(16.0, halfW * 0.30);
        double expandY = Math.max(16.0, halfH * 0.30);
        for (int i = 0; i < n; i++) {
            double px = room.normalizedXs[i] * parent.radius - localX;
            double py = room.normalizedYs[i] * parent.radius - localY;
            xs[i] = (int) Math.round(px);
            ys[i] = (int) Math.round(py);
            if (px >= -halfW - expandX && px <= halfW + expandX && py >= -halfH - expandY && py <= halfH + expandY) {
                anyInside = true;
            }
        }
        if (!anyInside) return null;
        return new Polygon(xs, ys, n);
    }

    private void drawMultipartImpactMarks(Graphics2D g2, double halfW, double halfH, double alpha, double damageFrac) {
        List<Ship.HullImpactMark> marks = (deathSnapshot == null) ? List.of() : deathSnapshot.hullImpactMarks;
        if (marks == null || marks.isEmpty()) return;

        double minX = localX - halfW;
        double maxX = localX + halfW;
        double minY = localY - halfH;
        double maxY = localY + halfH;
        int start = Math.max(0, marks.size() - 18);

        for (int i = start; i < marks.size(); i++) {
            Ship.HullImpactMark mark = marks.get(i);
            if (mark == null) continue;
            if (mark.localX < minX || mark.localX > maxX || mark.localY < minY || mark.localY > maxY) continue;

            double px = mark.localX - localX;
            double py = mark.localY - localY;
            double sev = MathUtil.clamp(mark.severity, 0.05, 1.0);
            double scorchR = Math.max(2.0, 2.5 + sev * 6.0 + damageFrac * 2.5);

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    (float) Math.min(0.42, 0.16 + sev * 0.18 + damageFrac * 0.10)));
            g2.setColor(new Color(10, 10, 12, 220));
            g2.fillOval((int) Math.round(px - scorchR), (int) Math.round(py - scorchR),
                    (int) Math.round(scorchR * 2.0), (int) Math.round(scorchR * 2.0));

            if (mark.breachRadius > 0.01) {
                double holeR = Math.max(1.5, mark.breachRadius * 0.55);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        (float) Math.min(0.70, 0.30 + sev * 0.25)));
                g2.setColor(new Color(12, 8, 8, 235));
                g2.fillOval((int) Math.round(px - holeR), (int) Math.round(py - holeR),
                        (int) Math.round(holeR * 2.0), (int) Math.round(holeR * 2.0));
            }
        }

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                (float) Math.min(0.20, 0.06 + damageFrac * 0.08)));
        g2.setColor(new Color(255, 156, 92, 140));
        int seamW = (int) Math.round(Math.max(3.0, halfW * 0.10));
        int seamH = (int) Math.round(Math.max(6.0, halfH * 0.58));
        g2.fillRoundRect((int) Math.round(-seamW * 0.4), (int) Math.round(-halfH * 0.28),
                seamW, seamH, 8, 8);
    }

    private void drawMultipartSecondaryScars(Graphics2D g2) {
        if (!multipart || secondaryScars.isEmpty()) return;
        for (SecondaryScar scar : secondaryScars) {
            if (scar == null) continue;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.34f));
            g2.setColor(new Color(48, 18, 12, 160));
            g2.fillOval((int) Math.round(scar.localX - scar.glow), (int) Math.round(scar.localY - scar.glow),
                    (int) Math.round(scar.glow * 2.0), (int) Math.round(scar.glow * 2.0));

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.58f));
            g2.setColor(new Color(10, 10, 12, 228));
            g2.fillOval((int) Math.round(scar.localX - scar.radius), (int) Math.round(scar.localY - scar.radius),
                    (int) Math.round(scar.radius * 2.0), (int) Math.round(scar.radius * 2.0));

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.76f));
            g2.setColor(new Color(0, 0, 0, 240));
            g2.fillOval((int) Math.round(scar.localX - scar.innerRadius), (int) Math.round(scar.localY - scar.innerRadius),
                    (int) Math.round(scar.innerRadius * 2.0), (int) Math.round(scar.innerRadius * 2.0));

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.28f));
            g2.setColor(new Color(112, 86, 72, 120));
            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawOval((int) Math.round(scar.localX - scar.radius), (int) Math.round(scar.localY - scar.radius),
                    (int) Math.round(scar.radius * 2.0), (int) Math.round(scar.radius * 2.0));
        }
    }

    private double multipartDamageFrac() {
        if (!multipart || parent == null || parent.hpMax <= 0) return 0.0;
        double frac = 1.0 - parent.hp / (double) parent.hpMax;
        return Math.max(0.0, Math.min(1.0, frac));
    }

    private void configureSecondaryPops(Profile profile, double t) {
        if (!multipart) return;
        int baseCount = switch (profile) {
            case SMALL -> 0;
            case MEDIUM -> 1;
            case LARGE -> 1 + ((t > 0.55) ? 1 : 0);
            case STATION -> 2;
        };
        secondaryPopsRemaining = Math.max(0, baseCount);
        if (secondaryPopsRemaining > 0) {
            nextSecondaryPopAt = maxLife * (0.78 - Math.min(0.22, t * 0.16));
        }
    }

    private void drawDirectionalKillDress(Graphics2D g2, double halfW, double halfH, double alpha) {
        if (!multipart || !bakedDamageVisuals || directionalDamageBias <= 0.06) return;

        double hitX = Math.max(-halfW * 0.72, Math.min(halfW * 0.72, visualLocalX(directionalLocalHitX)));
        double hitY = Math.max(-halfH * 0.72, Math.min(halfH * 0.72, visualLocalY(directionalLocalHitY)));
        double span = Math.max(6.0, Math.min(halfW, halfH));

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP,
                (float) Math.min(0.42, 0.14 + directionalDamageBias * 0.24)));
        g2.setColor(new Color(0, 0, 0, 190));
        double sootW = span * (1.10 + directionalDamageBias * 0.70);
        double sootH = span * (0.84 + directionalDamageBias * 0.48);
        g2.fillOval((int) Math.round(hitX - sootW * 0.5), (int) Math.round(hitY - sootH * 0.5),
                (int) Math.round(sootW), (int) Math.round(sootH));

        int holes = (directionalDamageBias >= 0.65) ? 3 : (directionalDamageBias >= 0.32 ? 2 : 1);
        for (int i = 0; i < holes; i++) {
            double offset = (i - (holes - 1) * 0.5) * span * 0.34;
            double hx = hitX + offset;
            double hy = hitY + ((i % 2 == 0) ? -span * 0.16 : span * 0.16);
            double holeR = span * (0.20 + directionalDamageBias * 0.16 - i * 0.025);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP,
                    (float) Math.min(0.82, 0.32 + directionalDamageBias * 0.34)));
            g2.setColor(new Color(0, 0, 0, 228));
            g2.fillOval((int) Math.round(hx - holeR), (int) Math.round(hy - holeR),
                    (int) Math.round(holeR * 2.0), (int) Math.round(holeR * 2.0));
        }
    }

    private double visualLocalX(double rawX) {
        return mirrorX ? -rawX : rawX;
    }

    private double visualLocalY(double rawY) {
        return mirrorY ? -rawY : rawY;
    }

    private void maybeTriggerSecondaryPop() {
        if (!multipart || attached || secondaryPopsRemaining <= 0 || !Double.isFinite(nextSecondaryPopAt)) return;
        if (life > nextSecondaryPopAt) return;

        double padX = Math.max(6.0, drawHalfWidth() * 0.22);
        double padY = Math.max(6.0, drawHalfHeight() * 0.22);
        double localHitX = (Ship.randomUnit() * 2.0 - 1.0) * Math.max(4.0, drawHalfWidth() - padX);
        double localHitY = (Ship.randomUnit() * 2.0 - 1.0) * Math.max(4.0, drawHalfHeight() - padY);
        double radius = Math.max(2.5, Math.min(drawHalfWidth(), drawHalfHeight()) * (0.08 + Ship.randomUnit() * 0.05));
        double innerRadius = Math.max(1.3, radius * (0.46 + Ship.randomUnit() * 0.12));
        double glow = radius * (1.6 + Ship.randomUnit() * 0.35);
        if (secondaryScars.size() >= MAX_SECONDARY_SCARS) {
            secondaryScars.remove(0);
        }
        secondaryScars.add(new SecondaryScar(localHitX, localHitY, radius, innerRadius, glow));

        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double wx = x + localHitX * cos - localHitY * sin;
        double wy = y + localHitX * sin + localHitY * cos;
        VFX.spawnImpactSparks(wx, wy, Math.cos(angle), Math.sin(angle), 3);

        secondaryPopsRemaining--;
        if (secondaryPopsRemaining > 0) {
            double interval = Math.max(0.18, maxLife * (0.10 + Ship.randomUnit() * 0.06));
            nextSecondaryPopAt = Math.max(0.08, life - interval);
        } else {
            nextSecondaryPopAt = Double.NEGATIVE_INFINITY;
        }
    }

    private static void seedInitialAmidshipBlast(Ship ship, Profile profile) {
        if (ship == null) return;

        double blastX = initialBlastX(profile, ship.radius);
        double blastY = initialBlastY(profile, ship.radius);
        double blastRadius = initialBlastRadius(profile, ship.radius);
        double innerRadius = blastRadius * 0.58;
        double glow = blastRadius * 1.9;

        boolean scarredAny = false;
        for (WreckChunk chunk : ACTIVE) {
            if (chunk == null || chunk.parent != ship || !chunk.multipart) continue;
            if (blastX < chunk.localX - chunk.drawHalfWidth() || blastX > chunk.localX + chunk.drawHalfWidth()
                    || blastY < chunk.localY - chunk.drawHalfHeight() || blastY > chunk.localY + chunk.drawHalfHeight()) {
                continue;
            }
            double localHitX = blastX - chunk.localX;
            double localHitY = blastY - chunk.localY;
            chunk.addSecondaryScar(localHitX, localHitY, blastRadius, innerRadius, glow);
            scarredAny = true;
        }

        if (scarredAny) {
            double cos = Math.cos(ship.angle);
            double sin = Math.sin(ship.angle);
            double wx = ship.x + blastX * cos - blastY * sin;
            double wy = ship.y + blastX * sin + blastY * cos;
            Explosion.spawnDeath(wx, wy);
            VFX.spawnImpactSparks(wx, wy, Math.cos(ship.angle), Math.sin(ship.angle), 10);
        }
    }

    private static MultipartFinaleState beginMultipartFinale(Ship ship) {
        if (ship == null) return null;
        MultipartFinaleState existing = MULTIPART_FINALES.get(ship);
        if (existing != null) return existing;

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double sumX = 0.0;
        double sumY = 0.0;
        int count = 0;
        for (WreckChunk chunk : ACTIVE) {
            if (chunk == null || !chunk.multipart || chunk.parent != ship) continue;
            double halfW = chunk.drawHalfWidth();
            double halfH = chunk.drawHalfHeight();
            minX = Math.min(minX, chunk.x - halfW);
            minY = Math.min(minY, chunk.y - halfH);
            maxX = Math.max(maxX, chunk.x + halfW);
            maxY = Math.max(maxY, chunk.y + halfH);
            sumX += chunk.x;
            sumY += chunk.y;
            count++;
        }
        if (count <= 0) return null;

        double centerX = sumX / count;
        double centerY = sumY / count;
        double radius = Math.max(42.0, Math.max(maxX - minX, maxY - minY) * 0.62);
        double cleanupDelay = 0.42;
        MultipartFinaleState state = new MultipartFinaleState(centerX, centerY, radius, cleanupDelay);
        MULTIPART_FINALES.put(ship, state);
        Explosion.spawnFinalDetonation(centerX, centerY, radius);
        ScreenShake.kick(Math.min(18.0, 6.0 + radius * 0.03));
        return state;
    }

    private void addSecondaryScar(double localHitX, double localHitY, double radius, double innerRadius, double glow) {
        if (!multipart) return;
        if (secondaryScars.size() >= MAX_SECONDARY_SCARS) {
            secondaryScars.remove(0);
        }
        secondaryScars.add(new SecondaryScar(localHitX, localHitY, radius, innerRadius, glow));
    }

    private static double initialBlastX(Profile profile, double radius) {
        return switch (profile) {
            case SMALL -> radius * -0.04;
            case MEDIUM -> radius * -0.02;
            case LARGE -> radius * -0.06;
            case STATION -> 0.0;
        };
    }

    private static double initialBlastY(Profile profile, double radius) {
        return switch (profile) {
            case SMALL -> 0.0;
            case MEDIUM -> radius * 0.01;
            case LARGE -> radius * 0.015;
            case STATION -> 0.0;
        };
    }

    private static double initialBlastRadius(Profile profile, double radius) {
        return switch (profile) {
            case SMALL -> Math.max(5.0, radius * 0.18);
            case MEDIUM -> Math.max(8.0, radius * 0.24);
            case LARGE -> Math.max(12.0, radius * 0.30);
            case STATION -> Math.max(16.0, radius * 0.28);
        };
    }

    private static double visualScaleForRole(ShipRole role) {
        if (role == null) return 1.0;
        return switch (role) {
            case FIGHTER -> 0.16;
            case BOMBER -> 0.17;
            case DRONE -> 0.20;
            default -> HullGeometry.roleVisualScale(role);
        };
    }

    private static Color roomTraceTint(ShipRoomLayout.RoomId roomId, int alpha) {
        int a = MathUtil.clamp(alpha, 0, 255);
        if (roomId == null) return new Color(255, 178, 105, a);
        if (ShipRoomLayout.isShieldStripRoom(roomId)) return new Color(124, 214, 255, a);
        if (ShipRoomLayout.isArmorRoom(roomId)) return new Color(210, 224, 236, a);
        if (ShipRoomLayout.isPowerRoom(roomId)) return new Color(255, 198, 112, a);
        if (ShipRoomLayout.isWeaponRoom(roomId)) return new Color(255, 164, 94, a);
        if (ShipRoomLayout.isMagazineRoom(roomId)) return new Color(255, 96, 86, a);
        if (ShipRoomLayout.isShieldRoom(roomId)) return new Color(178, 166, 255, a);
        if (ShipRoomLayout.isEngineRoom(roomId) || ShipRoomLayout.isWarpRoom(roomId) || roomId == ShipRoomLayout.RoomId.AFT_SPINE) {
            return new Color((roomId == ShipRoomLayout.RoomId.WARP_DRIVE) ? 144 : 130,
                    (roomId == ShipRoomLayout.RoomId.WARP_DRIVE) ? 186 : 208,
                    255,
                    a);
        }
        if (roomId == ShipRoomLayout.RoomId.SENSORS) return new Color(132, 238, 226, a);
        if (roomId == ShipRoomLayout.RoomId.BRIDGE || roomId == ShipRoomLayout.RoomId.BOW) return new Color(255, 214, 138, a);
        return new Color(200, 214, 230, a);
    }

    private static DeathVisualSnapshot captureDeathVisualSnapshot(Ship ship) {
        if (ship == null) return new DeathVisualSnapshot(List.of(), List.of(), 0.0, 0.0, 0.0);
        List<Ship.RoomStatus> roomStatuses = ship.roomStatusSnapshot();
        List<Ship.HullImpactMark> marks = ship.hullImpactMarks();
        int start = Math.max(0, marks.size() - 18);
        List<Ship.HullImpactMark> recentMarks = marks.subList(start, marks.size());
        double weightedX = 0.0;
        double weightedY = 0.0;
        double totalWeight = 0.0;
        for (int i = 0; i < recentMarks.size(); i++) {
            Ship.HullImpactMark mark = recentMarks.get(i);
            if (mark == null) continue;
            double recency = 0.50 + 0.50 * ((i + 1.0) / Math.max(1.0, recentMarks.size()));
            double weight = recency * (0.35 + mark.severity * 1.15 + mark.breachRadius * 0.028);
            weightedX += mark.localX * weight;
            weightedY += mark.localY * weight;
            totalWeight += weight;
        }
        double impactCenterX = 0.0;
        double impactCenterY = 0.0;
        double impactFocus = 0.0;
        if (totalWeight > 1e-6 && ship.radius > 1e-6) {
            impactCenterX = weightedX / totalWeight;
            impactCenterY = weightedY / totalWeight;
            double reach = Math.hypot(impactCenterX, impactCenterY);
            impactFocus = Math.max(0.18, Math.min(1.0, reach / Math.max(12.0, ship.radius * 0.34)));
        }
        return new DeathVisualSnapshot(roomStatuses, recentMarks, impactCenterX, impactCenterY, impactFocus);
    }
}

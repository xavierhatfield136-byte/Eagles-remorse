import java.util.EnumMap;

public final class CrewStationsSystem {
    private CrewStationsSystem() {}

    private static final class EngineeringPolicy {
        final Ship.PowerPreset preset;
        final Ship.CrewOrder crewOrder;
        final Ship.EngineeringPriority priority;
        final boolean overload;

        EngineeringPolicy(Ship.PowerPreset preset,
                          Ship.CrewOrder crewOrder,
                          Ship.EngineeringPriority priority,
                          boolean overload) {
            this.preset = preset;
            this.crewOrder = crewOrder;
            this.priority = priority;
            this.overload = overload;
        }
    }

    private static final EnumMap<GameContext.EngineeringMode, EngineeringPolicy> ENGINEERING_POLICY =
            new EnumMap<>(GameContext.EngineeringMode.class);

    static {
        ENGINEERING_POLICY.put(GameContext.EngineeringMode.BALANCED,
                new EngineeringPolicy(Ship.PowerPreset.BALANCED, Ship.CrewOrder.BALANCED, Ship.EngineeringPriority.BALANCED, false));
        ENGINEERING_POLICY.put(GameContext.EngineeringMode.ATTACK,
                new EngineeringPolicy(Ship.PowerPreset.ATTACK, Ship.CrewOrder.GUNNERY, Ship.EngineeringPriority.WEAPONS, true));
        ENGINEERING_POLICY.put(GameContext.EngineeringMode.DEFENSE,
                new EngineeringPolicy(Ship.PowerPreset.DEFENSE, Ship.CrewOrder.ENGINEERING, Ship.EngineeringPriority.SHIELDS, false));
        ENGINEERING_POLICY.put(GameContext.EngineeringMode.DAMAGE_CONTROL,
                new EngineeringPolicy(Ship.PowerPreset.DEFENSE, Ship.CrewOrder.DAMAGE_CONTROL, Ship.EngineeringPriority.REACTOR, false));
    }

    public static boolean updatePlayerAutomation(GameContext ctx, InputSnapshot snap, double dt) {
        if (ctx == null || ctx.player == null) return false;
        Player p = ctx.player;
        if (!p.alive || p.dying || p.hp <= 0) return false;
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER || ctx.gameOver) return false;
        ctx.command.miningAuto = false;

        InputSnapshot s = (snap == null)
                ? new InputSnapshot(false, false, false, false, false, 0, 0)
                : snap;
        boolean manualHelm = s.up || s.down || s.left || s.right;
        if (manualHelm) ctx.command.helmAutomation = false;
        if (ctx.firingPrimaryManual || ctx.firingSecondaryManual) ctx.command.tacticalAutomation = false;

        if (ctx.command.playerPowerManualOverride) {
            ctx.command.engineeringAutomation = false;
        }

        if (ctx.command.captainAutomation) applyCaptainDirectives(ctx);
        GameContext.CaptainDirective directive = (ctx.command.captainDirective == null)
                ? GameContext.CaptainDirective.BALANCED
                : ctx.command.captainDirective;
        boolean captainNavPriority = isCaptainNavigationDirective(directive) && ctx.command.captainAutomation;
        if (ctx.command.scienceAutomation) applyScienceAutomation(ctx);
        if (ctx.command.engineeringAutomation
                && !ctx.command.playerPowerManualOverride
                && (ctx.ui == null || !ctx.ui.powerManagementOpen)) {
            applyEngineeringAutomation(ctx);
        }
        if (ctx.command.tacticalAutomation) applyTacticalAutomation(ctx, captainNavPriority, directive);
        else {
            ctx.firingPrimaryAuto = false;
            ctx.firingSecondaryAuto = false;
        }
        if (ctx.command.helmAutomation) {
            applyHelmAutomation(ctx, dt);
            return true;
        }
        return false;
    }

    private static void applyCaptainDirectives(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        Ship target = preferredTarget(ctx, 2200.0);
        double hpFrac = (ctx.player.hpMax <= 0) ? 1.0 : (ctx.player.hp / (double) ctx.player.hpMax);
        double shieldFrac = (ctx.player.shieldMax <= 0.0) ? 1.0 : (ctx.player.shield / Math.max(1e-9, ctx.player.shieldMax));
        GameContext.CaptainDirective directive = (ctx.command.captainDirective == null)
                ? GameContext.CaptainDirective.BALANCED
                : ctx.command.captainDirective;
        double fireLoad = ctx.player.totalFireIntensity();
        int fireRooms = ctx.player.activeFireRoomCount();

        if (fireRooms >= 2 || fireLoad >= 2.1) {
            if (ctx.command.helmAutomation) ctx.command.helmMode = GameContext.HelmMode.EVASIVE;
            if (ctx.command.tacticalAutomation) ctx.command.tacticalMode = (fireLoad >= 2.8)
                    ? GameContext.TacticalMode.HOLD_FIRE
                    : GameContext.TacticalMode.DEFENSIVE;
            if (ctx.command.engineeringAutomation) ctx.command.engineeringMode = GameContext.EngineeringMode.DAMAGE_CONTROL;
            if (fireRooms >= 3 || fireLoad >= 3.0) {
                ctx.command.alliedFleetCommand = GameContext.FleetCommand.RETREAT;
            }
            return;
        }

        // Preservation-first fallback always takes precedence.
        if (hpFrac < 0.35 || (directive != GameContext.CaptainDirective.ATTACK && shieldFrac < 0.16)) {
            if (ctx.command.helmAutomation) ctx.command.helmMode = GameContext.HelmMode.EVASIVE;
            if (ctx.command.tacticalAutomation) ctx.command.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
            if (ctx.command.engineeringAutomation) ctx.command.engineeringMode = GameContext.EngineeringMode.DAMAGE_CONTROL;
            ctx.command.alliedFleetCommand = GameContext.FleetCommand.RETREAT;
            return;
        }

        switch (directive) {
            case ATTACK -> {
                if (ctx.command.helmAutomation) ctx.command.helmMode = GameContext.HelmMode.INTERCEPT;
                if (ctx.command.tacticalAutomation) ctx.command.tacticalMode = GameContext.TacticalMode.AGGRESSIVE;
                if (ctx.command.engineeringAutomation) ctx.command.engineeringMode = GameContext.EngineeringMode.ATTACK;
                if (target != null) ctx.lockedTarget = target;
                ctx.command.alliedFleetCommand = GameContext.FleetCommand.ATTACK;
            }
            case DEFENSE, DEFEND -> {
                Ship base = TeamSystem.getBaseForTeam(ctx, ctx.player.faction);
                if (base != null) {
                    ctx.ui.waypointX = base.x;
                    ctx.ui.waypointY = base.y;
                    ctx.command.helmDesiredRange = Math.max(280.0, base.radius + 180.0);
                }
                if (ctx.command.helmAutomation) ctx.command.helmMode = GameContext.HelmMode.ORBIT;
                if (ctx.command.tacticalAutomation) ctx.command.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                if (ctx.command.engineeringAutomation) ctx.command.engineeringMode = GameContext.EngineeringMode.DEFENSE;
                // Defense stays anchored to the base perimeter; tactical auto can still engage nearby threats.
                ctx.lockedTarget = null;
                ctx.command.alliedFleetCommand = GameContext.FleetCommand.DEFEND;
            }
            case EMERGENCY -> {
                if (ctx.command.helmAutomation) ctx.command.helmMode = GameContext.HelmMode.EVASIVE;
                if (ctx.command.tacticalAutomation) ctx.command.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                if (ctx.command.engineeringAutomation) ctx.command.engineeringMode = GameContext.EngineeringMode.DAMAGE_CONTROL;
                ctx.command.alliedFleetCommand = GameContext.FleetCommand.RETREAT;
            }
            case MINE -> {
                Asteroid ast = EconomySystem.findBestAsteroidNear(ctx, ctx.player.x, ctx.player.y, 2600.0);
                if (ast != null) {
                    ctx.ui.waypointX = ast.x;
                    ctx.ui.waypointY = ast.y;
                    double md = Math.hypot(ast.x - ctx.player.x, ast.y - ctx.player.y);
                    double mineReach = Math.max(0.0, ctx.player.miningRange) + ctx.player.radius + ast.radius + 22.0;
                    ctx.command.miningAuto = (md <= mineReach);
                }
                if (ctx.command.helmAutomation) ctx.command.helmMode = GameContext.HelmMode.INTERCEPT;
                if (ctx.command.tacticalAutomation) ctx.command.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                if (ctx.command.engineeringAutomation) ctx.command.engineeringMode = GameContext.EngineeringMode.BALANCED;
                ctx.lockedTarget = null;
                ctx.command.alliedFleetCommand = GameContext.FleetCommand.MINE;
            }
            case ESCORT -> {
                Ship escort = pickEscortAnchor(ctx);
                if (escort != null) {
                    ctx.ui.waypointX = escort.x;
                    ctx.ui.waypointY = escort.y;
                    ctx.command.helmDesiredRange = escortFollowRange(ctx.player, escort);
                }
                if (ctx.command.helmAutomation) ctx.command.helmMode = GameContext.HelmMode.ORBIT;
                if (ctx.command.tacticalAutomation) ctx.command.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                if (ctx.command.engineeringAutomation) ctx.command.engineeringMode = GameContext.EngineeringMode.BALANCED;
                ctx.lockedTarget = null;
                ctx.command.alliedFleetCommand = GameContext.FleetCommand.ESCORT;
            }
            case REPAIR -> {
                Ship base = TeamSystem.getBaseForTeam(ctx, ctx.player.faction);
                if (base != null) {
                    ctx.ui.waypointX = base.x;
                    ctx.ui.waypointY = base.y;
                }
                if (ctx.command.helmAutomation) ctx.command.helmMode = GameContext.HelmMode.INTERCEPT;
                if (ctx.command.tacticalAutomation) ctx.command.tacticalMode = GameContext.TacticalMode.HOLD_FIRE;
                if (ctx.command.engineeringAutomation) ctx.command.engineeringMode = GameContext.EngineeringMode.DAMAGE_CONTROL;
                ctx.lockedTarget = null;
                ctx.command.alliedFleetCommand = GameContext.FleetCommand.REPAIR;
            }
            case RTB -> {
                Ship base = TeamSystem.getBaseForTeam(ctx, ctx.player.faction);
                if (base != null) {
                    ctx.ui.waypointX = base.x;
                    ctx.ui.waypointY = base.y;
                }
                if (ctx.command.helmAutomation) ctx.command.helmMode = GameContext.HelmMode.INTERCEPT;
                if (ctx.command.tacticalAutomation) ctx.command.tacticalMode = GameContext.TacticalMode.HOLD_FIRE;
                if (ctx.command.engineeringAutomation) ctx.command.engineeringMode = GameContext.EngineeringMode.DEFENSE;
                ctx.lockedTarget = null;
                ctx.command.alliedFleetCommand = GameContext.FleetCommand.RTB;
            }
            default -> {
                if (shieldFrac < 0.25) {
                    if (ctx.command.helmAutomation) ctx.command.helmMode = GameContext.HelmMode.MAINTAIN_RANGE;
                    if (ctx.command.tacticalAutomation) ctx.command.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                    if (ctx.command.engineeringAutomation) ctx.command.engineeringMode = GameContext.EngineeringMode.DEFENSE;
                    ctx.command.alliedFleetCommand = GameContext.FleetCommand.DEFEND;
                    return;
                }

                if (target != null) {
                    double d = Math.hypot(target.x - ctx.player.x, target.y - ctx.player.y);
                    if (d > 650.0) {
                        if (ctx.command.helmAutomation) ctx.command.helmMode = GameContext.HelmMode.INTERCEPT;
                        if (ctx.command.tacticalAutomation) ctx.command.tacticalMode = GameContext.TacticalMode.AGGRESSIVE;
                        if (ctx.command.engineeringAutomation) ctx.command.engineeringMode = GameContext.EngineeringMode.ATTACK;
                        ctx.command.alliedFleetCommand = GameContext.FleetCommand.ATTACK;
                    } else {
                        if (ctx.command.helmAutomation) ctx.command.helmMode = GameContext.HelmMode.ORBIT;
                        if (ctx.command.tacticalAutomation) ctx.command.tacticalMode = GameContext.TacticalMode.AGGRESSIVE;
                        if (ctx.command.engineeringAutomation) ctx.command.engineeringMode = GameContext.EngineeringMode.BALANCED;
                        ctx.command.alliedFleetCommand = GameContext.FleetCommand.FORM_UP;
                    }
                    ctx.lockedTarget = target;
                } else {
                    ctx.command.alliedFleetCommand = GameContext.FleetCommand.AUTO;
                }
            }
        }
    }

    private static Ship pickEscortFriendly(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.ships == null) return null;
        Ship best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Ship s : ctx.ships) {
            if (s == null || s == ctx.player) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.role == ShipRole.BASE || s.role == ShipRole.STATIC_TURRET) continue;
            if (s.faction == null || !s.faction.isFriendlyTo(ctx.player.faction)) continue;

            double d = Math.hypot(s.x - ctx.player.x, s.y - ctx.player.y);
            double roleScore = escortRoleScore(s.role);
            double hullScore = s.hpMax * 0.12;
            double score = roleScore + hullScore - d * 0.03;
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    private static Ship pickEscortAnchor(GameContext ctx) {
        Ship commandShip = friendlyCommandShip(ctx);
        if (isLiveEscortAnchor(ctx, commandShip) && commandShip != ctx.player) {
            return commandShip;
        }
        return pickEscortFriendly(ctx);
    }

    private static Ship friendlyCommandShip(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.player.faction == null || ctx.command.fleetCommandShips == null) return null;
        Ship direct = ctx.command.fleetCommandShips.get(ctx.player.faction);
        if (isLiveEscortAnchor(ctx, direct)) return direct;
        for (Faction faction : Faction.fourTeamFactions()) {
            if (faction == null || !faction.isFriendlyTo(ctx.player.faction)) continue;
            Ship candidate = ctx.command.fleetCommandShips.get(faction);
            if (isLiveEscortAnchor(ctx, candidate)) return candidate;
        }
        return null;
    }

    private static boolean isLiveEscortAnchor(GameContext ctx, Ship ship) {
        if (ctx == null || ctx.player == null || ship == null) return false;
        if (!ship.alive || ship.dying || ship.hp <= 0) return false;
        if (ship.role == ShipRole.BASE || ship.role == ShipRole.STATIC_TURRET) return false;
        return ship.faction != null && ship.faction.isFriendlyTo(ctx.player.faction);
    }

    private static double escortRoleScore(ShipRole role) {
        if (role == null) return 0.0;
        return switch (role) {
            case MINER, HAULER, TRANSPORT, CARRIER, DRONE_CARRIER -> 140.0;
            case DREADNOUGHT, SUPERSHIP, BATTLESHIP, BATTLECRUISER -> 110.0;
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER -> 85.0;
            default -> 55.0;
        };
    }

    private static void applyScienceAutomation(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        double scanRange = 1800.0 * Math.max(0.20, ctx.player.sensorRangeMultiplier());
        // Science station continuously refreshes lock to the nearest viable contact.
        ctx.lockedTarget = TargetingSystem.findClosestEngagementTarget(
                ctx, ctx.player, ctx.player.x, ctx.player.y, scanRange
        );
    }

    private static void applyEngineeringAutomation(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (ctx.command != null && ctx.command.playerPowerManualOverride) return;
        Player p = ctx.player;

        double hpFrac = (p.hpMax <= 0) ? 1.0 : (p.hp / (double) p.hpMax);
        double fireLoad = p.totalFireIntensity();
        int fireRooms = p.activeFireRoomCount();
        if (hpFrac < 0.30 || fireRooms >= 2 || fireLoad >= 1.7) {
            ctx.command.engineeringMode = GameContext.EngineeringMode.DAMAGE_CONTROL;
        }
        EngineeringPolicy policy = ENGINEERING_POLICY.getOrDefault(
                ctx.command.engineeringMode,
                ENGINEERING_POLICY.get(GameContext.EngineeringMode.BALANCED)
        );
        p.setPowerPreset(policy.preset);
        p.crewOrder = policy.crewOrder;
        p.setEngineeringPriority(policy.priority);
        double closeRange = 600.0 * CampaignSystem.targetingRangeMul(ctx);
        Ship closeTarget = TargetingSystem.findClosestEngagementTarget(ctx, p, p.x, p.y, closeRange);
        boolean shouldOverload = policy.overload
                && closeTarget != null
                && ctx.command.tacticalMode != GameContext.TacticalMode.HOLD_FIRE;
        p.setOverloadMode(shouldOverload);
    }

    private static void applyTacticalAutomation(GameContext ctx, boolean captainNavPriority,
                                                GameContext.CaptainDirective directive) {
        if (ctx == null || ctx.player == null) return;
        Player p = ctx.player;
        double rangeMul = CampaignSystem.targetingRangeMul(ctx);
        double searchRange = 1600.0 * rangeMul;
        Ship target = preferredTarget(ctx, searchRange);
        Ship secondaryTarget = preferredSecondaryTarget(ctx, searchRange);
        if (target == null && secondaryTarget == null) {
            ctx.firingPrimaryAuto = false;
            ctx.firingSecondaryAuto = false;
            return;
        }
        double d = (target == null) ? Double.POSITIVE_INFINITY : Math.hypot(target.x - p.x, target.y - p.y);
        double secondaryDist = (secondaryTarget == null) ? Double.POSITIVE_INFINITY
                : Math.hypot(secondaryTarget.x - p.x, secondaryTarget.y - p.y);
        boolean lockForHelm = !captainNavPriority || directive == GameContext.CaptainDirective.ATTACK
                || directive == GameContext.CaptainDirective.EMERGENCY;
        if (target != null && lockForHelm && ctx.command.tacticalMode != GameContext.TacticalMode.HOLD_FIRE) {
            ctx.lockedTarget = target;
        }

        switch (ctx.command.tacticalMode) {
            case HOLD_FIRE -> {
                ctx.firingPrimaryAuto = false;
                ctx.firingSecondaryAuto = false;
            }
            case DEFENSIVE -> {
                double primaryRange = captainNavPriority ? 620.0 * rangeMul : 900.0 * rangeMul;
                double secondaryRange = captainNavPriority ? 520.0 * rangeMul : 760.0 * rangeMul;
                ctx.firingPrimaryAuto = d <= primaryRange;
                ctx.firingSecondaryAuto = secondaryDist <= secondaryRange;
            }
            case AGGRESSIVE -> {
                double primaryRange = captainNavPriority ? 920.0 * rangeMul : 1500.0 * rangeMul;
                double secondaryRange = captainNavPriority ? 760.0 * rangeMul : 1120.0 * rangeMul;
                ctx.firingPrimaryAuto = d <= primaryRange;
                ctx.firingSecondaryAuto = secondaryDist <= secondaryRange;
            }
        }
    }

    private static void applyHelmAutomation(GameContext ctx, double dt) {
        if (ctx == null || ctx.player == null) return;
        Player p = ctx.player;
        if (dt <= 0.0) {
            p.vx = 0.0;
            p.vy = 0.0;
            return;
        }

        GameContext.CaptainDirective directive = (ctx.command.captainDirective == null)
                ? GameContext.CaptainDirective.BALANCED
                : ctx.command.captainDirective;
        boolean captainNavPriority = ctx.command.captainAutomation && isCaptainNavigationDirective(directive);
        boolean hasWaypoint = Double.isFinite(ctx.ui.waypointX) && Double.isFinite(ctx.ui.waypointY);
        Ship target = preferredTarget(ctx, 2800.0);
        double speed = MovementModel.speedCeiling(p);

        if (directive == GameContext.CaptainDirective.ESCORT && applyEscortHelmAutomation(ctx, p, dt, speed, target)) {
            return;
        }

        switch (ctx.command.helmMode) {
            case INTERCEPT -> {
                if (captainNavPriority && hasWaypoint) {
                    moveToward(p, ctx.ui.waypointX, ctx.ui.waypointY, speed, dt);
                } else if (target != null) {
                    moveToward(p, target.x, target.y, speed, dt);
                } else if (hasWaypoint) {
                    moveToward(p, ctx.ui.waypointX, ctx.ui.waypointY, speed, dt);
                }
                else {
                    p.vx = 0;
                    p.vy = 0;
                }
            }
            case ORBIT -> {
                if (captainNavPriority && hasWaypoint) {
                    orbit(p, ctx.ui.waypointX, ctx.ui.waypointY, Math.max(260.0, ctx.command.helmDesiredRange), speed * 0.92, dt, 1.0);
                } else if (target != null) {
                    orbit(p, target.x, target.y, Math.max(260.0, ctx.command.helmDesiredRange), speed * 0.92, dt, 1.0);
                } else if (hasWaypoint) {
                    moveToward(p, ctx.ui.waypointX, ctx.ui.waypointY, speed, dt);
                }
            }
            case MAINTAIN_RANGE -> {
                double tx;
                double ty;
                if (captainNavPriority && hasWaypoint) {
                    tx = ctx.ui.waypointX;
                    ty = ctx.ui.waypointY;
                } else if (target != null) {
                    tx = target.x;
                    ty = target.y;
                } else if (hasWaypoint) {
                    moveToward(p, ctx.ui.waypointX, ctx.ui.waypointY, speed, dt);
                    break;
                } else {
                    break;
                }
                double desired = Math.max(260.0, ctx.command.helmDesiredRange);
                double dx = tx - p.x;
                double dy = ty - p.y;
                double d = Math.hypot(dx, dy) + 1e-9;
                double ux = dx / d;
                double uy = dy / d;
                if (d > desired + 70.0) {
                    setVelPerSec(p, ux * speed, uy * speed, dt);
                } else if (d < desired - 70.0) {
                    setVelPerSec(p, -ux * speed, -uy * speed, dt);
                } else {
                    setVelPerSec(p, -uy * speed * 0.85, ux * speed * 0.85, dt);
                }
                rotateShipToward(p, Math.atan2(ty - p.y, tx - p.x), dt);
            }
            case EVASIVE -> {
                if (target == null) {
                    if (hasWaypoint) moveToward(p, ctx.ui.waypointX, ctx.ui.waypointY, speed, dt);
                    break;
                }
                if (captainNavPriority && hasWaypoint) {
                    double tx = ctx.ui.waypointX - p.x;
                    double ty = ctx.ui.waypointY - p.y;
                    double tl = Math.hypot(tx, ty) + 1e-9;
                    double ux = tx / tl;
                    double uy = ty / tl;
                    double px = -uy;
                    double py = ux;
                    double weave = Math.sin(System.nanoTime() * 1e-9 * 4.2 + p.id * 0.31);
                    double vx = (ux * 0.92 + px * 0.28 * weave) * speed;
                    double vy = (uy * 0.92 + py * 0.28 * weave) * speed;
                    setVelPerSec(p, vx, vy, dt);
                    rotateShipToward(p, Math.atan2(vy, vx), dt);
                    break;
                }
                double dx = target.x - p.x;
                double dy = target.y - p.y;
                double d = Math.hypot(dx, dy) + 1e-9;
                double ux = dx / d;
                double uy = dy / d;
                double tx = -uy;
                double ty = ux;
                double weave = Math.sin(System.nanoTime() * 1e-9 * 4.5 + p.id * 0.37);
                double away = (d < 420.0) ? -0.55 : -0.20;
                double vx = (tx * weave + ux * away) * speed;
                double vy = (ty * weave + uy * away) * speed;
                setVelPerSec(p, vx, vy, dt);
                rotateShipToward(p, Math.atan2(target.y - p.y, target.x - p.x), dt);
            }
        }
    }

    private static boolean applyEscortHelmAutomation(GameContext ctx, Player player, double dt, double speed, Ship target) {
        if (ctx == null || player == null || dt <= 0.0) return false;
        Ship anchor = pickEscortAnchor(ctx);
        if (!isLiveEscortAnchor(ctx, anchor) || anchor == player) return false;

        ctx.ui.waypointX = anchor.x;
        ctx.ui.waypointY = anchor.y;
        double desiredRange = escortFollowRange(player, anchor);
        ctx.command.helmDesiredRange = desiredRange;

        if (anchor.isWarpCharging()) {
            maybeStartEscortWarpFollow(player, anchor, desiredRange);
        }
        if (player.isWarpCharging()) {
            steerWarpChargingShip(player, dt);
            return true;
        }

        double[] slot = escortSlot(anchor, player, anchor.x, anchor.y, desiredRange);
        double distToSlot = Math.hypot(slot[0] - player.x, slot[1] - player.y);
        if (distToSlot > Math.max(120.0, desiredRange * 0.60)) {
            moveToward(player, slot[0], slot[1], speed, dt);
        } else {
            double desiredVx = anchor.vx / Math.max(1e-9, dt);
            double desiredVy = anchor.vy / Math.max(1e-9, dt);
            double vx = desiredVx + (slot[0] - player.x) * 1.8;
            double vy = desiredVy + (slot[1] - player.y) * 1.8;
            double vMag = Math.hypot(vx, vy);
            double maxSpeed = speed * 0.96;
            if (vMag > maxSpeed && vMag > 1e-6) {
                double scale = maxSpeed / vMag;
                vx *= scale;
                vy *= scale;
            }
            setVelPerSec(player, vx, vy, dt);
            if (isValidTarget(ctx, target)) {
                rotateShipToward(player, Math.atan2(target.y - player.y, target.x - player.x), dt);
            } else {
                rotateShipToward(player, Math.atan2(anchor.y - player.y, anchor.x - player.x), dt);
            }
        }
        return true;
    }

    private static boolean isCaptainNavigationDirective(GameContext.CaptainDirective directive) {
        if (directive == null) return false;
        return switch (directive) {
            case MINE, ESCORT, DEFEND, REPAIR, RTB -> true;
            default -> false;
        };
    }

    private static Ship preferredTarget(GameContext ctx, double range) {
        if (ctx == null || ctx.player == null) return null;
        if (isValidTarget(ctx, ctx.lockedTarget)) return ctx.lockedTarget;
        return TargetingSystem.findClosestEngagementTarget(ctx, ctx.player, ctx.player.x, ctx.player.y, range);
    }

    private static Ship preferredSecondaryTarget(GameContext ctx, double range) {
        if (ctx == null || ctx.player == null) return null;
        if (playerHasSecondaryInterceptMissiles(ctx)) {
            Ship smallCraft = TargetingSystem.findClosestHostileSmallCraft(
                    ctx, ctx.player, ctx.player.x, ctx.player.y, range);
            if (smallCraft != null) return smallCraft;
        }
        return preferredTarget(ctx, range);
    }

    private static boolean playerHasSecondaryInterceptMissiles(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.player.turrets == null) return false;
        for (Turret turret : ctx.player.turrets) {
            if (turret == null || turret.kind != Turret.Kind.MISSILE || turret.primary) continue;
            Turret.MissileRole role = (turret.missileRole == null) ? Turret.MissileRole.ANTI_MEDIUM : turret.missileRole;
            if (role == Turret.MissileRole.INTERCEPT) return true;
        }
        return false;
    }

    private static boolean isValidTarget(GameContext ctx, Ship target) {
        if (ctx == null || target == null) return false;
        if (!target.alive || target.dying || target.hp <= 0) return false;
        if (!TeamSystem.isHostileToPlayer(ctx, target.faction)) return false;
        if (TargetingSystem.isCiwsOnlyTarget(target)) return false;
        if (TargetingSystem.isMainBatteryScreenTarget(ctx.player, target)) return false;
        return TargetingSystem.isDetectableToObserver(ctx.player, target);
    }

    private static void setVelPerSec(Ship ship, double vxPerSec, double vyPerSec, double dt) {
        if (ship == null) return;
        if (dt <= 0.0) {
            ship.vx = 0.0;
            ship.vy = 0.0;
            return;
        }
        boolean thrusting = Math.hypot(vxPerSec, vyPerSec) > 1e-4;
        MovementModel.applyDesiredVelocity(ship, vxPerSec, vyPerSec, dt, thrusting);
    }

    private static void moveToward(Ship ship, double tx, double ty, double speedPerSec, double dt) {
        if (ship == null) return;
        double dx = tx - ship.x;
        double dy = ty - ship.y;
        double len = Math.hypot(dx, dy) + 1e-9;
        double vx = (dx / len) * speedPerSec;
        double vy = (dy / len) * speedPerSec;
        setVelPerSec(ship, vx, vy, dt);
        rotateShipToward(ship, Math.atan2(vy, vx), dt);
    }

    private static double escortFollowRange(Ship escort, Ship anchor) {
        if (escort == null || anchor == null) return 240.0;
        return Math.max(220.0, anchor.radius + escort.radius + 120.0);
    }

    private static double[] escortSlot(Ship anchor, Ship escort, double anchorX, double anchorY, double desiredRange) {
        double lateral = Math.max(140.0, desiredRange * 0.74);
        double back = Math.max(96.0, anchor.radius + escort.radius + 36.0);
        double side = ((escort.id & 1) == 0) ? -1.0 : 1.0;
        double fx = Math.cos(anchor.angle);
        double fy = Math.sin(anchor.angle);
        double rx = -Math.sin(anchor.angle);
        double ry = Math.cos(anchor.angle);
        return new double[]{
                anchorX - fx * back + rx * side * lateral,
                anchorY - fy * back + ry * side * lateral
        };
    }

    private static void maybeStartEscortWarpFollow(Player player, Ship anchor, double desiredRange) {
        if (player == null || anchor == null) return;
        if (!player.canUseBattlefieldWarp() || player.isWarpCharging()) return;
        double tx = anchor.warpExitX();
        double ty = anchor.warpExitY();
        if (!Double.isFinite(tx) || !Double.isFinite(ty)) return;
        double[] exit = escortSlot(anchor, player, tx, ty, desiredRange);
        player.beginBattlefieldWarpFollowing(exit[0], exit[1], Math.max(0.1, anchor.warpChargeRemaining()), anchor.id);
    }

    private static void steerWarpChargingShip(Ship ship, double dt) {
        if (ship == null || dt <= 0.0) return;
        double tx = ship.warpExitX();
        double ty = ship.warpExitY();
        if (!Double.isFinite(tx) || !Double.isFinite(ty)) {
            setVelPerSec(ship, 0.0, 0.0, dt);
            return;
        }

        double dist = Math.hypot(tx - ship.x, ty - ship.y);
        if (dist <= Math.max(80.0, ship.radius * 2.4)) {
            setVelPerSec(ship, 0.0, 0.0, dt);
            return;
        }

        double speed = MovementModel.speedCeiling(ship);
        double slowRadius = Math.max(260.0, ship.radius * 8.0);
        double speedMul = MathUtil.clamp(dist / slowRadius, 0.42, 1.0);
        moveToward(ship, tx, ty, speed * speedMul, dt);
    }

    private static void orbit(Ship ship, double cx, double cy, double desiredRange, double speedPerSec, double dt, double dir) {
        if (ship == null) return;
        double dx = cx - ship.x;
        double dy = cy - ship.y;
        double d = Math.hypot(dx, dy) + 1e-9;
        double ux = dx / d;
        double uy = dy / d;
        double tx = -uy * dir;
        double ty = ux * dir;
        double err = d - desiredRange;
        double radial = Math.max(-1.0, Math.min(1.0, err / Math.max(1.0, desiredRange)));
        double blend = 0.55;
        double vx = (tx * (1.0 - blend) + ux * blend * radial) * speedPerSec;
        double vy = (ty * (1.0 - blend) + uy * blend * radial) * speedPerSec;
        setVelPerSec(ship, vx, vy, dt);
        rotateShipToward(ship, Math.atan2(vy, vx), dt);
    }

    private static void rotateShipToward(Ship ship, double desiredAngle, double dt) {
        if (ship == null || dt <= 0.0) return;
        double delta = MathUtil.normalizeAngle(desiredAngle - ship.angle);
        double maxDelta = MovementModel.turnRateRadPerSec(ship) * dt;
        delta = MathUtil.clamp(delta, -maxDelta, maxDelta);
        ship.angle = MathUtil.normalizeAngle(ship.angle + delta);
    }
}

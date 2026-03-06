public final class CrewStationsSystem {
    private CrewStationsSystem() {}

    public static boolean updatePlayerAutomation(GameContext ctx, InputSnapshot snap, double dt) {
        if (ctx == null || ctx.player == null) return false;
        Player p = ctx.player;
        if (!p.alive || p.dying || p.hp <= 0) return false;
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER || ctx.gameOver) return false;
        ctx.miningAuto = false;

        InputSnapshot s = (snap == null)
                ? new InputSnapshot(false, false, false, false, false, 0, 0)
                : snap;
        boolean manualHelm = s.up || s.down || s.left || s.right;
        if (manualHelm) ctx.helmAutomation = false;
        if (ctx.firingPrimaryManual || ctx.firingSecondaryManual) ctx.tacticalAutomation = false;

        if (ctx.captainAutomation) applyCaptainDirectives(ctx);
        GameContext.CaptainDirective directive = (ctx.captainDirective == null)
                ? GameContext.CaptainDirective.BALANCED
                : ctx.captainDirective;
        boolean captainNavPriority = isCaptainNavigationDirective(directive) && ctx.captainAutomation;
        if (ctx.scienceAutomation) applyScienceAutomation(ctx);
        if (ctx.engineeringAutomation) applyEngineeringAutomation(ctx);
        if (ctx.tacticalAutomation) applyTacticalAutomation(ctx, captainNavPriority, directive);
        else {
            ctx.firingPrimaryAuto = false;
            ctx.firingSecondaryAuto = false;
        }
        if (ctx.helmAutomation) {
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
        GameContext.CaptainDirective directive = (ctx.captainDirective == null)
                ? GameContext.CaptainDirective.BALANCED
                : ctx.captainDirective;

        // Preservation-first fallback always takes precedence.
        if (hpFrac < 0.35 || (directive != GameContext.CaptainDirective.ATTACK && shieldFrac < 0.16)) {
            if (ctx.helmAutomation) ctx.helmMode = GameContext.HelmMode.EVASIVE;
            if (ctx.tacticalAutomation) ctx.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
            if (ctx.engineeringAutomation) ctx.engineeringMode = GameContext.EngineeringMode.DAMAGE_CONTROL;
            ctx.alliedFleetCommand = GameContext.FleetCommand.RETREAT;
            return;
        }

        switch (directive) {
            case ATTACK -> {
                if (ctx.helmAutomation) ctx.helmMode = GameContext.HelmMode.INTERCEPT;
                if (ctx.tacticalAutomation) ctx.tacticalMode = GameContext.TacticalMode.AGGRESSIVE;
                if (ctx.engineeringAutomation) ctx.engineeringMode = GameContext.EngineeringMode.ATTACK;
                if (target != null) ctx.lockedTarget = target;
                ctx.alliedFleetCommand = GameContext.FleetCommand.ATTACK;
            }
            case DEFENSE, DEFEND -> {
                Ship base = TeamSystem.getBaseForTeam(ctx, ctx.player.faction);
                if (base != null) {
                    ctx.waypointX = base.x;
                    ctx.waypointY = base.y;
                    ctx.helmDesiredRange = Math.max(280.0, base.radius + 180.0);
                }
                if (ctx.helmAutomation) ctx.helmMode = GameContext.HelmMode.ORBIT;
                if (ctx.tacticalAutomation) ctx.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                if (ctx.engineeringAutomation) ctx.engineeringMode = GameContext.EngineeringMode.DEFENSE;
                // Defense stays anchored to the base perimeter; tactical auto can still engage nearby threats.
                ctx.lockedTarget = null;
                ctx.alliedFleetCommand = GameContext.FleetCommand.DEFEND;
            }
            case EMERGENCY -> {
                if (ctx.helmAutomation) ctx.helmMode = GameContext.HelmMode.EVASIVE;
                if (ctx.tacticalAutomation) ctx.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                if (ctx.engineeringAutomation) ctx.engineeringMode = GameContext.EngineeringMode.DAMAGE_CONTROL;
                ctx.alliedFleetCommand = GameContext.FleetCommand.RETREAT;
            }
            case MINE -> {
                Asteroid ast = EconomySystem.findBestAsteroidNear(ctx, ctx.player.x, ctx.player.y, 2600.0);
                if (ast != null) {
                    ctx.waypointX = ast.x;
                    ctx.waypointY = ast.y;
                    double md = Math.hypot(ast.x - ctx.player.x, ast.y - ctx.player.y);
                    double mineReach = Math.max(0.0, ctx.player.miningRange) + ctx.player.radius + ast.radius + 22.0;
                    ctx.miningAuto = (md <= mineReach);
                }
                if (ctx.helmAutomation) ctx.helmMode = GameContext.HelmMode.INTERCEPT;
                if (ctx.tacticalAutomation) ctx.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                if (ctx.engineeringAutomation) ctx.engineeringMode = GameContext.EngineeringMode.BALANCED;
                ctx.lockedTarget = null;
                ctx.alliedFleetCommand = GameContext.FleetCommand.MINE;
            }
            case ESCORT -> {
                Ship escort = pickEscortFriendly(ctx);
                if (escort != null) {
                    ctx.waypointX = escort.x;
                    ctx.waypointY = escort.y;
                    ctx.helmDesiredRange = Math.max(200.0, escort.radius + ctx.player.radius + 120.0);
                }
                if (ctx.helmAutomation) ctx.helmMode = GameContext.HelmMode.ORBIT;
                if (ctx.tacticalAutomation) ctx.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                if (ctx.engineeringAutomation) ctx.engineeringMode = GameContext.EngineeringMode.BALANCED;
                ctx.lockedTarget = null;
                ctx.alliedFleetCommand = GameContext.FleetCommand.ESCORT;
            }
            case REPAIR -> {
                Ship base = TeamSystem.getBaseForTeam(ctx, ctx.player.faction);
                if (base != null) {
                    ctx.waypointX = base.x;
                    ctx.waypointY = base.y;
                }
                if (ctx.helmAutomation) ctx.helmMode = GameContext.HelmMode.INTERCEPT;
                if (ctx.tacticalAutomation) ctx.tacticalMode = GameContext.TacticalMode.HOLD_FIRE;
                if (ctx.engineeringAutomation) ctx.engineeringMode = GameContext.EngineeringMode.DAMAGE_CONTROL;
                ctx.lockedTarget = null;
                ctx.alliedFleetCommand = GameContext.FleetCommand.REPAIR;
            }
            case RTB -> {
                Ship base = TeamSystem.getBaseForTeam(ctx, ctx.player.faction);
                if (base != null) {
                    ctx.waypointX = base.x;
                    ctx.waypointY = base.y;
                }
                if (ctx.helmAutomation) ctx.helmMode = GameContext.HelmMode.INTERCEPT;
                if (ctx.tacticalAutomation) ctx.tacticalMode = GameContext.TacticalMode.HOLD_FIRE;
                if (ctx.engineeringAutomation) ctx.engineeringMode = GameContext.EngineeringMode.DEFENSE;
                ctx.lockedTarget = null;
                ctx.alliedFleetCommand = GameContext.FleetCommand.RTB;
            }
            default -> {
                if (shieldFrac < 0.25) {
                    if (ctx.helmAutomation) ctx.helmMode = GameContext.HelmMode.MAINTAIN_RANGE;
                    if (ctx.tacticalAutomation) ctx.tacticalMode = GameContext.TacticalMode.DEFENSIVE;
                    if (ctx.engineeringAutomation) ctx.engineeringMode = GameContext.EngineeringMode.DEFENSE;
                    ctx.alliedFleetCommand = GameContext.FleetCommand.DEFEND;
                    return;
                }

                if (target != null) {
                    double d = Math.hypot(target.x - ctx.player.x, target.y - ctx.player.y);
                    if (d > 650.0) {
                        if (ctx.helmAutomation) ctx.helmMode = GameContext.HelmMode.INTERCEPT;
                        if (ctx.tacticalAutomation) ctx.tacticalMode = GameContext.TacticalMode.AGGRESSIVE;
                        if (ctx.engineeringAutomation) ctx.engineeringMode = GameContext.EngineeringMode.ATTACK;
                        ctx.alliedFleetCommand = GameContext.FleetCommand.ATTACK;
                    } else {
                        if (ctx.helmAutomation) ctx.helmMode = GameContext.HelmMode.ORBIT;
                        if (ctx.tacticalAutomation) ctx.tacticalMode = GameContext.TacticalMode.AGGRESSIVE;
                        if (ctx.engineeringAutomation) ctx.engineeringMode = GameContext.EngineeringMode.BALANCED;
                        ctx.alliedFleetCommand = GameContext.FleetCommand.FORM_UP;
                    }
                    ctx.lockedTarget = target;
                } else {
                    ctx.alliedFleetCommand = GameContext.FleetCommand.AUTO;
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
        if (ctx.scienceJamming) scanRange *= 0.85;
        // Science station continuously refreshes lock to the nearest viable contact.
        ctx.lockedTarget = TargetingSystem.findClosestEnemyToPoint(
                ctx, ctx.player, ctx.player.x, ctx.player.y, scanRange
        );
    }

    private static void applyEngineeringAutomation(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        Player p = ctx.player;

        double hpFrac = (p.hpMax <= 0) ? 1.0 : (p.hp / (double) p.hpMax);
        if (hpFrac < 0.30) {
            ctx.engineeringMode = GameContext.EngineeringMode.DAMAGE_CONTROL;
        }

        switch (ctx.engineeringMode) {
            case ATTACK -> {
                p.setPowerPreset(Ship.PowerPreset.ATTACK);
                p.crewOrder = Ship.CrewOrder.GUNNERY;
            }
            case DEFENSE -> {
                p.setPowerPreset(Ship.PowerPreset.DEFENSE);
                p.crewOrder = Ship.CrewOrder.ENGINEERING;
            }
            case DAMAGE_CONTROL -> {
                p.setPowerPreset(Ship.PowerPreset.DEFENSE);
                p.crewOrder = Ship.CrewOrder.DAMAGE_CONTROL;
            }
            default -> {
                p.setPowerPreset(Ship.PowerPreset.BALANCED);
                p.crewOrder = Ship.CrewOrder.BALANCED;
            }
        }
    }

    private static void applyTacticalAutomation(GameContext ctx, boolean captainNavPriority,
                                                GameContext.CaptainDirective directive) {
        if (ctx == null || ctx.player == null) return;
        Player p = ctx.player;
        double rangeMul = CampaignSystem.targetingRangeMul(ctx);
        double searchRange = 1600.0 * rangeMul * (ctx.scienceJamming ? 0.9 : 1.0);
        Ship target = preferredTarget(ctx, searchRange);
        if (target == null) {
            ctx.firingPrimaryAuto = false;
            ctx.firingSecondaryAuto = false;
            return;
        }
        double d = Math.hypot(target.x - p.x, target.y - p.y);
        boolean lockForHelm = !captainNavPriority || directive == GameContext.CaptainDirective.ATTACK
                || directive == GameContext.CaptainDirective.EMERGENCY;
        if (lockForHelm && ctx.tacticalMode != GameContext.TacticalMode.HOLD_FIRE) {
            ctx.lockedTarget = target;
        }

        switch (ctx.tacticalMode) {
            case HOLD_FIRE -> {
                ctx.firingPrimaryAuto = false;
                ctx.firingSecondaryAuto = false;
            }
            case DEFENSIVE -> {
                double primaryRange = captainNavPriority ? 620.0 * rangeMul : 900.0 * rangeMul;
                double secondaryRange = captainNavPriority ? 520.0 * rangeMul : 760.0 * rangeMul;
                ctx.firingPrimaryAuto = d <= primaryRange;
                ctx.firingSecondaryAuto = d <= secondaryRange;
            }
            case AGGRESSIVE -> {
                double primaryRange = captainNavPriority ? 920.0 * rangeMul : 1500.0 * rangeMul;
                double secondaryRange = captainNavPriority ? 760.0 * rangeMul : 1120.0 * rangeMul;
                ctx.firingPrimaryAuto = d <= primaryRange;
                ctx.firingSecondaryAuto = d <= secondaryRange;
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

        GameContext.CaptainDirective directive = (ctx.captainDirective == null)
                ? GameContext.CaptainDirective.BALANCED
                : ctx.captainDirective;
        boolean captainNavPriority = ctx.captainAutomation && isCaptainNavigationDirective(directive);
        boolean hasWaypoint = Double.isFinite(ctx.waypointX) && Double.isFinite(ctx.waypointY);
        Ship target = preferredTarget(ctx, 2800.0);
        double speed = Math.max(80.0, p.desiredSpeed);

        switch (ctx.helmMode) {
            case INTERCEPT -> {
                if (captainNavPriority && hasWaypoint) {
                    moveToward(p, ctx.waypointX, ctx.waypointY, speed, dt);
                } else if (target != null) {
                    moveToward(p, target.x, target.y, speed, dt);
                } else if (hasWaypoint) {
                    moveToward(p, ctx.waypointX, ctx.waypointY, speed, dt);
                }
                else {
                    p.vx = 0;
                    p.vy = 0;
                }
            }
            case ORBIT -> {
                if (captainNavPriority && hasWaypoint) {
                    orbit(p, ctx.waypointX, ctx.waypointY, Math.max(260.0, ctx.helmDesiredRange), speed * 0.92, dt, 1.0);
                } else if (target != null) {
                    orbit(p, target.x, target.y, Math.max(260.0, ctx.helmDesiredRange), speed * 0.92, dt, 1.0);
                } else if (hasWaypoint) {
                    moveToward(p, ctx.waypointX, ctx.waypointY, speed, dt);
                }
            }
            case MAINTAIN_RANGE -> {
                double tx;
                double ty;
                if (captainNavPriority && hasWaypoint) {
                    tx = ctx.waypointX;
                    ty = ctx.waypointY;
                } else if (target != null) {
                    tx = target.x;
                    ty = target.y;
                } else if (hasWaypoint) {
                    moveToward(p, ctx.waypointX, ctx.waypointY, speed, dt);
                    break;
                } else {
                    break;
                }
                double desired = Math.max(260.0, ctx.helmDesiredRange);
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
                p.angle = Math.atan2(ty - p.y, tx - p.x);
            }
            case EVASIVE -> {
                if (target == null) {
                    if (hasWaypoint) moveToward(p, ctx.waypointX, ctx.waypointY, speed, dt);
                    break;
                }
                if (captainNavPriority && hasWaypoint) {
                    double tx = ctx.waypointX - p.x;
                    double ty = ctx.waypointY - p.y;
                    double tl = Math.hypot(tx, ty) + 1e-9;
                    double ux = tx / tl;
                    double uy = ty / tl;
                    double px = -uy;
                    double py = ux;
                    double weave = Math.sin(System.nanoTime() * 1e-9 * 4.2 + p.id * 0.31);
                    double vx = (ux * 0.92 + px * 0.28 * weave) * speed;
                    double vy = (uy * 0.92 + py * 0.28 * weave) * speed;
                    setVelPerSec(p, vx, vy, dt);
                    p.angle = Math.atan2(vy, vx);
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
                p.angle = Math.atan2(target.y - p.y, target.x - p.x);
            }
        }
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
        return TargetingSystem.findClosestEnemyToPoint(ctx, ctx.player, ctx.player.x, ctx.player.y, range);
    }

    private static boolean isValidTarget(GameContext ctx, Ship target) {
        if (ctx == null || target == null) return false;
        if (!target.alive || target.dying || target.hp <= 0) return false;
        if (!TeamSystem.isHostileToPlayer(ctx, target.faction)) return false;
        return TargetingSystem.isDetectableToObserver(ctx.player, target);
    }

    private static void setVelPerSec(Ship ship, double vxPerSec, double vyPerSec, double dt) {
        if (ship == null || dt <= 0.0) return;
        ship.vx = vxPerSec * dt;
        ship.vy = vyPerSec * dt;
    }

    private static void moveToward(Ship ship, double tx, double ty, double speedPerSec, double dt) {
        if (ship == null) return;
        double dx = tx - ship.x;
        double dy = ty - ship.y;
        double len = Math.hypot(dx, dy) + 1e-9;
        double vx = (dx / len) * speedPerSec;
        double vy = (dy / len) * speedPerSec;
        setVelPerSec(ship, vx, vy, dt);
        ship.angle = Math.atan2(vy, vx);
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
        ship.angle = Math.atan2(cy - ship.y, cx - ship.x);
    }
}

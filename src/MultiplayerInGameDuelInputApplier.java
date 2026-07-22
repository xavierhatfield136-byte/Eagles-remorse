import java.util.List;

/** Applies accepted remote input frames to the host-authoritative V1 duel state. */
public final class MultiplayerInGameDuelInputApplier {
    private static final double DIRECT_FIRE_RANGE = 900.0;
    private static final int DIRECT_FIRE_DAMAGE = 240;

    private MultiplayerInGameDuelInputApplier() {}

    public static int drainAndApply(GameContext ctx, double dt, long hostTick) {
        if (ctx == null || ctx.multiplayerInGameSession == null || ctx.multiplayerBattleRuntime == null) return 0;
        if (ctx.multiplayerAuthorityMode != MultiplayerAuthorityMode.HOST) return 0;
        List<MultiplayerCommandGate.PlayerInputFrame> frames = ctx.multiplayerInGameSession.drainInputFrames();
        int accepted = 0;
        for (MultiplayerCommandGate.PlayerInputFrame frame : frames) {
            MultiplayerCommandGate.CommandResult result =
                    ctx.multiplayerBattleRuntime.acceptInput(frame, hostTick);
            if (!result.accepted()) continue;
            applyAcceptedInput(ctx, frame, Math.max(0.0, dt));
            ctx.multiplayerInGameSession.sendInputAck(new MultiplayerProtocolV1.InputAck(
                    frame.slotId(), frame.sequence(), hostTick));
            accepted++;
        }
        return accepted;
    }

    static void applyAcceptedInput(GameContext ctx, MultiplayerCommandGate.PlayerInputFrame frame, double dt) {
        if (ctx == null || frame == null) return;
        BattleAuthority authority = BattleAuthority.forContext(ctx);
        if (!authority.permits(BattleAuthorityOperation.MOVEMENT_INPUT)) return;
        Ship ship = findShip(ctx, frame.controlledShipId());
        if (!isAlive(ship)) return;

        double turnInput = MathUtil.clamp(frame.turn(), -1.0f, 1.0f);
        double turnRate = MovementModel.turnRateRadPerSec(ship);
        ship.angle = MathUtil.normalizeAngle(ship.angle + turnInput * turnRate * dt);

        double throttle = MathUtil.clamp(frame.thrust(), -1.0f, 1.0f);
        double speed = MovementModel.speedCeiling(ship);
        double thrustMul = (throttle >= 0.0) ? 1.0 : MovementModel.reverseThrustMul(ship);
        double coupling = MovementModel.rotationCoupling(ship);
        double rotationPenalty = 1.0 - coupling * Math.min(1.0, Math.abs(turnInput));
        rotationPenalty = MathUtil.clamp(rotationPenalty, 0.62, 1.0);
        double desiredVxPerSec = Math.cos(ship.angle) * speed * throttle * thrustMul * rotationPenalty;
        double desiredVyPerSec = Math.sin(ship.angle) * speed * throttle * thrustMul * rotationPenalty;

        if (Math.abs(throttle) <= 1e-6) {
            MovementModel.applyDesiredVelocity(ship, 0.0, 0.0, dt, false);
        } else {
            MovementModel.applyDesiredVelocity(ship, desiredVxPerSec, desiredVyPerSec, dt, true);
        }
        if (frame.primaryHeld()) {
            applyHostSideDirectFire(ctx, ship, authority);
        }
    }

    private static void applyHostSideDirectFire(GameContext ctx, Ship shooter, BattleAuthority authority) {
        if (authority == null || !authority.permits(BattleAuthorityOperation.WEAPON_FIRE)) return;
        Ship target = nearestHostile(ctx, shooter);
        if (!isAlive(target)) return;
        double dist = Math.hypot(target.x - shooter.x, target.y - shooter.y);
        if (dist > DIRECT_FIRE_RANGE) return;
        if (!authority.permits(BattleAuthorityOperation.DAMAGE_APPLICATION)) return;
        int damage = Math.max(DIRECT_FIRE_DAMAGE, target.hpMax * 2);
        target.takePenetratingInternalDamage(damage, target.x, target.y, 0.0, 0.0);
        if (target.hp > 0 && !target.dying) {
            target.scaleCurrentHullIntegrity(0.0);
        }
        if (target.hp <= 0 || target.dying) {
            target.alive = false;
            target.dying = false;
        }
    }

    private static Ship nearestHostile(GameContext ctx, Ship shooter) {
        if (ctx == null || !isAlive(shooter) || shooter.faction == null) return null;
        Ship best = null;
        double bestD2 = DIRECT_FIRE_RANGE * DIRECT_FIRE_RANGE;
        for (Ship ship : ctx.ships) {
            if (!isAlive(ship) || ship == shooter || ship.faction == null) continue;
            if (shooter.faction.isFriendlyTo(ship.faction)) continue;
            double d2 = MathUtil.dist2(shooter.x, shooter.y, ship.x, ship.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = ship;
            }
        }
        return best;
    }

    private static Ship findShip(GameContext ctx, int shipId) {
        if (ctx == null || shipId <= 0) return null;
        for (Ship ship : ctx.ships) {
            if (ship != null && ship.id == shipId) return ship;
        }
        return null;
    }

    private static boolean isAlive(Ship ship) {
        return ship != null && ship.alive && !ship.dying && ship.hp > 0;
    }
}

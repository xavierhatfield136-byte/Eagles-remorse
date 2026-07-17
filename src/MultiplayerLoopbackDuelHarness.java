import java.util.HashMap;
import java.util.Map;

/** Controlled Phase 7 vertical slice: loopback client input, host simulation, snapshots, events, and cleanup. */
public final class MultiplayerLoopbackDuelHarness {
    private final MultiplayerMultipleCommandSourcesScenario hostScenario;
    private final MultiplayerLoopbackTransport transport = new MultiplayerLoopbackTransport();
    private final MultiplayerClientSnapshotView clientView = new MultiplayerClientSnapshotView();
    private long snapshotSequence = 0L;
    private long eventSequence = 0L;
    private boolean started;
    private boolean victorySent;

    public MultiplayerLoopbackDuelHarness(MultiplayerRulesV1.BattleSetup setup) {
        this.hostScenario = new MultiplayerMultipleCommandSourcesScenario(setup);
    }

    public MultiplayerProtocolV1.CompatibilityResult connect() {
        MultiplayerProtocolV1.CompatibilityResult result = transport.connect(
                MultiplayerProtocolV1.localFingerprint(), MultiplayerProtocolV1.localFingerprint());
        pumpClientMessages();
        return result;
    }

    public void startMatch(long hostTick) {
        requireConnected();
        started = true;
        for (MultiplayerPlayerSlotState slot : hostScenario.runtime().slots().values()) {
            Ship ship = findShip(slot.controlledShipId);
            if (ship == null) continue;
            MultiplayerEntityIdAllocator.NetworkEntityId eventId =
                    new MultiplayerEntityIdAllocator.NetworkEntityId(ship.id, 1);
            sendEvent(MultiplayerReplicationV1.EventType.SHIP_SPAWNED, eventId, hostTick, slot.slotId, 0,
                    ship.name);
        }
        publishSnapshot(hostTick);
        pumpClientMessages();
    }

    public void sendClientInput(MultiplayerCommandGate.PlayerInputFrame frame) {
        transport.sendInputToHost(frame);
    }

    public void enqueueHostInput(MultiplayerCommandSource source, long hostTick) {
        hostScenario.enqueue(source, hostTick);
    }

    public void hostTick(double dt, long hostTick) {
        requireStarted();
        transport.heartbeat(MultiplayerLoopbackTransport.Endpoint.HOST, hostTick);
        Map<Integer, Integer> hpBefore = hpByShip();
        Map<Integer, Boolean> aliveBefore = aliveByShip();

        for (MultiplayerLoopbackTransport.Message message : transport.drainForHost()) {
            if (message.inputFrame() != null) {
                hostScenario.enqueueInputFrame(message.inputFrame());
                sendAck(message.inputFrame(), hostTick);
                if (message.inputFrame().primaryHeld()) {
                    MultiplayerEntityIdAllocator.NetworkEntityId shooterId =
                            new MultiplayerEntityIdAllocator.NetworkEntityId(
                                    message.inputFrame().controlledShipId(), 1);
                    sendEvent(MultiplayerReplicationV1.EventType.WEAPON_FIRED,
                            shooterId, hostTick, message.inputFrame().slotId(), 0, "primary");
                    Ship target = nearestHostileInDirectFireRange(message.inputFrame().controlledShipId());
                    if (target != null) {
                        sendEvent(MultiplayerReplicationV1.EventType.HIT_CONFIRMED,
                                new MultiplayerEntityIdAllocator.NetworkEntityId(target.id, 1),
                                hostTick, message.inputFrame().slotId(), 0, target.name);
                    }
                }
            }
        }

        hostScenario.tick(dt, hostTick);
        sendDamageAndDeathEvents(hpBefore, aliveBefore, hostTick);
        publishSnapshot(hostTick);
        pumpClientMessages();
    }

    public void clientHeartbeat(long hostTick) {
        transport.heartbeat(MultiplayerLoopbackTransport.Endpoint.CLIENT, hostTick);
    }

    public boolean clientTimedOut(long currentHostTick) {
        return transport.timedOut(MultiplayerLoopbackTransport.Endpoint.CLIENT, currentHostTick);
    }

    public void exitToMenu() {
        transport.close("Return to multiplayer menu");
        pumpClientMessages();
    }

    public MultiplayerMultipleCommandSourcesScenario hostScenario() {
        return hostScenario;
    }

    public MultiplayerLoopbackTransport transport() {
        return transport;
    }

    public MultiplayerClientSnapshotView clientView() {
        return clientView;
    }

    private void sendAck(MultiplayerCommandGate.PlayerInputFrame frame, long hostTick) {
        transport.sendAckToClient(new MultiplayerProtocolV1.InputAck(
                frame.slotId(), frame.sequence(), hostTick));
    }

    private void sendDamageAndDeathEvents(Map<Integer, Integer> hpBefore,
                                          Map<Integer, Boolean> aliveBefore,
                                          long hostTick) {
        for (Ship ship : hostScenario.runtime().context().ships) {
            if (ship == null) continue;
            int beforeHp = hpBefore.getOrDefault(ship.id, ship.hp);
            boolean beforeAlive = aliveBefore.getOrDefault(ship.id, ship.alive && !ship.dying && ship.hp > 0);
            boolean aliveNow = ship.alive && !ship.dying && ship.hp > 0;
            MultiplayerEntityIdAllocator.NetworkEntityId eventId =
                    new MultiplayerEntityIdAllocator.NetworkEntityId(ship.id, 1);
            if (ship.hp < beforeHp || (beforeAlive && !aliveNow)) {
                sendEvent(MultiplayerReplicationV1.EventType.HIT_CONFIRMED,
                        eventId, hostTick, 0, 0, "hp " + beforeHp + " -> " + ship.hp);
            }
            if (beforeAlive && !aliveNow) {
                sendEvent(MultiplayerReplicationV1.EventType.SHIP_DESTROYED,
                        eventId, hostTick, 0, 0, ship.name);
            }
        }

        MultiplayerDuelVictoryEvaluator.MatchResult result = hostScenario.lastResult();
        if (!victorySent && result.ended()) {
            victorySent = true;
            sendEvent(MultiplayerReplicationV1.EventType.VICTORY_DECLARED,
                    null, hostTick, 0, 0, result.reason());
        }
    }

    private void publishSnapshot(long hostTick) {
        transport.sendSnapshotToClient(++snapshotSequence, hostScenario.runtime().snapshot(hostTick), hostTick);
    }

    private void sendEvent(MultiplayerReplicationV1.EventType type,
                           MultiplayerEntityIdAllocator.NetworkEntityId entityId,
                           long hostTick,
                           int sourceSlotId,
                           int targetSlotId,
                           String detail) {
        transport.sendEventToClient(++eventSequence, hostTick,
                new MultiplayerReplicationV1.AuthoritativeEvent(
                        type, entityId, eventSequence, hostTick, sourceSlotId, targetSlotId, detail));
    }

    private void pumpClientMessages() {
        for (MultiplayerLoopbackTransport.Message message : transport.drainForClient()) {
            clientView.receive(message);
        }
    }

    private Map<Integer, Integer> hpByShip() {
        HashMap<Integer, Integer> out = new HashMap<>();
        for (Ship ship : hostScenario.runtime().context().ships) {
            if (ship != null) out.put(ship.id, ship.hp);
        }
        return out;
    }

    private Map<Integer, Boolean> aliveByShip() {
        HashMap<Integer, Boolean> out = new HashMap<>();
        for (Ship ship : hostScenario.runtime().context().ships) {
            if (ship != null) out.put(ship.id, ship.alive && !ship.dying && ship.hp > 0);
        }
        return out;
    }

    private Ship findShip(int shipId) {
        for (Ship ship : hostScenario.runtime().context().ships) {
            if (ship != null && ship.id == shipId) return ship;
        }
        return null;
    }

    private Ship nearestHostileInDirectFireRange(int shooterShipId) {
        Ship shooter = findShip(shooterShipId);
        if (shooter == null || shooter.faction == null || !shooter.alive || shooter.dying || shooter.hp <= 0) {
            return null;
        }
        Ship best = null;
        double bestD2 = 900.0 * 900.0;
        for (Ship ship : hostScenario.runtime().context().ships) {
            if (ship == null || ship == shooter || ship.faction == null || !ship.alive || ship.dying || ship.hp <= 0) {
                continue;
            }
            if (shooter.faction.isFriendlyTo(ship.faction)) continue;
            double dx = ship.x - shooter.x;
            double dy = ship.y - shooter.y;
            double d2 = dx * dx + dy * dy;
            if (d2 < bestD2) {
                bestD2 = d2;
                best = ship;
            }
        }
        return best;
    }

    private void requireConnected() {
        if (!transport.connected()) throw new IllegalStateException("Loopback transport is not connected");
    }

    private void requireStarted() {
        requireConnected();
        if (!started) throw new IllegalStateException("Loopback match has not started");
    }
}

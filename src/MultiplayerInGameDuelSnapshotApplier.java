/** Applies host snapshots to a client-side presentation GameContext. */
public final class MultiplayerInGameDuelSnapshotApplier {
    private MultiplayerInGameDuelSnapshotApplier() {}

    public static boolean applyLatest(GameContext ctx) {
        if (ctx == null || ctx.multiplayerInGameSession == null) return false;
        return apply(ctx, ctx.multiplayerInGameSession.latestSnapshot());
    }

    static boolean apply(GameContext ctx, MultiplayerBattleSnapshot snapshot) {
        if (ctx == null || snapshot == null) return false;
        if (ctx.multiplayerAuthorityMode != MultiplayerAuthorityMode.CLIENT_PRESENTATION) return false;
        assertSnapshotOwnerPath(ctx);

        for (MultiplayerBattleSnapshot.SlotSnapshot slot : snapshot.slots()) {
            if (slot == null) continue;
            if (slot.slotId() == ctx.multiplayerLocalSlotId) {
                ctx.multiplayerLocalNetworkShipId = slot.controlledShipId();
            }
        }

        boolean changed = false;
        for (MultiplayerBattleSnapshot.ShipSnapshot shipSnapshot : snapshot.ships()) {
            if (shipSnapshot == null) continue;
            Ship ship = localShipForSnapshot(ctx, snapshot, shipSnapshot);
            if (ship == null) continue;
            applyShipState(ship, shipSnapshot);
            changed = true;
        }
        if (changed) {
            ctx.entityQuery.rebuild(ctx);
        }
        return changed;
    }

    private static Ship localShipForSnapshot(GameContext ctx,
                                             MultiplayerBattleSnapshot snapshot,
                                             MultiplayerBattleSnapshot.ShipSnapshot shipSnapshot) {
        Integer localId = ctx.multiplayerNetworkShipIdToLocalShipId.get(shipSnapshot.shipId());
        Ship existing = findShip(ctx, localId == null ? 0 : localId);
        if (existing != null) return existing;

        MultiplayerBattleSnapshot.SlotSnapshot controllingSlot = controllingSlot(snapshot, shipSnapshot.shipId());
        if (controllingSlot != null && controllingSlot.slotId() == ctx.multiplayerLocalSlotId && ctx.player != null) {
            ctx.multiplayerNetworkShipIdToLocalShipId.put(shipSnapshot.shipId(), ctx.player.id);
            return ctx.player;
        }

        Ship byFactionRole = findUnmappedShip(ctx, shipSnapshot);
        if (byFactionRole != null) {
            ctx.multiplayerNetworkShipIdToLocalShipId.put(shipSnapshot.shipId(), byFactionRole.id);
            return byFactionRole;
        }

        FleetShip created = new FleetShip(shipSnapshot.role(), shipSnapshot.faction(), shipSnapshot.x(), shipSnapshot.y());
        created.name = controllingSlot == null || controllingSlot.displayName().isBlank()
                ? "Remote Ship"
                : controllingSlot.displayName();
        ctx.ships.add(created);
        if (controllingSlot != null) {
            ctx.multiplayerPlayerControlledShipIds.add(created.id);
        }
        ctx.multiplayerNetworkShipIdToLocalShipId.put(shipSnapshot.shipId(), created.id);
        return created;
    }

    private static void applyShipState(Ship ship, MultiplayerBattleSnapshot.ShipSnapshot snapshot) {
        ship.x = finiteOr(snapshot.x(), ship.x);
        ship.y = finiteOr(snapshot.y(), ship.y);
        ship.vx = finiteOr(snapshot.vx(), 0.0);
        ship.vy = finiteOr(snapshot.vy(), 0.0);
        ship.angle = MathUtil.normalizeAngle(snapshot.angle());
        ship.hp = Math.max(0, snapshot.hp());
        ship.shield = Math.max(0.0, finiteOr(snapshot.shield(), 0.0));
        ship.alive = snapshot.alive();
        if (!snapshot.alive()) {
            ship.dying = false;
            ship.vx = 0.0;
            ship.vy = 0.0;
        }
        if (snapshot.role() != null) ship.role = snapshot.role();
        if (snapshot.faction() != null) ship.faction = snapshot.faction();
    }

    private static Ship findUnmappedShip(GameContext ctx, MultiplayerBattleSnapshot.ShipSnapshot snapshot) {
        if (ctx == null || snapshot == null) return null;
        for (Ship ship : ctx.ships) {
            if (ship == null) continue;
            if (ctx.multiplayerNetworkShipIdToLocalShipId.containsValue(ship.id)) continue;
            if (ship.role == snapshot.role() && ship.faction == snapshot.faction()) {
                return ship;
            }
        }
        return null;
    }

    private static MultiplayerBattleSnapshot.SlotSnapshot controllingSlot(MultiplayerBattleSnapshot snapshot,
                                                                          int shipId) {
        if (snapshot == null) return null;
        for (MultiplayerBattleSnapshot.SlotSnapshot slot : snapshot.slots()) {
            if (slot != null && slot.controlledShipId() == shipId) return slot;
        }
        return null;
    }

    private static Ship findShip(GameContext ctx, int localId) {
        if (ctx == null || localId <= 0) return null;
        for (Ship ship : ctx.ships) {
            if (ship != null && ship.id == localId) return ship;
        }
        return null;
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static void assertSnapshotOwnerPath(GameContext ctx) {
        if (ctx.multiplayerClientSnapshotThreadGuard == null) {
            ctx.multiplayerClientSnapshotThreadGuard =
                    new MultiplayerBattleThreadGuard("client snapshot application path");
        }
        ctx.multiplayerClientSnapshotThreadGuard.assertOwnerThread("client snapshot application");
    }
}

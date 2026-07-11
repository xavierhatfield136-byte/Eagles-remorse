import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CampaignFleetLedgerSystem {
    private CampaignFleetLedgerSystem() {}

    static List<String> campaignFiniteFleetLedgerLines(GameContext ctx, CampaignSystem.CampaignState st) {
        if (ctx == null || st == null) return List.of("Finite fleet ledger unavailable.");
        CampaignSystem.ensureCampaignForceOwnership(ctx, st);
        CampaignSystem.reconcileCampaignFiniteEconomy(ctx, st);
        LinkedHashMap<Faction, int[]> counts = new LinkedHashMap<>();
        counts.put(Faction.ENEMY, new int[5]);
        counts.put(Faction.TEAM_C, new int[5]);
        counts.put(Faction.BRIGHT_YELLOW, new int[5]);
        counts.put(Faction.DARK_YELLOW, new int[5]);
        counts.put(Faction.ALLY, new int[5]);
        counts.put(Faction.PLAYER, new int[5]);
        if (!st.campaignShipPool.isEmpty()) {
            for (CampaignSystem.CampaignShipPoolRecord record : st.campaignShipPool.values()) {
                if (record == null) continue;
                Faction faction = record.faction == null ? Faction.ALLY : record.faction;
                int[] row = counts.computeIfAbsent(faction, ignored -> new int[5]);
                switch (record.status == null ? CampaignSystem.CampaignShipPoolStatus.RESERVE : record.status) {
                    case ACTIVE -> row[0]++;
                    case DOCKED, RESERVE -> row[1]++;
                    case DAMAGED, UNDER_REPAIR -> row[2]++;
                    case DESTROYED -> row[3]++;
                    case UNDER_CONSTRUCTION -> row[4]++;
                }
            }
        } else {
            for (CampaignSystem.CampaignForce force : st.campaignForces) {
                if (force == null) continue;
                Faction faction = force.faction == null ? Faction.ALLY : force.faction;
                int[] row = counts.computeIfAbsent(faction, ignored -> new int[5]);
                int ships = Math.max(0, force.shipIds.size());
                if (force.destroyed || force.strength <= 1.0
                        || force.state == CampaignSystem.CampaignFleetState.DESTROYED) {
                    row[3] += Math.max(1, ships);
                } else if (force.intent == CampaignSystem.CampaignForceIntent.REPAIRING
                        || force.intent == CampaignSystem.CampaignForceIntent.RETREATING
                        || force.hullIntegrity < 72.0 || force.readiness < 58.0) {
                    row[2] += Math.max(1, ships);
                } else if (force.simulationActive) {
                    row[0] += Math.max(1, ships);
                } else {
                    row[1] += Math.max(1, ships);
                }
            }
        }
        for (CampaignSystem.CampaignBaseQueueEntry order : st.campaignBaseQueues) {
            if (order == null) continue;
            Faction faction = order.faction == null ? Faction.ALLY : order.faction;
            int[] row = counts.computeIfAbsent(faction, ignored -> new int[5]);
            if (order.type == CampaignSystem.CampaignBaseQueueType.CONSTRUCTION) row[4]++;
        }
        ArrayList<String> out = new ArrayList<>();
        out.add("Finite Fleet Ledger: active / docked / repair / destroyed / building");
        for (Map.Entry<Faction, int[]> entry : counts.entrySet()) {
            int[] row = entry.getValue();
            if (row == null) continue;
            int total = row[0] + row[1] + row[2] + row[3] + row[4];
            if (total <= 0 && entry.getKey() != Faction.ENEMY && entry.getKey() != Faction.TEAM_C
                    && entry.getKey() != Faction.BRIGHT_YELLOW && entry.getKey() != Faction.DARK_YELLOW) {
                continue;
            }
            out.add(CampaignSystem.factionBoardName(entry.getKey()) + ": "
                    + row[0] + " / " + row[1] + " / " + row[2] + " / " + row[3] + " / " + row[4]);
        }
        return out;
    }

    static List<String> campaignOrderOfBattleReportLines(GameContext ctx, CampaignSystem.CampaignState st) {
        if (ctx == null || st == null) return List.of("ORDER OF BATTLE: unavailable");
        CampaignSystem.ensureStrategicOvermapReady(ctx);
        CampaignSystem.syncCampaignForceSimulationSeeds(ctx, st);
        CampaignSystem.ensureCampaignForceOwnership(ctx, st);
        CampaignSystem.reconcileCampaignFiniteEconomy(ctx, st);
        ArrayList<String> out = new ArrayList<>();
        out.add("ORDER OF BATTLE - AUTHORITATIVE FINITE INVENTORY");
        out.add("Blue starting command: MOTHERSHIP 1  |  " + roleCountSummaryForBlue(st));
        out.addAll(orderOfBattleFactionRoleLines(st, Faction.ENEMY, "Red"));
        out.addAll(orderOfBattleFactionRoleLines(st, Faction.TEAM_C, "Green"));
        out.addAll(orderOfBattleFactionRoleLines(st, Faction.BRIGHT_YELLOW, "Bright Yellow"));
        out.addAll(orderOfBattleFactionRoleLines(st, Faction.DARK_YELLOW, "Dark Orange-Yellow"));

        int active = 0;
        int garrisons = 0;
        int convoys = 0;
        int miners = 0;
        int reserves = 0;
        int construction = 0;
        int unassigned = 0;
        for (CampaignSystem.CampaignShipPoolRecord record : st.campaignShipPool.values()) {
            if (record == null) continue;
            if (record.status == CampaignSystem.CampaignShipPoolStatus.ACTIVE) {
                active++;
                CampaignSystem.CampaignForce owner = CampaignSystem.campaignForceById(st, record.forceId);
                if (owner != null) {
                    if (owner.kind == CampaignSystem.CampaignForceKind.BASE_DEFENSE) garrisons++;
                    if (owner.kind == CampaignSystem.CampaignForceKind.CONVOY
                            || owner.kind == CampaignSystem.CampaignForceKind.TRADE_GROUP
                            || owner.kind == CampaignSystem.CampaignForceKind.INSTALLATION_TRAFFIC) convoys++;
                    if (owner.kind == CampaignSystem.CampaignForceKind.MINING_GROUP) miners++;
                }
            }
            if (record.status == CampaignSystem.CampaignShipPoolStatus.RESERVE
                    || record.status == CampaignSystem.CampaignShipPoolStatus.DOCKED) reserves++;
            if (record.status == CampaignSystem.CampaignShipPoolStatus.UNDER_CONSTRUCTION) construction++;
            boolean queueOwned = record.status == CampaignSystem.CampaignShipPoolStatus.UNDER_CONSTRUCTION
                    && st.campaignBaseQueues.stream().anyMatch(queue -> queue != null && queue.shipRecordId == record.id);
            if (record.forceId <= 0 && record.baseId.isBlank() && !queueOwned
                    && record.status != CampaignSystem.CampaignShipPoolStatus.DESTROYED) unassigned++;
        }
        for (CampaignSystem.CampaignBaseQueueEntry queue : st.campaignBaseQueues) {
            if (queue != null && queue.type == CampaignSystem.CampaignBaseQueueType.CONSTRUCTION) construction++;
        }
        out.add("Assignments: active fleets " + active
                + "  |  garrisons " + garrisons
                + "  |  convoys " + convoys
                + "  |  mining groups " + miners
                + "  |  reserves/docked " + reserves
                + "  |  under construction " + construction
                + "  |  unassigned " + unassigned);

        ArrayList<String> problems = CampaignIntegritySystem.orderOfBattleAuditProblems(st, unassigned);
        if (problems.isEmpty()) {
            out.add("AUDIT PASS: unique IDs and persistent names; every live hull and force has faction, provenance, and mission.");
        } else {
            out.add("AUDIT FLAGS: " + problems.size());
            for (String problem : problems) out.add(" - " + problem);
        }
        out.add("Provenance rule: no runtime force may mint a missing hull; deployment claims only existing faction inventory.");
        return out;
    }

    static List<String> campaignFleetCompositionContractLines() {
        return List.of(
                "Small patrol: patrol/picket lead, missile or frigate support, optional CIWS screen.",
                "Mining deployment: miner, ore hauler, then picket/CIWS/frigate protection.",
                "Trade convoy: transport and hauler core with armed escort; Yellow may field a distinct capital escort.",
                "Infrastructure defense: pickets and CIWS with frigate/cruiser depth at high-value sites.",
                "Hunter-killer: missile and frigate screen led by a cruiser in dangerous regions.",
                "Capital task force: faction capital lead, escorts, missile spear, and logistics-capable support.",
                "Titan task force: late-region exceptional-strength deployment, faction titan lead, capital and escort requirement.",
                "Mixed large fleet: doctrine, mission, local threat, and finite available inventory determine each slot.",
                "Inventory invariant: a role is never deployed unless an existing faction hull record can supply it."
        );
    }

    static List<String> campaignTitanDoctrineLines(CampaignSystem.CampaignState st) {
        if (st == null) return List.of("Titan doctrine unavailable.");
        return List.of(
                "Titan inventory: Red " + CampaignSystem.countFactionTitans(st, Faction.ENEMY)
                        + "  |  Green " + CampaignSystem.countFactionTitans(st, Faction.TEAM_C)
                        + "  |  Bright Yellow " + CampaignSystem.countFactionTitans(st, Faction.BRIGHT_YELLOW)
                        + "  |  Dark Orange-Yellow " + CampaignSystem.countFactionTitans(st, Faction.DARK_YELLOW),
                "Construction: titan hulls require a strategic shipyard, rare role availability, heavy ore, and long build time.",
                "Deployment: ordinary early patrols cannot claim titans; only exceptional late-region task forces qualify.",
                "Escort: titan forces claim capital/line escorts and support hulls from the same finite inventory.",
                "Repair: damaged titans return through normal base repair queues and retain persistent names and condition.",
                "Loss: titan destruction removes the finite record, creates a recovery-grade wreck contact, and changes reputation/war pressure.",
                "Hunt: fully identified hostile titan contacts become persistent high-value hunt opportunities with major rewards."
        );
    }

    static List<String> campaignCapitalPresenceLines(GameContext ctx, CampaignSystem.CampaignState st) {
        if (ctx == null || st == null) return List.of("Capital presence unavailable.");
        CampaignSystem.reconcileCampaignFiniteEconomy(ctx, st);
        int activeCapitals = 0;
        int activeTitans = 0;
        for (CampaignSystem.CampaignShipPoolRecord record : st.campaignShipPool.values()) {
            if (record == null || record.status != CampaignSystem.CampaignShipPoolStatus.ACTIVE || record.role == null) continue;
            if (record.role.isTitanOrMothership()) activeTitans++;
            else if (record.role.isCapitalCombatant()) activeCapitals++;
        }
        return List.of(
                "Capital contact cadence: opening phase 0-1 per three long legs; middle phase at least 1 per two; late phase at least 1 per active-region leg.",
                "Live capital presence: capitals " + activeCapitals + "  |  titans " + activeTitans,
                "Risk/reward: capital kills grant major salvage and reputation; saving allied capitals grants coalition reputation and route support.",
                "Persistence: capital names, damage, retreat intent, finite loss, and recovery contacts survive tactical/strategic transitions."
        );
    }

    static List<String> campaignContactDensityLines(GameContext ctx, CampaignSystem.CampaignState st) {
        if (ctx == null || st == null) return List.of("Contact density unavailable.");
        CampaignSystem.ensureStrategicOvermapReady(ctx);
        CampaignSystem.syncCampaignForceSimulationSeeds(ctx, st);
        CampaignSystem.reconcileCampaignFiniteEconomy(ctx, st);
        int friendly = 0;
        int neutral = 0;
        int hostile = 0;
        int major = 0;
        double radius = 1800.0;
        for (CampaignSystem.CampaignForce force : st.campaignForces) {
            if (force == null || force.destroyed || force.kind == CampaignSystem.CampaignForceKind.PLAYER_FLEET) continue;
            if (GameMath.dist2(st.playerGalaxyX, st.playerGalaxyY,
                    CampaignSystem.forceMarkerX(force), CampaignSystem.forceMarkerY(force)) > radius * radius) continue;
            if (force.faction == Faction.ENEMY) hostile++;
            else if (force.faction == Faction.BRIGHT_YELLOW) neutral++;
            else friendly++;
            if (force.kind == CampaignSystem.CampaignForceKind.TASK_FORCE || force.strength >= 72.0) major++;
        }
        int nearby = friendly + neutral + hostile;
        return List.of(
                "Ordinary traffic target: at least 1 nearby visible contact; patrols, traders, miners, logistics, and scouts do not all interrupt travel.",
                "Nearby traffic: total " + nearby + "  |  friendly " + friendly + "  |  neutral " + neutral
                        + "  |  hostile " + hostile + "  |  major " + major,
                "Current travel leg: observed " + st.transitContactEventsThisLeg
                        + " meaningful contacts  |  tuned target " + st.transitContactTargetThisLeg,
                "Meaningful-event target: 5-8 across a long active-region travel leg, scaled down for short or quiet routes.",
                "Fatigue rule: visible traffic is informational; only route-crossing threats, urgent distress, or chosen contacts become mandatory encounters.",
                "Tuning inputs: route length, theater activity, danger, player posture, contact-chain state, and recent interruption count."
        );
    }

    private static String roleCountSummaryForBlue(CampaignSystem.CampaignState st) {
        LinkedHashMap<ShipRole, Integer> roles = new LinkedHashMap<>();
        for (CampaignSystem.PersistentFleetEntry entry : st.persistentBlueFleet) {
            if (entry == null || entry.destroyed) continue;
            roles.merge(entry.role, 1, Integer::sum);
        }
        return compactRoleCounts(roles);
    }

    private static List<String> orderOfBattleFactionRoleLines(CampaignSystem.CampaignState st,
                                                              Faction faction,
                                                              String label) {
        LinkedHashMap<ShipRole, Integer> roles = new LinkedHashMap<>();
        for (CampaignSystem.CampaignShipPoolRecord record : st.campaignShipPool.values()) {
            if (record == null || record.faction != faction
                    || record.status == CampaignSystem.CampaignShipPoolStatus.DESTROYED) continue;
            roles.merge(record.role, 1, Integer::sum);
        }
        return List.of(label + " starting inventory by role: " + compactRoleCounts(roles));
    }

    private static String compactRoleCounts(Map<ShipRole, Integer> roles) {
        if (roles == null || roles.isEmpty()) return "none";
        ArrayList<String> parts = new ArrayList<>();
        for (Map.Entry<ShipRole, Integer> entry : roles.entrySet()) {
            parts.add(entry.getKey().name() + " " + entry.getValue());
        }
        return String.join(", ", parts);
    }
}

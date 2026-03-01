/**
 * Team helpers for multi-faction modes.
 */
public final class TeamSystem {
    private TeamSystem() {}

    public static boolean isFriendlyToPlayer(GameContext ctx, Faction faction) {
        if (ctx == null || ctx.player == null || faction == null) return false;
        return faction.isFriendlyTo(ctx.player.faction);
    }

    public static boolean isHostileToPlayer(GameContext ctx, Faction faction) {
        if (ctx == null || ctx.player == null || faction == null) return false;
        return !faction.isFriendlyTo(ctx.player.faction);
    }

    public static Ship getBaseForTeam(GameContext ctx, Faction team) {
        if (ctx == null || team == null) return null;
        Ship direct = ctx.teamBases.get(team);
        if (direct != null && direct.role == ShipRole.BASE && direct.faction == team && direct.hp > 0) {
            return direct;
        }
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (s.role != ShipRole.BASE) continue;
            if (s.faction != team) continue;
            if (s.hp <= 0) continue;
            return s;
        }
        return null;
    }

    public static boolean isTeamAlive(GameContext ctx, Faction team) {
        if (ctx == null || team == null) return false;

        Ship base = getBaseForTeam(ctx, team);
        boolean baseAlive = (base != null && base.alive && base.hp > 0);

        boolean hasShips = false;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.faction == null) continue;
            if (s.faction.teamId() != team.teamId()) continue;
            if (s.role == ShipRole.BASE) continue;
            hasShips = true;
            break;
        }

        return baseAlive || hasShips;
    }

    public static int countAliveTeams(GameContext ctx, Faction[] teams) {
        if (teams == null) return 0;
        int alive = 0;
        for (Faction f : teams) {
            if (isTeamAlive(ctx, f)) alive++;
        }
        return alive;
    }

    public static Faction getLastAliveTeam(GameContext ctx, Faction[] teams) {
        if (teams == null) return null;
        Faction last = null;
        for (Faction f : teams) {
            if (isTeamAlive(ctx, f)) last = f;
        }
        return last;
    }

    public static int countAliveShips(GameContext ctx, Faction team) {
        if (ctx == null || team == null) return 0;
        int count = 0;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.faction == null) continue;
            if (s.faction.teamId() != team.teamId()) continue;
            if (s.role == ShipRole.BASE) continue;
            count++;
        }
        return count;
    }

    public static int countAliveMiners(GameContext ctx, Faction team) {
        if (ctx == null || team == null) return 0;
        int count = 0;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.role != ShipRole.MINER) continue;
            if (s.faction == null) continue;
            if (s.faction.teamId() != team.teamId()) continue;
            count++;
        }
        return count;
    }

    public static Faction getShipCountLeader(GameContext ctx, Faction[] teams) {
        if (teams == null) return null;
        int best = -1;
        Faction leader = null;
        for (Faction f : teams) {
            int ships = countAliveShips(ctx, f);
            if (ships > best) {
                best = ships;
                leader = f;
            }
        }
        return leader;
    }
}

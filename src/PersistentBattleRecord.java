import java.util.List;

/**
 * Compact retained battle history. This is intentionally much smaller than
 * BattleResult so saves can keep recent context without storing raw telemetry.
 */
public final class PersistentBattleRecord {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_RETAINED_BATTLE_HISTORY = CampaignSystem.CAMPAIGN_AFTER_ACTION_HISTORY_CAP;

    public final int schemaVersion;
    public final String battleId;
    public final String title;
    public final String source;
    public final String result;
    public final String missionResult;
    public final String location;
    public final double durationSeconds;
    public final int friendlyLost;
    public final int friendlySurvived;
    public final int hostileDestroyed;
    public final String resources;
    public final String keyBattleFactors;
    public final String shipHistorySummary;
    public final long endedAtMillis;

    public PersistentBattleRecord(int schemaVersion,
                                  String battleId,
                                  String title,
                                  String source,
                                  String result,
                                  String missionResult,
                                  String location,
                                  double durationSeconds,
                                  int friendlyLost,
                                  int friendlySurvived,
                                  int hostileDestroyed,
                                  String resources,
                                  String keyBattleFactors,
                                  String shipHistorySummary,
                                  long endedAtMillis) {
        this.schemaVersion = Math.max(1, schemaVersion);
        this.battleId = BattleResult.trimmedOrFallback(battleId, "battle");
        this.title = BattleResult.trimmedOrFallback(title, "Battle Record");
        this.source = BattleResult.trimmedOrFallback(source, "UNKNOWN");
        this.result = BattleResult.trimmedOrFallback(result, "UNKNOWN");
        this.missionResult = BattleResult.trimmedOrFallback(missionResult, "UNKNOWN");
        this.location = BattleResult.trimmedOrFallback(location, "Unknown Theater");
        this.durationSeconds = Math.max(0.0, durationSeconds);
        this.friendlyLost = Math.max(0, friendlyLost);
        this.friendlySurvived = Math.max(0, friendlySurvived);
        this.hostileDestroyed = Math.max(0, hostileDestroyed);
        this.resources = BattleResult.trimmedOrFallback(resources, "Resources unavailable");
        this.keyBattleFactors = BattleResult.trimmedOrFallback(keyBattleFactors, "No key battle factors recorded.");
        this.shipHistorySummary = BattleResult.trimmedOrFallback(shipHistorySummary, "No ship history recorded.");
        this.endedAtMillis = Math.max(0L, endedAtMillis);
    }

    public static PersistentBattleRecord from(BattleResult result, AfterActionReport report) {
        AfterActionReport resolved = report == null ? BattleAnalysisService.analyze(result) : report;
        if (result == null) {
            return new PersistentBattleRecord(
                    SCHEMA_VERSION,
                    "missing",
                    resolved.title,
                    "UNKNOWN",
                    "UNKNOWN",
                    "UNKNOWN",
                    resolved.location,
                    0.0,
                    0,
                    0,
                    0,
                    resolved.resourcesSummary,
                    resolved.compactKeyFactors(),
                    "No battle result available.",
                    0L);
        }
        return new PersistentBattleRecord(
                SCHEMA_VERSION,
                result.battleId,
                resolved.title,
                result.source.name(),
                result.tacticalResult.name(),
                result.missionResult.name(),
                result.location,
                result.durationSeconds,
                result.friendlyShipsLost(),
                result.friendlyShipsSurvived(),
                result.hostileShipsDestroyed(),
                resolved.resourcesSummary,
                resolved.compactKeyFactors(),
                shipHistorySummary(result),
                result.endedAtMillis);
    }

    public List<String> toDisplayLines() {
        return List.of(
                title + "  |  " + location,
                "Result: " + result + "  |  Mission: " + missionResult,
                "Forces: friendly survived " + friendlySurvived
                        + "  lost " + friendlyLost
                        + "  hostiles destroyed " + hostileDestroyed,
                "Resources: " + resources,
                "Key Battle Factors: " + keyBattleFactors,
                "Ship History: " + shipHistorySummary);
    }

    public String toDebugText() {
        return String.join(System.lineSeparator(), toDisplayLines());
    }

    public String toDebugJson() {
        return "{"
                + "\"schemaVersion\":" + schemaVersion + ","
                + "\"battleId\":\"" + json(battleId) + "\","
                + "\"title\":\"" + json(title) + "\","
                + "\"source\":\"" + json(source) + "\","
                + "\"result\":\"" + json(result) + "\","
                + "\"missionResult\":\"" + json(missionResult) + "\","
                + "\"location\":\"" + json(location) + "\","
                + "\"durationSeconds\":" + durationSeconds + ","
                + "\"friendlyLost\":" + friendlyLost + ","
                + "\"friendlySurvived\":" + friendlySurvived + ","
                + "\"hostileDestroyed\":" + hostileDestroyed + ","
                + "\"resources\":\"" + json(resources) + "\","
                + "\"keyBattleFactors\":\"" + json(keyBattleFactors) + "\","
                + "\"shipHistorySummary\":\"" + json(shipHistorySummary) + "\","
                + "\"endedAtMillis\":" + endedAtMillis
                + "}";
    }

    private static String shipHistorySummary(BattleResult result) {
        if (result == null || result.ships.isEmpty()) return "No ship history recorded.";
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (BattleResult.ShipSnapshot ship : result.ships) {
            if (ship == null || !ship.isFriendlyTo(result.playerFaction)) continue;
            if (count++ > 0) out.append(" | ");
            out.append(ship.stableShipId)
                    .append(' ')
                    .append(ship.name)
                    .append(" hull ")
                    .append(ship.startHull)
                    .append("->")
                    .append(ship.endHull);
            if (ship.kills > 0) out.append(" kills ").append(ship.kills);
            if (ship.destroyed) out.append(" lost");
            if (ship.withdrew) out.append(" withdrew");
            if (count >= 8) break;
        }
        return out.length() == 0 ? "No friendly ship history recorded." : out.toString();
    }

    private static String json(String value) {
        String v = value == null ? "" : value;
        StringBuilder out = new StringBuilder(v.length() + 8);
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(' ');
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }
}

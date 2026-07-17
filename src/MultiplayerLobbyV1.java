/** Host-owned lobby and match setup state machine for V1 custom-battle multiplayer. */
public final class MultiplayerLobbyV1 {
    public enum LobbyState {
        CONNECTING,
        IN_LOBBY,
        READY,
        COUNTDOWN,
        LOADING,
        IN_MATCH,
        MATCH_ENDED,
        RETURNING_TO_LOBBY,
        DISCONNECTED
    }

    public record MenuEntryModel(boolean enabled,
                                 String hostBattleLabel,
                                 String joinBattleLabel,
                                 String directAddressPlaceholder,
                                 String defaultHostName,
                                 String defaultClientName,
                                 Faction hostTeam,
                                 Faction clientTeam,
                                 boolean hullSelectionEnabled) {
        public MenuEntryModel {
            hostBattleLabel = clean(hostBattleLabel, "Host Battle");
            joinBattleLabel = clean(joinBattleLabel, "Join Battle");
            directAddressPlaceholder = clean(directAddressPlaceholder, "127.0.0.1:" + MultiplayerLanTransportV1.DEFAULT_PORT);
            defaultHostName = clean(defaultHostName, "Host");
            defaultClientName = clean(defaultClientName, "Client");
            if (hostTeam == null) hostTeam = Faction.ALLY;
            if (clientTeam == null) clientTeam = Faction.ENEMY;
        }
    }

    public record LobbyResult(boolean accepted, String reason) {
        public LobbyResult {
            reason = clean(reason, accepted ? "Accepted" : "Rejected");
        }
    }

    public record MatchStartSync(long lobbyRevision,
                                 MultiplayerRulesV1.BattleSetup setup,
                                 MultiplayerProtocolV1.CompatibilityFingerprint fingerprint) {
        public MatchStartSync {
            lobbyRevision = Math.max(0L, lobbyRevision);
            if (fingerprint == null) fingerprint = MultiplayerProtocolV1.localFingerprint();
        }
    }

    private String hostName;
    private String clientName;
    private ShipRole hostHull;
    private ShipRole clientHull;
    private long seed;
    private boolean hostReady;
    private boolean clientReady;
    private boolean locked;
    private long revision;
    private LobbyState state = LobbyState.CONNECTING;
    private MultiplayerRulesV1.BattleSetup lockedSetup;
    private MultiplayerLanTransportV1.DirectAddress directAddress =
            new MultiplayerLanTransportV1.DirectAddress("127.0.0.1", MultiplayerLanTransportV1.DEFAULT_PORT);

    public MultiplayerLobbyV1(String hostName, String clientName, ShipRole hostHull, ShipRole clientHull) {
        this.hostName = clean(hostName, "Host");
        this.clientName = clean(clientName, "Client");
        this.hostHull = directHullOrDefault(hostHull);
        this.clientHull = directHullOrDefault(clientHull);
        this.seed = 0L;
        this.state = LobbyState.IN_LOBBY;
    }

    public static MenuEntryModel menuEntryModel() {
        return new MenuEntryModel(
                MultiplayerRulesV1.entryPointEnabled(),
                "Host Battle",
                "Join Battle",
                "127.0.0.1:" + MultiplayerLanTransportV1.DEFAULT_PORT,
                "Host",
                "Client",
                Faction.ALLY,
                Faction.ENEMY,
                true);
    }

    public LobbyResult join(String requestedName) {
        if (locked || state == LobbyState.LOADING || state == LobbyState.IN_MATCH || state == LobbyState.MATCH_ENDED) {
            return new LobbyResult(false, "Match already in progress");
        }
        clientName = clean(requestedName, "Client");
        state = LobbyState.IN_LOBBY;
        revision++;
        clearReady();
        return new LobbyResult(true, "Client joined lobby");
    }

    public LobbyResult setDirectAddress(String addressText) {
        if (locked) return new LobbyResult(false, "Match configuration is locked");
        directAddress = MultiplayerLanTransportV1.parseDirectAddress(addressText);
        revision++;
        clearReady();
        return new LobbyResult(true, "Direct connect address updated");
    }

    public LobbyResult hostSetHull(int slotId, ShipRole hull) {
        if (locked) return new LobbyResult(false, "Match configuration is locked");
        ShipRole safeHull = directHullOrDefault(hull);
        if (slotId == MultiplayerRulesV1.HOST_SLOT_ID) {
            hostHull = safeHull;
        } else if (slotId == MultiplayerRulesV1.CLIENT_SLOT_ID) {
            clientHull = safeHull;
        } else {
            return new LobbyResult(false, "Unknown player slot");
        }
        revision++;
        clearReady();
        return new LobbyResult(true, "Hull selection updated");
    }

    public LobbyResult hostSetSeed(long seed) {
        if (locked) return new LobbyResult(false, "Match configuration is locked");
        this.seed = seed;
        revision++;
        clearReady();
        return new LobbyResult(true, "Match seed updated");
    }

    public LobbyResult clientSetHull(ShipRole ignored) {
        return new LobbyResult(false, "Lobby settings are host-authoritative");
    }

    public LobbyResult setReady(int slotId, boolean ready) {
        if (locked) return new LobbyResult(false, "Match configuration is locked");
        if (slotId == MultiplayerRulesV1.HOST_SLOT_ID) {
            hostReady = ready;
        } else if (slotId == MultiplayerRulesV1.CLIENT_SLOT_ID) {
            clientReady = ready;
        } else {
            return new LobbyResult(false, "Unknown player slot");
        }
        state = (hostReady && clientReady) ? LobbyState.READY : LobbyState.IN_LOBBY;
        return new LobbyResult(true, ready ? "Player ready" : "Player not ready");
    }

    public LobbyResult lockForCountdown() {
        if (!hostReady || !clientReady) return new LobbyResult(false, "Both players must be ready");
        MultiplayerRulesV1.ValidationResult validation = MultiplayerRulesV1.validate(currentSetup());
        if (!validation.accepted()) return new LobbyResult(false, validation.message());
        locked = true;
        lockedSetup = currentSetup();
        state = LobbyState.COUNTDOWN;
        return new LobbyResult(true, "Match configuration locked");
    }

    public MatchStartSync startLoading() {
        if (!locked || lockedSetup == null) {
            throw new IllegalStateException("Match must be locked before loading");
        }
        state = LobbyState.LOADING;
        return new MatchStartSync(revision, lockedSetup, MultiplayerProtocolV1.localFingerprint());
    }

    public MultiplayerRulesV1.BattleSetup startMatch() {
        if (state != LobbyState.LOADING) {
            throw new IllegalStateException("Match must be loading before match start");
        }
        state = LobbyState.IN_MATCH;
        return lockedSetup;
    }

    public void endMatch() {
        state = LobbyState.MATCH_ENDED;
    }

    public void returnToLobby() {
        locked = false;
        lockedSetup = null;
        hostReady = false;
        clientReady = false;
        state = LobbyState.RETURNING_TO_LOBBY;
    }

    public void disconnect() {
        locked = false;
        state = LobbyState.DISCONNECTED;
    }

    public MultiplayerRulesV1.BattleSetup currentSetup() {
        return setup();
    }

    private MultiplayerRulesV1.BattleSetup setup() {
        return new MultiplayerRulesV1.BattleSetup(
                seed,
                MultiplayerRulesV1.DEFAULT_ARENA_ID,
                new MultiplayerRulesV1.PlayerSlot(MultiplayerRulesV1.HOST_SLOT_ID, Faction.ALLY, hostHull, hostName),
                new MultiplayerRulesV1.PlayerSlot(MultiplayerRulesV1.CLIENT_SLOT_ID, Faction.ENEMY, clientHull, clientName),
                MultiplayerRulesV1.VictoryRule.ELIMINATION,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false);
    }

    public LobbyState state() {
        return state;
    }

    public long revision() {
        return revision;
    }

    public boolean locked() {
        return locked;
    }

    public boolean hostReady() {
        return hostReady;
    }

    public boolean clientReady() {
        return clientReady;
    }

    public MultiplayerLanTransportV1.DirectAddress directAddress() {
        return directAddress;
    }

    private void clearReady() {
        hostReady = false;
        clientReady = false;
        if (!locked) state = LobbyState.IN_LOBBY;
    }

    private static ShipRole directHullOrDefault(ShipRole role) {
        if (role == null || role == ShipRole.BASE || role == ShipRole.STATIC_TURRET) return ShipRole.FRIGATE;
        return role;
    }

    private static String clean(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}

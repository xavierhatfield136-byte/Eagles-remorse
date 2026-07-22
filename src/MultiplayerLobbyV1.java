import app.config.MultiplayerMissionChoice;
import app.config.MultiplayerLaunchConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Host-owned lobby and match setup state machine for V1 custom-battle multiplayer. */
public final class MultiplayerLobbyV1 {
    public enum LobbyState {
        CREATED,
        OPEN,
        LOCKED,
        LOADING,
        READY_TO_START,
        STARTING,
        IN_MATCH,
        POST_MATCH,
        CLOSING,
        CLOSED
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
                                 MultiplayerProtocolV1.MatchIdentity identity,
                                 MultiplayerRulesV1.BattleSetup setup,
                                 MultiplayerProtocolV1.CompatibilityFingerprint fingerprint) {
        public MatchStartSync {
            lobbyRevision = Math.max(0L, lobbyRevision);
            if (identity == null) {
                identity = new MultiplayerProtocolV1.MatchIdentity(
                        "lobby:local",
                        "match:local",
                        MultiplayerProtocolV1.sessionNonceForMatch("match:local"),
                        lobbyRevision);
            }
            if (fingerprint == null) fingerprint = MultiplayerProtocolV1.localFingerprint();
        }
    }

    public record LobbyMissionConfig(String missionId,
                                     int missionRevision,
                                     long seed,
                                     int worldW,
                                     int worldH,
                                     ShipRole hostHull,
                                     ShipRole clientHull,
                                     String rulesProfileId,
                                     String cosmeticDisplayText) {
        public LobbyMissionConfig(String missionId,
                                  long seed,
                                  int worldW,
                                  int worldH,
                                  ShipRole hostHull,
                                  ShipRole clientHull) {
            this(missionId, defaultMissionRevision(missionId), seed, worldW, worldH, hostHull, clientHull,
                    MultiplayerRulesV1.RULES_PROFILE_ID,
                    defaultMissionDisplayText(missionId));
        }

        public LobbyMissionConfig {
            missionId = MultiplayerMissionChoice.fromMissionId(missionId).missionId();
            missionRevision = Math.max(1, missionRevision);
            seed = Math.max(0L, seed);
            worldW = clampWorldSize(worldW);
            worldH = clampWorldSize(worldH);
            hostHull = directHullOrDefault(hostHull);
            clientHull = directHullOrDefault(clientHull);
            rulesProfileId = clean(rulesProfileId, MultiplayerRulesV1.RULES_PROFILE_ID);
            cosmeticDisplayText = clean(cosmeticDisplayText, defaultMissionDisplayText(missionId));
        }

        public LobbyMissionConfig withMissionId(String missionId) {
            return new LobbyMissionConfig(missionId, defaultMissionRevision(missionId),
                    seed, worldW, worldH, hostHull, clientHull,
                    rulesProfileId, defaultMissionDisplayText(missionId));
        }

        public LobbyMissionConfig withMissionRevision(int missionRevision) {
            return new LobbyMissionConfig(missionId, missionRevision, seed, worldW, worldH,
                    hostHull, clientHull, rulesProfileId, cosmeticDisplayText);
        }

        public LobbyMissionConfig withSeed(long seed) {
            return new LobbyMissionConfig(missionId, missionRevision, seed, worldW, worldH,
                    hostHull, clientHull, rulesProfileId, cosmeticDisplayText);
        }

        public LobbyMissionConfig withWorldSize(int worldW, int worldH) {
            return new LobbyMissionConfig(missionId, missionRevision, seed, worldW, worldH,
                    hostHull, clientHull, rulesProfileId, cosmeticDisplayText);
        }

        public LobbyMissionConfig withHull(int slotId, ShipRole hull) {
            if (slotId == MultiplayerRulesV1.HOST_SLOT_ID) {
                return new LobbyMissionConfig(missionId, missionRevision, seed, worldW, worldH,
                        hull, clientHull, rulesProfileId, cosmeticDisplayText);
            }
            if (slotId == MultiplayerRulesV1.CLIENT_SLOT_ID) {
                return new LobbyMissionConfig(missionId, missionRevision, seed, worldW, worldH,
                        hostHull, hull, rulesProfileId, cosmeticDisplayText);
            }
            return this;
        }

        public LobbyMissionConfig withRulesProfileId(String rulesProfileId) {
            return new LobbyMissionConfig(missionId, missionRevision, seed, worldW, worldH,
                    hostHull, clientHull, rulesProfileId, cosmeticDisplayText);
        }

        public LobbyMissionConfig withCosmeticDisplayText(String cosmeticDisplayText) {
            return new LobbyMissionConfig(missionId, missionRevision, seed, worldW, worldH,
                    hostHull, clientHull, rulesProfileId, cosmeticDisplayText);
        }
    }

    public record LockedDuelConfig(String lobbyId,
                                   String matchId,
                                   String sessionNonce,
                                   long lobbyRevision,
                                   int worldW,
                                   int worldH,
                                   MultiplayerRulesV1.BattleSetup setup,
                                   LobbyMissionConfig missionConfig) {
        public LockedDuelConfig {
            lobbyId = clean(lobbyId, "lobby:local");
            matchId = clean(matchId, "match:local");
            sessionNonce = clean(sessionNonce, MultiplayerProtocolV1.sessionNonceForMatch(matchId));
            lobbyRevision = Math.max(0L, lobbyRevision);
            worldW = clampWorldSize(worldW);
            worldH = clampWorldSize(worldH);
            if (missionConfig == null) {
                missionConfig = new LobbyMissionConfig(
                        MultiplayerMissionChoice.DEFAULT_MISSION_ID,
                        setup == null ? 0L : setup.seed(),
                        worldW,
                        worldH,
                        setup == null ? ShipRole.FRIGATE : setup.hostSlot().hull(),
                        setup == null ? ShipRole.FRIGATE : setup.clientSlot().hull());
            }
        }
    }

    private String hostName;
    private String clientName;
    private boolean clientConnected;
    private LobbyMissionConfig missionConfig;
    private boolean hostReady;
    private boolean clientReady;
    private long hostReadyRevision = -1L;
    private long clientReadyRevision = -1L;
    private boolean locked;
    private long revision;
    private long matchCounter;
    private final String lobbyId;
    private String activeMatchId;
    private String activeSessionNonce;
    private final ArrayList<String> observabilityLog = new ArrayList<>();
    private LobbyState state = LobbyState.CREATED;
    private MultiplayerRulesV1.BattleSetup lockedSetup;
    private LockedDuelConfig lockedDuelConfig;
    private MultiplayerLanTransportV1.DirectAddress directAddress =
            new MultiplayerLanTransportV1.DirectAddress("127.0.0.1", MultiplayerLanTransportV1.DEFAULT_PORT);

    public MultiplayerLobbyV1(String hostName, String clientName, ShipRole hostHull, ShipRole clientHull) {
        this.hostName = clean(hostName, "Host");
        this.clientName = clean(clientName, "Client");
        this.lobbyId = "lobby-" + UUID.randomUUID();
        this.activeMatchId = "";
        this.activeSessionNonce = "";
        this.missionConfig = new LobbyMissionConfig(
                MultiplayerMissionChoice.DEFAULT_MISSION_ID,
                0L,
                3600,
                2200,
                hostHull,
                clientHull);
        this.state = LobbyState.OPEN;
        log("lobby created lobbyId=" + lobbyId + " revision=" + revision);
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

    public synchronized LobbyResult join(String requestedName) {
        if (state != LobbyState.OPEN) {
            return new LobbyResult(false, "Match already in progress");
        }
        clientName = clean(requestedName, "Client");
        clientConnected = true;
        state = LobbyState.OPEN;
        revision++;
        clearReady();
        log("client joined name=" + clientName + " revision=" + revision);
        return new LobbyResult(true, "Client joined lobby");
    }

    public synchronized LobbyResult clientDisconnected(String reason) {
        if (!clientConnected && !clientReady) {
            return new LobbyResult(true, "Client slot already empty");
        }
        clientConnected = false;
        clearReady();
        if (!locked) state = LobbyState.OPEN;
        revision++;
        log("disconnect reason=" + clean(reason, "client_disconnected").replace(' ', '_')
                + " slot=" + MultiplayerRulesV1.CLIENT_SLOT_ID
                + " revision=" + revision);
        return new LobbyResult(true, "Client slot cleared");
    }

    public synchronized LobbyResult setDirectAddress(String addressText) {
        if (!settingsEditable()) return new LobbyResult(false, "Match configuration is locked");
        directAddress = MultiplayerLanTransportV1.parseDirectAddress(addressText);
        revision++;
        clearReady();
        log("lobby revision changed reason=direct_address revision=" + revision
                + " address=" + directAddress);
        return new LobbyResult(true, "Direct connect address updated");
    }

    public synchronized LobbyResult hostSetHull(int slotId, ShipRole hull) {
        if (!settingsEditable()) return new LobbyResult(false, "Match configuration is locked");
        if (slotId != MultiplayerRulesV1.HOST_SLOT_ID && slotId != MultiplayerRulesV1.CLIENT_SLOT_ID) {
            return new LobbyResult(false, "Unknown player slot");
        }
        return hostSetMissionConfig(missionConfig.withHull(slotId, hull), "Hull selection updated");
    }

    public synchronized LobbyResult hostSetSeed(long seed) {
        if (!settingsEditable()) return new LobbyResult(false, "Match configuration is locked");
        return hostSetMissionConfig(missionConfig.withSeed(seed), "Match seed updated");
    }

    public synchronized LobbyResult hostSetMissionRevision(int missionRevision) {
        if (!settingsEditable()) return new LobbyResult(false, "Match configuration is locked");
        return hostSetMissionConfig(missionConfig.withMissionRevision(missionRevision),
                "Mission revision updated");
    }

    public synchronized LobbyResult hostSetWorldSize(int worldW, int worldH) {
        if (!settingsEditable()) return new LobbyResult(false, "Match configuration is locked");
        return hostSetMissionConfig(missionConfig.withWorldSize(worldW, worldH), "World size updated");
    }

    public synchronized LobbyResult hostSetRulesProfileId(String rulesProfileId) {
        if (!settingsEditable()) return new LobbyResult(false, "Match configuration is locked");
        return hostSetMissionConfig(missionConfig.withRulesProfileId(rulesProfileId),
                "Rules profile updated");
    }

    public synchronized LobbyResult hostSetCosmeticDisplayText(String cosmeticDisplayText) {
        if (!settingsEditable()) return new LobbyResult(false, "Match configuration is locked");
        LobbyMissionConfig next = missionConfig.withCosmeticDisplayText(cosmeticDisplayText);
        if (next.equals(missionConfig)) return new LobbyResult(true, "Cosmetic display text unchanged");
        missionConfig = next;
        log("lobby cosmetic display changed mission=" + missionConfig.missionId()
                + " text=" + missionConfig.cosmeticDisplayText().replace(' ', '_'));
        return new LobbyResult(true, "Cosmetic display text updated");
    }

    public synchronized LobbyResult hostSetMissionConfig(LobbyMissionConfig config) {
        return hostSetMissionConfig(config, "Mission settings updated");
    }

    private LobbyResult hostSetMissionConfig(LobbyMissionConfig config, String reason) {
        if (!settingsEditable()) return new LobbyResult(false, "Match configuration is locked");
        LobbyMissionConfig next = config == null ? missionConfig : config;
        if (next.equals(missionConfig)) return new LobbyResult(true, "Mission settings unchanged");
        missionConfig = next;
        revision++;
        clearReady();
        log("lobby revision changed reason=" + reason.replace(' ', '_')
                + " revision=" + revision
                + " mission=" + missionConfig.missionId()
                + " missionRevision=" + missionConfig.missionRevision()
                + " rulesProfile=" + missionConfig.rulesProfileId()
                + " seed=" + missionConfig.seed()
                + " world=" + missionConfig.worldW() + "x" + missionConfig.worldH());
        return new LobbyResult(true, reason);
    }

    public synchronized LobbyResult clientSetHull(ShipRole ignored) {
        return new LobbyResult(false, "Lobby settings are host-authoritative");
    }

    public synchronized LobbyResult setClientDisplayName(String requestedName) {
        if (!settingsEditable()) return new LobbyResult(false, "Match configuration is locked");
        String next = clean(requestedName, "Client");
        if (next.equals(clientName)) return new LobbyResult(true, "Client name unchanged");
        clientName = next;
        revision++;
        log("lobby revision changed reason=client_name revision=" + revision + " client=" + clientName);
        return new LobbyResult(true, "Client name updated");
    }

    public synchronized LobbyResult setReady(int slotId, boolean ready) {
        return setReady(slotId, ready, revision);
    }

    public synchronized LobbyResult setReady(int slotId, boolean ready, long acceptedRevision) {
        if (!readyEditable()) return new LobbyResult(false, "Match configuration is locked");
        if (ready && acceptedRevision != revision) {
            return new LobbyResult(false, "Lobby settings changed; review the current mission before readying");
        }
        if (slotId == MultiplayerRulesV1.HOST_SLOT_ID) {
            hostReady = ready;
            hostReadyRevision = ready ? acceptedRevision : -1L;
        } else if (slotId == MultiplayerRulesV1.CLIENT_SLOT_ID) {
            clientReady = ready;
            clientReadyRevision = ready ? acceptedRevision : -1L;
        } else {
            return new LobbyResult(false, "Unknown player slot");
        }
        state = (hostReady && clientReady) ? LobbyState.READY_TO_START : LobbyState.OPEN;
        log("player " + (ready ? "ready" : "unready")
                + " slot=" + slotId
                + " acceptedRevision=" + acceptedRevision
                + " lobbyRevision=" + revision);
        return new LobbyResult(true, ready ? "Player ready" : "Player not ready");
    }

    public synchronized LobbyResult lockForCountdown() {
        if (!hostReady || !clientReady) return new LobbyResult(false, "Both players must be ready");
        if (state != LobbyState.READY_TO_START) return new LobbyResult(false, "Lobby is not ready to start");
        MultiplayerRulesV1.ValidationResult validation = MultiplayerRulesV1.validate(currentSetup());
        if (!validation.accepted()) return new LobbyResult(false, validation.message());
        locked = true;
        lockedSetup = currentSetup();
        activeMatchId = nextMatchId();
        activeSessionNonce = MultiplayerProtocolV1.sessionNonceForMatch(activeMatchId);
        lockedDuelConfig = new LockedDuelConfig(
                lobbyId, activeMatchId, activeSessionNonce, revision,
                missionConfig.worldW(), missionConfig.worldH(), lockedSetup, missionConfig);
        state = LobbyState.LOCKED;
        log("mission locked lobbyId=" + lobbyId
                + " matchId=" + activeMatchId
                + " lockedRevision=" + revision
                + " mission=" + missionConfig.missionId());
        log("match specification hash digest=" + lockedLaunchSpecDigest());
        log("player slot assignment hostSlot=" + MultiplayerRulesV1.HOST_SLOT_ID
                + " clientSlot=" + MultiplayerRulesV1.CLIENT_SLOT_ID
                + " hostPlayerId=" + MultiplayerProtocolV1.playerIdForSlot(MultiplayerRulesV1.HOST_SLOT_ID)
                + " clientPlayerId=" + MultiplayerProtocolV1.playerIdForSlot(MultiplayerRulesV1.CLIENT_SLOT_ID));
        return new LobbyResult(true, "Match configuration locked");
    }

    public synchronized MatchStartSync startLoading() {
        if (!locked || lockedSetup == null || state != LobbyState.LOCKED) {
            throw new IllegalStateException("Match must be locked before loading");
        }
        state = LobbyState.LOADING;
        return new MatchStartSync(revision, matchIdentity(), lockedSetup, MultiplayerProtocolV1.localFingerprint());
    }

    public synchronized MultiplayerRulesV1.BattleSetup startMatch() {
        if (state != LobbyState.LOADING) {
            throw new IllegalStateException("Match must be loading before match start");
        }
        state = LobbyState.STARTING;
        state = LobbyState.IN_MATCH;
        log("match started matchId=" + activeMatchId + " lockedRevision=" + revision);
        return lockedSetup;
    }

    public synchronized void endMatch() {
        state = LobbyState.POST_MATCH;
        log("match result matchId=" + activeMatchId + " state=MATCH_ENDED");
    }

    public synchronized void returnToLobby() {
        locked = false;
        lockedSetup = null;
        lockedDuelConfig = null;
        activeMatchId = "";
        activeSessionNonce = "";
        hostReady = false;
        clientReady = false;
        hostReadyRevision = -1L;
        clientReadyRevision = -1L;
        state = LobbyState.OPEN;
        log("cleanup completed lobbyId=" + lobbyId + " returnedToLobby=true");
    }

    public synchronized void disconnect() {
        state = LobbyState.CLOSING;
        locked = false;
        lockedSetup = null;
        lockedDuelConfig = null;
        activeMatchId = "";
        activeSessionNonce = "";
        clientConnected = false;
        hostReady = false;
        clientReady = false;
        hostReadyRevision = -1L;
        clientReadyRevision = -1L;
        state = LobbyState.CLOSED;
        log("disconnect reason=lobby_disconnected lobbyId=" + lobbyId);
    }

    public synchronized void recordCompatibilityResult(boolean accepted, String reason) {
        log("compatibility " + (accepted ? "accepted" : "rejected")
                + " reason=" + clean(reason, accepted ? "Compatible" : "Incompatible").replace(' ', '_'));
    }

    public synchronized MultiplayerRulesV1.BattleSetup currentSetup() {
        return setup();
    }

    private MultiplayerRulesV1.BattleSetup setup() {
        return new MultiplayerRulesV1.BattleSetup(
                missionConfig.seed(),
                MultiplayerRulesV1.DEFAULT_ARENA_ID,
                new MultiplayerRulesV1.PlayerSlot(
                        MultiplayerRulesV1.HOST_SLOT_ID, Faction.ALLY, missionConfig.hostHull(), hostName),
                new MultiplayerRulesV1.PlayerSlot(
                        MultiplayerRulesV1.CLIENT_SLOT_ID, Faction.ENEMY, missionConfig.clientHull(), clientName),
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

    public synchronized LobbyState state() {
        return state;
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized boolean locked() {
        return locked;
    }

    public synchronized boolean hostReady() {
        return hostReady;
    }

    public synchronized boolean clientReady() {
        return clientReady;
    }

    public synchronized boolean clientConnected() {
        return clientConnected;
    }

    public synchronized long hostReadyRevision() {
        return hostReadyRevision;
    }

    public synchronized long clientReadyRevision() {
        return clientReadyRevision;
    }

    public synchronized int worldW() {
        return missionConfig.worldW();
    }

    public synchronized int worldH() {
        return missionConfig.worldH();
    }

    public synchronized LobbyMissionConfig missionConfig() {
        return missionConfig;
    }

    public synchronized String hostName() {
        return hostName;
    }

    public synchronized String clientName() {
        return clientName;
    }

    public synchronized LockedDuelConfig lockedDuelConfig() {
        return lockedDuelConfig;
    }

    public synchronized String lobbyId() {
        return lobbyId;
    }

    public synchronized String activeMatchId() {
        return activeMatchId;
    }

    public synchronized String activeSessionNonce() {
        return activeSessionNonce;
    }

    public synchronized MultiplayerProtocolV1.MatchIdentity matchIdentity() {
        LockedDuelConfig lockedConfig = lockedDuelConfig;
        if (lockedConfig != null) {
            return new MultiplayerProtocolV1.MatchIdentity(
                    lockedConfig.lobbyId(),
                    lockedConfig.matchId(),
                    lockedConfig.sessionNonce(),
                    lockedConfig.lobbyRevision());
        }
        return new MultiplayerProtocolV1.MatchIdentity(
                lobbyId,
                activeMatchId,
                activeSessionNonce,
                revision);
    }

    public synchronized MultiplayerLanTransportV1.DirectAddress directAddress() {
        return directAddress;
    }

    public synchronized List<String> observabilityLog() {
        return List.copyOf(observabilityLog);
    }

    private void clearReady() {
        hostReady = false;
        clientReady = false;
        hostReadyRevision = -1L;
        clientReadyRevision = -1L;
        if (!locked) state = LobbyState.OPEN;
    }

    private boolean settingsEditable() {
        return state == LobbyState.OPEN || state == LobbyState.READY_TO_START;
    }

    private boolean readyEditable() {
        return state == LobbyState.OPEN || state == LobbyState.READY_TO_START;
    }

    private String nextMatchId() {
        matchCounter++;
        return lobbyId + "-match-" + matchCounter;
    }

    private String lockedLaunchSpecDigest() {
        MissionLaunchSpec spec = MultiplayerPlayableDuelContextFactory.resolveMissionSpec(
                MultiplayerLaunchConfig.host(MultiplayerLanTransportV1.DEFAULT_PORT, "")
                        .withMissionSettings(missionConfig.missionId(), missionConfig.seed(),
                                missionConfig.worldW(), missionConfig.worldH()),
                missionConfig.seed());
        return MissionDigest.lockedLaunchSpecDigest(spec, revision);
    }

    private void log(String line) {
        observabilityLog.add(clean(line, "multiplayer lobby event"));
    }

    private static ShipRole directHullOrDefault(ShipRole role) {
        if (role == null || role == ShipRole.BASE || role == ShipRole.STATIC_TURRET) return ShipRole.FRIGATE;
        return role;
    }

    private static String clean(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static int clampWorldSize(int value) {
        return Math.max(1800, Math.min(60000, value));
    }

    private static int defaultMissionRevision(String missionId) {
        CustomMissionDescriptor descriptor = CustomMissionCatalog.descriptorFor(
                MultiplayerMissionChoice.fromMissionId(missionId).missionId());
        return descriptor == null ? 1 : descriptor.revision();
    }

    private static String defaultMissionDisplayText(String missionId) {
        CustomMissionDescriptor descriptor = CustomMissionCatalog.descriptorFor(
                MultiplayerMissionChoice.fromMissionId(missionId).missionId());
        return descriptor == null ? "Custom mission" : descriptor.displayName();
    }
}

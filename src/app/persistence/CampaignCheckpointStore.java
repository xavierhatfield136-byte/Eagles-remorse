package app.persistence;

import app.config.GameConfig;
import app.config.GameMode;
import app.support.ErrorLog;
import app.support.UserDataPaths;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Persists resumable campaign checkpoints between sector transitions.
 */
public final class CampaignCheckpointStore {
    private static final String DEFAULT_BRANCH_ROUTE = "BALANCED";
    private static final String DEFAULT_PLAYER_FACTION_NAME = "PLAYER";
    private static final String DEFAULT_PLAYER_ROLE_NAME = "FRIGATE";
    private static final String DEFAULT_PRIMARY_WEAPON_FAMILY_NAME = "ENERGY_BOLT";
    private static final String DEFAULT_POWER_PRESET_NAME = "BALANCED";
    private static final String DEFAULT_CREW_ORDER_NAME = "BALANCED";
    private static final String DEFAULT_ENGINEERING_PRIORITY_NAME = "BALANCED";
    private static final String DEFAULT_OVERLOAD_BUS_NAME = "TACTICAL";
    private static final String DEFAULT_POWER_BUSES = "0.18,0.18,0.19,0.15,0.18,0.12";
    private static final String DEFAULT_CARRIER_COMMAND_MODE_NAME = "ATTACK";
    private static final String DEFAULT_OWNED_TITANS = "";
    private static final String DEFAULT_PERSISTENT_BLUE_FLEET = "";

    private CampaignCheckpointStore() {}

    private static final int CURRENT_VERSION = 5;
    private static final int MAX_CAMPAIGN_SECTORS = 24;
    private static final Path SAVE_DIR = UserDataPaths.saveDir();
    private static final Path CHECKPOINT_FILE = Paths.get(
            System.getProperty("codex.checkpointFile", SAVE_DIR.resolve("campaign_checkpoint.properties").toString()));
    private static final String SLOT_PRIMARY = "primary";
    private static final String AUTOSAVE_PREFIX = "autosave-";
    private static final Object IO_LOCK = new Object();

    public static final class SlotSummary {
        public final String id;
        public final String label;
        public final String summary;
        public final boolean autosave;
        public final boolean recoverable;

        SlotSummary(String id, String label, String summary, boolean autosave, boolean recoverable) {
            this.id = id;
            this.label = label;
            this.summary = summary;
            this.autosave = autosave;
            this.recoverable = recoverable;
        }
    }

    public static final class Checkpoint {
        public int version = CURRENT_VERSION;
        public int sourceVersion = CURRENT_VERSION;
        public boolean migrationApplied = false;
        public String migrationRepairs = "";
        public String migrationMessage = "";

        public int worldW = 5000;
        public int worldH = 5000;
        public boolean randomEvents = true;
        public long seed = 0L;

        public int nextSector = 2;
        public int credits = 0;
        public int sectorsCleared = 0;
        public int campaignKills = 0;
        public int branchScore = 0;
        public String branchRoute = DEFAULT_BRANCH_ROUTE;
        public int sideObjectivesCompletedTotal = 0;
        public int sideObjectivesFailedTotal = 0;

        public boolean unlockAuxGunGranted = false;
        public int unlockMissileTierGranted = 0;
        public boolean unlockCiwsGranted = false;
        public boolean unlockHullGranted = false;
        public boolean bossDropAegisArray = false;
        public boolean bossDropMissileCore = false;
        public boolean bossDropFlagCore = false;
        public int bossDropsCollected = 0;

        public String playerFactionName = DEFAULT_PLAYER_FACTION_NAME;
        public String playerRoleName = DEFAULT_PLAYER_ROLE_NAME;
        public String primaryWeaponFamilyName = DEFAULT_PRIMARY_WEAPON_FAMILY_NAME;
        public int hpMax = 10;
        public double shieldMax = 0.0;
        public double shieldRegen = 0.0;
        public boolean shieldActive = false;
        public int campaignOre = 0;
        public int cargo = 0;
        public int cargoMax = 120;
        public double miningRate = 10.0;
        public double miningRange = 56.0;
        public double orePriceBaseMul = 1.0;
        public double miningBaseMul = 1.0;
        public boolean hasCIWS = false;
        public double ciwsRange = 200.0;
        public double ciwsCooldown = 0.12;
        public double ciwsQuality = 0.35;
        public int ciwsPelletsPerBurst = 2;
        public double ciwsPelletSpeed = 920.0;
        public int ciwsPelletDamage = 1;
        public int ciwsPelletLife = 18;
        public double ciwsPelletRadius = 1.8;
        public String powerPresetName = DEFAULT_POWER_PRESET_NAME;
        public String crewOrderName = DEFAULT_CREW_ORDER_NAME;
        public String engineeringPriorityName = DEFAULT_ENGINEERING_PRIORITY_NAME;
        public String overloadBusName = DEFAULT_OVERLOAD_BUS_NAME;
        public String powerBuses = DEFAULT_POWER_BUSES;
        public String turretData = "";
        public boolean isCarrier = false;
        public int maxFighters = 4;
        public String carrierCommandModeName = DEFAULT_CARRIER_COMMAND_MODE_NAME;
        public boolean carrierAutoLaunch = true;
        public String flightDeckLoadout = "";
        public String ownedTitans = DEFAULT_OWNED_TITANS;
        public String persistentBlueFleet = DEFAULT_PERSISTENT_BLUE_FLEET;
        public String campaignYardOrders = "";
        public int nextCampaignYardOrderId = 1;
        public String selectedStrategicOverlayId = "CONTROL";
        public String factionAttackCommitments = "";
        public int selectedCampaignTaskGroupId = 0;
        public int escortCapUpgradeLevel = 0;
        public int lineCapUpgradeLevel = 0;
        public int capitalCapUpgradeLevel = 0;
        public boolean campaignBlueYellowAlliance = false;
        public boolean greenContractFleetJoined = false;
        public boolean yellowLiberationFleetJoined = false;
        public int greenContractFavor = 0;
        public int yellowLiberationFavor = 0;
        public double fleetStrain = 0.0;
        public String vossRelationshipStateId = "";
        public String marrRelationshipStateId = "";
        public String rookRelationshipStateId = "";
        public boolean strategicOvermapMode = false;
        public String currentGalaxyLocationId = "";
        public String selectedGalaxyLocationId = "";
        public String dockedGalaxyLocationId = "";
        public String activeGalaxyEncounterLocationId = "";
        public int activeGalaxyEncounterSearchGroupId = 0;
        public String activeGalaxyEncounterForceIds = "";
        public int activeGalaxyEncounterParentForceId = 0;
        public String selectedFleetPostureId = "";
        public String selectedSiteResolutionModeId = "";
        public String activeSiteResolutionModeId = "";
        public int completedMainMissions = 0;
        public double earthProgress = 0.0;
        public double enemyAlertLevel = 0.0;
        public double campaignIntelLevel = 0.0;
        public double strategicExposureLevel = 0.0;
        public double recentStrikePressure = 0.0;
        public double lastSensorSweepAtSec = -1000.0;
        public boolean galaxyEncounterActive = false;
        public boolean galaxyAmbientEncounterActive = false;
        public String activeMapModifiers = "NONE";
        public int environmentHazardPulseIndex = -1;
        public int campaignFuel = 0;
        public int campaignSupplies = 0;
        public int campaignAmmo = 0;
        public int campaignSalvage = 0;
        public double travelFuelAttritionRemainder = 0.0;
        public double travelSupplyAttritionRemainder = 0.0;
        public double travelAmmoAttritionRemainder = 0.0;
        public double playerGalaxyX = Double.NaN;
        public double playerGalaxyY = Double.NaN;
        public double playerGalaxyHeadingDeg = -90.0;
        public double selectedFreeGalaxyTargetX = Double.NaN;
        public double selectedFreeGalaxyTargetY = Double.NaN;
        public double transitEventCooldownSec = 0.0;
        public double transitEncounterPressure = 0.0;
        public double transitNextEncounterThreshold = 6.0;
        public int transitContactEventsThisLeg = 0;
        public int transitContactTargetThisLeg = 0;
        public int transientGalaxySiteSerial = 0;
        public int strategicTorpedoCharges = 0;
        public int strategicSortiesLaunched = 0;
        public int strategicAtomicCharges = 0;
        public double torpedoStrikeCooldownSec = 0.0;
        public double carrierSortieCooldownSec = 0.0;
        public double atomicStrikeCooldownSec = 0.0;
        public int nextGalaxySearchGroupId = 1;
        public int nextStrategicStrikeObjectId = 1;
        public String defeatedStrategicTaskForceKeys = "";
        public int nextCampaignForceId = 1;
        public String defeatedCampaignForceKeys = "";
        public int lastChecklistFleetSeedSector = 0;
        public String strategicStrikeObjects = "";
        public String strategicDivisions = "";
        public String campaignForces = "";
        public String shipCampaignForceIds = "";
        public long campaignIntelTick = 0L;
        public String campaignFleetIntel = "";
        public String campaignOperationIntel = "";
        public String enemyPlayerContact = "";
        public int nextPendingHostileReinforcementId = 1;
        public String pendingHostileReinforcements = "";
        public int nextCampaignBattleId = 1;
        public String campaignBattles = "";
        public int nextAfterActionReportId = 1;
        public String campaignAfterActionReports = "";
        public int nextCampaignLogEntryId = 1;
        public String campaignCaptainLog = "";
        public String campaignMemoryFlags = "";
        public int nextCampaignShipRecordId = 1;
        public int nextCampaignBaseQueueId = 1;
        public boolean campaignFiniteEconomyInitialized = false;
        public double campaignEconomyTickAccumulatorSec = 0.0;
        public String campaignShipPool = "";
        public String campaignBaseQueues = "";
        public String galaxyTravelOriginId = "";
        public String galaxyTravelDestinationId = "";
        public String galaxyTravelDestinationLabel = "";
        public double galaxyTravelProgress = 0.0;
        public double galaxyTravelDurationSec = 0.0;
        public boolean galaxyTravelTraveling = false;
        public boolean galaxyTravelFreeTravel = false;
        public double galaxyTravelInterceptionRisk = 0.0;
        public double galaxyTravelTargetX = Double.NaN;
        public double galaxyTravelTargetY = Double.NaN;
        public double galaxyTravelSpeed = 0.0;
        public String galaxyLocationStates = "";
        public String galaxySearchGroups = "";
        public String campaignTheaters = "";
        public String strategicNodes = "";
        public String theaterWarRecentEvents = "";
        public String completedCampaignBoardMissionIds = "";
        public String expiredCampaignBoardMissionIds = "";
        public double theaterWarTickAccumulatorSec = 0.0;
        public int theaterWarTickIndex = 0;
        public String selectedTheaterId = "";
        public double blueInterventionReserve = 100.0;
        public int earthOperationStage = 0;
        public boolean redGlobalCollapseActive = false;
        public double factionDirectorAccumulatorSec = 0.0;
        public String redDirectorBrief = "";
        public String greenDirectorBrief = "";
        public String yellowDirectorBrief = "";
        public String strategicExpansionState = "";
        public String economyExpansionState = "";
        public String diplomacyNarrativeState = "";
        public String operationsExpansionState = "";
        public String flagshipOperationsState = "";
        public String boardingRescueState = "";
        public String alternativeCampaignState = "";
        public String cooperativeCommandState = "";
        public String warMemoryState = "";
        public String productionReadinessState = "";
        public String fleetDoctrineExpansionState = "";
        public String deepCampaignExpansionState = "";
        public String communityContentState = "";

        public int allyOreStockpile = 0;
        public int enemyOreStockpile = 0;
        public int allyHullLv = 0;
        public int allyShieldLv = 0;
        public int allyTurretLv = 0;
        public int allyMiningLv = 0;
        public int allyHangarLv = 0;
        public int enemyHullLv = 0;
        public int enemyShieldLv = 0;
        public int enemyTurretLv = 0;
        public int enemyMiningLv = 0;
        public int enemyHangarLv = 0;

        public void normalize() {
            version = CURRENT_VERSION;
            worldW = Math.max(2000, worldW);
            worldH = Math.max(2000, worldH);
            randomEvents = true;
            nextSector = clamp(nextSector, 1, MAX_CAMPAIGN_SECTORS);
            credits = Math.max(0, credits);
            sectorsCleared = clamp(sectorsCleared, 0, MAX_CAMPAIGN_SECTORS);
            campaignKills = Math.max(0, campaignKills);
            bossDropsCollected = Math.max(0, bossDropsCollected);
            unlockMissileTierGranted = clamp(unlockMissileTierGranted, 0, 2);
            sideObjectivesCompletedTotal = Math.max(0, sideObjectivesCompletedTotal);
            sideObjectivesFailedTotal = Math.max(0, sideObjectivesFailedTotal);
            branchScore = Math.max(-99, Math.min(99, branchScore));
            branchRoute = safeName(branchRoute, DEFAULT_BRANCH_ROUTE);
            playerFactionName = safeName(playerFactionName, DEFAULT_PLAYER_FACTION_NAME);
            playerRoleName = safeName(playerRoleName, DEFAULT_PLAYER_ROLE_NAME);
            primaryWeaponFamilyName = safeName(primaryWeaponFamilyName, DEFAULT_PRIMARY_WEAPON_FAMILY_NAME);
            powerPresetName = safeName(powerPresetName, DEFAULT_POWER_PRESET_NAME);
            crewOrderName = safeName(crewOrderName, DEFAULT_CREW_ORDER_NAME);
            engineeringPriorityName = safeName(engineeringPriorityName, DEFAULT_ENGINEERING_PRIORITY_NAME);
            overloadBusName = safeName(overloadBusName, DEFAULT_OVERLOAD_BUS_NAME);
            carrierCommandModeName = safeName(carrierCommandModeName, DEFAULT_CARRIER_COMMAND_MODE_NAME);
            hpMax = Math.max(1, hpMax);
            shieldMax = finiteOr(shieldMax, 0.0);
            shieldRegen = finiteOr(shieldRegen, 0.0);
            cargo = Math.max(0, cargo);
            campaignOre = Math.max(cargo, Math.max(0, campaignOre));
            cargoMax = Math.max(0, cargoMax);
            miningRate = Math.max(0.0, finiteOr(miningRate, 10.0));
            miningRange = Math.max(0.0, finiteOr(miningRange, 56.0));
            orePriceBaseMul = Math.max(0.0, finiteOr(orePriceBaseMul, 1.0));
            miningBaseMul = Math.max(0.0, finiteOr(miningBaseMul, 1.0));
            ciwsRange = Math.max(0.0, finiteOr(ciwsRange, 200.0));
            ciwsCooldown = Math.max(0.02, finiteOr(ciwsCooldown, 0.12));
            ciwsQuality = clamp(finiteOr(ciwsQuality, 0.35), 0.0, 1.0);
            ciwsPelletsPerBurst = Math.max(1, ciwsPelletsPerBurst);
            ciwsPelletSpeed = Math.max(0.0, finiteOr(ciwsPelletSpeed, 920.0));
            ciwsPelletDamage = Math.max(1, ciwsPelletDamage);
            ciwsPelletLife = Math.max(1, ciwsPelletLife);
            ciwsPelletRadius = Math.max(0.1, finiteOr(ciwsPelletRadius, 1.8));
            maxFighters = Math.max(0, maxFighters);
            powerBuses = safeName(powerBuses, DEFAULT_POWER_BUSES);
            turretData = (turretData == null) ? "" : turretData.trim();
            flightDeckLoadout = (flightDeckLoadout == null) ? "" : flightDeckLoadout.trim();
            ownedTitans = (ownedTitans == null) ? DEFAULT_OWNED_TITANS : ownedTitans.trim();
            persistentBlueFleet = (persistentBlueFleet == null) ? DEFAULT_PERSISTENT_BLUE_FLEET : persistentBlueFleet.trim();
            campaignYardOrders = (campaignYardOrders == null) ? "" : campaignYardOrders.trim();
            nextCampaignYardOrderId = Math.max(1, nextCampaignYardOrderId);
            selectedStrategicOverlayId = safeName(selectedStrategicOverlayId, "CONTROL");
            factionAttackCommitments = factionAttackCommitments == null ? "" : factionAttackCommitments.trim();
            selectedCampaignTaskGroupId = Math.max(0, selectedCampaignTaskGroupId);
            escortCapUpgradeLevel = clamp(escortCapUpgradeLevel, 0, 5);
            lineCapUpgradeLevel = clamp(lineCapUpgradeLevel, 0, 4);
            capitalCapUpgradeLevel = clamp(capitalCapUpgradeLevel, 0, 3);
            greenContractFavor = Math.max(0, greenContractFavor);
            yellowLiberationFavor = Math.max(0, yellowLiberationFavor);
            fleetStrain = clamp(finiteOr(fleetStrain, 0.0), 0.0, 100.0);
            vossRelationshipStateId = safeName(vossRelationshipStateId, "");
            marrRelationshipStateId = safeName(marrRelationshipStateId, "");
            rookRelationshipStateId = safeName(rookRelationshipStateId, "");
            currentGalaxyLocationId = safeName(currentGalaxyLocationId, "");
            selectedGalaxyLocationId = safeName(selectedGalaxyLocationId, "");
            dockedGalaxyLocationId = safeName(dockedGalaxyLocationId, "");
            activeGalaxyEncounterLocationId = safeName(activeGalaxyEncounterLocationId, "");
            activeGalaxyEncounterSearchGroupId = Math.max(0, activeGalaxyEncounterSearchGroupId);
            activeGalaxyEncounterForceIds = (activeGalaxyEncounterForceIds == null) ? "" : activeGalaxyEncounterForceIds.trim();
            activeGalaxyEncounterParentForceId = Math.max(0, activeGalaxyEncounterParentForceId);
            selectedFleetPostureId = safeName(selectedFleetPostureId, "");
            selectedSiteResolutionModeId = safeName(selectedSiteResolutionModeId, "");
            activeSiteResolutionModeId = safeName(activeSiteResolutionModeId, "");
            completedMainMissions = clamp(completedMainMissions, 0, MAX_CAMPAIGN_SECTORS);
            earthProgress = clamp(finiteOr(earthProgress, 0.0), 0.0, 1.0);
            enemyAlertLevel = clamp(finiteOr(enemyAlertLevel, 0.0), 0.0, 100.0);
            campaignIntelLevel = clamp(finiteOr(campaignIntelLevel, 0.0), 0.0, 100.0);
            strategicExposureLevel = clamp(finiteOr(strategicExposureLevel, 0.0), 0.0, 100.0);
            recentStrikePressure = clamp(finiteOr(recentStrikePressure, 0.0), 0.0, 100.0);
            lastSensorSweepAtSec = finiteOr(lastSensorSweepAtSec, -1000.0);
            campaignFuel = Math.max(0, campaignFuel);
            campaignSupplies = Math.max(0, campaignSupplies);
            campaignAmmo = Math.max(0, campaignAmmo);
            campaignSalvage = Math.max(0, campaignSalvage);
            travelFuelAttritionRemainder = clamp(finiteOr(travelFuelAttritionRemainder, 0.0), 0.0, 0.999999);
            travelSupplyAttritionRemainder = clamp(finiteOr(travelSupplyAttritionRemainder, 0.0), 0.0, 0.999999);
            travelAmmoAttritionRemainder = clamp(finiteOr(travelAmmoAttritionRemainder, 0.0), 0.0, 0.999999);
            transitEncounterPressure = Math.max(0.0, finiteOr(transitEncounterPressure, 0.0));
            transitNextEncounterThreshold = Math.max(4.0, finiteOr(transitNextEncounterThreshold, 6.0));
            transitContactEventsThisLeg = Math.max(0, transitContactEventsThisLeg);
            transitContactTargetThisLeg = Math.max(0, Math.min(8, transitContactTargetThisLeg));
            playerGalaxyX = finiteOr(playerGalaxyX, Double.NaN);
            playerGalaxyY = finiteOr(playerGalaxyY, Double.NaN);
            playerGalaxyHeadingDeg = finiteOr(playerGalaxyHeadingDeg, -90.0);
            strategicTorpedoCharges = Math.max(0, strategicTorpedoCharges);
            strategicSortiesLaunched = Math.max(0, strategicSortiesLaunched);
            strategicAtomicCharges = Math.max(0, strategicAtomicCharges);
            torpedoStrikeCooldownSec = Math.max(0.0, finiteOr(torpedoStrikeCooldownSec, 0.0));
            carrierSortieCooldownSec = Math.max(0.0, finiteOr(carrierSortieCooldownSec, 0.0));
            atomicStrikeCooldownSec = Math.max(0.0, finiteOr(atomicStrikeCooldownSec, 0.0));
            nextGalaxySearchGroupId = Math.max(1, nextGalaxySearchGroupId);
            nextStrategicStrikeObjectId = Math.max(1, nextStrategicStrikeObjectId);
            defeatedStrategicTaskForceKeys = (defeatedStrategicTaskForceKeys == null) ? "" : defeatedStrategicTaskForceKeys.trim();
            nextCampaignForceId = Math.max(1, nextCampaignForceId);
            defeatedCampaignForceKeys = (defeatedCampaignForceKeys == null) ? "" : defeatedCampaignForceKeys.trim();
            lastChecklistFleetSeedSector = clamp(lastChecklistFleetSeedSector, 0, MAX_CAMPAIGN_SECTORS);
            strategicStrikeObjects = (strategicStrikeObjects == null) ? "" : strategicStrikeObjects.trim();
            strategicDivisions = (strategicDivisions == null) ? "" : strategicDivisions.trim();
            campaignForces = (campaignForces == null) ? "" : campaignForces.trim();
            shipCampaignForceIds = (shipCampaignForceIds == null) ? "" : shipCampaignForceIds.trim();
            campaignIntelTick = Math.max(0L, campaignIntelTick);
            campaignFleetIntel = (campaignFleetIntel == null) ? "" : campaignFleetIntel.trim();
            campaignOperationIntel = (campaignOperationIntel == null) ? "" : campaignOperationIntel.trim();
            enemyPlayerContact = (enemyPlayerContact == null) ? "" : enemyPlayerContact.trim();
            nextPendingHostileReinforcementId = Math.max(1, nextPendingHostileReinforcementId);
            pendingHostileReinforcements = (pendingHostileReinforcements == null) ? "" : pendingHostileReinforcements.trim();
            nextCampaignBattleId = Math.max(1, nextCampaignBattleId);
            campaignBattles = (campaignBattles == null) ? "" : campaignBattles.trim();
            nextAfterActionReportId = Math.max(1, nextAfterActionReportId);
            campaignAfterActionReports = (campaignAfterActionReports == null) ? "" : campaignAfterActionReports.trim();
            nextCampaignLogEntryId = Math.max(1, nextCampaignLogEntryId);
            campaignCaptainLog = (campaignCaptainLog == null) ? "" : campaignCaptainLog.trim();
            campaignMemoryFlags = (campaignMemoryFlags == null) ? "" : campaignMemoryFlags.trim();
            activeMapModifiers = (activeMapModifiers == null || activeMapModifiers.isBlank())
                    ? "NONE" : activeMapModifiers.trim();
            environmentHazardPulseIndex = Math.max(-1, environmentHazardPulseIndex);
            nextCampaignShipRecordId = Math.max(1, nextCampaignShipRecordId);
            nextCampaignBaseQueueId = Math.max(1, nextCampaignBaseQueueId);
            campaignEconomyTickAccumulatorSec = Math.max(0.0, finiteOr(campaignEconomyTickAccumulatorSec, 0.0));
            campaignShipPool = (campaignShipPool == null) ? "" : campaignShipPool.trim();
            campaignBaseQueues = (campaignBaseQueues == null) ? "" : campaignBaseQueues.trim();
            factionDirectorAccumulatorSec = Math.max(0.0, finiteOr(factionDirectorAccumulatorSec, 0.0));
            redDirectorBrief = safeName(redDirectorBrief, "");
            greenDirectorBrief = safeName(greenDirectorBrief, "");
            yellowDirectorBrief = safeName(yellowDirectorBrief, "");
            strategicExpansionState = (strategicExpansionState == null) ? "" : strategicExpansionState.trim();
            economyExpansionState = (economyExpansionState == null) ? "" : economyExpansionState.trim();
            diplomacyNarrativeState = (diplomacyNarrativeState == null) ? "" : diplomacyNarrativeState.trim();
            operationsExpansionState = (operationsExpansionState == null) ? "" : operationsExpansionState.trim();
            flagshipOperationsState = (flagshipOperationsState == null) ? "" : flagshipOperationsState.trim();
            boardingRescueState = (boardingRescueState == null) ? "" : boardingRescueState.trim();
            alternativeCampaignState = (alternativeCampaignState == null) ? "" : alternativeCampaignState.trim();
            cooperativeCommandState = (cooperativeCommandState == null) ? "" : cooperativeCommandState.trim();
            warMemoryState = (warMemoryState == null) ? "" : warMemoryState.trim();
            productionReadinessState = (productionReadinessState == null) ? "" : productionReadinessState.trim();
            fleetDoctrineExpansionState = (fleetDoctrineExpansionState == null) ? "" : fleetDoctrineExpansionState.trim();
            deepCampaignExpansionState = (deepCampaignExpansionState == null) ? "" : deepCampaignExpansionState.trim();
            communityContentState = (communityContentState == null) ? "" : communityContentState.trim();
            galaxyTravelOriginId = safeName(galaxyTravelOriginId, "");
            galaxyTravelDestinationId = safeName(galaxyTravelDestinationId, "");
            galaxyTravelProgress = clamp(finiteOr(galaxyTravelProgress, 0.0), 0.0, 1.0);
            galaxyTravelDurationSec = Math.max(0.0, finiteOr(galaxyTravelDurationSec, 0.0));
            galaxyTravelInterceptionRisk = clamp(finiteOr(galaxyTravelInterceptionRisk, 0.0), 0.0, 100.0);
            galaxyTravelTargetX = finiteOr(galaxyTravelTargetX, Double.NaN);
            galaxyTravelTargetY = finiteOr(galaxyTravelTargetY, Double.NaN);
            galaxyTravelSpeed = Math.max(0.0, finiteOr(galaxyTravelSpeed, 0.0));
            galaxyLocationStates = (galaxyLocationStates == null) ? "" : galaxyLocationStates.trim();
            galaxySearchGroups = (galaxySearchGroups == null) ? "" : galaxySearchGroups.trim();
            campaignTheaters = (campaignTheaters == null) ? "" : campaignTheaters.trim();
            strategicNodes = (strategicNodes == null) ? "" : strategicNodes.trim();
            theaterWarRecentEvents = (theaterWarRecentEvents == null) ? "" : theaterWarRecentEvents.trim();
            completedCampaignBoardMissionIds = (completedCampaignBoardMissionIds == null) ? "" : completedCampaignBoardMissionIds.trim();
            expiredCampaignBoardMissionIds = (expiredCampaignBoardMissionIds == null) ? "" : expiredCampaignBoardMissionIds.trim();
            theaterWarTickAccumulatorSec = Math.max(0.0, finiteOr(theaterWarTickAccumulatorSec, 0.0));
            theaterWarTickIndex = Math.max(0, theaterWarTickIndex);
            selectedTheaterId = safeName(selectedTheaterId, "");
            blueInterventionReserve = clamp(finiteOr(blueInterventionReserve, 100.0), 0.0, 100.0);
            earthOperationStage = clamp(earthOperationStage, 0, 3);
            allyOreStockpile = Math.max(0, allyOreStockpile);
            enemyOreStockpile = Math.max(0, enemyOreStockpile);
            allyHullLv = clamp(allyHullLv, 0, 5);
            allyShieldLv = clamp(allyShieldLv, 0, 5);
            allyTurretLv = clamp(allyTurretLv, 0, 5);
            allyMiningLv = clamp(allyMiningLv, 0, 5);
            allyHangarLv = clamp(allyHangarLv, 0, 5);
            enemyHullLv = clamp(enemyHullLv, 0, 5);
            enemyShieldLv = clamp(enemyShieldLv, 0, 5);
            enemyTurretLv = clamp(enemyTurretLv, 0, 5);
            enemyMiningLv = clamp(enemyMiningLv, 0, 5);
            enemyHangarLv = clamp(enemyHangarLv, 0, 3);
        }

        public boolean isUsable() {
            return nextSector >= 1 && nextSector <= MAX_CAMPAIGN_SECTORS;
        }

        public String menuSummary() {
            String role = playerRoleName.replace('_', ' ');
            return "Sector " + nextSector
                    + "  |  " + role
                    + "  |  Doctrine " + branchRoute
                    + "  |  Titans " + countCsvEntries(ownedTitans)
                    + "  |  Fleet " + countSerializedFleetEntries(persistentBlueFleet);
        }

        public GameConfig toGameConfig() {
            return toGameConfig(GameMode.CAMPAIGN_OPS);
        }

        public GameConfig toGameConfig(GameMode mode) {
            GameMode resumeMode = (mode == GameMode.FLEET) ? GameMode.FLEET : GameMode.CAMPAIGN_OPS;
            return new GameConfig(resumeMode, worldW, worldH, true, seed, false, 0, true);
        }
    }

    public static Checkpoint load() {
        synchronized (IO_LOCK) {
            if (!Files.exists(CHECKPOINT_FILE)) return null;

            Properties props = new Properties();
            Checkpoint cp = new Checkpoint();
            try (InputStream in = Files.newInputStream(CHECKPOINT_FILE, StandardOpenOption.READ)) {
                props.load(in);
                cp.version = parseInt(props, "version", 1);
                cp.sourceVersion = cp.version;
                cp.worldW = parseInt(props, "worldW", cp.worldW);
                cp.worldH = parseInt(props, "worldH", cp.worldH);
                cp.randomEvents = parseBoolean(props, "randomEvents", cp.randomEvents);
                cp.seed = parseLong(props, "seed", cp.seed);
                cp.nextSector = parseInt(props, "nextSector", cp.nextSector);
                cp.credits = parseInt(props, "credits", cp.credits);
                cp.sectorsCleared = parseInt(props, "sectorsCleared", cp.sectorsCleared);
                cp.campaignKills = parseInt(props, "campaignKills", cp.campaignKills);
                cp.branchScore = parseInt(props, "branchScore", cp.branchScore);
                cp.branchRoute = props.getProperty("branchRoute", cp.branchRoute);
                cp.sideObjectivesCompletedTotal = parseInt(props, "sideObjectivesCompletedTotal", cp.sideObjectivesCompletedTotal);
                cp.sideObjectivesFailedTotal = parseInt(props, "sideObjectivesFailedTotal", cp.sideObjectivesFailedTotal);
                cp.unlockAuxGunGranted = parseBoolean(props, "unlockAuxGunGranted", cp.unlockAuxGunGranted);
                cp.unlockMissileTierGranted = parseInt(props, "unlockMissileTierGranted", cp.unlockMissileTierGranted);
                cp.unlockCiwsGranted = parseBoolean(props, "unlockCiwsGranted", cp.unlockCiwsGranted);
                cp.unlockHullGranted = parseBoolean(props, "unlockHullGranted", cp.unlockHullGranted);
                cp.bossDropAegisArray = parseBoolean(props, "bossDropAegisArray", cp.bossDropAegisArray);
                cp.bossDropMissileCore = parseBoolean(props, "bossDropMissileCore", cp.bossDropMissileCore);
                cp.bossDropFlagCore = parseBoolean(props, "bossDropFlagCore", cp.bossDropFlagCore);
                cp.bossDropsCollected = parseInt(props, "bossDropsCollected", cp.bossDropsCollected);
                cp.playerFactionName = props.getProperty("playerFactionName", cp.playerFactionName);
                cp.playerRoleName = props.getProperty("playerRoleName", cp.playerRoleName);
                cp.primaryWeaponFamilyName = props.getProperty("primaryWeaponFamilyName", cp.primaryWeaponFamilyName);
                cp.hpMax = parseInt(props, "hpMax", cp.hpMax);
                cp.shieldMax = parseDouble(props, "shieldMax", cp.shieldMax);
                cp.shieldRegen = parseDouble(props, "shieldRegen", cp.shieldRegen);
                cp.shieldActive = parseBoolean(props, "shieldActive", cp.shieldActive);
                cp.campaignOre = parseInt(props, "campaignOre", cp.campaignOre);
                cp.cargo = parseInt(props, "cargo", cp.cargo);
                cp.cargoMax = parseInt(props, "cargoMax", cp.cargoMax);
                cp.miningRate = parseDouble(props, "miningRate", cp.miningRate);
                cp.miningRange = parseDouble(props, "miningRange", cp.miningRange);
                cp.orePriceBaseMul = parseDouble(props, "orePriceBaseMul", cp.orePriceBaseMul);
                cp.miningBaseMul = parseDouble(props, "miningBaseMul", cp.miningBaseMul);
                cp.hasCIWS = parseBoolean(props, "hasCIWS", cp.hasCIWS);
                cp.ciwsRange = parseDouble(props, "ciwsRange", cp.ciwsRange);
                cp.ciwsCooldown = parseDouble(props, "ciwsCooldown", cp.ciwsCooldown);
                cp.ciwsQuality = parseDouble(props, "ciwsQuality", cp.ciwsQuality);
                cp.ciwsPelletsPerBurst = parseInt(props, "ciwsPelletsPerBurst", cp.ciwsPelletsPerBurst);
                cp.ciwsPelletSpeed = parseDouble(props, "ciwsPelletSpeed", cp.ciwsPelletSpeed);
                cp.ciwsPelletDamage = parseInt(props, "ciwsPelletDamage", cp.ciwsPelletDamage);
                cp.ciwsPelletLife = parseInt(props, "ciwsPelletLife", cp.ciwsPelletLife);
                cp.ciwsPelletRadius = parseDouble(props, "ciwsPelletRadius", cp.ciwsPelletRadius);
                cp.powerPresetName = props.getProperty("powerPresetName", cp.powerPresetName);
                cp.crewOrderName = props.getProperty("crewOrderName", cp.crewOrderName);
                cp.engineeringPriorityName = props.getProperty("engineeringPriorityName", cp.engineeringPriorityName);
                cp.overloadBusName = props.getProperty("overloadBusName", cp.overloadBusName);
                cp.powerBuses = props.getProperty("powerBuses", cp.powerBuses);
                cp.turretData = props.getProperty("turretData", cp.turretData);
                cp.isCarrier = parseBoolean(props, "isCarrier", cp.isCarrier);
                cp.maxFighters = parseInt(props, "maxFighters", cp.maxFighters);
                cp.carrierCommandModeName = props.getProperty("carrierCommandModeName", cp.carrierCommandModeName);
                cp.carrierAutoLaunch = parseBoolean(props, "carrierAutoLaunch", cp.carrierAutoLaunch);
                cp.flightDeckLoadout = props.getProperty("flightDeckLoadout", cp.flightDeckLoadout);
                cp.ownedTitans = props.getProperty("ownedTitans", cp.ownedTitans);
                cp.persistentBlueFleet = props.getProperty("persistentBlueFleet", cp.persistentBlueFleet);
                cp.campaignYardOrders = props.getProperty("campaignYardOrders", cp.campaignYardOrders);
                cp.nextCampaignYardOrderId = parseInt(props, "nextCampaignYardOrderId", cp.nextCampaignYardOrderId);
                cp.selectedStrategicOverlayId = props.getProperty("selectedStrategicOverlayId", cp.selectedStrategicOverlayId);
                cp.factionAttackCommitments = props.getProperty("factionAttackCommitments", cp.factionAttackCommitments);
                cp.selectedCampaignTaskGroupId = parseInt(props, "selectedCampaignTaskGroupId", cp.selectedCampaignTaskGroupId);
                cp.escortCapUpgradeLevel = parseInt(props, "escortCapUpgradeLevel", cp.escortCapUpgradeLevel);
                cp.lineCapUpgradeLevel = parseInt(props, "lineCapUpgradeLevel", cp.lineCapUpgradeLevel);
                cp.capitalCapUpgradeLevel = parseInt(props, "capitalCapUpgradeLevel", cp.capitalCapUpgradeLevel);
                cp.campaignBlueYellowAlliance = parseBoolean(props, "campaignBlueYellowAlliance", cp.campaignBlueYellowAlliance);
                cp.greenContractFleetJoined = parseBoolean(props, "greenContractFleetJoined", cp.greenContractFleetJoined);
                cp.yellowLiberationFleetJoined = parseBoolean(props, "yellowLiberationFleetJoined", cp.yellowLiberationFleetJoined);
                cp.greenContractFavor = parseInt(props, "greenContractFavor", cp.greenContractFavor);
                cp.yellowLiberationFavor = parseInt(props, "yellowLiberationFavor", cp.yellowLiberationFavor);
                cp.fleetStrain = parseDouble(props, "fleetStrain", cp.fleetStrain);
                cp.vossRelationshipStateId = props.getProperty("vossRelationshipStateId", cp.vossRelationshipStateId);
                cp.marrRelationshipStateId = props.getProperty("marrRelationshipStateId", cp.marrRelationshipStateId);
                cp.rookRelationshipStateId = props.getProperty("rookRelationshipStateId", cp.rookRelationshipStateId);
                cp.strategicOvermapMode = parseBoolean(props, "strategicOvermapMode", cp.strategicOvermapMode);
                cp.currentGalaxyLocationId = props.getProperty("currentGalaxyLocationId", cp.currentGalaxyLocationId);
                cp.selectedGalaxyLocationId = props.getProperty("selectedGalaxyLocationId", cp.selectedGalaxyLocationId);
                cp.dockedGalaxyLocationId = props.getProperty("dockedGalaxyLocationId", cp.dockedGalaxyLocationId);
                cp.activeGalaxyEncounterLocationId = props.getProperty("activeGalaxyEncounterLocationId", cp.activeGalaxyEncounterLocationId);
                cp.activeGalaxyEncounterSearchGroupId = parseInt(props, "activeGalaxyEncounterSearchGroupId", cp.activeGalaxyEncounterSearchGroupId);
                cp.activeGalaxyEncounterForceIds = props.getProperty("activeGalaxyEncounterForceIds", cp.activeGalaxyEncounterForceIds);
                cp.activeGalaxyEncounterParentForceId = parseInt(props, "activeGalaxyEncounterParentForceId", cp.activeGalaxyEncounterParentForceId);
                cp.selectedFleetPostureId = props.getProperty("selectedFleetPostureId", cp.selectedFleetPostureId);
                cp.selectedSiteResolutionModeId = props.getProperty("selectedSiteResolutionModeId", cp.selectedSiteResolutionModeId);
                cp.activeSiteResolutionModeId = props.getProperty("activeSiteResolutionModeId", cp.activeSiteResolutionModeId);
                cp.completedMainMissions = parseInt(props, "completedMainMissions", cp.completedMainMissions);
                cp.earthProgress = parseDouble(props, "earthProgress", cp.earthProgress);
                cp.enemyAlertLevel = parseDouble(props, "enemyAlertLevel", cp.enemyAlertLevel);
                cp.campaignIntelLevel = parseDouble(props, "campaignIntelLevel", cp.campaignIntelLevel);
                cp.strategicExposureLevel = parseDouble(props, "strategicExposureLevel", cp.strategicExposureLevel);
                cp.recentStrikePressure = parseDouble(props, "recentStrikePressure", cp.recentStrikePressure);
                cp.galaxyEncounterActive = parseBoolean(props, "galaxyEncounterActive", cp.galaxyEncounterActive);
                cp.galaxyAmbientEncounterActive = parseBoolean(props, "galaxyAmbientEncounterActive", cp.galaxyAmbientEncounterActive);
                cp.activeMapModifiers = props.getProperty("activeMapModifiers", cp.activeMapModifiers);
                cp.environmentHazardPulseIndex = parseInt(props, "environmentHazardPulseIndex", cp.environmentHazardPulseIndex);
                cp.campaignFuel = parseInt(props, "campaignFuel", cp.campaignFuel);
                cp.campaignSupplies = parseInt(props, "campaignSupplies", cp.campaignSupplies);
                cp.campaignAmmo = parseInt(props, "campaignAmmo", cp.campaignAmmo);
                cp.campaignSalvage = parseInt(props, "campaignSalvage", cp.campaignSalvage);
                cp.lastSensorSweepAtSec = parseDouble(props, "lastSensorSweepAtSec", cp.lastSensorSweepAtSec);
                cp.travelFuelAttritionRemainder = parseDouble(props, "travelFuelAttritionRemainder", cp.travelFuelAttritionRemainder);
                cp.travelSupplyAttritionRemainder = parseDouble(props, "travelSupplyAttritionRemainder", cp.travelSupplyAttritionRemainder);
                cp.travelAmmoAttritionRemainder = parseDouble(props, "travelAmmoAttritionRemainder", cp.travelAmmoAttritionRemainder);
                cp.playerGalaxyX = parseDouble(props, "playerGalaxyX", cp.playerGalaxyX);
                cp.playerGalaxyY = parseDouble(props, "playerGalaxyY", cp.playerGalaxyY);
                cp.playerGalaxyHeadingDeg = parseDouble(props, "playerGalaxyHeadingDeg", cp.playerGalaxyHeadingDeg);
                cp.selectedFreeGalaxyTargetX = parseDouble(props, "selectedFreeGalaxyTargetX", cp.selectedFreeGalaxyTargetX);
                cp.selectedFreeGalaxyTargetY = parseDouble(props, "selectedFreeGalaxyTargetY", cp.selectedFreeGalaxyTargetY);
                cp.transitEventCooldownSec = parseDouble(props, "transitEventCooldownSec", cp.transitEventCooldownSec);
                cp.transitEncounterPressure = parseDouble(props, "transitEncounterPressure", cp.transitEncounterPressure);
                cp.transitNextEncounterThreshold = parseDouble(props, "transitNextEncounterThreshold", cp.transitNextEncounterThreshold);
                cp.transitContactEventsThisLeg = parseInt(props, "transitContactEventsThisLeg", cp.transitContactEventsThisLeg);
                cp.transitContactTargetThisLeg = parseInt(props, "transitContactTargetThisLeg", cp.transitContactTargetThisLeg);
                cp.transientGalaxySiteSerial = parseInt(props, "transientGalaxySiteSerial", cp.transientGalaxySiteSerial);
                cp.strategicTorpedoCharges = parseInt(props, "strategicTorpedoCharges", cp.strategicTorpedoCharges);
                cp.strategicSortiesLaunched = parseInt(props, "strategicSortiesLaunched", cp.strategicSortiesLaunched);
                cp.strategicAtomicCharges = parseInt(props, "strategicAtomicCharges", cp.strategicAtomicCharges);
                cp.torpedoStrikeCooldownSec = parseDouble(props, "torpedoStrikeCooldownSec", cp.torpedoStrikeCooldownSec);
                cp.carrierSortieCooldownSec = parseDouble(props, "carrierSortieCooldownSec", cp.carrierSortieCooldownSec);
                cp.atomicStrikeCooldownSec = parseDouble(props, "atomicStrikeCooldownSec", cp.atomicStrikeCooldownSec);
                cp.nextGalaxySearchGroupId = parseInt(props, "nextGalaxySearchGroupId", cp.nextGalaxySearchGroupId);
                cp.nextStrategicStrikeObjectId = parseInt(props, "nextStrategicStrikeObjectId", cp.nextStrategicStrikeObjectId);
                cp.defeatedStrategicTaskForceKeys = props.getProperty("defeatedStrategicTaskForceKeys", cp.defeatedStrategicTaskForceKeys);
                cp.nextCampaignForceId = parseInt(props, "nextCampaignForceId", cp.nextCampaignForceId);
                cp.defeatedCampaignForceKeys = props.getProperty("defeatedCampaignForceKeys", cp.defeatedCampaignForceKeys);
                cp.lastChecklistFleetSeedSector = parseInt(props, "lastChecklistFleetSeedSector", cp.lastChecklistFleetSeedSector);
                cp.strategicStrikeObjects = props.getProperty("strategicStrikeObjects", cp.strategicStrikeObjects);
                cp.strategicDivisions = props.getProperty("strategicDivisions", cp.strategicDivisions);
                cp.campaignForces = props.getProperty("campaignForces", cp.campaignForces);
                cp.shipCampaignForceIds = props.getProperty("shipCampaignForceIds", cp.shipCampaignForceIds);
                cp.campaignIntelTick = parseLong(props, "campaignIntelTick", cp.campaignIntelTick);
                cp.campaignFleetIntel = props.getProperty("campaignFleetIntel", cp.campaignFleetIntel);
                cp.campaignOperationIntel = props.getProperty("campaignOperationIntel", cp.campaignOperationIntel);
                cp.enemyPlayerContact = props.getProperty("enemyPlayerContact", cp.enemyPlayerContact);
                cp.nextPendingHostileReinforcementId = parseInt(props, "nextPendingHostileReinforcementId", cp.nextPendingHostileReinforcementId);
                cp.pendingHostileReinforcements = props.getProperty("pendingHostileReinforcements", cp.pendingHostileReinforcements);
                cp.nextCampaignBattleId = parseInt(props, "nextCampaignBattleId", cp.nextCampaignBattleId);
                cp.campaignBattles = props.getProperty("campaignBattles", cp.campaignBattles);
                cp.nextAfterActionReportId = parseInt(props, "nextAfterActionReportId", cp.nextAfterActionReportId);
                cp.campaignAfterActionReports = props.getProperty("campaignAfterActionReports", cp.campaignAfterActionReports);
                cp.nextCampaignLogEntryId = parseInt(props, "nextCampaignLogEntryId", cp.nextCampaignLogEntryId);
                cp.campaignCaptainLog = props.getProperty("campaignCaptainLog", cp.campaignCaptainLog);
                cp.campaignMemoryFlags = props.getProperty("campaignMemoryFlags", cp.campaignMemoryFlags);
                cp.nextCampaignShipRecordId = parseInt(props, "nextCampaignShipRecordId", cp.nextCampaignShipRecordId);
                cp.nextCampaignBaseQueueId = parseInt(props, "nextCampaignBaseQueueId", cp.nextCampaignBaseQueueId);
                cp.campaignFiniteEconomyInitialized = parseBoolean(props, "campaignFiniteEconomyInitialized", cp.campaignFiniteEconomyInitialized);
                cp.campaignEconomyTickAccumulatorSec = parseDouble(props, "campaignEconomyTickAccumulatorSec", cp.campaignEconomyTickAccumulatorSec);
                cp.campaignShipPool = props.getProperty("campaignShipPool", cp.campaignShipPool);
                cp.campaignBaseQueues = props.getProperty("campaignBaseQueues", cp.campaignBaseQueues);
                cp.factionDirectorAccumulatorSec = parseDouble(props, "factionDirectorAccumulatorSec", cp.factionDirectorAccumulatorSec);
                cp.redDirectorBrief = props.getProperty("redDirectorBrief", cp.redDirectorBrief);
                cp.greenDirectorBrief = props.getProperty("greenDirectorBrief", cp.greenDirectorBrief);
                cp.yellowDirectorBrief = props.getProperty("yellowDirectorBrief", cp.yellowDirectorBrief);
                cp.strategicExpansionState = props.getProperty("strategicExpansionState", cp.strategicExpansionState);
                cp.economyExpansionState = props.getProperty("economyExpansionState", cp.economyExpansionState);
                cp.diplomacyNarrativeState = props.getProperty("diplomacyNarrativeState", cp.diplomacyNarrativeState);
                cp.operationsExpansionState = props.getProperty("operationsExpansionState", cp.operationsExpansionState);
                cp.flagshipOperationsState = props.getProperty("flagshipOperationsState", cp.flagshipOperationsState);
                cp.boardingRescueState = props.getProperty("boardingRescueState", cp.boardingRescueState);
                cp.alternativeCampaignState = props.getProperty("alternativeCampaignState", cp.alternativeCampaignState);
                cp.cooperativeCommandState = props.getProperty("cooperativeCommandState", cp.cooperativeCommandState);
                cp.warMemoryState = props.getProperty("warMemoryState", cp.warMemoryState);
                cp.productionReadinessState = props.getProperty("productionReadinessState", cp.productionReadinessState);
                cp.fleetDoctrineExpansionState = props.getProperty("fleetDoctrineExpansionState", cp.fleetDoctrineExpansionState);
                cp.deepCampaignExpansionState = props.getProperty("deepCampaignExpansionState", cp.deepCampaignExpansionState);
                cp.communityContentState = props.getProperty("communityContentState", cp.communityContentState);
                cp.galaxyTravelOriginId = props.getProperty("galaxyTravelOriginId", cp.galaxyTravelOriginId);
                cp.galaxyTravelDestinationId = props.getProperty("galaxyTravelDestinationId", cp.galaxyTravelDestinationId);
                cp.galaxyTravelDestinationLabel = props.getProperty("galaxyTravelDestinationLabel", cp.galaxyTravelDestinationLabel);
                cp.galaxyTravelProgress = parseDouble(props, "galaxyTravelProgress", cp.galaxyTravelProgress);
                cp.galaxyTravelDurationSec = parseDouble(props, "galaxyTravelDurationSec", cp.galaxyTravelDurationSec);
                cp.galaxyTravelTraveling = parseBoolean(props, "galaxyTravelTraveling", cp.galaxyTravelTraveling);
                cp.galaxyTravelFreeTravel = parseBoolean(props, "galaxyTravelFreeTravel", cp.galaxyTravelFreeTravel);
                cp.galaxyTravelInterceptionRisk = parseDouble(props, "galaxyTravelInterceptionRisk", cp.galaxyTravelInterceptionRisk);
                cp.galaxyTravelTargetX = parseDouble(props, "galaxyTravelTargetX", cp.galaxyTravelTargetX);
                cp.galaxyTravelTargetY = parseDouble(props, "galaxyTravelTargetY", cp.galaxyTravelTargetY);
                cp.galaxyTravelSpeed = parseDouble(props, "galaxyTravelSpeed", cp.galaxyTravelSpeed);
                cp.galaxyLocationStates = props.getProperty("galaxyLocationStates", cp.galaxyLocationStates);
                cp.galaxySearchGroups = props.getProperty("galaxySearchGroups", cp.galaxySearchGroups);
                cp.campaignTheaters = props.getProperty("campaignTheaters", cp.campaignTheaters);
                cp.strategicNodes = props.getProperty("strategicNodes", cp.strategicNodes);
                cp.theaterWarRecentEvents = props.getProperty("theaterWarRecentEvents", cp.theaterWarRecentEvents);
                cp.completedCampaignBoardMissionIds = props.getProperty("completedCampaignBoardMissionIds", cp.completedCampaignBoardMissionIds);
                cp.expiredCampaignBoardMissionIds = props.getProperty("expiredCampaignBoardMissionIds", cp.expiredCampaignBoardMissionIds);
                cp.theaterWarTickAccumulatorSec = parseDouble(props, "theaterWarTickAccumulatorSec", cp.theaterWarTickAccumulatorSec);
                cp.theaterWarTickIndex = parseInt(props, "theaterWarTickIndex", cp.theaterWarTickIndex);
                cp.selectedTheaterId = props.getProperty("selectedTheaterId", cp.selectedTheaterId);
                cp.blueInterventionReserve = parseDouble(props, "blueInterventionReserve", cp.blueInterventionReserve);
                cp.earthOperationStage = parseInt(props, "earthOperationStage", cp.earthOperationStage);
                cp.redGlobalCollapseActive = parseBoolean(props, "redGlobalCollapseActive", cp.redGlobalCollapseActive);
                cp.allyOreStockpile = parseInt(props, "allyOreStockpile", cp.allyOreStockpile);
                cp.enemyOreStockpile = parseInt(props, "enemyOreStockpile", cp.enemyOreStockpile);
                cp.allyHullLv = parseInt(props, "allyHullLv", cp.allyHullLv);
                cp.allyShieldLv = parseInt(props, "allyShieldLv", cp.allyShieldLv);
                cp.allyTurretLv = parseInt(props, "allyTurretLv", cp.allyTurretLv);
                cp.allyMiningLv = parseInt(props, "allyMiningLv", cp.allyMiningLv);
                cp.allyHangarLv = parseInt(props, "allyHangarLv", cp.allyHangarLv);
                cp.enemyHullLv = parseInt(props, "enemyHullLv", cp.enemyHullLv);
                cp.enemyShieldLv = parseInt(props, "enemyShieldLv", cp.enemyShieldLv);
                cp.enemyTurretLv = parseInt(props, "enemyTurretLv", cp.enemyTurretLv);
                cp.enemyMiningLv = parseInt(props, "enemyMiningLv", cp.enemyMiningLv);
                cp.enemyHangarLv = parseInt(props, "enemyHangarLv", cp.enemyHangarLv);
            } catch (IOException | IllegalArgumentException ex) {
                ErrorLog.logException("[campaign] checkpoint_load_failed path=" + CHECKPOINT_FILE, ex);
                return null;
            }
            migrateCheckpoint(cp, props);
            cp.normalize();
            return cp.isUsable() ? cp : null;
        }
    }

    public static int currentVersion() {
        return CURRENT_VERSION;
    }

    public static boolean verifyAndCommitMigration(Checkpoint cp) {
        if (cp == null || !cp.migrationApplied) return true;
        synchronized (IO_LOCK) {
            if (!Files.exists(CHECKPOINT_FILE)) return false;
            Path backup = CHECKPOINT_FILE.resolveSibling(
                    CHECKPOINT_FILE.getFileName() + ".pre-migration-v" + Math.max(1, cp.sourceVersion) + ".bak");
            try {
                Files.copy(CHECKPOINT_FILE, backup, StandardCopyOption.REPLACE_EXISTING);
                save(cp);
                Checkpoint verified = load();
                if (verified != null && verified.isUsable() && verified.version == CURRENT_VERSION) {
                    return true;
                }
                Files.copy(backup, CHECKPOINT_FILE, StandardCopyOption.REPLACE_EXISTING);
                return false;
            } catch (IOException ex) {
                ErrorLog.logException("[campaign] checkpoint_migration_verify_failed path=" + CHECKPOINT_FILE, ex);
                try {
                    if (Files.exists(backup)) {
                        Files.copy(backup, CHECKPOINT_FILE, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException restoreEx) {
                    ErrorLog.logException("[campaign] checkpoint_migration_restore_failed path=" + CHECKPOINT_FILE, restoreEx);
                }
                return false;
            }
        }
    }

    private static void migrateCheckpoint(Checkpoint cp, Properties props) {
        if (cp == null || props == null) return;
        int sourceVersion = Math.max(1, cp.sourceVersion);
        LinkedHashSet<String> repairs = new LinkedHashSet<>();

        if (!props.containsKey("campaignFuel")) {
            cp.campaignFuel = 120;
            repairs.add("fuel reserve");
        }
        if (!props.containsKey("campaignSupplies")) {
            cp.campaignSupplies = 90;
            repairs.add("supply reserve");
        }
        if (!props.containsKey("campaignAmmo")) {
            cp.campaignAmmo = 100;
            repairs.add("ammunition reserve");
        }
        if (!props.containsKey("campaignSalvage")) {
            cp.campaignSalvage = 0;
            repairs.add("salvage ledger");
        }
        int migratedOre = Math.max(0, cp.campaignFuel)
                + Math.max(0, cp.campaignSupplies)
                + Math.max(0, cp.campaignAmmo)
                + Math.max(0, cp.campaignSalvage) * 100;
        if (sourceVersion < 5 && migratedOre > 0) {
            cp.campaignOre = Math.max(0, cp.campaignOre) + migratedOre;
            cp.campaignFuel = 0;
            cp.campaignSupplies = 0;
            cp.campaignAmmo = 0;
            cp.campaignSalvage = 0;
            repairs.add("legacy resource conversion");
        }
        if (!props.containsKey("strategicTorpedoCharges")) {
            cp.strategicTorpedoCharges = 2;
            repairs.add("torpedo inventory");
        }
        if (!props.containsKey("strategicAtomicCharges")) {
            cp.strategicAtomicCharges = 1;
            repairs.add("atomic inventory");
        }
        if (!props.containsKey("persistentBlueFleet") || cp.persistentBlueFleet.isBlank()) {
            cp.persistentBlueFleet = DEFAULT_PERSISTENT_BLUE_FLEET;
            repairs.add("friendly fleet roster");
        }
        if (!props.containsKey("campaignYardOrders")) {
            cp.campaignYardOrders = "";
            cp.nextCampaignYardOrderId = 1;
            repairs.add("production queue");
        }
        if (!props.containsKey("campaignOre")) {
            cp.campaignOre = Math.max(24, cp.cargo);
            repairs.add("mining cargo");
        }
        if (!props.containsKey("campaignTheaters") || !props.containsKey("strategicNodes")) {
            cp.campaignTheaters = "";
            cp.strategicNodes = "";
            repairs.add("territory state");
        }
        if (!props.containsKey("diplomacyNarrativeState")) {
            cp.diplomacyNarrativeState = "";
            repairs.add("reputation history");
        }
        if (!props.containsKey("flagshipOperationsState")) repairs.add("flagship operations");
        if (!props.containsKey("boardingRescueState")) repairs.add("boarding and rescue operations");
        if (!props.containsKey("alternativeCampaignState")) repairs.add("alternative campaign identity");
        if (!props.containsKey("cooperativeCommandState")) repairs.add("cooperative command seats");
        if (!props.containsKey("warMemoryState")) repairs.add("structured war memory");
        if (!props.containsKey("galaxyLocationStates")) {
            cp.galaxyLocationStates = "";
            repairs.add("environment state");
        }
        if (!props.containsKey("campaignForces")) {
            cp.campaignForces = "";
            cp.shipCampaignForceIds = "";
            cp.nextCampaignForceId = 1;
            repairs.add("faction fleets");
        }

        repairEnum(props, "playerFactionName", cp.playerFactionName,
                Set.of("PLAYER", "ALLY", "ENEMY", "TEAM_C", "TEAM_D", "BRIGHT_YELLOW", "DARK_YELLOW"), "PLAYER", repairs,
                value -> cp.playerFactionName = value);
        repairEnum(props, "branchRoute", cp.branchRoute,
                Set.of("BALANCED", "GREEN", "YELLOW"), "BALANCED", repairs,
                value -> cp.branchRoute = value);
        repairEnum(props, "powerPresetName", cp.powerPresetName,
                Set.of("BALANCED", "WEAPONS", "SHIELDS", "ENGINES"), "BALANCED", repairs,
                value -> cp.powerPresetName = value);

        cp.migrationApplied = sourceVersion < CURRENT_VERSION || !repairs.isEmpty();
        cp.migrationRepairs = String.join(", ", repairs);
        cp.migrationMessage = cp.migrationApplied
                ? "SAVE RECOVERED FROM SCHEMA " + sourceVersion + "  |  REPAIRED "
                + (repairs.isEmpty() ? "VERSION METADATA" : cp.migrationRepairs.toUpperCase())
                : "";
    }

    private interface StringRepair {
        void apply(String value);
    }

    private static void repairEnum(Properties props,
                                   String key,
                                   String current,
                                   Set<String> allowed,
                                   String fallback,
                                   Set<String> repairs,
                                   StringRepair repair) {
        if (!props.containsKey(key)) return;
        String value = current == null ? "" : current.trim().toUpperCase();
        if (allowed.contains(value)) return;
        repair.apply(fallback);
        repairs.add(key + " enum");
    }

    public static Checkpoint loadSlot(String slotId) {
        synchronized (IO_LOCK) {
            Path path = slotPath(slotId);
            if (!Files.exists(path)) return null;
            return readCheckpointThroughPrimary(path);
        }
    }

    public static void saveSlot(String slotId, Checkpoint cp) {
        if (cp == null) return;
        synchronized (IO_LOCK) {
            writeCheckpointThroughPrimary(slotPath(slotId), cp, "slot_save");
        }
    }

    public static void saveAutosave(Checkpoint cp, int rotation) {
        if (cp == null) return;
        synchronized (IO_LOCK) {
            int safeRotation = clamp(rotation, 1, 12);
            int index = nextAutosaveIndex(safeRotation);
            writeCheckpointThroughPrimary(autosavePath(index), cp, "autosave");
            writeAutosaveCursor(index);
            pruneAutosaves(safeRotation);
        }
    }

    public static Checkpoint recoverLatestAutosave() {
        synchronized (IO_LOCK) {
            int cursor = readAutosaveCursor();
            if (cursor > 0) {
                Checkpoint latest = readCheckpointThroughPrimary(autosavePath(cursor));
                if (latest != null) return latest;
            }
            List<Path> autosaves = autosaveFiles();
            for (Path path : autosaves) {
                Checkpoint cp = readCheckpointThroughPrimary(path);
                if (cp != null) return cp;
            }
            return null;
        }
    }

    public static List<SlotSummary> listSlots() {
        synchronized (IO_LOCK) {
            List<SlotSummary> out = new ArrayList<>();
            addSlotSummary(out, SLOT_PRIMARY, "Primary campaign", CHECKPOINT_FILE, false);
            Path dir = slotDir();
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    stream.filter(path -> path.getFileName().toString().endsWith(".properties"))
                            .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                            .forEach(path -> addSlotSummary(out,
                                    stripPropertiesSuffix(path.getFileName().toString()),
                                    slotLabel(stripPropertiesSuffix(path.getFileName().toString())),
                                    path,
                                    false));
                } catch (IOException ex) {
                    ErrorLog.logException("[campaign] slot_list_failed path=" + dir, ex);
                }
            }
            for (Path path : autosaveFiles()) {
                String id = stripPropertiesSuffix(path.getFileName().toString());
                addSlotSummary(out, id, "Autosave " + id.substring(AUTOSAVE_PREFIX.length()), path, true);
            }
            return out;
        }
    }

    public static void save(Checkpoint cp) {
        if (cp == null) return;
        synchronized (IO_LOCK) {
            cp.normalize();
            try {
                Files.createDirectories(SAVE_DIR);
            } catch (IOException ex) {
                ErrorLog.logException("[campaign] checkpoint_save_failed mkdir path=" + SAVE_DIR, ex);
                return;
            }

            Properties props = new Properties();
            props.setProperty("version", String.valueOf(cp.version));
            props.setProperty("worldW", String.valueOf(cp.worldW));
            props.setProperty("worldH", String.valueOf(cp.worldH));
            props.setProperty("randomEvents", String.valueOf(cp.randomEvents));
            props.setProperty("seed", String.valueOf(cp.seed));
            props.setProperty("nextSector", String.valueOf(cp.nextSector));
            props.setProperty("credits", String.valueOf(cp.credits));
            props.setProperty("sectorsCleared", String.valueOf(cp.sectorsCleared));
            props.setProperty("campaignKills", String.valueOf(cp.campaignKills));
            props.setProperty("branchScore", String.valueOf(cp.branchScore));
            props.setProperty("branchRoute", cp.branchRoute);
            props.setProperty("sideObjectivesCompletedTotal", String.valueOf(cp.sideObjectivesCompletedTotal));
            props.setProperty("sideObjectivesFailedTotal", String.valueOf(cp.sideObjectivesFailedTotal));
            props.setProperty("unlockAuxGunGranted", String.valueOf(cp.unlockAuxGunGranted));
            props.setProperty("unlockMissileTierGranted", String.valueOf(cp.unlockMissileTierGranted));
            props.setProperty("unlockCiwsGranted", String.valueOf(cp.unlockCiwsGranted));
            props.setProperty("unlockHullGranted", String.valueOf(cp.unlockHullGranted));
            props.setProperty("bossDropAegisArray", String.valueOf(cp.bossDropAegisArray));
            props.setProperty("bossDropMissileCore", String.valueOf(cp.bossDropMissileCore));
            props.setProperty("bossDropFlagCore", String.valueOf(cp.bossDropFlagCore));
            props.setProperty("bossDropsCollected", String.valueOf(cp.bossDropsCollected));
            props.setProperty("playerFactionName", cp.playerFactionName);
            props.setProperty("playerRoleName", cp.playerRoleName);
            props.setProperty("primaryWeaponFamilyName", cp.primaryWeaponFamilyName);
            props.setProperty("hpMax", String.valueOf(cp.hpMax));
            props.setProperty("shieldMax", String.valueOf(cp.shieldMax));
            props.setProperty("shieldRegen", String.valueOf(cp.shieldRegen));
            props.setProperty("shieldActive", String.valueOf(cp.shieldActive));
            props.setProperty("campaignOre", String.valueOf(cp.campaignOre));
            props.setProperty("cargo", String.valueOf(cp.cargo));
            props.setProperty("cargoMax", String.valueOf(cp.cargoMax));
            props.setProperty("miningRate", String.valueOf(cp.miningRate));
            props.setProperty("miningRange", String.valueOf(cp.miningRange));
            props.setProperty("orePriceBaseMul", String.valueOf(cp.orePriceBaseMul));
            props.setProperty("miningBaseMul", String.valueOf(cp.miningBaseMul));
            props.setProperty("hasCIWS", String.valueOf(cp.hasCIWS));
            props.setProperty("ciwsRange", String.valueOf(cp.ciwsRange));
            props.setProperty("ciwsCooldown", String.valueOf(cp.ciwsCooldown));
            props.setProperty("ciwsQuality", String.valueOf(cp.ciwsQuality));
            props.setProperty("ciwsPelletsPerBurst", String.valueOf(cp.ciwsPelletsPerBurst));
            props.setProperty("ciwsPelletSpeed", String.valueOf(cp.ciwsPelletSpeed));
            props.setProperty("ciwsPelletDamage", String.valueOf(cp.ciwsPelletDamage));
            props.setProperty("ciwsPelletLife", String.valueOf(cp.ciwsPelletLife));
            props.setProperty("ciwsPelletRadius", String.valueOf(cp.ciwsPelletRadius));
            props.setProperty("powerPresetName", cp.powerPresetName);
            props.setProperty("crewOrderName", cp.crewOrderName);
            props.setProperty("engineeringPriorityName", cp.engineeringPriorityName);
            props.setProperty("overloadBusName", cp.overloadBusName);
            props.setProperty("powerBuses", cp.powerBuses);
            props.setProperty("turretData", cp.turretData);
            props.setProperty("isCarrier", String.valueOf(cp.isCarrier));
            props.setProperty("maxFighters", String.valueOf(cp.maxFighters));
            props.setProperty("carrierCommandModeName", cp.carrierCommandModeName);
            props.setProperty("carrierAutoLaunch", String.valueOf(cp.carrierAutoLaunch));
            props.setProperty("flightDeckLoadout", cp.flightDeckLoadout);
            props.setProperty("ownedTitans", cp.ownedTitans);
            props.setProperty("persistentBlueFleet", cp.persistentBlueFleet);
            props.setProperty("campaignYardOrders", cp.campaignYardOrders);
            props.setProperty("nextCampaignYardOrderId", String.valueOf(cp.nextCampaignYardOrderId));
            props.setProperty("selectedStrategicOverlayId", cp.selectedStrategicOverlayId);
            props.setProperty("factionAttackCommitments", cp.factionAttackCommitments);
            props.setProperty("selectedCampaignTaskGroupId", String.valueOf(cp.selectedCampaignTaskGroupId));
            props.setProperty("escortCapUpgradeLevel", String.valueOf(cp.escortCapUpgradeLevel));
            props.setProperty("lineCapUpgradeLevel", String.valueOf(cp.lineCapUpgradeLevel));
            props.setProperty("capitalCapUpgradeLevel", String.valueOf(cp.capitalCapUpgradeLevel));
            props.setProperty("campaignBlueYellowAlliance", String.valueOf(cp.campaignBlueYellowAlliance));
            props.setProperty("greenContractFleetJoined", String.valueOf(cp.greenContractFleetJoined));
            props.setProperty("yellowLiberationFleetJoined", String.valueOf(cp.yellowLiberationFleetJoined));
            props.setProperty("greenContractFavor", String.valueOf(cp.greenContractFavor));
            props.setProperty("yellowLiberationFavor", String.valueOf(cp.yellowLiberationFavor));
            props.setProperty("fleetStrain", String.valueOf(cp.fleetStrain));
            props.setProperty("vossRelationshipStateId", cp.vossRelationshipStateId);
            props.setProperty("marrRelationshipStateId", cp.marrRelationshipStateId);
            props.setProperty("rookRelationshipStateId", cp.rookRelationshipStateId);
            props.setProperty("strategicOvermapMode", String.valueOf(cp.strategicOvermapMode));
            props.setProperty("currentGalaxyLocationId", cp.currentGalaxyLocationId);
            props.setProperty("selectedGalaxyLocationId", cp.selectedGalaxyLocationId);
            props.setProperty("dockedGalaxyLocationId", cp.dockedGalaxyLocationId);
            props.setProperty("activeGalaxyEncounterLocationId", cp.activeGalaxyEncounterLocationId);
            props.setProperty("activeGalaxyEncounterSearchGroupId", String.valueOf(cp.activeGalaxyEncounterSearchGroupId));
            props.setProperty("activeGalaxyEncounterForceIds", cp.activeGalaxyEncounterForceIds);
            props.setProperty("activeGalaxyEncounterParentForceId", String.valueOf(cp.activeGalaxyEncounterParentForceId));
            props.setProperty("selectedFleetPostureId", cp.selectedFleetPostureId);
            props.setProperty("selectedSiteResolutionModeId", cp.selectedSiteResolutionModeId);
            props.setProperty("activeSiteResolutionModeId", cp.activeSiteResolutionModeId);
            props.setProperty("completedMainMissions", String.valueOf(cp.completedMainMissions));
            props.setProperty("earthProgress", String.valueOf(cp.earthProgress));
            props.setProperty("enemyAlertLevel", String.valueOf(cp.enemyAlertLevel));
            props.setProperty("campaignIntelLevel", String.valueOf(cp.campaignIntelLevel));
            props.setProperty("strategicExposureLevel", String.valueOf(cp.strategicExposureLevel));
            props.setProperty("recentStrikePressure", String.valueOf(cp.recentStrikePressure));
            props.setProperty("galaxyEncounterActive", String.valueOf(cp.galaxyEncounterActive));
            props.setProperty("galaxyAmbientEncounterActive", String.valueOf(cp.galaxyAmbientEncounterActive));
            props.setProperty("activeMapModifiers", cp.activeMapModifiers);
            props.setProperty("environmentHazardPulseIndex", String.valueOf(cp.environmentHazardPulseIndex));
            props.setProperty("campaignFuel", String.valueOf(cp.campaignFuel));
            props.setProperty("campaignSupplies", String.valueOf(cp.campaignSupplies));
            props.setProperty("campaignAmmo", String.valueOf(cp.campaignAmmo));
            props.setProperty("campaignSalvage", String.valueOf(cp.campaignSalvage));
            props.setProperty("lastSensorSweepAtSec", String.valueOf(cp.lastSensorSweepAtSec));
            props.setProperty("travelFuelAttritionRemainder", String.valueOf(cp.travelFuelAttritionRemainder));
            props.setProperty("travelSupplyAttritionRemainder", String.valueOf(cp.travelSupplyAttritionRemainder));
            props.setProperty("travelAmmoAttritionRemainder", String.valueOf(cp.travelAmmoAttritionRemainder));
            props.setProperty("playerGalaxyX", String.valueOf(cp.playerGalaxyX));
            props.setProperty("playerGalaxyY", String.valueOf(cp.playerGalaxyY));
            props.setProperty("playerGalaxyHeadingDeg", String.valueOf(cp.playerGalaxyHeadingDeg));
            props.setProperty("selectedFreeGalaxyTargetX", String.valueOf(cp.selectedFreeGalaxyTargetX));
            props.setProperty("selectedFreeGalaxyTargetY", String.valueOf(cp.selectedFreeGalaxyTargetY));
            props.setProperty("transitEventCooldownSec", String.valueOf(cp.transitEventCooldownSec));
            props.setProperty("transitEncounterPressure", String.valueOf(cp.transitEncounterPressure));
            props.setProperty("transitNextEncounterThreshold", String.valueOf(cp.transitNextEncounterThreshold));
            props.setProperty("transitContactEventsThisLeg", String.valueOf(cp.transitContactEventsThisLeg));
            props.setProperty("transitContactTargetThisLeg", String.valueOf(cp.transitContactTargetThisLeg));
            props.setProperty("transientGalaxySiteSerial", String.valueOf(cp.transientGalaxySiteSerial));
            props.setProperty("strategicTorpedoCharges", String.valueOf(cp.strategicTorpedoCharges));
            props.setProperty("strategicSortiesLaunched", String.valueOf(cp.strategicSortiesLaunched));
            props.setProperty("strategicAtomicCharges", String.valueOf(cp.strategicAtomicCharges));
            props.setProperty("torpedoStrikeCooldownSec", String.valueOf(cp.torpedoStrikeCooldownSec));
            props.setProperty("carrierSortieCooldownSec", String.valueOf(cp.carrierSortieCooldownSec));
            props.setProperty("atomicStrikeCooldownSec", String.valueOf(cp.atomicStrikeCooldownSec));
            props.setProperty("nextGalaxySearchGroupId", String.valueOf(cp.nextGalaxySearchGroupId));
            props.setProperty("nextStrategicStrikeObjectId", String.valueOf(cp.nextStrategicStrikeObjectId));
            props.setProperty("defeatedStrategicTaskForceKeys", cp.defeatedStrategicTaskForceKeys);
            props.setProperty("nextCampaignForceId", String.valueOf(cp.nextCampaignForceId));
            props.setProperty("defeatedCampaignForceKeys", cp.defeatedCampaignForceKeys);
            props.setProperty("lastChecklistFleetSeedSector", String.valueOf(cp.lastChecklistFleetSeedSector));
            props.setProperty("strategicStrikeObjects", cp.strategicStrikeObjects);
            props.setProperty("strategicDivisions", cp.strategicDivisions);
            props.setProperty("campaignForces", cp.campaignForces);
            props.setProperty("shipCampaignForceIds", cp.shipCampaignForceIds);
            props.setProperty("campaignIntelTick", String.valueOf(cp.campaignIntelTick));
            props.setProperty("campaignFleetIntel", cp.campaignFleetIntel);
            props.setProperty("campaignOperationIntel", cp.campaignOperationIntel);
            props.setProperty("enemyPlayerContact", cp.enemyPlayerContact);
            props.setProperty("nextPendingHostileReinforcementId", String.valueOf(cp.nextPendingHostileReinforcementId));
            props.setProperty("pendingHostileReinforcements", cp.pendingHostileReinforcements);
            props.setProperty("nextCampaignBattleId", String.valueOf(cp.nextCampaignBattleId));
            props.setProperty("campaignBattles", cp.campaignBattles);
            props.setProperty("nextAfterActionReportId", String.valueOf(cp.nextAfterActionReportId));
            props.setProperty("campaignAfterActionReports", cp.campaignAfterActionReports);
            props.setProperty("nextCampaignLogEntryId", String.valueOf(cp.nextCampaignLogEntryId));
            props.setProperty("campaignCaptainLog", cp.campaignCaptainLog);
            props.setProperty("campaignMemoryFlags", cp.campaignMemoryFlags);
            props.setProperty("nextCampaignShipRecordId", String.valueOf(cp.nextCampaignShipRecordId));
            props.setProperty("nextCampaignBaseQueueId", String.valueOf(cp.nextCampaignBaseQueueId));
            props.setProperty("campaignFiniteEconomyInitialized", String.valueOf(cp.campaignFiniteEconomyInitialized));
            props.setProperty("campaignEconomyTickAccumulatorSec", String.valueOf(cp.campaignEconomyTickAccumulatorSec));
            props.setProperty("campaignShipPool", cp.campaignShipPool);
            props.setProperty("campaignBaseQueues", cp.campaignBaseQueues);
            props.setProperty("factionDirectorAccumulatorSec", String.valueOf(cp.factionDirectorAccumulatorSec));
            props.setProperty("redDirectorBrief", cp.redDirectorBrief);
            props.setProperty("greenDirectorBrief", cp.greenDirectorBrief);
            props.setProperty("yellowDirectorBrief", cp.yellowDirectorBrief);
            props.setProperty("strategicExpansionState", cp.strategicExpansionState);
            props.setProperty("economyExpansionState", cp.economyExpansionState);
            props.setProperty("diplomacyNarrativeState", cp.diplomacyNarrativeState);
            props.setProperty("operationsExpansionState", cp.operationsExpansionState);
            props.setProperty("flagshipOperationsState", cp.flagshipOperationsState);
            props.setProperty("boardingRescueState", cp.boardingRescueState);
            props.setProperty("alternativeCampaignState", cp.alternativeCampaignState);
            props.setProperty("cooperativeCommandState", cp.cooperativeCommandState);
            props.setProperty("warMemoryState", cp.warMemoryState);
            props.setProperty("productionReadinessState", cp.productionReadinessState);
            props.setProperty("fleetDoctrineExpansionState", cp.fleetDoctrineExpansionState);
            props.setProperty("deepCampaignExpansionState", cp.deepCampaignExpansionState);
            props.setProperty("communityContentState", cp.communityContentState);
            props.setProperty("galaxyTravelOriginId", cp.galaxyTravelOriginId);
            props.setProperty("galaxyTravelDestinationId", cp.galaxyTravelDestinationId);
            props.setProperty("galaxyTravelDestinationLabel", cp.galaxyTravelDestinationLabel);
            props.setProperty("galaxyTravelProgress", String.valueOf(cp.galaxyTravelProgress));
            props.setProperty("galaxyTravelDurationSec", String.valueOf(cp.galaxyTravelDurationSec));
            props.setProperty("galaxyTravelTraveling", String.valueOf(cp.galaxyTravelTraveling));
            props.setProperty("galaxyTravelFreeTravel", String.valueOf(cp.galaxyTravelFreeTravel));
            props.setProperty("galaxyTravelInterceptionRisk", String.valueOf(cp.galaxyTravelInterceptionRisk));
            props.setProperty("galaxyTravelTargetX", String.valueOf(cp.galaxyTravelTargetX));
            props.setProperty("galaxyTravelTargetY", String.valueOf(cp.galaxyTravelTargetY));
            props.setProperty("galaxyTravelSpeed", String.valueOf(cp.galaxyTravelSpeed));
            props.setProperty("galaxyLocationStates", cp.galaxyLocationStates);
            props.setProperty("galaxySearchGroups", cp.galaxySearchGroups);
            props.setProperty("campaignTheaters", cp.campaignTheaters);
            props.setProperty("strategicNodes", cp.strategicNodes);
            props.setProperty("theaterWarRecentEvents", cp.theaterWarRecentEvents);
            props.setProperty("completedCampaignBoardMissionIds", cp.completedCampaignBoardMissionIds);
            props.setProperty("expiredCampaignBoardMissionIds", cp.expiredCampaignBoardMissionIds);
            props.setProperty("theaterWarTickAccumulatorSec", String.valueOf(cp.theaterWarTickAccumulatorSec));
            props.setProperty("theaterWarTickIndex", String.valueOf(cp.theaterWarTickIndex));
            props.setProperty("selectedTheaterId", cp.selectedTheaterId);
            props.setProperty("blueInterventionReserve", String.valueOf(cp.blueInterventionReserve));
            props.setProperty("earthOperationStage", String.valueOf(cp.earthOperationStage));
            props.setProperty("redGlobalCollapseActive", String.valueOf(cp.redGlobalCollapseActive));
            props.setProperty("allyOreStockpile", String.valueOf(cp.allyOreStockpile));
            props.setProperty("enemyOreStockpile", String.valueOf(cp.enemyOreStockpile));
            props.setProperty("allyHullLv", String.valueOf(cp.allyHullLv));
            props.setProperty("allyShieldLv", String.valueOf(cp.allyShieldLv));
            props.setProperty("allyTurretLv", String.valueOf(cp.allyTurretLv));
            props.setProperty("allyMiningLv", String.valueOf(cp.allyMiningLv));
            props.setProperty("allyHangarLv", String.valueOf(cp.allyHangarLv));
            props.setProperty("enemyHullLv", String.valueOf(cp.enemyHullLv));
            props.setProperty("enemyShieldLv", String.valueOf(cp.enemyShieldLv));
            props.setProperty("enemyTurretLv", String.valueOf(cp.enemyTurretLv));
            props.setProperty("enemyMiningLv", String.valueOf(cp.enemyMiningLv));
            props.setProperty("enemyHangarLv", String.valueOf(cp.enemyHangarLv));

            Path tmp = CHECKPOINT_FILE.resolveSibling(CHECKPOINT_FILE.getFileName() + ".tmp");
            try (FileChannel channel = FileChannel.open(
                    tmp,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
                 OutputStream out = Channels.newOutputStream(channel)) {
                props.store(out, "Campaign checkpoint");
                out.flush();
                channel.force(true);
            } catch (IOException ex) {
                ErrorLog.logException("[campaign] checkpoint_save_failed write path=" + tmp, ex);
                deleteTempQuietly(tmp);
                return;
            }

            try {
                Files.move(tmp, CHECKPOINT_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                try {
                    Files.move(tmp, CHECKPOINT_FILE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex2) {
                    ErrorLog.logException("[campaign] checkpoint_save_failed move path=" + CHECKPOINT_FILE, ex2);
                    deleteTempQuietly(tmp);
                }
            } catch (IOException ex) {
                ErrorLog.logException("[campaign] checkpoint_save_failed move path=" + CHECKPOINT_FILE, ex);
                deleteTempQuietly(tmp);
            }
        }
    }

    public static void clear() {
        synchronized (IO_LOCK) {
            try {
                Files.deleteIfExists(CHECKPOINT_FILE);
            } catch (IOException ex) {
                ErrorLog.logException("[campaign] checkpoint_clear_failed path=" + CHECKPOINT_FILE, ex);
            }
            clearDirectory(slotDir(), "slot_clear");
            clearDirectory(autosaveDir(), "autosave_clear");
        }
    }

    private static void addSlotSummary(List<SlotSummary> out, String id, String label, Path path, boolean autosave) {
        if (out == null || path == null || !Files.exists(path)) return;
        Checkpoint cp = readCheckpointThroughPrimary(path);
        boolean recoverable = cp != null;
        String summary = recoverable ? cp.menuSummary() : "Recovery available: checkpoint file is damaged";
        out.add(new SlotSummary(id, label, summary, autosave, recoverable));
    }

    private static Checkpoint readCheckpointThroughPrimary(Path source) {
        if (source == null || !Files.exists(source)) return null;
        if (samePath(source, CHECKPOINT_FILE)) return load();
        byte[] original = readPrimaryBytes();
        boolean hadOriginal = Files.exists(CHECKPOINT_FILE);
        try {
            Files.createDirectories(CHECKPOINT_FILE.getParent());
            Files.copy(source, CHECKPOINT_FILE, StandardCopyOption.REPLACE_EXISTING);
            return load();
        } catch (IOException ex) {
            ErrorLog.logException("[campaign] checkpoint_slot_load_failed path=" + source, ex);
            return null;
        } finally {
            restorePrimaryBytes(original, hadOriginal);
        }
    }

    private static void writeCheckpointThroughPrimary(Path target, Checkpoint cp, String reason) {
        if (target == null || cp == null) return;
        if (samePath(target, CHECKPOINT_FILE)) {
            save(cp);
            return;
        }
        byte[] original = readPrimaryBytes();
        boolean hadOriginal = Files.exists(CHECKPOINT_FILE);
        try {
            save(cp);
            Files.createDirectories(target.getParent());
            Files.copy(CHECKPOINT_FILE, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            ErrorLog.logException("[campaign] checkpoint_" + reason + "_failed path=" + target, ex);
        } finally {
            restorePrimaryBytes(original, hadOriginal);
        }
    }

    private static byte[] readPrimaryBytes() {
        try {
            return Files.exists(CHECKPOINT_FILE) ? Files.readAllBytes(CHECKPOINT_FILE) : null;
        } catch (IOException ex) {
            ErrorLog.logException("[campaign] checkpoint_primary_backup_failed path=" + CHECKPOINT_FILE, ex);
            return null;
        }
    }

    private static void restorePrimaryBytes(byte[] original, boolean hadOriginal) {
        try {
            if (hadOriginal && original != null) {
                Files.createDirectories(CHECKPOINT_FILE.getParent());
                Files.write(CHECKPOINT_FILE, original, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                Files.deleteIfExists(CHECKPOINT_FILE);
            }
        } catch (IOException ex) {
            ErrorLog.logException("[campaign] checkpoint_primary_restore_failed path=" + CHECKPOINT_FILE, ex);
        }
    }

    private static int nextAutosaveIndex(int rotation) {
        int current = readAutosaveCursor();
        return (current % Math.max(1, rotation)) + 1;
    }

    private static int readAutosaveCursor() {
        Path cursor = autosaveCursorPath();
        if (!Files.exists(cursor)) return 0;
        try {
            return Math.max(0, Integer.parseInt(Files.readString(cursor).trim()));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static void writeAutosaveCursor(int index) {
        try {
            Files.createDirectories(autosaveDir());
            Files.writeString(autosaveCursorPath(), String.valueOf(Math.max(1, index)),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            ErrorLog.logException("[campaign] autosave_cursor_failed path=" + autosaveCursorPath(), ex);
        }
    }

    private static void pruneAutosaves(int rotation) {
        for (Path path : autosaveFiles()) {
            String id = stripPropertiesSuffix(path.getFileName().toString());
            int n = parseAutosaveNumber(id);
            if (n > rotation) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    ErrorLog.logException("[campaign] autosave_prune_failed path=" + path, ex);
                }
            }
        }
    }

    private static List<Path> autosaveFiles() {
        List<Path> out = new ArrayList<>();
        Path dir = autosaveDir();
        if (!Files.isDirectory(dir)) return out;
        try (var stream = Files.list(dir)) {
            stream.filter(path -> path.getFileName().toString().startsWith(AUTOSAVE_PREFIX))
                    .filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .sorted(Comparator.comparingInt((Path path) -> parseAutosaveNumber(
                            stripPropertiesSuffix(path.getFileName().toString()))).reversed())
                    .forEach(out::add);
        } catch (IOException ex) {
            ErrorLog.logException("[campaign] autosave_list_failed path=" + dir, ex);
        }
        return out;
    }

    private static int parseAutosaveNumber(String id) {
        if (id == null || !id.startsWith(AUTOSAVE_PREFIX)) return 0;
        try {
            return Integer.parseInt(id.substring(AUTOSAVE_PREFIX.length()).trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Path slotPath(String slotId) {
        String safe = sanitizeSlotId(slotId);
        if (SLOT_PRIMARY.equals(safe)) return CHECKPOINT_FILE;
        return slotDir().resolve(safe + ".properties");
    }

    private static Path autosavePath(int index) {
        return autosaveDir().resolve(AUTOSAVE_PREFIX + Math.max(1, index) + ".properties");
    }

    private static Path slotDir() {
        return checkpointRoot().resolve("campaign_slots");
    }

    private static Path autosaveDir() {
        return checkpointRoot().resolve("campaign_autosaves");
    }

    private static Path autosaveCursorPath() {
        return autosaveDir().resolve("cursor.txt");
    }

    private static Path checkpointRoot() {
        Path parent = CHECKPOINT_FILE.getParent();
        return parent == null ? Paths.get(".") : parent;
    }

    private static String sanitizeSlotId(String raw) {
        String value = (raw == null || raw.isBlank()) ? SLOT_PRIMARY : raw.trim().toLowerCase();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
                out.append(c);
            } else if (Character.isWhitespace(c)) {
                out.append('-');
            }
        }
        return out.length() == 0 ? SLOT_PRIMARY : out.toString();
    }

    private static String slotLabel(String slotId) {
        if (slotId == null || slotId.isBlank()) return "Campaign slot";
        String[] parts = slotId.replace('_', '-').split("-");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part.substring(1));
        }
        return out.length() == 0 ? "Campaign slot" : out.toString();
    }

    private static String stripPropertiesSuffix(String name) {
        if (name == null) return "";
        return name.endsWith(".properties") ? name.substring(0, name.length() - ".properties".length()) : name;
    }

    private static boolean samePath(Path a, Path b) {
        if (a == null || b == null) return false;
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }

    private static void clearDirectory(Path dir, String reason) {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (var stream = Files.list(dir)) {
            stream.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    ErrorLog.logException("[campaign] checkpoint_" + reason + "_failed path=" + path, ex);
                }
            });
            Files.deleteIfExists(dir);
        } catch (IOException ex) {
            ErrorLog.logException("[campaign] checkpoint_" + reason + "_failed path=" + dir, ex);
        }
    }

    private static int parseInt(Properties props, String key, int fallback) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(fallback)).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long parseLong(Properties props, String key, long fallback) {
        try {
            return Long.parseLong(props.getProperty(key, String.valueOf(fallback)).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(Properties props, String key, boolean fallback) {
        String raw = props.getProperty(key);
        if (raw == null) return fallback;
        return Boolean.parseBoolean(raw.trim());
    }

    private static double parseDouble(Properties props, String key, double fallback) {
        try {
            return Double.parseDouble(props.getProperty(key, String.valueOf(fallback)).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String safeName(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isBlank() ? fallback : trimmed;
    }

    private static int countCsvEntries(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        int count = 0;
        for (String part : raw.split(",")) {
            if (part != null && !part.trim().isEmpty()) count++;
        }
        return count;
    }

    private static int countSerializedFleetEntries(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        int count = 0;
        for (String part : raw.split(";")) {
            if (part != null && !part.trim().isEmpty()) count++;
        }
        return count;
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void deleteTempQuietly(Path tmp) {
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }
}

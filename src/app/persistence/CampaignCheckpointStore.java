package app.persistence;

import app.config.GameConfig;
import app.config.GameMode;
import app.support.ErrorLog;
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
import java.util.Properties;

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

    private static final int CURRENT_VERSION = 1;
    private static final int MAX_CAMPAIGN_SECTORS = 24;
    private static final Path SAVE_DIR = Paths.get("save");
    private static final Path CHECKPOINT_FILE = Paths.get(
            System.getProperty("codex.checkpointFile", SAVE_DIR.resolve("campaign_checkpoint.properties").toString()));
    private static final Object IO_LOCK = new Object();

    public static final class Checkpoint {
        public int version = CURRENT_VERSION;

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
        public boolean galaxyEncounterActive = false;
        public boolean galaxyAmbientEncounterActive = false;
        public int campaignFuel = 0;
        public int campaignSupplies = 0;
        public int campaignAmmo = 0;
        public int campaignSalvage = 0;
        public double playerGalaxyX = Double.NaN;
        public double playerGalaxyY = Double.NaN;
        public double playerGalaxyHeadingDeg = -90.0;
        public double selectedFreeGalaxyTargetX = Double.NaN;
        public double selectedFreeGalaxyTargetY = Double.NaN;
        public double transitEventCooldownSec = 0.0;
        public int transientGalaxySiteSerial = 0;
        public int strategicTorpedoCharges = 0;
        public int strategicSortiesLaunched = 0;
        public int strategicAtomicCharges = 0;
        public int nextGalaxySearchGroupId = 1;
        public int nextStrategicStrikeObjectId = 1;
        public int nextCampaignForceId = 1;
        public int lastChecklistFleetSeedSector = 0;
        public String strategicStrikeObjects = "";
        public String strategicDivisions = "";
        public String campaignForces = "";
        public String shipCampaignForceIds = "";
        public String enemyPlayerContact = "";
        public int nextPendingHostileReinforcementId = 1;
        public String pendingHostileReinforcements = "";
        public int nextCampaignBattleId = 1;
        public String campaignBattles = "";
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
            campaignFuel = Math.max(0, campaignFuel);
            campaignSupplies = Math.max(0, campaignSupplies);
            campaignAmmo = Math.max(0, campaignAmmo);
            campaignSalvage = Math.max(0, campaignSalvage);
            playerGalaxyX = finiteOr(playerGalaxyX, Double.NaN);
            playerGalaxyY = finiteOr(playerGalaxyY, Double.NaN);
            playerGalaxyHeadingDeg = finiteOr(playerGalaxyHeadingDeg, -90.0);
            strategicTorpedoCharges = Math.max(0, strategicTorpedoCharges);
            strategicSortiesLaunched = Math.max(0, strategicSortiesLaunched);
            strategicAtomicCharges = Math.max(0, strategicAtomicCharges);
            nextGalaxySearchGroupId = Math.max(1, nextGalaxySearchGroupId);
            nextStrategicStrikeObjectId = Math.max(1, nextStrategicStrikeObjectId);
            nextCampaignForceId = Math.max(1, nextCampaignForceId);
            lastChecklistFleetSeedSector = clamp(lastChecklistFleetSeedSector, 0, MAX_CAMPAIGN_SECTORS);
            strategicStrikeObjects = (strategicStrikeObjects == null) ? "" : strategicStrikeObjects.trim();
            strategicDivisions = (strategicDivisions == null) ? "" : strategicDivisions.trim();
            campaignForces = (campaignForces == null) ? "" : campaignForces.trim();
            shipCampaignForceIds = (shipCampaignForceIds == null) ? "" : shipCampaignForceIds.trim();
            enemyPlayerContact = (enemyPlayerContact == null) ? "" : enemyPlayerContact.trim();
            nextPendingHostileReinforcementId = Math.max(1, nextPendingHostileReinforcementId);
            pendingHostileReinforcements = (pendingHostileReinforcements == null) ? "" : pendingHostileReinforcements.trim();
            nextCampaignBattleId = Math.max(1, nextCampaignBattleId);
            campaignBattles = (campaignBattles == null) ? "" : campaignBattles.trim();
            factionDirectorAccumulatorSec = Math.max(0.0, finiteOr(factionDirectorAccumulatorSec, 0.0));
            redDirectorBrief = safeName(redDirectorBrief, "");
            greenDirectorBrief = safeName(greenDirectorBrief, "");
            yellowDirectorBrief = safeName(yellowDirectorBrief, "");
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
                    + "  |  Titans " + countCsvEntries(ownedTitans) + "/8"
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
                cp.version = parseInt(props, "version", cp.version);
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
                cp.campaignFuel = parseInt(props, "campaignFuel", cp.campaignFuel);
                cp.campaignSupplies = parseInt(props, "campaignSupplies", cp.campaignSupplies);
                cp.campaignAmmo = parseInt(props, "campaignAmmo", cp.campaignAmmo);
                cp.campaignSalvage = parseInt(props, "campaignSalvage", cp.campaignSalvage);
                cp.playerGalaxyX = parseDouble(props, "playerGalaxyX", cp.playerGalaxyX);
                cp.playerGalaxyY = parseDouble(props, "playerGalaxyY", cp.playerGalaxyY);
                cp.playerGalaxyHeadingDeg = parseDouble(props, "playerGalaxyHeadingDeg", cp.playerGalaxyHeadingDeg);
                cp.strategicTorpedoCharges = parseInt(props, "strategicTorpedoCharges", cp.strategicTorpedoCharges);
                cp.strategicSortiesLaunched = parseInt(props, "strategicSortiesLaunched", cp.strategicSortiesLaunched);
                cp.strategicAtomicCharges = parseInt(props, "strategicAtomicCharges", cp.strategicAtomicCharges);
                cp.nextGalaxySearchGroupId = parseInt(props, "nextGalaxySearchGroupId", cp.nextGalaxySearchGroupId);
                cp.nextStrategicStrikeObjectId = parseInt(props, "nextStrategicStrikeObjectId", cp.nextStrategicStrikeObjectId);
                cp.nextCampaignForceId = parseInt(props, "nextCampaignForceId", cp.nextCampaignForceId);
                cp.lastChecklistFleetSeedSector = parseInt(props, "lastChecklistFleetSeedSector", cp.lastChecklistFleetSeedSector);
                cp.strategicStrikeObjects = props.getProperty("strategicStrikeObjects", cp.strategicStrikeObjects);
                cp.strategicDivisions = props.getProperty("strategicDivisions", cp.strategicDivisions);
                cp.campaignForces = props.getProperty("campaignForces", cp.campaignForces);
                cp.shipCampaignForceIds = props.getProperty("shipCampaignForceIds", cp.shipCampaignForceIds);
                cp.enemyPlayerContact = props.getProperty("enemyPlayerContact", cp.enemyPlayerContact);
                cp.nextPendingHostileReinforcementId = parseInt(props, "nextPendingHostileReinforcementId", cp.nextPendingHostileReinforcementId);
                cp.pendingHostileReinforcements = props.getProperty("pendingHostileReinforcements", cp.pendingHostileReinforcements);
                cp.nextCampaignBattleId = parseInt(props, "nextCampaignBattleId", cp.nextCampaignBattleId);
                cp.campaignBattles = props.getProperty("campaignBattles", cp.campaignBattles);
                cp.factionDirectorAccumulatorSec = parseDouble(props, "factionDirectorAccumulatorSec", cp.factionDirectorAccumulatorSec);
                cp.redDirectorBrief = props.getProperty("redDirectorBrief", cp.redDirectorBrief);
                cp.greenDirectorBrief = props.getProperty("greenDirectorBrief", cp.greenDirectorBrief);
                cp.yellowDirectorBrief = props.getProperty("yellowDirectorBrief", cp.yellowDirectorBrief);
                cp.galaxyTravelOriginId = props.getProperty("galaxyTravelOriginId", cp.galaxyTravelOriginId);
                cp.galaxyTravelDestinationId = props.getProperty("galaxyTravelDestinationId", cp.galaxyTravelDestinationId);
                cp.galaxyTravelProgress = parseDouble(props, "galaxyTravelProgress", cp.galaxyTravelProgress);
                cp.galaxyTravelDurationSec = parseDouble(props, "galaxyTravelDurationSec", cp.galaxyTravelDurationSec);
                cp.galaxyTravelTraveling = parseBoolean(props, "galaxyTravelTraveling", cp.galaxyTravelTraveling);
                cp.galaxyTravelInterceptionRisk = parseDouble(props, "galaxyTravelInterceptionRisk", cp.galaxyTravelInterceptionRisk);
                cp.galaxyTravelTargetX = parseDouble(props, "galaxyTravelTargetX", cp.galaxyTravelTargetX);
                cp.galaxyTravelTargetY = parseDouble(props, "galaxyTravelTargetY", cp.galaxyTravelTargetY);
                cp.galaxyTravelSpeed = parseDouble(props, "galaxyTravelSpeed", cp.galaxyTravelSpeed);
                cp.galaxyLocationStates = props.getProperty("galaxyLocationStates", cp.galaxyLocationStates);
                cp.galaxySearchGroups = props.getProperty("galaxySearchGroups", cp.galaxySearchGroups);
                cp.campaignTheaters = props.getProperty("campaignTheaters", cp.campaignTheaters);
                cp.strategicNodes = props.getProperty("strategicNodes", cp.strategicNodes);
                cp.theaterWarRecentEvents = props.getProperty("theaterWarRecentEvents", cp.theaterWarRecentEvents);
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
            } catch (IOException ex) {
                ErrorLog.logException("[campaign] checkpoint_load_failed path=" + CHECKPOINT_FILE, ex);
                return null;
            }
            cp.normalize();
            return cp.isUsable() ? cp : null;
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
            props.setProperty("campaignFuel", String.valueOf(cp.campaignFuel));
            props.setProperty("campaignSupplies", String.valueOf(cp.campaignSupplies));
            props.setProperty("campaignAmmo", String.valueOf(cp.campaignAmmo));
            props.setProperty("campaignSalvage", String.valueOf(cp.campaignSalvage));
            props.setProperty("playerGalaxyX", String.valueOf(cp.playerGalaxyX));
            props.setProperty("playerGalaxyY", String.valueOf(cp.playerGalaxyY));
            props.setProperty("playerGalaxyHeadingDeg", String.valueOf(cp.playerGalaxyHeadingDeg));
            props.setProperty("strategicTorpedoCharges", String.valueOf(cp.strategicTorpedoCharges));
            props.setProperty("strategicSortiesLaunched", String.valueOf(cp.strategicSortiesLaunched));
            props.setProperty("strategicAtomicCharges", String.valueOf(cp.strategicAtomicCharges));
            props.setProperty("nextGalaxySearchGroupId", String.valueOf(cp.nextGalaxySearchGroupId));
            props.setProperty("nextStrategicStrikeObjectId", String.valueOf(cp.nextStrategicStrikeObjectId));
            props.setProperty("nextCampaignForceId", String.valueOf(cp.nextCampaignForceId));
            props.setProperty("lastChecklistFleetSeedSector", String.valueOf(cp.lastChecklistFleetSeedSector));
            props.setProperty("strategicStrikeObjects", cp.strategicStrikeObjects);
            props.setProperty("strategicDivisions", cp.strategicDivisions);
            props.setProperty("campaignForces", cp.campaignForces);
            props.setProperty("shipCampaignForceIds", cp.shipCampaignForceIds);
            props.setProperty("enemyPlayerContact", cp.enemyPlayerContact);
            props.setProperty("nextPendingHostileReinforcementId", String.valueOf(cp.nextPendingHostileReinforcementId));
            props.setProperty("pendingHostileReinforcements", cp.pendingHostileReinforcements);
            props.setProperty("nextCampaignBattleId", String.valueOf(cp.nextCampaignBattleId));
            props.setProperty("campaignBattles", cp.campaignBattles);
            props.setProperty("factionDirectorAccumulatorSec", String.valueOf(cp.factionDirectorAccumulatorSec));
            props.setProperty("redDirectorBrief", cp.redDirectorBrief);
            props.setProperty("greenDirectorBrief", cp.greenDirectorBrief);
            props.setProperty("yellowDirectorBrief", cp.yellowDirectorBrief);
            props.setProperty("galaxyTravelOriginId", cp.galaxyTravelOriginId);
            props.setProperty("galaxyTravelDestinationId", cp.galaxyTravelDestinationId);
            props.setProperty("galaxyTravelProgress", String.valueOf(cp.galaxyTravelProgress));
            props.setProperty("galaxyTravelDurationSec", String.valueOf(cp.galaxyTravelDurationSec));
            props.setProperty("galaxyTravelTraveling", String.valueOf(cp.galaxyTravelTraveling));
            props.setProperty("galaxyTravelInterceptionRisk", String.valueOf(cp.galaxyTravelInterceptionRisk));
            props.setProperty("galaxyTravelTargetX", String.valueOf(cp.galaxyTravelTargetX));
            props.setProperty("galaxyTravelTargetY", String.valueOf(cp.galaxyTravelTargetY));
            props.setProperty("galaxyTravelSpeed", String.valueOf(cp.galaxyTravelSpeed));
            props.setProperty("galaxyLocationStates", cp.galaxyLocationStates);
            props.setProperty("galaxySearchGroups", cp.galaxySearchGroups);
            props.setProperty("campaignTheaters", cp.campaignTheaters);
            props.setProperty("strategicNodes", cp.strategicNodes);
            props.setProperty("theaterWarRecentEvents", cp.theaterWarRecentEvents);
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

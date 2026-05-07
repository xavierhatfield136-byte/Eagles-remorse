import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Command, automation, and fleet-control state shared across UI and simulation systems.
 */
public final class CommandState {
    public static final class CommFactionMemory {
        public double trust = 0.0;
        public double fear = 0.0;
        public double cooperation = 0.0;
    }

    public GameContext.CrewStation activeCrewStation = GameContext.CrewStation.CAPTAIN;
    public GameContext.HelmMode helmMode = GameContext.HelmMode.INTERCEPT;
    public GameContext.TacticalMode tacticalMode = GameContext.TacticalMode.DEFENSIVE;
    public GameContext.EngineeringMode engineeringMode = GameContext.EngineeringMode.BALANCED;
    public GameContext.CaptainDirective captainDirective = GameContext.CaptainDirective.BALANCED;
    public boolean captainAutomation = false;
    public boolean helmAutomation = false;
    public boolean tacticalAutomation = false;
    public boolean engineeringAutomation = false;
    public boolean scienceAutomation = true;
    public boolean scienceJamming = false;
    public double helmDesiredRange = 480.0;
    public boolean miningAuto = false;
    public GameContext.FleetCommand alliedFleetCommand = GameContext.FleetCommand.AUTO;
    public GameContext.FleetFormation alliedFleetFormation = GameContext.FleetFormation.WEDGE;
    public final Map<Integer, GameContext.FleetCommand> shipFleetCommandOverrides = new HashMap<>();
    public final Map<Integer, Double> shipFleetCommandOverrideTimers = new HashMap<>();
    public final Map<Integer, Double> shipCommActionCooldowns = new HashMap<>();
    public final Map<Integer, Double> shipCommCeasefireTimers = new HashMap<>();
    public final EnumMap<Faction, CommFactionMemory> commFactionMemory = new EnumMap<>(Faction.class);
    public final EnumMap<Faction, Ship> fleetCommandShips = new EnumMap<>(Faction.class);
    public final EnumMap<Faction, Ship> fleetSharedTargets = new EnumMap<>(Faction.class);
    public final EnumMap<Faction, GameContext.FleetCommand> fleetResolvedCommands =
            new EnumMap<>(Faction.class);
    public final EnumMap<Faction, GameContext.FleetFormation> fleetResolvedFormations =
            new EnumMap<>(Faction.class);
    public final Map<Integer, String> fleetSquadLabelByShip = new HashMap<>();
    public final Map<Integer, String> fleetSquadRoleByShip = new HashMap<>();
    public final Map<Integer, Integer> fleetSquadLeaderByShip = new HashMap<>();
    public final Map<Integer, Integer> fleetSquadIndexByShip = new HashMap<>();
    public final Map<Integer, Long> fleetSquadStatusMemory = new HashMap<>();
    public boolean playerTeleportCharging = false;
    public double playerTeleportChargeRemaining = 0.0;
    public boolean safeMissionExitPending = false;
    public boolean safeMissionExitReady = false;
    public Faction shootingRangeTargetFaction = Faction.ENEMY;
    public TitanArchetype shootingRangeTitanArchetype = null;
    public double shootingRangeOriginX = Double.NaN;
    public double shootingRangeOriginY = Double.NaN;
}

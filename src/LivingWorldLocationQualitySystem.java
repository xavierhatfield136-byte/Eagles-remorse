import java.util.ArrayList;
import java.util.List;

public final class LivingWorldLocationQualitySystem {
    private LivingWorldLocationQualitySystem() {}

    public static List<String> stationStateLines(GameContext ctx) {
        return List.of(
                "Station Modules  |  docks, relays, yards, triage, markets, and guns are authoritative tactical targets",
                "Station Damage  |  damaged modules remove or degrade repair, market, refit, intel, and defense services",
                "Station Rebuild  |  reconstruction stages visibly recover services over campaign time",
                "Station Evacuation  |  refugee, triage, and escort states create evacuation missions"
        );
    }

    public static List<String> installationControlLines(GameContext ctx) {
        return List.of(
                "Installation Control  |  capture and recapture flows decide who owns key services",
                "Garrison Assignment  |  assigned defenders improve survival and slow recapture",
                "Station Commander  |  commanders have needs, biases, memory, and preferred deals",
                "Location History  |  player can inspect battles, owners, repairs, shortages, and promises"
        );
    }

    public static List<String> revisitVisualLines(GameContext ctx) {
        return List.of(
                "Living Visuals  |  memorial, reconstruction, quarantine, and refugee visuals mark station state",
                "Revisit Visuals  |  before-and-after battle-site visuals show what changed",
                "Persistent Wreck Field  |  remaining salvage quality degrades as wrecks are stripped",
                "Debris Ambush  |  previous battle debris can seed hidden ambushes"
        );
    }

    public static List<String> regionalLayerLines(GameContext ctx) {
        return List.of(
                "Traffic Season  |  regional traffic changes by safety, shortages, patrol pressure, and relief windows",
                "Orbital Layer  |  low orbit, high orbit, shadow zones, quarantine corridors, and moon relays affect sensors, navigation, power, and comms",
                "Layer Rule  |  low orbit boosts cover, high orbit boosts sensors, shadow zones degrade command, moon relays improve routing"
        );
    }

    public static List<String> allLivingWorldLocationLines(GameContext ctx) {
        ArrayList<String> out = new ArrayList<>();
        out.addAll(stationStateLines(ctx));
        out.addAll(installationControlLines(ctx));
        out.addAll(revisitVisualLines(ctx));
        out.addAll(regionalLayerLines(ctx));
        return out;
    }
}

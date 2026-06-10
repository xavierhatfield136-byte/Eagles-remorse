import java.util.ArrayList;
import java.util.List;

public final class ArtAudioPolishQualitySystem {
    private ArtAudioPolishQualitySystem() {}

    public static List<String> visualPolishLines(GameContext ctx) {
        return List.of(
                "Damage Stage Visuals  |  normalized clean, damaged, critical, burning, disabled, and wreck states",
                "Placeholder Disposition  |  wreck, prop, portal, and map-icon placeholders marked keep, replace, archive, or post-alpha",
                "Faction Hull Skins  |  final skins prioritized only where readability is weak",
                "Turret Role Skins  |  final turret art added only when role identity is unclear",
                "Damaged Critical Readability  |  critical states use silhouette, sparks, smoke, and icon reinforcement",
                "Multipart Wrecks  |  wreck silhouettes preserve destroyed ship scale and role"
        );
    }

    public static List<String> effectsAndEnvironmentLines(GameContext ctx) {
        return List.of(
                "Engine Plumes / Shield Impacts  |  plumes show thrust state; shield impacts separate ring, spark, and bloom",
                "Missile Trail Variants  |  interceptor, torpedo, atomic, and swarm missiles use distinct trails",
                "Station Module Art  |  service modules read as repair, market, refit, intel, relay, or defense",
                "Environmental Props  |  orbital, salvage, mining, and battlefield zones get readable props",
                "Bridge Portrait Gate  |  portraits ship only if recognition improves without clutter"
        );
    }

    public static List<String> audioPolishLines(GameContext ctx) {
        return List.of(
                "Layered Audio  |  engines, impacts, ambience, warnings, and radio distortion layer without masking",
                "Adaptive Music  |  travel, tension, combat, victory, loss, and aftermath states select music intensity",
                "Audio Ducking  |  warnings beat chatter; critical alerts duck ambience and nonessential voices",
                "Important Captions  |  every important voice and warning event has a caption",
                "Regional Ambience  |  hub, allied, neutral, hostile, empty-space, and operational zones sound distinct",
                "Title Menu Polish  |  menu presentation upgrades only after the game loop remains stable"
        );
    }

    public static List<String> allArtAudioPolishLines(GameContext ctx) {
        ArrayList<String> out = new ArrayList<>();
        out.addAll(visualPolishLines(ctx));
        out.addAll(effectsAndEnvironmentLines(ctx));
        out.addAll(audioPolishLines(ctx));
        return out;
    }
}

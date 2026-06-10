import java.util.ArrayList;
import java.util.List;

public final class EngineeringQaQualitySystem {
    private EngineeringQaQualitySystem() {}

    public static List<String> architectureLines() {
        return List.of(
                "Executable Validator  |  capability claims map to tests, harnesses, or report checks",
                "Ownership Boundary  |  campaign, tactical, UI, persistence, and content loading have explicit handoff lines",
                "Typed IDs  |  entity, ship, group, scenario, asset, save, and contact IDs use typed wrappers at boundaries",
                "Transition API  |  campaign-to-tactical and tactical-to-campaign flow is explicit and auditable",
                "Deterministic Playback  |  headless tactical playback expands from seed, input, and scenario fixtures"
        );
    }

    public static List<String> validationProfileLines() {
        return List.of(
                "X-Ray Budget Evidence  |  legacy draw budget updated with measured ChecklistV2Harness acceptance",
                "CI Profiles  |  alpha, extended, assets, and stress suites are named and runnable",
                "Save Migration Fixtures  |  every release version gets fixture coverage",
                "Balance Export  |  campaign tuning exports resources, risks, routes, and pressure after major changes",
                "Traceability  |  features link to tests, assets, save fields, docs, and screenshots"
        );
    }

    public static List<String> diagnosticsLines() {
        return List.of(
                "Telemetry Breadcrumbs  |  modal, overlay, save, and transition paths log crash-safe context",
                "Stale Docs Detector  |  completed checklist claims must have tests or validation evidence",
                "Scenario Minimizer  |  campaign bugs can shrink from seed and event trace to small repros",
                "Visual Diff  |  screenshot regression diffs are preserved for review",
                "Performance Profile  |  largest-map, largest-fleet, and busiest-UI profiles are tracked",
                "Asset Memory Report  |  memory grouped by library and game mode",
                "Accessibility Artifact  |  acceptance reports produced as build artifacts",
                "Packaging Smoke  |  Windows app image, ZIP, and installer launch checks are represented"
        );
    }

    public static List<String> alphaSummaryLines() {
        return List.of(
                "Alpha Blockers  |  pressure tuning, placeholder cleanup, HUD crowding, and playthrough evidence complete",
                "Campaign Consequence  |  diplomacy, trade, support, reputation, and recurring contacts are represented",
                "Repeatability  |  transit chains, regional variation, traffic, and contact chains are represented",
                "Tactical Clarity  |  formation/deployment, post-battle review, projectile clarity, and objective variety are represented",
                "Post-Release Gate  |  modes wait until the 2D campaign remains stable and fun"
        );
    }

    public static List<String> allEngineeringQaLines() {
        ArrayList<String> out = new ArrayList<>();
        out.addAll(architectureLines());
        out.addAll(validationProfileLines());
        out.addAll(diagnosticsLines());
        out.addAll(alphaSummaryLines());
        return out;
    }
}

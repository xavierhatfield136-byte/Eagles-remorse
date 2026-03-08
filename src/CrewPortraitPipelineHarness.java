import java.nio.file.Path;

/**
 * Runs ingest checks and HUD readability previews for portrait assets.
 */
public final class CrewPortraitPipelineHarness {
    private CrewPortraitPipelineHarness() {}

    public static void main(String[] args) throws Exception {
        Path outDir = Path.of("out", "crew_portrait_preview");
        boolean strict = false;

        if (args != null) {
            for (String arg : args) {
                if (arg == null || arg.isBlank()) continue;
                if (arg.startsWith("--out=")) {
                    String path = arg.substring("--out=".length()).trim();
                    if (!path.isEmpty()) outDir = Path.of(path);
                } else if (arg.equalsIgnoreCase("--strict")) {
                    strict = true;
                }
            }
        }

        CrewPortraitSystem.PortraitAudit audit = CrewPortraitSystem.auditLibrary();
        CrewPortraitSystem.writeHudPreviewSnapshots(outDir);

        System.out.println("[portrait-harness] style lock prompt:");
        System.out.println(CrewPortraitSystem.styleLockPrompt());
        System.out.println("[portrait-harness] total portraits: " + audit.totalPortraits());
        System.out.println("[portrait-harness] base set complete: " + audit.baseComplete());
        System.out.println("[portrait-harness] hud previews: " + outDir.toAbsolutePath());

        for (String role : audit.perRolePortraitCount().keySet()) {
            int count = audit.perRolePortraitCount().getOrDefault(role, 0);
            System.out.println("[portrait-harness] role=" + role + " portraits=" + count);
        }

        if (audit.issues().isEmpty()) {
            System.out.println("[portrait-harness] issues: none");
            return;
        }

        System.out.println("[portrait-harness] issues:");
        for (CrewPortraitSystem.PortraitIssue issue : audit.issues()) {
            System.out.println(" - [" + issue.code() + "] " + issue.fileName() + " :: " + issue.detail());
        }

        if (strict) {
            System.exit(2);
        }
    }
}

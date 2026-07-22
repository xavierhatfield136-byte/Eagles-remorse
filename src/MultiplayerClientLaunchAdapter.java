public final class MultiplayerClientLaunchAdapter {
    public record ClientPresentationLaunch(MissionLaunchSpec spec,
                                           String missionDefinitionDigest,
                                           String lockedLaunchSpecDigest) {
        public ClientPresentationLaunch {
            if (spec == null) {
                throw new IllegalArgumentException("Client presentation launch requires a locked mission spec");
            }
            missionDefinitionDigest = clean(missionDefinitionDigest);
            lockedLaunchSpecDigest = clean(lockedLaunchSpecDigest);
        }
    }

    private MultiplayerClientLaunchAdapter() {}

    public static ClientPresentationLaunch prepare(MissionLaunchSpec spec,
                                                   CustomMissionDescriptor descriptor,
                                                   MissionTemplate template,
                                                   long lockedLobbyRevision) {
        if (spec == null) {
            throw new IllegalArgumentException("Client cannot prepare multiplayer presentation without a locked spec");
        }
        MultiplayerMissionValidator.requireV1(spec);
        String definitionDigest = MissionDigest.missionDefinitionDigest(descriptor, template);
        String launchDigest = MissionDigest.lockedLaunchSpecDigest(spec, lockedLobbyRevision);
        return new ClientPresentationLaunch(spec, definitionDigest, launchDigest);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}

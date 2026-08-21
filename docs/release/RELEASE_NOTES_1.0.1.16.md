# Eagles Remorse 1.0.1.16

This release focuses on final release-candidate trust work: strategic-map intel
projection, persistent fleet handoff correctness, visual-regression review, and
1.0.1.16 packaging metadata.

## Downloads

- Windows portable build: `EaglesRemorse-1.0.1.16-windows-x64-full.zip`
- Linux portable build: `EaglesRemorse-1.0.1.16-linux-x64.tar.gz`
- macOS app bundle ZIP: `EaglesRemorse-1.0.1.16-macos.zip`

Package artifacts are produced by the release packaging workflow for this
version.

## Highlights

- Fixed strategic-map projection for enemy fleet intel so exact observations
  produce actionable live fleet markers at the observed position.
- Fixed approximate enemy fleet intel so it appears as a non-interactive
  uncertainty marker instead of vanishing or being treated as an exact contact.
- Preserved the distinction between strategic-only operation knowledge and
  physical fleet contact markers, preventing the map from sending players after
  non-actionable fleet positions.
- Fixed tactical encounter manifests for persistent campaign forces so concrete
  fleet members are not truncated by display-size limits.
- Preserved persistent ship condition, role, crew readiness, ammunition state,
  and retreat intent when campaign fleets enter tactical combat.
- Added the 1.0.1.16 release-note contract required by the packaging validation
  gate.
- Re-reviewed screenshot-regression targets for the campaign map, fleet board,
  strike tab, tactical HUD, and accessibility HUD as part of the final candidate
  pass.

## Validation

- `CampaignMultiSourceIntelMilestoneThreeTest`
- `CampaignPhaseTwoFleetPopulationTest`
- `CampaignPhaseElevenPackagingReleaseTest`
- `ScreenshotRegressionHarnessTest`
- `FirstHourExperienceTest`
- `CommandSchoolOverworldExpansionTest`
- `BattleResultAnalysisServiceTest`
- `PostAlphaAcceptanceHarnessTest`

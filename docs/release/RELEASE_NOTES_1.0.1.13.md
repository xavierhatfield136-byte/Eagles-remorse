# Eagles Remorse 1.0.1.13

This release focuses on dynamic-campaign encounter safety, clearer pre-battle
commitment flow, campaign save control, and removing several old UI/theme and
linear-campaign interference paths.

## Downloads

- Windows portable build: `EaglesRemorse-1.0.1.13-windows-x64-full.zip`
- Linux portable build: `EaglesRemorse-1.0.1.13-linux-x64.tar.gz`
- macOS app bundle ZIP: `EaglesRemorse-1.0.1.13-macos.zip`

Package artifacts are produced by the release packaging workflow for this
version.

## Highlights

- Prevented dynamic campaign fleets from stacking on top of one another on the
  strategic map or choosing identical travel targets.
- Reduced hostile sensor-bubble joiner counts and spread multi-fleet tactical
  participants by overmap distance and selected insertion range.
- Added a campaign encounter loading overlay with progress details before
  tactical combat.
- Reworked the pre-battle briefing to show individual friendly and enemy hull
  cards instead of only aggregate ship counts.
- Added close, moderate, and far tactical insertion choices before campaign
  force engagements.
- Fixed empty or depleted strategic fleets reappearing as ghost contacts.
- Corrected Red force classification and reinforcement caps so low-level patrol
  labels do not hide capital/titan-heavy fleets.
- Retired legacy opening Red patrol/scout forces from dynamic campaign sector
  one so previously cleared starter contacts do not return as stale map ghosts.
- Moved the old authored linear sector missions behind the separate
  `Classic Campaign` menu entry.
- Forced normal `Campaign Ops` starts, route launches, and legacy checkpoint
  resumes through the dynamic strategic overmap instead of the old sector script
  path.
- Added main-menu campaign save deletion for the primary checkpoint/autosaves
  and for each named campaign slot.
- Removed custom HUD alert/theme image overrides so the menus fall back to the
  cleaner standard panel assets.

## Validation

- `CampaignFleetHubMenuRegressionTest`
- `CampaignOvermapEncounterFlowTest`
- `CampaignFleetEscalationTest`
- `CampaignLivingWarSystemTest`
- `git diff --check`

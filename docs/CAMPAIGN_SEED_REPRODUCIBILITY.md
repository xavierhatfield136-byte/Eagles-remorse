# Campaign Seed Reproducibility

## Shareable Seed Contract

A campaign seed must reproduce the validated campaign setup: main points of interest, procedural areas of interest, starting campaign force catalog, initial force positions, and initial force targets.

The acceptance test is `CampaignOvermapCheckpointTest.shareableSeedReproducesValidatedCampaignSetup`.

## Intentionally Nondeterministic Systems

The following systems may vary after live play begins and are not part of the initial shareable-seed contract:

- wall-clock UI animation timing;
- audio variant selection and cooldown timing;
- manual player input timing;
- performance-dependent frame pacing;
- post-start combat outcomes caused by different player decisions.

## Save And Replay Notes

Checkpoint restore remains authoritative once a campaign has started. Shareable seeds reproduce the initial setup; checkpoints reproduce the evolved campaign state.

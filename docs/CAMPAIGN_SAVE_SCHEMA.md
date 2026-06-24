# Campaign Save Schema

Checkpoint properties are backward-compatible and normalized on load.

## Expansion Fields

Expansion checkpoint fields are saved for compatibility and deterministic restoration. They are not all
player-facing alpha systems. The live status column is the release-facing contract:
`alpha-live` affects normal campaign play, `debug/readout-only` is visible only through developer/audit
surfaces, and `future/model-only` is persisted prototype state that must not be presented as a playable feature.

| Field | Added for | Alpha status | Fallback |
| --- | --- | --- | --- |
| `strategicExpansionState` | Section 5 strategic task groups | future/model-only | Seeded strategic bootstrap |
| `economyExpansionState` | Section 6 logistics state | alpha-live support model | Seeded economy bootstrap |
| `diplomacyNarrativeState` | Section 7 reputation and crew state | alpha-live support model | Seeded diplomacy bootstrap |
| `operationsExpansionState` | Sections 8-11 command preferences | future/model-only | Seeded operations bootstrap |
| `productionReadinessState` | Sections 12-15 longevity preferences | debug/readout-only | Seeded production bootstrap |
| `fleetDoctrineExpansionState` | Sections 16-18 stretch and fleet-doctrine preferences | debug/readout-only | Seeded doctrine bootstrap |
| `deepCampaignExpansionState` | Sections 19-25 living campaign simulation and legacy state | future/model-only | Seeded deep-campaign bootstrap |
| `communityContentState` | Section 26 scenario-editor and local community preferences | future/model-only | Seeded community-content bootstrap |

Unknown or malformed expansion payloads fall back to deterministic seeded defaults.

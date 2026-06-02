# Campaign Save Schema

Checkpoint properties are backward-compatible and normalized on load.

## Expansion Fields

| Field | Added for | Fallback |
| --- | --- | --- |
| `strategicExpansionState` | Section 5 strategic task groups | Seeded strategic bootstrap |
| `economyExpansionState` | Section 6 logistics state | Seeded economy bootstrap |
| `diplomacyNarrativeState` | Section 7 reputation and crew state | Seeded diplomacy bootstrap |
| `operationsExpansionState` | Sections 8-11 command preferences | Seeded operations bootstrap |
| `productionReadinessState` | Sections 12-15 longevity preferences | Seeded production bootstrap |
| `fleetDoctrineExpansionState` | Sections 16-18 stretch and fleet-doctrine preferences | Seeded doctrine bootstrap |
| `deepCampaignExpansionState` | Sections 19-25 living campaign simulation and legacy state | Seeded deep-campaign bootstrap |
| `communityContentState` | Section 26 scenario-editor and local community preferences | Seeded community-content bootstrap |

Unknown or malformed expansion payloads fall back to deterministic seeded defaults.

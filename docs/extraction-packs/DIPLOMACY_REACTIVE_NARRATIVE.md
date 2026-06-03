# Diplomacy And Reactive Narrative

## Scope

Tie reputation, favors, obligations, alliances, recurring contacts, bridge-officer opinions, quiet mode, bulletins, logs, and ending summaries to live campaign choices.

## Dependencies

- `DiplomacyNarrativeCrewSystem`
- hub services
- mission aftermath
- civilian rescue and collateral outcomes
- checkpoint persistence

## UI Flow

Comms board, bulletins, after-action notes, bridge logs, and ending timelines should explain who changed opinion and why.

## Data Ownership

Relationship and narrative state belongs to the diplomacy narrative system. Campaign events submit reasons, not freeform duplicate state.

## Save Impact

Persist reputation, recurring contacts, quiet mode, bridge logs, authored beat cooldowns, and ending inputs.

## Asset Needs

Voice and portrait placeholders are acceptable only if listed in the asset approval report.

## Tests

Cover reputation changes from service choices, rescues, strikes, betrayals, recurring contacts, quiet mode, and save/load.

## Non-Goals

This pack does not produce final localized narrative pools.

# Current Campaign State (As Implemented)

Date: 2026-05-29  
Status: Active implementation snapshot (theater-war baseline live)

## What The Campaign Is Right Now

The current campaign is an open-world strategic overmap, not a linear mission ladder.

- The player moves across a large south-to-north map toward Earth.
- Main progression is delivered through authored objective locations embedded in the overmap.
- Optional local sites, roaming hostile groups, and hub logistics shape each run.
- Tactical combat occurs only when an encounter is triggered.

## Campaign Loop

1. Select route or objective on the overmap.
2. Travel in continuous world-space movement.
3. Scan, manage resources, and react to contacts.
4. Dock at hubs for services when in range.
5. Resolve encounters (manual tactical battle or auto-resolve where allowed).
6. Return to overmap with updated state and continue north.

## Core Systems Currently Active

- Full-screen strategic campaign map
- Continuous travel with hold/redirect behavior
- Docking-gated hub services
- Hostile search-group contact and interception pressure
- Intel/contact confidence states
- Faction support calls (Green/Yellow)
- Long-range strike actions (with intel requirements)
- Persistent fleet continuity and checkpoint persistence
- One large tactical combat space per encounter

## Current Content Structure

- 24 authored main objective locations on the overmap
- Multiple optional site types (resource, salvage, distress, cache, relay/story, repair)
- Regional escalation from lower-threat south to high-pressure Earth approach
- Strategic war framing: Blue + Green vs Red + Yellow, with Yellow support coerced rather than allied
- Multiple ending outcomes based on campaign performance and branch state

## What It Is Not

- Not a fixed linear mission chain
- Not a static node-by-node click path
- Not tactical combat running continuously behind the map

## Canonical References

- `STRATEGIC_CAMPAIGN_MAP_SPEC.md` for high-level design rules
- `STRATEGIC_CAMPAIGN_SCRIPT.md` for detailed player-facing behavior and content
- `CAMPAIGN_THEATER_CONQUEST_PLAN.md` for theater-war design intent
- `CAMPAIGN_THEATER_CONQUEST_CHECKLIST.md` for implementation completion status

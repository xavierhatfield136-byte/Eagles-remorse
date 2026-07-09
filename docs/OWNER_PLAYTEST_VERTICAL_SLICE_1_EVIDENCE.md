# Owner Playtest Vertical Slice 1 Evidence

**Status:** Automated acceptance complete; rendered owner review pending  
**Scope:** Persistent Green/Yellow/Red fleets, one visible operation, and lawful territory outcomes

## Implemented proof

- Normal strategic-map activation now seeds canonical physical campaign fleets before the first presented frame.
- Every physical fleet marker carries its source fleet ID, enabling an authoritative fleet-to-marker parity report.
- Fleet removal records a named reason and emits one disappearance event for a known fleet.
- The campaign schedules an opening focused Red operation after the opening interval when no operation is active.
- Autonomous ownership changes require an authoritative resolving operation and a real assigned capture fleet physically present at the target.
- Ownership evidence names the operation, attacker fleet, defender fleet or site, arrival state, and outcome reason.
- An ownership attempt that lacks this evidence is paused, remains contested, and produces both diagnostic telemetry and a player-safe event.

## Automated acceptance results

### Focused regression gate

The 33-test campaign-map and operation milestone set passed, including the new `OwnerPlaytestVerticalSliceOneTest` and the strengthened `FocusedFactionAttackChecklistTest`.

The new acceptance coverage proves:

- canonical Green, Yellow, and Red physical fleets project through the normal map path when valid intel exists;
- adjacent authoritative and presentation frames contain no fleet projection mismatch;
- a removed known fleet emits exactly one named disappearance reason;
- a lawful capture contains complete operation evidence; and
- a capture without a real arrived fleet is rejected.

### Twenty-minute strategic soak

`focusedFactionAttackSoak` passed for seeds `71000`, `71001`, and `71002`, each for 1,200 simulated seconds through the normal strategic activation path.

At every seed's opening checkpoint the run contained 24 Green, 14 Bright Yellow, 9 Dark Yellow, and 47 Red physical fleets. By 180 seconds each run had one active focused operation, one operation arrow, and 38 visible routes. All checkpoints reported zero projection mismatches and zero unexplained fleet disappearances.

No natural ownership transfer occurred during these three soak runs. The deterministic lawful-capture acceptance test therefore remains the proof for the ownership boundary; the manual review should observe whether operation pacing produces a satisfying outcome in ordinary play.

## Broader regression disclosure

The focused Slice 1 gate is green. A broader 218-test selection currently reports seven failures in four older suites:

- `CampaignForceOwnershipTest` (2)
- `CampaignLivingWarSystemTest` (2)
- `CampaignNpcFleetAiTest` (2)
- `CampaignTheaterConquestChecklistTest` (1)

The same failures reproduce when those four suites are run alone. They are not hidden by this evidence and must be triaged before the full-suite release gate can pass.

## Remaining owner-visible gate

### First rendered review finding

The first owner review found one moving Yellow fleet and three Red fleets, but no legible Green fleet. It also identified scattered blue and yellow circles marked `S/S` as confusing. Those circles were abstract secure/supplied territory nodes, not fleets.

The follow-up correction:

- treats Green and coalition transponders as routine exact fleet reports even when a formal Blue/Green alliance flag is not active;
- permits trusted fleet reports beyond the player's local sensor radius;
- keeps moving Green, Yellow, and Red fleet markers at overview zoom and gives priority fleet markers plain labels such as `GREEN PATROL` and `YELLOW CONVOY`;
- suppresses routine secure/supplied territory-node circles; and
- shows only actionable territory nodes, using words such as `FRONT`, `OPERATION`, `CONTESTED`, or `SUPPLY SOURCE` instead of `S/S`.

The corrected 55-test focused presentation and Slice 1 regression selection passes with zero failures.

### Second rendered review finding

The second owner review confirmed that more fleets were visible, but exposed three remaining failures: Red fleets repeatedly jumped away when approached and then resumed pursuit, routine `FRONT` territory circles still looked like units, and generic theater seeding placed factions in regions where they had no supporting base.

The follow-up correction:

- makes opening spawn protection a one-time initialization event instead of a player-relative position writer that runs every simulation sync;
- persists that one-time state for both canonical fleets and linked search groups across checkpoints;
- prevents legacy name-based seed reconciliation from overwriting an existing NPC fleet's canonical position;
- requires ambient Green and Yellow theater fleets to have a real same-faction home base;
- anchors fallback Red theater formations to real Red territory rather than arbitrary theater coordinates;
- keeps generic trade routes within the owning faction's territory unless a dedicated operation supplies a cross-faction order;
- limits overview-priority labels to patrols, task forces, and strike detachments, leaving miners and routine convoys for closer zoom or selection; and
- suppresses front-line-only territory circles, retaining territory glyphs only for operations, beachheads, supply sources, degraded supply, or non-secure control.

The resulting 70-test focused fleet, operation, map-presentation, and checkpoint selection passes with zero failures.

### Sensor-volume adjustment

At owner request, the authoritative player campaign sensor radius is now three times its previous value: the base radius increases from 720 to 2,160 world units, while intelligence scaling is tripled with it. Detection and contact updates consume that same authoritative value. The strategic map now renders the volume as a filled circular `SENSOR SPHERE` using one pixel radius rather than a stretched ellipse. The expanded 119-test focused campaign selection passes with zero failures.

- [ ] Start a fresh Standard Command Campaign Ops run on this build.
- [ ] Observe at least one Green, Yellow, and Red fleet moving on the rendered map.
- [ ] Follow the focused operation arrow and Sensor Net reports through its visible phases.
- [ ] Confirm any lost fleet contact has an understandable stale/lost/removal explanation.
- [ ] Confirm no site changes ownership without an arrived fleet and an understandable outcome event.
- [ ] Record the build commit, seed, preset, elapsed time, and screenshots or notes.

Do not begin Vertical Slice 2 until this rendered review is accepted or its findings are folded back into Slice 1.

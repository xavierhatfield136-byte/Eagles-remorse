# Post-Alpha Crew Media And Accessibility Policy

Status: mandatory for every post-alpha expansion and release candidate.

## Creative boundary

Post-alpha work must not create, commission through a generative service, ship,
or temporarily substitute any AI-generated crew face, crew portrait, animated
crew avatar, interior talking-head video, or synthetic crew performance.

The existing alpha media directories are frozen legacy inputs. Their presence
does not authorize new generated assets, variants, regeneration, model changes,
or reuse as a post-alpha production workflow. The local generation scripts and
guides are not part of the expansion workflow and must not be invoked by an
implementation slice.

If character artwork or spoken crew performance is approved in a future budget,
it must be created by credited human artists or performers under an explicit
agreement. Approval must include creator/performer name, source, date, license,
usage scope, consent/release reference, and reviewer. Until then, post-alpha
features remain text-and-instrument complete.

## Approved representation

People and consequences are represented through authored text, names, ranks,
orders, decisions, casualty and survivor lists, service records, memorials,
ship identities, insignia, maps, instrument readouts, and written reports.
Interior personnel are represented as abstract teams, schematic tokens,
readiness values, station status, assignment state, hazards, and automation.

No gameplay-critical fact may depend on a face, animation, video, or spoken
line. Alerts and reports must have complete written captions. Muting all speech
must leave objectives, timing, warnings, attribution, and outcomes playable.

## Provenance requirements

Every future character image, character video, or recorded performance must be
listed in `config/crew_media_provenance.csv` before packaging. Required fields:

- asset path and media category;
- origin and human creator or performer;
- license and allowed usage scope;
- consent or release reference;
- production status and reviewer;
- explicit `synthetic=false` declaration.

Unknown, synthetic, placeholder, or unreviewed media is release-blocking. New
files in crew portrait, avatar, character-video, or crew-voice paths without a
matching provenance record are release-blocking.

## Release audit

Each release candidate must run `CrewMediaPolicyAudit --strict`. The audit must:

1. reject expansion-era AI-generation configuration and generated-media tasks;
2. inventory portrait, avatar, character-video, and crew-voice assets;
3. reject unregistered new media and invalid provenance fields;
4. verify that flagship and boarding UI use abstract personnel presentation;
5. verify captions and written reports cover their complete playable state.

Legacy alpha media is recorded as `legacy_alpha_unverified` and frozen, not
misrepresented as human-authored. Replacing or removing it is a separate owner
decision; expanding it is prohibited by this policy.


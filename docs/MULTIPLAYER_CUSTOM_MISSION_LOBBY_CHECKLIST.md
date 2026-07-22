# Multiplayer Custom Mission Lobby Checklist

Date: 2026-07-20
Status: Revised implementation scaffold after design review
Scope: Make multiplayer accessible from the main menu through a real lobby flow, then grow from the current hard-coded duel into a shared custom mission roster without breaking single-player custom missions.

## Guiding Decision

The first implementation target is intentionally narrow:

- [x] Two human players only.
- [x] Opposing teams only.
- [x] One directly controlled ship per player.
- [x] No AI ships in Milestone 1.
- [x] Elimination victory only in Milestone 1.
- [x] No campaign multiplayer.
- [x] No reconnect, join-in-progress, host migration, public matchmaking, NAT traversal, relay, voice chat, persistent multiplayer progression, or mod synchronization.
- [x] Dead players may only spectate locally after death unless a later milestone adds respawn.
- [x] The existing LAN duel harness remains available as a diagnostics path.

The most important risk to prove first is not the catalog. It is whether the playable battle runtime can cleanly separate local, host-authoritative, and client-presentation authority inside the real game view.

## Current Shape

- [x] Main menu has a feature-flagged multiplayer entry panel in `MainMenuPanel`.
- [x] The existing multiplayer panel exposes host, join, and direct address controls.
- [x] Multiplayer launch carries a selected multiplayer mission ID through `MultiplayerLaunchConfig`.
- [x] `Main.createGameView` sends normal multiplayer launches into a real `GamePanel`.
- [x] `MultiplayerInGameLaunchPanel` remains available for diagnostics launches only.
- [x] `MultiplayerLobbyV1` has a small host-authoritative lobby state machine for a two-player duel.
- [x] `MultiplayerRulesV1` only allows a two-player opposing-team duel with one directly controlled ship per player.
- [x] Single-player custom battles already support richer friendly and enemy rosters via `GameConfig.customBattleFriendlyRoster` and `GameConfig.customBattleEnemyRoster`.
- [x] There is a host-facing multiplayer mission selector for all V1-supported catalog entries; unsupported single-player custom missions remain hidden until their required capabilities are supported.
- [x] Multiplayer has a shared mission launch model; single-player custom battles resolve legacy config through `SinglePlayerLaunchAdapter`.
- [x] The lobby publishes mission choice, map size, selected player slots, and mission config revision to clients.
- [x] Multiplayer runtime spawns from `MissionLaunchSpec`; single-player custom battles now route live custom-battle spawning through the shared launch spec.

## Product Target

- [x] Add a visible main-menu path called `Multiplayer Setup`.
- [x] Let the player choose `Create Lobby` or `Join Lobby`.
- [x] On create, show a lobby screen where the host owns mission settings.
- [x] On join, show a read-only lobby screen where the client can view mission settings and toggle ready.
- [x] Show connected players, readiness, selected mission, map, teams, content compatibility, and lobby address.
- [x] Start the selected mission in playable multiplayer, not only as a harness output panel.
- [x] Preserve single-player custom mission behavior.
- [x] Keep campaign multiplayer out of scope.

## Multiplayer Capabilities

Mission catalog visibility must not imply multiplayer safety. Capability requirements are the authoritative eligibility model; labels such as "Duel Ready" or "AI Ready" may be derived for UI, but they must not control launch eligibility.

- [x] Define `MultiplayerCapability.OPPOSING_PLAYERS`.
- [x] Define `MultiplayerCapability.COOP_PLAYERS`.
- [x] Define `MultiplayerCapability.AI_REPLICATION`.
- [x] Define `MultiplayerCapability.OBJECTIVE_REPLICATION`.
- [x] Define `MultiplayerCapability.RESOURCE_REPLICATION`.
- [x] Define `MultiplayerCapability.WAVE_REPLICATION`.
- [x] Define `MultiplayerCapability.SHARED_TEAM_COMMANDS`.
- [x] Each mission declares `requiredCapabilities`.
- [x] Each rules profile declares `supportedCapabilities`.
- [x] A mission is multiplayer-eligible only when `rulesProfile.supportedCapabilities().containsAll(mission.requiredCapabilities())`.
- [x] `core:v1_duel` requires only `OPPOSING_PLAYERS`.

## Resolved Feature Boundary

- [x] Introduce a new flag named `MULTIPLAYER_CUSTOM_MISSIONS`.
- [x] Use `Multiplayer Setup` as player-facing UI text.
- [x] Keep the existing duel as the first supported multiplayer mission: `core:v1_duel`.
- [x] Keep AI fleets, same-team co-op, replicated objectives, respawns, and shared fleet command outside Milestone 1.
- [x] Add a later rules profile such as `MultiplayerRulesV2` or `MultiplayerRulesCustomMission` instead of stretching `MultiplayerRulesV1`.

## Stable Mission Identity

- [x] Mission IDs never depend on display names.
- [x] Mission IDs are never enum ordinals.
- [x] Renaming a mission does not change its ID.
- [x] Material gameplay changes increment mission revision.
- [x] Removed mission IDs remain reserved.
- [x] Experimental missions use a separate namespace such as `debug:` or `experimental:`.
- [x] Suggested initial IDs:
  - [x] `core:v1_duel`
  - [x] `core:heavy_duel`
  - [x] `core:custom_battle`
  - [x] `core:last_stand`
  - [x] `core:resource_rush`
  - [x] `core:four_team_domination`
  - [x] `debug:shooting_range`
  - [x] `showcase:fleet_showcase`

## Launch Model

Avoid one oversized `CustomMissionDefinition` that becomes both catalog metadata and concrete match state. Split the model into three concepts.

- [x] `CustomMissionDescriptor`
  - [x] `id`
  - [x] `revision`
  - [x] `displayName`
  - [x] `description`
  - [x] `supportedLaunchModes`
  - [x] `requiredMultiplayerCapabilities`
- [x] `MissionTemplate`
  - [x] `worldDefaults`
  - [x] `teamDefinitions`
  - [x] `rosterTemplate`
  - [x] `objectiveDefinition`
  - [x] `victoryDefinition`
  - [x] `seedPolicy`
- [x] `MissionLaunchSpec`
  - [x] `missionId`
  - [x] `missionRevision`
  - [x] `seed`
  - [x] `worldSize`
  - [x] `resolvedRosters`
  - [x] `playerSlots`
  - [x] `rulesProfileId`

Mission descriptors, templates, and launch specifications are immutable data models. Runtime behavior is selected through stable type IDs and resolved by the appropriate runtime adapter.

- [x] Mission data must not contain Swing components.
- [x] Mission data must not contain runtime entity references.
- [x] Mission data must not contain lambdas capturing simulation state.
- [x] Mission data must not contain open sockets.
- [x] Mission data must not contain mutable game collections.
- [x] Mission data must not contain `GamePanel`.
- [x] Mission data must not contain random-number-generator instances.
- [x] Mission data must not contain direct spawn callbacks tied to single-player code.

Both single-player and multiplayer should resolve through `MissionLaunchSpec`, then use separate adapters:

- [x] `SinglePlayerLaunchAdapter`
- [x] `MultiplayerHostLaunchAdapter`
- [x] `MultiplayerClientLaunchAdapter`

`MultiplayerClientLaunchAdapter` must initialize the client presentation runtime, validate the locked launch specification, prepare map and UI resources, and consume the authoritative initial state sent by the host. It must not independently create authoritative gameplay entities, resolve random spawn variation, apply damage, or evaluate victory.

- [x] Current adapter prepares locked presentation metadata and digests without creating authoritative gameplay entities.

## Game Config Boundary

- [x] Do not push all multiplayer mission state directly into `GameConfig`.
- [x] Prefer a `GameLaunchRequest` or equivalent wrapper containing:
  - [x] game mode
  - [x] resolved `MissionLaunchSpec`
  - [x] optional `MultiplayerLaunchContext`
- [x] Keep existing `GameConfig` constructors temporarily for compatibility.
- [x] Route old constructors through one builder or factory.
- [x] Add tests proving legacy `GameConfig(GameMode.CUSTOM_BATTLES, ...)` calls still produce equivalent launch data.
- [x] Deprecate overloaded constructors once the new launch path is stable.

## Lobby Screens

Keep setup, host lobby, and client lobby as distinct UI states.

- [x] Multiplayer landing screen:
  - [x] `Create Lobby`
  - [x] `Join Lobby`
  - [x] player name
  - [x] direct address field
  - [x] diagnostics/harness entry
- [x] Host lobby screen:
  - [x] connected players
  - [x] mission selection
  - [x] seed and map controls
  - [x] player slot assignment
  - [x] compatibility state
  - [x] ready states
  - [x] start button
  - [x] lobby address
- [x] Client lobby screen:
  - [x] read-only mission settings
  - [x] assigned ship
  - [x] compatibility result
  - [x] ready button
  - [x] leave button
- [x] Add Swing component names for mission selector, seed/map controls, create lobby button, join lobby button, ready button, start button, address field, and diagnostics entry.
- [x] Persist last mission id, player name, seed, and direct address.
- [x] Do not persist active session IDs, readiness, or auto-connect intent.

## Lobby State Machine

Use explicit lifecycle states instead of scattered booleans.

- [x] `CREATED`
- [x] `OPEN`
- [x] `LOCKED`
- [x] `LOADING`
- [x] `READY_TO_START`
- [x] `STARTING`
- [x] `IN_MATCH`
- [x] `POST_MATCH`
- [x] `CLOSING`
- [x] `CLOSED`

Required state rules:

- [x] Join is allowed only while `OPEN`.
- [x] Host mission edits are allowed only before launching the current V1 match.
- [x] Client ready toggles are allowed from the V1 lobby before match start.
- [x] Host pressing `Start` is the host's readiness action in Milestone 1.
- [x] Start is allowed only when the lobby is valid, compatible, and the client is ready for the current revision.
- [x] Leave/cancel is allowed from every state and must clean up sockets and workers.
- [x] Match lock stores the exact accepted lobby revision and mission spec.
- [x] Late join requests after lock receive `Match already in progress`.
- [x] Multiplayer battles run at fixed simulation speed.
- [x] Local pause and time-scale controls are disabled in Milestone 1.
- [x] Opening local menus may release local controls or show overlays, but it must not pause host simulation.

## Revision Rules

- [x] Lobby revisions are monotonically increasing.
- [x] Mission-setting changes replace one immutable `LobbyMissionConfig` object.
- [x] Clients ignore lobby snapshots older than their current revision.
- [x] Ready messages include the revision the player is accepting.
- [x] Host rejects ready messages for stale revisions.
- [x] Mission lock stores the exact accepted revision.
- [x] Match-start messages reference the locked revision.
- [x] Stale lobby packets cannot overwrite newer mission settings.
- [x] Mission ID changes clear readiness.
- [x] Mission revision changes clear readiness.
- [x] Seed changes clear readiness.
- [x] World-size changes clear readiness.
- [x] Player-slot changes clear readiness.
- [x] Assigned-hull changes clear readiness.
- [x] Rules-profile changes clear readiness.
- [x] Cosmetic display text changes do not clear readiness.

## Validation

Create one authoritative validator, such as `MultiplayerMissionValidator`, and use it from UI, lobby, protocol, and runtime paths.

- [x] Reject unsupported mission.
- [x] Reject unsupported objective.
- [x] Reject unsupported hull.
- [x] Reject invalid world size.
- [x] Reject too many entities for the current rules profile.
- [x] Reject missing player slot.
- [x] Reject duplicate slot assignment.
- [x] Reject team conflicts.
- [x] Reject AI when the active rules profile does not support AI.
- [x] Reject mismatched content revision.
- [x] Return structured errors with readable player-facing messages.

## Protocol And Compatibility

Use two separate digests.

`missionDefinitionDigest` verifies that both programs understand a mission definition the same way.

- [x] Include mission id.
- [x] Include mission revision.
- [x] Include default teams.
- [x] Include roster template.
- [x] Include objective definition.
- [x] Include victory definition.
- [x] Include allowed settings.
- [x] Include required capabilities.
- [x] Do not include selected seed.
- [x] Do not include player identities.

`lockedLaunchSpecDigest` verifies the exact match being started.

- [x] Include mission id.
- [x] Include mission revision.
- [x] Include rules profile id.
- [x] Include world width and height.
- [x] Include resolved seed.
- [x] Include resolved roster slots.
- [x] Include player-slot assignments.
- [x] Include locked lobby revision.
- [x] Include objective type.
- [x] Include victory type.

Compatibility hashing must use a canonical protocol representation.

- [x] Use canonical field order.
- [x] Use stable enum names or explicit numeric protocol IDs.
- [x] Canonical roster ordering uses stable slot identity: `teamId`, `slotId`, `hullTypeId`, `controlMode`, and `spawnAnchorId`.
- [x] Do not sort roster data only by hull name or quantity when slot order changes formation semantics.
- [x] Use SHA-256 or another named stable digest.
- [x] Do not use `Object.hashCode()`.
- [x] Do not use Java object serialization.
- [x] Do not depend on locale-sensitive formatting.
- [x] Avoid floating-point string formatting ambiguity.

Handshake order:

1. Transport connection.
2. Protocol handshake.
3. Content manifest exchange.
4. Player identity assignment.
5. Lobby snapshot.
6. Mission compatibility confirmation.
7. Ready eligibility.
8. Locked match specification with `lockedLaunchSpecDigest`.
9. Prepare-match loading handshake.
10. Start synchronization.

Readable mismatch categories:

- [x] Protocol version mismatch.
- [x] Selected mission unavailable.
- [x] Mission definition hash mismatch.
- [x] Locked launch-spec hash mismatch.
- [x] Hull/content manifest mismatch.
- [x] Rules profile unsupported.
- [x] Global catalog revision mismatch is logged or shown as a warning unless it changes protocol interpretation or the selected mission definition.

## Match Loading

- [x] Host sends `PrepareMatch` containing `matchId` and `lockedLaunchSpecDigest`.
- [x] Client validates the locked specification before constructing the battle view.
- [x] Host begins loading the battle after lock.
- [x] Client begins loading its presentation runtime after validation.
- [x] Client sends `MatchLoaded` only after its presentation runtime is ready.
- [x] `MatchLoaded` includes `matchId`, `lockedLaunchSpecDigest`, and client load status.
- [x] Host confirms its own load is complete.
- [x] Host does not begin simulation until all required peers are loaded.
- [x] Host sends one authoritative `BeginMatch` message with agreed match-start tick.
- [x] Loading has a timeout and readable failure result.

## Authority Boundary

Before moving multiplayer into `GamePanel`, identify and gate every local path that directly mutates simulation state.

- [x] movement input
- [x] target selection
- [x] weapon fire
- [x] ability activation
- [x] order issuance
- [x] pause and time scale
- [x] spawning
- [x] damage application
- [x] victory evaluation
- [x] entity deletion

Local highlighting, inspection panels, and purely visual targeting reticles may remain client presentation state. Any selected target used by weapons, missile locks, AI orders, turret behavior, abilities, or other simulation behavior must be submitted to and validated by the host.

Introduce an authority abstraction if needed:

- [x] `LocalBattleAuthority`
- [x] `HostBattleAuthority`
- [x] `ClientBattleAuthority`

The boundary must guarantee:

- [x] Host owns simulation ticks, spawning, AI, damage, death, and victory for supported V1 mission types; unsupported objective-authority missions are rejected before match loading and tracked in `docs/MULTIPLAYER_OBJECTIVE_REPLICATION_CHECKLIST.md`.
- [x] Client sends commands and renders from validated host state.
- [x] Client fire input does not locally create damage.
- [x] Client cannot spawn ships.
- [x] Client cannot declare victory.
- [x] Client cannot control an unassigned entity.
- [x] Client network threads do not directly mutate Swing components or simulation collections.
- [x] Dead players cannot submit gameplay commands.
- [x] Local spectator camera movement remains presentation-only.

## Gameplay Command Envelope

Every client gameplay command carries:

- [x] `matchId`
- [x] `sessionNonce`
- [x] host-assigned opaque `playerId`
- [x] `controlledEntityId`
- [x] monotonic `inputSequence`
- [x] `clientInputTick`
- [x] `commandType`
- [x] payload

The host validates:

- [x] Match ID matches the active match.
- [x] Session nonce is valid.
- [x] Player ID belongs to that connection.
- [x] Player names are display values, not authority identifiers.
- [x] Entity belongs to that player.
- [x] Input sequence is newer than the last accepted sequence.
- [x] Duplicate and out-of-order commands are rejected.
- [x] Command is permitted by the current rules profile.
- [x] Payload values are clamped or rejected when impossible.
- [x] Player is alive and allowed to act.

## Heartbeat And Timeout

- [x] Define handshake timeout.
- [x] Define lobby heartbeat or inactivity timeout.
- [x] Define match-loading timeout.
- [x] Define match heartbeat timeout.
- [x] Send heartbeat periodically when no other traffic is flowing.
- [x] Track last valid message time.
- [x] Mark peer disconnected after a configurable timeout.
- [x] Do not block indefinitely on socket reads.
- [x] Log graceful disconnect separately from abrupt socket failure.
- [x] Use named configurable constants instead of scattered timeout literals.

## Snapshot Baseline

- [x] Define host simulation tick rate.
- [x] Define snapshot send rate.
- [x] Define reliable events for creation, destruction, ownership changes, and match result.
- [x] Define replaceable state snapshots for position, velocity, health, shield, alive/dead, and objective summary.
- [x] Include input sequence numbers.
- [x] Include ownership acknowledgements.
- [x] Include match-start tick.
- [x] Add a minimal client presentation buffer before interpolation polish.
- [x] Apply snapshots through a client battle runtime or presentation model, not directly from network worker threads.

## Runtime Handoff

- [x] Preserve the current harness as `Multiplayer Diagnostics` or `Network Duel Harness`.
- [x] Normal `Multiplayer Setup` runs inside each player's existing game application instance.
- [x] Hosting and joining do not launch a child JVM in the normal user-facing flow.
- [x] Separate-process execution is diagnostics-only.
- [x] Add a real `GamePanel` path for a locked multiplayer duel spec.
- [x] Prove host launches a playable game view.
- [x] Prove client launches a playable game view.
- [x] Prove each player controls only one frigate.
- [x] Prove host resolves damage and elimination victory.
- [x] Prove both processes return cleanly after match end.
- [x] Do not expose AI/custom objectives until the hard-coded playable duel is stable.
- [x] Extract reusable spawn-plan generation from `SpawnSystem.initCustomBattles` rather than calling a single-player initializer that performs local-only UI, ownership, or runtime mutation.

Spawn parity means:

- [x] Same hull types.
- [x] Same team assignment.
- [x] Same spawn formation.
- [x] Same initial loadouts.
- [x] Same mission-objective entities are guaranteed for supported V1 missions by publishing a stable "no active objective" summary; objective-entity replication is gated behind `docs/MULTIPLAYER_OBJECTIVE_REPLICATION_CHECKLIST.md`.
- [x] Same seed-derived variation.
- [x] Network entity IDs, player ownership metadata, session IDs, and client presentation objects may differ.

## Player Slot Semantics

Player-controlled ships should occupy explicit mission roster slots. They should not be added on top of the roster.

- [x] Each slot has `slotId`.
- [x] Each slot has `teamId`.
- [x] Each slot has allowed hull categories.
- [x] Each slot has default hull.
- [x] Each slot has control mode.
- [x] Each slot marks whether it is required.

Control modes:

- [x] `PLAYER_REQUIRED`
- [x] `PLAYER_OR_AI`
- [x] `AI_ONLY`
- [x] `STATIC`

Ownership mapping:

- [x] Bind `playerId -> slotId -> spawnedEntityId` on the host.
- [x] Transmit the ownership mapping to clients.
- [x] Never assign ownership by searching for the first matching hull after spawn.
- [x] Prevent base, static turret, or non-controllable support hulls from being direct player ships.
- [x] Ensure AI does not override player-owned ships.

## Thread Ownership

- [x] Swing UI is owned by the Event Dispatch Thread.
- [x] Network reads run off the Event Dispatch Thread.
- [x] Network writes run off the Event Dispatch Thread.
- [x] Host simulation has one owner thread.
- [x] Client snapshot application has one owner path.
- [x] Lobby state changes are serialized through one owner path.
- [x] Network reads enqueue work; they do not mutate gameplay collections directly.
- [x] UI changes caused by networking are marshalled back to the Event Dispatch Thread.
- [x] Closing a screen interrupts and joins network workers cleanly.

## Match Identity

Every lobby and match should carry:

- [x] `lobbyId`
- [x] `matchId`
- [x] `sessionNonce`
- [x] `lockedConfigRevision`

Use these values to prevent delayed packets from a previous lobby or match from affecting a later one.

## LAN Address Policy

- [x] Host binds to a configurable port.
- [x] Port-binding failure is reported cleanly.
- [x] Recreating a lobby releases the old port first.
- [x] Host displays detected private LAN addresses.
- [x] Loopback is labeled as local-only and is not shown as the address another machine should use.
- [x] If multiple network interfaces exist, show a selectable or clearly labeled list.
- [x] Do not claim the displayed address works over the public internet.

## Post-Match And Disconnect Policy

- [x] Normal match completion with connection intact returns both players to the same lobby.
- [x] Returning to lobby clears readiness.
- [x] Returning to lobby unlocks mission editing.
- [x] Returning to lobby assigns a new `matchId`.
- [x] Returning to lobby preserves the connection.
- [x] Any Milestone 1 disconnect during an active match aborts the match.
- [x] Client voluntarily leaving during a match returns the client to `Multiplayer Setup`.
- [x] Client voluntarily leaving frees the client slot and returns host lobby to `OPEN`.
- [x] Host leaving or crashing terminates the match and returns the client to `Multiplayer Setup` with a readable message.
- [x] Host closing the lobby returns the client to `Multiplayer Setup`.
- [x] Compatibility or loading failure returns both players to the lobby when possible.
- [x] Fatal transport failure returns the affected player to `Multiplayer Setup`.

## Observability

Add readable logs for:

- [x] lobby created
- [x] client joined
- [x] compatibility accepted or rejected
- [x] lobby revision changed
- [x] player ready or unready
- [x] mission locked
- [x] match specification hash
- [x] player slot assignment
- [x] match started
- [x] disconnect reason
- [x] match result
- [x] cleanup completed

## Implementation Gates

### Gate A - Playable Hard-Coded Duel

- [x] Preserve the current harness.
- [x] Existing lobby connects.
- [x] Existing hard-coded duel enters `GamePanel`.
- [x] Each player controls one ship. Host/client launch roles each get the correct local slot and host-side ownership rejects unassigned control.
- [x] Host resolves damage and victory. Host-side remote direct-fire damage and in-game elimination victory events are implemented; post-match lobby return remains a separate item.
- [x] Both return cleanly.
- [x] No sockets, worker threads, or processes are leaked after repeated create/back/create.
- [x] Gate A tests cover host/client connect.
- [x] Gate A tests cover real `GamePanel` handoff.
- [x] Gate A tests cover ownership isolation.
- [x] Gate A tests cover host-authoritative remote input and ownership rejection.
- [x] Gate A tests cover elimination.
- [x] Gate A tests cover cleanup and repeated launch/exit.

### Gate B - Lobby-Owned Duel Configuration

- [x] Duel seed travels through lobby state.
- [x] Duel map size travels through lobby state.
- [x] Ready messages include lobby revision.
- [x] Host rejects stale ready messages.
- [x] Match lock freezes the exact config.
- [x] Locked config enters the runtime.
- [x] Double-clicking start cannot launch two matches.
- [x] Gate B tests cover lobby revisions.
- [x] Gate B tests cover stale ready rejection.
- [x] Gate B tests cover locking.
- [x] Gate B tests cover map and seed synchronization.
- [x] Gate B tests cover lobby panel start gating.

### Gate C - Shared Catalog Duel

- [x] `core:v1_duel` exists in the custom mission catalog.
- [x] Single-player and multiplayer both resolve through `MissionLaunchSpec`. Multiplayer V1 duel does, and single-player custom battles now use `SinglePlayerLaunchAdapter` on the live spawn path.
- [x] Main-menu setup selects from catalog-backed V1 multiplayer mission IDs.
- [x] No hard-coded `FRIGATE` duel launch remains in the menu launch path.
- [x] Existing single-player custom battle setup still launches and spawns the same rosters as before.
- [x] Gate C tests cover catalog resolution.
- [x] Gate C tests cover single-player parity.
- [x] Gate C tests cover mission definition digest.
- [x] Gate C tests cover locked launch-spec digest.
- [x] Gate C tests cover legacy launch regression.

### Gate D - Heavy Duel

- [x] Add `core:heavy_duel`.
- [x] Keep two opposing players, one ship each, no AI, elimination victory.
- [x] Use a different hull class and larger map than `core:v1_duel`.
- [x] Prove the catalog and launch spec can vary mission setup without changing networking rules.
- [x] Gate D tests cover alternate hull and map.
- [x] Gate D tests cover slot compatibility.
- [x] Gate D tests prove no networking-rule changes are required.

### Gate E - Fleet Duel

- [x] Add one or two host-controlled AI support ships per team.
- [x] Move to a rules profile that explicitly supports AI.
- [x] Host owns AI targeting, movement, spawning, damage, and death.
- [x] Snapshot volume and entity count stay within budget.
- [x] Victory remains elimination or fleet elimination.
- [x] Gate E tests cover AI host authority.
- [x] Gate E tests cover AI entity replication.
- [x] Gate E tests cover expanded snapshot volume.
- [x] Gate E tests cover fleet victory.

### Gate F - Objective Missions

- [x] Create a separate objective-replication checklist before implementation.
- [x] Define `ObjectiveStateSnapshot`.
- [x] Reject missions requiring unsupported objective replication.
- [x] Only then consider Resource Rush, Four Team Domination, waves, capture, timers, score, or resource counters; future work is gated in `docs/MULTIPLAYER_OBJECTIVE_REPLICATION_CHECKLIST.md`.

## Risk-First Implementation Order

1. Freeze the current duel baseline and keep the harness as diagnostics.
2. Create a minimal locked match spec for `core:v1_duel`.
3. Prove playable `GamePanel` handoff for host and client with a hard-coded duel.
4. Establish the authority boundary for input, damage, spawning, death, and victory.
5. Harden lifecycle and disconnect cleanup.
6. Extract the custom mission catalog after the real runtime contract is known.
7. Route single-player and multiplayer through `MissionLaunchSpec`.
8. Add the multiplayer landing, host lobby, and client lobby screens.
9. Replace the hard-coded duel with the catalog `core:v1_duel` entry.
10. Add `core:heavy_duel` as the first non-duel-variant expansion.
11. Add AI support only after duel setup, snapshots, ownership, and cleanup are stable.
12. Add objective replication as a separate later milestone.

## Acceptance Tests

Happy path:

- [x] Main menu shows `Multiplayer Setup` when the feature flag is enabled.
- [x] Create lobby opens the host lobby.
- [x] Join lobby opens direct-address join flow.
- [x] Host can select supported multiplayer missions only.
- [x] Client sees host mission updates.
- [x] Ready states clear after host mission changes.
- [x] Host starts the locked mission.
- [x] Match enters playable battle UI.
- [x] Host and client can fight.
- [x] Normal match completion returns both players to the same lobby with readiness cleared.

Negative path:

- [x] Joining an unreachable address fails without freezing Swing UI.
- [x] Invalid address shows a readable validation error.
- [x] Version mismatch returns the player to the join screen cleanly.
- [x] Selected mission unavailable returns a readable compatibility error.
- [x] Client disconnect clears its player slot.
- [x] Host cancellation returns client to menu or lobby cleanly.
- [x] Changing missions while client is ready clears readiness on both screens.
- [x] Closing lobby closes sockets and worker threads.
- [x] Repeated create/back/create does not leak ports or processes.
- [x] Stale lobby packets cannot overwrite newer mission settings.
- [x] Lost connection during countdown cancels or safely resolves countdown.
- [x] Leaving the match does not terminate the entire application.

Test categories:

- [x] Catalog tests.
- [x] Determinism tests.
- [x] Authority tests.
- [x] Lifecycle tests.
- [x] Threading tests.
- [x] Protocol compatibility tests.
- [x] Two-process smoke tests.
- [x] Single-player custom battle regression tests.
- [x] Campaign-save untouched regression tests.

Determinism coverage:

- [x] Same locked spec produces same spawn count.
- [x] Same locked spec produces same spawn locations.
- [x] Same locked spec produces the same initial objective state for supported V1 missions by rejecting unsupported objective specs before loading and publishing `ObjectiveSummarySnapshot.none()` for elimination matches; future objective parity tests live in `docs/MULTIPLAYER_OBJECTIVE_REPLICATION_CHECKLIST.md`.
- [x] Host restart from the same seed resolves identically.

Authority coverage:

- [x] Client cannot control an unassigned entity.
- [x] Client cannot change host settings.
- [x] Client cannot declare victory.
- [x] Client cannot spawn ships.
- [x] Host rejects malformed or unauthorized input.

Two-process coverage:

- [x] Port acquisition.
- [x] Host address display.
- [x] Connection.
- [x] Ready synchronization.
- [x] Match start tick.
- [x] Input exchange.
- [x] Damage visibility.
- [x] Match end.
- [x] Process cleanup.

# Multiplayer Custom Battle Checklist

Date: 2026-07-17  
Status: Proposed implementation plan for single-mission multiplayer only  
Scope: Personal custom battles. V1 proves a two-player duel; later phases may add same-team co-op, AI fleets, and expanded rules.

> Campaign multiplayer is explicitly out of scope. Do not sync campaign saves, campaign economy, strategic travel, mission boards, authored campaign transitions, persistent fleet rosters, or campaign progression for this feature.

## How To Use This Checklist

- `[ ]` means not implemented or not yet proven.
- `[~]` means partially implemented, prototyped, or dependent on an existing system that still needs integration.
- `[x]` means implemented, tested, and accepted in a playable build.
- Complete phases in order. Do not begin expanded multiplayer features until the V1 duel vertical slice is stable.
- Multiplayer may use separate session orchestration, input sources, transport, and presentation paths, but it must reuse the same authoritative tactical simulation, combat rules, ship systems, AI systems, damage model, and victory evaluation used by single-player custom battles.
- Do not create duplicated single-player and multiplayer combat systems.

## V1 Non-Negotiable Scope

- [x] Exactly two players: one host and one client.
- [x] One directly controlled ship per player.
- [x] Opposing teams only.
- [x] One arena.
- [x] No AI ships.
- [x] No escorts.
- [x] No formations or fleet-wide orders.
- [x] No fog of war or sensor-filtered replication.
- [x] No respawns.
- [x] No reconnect.
- [x] No mid-match joining.
- [x] No host migration.
- [x] No pause during active matches.
- [x] No superweapons.
- [x] No battlefield warp.
- [x] Elimination victory.
- [x] Loopback first, then direct LAN connection.
- [x] Return to menu after match end.
- [x] Host and client must use the same game build and multiplayer content manifest.

## First Vertical-Slice Acceptance Scenario

- [x] Launch one process as host.
- [x] Launch a second process as client.
- [x] Client connects through loopback or direct LAN address.
- [x] Host assigns Player 1 to Blue.
- [x] Client is assigned to Red.
- [x] Both players control exactly one ship.
- [x] Both players can thrust, rotate, aim, and fire.
- [x] Each player sees the other ship move smoothly.
- [x] The host authoritatively processes weapon hits.
- [x] Both machines show identical health and shield values after host snapshots arrive.
- [x] One ship is destroyed.
- [x] The host declares the surviving team the winner.
- [x] Both machines display the same result.
- [x] Both processes return cleanly to the multiplayer menu.
- [x] Campaign state and campaign saves remain unchanged.
- [x] Single-player custom battle still functions normally.

## Phase 0 - Lock V1 Rules

- [x] Add a `MultiplayerRulesV1` or equivalent named rules profile.
- [x] Record that campaign multiplayer is unsupported.
- [x] Record that V1 is exactly two players.
- [x] Record that V1 is one directly controlled ship per player.
- [x] Record that V1 is opposing-team duel only.
- [x] Record that V1 uses elimination victory.
- [x] Record that AI fill is unsupported in V1.
- [x] Record that same-team co-op is unsupported in V1.
- [x] Record that respawn is unsupported in V1.
- [x] Record that reconnect is unsupported in V1.
- [x] Record that mid-match joining is unsupported in V1.
- [x] Record that host migration is unsupported in V1.
- [x] Record that active-match pause is unsupported in V1.
- [x] Record that superweapons and battlefield warp are disabled in V1.
- [x] Add tests or acceptance checks proving unsupported V1 features are rejected with readable messages.
- [x] Add a feature flag so unfinished multiplayer entry points can be disabled in public builds.

## Phase 1 - Shared Battle Runtime Boundary

- [x] Create session orchestration types such as `SinglePlayerCustomBattleSession`, `MultiplayerHostBattleSession`, and `MultiplayerClientBattleSession`.
- [x] Ensure all authoritative sessions drive the same underlying battle simulation path.
- [x] Create battle-only setup data for arena, seed, teams, player slots, hulls, victory rule, and enabled rules.
- [x] Start multiplayer battles from a fresh tactical `GameContext` or battle world, not from campaign state.
- [x] Ensure multiplayer custom battles do not read campaign state.
- [x] Ensure multiplayer custom battles do not mutate campaign state.
- [x] Ensure multiplayer custom battles do not write campaign saves.
- [x] Add guardrails that prevent campaign-only actions and UI panels from appearing during multiplayer battles.
- [x] Define local-only presentation state: camera, hover, local menus, input hints, cosmetic-only effects, and local debug overlays.
- [x] Add regression tests proving campaign state remains untouched after hosting, joining, playing, ending, and exiting a multiplayer battle.

## Phase 2 - Fixed Tick, IDs, And Thread Ownership

- [x] Define the authoritative multiplayer tick rate.
- [x] Run host battle simulation on a fixed timestep independent from rendering.
- [x] Define the maximum number of catch-up ticks allowed per rendered frame.
- [x] Ensure host overload cannot create an unlimited simulation catch-up spiral.
- [x] Attach each accepted player command to an authoritative simulation tick.
- [x] Define stable `PlayerSlotId` values independent from ship IDs.
- [x] Store slot ID, team ID, controlled ship ID, player role, connection state, and display name in player slot state.
- [x] Keep player slot state alive after a controlled ship dies.
- [x] Define stable network entity IDs for ships, projectiles, hazards, and objectives.
- [x] Prevent immediate network entity ID reuse.
- [x] Include an entity generation or lifetime identity in networked entity references.
- [x] Define explicit spawn and despawn events.
- [x] Include despawn reason.
- [x] Ignore updates for unknown or retired entity generations.
- [x] Define that the host owns all gameplay randomness.
- [x] Use the match seed for reproducible setup, arena generation, spawn positions, tests, rematches, and bug reports.
- [x] Prevent clients from independently rolling authoritative weapon spread, AI choices, damage variation, loot, or objective outcomes.
- [x] Define simulation-thread ownership of authoritative battle state.
- [x] Allow networking threads to decode messages and enqueue work only.
- [x] Prevent networking callbacks from directly mutating ships, projectiles, health, AI, objectives, player ownership, or match state.
- [x] Publish immutable render and network snapshots.
- [x] Add debug assertions for off-thread battle-state mutation where practical.

## Phase 3 - Command Pipeline

- [x] Route single-player custom battle control through the same battle command path used by multiplayer.
- [x] Split command types into continuous input frames and discrete battle commands.
- [x] Define a compact `PlayerInputFrame` for thrust, strafe if supported, turn, aim angle, and held fire state.
- [x] Add sequence numbers to continuous input frames.
- [x] Define maximum accepted input frequency.
- [x] Define duplicate input handling.
- [x] Define stale input rejection.
- [x] Clear held movement and fire inputs if no fresh input arrives within a host-owned timeout.
- [x] Add discrete commands for ability activation, target selection, ready state, lobby changes, and rule-supported actions.
- [x] Keep fleet order, formation, escort assignment, and respawn commands out of V1 runtime even if protocol placeholders exist.
- [x] Validate every command against player slot ownership.
- [x] Reject commands for ships not owned by the sending slot.
- [x] Reject commands that are illegal under `MultiplayerRulesV1`.
- [x] Add tests proving one player cannot control the other player's ship.
- [x] Add tests proving single-player custom battle still works through the command path.

## Phase 4 - Multiple Command Sources Without Network

- [x] Rename any local prototype work to `Multiple Command Sources and Player Ownership`, not local split-screen.
- [x] Support two command queues feeding one authoritative battle simulation.
- [x] Use one real local input source and one scripted command source, or two scripted command sources.
- [x] Assign Slot A to Ship A and Slot B to Ship B.
- [x] Put Slot A and Slot B on opposing teams.
- [x] Prove each slot can move and fire only its controlled ship.
- [x] Prove host-side damage, death, and elimination victory work without network transport.
- [x] Ensure AI ignores direct control of player-owned ships.
- [x] Add a debug scenario for the two-slot duel pipeline.
- [x] Add tests for damage, death, team ownership, and victory.

## Phase 5 - Protocol And Replication Contract

- [x] Define protocol version.
- [x] Define game build compatibility requirements.
- [x] Define multiplayer content manifest compatibility requirements.
- [x] Include hashes or versions for battle rules, hull definitions, weapons, abilities, arenas, enabled mods, and required assets.
- [x] Reject incompatible clients with a readable reason.
- [x] Define message families: handshake, lobby/control, client input, reliable authoritative events, snapshots, acknowledgements, heartbeat, error, and disconnect.
- [x] Define reliable ordered messages for lobby, match configuration, spawn, despawn, death, match end, and error messages.
- [x] Define sequenced replaceable snapshots for battle state.
- [x] Start V1 with compact full snapshots at a fixed snapshot rate.
- [x] Defer delta snapshots until profiling proves full snapshots are too expensive.
- [x] Define snapshot sequence numbers.
- [x] Define host tick included in every snapshot.
- [x] Define input acknowledgements from host to client.
- [x] Define unknown entity and retired generation handling.
- [x] Define message size limits.
- [x] Define malformed-message behavior.
- [x] Avoid deserializing arbitrary Java object graphs from peers.
- [x] Use explicit protocol records.

## Phase 6 - Entity, Projectile, And Event Replication

- [x] Define ship replication fields: network ID, generation, role, faction, position, velocity, angle, health, shield, alive/dead state, and major status effects.
- [x] Define player slot to controlled ship mapping replication.
- [x] Define match timer and victory state replication.
- [x] Define authoritative event types: ship spawned, ship despawned, ship destroyed, weapon fired, hit confirmed, explosion occurred, objective completed, player disconnected, victory declared, and control ownership changed.
- [x] Define projectile replication categories.
- [x] Fully replicate important persistent projectiles such as missiles, torpedoes, mines, and slow major weapons.
- [x] Use fire and hit events for hitscan or instant weapons.
- [x] Use cosmetic fire events for rapid tracers or point-defense effects where host damage is still authoritative.
- [x] Define bandwidth budgets before expanding projectile replication.
- [x] Add tests for delayed snapshots referencing destroyed entities.
- [x] Add tests for spawn/despawn events and generation mismatch handling.

## Phase 7 - Loopback Host/Client Vertical Slice

- [x] Implement a loopback transport behind the transport interface.
- [x] Run one process as host and one local client, or two sessions in a controlled local harness.
- [x] Send client input frames to the host.
- [x] Apply host-side validation and tick assignment.
- [x] Simulate movement and weapons on the host.
- [x] Send full host snapshots to the client.
- [x] Render the client from snapshots.
- [x] Send reliable events for spawn, weapon fire, hit confirmation where needed, death, and victory.
- [x] Add heartbeat and timeout behavior in loopback.
- [x] Add clean match exit.
- [x] Add loopback integration tests for connect, start, move, fire, damage, destroy, victory, and return to menu.

## Phase 8 - Client Presentation And Interpolation

- [x] Create a client battle view that renders from host snapshots.
- [x] Prevent clients from running authoritative AI, damage, death, objective completion, ship spawning, target validity, or victory evaluation.
- [x] Allow clients to run interpolation, local camera, cosmetic particles, sound, UI, and temporary predicted muzzle effects.
- [x] Define interpolation delay.
- [x] Render slightly behind latest host state so the client usually has two snapshots to interpolate between.
- [x] Display latest received host tick.
- [x] Display rendered host tick.
- [x] Display interpolation delay.
- [x] Display snapshot gap.
- [x] Display extrapolation duration.
- [x] Display correction magnitude when prediction is later enabled.
- [x] Show local player hull, team, health, shields, weapon state, and match result.
- [x] Show remote player name and team marker.
- [x] Use complete visibility in V1.
- [x] Defer per-client fog-of-war and sensor-filtered replication until after the duel is stable.

## Phase 9 - LAN Duel

- [x] Choose V1 transport requirements before choosing a library.
- [x] Define whether V1 requires reliable ordered delivery, sequenced replaceable snapshots, connection-oriented sessions, LAN discovery, direct IP, encryption, platform invites, or relay support.
- [x] Implement direct loopback connection.
- [x] Implement manual LAN address connection.
- [x] Keep LAN discovery as a separate feature from direct IP join.
- [x] Clearly explain firewall and NAT limitations.
- [x] Do not claim internet hosting until NAT, firewall, relay, or platform networking is intentionally supported.
- [x] Add connect, disconnect, heartbeat, timeout, and readable error handling.
- [x] Add connection lifecycle logs.
- [x] Add match and connection correlation IDs to logs.
- [x] Include match ID, connection ID, player slot ID, host tick, command sequence, snapshot sequence, protocol version, game build, and disconnect reason where relevant.
- [ ] Run the first real two-machine LAN CLI pass. Evidence required: host and client reports from two separate machines must validate through `multiplayerLanAcceptanceValidate`; loopback dry-runs are intentionally rejected.

## Phase 10 - Lobby And Match Setup

- [x] Define host-owned lobby state machine: connecting, in lobby, ready, countdown, loading, in match, match ended, returning to lobby, disconnected.
- [x] Add multiplayer entry point from the custom battle menu.
- [x] Add `Host Battle`.
- [x] Add `Join Battle`.
- [x] Let `Host Battle` and `Join Battle` initiate the V1 multiplayer LAN harness from inside the game instead of only displaying external commands.
- [x] Add direct connect address entry.
- [x] Add generated default player names or player name entry.
- [x] Add V1-locked team assignment: host Blue, client Red unless later settings allow otherwise.
- [x] Add V1 hull selection if hull selection is included in the first playable slice.
- [x] Add ready state.
- [x] Make lobby settings host-authoritative.
- [x] Increment lobby revision when match settings change.
- [x] Clear affected ready states after material configuration changes.
- [x] Lock match configuration when countdown begins.
- [x] Reject late configuration messages after match lock.
- [x] Reject join requests after the match is locked or active with a readable `Match already in progress` message.
- [x] Add client loading and match-start synchronization.

## Phase 11 - Disconnect, Timeout, And Cleanup

- [x] Define V1 client disconnect policy before network implementation is considered complete.
- [x] For V1, choose either immediate client forfeit or host-controlled AI takeover; document the rule.
- [x] Implement the chosen disconnect rule on the host.
- [x] If host disconnects, end the match and return clients to lobby or multiplayer menu.
- [x] Explicitly mark host migration unsupported.
- [x] Explicitly mark reconnect unsupported.
- [x] Reject reconnect attempts with a readable message unless a later phase implements reconnect fully.
- [x] Clear command queues, snapshot buffers, listeners, sockets, and background threads on match exit.
- [x] Add tests for sudden disconnect, stalled connection, timeout, and cleanup.
- [x] Add repeated match creation/destruction tests to catch stale listeners and abandoned threads.

## Phase 12 - Network Conditions And Latency Hardening

- [x] Add a simulated network condition wrapper around loopback or test transport.
- [x] Simulate latency.
- [x] Simulate jitter.
- [x] Simulate packet loss.
- [x] Simulate duplication.
- [x] Simulate reordering.
- [x] Simulate burst traffic.
- [x] Simulate malformed messages.
- [x] Simulate client freeze and recovery.
- [x] Simulate host frame-rate degradation.
- [x] Define LAN latency target.
- [x] Define acceptable snapshot age.
- [x] Define target snapshots per second.
- [x] Define maximum normal snapshot size.
- [x] Define maximum peak snapshot size.
- [x] Define maximum bytes per second per client.
- [x] Define maximum V1 supported entities and replicated projectiles.

## Phase 13 - Optional Local Prediction

- [x] Do not add prediction until host-confirmed movement plus interpolation is stable and measured.
- [x] If prediction is needed, predict the local player ship only.
- [x] Keep weapon hits host-authoritative.
- [x] Keep AI host-authoritative.
- [x] Assign sequence numbers to predicted inputs.
- [x] Apply local input immediately.
- [x] Retain unacknowledged inputs.
- [x] Receive authoritative state with last processed input sequence.
- [x] Reset to host state.
- [x] Replay remaining unacknowledged inputs.
- [x] Smooth visible correction.
- [x] Add debug display for correction magnitude and replay count.

## Phase 14 - Security And Trust Boundaries

- [x] Prioritize crash prevention, ownership validation, bounds checking, message size limits, protocol validation, rate limiting, and safe disconnect handling.
- [x] Validate every network message before use.
- [x] Reject commands from unknown, disconnected, unready, or invalid player slots.
- [x] Reject commands for ships not owned by the sending slot.
- [x] Reject impossible movement, fire, ability, or menu commands.
- [x] Clamp all numeric network inputs.
- [x] Never deserialize arbitrary classes from untrusted peers.
- [x] Avoid exposing filesystem paths, saves, config files, or developer tools to peers.
- [x] Avoid logging passwords, tokens, or private addresses unnecessarily.
- [x] Add suspicious-command logging without crashing the match.
- [x] Defer advanced anti-cheat until the personal-battle MVP is playable.

## Phase 15 - V1 Testing And Acceptance

- [x] Add unit tests for command validation.
- [x] Add unit tests for input timeout clearing.
- [x] Add unit tests for player slot ownership.
- [x] Add unit tests for entity ID generation and retired generation rejection.
- [x] Add unit tests for snapshot serialization.
- [x] Add unit tests for reliable event serialization.
- [x] Add integration tests for local loopback host/client.
- [x] Add integration tests for opposing-team duel.
- [x] Add integration tests for direct LAN where practical.
- [x] Add regression tests proving campaign state is untouched by multiplayer battles.
- [x] Add regression tests proving single-player custom battle still works.
- [x] Add routine 10-minute loopback or LAN soak.
- [x] Add 30-minute manual soak before release readiness.
- [x] Add 60-120 minute pre-release stability soak.
- [x] Add repeated rematch or match recreation cycles to catch cleanup leaks.
- [x] Add performance tests for snapshot generation and client interpolation.
- [x] Add manual acceptance script for host, join, ready, fight, win, disconnect, and return to menu.
- [x] Add a command-line two-machine LAN duel acceptance harness for the first direct-address pass.
- [x] Add Gradle host/client/validation tasks for two-machine LAN acceptance reports.
- [x] Run a same-machine loopback dry-run of the Gradle host/client/validation acceptance workflow.
- [x] Surface the Gradle LAN acceptance commands from the feature-flagged multiplayer custom-battle menu entry.
- [x] Add a standalone Gradle two-process loopback acceptance report task.
- [x] Add a Gradle multiplayer acceptance audit that separates automated evidence from external manual gates.
- [x] Add a LAN host preflight report task for two-machine acceptance setup.
- [x] Make the multiplayer acceptance audit validate the LAN preflight report.
- [x] Add a local two-machine readiness report before the external LAN pass.
- [x] Make the release gate require a passing local two-machine readiness report.
- [x] Add manual acceptance evidence templates and validation to prevent boolean-only signoff.
- [x] Add one-command generation for the default manual acceptance evidence templates.
- [x] Require manual acceptance reports to link to passing generated evidence reports.
- [x] Add a generated two-machine acceptance runbook with host, client, and audit scripts.
- [x] Add a validated two-machine machine/build/network acceptance log.
- [x] Record advertised LAN addresses and socket endpoint evidence in two-machine CLI reports.
- [x] Add a release gate that fails if multiplayer is enabled without complete acceptance evidence.
- [x] Make the release gate require the validated two-machine acceptance log.
- [x] Include the release gate in the generated two-machine runbook audit script.
- [x] Print gate-by-gate release status from the multiplayer release gate.
- [x] Add an evidence bundle manifest with hashes for multiplayer acceptance reports.
- [x] Include protocol version, game build, and content manifest hashes in the evidence bundle.

## Phase 16 - Post-V1 Same-Team Co-op And AI Fleets

- [x] Add same-team player slots only after opposing-team duel is stable.
- [x] Add AI ships only after opposing-team duel is stable.
- [x] Add one designated fleet commander per team.
- [x] Allow only the fleet commander slot to issue team-wide fleet, formation, and escort orders.
- [x] Keep non-commander slots limited to direct control of their assigned ships and allowed local commands.
- [x] Add captain authority UI.
- [x] Add same-team sensor sharing rules.
- [x] Add AI escort ownership or command scope only if captain authority is not enough.
- [x] Add tests for same-team authority conflicts.
- [x] Add tests proving non-commanders cannot overwrite team-wide orders.

## Phase 17 - Post-V1 Expanded Rules

- [x] Consider additional arenas.
- [x] Consider hull selection expansion.
- [x] Consider AI-filled teams.
- [x] Consider base destruction victory.
- [x] Consider score timer victory.
- [x] Consider custom objective victory.
- [x] Consider respawn.
- [x] Consider superweapons.
- [x] Consider battlefield warp.
- [x] Consider hazards, mines, salvage, and complex projectiles.
- [x] Consider fog of war and per-client visibility-filtered snapshots.
- [x] Consider reconnect with slot reservation, reconnect token, full resync, obsolete command discard, and control restore.
- [x] Consider internet hosting through platform relay or another intentionally chosen networking layer.
- [x] Consider passwords or invite codes only as part of a broader connection-security plan.

## Phase 18 - Release Readiness

- [x] Add multiplayer known limitations to release notes.
- [x] Add troubleshooting notes for hosting and joining.
- [x] Add protocol version and content manifest display in debug UI.
- [x] Add debug-only authoritative state hash support for important host state.
- [x] Use state hashes to validate full snapshot reconstruction in tests.
- [x] Add opt-in telemetry or debug logs for failed connects if telemetry is enabled.
- [x] Add crash-safe cleanup when a match exits.
- [x] Confirm single-player custom battles remain unchanged.
- [x] Confirm campaign mode remains unchanged.
- [x] Confirm multiplayer feature flag can disable all multiplayer entry points.
- [x] Confirm the release gate accepts the disabled feature while still reporting incomplete multiplayer acceptance evidence.
- [x] Confirm the release gate requires automated two-process TCP duel evidence.
- [x] Confirm the release gate requires LAN host preflight evidence.
- [x] Confirm the release gate requires a passing local two-machine readiness report.
- [x] Confirm the release gate requires the first real two-machine LAN CLI pass.
- [x] Confirm the release gate requires interactive two-process manual acceptance.
- [x] Confirm the release gate requires final two-machine manual acceptance.
- [x] Confirm the release gate requires a completed two-machine machine/build/network acceptance log.
- [~] Complete final manual acceptance on two processes. Current evidence: automated two-process TCP duel passes; interactive manual report is still `passed=false`.
- [ ] Complete the first real two-machine LAN CLI pass on two separate machines. Current evidence: same-machine loopback dry-run exists, but release gate rejects it as two-machine proof.
- [ ] Complete final manual acceptance on two machines. Current evidence: final manual report template exists, but it is still `passed=false`.
- [ ] Complete the two-machine machine/build/network acceptance log. Current evidence: validated template exists, but required fields and pass checkboxes are intentionally incomplete until the real LAN pass.

## Current Acceptance Audit Snapshot

- [x] Gate 1: automated two-process TCP duel - proven.
- [x] Gate 2: LAN host preflight - proven.
- [ ] Gate 3: first real two-machine LAN CLI pass - not proven; loopback reports do not count.
- [x] Gate 4: local two-machine readiness report - proven.
- [~] Gate 5: interactive two-process manual acceptance - not proven; manual report did not pass.
- [ ] Gate 6: final two-machine manual acceptance - not proven; manual report did not pass.
- [ ] Gate 7: two-machine machine/build/network acceptance log - not proven; generated template still needs real machine/build/network values and all required pass boxes checked.

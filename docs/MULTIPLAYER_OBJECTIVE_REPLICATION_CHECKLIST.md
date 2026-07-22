# Multiplayer Objective Replication Checklist

This checklist is intentionally separate from the V1 custom-mission lobby work. V1 remains elimination-only until every gate below is complete.

## Gate 1: State Shape

- [x] Define `ObjectiveStateSnapshot` with objective id, active/complete flags, owning team, normalized progress, team scores, remaining time, revision, and summary text.
- [x] Provide a lossier `ObjectiveSummarySnapshot` projection for HUD/snapshot compatibility.
- [ ] Decide whether full objective state travels in the regular battle snapshot, reliable events, or both.

## Gate 2: Supported Objective Types

- [ ] Define explicit objective type ids for capture, domination, resource, waves, timers, score, and escort-style objectives.
- [ ] Define per-objective state fields and revision semantics.
- [ ] Define which objective transitions are reliable lifecycle events.

## Gate 3: Authority

- [ ] Host owns objective entity creation, ownership, timers, counters, scoring, completion, and failure.
- [ ] Clients reject local objective mutation except presentation-only HUD state.
- [ ] Objective updates are deterministic across host snapshots and client presentation.

## Gate 4: Compatibility

- [x] V1 rejects missions requiring unsupported objective replication.
- [ ] Lobby compatibility fingerprints include objective replication support.
- [ ] Loading failure returns both peers to lobby with a readable objective-compatibility message.

## Gate 5: Tests

- [ ] Same locked spec creates the same initial objective state on host and client.
- [ ] Capture/domination state replicates ownership and progress.
- [ ] Resource/score state replicates counters and victory.
- [ ] Timer state replicates remaining time and expiry result.
- [ ] Unsupported objective missions are rejected before match loading.

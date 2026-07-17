# Multiplayer V1 Two-Machine Acceptance Log

Use this log for the first real two-machine LAN test and final two-machine acceptance. Do not mark the checklist's two-machine items complete until this has real values from two separate machines. Loopback reports such as `127.0.0.1`, `localhost`, or `::1` are intentionally rejected as two-machine evidence.

## Build And Machines

- Build/version:
- Commit or packaged build ID:
- Host machine OS / CPU / RAM:
- Client machine OS / CPU / RAM:
- Network type: wired / Wi-Fi / mixed
- Host LAN address and port:
- Client LAN address:
- Firewall rule created or confirmed:
- Two-machine runbook directory:
- Host preflight report path:
- Host preflight candidate address used:
- Host CLI report path:
- Client CLI report path:
- Host observed client endpoint:
- Client reported local endpoint:
- Final two-machine manual report path:
- Evidence validator result: `.\gradlew.bat "-PmpHostReport=<host-report-path>" "-PmpClientReport=<client-report-path>" multiplayerLanAcceptanceValidate`
- Manual report validator result: `.\gradlew.bat "-PmpMode=validate" "-PmpScope=final-two-machine" "-PmpReport=<final-two-machine-manual-report-path>" multiplayerManualAcceptanceReport`
  The final manual report must point at passing `twoProcessReport`, `preflightReport`, `hostReport`, and `clientReport` files.
- Acceptance audit result: `.\gradlew.bat "-PmpTwoProcessReport=<two-process-report-path>" "-PmpPreflightReport=<preflight-report-path>" "-PmpHostReport=<host-report-path>" "-PmpClientReport=<client-report-path>" "-PmpInteractiveReport=<interactive-manual-report-path>" "-PmpFinalReport=<final-two-machine-manual-report-path>" multiplayerAcceptanceAudit`
- Release gate result: `.\gradlew.bat "-PmpTwoProcessReport=<two-process-report-path>" "-PmpPreflightReport=<preflight-report-path>" "-PmpHostReport=<host-report-path>" "-PmpClientReport=<client-report-path>" "-PmpInteractiveReport=<interactive-manual-report-path>" "-PmpFinalReport=<final-two-machine-manual-report-path>" multiplayerReleaseGate`
- Evidence bundle report path:

## Required Pass

- [ ] Host CLI report from `MultiplayerLanDuelAcceptanceHarness host` contains `passed=true`.
- [ ] Client CLI report from `MultiplayerLanDuelAcceptanceHarness client` contains `passed=true`.
- [ ] Both CLI reports contain `Elimination victory`.
- [ ] Client joins host through direct LAN address.
- [ ] Host is Blue and client is Red.
- [ ] Both players control exactly one ship.
- [ ] Both players can thrust, rotate, aim, and fire.
- [ ] Remote ship movement is smooth enough for a personal battle.
- [ ] Host authoritatively processes weapon hits.
- [ ] Health and shield values match after snapshots arrive.
- [ ] One ship is destroyed.
- [ ] Both machines show the same winner.
- [ ] Client disconnect awards host forfeit in a second match.
- [ ] Host disconnect returns client to multiplayer menu in a third match.
- [ ] Both processes return cleanly to the multiplayer menu.
- [ ] Campaign saves and campaign state remain unchanged.

## Observations

- Snapshot gap / perceived latency:
- Disconnects or reconnect attempts:
- Errors or warnings:
- Memory/process growth:
- Follow-up defects filed:

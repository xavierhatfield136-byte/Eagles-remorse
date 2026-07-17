# Multiplayer V1 Manual Acceptance

Scope: single-mission custom battle only. Campaign multiplayer remains unsupported.

## Host, Join, Ready, Fight, Win, Disconnect, Return

1. Enable `multiplayer_custom_battle` only in a local test build.
   Local `.\gradlew.bat run` enables the experimental in-game launcher by default; pass `-PenableMultiplayerCustomBattle=false` to hide it during local runs.
2. Generate a two-machine acceptance runbook after choosing the host LAN address:
   `.\gradlew.bat "-PmpRunDir=build/reports/multiplayer-two-machine-run" "-PmpHostAddress=<host-lan-ip>" "-PmpClientAddress=<client-lan-ip>" "-PmpPort=46717" multiplayerTwoMachineRunbook`
3. Prefer the generated `host-acceptance.ps1`, `client-acceptance.ps1`, and `audit-acceptance.ps1` scripts so every report path stays consistent; the audit script also runs the release gate and writes the evidence bundle. Use the commands below as the explicit fallback/debug path.
4. Run the automated two-process TCP duel acceptance task and confirm the report contains `passed=true` and `victoryObserved=true`:
   `.\gradlew.bat "-PmpReport=build/reports/multiplayer-two-process-acceptance.txt" multiplayerTwoProcessAcceptance`
5. Run the LAN host preflight on the host machine and record its candidate LAN address:
   `.\gradlew.bat "-PmpPort=46717" "-PmpReport=build/reports/multiplayer-lan-preflight.txt" multiplayerLanPreflight`
6. For the first two-machine CLI pass, run this on the host machine:
   `.\gradlew.bat "-PmpPort=46717" "-PmpHostAddress=<host-lan-ip>" "-PmpTimeoutMs=60000" "-PmpReport=build/reports/multiplayer-lan-host-acceptance.txt" multiplayerLanAcceptanceHost`
7. Run this on the client machine:
   `.\gradlew.bat "-PmpAddress=<host-lan-ip>:46717" "-PmpClientAddress=<client-lan-ip>" "-PmpTimeoutMs=60000" "-PmpReport=build/reports/multiplayer-lan-client-acceptance.txt" multiplayerLanAcceptanceClient`
8. Validate both reports with:
   `.\gradlew.bat "-PmpHostReport=<host-report-path>" "-PmpClientReport=<client-report-path>" multiplayerLanAcceptanceValidate`
9. Confirm both CLI reports contain `passed=true`, `result=Elimination victory`, and matching direct LAN address evidence.
10. Create both default manual evidence templates:
   `.\gradlew.bat multiplayerManualAcceptanceTemplates`
11. Write the local readiness report before involving a second computer:
   `.\gradlew.bat multiplayerTwoMachineReadiness`
12. Create the real two-machine acceptance log template if you are not using the generated runbook copy:
   `.\gradlew.bat multiplayerTwoMachineAcceptanceLog`
13. If you need custom paths, create or validate individual manual reports with `multiplayerManualAcceptanceReport`.
14. Start one process as host from the multiplayer custom battle entry point.
15. Confirm the host lobby shows Host Battle, host name, Blue team, hull selection, ready state, protocol version, game build, and content manifest.
16. Start a second process as client and join by direct address, using `127.0.0.1:<port>` for loopback or the host LAN address for a two-machine test.
   The in-game `Host Battle` and `Join Battle` buttons launch the same V1 LAN harness and write `build/reports/multiplayer-in-game-host.txt` or `build/reports/multiplayer-in-game-client.txt`.
17. Confirm the client lobby shows Join Battle, direct address entry, Red team, hull selection, ready state, protocol version, game build, and content manifest.
18. Change a host-owned setting and confirm both ready states clear.
19. Ready both players and begin countdown.
20. Confirm late setting changes are rejected after lock.
21. Confirm late join receives `Match already in progress`.
22. Enter the match and confirm each player controls exactly one ship.
23. Confirm thrust, turn, aim, and primary fire reach the host.
24. Confirm host snapshots update both clients with matching health and shield values.
25. Destroy one ship and confirm both processes display the same winner.
26. Disconnect the client during a second match and confirm V1 awards a host win by forfeit.
27. Disconnect the host during a third match and confirm the client returns to the multiplayer menu.
28. Exit cleanly and confirm no campaign save, campaign economy, or campaign roster changed.
29. Update the applicable manual evidence report by setting `passed=true`, filling tester/build/date/address fields, pointing the report evidence fields at the actual generated reports, and setting every `check.*` key to `true`.
30. Fill the real two-machine acceptance log with machine/build/network values and check every required pass item.
31. Validate the completed manual evidence report:
   `.\gradlew.bat "-PmpMode=validate" "-PmpScope=<interactive-two-process|final-two-machine>" "-PmpReport=<manual-report-path>" multiplayerManualAcceptanceReport`
32. Validate the two-machine acceptance log:
   `.\gradlew.bat "-PmpMode=validate" "-PmpTwoMachineLog=<two-machine-acceptance-log-path>" multiplayerTwoMachineAcceptanceLog`
33. Write the acceptance audit:
   `.\gradlew.bat "-PmpTwoProcessReport=build/reports/multiplayer-two-process-acceptance.txt" "-PmpPreflightReport=build/reports/multiplayer-lan-preflight.txt" "-PmpHostReport=<host-report-path>" "-PmpClientReport=<client-report-path>" "-PmpInteractiveReport=<interactive-manual-report-path>" "-PmpFinalReport=<final-two-machine-manual-report-path>" "-PmpTwoMachineLog=<two-machine-acceptance-log-path>" "-PmpReadinessReport=<readiness-report-path>" multiplayerAcceptanceAudit`
34. Before enabling `multiplayer_custom_battle` in any public or release build, run the release gate:
   `.\gradlew.bat "-PmpTwoProcessReport=<two-process-report-path>" "-PmpPreflightReport=<preflight-report-path>" "-PmpHostReport=<host-report-path>" "-PmpClientReport=<client-report-path>" "-PmpInteractiveReport=<interactive-manual-report-path>" "-PmpFinalReport=<final-two-machine-manual-report-path>" "-PmpTwoMachineLog=<two-machine-acceptance-log-path>" "-PmpReadinessReport=<readiness-report-path>" multiplayerReleaseGate`
35. Write the evidence bundle for review, including report hashes, the readiness report, the two-machine acceptance log, and the multiplayer protocol/content manifest fingerprint:
   `.\gradlew.bat "-PmpTwoProcessReport=<two-process-report-path>" "-PmpPreflightReport=<preflight-report-path>" "-PmpHostReport=<host-report-path>" "-PmpClientReport=<client-report-path>" "-PmpInteractiveReport=<interactive-manual-report-path>" "-PmpFinalReport=<final-two-machine-manual-report-path>" "-PmpAuditReport=<acceptance-audit-report-path>" "-PmpReadinessReport=<readiness-report-path>" "-PmpTwoMachineLog=<two-machine-acceptance-log-path>" "-PmpBundleReport=<evidence-bundle-report-path>" multiplayerEvidenceBundle`
36. For the real LAN pass, keep the completed machine/build/network evidence with the final evidence bundle.

For a same-machine dry-run before involving a second computer, add `"-PmpLoopback=true"` to the host command and use `"-PmpAddress=127.0.0.1:46717"` on the client.

## Known V1 Limits

- Direct LAN/manual address only.
- Firewall rules may block hosting.
- NAT traversal, relay, internet hosting, LAN discovery, encryption, platform invites, reconnect, host migration, mid-match join, respawn, same-team co-op, AI fleets, fog-of-war replication, superweapons, and battlefield warp are unsupported.

## Soak Routine

1. Run the automated loopback soak for 10 minutes with `MultiplayerSoakHarnessV1.ROUTINE_LOOPBACK_SOAK`.
2. Before release readiness, run one 30-minute manual loopback or LAN soak with normal movement, aiming, firing, victory, rematch, disconnect, and return-to-menu checks.
3. Before pre-release signoff, run one 60-120 minute LAN stability soak and record snapshot gaps, disconnects, process exits, and memory growth.

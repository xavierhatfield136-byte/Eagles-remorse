# Multiplayer V1 Known Limitations And Troubleshooting

## Known Limitations

- Multiplayer is custom-battle only; campaign multiplayer is unsupported.
- V1 is a two-player duel with one directly controlled ship per player.
- Same-team co-op, AI fleets, respawn, reconnect, mid-match join, host migration, pause, fog-of-war replication, superweapons, and battlefield warp are unsupported.
- Direct LAN/manual address is the only intended network path.
- LAN discovery, relay, NAT traversal, platform invites, encryption, and internet hosting are unsupported.
- The feature flag can disable multiplayer entry points for public builds.

## Troubleshooting

- If a client cannot join, confirm host and client are on the same game build and content manifest.
- If loopback fails, try `127.0.0.1:<port>` and confirm no local firewall rule is blocking the process.
- If LAN join fails, confirm both machines are on the same network and the host firewall allows inbound TCP on the chosen port.
- If the client receives `Protocol mismatch`, update both processes to the same build.
- If the client receives `Multiplayer content manifest mismatch`, disable mismatched local content or use the same packaged assets.
- If the host disconnects, V1 ends the match because host migration is unsupported.
- If the client disconnects or times out, V1 awards the match to the host by forfeit.

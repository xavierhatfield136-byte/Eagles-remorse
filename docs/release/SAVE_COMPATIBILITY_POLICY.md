# Save Compatibility Policy

## Alpha Policy

- Public alpha saves use the campaign checkpoint schema documented in
  `docs/CAMPAIGN_SAVE_SCHEMA.md`.
- Runtime saves, autosaves, menu settings, control settings, accessibility
  settings, unlock profiles, and logs are stored under the user's application
  data directory.
- Uninstalling or deleting the app-image/portable folder must not delete user
  saves unless the player explicitly removes the user data directory.

## Migration Policy

- Existing migration code repairs known older checkpoint fields into the current
  schema.
- New public releases must either preserve the previous public schema or include
  an explicit migration path and fixture coverage.
- Corrupt or incompatible saves should fail safely with recovery messaging rather
  than blocking launch.

## Manual Save Backup

Before major release upgrades, players may copy the user data folder shown in the
Phase 11 packaging report. On Windows, the default location is under:

```text
%APPDATA%\Eagles Remorse
```

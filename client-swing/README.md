# Swing Client Module (Planned)

Purpose:
- Keep existing Swing frontend as a stable reference client during migration.
- Consume core simulation through an adapter layer.

Current status:
- Module scaffold created for M0 foundation split.
- Existing Swing app still runs from legacy `src/` to avoid breaking active development.

Next move:
- Move Swing-specific entry/render/input classes into this module after core split hardens.

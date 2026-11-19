# Changelog

## [1.1.0] - 2026-11-19

### Added
- Bosses pick what they hold from datapack loadouts in `data/rpg-minibosses/miniboss_loadouts/<entity>.json`. The README has the format.
- Bosses use the unique abilities on Simply Swords weapons they hold, and cast Spell Engine spells from their items or from the loadout entry.
- Each entry can tune its ability with chance, interval, cooldown, range and health threshold settings, or turn it off with `enabled: false`.
- Config options `heldAbilities` and `abilityCooldownMultiplier`.

### Fixed
- Boss spells did no damage on dedicated servers. They only worked in singleplayer.
- Swords a boss drops come back dormant instead of fully awakened.

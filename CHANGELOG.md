# Changelog

## [Unreleased]

### Added
- Mercenary loadouts can give it any bow or crossbow. It shoots with the weapon's enchantments and Ranged Weapon API damage, and the held weapon shows instead of its built-in crossbow.
- Every adventurer except Magus Prime now rolls an RPG Series or Simply Swords weapon by class, and casts that class's spells: wands and staffs get wizard spells, daggers get rogue spells, runeblades get death knight spells and so on. Lesser bosses roll up to diamond, greater ones diamond and up plus uniques. Classes are item tags under `rpg-minibosses:loadout/`, and mods you don't have just get skipped.
- Loadout entries take `spell_pool`, a Spell Engine pool id or a list of them. Pools are read live, so packs that edit them change what bosses cast.
- Spell books with no spells of their own cast from their pool.
- Mercenaries cast archery spells like Barrage and Power Shot by firing an arrow that carries the spell. Greater ones can also roll an Archers Expansion pool.
- Bosses play their own moves when they cast a held spell. Strikes use their swings, area and channeled spells use the Archmage's wave or the Juggernaut's slam, and the Mercenary draws a heavy shot. The spell lands on the hit frame, like their normal attacks. Spells with no matching move, like shouts and whirlwind, cast with no animation.
- Config option `spellAnimations` to turn those off.

### Changed
- Simply Swords is optional now. Without it, unique abilities and parrying are off and Simply Swords entries roll nothing.
- Bosses never cast spells that heal.
- Bosses don't cast held spells in the middle of one of their own moves.
- Loadout entries whose tag is empty get skipped instead of leaving the hand empty.

### Fixed
- Simply Swords and RPG Series weapons held by Juggernauts and Templars aren't scaled up anymore. Their models are already big, so they looked huge.
- A boss that cast Death Knights' Remorseless Winter crashed when the effect ran out. It works on bosses now.
- Bosses saved while petrified or downed came back with their AI off for good. Ones already stuck from an older version stay stuck, run `/execute as @e[type=!player,nbt={NoAI:1b},distance=..30] run data merge entity @s {NoAI:0b}` near them to wake them up.
- Bosses kept their name, down state and weapon across a reload only by luck. Now they're saved, so a boss doesn't get re-petrified, renamed or re-armed every time its chunk loads, and a downed boss can still be recruited after a relog.

## [1.1.0] - 2026-11-19

### Added
- Bosses pick what they hold from datapack loadouts in `data/rpg-minibosses/miniboss_loadouts/<entity>.json`. The README has the format.
- Bosses use the unique abilities on Simply Swords weapons they hold, and cast Spell Engine spells from their items or from the loadout entry.
- Each entry can tune its ability with chance, interval, cooldown, range and health threshold settings, or turn it off with `enabled: false`.
- Config options `heldAbilities` and `abilityCooldownMultiplier`.

### Fixed
- Boss spells did no damage on dedicated servers. They only worked in singleplayer.
- Swords a boss drops come back dormant instead of fully awakened.

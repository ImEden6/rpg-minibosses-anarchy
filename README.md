# RPG Minibosses (ANARCHY fork)

Fork of cleannrooster's RPG Minibosses for Fabric 1.20.1.

## Building

```
gradlew.bat build
```

Needs Java 17+. Gradle pulls these from the Modrinth maven, versions live in `gradle.properties`:

- spell_engine `0.15.12+1.20.1` (older 0.15.x won't compile, `LootConfigV2` is missing)
- extraspellattributes `1.3.2`

Both are also required at runtime, `fabric.mod.json` asks for at least those versions.

Simply Swords is optional. Without it, bosses skip unique abilities and parrying, and Simply Swords entries in loadouts roll nothing. `gradlew.bat runGametest -PnoSimplySwords` drops it from the dev runtime if you want to check that, though the Simply Swords tests will crash without it.

## Loadouts

What each miniboss holds comes from datapack JSON in `data/rpg-minibosses/miniboss_loadouts/<entity>.json`. The file name is the entity id, so `templar.json` is for `rpg-minibosses:templar`. To change one in a datapack, put a file at the same path and it replaces the built-in one.

Entity ids: `templar`, `juggernaut`, `trickster`, `mercenary`, `archmage_fire`, and the `minor_` version of each. Magus Prime doesn't use loadouts.

```json
{
  "mainhand": [
    { "item": "simplyswords:stormbringer", "weight": 2, "drop_chance": 0.05 },
    {
      "tag": "mypack:boss_swords",
      "weight": 5,
      "ability": { "max_range": 4, "chance": 0.5 }
    },
    { "item": "minecraft:air", "weight": 3 }
  ],
  "offhand": [
    {
      "item": "minecraft:blaze_rod",
      "spells": ["rpg-minibosses:fireball"],
      "ability": { "cooldown": 100, "min_range": 4, "max_range": 16, "below_health": 0.5 }
    }
  ]
}
```

On spawn the boss picks one entry per hand, weighted by `weight`. An `air` entry leaves that hand empty. If the mainhand ends up empty, the boss falls back to its normal weapon.

The mercenary only takes bows and crossbows in its mainhand, modded ones included. Anything else rolls back to its plain crossbow. Bows use their Power, Punch and Flame enchantments, crossbows use Piercing, and bows from Ranged Weapon API mods (Archers and the like) keep their damage.

Entry fields, all optional except `item` or `tag`:

| field | default | what it does |
|---|---|---|
| `item` | | item id |
| `tag` | | item tag, a random item from it gets picked. Use this or `item`, not both |
| `weight` | `1` | odds of this entry against the others in the same hand |
| `awakening` | `8` | Simply Swords awakening level the boss gets. 8 unlocks every unique ability |
| `drop_chance` | config `equipmentDropChance` | chance to drop on death. Dropped items always come back at awakening 0 |
| `nbt` | | SNBT string merged onto the item, like `"{display:{Name:'\"Bob\"'}}"` |
| `spells` | the item's own spells | Spell Engine spell ids to cast. One gets picked at random each time |
| `spell_pool` | | Spell Engine pool id, or a list of them, like `"wizards:fire"`. Every spell in the pool gets added to the ones above |
| `ability` | see below | when the held ability fires |

`ability` fields:

| field | default | what it does |
|---|---|---|
| `enabled` | `true` | set `false` and the boss never uses the ability, Stormbringer's parry included |
| `cooldown` | the Simply Swords or spell cooldown | ticks before it can fire again |
| `min_range` | `0` | target has to be at least this far away |
| `max_range` | `10` | and no farther than this |
| `chance` | `0.25` | chance to fire each time it gets checked |
| `below_health` | `1.0` | only fires when the boss is at or under this fraction of max health |
| `interval` | `10` | how many ticks between checks |

Simply Swords uniques use their own active ability. Anything else casts from `spells`, or from the Spell Engine spells on the item if `spells` is empty, plus whatever's in `spell_pool`. Pools are read live, so a pack that edits `data/<mod>/spell_pools/` changes what the bosses cast too. A spell book with no spells of its own uses its pool. Items with no spells at all don't do anything special.

Bosses never cast a spell that heals, even if it also does damage, so a priest staff only gets its attack spells.

Archery spells (`SHOOT_ARROW` ones like Barrage, and bow ones like Power Shot) only work on the mercenary. It fires an extra arrow from whatever it's holding, and the arrow carries the spell, so its perks and on-hit effects work like they do for players. Other bosses skip them.

An entry whose tag ends up empty, like when the mod behind it isn't installed, gets skipped when rolling.

### Built-in loadouts

Every adventurer except Magus Prime rolls a weapon from an RPG Series class, and casts that class's spell pool with it. The classes are item tags in `data/rpg-minibosses/tags/items/loadout/`, every entry `required: false`, so whatever mods you have fill them in. Lesser bosses use the `_lesser` tags (wood up to diamond), greater ones the `_greater` tags (diamond and up) plus Simply Swords uniques and Spellblade weapons.

| tag | weapons | spell_pool |
|---|---|---|
| `rogue_*` | Rogues daggers and sickles, Simply Swords rapiers, sais, cutlasses, katanas, twinblades | `rogues:rogue` |
| `warrior_*` | Rogues double axes and glaives, Archers spears, Simply Swords greataxes, glaives, halberds, warglaives, spears | `rogues:warrior` |
| `paladin_*` | Paladins claymores, great hammers, maces, Simply Swords claymores, greathammers, longswords | `paladins:paladin` |
| `priest_*` | Paladins holy wands and staffs | `paladins:priest` |
| `wizard_<school>_*` | Wizards wands and staffs of that school | `wizards:<school>` |
| `death_knight_*` | Death Knights runeblades and runeaxes | `death_knights:blood`, `frost`, `unholy` |
| `battlemage_<school>` | Spellblade blades, claymores and orbs | `spellbladenext:<school>_battlemage` |
| `archer_*` | bows and crossbows, Archers longbows, shortbows, heavy and rapid crossbows | `archers:archer` |
| `uniques_heavy`, `uniques_light` | Simply Swords uniques | their own ability |

| boss | rolls from |
|---|---|
| juggernaut | warrior, paladin, death knight, heavy uniques |
| templar | paladin, priest, death knight, battlemage, heavy uniques |
| trickster | rogue, battlemage, light uniques |
| archmage_fire | fire wizard mostly, then arcane and frost wizard, priest, battlemage |
| mercenary | archer, greater ones sometimes with a Deadeye, Tundra Hunter or War Archer pool from Archers Expansion on top |

Each one keeps its old vanilla weapon as a low-weight fallback, which is also all it gets if none of the class mods are installed. To change the weapons in a class, override the tag. To change who gets which class, override the loadout file.

Stormbringer also parries when hit, and a parry puts the same cooldown on it.

Three server config options cover all of this. `heldAbilities` turns held abilities off completely. `abilityCooldownMultiplier` scales every cooldown, so `0.5` brings abilities back twice as fast. `spellAnimations` off makes bosses cast held spells right away, with none of their own swings, waves or slams and no wait for the hit frame.

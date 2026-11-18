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
| `ability` | see below | when the held ability fires |

`ability` fields:

| field | default | what it does |
|---|---|---|
| `enabled` | `true` | set `false` and the boss never uses it on its own. Stormbringer still parries |
| `cooldown` | the Simply Swords or spell cooldown | ticks before it can fire again |
| `min_range` | `0` | target has to be at least this far away |
| `max_range` | `10` | and no farther than this |
| `chance` | `0.25` | chance to fire each time it gets checked |
| `below_health` | `1.0` | only fires when the boss is at or under this fraction of max health |
| `interval` | `10` | how many ticks between checks |

Simply Swords uniques use their own active ability. Anything else casts from `spells`, or from the Spell Engine spells on the item if `spells` is empty. Items with neither don't do anything special.

Stormbringer still parries on its own, and a parry puts the same cooldown on it.

Two server config options cover all of this. `heldAbilities` turns held abilities off completely. `abilityCooldownMultiplier` scales every cooldown, so `0.5` brings abilities back twice as fast.

# fork changes

Recovered by diffing `rpg-minibosses-1.0.0+1.20.1.jar` (fork) against this source built as-is
(`rpg-minibosses-1.9.1+1.20.1.jar`), both decompiled with Vineflower 1.12.0.

Mod id `rpg-minibosses`, entrypoints `com.cleannrooster.rpg_minibosses.RPGMinibosses` (main) and
`com.cleannrooster.rpg_minibosses.RPGMinibossesClient` (client). No shaded libraries. Entrypoints and
dependencies in fabric.mod.json are unchanged. No access widener in either jar.

## 1. config options (ServerConfig)

New fields, appended after the existing ones:

| field | type | default |
|---|---|---|
| magusFrostSchoolPower | float | 0 |
| followRange | float | 32 |
| mercenaryLesserFollowRange | float | 50 |
| experience | int | 100 |
| magusExperience | int | 500 |
| fallDamageImmune | boolean | true |
| despawnWhenFarAway | boolean | true |
| lavosHornDropChanceGreater | float | 0 |
| lavosHornDropChanceLesser | float | 0 |
| equipmentDropChance | float | 0 |
| lesserNaturalSpawns | boolean | true |
| greaterNaturalSpawns | boolean | false |
| spawnInWitchBiomes | boolean | true |
| extraSpawnBiomes | List<String> | empty |
| excludedSpawnBiomes | List<String> | empty |
| spawnGroupMin | int | 1 |
| spawnGroupMax | int | 1 |
| spawnRequiresDarkness | boolean | true |
| patrolChance | double | 0.2 |
| patrolDaytimeOnly | boolean | true |
| patrolMinDistance | int | 24 |
| patrolMaxDistance | int | 48 |
| patrolExtraMembers | int | 1 |
| patrolAvoidVillages | boolean | true |
| infamyPerKill | int | 5 |
| infamyPerProvoke | int | 1 |
| infamyThreshold | int | 5 |

Comment-only changes on existing fields: Lesser Rogues spawn chance, lesser/greater scale
("no effect on 1.20.1"), magusFrostPower (actually Arcane), magusArcanePower (unused), mult
(natural spawn weight). Comment text taken verbatim from the jar.

## 2. entity attributes (RPGMinibossesEntities)

- every hardcoded follow range `32.0` now `config.followRange`
- lesser mercenary follow range used `config.mercenaryLesserMaxHealth`, now `config.mercenaryLesserFollowRange`
- one builder's max health `100.0` now `config.templarMaxHealth`
- Magus attribute builder wrapped in `withMagusFrost(...)`, adds `SpellSchools.FROST.attribute` when
  `magusFrostSchoolPower != 0`

## 3. natural spawning (RPGMinibossesEntities)

- `entry.shouldSpawn` check replaced by `spawnsNaturally(entry)`: lesser uses `lesserNaturalSpawns`,
  greater uses `greaterNaturalSpawns`, Magus Prime never
- `addSpawn` only when `config.mult > 0`, biomes from `spawnBiomes()` (witch biomes if enabled, plus
  `extraSpawnBiomes`, minus `excludedSpawnBiomes`; ids or `#tags`, invalid ones logged and ignored),
  group size from `spawnGroupMin`/`spawnGroupMax` clamped to at least 1
- `minibosses.add(entry)` stays inside the `spawnsNaturally` branch (patrol pool)

## 4. miniboss behaviour (MinibossEntity, MagusPrimeEntity)

- experience `100` now `config.experience`, Magus `500` now `config.magusExperience`
- infamy per kill `+5` now `config.infamyPerKill`, per provoke `+1` now `config.infamyPerProvoke`
- hostility threshold `5 + benevolence` now `config.infamyThreshold + benevolence`
- equipment drop chance `100.0F` now `config.equipmentDropChance`
- fall damage immunity gated on `config.fallDamageImmune`
- `canImmediatelyDespawn` now `config.despawnWhenFarAway && !isTamed()`
- `canSpawn(WorldView)` first returns false when `random.nextFloat() >= spawnCoeff`
- static `canSpawn` darkness check skipped when `spawnRequiresDarkness` is false

## 5. patrols (Patrol)

- `nextInt(patrolAdded)` now `nextInt(Math.max(1, patrolAdded))`
- daytime check gated on `patrolDaytimeOnly`
- `random.nextInt(5) != 0` replaced by `random.nextFloat() < patrolChance`, and bail out when
  `minibosses` is empty
- village check gated on `patrolAvoidVillages`
- offsets from new `patrolOffset(random)` using `patrolMinDistance`/`patrolMaxDistance` (both spawn methods)
- extra members `+1` now `+ config.patrolExtraMembers` (both spawn methods)
- second spawn method also bails out when `minibosses` is empty

## 6. lavos horn drops (RPGMinibosses, loot tables)

- removed the lavos_horn pool from the 10 entity loot tables (archmage_fire, juggernaut, mercenary,
  templar, trickster and their minor_ versions), now `"pools": []`
- added `LootTableEvents.MODIFY` in `onInitialize`: for builtin tables matching an entry's loot table
  (except Magus Prime) add a pool with one lavos horn and `random_chance` of
  `lavosHornDropChanceLesser` or `lavosHornDropChanceGreater`, skipped when chance is 0

## 7. dead code and unused assets removed

- classes: `entity/Spawning`, `loot/ModLoot`, `mixin/DimensionEffectsMixin`, `mixin/ExampleMixin`,
  `mixin/PropertiesMixin`, `mixin/RenderSystemMixin` (all empty or commented out)
- `rpg-minibosses.mixins.json`: dropped DimensionEffectsMixin, PropertiesMixin, RenderSystemMixin
  (`refmap` key is added by loom at build time, not a source change)
- lang: removed gemini, guild_hall, juggernaut/trickster armor, gemini_fragment, tabula_rasa,
  abberraths_hooves, whispering_ice, abberrath_nova/ice_storm spells, armors item group
- assets deleted: gemini animations/geo/textures, abberraths_hooves, juggernaut/trickster armor
  models and textures, magus_prime.bbmodel, templarmob converted geo, thiefmodel, whispering_ice,
  tabula_rasa, gemini items, ice_storm spell texture, `data/rpg-minibosses/spell_old/` and `spells_old/`
  (none referenced by code)

## 8. misc

- `icon.png` replaced (taken from jar)
- version `1.0.0+1.20.1` in the jar vs `mod_version=1.9.1+1.20.1` here: set `mod_version` to match

## not recoverable / notes

- gradle.properties and build.gradle aren't in the jar. Only the version is known. The fork declares
  the same `spell_engine >=0.15.4`; LootConfigV2 needs spell_engine 0.15.12 to compile.
- upstream doesn't build without `run/mods/` (gitignored), which must hold `spell_engine` and
  `extraspellattributes` jars. Used spell_engine-0.15.12+1.20.1 and extraspellattributes-1.3.2.
- local variable names and exact formatting of new code are reconstructed; logic matches bytecode.

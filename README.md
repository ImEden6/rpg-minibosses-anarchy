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

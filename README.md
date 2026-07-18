# Anvilcraft: Plasticraft

Anvilcraft: Plasticraft is a standalone NeoForge addon for AnvilCraft. The
project currently targets Minecraft 1.21.1, NeoForge 21.1.219, and Java 21,
matching the latest remote `dev/1.21/1.6` baseline of AnvilCraft.

The first vertical slice contains:

- a white/light-gray plastic anvil item and Royal Anvil-inspired model;
- a persistent, hard-collision entity that can be pushed by other entities;
- right-click placement and right-click interaction with an entity-backed
  Royal Anvil menu;
- a temporary shapeless vanilla recipe: one snowball creates one plastic anvil;
- Registrum-backed registration and DataGen for models, blockstates,
  translations, tags, recipes, and advancements;
- optional JEI and Jade integrations, plus Ageratum manual pages in English and
  Simplified Chinese.

## Development

Open this directory directly in IntelliJ IDEA as an independent Gradle
project. The generated local run configurations are `Client`, `Data`,
`Server`, and `GameTestServer`; IDEA configuration files are ignored and are
not part of the Git repository.

```powershell
.\gradlew.bat runData
.\gradlew.bat build
.\gradlew.bat runClient
```

CI and normal builds use the published remote coordinate from
`gradle/libs.versions.toml`. For local IDEA runs, opt into a sibling build with
`-Puse_local_anvilcraft=true`, or point at one exact artifact with
`-Panvilcraft_jar=...`; this prevents an older local JAR from overriding the
remote baseline accidentally. The local run configurations in this checkout
were synchronized with the current sibling build. To refresh them after
rebuilding AnvilCraft, run:

```powershell
.\gradlew.bat neoForgeIdeSync -Puse_local_anvilcraft=true
.\gradlew.bat runClient -Puse_local_anvilcraft=true
```

## Compatibility

JEI, Jade, and Ageratum are optional at runtime. The addon does not require
those client mods to load, but registers recipe/catalyst and entity tooltip
information when the corresponding integration is present. Ageratum manual
source files live under `src/main/resources/assets/anvilcraftplasticraft/ageratum`.

## Migration notes

See [`AGENT.md`](AGENT.md) for the conventions used to keep the 1.21 code easy
to port when AnvilCraft moves to 26.1.

## License

Code is released under the GNU Lesser General Public License, version 3 or any
later version. See [`LICENSE`](LICENSE) and [`ASSETS_LICENSE`](ASSETS_LICENSE).

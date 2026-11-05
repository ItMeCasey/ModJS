# ModJS

KubeJS-style scripting for Forge and NeoForge, powered by Rhino. Supports Minecraft 26.3, 26.2, 26.1.2, 1.21.1 and 1.20.1 on both loaders.

## Layout

- `common/` - script engine, events, data generation (no Minecraft code). Shared by both modules and unit tested.
- `forge-26.3/`, `forge-26.2/`, `forge-26.1.2/` - Forge 26.x modules (ForgeGradle 7, Java 25, Gradle 9.3+).
- `forge-1.21.1/` - Forge 1.21.1 module (ForgeGradle 6, Java 21, Gradle 8.14).
- `forge-1.20.1/` - Forge 1.20.1 module (ForgeGradle 6, Java 17, Gradle 8.14).
- `neoforge-26.3/`, `neoforge-26.2/`, `neoforge-26.1.2/`, `neoforge-1.21.1/` - NeoForge modules (ModDevGradle).
- `neoforge-1.20.1/` - NeoForge 1.20.1 module (NeoGradle 7, Java 17); ship the `-all` jar.

`docs/PORTING.md` lists what each port needs and every known gap.

Each module is its own Gradle build. Run `./gradlew build` inside a module folder.

## Scripts

On first launch ModJS creates a `modjs/` folder in the game directory with `startup_scripts`, `server_scripts`,
`client_scripts`, `assets`, `data`, and `probe/modjs.d.ts` for editor completion. Use `/modjs reload` to re-run server scripts.

## Docs

- [docs/REFERENCE.md](docs/REFERENCE.md) - scripting reference
- [docs/PARITY.md](docs/PARITY.md) - what is and is not implemented compared with KubeJS
- [smoke-test/](smoke-test/) - scripts used to check both modules in a headless server

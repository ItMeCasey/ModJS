# ModJS

KubeJS-style scripting for Forge and NeoForge, powered by Rhino. Supports Minecraft 26.3, 26.2, 26.1.2, 1.21.1 and 1.20.1 on both loaders.

## Layout

- `common/` - script engine, events, data generation (no Minecraft code). Shared by all ten modules and unit tested.
- `forge-26.3/`, `forge-26.2/`, `forge-26.1.2/` - Forge 26.x modules (ForgeGradle 7, Java 25, Gradle 9.3+).
- `forge-1.21.1/` - Forge 1.21.1 module (ForgeGradle 6, Java 21, Gradle 8.14).
- `forge-1.20.1/` - Forge 1.20.1 module (ForgeGradle 6, Java 17, Gradle 8.14).
- `neoforge-26.3/`, `neoforge-26.2/`, `neoforge-26.1.2/`, `neoforge-1.21.1/` - NeoForge modules (ModDevGradle).
- `neoforge-1.20.1/` - NeoForge 1.20.1 module (NeoGradle 7, Java 17); the Forge 1.20.1 jar (converted to the names a real 1.20.1 game uses) is the one uploaded for both loaders.

`docs/PORTING.md` lists what each port needs and every known gap.

Each module is its own Gradle build. Run `./gradlew build` inside a module folder.

## Block and entity info

`InfoEvents` lets scripts add lines, bars and icons to the Show Me What This Is and Jade overlays (`common/info`, `compat/smwti`, `compat/jade`).
Show Me What This Is works on every build; Jade on NeoForge 26.3, 26.2, 26.1.2 and 1.21.1 (Jade 1.20.1 is compile-checked only; Forge 26.x and 1.21.1 have no Jade build).
Example: `examples/block-info`; screenshots from real clients: `docs/frames` (made by `scripts/info_tour.py`).

## Scripts

On first launch ModJS creates a `modjs/` folder in the game directory with `startup_scripts`, `server_scripts`,
`client_scripts`, `assets`, `data`, and `probe/modjs.d.ts` for editor completion. Use `/modjs reload` to re-run server scripts.

## Client scripting and recipe viewers

Client scripts can draw HUD text and rectangles, register keys that players can rebind in Controls, and keep an
overlay of progress bars, text, rectangles, pictures and item icons that they create once and update later
(`Overlay.bar('hp', { value: 80, max: 100 })`, with anchors, colors, alpha, smooth fills and animations). Buttons on the title
screen and on your own screens can carry an icon (an item or a texture). World overlays (`Overlay.at(x, y, z)`) draw text, item icons,
bars and textures in the world over a block, with distance and visibility culling and an element cap. Startup scripts can make blocks
with a different texture on every face, real slots, a tick handler, saved and synced data, and a screen with bars and buttons
(`examples/world-overlay`). On Forge 1.21.1 the scripted title screen is swapped in
by ModJS itself, because Forge 52.1.21 never delivers its screen-opening event.
Examples: `examples/client/client_scripts`. JEI, EMI and REI adapters support item hiding,
information pages and category hiding where each viewer supports the target loader and version;
see the [viewer matrix](docs/REFERENCE.md#recipe-viewers-jei-emi-and-rei).

## Scripts and Minecraft classes

Use ids first: `Item.of('minecraft:diamond')` and the ids in recipes, tags, loot and events work the same on every version. For what ModJS
has no function for, scripts can call Minecraft's own classes by their usual names (`Items.DIAMOND`, `server.getRecipeManager()`). A real
Minecraft 1.20.1 game only knows those under other internal names, so the 1.20.1 builds translate them with a table made from the build's
mappings (`scripts/gen_name_map.py`, `script/names/`). Checked in real Forge 47.4.28 and NeoForge 47.1.106 installs.

## Docs

- [docs/REFERENCE.md](docs/REFERENCE.md) - scripting reference
- [docs/PARITY.md](docs/PARITY.md) - what is and is not implemented compared with KubeJS
- [smoke-test/](smoke-test/) - scripts used to check both modules in a headless server

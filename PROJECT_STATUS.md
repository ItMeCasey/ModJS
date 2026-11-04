# Project Status
<!-- status-format: 1 -->

Updated: 2026-11-03
Summary: ModJS runs KubeJS-style scripts on Rhino in Forge 26.1.2 and 1.21.1, both built and smoke-tested in a real game-test server; gameplay events, client scripts and networking are untested in a running game.
Spend: none

## Foundation
- [x] Gradle projects: common/ holds shared code, forge-26.1.2 and forge-1.21.1 each build, pass tests and load in a game-test server (w:4)
- [x] Embed Rhino and load script folders (startup, server, client, data, assets) (w:4)
- [x] Script sandbox: class allow/deny filter and per-script error reporting to log and in-game (w:3)
- [~] Event system: priority, cancel, once/off, and JS-side listener registration (w:3)
- [~] `/modjs reload` command for script hot reload (w:2)

## Startup scripts
- [~] Registry events for items, blocks, fluids, block entities, entity types, and creative tabs (w:5)
- [~] Item and block builders: properties, tooltip, food, tool, armor, and block types (w:4)
- [x] Data generation: tags, loot tables, and recipes written as data pack output (w:3)
- [~] Asset generation: lang, models, blockstates, and textures via a generated resource pack (w:4)

## Server scripts: recipes and data
- [x] Recipe event: shaped, shapeless, smelting, blasting, smoking, campfire, smithing, and stonecutting (w:5)
- [x] Recipe remove and replace by id, output, input, and ingredient (w:3)
- [x] Raw JSON recipe escape hatch and custom recipe type support (w:2)
- [~] Tag events: add and remove entries for block, item, fluid, and entity type tags (w:3)
- [~] Loot table events: add and replace tables (modifying existing tables is not done) (w:3)

## Server scripts: world and entity events
- [~] Block events: placed, broken, right and left click (w:3)
- [~] Item events: right click, first right click, entity interact, tooltip, modification, food eaten, crafted, and smelted (w:3)
- [~] Player, entity, and level events: join and leave, spawn, death, hurt, chat, and tick (w:4)
- [~] Command registration event with a builder API (w:2)
- [x] Server scheduler: run after N ticks and repeating tasks (w:1)
- [~] Persistent data: global key-value stores saved as JSON (per-player and per-entity data not done) (w:2)
- [~] Worldgen: add ores and features, remove features, add and remove spawns via biome modifiers (w:4)

## Shared helpers
- [~] Global helpers: item and ingredient builders, text components, JSON IO, NBT, and server access (w:3)
- [~] Networking: one channel for script messages; builds and loads, sending not yet tested with a client (w:3)

## Client scripts
- [~] Client events: tooltip, key bindings, HUD overlay, and asset preload (w:3)
- [ ] Client screen and widget hooks (w:2)

## Compatibility and tooling
- [ ] Integrations with JEI or EMI and with popular mod recipe types such as Create (w:3)
- [~] Type definitions: a hand-written modjs.d.ts is copied to modjs/probe on launch (not generated from the code) (w:3)
- [ ] In-game error overlay at startup and on script error (w:2)

## Testing and release
- [~] Automated tests with a game-test harness and script fixtures (w:3)
- [~] Example scripts are written on first run; reference docs are not written (w:2)
- [ ] Parity checklist against the KubeJS feature list, reviewed per release (w:1)

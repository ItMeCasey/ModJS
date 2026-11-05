# Project Status
<!-- status-format: 1 -->

Updated: 2026-11-05
Summary: ModJS runs KubeJS-style scripts on Rhino on Forge and NeoForge for Minecraft 26.3, 26.2, 26.1.2, 1.21.1 and 1.20.1; all ten builds are built, self-tested in a headless server and built by CI, while client scripts and networking are untested on a real client.
Spend: none

## Foundation
- [x] Gradle projects: common/ holds shared code, forge-26.1.2 and forge-1.21.1 each build, pass tests and load in a game-test server (w:4)
- [x] Embed Rhino and load script folders (startup, server, client, data, assets) (w:4)
- [x] Script sandbox: class allow/deny filter and per-script error reporting to log and in-game (w:3)
- [x] Event system: priority, cancel, once/off, and JS-side listener registration (w:3)
- [~] `/modjs reload` command for script hot reload (w:2)
- [~] Item commands `/modjs hand`, `toolbar` and `inventory` list held item ids (registered, not yet run by a player) (w:1)

## Startup scripts
- [~] Registry events: items, blocks, creative tabs, fluids, tool tiers, armor materials and container block entities done; entity types left out on purpose (w:5)
- [~] Item and block builders: properties, tooltip, food, tool, armor, and block types (w:4)
- [x] Data generation: tags, loot tables, and recipes written as data pack output (w:3)
- [~] Asset generation: lang, models, blockstates, and textures via a generated resource pack (w:4)

## Server scripts: recipes and data
- [x] Recipe event: shaped, shapeless, smelting, blasting, smoking, campfire, smithing, and stonecutting (w:5)
- [x] Recipe remove and replace by id, output, input, and ingredient (w:3)
- [x] Raw JSON recipe escape hatch and custom recipe type support (w:2)
- [x] Tag events: add and remove entries for block, item, fluid, and entity type tags (w:3)
- [x] Loot table events: add, replace and edit tables (edits cover tables shipped in game and mod jars) (w:3)

## Server scripts: world and entity events
- [~] Block events: placed, broken, right and left click (broken checked in an in-game self test; the rest compile) (w:3)
- [~] Item events: right click, first right click, entity interact, tooltip, modification, food eaten, crafted, and smelted (w:3)
- [~] Player, entity, and level events: chat, login and hurt checked in-game; the rest compile (w:4)
- [~] Command registration event with a builder API (w:2)
- [x] Server scheduler: run after N ticks and repeating tasks (w:1)
- [x] Persistent data: global, per-player and per-entity key-value stores saved as JSON (w:2)
- [~] Worldgen: add ores and features, remove features, add and remove spawns via biome modifiers (w:4)
- [x] Item modification: change max stack size, max damage and rarity of existing items (checked on both versions) (w:2)
- [~] Dimensions: DimensionEvents (data pack) and run-time Dimensions.create/delete/teleport; levels self-tested headless, real-player teleport and restart persistence unchecked (w:4)
- [x] Block shapes (slab, stairs, wall, fence), vanilla-tier tools and armor, fluids, and modifyCreativeTab (add only); registered and self-tested, models not checked on a client (w:2)
- [x] GitHub Actions build runs both modules and the self test (passing) (w:1)

## Shared helpers
- [~] Global helpers: item and ingredient builders, text components, JSON IO, NBT, and server access (w:3)
- [~] Networking: one channel for script messages; builds and loads, sending not yet tested with a client (w:3)

## Client scripts
- [~] Client events: tooltip, key bindings, HUD overlay, and asset preload (w:3)
- [~] Client screens: scripted title screen, splash text and your own screens with buttons, text and text boxes (compiled, not seen on a client) (w:3)

## Compatibility and tooling
- [~] Recipe viewers: JEI on 1.21.1 (hide items, info pages, hide categories); EMI, REI and 26.1.2 blocked by missing Forge builds; Create recipe types not started (w:3)
- [x] Type definitions: a hand-written modjs.d.ts and jsconfig.json are written to modjs/ on launch (w:3)
- [x] Script errors shown in chat when operators join (server and startup scripts) and to players for client scripts; no separate overlay screen (w:2)
- [x] Item unification: canonical items, materials (ores, crushed, clumps, shards), fluids, recipe, loot and inventory unification, config file (both versions self-tested with fake mods, not real ones) (w:5)
- [x] Six ports built and self-tested: Forge 26.1.2, 1.21.1 and 1.20.1; NeoForge 26.1.2, 1.21.1 and 1.20.1 (w:5)
- [x] Minecraft 26.2 and 26.3 on Forge and NeoForge: four modules build, pass unit tests and 81 self-test checks in a headless game-test server; CI and CurseForge tables list them (dev server and client not run) (w:4)
- [x] Release gate: a tag uploads only builds whose own mod_version equals the tag; others are skipped and logged, and the run fails only when none match (checked with a local dry run of the collect step; not yet run in CI) (w:2)
- [x] Script info API (InfoEvents.block and entity: lines, bars, icons, hide for Jade) with unit tests, docs, wiki page and example (w:3)
- [~] Info bridges: SMWTI on all ten builds and Jade on the six that have one compile; seen on a real client (frames in docs/frames) on NeoForge 26.3, 26.2, 26.1.2 and 1.21.1 with both overlays and on NeoForge and Forge 1.20.1 with SMWTI; Forge 26.x and Forge 1.21.1 not seen yet (client stops on a screen before the server starts) (w:4)
- [x] NeoForge 26.x client load crashes fixed on 26.1.2, 26.2 and 26.3 (toast manager created before the game instance exists; missing client payload handler), seen on a real client; tab items no longer added twice on all ten builds (w:3)
- [ ] Close the per-version gaps in docs/PORTING.md: EMI, REI, JEI on 26.1.2, F3 lines and fluid art on NeoForge 26.1.2, item rarity changes on 1.20.1 (w:3)

## Testing and release
- [x] Automated tests: shared unit tests plus a game-test self test in both modules (w:3)
- [x] Example scripts are written on first run; docs/REFERENCE.md covers every event family (w:2)
- [x] Parity checklist against the KubeJS event list in docs/PARITY.md (w:1)
- [x] Wiki at itmecasey.com/wiki/modjs: 38 pages with examples, written and checked locally in the site folder (w:3)
- [x] Deploy the ModJS wiki to the droplet and check it live (w:1)
- [x] Logo, banners, section headers and CurseForge description in branding/ and curseforge/ (not yet pushed or uploaded) (w:2)
- [x] CurseForge project 1783874 created and 2026.11.24 beta uploaded by the release workflow (a v<version> tag builds, self-tests, makes a GitHub release and uploads) (w:3)
- [ ] Modrinth project and uploads (on hold) (w:2)
- [x] Command editing (add, remove, edit, alias, rename), Scoreboard global, toast hiding and editing, tutorial and experimental-warning switches; 20 new self-test checks and 12 unit tests pass on all six builds (client parts compiled, not seen on a client) (w:4)
- [x] World types (StartupEvents.worldTypes): add, edit, duplicate, hide, rename, drop; 9 new self-test checks pass on all six builds (button not seen on a client) (w:3)

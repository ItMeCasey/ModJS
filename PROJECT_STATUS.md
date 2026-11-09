# Project Status
<!-- status-format: 1 -->

Updated: 2026-11-08
Summary: Local 2026.11.30 candidate adds scripted HUDs, rebindable keys and native recipe-viewer adapters; all ten builds pass unit and headless tests, all sixteen supported viewer combinations pass real-client checks, and remaining development-environment limits are documented.
Spend: none

## Foundation
- [x] Gradle projects: common/ holds shared code, forge-26.1.2 and forge-1.21.1 each build, pass tests and load in a game-test server (w:4)
- [x] Embed Rhino and load script folders (startup, server, client, data, assets) (w:4)
- [x] Script sandbox: class allow/deny filter and per-script error reporting to log and in-game (w:3)
- [x] Event system: priority, cancel, once/off, and JS-side listener registration (w:3)
- [x] `/modjs reload` command for script hot reload: a script added while the game ran was picked up, seen on a real client on nine builds (Forge 1.21.1 dev client has no /modjs command, open) (w:2)
- [x] Item commands `/modjs hand`, `toolbar` and `inventory` run on a real client on nine builds with the expected output (Forge 1.21.1: command missing in the dev client, open) (w:1)

## Startup scripts
- [x] Registry events: items, blocks, creative tabs, fluids, tool tiers, armor materials and container block entities; one example script (examples/registry) with a unit test, seen on a real client on all ten builds; entity types left out on purpose (w:5)
- [x] Item and block builders: stack size, rarity, glow, food, unstackable, tooltip, tool, armor, light level, tool requirement, resistance, container and noItem blocks and the block types are self-tested on all ten builds (a new check set found and fixed a no-op glow and a noItem data error on 26.3) (w:4)
- [x] Data generation: tags, loot tables, and recipes written as data pack output (w:3)
- [x] Asset generation: lang, models, blockstates and textures via a generated resource pack: unit tests for each file kind, and the generated names, models and textures are visible in real-client frames on all ten builds (docs/frames/info-<build>-registry.png) (w:4)

## Server scripts: recipes and data
- [x] Recipe event: shaped, shapeless, smelting, blasting, smoking, campfire, smithing, and stonecutting (w:5)
- [x] Recipe remove and replace by id, output, input, and ingredient (w:3)
- [x] Raw JSON recipe escape hatch and custom recipe type support (w:2)
- [x] Tag events: add and remove entries for block, item, fluid, and entity type tags (w:3)
- [x] Loot table events: add, replace and edit tables (edits cover tables shipped in game and mod jars) (w:3)

## Server scripts: world and entity events
- [x] Block events: placed, broken, right click, left click and farmland trampled fire with correct data in a real client on all ten builds (examples/events/tour/events_check.js drives them through the server's packet handlers) (w:3)
- [x] Item events: right click, entity interact, dropped, picked up, can-pick-up, crafted, smelted and food eaten fire in a real client on all ten builds (crafted, smelted and food eaten are fired through the loader's event factory, because a client cancels a meal and no crafting menu is driven); tooltip events are now checked by constructing real item tooltips in each client tour (w:3)
- [x] Player, entity, and level events: login, respawn, death, advancement, inventory, chest, tick (player, server, level), loaded, hurt, death, spawned, check spawn and explosion events fire in a real client on nine builds; on Forge 1.21.1 the same events fire after a world opens (probed 2026-11-06) but the Forge 52.1.21 dev client never posts RegisterCommandsEvent and AddReloadListenerEvent to any listener, Forge's own included (see docs/PORTING.md); chat receipt, logged out and level unloaded are now verified through real client packets and clean exits on all ten builds (docs/testing/client-*.log) (w:4)
- [x] Command registration event with a builder API: add with typed arguments and number ranges, permissions, alias, edit, remove and rename are self-tested on all ten builds by running the commands through the dispatcher; `/modjs` commands ran on a real client on nine builds (Forge 1.21.1 dev client: see docs/PORTING.md) (w:2)
- [x] Server scheduler: run after N ticks and repeating tasks (w:1)
- [x] Persistent data: global, per-player and per-entity key-value stores saved as JSON (w:2)
- [x] Worldgen: add ores and features, remove features, add and remove spawns via biome modifiers; the self test generates 16 chunks and counts the script-added ore next to vanilla iron on all ten builds (flat game-test overworlds use a run-time dimension that copies the overworld), all passing (w:4)
- [x] Item modification: change max stack size, max damage and rarity of existing items (checked on both versions) (w:2)
- [x] Dimensions: DimensionEvents (data pack) and run-time Dimensions.create/delete/teleport; levels self-tested headless and a real client entered a run-time dimension on all ten builds (docs/frames/dim-<build>.png); two clean client launches per build verify restoration and a saved diamond block in the restored dimension (docs/testing/restart-*.log and restart-client-*.log) (w:4)
- [x] Block shapes (slab, stairs, wall, fence), vanilla-tier tools and armor, fluids, and modifyCreativeTab (add only); registered and self-tested, models not checked on a client (w:2)
- [x] GitHub Actions build runs both modules and the self test (passing) (w:1)

## Shared helpers
- [x] Global helpers: item and ingredient builders, text components, JSON IO and persistent values (unit tested from real scripts: round trip, path escape refused, values survive a reload), NBT (block entity SNBT read in the self test) and Text.of, translate and color helpers (self-test checks on all ten builds, all passing) (w:3)
- [x] Networking: one channel for script messages; a client-to-server ping and the server's reply to the client were proven in a real client on all ten builds (examples/network, log lines NET-CHECK); a dedicated server with a remote client was not run (w:3)

## Client scripts
- [x] Client events: tooltip and dynamic tooltip callbacks, asset preload, HUD text and rectangles, and native rebindable key mappings are verified in real clients on all ten builds; key rebinding and queued presses use Minecraft APIs without OS input (docs/testing/client-*.log and docs/frames/hud-*.png) (w:3)
- [~] Client screens: a scripted screen with text, buttons and a text box opens, draws and closes in a real client on all ten builds (frames info-<build>-screen.png); the scripted title screen and splash text were seen on the main menu on Forge 1.20.1 (frame docs/frames/title-forge-1.20.1.png; a logo bug there was fixed); also seen on NeoForge 1.20.1, NeoForge 1.21.1, Forge and NeoForge 26.1.2 and 26.2 (frames docs/frames/title-<build>.png); on Forge 1.21.1 the dev client shows the vanilla menu (see docs/PORTING.md); Forge and NeoForge 26.3 seen too (frames title-forge-26.3.png and title-neoforge-26.3.png), so all builds but Forge 1.21.1 are confirmed (w:3)

## Compatibility and tooling
- [x] Recipe viewers: native JEI, EMI and REI adapters implement item hiding, information pages and category hiding; all sixteen supported version/loader/viewer combinations pass real-client checks with information-page frames (docs/testing/2026.11.30.md); missing upstream loader targets and intentionally omitted custom categories/Create schemas are documented (w:3)
- [x] Type definitions: a hand-written modjs.d.ts and jsconfig.json are written to modjs/ on launch (w:3)
- [x] Script errors shown in chat when operators join (server and startup scripts) and to players for client scripts; no separate overlay screen (w:2)
- [x] Item unification: canonical items, materials (ores, crushed, clumps, shards), fluids, recipe, loot and inventory unification, config file (both versions self-tested with fake mods, not real ones) (w:5)
- [x] Six ports built and self-tested: Forge 26.1.2, 1.21.1 and 1.20.1; NeoForge 26.1.2, 1.21.1 and 1.20.1 (w:5)
- [x] Minecraft 26.2 and 26.3 on Forge and NeoForge: four modules build, pass unit tests and 81 self-test checks in a headless game-test server; real-client tours include scripted dimensions, screens and title menus (w:4)
- [x] Release gate: a tag uploads only builds whose own mod_version equals the tag; others are skipped and logged, and the run fails only when none match (checked with a local dry run of the collect step; not yet run in CI) (w:2)
- [x] Script info API (InfoEvents.block and entity: lines, bars, icons, hide for Jade) with unit tests, docs, wiki page and example (w:3)
- [~] Info bridges: SMWTI on all ten builds and Jade on the six that have one compile; seen on a real client (frames in docs/frames) on NeoForge 26.3, 26.2, 26.1.2 and 1.21.1 with both overlays and on NeoForge and Forge 1.20.1 with SMWTI; all four Forge 26.x and 1.21.1 builds seen too (SMWTI only, no Jade build) (w:4)
- [x] Forge 26.x and 1.21.1 clients could not open a world (the jar-shipped ore biome modifier flagged it experimental, a confirmation screen waited): modifier now generated only when Unify.ores() is on, seen on a real client on all four builds (w:4)
- [x] NeoForge 26.x client load crashes fixed on 26.1.2, 26.2 and 26.3 (toast manager created before the game instance exists; missing client payload handler), seen on a real client; tab items no longer added twice on all ten builds (w:3)
- [x] Release 2026.11.29 published as ten approved beta files through tag v2026.11.29; the GitHub release and upload workflow passed (w:2)
- [x] Script fluids on Forge and NeoForge 26.x drawn with tinted water (a fluid model) instead of the missing texture, seen on a real client on all six builds (w:2)
- [~] Per-version gaps: native viewers and rarity changes are complete and verified; F3 lines work on NeoForge 26.x; remaining Forge 1.21.1 development event/title-screen and Jade 1.20.1 development mixin limits are documented in docs/PORTING.md (w:3)

## Testing and release
- [x] Automated tests: shared unit tests plus a game-test self test in both modules (w:3)
- [x] Example scripts are written on first run; docs/REFERENCE.md covers every event family (w:2)
- [x] Parity checklist against the KubeJS event list in docs/PARITY.md (w:1)
- [x] Wiki at itmecasey.com/wiki/modjs: 38 pages with examples, written and checked locally in the site folder (w:3)
- [x] Deploy the ModJS wiki to the droplet and check it live (w:1)
- [x] Logo, banners, section headers and CurseForge description in branding/ and curseforge/ (not yet pushed or uploaded) (w:2)
- [x] CurseForge project 1783874 uses a tag-triggered beta release workflow; v2026.11.29 built, self-tested and published all ten approved files (w:3)
- [x] Modrinth project and uploads: frozen indefinitely by Casey (2026-11-06), nothing to do (w:2)
- [x] Command editing (add, remove, edit, alias, rename), Scoreboard global, toast hiding and editing, tutorial and experimental-warning switches; 20 new self-test checks and 12 unit tests pass on all six builds (client parts compiled, not seen on a client) (w:4)
- [x] World types (StartupEvents.worldTypes): add, edit, duplicate, hide, rename, drop; 9 new self-test checks pass on all six builds (button not seen on a client) (w:3)
- [x] Local 2026.11.30 validation: 87 unit tests per module, all ten headless self-tests, all ten HUD/key client checks, all ten two-launch dimension-restoration checks, and all sixteen supported native viewer checks pass; candidate remains unpublished pending approval (w:3)
- [x] BEI spot check with 2026.11.30 on Forge 26.1.2: all four viewer events including grouping pass, live index and group checks recorded; release collector assembles all ten jars locally without upload (w:1)

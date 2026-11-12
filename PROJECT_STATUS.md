# Project Status
<!-- status-format: 1 -->

Updated: 2026-11-11
Summary: 2026.11.32 local candidate (not pushed): adds world overlays (`Overlay.at`) and scripted blocks with real inventories, per-face textures, models, tick handlers and screens to all ten builds; tests, headless self tests and client frames pass on all ten builds, and the 1.20.1 jar was seen in real Forge and NeoForge installs.
Spend: none

## Foundation
- [x] Gradle projects: common/ holds shared code, forge-26.1.2 and forge-1.21.1 each build, pass tests and load in a game-test server (w:4)
- [x] Embed Rhino and load script folders (startup, server, client, data, assets) (w:4)
- [x] Script sandbox: class allow/deny filter and per-script error reporting to log and in-game (w:3)
- [x] Event system: priority, cancel, once/off, and JS-side listener registration (w:3)
- [x] `/modjs reload` command for script hot reload: a script added while the game ran was picked up, seen on a real client on nine builds (Forge 1.21.1 opened with Quick Play has no /modjs; opened from the title screen in a real Forge install it works, see docs/PORTING.md) (w:2)
- [x] Item commands `/modjs hand`, `toolbar` and `inventory` run on a real client on nine builds with the expected output (Forge 1.21.1: works when the world is opened from the menu, see docs/PORTING.md) (w:1)

## Startup scripts
- [x] Registry events: items, blocks, creative tabs, fluids, tool tiers, armor materials and container block entities; one example script (examples/registry) with a unit test, seen on a real client on all ten builds; entity types left out on purpose (w:5)
- [x] Item and block builders: stack size, rarity, glow, food, unstackable, tooltip, tool, armor, light level, tool requirement, resistance, container and noItem blocks and the block types are self-tested on all ten builds (a new check set found and fixed a no-op glow and a noItem data error on 26.3) (w:4)
- [x] Scripted blocks with real inventories and unique textures: `.textures({...})` per face, `.model(...)` (cube, column, bottom and top, orientable, your own model), `.inventory({ slots, stackLimit, values })` and `.entity()` with a block entity API (slots, tick handler, saved and synced data and numbers), `.onButton` and `.screen(...)` on the existing screen building blocks (plus `size` and `bar`); 4 unit tests, 12 headless checks (`SELFTEST DONE failures=0` on NeoForge 1.21.1) and screen frames on NeoForge 1.21.1, Forge 1.21.1, Forge 1.20.1, NeoForge 26.3 and Forge 26.3 (docs/frames/worldoverlay-*-screen.png) (w:5)
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
- [x] Player, entity, and level events: login, respawn, death, advancement, inventory, chest, tick (player, server, level), loaded, hurt, death, spawned, check spawn, explosion, chat, logged out and level unloaded events fire in a real client on all ten builds (Forge 1.21.1 opened with Quick Play drops events posted during startup, a Forge behavior that affects every mod; opened from the menu they fire, see docs/PORTING.md) (w:4)
- [x] Command registration event with a builder API: add with typed arguments and number ranges, permissions, alias, edit, remove and rename are self-tested on all ten builds by running the commands through the dispatcher; `/modjs` commands ran on a real client on nine builds (Forge 1.21.1: when the world is opened from the menu, proved in a real Forge install; see docs/PORTING.md) (w:2)
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
- [x] Client screens: a scripted screen with text, buttons and a text box opens, draws and closes in a real client on all ten builds (frames info-<build>-screen.png); the scripted title screen and splash text are seen on the main menu on all ten builds (frames docs/frames/title-<build>.png; Forge 1.21.1 swaps the screen in from ScreenEvent.Init.Pre because Forge drops the first Opening event, seen in a real Forge install) (w:3)
- [x] Overlay (retained bars, text, rectangles, gradients, pictures, item icons, anchors, colors, alpha, layers, animation) and pictures on buttons: 15 unit tests, all ten self tests pass, and a real-client run with frames on all ten builds (docs/frames/overlay-<build>-hud.png and -screen.png) (w:5)
- [x] World overlays: `Overlay.at(x, y, z)` draws text, item icons, bars and textures in the world over a block (faces, alpha, layers, animation, distance and chunk and facing culling, an element cap, data synced from scripted blocks, `Overlay.forBlock` templates); 15 unit tests, and frames from real clients on NeoForge 1.21.1, Forge 1.21.1, Forge 1.20.1, NeoForge 26.3 and Forge 26.3, which covers all four drawing paths (docs/frames/worldoverlay-*-world.png). Forge 26.x has no level-wide render hook upstream, so there overlays draw over ModJS block entities only (docs/PORTING.md) (w:5)

## Compatibility and tooling
- [x] Recipe viewers: native JEI, EMI and REI adapters implement item hiding, information pages and category hiding; all sixteen supported version/loader/viewer combinations pass real-client checks with information-page frames (docs/testing/2026.11.30.md); missing upstream loader targets and intentionally omitted custom categories/Create schemas are documented (w:3)
- [x] Type definitions: a hand-written modjs.d.ts and jsconfig.json are written to modjs/ on launch (w:3)
- [x] Script errors shown in chat when operators join (server and startup scripts) and to players for client scripts; no separate overlay screen (w:2)
- [x] Item unification: canonical items, materials (ores, crushed, clumps, shards), fluids, recipe, loot and inventory unification, config file (both versions self-tested with fake mods, not real ones) (w:5)
- [x] Six ports built and self-tested: Forge 26.1.2, 1.21.1 and 1.20.1; NeoForge 26.1.2, 1.21.1 and 1.20.1 (w:5)
- [x] Minecraft 26.2 and 26.3 on Forge and NeoForge: four modules build, pass unit tests and 81 self-test checks in a headless game-test server; real-client tours include scripted dimensions, screens and title menus (w:4)
- [x] Release gate: a tag uploads only builds whose own mod_version equals the tag; others are skipped and logged, and the run fails only when none match (checked with a local dry run of the collect step; not yet run in CI) (w:2)
- [x] Script info API (InfoEvents.block and entity: lines, bars, icons, hide for Jade) with unit tests, docs, wiki page and example (w:3)
- [x] Info bridges: SMWTI on all ten builds and Jade on the six that have one compile; seen on a real client (frames in docs/frames) on NeoForge 26.3, 26.2, 26.1.2 and 1.21.1 with both overlays, on all four Forge 26.x and 1.21.1 builds with SMWTI (no Jade build there), and with Jade 11.13.3 in real Forge 47.4.28 and NeoForge 47.1.106 1.20.1 installs (docs/frames/info-<loader>-1.20.1-real-<scene>.png: lamp, crate, furnace, cow with ModJS's lines) (w:4)
- [x] Forge 26.x and 1.21.1 clients could not open a world (the jar-shipped ore biome modifier flagged it experimental, a confirmation screen waited): modifier now generated only when Unify.ores() is on, seen on a real client on all four builds (w:4)
- [x] NeoForge 26.x client load crashes fixed on 26.1.2, 26.2 and 26.3 (toast manager created before the game instance exists; missing client payload handler), seen on a real client; tab items no longer added twice on all ten builds (w:3)
- [x] Release 2026.11.29 published as ten approved beta files through tag v2026.11.29; the GitHub release and upload workflow passed (w:2)
- [x] Script fluids on Forge and NeoForge 26.x drawn with tinted water (a fluid model) instead of the missing texture, seen on a real client on all six builds (w:2)
- [x] Per-version gaps: native viewers, rarity, F3 lines and the Forge 1.21.1 startup events are done and verified; what remains is outside our control: EMI and REI have no release to compile against for the other builds, and Jade's 1.20.1 jar does not work in the dev client (see docs/PORTING.md) (w:3)

## Testing and release
- [x] Automated tests: shared unit tests plus a game-test self test in both modules (w:3)
- [x] Example scripts are written on first run; docs/REFERENCE.md covers every event family (w:2)
- [x] Parity checklist against the KubeJS event list in docs/PARITY.md (w:1)
- [x] Wiki at itmecasey.com/wiki/modjs: 40 pages (hud and key-bindings pages added for 2026.11.30, deployed and checked live 2026-11-09) with examples, written and checked locally in the site folder (w:3)
- [x] Deploy the ModJS wiki to the droplet and check it live (w:1)
- [x] Logo, banners, section headers and CurseForge description in branding/ and curseforge/ (not yet pushed or uploaded) (w:2)
- [x] CurseForge project 1783874 uses a tag-triggered beta release workflow; v2026.11.29 built, self-tested and published all ten approved files (w:3)
- [x] Modrinth project and uploads: frozen indefinitely by Casey (2026-11-06), nothing to do (w:2)
- [x] Command editing (add, remove, edit, alias, rename), Scoreboard global, toast hiding and editing, tutorial and experimental-warning switches; 20 new self-test checks and 12 unit tests pass on all six builds (client parts compiled, not seen on a client) (w:4)
- [x] World types (StartupEvents.worldTypes): add, edit, duplicate, hide, rename, drop; 9 new self-test checks pass on all six builds (button not seen on a client) (w:3)
- [x] Local 2026.11.30 validation: 87 unit tests per module, all ten headless self-tests, all ten HUD/key client checks, all ten two-launch dimension-restoration checks, and all sixteen supported native viewer checks pass; candidate remains unpublished pending approval (w:3)
- [x] BEI spot check with 2026.11.30 on Forge 26.1.2: all four viewer events including grouping pass, live index and group checks recorded; release collector assembles all ten jars locally without upload (w:1)
- [x] 2026.11.32 on the other five builds: client tours with world and screen frames and the headless self test (12 scripted-block checks, `SELFTEST DONE failures=0`) pass on NeoForge 1.20.1, NeoForge 26.1.2, NeoForge 26.2, Forge 26.1.2 and Forge 26.2 (docs/frames/worldoverlay-*, docs/testing/selftest-*.log) (w:2)
- [x] The 2026.11.32 Forge 1.20.1 jar loads in a real Forge 47.4.28 install and a real NeoForge 47.1.106 install: world overlays and the scripted smelter with its screen seen in frames (docs/frames/worldoverlay-real-forge-1.20.1-*.png, worldoverlay-real-neo-1.20.1-*.png) (w:1)
- [ ] Deploy the new wiki pages world-overlays and scripted-blocks (drafted in the site folder, not deployed) (w:1)

## Planned
- [x] Name translation for scripts on Forge and NeoForge 1.20.1: a table made from the build mappings (scripts/gen_name_map.py, 6536 classes) lets scripts call Minecraft classes and methods by their usual names in a normal 1.20.1 game; 6 unit tests, and `Items.DIAMOND`, `server.getRecipeManager()`, `Commands.literal`, `player.health` ran in real Forge 47.4.28 and NeoForge 47.1.106 installs (examples/events/tour/names_check.js); all ten development-game self tests still pass (w:4)
- [x] 1.20.1 jars load in a normal game: the Forge jar is reobfuscated to the names a real game uses and uploaded for both loaders; seen loading, firing events and applying recipe changes in real Forge 47.4.28 and NeoForge 47.1.106 installs (w:4)

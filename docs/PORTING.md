# Porting ModJS to more versions

ModJS ships Forge and NeoForge for 1.20.1, 1.21.1, 26.1.2, 26.2 and 26.3. This file lists
what a new port has to provide and every gap that exists because a version could not do something yet.

## Ports so far

| Module | Minecraft | Loader | Status |
|---|---|---|---|
| `forge-26.3` | 26.3 | Forge 66.1.5 | build, 87 unit tests and 91 headless self-test checks pass; client evidence in docs/testing/2026.11.30.md |
| `neoforge-26.3` | 26.3 | NeoForge 26.3.0.101 | build, 87 unit tests and 92 headless self-test checks pass; client evidence in docs/testing/2026.11.30.md |
| `forge-26.2` | 26.2 | Forge 65.1.6 | build, 87 unit tests and 91 headless self-test checks pass; client evidence in docs/testing/2026.11.30.md |
| `neoforge-26.2` | 26.2 | NeoForge 26.2.0.93 | build, 87 unit tests and 92 headless self-test checks pass; client evidence in docs/testing/2026.11.30.md |
| `forge-26.1.2` | 26.1.2 | Forge 64.1.6 | build, 87 unit tests and 91 headless self-test checks pass; client evidence in docs/testing/2026.11.30.md |
| `forge-1.21.1` | 1.21.1 | Forge 52.1.21 | build, 87 unit tests and 91 headless self-test checks pass; client evidence in docs/testing/2026.11.30.md |
| `forge-1.20.1` | 1.20.1 | Forge 47.4.28 | build, 87 unit tests and 94 headless self-test checks pass; client evidence in docs/testing/2026.11.30.md |
| `neoforge-1.20.1` | 1.20.1 | NeoForge 47.1.106 | build, 87 unit tests and 94 headless self-test checks pass; client evidence in docs/testing/2026.11.30.md |
| `neoforge-1.21.1` | 1.21.1 | NeoForge 21.1.265 | build, 87 unit tests and 91 headless self-test checks pass; client evidence in docs/testing/2026.11.30.md |
| `neoforge-26.1.2` | 26.1.2 | NeoForge 26.1.2.125 | build, 87 unit tests and 92 headless self-test checks pass; client evidence in docs/testing/2026.11.30.md |

The 1.20.1 module copies the Forge 1.21.1 one and adapts to the older game: plural data folders and `item`/string recipe results (`DataFolders`, `Legacy120Format`), `FriendlyByteBuf` messages on a `SimpleChannel`, the old `Tier` and `ArmorMaterial` interfaces, `RecipeManager.fromJson`, reflection for item stack size and durability, `Codec` instead of `MapCodec`, and Rhino through `minecraftLibrary`. Its self test needs a connected test player (an `EmbeddedChannel`) because Forge reacts to logins.

NeoForge 1.20.1 (47.1) still uses the `net.minecraftforge` packages and the `forge` ids, so its sources are the Forge 1.20.1 ones unchanged; only the build differs (NeoGradle 7, Rhino through a `library` configuration added to each run, the `-all` jar from Jar-in-Jar).

The 26.2 modules copy the 26.1.2 ones and the 26.3 modules copy the 26.2 ones. 26.2 moved screen handling to `Minecraft.gui`, replaced `ChatFormatting` colors with `TextColor` and `TeamColor`, and (NeoForge) renamed `ModListScreen` and dropped `EntityInteractSpecific`. 26.3 turned ore generation into the new `Feature` model (one `worldgen/feature` registry, ores read through `AbstractOreFeature`), made composter chances an item component (`ServerEvents.compostableRecipes` snaps a chance to the nearest vanilla one: 0.3, 0.5, 0.65, 0.85 or always one layer), changed the recipe registry, pack supplier and loot and biome modifier signatures, and renamed `Name` to `id` in block states and `minCount`/`maxCount` to `count` in spawner entries (the shared code writes the right format for the running version). Forge 26.3 ships its own `modjs:remove_spawns` because the stock `forge:remove_spawns` crashes on biomes without spawns in a category.

The NeoForge 1.21.1 and 26.1.2 modules copy the Forge 1.21.1 one and changes only loader API names: events on `NeoForge.EVENT_BUS` with `net.neoforged` packages, `PlayerTickEvent.Post` and friends for ticks, `LivingIncomingDamageEvent` for hurt,
`ItemEntityPickupEvent.Pre`, `ModifyDefaultComponentsEvent` for item changes, payload handlers instead of a channel for networking, `DeferredHolder` instead of `RegistryObject`, `BaseFlowingFluid`,
and `neoforge:` ids and data folders (`LoaderNames`). Build files use ModDevGradle with Gradle 9.3.1, Rhino on `additionalRuntimeClasspath` and as Jar-in-Jar.
On NeoForge 26.1.2, 26.2 and 26.3 `ClientEvents.leftDebugInfo` and `rightDebugInfo` go through a `RegisterDebugEntriesEvent` entry (`client/ScriptDebugEntries`): the loader has no F3 text event any more, so both events show as one block of lines, left ones first (seen on a client on 26.1.2 and 26.3, frames docs/frames/f3-neoforge-26.1.2.png and f3-neoforge-26.3.png; 26.2 shares the code and compiles). The entry is switched on when a player joins a world, because saved debug screen settings do not know it. Script fluids use tinted water fluid models, verified in real clients on all six 26.x builds. Native JEI adapters are available on NeoForge 26.1.2, 26.2 and 26.3. Block breaking uses `BreakBlockEvent`, reload listeners `AddServerReloadListenersEvent`, and the client can only send with `ClientPacketDistributor`.
On NeoForge 1.21.1 fluid client art still uses the deprecated `FluidType.initializeClient`; move it to `RegisterClientExtensionsEvent` when a later NeoForge removes it.

## Optional recipe viewers

The [reference matrix](REFERENCE.md#recipe-viewers-jei-emi-and-rei) lists native adapters.
Forge 26.x has no JEI, EMI or REI adapter because those viewer builds target other loaders;
Forge 1.21.1 has JEI, while EMI and REI use NeoForge on that Minecraft version.
Each adapter is discovered by its viewer, with compile-only API dependencies, so absent viewers
never load the adapter classes. BEI consumes the shared script events through its own bridge.

Development profiles `-PjeiTour`, `-PemiTour` and `-PreiTour` install one viewer for a client check.
NeoForge 1.20.1 uses the same Forge sources; its NeoGradle 7 development runtime needs the
Mojang-named optional mod jars produced by running the corresponding Forge 1.20.1 profile first.
The build resolves those opt-in files from the local Gradle cache and fails clearly if missing.
The profiles also remap viewer access-transformer rules for the Mojang-named development runtime.
Ordinary builds and shipped jars do not depend on those cached development artifacts or include the dev-only rules.
Forge/NeoForge 1.20.1 EMI and REI profiles also remap their SRG mixin refmaps in development.

## Client and restart verification

`python scripts/client_tour.py <module> [--jei|--emi|--rei]` checks tooltip callbacks, raw loader
input, rebindable keys, HUD rendering, client events, chat receipt, logout and level unload.
`python scripts/restart_tour.py <module>` creates a runtime dimension, saves a diamond block,
exits the client cleanly, relaunches and verifies the restored dimension and block.
`client_tour.py --restart` combines both stages. Logs are retained in `docs/testing` and frames
in `docs/frames`. Checks call game and loader APIs inside the client; they never send OS input.

Copied worlds are prepared before launch: Peaceful difficulty, mob spawning disabled, special
spawn sources disabled, and a fresh player for restart/client tours. Modern copied gamerules
exclude optional-mod entries because the isolated tour removes optional mods; stale registry keys
would otherwise make Minecraft discard the saved rules. Test helpers preserve scripts and mods
in a `finally` block and refuse to overwrite an existing restart/client test world.

## What a port provides

`common/` holds everything free of Minecraft classes (scripting, events, recipes, tags, loot, data and asset
generation, dimensions' JSON, title screen description). A port is a new module next to `forge-1.21.1/` that:

1. Adds `common/src/main/java` as a source dir and installs a `VersionFormat` (ingredient and result JSON, item
   definitions) for its Minecraft version.
2. Bridges platform events to the script event groups (`game/GameEvents`) and the registries (`registry/ContentRegistry`,
   `FluidRegistry`).
3. Provides the platform globals: `Utils`, `Text`, `Network`, `GameRule`, `Dimensions`, and the `/modjs` commands.
4. Adds the generated resource and data packs (`pack/ModJSPacks`), and the recipe reload hook.
5. Copies the self test (`game/SelfTest`) and runs `./gradlew runGameTestServer` until it prints `failures=0`.

Look at `forge-1.21.1/` for the older API shape and `forge-26.1.2/` for the newer one; most differences are renames
(`ResourceLocation` to `Identifier`), event buses (`MinecraftForge.EVENT_BUS` to per-event `BUS`), and rendering
(`GuiGraphics` to `GuiGraphicsExtractor`).

## Block and entity info

`InfoEvents` (shared in `common/info`) feeds two optional overlays. `compat/smwti/ModJSSmwtiPlugin` and `compat/jade/ModJSJadePlugin` are found by their annotations,
so they load only when SMWTI or Jade is installed; the SMWTI API jars are compile-only in `libs/` and Jade is compile-only from CurseForge. `info/InfoBridge` builds the
events and moves lines from server to client for Jade. The per-version files are generated from one template, so change all ten together.

## Known gaps to close when porting

| Area | Gap | Why |
|---|---|---|
| Recipe viewers | Create recipe types: not planned | Create has no build for most supported versions and its recipe types are a large separate API. |
| Recipe viewers | `hideItems` and `hideCategories` not seen in a client | BEI's JEI bridge (2026.11.8) feeds ModJS's plugin `registerRecipes` (info pages work) but never calls `onRuntimeAvailable`; real JEI on 1.21.1 or 1.20.1 has not been run in a client. |
| Recipe viewers | KubeJS events beyond hide items, add information, hide categories | Not built (custom categories, hiding single recipes, and so on). |
| Scripts | On 26.1.2 `Block` and `BlockState` are replaced by stand-ins (`BlockFacades`) | The script engine would inspect client-only classes and crash a dedicated server. Check whether each new version needs the same. |
| Title screen | Vanilla panorama background only on 1.21.1 | `PanoramaRenderer` was removed in 26.1.2. |
| Block info | Jade plugin missing on Forge 26.1.2, 26.2, 26.3 and Forge 1.21.1 | Jade publishes no Forge build for those versions (checked 2026-11-05 on CurseForge). `compat/jade/` is copied from the NeoForge module when one appears. |
| Block info | `hideTag` only works in Jade | SMWTI's API only lets a mod add lines, not remove another mod's. |
| Block info | Jade shows a bar as a row of blocks | Jade's bar element differs between its 1.20.1, 1.21.1 and 26.x versions. |
| Forge 26.x and 1.21.1 | A biome modifier shipped in the mod jar makes every world "use experimental settings" | The world then waits on a vanilla confirmation screen. ModJS therefore writes its ore-swap modifier into the generated data pack only while `Unify.ores()` is on (`UnifyOreData`); with that on, players see the screen once per world. Forge 1.20.1 and NeoForge are not affected. |
| Block info | Jade on 1.20.1 has no client frame | Jade's 1.20.1 jar uses SRG names and its mixins fail in the Mojang-named dev game; compile-checked only. |
| Block info | Entity `nbt` is empty | Saving an entity changed on 26.x (value output), so scripts read `event.entity` instead. |
| Forge 1.21.1 | Quick Play (`--quickPlaySingleplayer`, used by the dev client tours) opens the world and the first title screen before Forge starts its event bus, so every event posted then is dropped for every mod, Forge's own included: `RegisterCommandsEvent` (no `/modjs`, no script commands), `AddReloadListenerEvent` (ModJS's recipe hook and Forge's loot modifier manager, which throws "Can not retrieve LootModifierManager" on a player death) and `ScreenEvent.Opening` for the first title screen | Proved on 2026-11-09 in a real Forge 1.21.1 install built with Forge's own installer. Opened with Quick Play, `/modjs` was missing and `ModJS recipes:` was absent from the log. The same install opening the same world from the title screen (`createWorldOpenFlows().openWorld` called from a script) logged `PROBE commandRegistry` and `ModJS recipes: 1292 loaded, 3 removed`, and `/modjs` existed. So a player who clicks Singleplayer is not affected. ModJS only works around the first title screen: it swaps its scripted title screen in from `ScreenEvent.Init.Pre` (frame docs/frames/title-forge-1.21.1-real-install.png). Tests on Forge 1.21.1 that need commands or recipes must open the world from the menu, not with Quick Play. |
| Worldgen | The chunk-generation self-test check runs in a run-time dimension that copies the overworld on builds whose game-test overworld is flat (Forge 1.21.1, 26.x and NeoForge 1.21.1) | A flat world places no features. The test world is saved between runs, so the check uses random far-away chunks. NeoForge 1.21.1's headless self test also fails if `run/mods` holds Jade (NPE in Jade's player join handler); move the jar away first (do not rename the whole `mods` folder: Gradle recreates it and a later move nests it). |
| Dimensions | Teleporting the self-test player is only checked on 1.20.1 | On 1.21.1 and 26.x the headless test player has no network connection and `teleportTo` throws a NullPointerException. A real-client tour now enters a run-time dimension on all ten builds (`docs/frames/dim-<build>.png`). |
| Item modification | Applies on the server and single player, not to a remote client | Components are not synced. |

Unification needs from a port: the item tag folder name (`tags/item`), an item-exists check, the recipe reload hook calling
`UnifyReload.prepare`, a loot modifier (`UnifyLootModifier`), an inventory sweep (`InventoryUnifier`) and the `/modjs unify` command.
Ore swapping needs a biome modifier (`UnifyOreBiomeModifier`), `OreUnifier` and a chunk-load listener. Everything else (`common/unify`) is shared.

## Version-specific pitfalls already met

- Dimension, recipe, tag and loot JSON formats change between versions; read vanilla's own JSON from the game jar instead
  of hard-coding fields (see `DimensionEventJS.addType`).
- 26.1.2 needs `setId` on block and item properties, and has no chunk progress listener or random sequences in
  `ServerLevel`'s constructor.
- Run the headless self test on every port, and unit tests in `common/`.
- Client-only code (title screen, tooltips, key events, JEI) cannot be run here; compile it, then test with `runClient`.

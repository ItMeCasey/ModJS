# Porting ModJS to more versions

ModJS ships Forge 26.1.2 and Forge 1.21.1 today. More Minecraft versions and loaders are planned, so this file lists
what a new port has to provide and every gap that exists because a version could not do something yet.

## Ports so far

| Module | Minecraft | Loader | Status |
|---|---|---|---|
| `forge-26.3` | 26.3 | Forge 66.1.5 | full; 81 self-test checks and the unit tests pass in a headless game-test server (no client run) |
| `neoforge-26.3` | 26.3 | NeoForge 26.3.0.101 | full; 81 self-test checks and the unit tests pass in a headless game-test server (no client run) |
| `forge-26.2` | 26.2 | Forge 65.1.6 | full; 81 self-test checks and the unit tests pass in a headless game-test server (no client run) |
| `neoforge-26.2` | 26.2 | NeoForge 26.2.0.93 | full; 81 self-test checks and the unit tests pass in a headless game-test server (no client run) |
| `forge-26.1.2` | 26.1.2 | Forge 64.1.6 | full; self test passes |
| `forge-1.21.1` | 1.21.1 | Forge 52.1.21 | full; self test passes |
| `forge-1.20.1` | 1.20.1 | Forge 47.4.28 | full (rarity is rewritten through the item's `rarity` field); the same 82 self-test checks and 71 unit tests pass |
| `neoforge-1.20.1` | 1.20.1 | NeoForge 47.1.106 | same as Forge 1.20.1 (same sources); 82 self-test checks and 71 unit tests pass. Ship the `-all` jar (it carries Rhino) |
| `neoforge-1.21.1` | 1.21.1 | NeoForge 21.1.265 | full; the same 81 self-test checks and 71 unit tests pass |
| `neoforge-26.1.2` | 26.1.2 | NeoForge 26.1.2.125 | full (F3 lines through a debug entry, compiled but not yet seen on a client); the same 81 self-test checks and 71 unit tests pass |

The 1.20.1 module copies the Forge 1.21.1 one and adapts to the older game: plural data folders and `item`/string recipe results (`DataFolders`, `Legacy120Format`), `FriendlyByteBuf` messages on a `SimpleChannel`, the old `Tier` and `ArmorMaterial` interfaces, `RecipeManager.fromJson`, reflection for item stack size and durability, `Codec` instead of `MapCodec`, and Rhino through `minecraftLibrary`. Its self test needs a connected test player (an `EmbeddedChannel`) because Forge reacts to logins.

NeoForge 1.20.1 (47.1) still uses the `net.minecraftforge` packages and the `forge` ids, so its sources are the Forge 1.20.1 ones unchanged; only the build differs (NeoGradle 7, Rhino through a `library` configuration added to each run, the `-all` jar from Jar-in-Jar).

The 26.2 modules copy the 26.1.2 ones and the 26.3 modules copy the 26.2 ones. 26.2 moved screen handling to `Minecraft.gui`, replaced `ChatFormatting` colors with `TextColor` and `TeamColor`, and (NeoForge) renamed `ModListScreen` and dropped `EntityInteractSpecific`. 26.3 turned ore generation into the new `Feature` model (one `worldgen/feature` registry, ores read through `AbstractOreFeature`), made composter chances an item component (`ServerEvents.compostableRecipes` snaps a chance to the nearest vanilla one: 0.3, 0.5, 0.65, 0.85 or always one layer), changed the recipe registry, pack supplier and loot and biome modifier signatures, and renamed `Name` to `id` in block states and `minCount`/`maxCount` to `count` in spawner entries (the shared code writes the right format for the running version). Forge 26.3 ships its own `modjs:remove_spawns` because the stock `forge:remove_spawns` crashes on biomes without spawns in a category.

The NeoForge 1.21.1 and 26.1.2 modules copy the Forge 1.21.1 one and changes only loader API names: events on `NeoForge.EVENT_BUS` with `net.neoforged` packages, `PlayerTickEvent.Post` and friends for ticks, `LivingIncomingDamageEvent` for hurt,
`ItemEntityPickupEvent.Pre`, `ModifyDefaultComponentsEvent` for item changes, payload handlers instead of a channel for networking, `DeferredHolder` instead of `RegistryObject`, `BaseFlowingFluid`,
and `neoforge:` ids and data folders (`LoaderNames`). Build files use ModDevGradle with Gradle 9.3.1, Rhino on `additionalRuntimeClasspath` and as Jar-in-Jar.
On NeoForge 26.1.2, 26.2 and 26.3 `ClientEvents.leftDebugInfo` and `rightDebugInfo` go through a `RegisterDebugEntriesEvent` entry (`client/ScriptDebugEntries`): the loader has no F3 text event any more, so both events show as one block of lines, left ones first (compiled on all three, not yet seen on a client). A script fluid has no custom client art (the old texture methods are gone; fluid models are not wired up yet). JEI has no 26.1.2 build. Block breaking uses `BreakBlockEvent`, reload listeners `AddServerReloadListenersEvent`, and the client can only send with `ClientPacketDistributor`.
On NeoForge 1.21.1 fluid client art still uses the deprecated `FluidType.initializeClient`; move it to `RegisterClientExtensionsEvent` when a later NeoForge removes it.

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
| Recipe viewers | EMI and REI not implemented anywhere (needs upstream build) | No Forge build to compile against for 1.21.1 or 26.1.2 (EMI Forge builds stop at 1.20.x, REI Forge artifacts stop at 12.x). A NeoForge or Fabric port can use their real APIs. |
| Recipe viewers | JEI not implemented on 26.1.2 (needs upstream build) | JEI publishes 26.1.2 for NeoForge and Fabric only. The 1.21.1 plugin is `compat/jei/ModJSJeiPlugin.java`; copy it when a build exists. |
| Recipe viewers | Create recipe types: not planned | Create has no build for most supported versions and its recipe types are a large separate API. |
| Recipe viewers | KubeJS events beyond hide items, add information, hide categories | Not built (custom categories, hiding single recipes, and so on). |
| Scripts | On 26.1.2 `Block` and `BlockState` are replaced by stand-ins (`BlockFacades`) | The script engine would inspect client-only classes and crash a dedicated server. Check whether each new version needs the same. |
| Title screen | Vanilla panorama background only on 1.21.1 | `PanoramaRenderer` was removed in 26.1.2. |
| Block info | Jade plugin missing on Forge 26.1.2, 26.2, 26.3 and Forge 1.21.1 | Jade publishes no Forge build for those versions (checked 2026-11-05 on CurseForge). `compat/jade/` is copied from the NeoForge module when one appears. |
| Block info | `hideTag` only works in Jade | SMWTI's API only lets a mod add lines, not remove another mod's. |
| Block info | Jade shows a bar as a row of blocks | Jade's bar element differs between its 1.20.1, 1.21.1 and 26.x versions. |
| Forge 26.x and 1.21.1 | A biome modifier shipped in the mod jar makes every world "use experimental settings" | The world then waits on a vanilla confirmation screen. ModJS therefore writes its ore-swap modifier into the generated data pack only while `Unify.ores()` is on (`UnifyOreData`); with that on, players see the screen once per world. Forge 1.20.1 and NeoForge are not affected. |
| Block info | Jade on 1.20.1 has no client frame | Jade's 1.20.1 jar uses SRG names and its mixins fail in the Mojang-named dev game; compile-checked only. |
| Block info | Entity `nbt` is empty | Saving an entity changed on 26.x (value output), so scripts read `event.entity` instead. |
| Forge 1.21.1 | In a dev client opened with Quick Play, `/modjs ...` is an unknown command and a player death throws Forge's "Can not retrieve LootModifierManager until resources have loaded once" | Seen on 2026-11-06 in three launches (BEI's flat world, SMWTI's saved world, ModJS alone): the `modjs` command is missing even for the console, and Forge's own `ForgeInternalHandler` loot manager is never created, with no exception logged. The same command code passes on Forge 1.20.1, 26.x and NeoForge 1.21.1, and the headless self test passes. Looks like the Forge 52.1.21 dev client's Quick Play world open, not ModJS code, but a normal world was not tried (it needs a person at the keyboard). The 2026.11.27 tour also shows `ServerEvents.loaded` and `LevelEvents.loaded` not firing there, so server-start events are missing in that run as well; every other check passes on that build. Open. |
| Forge 1.21.1 | In the dev client the scripted title screen does not replace the main menu either (frame docs/frames/title-forge-1.21.1-vanilla.png, Forge 1.21.1 client with only ModJS loaded; the same script replaces it on Forge 1.20.1) | Same family as the row above: some Forge events (`ScreenEvent.Opening` for the first screen, `RegisterCommandsEvent`, server lifecycle) seem not to reach ModJS there, while block, item and player events do. The run configs of Forge 1.21.1 and 1.20.1 are identical, so this is most likely a Forge 52.1.21 dev-client issue, but it was not proven. |
| NeoForge 26.x | The F3 debug entry (`ScriptDebugEntries`) was not seen on a client | Three client attempts on NeoForge 26.1.2 never reached the world: the saved test world `modjs_info` opened a `BackupConfirmScreen` that scripts cannot press (the Rhino sandbox blocks `getClass`, and `Button.onPress` needs an input argument). Fix the world (open it once by hand or recreate it) and re-run with a client script that calls `Minecraft.debugEntries.setOverlayVisible(true)`. |
| Events | chat, logged out and level unloaded are not driven in the client check | Driving them needs a second player or a real chat packet; logged out and unloaded fire only when the server stops. The other 31 gameplay events fire with correct data in a real client on all ten builds (Forge 1.21.1: see its own row). |
| Worldgen | The chunk-generation self-test check (script-added ore counted next to vanilla iron) is skipped on Forge 1.21.1, 26.1.2, 26.2, 26.3 and NeoForge 1.21.1 | Their game-test overworld is a flat world, which places no features. The test world is saved between runs, so the check uses random far-away chunks. NeoForge 1.21.1's headless self test also fails if `run/mods` holds Jade (NPE in Jade's player join handler); move the folder away first. |
| Dimensions | Teleporting the self-test player is only checked on 1.20.1 | On 1.21.1 and 26.x the headless test player has no network connection and `teleportTo` throws a NullPointerException; a real client is needed. |
| Dimensions | Restart persistence of run-time dimensions is not tested | A restart test needs the dedicated server (EULA, Casey only) or a client that quits and reopens the world. |
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

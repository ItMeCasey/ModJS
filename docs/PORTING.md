# Porting ModJS to more versions

ModJS ships Forge 26.1.2 and Forge 1.21.1 today. More Minecraft versions and loaders are planned, so this file lists
what a new port has to provide and every gap that exists because a version could not do something yet.

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

## Known gaps to close when porting

| Area | Gap | Why |
|---|---|---|
| Recipe viewers | EMI and REI not implemented anywhere | No Forge build to compile against for 1.21.1 or 26.1.2 (EMI Forge builds stop at 1.20.x, REI Forge artifacts stop at 12.x). A NeoForge or Fabric port can use their real APIs. |
| Recipe viewers | JEI not implemented on 26.1.2 | JEI publishes 26.1.2 for NeoForge and Fabric only. The 1.21.1 plugin is `compat/jei/ModJSJeiPlugin.java`; copy it when a build exists. |
| Recipe viewers | KubeJS events beyond hide items, add information, hide categories | Not built (custom categories, hiding single recipes, and so on). |
| Scripts | On 26.1.2 `Block` and `BlockState` are replaced by stand-ins (`BlockFacades`) | The script engine would inspect client-only classes and crash a dedicated server. Check whether each new version needs the same. |
| Title screen | Vanilla panorama background only on 1.21.1 | `PanoramaRenderer` was removed in 26.1.2. |
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

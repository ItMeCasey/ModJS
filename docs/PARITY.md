# KubeJS parity checklist

Event names come from the KubeJS wiki's event list (kubejs.com/wiki/events, read 2026-11-03). `yes` means ModJS has the
event and it either passed an in-game check or is the same bridge as one that did; `compiled` means the bridge builds
and registers but nobody has triggered it in a running game; `no` means not implemented.

How things were checked: server-side data (recipes, tags, loot, worldgen files, registry) and the event bridges listed in
the self test ran in a real Forge game-test server on both 26.1.2 and 1.21.1. Client events, networking and generated
textures and models have not been run on a real client.

See [PORTING.md](PORTING.md) for what a new Minecraft version or loader has to provide.

## Script folders

| KubeJS | ModJS |
|---|---|
| `startup_scripts`, `server_scripts`, `client_scripts` | yes (`modjs/` in the game folder) |
| `assets`, `data` (resource and data pack folders) | yes, copied into the generated packs |
| `config`, `exported` | no |

## Events

| Group | Event | Status |
|---|---|---|
| StartupEvents | registry | yes (item, block, creative_tab) |
| | init, postInit | compiled |
| | modifyCreativeTab (add only) | compiled |
| ServerEvents | recipes, tags, generateData, highPriorityData | yes |
| | blockLootTables, entityLootTables, chestLootTables, genericLootTables | yes (ModJS names; KubeJS names may differ) |
| | commandRegistry, loaded, unloaded, tick | yes |
| | afterRecipes | compiled |
| | command | compiled |
| | basicCommand | compiled |
| | compostableRecipes | yes (checked) |
| | recipeTypeRegistry, specialRecipeSerializers | no |
| ClientEvents | lang, highPriorityAssets, tick | yes / compiled |
| | loggedIn, loggedOut | compiled |
| | splashes, titleScreen (ModJS only) | compiled; splash and screen unchecked on a client |
| | init, leftDebugInfo, rightDebugInfo | compiled |
| | painterUpdated, paintScreen | no (use `ClientEvents.screens` for your own screens) |
| | screens (ModJS only): scripted screens with buttons, text and text boxes; `Client.openScreen` | compiled; not seen on a client |
| BlockEvents | broken, placed, rightClicked, leftClicked | yes (broken checked in game test) |
| | farmlandTrampled | compiled |
| | modification | yes (hardness, resistance, light, friction, speed, jump, tool requirement; checked) |
| | detectorChanged, detectorPowered, detectorUnpowered | no |
| ItemEvents | rightClicked, entityInteracted, crafted, smelted, foodEaten, dropped, pickedUp | crafted checked; others compiled |
| | tooltip (KubeJS: modifyTooltips) | compiled |
| | modification (max stack size, max damage, rarity) | yes, checked on both versions |
| | firstLeftClicked, firstRightClicked, canPickUp | compiled |
| | armorTierRegistry, toolTierRegistry (as `StartupEvents.registry` `armor_material` and `tool_tier`) | yes (checked) |
| | dynamicTooltips | compiled (same data as `tooltip`, filtered by item id) |
| | modelProperties | no |
| EntityEvents | hurt | yes (checked) |
| | death, spawned | compiled |
| | checkSpawn | compiled on both versions |
| LevelEvents | tick, loaded, unloaded, beforeExplosion, afterExplosion | compiled |
| PlayerEvents | chat, loggedIn | yes (checked) |
| | loggedOut, respawned, tick, advancement, inventoryOpened, inventoryClosed | compiled |
| | chestOpened, chestClosed | compiled |
| | decorateChat, inventoryChanged | yes (checked in the self test) |
| Dimensions global (ModJS only) | create, exists, list, teleport, delete at run time; saved per world | created, loaded as real levels, edited and deleted in headless self test; teleport with a real player and restart persistence unchecked |
| DimensionEvents (ModJS only) | add, edit, duplicate, drop; dimension types | JSON checked against the game's codec; not seen in a real world |
| NetworkEvents | dataReceived | compiled |
| RecipeViewerEvents | hideItems, addInformation, hideCategories | JEI on 1.21.1: compiles against JEI 19.70.0.469, not run with JEI |
| | groupItems (for viewers with grouping, such as Barely Enough Items) | event defined and unit tested; read by BEI, ignored by JEI |
| | the other four (custom categories, recipe hiding, and so on) | no |
| | Barely Enough Items (26.1.2) | reads hideItems, addInformation, hideCategories and groupItems by reflection; tested by the BEI session in a dev client for the first three, groupItems untested |
| | EMI, REI | blocked: no Forge build to compile against. EMI's published Forge builds stop at 1.20.x and its 1.21.1 build is NeoForge only; REI's Forge artifacts stop at 12.x (1.20.x) and REI 16 for Forge 1.21.1 is not on a Maven. |
| | JEI on 26.1.2 | blocked: JEI lists 26.1.2 for NeoForge and Fabric, no Forge artifact on its Maven |

## Registry builders

| Type | Status |
|---|---|
| Item (name, stack size, food, rarity, tooltip, texture, creative tab) | yes |
| Tools (pickaxe, axe, shovel, hoe, sword with a vanilla tier) | yes (registered; models unchecked on a client) |
| Armor (vanilla material) | yes (registered; worn look unchecked on a client) |
| Custom tool tiers and armor materials | yes (checked) |
| Block (hardness, resistance, light, requires tool, texture, loot drop) | yes |
| Block shapes: slab, stairs, wall, fence | yes (registered; models unchecked on a client) |
| Block entities, other shapes | no |
| Creative tab | yes |
| Fluid (source, flowing, block, bucket, tint) | yes (registered; look and behavior unchecked on a client) |
| Container block entity (`block.container(rows)`) | yes (checked on both versions) |
| Entity type, other block entities, sound, enchantment, and the rest | no (entity types need a renderer and AI, and are not in KubeJS core either) |

## Recipes

shaped, shapeless, smelting, blasting, smoking, campfireCooking, stonecutting, smithing, custom JSON, remove,
replaceInput, replaceOutput: yes. Filters support `id`, `mod`, `type`, `group`, `output`, `input`, `not`, `or`.
Mod-specific recipe schemas (for example Create): no.

## Global scope

| Name | Status |
|---|---|
| `console`, `global`, `Java`, `Item`, `Ingredient`, `Text`, `JsonIO`, `Utils`, `Platform`, `SECOND`, `MINUTE`, `HOUR` | yes (partial APIs) |
| `Persistent` (ModJS only), `Network`, `GameRule` (ModJS only) | yes |
| `UUID`, `ResourceLocation`, `Direction`, `Facing`, `AABB`, `BlockPos`, `Vec3`, `JavaMath`, `Blocks`, `Items`, `Fluids`, `EntityType`, `MobEffects`, `Mth` and a few more | yes, as Java classes (checked: `Vec3`, `BlockPos`, `UUID`, `Direction`) |
| `Block`, `NBT`, `NBTIO`, `Fluid`, `Color`, `BlockStatePredicate`, `Stats`, `DamageSource`, `string#namespace` | no |

## Known behavior differences

- Tag removal and loot-table editing read the original files from the game and mod jars, not from world data packs.
- Gameplay events pass real Minecraft objects (`player`, `level`, `item`) instead of KubeJS wrapper classes.
- Recipe, tag, loot and worldgen changes are written as a generated data pack, so they work with any other data pack.
- On 26.1.2 a script cannot hold a raw Minecraft `Block` or block state (the engine would inspect client-only classes and crash a dedicated server), so those are swapped for stand-ins with `id`, `defaultState`, `blockId` and `properties`. Fixed and checked in the self test.
- `Unify` (ModJS only, an Ore Dictionary replacement): recipe, loot and inventory unification and generated materials were checked in the headless self test on both versions with a fake second mod (vanilla iron ingots as tin). Not checked with real mods that use custom recipe types.

## Left unbuilt on purpose

- **Entity types:** a new mob needs a model, renderer, attributes and AI. A script cannot supply those safely without a client to check, and KubeJS core has no entity registry either.
- **Block detectors** (`detectorChanged`, `detectorPowered`, `detectorUnpowered`): they belong to a KubeJS-only detector block. Use a normal block with `BlockEvents` or a container block.
- **`recipeTypeRegistry`, `specialRecipeSerializers`:** new recipe types need Java serializers; scripts cannot define them.
- **Create and other mod recipe schemas:** their JSON formats change between mod versions and cannot be checked here. Use `event.custom({...})` with the mod's own JSON.
- **JEI custom categories and recipe hiding:** custom categories need drawing code; hiding single recipes needs each viewer's recipe ids. `hideItems` and `hideCategories` cover most needs.
- **Bucket unification, custom-code ores, custom-code structures:** see the unification section of the reference.

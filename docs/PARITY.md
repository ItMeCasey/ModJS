# KubeJS parity checklist

Event names come from the KubeJS wiki's event list (kubejs.com/wiki/events, read 2026-11-03). `yes` means ModJS has the
event and it either passed an in-game check or is the same bridge as one that did; `compiled` means the bridge builds
and registers but nobody has triggered it in a running game; `no` means not implemented.

How things were checked: server-side data (recipes, tags, loot, worldgen files, registry) and the event bridges listed in
the self test ran in a real Forge game-test server on both 26.1.2 and 1.21.1. Client events, networking and generated
textures and models have not been run on a real client.

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
| | basicCommand, compostableRecipes, recipeTypeRegistry, specialRecipeSerializers | no |
| ClientEvents | lang, highPriorityAssets, tick | yes / compiled |
| | loggedIn, loggedOut | compiled |
| | init, leftDebugInfo, rightDebugInfo, painterUpdated, paintScreen | no |
| BlockEvents | broken, placed, rightClicked, leftClicked | yes (broken checked in game test) |
| | farmlandTrampled | compiled |
| | detectorChanged, detectorPowered, detectorUnpowered, modification | no |
| ItemEvents | rightClicked, entityInteracted, crafted, smelted, foodEaten, dropped, pickedUp | crafted checked; others compiled |
| | tooltip (KubeJS: modifyTooltips) | compiled |
| | modification (max stack size, max damage, rarity) | yes, checked on both versions |
| | firstLeftClicked, firstRightClicked, canPickUp, dynamicTooltips, modelProperties, armorTierRegistry, toolTierRegistry | no |
| EntityEvents | hurt | yes (checked) |
| | death, spawned | compiled |
| | checkSpawn | compiled on 1.21.1 only; no on 26.1.2 |
| LevelEvents | tick, loaded, unloaded, beforeExplosion, afterExplosion | compiled |
| PlayerEvents | chat, loggedIn | yes (checked) |
| | loggedOut, respawned, tick, advancement, inventoryOpened, inventoryClosed | compiled |
| | chestOpened, chestClosed | compiled |
| | decorateChat, inventoryChanged | no |
| NetworkEvents | dataReceived | compiled |
| RecipeViewerEvents | all eight | no |

## Registry builders

| Type | Status |
|---|---|
| Item (name, stack size, food, rarity, tooltip, texture, creative tab) | yes |
| Tools (pickaxe, axe, shovel, hoe, sword with a vanilla tier) | yes (registered; models unchecked on a client) |
| Armor (vanilla material) | yes (registered; worn look unchecked on a client) |
| Custom tiers and armor materials | no |
| Block (hardness, resistance, light, requires tool, texture, loot drop) | yes |
| Block shapes: slab, stairs, wall, fence | yes (registered; models unchecked on a client) |
| Block entities, other shapes | no |
| Creative tab | yes |
| Fluid (source, flowing, block, bucket, tint) | yes (registered; look and behavior unchecked on a client) |
| Entity type, block entity, sound, enchantment, and the rest | no (entity types are not in KubeJS core either) |

## Recipes

shaped, shapeless, smelting, blasting, smoking, campfireCooking, stonecutting, smithing, custom JSON, remove,
replaceInput, replaceOutput: yes. Filters support `id`, `mod`, `type`, `group`, `output`, `input`, `not`, `or`.
Mod-specific recipe schemas (for example Create): no.

## Global scope

| Name | Status |
|---|---|
| `console`, `global`, `Java`, `Item`, `Ingredient`, `Text`, `JsonIO`, `Utils`, `Platform`, `SECOND`, `MINUTE`, `HOUR` | yes (partial APIs) |
| `Persistent` (ModJS only) and `Network` | yes |
| `UUID`, `ResourceLocation`, `Direction`, `Facing`, `AABB`, `BlockPos`, `Vec3`, `JavaMath`, `Blocks`, `Items`, `Fluids`, `EntityType`, `MobEffects`, `Mth` and a few more | yes, as Java classes (checked: `Vec3`, `BlockPos`, `UUID`, `Direction`) |
| `Block`, `NBT`, `NBTIO`, `Fluid`, `Color`, `BlockStatePredicate`, `Stats`, `DamageSource`, `string#namespace` | no |

## Known behavior differences

- Tag removal and loot-table editing read the original files from the game and mod jars, not from world data packs.
- Gameplay events pass real Minecraft objects (`player`, `level`, `item`) instead of KubeJS wrapper classes.
- Recipe, tag, loot and worldgen changes are written as a generated data pack, so they work with any other data pack.
- On 26.1.2, a script that holds a Minecraft `Block` object (for example `Blocks.STONE`) crashes a dedicated server, because the script engine inspects client-only classes. Use id strings there.

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
| | modifyCreativeTab | no |
| ServerEvents | recipes, tags, generateData, highPriorityData | yes |
| | blockLootTables, entityLootTables, chestLootTables, genericLootTables | yes (ModJS names; KubeJS names may differ) |
| | commandRegistry, loaded, unloaded, tick | yes |
| | afterRecipes | compiled |
| | basicCommand, command, compostableRecipes, recipeTypeRegistry, specialRecipeSerializers | no |
| ClientEvents | lang, highPriorityAssets, tick | yes / compiled |
| | loggedIn, loggedOut | compiled |
| | init, leftDebugInfo, rightDebugInfo, painterUpdated, paintScreen | no |
| BlockEvents | broken, placed, rightClicked, leftClicked | yes (broken checked in game test) |
| | detectorChanged, detectorPowered, detectorUnpowered, farmlandTrampled, modification | no |
| ItemEvents | rightClicked, entityInteracted, crafted, smelted, foodEaten, dropped, pickedUp | crafted checked; others compiled |
| | tooltip (KubeJS: modifyTooltips) | compiled |
| | firstLeftClicked, firstRightClicked, canPickUp, dynamicTooltips, modelProperties, modification, armorTierRegistry, toolTierRegistry | no |
| EntityEvents | hurt | yes (checked) |
| | death, spawned | compiled |
| | checkSpawn | no |
| LevelEvents | tick, loaded, unloaded, beforeExplosion, afterExplosion | compiled |
| PlayerEvents | chat, loggedIn | yes (checked) |
| | loggedOut, respawned, tick, advancement, inventoryOpened, inventoryClosed | compiled |
| | chestOpened, chestClosed, decorateChat, inventoryChanged | no |
| NetworkEvents | dataReceived | compiled |
| RecipeViewerEvents | all eight | no |

## Registry builders

| Type | Status |
|---|---|
| Item (name, stack size, food, rarity, tooltip, texture, creative tab) | yes (tools, armor and tiers: no) |
| Block (hardness, resistance, light, requires tool, texture, loot drop) | yes (shaped blocks, block entities: no) |
| Creative tab | yes |
| Fluid, entity type, block entity, sound, enchantment, and the rest | no |

## Recipes

shaped, shapeless, smelting, blasting, smoking, campfireCooking, stonecutting, smithing, custom JSON, remove,
replaceInput, replaceOutput: yes. Filters support `id`, `mod`, `type`, `group`, `output`, `input`, `not`, `or`.
Mod-specific recipe schemas (for example Create): no.

## Global scope

| Name | Status |
|---|---|
| `console`, `global`, `Java`, `Item`, `Ingredient`, `Text`, `JsonIO`, `Utils`, `Platform`, `SECOND`, `MINUTE`, `HOUR` | yes (partial APIs) |
| `Persistent` (ModJS only) and `Network` | yes |
| `Block`, `NBT`, `NBTIO`, `UUID`, `ResourceLocation`, `Direction`, `Facing`, `AABB`, `Fluid`, `Color`, `BlockStatePredicate`, `Blocks`, `Items`, `Stats`, `BlockPos`, `DamageSource`, `Vec3`, `JavaMath`, `string#namespace` | no (the Minecraft classes are reachable through `Java.loadClass`) |

## Known behavior differences

- Tag removal and loot-table editing read the original files from the game and mod jars, not from world data packs.
- Gameplay events pass real Minecraft objects (`player`, `level`, `item`) instead of KubeJS wrapper classes.
- Recipe, tag, loot and worldgen changes are written as a generated data pack, so they work with any other data pack.

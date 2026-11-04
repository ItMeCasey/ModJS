# ModJS scripting reference

Scripts live in the `modjs/` folder of the game directory. ModJS writes `probe/modjs.d.ts` and a `jsconfig.json` there
on every launch, so editors such as VS Code complete every name below.

| Folder | Runs | Reload |
|---|---|---|
| `startup_scripts` | once while the game loads (registers items, blocks, creative tabs) | restart |
| `server_scripts` | when a world loads | `/modjs reload` or `/reload` |
| `client_scripts` | when resources load on the client | F3+T |
| `assets/<namespace>/...` | copied into a generated resource pack | F3+T |
| `data/<namespace>/...` | copied into a generated data pack | `/reload` |

Handlers: `ServerEvents.recipes(event => ...)`. With an id filter: `BlockEvents.broken('minecraft:stone', event => ...)`.
Extras: `ServerEvents.recipes.once(handler)`, `.priority(10, handler)` (higher runs first), `.off(handler)`.
Call `event.cancel()` in a cancellable event to stop the action. A script error is logged, shown by `/modjs errors`,
and never stops other scripts.

## Commands

| Command | Who | What |
|---|---|---|
| `/modjs hand` | everyone | item id in your main hand |
| `/modjs toolbar` | everyone | item ids in your toolbar slots |
| `/modjs inventory` | everyone | item ids in every slot, including armor and off hand |
| `/modjs reload` | operators | reload data packs and server scripts |
| `/modjs errors` | operators | list script errors |
| `/modjs status` | operators | script counts and recipe change counts |

## Startup scripts

```js
StartupEvents.registry('item', event => {
  event.create('ruby').displayName('Ruby').maxStackSize(16).food(4, 0.6).tooltip('Shiny').tab('modjs:gems');
});
StartupEvents.registry('block', event => {
  event.create('ruby_block').hardness(5).resistance(6).requiresTool(true).lightLevel(7);
});
StartupEvents.registry('creative_tab', event => {
  event.create('gems').displayName('Gems').icon('modjs:ruby');
});
```

Block shapes: `.type('slab' | 'stairs' | 'wall' | 'fence')` (the texture is used on every face). Tools: `.tool('pickaxe' | 'axe' | 'shovel' | 'hoe' | 'sword', 'wood' | 'stone' | 'iron' | 'gold' | 'diamond' | 'netherite')` uses a vanilla tier.

Everything is registered as `modjs:<id>`. Put textures at `modjs/assets/modjs/textures/item/<id>.png` and
`.../block/<id>.png`. Models, blockstates, lang entries and block loot tables are generated for you.
`StartupEvents.init` and `StartupEvents.postInit` fire at common setup and load complete.

Change existing items from a startup script: `ItemEvents.modification(event => event.modify('minecraft:ender_pearl', item => { item.maxStackSize = 64; item.maxDamage = 100; item.rarity = 'epic'; }))`.
The change applies on the server and in single player; a remote client does not receive it.

## Server scripts

Recipes: `shaped(result, pattern, key)`, `shapeless`, `smelting`, `blasting`, `smoking`, `campfireCooking`,
`stonecutting`, `smithing(result, template, base, addition)`, `custom(json)`. Builders accept `.id()`, `.group()`,
`.xp()`, `.cookingTime()`. Results take `'3x minecraft:stone'`, `Item.of('minecraft:stone', 3)` or `{item, count}`.
Ingredients take ids, `#tags`, arrays, `{item}` or `{tag}`.

Changing existing recipes: `remove(filter)`, `replaceInput(filter, from, to)`, `replaceOutput(filter, from, to)`.
A filter is a recipe id string, an array (any of), or an object with `id`, `mod`, `type`, `group`, `output`, `input`,
`not` and `or`.

Tags: `ServerEvents.tags('item', event => { event.add('c:gems/ruby', 'modjs:ruby'); event.remove('minecraft:planks', 'minecraft:oak_planks'); event.removeAll('mymod:old'); })`.
Removal rewrites the tag from the copies inside the game and mod jars.

Loot: `blockLootTables`, `entityLootTables`, `chestLootTables`, `genericLootTables` events with `add(id, table => ...)`,
`addSimpleBlock(block, drop)`, `addSimple(id, drop)`, `clear(id)`, `modify(id, table => ...)` (tables in jars only).
Tables offer `addPool(pool => pool.rolls(2).addItem(item, count, weight).randomChance(0.5))`, `removeItem`,
`replaceItem`, `clearPools`.

Worldgen: `WorldgenEvents.add(event => event.addOre({id, block, size, count, minY, maxY, biomes}))`, `addFeature`,
`removeFeature`, `addSpawn`, `removeSpawn`. These write Forge biome modifiers.

Raw data: `ServerEvents.generateData(event => event.addJson('mymod:loot_table/x', {...}))`.

Commands: `ServerEvents.commandRegistry(event => event.register(Java.loadClass('net.minecraft.commands.Commands').literal('hi').executes(ctx => 1)))`.

`ServerEvents.command("gamemode", e => e.cancel())` runs for every command typed, filtered by command name (`e.message` is the full line).

Gameplay events (`BlockEvents`, `ItemEvents`, `PlayerEvents`, `EntityEvents`, `LevelEvents`) hand you a `GameEvent` with
`player`, `playerName`, `level`, `entity`, `entityId`, `item`, `itemId`, `blockId`, `x`, `y`, `z`, `message`, `amount`,
`tell(text)` and `runCommand(command)`. Chat can be rewritten with `event.message = '...'`; hurt damage with
`event.setAmount(n)`.

## Client scripts

`ClientEvents.lang('en_us', event => event.add('key', 'Value'))`, `ItemEvents.tooltip(event => event.add('minecraft:apple', 'Crunchy'))`,
`ClientEvents.keyPressed(event => ...)`, `ClientEvents.tick`, `loggedIn`, `loggedOut`.

## Helpers

`Item.of`, `Ingredient.of`, `Text.of/red/green/...`, `Utils.runAfter(ticks, fn)`, `Utils.runEvery(ticks, fn)`,
`Utils.server`, `Utils.randomInt`, `Utils.rollChance`, `Utils.runCommand`, `JsonIO.read/write/exists` (inside `modjs/`),
`Persistent.get/set/has/remove`, `Persistent.store(name)`, `Persistent.player(uuid)`, `Persistent.entity(uuid)`,
`Network.sendToServer/sendToPlayer/sendToAll` with `NetworkEvents.dataReceived('channel', event => event.data)`,
`Platform.mcVersion/isClient()/isLoaded(modId)`, `global` (shared between script types), `console`, `Java.loadClass`.

## Sandbox

Scripts cannot load file, network, reflection, process or thread classes. `Java.loadClass` and Java package access follow
the same filter.

## Developing ModJS

Each version is its own Gradle build: `cd forge-26.1.2 && ./gradlew build` (Gradle 9.3+, Java 25) or
`cd forge-1.21.1 && ./gradlew build` (Gradle 8.14, Java 21). `common/` is shared. `./gradlew runGameTestServer` starts a
headless server with `-Dmodjs.selftest=true`, which posts real Forge events for a test player and prints
`SELFTEST PASS/FAIL` lines (scripts in `smoke-test/server_scripts/selftest.js`).

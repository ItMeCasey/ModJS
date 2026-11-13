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
| `/modjs trace on` / `off` | operators | prints every script event (name, player, block, item, entity) to your chat and the log as it fires; `trace on ticks` includes tick events |

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

Custom tool tiers and armor materials:
```js
StartupEvents.registry('tool_tier', e => e.create('ruby').uses(900).speed(8).attackDamage(3).level('diamond').enchantability(15).repair('minecraft:diamond'));
StartupEvents.registry('armor_material', e => e.create('ruby').durability(30).defense(3, 8, 6, 3).toughness(1).repair('minecraft:diamond').color(0xD81E3C));
StartupEvents.registry('item', e => { e.create('ruby_pickaxe').tool('pickaxe', 'modjs:ruby'); e.create('ruby_helmet').armor('helmet', 'ruby'); });
```
Worn armor art is the vanilla iron armor recolored with `color`; put your own textures in `assets/modjs/textures/models/armor/` (1.21.1) or `.../entity/equipment/humanoid/` (26.1.2) to replace it.
Change existing blocks from a startup script: `BlockEvents.modification(e => e.modify('minecraft:stone', b => { b.destroySpeed = 9; b.explosionResistance = 77; b.lightEmission = 5; }))`
(also `friction`, `speedFactor`, `jumpFactor`, `requiresTool`).

Storage blocks: `event.create('crate').container(3)` makes a chest-like block with 3 rows of 9 slots (opens on right-click, drops its items, readable with `event.blockEntity`).

Scripted blocks: `.textures({ top, bottom, side, north, south, east, west })` puts a different texture on each face (`all`, `front` = `north`, `end` = top and bottom; the most specific key wins), `.model('cube_all' | 'cube' | 'cube_column' | 'cube_bottom_top' | 'orientable' | 'mymod:block/thing')` picks the model (`orientable` turns the block to face the player), and `.inventory({ slots, stackLimit, values })` / `.entity()` give the block a block entity with slots, a saved and synced data map and synced numbers. `.onTick(interval, be => {...})` runs on the server, `.onButton((id, be, player) => {...})` when a screen button with an `id` is pressed, and `.screen(screen => {...})` describes the screen with `text`, `button`, `background`, `size(w, h)` and `bar({ value, max, x, y, width, height, color })`; all positions count from the panel's top left. `be` has `getItemId(slot)`, `getCount(slot)`, `setItem(slot, id, count)`, `clearSlot(slot)`, `shrink(slot, n)`, `insert(id, count)`, `getData(key)`, `setData(key, value)`, `getValue(name)`, `setValue(name, n)`, `markChanged()` and `sync()`. A full machine is in `examples/world-overlay`; the wiki page is "Scripted blocks: inventories and screens".

Block shapes: `.type('slab' | 'stairs' | 'wall' | 'fence')` (the texture is used on every face). Tools: `.tool('pickaxe' | 'axe' | 'shovel' | 'hoe' | 'sword', 'wood' | 'stone' | 'iron' | 'gold' | 'diamond' | 'netherite')` uses a vanilla tier. Armor: `.armor('helmet' | 'chestplate' | 'leggings' | 'boots', 'leather' | 'chainmail' | 'iron' | 'gold' | 'diamond' | 'netherite')` uses a vanilla material. Add items to existing tabs: `StartupEvents.modifyCreativeTab('minecraft:tools_and_utilities', e => e.add('modjs:ruby'))`.

One example script with every kind of registration is in `examples/registry` (tab, item, tool tier and tool, armor material and armor, blocks, slab, stairs, crate and a fluid).

Fluids: `StartupEvents.registry('fluid', e => e.create('goo').color(0x44FF44).luminosity(6).density(1500).noBucket())` registers `modjs:goo`, `modjs:goo_flowing`, a liquid block and `modjs:goo_bucket`; it uses the vanilla water textures tinted by `color`.

Everything is registered as `modjs:<id>` unless the id has a namespace: `event.create('mypack:ruby')` registers `mypack:ruby` (assets and data go to `assets/mypack` and `data/mypack`; the namespace is lowercase letters, digits, `_ . -` and not `minecraft`), and `event.namespace('mypack')` sends the plain ids of one handler there. Put textures at `modjs/assets/modjs/textures/item/<id>.png` and
`.../block/<id>.png`. Models, blockstates, lang entries and block loot tables are generated for you.
`StartupEvents.init` and `StartupEvents.postInit` fire at common setup and load complete.

Change existing items from a startup script: `ItemEvents.modification(event => event.modify('minecraft:ender_pearl', item => { item.maxStackSize = 64; item.maxDamage = 100; item.rarity = 'epic'; }))`.
The change applies on the server and in single player; a remote client does not receive it.

## Server scripts
### In-world crafting

`ServerEvents.worldRecipes(event => {...})` defines recipes that happen in the world (like the Lychee mod); see `examples/world-recipes`.

| Method | Starts when |
|---|---|
| `itemOnBlock(block, item)` | a player right-clicks the block holding the item |
| `leftClickBlock(block, item)` | a player left-clicks the block holding the item |
| `itemsInBlock(block, [items])` | dropped items are inside the block (water, lava, a cauldron...) |
| `itemsOnBlock(block, [items])` | dropped items rest on top of the block |
| `anvil(block)` | a falling anvil lands on the block |
| `lightning(block)` | lightning strikes the block (`.item(...)` also needs items within three blocks) |
| `randomTick(block)` | random blocks near players (about 100 checks per player per tick); set `.chance()` low |
| `blockBreak(block)` | a survival player breaks the block (`.tool(item)`) |

Blocks and items are ids or `#tags`; a block may carry state values (`'minecraft:cauldron[level=3]'`); items may be `'minecraft:coal x2'` or `{ item, count }`. Options: `sneaking(bool)`, `hand('main'|'off'|'any')`, `consume(n)` (0 keeps), `damage(n)`, `chance(p)`, `id(name)`. Results: `turnInto(block)`, `breakBlock()`, `drop(item, n)`, `give(item, n)`, `command(text)`, and for `blockBreak` `replaceDrops()`. Dropped items are checked every 10 ticks; an anvil counts when it lands on a block; recipes are in effect on the server that runs the script, and the recipe viewer pages (JEI, EMI, REI, BEI) show them in single player.


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

Dimensions (server scripts; applied when a world loads, so restart rather than `/reload`):
```js
DimensionEvents.add(event => {
  event.addType('mymod:bright', { base: 'minecraft:overworld', set: { ambient_light: 0.5, coordinate_scale: 4 } });
  event.add('mymod:void', { generator: 'void' });                          // void, flat, overworld, nether, end or a JSON object
  event.add('mymod:flat', { generator: 'flat', layers: [{ block: 'minecraft:stone', height: 3 }], biome: 'minecraft:plains', type: 'mymod:bright' });
  event.edit('minecraft:the_nether', { generator: 'nether', type: 'mymod:bright' }); // replace an existing dimension
  event.duplicate('minecraft:the_nether', 'mymod:nether_two');             // copy a script-made, data pack or vanilla dimension
  event.duplicateType('minecraft:overworld', 'mymod:dark', { ambient_light: 0 });
  event.drop('mymod:old');                                                 // removes script-made and modjs/data dimensions
});
```
`addRaw(id, json)` and `addTypeRaw(id, json)` take full JSON. Dimensions from the game or other mods cannot be removed, only replaced with `edit`.
Reach one with `/execute in mymod:void run tp @s 0 100 0`.

Dimensions at run time (no restart): the `Dimensions` global builds, moves into and removes dimensions while the server runs. Options are the same as `DimensionEvents.add`.
Created dimensions are remembered in `modjs_dimensions.json` in the world folder and come back on the next start with their chunks.
```js
// A hub plus one private void dimension per player
ServerEvents.loaded(() => {
  Dimensions.create('mymod:hub', { generator: 'flat', layers: [{ block: 'minecraft:stone', height: 4 }] });
});
PlayerEvents.loggedIn(event => {
  const id = 'mymod:player_' + event.player.getStringUUID().replace(/-/g, '');
  if (Dimensions.create(id, { generator: 'void' })) {
    event.runCommand('execute in ' + id + ' run setblock 0 63 0 minecraft:stone');   // first visit: build a platform
  }
  Dimensions.teleport(event.player, id, 0.5, 64, 0.5);
});
```
`Dimensions.create(id, options)` (false if it exists), `exists(id)`, `list()`, `created()`, `teleport(player, id, x, y, z)` and `delete(id)`.
`delete` sends players inside to the overworld spawn, unloads the level and deletes its folder; it only works on dimensions made with `create`.

World types (the Create World screen's World Type button; a startup script, restart to change):
```js
StartupEvents.worldTypes(event => {
  event.add('mypack:skyblock', { name: 'Skyblock', overworld: 'void' });                   // nether and end stay vanilla; base defaults to minecraft:normal
  event.add('mypack:hollow', { overworld: 'void', nether: 'void', dimensions: { 'mypack:sky': { generator: 'void' } }, visible: false });
  event.edit('minecraft:large_biomes', { nether: 'void' });                                // keeps the dimensions you do not mention
  event.duplicate('minecraft:amplified', 'mypack:tall', { name: 'Tall' });
  event.hide('minecraft:single_biome_surface');  event.rename('minecraft:flat', 'Flatland');  event.drop('mypack:old');
});
```
Dimension options are those of `DimensionEvents.add`. Presets are written to `worldgen/world_preset/<id>.json`, the list is the tag `minecraft:normal`, labels are language keys `generator.<ns>.<id>`. A dedicated server picks one with `level-type=mypack:skyblock`.

Raw data: `ServerEvents.generateData(event => event.addJson('mymod:loot_table/x', {...}))`.

Commands: `ServerEvents.commandRegistry` runs each time the command tree is built (world load and `/reload`), after every other mod has registered.
```js
ServerEvents.commandRegistry(event => {
  event.add('warp <place:word>', { permission: 2, run: ctx => { ctx.reply('Warping to ' + ctx.arg('place')); return 1; } });
  event.add('home set', ctx => 1);                   // words that share a first word share one command
  event.remove('gamemode');                          // also removes aliases such as /tp for /teleport
  event.remove('time query');                        // a sub-command
  event.edit('list', { permission: 2 });             // who may use it; `requires: player => ...` also works
  event.edit('help', { run: ctx => { ctx.reply('Ask in Discord'); return 1; } });
  event.alias('teleport', 'warpto');  event.rename('gamemode', 'gm');
  // event.exists('time set'), event.list(), and event.register(brigadierLiteral) for raw Brigadier
});
```
Argument types in a usage string: `word`, `string`, `text` (rest of line), `integer`, `long`, `float`, `double` (with limits, `<n:integer:1:100>`), `bool`, `player`, `players`, `entity`, `entities`. A handler gets `ctx.player`, `ctx.source`, `ctx.input`, `ctx.arg(name)`, `ctx.has(name)`, `ctx.reply(text)` and `ctx.fail(text)`; its number is the command result. Brigadier cannot remove or change nodes, so `remove` and `edit` reach into its command tree by reflection.

`ServerEvents.command("gamemode", e => e.cancel())` runs for every command typed, filtered by command name (`e.message` is the full line).

Gameplay events (`BlockEvents`, `ItemEvents`, `PlayerEvents`, `EntityEvents`, `LevelEvents`) hand you a `GameEvent` with
`player`, `playerName`, `level`, `entity`, `entityId`, `item`, `itemId`, `blockId`, `x`, `y`, `z`, `message`, `amount`,
`tell(text)`, `runCommand(command)` and `blockEntity` (the chest, furnace or sign at the event's block, or null). Chat can be rewritten with `event.message = '...'`; hurt damage with
`event.setAmount(n)`.

`dimension` is the event's dimension id, `sneaking` whether its player is crouching, and `world` (null on the client) reads and changes blocks around the event: `getBlock(x, y, z)` (`{id, properties, x, y, z}` or null when unloaded), `getBlockId`, `matches(x, y, z, matcher)`, `isLoaded`, `findBlocks(x1, y1, z1, x2, y2, z2, matcher)`, `findBlocksAround(x, y, z, radius, matcher)`, `connected(x, y, z, matcher, diagonal, limit)` and `connectedSame(x, y, z, diagonal, limit)` (flood fill; positions come back as `{x, y, z}`), `destroyBlock(x, y, z, drop)`, `setBlock(x, y, z, 'id[state=value]')` and `bonemeal(x, y, z)`. A matcher is an id, a `#tag`, or either with state values (`minecraft:wheat[age=7]`). Searches are capped at 262,144 blocks per box and 4,096 results.
Block events take a tag as well as an id: `BlockEvents.broken('#minecraft:logs', e => ...)`. `PlayerEvents.sneakStarted` and `PlayerEvents.sneakStopped` run when a player starts and stops crouching.
`event.breakBlock(x, y, z)` and `event.breakBlocks(positions)` break blocks as the event's player (tool durability and enchantments, hunger, drops, game mode and protection mods; loaded, not air, within 64 blocks); they skip `BlockEvents.broken` unless a last argument `true` asks for it (nesting stops at 3 levels), `event.fromScript` marks such events, and `breakBlocks` goes nearest first and stops when the tool breaks. Client scripts: `Client.lookingAt()` (`{x, y, z, id, face, distance}` or null), `Client.world` (the read calls of `event.world` on loaded blocks; changing calls are errors), `Client.getBlock(x, y, z)` and `Client.isKeyHeld(id)` for keys from `ClientEvents.keyBindings`. `examples/vein-miner`; wiki page "Vein miner".

A block entity gives `id`, `blockId`, `x/y/z`, `hasInventory`, `size`, `getItemId(slot)`, `getCount(slot)`, `items` (list of `{slot, id, count}`),
`setItem(slot, id, count)`, `clearSlot(slot)` and `nbt` (SNBT text). Example:
`BlockEvents.rightClicked('minecraft:chest', e => { const be = e.blockEntity; if (be) e.tell(be.items.length + ' stacks'); })`.

More events: `ItemEvents.firstRightClicked` / `firstLeftClicked` / `canPickUp` (cancellable), `PlayerEvents.inventoryChanged` (`event.slot`, `event.item`),
`PlayerEvents.decorateChat` (runs before `chat`; set `event.message`), `ServerEvents.basicCommand` (every command; `event.message`; cancellable),
`ServerEvents.compostableRecipes(event => event.add(0.3, 'minecraft:stone'))` (0 removes), `EntityEvents.checkSpawn` (cancel to deny a spawn).
Script errors are also shown in chat to operators when they join (server and startup scripts) and to every player for their own client scripts.
On 26.1.2 blocks and block states reach scripts as stand-ins: `Blocks.STONE.id`, `.defaultState.properties`.

## Client scripts

`ClientEvents.lang('en_us', event => event.add('key', 'Value'))`; rename existing things with `event.renameItem('minecraft:diamond', 'Shiny Rock')`, `renameBlock`, `renameEntity` (display names only; F3+T to apply), `ItemEvents.tooltip(event => event.add('minecraft:apple', 'Crunchy'))`,
`ClientEvents.keyPressed(event => ...)`, `ItemEvents.dynamicTooltips('minecraft:apple', e => ...)` (per item id), `ClientEvents.leftDebugInfo` / `rightDebugInfo` (`event.add('text')` adds lines to F3), `ClientEvents.init`, `ClientEvents.tick`, `loggedIn`, `loggedOut`.

### Rebindable keys

Register keys in `client_scripts`; Minecraft lists them under Controls > Key Binds > Miscellaneous.
The default is a GLFW key code (`71` is G; `-1` starts unbound). Players can change it in Controls.
The namespaced id is stable across display-name edits, so saved bindings survive a label change.
Restart the client after adding or removing registrations or changing their defaults.

```js
ClientEvents.keyBindings(event => {
  event.register('pack:open_menu', 71, 'Open pack menu');
});
ClientEvents.keyBindingPressed('pack:open_menu', event => {
  Client.openScreen('hub');
});
```

`keyBindingPressed` fires once per queued press while a player is in a world; holding a key follows
Minecraft's normal key mapping behavior. `ClientEvents.keyPressed` remains available for raw key codes.

### Scripted HUD

`ClientEvents.hud` draws once per HUD frame while a player is in a world and the HUD is visible.
Coordinates and `event.width` / `event.height` use scaled GUI pixels. Colors are ARGB numbers
(`0xAARRGGBB`). Filled rectangles draw behind text; `event.cancel()` skips the scripted HUD for that frame.

```js
ClientEvents.hud(event => {
  event.fill(8, 8, 160, 22, 0xCC182432);
  event.text('Welcome to the pack', 14, 15, 0xFFFFCC44);
});
```

`event.text(text, x, y)` defaults to opaque white. `event.fill(x, y, width, height, color)` ignores
rectangles with zero or negative dimensions. Keep handlers short: they run every rendered frame.

### Overlay: bars, text, pictures and animation

`Overlay` (client scripts) keeps elements that you create once and change later; the game draws them every frame.

```js
const hp = Overlay.bar('hp', { x: 10, y: 10, width: 120, height: 10, value: 80, max: 100, color: '#cc2222', color2: '#ff8844', label: '{value} / {max}', smooth: 300 });
Overlay.text('score', { x: 10, y: 26, text: 'Score: 0', color: '#ffcc44', scale: 1.5, outline: '#000000' });
Overlay.rect('panel', { anchor: 'left', x: 6, width: 90, height: 28, color: '#cc3a5ca8', color2: '#cc101840' });
Overlay.image('logo', { anchor: 'topright', x: -10, y: 10, width: 32, height: 32, texture: 'mypack:textures/gui/logo.png' });
Overlay.item('sword', { x: 10, y: 50, item: 'minecraft:diamond_sword', scale: 2 });
hp.value = 35;                                       // the bar glides to the new value
hp.animate({ x: 100, alpha: 0.5 }, 500, 'easeOut');  // linear, easeIn, easeOut, easeInOut, easeOutBack, bounce
hp.fadeIn(300); hp.fadeOut(300); hp.pulse(900, 0.25, 1.0); hp.stop();
```

Common options: `x`, `y`, `anchor` (`topleft`, `top`, `topright`, `left`, `center`, `right`, `bottomleft`, `bottom`, `bottomright`), `visible`, `alpha`, `scale`, `layer`, `surface` (`hud` or `screen`). Colors are numbers (`0xAARRGGBB`, `0xRRGGBB` is opaque) or strings (`"#RRGGBB"`, `"#AARRGGBB"`).
Bars: `width`, `height`, `value`, `min`, `max`, `smooth`, `direction`, `color`, `color2`, `background`, `border`, `borderSize`, `label` (`{value}`, `{max}`, `{percent}`), `labelColor`, `labelShadow`, `labelScale`.
Text: `text`, `color`, `shadow`, `outline`, `background`, `padding`, `align`, `width`. Rectangles: `width`, `height`, `color`, `color2`, `horizontal`, `border`, `borderSize`. Images: `texture`, `width`, `height`, `tint`, and for a part of a sheet `texWidth`, `texHeight`, `u`, `v`, `regionWidth`, `regionHeight`.
`Overlay.get(id)`, `has`, `remove`, `clear`. A mistyped option is an error. Client scripts clear the overlay when they reload. Elements with `surface: 'screen'` draw on your own screens and the title screen.
Buttons there accept `icon` (an item id or a texture), `iconSize` and `iconSide` (`left`, `right`, `center`).

### World overlays

`Overlay.at(x, y, z)` (client scripts) draws text, item icons, bars and textures in the world over a block position; sizes and offsets are in blocks.

```js
const at = Overlay.at(100, 64, -20);                 // also Overlay.at({ x, y, z })
at.text('title', { text: 'Generator', y: 0.45, color: '#ffd27a', background: '#80000000' });
at.bar('progress', { value: 40, max: 100, width: 1, height: 0.14, y: 0.2 });
at.item('icon', { item: 'minecraft:diamond', y: 0.95 });
at.texture('badge', { texture: 'mymod:textures/gui/badge.png', face: 'up', width: 0.5, height: 0.5 });
Overlay.world.maxDistance = 16; Overlay.world.maxElements = 64;
Overlay.forBlock('modjs:demo_smelter', (at, data, pos) => { /* describes the block from its synced data; runs again when it changes */ });
```

Every element takes `x`, `y`, `z`, `face` (`billboard`, `up`, `down`, `north`, `south`, `east`, `west`), `scale`, `visible`, `alpha`, `layer` and `distance`, and animates like the screen overlay. Elements farther than their distance, in unloaded chunks, facing away, or beyond the element cap (nearest blocks win) are not drawn. `Overlay.at(...).data` and `Overlay.data(x, y, z)` read the data a scripted block syncs. Forge 26.x has no level-wide drawing hook, so there overlays draw over ModJS's own block entities only. `examples/world-overlay`; wiki page "World overlays".
`Overlay.outline(id, positions, { color, fill, connected, width, throughWalls, alpha, visible })` (the id is optional) outlines (and with `fill` tints) any block; `connected: true` draws one merged silhouette around the group instead of a box per block, up to 4096 blocks per set and 8192 boxes per frame, farther than 128 blocks skipped; `Overlay.getOutline(id)` and `Overlay.removeOutline(id)`. `throughWalls` works on 1.20.1 and 1.21.1, `width` on 26.x. Forge 26.x draws outlines only while the crosshair is on a block (the block highlight callback), every other build always.

### Unifying items (Ore Dictionary style)

One canonical item per material: every recipe, drop and inventory that holds another mod's version ends up with it.
```js
// startup_scripts: register ModJS items for materials (placeholder textures are generated; your own in assets/modjs/textures/ win)
Unify.materials({ materials: ['tin', 'zinc', 'ruby'] });          // omit materials for the built-in list; kinds default per material
StartupEvents.registry('item', event => {
  event.create('steel_gear').unify('c:gears/steel');               // any item you build can be the canonical one for a tag
});

// server_scripts
Unify.auto('all');                                                  // every c:<kind>/* tag; or Unify.auto(['ingots', 'dusts'])
Unify.group('c:ingots/lead', { canonical: 'thermal:lead_ingot' });  // pick an existing item instead of ours
Unify.prefer(['modjs', 'create', 'thermal']);                       // who wins when no ModJS item exists
Unify.inputs('tag');                                                // inputs become #c:... so every member still works (default: 'canonical')
Unify.skipRecipes({ mod: 'create' });                               // leave some recipes alone
Unify.skipItems(['@mekanism', 'thermal:tin_ingot']);                // never replace these
Unify.swapInventories(true);  Unify.convertRecipes(true);           // defaults
```
Kinds registered by `Unify.materials`: ingot, nugget, dust, plate, gear, rod, wire, coin, gem, raw, storage_block by default (tags `c:ingots/<m>`,
`c:raw_materials/<m>`, and so on). Opt-in kinds, listed in `kinds`: `ore` (a `<m>_ore` block that drops the raw item or gem and joins `c:ores/<m>`; it
is not generated in the world, place it with `WorldgenEvents.addOre`), `crushed` (`c:crushed_raw_materials`), `clump`, `shard` and `dirty_dust`.
Generated art is the game's own textures recolored (so it keeps vanilla's outline and shading): ingots from the iron ingot, ores from iron ore (only the ore
specks change), dust from gunpowder, shards from amethyst, and so on; plates, gears and coins are drawn sprites in the same style. Colors are found automatically: for tin, ModJS looks for `tin_ingot`, `tin_dust`, `tin_nugget`, `raw_tin`, `tin_ore` and similar textures in every loaded
mod and the game, and uses the base tone of the first one it finds (so a material another mod already has matches that mod's look). Only when nothing is found does it
use a small built-in fallback, then a color derived from the name. Override any material with
`Unify.colors({ tin: 0xC9D3D9, mythril: 0x7FD6F5 })` (startup) or `colors` in `unify.json`. Without the game's textures (a dedicated server) simple drawn sprites are used, and any PNG
you put in `assets/modjs/textures/item/` wins.
Items Minecraft already has (iron, gold, copper ingots, diamond and so on) are skipped unless you pass `includeVanilla: true`.
Order of choice for the canonical item: `Unify.group(..., {canonical})`, then a ModJS item built with `.unify(tag)`, then the first match in `Unify.prefer`.
Ores in the world: `Unify.ores()` (server script, or `oreWorldgen` / `oreChunks` in `unify.json`) forces the canonical ore block. `worldgen` (default true)
swaps the ore features of every biome, so new chunks place the canonical block (the same vein size, count and height; every stone and deepslate variant becomes the one
canonical block). `chunks` (default true) converts ore blocks already in chunks as they load. Both only touch ores in unified `c:ores/*` groups.
`Unify.ores({ chunks: false })` leaves existing chunks alone. Worldgen swaps apply when the world or server starts, not on `/reload`.
Not covered: ores a mod places with custom code or as part of structures, and mod ore blocks that carry block state properties (they become the canonical default state).

Fluids: `Unify.fluid('c:oil', {canonical: 'modjs:oil'})`, `Unify.autoFluids(true)` (every `c:` fluid tag) and `event.create('oil').unify('c:oil')` on a fluid builder.
Fluid ids stored under `fluid`, `fluid_id` or `fluidId` keys in recipe JSON are rewritten, so mods that name fluids differently are not covered.
No script needed: `modjs/config/unify.json` is created on first run (`registerMaterials`, `materials`, `kinds`, `includeVanilla`, `prefer`, `auto`,
`autoFluids`, `inputs`, `skipMods`, `skipItems`, `skipRecipes`, `swapInventories`, `convertRecipes`). Scripts run after it and override it.
What it changes: recipe outputs and item inputs (any recipe stored as JSON), loot from blocks, mobs, chests and fishing, and items already in
player inventories (checked every two seconds). Each replaced item also gets a 1:1 crafting recipe into the canonical one, and JEI hides the
replaced items on 1.21.1. `/modjs unify` lists every group and the totals.
Not changed: recipe types whose ingredients are not plain item or tag values (some mod machines), items on the ground, world generation (ores keep
coming from the mods that generate them, but they drop the canonical item).

### Splash text and the main menu

```js
ClientEvents.splashes(event => {
  event.add('Made with ModJS!', 'Welcome back');   // add lines
  event.remove('creeper');                          // remove any splash containing the text
  // event.set('Only this one');  event.clear();    // replace or empty the list
});

ClientEvents.titleScreen(event => {
  event.background('panorama');            // or 'dirt', 'mymod:textures/gui/bg.png', or a color like 0x101018
  event.logo('vanilla');                   // or 'none', or a texture id
  event.showSplash(true);
  event.button('Singleplayer', 'singleplayer');
  event.button('Multiplayer', 'multiplayer');
  event.button('Join the Hub', 'connect:play.example.com');
  event.button('Our Discord', 'url:https://discord.gg/example', { width: 120 });
  event.button('Say hi', () => console.log('hi'), { x: 130, y: 60, width: 60 });   // x: from center, y: from top
  event.button('Quit', 'quit');
  event.text('Welcome to the server', { y: 8, color: 0xFFD700 });
});
```
Button actions: `singleplayer`, `multiplayer`, `options`, `mods`, `languages`, `quit`, `connect:host`, `url:https://...`, or a function.
Buttons without `x`/`y`/`anchor` stack down the middle. `button`, `text` and `input` take `anchor` (`topleft`, `top`, `topright`, `left`, `center`, `right`, `bottomleft`, `bottom`, `bottomright`, as in `Overlay`): `x` and `y` are then offsets from the anchor point and the widget's matching edge or corner aligns to it (`{ anchor: 'left', x: 12, y: -36 }`), recomputed on every resize. `x` and `y` also take `'25%'` of the screen's width or height. If no `titleScreen` handler exists the vanilla menu stays. F3+T applies changes.
The `panorama` background draws the vanilla one on 1.21.1; on 26.1.2 it uses the game's default menu background.

### Your own screens

```js
ClientEvents.screens(event => {
  event.create('hub', screen => {
    screen.text('Pick a name', { y: 30 });
    screen.input('name', { placeholder: 'Your name', y: 60, width: 160 });
    screen.button('Go', s => { console.log('hello ' + s.getValue('name')); s.close(); }, { y: 90, width: 80 });
    screen.button('More', 'open:settings', { y: 115, width: 80 });
    screen.button('Close', 'close', { y: 140, width: 80 });
  });
  event.create('settings', screen => screen.button('Back', 'close', { y: 60 }));
});
ClientEvents.keyPressed(e => { if (e.key === 72 && e.action === 1) Client.openScreen('hub'); });   // H
```
A screen config has the same methods as `ClientEvents.titleScreen` plus `input(id, options)` and `closeOnEsc(bool)`; a button callback receives the screen
(`getValue(id)`, `setValue(id, text)`, `close()`, `open(name)`). Button actions also include `close` and `open:<name>`. `Client.openScreen(name)` and `Client.closeScreen()` work from any client script.

### Toasts, tutorial and warnings

```js
Client.hideToasts('tutorial', 'recipe');                 // also system, advancement, all, or a system toast id
ClientEvents.toast(e => { if (e.id === 'unsecure_server_warning') e.setMessage('Chat is not signed here'); });  // e.type, e.id, e.title, e.message, e.hide()
Client.showToast('Title', 'Message');  Client.clearToasts();
Client.disableTutorial();                                // sets the game's tutorial option to none
Client.skipExperimentalWarning();                        // answers "proceed" on the experimental settings warning
```
All of this reaches the game by reflection and class names, so a version that renamed something logs a warning and does nothing. Not checked on a real client.

### Block and entity info (Show Me What This Is and Jade)
`InfoEvents` decides what the look-at overlays say. Handlers go in startup scripts (they run on both sides) or client scripts.
```js
InfoEvents.block('mymod:lamp', e => { e.add('Lit', { color: '#ffd24a' }); e.bar('Power', 0.4, '#ffd24a'); e.item('minecraft:coal', 'Fuel', 8); });
InfoEvents.entity('minecraft:cow', e => e.bar('Health', e.entity.getHealth() / e.entity.getMaxHealth(), '#ff5555'));
InfoEvents.block(e => e.hideTag('jade:energy_storage'));   // no id: every block; hideTag is Jade only
```
Event fields: `id`, `kind`, `side`, `detailed`, `x`, `y`, `z`, `state`, `blockEntity`, `entity`, `nbt`. Calls: `add(text, {color, position, item, count})`, `bar(text, fraction, color)`, `item(id, text, count)`, `hideTag(tag)`.
Handlers in startup and server scripts run on the server for blocks with a block entity and for entities, and on the client for plain blocks; client script handlers always run on the client.
`Client.screenshot(name)` (client scripts) saves the current frame, overlays included, to `screenshots/<name>.png` in the game folder; `scripts/info_tour.py` uses it for docs/frames.
Works with SMWTI on every build and Jade on NeoForge 26.x, NeoForge 1.21.1 and 1.20.1 (Jade has no Forge 26.x or Forge 1.21.1 build). Example: `examples/block-info`.

### Recipe viewers (JEI, EMI and REI)

```js
// client_scripts/viewer.js
RecipeViewerEvents.hideItems(event => event.hide('minecraft:dirt', '#minecraft:wool', '@create'));   // id, #tag or @mod
RecipeViewerEvents.addInformation(event => event.add('minecraft:diamond', 'Found deep underground.', 'Needs an iron pickaxe.'));
RecipeViewerEvents.hideCategories(event => event.hide('minecraft:campfire_cooking'));
RecipeViewerEvents.groupItems(event => event.group('Gems', ['minecraft:diamond', '#c:gems'], { icon: 'minecraft:diamond' }));   // viewers with grouping (Barely Enough Items)
```
`groupItems` is read only by viewers that can group items (Barely Enough Items); JEI, EMI and REI ignore it.
Native adapters provide item hiding, information pages and category hiding on the supported targets below.

| Viewer | Supported ModJS targets |
|---|---|
| JEI | Forge and NeoForge 1.20.1 and 1.21.1; NeoForge 26.1.2, 26.2 and 26.3 |
| EMI | Forge and NeoForge 1.20.1; NeoForge 1.21.1 |
| REI | Forge and NeoForge 1.20.1; NeoForge 1.21.1, 26.1.2, 26.2 and 26.3 |

Selectors accept an item id, `#tag`, or `@mod`. Category ids use the viewer's own ids; REI's vanilla
`minecraft:plugins/` prefix and JEI 1.20.1's furnace/campfire/compostable names also accept their
modern vanilla aliases. EMI category hiding removes that category's recipes from its viewer.
Barely Enough Items reads all four events through its own optional bridge; its `hideCategories` can
only hide plugin categories unless BEI is installed on the server too. Viewer hiding does not remove
items or recipes from the game (use `ServerEvents.recipes` for that).


## Helpers

`Item.of`, `Ingredient.of`, `Text.of/red/green/...`, `Utils.runAfter(ticks, fn)`, `Utils.runEvery(ticks, fn)`,
`Utils.server`, `Utils.randomInt`, `Utils.rollChance`, `Utils.runCommand`, `Utils.blockEntity(dimension, x, y, z)`, `JsonIO.read/write/exists` (inside `modjs/`),
`Config.register(name, schema, { side })` declares typed settings kept in `modjs/config/<name>.json5` (`<name>-client.json5`, `<name>-server.json5` for the other sides): builders `Config.number/integer/bool/string/choice/list/group` (or plain values and nested objects), values as properties (`cfg.speed`, `cfg.nested.limit`), `cfg.get/set/save/reload/onChange`, `Config.get/reload/names/file`. A new file is written with the defaults and a comment per entry; an existing file is never rewritten by a reload (missing entries and wrong or out-of-range values use their defaults with a log line, an unreadable file is left alone); `cfg.save()` writes the whole file, keeping a `.bak` copy of a file edited by hand. The file is polled about once a second and `onChange` runs after edits; `/modjs reload config [name]` reads server and common configs. Not synced to clients. A config is shared by every script of every type: `Config.get(name)` is the same object (null before it is registered, `Config.has(name)` tells), writes are visible at once and reach every `onChange` (server-side callbacks on the server tick, client ones on the client tick); registering a name again keeps the same config object and the values in memory, the latest entries win with one log line, another side is refused with a log line; a script reload keeps configs and other scripts' references. `examples/config`; wiki page "Config files".
`Scoreboard` (server scripts: `addObjective(name[, displayName[, criteria]])`, `removeObjective`, `objectives()`, `get/set/add/reset(holder, objective)`, `has`, `scores(objective)`, `holders()`, `setDisplay(slot, objective)`, teams with `addTeam`, `removeTeam`, `joinTeam`, `leaveTeam`, `teamOf`, `teamMembers`, `setTeamColor`, `setTeamPrefix`, `setTeamSuffix`, `setFriendlyFire`; a holder is a name or a player),
`GameRule.get(name)`, `GameRule.set(name, value)`, `GameRule.list()` (names match with or without underscores and case, so `keepInventory` and `keep_inventory` both work; server scripts only, once a world is loaded),
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

### Loop-local variables in Rhino

ModJS currently embeds Rhino 1.7.15 in interpreted ES6 mode. In that engine, a `const` declared inside a repeated loop block retains its first value on later iterations. Use `let` for values initialized inside `for` and `while` bodies. Top-level constants and constants in separately invoked functions do not have this loop behavior.

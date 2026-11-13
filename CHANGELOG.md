# Changelog

## 2026.11.36

All ten builds (the config code is shared; each build has a one-line client tick call and the `/modjs reload config` command).

- **New: config files for scripts.** `Config.register(name, schema, { side })` declares typed settings (`Config.number`, `integer`, `bool`, `string`, `choice`, `list`, nested groups, or plain values) and returns an object: `cfg.speed`, `cfg.nested.limit`, `cfg.speed = 3`, `cfg.get/set/save/reload/onChange`. The file is `modjs/config/<name>.json5` (`<name>-client.json5` and `<name>-server.json5` for the other sides): JSON with `//` comments, created with the defaults and a comment above every entry. An existing file is never rewritten when ModJS starts or reloads: missing entries and wrong or out-of-range values use their defaults with a log line, an unreadable file is left alone, and scripts never fail because of the file; `cfg.save()` writes the whole file and keeps a `.bak` copy of a file you edited. Files are checked about once a second and `onChange` runs after edits; `/modjs reload config [name]` reads server and common configs again. Config values are not synced to clients. Wiki page "Config files", typings, `examples/config`.
- **Config sharing between scripts is defined and tested.** A config is one object for all scripts of all types: `Config.get(name)` (null before registration, new `Config.has(name)`), writes visible at once and delivered to every `onChange`, `save()` from any script. Registering a name again keeps the config and its in-memory values (checked against the latest entries, which win; one log line when they differ); another side is refused with a log line; a script reload keeps the configs and other scripts' references. A script type is now recognized from the calling scope, so `onChange` registered from a function that runs later is delivered on the right side.
- **Safer file handling.** ModJS only creates files that do not exist (the example scripts for a script folder that did not exist, `jsconfig.json`, `config/unify.json`, a new config file) and never replaces a user file; the one folder it owns is `probe/`, whose `modjs.d.ts` is rewritten only when it differs. Every file ModJS writes (those, `JsonIO.write`, persistent data, the dimensions file of a world) goes through a temporary file and an atomic move, so a crash cannot leave a truncated file. Deleting a script-created dimension refuses any folder that is not inside a `dimensions` folder of the world.

## 2026.11.35

All ten builds (the new calls are in the shared code and every build has its own drawing and game hooks).

- **New: `Overlay.outline(id, positions, { color, width, fill, throughWalls })`.** Outlines, and optionally tints, any block in the world; `positions` is what `world.connected` or `world.findBlocks` return (or `[x, y, z]` arrays). Sets hold up to 4096 blocks; calling it again with the same id replaces them; `Overlay.getOutline` and `Overlay.removeOutline` find and remove them. `color` is the border, `fill` an optional tint with alpha (none by default), and `connected: true` draws one merged outline around the whole group (only the outer edges, none between touching blocks) while `false` outlines each block on its own. `throughWalls` draws over blocks in front on 1.20.1 and 1.21.1; `width` is honored on 26.x. `Overlay.outline(positions, options)` works without an id. **Forge 26.1.2, 26.2 and 26.3 draw outlines only while the crosshair is on a block** (Forge has no level-wide render event there; the block highlight callback is used); every other build draws always. (GitHub issue 3)
- **New: reading the client's world.** `Client.lookingAt()` answers `{x, y, z, id, face, distance}` for the block under the crosshair (or null), `Client.world` has the read calls of `event.world` (`getBlock`, `matches`, `findBlocks`, `connected`, ...) for loaded blocks, and `Client.getBlock(x, y, z)` is a shortcut. Changing calls are errors on the client. (GitHub issue 3)
- **New: `Client.isKeyHeld(id)`** for keys registered with `ClientEvents.keyBindings`, so a tick handler can follow a held key. An unregistered id is an error. (GitHub issue 3)
- **New: `event.breakBlock(x, y, z)` and `event.breakBlocks(positions)`** break blocks as the event's player: the tool in hand is used (durability, enchantments, drops), hunger goes down, game mode and protection mods can refuse. They must be loaded, not air and within 64 blocks. Breaks started this way skip `BlockEvents.broken` handlers (no recursion); pass `true` as the last argument to run them (nesting stops at 3 levels), and `event.fromScript` tells a handler where the break came from. `breakBlocks` goes nearest first and stops when the tool breaks. (GitHub issue 3)
- **Fix (Forge and NeoForge 26.3): keys registered with `ClientEvents.keyBindings` were bound to the wrong key, and the game could crash.** Minecraft 26.3 switched its input to SDL, where a key mapping holds an SDL scancode, not the GLFW code that scripts give (`event.register('pack:menu', 71, ...)` meant G but became scancode 71, Scroll Lock). Script key codes are now translated to scancodes when the key is registered, and the `keyPressed` event translates the game's scancode back to the GLFW code (`event.key`). Unbound (-1) and unknown keys stay unbound. Script code is unchanged: GLFW codes everywhere. 26.1.2 and 26.2 still use GLFW codes and are not affected; 1.20.1 and 1.21.1 are not affected.
- **Fix (Forge 26.1.2, 26.2, 26.3): `e.cancel()` in `BlockEvents.broken` now stops the player's break.** Forge 26.x reads the break event's result, which ModJS did not set, so the cancel was ignored there.
- Example `examples/vein-miner` (hold a key to outline a tree, break one log to fell it with your tool), wiki page "Vein miner", typings and wiki sections for all of it.

## 2026.11.34

All ten builds.

- **Fix (26.1.2, 26.2, 26.3): a title screen button with an item icon no longer crashes the game.** On 26.3 the main menu can draw before the item holders have their components bound (`Components not bound yet`). On the title screen the components are never bound, so the icon is drawn from the item's own texture file (`textures/item/NAME.png`, then `textures/block/NAME.png`); items without a single flat texture show no icon there. In a world the real item model is drawn and the stack is kept for the next frames. (GitHub issue 2)
- **New: anchors on the title screen and your own screens.** `button`, `text` and `input` take `anchor` (`topleft`, `top`, `topright`, `left`, `center`, `right`, `bottomleft`, `bottom`, `bottomright`, the same names as `Overlay`). `x` and `y` are then offsets from the anchor point and the widget's matching edge or corner lines up with it, recomputed on every resize and fullscreen change. `x` and `y` also take a share of the screen as text (`'25%'`). Without an anchor nothing changes. (GitHub issue 4)
- **New: read and change the blocks around an event.** Gameplay events have `event.dimension` and `event.world` with `getBlock(x, y, z)` (`{id, properties}`), `getBlockId`, `matches`, `isLoaded`, `findBlocks` (a box), `findBlocksAround` (a cube), `connected` and `connectedSame` (flood fill through connected blocks, optionally diagonal, with a limit), `destroyBlock(x, y, z, drop)`, `setBlock` and `bonemeal(x, y, z)`. Matchers are ids, `#tags` and state values (`minecraft:wheat[age=7]`). Searches are capped (262,144 blocks per box, 4,096 results). (GitHub issue 5)
- **New: block tags in block events.** `BlockEvents.broken('#minecraft:logs', ...)` (and `placed`, `rightClicked`, `leftClicked`) run for every block in the tag; a block that matches an id handler and a tag handler runs both once. (GitHub issue 5)
- **New: crouch events.** `PlayerEvents.sneakStarted` and `PlayerEvents.sneakStopped`, and `event.sneaking` on every event with a player. With `world.bonemeal(x, y, z)` a script can grow crops and saplings when a player crouches next to them. (GitHub issue 6)
- Wiki page "Reading and changing blocks around an event", typings, and the title screen page with the anchor section.

## 2026.11.33

All ten builds.

- **New: your own namespace.** `event.create('mypack:ruby')` in `StartupEvents.registry` registers `mypack:ruby`; a plain `ruby` still goes to `modjs:ruby`, so existing scripts do not change. It works for items, blocks (including scripted blocks and their menus), fluids, creative tabs, tool tiers and armor materials. Models, blockstates, language entries, loot tables, tags, repair tags and the default textures (`mypack:item/ruby`) are written under `assets/mypack` and `data/mypack`, and a mod-ids check refuses a namespace that is not lowercase letters, digits, `_ . -` or is `minecraft`. A duplicate is checked per full id (`modjs:ruby` and `mypack:ruby` can both exist), and an item and a block with an item that share a full id are reported. `event.namespace('mypack')` makes the plain ids of one handler go to that namespace. Unify, tooltips and the recipe viewer pages use the full id.
- **New: in-world crafting** (like the Lychee mod): `ServerEvents.worldRecipes(event => {...})` with right-click an item on a block (`itemOnBlock`), left-click (`leftClickBlock`), dropped items in a block such as water or a cauldron (`itemsInBlock`) or on top of one (`itemsOnBlock`), an anvil landing on a block (`anvil`), lightning (`lightning`, optionally with items lying nearby), random block changes near players (`randomTick`) and breaking blocks with extra or replaced drops (`blockBreak`). Recipes can need sneaking, a hand, a tag, a block state, can use up or keep the item, wear the tool, roll a chance, turn or remove the block, drop or give items and run a command. Every recipe shows up as an information page in JEI, EMI, REI and Barely Enough Items on the items it involves. Script only; `/modjs reload` applies changes.
- Examples in `examples/namespaces` and `examples/world-recipes`, wiki pages, typings.

## 2026.11.32

All ten builds.

- **New: world overlays.** `Overlay.at(x, y, z)` in a client script draws text, item icons, bars and textures in the world over a block, with sizes in blocks, a `face` (`billboard`, or lying flat on any side of the block), alpha, layers, animation, a per-element `distance`, a global `Overlay.world.maxDistance` and an element cap (`Overlay.world.maxElements`, nearest blocks first). Elements in unloaded chunks and on faces that point away are skipped. `Overlay.forBlock('modjs:id', (at, data, pos) => {...})` describes the overlay of every block of that id from the data it syncs, and runs again when the data changes. On Forge and NeoForge 1.20.1 and 1.21.1 and on NeoForge 26.x the overlay draws over any block; Forge 26.x has no level-wide drawing hook, so there it draws over ModJS's own block entities (scripted and storage blocks).
- **New: scripted blocks with real inventories and unique textures.** `.textures({ top, bottom, side, north, ... })` gives each face its own texture and `.model(...)` chooses cube, column, bottom and top, or `orientable` (the block turns to face the player and gets a `facing` property) or your own model file. `.inventory({ slots, stackLimit, values })` and `.entity()` give a block a block entity with slots (any positions on the screen), a saved data map that syncs to clients, and named numbers that reach an open screen; `.onTick(interval, be => ...)` runs on the server, `.onButton((id, be, player) => ...)` handles screen buttons, and `.screen(screen => ...)` describes the screen with the existing screen building blocks plus `size` and `bar`. The menu is a real container menu: Shift-click, hoppers and drops work, and the block's items are saved with it. `container(rows)` is unchanged.
- Four new example files in `examples/world-overlay` (a smelter machine, its overlay, textures and the screenshot tour), two wiki pages and typings for all of it in `modjs.d.ts`.
- ModJS now ships a small white texture (`modjs:textures/misc/white.png`) for the filled parts of world overlays, because 26.x no longer has the vanilla one.

## 2026.11.31

All ten builds.

- **Fix (Forge and NeoForge 1.20.1): the jar now loads in a normal game.** Versions 2026.11.28 to 2026.11.30 were built with development names and crashed a real Forge or NeoForge 1.20.1 install at startup (`NoSuchFieldError: Registries.CREATIVE_MODE_TAB`); the automatic checks only run in a development game, which hides this. The jar is now converted to the names a real 1.20.1 game uses, and the same file is uploaded for NeoForge 1.20.1 (checked in real Forge 47.4.28 and NeoForge 47.1.106 installs: mod loads, events fire, recipe changes apply). Item and block changes (`ItemEvents.modification`, `BlockEvents.modification`) find their fields by those names.
- **Fix (Forge and NeoForge 1.20.1): scripts can call Minecraft's own classes and methods by their usual names.** A normal 1.20.1 game only knows its own names (`m_82127_`, `f_42415_`), so `Items.DIAMOND`, `server.getRecipeManager()` and `Commands.literal` failed with "has no public instance field or method named". The script engine now translates the usual names with a table made from the build's own mappings (static and instance fields and methods, overloads, inherited methods, `player.health` style access, field writes). Checked in real Forge 47.4.28 and NeoForge 47.1.106 installs, where Jade also shows ModJS's info lines. The development game and newer versions are not affected.

- **New: the overlay.** `Overlay.bar`, `text`, `rect`, `image` and `item` create elements once in a client script; you change their fields afterwards (`bar.value = 40`) and the game draws them every frame. Progress bars have a value and a max, a color blend, a border, a label with `{value}`, `{max}` and `{percent}`, and a `smooth` option that eases the fill to a new value. Text has size, shadow, outline, alignment and a background box. Everything takes colors as numbers or `"#RRGGBB"` strings, an anchor (corners, edges, center), a layer and an alpha. Elements can `animate` any number field with an easing, `fadeIn`, `fadeOut` and `pulse`. See the new Overlay page on the wiki.
- **New: pictures on buttons.** Buttons on the title screen and on your own screens take `icon` (an item id or a texture), `iconSize` and `iconSide`.
- **Fix (Forge 1.21.1): the scripted title screen now shows.** Forge 52.1.21 never delivers its screen-opening event, so ModJS swaps the screen in when the vanilla one initializes. Seen in a real Forge install.

## 2026.11.30

All ten builds.

- Added `ClientEvents.hud` for scripted text and filled rectangles, with scaled GUI coordinates, ARGB colors, and per-frame cancellation.
- Removed obsolete global-loot-modifier list metadata on NeoForge 26.1.2 and 26.2; the loader now discovers modifiers directly and was trying to parse the old list as a modifier. Added a resource-manager registration check.
- Added rebindable scripted keys in Minecraft Controls, stable saved binding ids, and `ClientEvents.keyBindingPressed`.
- Added JEI bridges on NeoForge 26.1.2, 26.2 and 26.3, and legacy vanilla category aliases on JEI 1.20.1.
- Added native EMI bridges on Forge/NeoForge 1.20.1 and NeoForge 1.21.1, and REI bridges on those builds plus NeoForge 26.x. All support item hiding, information pages and category hiding.
- Test worlds now begin in Peaceful with natural and special mob spawning disabled before launch.
- Added repeatable isolated client checks for dimension restoration across clean game restarts, saved blocks, and logout/unload events.

## 2026.11.29

All ten builds.

- **Fix: `.glow(true)` on an item builder did nothing.** Plain items now shimmer like enchanted ones (tools and armor made with `.tool` or `.armor` do not take it yet).
- **Fix: a block made with `.noItem()` broke data loading on 26.3.** Its loot table named an item that does not exist; such a block now drops nothing unless `.drops(...)` is set.

- **`ClientEvents.leftDebugInfo` and `rightDebugInfo` now work on NeoForge 26.x.** The loader dropped its F3 text event, so the lines of both events show as one block, left ones first. Seen on a real client on 26.1.2 and 26.3.

- **Fix: a scripted title screen drew the Minecraft logo twice and garbled on 1.20.1.** It now draws the logo like the game does. Seen on a real client (docs/frames/title-forge-1.20.1.png), with the panorama, the scripted buttons and text, and a script-added splash line.
- Test only (all ten builds): the self test generates chunks and counts the ore a script added with `WorldgenEvents.addOre`.
- **`ItemEvents.modification` can now change `rarity` on 1.20.1.** Before, it was logged as unsupported. A new self-test check covers it.

## 2026.11.28

All ten builds.

- **Fix: the text box in your own screens ignored `initial`,** the option name the wiki and reference document. `initial` now sets the
  starting text; `value`, the older name, still works.
- Checked on a real client on every build: 31 of the 34 gameplay events (blocks, items, players, entities, ticks, explosions), a script
  message from the client to the server and the reply back, and a scripted screen. Forge 1.21.1 has an open problem in the development
  client (see docs/PORTING.md).

## 2026.11.27

Forge and NeoForge 26.3, 26.2 and 26.1.2 (six builds); the other four builds stay at 2026.11.26.

- **Fix: fluids registered by scripts were drawn as the pink-and-black missing texture on Forge and NeoForge 26.x.** They now use the vanilla water
  textures tinted with the fluid's color, as on the older versions.
- Checked on a real client on every build: the `/modjs hand`, `toolbar`, `inventory` and `reload` commands (a script added while the game ran
  is picked up by the reload), a player death, and one example script that registers every kind of content (`examples/registry`).
  Known open problem: on Forge 1.21.1, a dev client opened with Quick Play has no `/modjs` command (see docs/PORTING.md).

## 2026.11.26

All ten builds (Forge and NeoForge for 26.3, 26.2, 26.1.2, 1.21.1 and 1.20.1).

- **New: `InfoEvents`.** Scripts add lines, bars and item icons to what Show Me What This Is and Jade show for a block or entity,
  for their own blocks and entities and for any other block or entity (`InfoEvents.block(id, event => ...)`, `InfoEvents.entity(...)`).
  Show Me What This Is works on every build. Jade works on NeoForge 26.3, 26.2, 26.1.2 and 1.21.1; Forge has no Jade build for those
  versions, and Jade 1.20.1 is only compile-checked. `Client.screenshot(name)` saves a frame from a client script.
- **Fix: NeoForge 26.1.2, 26.2 and 26.3 crashed when the client loaded.** The toast manager was created before the game instance
  existed, and NeoForge 26.x also needs the client side of the `modjs:data` network message registered on its own event.
- **Fix: Forge 26.3, 26.2, 26.1.2 and 1.21.1 showed "Worlds using Experimental Settings are not supported" for every world,** so a
  world would not open until the player clicked through. The ore biome modifier shipped inside the jar caused it. It is now written
  into the generated data pack, and only while `Unify.ores()` is on. A pack that turns that on will still see the screen once per
  world on those Forge versions. NeoForge and Forge 1.20.1 are not affected.
- **Fix: a creative tab filled by a script listed each of its items twice,** which crashed 26.3 when the tab contents were built.

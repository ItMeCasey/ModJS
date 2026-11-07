# Changelog

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

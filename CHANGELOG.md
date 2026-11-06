# Changelog

## 2026.11.27

NeoForge 26.3, 26.2 and 26.1.2 only; the other seven builds stay at 2026.11.26.

- **Fix: fluids registered by scripts were drawn as the pink missing texture on NeoForge 26.x.** They now use the vanilla water
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

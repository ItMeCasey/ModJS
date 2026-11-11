# World overlays and scripted blocks: API design note (2026.11.32)

The design below is what shipped; the changes found while building it are marked **Built**.

Two features that were "Later" in `PROJECT_STATUS.md`. This note fixes the script API and the per-version plan before the code.

## 1. World overlays: `Overlay.at(pos)`

Client scripts draw text, item icons, bars and textures in the world, attached to a block position.

```js
const o = Overlay.at(x, y, z);              // also Overlay.at({x, y, z}); the same handle comes back for the same position
o.text('title',  { text: 'Generator', y: 1.4, color: '#ffffff', scale: 1, billboard: true });
o.item('icon',   { item: 'minecraft:diamond', y: 1.0, size: 0.5 });
o.bar('progress',{ value: 40, max: 100, width: 1, height: 0.12, color: '#3fdc5a', y: 0.8 });
o.texture('pic', { texture: 'modjs:textures/gui/pic.png', width: 0.5, height: 0.5, face: 'up' });
o.get('title').text = 'Changed';  o.remove('title');  o.clear();   Overlay.at(x,y,z).remove();
Overlay.world.maxDistance = 24;  Overlay.world.maxElements = 128;     // global limits
```

- Units are blocks. For a billboard `x`, `y`, `z` are offsets from the middle of the top of the block; for a flat element on a face (`up`, `down`, `north`, `south`, `east`, `west`) `x` is right and `y` is up on that face and `z` lifts it off the face. `face` defaults to `billboard` (turns to the camera).
- Elements reuse the existing `OverlayElement` machinery (fields set by option name, `animate`, `fadeIn`, `pulse`, `visible`, `alpha`, `scale`, `layer`): `WorldElement` extends it and only adds `z`, `face` and `distance`, and draws through `WorldCanvas` instead of the HUD canvas. **Built:** the classes are in `client/overlay` (not a `world` subpackage) because they use the package-private tick of `OverlayElement`.
- **Culling:** an element is skipped when it is farther than `distance` (its own option, default the global `maxDistance`, 24 blocks), when `visible` is false or `alpha` is 0, or when its block position is not in a loaded chunk. Billboard and face elements facing away from the camera are skipped. **Cap:** at most `maxElements` (default 128) elements are drawn per frame; the nearest positions win.
- **Block data synced to the client:** a scripted block with a block entity keeps a data map (below). The server sends it to every client that tracks the chunk (the vanilla block entity update packet), and the client reads it with `Overlay.data(x, y, z)` (a plain object, empty when unknown) or gets it as the argument of a template.
- **Templates:** `Overlay.forBlock('modjs:smelter', (at, data, pos) => { at.text(...) })` runs when a block of that id is first drawn and again whenever its synced data changed (at most once a second); the handle is cleared first, so the callback only describes the current state. This is how script blocks show state without a per-frame script call.

### Per-version render hook

All the layout, culling, capping and sorting is Minecraft-free and unit tested in `common/`. Each build supplies one small `WorldCanvas` (push at a point or face, text, item, filled quad, textured quad):

| Build | Hook | Draws at |
|---|---|---|
| Forge/NeoForge 1.20.1, Forge/NeoForge 1.21.1 | `RenderLevelStageEvent` (after block entities), buffer source | any position |
| NeoForge 26.1.2, 26.2, 26.3 | `SubmitCustomGeometryEvent` (submit node collector) | any position |
| Forge 26.1.2, 26.2, 26.3 | Forge has no level submit event; a `BlockEntityRenderer` is registered for the block entity types ModJS adds | ModJS block entities (scripted and storage blocks). **Built:** not every vanilla type, because a renderer on a chest would replace the chest's own. |

This follows how Skyblock Resources draws generator overlays (level stage event on 1.20.1/1.21.1, submit collector on NeoForge 26.x, a block entity renderer on Forge 26.x).

## 2. Scripted blocks with inventories, per-face textures and screens

```js
StartupEvents.registry('block', event => {
  event.create('smelter').displayName('Smelter').hardness(3)
    .textures({ top: 'modjs:block/smelter_top', bottom: 'modjs:block/smelter_bottom', side: 'modjs:block/smelter_side', north: 'modjs:block/smelter_front' })
    .model('orientable')                      // optional
    .inventory({ slots: [{ x: 56, y: 17 }, { x: 56, y: 53 }, { x: 116, y: 35 }], stackLimit: 64, values: ['progress', 'max'] })
    .onTick(10, be => { ... })                // server, every 10 ticks (default 1)
    .onButton((id, be, player) => { ... })    // a screen button was pressed
    .screen(s => { s.background(0xC6C6C6); s.text('Smelter', { x: 8, y: 6, left: true }); s.bar({ value: 'progress', max: 'max', x: 79, y: 35, width: 24, height: 6, color: 0x3fdc5a }); s.button('Go', { id: 'go', x: 60, y: 70, width: 40, height: 14 }); })
    .tab('modjs:ruby_tab');
});
```

- **Textures.** `texture(id)` stays (one texture on all faces). `textures({...})` takes `all`, `top`, `bottom`, `side`, and the four sides `north`, `south`, `east`, `west`; the most specific key wins (`north` over `side` over `all`). Missing faces fall back to `all`, then to the first texture given.
- **Model.** `model(name)`: `cube_all` (default with one texture), `cube` (six faces), `cube_column` (`top`/`bottom` shared as `end`, `side`), `cube_bottom_top`, `orientable` (front texture is `north`, the block turns to face the player and gets a `facing` property), or a model id such as `modjs:block/smelter_model` that the script supplies in `assets/` (then `textures` is only used for particles and the item).
- **Block entity.** `inventory({...})` (or `entity()` for a block entity without slots) turns the block into a block entity block. `container(rows)` keeps working unchanged (a chest menu).
  - `slots`: a number (laid out as a row in the screen) or a list of `{x, y}` positions relative to the screen's top left. `stackLimit` caps stacks (default 64). Menus drop their items on break.
  - `values`: names of integers that are saved with the block and sent to an open screen every tick (progress bars). `be.getValue(name)`, `be.setValue(name, n)`.
  - `be` (script object, server and client side): `x`, `y`, `z`, `blockId`, `size`, `getItemId(slot)`, `getCount(slot)`, `setItem(slot, id, count)`, `clearSlot(slot)`, `shrink(slot, n)`, `insert(id, count)` (puts into the first fitting slot, returns what did not fit), `getData(key)` / `setData(key, value)` (numbers, strings, booleans; saved with the block, synced to clients), `sync()` (forces a client update), `getValue`/`setValue`, `markChanged()`.
  - `onTick(interval, fn)`: runs on the server only, for loaded block entities, every `interval` ticks.
  - Hoppers and other mods see the block as a normal `Container`; the stack limit and slot count apply.
- **Screens.** The menu is a real `AbstractContainerMenu` (one `MenuType` per scripted block, so no extra packet is needed to learn the layout) with the script's slots and the player inventory below. Positions on a block screen count from the panel's top left; `text` with `left: true` starts at `x`, without it the text is centered on the panel. `screen(fn)` is built on `TitleScreenConfig` (the existing screen API): `background(color or texture)`, `text(...)`, `button(label, { id, ... })` (the options object takes the place of the action) and a new `bar({...})`, plus `size(width, height)` (default 176x166). Button presses go to the server as the menu's standard button click, and the `onButton` callback receives the button `id`. `bar.value` and `bar.max` are value names, numbers, or both, as given in `values`.
- A block without `screen` and without a layout gets the default panel (title, slots, player inventory).

## 3. What changes where

| Area | Files |
|---|---|
| Common (Minecraft-free) | `registry/BlockBuilderJS` (textures, model, inventory, callbacks), `registry/RegistryManager` (per-face model and blockstate JSON), `client/TitleScreenConfig` (id on buttons, bars, size), `block/ScriptBlockLogic` (tick and button dispatch, data map), `client/overlay/world/*` (elements, handles, culling, cap, sort, `Overlay.at`, templates), `ScriptBlockEntityJS` (the `be` interface) |
| Per build (10) | `registry/ContainerBlocks` -> scripted block, entity, menu; `registry/ContentRegistry` (block, entity, menu type); `client/WorldCanvas` + hook; `client` screen registration and the container screen; `ModJS` wiring |

## 4. Test plan

- Unit tests (`common/`): world overlay layout, culling, cap and nearest-first order, data parsing, template refresh; per-face model and blockstate JSON for each model name; builder validation.
- Headless self test (every build): a scripted block with a slot list places, takes an item through `be.insert`, ticks, saves and loads its data and slots, opens a menu on a fake player and takes a button click.
- Client frames (NeoForge 1.21.1 reference, plus the builds where the hook differs: Forge 1.21.1, Forge 1.20.1, NeoForge 26.3, Forge 26.3): a world overlay with text, item, bar and texture over a scripted block and over a plain stone block, and the scripted block's own screen, written by in-game screenshots (`Client.screenshot`). Driver: `python scripts/world_overlay_tour.py <module>`. **Built:** a text background drawn by the font z-fights with the glyphs at a distance, so the background is a separate quad one layer behind (`Z_STEP`).

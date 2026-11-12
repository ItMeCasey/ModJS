![ModJS](https://raw.githubusercontent.com/ItMeCasey/ModJS/main/branding/banners/banner.png)

**Script Minecraft in JavaScript. Add items, blocks with real inventories, recipes and whole dimensions, draw on the screen and in the world, react to gameplay and rebuild the main menu, all from plain text files.**

ModJS is a scripting mod for Forge and NeoForge, built on the Rhino JavaScript engine. Drop `.js` files in a `modjs/` folder and the game runs them. There is no Java, no build step and no mod project. If you know KubeJS, you already know the layout.

**Full guide:** [itmecasey.com/wiki/modjs](https://itmecasey.com/wiki/modjs/)

```js
StartupEvents.registry('item', event => event.create('ruby').displayName('Ruby'));
ServerEvents.recipes(event => event.shaped('modjs:ruby', ['DD', 'DD'], { D: 'minecraft:diamond' }));
```

![Features](https://raw.githubusercontent.com/ItMeCasey/ModJS/main/branding/banners/h_features.png)

- **Items, blocks and fluids:** register your own, with tools, armor, slabs, stairs, walls, fences and storage blocks. Models, language entries and block loot are generated for you.
- **Scripted blocks with inventories:** give a block its own texture on every face, pick a model (cube, column, top and bottom, one that turns to face the player, or your own), and add a block entity with item slots, a tick handler and saved data. Open it with a screen you build yourself. [Scripted blocks](https://itmecasey.com/wiki/modjs/?p=scripted-blocks)
- **Recipes, tags and loot:** shaped, shapeless, smelting, stonecutting and smithing, plus remove and replace with filters. Edit any loot table or add new ones.
- **World generation:** add ore veins and features, remove them, and change which mobs spawn where.
- **Gameplay events:** block broken, player joined, chat sent, item used, entity hurt, and many more. Cancel an action or rewrite it.
- **Dimensions:** add, edit, duplicate and drop dimensions, or create them while the server runs. Give every player who joins their own.
- **Game state:** read and edit chests, furnaces and signs, change game rules, work with the scoreboard, and save values between restarts.
- **Look-at info:** `InfoEvents` adds your own lines, bars and item icons to what Show Me What This Is and Jade show for a block or entity, yours or another mod's. [Block and entity info](https://itmecasey.com/wiki/modjs/?p=block-info)
- **Recipe viewers:** hide items, add information pages and hide categories in JEI, EMI, REI and Barely Enough Items from your scripts. [Recipe viewers](https://itmecasey.com/wiki/modjs/?p=recipe-viewers)
- **World overlays:** `Overlay.at(x, y, z)` draws text, item icons, progress bars and textures in the world over a block. Size them in blocks, make them face the player or lie flat on any side of the block, fade, layer and animate them. Distance limits and an element cap keep them cheap. [World overlays](https://itmecasey.com/wiki/modjs/?p=world-overlays)
- **Screen overlay:** progress bars with a value, a color blend, a border and a label such as `{percent}`, plus text, rectangles, images and item icons that you change by setting a field. `ClientEvents.hud` draws your own text and shapes every frame. [Overlay](https://itmecasey.com/wiki/modjs/?p=overlay)
- **Client side:** rename things, add tooltips, change the splash text, add your own debug lines, rebindable keys in the Controls menu, toasts, pictures on buttons, and screens of your own with buttons and text boxes. Rebuild the whole title screen.

![How it works](https://raw.githubusercontent.com/ItMeCasey/ModJS/main/branding/banners/h_scripts.png)

Scripts go in three folders inside `modjs/`:

- `startup_scripts` run once while the game loads and register content.
- `server_scripts` run when a world loads and reload with `/modjs reload`.
- `client_scripts` run on the client and reload with F3+T.

`/modjs hand` prints the id of the item you hold, `/modjs errors` lists script errors and `/modjs trace on` prints every event as it fires. Editors such as VS Code complete every name, because ModJS writes a type file on each launch.

![Unify](https://raw.githubusercontent.com/ItMeCasey/ModJS/main/branding/banners/h_unify.png)

Three mods add tin and you end up with three tin ingots that do not stack. **Unify** works like the old Ore Dictionary: it picks one canonical item per material and converts every recipe, drop, inventory and ore in the world to it.

- Generates items for materials (ingots, nuggets, dusts, plates, gears, rods, wires, coins, raw items, storage blocks and ores), with art recolored from the game's own textures and colors read from the mods you already have.
- Rewrites recipe outputs and inputs, loot from blocks, mobs, chests and fishing, and items already in player inventories.
- Can force new chunks to place the canonical ore.
- Works from a config file with no script at all. [How Unify works](https://itmecasey.com/wiki/modjs/?p=unify)

![Dimensions](https://raw.githubusercontent.com/ItMeCasey/ModJS/main/branding/banners/h_dimensions.png)

```js
PlayerEvents.loggedIn(event => {
  const id = 'mymod:player_' + event.player.getStringUUID().replace(/-/g, '');
  Dimensions.create(id, { generator: 'void' });
  Dimensions.teleport(event.player, id, 0.5, 64, 0.5);
});
```

A hub plus a private dimension for every player, created while the server runs and kept across restarts. [Dimensions at run time](https://itmecasey.com/wiki/modjs/?p=dimensions-runtime)

![FAQ](https://raw.githubusercontent.com/ItMeCasey/ModJS/main/branding/banners/h_faq.png)

**Is it KubeJS?** No. It shares KubeJS's structure so scripts translate with few changes, but it is its own mod, with Unify, run-time dimensions, game rules, scripted screens, world overlays and scripted blocks on top. [Coming from KubeJS](https://itmecasey.com/wiki/modjs/?p=kubejs-parity)

**Can I add a new mob?** Not yet. A mob needs a model, a renderer and AI that a script cannot supply safely.

**Can I use it in my modpack?** Yes.

Source and issues on [GitHub](https://github.com/ItMeCasey/ModJS).

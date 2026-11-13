# ModJS for VS Code

Editor support for ModJS scripts. It reads the files `/modjs dump` writes to `modjs/probe/` and gives the editor typings, id completion, hover text, go to definition and warnings for unknown ids and event names.

## Activation

The extension starts when the workspace contains a `modjs/` folder with a `jsconfig.json`, a `probe/` folder or script folders. Open either the game folder or the `modjs/` folder.

## Files it reads

| Path | Content |
|---|---|
| `probe/*.d.ts` | Typings: `ids.d.ts`, `tags.d.ts`, `states.d.ts`, `recipes.d.ts`, `overloads.d.ts`, `api.d.ts`, and ModJS's own `modjs.d.ts` |
| `probe/data/meta.json` | Minecraft version, loader, mods, time of the dump |
| `probe/data/registries.json` | Ids of every registry |
| `probe/data/tags.json` | Tags of every registry with their entries |
| `probe/data/states.json` | Block state properties and values |
| `probe/data/recipes.json` | Recipe JSON from the loaded data packs |
| `probe/data/commands.json`, `gamerules.json` | Command tree and game rules |
| `probe/data/lang.json`, `assets.json`, `keys.json` | English language keys, resource pack file ids, key mappings (written when the dump ran in a client) |

The built-in JavaScript language service uses `jsconfig.json` to load the `.d.ts` files. If the file does not list `probe/**/*.d.ts`, the extension offers to add it.

## Features

- Completion inside string literals: item, block, fluid, entity, effect, sound, particle and enchantment ids; tag ids (with `#` in ingredients); recipe ids; mod ids; game rule names; language keys. Each entry shows its English name and mod.
- Hover on an id, tag, recipe id or language key.
- Go to definition on an id opens its entry in the dump.
- Warnings for ids that are not in the dump and for event names that `modjs.d.ts` does not declare. Turn off with `modjs.diagnostics.enabled`.
- Snippets with the prefix `modjs-`.
- Status bar item with the dump's Minecraft version, loader and mod count.
- Command `ModJS: Refresh dump` reloads the files. The extension also reloads when anything in `probe/` changes.
- Command `ModJS: Set up project` creates or fixes `jsconfig.json`.

## Settings

| Setting | Default | Meaning |
|---|---|---|
| `modjs.diagnostics.enabled` | `true` | Report unknown ids and event names |
| `modjs.completion.limit` | `4000` | Most ids in one completion list |

## Build

```
npm install
npm test
npm run package
```

`npm run package` writes `modjs-<version>.vsix`.

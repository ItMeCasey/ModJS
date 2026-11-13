# ModJS

Editor support for [ModJS](https://itmecasey.com/wiki/modjs/) scripts. It reads the files that the in-game command `/modjs dump` writes to `modjs/probe/` and uses them for typings, completion, hover text, go to definition and warnings.

## Features

- **Typings.** The generated declaration files in `modjs/probe/` (`ids.d.ts`, `tags.d.ts`, `states.d.ts`, `recipes.d.ts`, `overloads.d.ts`, `api.d.ts`) and ModJS's `modjs.d.ts` are picked up through the project's `jsconfig.json`, so the built-in JavaScript language service completes ModJS globals, events, builders, id unions and block state properties.
- **Completion in strings.** Item, block, fluid, entity, effect, sound, particle and enchantment ids, tag ids (with `#` in ingredients), recipe ids, mod ids, game rule names and language keys. Entries show their English name and the mod. Which kind of id is offered depends on the call the string is in, such as `event.shaped`, `event.remove({ output })`, `ServerEvents.tags` or `Text.translate`.
- **Hover.** Name, id, registries, mod, tags and block state properties for ids; entries of a tag; the JSON of a recipe; the text of a language key.
- **Go to definition.** Opens the dump entry for an id, tag, recipe or language key.
- **Diagnostics.** Warnings for ids that are not in the dump and for event names that `modjs.d.ts` does not declare. Switch off with `modjs.diagnostics.enabled`.
- **Snippets.** Prefix `modjs-` for recipe, tag, registry, block, player, item, language and command events.
- **Status bar.** Minecraft version, loader and mod count of the loaded dump.
- **Project setup.** Creates `jsconfig.json` or adds `probe/**/*.d.ts` to its include list.

The extension starts in a workspace that contains a ModJS `modjs/` folder (or is one).

## Commands

| Command | Action |
|---|---|
| `ModJS: Refresh dump` | Reloads the files in `modjs/probe/` |
| `ModJS: Set up project` | Creates or fixes `jsconfig.json` |

## Settings

| Setting | Default | Meaning |
|---|---|---|
| `modjs.diagnostics.enabled` | `true` | Report unknown ids and event names |
| `modjs.completion.limit` | `4000` | Most ids in one completion list |

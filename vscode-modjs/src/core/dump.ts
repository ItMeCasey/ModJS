import * as fs from 'fs';
import * as path from 'path';

export interface ModEntry { id: string; name: string; version: string }

export interface Meta {
  format: number;
  minecraft: string;
  loader: string;
  modjs: string;
  side: string;
  generatedAt: string;
  clientData: boolean;
  mods: ModEntry[];
  warnings: string[];
  counts?: Record<string, number>;
}

export interface CommandNode { n: string; t?: string; x?: boolean; r?: string; c?: CommandNode[] }

/** Lang key prefixes that name an entry of a registry, tried in order. */
const LANG_PREFIXES: Record<string, string[]> = {
  item: ['item', 'block'],
  block: ['block'],
  fluid: ['fluid_type', 'block'],
  entity_type: ['entity'],
  mob_effect: ['effect'],
  enchantment: ['enchantment'],
  'worldgen/biome': ['biome'],
  creative_mode_tab: ['itemGroup'],
  attribute: ['attribute.name'],
  villager_profession: ['entity.minecraft.villager'],
  potion: ['item.minecraft.potion.effect'],
  menu: ['container'],
  stat_type: ['stat_type'],
  painting_variant: ['painting'],
  instrument: ['instrument'],
  jukebox_song: ['jukebox_song'],
};

export function splitId(id: string): { ns: string; path: string } {
  const colon = id.indexOf(':');
  return colon < 0 ? { ns: 'minecraft', path: id } : { ns: id.slice(0, colon), path: id.slice(colon + 1) };
}

function readJson<T>(file: string, fallback: T): T {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8')) as T;
  } catch {
    return fallback;
  }
}

/** The dump files in `modjs/probe/data/`, loaded for lookups. */
export class DumpIndex {
  meta?: Meta;
  registries: Record<string, string[]> = {};
  tags: Record<string, Record<string, string[]>> = {};
  states: Record<string, Record<string, string[]>> = {};
  gamerules: Record<string, string> = {};
  commands: CommandNode = { n: '' };
  recipes: Record<string, unknown> = {};
  lang: Record<string, string> = {};
  assets: Record<string, string[]> = {};
  keys: string[] = [];
  private sets = new Map<string, Set<string>>();
  private tagSets = new Map<string, Set<string>>();
  private universe?: Set<string>;
  private modsById = new Map<string, ModEntry>();

  static load(probeDir: string): DumpIndex {
    const data = path.join(probeDir, 'data');
    const index = new DumpIndex();
    const meta = readJson<Meta | undefined>(path.join(data, 'meta.json'), undefined);
    if (!meta) {
      return index;
    }
    index.meta = meta;
    index.registries = readJson(path.join(data, 'registries.json'), {});
    index.tags = readJson(path.join(data, 'tags.json'), {});
    index.states = readJson(path.join(data, 'states.json'), {});
    index.gamerules = readJson(path.join(data, 'gamerules.json'), {});
    index.commands = readJson(path.join(data, 'commands.json'), { n: '' });
    index.recipes = readJson(path.join(data, 'recipes.json'), {});
    index.lang = readJson(path.join(data, 'lang.json'), {});
    index.assets = readJson(path.join(data, 'assets.json'), {});
    index.keys = readJson(path.join(data, 'keys.json'), []);
    for (const mod of meta.mods ?? []) {
      index.modsById.set(mod.id, mod);
    }
    return index;
  }

  get loaded(): boolean {
    return this.meta !== undefined;
  }

  registryNames(): string[] {
    return Object.keys(this.registries);
  }

  ids(registry: string): string[] {
    return this.registries[registry] ?? [];
  }

  has(registry: string, id: string): boolean {
    let set = this.sets.get(registry);
    if (!set) {
      set = new Set(this.ids(registry));
      this.sets.set(registry, set);
    }
    return set.has(id);
  }

  tagIds(registry: string): string[] {
    return Object.keys(this.tags[registry] ?? {});
  }

  hasTag(registry: string, id: string): boolean {
    let set = this.tagSets.get(registry);
    if (!set) {
      set = new Set(this.tagIds(registry));
      this.tagSets.set(registry, set);
    }
    return set.has(id);
  }

  tagRegistryNames(): string[] {
    return Object.keys(this.tags);
  }

  mods(): ModEntry[] {
    return this.meta?.mods ?? [];
  }

  mod(id: string): ModEntry | undefined {
    return this.modsById.get(id);
  }

  hasMod(id: string): boolean {
    return this.modsById.has(id);
  }

  /** The English name of an entry from the language files, or undefined when there is none. */
  displayName(registry: string, id: string): string | undefined {
    const { ns, path: p } = splitId(id);
    const dotted = p.replace(/\//g, '.');
    const prefixes = LANG_PREFIXES[registry] ?? [registry.replace(/\//g, '.')];
    for (const prefix of prefixes) {
      const text = this.lang[`${prefix}.${ns}.${dotted}`];
      if (text !== undefined) {
        return text;
      }
    }
    return undefined;
  }

  registriesContaining(id: string): string[] {
    return this.registryNames().filter((name) => this.has(name, id));
  }

  tagRegistriesContaining(id: string): string[] {
    return this.tagRegistryNames().filter((name) => this.hasTag(name, id));
  }

  /** Every id, tag, recipe, asset, loot table and language key the dump knows. */
  isKnown(id: string): boolean {
    if (!this.universe) {
      const all = new Set<string>();
      for (const ids of Object.values(this.registries)) {
        ids.forEach((v) => all.add(v));
      }
      for (const tags of Object.values(this.tags)) {
        Object.keys(tags).forEach((v) => all.add(v));
      }
      Object.keys(this.recipes).forEach((v) => all.add(v));
      for (const ids of Object.values(this.assets)) {
        ids.forEach((v) => all.add(v));
      }
      Object.keys(this.lang).forEach((v) => all.add(v));
      Object.keys(this.gamerules).forEach((v) => all.add(v));
      this.universe = all;
    }
    return this.universe.has(id);
  }

  /** Tags that contain an id, as `registry` and tag id pairs. */
  tagsOf(registry: string, id: string): string[] {
    const found: string[] = [];
    for (const [tag, members] of Object.entries(this.tags[registry] ?? {})) {
      if (members.includes(id)) {
        found.push(tag);
      }
    }
    return found;
  }

  /** The text of the virtual document that go to definition opens for an id. */
  entryText(kind: string, name: string): string {
    if (kind === 'recipe') {
      return JSON.stringify({ recipe: name, ...(this.recipes[name] as object) }, null, 2);
    }
    if (kind === 'tag') {
      const [registry, tag] = [name.slice(0, name.indexOf('|')), name.slice(name.indexOf('|') + 1)];
      return JSON.stringify({ registry, tag, members: this.tags[registry]?.[tag] ?? [] }, null, 2);
    }
    if (kind === 'lang') {
      return JSON.stringify({ key: name, text: this.lang[name] }, null, 2);
    }
    const entry: Record<string, unknown> = { registry: kind, id: name };
    const display = this.displayName(kind, name);
    if (display) {
      entry.name = display;
    }
    const { ns } = splitId(name);
    const mod = this.mod(ns);
    if (mod) {
      entry.mod = `${mod.name} ${mod.version}`;
    }
    const tags = this.tagsOf(kind, name);
    if (tags.length) {
      entry.tags = tags;
    }
    if (this.states[name]) {
      entry.states = this.states[name];
    }
    return JSON.stringify(entry, null, 2);
  }
}

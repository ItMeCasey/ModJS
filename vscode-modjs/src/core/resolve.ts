import { CallContext } from './context';

/** What a string literal at some place in a script is expected to hold. */
export interface Target {
  /** Registries whose ids fit here. */
  registries: string[];
  /** Tag registries whose tags fit here (written with a leading hash in ingredients). */
  tagRegistries: string[];
  /** Tags are written as `#ns:tag` here. */
  hash: boolean;
  recipes: boolean;
  lang: boolean;
  mods: boolean;
  gamerules: boolean;
  /** Offer registry names ('item', 'block') themselves. */
  registryNames?: string[];
  /** Offer tag registry names. */
  tagRegistryNames: boolean;
  /** A short noun for messages: 'item', 'block', ... */
  label: string;
}

function target(partial: Partial<Target> & { label: string }): Target {
  return {
    registries: [], tagRegistries: [], hash: false, recipes: false, lang: false, mods: false, gamerules: false,
    tagRegistryNames: false, ...partial,
  };
}

const RECIPE_METHODS = new Set(['shaped', 'shapeless', 'smelting', 'blasting', 'smoking', 'campfireCooking', 'stonecutting', 'smithing']);
const RECIPE_FILTER_METHODS = new Set(['remove', 'replaceInput', 'replaceOutput']);
const SOUND_METHODS = new Set(['playSound', 'sound']);
const PARTICLE_METHODS = new Set(['particle', 'spawnParticle', 'spawnParticles']);
const EFFECT_METHODS = new Set(['addEffect', 'giveEffect', 'effect', 'removeEffect']);
const ENCHANT_METHODS = new Set(['enchant', 'hasEnchantment', 'addEnchantment']);

const ITEMS = () => target({ registries: ['item'], label: 'item' });
const ITEMS_AND_TAGS = () => target({ registries: ['item'], tagRegistries: ['item'], hash: true, label: 'item' });
const BLOCKS_AND_TAGS = () => target({ registries: ['block'], tagRegistries: ['block'], hash: true, label: 'block' });

/**
 * Decides what a string argument should hold, from the call it is in. `before` is the script text before the string;
 * it tells the tag event which registry its handler works on and the lang event that its keys are language keys.
 */
export function resolveTarget(ctx: CallContext | undefined, before: string): Target | undefined {
  if (!ctx) {
    return undefined;
  }
  const { callee, method, owner, argIndex, property } = ctx;

  if (callee === 'ServerEvents.tags' && argIndex === 0) {
    return target({ tagRegistryNames: true, label: 'tag registry' });
  }
  if (callee === 'StartupEvents.registry' && argIndex === 0) {
    return target({ registryNames: ['item', 'block', 'creative_tab', 'fluid'], label: 'registry' });
  }
  if ((method === 'add' || method === 'remove' || method === 'removeAll') && owner !== '' && !owner.includes('.')) {
    const tags = lastMatch(before, /ServerEvents\.tags\(\s*(['"])([^'"]+)\1/g);
    const lang = before.lastIndexOf('ClientEvents.lang(');
    if (lang >= 0 && (!tags || lang > tags.index)) {
      return method === 'add' && argIndex === 0 ? target({ lang: true, label: 'language key' }) : undefined;
    }
    if (tags) {
      const registry = tags.groups[2].replace(/^minecraft:/, '');
      if (argIndex === 0) {
        return target({ tagRegistries: [registry], label: 'tag' });
      }
      return target({ registries: [registry], tagRegistries: [registry], hash: true, label: registry });
    }
  }
  if (RECIPE_FILTER_METHODS.has(method)) {
    if (property === 'output') {
      return ITEMS();
    }
    if (property === 'input') {
      return ITEMS_AND_TAGS();
    }
    if (property === 'id') {
      return target({ recipes: true, label: 'recipe' });
    }
    if (property === 'mod') {
      return target({ mods: true, label: 'mod' });
    }
    if (property === 'type') {
      return target({ registries: ['recipe_type'], label: 'recipe type' });
    }
    if (method !== 'remove' && argIndex >= 1 && property === '') {
      return ITEMS();
    }
    return undefined;
  }
  if (RECIPE_METHODS.has(method)) {
    if (argIndex === 0) {
      return ITEMS();
    }
    if (method === 'shaped' && argIndex === 1) {
      return undefined;
    }
    return ITEMS_AND_TAGS();
  }
  if (method === 'custom' && property === 'type') {
    return target({ registries: ['recipe_type'], label: 'recipe type' });
  }
  if ((owner === 'Item' || owner === 'Ingredient') && method === 'of' && argIndex === 0) {
    return owner === 'Item' ? ITEMS() : ITEMS_AND_TAGS();
  }
  if (owner === 'BlockEvents' && argIndex === 0) {
    return BLOCKS_AND_TAGS();
  }
  if (owner === 'ItemEvents' && argIndex === 0) {
    return ITEMS_AND_TAGS();
  }
  if (owner === 'EntityEvents' && argIndex === 0 && ['spawned', 'death', 'hurt'].includes(method)) {
    return target({ registries: ['entity_type'], tagRegistries: ['entity_type'], hash: true, label: 'entity type' });
  }
  if (owner === 'LevelEvents' && argIndex === 0 && ['tick', 'loaded', 'unloaded'].includes(method)) {
    return target({ registries: ['dimension'], label: 'dimension' });
  }
  if (method === 'modify' && argIndex === 0 && before.includes('ItemEvents.modification')) {
    return ITEMS();
  }
  if (owner === 'GameRule' && argIndex === 0) {
    return target({ gamerules: true, label: 'game rule' });
  }
  if (owner === 'Text' && method === 'translate' && argIndex === 0) {
    return target({ lang: true, label: 'language key' });
  }
  if (method === 'setBlock' && argIndex === 3) {
    return target({ registries: ['block'], label: 'block' });
  }
  if (SOUND_METHODS.has(method) && argIndex === 0) {
    return target({ registries: ['sound_event'], label: 'sound' });
  }
  if (PARTICLE_METHODS.has(method) && argIndex === 0) {
    return target({ registries: ['particle_type'], label: 'particle' });
  }
  if (EFFECT_METHODS.has(method) && argIndex === 0) {
    return target({ registries: ['mob_effect'], label: 'effect' });
  }
  if (ENCHANT_METHODS.has(method) && argIndex === 0) {
    return target({ registries: ['enchantment'], label: 'enchantment' });
  }
  if (property === 'biomes' || property === 'biome') {
    return target({ registries: ['worldgen/biome'], tagRegistries: ['worldgen/biome'], hash: true, label: 'biome' });
  }
  if (property === 'entity' || property === 'entities') {
    return target({ registries: ['entity_type'], label: 'entity type' });
  }
  return undefined;
}

function lastMatch(text: string, pattern: RegExp): { index: number; groups: string[] } | undefined {
  let found: { index: number; groups: string[] } | undefined;
  for (const m of text.matchAll(pattern)) {
    found = { index: m.index ?? 0, groups: [...m] };
  }
  return found;
}

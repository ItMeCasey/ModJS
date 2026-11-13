import { callContext, stringAt, stringLiterals } from './context';
import { DumpIndex, splitId } from './dump';
import { EventTable } from './events';
import { resolveTarget, Target } from './resolve';

export interface CompletionEntry {
  label: string;
  /** Text that replaces the whole string content. */
  insert: string;
  detail: string;
  documentation: string;
  /** Sort key. */
  sort: string;
}

export interface CompletionResult {
  /** Offsets of the string content the entries replace. */
  start: number;
  end: number;
  entries: CompletionEntry[];
  truncated: boolean;
}

const ID_PATTERN = /^#?[a-z0-9_.-]+:[a-z0-9_./-]+$/;

function describeEntry(index: DumpIndex, registry: string, id: string): { detail: string; documentation: string } {
  const name = index.displayName(registry, id);
  const mod = index.mod(splitId(id).ns);
  const tags = index.tagsOf(registry, id).slice(0, 8);
  const detail = [name, mod ? mod.name : splitId(id).ns].filter(Boolean).join(' · ');
  const lines = [`${registry} \`${id}\``];
  if (tags.length) {
    lines.push(`Tags: ${tags.join(', ')}`);
  }
  return { detail, documentation: lines.join('\n\n') };
}

/** Completion entries for a string at the offset; undefined when the place is not one that takes ids. */
export function completionsAt(text: string, offset: number, index: DumpIndex, limit = 4000): CompletionResult | undefined {
  if (!index.loaded) {
    return undefined;
  }
  const literal = stringAt(text, offset);
  if (!literal) {
    return undefined;
  }
  const typed = text.slice(literal.start, offset);
  const ctx = callContext(text, literal.quoteStart);
  const target = resolveTarget(ctx, text.slice(0, literal.quoteStart));
  const entries: CompletionEntry[] = [];
  const hashTyped = typed.startsWith('#');

  const addIds = (registry: string) => {
    for (const id of index.ids(registry)) {
      const info = describeEntry(index, registry, id);
      entries.push({ label: id, insert: id, ...info, sort: `1${id}` });
    }
  };
  const addTags = (registry: string, hash: boolean) => {
    for (const tag of index.tagIds(registry)) {
      const text2 = hash ? `#${tag}` : tag;
      entries.push({
        label: text2,
        insert: text2,
        detail: `${registry} tag · ${index.tags[registry][tag].length} entries`,
        documentation: `Tag \`${tag}\` of ${registry}`,
        sort: `2${tag}`,
      });
    }
  };

  if (target) {
    if (!(hashTyped && !target.hash)) {
      if (!hashTyped) {
        target.registries.forEach(addIds);
      }
      target.tagRegistries.forEach((r) => addTags(r, target.hash));
      if (target.recipes) {
        for (const id of Object.keys(index.recipes)) {
          entries.push({ label: id, insert: id, detail: 'recipe', documentation: `Recipe \`${id}\``, sort: `1${id}` });
        }
      }
      if (target.lang) {
        for (const [key, value] of Object.entries(index.lang)) {
          entries.push({ label: key, insert: key, detail: value, documentation: value, sort: `1${key}` });
        }
      }
      if (target.mods) {
        for (const mod of index.mods()) {
          entries.push({ label: mod.id, insert: mod.id, detail: `${mod.name} ${mod.version}`, documentation: mod.name, sort: `1${mod.id}` });
        }
      }
      if (target.gamerules) {
        for (const [rule, type] of Object.entries(index.gamerules)) {
          entries.push({ label: rule, insert: rule, detail: type, documentation: `Game rule \`${rule}\` (${type})`, sort: `1${rule}` });
        }
      }
      (target.registryNames ?? []).forEach((name) => entries.push({ label: name, insert: name, detail: 'registry', documentation: '', sort: `1${name}` }));
      if (target.tagRegistryNames) {
        index.tagRegistryNames().forEach((name) => entries.push({ label: name, insert: name, detail: 'tag registry', documentation: '', sort: `1${name}` }));
      }
    }
  } else if (/^#?[a-z0-9_.-]+:/.test(typed) && index.hasMod(typed.replace(/^#/, '').split(':')[0])) {
    // Outside a known call, only offer ids once a known mod namespace has been typed.
    for (const registry of ['item', 'block', 'fluid', 'entity_type']) {
      addIds(registry);
    }
  } else {
    return undefined;
  }
  const needle = typed.toLowerCase();
  let filtered = needle ? entries.filter((e) => e.label.toLowerCase().includes(needle) || e.detail.toLowerCase().includes(needle)) : entries;
  filtered.sort((a, b) => (a.sort < b.sort ? -1 : a.sort > b.sort ? 1 : 0));
  const truncated = filtered.length > limit;
  if (truncated) {
    filtered = filtered.slice(0, limit);
  }
  return { start: literal.start, end: literal.end, entries: filtered, truncated };
}

export interface IdMatch {
  kind: string;
  name: string;
}

/** What a string literal names in the dump: an id of some registry, a tag, a recipe or a language key. */
export function identify(index: DumpIndex, value: string, target?: Target): IdMatch[] {
  const matches: IdMatch[] = [];
  const isTag = value.startsWith('#');
  const id = isTag ? value.slice(1) : value;
  if (isTag) {
    for (const registry of index.tagRegistriesContaining(id)) {
      matches.push({ kind: 'tag', name: `${registry}|${id}` });
    }
    return matches;
  }
  const preferred = target?.registries ?? [];
  for (const registry of index.registriesContaining(id)) {
    matches.push({ kind: registry, name: id });
  }
  matches.sort((a, b) => Number(preferred.includes(b.kind)) - Number(preferred.includes(a.kind)));
  if (index.recipes[id] !== undefined) {
    matches.push({ kind: 'recipe', name: id });
  }
  if (index.lang[id] !== undefined) {
    matches.push({ kind: 'lang', name: id });
  }
  return matches;
}

export function hoverAt(text: string, offset: number, index: DumpIndex): string | undefined {
  if (!index.loaded) {
    return undefined;
  }
  const literal = stringAt(text, offset);
  if (!literal || literal.value === '') {
    return undefined;
  }
  const ctx = callContext(text, literal.quoteStart);
  const target = resolveTarget(ctx, text.slice(0, literal.quoteStart));
  const matches = identify(index, literal.value, target);
  if (matches.length === 0) {
    return undefined;
  }
  const first = matches[0];
  if (first.kind === 'tag') {
    const [registry, tag] = first.name.split('|');
    const members = index.tags[registry]?.[tag] ?? [];
    const sample = members.slice(0, 12).map((m) => `\`${m}\``).join(', ');
    return `**#${tag}** (${registry} tag, ${members.length} entries)\n\n${sample}${members.length > 12 ? ', ...' : ''}`;
  }
  if (first.kind === 'recipe') {
    return `**Recipe** \`${first.name}\`\n\n\`\`\`json\n${JSON.stringify(index.recipes[first.name], null, 2).slice(0, 1500)}\n\`\`\``;
  }
  if (first.kind === 'lang') {
    return `**${first.name}**\n\n${index.lang[first.name]}`;
  }
  const lines: string[] = [];
  const name = index.displayName(first.kind, first.name);
  lines.push(`**${name ?? first.name}**`);
  lines.push(`\`${first.name}\` · ${matches.filter((m) => m.kind !== 'recipe' && m.kind !== 'lang').map((m) => m.kind).join(', ')}`);
  const mod = index.mod(splitId(first.name).ns);
  if (mod) {
    lines.push(`${mod.name} ${mod.version}`);
  }
  const tags = index.tagsOf(first.kind, first.name);
  if (tags.length) {
    lines.push(`Tags: ${tags.slice(0, 12).join(', ')}${tags.length > 12 ? ', ...' : ''}`);
  }
  const states = index.states[first.name];
  if (states) {
    lines.push(`States: ${Object.entries(states).map(([k, v]) => `${k} (${v.length <= 6 ? v.join('/') : v.length + ' values'})`).join(', ')}`);
  }
  return lines.join('\n\n');
}

export interface Problem {
  start: number;
  end: number;
  message: string;
  severity: 'warning' | 'error';
}

/** Unknown ids in places that take a known kind of id, unknown ids of known mods anywhere, and unknown event names. */
export function diagnose(text: string, index: DumpIndex, events: EventTable): Problem[] {
  const problems: Problem[] = [];
  for (const [group, names] of events) {
    const pattern = new RegExp(`\\b${group}\\.(\\w+)\\s*\\(`, 'g');
    for (const m of text.matchAll(pattern)) {
      if (!names.has(m[1]) && events.size > 0) {
        const start = (m.index ?? 0) + group.length + 1;
        problems.push({ start, end: start + m[1].length, message: `Unknown event ${group}.${m[1]}`, severity: 'warning' });
      }
    }
  }
  if (!index.loaded) {
    return problems;
  }
  for (const literal of stringLiterals(text)) {
    const value = literal.value;
    if (!ID_PATTERN.test(value)) {
      continue;
    }
    const ctx = callContext(text, literal.quoteStart);
    const target = resolveTarget(ctx, text.slice(0, literal.quoteStart));
    const isTag = value.startsWith('#');
    const id = isTag ? value.slice(1) : value;
    const { ns } = splitId(id);
    if (target && (target.registries.length || target.tagRegistries.length || target.recipes)) {
      const ok = isTag
        ? target.hash && target.tagRegistries.some((r) => index.hasTag(r, id))
        : target.registries.some((r) => index.has(r, id)) || (!target.hash && target.tagRegistries.some((r) => index.hasTag(r, id)))
          || (target.recipes && index.recipes[id] !== undefined);
      if (!ok && (index.hasMod(ns) || ns === 'minecraft' || isTag)) {
        problems.push({ start: literal.start, end: literal.end, message: `Unknown ${isTag ? 'tag' : target.label} '${value}'`, severity: 'warning' });
      }
      continue;
    }
    if (target) {
      continue;
    }
    if (index.hasMod(ns) && !isTag && !index.isKnown(id)) {
      problems.push({ start: literal.start, end: literal.end, message: `Unknown id '${value}'`, severity: 'warning' });
    }
  }
  return problems;
}

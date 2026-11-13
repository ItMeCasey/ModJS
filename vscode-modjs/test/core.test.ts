import * as assert from 'node:assert/strict';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { test } from 'node:test';
import { callContext, frameAt, stringAt, stringLiterals } from '../src/core/context';
import { DumpIndex } from '../src/core/dump';
import { parseEvents } from '../src/core/events';
import { completionsAt, diagnose, hoverAt, identify } from '../src/core/features';
import { addProbeInclude, DEFAULT_JSCONFIG, findModjsRoot, includesProbe, statusText } from '../src/core/project';
import { resolveTarget } from '../src/core/resolve';

function makeDump(): string {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'modjs-ext-'));
  const data = path.join(root, 'probe', 'data');
  fs.mkdirSync(data, { recursive: true });
  const write = (name: string, value: unknown) => fs.writeFileSync(path.join(data, name), JSON.stringify(value));
  write('meta.json', {
    format: 1, minecraft: '1.21.1', loader: 'neoforge', modjs: '2026.11.37', side: 'client+server', generatedAt: '2026-11-13T10:00:00Z', clientData: true,
    mods: [{ id: 'minecraft', name: 'Minecraft', version: '1.21.1' }, { id: 'create', name: 'Create', version: '6.0' }], warnings: [],
  });
  write('registries.json', {
    item: ['minecraft:apple', 'minecraft:stone', 'minecraft:oak_log', 'create:cog'],
    block: ['minecraft:stone', 'minecraft:oak_stairs'],
    recipe_type: ['minecraft:crafting_shaped'],
    sound_event: ['minecraft:entity.pig.ambient'],
  });
  write('tags.json', { item: { 'minecraft:logs': ['minecraft:oak_log'], 'c:gems': ['minecraft:apple'] }, block: { 'minecraft:mineable/pickaxe': ['minecraft:stone'] } });
  write('states.json', { 'minecraft:oak_stairs': { facing: ['north', 'south'] } });
  write('gamerules.json', { keepInventory: 'boolean' });
  write('recipes.json', { 'minecraft:stone_sword': { type: 'minecraft:crafting_shaped' } });
  write('lang.json', { 'item.minecraft.stone': 'Stone', 'block.minecraft.stone': 'Stone', 'item.create.cog': 'Cogwheel', 'gui.done': 'Done' });
  write('assets.json', { texture: ['minecraft:block/stone'] });
  write('keys.json', ['key.jump']);
  return root;
}

const dump = makeDump();
const index = DumpIndex.load(path.join(dump, 'probe'));
const events = parseEvents('declare const ServerEvents: {\n  recipes(handler: Handler<RecipesEvent>): void;\n  tags(registry: string, handler: Handler<TagsEvent>): void;\n};\ndeclare const BlockEvents: {\n  broken(handler: Handler<GameEvent>): void;\n};\n');

function at(source: string): { text: string; offset: number } {
  const offset = source.indexOf('|');
  return { text: source.replace('|', ''), offset };
}

test('dump index loads every data file and answers lookups', () => {
  assert.ok(index.loaded);
  assert.equal(index.meta?.minecraft, '1.21.1');
  assert.equal(statusText(index), 'MC 1.21.1 · neoforge · 2 mods');
  assert.ok(index.has('item', 'minecraft:apple'));
  assert.ok(!index.has('item', 'minecraft:nope'));
  assert.equal(index.displayName('item', 'create:cog'), 'Cogwheel');
  assert.equal(index.displayName('block', 'minecraft:stone'), 'Stone');
  assert.deepEqual(index.tagsOf('item', 'minecraft:oak_log'), ['minecraft:logs']);
  assert.ok(index.isKnown('minecraft:block/stone'));
  assert.ok(index.isKnown('gui.done'));
});

test('a folder without a dump gives an empty index and the status says so', () => {
  const empty = DumpIndex.load(path.join(os.tmpdir(), 'modjs-ext-does-not-exist'));
  assert.ok(!empty.loaded);
  assert.equal(statusText(empty), 'ModJS: no dump');
});

test('string scanner finds literals, skips comments and handles escapes', () => {
  const text = "a('x'); // 'no'\n/* 'no' */ b(\"y\\\"z\", `t`)";
  assert.deepEqual(stringLiterals(text).map((s) => s.value), ['x', 'y\\"z', 't']);
  const found = stringAt("f('minecraft:st')", 14);
  assert.equal(found?.value, 'minecraft:st');
  assert.equal(stringAt("f('a')", 0), undefined);
});

test('call context reports callee, argument index and object property', () => {
  const { text } = at("event.shaped('a', ['AAA'], { A: 'b' });");
  const q = text.indexOf("'b'");
  const ctx = callContext(text, q);
  assert.equal(ctx?.callee, 'event.shaped');
  assert.equal(ctx?.method, 'shaped');
  assert.equal(ctx?.argIndex, 2);
  assert.equal(ctx?.property, 'A');
  const second = callContext(text, text.indexOf("'AAA'"));
  assert.equal(second?.argIndex, 1);
  assert.equal(second?.inArray, true);
  assert.equal(frameAt('x(', 2).length, 1);
});

test('targets follow the call: recipes, filters, tags, lang and others', () => {
  const t1 = resolveTarget({ callee: 'event.shaped', method: 'shaped', owner: 'event', argIndex: 0, property: '', inArray: false }, '');
  assert.deepEqual(t1?.registries, ['item']);
  assert.equal(t1?.hash, false);
  const t2 = resolveTarget({ callee: 'event.shapeless', method: 'shapeless', owner: 'event', argIndex: 1, property: '', inArray: true }, '');
  assert.equal(t2?.hash, true);
  const t3 = resolveTarget({ callee: 'event.remove', method: 'remove', owner: 'event', argIndex: 0, property: 'output', inArray: false }, '');
  assert.deepEqual(t3?.registries, ['item']);
  const t4 = resolveTarget({ callee: 'event.remove', method: 'remove', owner: 'event', argIndex: 0, property: 'id', inArray: false }, '');
  assert.equal(t4?.recipes, true);
  const before = "ServerEvents.tags('item', event => {\n  ";
  const t5 = resolveTarget({ callee: 'event.add', method: 'add', owner: 'event', argIndex: 0, property: '', inArray: false }, before);
  assert.deepEqual(t5?.tagRegistries, ['item']);
  const t6 = resolveTarget({ callee: 'event.add', method: 'add', owner: 'event', argIndex: 1, property: '', inArray: false }, before);
  assert.deepEqual(t6?.registries, ['item']);
  const t7 = resolveTarget({ callee: 'event.add', method: 'add', owner: 'event', argIndex: 0, property: '', inArray: false }, "ClientEvents.lang('en_us', event => {\n  ");
  assert.equal(t7?.lang, true);
  assert.equal(resolveTarget({ callee: 'foo', method: 'foo', owner: '', argIndex: 0, property: '', inArray: false }, ''), undefined);
  assert.equal(resolveTarget(undefined, ''), undefined);
});

test('completion lists items with names and mod, filtered by what is typed', () => {
  const { text, offset } = at("ServerEvents.recipes(event => { event.smelting('minecraft:|', 'x'); });");
  const result = completionsAt(text, offset, index);
  assert.ok(result);
  const labels = result.entries.map((e) => e.label);
  assert.ok(labels.includes('minecraft:stone'));
  assert.ok(!labels.includes('create:cog'));
  assert.equal(result.entries.find((e) => e.label === 'minecraft:stone')?.detail, 'Stone · Minecraft');
  assert.ok(text.slice(result.start, result.end).startsWith('minecraft:'));
});

test('ingredient positions also offer tags with a hash', () => {
  const { text, offset } = at("ServerEvents.recipes(event => { event.smelting('minecraft:glass', '|'); });");
  const labels = completionsAt(text, offset, index)!.entries.map((e) => e.label);
  assert.ok(labels.includes('#minecraft:logs'));
  assert.ok(labels.includes('create:cog'));
  const hashed = at("ServerEvents.recipes(event => { event.smelting('minecraft:glass', '#|'); });");
  const onlyTags = completionsAt(hashed.text, hashed.offset, index)!.entries.map((e) => e.label);
  assert.ok(onlyTags.every((l) => l.startsWith('#')));
});

test('tag event completes tag ids first and then member ids of that registry', () => {
  const first = at("ServerEvents.tags('item', event => { event.add('|'); });");
  assert.ok(completionsAt(first.text, first.offset, index)!.entries.some((e) => e.label === 'c:gems'));
  const second = at("ServerEvents.tags('item', event => { event.add('c:gems', '|'); });");
  const labels = completionsAt(second.text, second.offset, index)!.entries.map((e) => e.label);
  assert.ok(labels.includes('minecraft:apple') && !labels.includes('minecraft:oak_stairs'));
});

test('lang keys, game rules and sounds complete in their calls', () => {
  const lang = at("Text.translate('|')");
  assert.ok(completionsAt(lang.text, lang.offset, index)!.entries.some((e) => e.label === 'gui.done' && e.detail === 'Done'));
  const rule = at("GameRule.get('|')");
  assert.ok(completionsAt(rule.text, rule.offset, index)!.entries.some((e) => e.label === 'keepInventory'));
  const sound = at("player.playSound('|')");
  assert.ok(completionsAt(sound.text, sound.offset, index)!.entries.some((e) => e.label === 'minecraft:entity.pig.ambient'));
});

test('no completion in unrelated strings, and the limit truncates', () => {
  const plain = at("console.log('|')");
  assert.equal(completionsAt(plain.text, plain.offset, index), undefined);
  const generic = at("const id = 'create:|';");
  assert.ok(completionsAt(generic.text, generic.offset, index)!.entries.some((e) => e.label === 'create:cog'));
  const limited = at("Item.of('|')");
  const result = completionsAt(limited.text, limited.offset, index, 2)!;
  assert.equal(result.entries.length, 2);
  assert.equal(result.truncated, true);
});

test('hover describes ids, tags, recipes and lang keys', () => {
  const stone = at("Item.of('minecraft:st|one')");
  const hover = hoverAt(stone.text, stone.offset, index)!;
  assert.ok(hover.includes('Stone') && hover.includes('minecraft:stone'));
  const tag = at("event.shaped('minecraft:stone', ['A'], { A: '#minecraft:lo|gs' })");
  assert.ok(hoverAt(tag.text, tag.offset, index)!.includes('1 entries'));
  const stairs = at("event.setBlock(0, 0, 0, 'minecraft:oak_st|airs')");
  assert.ok(hoverAt(stairs.text, stairs.offset, index)!.includes('facing (north/south)'));
  const unknown = at("Item.of('minecraft:nothing_he|re')");
  assert.equal(hoverAt(unknown.text, unknown.offset, index), undefined);
});

test('identify finds registries, recipes and lang keys for definitions', () => {
  assert.deepEqual(identify(index, 'minecraft:stone').map((m) => m.kind), ['item', 'block']);
  assert.deepEqual(identify(index, 'minecraft:stone_sword').map((m) => m.kind), ['recipe']);
  assert.deepEqual(identify(index, '#c:gems').map((m) => m.kind), ['tag']);
  assert.match(index.entryText('item', 'minecraft:oak_log'), /minecraft:logs/);
  assert.match(index.entryText('tag', 'item|c:gems'), /minecraft:apple/);
});

test('diagnostics flag unknown ids in known places and unknown events', () => {
  const text = [
    "ServerEvents.recipes(event => {",
    "  event.smelting('minecraft:apple', 'minecraft:nothing');",
    "  event.smelting('create:missing', '#c:nope');",
    "  event.remove({ output: 'minecraft:stone' });",
    "});",
    "ServerEvents.recipez(event => {});",
    "BlockEvents.broken(event => {});",
    "const other = 'create:unknown_thing';",
    "const fine = 'create:cog';",
    "console.log('somepack:whatever');",
  ].join('\n');
  const messages = diagnose(text, index, events).map((p) => p.message);
  assert.deepEqual(messages, [
    'Unknown event ServerEvents.recipez',
    "Unknown item 'minecraft:nothing'",
    "Unknown item 'create:missing'",
    "Unknown tag '#c:nope'",
    "Unknown id 'create:unknown_thing'",
  ]);
});

test('event names come from the declarations', () => {
  assert.deepEqual([...events.get('ServerEvents')!], ['recipes', 'tags']);
  assert.deepEqual([...events.keys()], ['ServerEvents', 'BlockEvents']);
});

test('without a dump only event names are checked', () => {
  const empty = new DumpIndex();
  const problems = diagnose("ServerEvents.nope(e => {}); Item.of('x:y');", empty, events);
  assert.equal(problems.length, 1);
});

test('project detection and jsconfig handling', () => {
  assert.equal(findModjsRoot(path.join(dump)), dump);
  const game = fs.mkdtempSync(path.join(os.tmpdir(), 'modjs-game-'));
  assert.equal(findModjsRoot(game), undefined);
  fs.mkdirSync(path.join(game, 'modjs'));
  assert.equal(findModjsRoot(game), path.join(game, 'modjs'));
  assert.ok(includesProbe(DEFAULT_JSCONFIG));
  const old = '{\n  "include": ["startup_scripts/**/*.js"]\n}';
  assert.ok(!includesProbe(old));
  const fixed = addProbeInclude(old)!;
  assert.ok(includesProbe(fixed));
  assert.deepEqual(JSON.parse(fixed).include, ['probe/**/*.d.ts', 'startup_scripts/**/*.js']);
  assert.equal(addProbeInclude('{}'), undefined);
  assert.ok(includesProbe('{}'));
});

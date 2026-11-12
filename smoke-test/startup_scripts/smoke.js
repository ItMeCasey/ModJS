console.log('smoke test: startup script ran, Java sandbox check next');
StartupEvents.registry('item', event => {
  event.create('smoke_item').displayName('Smoke Item').tooltip('From a startup script');
});
StartupEvents.registry('block', event => {
  event.create('smoke_block').hardness(2);
});
StartupEvents.registry('creative_tab', event => event.create('smoke_tab').displayName('Smoke Tab').icon('modjs:smoke_item'));
StartupEvents.registry('item', event => event.create('tabbed_item').tab('modjs:smoke_tab'));
ItemEvents.modification(event => event.modify('minecraft:ender_pearl', item => { item.maxStackSize = 64; item.rarity = 'epic'; }));
StartupEvents.registry('block', event => {
  event.create('test_slab').type('slab').texture('minecraft:block/stone');
  event.create('test_stairs').type('stairs').texture('minecraft:block/stone');
  event.create('test_wall').type('wall').texture('minecraft:block/stone');
  event.create('test_fence').type('fence').texture('minecraft:block/stone');
});
StartupEvents.registry('item', event => {
  event.create('test_pickaxe').tool('pickaxe', 'diamond');
  event.create('test_sword').tool('sword', 'iron');
});
StartupEvents.modifyCreativeTab('minecraft:tools_and_utilities', e => e.add('minecraft:apple'));
StartupEvents.registry('item', event => {
  event.create('test_helmet').armor('helmet', 'iron');
  event.create('test_boots').armor('boots', 'diamond');
});
StartupEvents.registry('fluid', event => {
  event.create('test_goo').color(0x44FF44).luminosity(6).unify('c:goo');
});
Unify.materials({ materials: ['tin', 'ruby'] });
StartupEvents.registry('item', event => {
  event.create('test_unified_rod').unify('c:rods/test_unified');
});
Unify.materials({ materials: ['zinc'], kinds: ['ore', 'crushed', 'clump', 'shard', 'dirty_dust'] });
BlockEvents.modification(event => event.modify('minecraft:stone', b => { b.destroySpeed = 9; b.explosionResistance = 77; b.lightEmission = 5; }));
StartupEvents.registry('tool_tier', event => event.create('ruby').uses(900).speed(8).attackDamage(3).level('diamond').repair('minecraft:diamond'));
StartupEvents.registry('armor_material', event => event.create('ruby').durability(30).defense(3, 8, 6, 3).toughness(1).repair('minecraft:diamond').color(0xD81E3C));
StartupEvents.registry('item', event => {
  event.create('ruby_pickaxe').tool('pickaxe', 'modjs:ruby');
  event.create('ruby_helmet').armor('helmet', 'ruby');
});
StartupEvents.registry('block', event => event.create('test_crate').container(2).hardness(2));
// A scripted block with real slots, synced values, a tick handler, a button and a screen, and one that turns to face the player.
StartupEvents.registry('block', event => {
  event.create('test_machine').hardness(3)
    .textures({ top: 'minecraft:block/furnace_top', bottom: 'minecraft:block/stone', side: 'minecraft:block/furnace_side', north: 'minecraft:block/furnace_front' })
    .model('orientable')
    .inventory({ slots: [{ x: 56, y: 17 }, { x: 56, y: 53 }, { x: 116, y: 35 }], stackLimit: 32, values: ['progress', 'max'] })
    .onTick(1, be => { be.setData('ticks', (be.getData('ticks') || 0) + 1); })
    .onButton((id, be, player) => { be.setData('pressed', id); be.setValue('progress', 40); })
    .screen(s => {
      s.text('Test Machine', { x: 8, y: 6, left: true, color: 0x404040 });
      s.bar({ value: 'progress', max: 'max', x: 79, y: 35, width: 24, height: 6, color: 0x3fdc5a });
      s.button('Go', { id: 'go', x: 70, y: 56, width: 36, height: 14 });
    });
  event.create('test_columns').textures({ top: 'minecraft:block/stone', bottom: 'minecraft:block/dirt', side: 'minecraft:block/oak_log' });
});
StartupEvents.worldTypes(event => {
  event.add('modjs:selftest_void', { name: 'Self Test Void', overworld: { generator: 'void' } });
  event.add('modjs:selftest_hidden', { visible: false, overworld: 'void' });
  event.edit('minecraft:large_biomes', { nether: { generator: 'void' } });
  event.duplicate('minecraft:amplified', 'modjs:selftest_amp', { name: 'Self Test Amplified' });
  event.hide('minecraft:single_biome_surface');
  event.add('modjs:selftest_gone', {});
  event.drop('modjs:selftest_gone');
});

StartupEvents.registry('item', event => {
  event.create('props_item').maxStackSize(16).rarity('rare').glow(true).food(4, 0.6).alwaysEdible();
  event.create('props_unstackable').unstackable();
});
StartupEvents.registry('block', event => {
  event.create('props_block').lightLevel(7).requiresTool(true).resistance(11).hardness(3);
  event.create('props_noitem').noItem();
});
// Registered in a namespace of their own: a full id, and event.namespace() for the plain ids of one handler.
StartupEvents.registry('creative_tab', event => event.create('nsdemo:ns_tab').displayName('Namespaced Tab').icon('nsdemo:ns_gem'));
StartupEvents.registry('item', event => {
  event.create('nsdemo:ns_gem').displayName('Namespaced Gem').tab('nsdemo:ns_tab');
  event.namespace('nsdemo').create('ns_plain').tab('nsdemo:ns_tab');
});
StartupEvents.registry('block', event => event.create('nsdemo:ns_block').hardness(2).tab('nsdemo:ns_tab'));
StartupEvents.registry('fluid', event => event.create('nsdemo:ns_goo').noBucket());

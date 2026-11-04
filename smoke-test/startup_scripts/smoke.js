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

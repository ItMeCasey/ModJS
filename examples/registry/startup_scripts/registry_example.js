// One of each thing a startup script can register: a creative tab, items, a tool tier and a tool, an armor material and a piece
// of armor, blocks (plain, slab, stairs, storage) and a fluid. Everything lands in the "Ruby Things" creative tab.

StartupEvents.registry('creative_tab', event => {
  event.create('ruby_tab').displayName('Ruby Things').icon('modjs:ruby');
});

StartupEvents.registry('item', event => {
  event.create('ruby').displayName('Ruby').maxStackSize(16).food(4, 0.6).tooltip('Shiny').texture('minecraft:item/diamond').tab('modjs:ruby_tab');
});

// A tool tier and an armor material are registered first, then items can use them by name.
StartupEvents.registry('tool_tier', event => {
  event.create('ruby').uses(900).speed(8).attackDamage(3).level('diamond').enchantability(15).repair('minecraft:diamond');
});
StartupEvents.registry('armor_material', event => {
  event.create('ruby').durability(30).defense(3, 8, 6, 3).toughness(1).repair('minecraft:diamond').color(0xD81E3C);
});
StartupEvents.registry('item', event => {
  event.create('ruby_pickaxe').displayName('Ruby Pickaxe').tool('pickaxe', 'modjs:ruby').texture('minecraft:item/diamond_pickaxe').tab('modjs:ruby_tab');
  event.create('ruby_helmet').displayName('Ruby Helmet').armor('helmet', 'ruby').texture('minecraft:item/diamond_helmet').tab('modjs:ruby_tab');
});

StartupEvents.registry('block', event => {
  event.create('ruby_block').displayName('Ruby Block').hardness(5).resistance(6).requiresTool(true).lightLevel(7)
    .texture('minecraft:block/redstone_block').tab('modjs:ruby_tab');
  event.create('ruby_slab').displayName('Ruby Slab').type('slab').texture('minecraft:block/redstone_block').tab('modjs:ruby_tab');
  event.create('ruby_stairs').displayName('Ruby Stairs').type('stairs').texture('minecraft:block/redstone_block').tab('modjs:ruby_tab');
  // A storage block: 2 rows of 9 slots, opens on right-click, drops its items when broken.
  event.create('ruby_crate').displayName('Ruby Crate').container(2).hardness(2).texture('minecraft:block/barrel_side').tab('modjs:ruby_tab');
});

// A fluid registers the source, the flowing form and a liquid block; noBucket() skips the bucket item.
StartupEvents.registry('fluid', event => {
  event.create('ruby_goo').displayName('Ruby Goo').color(0xD81E3C).luminosity(6).density(1500).noBucket();
});

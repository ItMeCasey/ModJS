console.log('server script loaded');
ServerEvents.recipes(event => {
  event.shaped('3x modjs:smoke_item', ['AA', 'AA'], { A: 'minecraft:stick' }).id('modjs:smoke_shaped');
  event.smelting('minecraft:glass', 'minecraft:sand').xp(1).id('modjs:smoke_smelt');
  event.remove({ output: 'minecraft:torch' });
  event.replaceInput({ id: 'minecraft:iron_pickaxe' }, '#c:rods/wooden', 'minecraft:blaze_rod');
});
ServerEvents.tags('item', event => event.add('modjs:smoke_tag', ['modjs:smoke_item', 'minecraft:apple']));
ServerEvents.commandRegistry(event => {
  const Commands = Java.loadClass('net.minecraft.commands.Commands');
  event.register(Commands.literal('smoke').executes(ctx => { console.log('smoke command ran'); return 1; }));
});
ServerEvents.loaded(event => {
  console.log('server loaded; recipes = ' + event.server.getRecipeManager().getRecipes().size());
  Utils.runAfter(5, () => console.log('scheduled task ran'));
});
WorldgenEvents.add(event => {
  event.addOre({ id: 'smoke_ore', block: 'minecraft:sea_lantern', size: 8, count: 40, minY: 0, maxY: 60 });
  event.removeFeature({ id: 'smoke_no_coal', features: 'minecraft:ore_coal_upper' });
  event.addSpawn({ id: 'smoke_cows', entity: 'minecraft:cow', weight: 20 });
  event.removeSpawn({ id: 'smoke_no_zombies', entities: ['minecraft:zombie'] });
});
ServerEvents.blockLootTables(event => {
  event.addSimpleBlock('minecraft:dirt', '2x minecraft:diamond');
  event.add('modjs:smoke_block', t => t.addPool(p => p.addItem('minecraft:stick', [1, 3]).survivesExplosion()));
});
NetworkEvents.dataReceived('hello', event => console.log('network event ' + JSON.stringify(event.data)));
ServerEvents.tags('item', event => event.remove('minecraft:planks', ['minecraft:oak_planks']));
ServerEvents.loaded(event => {
  const ItemTags = Java.loadClass('net.minecraft.tags.ItemTags');
  const Items = Java.loadClass('net.minecraft.world.item.Items');
  console.log('oak in planks tag: ' + Items.OAK_PLANKS.getDefaultInstance().is(ItemTags.PLANKS)
    + ', birch in planks tag: ' + Items.BIRCH_PLANKS.getDefaultInstance().is(ItemTags.PLANKS));
});
ServerEvents.loaded(event => {
  const Items = Java.loadClass('net.minecraft.world.item.Items');
  console.log('ender pearl max stack: ' + Items.ENDER_PEARL.getDefaultInstance().getMaxStackSize());
});

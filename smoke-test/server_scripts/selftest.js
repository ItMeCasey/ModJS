BlockEvents.broken('minecraft:stone', e => { console.log('SELFTEST script: broken ' + e.blockId + ' by ' + e.playerName); e.cancel(); });
PlayerEvents.chat(e => { console.log('SELFTEST script: chat "' + e.message + '"'); e.message = e.message.toUpperCase(); });
PlayerEvents.loggedIn(e => console.log('SELFTEST script: loggedIn ' + e.playerName));
ItemEvents.crafted(e => console.log('SELFTEST script: crafted ' + e.itemId));
EntityEvents.hurt(e => { console.log('SELFTEST script: hurt amount ' + e.amount); e.setAmount(e.amount * 2); });
console.log("SELFTEST script: globals " + new Vec3(1, 2, 3).x + " " + new BlockPos(4, 5, 6).y + " " + (UUID.randomUUID().toString().length) + " " + Direction.UP);
ServerEvents.loaded(e => {
  const before = GameRule.get('keepInventory');
  GameRule.set('keepInventory', !before);
  const after = GameRule.get('keepInventory');
  console.log('SELFTEST script: gamerule ' + before + ' -> ' + after + ' of ' + GameRule.list().length);
  GameRule.set('keepInventory', before);
});
DimensionEvents.add(event => {
  event.addType('modjs:bright', { base: 'minecraft:overworld', set: { ambient_light: 0.5 } });
  event.add('modjs:void_test', { generator: 'void' });
  event.add('modjs:flat_test', { generator: 'flat', layers: [{ block: 'minecraft:stone', height: 3 }], type: 'modjs:bright' });
  event.duplicate('minecraft:the_nether', 'modjs:nether_copy');
  event.add('modjs:dropped', { generator: 'void' });
  event.drop('modjs:dropped');
});
ServerEvents.tags('item', event => {
  // pretend vanilla iron ingots are a second mod's tin ingots, so vanilla recipes exercise the unifier
  event.add('c:ingots/tin', 'minecraft:iron_ingot');
  event.add('c:ores/zinc', ['minecraft:coal_ore', 'minecraft:deepslate_coal_ore']);   // fake foreign zinc ores
});
ServerEvents.tags('fluid', event => event.add('c:goo', 'minecraft:lava'));
Unify.ores();

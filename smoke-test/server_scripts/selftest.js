BlockEvents.broken('minecraft:stone', e => { console.log('SELFTEST script: broken ' + e.blockId + ' by ' + e.playerName); e.cancel(); });
PlayerEvents.decorateChat(e => { e.message = e.message + '?'; });
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
ServerEvents.compostableRecipes(event => event.add(0.85, 'minecraft:stone'));
PlayerEvents.inventoryChanged(e => console.log('SELFTEST script: inventoryChanged slot ' + e.slot + ' ' + e.itemId));
ServerEvents.loaded(() => { if (Platform.mcVersion.startsWith('26')) { console.log('SELFTEST script: facade ' + Blocks.STONE.id); } });
ServerEvents.commandRegistry(event => {
  event.add('selftest_hello <name:word>', ctx => { ctx.reply('hello ' + ctx.arg('name')); return 7; });
  event.add('selftest_pick <n:integer:1:100>', ctx => ctx.arg('n'));
  event.add('selftest_secret', { permission: 4, run: ctx => 1 });
  event.alias('selftest_hello', 'selftest_hi');
  event.remove('seed');
  event.remove('teleport');
  event.remove('time query');
  event.edit('list', { permission: 2 });
  event.edit('help', { run: ctx => { ctx.reply('help was replaced'); return 42; } });
  event.rename('kill', 'selftest_kill');
  console.log('SELFTEST script: commands exist ' + event.exists('time set') + ' ' + event.exists('nope') + ' ' + (event.list().length > 10));
});
ServerEvents.loaded(() => {
  Scoreboard.addObjective('st_points', 'Points');
  Scoreboard.set('Alice', 'st_points', 5);
  Scoreboard.add('Alice', 'st_points', 3);
  Scoreboard.addTeam('st_team');
  Scoreboard.joinTeam('Alice', 'st_team');
  Scoreboard.setTeamColor('st_team', 'red');
  console.log('SELFTEST script: scoreboard ' + Scoreboard.get('Alice', 'st_points') + ' ' + Scoreboard.teamOf('Alice'));
});

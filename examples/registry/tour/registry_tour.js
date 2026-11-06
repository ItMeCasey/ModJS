// Development only, part of the client tour (scripts/info_tour.py). After the info scenes it shows what examples/registry registers,
// then runs /modjs hand, toolbar, inventory and reload through the server's own command dispatcher and logs "INFO-TOUR ..." markers.
PlayerEvents.loggedIn(event => {
  const run = command => event.runCommand(command);
  const me = event.player.getName().getString();
  const bx = Math.floor(event.player.getX());
  const by = Math.round(event.player.getY());
  const bz = Math.floor(event.player.getZ()) + 3;
  const start = 1000;

  Utils.runAfter(start, () => {
    // The info scenes left one block in the middle of the floor; clear it, then build the row.
    run('setblock ' + bx + ' ' + by + ' ' + bz + ' minecraft:air');
    run('summon minecraft:armor_stand ' + (bx + 0.5) + ' ' + by + ' ' + (bz + 2.5) + ' {NoGravity:1b,Rotation:[180f,0f]}');
    run('setblock ' + (bx - 2) + ' ' + by + ' ' + bz + ' modjs:ruby_block');
    run('setblock ' + (bx - 1) + ' ' + by + ' ' + bz + ' modjs:ruby_slab');
    run('setblock ' + bx + ' ' + by + ' ' + bz + ' modjs:ruby_stairs');
    run('setblock ' + (bx + 1) + ' ' + by + ' ' + bz + ' modjs:ruby_crate');
    // The goo goes in a little glass pen (the crate is its west wall) so it does not flood the floor and stays visible.
    run('setblock ' + (bx + 3) + ' ' + by + ' ' + bz + ' minecraft:glass');
    run('setblock ' + (bx + 2) + ' ' + by + ' ' + (bz - 1) + ' minecraft:glass');
    run('setblock ' + (bx + 2) + ' ' + by + ' ' + (bz + 1) + ' minecraft:glass');
    run('setblock ' + (bx + 2) + ' ' + by + ' ' + bz + ' modjs:ruby_goo');
    run('item replace entity @e[type=minecraft:armor_stand,limit=1,sort=nearest] armor.head with modjs:ruby_helmet');
    run('item replace entity ' + me + ' hotbar.0 with modjs:ruby 16');
    run('item replace entity ' + me + ' hotbar.1 with modjs:ruby_pickaxe');
    run('item replace entity ' + me + ' hotbar.2 with modjs:ruby_helmet');
    run('item replace entity ' + me + ' hotbar.3 with modjs:ruby_block 5');
    const look = (bx + 0.5) + ' ' + (by + 0.5) + ' ' + (bz + 0.5);
    run('execute as ' + me + ' at ' + me + ' run tp @s ~ ~ ~ facing ' + look);
    run('rotate ' + me + ' facing ' + look);
    run('execute as ' + me + ' at ' + me + ' run tp @s ~ ~ ~ 0 20');
    run('rotate ' + me + ' 0 20');
  });
  Utils.runAfter(start + 120, () => console.log('INFO-TOUR READY registry'));

  Utils.runAfter(start + 200, () => {
    run('item replace entity ' + me + ' weapon.mainhand with minecraft:diamond_sword');
    run('item replace entity ' + me + ' hotbar.1 with minecraft:apple 5');
    console.log('INFO-TOUR COMMANDS hand');
    run('execute as ' + me + ' run modjs hand');
    console.log('INFO-TOUR COMMANDS toolbar');
    run('execute as ' + me + ' run modjs toolbar');
    console.log('INFO-TOUR COMMANDS inventory');
    run('execute as ' + me + ' run modjs inventory');
  });

  // A death rolls the player's loot table. The Forge 1.21.1 dev client once crashed there with Forge's own
  // "Can not retrieve LootModifierManager until resources have loaded once", so the tour checks it on every build.
  Utils.runAfter(start + 1300, () => {
    console.log('INFO-TOUR DEATH now');
    run('kill ' + me);
  });

  // The driver writes server_scripts/zz_reload_probe.js when it sees this line; the reload then has to pick it up. A reload
  // drops scheduled tasks, so the probe script itself logs the final "INFO-TOUR DONE".
  Utils.runAfter(start + 1400, () => console.log('INFO-TOUR RELOAD-READY'));
  Utils.runAfter(start + 1600, () => {
    console.log('INFO-TOUR RELOAD now');
    run('modjs reload');
  });
});

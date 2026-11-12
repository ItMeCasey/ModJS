// Development only: the screenshot tour for custom namespaces (scripts/namespace_tour.py). On login it builds a stone floor, puts
// nsdemo:crystal and modjs:crystal side by side, gives the player nsdemo:gem and modjs:gem in the hotbar, and logs "NS-TOUR ..." markers.
(function () {
  const J = name => Java.loadClass(name);
  DimensionEvents.add(event => {
    event.addType('modjs:bright', {base: 'minecraft:overworld', set: {ambient_light: 0.5}});
    event.add('modjs:flat_test', {generator: 'flat', layers: [{block: 'minecraft:stone', height: 3}], type: 'modjs:bright'});
  });
  PlayerEvents.loggedIn(event => {
    const run = command => event.runCommand(command);
    const me = event.player.getName().getString();
    const bx = Math.floor(event.player.getX());
    const by = Math.round(event.player.getY());
    const bz = Math.floor(event.player.getZ()) + 3;
    const px = Math.floor(event.player.getX());
    const pz = Math.floor(event.player.getZ());
    let version = '';
    try { version = String(J('net.minecraft.SharedConstants').getCurrentVersion().getName()); } catch (e) {}
    if (version.indexOf('1.') === 0) {
      run('gamerule sendCommandFeedback false');
      run('gamerule logAdminCommands false');
    }
    run('kill @e[type=minecraft:item_frame]');
    run('kill @e[type=minecraft:item]');
    run('fill ' + (px - 5) + ' ' + by + ' ' + (pz - 4) + ' ' + (px + 5) + ' ' + (by + 5) + ' ' + (pz + 7) + ' minecraft:air');
    run('fill ' + (px - 5) + ' ' + (by - 1) + ' ' + (pz - 4) + ' ' + (px + 5) + ' ' + (by - 1) + ' ' + (pz + 7) + ' minecraft:stone');
    run('gamemode creative ' + me);
    run('time set noon');
    run('weather clear');
    Utils.runAfter(100, () => {
      run('setblock ' + (bx + 1) + ' ' + by + ' ' + bz + ' nsdemo:crystal');
      run('setblock ' + (bx - 1) + ' ' + by + ' ' + bz + ' modjs:crystal');
      run('item replace entity ' + me + ' hotbar.0 with nsdemo:gem 3');
      run('item replace entity ' + me + ' hotbar.1 with modjs:gem 3');
      run('item replace entity ' + me + ' hotbar.2 with nsdemo:shard 3');
      run('item replace entity ' + me + ' hotbar.3 with nsdemo:crystal 3');
      run('item replace entity ' + me + ' hotbar.4 with modjs:crystal 3');
      run('tp ' + me + ' ' + bx + ' ' + by + ' ' + (bz - 3.5) + ' 0 14');
      console.log('NS-TOUR READY world');
    });
  });
})();

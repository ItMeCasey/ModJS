// Development only: the screenshot tour for docs/frames. Copy into run/modjs/server_scripts together with block_info.js
// and open the saved world with -PquickWorld. On login it puts each scene a few blocks in front of the player, turns the
// player to look at it and logs "INFO-TOUR READY <name>"; scripts/info_tour.py watches the log and captures the screen then.
PlayerEvents.loggedIn(event => {
  const run = command => event.runCommand(command);
  const me = event.player.getName().getString();
  const bx = Math.floor(event.player.getX());
  const by = Math.round(event.player.getY());
  const bz = Math.floor(event.player.getZ()) + 3;
  const at = bx + ' ' + by + ' ' + bz;
  const scenes = [
    { name: 'lamp', place: () => run('setblock ' + at + ' modjs:info_lamp') },
    { name: 'crate', place: () => {
      run('setblock ' + at + ' modjs:info_crate');
      run('item replace block ' + at + ' container.0 with minecraft:coal 23');
    } },
    { name: 'furnace', place: () => run('setblock ' + at + ' minecraft:furnace') },
    { name: 'cow', place: () => {
      run('setblock ' + at + ' minecraft:air');
      run('summon minecraft:cow ' + (bx + 0.5) + ' ' + by + ' ' + (bz + 0.5) + ' {NoAI:1b,PersistenceRequired:1b}');
    } },
  ];
  // The reused test world has an item frame and a dropped sword near the spawn; they would be what the crosshair hits.
  run('kill @e[type=minecraft:item_frame]');
  run('kill @e[type=minecraft:item]');
  run('time set noon');
  run('weather clear');
  scenes.forEach((scene, index) => {
    const start = 100 + index * 200;
    Utils.runAfter(start, () => {
      scene.place();
      // Command syntax differs between versions, so each form is tried; the ones a version does not know just print an error.
      const look = (bx + 0.5) + ' ' + (by + 0.5) + ' ' + (bz + 0.5);
      run('execute as ' + me + ' at ' + me + ' run tp @s ~ ~ ~ facing ' + look);
      run('rotate ' + me + ' facing ' + look);
      // Plain angles as well: 0 faces south, 20 looks down a little. Old versions take them on tp, new ones on rotate.
      run('execute as ' + me + ' at ' + me + ' run tp @s ~ ~ ~ 0 20');
      run('rotate ' + me + ' 0 20');
    });
    Utils.runAfter(start + 120, () => console.log('INFO-TOUR READY ' + scene.name));
  });
  Utils.runAfter(100 + scenes.length * 200 + 40, () => console.log('INFO-TOUR DONE'));
});

// Custom in-world info for blocks and entities, shown by Show Me What This Is and by Jade.
// Startup scripts run on both the client and the server, so these handlers work in singleplayer and on a dedicated server.

StartupEvents.registry('block', event => {
  event.create('info_lamp').displayName('Info Lamp').hardness(1).lightLevel(10).texture('minecraft:block/glowstone');
  event.create('info_crate').displayName('Info Crate').container(1).hardness(2).texture('minecraft:block/barrel_side');
});

// A block with no block entity: the handler runs on the client and sees the block state.
InfoEvents.block('modjs:info_lamp', event => {
  event.add('Lit by a script', { color: '#ffd24a' });
  event.bar('Brightness', 10 / 15, '#ffd24a');
  event.item('minecraft:glowstone_dust', 'Made of glow');
  if (event.detailed) event.add('Sneaking shows the long form', { position: 'tail', color: '#aaaaaa' });
});

// A block with a block entity: the handler runs on the server and sees what is really inside.
InfoEvents.block('modjs:info_crate', event => {
  const crate = event.blockEntity;
  const id = crate.getItemId(0);
  const count = crate.getCount(0);
  if (!id || id === 'minecraft:air' || count === 0) {
    event.add('Empty', { color: '#888888' });
    return;
  }
  event.item(id, 'Holding ' + count, count);
  event.bar('Filled', count / 64, '#55ff55');
});

// Existing blocks work the same way: this adds a line to the vanilla furnace.
InfoEvents.block('minecraft:furnace', event => {
  event.add('Edited by a script', { color: '#ff8844', position: 'tail' });
});

// Entities: the server handler sees the real health.
InfoEvents.entity('minecraft:cow', event => {
  const cow = event.entity;
  event.bar('Health', cow.getHealth() / cow.getMaxHealth(), '#ff5555');
  event.add('Says moo', { color: '#cccccc' });
});

// Jade only: hide the lines another Jade provider added, by its tag.
// InfoEvents.block('minecraft:furnace', event => event.hideTag('minecraft:furnace'));

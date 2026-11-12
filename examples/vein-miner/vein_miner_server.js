// Vein miner, server half: while the player holds the key, breaking a log breaks every connected log with the player's own tool.
const holding = {};

NetworkEvents.dataReceived('veinminer:hold', e => {
  holding[e.player.getUUID().toString()] = !!e.data.held;
});

BlockEvents.broken('#minecraft:logs', e => {
  if (e.fromScript || !holding[e.player.getUUID().toString()]) {
    return;                                      // breaks started by breakBlocks never run this handler anyway
  }
  const logs = e.world.connected(e.x, e.y, e.z, '#minecraft:logs', true, 64)
    .filter(p => !(p.x === e.x && p.y === e.y && p.z === e.z));   // the log being broken is broken by the game itself
  // Uses the tool (durability, enchantments), hunger and drops; stops when the tool breaks. BlockEvents.broken does not run again.
  e.breakBlocks(logs);
});

// Vein miner, client half: hold the key (default: grave accent ` ) while looking at a log to outline the whole tree.
// Put this in the client_scripts folder and vein_miner_server.js in server_scripts.
ClientEvents.keyBindings(e => {
  e.register('veinminer:hold', 96, 'Vein miner (hold)');
});

let outlinedFor = '';
let wasHeld = false;

ClientEvents.tick(() => {
  const held = Client.isKeyHeld('veinminer:hold');
  if (held !== wasHeld) {                       // tell the server only when the key changes
    wasHeld = held;
    Network.sendToServer('veinminer:hold', { held: held });
  }
  const hit = held ? Client.lookingAt() : null;
  if (!hit || !Client.world.matches(hit.x, hit.y, hit.z, '#minecraft:logs')) {
    if (outlinedFor !== '') {
      Overlay.removeOutline('vein');
      outlinedFor = '';
    }
    return;
  }
  const key = hit.x + ',' + hit.y + ',' + hit.z;
  if (key === outlinedFor) {
    return;                                      // same block as last tick: keep the outline
  }
  outlinedFor = key;
  const logs = Client.world.connected(hit.x, hit.y, hit.z, '#minecraft:logs', true, 64);
  Overlay.outline('vein', logs, { color: '#ffd24a', width: 2, fill: '#30ffd24a', throughWalls: true });
});

// Development only (scripts/outline_tour.py): looks at the ground, reads the block with Client.lookingAt / getBlock, outlines blocks around it
// with Overlay.outline, holds a script key, takes a frame and exits cleanly. Logs "OL-CHECK ..." results.
(function () {
  const mc = () => Java.loadClass('net.minecraft.client.Minecraft').getInstance();
  let ticks = -1;
  Client.skipExperimentalWarning();
  Client.hideToasts('all');
  ClientEvents.keyBindings(e => { e.register('modjs_tour:hold', 80, 'Tour hold'); });
  ClientEvents.loggedIn(() => { ticks = 0; console.log('OL-TOUR READY'); });
  function check(name, ok, detail) {
    console.log('OL-CHECK ' + (ok ? 'PASS' : 'FAIL') + ' ' + name + (detail ? ': ' + detail : ''));
  }
  ClientEvents.tick(() => {
    if (ticks < 0) return;
    ticks++;
    if (ticks === 40) mc().player.setXRot(40);
    if (ticks === 70) {
      const hit = Client.lookingAt();
      check('lookingAt', hit != null && typeof hit.id === 'string' && hit.distance > 0, JSON.stringify(hit));
      if (hit == null) return;
      const block = Client.getBlock(hit.x, hit.y, hit.z);
      check('getBlock', block != null && block.id === hit.id && Client.world.getBlockId(hit.x, hit.y, hit.z) === hit.id, block ? block.id : 'null');
      const keys = Java.loadClass('com.itmecasey.modjs.client.ScriptedKeys');
      const before = Client.isKeyHeld('modjs_tour:hold');
      keys.get('modjs_tour:hold').setDown(true);
      const during = Client.isKeyHeld('modjs_tour:hold');
      keys.get('modjs_tour:hold').setDown(false);
      check('isKeyHeld', before === false && during === true && Client.isKeyHeld('modjs_tour:hold') === false, before + '/' + during);
      let refused = false;
      try { Client.world.destroyBlock(hit.x, hit.y, hit.z, true); } catch (e) { refused = true; }
      check('read-only client world refuses destroyBlock', refused);
      const ring = [];
      for (let dx = -2; dx <= 2; dx++) for (let dz = -2; dz <= 2; dz++) ring.push({ x: hit.x + dx, y: hit.y, z: hit.z + dz });
      const set = Overlay.outline('tour_ring', ring, { color: '#ffa000', width: 3, fill: '#55ffa000' });
      const column = Overlay.outline('tour_column', [[hit.x, hit.y - 1, hit.z], [hit.x, hit.y - 2, hit.z]], { color: '#00e5ff', width: 3, throughWalls: true });
      check('outline', set.count === 25 && column.count === 2 && Overlay.getOutline('tour_ring') != null, set.count + '/' + column.count);
    }
    if (ticks === 110) { Client.screenshot('ol_world.png'); console.log('OL-CHECK ok frame'); }
    if (ticks === 260) { console.log('OL-TOUR clean client exit'); mc().stop(); }
  });
})();

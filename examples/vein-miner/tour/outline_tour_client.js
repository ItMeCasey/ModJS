// Development only (scripts/outline_tour.py): looks at the ground, reads the block with Client.lookingAt / getBlock, outlines blocks around it
// with Overlay.outline, holds a script key, takes a frame and exits cleanly. Logs "OL-CHECK ..." results.
(function () {
  const mc = () => Java.loadClass('net.minecraft.client.Minecraft').getInstance();
  let ticks = -1;
  Client.skipExperimentalWarning();
  Client.hideToasts('all');
  ClientEvents.keyBindings(e => { e.register('modjs_tour:hold', 80, 'Tour hold'); });   // P
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
      let unknownKey = false;
      try { Client.isKeyHeld('modjs_tour:none'); } catch (e) { unknownKey = true; }
      check('isKeyHeld', before === false && during === true && Client.isKeyHeld('modjs_tour:hold') === false && unknownKey, before + '/' + during);
      let refused = false;
      try { Client.world.destroyBlock(hit.x, hit.y, hit.z, true); } catch (e) { refused = true; }
      check('read-only client world refuses destroyBlock', refused);
      const left = [], right = [];
      for (let dz = -1; dz <= 1; dz++) {
        left.push({ x: hit.x - 2, y: hit.y, z: hit.z + dz }, { x: hit.x - 1, y: hit.y, z: hit.z + dz });
        right.push([hit.x + 1, hit.y, hit.z + dz], [hit.x + 2, hit.y, hit.z + dz]);
      }
      // left: one merged outline with a tint; right: every block outlined on its own, no tint
      const merged = Overlay.outline('tour_merged', left, { color: '#ffd27a', fill: '#55ffd27a', connected: true, width: 3 });
      const single = Overlay.outline(right, { color: '#00e5ff', width: 3, connected: false });
      check('outline', merged.count === 6 && single.count === 6 && merged.connected === true && single.connected === false
        && Overlay.getOutline('tour_merged') != null && Overlay.getOutline(single.id) != null, merged.count + '/' + single.count + ' ' + single.id);
    }
    if (ticks === 110) { Client.screenshot('ol_world.png'); console.log('OL-CHECK ok frame'); }
    if (ticks === 260) { console.log('OL-TOUR clean client exit'); mc().stop(); }
  });
})();

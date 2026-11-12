// Development only, client half of the sneak tour: skips popups, takes a frame, exits cleanly.
(function () {
  const mc = () => Java.loadClass('net.minecraft.client.Minecraft').getInstance();
  let ticks = -1;
  Client.skipExperimentalWarning();
  Client.hideToasts('all');
  ClientEvents.loggedIn(() => { ticks = 0; });
  ClientEvents.tick(() => {
    if (ticks < 0) return;
    ticks++;
    if (ticks === 140) { Client.screenshot('sn_world.png'); console.log('SN-CHECK ok frame'); }
    if (ticks === 320) { console.log('SN-TOUR clean client exit'); mc().stop(); }
  });
})();

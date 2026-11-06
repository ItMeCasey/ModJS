// Development only: takes the tour's screenshots from inside the game, so the tour needs no screen capture. The times match
// info_tour.js: each scene is placed at 100 + 200 * n server ticks after login and is looked at a little later.
// If the client is still not in a world after 30 seconds, one frame of whatever screen is open is saved as info_stall.png.
// Wrapped so the helper names below do not clash with the other tour scripts, which share one scope.
(function () {
  const names = ['lamp', 'crate', 'furnace', 'cow'];
  const registryTick = 1000 + 110;   // examples/registry/tour/registry_tour.js
  let ticks = -1;
  let waiting = 0;
  ClientEvents.loggedIn(() => { ticks = 0; });
  ClientEvents.tick(() => {
    if (ticks < 0) {
      waiting++;
      if (waiting === 600) Client.screenshot('info_stall.png');
      return;
    }
    ticks++;
    names.forEach((name, index) => {
      if (ticks === 100 + index * 200 + 110) Client.screenshot('info_' + name + '.png');
    });
    if (ticks === registryTick) Client.screenshot('info_registry.png');
  });
})();

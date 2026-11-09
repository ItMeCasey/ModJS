// Copy into modjs/client_scripts. Coordinates use scaled GUI pixels; colors are ARGB.
ClientEvents.hud(event => {
  event.fill(8, 8, 180, 22, 0xCC182432);
  event.text('Welcome to the pack', 14, 15, 0xFFFFCC44);
});

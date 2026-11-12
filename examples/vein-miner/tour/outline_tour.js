// Development only (scripts/outline_tour.py): on login, clears the air around the player and lays a grass floor so every build's frame
// shows the outlines on the same flat ground.
PlayerEvents.loggedIn(event => {
  const run = command => event.runCommand(command);
  const px = Math.floor(event.player.getX());
  const py = Math.round(event.player.getY());
  const pz = Math.floor(event.player.getZ());
  run('fill ' + (px - 7) + ' ' + py + ' ' + (pz - 7) + ' ' + (px + 7) + ' ' + (py + 5) + ' ' + (pz + 7) + ' minecraft:air');
  run('fill ' + (px - 7) + ' ' + (py - 1) + ' ' + (pz - 7) + ' ' + (px + 7) + ' ' + (py - 1) + ' ' + (pz + 7) + ' minecraft:grass_block');
});

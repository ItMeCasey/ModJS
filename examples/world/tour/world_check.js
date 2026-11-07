// Development only, part of the client tour (server script). Logs the chat, logged-out and level-unloaded events when the player chats
// and when the world closes, and checks that a run-time dimension is created, entered, and still there after the world was reopened.
// Results are logged as "WORLD-CHECK ..." lines.
(function () {
  const ID = 'modjs:tour_dim';
  PlayerEvents.chat(e => console.log('WORLD-CHECK ok chat "' + e.message + '" by ' + e.playerName));
  PlayerEvents.loggedOut(e => console.log('WORLD-CHECK ok loggedOut ' + e.playerName));
  LevelEvents.unloaded(e => console.log('WORLD-CHECK ok level unloaded'));
  PlayerEvents.loggedIn(e => {
    const player = e.player;
    Utils.runAfter(40, () => {
      console.log('WORLD-CHECK dimension exists at join: ' + Dimensions.exists(ID));
      if (!Dimensions.exists(ID)) {
        console.log('WORLD-CHECK create ' + Dimensions.create(ID, { generator: 'void' }));
      }
      Utils.runAfter(40, () => {
        console.log('WORLD-CHECK teleport ' + Dimensions.teleport(player, ID, 0.5, 70, 0.5));
        const where = tag => { try { const lvl = typeof player.level === 'function' ? player.level() : player.level; console.log('WORLD-CHECK ' + tag + ' in ' + lvl.dimension() + ' y=' + Math.round(player.getY()) + ' alive=' + player.isAlive()); } catch (e) { console.log('WORLD-CHECK where ' + e); } };
        Utils.runAfter(10, () => where('after10'));
        Utils.runAfter(40, () => where('after40'));
        Utils.runAfter(200, () => where('after200'));
      });
    });
  });
})();

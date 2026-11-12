// Development only (scripts/sneak_tour.py): crouches the player through the server (setShiftKeyDown, as the game's own input handler does)
// and logs the sneak events and event.sneaking, then "SN-CHECK ..." results.
(function () {
  const seen = [];
  PlayerEvents.sneakStarted(event => { seen.push('started:' + event.sneaking); console.log('SN-TOUR sneakStarted sneaking=' + event.sneaking); });
  PlayerEvents.sneakStopped(event => { seen.push('stopped:' + event.sneaking); console.log('SN-TOUR sneakStopped sneaking=' + event.sneaking); });
  PlayerEvents.loggedIn(event => {
    const player = event.player;
    console.log('SN-TOUR READY world');
    Utils.runAfter(100, () => { player.setShiftKeyDown(true); console.log('SN-TOUR crouch on'); });
    Utils.runAfter(160, () => { player.setShiftKeyDown(false); console.log('SN-TOUR crouch off'); });
    Utils.runAfter(220, () => {
      console.log('SN-CHECK ' + (seen.indexOf('started:true') >= 0 ? 'PASS' : 'FAIL') + ' sneakStarted fired with sneaking true: ' + seen);
      console.log('SN-CHECK ' + (seen.indexOf('stopped:false') >= 0 ? 'PASS' : 'FAIL') + ' sneakStopped fired with sneaking false: ' + seen);
    });
  });
})();

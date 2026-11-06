// Development only, part of the client tour: sends the ping once the client has been in the world for 60 ticks and logs the pong.
// Wrapped so the helper names below do not clash with the other tour scripts, which share one scope.
(function () {
  let ticks = -1;
  ClientEvents.loggedIn(() => { ticks = 0; });
  ClientEvents.tick(() => {
    if (ticks < 0) return;
    ticks++;
    if (ticks === 60) Network.sendToServer('tour_ping', { n: 1 });
  });
  NetworkEvents.dataReceived('tour_pong', event => {
    console.log('NET-CHECK client got pong ' + event.data.n + ' ' + event.data.text);
  });
})();

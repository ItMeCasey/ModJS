// Development only, part of the client tour. The server answers a ping from the client with a pong, and logs both ends, so the
// tour can prove a script message travelled client -> server and server -> client in a real client ("NET-CHECK ..." lines).
NetworkEvents.dataReceived('tour_ping', event => {
  console.log('NET-CHECK server got ping ' + event.data.n + ' from ' + event.player.getName().getString());
  Network.sendToPlayer(event.player, 'tour_pong', { n: event.data.n + 1, text: 'back again' });
});

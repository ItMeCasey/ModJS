ClientEvents.splashes(event => {
  event.add('Made with ModJS!');
});
ClientEvents.titleScreen(event => {
  event.background('panorama');
  event.button('Singleplayer', 'singleplayer');
  event.button('Multiplayer', 'multiplayer');
  event.button('Options', 'options');
  event.button('Quit', 'quit');
  event.text('Welcome to the server', { y: 8 });
});

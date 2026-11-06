// Development only, part of the client tour: opens a scripted screen with text, a button and a text box in the running game,
// saves a frame of it (info_screen.png) and closes it again. The screen is also defined here so the tour needs nothing else.
ClientEvents.screens(event => {
  event.create('tour_hub', screen => {
    screen.text('Scripted screen', { y: 30 });
    screen.input('name', { placeholder: 'Your name', y: 60, width: 160, initial: 'ModJS' });
    screen.button('Go', 'close', { y: 90, width: 80 });
    screen.button('Close', 'close', { y: 115, width: 80 });
  });
});
let ticks = -1;
ClientEvents.loggedIn(() => { ticks = 0; });
ClientEvents.tick(() => {
  if (ticks < 0) return;
  ticks++;
  if (ticks === 1250) { Client.openScreen('tour_hub'); console.log('SCREEN-CHECK opened'); }
  if (ticks === 1270) Client.screenshot('info_screen.png');
  if (ticks === 1290) { Client.closeScreen(); console.log('SCREEN-CHECK closed'); }
});

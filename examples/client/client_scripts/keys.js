// Restart the client after editing registrations; players can rebind these in Controls.
ClientEvents.keyBindings(event => {
  event.register('pack:welcome', 71, 'Show welcome message'); // G
});
ClientEvents.keyBindingPressed('pack:welcome', event => {
  console.log('Welcome key pressed');
});

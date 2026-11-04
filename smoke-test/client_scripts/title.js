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

RecipeViewerEvents.hideItems(event => event.hide('minecraft:dirt', '#minecraft:wool'));
RecipeViewerEvents.addInformation(event => event.add('minecraft:diamond', 'Found deep underground.', 'Needs an iron pickaxe.'));
RecipeViewerEvents.hideCategories(event => event.hide('minecraft:campfire_cooking'));
RecipeViewerEvents.groupItems(event => event.group('Test gems', ['minecraft:diamond', 'minecraft:emerald'], { icon: 'minecraft:diamond' }));

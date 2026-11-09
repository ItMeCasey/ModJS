// Development only, part of the client tour: exercises the three recipe viewer events so JEI (or a bridge that provides it) shows
// them. The plugin logs "RecipeViewerEvents.<event>: ..." lines that the tour checks.
(function () {
  RecipeViewerEvents.hideItems(event => event.hide('minecraft:dirt', '#minecraft:wool'));
  RecipeViewerEvents.addInformation(event => event.add('minecraft:diamond', 'Found deep underground.', 'Needs an iron pickaxe.'));
  RecipeViewerEvents.hideCategories(event => event.hide('minecraft:campfire_cooking'));
})();

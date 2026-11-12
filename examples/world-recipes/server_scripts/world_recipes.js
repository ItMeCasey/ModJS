// In-world crafting, like the Lychee mod. Copy into modjs/server_scripts; /modjs reload applies changes.
// Blocks and items are ids or #tags. Results can change or remove the block, drop or give items, wear the tool and run a command.
ServerEvents.worldRecipes(event => {
  // Right-click stone with flint: it becomes cobblestone and throws out two gravel (the flint is used up).
  event.itemOnBlock('minecraft:stone', 'minecraft:flint').turnInto('minecraft:cobblestone').drop('minecraft:gravel', 2);

  // Sneak-right-click a full water cauldron with sand: clay, and the cauldron empties. Block state values go in brackets.
  event.itemOnBlock('minecraft:water_cauldron[level=3]', 'minecraft:sand').sneaking(true)
    .turnInto('minecraft:cauldron').give('minecraft:clay_ball', 4);

  // Left-click a log with a shovel: sometimes two sticks fall out and the shovel wears down a little.
  event.leftClickBlock('#minecraft:logs', '#minecraft:shovels').damage(1).chance(0.5).drop('minecraft:stick', 2).consume(0);

  // Dropped items in water: two coal and some sand make a diamond (the items are used up).
  event.itemsInBlock('minecraft:water', ['minecraft:coal x2', 'minecraft:sand']).drop('minecraft:diamond');

  // Redstone left on a gold block turns it into a redstone block.
  event.itemsOnBlock('minecraft:gold_block', ['minecraft:redstone x9']).turnInto('minecraft:redstone_block');

  // An anvil dropped on stone crushes it; lightning turns sand into glass; grass slowly dries out next to you.
  event.anvil('minecraft:stone').turnInto('minecraft:gravel');
  event.lightning('minecraft:sand').turnInto('minecraft:glass');
  event.randomTick('minecraft:grass_block').chance(0.001).turnInto('minecraft:dirt');

  // Breaking coal ore with an iron pickaxe gives a diamond too, or with replaceDrops() only what you say.
  event.blockBreak('minecraft:coal_ore').tool('minecraft:iron_pickaxe').drop('minecraft:diamond');
});

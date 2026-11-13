// Villager trades for the headless self test: whole-tier replacement, remove-all-then-add, and clearing a profession.
Villagers.replaceTrades('minecraft:cleric', 1, [
  { give: { item: 'minecraft:diamond', count: 2 }, want: '3x minecraft:emerald', maxUses: 7, xp: 9, priceMultiplier: 0.1 },
  { give: 'minecraft:gold_ingot', want: ['minecraft:emerald', 'minecraft:book'] }
]);
Villagers.removeTrades('minecraft:toolsmith', 1, { give: '*' });
Villagers.addTrade('minecraft:toolsmith', 1, { give: 'minecraft:iron_axe', want: '4x minecraft:emerald' });
Villagers.clearTrades('minecraft:librarian');
// Conditions: a pool of two trades, one for desert villagers and one for plains and taiga villagers; each villager is offered only the one that fits.
// A fletcher with a biome condition only has to load (the biome the test world is in decides whether it is offered).
Villagers.replaceTrades('minecraft:mason', 1, [
  { give: 'minecraft:stone', want: 'minecraft:emerald', villagerType: 'minecraft:desert' },
  { give: 'minecraft:granite', want: 'minecraft:emerald', villagerType: ['minecraft:plains', 'minecraft:taiga'] }
]);
Villagers.replaceTrades('minecraft:fletcher', 1, [{ give: 'minecraft:arrow', want: 'minecraft:emerald', biome: ['minecraft:desert', 'minecraft:plains'] }]);

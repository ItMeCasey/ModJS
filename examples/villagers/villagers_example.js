// Villager trades: add, replace, remove and clear. Put this in server_scripts (or startup_scripts).
Villagers.addTrade('minecraft:librarian', 2, {
  give: 'minecraft:diamond_sword',
  want: ['12x minecraft:emerald'],
  maxUses: 4,
  xp: 10,
  priceMultiplier: 0.2
});

Villagers.addTrade('minecraft:farmer', 1, {
  give: '3x minecraft:cactus',
  want: ['2x minecraft:emerald', 'minecraft:bone_meal'],
  villagerType: 'minecraft:desert'
});

Villagers.removeTrades('minecraft:farmer', { give: 'minecraft:bread' });
Villagers.removeTrades('minecraft:cleric', 1, trade => trade.want[0].count > 8);
Villagers.replaceTrades('minecraft:fisherman', 1, [{ give: 'minecraft:cod', want: 'minecraft:emerald' }]);

Villagers.addTrade('minecraft:wandering_trader', 1, { give: '8x minecraft:bamboo', want: 'minecraft:emerald', maxUses: 6 });

// A new profession (startup_scripts): a job site, a work sound, a skin and trades for two levels.
Villagers.profession('mypack:smith', {
  jobSite: 'minecraft:anvil',
  workSound: 'minecraft:entity.villager.work_toolsmith',
  name: 'Smith',
  texture: 'minecraft:toolsmith',
  trades: {
    1: [{ give: 'minecraft:iron_pickaxe', want: '4x minecraft:emerald', maxUses: 5, xp: 3 }],
    2: [{ give: 'minecraft:anvil', want: '10x minecraft:emerald' }]
  }
});

// A custom villager profession for the headless self test: a job site block, a work sound, and trades for two levels.
Villagers.profession('modjs_test:smith', {
  jobSite: 'minecraft:crafting_table',
  workSound: 'minecraft:entity.villager.work_toolsmith',
  name: 'Test Smith',
  texture: 'minecraft:toolsmith',
  trades: {
    1: [
      { give: 'minecraft:iron_pickaxe', want: '4x minecraft:emerald', maxUses: 5, xp: 3 },
      { give: 'minecraft:iron_axe', want: ['2x minecraft:emerald', 'minecraft:stick'] }
    ],
    2: [{ give: 'minecraft:anvil', want: '10x minecraft:emerald' }]
  }
});

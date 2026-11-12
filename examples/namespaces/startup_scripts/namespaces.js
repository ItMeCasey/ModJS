// Items and blocks in a namespace of your own. An id without a namespace goes to modjs; 'nsdemo:gem' goes to nsdemo, with its
// models, language entries, loot table and default texture (nsdemo:item/gem) in the nsdemo folders. Copy into modjs/startup_scripts
// and the textures in examples/namespaces/assets into modjs/assets (one folder per namespace).
StartupEvents.registry('item', event => {
  event.create('nsdemo:gem').displayName('Pack Gem');
  event.create('gem').displayName('Modjs Gem');
});

StartupEvents.registry('block', event => {
  event.create('nsdemo:crystal').displayName('Pack Crystal').hardness(2);
  event.create('crystal').displayName('Modjs Crystal').hardness(2);
});

// event.namespace() changes where the plain ids of one handler go.
StartupEvents.registry('item', event => {
  event.namespace('nsdemo');
  event.create('shard').displayName('Pack Shard');   // nsdemo:shard
});

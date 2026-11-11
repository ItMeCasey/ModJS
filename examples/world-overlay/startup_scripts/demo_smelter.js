// A scripted machine with real slots, a tick handler, a screen with a progress bar and a button, a different texture on every
// side, and a block model that turns to face you. It smelts raw iron into iron ingots at its own pace. Copy into
// modjs/startup_scripts together with the textures in examples/world-overlay/assets (into modjs/assets).
StartupEvents.registry('block', event => {
  event.create('demo_smelter').displayName('Demo Smelter').hardness(3)
    .textures({
      top: 'modjs:block/demo_smelter_top', bottom: 'modjs:block/demo_smelter_bottom',
      side: 'modjs:block/demo_smelter_side', front: 'modjs:block/demo_smelter_front'
    })
    .model('orientable')
    // slot 0 takes raw iron, slot 1 holds the ingots; 'progress' and 'max' are saved with the block and reach the screen
    .inventory({ slots: [{ x: 44, y: 35 }, { x: 116, y: 35 }], stackLimit: 64, values: ['progress', 'max'] })
    .onTick(5, be => {
      be.setValue('max', 100);
      const outputItem = be.getItemId(1);
      const hasRoom = (outputItem === 'minecraft:air' || outputItem === 'minecraft:iron_ingot') && be.getCount(1) < 64;
      if (be.getData('paused') === true || be.getItemId(0) !== 'minecraft:raw_iron' || !hasRoom) return;
      const next = be.getValue('progress') + 5;
      if (next < 100) { be.setValue('progress', next); return; }
      be.shrink(0, 1);
      be.setItem(1, 'minecraft:iron_ingot', be.getCount(1) + 1);
      be.setValue('progress', 0);
    })
    .onButton((id, be, player) => {
      if (id === 'toggle') be.setData('paused', be.getData('paused') !== true);
    })
    .screen(screen => {
      screen.text('Demo Smelter', { x: 8, y: 6, left: true, color: 0x404040 });
      screen.bar({ value: 'progress', max: 'max', x: 70, y: 38, width: 36, height: 8, color: 0xE8872A });
      screen.button('Pause', { id: 'toggle', x: 70, y: 56, width: 36, height: 16 });
    });
});

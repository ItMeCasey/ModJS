// Draws a floating title, a progress bar, an item and a texture on the top face over every Demo Smelter, from the data the
// block syncs to the client. The template runs when a smelter comes near and again when its synced data changes.
Overlay.forBlock('modjs:demo_smelter', (at, data, pos) => {
  at.text('title', { text: 'Demo Smelter', y: 0.45, color: '#ffd27a', background: '#80000000' });
  at.bar('progress', { value: data.progress || 0, max: data.max || 100, width: 1.0, height: 0.14, y: 0.2, color: '#e8872a' });
  at.item('icon', { item: 'minecraft:raw_iron', y: 0.95 });
  at.texture('badge', { texture: 'modjs:textures/gui/overlay_badge.png', face: 'up', width: 0.5, height: 0.5 });
});

// Development only (scripts/title_icon_tour.py): a main menu with an item icon on a button (GitHub issue 2: crashed on 26.3), a frame, a clean exit.
(function () {
  const mc = () => Java.loadClass('net.minecraft.client.Minecraft').getInstance();
  let ticks = 0;
  Client.skipExperimentalWarning();
  Client.hideToasts('all');
  ClientEvents.titleScreen(event => {
    event.background('dirt');
    event.button('Singleplayer', 'singleplayer', {icon: 'minecraft:diamond', iconSize: 16});
    event.button('Quit', 'quit', {icon: 'minecraft:compass'});
    // anchors (GitHub issue 4): a left column, a corner button, a centered title and a box at the bottom
    event.button('Left top', 'close', {anchor: 'left', x: 12, y: -36, width: 130, icon: 'minecraft:emerald'});
    event.button('Left bottom', 'close', {anchor: 'left', x: 12, y: 36, width: 130});
    event.button('Corner', 'close', {anchor: 'bottomright', x: -8, y: -8, width: 90});
    event.text('Anchored title', {anchor: 'top', y: 8, color: 0xFFD700});
    event.input('name', {anchor: 'bottom', y: -40, width: 160, placeholder: 'bottom input'});
  });
  ClientEvents.tick(() => {
    ticks++;
    if (ticks === 900) { Client.screenshot('ti_title.png'); console.log('TI-CHECK frame taken'); }
    if (ticks === 940) { console.log('TI-TOUR clean client exit'); mc().stop(); }
  });
})();
